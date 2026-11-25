package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoAiHookPackTest {

    @Test
    fun targetMatchesXiaoAiAndCoversAllTwentyOneRules() {
        assertEquals(ImeTarget.XIAOAI, XiaoAiHookPack.target)

        val allXiaoAiRules = HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)
        assertEquals(21, allXiaoAiRules.size)

        val nonOverlayIds = allXiaoAiRules
            .filterNot { it.ruleId == HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL }
            .map { it.ruleId }
            .toSet()

        assertEquals(20, nonOverlayIds.size)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLOUD_DICTPACK_SYNC in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_NET_SMARTENGINE_JNI_STRICT in nonOverlayIds)
    }

    @Test
    fun ctaGateBlocksAllFailOpenPathsButPreservesExplicitUserConsent() {
        val defaultCfg = PrivacyConfig()

        assertFalse(XiaoAiPrivacyPolicy.shouldAllowCtaAccept(defaultCfg, hasExplicitUserAccept = false))
        assertTrue(XiaoAiPrivacyPolicy.shouldAllowCtaAccept(defaultCfg, hasExplicitUserAccept = true))

        assertTrue(
            XiaoAiPrivacyPolicy.shouldAllowCtaAccept(
                defaultCfg.withRuleOverride(HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN, false),
                hasExplicitUserAccept = false
            )
        )
        assertTrue(
            XiaoAiPrivacyPolicy.shouldAllowCtaAccept(
                defaultCfg.copy(xiaoaiManaged = false),
                hasExplicitUserAccept = false
            )
        )
    }

    @Test
    fun oneTrackAndPasswordSessionRulesSupportIndependentToggles() {
        val defaultCfg = PrivacyConfig()
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockOneTrackTelemetry(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(defaultCfg, isPasswordSession = false))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockSessionStats(defaultCfg))

        // OneTrack off, Password/Session on -> ONLY password sessions blocked on OneTrack
        val passwordOnlyCfg = defaultCfg
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION, true)
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackTelemetry(passwordOnlyCfg))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(passwordOnlyCfg, isPasswordSession = false))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(passwordOnlyCfg, isPasswordSession = true))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockSessionStats(passwordOnlyCfg))

        // Both off -> nothing blocked
        val offCfg = passwordOnlyCfg
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION, false)
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(offCfg, isPasswordSession = true))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockSessionStats(offCfg))

        // XiaoAi unmanaged -> nothing blocked
        val unmanagedCfg = defaultCfg.copy(xiaoaiManaged = false)
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackTelemetry(unmanagedCfg))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(unmanagedCfg, isPasswordSession = true))
    }

    @Test
    fun cloudRulesPreserveWorkingCloudWhenEnabledAndBlockWhenCloudDisabled() {
        val defaultCfg = PrivacyConfig()
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockCloudService(defaultCfg, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM))
        assertTrue(XiaoAiPrivacyPolicy.shouldSanitizeMiclawPrompt(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockScreenCapture(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldMaskSpeechHostPackage(defaultCfg))

        // Per-IME FORCE_OFF blocks AI cloud services cleanly
        val perImeOff = defaultCfg.copy(xiaoaiCloudOverride = CloudOverride.FORCE_OFF)
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(perImeOff, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(perImeOff, HookRuleCatalog.RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(perImeOff, HookRuleCatalog.RULE_XIAOAI_CLOUD_DICTPACK_SYNC))

        // Global cloud off -> FOLLOW_GLOBAL blocks even when individual cloud rule is toggled off;
        // FORCE_ON stays working with sanitization; unmanaged XiaoAi never blocks.
        val globalCloudOff = defaultCfg.copy(allowCloudInput = false)
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(globalCloudOff, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM))

        val globalCloudOffRuleDisabled = globalCloudOff
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLOUD_DICTPACK_SYNC, false)
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(globalCloudOffRuleDisabled, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(globalCloudOffRuleDisabled, HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(globalCloudOffRuleDisabled, HookRuleCatalog.RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudService(globalCloudOffRuleDisabled, HookRuleCatalog.RULE_XIAOAI_CLOUD_DICTPACK_SYNC))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockCloudService(globalCloudOffRuleDisabled.copy(xiaoaiManaged = false), HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM))

        val forcedOnWhileGlobalOff = globalCloudOff.copy(xiaoaiCloudOverride = CloudOverride.FORCE_ON)
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockCloudService(forcedOnWhileGlobalOff, HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM))
        assertTrue(XiaoAiPrivacyPolicy.shouldSanitizeMiclawPrompt(forcedOnWhileGlobalOff))
    }

    @Test
    fun sanitizeAgentPromptStripsSensitiveSectionsWithoutManglingMultilineUserContent() {
        val rawPrompt = """
            [user_text:请把这段话润色为正式语气]
            [app:com.tencent.mm]
            [peer_text:第一行对方消息 [带方括号的引用]
            第二行敏感聊天记录]
            [has_screenshot:true]
        """.trimIndent()

        val defaultSanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, PrivacyConfig())
        assertEquals("[user_text:请把这段话润色为正式语气]", defaultSanitized)

        // Mask host only -> keeps peer_text & has_screenshot, removes app tag
        val hostOnlyCfg = PrivacyConfig()
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG, true)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE, false)
        val hostOnlySanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, hostOnlyCfg)
        assertFalse(hostOnlySanitized.contains("[app:"))
        assertTrue(hostOnlySanitized.contains("[user_text:请把这段话润色为正式语气]"))
        assertTrue(hostOnlySanitized.contains("第二行敏感聊天记录]"))
        assertTrue(hostOnlySanitized.contains("[has_screenshot:true]"))

        // Strip cursor context only -> keeps app & has_screenshot, removes multiline peer_text
        val cursorOnlyCfg = PrivacyConfig()
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP, true)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE, false)
        val cursorOnlySanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, cursorOnlyCfg)
        assertTrue(cursorOnlySanitized.contains("[app:com.tencent.mm]"))
        assertFalse(cursorOnlySanitized.contains("[peer_text:"))
        assertFalse(cursorOnlySanitized.contains("第二行敏感聊天记录"))
        assertTrue(cursorOnlySanitized.contains("[has_screenshot:true]"))

        assertEquals("帮我写一封请假邮件", XiaoAiPrivacyPolicy.sanitizeAgentPrompt("帮我写一封请假邮件", PrivacyConfig()))
        assertEquals(rawPrompt, XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, PrivacyConfig(xiaoaiManaged = false)))
    }

    @Test
    fun clipboardAndHotwordAicrPoliciesRespectV2RuleDefaultsAndOverrides() {
        val defaultCfg = PrivacyConfig()
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockBackgroundClipboardRead(defaultCfg))
        assertFalse(
            XiaoAiPrivacyPolicy.shouldBlockBackgroundClipboardRead(
                defaultCfg.withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD, false)
            )
        )

        // v2.0: xiaoai.context.hotword_aicr is critical and defaultEnabled=true
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockHotwordAicr(defaultCfg))
        assertFalse(
            XiaoAiPrivacyPolicy.shouldBlockHotwordAicr(
                defaultCfg.withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR, false)
            )
        )
        assertFalse(
            XiaoAiPrivacyPolicy.shouldBlockHotwordAicr(
                defaultCfg.copy(xiaoaiManaged = false)
            )
        )

        // Default-false rules
        assertFalse(XiaoAiPrivacyPolicy.shouldBypassUnlockDegrade(defaultCfg))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockVoiceAssistLaunch(defaultCfg))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockSystemCloudControl(defaultCfg))
        assertTrue(
            XiaoAiPrivacyPolicy.shouldBlockSystemCloudControl(
                defaultCfg.withRuleOverride(HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL, true)
            )
        )
    }

    @Test
    fun verifiedXiaoAiAnchorsAndCriticalExclusionsAreIntact() {
        assertEquals("com.mi.ime.CtaConsentActivity", XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY)
        assertEquals(1001, XiaoAiAnchors.CTA_REQUEST_CODE)
        assertEquals(1, XiaoAiAnchors.CTA_RESULT_ACCEPTED)
        assertEquals("com.xiaomi.onetrack.api.c", XiaoAiAnchors.CLASS_ONETRACK_BASE_IMP)
        assertEquals("com.xiaomi.onetrack.api.au", XiaoAiAnchors.CLASS_ONETRACK_IMP)
        assertEquals("com.xiaomi.onetrack.api.ay", XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP)
        assertEquals("com.xiaomi.onetrack.api.av", XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP)
        assertEquals("com.xiaomi.onetrack.CrashAnalysis", XiaoAiAnchors.CLASS_CRASH_ANALYSIS)
        assertEquals("com.miui.bugreport.logprovider.DumpLogProvider", XiaoAiAnchors.CLASS_DUMP_LOG_PROVIDER)
        assertEquals("com.xiaomi.passport.SecurityDeviceReporter", XiaoAiAnchors.CLASS_SECURITY_DEVICE_REPORTER)
        assertEquals("com.xiaomi.account.privacy_data.master.PrivacyDataMaster", XiaoAiAnchors.CLASS_PRIVACY_DATA_MASTER)
        assertEquals("xc.i", XiaoAiAnchors.CLASS_SENSOR_ANTIHOOK_HELPER)
        assertEquals("fb.y5", XiaoAiAnchors.CLASS_VOICE_HOTWORD_CACHE)
        assertEquals("ca.u", XiaoAiAnchors.CLASS_HOTWORD_INIT_TASK)
        assertEquals("pd.a", XiaoAiAnchors.CLASS_APP_LABEL_HELPER)
        assertEquals("i5.a0", XiaoAiAnchors.CLASS_TAIYI_APP_TRACK_RUNNABLE)
        assertEquals("lc.f", XiaoAiAnchors.CLASS_SPEECH_MANAGER)
        assertEquals("mb.k3", XiaoAiAnchors.CLASS_HOTWORD_CLOSURE)
        assertEquals("u9.d", XiaoAiAnchors.CLASS_DICTPACK_SCHEDULER)
        assertEquals("i.o0", XiaoAiAnchors.CLASS_CATALOG_HTTP_CLIENT)
        assertEquals("o8.b", XiaoAiAnchors.CLASS_CLOUD_CONTROL_FLOW)
        assertEquals("o8.c", XiaoAiAnchors.CLASS_CLOUD_CONTROL_HELPER)
        assertEquals("ub.a", XiaoAiAnchors.CLASS_SCENARIO_BINDER)
        assertEquals("t7.e", XiaoAiAnchors.CLASS_DEGRADE_LEVEL)

        val allAnchorClasses = listOf(
            XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY,
            XiaoAiAnchors.CLASS_ONETRACK_SDK,
            XiaoAiAnchors.CLASS_ONETRACK_HELPER,
            XiaoAiAnchors.CLASS_ONETRACK_BASE_IMP,
            XiaoAiAnchors.CLASS_ONETRACK_IMP,
            XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP,
            XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP,
            XiaoAiAnchors.CLASS_ONETRACK_SERVICE_CONN,
            XiaoAiAnchors.CLASS_SESSION_COLLECTOR,
            XiaoAiAnchors.CLASS_SESSION_HOLDER,
            XiaoAiAnchors.CLASS_SESSION_CACHE,
            XiaoAiAnchors.CLASS_AGENT_CONNECTION,
            XiaoAiAnchors.CLASS_VOICEASSIST_LAUNCH,
            XiaoAiAnchors.CLASS_MICLAW_MANAGER,
            XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE,
            XiaoAiAnchors.CLASS_TRANSLATE_ENGINE,
            XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER,
            XiaoAiAnchors.CLASS_TAIYI_SERVICE,
            XiaoAiAnchors.CLASS_TAIYI_MANAGER,
            XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR,
            XiaoAiAnchors.CLASS_HOTWORD_CLIENT,
            XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER
        )
        assertFalse(allAnchorClasses.contains("j9.e"))
        assertFalse(allAnchorClasses.contains("m9.a0"))
        assertFalse(allAnchorClasses.contains("j5.v"))
        assertFalse(allAnchorClasses.contains("r9.v"))
    }

    @Test
    fun oneTrackImpInitShortCircuitsBeforeProceedAndBaseGetArProvidesSafeNoOpProxy() {
        FakeOneTrackSdk.disabled = false
        val baseImp = FakeBaseOneTrackImp()
        var blockedCount = 0
        var proceedCalled = false

        // When telemetry is blocked, au#a short-circuits BEFORE proceed() so ba bindService never runs
        // AND static OneTrack.b (setDisable) is NOT mutated!
        val result = XiaoAiOneTrackGuard.handleOneTrackImpInit(
            config = PrivacyConfig(),
            proceed = {
                proceedCalled = true
                baseImp.initDelegate(useSystemImp = true)
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertNull(result)
        assertFalse("au#a must short-circuit BEFORE proceed() when OneTrack rule is enabled", proceedCalled)
        assertNull("f4218a remains null because au#a did not construct ay/ba", baseImp.delegate)
        assertFalse("OneTrack.setDisable must NOT be mutated by au#a short-circuit", FakeOneTrackSdk.disabled)
        assertEquals(1, blockedCount)

        // c#j() dynamic proxy fallback prevents NPE when callers invoke j().a(1)
        val safeDelegate = XiaoAiOneTrackGuard.ensureSafeArDelegate(
            delegateResult = baseImp.delegate,
            arInterface = FakeAr::class.java,
            config = PrivacyConfig()
        ) as FakeAr
        assertNotNull(safeDelegate)
        safeDelegate.a(1)
        safeDelegate.a("ev", "{}")
        safeDelegate.b("ev2", "{}")
        safeDelegate.a(true)

        // When RULE_XIAOAI_TELEMETRY_ONETRACK is disabled, au#a proceeds normally
        val offCfg = PrivacyConfig().withRuleOverride(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK, false)
        XiaoAiOneTrackGuard.handleOneTrackImpInit(
            config = offCfg,
            proceed = {
                proceedCalled = true
                baseImp.initDelegate(useSystemImp = false)
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertTrue(proceedCalled)
        assertNotNull(baseImp.delegate)
        assertEquals(1, blockedCount)
    }

    @Test
    fun oneTrackSystemAndLocalImpArMethodsBlockCleanlyAndConfigBridgePointGatingMatches() {
        val sysMethods = XiaoAiOneTrackGuard.findPublicArInterfaceMethods(FakeSystemImp::class.java)
        val localMethods = XiaoAiOneTrackGuard.findPublicArInterfaceMethods(FakeLocalImp::class.java)

        assertEquals(
            setOf("a(int)", "a(String,String)", "b(String,String)", "a(boolean)"),
            sysMethods.map { "${it.name}(${it.parameterTypes.joinToString(",") { t -> t.simpleName }})" }.toSet()
        )
        assertEquals(
            setOf("a(int)", "a(String,String)", "b(String,String)", "a(boolean)"),
            localMethods.map { "${it.name}(${it.parameterTypes.joinToString(",") { t -> t.simpleName }})" }.toSet()
        )

        ConfigBridge.detach()
        assertTrue(ConfigBridge.isPointEnabled(ImeTarget.XIAOAI.packageName, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK))
        assertTrue(ConfigBridge.isPointEnabled(ImeTarget.XIAOAI.packageName, HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION))
        assertFalse(ConfigBridge.isPointEnabled(ImeTarget.XIAOAI.packageName, HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS))
        assertFalse(ConfigBridge.isPointEnabled(ImeTarget.XIAOAI.packageName, HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH))
        assertFalse(ConfigBridge.isPointEnabled(ImeTarget.XIAOAI.packageName, HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL))

        val pwdOnlyCfg = PrivacyConfig()
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION, true)
        assertFalse(pwdOnlyCfg.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK))
        assertTrue(pwdOnlyCfg.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION))

        val sysImp = FakeSystemImp()
        val localImp = FakeLocalImp()
        var blockedCount = 0

        XiaoAiOneTrackGuard.handleOneTrackArMethod(
            config = pwdOnlyCfg,
            isPasswordSession = false,
            returnType = Void.TYPE,
            proceed = {
                sysImp.a("ev1", "{}")
                localImp.b("ev2", "{}")
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertEquals(1, sysImp.trackACalls)
        assertEquals(1, localImp.trackBCalls)
        assertEquals(0, blockedCount)

        XiaoAiOneTrackGuard.handleOneTrackArMethod(
            config = pwdOnlyCfg,
            isPasswordSession = true,
            returnType = Void.TYPE,
            proceed = {
                sysImp.a("ev3", "{}")
                localImp.a(2)
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertEquals(1, sysImp.trackACalls)
        assertEquals(0, localImp.lifecycleCalls)
        assertEquals(1, blockedCount)

        assertEquals(false, XiaoAiOneTrackGuard.defaultReturnValue(Boolean::class.javaPrimitiveType!!))
        assertEquals(0, XiaoAiOneTrackGuard.defaultReturnValue(Int::class.javaPrimitiveType!!))
        assertEquals(0L, XiaoAiOneTrackGuard.defaultReturnValue(Long::class.javaPrimitiveType!!))
        assertEquals(0f, XiaoAiOneTrackGuard.defaultReturnValue(Float::class.javaPrimitiveType!!))
        assertEquals(0.0, XiaoAiOneTrackGuard.defaultReturnValue(Double::class.javaPrimitiveType!!))
        assertEquals("", XiaoAiOneTrackGuard.defaultReturnValue(String::class.java))
        assertNull(XiaoAiOneTrackGuard.defaultReturnValue(Void.TYPE))
    }

    private interface FakeAr {
        fun a(i: Int)
        fun a(str: String, str2: String)
        fun a(z: Boolean)
        fun b(str: String, str2: String)
    }

    private class FakeSystemImp : FakeAr {
        @JvmField var lifecycleCalls = 0
        @JvmField var trackACalls = 0
        @JvmField var trackBCalls = 0
        @JvmField var flushCalls = 0

        override fun a(i: Int) { lifecycleCalls++ }
        override fun a(str: String, str2: String) { trackACalls++ }
        override fun a(z: Boolean) { flushCalls++ }
        override fun b(str: String, str2: String) { trackBCalls++ }

        fun a() {}

        @Suppress("unused")
        private fun a(str: String, str2: String, flag: Boolean) {}
    }

    private class FakeLocalImp : FakeAr {
        @JvmField var lifecycleCalls = 0
        @JvmField var trackACalls = 0
        @JvmField var trackBCalls = 0
        @JvmField var flushCalls = 0

        override fun a(i: Int) { lifecycleCalls++ }
        override fun a(str: String, str2: String) { trackACalls++ }
        override fun a(z: Boolean) { flushCalls++ }
        override fun b(str: String, str2: String) { trackBCalls++ }

        @Suppress("unused")
        private fun c(str: String, str2: String): Boolean = false

        @Suppress("unused")
        private fun d(str: String, str2: String): Boolean = true
    }

    private class FakeBaseOneTrackImp {
        var delegate: FakeAr? = null

        fun initDelegate(useSystemImp: Boolean) {
            delegate = if (useSystemImp) FakeSystemImp() else FakeLocalImp()
        }
    }

    private object FakeOneTrackSdk {
        @JvmField
        var disabled: Boolean = false
    }
}
