package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.emoji.SogouDualSkinPopupHook
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.InMemorySharedPreferences
import com.scluzlep.miimeprivacy.xposed.RealApkDexSimulationHarness
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Executable

class SogouRealDexParityTest {

    private val allSogouRulesEnabledConfig: PrivacyConfig by lazy {
        var cfg = PrivacyConfig(sogouManaged = true)
        for (spec in HookRuleCatalog.rulesFor(ImeTarget.SOGOU)) {
            if (!spec.isOverlayPill) {
                cfg = cfg.withRuleOverride(spec.ruleId, true)
            }
        }
        cfg
    }

    private val expectedNonOverlayRuleIds: Set<String> by lazy {
        HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }
            .toSet()
    }

    @Before
    fun requireRealSogouApk() {
        val apk = RealApkDexSimulationHarness.apkFileFor(ImeTarget.SOGOU)
        Assume.assumeTrue("Missing real Sogou APK: ${apk.absolutePath}", apk.isFile)
    }

    @After
    fun restoreConfigBridgeAndCaches() {
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        if (RealApkDexSimulationHarness.apkFileFor(ImeTarget.SOGOU).isFile) {
            RealApkDexSimulationHarness.openSimulation(
                target = ImeTarget.SOGOU,
                resolveMode = SymbolResolveMode.AUTO,
                config = PrivacyConfig()
            )
        }
        ConfigBridge.detach()
    }

    @Test
    fun allFourGates_autoDynamicParityAndWarmCache_againstRealSogouApk() {
        val failures = mutableListOf<String>()

        // ---------------------------------------------------------------------
        // Gate 1: AUTO mode (100% FAST_PATH, all 30 rules ACTIVE, 0 MISS)
        // ---------------------------------------------------------------------
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val autoSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.AUTO,
            config = allSogouRulesEnabledConfig
        )
        val autoResult = autoSession.installPack(SogouHookPack)
        val autoReportsByRule = autoResult.reports.associateBy { it.ruleId }

        assertEquals(30, expectedNonOverlayRuleIds.size)
        assertEquals(expectedNonOverlayRuleIds, autoReportsByRule.keys)

        val autoNonFastTraces = autoSession.resolutionTraces.filter { it.source != ResolveSource.FAST_PATH }
        if (autoNonFastTraces.isNotEmpty()) {
            failures += "Gate 1 [AUTO] non-FAST_PATH traces (${autoNonFastTraces.size}):\n" +
                autoNonFastTraces.joinToString("\n") { "  ${it.kind} ${it.cacheKey} -> ${it.source} (${it.descriptor})" }
        }
        assertTrue("Gate 1 [AUTO] resolutionTraces should not be empty", autoSession.resolutionTraces.isNotEmpty())

        val autoBadRules = autoResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:") ||
                it.source.startsWith("FAST_PATH,")
        }
        if (autoBadRules.isNotEmpty()) {
            failures += "Gate 1 [AUTO] non-ACTIVE rules (${autoBadRules.size}):\n" +
                autoBadRules.joinToString("\n") { "  ${it.ruleId}: status=${it.status} source=${it.source} detail=${it.detail}" }
        }

        // ---------------------------------------------------------------------
        // Gate 2: DYNAMIC mode on cold prefs (100% DEXKIT, 0 FAST_PATH, 0 STRUCTURAL_FALLBACK, 0 MISS)
        // ---------------------------------------------------------------------
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val dynamicPrefs = InMemorySharedPreferences()
        val dynamicSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allSogouRulesEnabledConfig,
            prefs = dynamicPrefs
        )
        val dynamicResult = dynamicSession.installPack(SogouHookPack)
        val dynamicReportsByRule = dynamicResult.reports.associateBy { it.ruleId }

        assertEquals(expectedNonOverlayRuleIds, dynamicReportsByRule.keys)

        val dynamicNonDexKitTraces = dynamicSession.resolutionTraces.filter { it.source != ResolveSource.DEXKIT }
        if (dynamicNonDexKitTraces.isNotEmpty()) {
            failures += "Gate 2 [DYNAMIC] non-DEXKIT traces (${dynamicNonDexKitTraces.size}):\n" +
                dynamicNonDexKitTraces.joinToString("\n") { "  ${it.kind} ${it.cacheKey} -> ${it.source} (${it.descriptor})" }
        }
        assertTrue("Gate 2 [DYNAMIC] resolutionTraces should not be empty", dynamicSession.resolutionTraces.isNotEmpty())

        val dynamicBadRules = dynamicResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("FAST_PATH") ||
                it.source.contains("STRUCTURAL_FALLBACK") ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:")
        }
        if (dynamicBadRules.isNotEmpty()) {
            failures += "Gate 2 [DYNAMIC] non-ACTIVE/non-DEXKIT rules (${dynamicBadRules.size}):\n" +
                dynamicBadRules.joinToString("\n") { "  ${it.ruleId}: status=${it.status} source=${it.source} detail=${it.detail}" }
        }

        // ---------------------------------------------------------------------
        // Gate 3: 1-to-1 Descriptor Parity (AUTO vs DYNAMIC)
        // ---------------------------------------------------------------------
        assertAllTracesAndHooksBackedByRealDex(autoSession, "Gate 1 AUTO", failures)
        assertAllTracesAndHooksBackedByRealDex(dynamicSession, "Gate 2 DYNAMIC", failures)
        val autoHookMap = buildHookDescriptorMap(autoSession.xposed.recordedHooks.map { it.id to it.executable })
        val dynamicHookMap = buildHookDescriptorMap(dynamicSession.xposed.recordedHooks.map { it.id to it.executable })
        if (autoHookMap != dynamicHookMap) {
            val missingInDyn = autoHookMap.keys - dynamicHookMap.keys
            val extraInDyn = dynamicHookMap.keys - autoHookMap.keys
            val diffValues = autoHookMap.keys.intersect(dynamicHookMap.keys)
                .filter { autoHookMap[it] != dynamicHookMap[it] }
            failures += buildString {
                appendLine("Gate 3 [PARITY] recordedHooks mismatch (auto=${autoHookMap.size}, dynamic=${dynamicHookMap.size}):")
                if (missingInDyn.isNotEmpty()) appendLine("  missingInDynamic(${missingInDyn.size}): $missingInDyn")
                if (extraInDyn.isNotEmpty()) appendLine("  extraInDynamic(${extraInDyn.size}): $extraInDyn")
                diffValues.forEach { k ->
                    appendLine("  diff[$k]: auto=${autoHookMap[k]} vs dynamic=${dynamicHookMap[k]}")
                }
            }.trimEnd()
        }

        val autoTraceMap = autoSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        val dynamicTraceMap = dynamicSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        if (autoTraceMap != dynamicTraceMap) {
            val allKeys = autoTraceMap.keys + dynamicTraceMap.keys
            val diffs = allKeys.filter { autoTraceMap[it] != dynamicTraceMap[it] }
            failures += "Gate 3 [PARITY] trace mismatches (${diffs.size}):\n" +
                diffs.joinToString("\n") { k -> "  $k: auto=${autoTraceMap[k]} vs dynamic=${dynamicTraceMap[k]}" }
        }

        // ---------------------------------------------------------------------
        // Gate 4: Warm CACHE (DYNAMIC re-run on warmed prefs -> 100% CACHE, 0 DEXKIT, 0 MISS)
        // ---------------------------------------------------------------------
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val warmSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allSogouRulesEnabledConfig,
            prefs = dynamicPrefs
        )
        val warmResult = warmSession.installPack(SogouHookPack)

        val warmNonCacheTraces = warmSession.resolutionTraces.filter { it.source != ResolveSource.CACHE }
        if (warmNonCacheTraces.isNotEmpty()) {
            failures += "Gate 4 [WARM CACHE] non-CACHE traces (${warmNonCacheTraces.size}):\n" +
                warmNonCacheTraces.joinToString("\n") { "  ${it.kind} ${it.cacheKey} -> ${it.source} (${it.descriptor})" }
        }

        val warmBadRules = warmResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("DEXKIT") ||
                it.source.contains("FAST_PATH") ||
                it.source.contains("STRUCTURAL_FALLBACK") ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:")
        }
        if (warmBadRules.isNotEmpty()) {
            failures += "Gate 4 [WARM CACHE] non-ACTIVE/non-CACHE rules (${warmBadRules.size}):\n" +
                warmBadRules.joinToString("\n") { "  ${it.ruleId}: status=${it.status} source=${it.source} detail=${it.detail}" }
        }

        assertAllTracesAndHooksBackedByRealDex(warmSession, "Gate 4 WARM CACHE", failures)
        val warmHookMap = buildHookDescriptorMap(warmSession.xposed.recordedHooks.map { it.id to it.executable })
        if (autoHookMap != warmHookMap) {
            failures += "Gate 4 [WARM CACHE] recordedHooks mismatch vs AUTO (auto=${autoHookMap.size}, warm=${warmHookMap.size})"
        }

        val expectedV3SogouExactHooks = mapOf(
            "bu_util_b_e" to "Lcom/sogou/bu/basic/util/b;->e(Ljava/util/Map;)V",
            "biz_ad_b_d" to "Lcom/sohu/inputmethod/businessadvisement/b;->d(Ljava/lang/String;Ljava/lang/String;)V",
            "biz_ad_d_c" to "Lcom/sohu/inputmethod/businessadvisement/d;->c(Ljava/lang/String;Ljava/lang/String;)V",
            "j0_b" to "Lcom/sohu/inputmethod/sogou/j0;->b(Ljava/lang/String;)V",
            "net_proc_handler_k0" to "Lcom/sohu/inputmethod/settings/NetworkProcessHandler;->k0(Z)V",
            "hj_d_b" to "Lcom/sogou/hj/d;->b(Ljava/util/Map;)V",
            "internet_conn_Y0" to "Lcom/sohu/inputmethod/internet/InternetConnection;->Y0()I",
            "raft_atta_doPostBatchReport" to "Lcom/tencent/raft/measure/report/ATTAReporter;->doPostBatchReport(Lorg/json/JSONArray;)V",
            "doutu_http_sendPingback" to "Lcom/sdk/doutu/request/HttpClient;->sendPingback(Ljava/lang/String;Ljava/lang/String;)V",
            "sdk_pb_base_b_d" to "Lcom/sdk/sogou/pingback/base/b;->d(Landroid/content/Context;Ljava/lang/String;)V",
            "sogou_js_getQ36" to "Lbase/sogou/mobile/hotwordsbase/utils/SogouJSInterface;->getQ36()Ljava/lang/String;",
            "bigdata_c_E" to "Lcom/sohu/inputmethod/sogou/bigdata/c;->E(I)V",
            "hotwords_utils_a_B" to "Lbase/sogou/mobile/hotwordsbase/utils/a;->B(Landroid/content/Context;)Ljava/lang/String;",
            "hotwords_utils_a_A" to "Lbase/sogou/mobile/hotwordsbase/utils/a;->A(Landroid/content/Context;)Ljava/lang/String;",
            "ac_checker_getType" to "Lcom/lib/ac/ACCheckerInterface;->getType()I",
            "infomgr_m" to "Lcom/sogou/bu/basic/util/InfoManager;->m()Ljava/lang/String;",
            "infomgr_f" to "Lcom/sogou/bu/basic/util/InfoManager;->f()Ljava/lang/String;",
            "devinfo_svc_getLocation" to "Lcom/sohu/inputmethod/settings/deviceinfo/DeviceInfoServiceImpl;->getLocation(Landroid/app/Activity;)Ljava/lang/String;",
            "devinfo_b_p" to "Lcom/sohu/inputmethod/settings/deviceinfo/b;->p(Landroid/app/Activity;)Ljava/lang/String;",
            "hotwords_commonlib_getCellLAC" to "Lbase/sogou/mobile/hotwordsbase/utils/CommonLib;->getCellLAC(Landroid/content/Context;)Ljava/lang/String;"
        )
        for ((hookId, expectedDesc) in expectedV3SogouExactHooks) {
            if (autoHookMap[hookId] != expectedDesc) {
                failures += "AUTO missing/mismatch v3.0 $hookId: expected=$expectedDesc actual=${autoHookMap[hookId]}"
            }
            if (dynamicHookMap[hookId] != expectedDesc) {
                failures += "DYNAMIC missing/mismatch v3.0 $hookId: expected=$expectedDesc actual=${dynamicHookMap[hookId]}"
            }
        }
        val expectedV3SogouPrefixDescriptors = listOf(
            "alive_install_c#" to "Lcom/sohu/inputmethod/settings/internet/alive/e;->c()V",
            "alive_install_d#" to "Lcom/sohu/inputmethod/settings/internet/alive/e;->d()V",
            "device_i_h_imei#" to "Lcom/sogou/lib/device/i;->h(Lcom/sogou/lib/device/iinterface/c;)[Ljava/lang/String;",
            "device_support_b_b#" to "Lcom/sogou/lib/device/support/b;->b(Lcom/sogou/lib/device/iinterface/b;)Ljava/lang/String;",
            "weibo_sso_riseWind#" to "Lcom/weibo/ssosdk/WeiboSsoSdk;->riseWind(",
            "weibo_sso_j#" to "Lcom/weibo/ssosdk/WeiboSsoSdk;->j(Lcom/weibo/ssosdk/c;)Z",
            "weibo_sso_l#" to "Lcom/weibo/ssosdk/WeiboSsoSdk;->l(Ljava/lang/String;I)V",
            "internet_t_onWork#" to "Lcom/sohu/inputmethod/settings/internet/t;->onWork(Lcom/sogou/threadpool/k;)V",
            "internet_d_onWork#" to "Lcom/sohu/inputmethod/settings/internet/d;->onWork(Lcom/sogou/threadpool/k;)V",
            "internet_g_onWork#" to "Lcom/sohu/inputmethod/settings/internet/g;->onWork(Lcom/sogou/threadpool/k;)V",
            "internet_n_onWork#" to "Lcom/sohu/inputmethod/settings/internet/n;->onWork(Lcom/sogou/threadpool/k;)V",
            "upgrade_h_onWork#" to "Lcom/sohu/inputmethod/settings/internet/upgrade/h;->onWork(Lcom/sogou/threadpool/k;)V",
            "voiceinput_pb_a_onWork#" to "Lcom/sohu/inputmethod/voiceinput/pingback/a;->onWork(Lcom/sogou/threadpool/k;)V",
            "hotwords_http_a_d#" to "Lbase/sogou/mobile/hotwordsbase/http/a;->d(",
            "hotwords_http_a_j#" to "Lbase/sogou/mobile/hotwordsbase/http/a;->j(",
            "hj_requestwork_s#" to "Lcom/sogou/hj/RequestWork;->s(Ljava/lang/String;Lcom/sogou/http/okhttp/q;)V",
            "okhttp_g_a#" to "Lcom/sohu/inputmethod/internet/okhttp/g;->a(Ljava/lang/String;Lcom/sogou/http/okhttp/q;)V",
            "device_m_atta_b#" to "Lcom/sogou/lib/device/m;->b(Lcom/tencent/raft/measure/report/ATTAReporter;Lorg/json/JSONArray;)V",
            "device_m_atta_k#" to "Lcom/sogou/lib/device/m;->k()Z",
            "device_m_atta_m#" to "Lcom/sogou/lib/device/m;->m(Lcom/tencent/rfix/lib/atta/RFixATTAReporter;Ljava/util/Map;)Z",
            "sdk_pb_a_i#" to "Lcom/sdk/sogou/pingback/a;->i(Lcom/sdk/sogou/pingback/base/b;)V",
            "theme_net_e_t#" to "Lcom/sogou/theme/network/e;->t(",
            "theme_net_e_v#" to "Lcom/sogou/theme/network/e;->v(",
            "theme_net_e_w#" to "Lcom/sogou/theme/network/e;->w(",
            "beacon_e_f#" to "Lcom/sogou/inputmethod/beacon/e;->f()Ljava/lang/String;",
            "message_anr_a#" to "Lcom/sohu/inputmethod/messageAnr/net/a;->a(",
            "fluency_native_refreshHookFromSettings#" to "Lcom/sogou/lib/performance/fluency/FluencyNativeInterface;->refreshHookFromSettings()V",
            "fluency_native_hookBinderNative#" to "Lcom/sogou/lib/performance/fluency/FluencyNativeInterface;->hookBinderNative()Z",
            "fluency_native_hookIoNative#" to "Lcom/sogou/lib/performance/fluency/FluencyNativeInterface;->hookIoNative()Z",
            "fluency_native_hookAssetManagerNative#" to "Lcom/sogou/lib/performance/fluency/FluencyNativeInterface;->hookAssetManagerNative()Z",
            "lbs_notify_b_onWork#" to "Lcom/sohu/inputmethod/settings/internet/lbsnetnotify/b;->onWork(Lcom/sogou/threadpool/k;)V",
            "sogou_native_engine#getCloudPingbackInfo" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->getCloudPingbackInfo()Ljava/lang/String;",
            "sogou_native_engine#getCorrectPingbackInfo" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->getCorrectPingbackInfo()Ljava/lang/String;",
            "sogou_native_engine#getIndividualPingback" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->getIndividualPingback()Ljava/lang/String;",
            "sogou_native_engine#getInputPingbackInfo" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->getInputPingbackInfo()Ljava/lang/String;",
            "sogou_native_engine#getCloudWhiteDogInfo" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->getCloudWhiteDogInfo(I[C)I",
            "sogou_native_engine#checkUploadUsrDict" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->checkUploadUsrDict([BZZZ[BI)I",
            "sogou_native_engine#pingbackAction" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->pingbackAction(JILjava/lang/String;Ljava/lang/String;[CIIZZZZZIZI[S)V",
            "sogou_native_engine#pingbackCloudAction" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->pingbackCloudAction(JII[I)V",
            "sogou_native_engine#setClientPackageName" to "Lcom/sohu/inputmethod/engine/IMENativeInterface;->setClientPackageName(Ljava/lang/String;)V",
            "sogou_native_sec#postguard" to "Lsogouime/security/SecurityInterface;->postguard([B)[B",
            "sogou_native_sec#proguard" to "Lsogouime/security/SecurityInterface;->proguard([B)[B",
            "sogou_native_sec#getRule" to "Lsogouime/security/SecurityInterface;->getRule([B)[B",
            "sogou_native_sec#applyRule" to "Lsogouime/security/SecurityInterface;->applyRule([B[B)[B"
        )
        for ((prefix, descPrefix) in expectedV3SogouPrefixDescriptors) {
            if (autoHookMap.entries.none { (k, v) -> k.startsWith(prefix) && v.startsWith(descPrefix) }) {
                failures += "AUTO missing/mismatch v3.0 $prefix -> $descPrefix"
            }
            if (dynamicHookMap.entries.none { (k, v) -> k.startsWith(prefix) && v.startsWith(descPrefix) }) {
                failures += "DYNAMIC missing/mismatch v3.0 $prefix -> $descPrefix"
            }
        }

        assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
        println(
            "SogouRealDexParityTest SUMMARY: rules=${autoResult.reports.size}, " +
                "autoHooks=${autoHookMap.size}, dynamicHooks=${dynamicHookMap.size}, warmHooks=${warmHookMap.size}, " +
                "autoTraces=${autoSession.resolutionTraces.size}, dynamicTraces=${dynamicSession.resolutionTraces.size}, warmTraces=${warmSession.resolutionTraces.size}"
        )
    }

    @Test
    fun disabledVpaAndVoiceRules_doNotOverwriteHostPackageReport_andEmptyArgsAreSafe() {
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val cfg = allSogouRulesEnabledConfig
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_CLOUD_WANGZAI_VPA, false)
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_CLOUD_VOICE_PRIVACY, false)
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE, true)

        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.AUTO,
            config = cfg
        )
        val result = session.installPack(SogouHookPack)
        val reports = result.reports.associateBy { it.ruleId }

        assertEquals(HookMatchStatus.SKIPPED, reports[HookRuleCatalog.RULE_SOGOU_CLOUD_WANGZAI_VPA]?.status)
        assertEquals(HookMatchStatus.SKIPPED, reports[HookRuleCatalog.RULE_SOGOU_CLOUD_VOICE_PRIVACY]?.status)
        val hostReport = reports[HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE]
        assertEquals(HookMatchStatus.ACTIVE, hostReport?.status)
        assertTrue(
            "Expected host package report to retain CloudPayload primary signature, got: ${hostReport?.detail} / ${hostReport?.matchedMethod}",
            hostReport?.detail?.startsWith("CloudPayload ") == true
        )

        val voiceNdHook = session.xposed.recordedHooks.first { it.id!!.startsWith("voice_n_d") }
        val httpDnsLHook = session.xposed.recordedHooks.first { it.id!!.startsWith("sogou_httpdns_b_l") }
        val emptyChain = object : io.github.libxposed.api.XposedInterface.Chain {
            override fun getExecutable(): java.lang.reflect.Executable = voiceNdHook.executable
            override fun getThisObject(): Any? = null
            override fun getArgs(): List<Any?> = emptyList()
            override fun getArg(index: Int): Any? = throw IndexOutOfBoundsException("idx=$index")
            override fun proceed(): Any? = "proceed-ok"
            override fun proceed(args: Array<out Any?>): Any? = "proceed-ok"
            override fun proceedWith(thisObject: Any): Any? = proceed()
            override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed(args)
        }
        assertEquals("proceed-ok", voiceNdHook.hooker.intercept(emptyChain))
        assertEquals(emptyList<Any>(), httpDnsLHook.hooker.intercept(emptyChain))
    }

    @Test
    fun defaultConfig_skipsInstallGatedRules_withoutResolvingOrHooking() {
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val defaultCfg = PrivacyConfig(sogouManaged = true)

        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = defaultCfg,
            prefs = InMemorySharedPreferences()
        )
        val result = session.installPack(SogouHookPack)
        val reports = result.reports.associateBy { it.ruleId }

        assertEquals(HookMatchStatus.SKIPPED, reports[HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS]?.status)
        assertEquals(HookMatchStatus.SKIPPED, reports[HookRuleCatalog.RULE_SOGOU_NET_NATIVE_ENGINE_STRICT]?.status)
        assertTrue(
            "Install-gated rules must not install any hook when disabled",
            session.xposed.recordedHooks.none { hook ->
                val id = hook.id.orEmpty()
                id.startsWith("sogou_native_") || id.startsWith("sogou_httpdns")
            }
        )
        assertTrue(
            "Install-gated rules must not resolve symbols when disabled",
            session.resolutionTraces.none { trace ->
                trace.cacheKey.startsWith("sogou.engine.IMENativeInterface") ||
                    trace.cacheKey.startsWith("sogou.security.SecurityInterface") ||
                    trace.cacheKey.contains("httpdns", ignoreCase = true)
            }
        )
    }

    private fun buildHookDescriptorMap(hooks: List<Pair<String?, Executable>>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for ((id, exec) in hooks) {
            val key = id ?: error("Hook recorded with null id for $exec")
            val desc = SymbolResolver.executableToDescriptor(exec)
            val prev = map.put(key, desc)
            assertTrue("Duplicate hookId recorded: $key (prev=$prev, new=$desc)", prev == null)
        }
        return map
    }

    private fun assertAllTracesAndHooksBackedByRealDex(
        session: RealApkDexSimulationHarness.SimulationSession,
        label: String,
        failures: MutableList<String>
    ) {
        for (trace in session.resolutionTraces) {
            if (trace.descriptor.isBlank()) {
                failures += "[$label] Blank descriptor for ${trace.kind}:${trace.cacheKey}"
                continue
            }
            val exists = when (trace.kind) {
                "class" -> session.database.classesByDescriptor.containsKey(trace.descriptor)
                "executable" -> session.database.methodsByDescriptor.containsKey(trace.descriptor)
                "field" -> session.database.fieldsByDescriptor.containsKey(trace.descriptor)
                else -> false
            }
            if (!exists) {
                failures += "[$label] Trace ${trace.kind}:${trace.cacheKey} -> ${trace.descriptor} missing in real DEX database"
            }
        }
        for (hook in session.xposed.recordedHooks) {
            val desc = SymbolResolver.executableToDescriptor(hook.executable)
            if (!session.database.methodsByDescriptor.containsKey(desc)) {
                failures += "[$label] Hook ${hook.id} -> $desc missing in real DEX methodsByDescriptor"
            }
        }
    }
}
