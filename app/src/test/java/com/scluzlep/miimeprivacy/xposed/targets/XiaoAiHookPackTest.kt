package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoAiHookPackTest {

    @Test
    fun targetMatchesXiaoAiAndCoversAllFiveNonOverlayRules() {
        assertEquals(ImeTarget.XIAOAI, XiaoAiHookPack.target)

        val nonOverlayIds = HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL }
            .map { it.ruleId }
            .toSet()

        assertEquals(
            setOf(
                HookRuleCatalog.RULE_XIAOAI_CTA_GATE,
                HookRuleCatalog.RULE_XIAOAI_ONETRACK,
                HookRuleCatalog.RULE_XIAOAI_AI_CLOUD_IPC,
                HookRuleCatalog.RULE_XIAOAI_CLIPBOARD,
                HookRuleCatalog.RULE_XIAOAI_HOTWORD_TRACKER
            ),
            nonOverlayIds
        )
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
        assertEquals("v7.f", XiaoAiAnchors.CLASS_ONETRACK_HELPER)
        assertEquals("v7.e", XiaoAiAnchors.CLASS_SESSION_COLLECTOR)
        assertEquals("v7.g", XiaoAiAnchors.CLASS_SESSION_HOLDER)
        assertEquals("com.xiaomi.onetrack.api.au", XiaoAiAnchors.CLASS_ONETRACK_IMP)
        assertTrue(XiaoAiAnchors.ONETRACK_HELPER_STRINGS.contains("OneTrackHelper"))
        assertTrue(XiaoAiAnchors.ONETRACK_HELPER_STRINGS.contains("Failed to restore OneTrack SDK state"))
        assertTrue(XiaoAiAnchors.ONETRACK_HELPER_ERROR_STRINGS.contains("ime_error"))
        assertEquals(
            listOf("SessionActionCollector", "duration_s", "key_cnt", "assoc_show_cnt"),
            XiaoAiAnchors.SESSION_COLLECTOR_STRINGS
        )

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
}
