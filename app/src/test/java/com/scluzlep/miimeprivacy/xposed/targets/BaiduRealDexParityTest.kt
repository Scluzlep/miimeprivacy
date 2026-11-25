package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.InMemorySharedPreferences
import com.scluzlep.miimeprivacy.xposed.RealApkDexSimulationHarness
import com.scluzlep.miimeprivacy.xposed.RecordingXposedInterface
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class BaiduRealDexParityTest {

    private val allBaiduNonOverlayRules by lazy {
        HookRuleCatalog.rulesFor(ImeTarget.BAIDU)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }
    }

    @Before
    fun requireRealBaiduApk() {
        val apk = RealApkDexSimulationHarness.apkFileFor(ImeTarget.BAIDU)
        Assume.assumeTrue(
            "Skipping real-APK Baidu parity test: missing ${apk.absolutePath}",
            apk.isFile
        )
    }

    @After
    fun resetConfigBridge() {
        val defaultPrefs = InMemorySharedPreferences()
        PrivacyConfig(updatedAtMs = ConfigBridge.current.updatedAtMs + 1L)
            .toSharedPreferences(defaultPrefs, commitSync = true)
        ConfigBridge.detach()
        ConfigBridge.attachRemote(
            RecordingXposedInterface(
                remotePrefsMap = mapOf(ConfigContract.REMOTE_PREFS_NAME to defaultPrefs)
            )
        )
        ConfigBridge.detach()
    }

    private fun allEnabledBaiduConfig(): PrivacyConfig {
        var cfg = PrivacyConfig(
            baiduManaged = true,
            blockHttpDns = true
        )
        for (ruleId in allBaiduNonOverlayRules) {
            cfg = cfg.withRuleOverride(ruleId, true)
        }
        return cfg
    }

    @Test
    fun allFourGates_autoFastPath_dynamicDexKit_descriptorParity_andWarmCache() {
        val config = allEnabledBaiduConfig()
        assertEquals(27, allBaiduNonOverlayRules.size)
        for (ruleId in allBaiduNonOverlayRules) {
            assertTrue("Expected $ruleId enabled in test config", config.isRuleEnabled(ruleId))
        }

        // Gate 1: AUTO mode -> 100% FAST_PATH, all 27 rules ACTIVE
        val autoSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.AUTO,
            config = config,
            prefs = InMemorySharedPreferences()
        )
        val autoResult = autoSession.installPack(BaiduHookPack)

        val autoNonFastTraces = autoSession.resolutionTraces.filter { it.source != ResolveSource.FAST_PATH }
        val autoMissTraces = autoSession.resolutionTraces.filter { it.source == ResolveSource.MISS }
        val autoBadReports = autoResult.reports.filter { it.status != HookMatchStatus.ACTIVE }

        // Gate 2: DYNAMIC mode (cold prefs) -> 100% DEXKIT, 0 FAST_PATH, 0 STRUCTURAL_FALLBACK, 0 MISS
        val dynamicPrefs = InMemorySharedPreferences()
        val dynamicSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = config,
            prefs = dynamicPrefs
        )
        val dynamicResult = dynamicSession.installPack(BaiduHookPack)

        val dynNonDexKitTraces = dynamicSession.resolutionTraces.filter { it.source != ResolveSource.DEXKIT }
        val dynBadReports = dynamicResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("FAST_PATH") ||
                it.source.contains("STRUCTURAL_FALLBACK") ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:", ignoreCase = true)
        }

        // Gate 3: 1-to-1 Descriptor Parity (recordedHooks & resolutionTraces)
        assertAllTracesAndHooksBackedByRealDex(autoSession, "Gate 1 AUTO")
        assertAllTracesAndHooksBackedByRealDex(dynamicSession, "Gate 2 DYNAMIC")

        val autoHooksById = buildHookDescriptorMap(
            autoSession.xposed.recordedHooks.map { it.id to it.executable },
            "Gate 1 AUTO"
        )
        val dynamicHooksById = buildHookDescriptorMap(
            dynamicSession.xposed.recordedHooks.map { it.id to it.executable },
            "Gate 2 DYNAMIC"
        )

        val autoTraceMap = autoSession.resolutionTraces.associate {
            (it.kind to it.cacheKey) to it.descriptor
        }
        val dynamicTraceMap = dynamicSession.resolutionTraces.associate {
            (it.kind to it.cacheKey) to it.descriptor
        }

        // Gate 4: Warm CACHE mode (re-run DYNAMIC on warmed dynamicPrefs)
        val cacheSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = config,
            prefs = dynamicPrefs
        )
        val cacheResult = cacheSession.installPack(BaiduHookPack)
        val cacheNonCacheTraces = cacheSession.resolutionTraces.filter { it.source != ResolveSource.CACHE }
        val cacheBadReports = cacheResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertAllTracesAndHooksBackedByRealDex(cacheSession, "Gate 4 CACHE")
        val cacheHooksById = buildHookDescriptorMap(
            cacheSession.xposed.recordedHooks.map { it.id to it.executable },
            "Gate 4 CACHE"
        )

        // Aggregate diagnostic report before failing so all gaps are visible in one run
        val failures = buildString {
            if (autoNonFastTraces.isNotEmpty() || autoMissTraces.isNotEmpty()) {
                appendLine("=== GATE 1 (AUTO) non-FAST_PATH traces (${autoNonFastTraces.size}) ===")
                autoNonFastTraces.forEach { appendLine("  $it") }
            }
            if (autoBadReports.isNotEmpty()) {
                appendLine("=== GATE 1 (AUTO) non-ACTIVE reports (${autoBadReports.size}) ===")
                autoBadReports.forEach { appendLine("  $it") }
            }
            if (dynNonDexKitTraces.isNotEmpty()) {
                appendLine("=== GATE 2 (DYNAMIC) non-DEXKIT traces (${dynNonDexKitTraces.size}) ===")
                dynNonDexKitTraces.forEach { appendLine("  $it") }
            }
            if (dynBadReports.isNotEmpty()) {
                appendLine("=== GATE 2 (DYNAMIC) bad reports (${dynBadReports.size}) ===")
                dynBadReports.forEach { appendLine("  $it") }
            }
            if (autoHooksById != dynamicHooksById) {
                appendLine("=== GATE 3 hookId->descriptor diff (AUTO=${autoHooksById.size}, DYNAMIC=${dynamicHooksById.size}) ===")
                val allKeys = (autoHooksById.keys + dynamicHooksById.keys).sorted()
                for (k in allKeys) {
                    if (autoHooksById[k] != dynamicHooksById[k]) {
                        appendLine("  $k: AUTO=${autoHooksById[k]} | DYNAMIC=${dynamicHooksById[k]}")
                    }
                }
            }
            if (autoTraceMap != dynamicTraceMap) {
                appendLine("=== GATE 3 (kind,cacheKey)->descriptor diff ===")
                val allKeys = (autoTraceMap.keys + dynamicTraceMap.keys).sortedBy { "${it.first}:${it.second}" }
                for (k in allKeys) {
                    if (autoTraceMap[k] != dynamicTraceMap[k]) {
                        appendLine("  $k: AUTO=${autoTraceMap[k]} | DYNAMIC=${dynamicTraceMap[k]}")
                    }
                }
            }
            if (cacheNonCacheTraces.isNotEmpty() || cacheBadReports.isNotEmpty() || cacheHooksById != dynamicHooksById) {
                appendLine("=== GATE 4 (CACHE) issues: nonCacheTraces=${cacheNonCacheTraces.size}, badReports=${cacheBadReports.size} ===")
                cacheNonCacheTraces.forEach { appendLine("  $it") }
                cacheBadReports.forEach { appendLine("  $it") }
            }
        }
        assertTrue("Parity gate failures:\n$failures", failures.isEmpty())

        // Explicit assertions for each gate
        assertEquals(27, autoResult.reports.size)
        assertTrue(autoSession.resolutionTraces.isNotEmpty())
        assertTrue(autoNonFastTraces.isEmpty())
        assertTrue(autoBadReports.isEmpty())

        assertEquals(27, dynamicResult.reports.size)
        assertTrue(dynamicSession.resolutionTraces.isNotEmpty())
        assertTrue(dynNonDexKitTraces.isEmpty())
        assertTrue(dynBadReports.isEmpty())
        assertFalse(dynamicResult.reports.any { it.source.contains("FAST_PATH") })
        assertFalse(dynamicResult.reports.any { it.source.contains("STRUCTURAL_FALLBACK") })

        assertEquals(autoSession.xposed.recordedHooks.size, dynamicSession.xposed.recordedHooks.size)
        assertEquals(autoHooksById, dynamicHooksById)
        assertEquals(autoTraceMap, dynamicTraceMap)

        assertEquals(27, cacheResult.reports.size)
        assertTrue(cacheNonCacheTraces.isEmpty())
        assertTrue(cacheBadReports.isEmpty())
        assertEquals(dynamicHooksById, cacheHooksById)

        val expectedV3BaiduHooks = mapOf(
            "baidu.privacy.dj0_l_auto_consent_o" to "Ldj0/l;->o()V",
            "baidu.gate.noticenter_hotspots_n" to "Lph0/e;->n(Lorg/json/JSONObject;)V",
            "baidu.gate.noticenter_switch_c" to "Lph0/e;->c(Lkh0/j;)Lpe1/x;",
            "baidu.id.helios_fa_m_oaid" to "Lfa/m;->a(Landroid/content/Context;Lfa/a;)V",
            "baidu.id.helios_ha_cert_sync" to "Lha/b;->b()V",
            "baidu.id.helios_ga_b_honor_oaid" to "Lga/b;->a(Landroid/content/Context;Lfa/a;)V",
            "baidu.id.helios_ga_d_huawei_oaid" to "Lga/d;->a(Landroid/content/Context;Lfa/a;)V",
            "baidu.id.sofire_ac_f_gom" to "Lcom/baidu/sofire/ac/F;->gom(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.id.sofire_ac_f_gzd" to "Lcom/baidu/sofire/ac/F;->gzd(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.id.sofire_ac_f_rf" to "Lcom/baidu/sofire/ac/F;->rf(Landroid/content/Context;)V",
            "baidu.id.legacy_device_id" to "Lcom/baidu/android/common/util/DeviceId;->getDeviceID(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.id.legacy_cuid" to "Lcom/baidu/android/common/util/DeviceId;->getCUID(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.telemetry.zeus_upload_realtime" to "Lcom/baidu/zeus/jsr/statistics/LogUploader;->uploadRealTimeData(Ljava/lang/String;)V",
            "baidu.telemetry.zeus_native_set_trans" to "Lcom/baidu/zeus/jsr/statistics/LogUploader;->nativeSetStatisticsTransmission()V",
            "baidu.telemetry.iptcore_user_trace_write" to "Lcom/baidu/iptcore/IptCoreInterface;->userTraceWrite(II[B)V",
            "baidu.telemetry.iptcore_user_voice_log" to "Lcom/baidu/iptcore/IptCoreInterface;->userVoiceInputLog(Ljava/lang/String;)V",
            "baidu.telemetry.iptcore_set_upl_addr" to "Lcom/baidu/iptcore/IptCoreInterface;->setUplDataAddress(Ljava/lang/String;I)V",
            "baidu.telemetry.speech_analysis_check_upload" to "Lcom/baidu/speech/utils/analysis/AnalysisUpload;->checkIsUpload()Z",
            "baidu.telemetry.speech_analysis_get_permission" to "Lcom/baidu/speech/utils/analysis/AnalysisUpload;->getUploadPermission()Z",
            "baidu.telemetry.speech_analysis_data_callback" to "Lcom/baidu/speech/utils/analysis/AnalysisUpload;->dataCallBack(I[BIZ)I",
            "baidu.telemetry.speech_interceptor_init_asr" to "Lcom/baidu/speech/utils/analysis/AnalysisInterceptor;->initLocalAsrWpData(Ljava/lang/String;IZZZZZ)V",
            "baidu.telemetry.speech_interceptor_write_local" to "Lcom/baidu/speech/utils/analysis/AnalysisInterceptor;->writeLocalFile(Ljava/lang/String;II)V",
            "baidu.telemetry.speech_interceptor_set_callback" to "Lcom/baidu/speech/utils/analysis/AnalysisInterceptor;->setCallBack()V",
            "baidu.ads.ime_env_allow_cloud_ad" to "Lcom/baidu/input/ime/newcore/ImeEnvCallback;->isAllowCloudAdShow()Z",
            "baidu.telemetry.rj0_a_ads" to "Lrj0/a;->b()V",
            "baidu.cloud.aicard_cursor_reader" to "Lcom/baidu/input/aicard/impl/b;->M1(Z)Ljava/lang/String;",
            "baidu.clip.c2_d_k_read" to "Lc2/d;->k(Lpe1/m;)V",
            "baidu.clip.sapi_js_sms_clip#0" to "Lcom/baidu/sapi2/SapiJsInterpreters\$ActionGetSmsCheckCodeFromClip;->interpret(Lcom/baidu/sapi2/SapiWebView\$Command;)Ljava/lang/String;",
            "baidu.clipboard.db_insert_batch" to "Lcom/baidu/input/clipboard/datamanager/clipboard/a;->Y1(Ljava/util/List;Z)Ljava/util/List;",
            "baidu.clipboard.business_product_p" to "Lcom/baidu/business/Product;->p(Landroid/content/Context;Ljava/lang/String;)V",
            "baidu.clipboard.reader_b" to "Lwg0/b;->B(Lcom/baidu/input_mi/ImeService;)V",
            "baidu.scan.connect_getWifiName" to "Lcom/baidu/android/util/connect/NetWorkUtils;->getWifiName(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.scan.connect_getWifiInfo" to "Lcom/baidu/android/util/connect/NetWorkUtils;->getWifiInfo(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.scan.devices_getWifiName#0" to "Lcom/baidu/android/util/devices/NetWorkUtils;->getWifiName()Ljava/lang/String;",
            "baidu.scan.devices_getWifiName#1" to "Lcom/baidu/android/util/devices/NetWorkUtils;->getWifiName(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.scan.devices_getWifiInfo#0" to "Lcom/baidu/android/util/devices/NetWorkUtils;->getWifiInfo()Ljava/lang/String;",
            "baidu.scan.devices_getWifiInfo#1" to "Lcom/baidu/android/util/devices/NetWorkUtils;->getWifiInfo(Landroid/content/Context;)Ljava/lang/String;",
            "baidu.scan.iptcore_send_location_event" to "Lcom/baidu/iptcore/IptCoreInterface;->sendLocationEvent(FF)V",
            "baidu.scan.turbonet_wifi_ssid" to "Lcom/baidu/turbonet/net/NetworkChangeNotifierAutoDetect\$WifiManagerDelegate;->getWifiSSID()Ljava/lang/String;",
            "baidu.httpdns.turbonet_builder_bdns_backup" to "Lcom/baidu/turbonet/net/TurbonetEngine\$Builder;->addBdnsBackupHosts(Ljava/lang/String;)Lcom/baidu/turbonet/net/TurbonetEngine\$Builder;",
            "baidu.httpdns.turbonet_builder_apply_config" to "Lcom/baidu/turbonet/net/TurbonetEngine\$Builder;->applyBaiduConfiguration(Ljava/lang/String;)Lcom/baidu/turbonet/net/TurbonetEngine\$Builder;",
            "baidu.iptcore.request_url_resource" to "Lcom/baidu/input/ime/newcore/ImeEnvCallback;->requestUrlResource([Ljava/lang/String;JI)Z",
            "baidu.ocr.make_request" to "Lcom/baidu/speech/ocr/OcrHttpRequestMaker;->makeRequest(Ljava/lang/String;[B[Ljava/lang/String;FI)Lcom/baidu/speech/ocr/BDSHTTPResponse;",
            "baidu.speech.bds_core_post_message" to "Lcom/baidu/speech/core/BDSCoreJniInterface;->postMessage(Lcom/baidu/speech/core/BDSMessage;)I",
            "baidu.speech.bds_core_post_native" to "Lcom/baidu/speech/core/BDSCoreJniInterface;->Post(Lcom/baidu/speech/core/BDSMessage;Ljava/lang/String;)I",
            "baidu.speech.http_make_request" to "Lcom/baidu/speech/core/BDSHttpRequestMaker;->makeRequest(Ljava/lang/String;[B[Ljava/lang/String;FI)Lcom/baidu/speech/core/BDSHTTPResponse;",
            "baidu.speech.http_setup_connection" to "Lcom/baidu/speech/core/BDSHttpRequestMaker;->setupConnection(Ljava/lang/String;[Ljava/lang/String;FIZ)I",
            "baidu.speech.http_send_data" to "Lcom/baidu/speech/core/BDSHttpRequestMaker;->sendData([BZ)I",
            "baidu.talos.call_js_function" to "Lcom/baidu/talos/core/bridge/TalosBridgeManager;->callJSFunction(Ljava/lang/String;Ljava/lang/String;Lcom/baidu/talos/core/data/ParamArray;)V",
            "baidu.webkit.uploadRealTimeDataToUBCForJSR" to "Lcom/baidu/webkit/internal/monitor/SessionMonitorEngine;->uploadRealTimeDataToUBCForJSR(Ljava/lang/String;)V",
            "baidu.webkit.recordImmediately" to "Lcom/baidu/webkit/internal/monitor/SessionMonitorEngine;->recordImmediately(Ljava/lang/String;Ljava/lang/String;)V",
            "baidu.simeji.check_if_upload" to "Lcom/baidu/simeji/dictionary/session/helper/UploadHelper;->checkIfUpload()V",
            "baidu.cyber.ubc_str2" to "Lcom/baidu/cyberplayer/sdk/statistics/UbcSessionUploader;->upload(Ljava/lang/String;Ljava/lang/String;)V",
            "baidu.cyber.ubc_str3" to "Lcom/baidu/cyberplayer/sdk/statistics/UbcSessionUploader;->upload(Ljava/lang/String;Ljava/lang/String;I)V",
            "baidu.cyber.dp_upload2" to "Lcom/baidu/cyberplayer/sdk/statistics/DpSessionDatasUploader;->upload(Ljava/lang/String;Ljava/lang/String;)V",
            "baidu.cyber.dp_upload3" to "Lcom/baidu/cyberplayer/sdk/statistics/DpSessionDatasUploader;->upload(Ljava/lang/String;Ljava/lang/String;I)V",
            "baidu.cyber.dp_lib_init" to "Lcom/baidu/cyberplayer/sdk/statistics/DpSessionDatasUploader;->uploadLibInitSession(Ljava/lang/String;Ljava/lang/String;)V"
        )
        for ((hookId, expectedDesc) in expectedV3BaiduHooks) {
            assertEquals("AUTO missing/mismatch for v3.0 $hookId", expectedDesc, autoHooksById[hookId])
            assertEquals("DYNAMIC missing/mismatch for v3.0 $hookId", expectedDesc, dynamicHooksById[hookId])
        }

        println(
            "[BaiduRealDexParityTest] AUTO hooks=${autoHooksById.size}, traces=${autoSession.resolutionTraces.size} | " +
                "DYNAMIC hooks=${dynamicHooksById.size}, traces=${dynamicSession.resolutionTraces.size} | " +
                "CACHE hooks=${cacheHooksById.size}, traces=${cacheSession.resolutionTraces.size}"
        )
    }

    @Test
    fun hookCallbacks_returnNullForVoid_attributeStrictClipDynamically_andRespectBaiduManaged() {
        val cfg = allEnabledBaiduConfig()
        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.AUTO,
            config = cfg
        )
        session.installPack(BaiduHookPack)

        // 1. Void method baidu.clip.token_upload_ch0 returns null when blocking
        val ch0Hook = session.xposed.recordedHooks.first { it.id == "baidu.clip.token_upload_ch0" }
        val ch0Result = ch0Hook.hooker.intercept(
            object : io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable(): java.lang.reflect.Executable = ch0Hook.executable
                override fun getThisObject(): Any? = null
                override fun getArgs(): List<Any?> = emptyList()
                override fun getArg(index: Int): Any? = throw IndexOutOfBoundsException()
                override fun proceed(): Any? = "unexpected-proceed"
                override fun proceed(args: Array<out Any?>): Any? = "unexpected-proceed"
                override fun proceedWith(thisObject: Any): Any? = proceed()
                override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed(args)
            }
        )
        assertEquals(null, ch0Result)

        // 2. baidu.clipboard.reader_u attributes blocks to strict_block_readers when enabled
        val readerHook = session.xposed.recordedHooks.first { it.id == "baidu.clipboard.reader_u" }
        val strictBefore = session.registry.getReport(HookRuleCatalog.RULE_BAIDU_CLIP_STRICT_BLOCK_READERS)?.blockedCount ?: 0L
        val readerRes = readerHook.hooker.intercept(
            object : io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable(): java.lang.reflect.Executable = readerHook.executable
                override fun getThisObject(): Any? = null
                override fun getArgs(): List<Any?> = listOf(null)
                override fun getArg(index: Int): Any? = null
                override fun proceed(): Any? = "proceed"
                override fun proceed(args: Array<out Any?>): Any? = "proceed"
                override fun proceedWith(thisObject: Any): Any? = proceed()
                override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed(args)
            }
        )
        assertEquals("", readerRes)
        val strictAfter = session.registry.getReport(HookRuleCatalog.RULE_BAIDU_CLIP_STRICT_BLOCK_READERS)?.blockedCount ?: 0L
        assertEquals(strictBefore + 1L, strictAfter)

        // 3. When baiduManaged=false and allowCloudInput=false, voice_trace_upload proceeds
        val unmanagedPrefs = InMemorySharedPreferences()
        PrivacyConfig(
            baiduManaged = false,
            allowCloudInput = false,
            updatedAtMs = ConfigBridge.current.updatedAtMs + 2L
        ).toSharedPreferences(unmanagedPrefs, commitSync = true)
        ConfigBridge.detach()
        ConfigBridge.attachRemote(
            RecordingXposedInterface(
                remotePrefsMap = mapOf(ConfigContract.REMOTE_PREFS_NAME to unmanagedPrefs)
            )
        )
        val voiceHook = session.xposed.recordedHooks.first { it.id == "baidu.telemetry.voice_trace_upload" }
        val voiceRes = voiceHook.hooker.intercept(
            object : io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable(): java.lang.reflect.Executable = voiceHook.executable
                override fun getThisObject(): Any? = null
                override fun getArgs(): List<Any?> = listOf(null)
                override fun getArg(index: Int): Any? = null
                override fun proceed(): Any? = "proceed-unmanaged"
                override fun proceed(args: Array<out Any?>): Any? = "proceed-unmanaged"
                override fun proceedWith(thisObject: Any): Any? = proceed()
                override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed(args)
            }
        )
        assertEquals("proceed-unmanaged", voiceRes)
    }

    @Test
    fun defaultConfig_skipsInstallGatedRules_withoutResolvingOrHooking() {
        val defaultCfg = PrivacyConfig(baiduManaged = true)
        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = defaultCfg,
            prefs = InMemorySharedPreferences()
        )
        val result = session.installPack(BaiduHookPack)
        val reports = result.reports.associateBy { it.ruleId }

        val installGatedRules = listOf(
            HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS,
            HookRuleCatalog.RULE_BAIDU_NET_IPTCORE_URL_RESOURCE,
            HookRuleCatalog.RULE_BAIDU_NET_OCR_AUTH_REQUEST,
            HookRuleCatalog.RULE_BAIDU_CLOUD_SPEECH_ENGINE_STRICT,
            HookRuleCatalog.RULE_BAIDU_NET_TALOS_AND_SECONDARY_SDKS
        )
        for (ruleId in installGatedRules) {
            assertEquals("Expected $ruleId SKIPPED by default", HookMatchStatus.SKIPPED, reports[ruleId]?.status)
        }

        val forbiddenHookPrefixes = listOf(
            "baidu.httpdns.",
            "baidu.iptcore.request_url_resource",
            "baidu.ocr.",
            "baidu.speech.",
            "baidu.talos.",
            "baidu.webkit.",
            "baidu.simeji.",
            "baidu.cyber."
        )
        assertTrue(
            "Disabled install-gated rules must not register hooks",
            session.xposed.recordedHooks.none { hook ->
                val id = hook.id.orEmpty()
                forbiddenHookPrefixes.any { id.startsWith(it) }
            }
        )
        val forbiddenTracePrefixes = listOf(
            "baidu:http_dns_cache",
            "baidu:httpdns_",
            "baidu:iptcore_request_url_resource",
            "baidu:ocr_",
            "baidu:bds_",
            "baidu:talos_param_array",
            "baidu:talos_bridge_manager",
            "baidu:talos_call_js_function",
            "baidu:session_monitor_engine",
            "baidu:webkit_",
            "baidu:simeji_",
            "baidu:cyber"
        )
        val unexpectedTraces = session.resolutionTraces.filter { trace ->
            forbiddenTracePrefixes.any { trace.cacheKey.startsWith(it) }
        }
        assertTrue(
            "Disabled install-gated rules must not resolve symbols, found: ${unexpectedTraces.map { it.cacheKey }}",
            unexpectedTraces.isEmpty()
        )
    }

    private fun buildHookDescriptorMap(
        hooks: List<Pair<String?, java.lang.reflect.Executable>>,
        label: String
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for ((id, exec) in hooks) {
            val key = requireNotNull(id) { "[$label] Null hookId for $exec" }
            val desc = SymbolResolver.executableToDescriptor(exec)
            val prev = map.put(key, desc)
            assertTrue("[$label] Duplicate hookId recorded: $key (prev=$prev, new=$desc)", prev == null)
        }
        return map
    }

    private fun assertAllTracesAndHooksBackedByRealDex(
        session: RealApkDexSimulationHarness.SimulationSession,
        label: String
    ) {
        for (trace in session.resolutionTraces) {
            assertTrue("[$label] Blank descriptor for ${trace.kind}:${trace.cacheKey}", trace.descriptor.isNotBlank())
            val exists = when (trace.kind) {
                "class" -> session.database.classesByDescriptor.containsKey(trace.descriptor)
                "executable" -> session.database.methodsByDescriptor.containsKey(trace.descriptor)
                "field" -> session.database.fieldsByDescriptor.containsKey(trace.descriptor)
                else -> false
            }
            assertTrue(
                "[$label] Trace ${trace.kind}:${trace.cacheKey} -> ${trace.descriptor} missing in real DEX database",
                exists
            )
        }
        for (hook in session.xposed.recordedHooks) {
            val desc = SymbolResolver.executableToDescriptor(hook.executable)
            assertTrue(
                "[$label] Hook ${hook.id} -> $desc missing in real DEX methodsByDescriptor",
                session.database.methodsByDescriptor.containsKey(desc)
            )
        }
    }
}
