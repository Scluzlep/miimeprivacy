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
import com.scluzlep.miimeprivacy.xposed.overlay.KeyboardQuickPillHook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class XiaoAiRealDexParityTest {

    @Before
    fun requireRealXiaoAiApk() {
        val apk = RealApkDexSimulationHarness.apkFileFor(ImeTarget.XIAOAI)
        Assume.assumeTrue(
            "Skipping XiaoAi real-APK parity test: missing ${apk.absolutePath}",
            apk.isFile
        )
    }

    @After
    fun restoreConfigBridgeDefaults() {
        val resetPrefs = InMemorySharedPreferences()
        PrivacyConfig(updatedAtMs = ConfigBridge.current.updatedAtMs + 1L)
            .toSharedPreferences(resetPrefs, commitSync = true)
        val xposed = RecordingXposedInterface(
            remotePrefsMap = mapOf(ConfigContract.REMOTE_PREFS_NAME to resetPrefs)
        )
        ConfigBridge.detach()
        ConfigBridge.attachRemote(xposed)
        ConfigBridge.detach()
    }

    private fun allEnabledXiaoAiConfig(): PrivacyConfig {
        var cfg = PrivacyConfig(xiaoaiManaged = true)
        for (spec in HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)) {
            if (!spec.isOverlayPill) {
                cfg = cfg.withRuleOverride(spec.ruleId, true)
            }
        }
        return cfg
    }

    @Test
    fun allFourGatesPassWithZeroFallbackAndExactDescriptorParityAcrossAll20Rules() {
        val allEnabledConfig = allEnabledXiaoAiConfig()
        val expectedRuleIds = HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }
            .toSet()
        assertEquals(20, expectedRuleIds.size)

        // Gate 1: AUTO mode (100% FAST_PATH, 0 MISS, all 20 ACTIVE)
        val autoSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.AUTO,
            config = allEnabledConfig
        )
        val autoResult = autoSession.installPack(XiaoAiHookPack)
        assertEquals(expectedRuleIds, autoResult.reports.map { it.ruleId }.toSet())
        val autoNonActive = autoResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertTrue(
            "Gate 1 AUTO non-ACTIVE (${autoNonActive.size}): ${autoNonActive.joinToString { "${it.ruleId}=${it.status}(${it.detail})" }}",
            autoNonActive.isEmpty()
        )
        assertTrue("Gate 1 AUTO expected non-empty resolutionTraces", autoSession.resolutionTraces.isNotEmpty())
        val autoNonFastPath = autoSession.resolutionTraces.filter { it.source != ResolveSource.FAST_PATH }
        assertTrue(
            "Gate 1 AUTO non-FAST_PATH (${autoNonFastPath.size}): ${autoNonFastPath.joinToString { "${it.kind}:${it.cacheKey}=${it.source}" }}",
            autoNonFastPath.isEmpty()
        )

        // Gate 2: DYNAMIC mode, cold prefs (100% DEXKIT, 0 FAST_PATH, 0 STRUCTURAL_FALLBACK, 0 MISS, all 20 ACTIVE)
        val coldPrefs = InMemorySharedPreferences()
        val dynamicSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allEnabledConfig,
            prefs = coldPrefs
        )
        val dynamicResult = dynamicSession.installPack(XiaoAiHookPack)
        assertEquals(expectedRuleIds, dynamicResult.reports.map { it.ruleId }.toSet())
        val dynamicNonActive = dynamicResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertTrue(
            "Gate 2 DYNAMIC non-ACTIVE (${dynamicNonActive.size}): ${dynamicNonActive.joinToString { "${it.ruleId}=${it.status}(${it.detail})" }}",
            dynamicNonActive.isEmpty()
        )
        val dynamicNonDexKit = dynamicSession.resolutionTraces.filter { it.source != ResolveSource.DEXKIT }
        assertTrue(
            "Gate 2 DYNAMIC non-DEXKIT (${dynamicNonDexKit.size}): ${dynamicNonDexKit.joinToString { "${it.kind}:${it.cacheKey}=${it.source}(${it.descriptor})" }}",
            dynamicNonDexKit.isEmpty()
        )

        // Gate 3: 1-to-1 Descriptor Parity (hookId -> descriptor AND (kind, cacheKey) -> descriptor)
        assertAllTracesAndHooksBackedByRealDex(autoSession, "Gate 1 AUTO")
        assertAllTracesAndHooksBackedByRealDex(dynamicSession, "Gate 2 DYNAMIC")

        val autoHookMap = buildHookDescriptorMap(
            autoSession.xposed.recordedHooks.map { it.id to it.executable },
            "Gate 1 AUTO"
        )
        val dynamicHookMap = buildHookDescriptorMap(
            dynamicSession.xposed.recordedHooks.map { it.id to it.executable },
            "Gate 2 DYNAMIC"
        )
        assertEquals(
            "Gate 3 hookId -> descriptor count mismatch (AUTO=${autoSession.xposed.recordedHooks.size}, DYNAMIC=${dynamicSession.xposed.recordedHooks.size})",
            autoSession.xposed.recordedHooks.size,
            dynamicSession.xposed.recordedHooks.size
        )
        assertEquals("Gate 3 hookId -> descriptor mismatch", autoHookMap, dynamicHookMap)

        val autoTraceMap = autoSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        val dynamicTraceMap = dynamicSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        assertEquals(
            "Gate 3 trace count mismatch (AUTO=${autoSession.resolutionTraces.size}, DYNAMIC=${dynamicSession.resolutionTraces.size})",
            autoSession.resolutionTraces.size,
            dynamicSession.resolutionTraces.size
        )
        assertEquals("Gate 3 (kind, cacheKey) -> descriptor mismatch", autoTraceMap, dynamicTraceMap)

        val expectedV3XiaoAiHooks = mapOf(
            "unlock_degrade_lambda_106" to "Lcom/mi/ime/MiInputMethodService;->initializeManagers\$lambda\$106(Lcom/mi/ime/MiInputMethodService;)Lt7/e;",
            "phonenum_cf_certifications" to "Lcf/c;->c(Landroid/content/Context;Ljava/lang/String;Lcom/xiaomi/phonenum/procedure/AccountPhoneNumberSourceFlag;)[Lcom/xiaomi/phonenum/data/AccountCertification;",
            "phonenum_cf_sim_infos" to "Lcf/c;->a(Landroid/content/Context;[Ljava/lang/String;)[Lcom/xiaomi/passport/sim/SIMInfo;",
            "onetrack_device_util_android_id" to "Lcom/xiaomi/onetrack/util/DeviceUtil;->k(Landroid/content/Context;)Ljava/lang/String;",
            "onetrack_device_util_gaid" to "Lcom/xiaomi/onetrack/util/DeviceUtil;->e(Landroid/content/Context;)Ljava/lang/String;",
            "onetrack_config_pull_a" to "Lcom/xiaomi/onetrack/b/a;->b(Ljava/util/List;)V",
            "onetrack_config_updater_g" to "Lcom/xiaomi/onetrack/b/g;->f()V",
            "onetrack_claw_updater_e" to "Lcom/xiaomi/onetrack/b/e;->a()V",
            "onetrack_http_post_bytes" to "Lcom/xiaomi/onetrack/g/b;->a(Ljava/lang/String;[B)Ljava/lang/String;",
            "onetrack_http_req_map" to "Lcom/xiaomi/onetrack/g/b;->a(Ljava/lang/String;Ljava/lang/String;Ljava/util/Map;Z)Ljava/lang/String;",
            "onetrack_http_upload_file" to "Lcom/xiaomi/onetrack/g/b;->a(Ljava/lang/String;Ljava/util/Map;Ljava/io/File;)Ljava/lang/String;",
            "crash_onetrack_ex_install" to "Lcom/xiaomi/onetrack/api/as;->a()V",
            "crash_onetrack_ex_handle" to "Lcom/xiaomi/onetrack/api/as;->a(Ljava/lang/Thread;Ljava/lang/Throwable;)V",
            "ime_smart_reply_top_activity" to "Lcom/mi/ime/MiInputMethodService;->smartReplyCoordinatorLazy\$lambda\$29\$lambda\$28(Lcom/mi/ime/MiInputMethodService;)Ljava/lang/String;",
            "hotword_client_paged_f" to "Lca/w;->f(IILjava/lang/String;Led/c;)Ljava/lang/Object;",
            "smartengine_x1_http_post" to "Lcom/iflytek/inputmethod/smartengine/x1;->httpPost(Ljava/lang/String;[BII)I",
            "smartengine_common_nativeSetUid" to "Lcom/iflytek/inputmethod/service/smart/engine/XFInputCommonInfo;->nativeSetUid(Ljava/lang/String;)V",
            "smartengine_common_nativeSetOsid" to "Lcom/iflytek/inputmethod/service/smart/engine/XFInputCommonInfo;->nativeSetOsid(Ljava/lang/String;)V",
            "smartengine_common_nativeSetPinyinVoiceCloudUrl" to "Lcom/iflytek/inputmethod/service/smart/engine/XFInputCommonInfo;->nativeSetPinyinVoiceCloudUrl(Ljava/lang/String;)V",
            "smartengine_common_nativeSetSentenceAssociateCloudUrl" to "Lcom/iflytek/inputmethod/service/smart/engine/XFInputCommonInfo;->nativeSetSentenceAssociateCloudUrl(Ljava/lang/String;)V",
            "smartengine_n2_cursor_pretext" to "Lcom/iflytek/inputmethod/smartengine/n2;->getCursorPretext(I)Ljava/lang/String;",
            "smartengine_n2_cloud_contact_text" to "Lcom/iflytek/inputmethod/smartengine/n2;->getCloudContextContactText()Ljava/lang/String;",
            "smartengine_iptlog_info" to "Lcom/iflytek/inputmethod/service/smart/engine/XFIptLogMgr;->nativeLogInfo([C[CZ)[I",
            "smartengine_iptlog_extend_choose" to "Lcom/iflytek/inputmethod/service/smart/engine/XFIptLogMgr;->nativeIptlog_ExtendChoose(ILjava/lang/String;Ljava/lang/String;IZI)I",
            "smartengine_d1_collect_stat_log" to "Lcom/iflytek/inputmethod/smartengine/d1;->collectStatLogFromNative(Ljava/lang/String;I)V"
        )
        for ((hookId, expectedDesc) in expectedV3XiaoAiHooks) {
            assertEquals("AUTO missing/mismatch for v3.0 $hookId", expectedDesc, autoHookMap[hookId])
            assertEquals("DYNAMIC missing/mismatch for v3.0 $hookId", expectedDesc, dynamicHookMap[hookId])
        }
        assertEquals(
            "Lca/h;->a:Lca/h;",
            autoTraceMap["field" to "xiaoai.hotword_paged_empty_field"]
        )
        assertEquals(
            "Lca/h;->a:Lca/h;",
            dynamicTraceMap["field" to "xiaoai.hotword_paged_empty_field"]
        )

        // Gate 4: Warm CACHE (re-running DYNAMIC on warmed prefs -> 100% CACHE, 0 DEXKIT, 0 MISS, identical hooks)
        val warmSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allEnabledConfig,
            prefs = coldPrefs
        )
        val warmResult = warmSession.installPack(XiaoAiHookPack)
        assertEquals(expectedRuleIds, warmResult.reports.map { it.ruleId }.toSet())
        val warmNonActive = warmResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertTrue(
            "Gate 4 CACHE non-ACTIVE (${warmNonActive.size}): ${warmNonActive.joinToString { "${it.ruleId}=${it.status}(${it.detail})" }}",
            warmNonActive.isEmpty()
        )
        val warmNonCache = warmSession.resolutionTraces.filter { it.source != ResolveSource.CACHE }
        assertTrue(
            "Gate 4 CACHE non-CACHE (${warmNonCache.size}): ${warmNonCache.joinToString { "${it.kind}:${it.cacheKey}=${it.source}" }}",
            warmNonCache.isEmpty()
        )
        assertAllTracesAndHooksBackedByRealDex(warmSession, "Gate 4 CACHE")
        val warmHookMap = buildHookDescriptorMap(
            warmSession.xposed.recordedHooks.map { it.id to it.executable },
            "Gate 4 CACHE"
        )
        assertEquals("Gate 4 CACHE hookId -> descriptor mismatch", autoHookMap, warmHookMap)
        println(
            "XiaoAiRealDexParityTest PASS: rules=${expectedRuleIds.size}, hooks=${autoHookMap.size}, traces=${autoTraceMap.size}"
        )
    }

    @Test
    fun privacyDataMasterAndMiclawHooks_doNotDoubleCountBlockedStats() {
        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.AUTO,
            config = allEnabledXiaoAiConfig()
        )
        session.installPack(XiaoAiHookPack)

        // 1. PrivacyDataMaster#get with "android_id" increments RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT by exactly 1
        val pdmHook = session.xposed.recordedHooks.first { it.id == "pdm_get_str" }
        val secBefore = session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT)?.blockedCount ?: 0L
        val pdmResult = pdmHook.hooker.intercept(
            object : io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable(): java.lang.reflect.Executable = pdmHook.executable
                override fun getThisObject(): Any? = null
                override fun getArgs(): List<Any?> = listOf(null, "android_id", null)
                override fun getArg(index: Int): Any? = getArgs()[index]
                override fun proceed(): Any? = "real-id"
                override fun proceed(args: Array<out Any?>): Any? = "real-id"
                override fun proceedWith(thisObject: Any): Any? = proceed()
                override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed(args)
            }
        )
        assertEquals("", pdmResult)
        val secAfter = session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT)?.blockedCount ?: 0L
        assertEquals(secBefore + 1L, secAfter)

        // 2. miclaw_submit_text increments RULE_XIAOAI_CLOUD_MICLAW_LLM by at most 1 per call
        val miclawHook = session.xposed.recordedHooks.first { it.id == "miclaw_submit_text" }
        val llmBefore = session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM)?.blockedCount ?: 0L
        val cursorBefore = session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP)?.blockedCount ?: 0L
        val pkgBefore = session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)?.blockedCount ?: 0L
        val screenBefore = session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)?.blockedCount ?: 0L
        miclawHook.hooker.intercept(
            object : io.github.libxposed.api.XposedInterface.Chain {
                override fun getExecutable(): java.lang.reflect.Executable = miclawHook.executable
                override fun getThisObject(): Any? = null
                override fun getArgs(): List<Any?> = listOf(
                    "[app:com.tencent.mm]\n[cursor:secret]\n[user_text:hi]",
                    "tts",
                    "feat",
                    listOf("attachment-stub")
                )
                override fun getArg(index: Int): Any? = getArgs()[index]
                override fun proceed(): Any? = "session"
                override fun proceed(args: Array<out Any?>): Any? = "session:${args[0]}:${(args[3] as List<*>).size}"
                override fun proceedWith(thisObject: Any): Any? = proceed()
                override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed(args)
            }
        )
        assertEquals(llmBefore + 1L, session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM)?.blockedCount)
        assertEquals(cursorBefore + 1L, session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP)?.blockedCount)
        assertEquals(pkgBefore + 1L, session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG)?.blockedCount)
        assertEquals(screenBefore + 1L, session.registry.getReport(HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE)?.blockedCount)
    }

    @Test
    fun defaultConfig_skipsInstallGatedSmartEngineJniRule_withoutResolvingOrHooking() {
        val defaultCfg = PrivacyConfig(xiaoaiManaged = true)
        val session = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = defaultCfg,
            prefs = InMemorySharedPreferences()
        )
        val result = session.installPack(XiaoAiHookPack)
        val reports = result.reports.associateBy { it.ruleId }

        assertEquals(
            HookMatchStatus.SKIPPED,
            reports[HookRuleCatalog.RULE_XIAOAI_NET_SMARTENGINE_JNI_STRICT]?.status
        )
        assertTrue(
            "SmartEngine JNI rule must not install hooks when disabled",
            session.xposed.recordedHooks.none { it.id.orEmpty().startsWith("smartengine_") }
        )
        assertTrue(
            "SmartEngine JNI rule must not resolve symbols when disabled",
            session.resolutionTraces.none { it.cacheKey.startsWith("xiaoai.smartengine_") }
        )
    }

    @Test
    fun keyboardQuickPillOverlay_resolvesRealImeLifecycleMethods_acrossAllRealImeApks() {
        val targets = listOf(ImeTarget.SOGOU, ImeTarget.BAIDU, ImeTarget.XIAOAI)
        val expectedOverlayHooks = setOf(
            "overlay:onStartInputView",
            "overlay:onFinishInputView",
            "overlay:onComputeInsets",
            "overlay:onWindowHidden",
            "overlay:onConfigurationChanged",
            "overlay:onDestroy"
        )
        for (imeTarget in targets) {
            val apk = RealApkDexSimulationHarness.apkFileFor(imeTarget)
            Assume.assumeTrue("Skipping KeyboardQuickPillHook check for $imeTarget: missing ${apk.absolutePath}", apk.isFile)
            val session = RealApkDexSimulationHarness.openSimulation(
                target = imeTarget,
                resolveMode = SymbolResolveMode.AUTO,
                config = PrivacyConfig()
            )
            try {
                val serviceClass = SymbolResolver.loadClassOrNull(imeTarget.imeServiceClassName, session.classLoader)
                assertTrue(
                    "Missing IME service class ${imeTarget.imeServiceClassName} in ${imeTarget.name}",
                    serviceClass != null
                )
                val serviceDexDesc = "L${imeTarget.imeServiceClassName.replace('.', '/')};"
                assertTrue(
                    "IME service $serviceDexDesc missing from real APK database for ${imeTarget.name}",
                    session.database.classesByDescriptor.containsKey(serviceDexDesc)
                )

                KeyboardQuickPillHook.install(
                    target = imeTarget,
                    context = null,
                    classLoader = session.classLoader,
                    appInfo = session.appInfo,
                    registry = session.registry
                )
                val overlayReport = session.registry.getReport(HookRuleCatalog.overlayRuleIdFor(imeTarget))
                assertEquals(HookMatchStatus.ACTIVE, overlayReport?.status)
                val hookMap = buildHookDescriptorMap(
                    session.xposed.recordedHooks.map { it.id to it.executable },
                    "Overlay(${imeTarget.name})"
                )
                assertEquals(expectedOverlayHooks, hookMap.keys)
                for (hook in session.xposed.recordedHooks) {
                    val exec = hook.executable
                    if (exec.declaringClass != android.inputmethodservice.InputMethodService::class.java) {
                        val desc = SymbolResolver.executableToDescriptor(exec)
                        assertTrue(
                            "Overlay hook ${hook.id} resolved non-DEX executable $desc for ${imeTarget.name}",
                            session.database.methodsByDescriptor.containsKey(desc)
                        )
                    }
                }
            } finally {
                KeyboardQuickPillHook.clearOnHotReload()
            }
        }
    }

    private fun buildHookDescriptorMap(
        hooks: List<Pair<String?, java.lang.reflect.Executable>>,
        label: String
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for ((id, exec) in hooks) {
            val key = requireNotNull(id) { "[$label] Hook recorded with null id for $exec" }
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
