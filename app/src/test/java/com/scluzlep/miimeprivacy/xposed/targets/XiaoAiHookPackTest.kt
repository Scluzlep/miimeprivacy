package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoAiHookPackTest {

    @Test
    fun targetMatchesXiaoAiAndCoversAllNonOverlayRules() {
        assertEquals(ImeTarget.XIAOAI, XiaoAiHookPack.target)

        val nonOverlayIds = HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL }
            .map { it.ruleId }
            .toSet()

        assertEquals(19, nonOverlayIds.size)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK in nonOverlayIds)
        assertTrue(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR in nonOverlayIds)
    }

    @Test
    fun ctaGateBlocksAllFailOpenPathsButPreservesExplicitUserConsent() {
        val defaultCfg = PrivacyConfig()

        // Fail-open callers (!isCtaSupported, requestCtaDialog catch, resultCode=-1/-2, nested step)
        assertFalse(XiaoAiPrivacyPolicy.shouldAllowCtaAccept(defaultCfg, hasExplicitUserAccept = false))

        // Genuine user accept (requestCode == 1001 && resultCode == 1 outside nested retry)
        assertTrue(XiaoAiPrivacyPolicy.shouldAllowCtaAccept(defaultCfg, hasExplicitUserAccept = true))

        // Live toggles off allow existing behavior
        assertTrue(
            XiaoAiPrivacyPolicy.shouldAllowCtaAccept(
                defaultCfg.copy(blockTelemetry = false),
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
    fun oneTrackAndPasswordStatsGateSupportsIndependentTelemetryAndPasswordToggles() {
        val defaultCfg = PrivacyConfig()
        assertTrue(XiaoAiPrivacyPolicy.shouldForceOneTrackNetworkDisabled(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(defaultCfg, isPasswordSession = false))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockSessionCounters(defaultCfg, isPasswordSession = true))

        // Telemetry off, password stats on -> ONLY password sessions blocked
        val passwordOnlyCfg = PrivacyConfig(blockTelemetry = false, blockPasswordStats = true)
        assertFalse(XiaoAiPrivacyPolicy.shouldForceOneTrackNetworkDisabled(passwordOnlyCfg))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(passwordOnlyCfg, isPasswordSession = false))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockSessionCounters(passwordOnlyCfg, isPasswordSession = false))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(passwordOnlyCfg, isPasswordSession = true))
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockSessionCounters(passwordOnlyCfg, isPasswordSession = true))

        // Both off -> nothing blocked
        val offCfg = PrivacyConfig(blockTelemetry = false, blockPasswordStats = false)
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(offCfg, isPasswordSession = true))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockSessionCounters(offCfg, isPasswordSession = true))

        // XiaoAi unmanaged -> nothing blocked
        val unmanagedCfg = PrivacyConfig(xiaoaiManaged = false)
        assertFalse(XiaoAiPrivacyPolicy.shouldForceOneTrackNetworkDisabled(unmanagedCfg))
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockOneTrackEvent(unmanagedCfg, isPasswordSession = true))
    }

    @Test
    fun cloudAiPolicyPreservesWorkingCloudWhenEnabledAndBlocksWhenDisabled() {
        val defaultCfg = PrivacyConfig()
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldSanitizeVoiceAssistPayload(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldStripScreenCapture(defaultCfg))
        assertTrue(XiaoAiPrivacyPolicy.shouldMaskSpeechHostPackage(defaultCfg))

        // Per-IME FORCE_OFF blocks AI IPC cleanly
        val perImeOff = defaultCfg.copy(xiaoaiCloudOverride = CloudOverride.FORCE_OFF)
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(perImeOff))
        assertTrue(XiaoAiPrivacyPolicy.shouldStripScreenCapture(perImeOff))
        assertFalse(XiaoAiPrivacyPolicy.shouldMaskSpeechHostPackage(perImeOff))

        // Global cloud off -> FOLLOW_GLOBAL blocks, FORCE_ON stays working with sanitization
        val globalCloudOff = defaultCfg.copy(allowCloudInput = false)
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(globalCloudOff))
        assertFalse(XiaoAiPrivacyPolicy.shouldMaskSpeechHostPackage(globalCloudOff))

        val forcedOnWhileGlobalOff = globalCloudOff.copy(xiaoaiCloudOverride = CloudOverride.FORCE_ON)
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockCloudAiIpc(forcedOnWhileGlobalOff))
        assertTrue(XiaoAiPrivacyPolicy.shouldSanitizeVoiceAssistPayload(forcedOnWhileGlobalOff))
        assertTrue(XiaoAiPrivacyPolicy.shouldMaskSpeechHostPackage(forcedOnWhileGlobalOff))
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

        // Default config: maskHostPackage=true, stripCursorContext=true, sanitizeCloudPayload=true
        val defaultSanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, PrivacyConfig())
        assertEquals("[user_text:请把这段话润色为正式语气]", defaultSanitized)

        // Mask host only -> keeps peer_text & has_screenshot, removes app tag
        val hostOnlyCfg = PrivacyConfig(
            maskHostPackage = true,
            stripCursorContext = false,
            sanitizeCloudPayload = false
        )
        val hostOnlySanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, hostOnlyCfg)
        assertFalse(hostOnlySanitized.contains("[app:"))
        assertTrue(hostOnlySanitized.contains("[user_text:请把这段话润色为正式语气]"))
        assertTrue(hostOnlySanitized.contains("第二行敏感聊天记录]"))
        assertTrue(hostOnlySanitized.contains("[has_screenshot:true]"))

        // Strip cursor context only -> keeps app & has_screenshot, removes multiline peer_text
        val cursorOnlyCfg = PrivacyConfig(
            maskHostPackage = false,
            stripCursorContext = true,
            sanitizeCloudPayload = false
        )
        val cursorOnlySanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, cursorOnlyCfg)
        assertTrue(cursorOnlySanitized.contains("[app:com.tencent.mm]"))
        assertFalse(cursorOnlySanitized.contains("[peer_text:"))
        assertFalse(cursorOnlySanitized.contains("第二行敏感聊天记录"))
        assertTrue(cursorOnlySanitized.contains("[has_screenshot:true]"))

        // Plain prompt without structured tags passes through unmodified
        assertEquals("帮我写一封请假邮件", XiaoAiPrivacyPolicy.sanitizeAgentPrompt("帮我写一封请假邮件", PrivacyConfig()))

        // Unmanaged XiaoAi skips prompt rewriting
        assertEquals(rawPrompt, XiaoAiPrivacyPolicy.sanitizeAgentPrompt(rawPrompt, PrivacyConfig(xiaoaiManaged = false)))
    }

    @Test
    fun clipboardAndHotwordTrackerPoliciesRespectLiveConfig() {
        val defaultCfg = PrivacyConfig()
        assertTrue(XiaoAiPrivacyPolicy.shouldBlockBackgroundClipboardRead(defaultCfg))
        assertFalse(
            XiaoAiPrivacyPolicy.shouldBlockBackgroundClipboardRead(
                defaultCfg.copy(clipboardPrivacy = false)
            )
        )

        // Hotword tracker defaults to off while effectiveCloudEnabled(XIAOAI) == true
        assertFalse(XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(defaultCfg))

        // Turning ON blockXiaoAiHotwordTracker or disabling cloud activates blocker
        assertTrue(
            XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(
                defaultCfg.copy(blockXiaoAiHotwordTracker = true)
            )
        )
        assertTrue(
            XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(
                defaultCfg.copy(allowCloudInput = false)
            )
        )
        assertTrue(
            XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(
                defaultCfg.copy(xiaoaiCloudOverride = CloudOverride.FORCE_OFF)
            )
        )

        // Unmanaged XiaoAi disables blocking even if hotword tracker toggle is set
        assertFalse(
            XiaoAiPrivacyPolicy.shouldBlockHotwordTracker(
                defaultCfg.copy(xiaoaiManaged = false, blockXiaoAiHotwordTracker = true)
            )
        )
    }

    @Test
    fun verifiedXiaoAiAnchorsAndCriticalDictPackExclusionsAreIntact() {
        // Verified CTA fail-open anchors (never j9.e.n() or DictPack m9.a0/j5.v/r9.v)
        assertEquals("com.mi.ime.CtaConsentActivity", XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY)
        assertEquals(1001, XiaoAiAnchors.CTA_REQUEST_CODE)
        assertEquals(1, XiaoAiAnchors.CTA_RESULT_ACCEPTED)
        assertTrue(XiaoAiAnchors.CTA_ACTIVITY_STRINGS.contains("Failed to start CTA dialog"))
        assertTrue(XiaoAiAnchors.CTA_ACTIVITY_STRINGS.contains("CTA no permission to launch"))

        // Verified OneTrack & SessionActionCollector anchors
        assertEquals("setDisable", XiaoAiAnchors.METHOD_ONETRACK_SET_DISABLE)
        assertEquals("v7.f", XiaoAiAnchors.CLASS_ONETRACK_HELPER)
        assertEquals("v7.e", XiaoAiAnchors.CLASS_SESSION_COLLECTOR)
        assertEquals("v7.g", XiaoAiAnchors.CLASS_SESSION_HOLDER)
        assertEquals("com.xiaomi.onetrack.api.au", XiaoAiAnchors.CLASS_ONETRACK_IMP)
        assertEquals("com.xiaomi.onetrack.api.ay", XiaoAiAnchors.CLASS_ONETRACK_SYSTEM_IMP)
        assertEquals("com.xiaomi.onetrack.api.av", XiaoAiAnchors.CLASS_ONETRACK_LOCAL_IMP)
        assertTrue(XiaoAiAnchors.ONETRACK_HELPER_STRINGS.contains("OneTrackHelper"))
        assertTrue(XiaoAiAnchors.ONETRACK_HELPER_STRINGS.contains("Failed to restore OneTrack SDK state"))
        assertTrue(XiaoAiAnchors.ONETRACK_HELPER_ERROR_STRINGS.contains("ime_error"))
        assertTrue(XiaoAiAnchors.ONETRACK_LOCAL_IMP_STRINGS.contains("OneTrackLocalImp"))
        assertTrue(
            XiaoAiAnchors.ONETRACK_LOCAL_IMP_STRINGS.contains(
                "Tracking data is disabled or onetrack use system net traffic only, skip it."
            )
        )
        assertEquals(
            listOf("SessionActionCollector", "duration_s", "key_cnt", "assoc_show_cnt"),
            XiaoAiAnchors.SESSION_COLLECTOR_STRINGS
        )
        assertTrue(XiaoAiAnchors.SESSION_HOLDER_STRINGS.contains("newline"))
        assertEquals(listOf("source_app"), XiaoAiAnchors.SESSION_ACTION_OTHER_STRINGS)
        assertTrue(XiaoAiAnchors.SESSION_CACHE_STRINGS.contains("candidate_content"))

        // Verified Cloud IPC, ClipboardMonitor & HotwordTracker anchors
        assertEquals("k8.o", XiaoAiAnchors.CLASS_AGENT_CONNECTION)
        assertEquals("k8.n0", XiaoAiAnchors.CLASS_MICLAW_MANAGER)
        assertEquals("eb.r", XiaoAiAnchors.CLASS_SCREENSHOT_CAPTURE)
        assertEquals("nc.r", XiaoAiAnchors.CLASS_TRANSLATE_ENGINE)
        assertEquals("m8.h", XiaoAiAnchors.CLASS_SPEECH_ASR_MANAGER)
        assertEquals("rc.b", XiaoAiAnchors.CLASS_TAIYI_MANAGER)
        assertEquals("fb.d0", XiaoAiAnchors.CLASS_CLIPBOARD_MONITOR)
        assertEquals("ca.w", XiaoAiAnchors.CLASS_HOTWORD_CLIENT)
        assertEquals("ba.b", XiaoAiAnchors.CLASS_HOTWORD_EDIT_TRACKER)
        assertTrue(XiaoAiAnchors.AGENT_BIND_STRINGS.contains("com.aios.osbot.action.EXTERNAL_AGENT"))
        assertTrue(XiaoAiAnchors.MICLAW_FAILED_STRINGS.contains("Failed(message="))
        assertTrue(XiaoAiAnchors.TRANSLATE_START_STRINGS.contains("Start(textLen="))
        assertEquals(listOf("Idle"), XiaoAiAnchors.TRANSLATE_IDLE_STRINGS)
        assertTrue(XiaoAiAnchors.TRANSLATE_ERROR_STRINGS.contains("TranslateError(code="))
        assertTrue(XiaoAiAnchors.CLIPBOARD_MONITOR_STRINGS.contains("sensitive clip ignored"))
        assertTrue(XiaoAiAnchors.HOTWORD_CLIENT_STRINGS.contains("com.xiaomi.aicr"))
        assertTrue(XiaoAiAnchors.HOTWORD_CLIENT_STRINGS.contains("ime_hotword_service_version"))
        assertTrue(XiaoAiAnchors.HOTWORD_TRACKER_STRINGS.contains("HotwordEditTracker"))

        // DictPack & j9.e.n() must remain untouched
        val allAnchorClasses = listOf(
            XiaoAiAnchors.CLASS_CTA_CONSENT_ACTIVITY,
            XiaoAiAnchors.CLASS_ONETRACK_SDK,
            XiaoAiAnchors.CLASS_ONETRACK_HELPER,
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
    fun oneTrackImpInitAlwaysProceedsSoDelegateIsNonNullAndSetsDisable() {
        XiaoAiOneTrackGuard.resetForTest()
        FakeOneTrackSdk.disabled = false
        val baseImp = FakeBaseOneTrackImp()
        var blockedCount = 0

        // Even with blockTelemetry=true (default), proceed MUST execute so f4218a is non-null
        // and ActivityLifecycleCallbacks#onActivityStarted -> j().a(1) never throws NPE.
        val result = XiaoAiOneTrackGuard.handleOneTrackImpInit(
            config = PrivacyConfig(),
            oneTrackSdkClass = FakeOneTrackSdk::class.java,
            proceed = {
                baseImp.initDelegate(useSystemImp = true)
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertNull(result)
        assertNotNull("c.f4218a delegate must remain non-null after onetrack_imp_init", baseImp.delegate)
        assertTrue("OneTrack.setDisable(true) must be invoked when telemetry is blocked", FakeOneTrackSdk.disabled)
        assertEquals(1, blockedCount)

        // Activity start simulation: c.j().a(1) must not throw NPE, and ar#a(int) is blocked cleanly
        var delegateLifecycleProceeded = false
        val arResult = XiaoAiOneTrackGuard.handleOneTrackArMethod(
            config = PrivacyConfig(),
            isPasswordSession = false,
            returnType = Void.TYPE,
            proceed = {
                delegateLifecycleProceeded = true
                baseImp.delegate!!.a(1)
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertNull(arResult)
        assertFalse(delegateLifecycleProceeded)
        assertEquals(0, (baseImp.delegate as FakeSystemImp).lifecycleCalls)
        assertEquals(2, blockedCount)

        // When blockTelemetry is turned OFF, init still proceeds and clears forced disable flag
        val disabledOffImp = FakeBaseOneTrackImp()
        XiaoAiOneTrackGuard.handleOneTrackImpInit(
            config = PrivacyConfig(blockTelemetry = false),
            oneTrackSdkClass = FakeOneTrackSdk::class.java,
            proceed = {
                disabledOffImp.initDelegate(useSystemImp = false)
                null
            },
            incrementBlocked = { blockedCount++ }
        )
        assertNotNull(disabledOffImp.delegate)
        assertFalse(FakeOneTrackSdk.disabled)
        assertEquals(2, blockedCount)
    }

    @Test
    fun oneTrackSystemAndLocalImpArMethodsBlockCleanlyWithoutTouchingPrivateMethods() {
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

        val sysImp = FakeSystemImp()
        val localImp = FakeLocalImp()
        var blockedCount = 0

        // Password-only mode: non-password proceeds on both ay & av; password session blocks both
        val pwdOnlyCfg = PrivacyConfig(blockTelemetry = false, blockPasswordStats = true)
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

        // Primitive defaultReturnValue preserves type safety for boolean/numeric hooks (e.g. rh.b#L, ba IPC)
        assertEquals(false, XiaoAiOneTrackGuard.defaultReturnValue(Boolean::class.javaPrimitiveType!!))
        assertEquals(0, XiaoAiOneTrackGuard.defaultReturnValue(Int::class.javaPrimitiveType!!))
        assertEquals(0L, XiaoAiOneTrackGuard.defaultReturnValue(Long::class.javaPrimitiveType!!))
        assertEquals(0f, XiaoAiOneTrackGuard.defaultReturnValue(Float::class.javaPrimitiveType!!))
        assertEquals(0.0, XiaoAiOneTrackGuard.defaultReturnValue(Double::class.javaPrimitiveType!!))
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

        override fun a(i: Int) {
            lifecycleCalls++
        }

        override fun a(str: String, str2: String) {
            trackACalls++
        }

        override fun a(z: Boolean) {
            flushCalls++
        }

        override fun b(str: String, str2: String) {
            trackBCalls++
        }

        // Extra ba.a#a() and private helper must not be matched as ar methods
        fun a() {}

        @Suppress("unused")
        private fun a(str: String, str2: String, flag: Boolean) {}
    }

    private class FakeLocalImp : FakeAr {
        @JvmField var lifecycleCalls = 0
        @JvmField var trackACalls = 0
        @JvmField var trackBCalls = 0
        @JvmField var flushCalls = 0

        override fun a(i: Int) {
            lifecycleCalls++
        }

        override fun a(str: String, str2: String) {
            trackACalls++
        }

        override fun a(z: Boolean) {
            flushCalls++
        }

        override fun b(str: String, str2: String) {
            trackBCalls++
        }

        // Private boolean(String, String) helpers in av.java must not be matched as ar methods
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

        @JvmStatic
        @Suppress("unused")
        fun setDisable(value: Boolean) {
            disabled = value
        }
    }
}
