package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SogouHookPackTest {

    class FakeDialogueMsg(
        @JvmField var text: String = "secret wechat message",
        @JvmField var sender: Int = 1
    )

    class FakeKeyPos(
        @JvmField var x: Int = 120,
        @JvmField var y: Int = 450
    )

    class FakeDeviceInfo(
        @JvmField var geo: Any? = Any(),
        @JvmField var imei: String = "860000000000001",
        @JvmField var imsi: String = "460000000000001",
        @JvmField var h: String = "hardware-fp-123",
        @JvmField var brand: String = "Xiaomi"
    )

    open class FakeClientRequestBody(
        @JvmField var dialogue: Array<FakeDialogueMsg>? = arrayOf(
            FakeDialogueMsg("msg-1"),
            FakeDialogueMsg("msg-2")
        ),
        @JvmField var env: String = "com.tencent.mm",
        @JvmField var keypositions: Array<FakeKeyPos>? = arrayOf(FakeKeyPos()),
        @JvmField var usePosAdjust: Boolean = true,
        @JvmField var stdata: ByteArray? = byteArrayOf(1, 2, 3, 4),
        @JvmField var stdataBack: ByteArray? = byteArrayOf(5, 6, 7),
        @JvmField var device: FakeDeviceInfo? = FakeDeviceInfo(),
        // Functional fields that MUST stay untouched for cloud candidates & dictionary sync:
        @JvmField var input: String = "nihao",
        @JvmField var data: ByteArray = byteArrayOf(9, 8, 7),
        @JvmField var syllablefilterinfo: ByteArray = byteArrayOf(11, 12),
        @JvmField var clientcands: Array<String> = arrayOf("你好", "拟好")
    )

    class SubclassClientRequestBody : FakeClientRequestBody()

    @Test
    fun sanitize_defaultConfig_stripsAllSensitiveFields_preservesCandidatesAndDictData() {
        val body = FakeClientRequestBody()
        val originalDataRef = body.data
        val originalSyllableRef = body.syllablefilterinfo
        val originalCandsRef = body.clientcands

        val report = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(body, PrivacyConfig())

        assertTrue(report.modified)
        assertEquals(
            setOf(
                "dialogue",
                "device.geo",
                "device.imei",
                "device.imsi",
                "device.h",
                "stdata",
                "stdataBack",
                "env",
                "keypositions",
                "usePosAdjust"
            ),
            report.strippedFields
        )

        // Sensitive fields sanitized
        assertNull(body.dialogue)
        assertNull(body.device?.geo)
        assertEquals("", body.device?.imei)
        assertEquals("", body.device?.imsi)
        assertEquals("", body.device?.h)
        assertEquals(0, body.stdata?.size)
        assertEquals(0, body.stdataBack?.size)
        assertEquals("", body.env)
        assertNull(body.keypositions)
        assertFalse(body.usePosAdjust)

        // Unrelated & candidate-critical fields completely intact
        assertEquals("Xiaomi", body.device?.brand)
        assertEquals("nihao", body.input)
        assertSame(originalDataRef, body.data)
        assertArrayEquals(byteArrayOf(9, 8, 7), body.data)
        assertSame(originalSyllableRef, body.syllablefilterinfo)
        assertArrayEquals(byteArrayOf(11, 12), body.syllablefilterinfo)
        assertSame(originalCandsRef, body.clientcands)
        assertArrayEquals(arrayOf("你好", "拟好"), body.clientcands)
    }

    @Test
    fun sanitize_isIdempotent_onAlreadySanitizedBody_andHandlesNulls() {
        val body = FakeClientRequestBody()
        val cfg = PrivacyConfig()

        assertTrue(SogouPayloadSanitizer.sanitizeClientRequestBody(body, cfg))
        val secondReport = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(body, cfg)
        assertFalse(secondReport.modified)
        assertTrue(secondReport.strippedFields.isEmpty())

        assertFalse(SogouPayloadSanitizer.sanitizeClientRequestBody(null, cfg))
        val bodyWithoutDevice = FakeClientRequestBody(device = null)
        assertTrue(SogouPayloadSanitizer.sanitizeClientRequestBody(bodyWithoutDevice, cfg))
    }

    @Test
    fun sanitize_respectsIndividualToggleFlags_withoutTouchingDisabledOnes() {
        val baseOff = PrivacyConfig(
            sanitizeCloudPayload = false,
            stripCursorContext = false,
            maskHostPackage = false,
            stripTouchPositions = false
        )

        // 1. All flags OFF -> untouched
        val untouched = FakeClientRequestBody()
        assertFalse(SogouPayloadSanitizer.sanitizeClientRequestBody(untouched, baseOff))
        assertNotNull(untouched.dialogue)
        assertEquals("com.tencent.mm", untouched.env)
        assertEquals(4, untouched.stdata?.size)
        assertNotNull(untouched.keypositions)
        assertTrue(untouched.usePosAdjust)

        // 2. Only sanitizeCloudPayload ON -> only dialogue & device identifiers stripped
        val payloadOnly = FakeClientRequestBody()
        val rPayload = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(
            payloadOnly,
            baseOff.copy(sanitizeCloudPayload = true)
        )
        assertTrue(rPayload.modified)
        assertNull(payloadOnly.dialogue)
        assertNull(payloadOnly.device?.geo)
        assertEquals("", payloadOnly.device?.imei)
        assertEquals("", payloadOnly.device?.imsi)
        assertEquals("", payloadOnly.device?.h)
        assertEquals("com.tencent.mm", payloadOnly.env)
        assertEquals(4, payloadOnly.stdata?.size)
        assertNotNull(payloadOnly.keypositions)
        assertTrue(payloadOnly.usePosAdjust)

        // 3. Only stripCursorContext ON -> only stdata & stdataBack cleared
        val cursorOnly = FakeClientRequestBody()
        val rCursor = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(
            cursorOnly,
            baseOff.copy(stripCursorContext = true)
        )
        assertEquals(setOf("stdata", "stdataBack"), rCursor.strippedFields)
        assertNotNull(cursorOnly.dialogue)
        assertEquals("com.tencent.mm", cursorOnly.env)
        assertNotNull(cursorOnly.device?.geo)
        assertTrue(cursorOnly.usePosAdjust)

        // 4. Only maskHostPackage ON -> only env cleared
        val envOnly = FakeClientRequestBody()
        val rEnv = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(
            envOnly,
            baseOff.copy(maskHostPackage = true)
        )
        assertEquals(setOf("env"), rEnv.strippedFields)
        assertEquals("", envOnly.env)
        assertNotNull(envOnly.dialogue)
        assertEquals(4, envOnly.stdata?.size)

        // 5. Only stripTouchPositions ON -> only keypositions & usePosAdjust cleared
        val touchOnly = FakeClientRequestBody()
        val rTouch = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(
            touchOnly,
            baseOff.copy(stripTouchPositions = true)
        )
        assertEquals(setOf("keypositions", "usePosAdjust"), rTouch.strippedFields)
        assertNull(touchOnly.keypositions)
        assertFalse(touchOnly.usePosAdjust)
        assertNotNull(touchOnly.dialogue)
        assertEquals("com.tencent.mm", touchOnly.env)
    }

    @Test
    fun sanitize_worksAcrossSubclassHierarchy() {
        val sub = SubclassClientRequestBody()
        assertTrue(SogouPayloadSanitizer.sanitizeClientRequestBody(sub, PrivacyConfig()))
        assertNull(sub.dialogue)
        assertEquals("", sub.env)
        assertEquals("nihao", sub.input)
    }

    @Test
    fun evaluateWebSocketRequest_respectsPerImeCloudOverrides_andPreservesDictSync() {
        // Global cloud OFF, FOLLOW_GLOBAL -> block 1/3/15, ALLOW dict sync (11/13/6)
        val globalOff = PrivacyConfig(allowCloudInput = false, sogouCloudOverride = CloudOverride.FOLLOW_GLOBAL)
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_INPUT, globalOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_ASSOC, globalOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_FREQUENCY, globalOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_DICT_UPGRADE, globalOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_PERSONALIZED_DICT, globalOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_NET_SWITCH, globalOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.BLOCK_MUTUAL,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_MUTUAL_DATA, globalOff)
        )

        // Global cloud ON, Sogou FORCE_OFF -> block Sogou cloud candidates while preserving dict sync
        val forceOff = PrivacyConfig(allowCloudInput = true, sogouCloudOverride = CloudOverride.FORCE_OFF)
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.BLOCK_CLOUD,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_INPUT, forceOff)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_DICT_UPGRADE, forceOff)
        )

        // Global cloud OFF, Sogou FORCE_ON -> resume Sogou cloud candidates & allow forcing CloudMgr state 0->3
        val forceOn = PrivacyConfig(allowCloudInput = false, sogouCloudOverride = CloudOverride.FORCE_ON)
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_INPUT, forceOn)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_ASSOC, forceOn)
        )
        assertTrue(SogouPayloadSanitizer.shouldForceCloudMgrStateOn(currentState = 0, config = forceOn))
        assertFalse(SogouPayloadSanitizer.shouldForceCloudMgrStateOn(currentState = 3, config = forceOn))

        // blockMutualData = false allows MutualData
        val mutualAllowed = PrivacyConfig(blockMutualData = false)
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_MUTUAL_DATA, mutualAllowed)
        )

        // Unmanaged Sogou passes everything through untouched
        val unmanaged = PrivacyConfig(sogouManaged = false, allowCloudInput = false, blockMutualData = true)
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_INPUT, unmanaged)
        )
        assertEquals(
            SogouPayloadSanitizer.OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(SogouPayloadSanitizer.REQUEST_TYPE_MUTUAL_DATA, unmanaged)
        )
    }

    @Test
    fun clipboardAndAliveUrlGuards_behaveCorrectlyAcrossTransitions() {
        val cfg = PrivacyConfig(clipboardPrivacy = true, blockTelemetry = true)

        // Background capture blocked when IME window is hidden, allowed when user opened IME/clipboard panel
        assertTrue(SogouPayloadSanitizer.shouldBlockClipboardBackgroundCapture(isInputViewShown = false, config = cfg))
        assertFalse(SogouPayloadSanitizer.shouldBlockClipboardBackgroundCapture(isInputViewShown = true, config = cfg))
        assertFalse(
            SogouPayloadSanitizer.shouldBlockClipboardBackgroundCapture(
                isInputViewShown = false,
                config = cfg.copy(clipboardPrivacy = false)
            )
        )

        // Alive enforce_send blocked, while Glide CDN & Xiaomi offline voice wapdl allowed
        assertTrue(
            SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(
                "https://api-android.shouji.sogou.com/v1/alive/install",
                cfg
            )
        )
        assertTrue(
            SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(
                "https://api-android.shouji.sogou.com/v1/alive/special_alive",
                cfg
            )
        )
        assertTrue(
            SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(
                "https://api-android.shouji.sogou.com/v1/alive/trackInstall",
                cfg
            )
        )
        assertFalse(
            SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(
                "https://wap.dl.pinyin.sogou.com/wapdl/voice/xiaomi_offline_v1.zip",
                cfg
            )
        )
        assertFalse(
            SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(
                "https://img.shouji.sogou.com/skin/theme_preview.png",
                cfg
            )
        )
        assertFalse(
            SogouPayloadSanitizer.shouldBlockAliveEnforceSendUrl(
                "https://api-android.shouji.sogou.com/v1/alive/install",
                cfg.copy(blockTelemetry = false)
            )
        )
    }

    @Test
    fun configBridge_cloudToggleTransitions_andRuleCatalogInvariants() {
        val nonOverlayIds = HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL }
            .map { it.ruleId }
            .toSet()

        assertEquals(
            setOf(
                HookRuleCatalog.RULE_SOGOU_PRIVACY_GATE,
                HookRuleCatalog.RULE_SOGOU_TELEMETRY,
                HookRuleCatalog.RULE_SOGOU_CLOUD_SANITIZE,
                HookRuleCatalog.RULE_SOGOU_MUTUAL_DATA,
                HookRuleCatalog.RULE_SOGOU_CLIPBOARD,
                HookRuleCatalog.RULE_SOGOU_CONTACTS_APP_SCAN,
                HookRuleCatalog.RULE_SOGOU_DYNAMIC_DEX,
                HookRuleCatalog.RULE_SOGOU_HTTPDNS
            ),
            nonOverlayIds
        )

        // HTTP DNS stays disabled by default
        assertFalse(PrivacyConfig().blockHttpDns)

        val criticalIds = HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
            .filter { it.critical }
            .map { it.ruleId }
            .toSet()
        assertEquals(
            setOf(
                HookRuleCatalog.RULE_SOGOU_PRIVACY_GATE,
                HookRuleCatalog.RULE_SOGOU_CLOUD_SANITIZE
            ),
            criticalIds
        )

        // Default ConfigBridge matches default PrivacyConfig cloud policy
        ConfigBridge.detach()
        assertEquals(PrivacyConfig().effectiveCloudEnabled(ImeTarget.SOGOU), ConfigBridge.isCloudEnabled(ImeTarget.SOGOU))

        // Per-IME cloud override transitions & state resolution
        val globalOnFollow = PrivacyConfig(allowCloudInput = true, sogouCloudOverride = CloudOverride.FOLLOW_GLOBAL)
        val globalOffFollow = PrivacyConfig(allowCloudInput = false, sogouCloudOverride = CloudOverride.FOLLOW_GLOBAL)
        val forceOff = PrivacyConfig(allowCloudInput = true, sogouCloudOverride = CloudOverride.FORCE_OFF)
        val forceOn = PrivacyConfig(allowCloudInput = false, sogouCloudOverride = CloudOverride.FORCE_ON)

        assertTrue(globalOnFollow.effectiveCloudEnabled(ImeTarget.SOGOU))
        assertFalse(globalOffFollow.effectiveCloudEnabled(ImeTarget.SOGOU))
        assertFalse(forceOff.effectiveCloudEnabled(ImeTarget.SOGOU))
        assertTrue(forceOn.effectiveCloudEnabled(ImeTarget.SOGOU))
        assertEquals(ImeTarget.SOGOU, SogouHookPack.target)
    }
}
