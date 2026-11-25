package com.scluzlep.miimeprivacy

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.targets.SogouPayloadSanitizer
import com.scluzlep.miimeprivacy.xposed.targets.SogouPayloadSanitizer.OutboundDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * JVM unit tests covering Sogou v2.0 granular sanitizer behavior, `BaseInputRequestInfo` constants,
 * `CloudMgr.m` FORCE_ON state restoration, Wangzai VPA payload stripping, and 30-rule wiring.
 */
class SogouV2SanitizerTest {

    private class FakeGeoInfo(var lat: Double = 39.9, var lon: Double = 116.4)
    private class FakeDeviceInfo(
        var geo: FakeGeoInfo? = FakeGeoInfo(),
        var imei: String = "860000000000001",
        var imsi: String = "460000000000001",
        var h: String = "hardware-fp"
    )

    private class FakeClientRequestBody(
        var input: String = "nihao",
        var dialogue: Array<String>? = arrayOf("secret chat"),
        var env: String = "com.tencent.mm",
        var keypositions: IntArray? = intArrayOf(12, 34),
        var usePosAdjust: Boolean = true,
        var stdata: ByteArray = byteArrayOf(1, 2, 3),
        var stdataBack: ByteArray = byteArrayOf(4, 5),
        var device: FakeDeviceInfo? = FakeDeviceInfo()
    )

    private class FakeVpaDevice(
        var b: String = "com.tencent.mm",
        var d: String = "android-id-123",
        var n: String = "sogou-account-id"
    )

    private class FakeVpaLocation(
        var b: Double = 31.23,
        var c: Double = 121.47,
        var d: String = "Shanghai"
    )

    private class FakeVpaRequestBody(
        var b: FakeVpaDevice? = FakeVpaDevice(),
        var c: FakeVpaLocation? = FakeVpaLocation(),
        var z: Array<String>? = arrayOf("cursor line")
    )

    @Test
    fun `baseInputRequestInfo constants match APK 20160 protocol`() {
        assertEquals(1, SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_INPUT)
        assertEquals(3, SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_ASSOC)
        assertEquals(6, SogouPayloadSanitizer.REQUEST_TYPE_NET_SWITCH)
        assertEquals(7, SogouPayloadSanitizer.REQUEST_TYPE_MUTUAL_DATA)
        assertEquals(10, SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_ENTERPRISE)
        assertEquals(11, SogouPayloadSanitizer.REQUEST_TYPE_DICT_UPGRADE)
        assertEquals(12, SogouPayloadSanitizer.REQUEST_TYPE_LOCATION)
        assertEquals(13, SogouPayloadSanitizer.REQUEST_TYPE_PERSONALIZED_DICT)
        assertEquals(14, SogouPayloadSanitizer.REQUEST_TYPE_REAL_TIME_RECOMMEND)
        assertEquals(15, SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_FREQUENCY)
    }

    @Test
    fun `evaluateWebSocketRequest blocks enterprise cloud type 10 when cloud disabled`() {
        assertEquals(
            OutboundDecision.BLOCK_CLOUD,
            SogouPayloadSanitizer.evaluateWebSocketRequest(
                requestType = SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_ENTERPRISE,
                targetManaged = true,
                cloudEnabled = false,
                blockMutualData = true
            )
        )
        assertEquals(
            OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(
                requestType = SogouPayloadSanitizer.REQUEST_TYPE_CLOUD_ENTERPRISE,
                targetManaged = true,
                cloudEnabled = true,
                blockMutualData = true
            )
        )
        assertEquals(
            OutboundDecision.BLOCK_MUTUAL,
            SogouPayloadSanitizer.evaluateWebSocketRequest(
                requestType = SogouPayloadSanitizer.REQUEST_TYPE_MUTUAL_DATA,
                targetManaged = true,
                cloudEnabled = true,
                blockMutualData = true
            )
        )
        assertEquals(
            OutboundDecision.ALLOW,
            SogouPayloadSanitizer.evaluateWebSocketRequest(
                requestType = SogouPayloadSanitizer.REQUEST_TYPE_PERSONALIZED_DICT,
                targetManaged = true,
                cloudEnabled = false,
                blockMutualData = true
            )
        )
    }

    @Test
    fun `granular sanitizeClientRequestBodyDetailed isolates each of the four rules`() {
        val bodyOnlyHost = FakeClientRequestBody()
        val report = SogouPayloadSanitizer.sanitizeClientRequestBodyDetailed(
            body = bodyOnlyHost,
            sanitizePayloadDeviceDialogue = false,
            stripCursorContext = false,
            maskHostPackage = true,
            stripTouchPositions = false
        )
        assertTrue(report.modified)
        assertEquals(setOf("env"), report.strippedFields)
        assertEquals("", bodyOnlyHost.env)
        assertNotNull(bodyOnlyHost.dialogue)
        assertNotNull(bodyOnlyHost.keypositions)
        assertTrue(bodyOnlyHost.usePosAdjust)
        assertEquals(3, bodyOnlyHost.stdata.size)
        assertNotNull(bodyOnlyHost.device?.geo)
        assertEquals("860000000000001", bodyOnlyHost.device?.imei)
        assertEquals("nihao", bodyOnlyHost.input)
    }

    @Test
    fun `cloudMgrStateTransition forces 3 on FORCE_ON and restores original value on exit`() {
        // Enter FORCE_ON from state 2 -> forces 3, remembers 2
        val enter = SogouPayloadSanitizer.resolveCloudMgrStateTransition(
            currentState = 2,
            savedPriorState = null,
            forceOn = true
        )
        assertEquals(3, enter.newState)
        assertEquals(2, enter.nextSavedPriorState)

        // Subsequent check while already 3 -> keeps saved prior 2, no redundant write
        val steady = SogouPayloadSanitizer.resolveCloudMgrStateTransition(
            currentState = 3,
            savedPriorState = enter.nextSavedPriorState,
            forceOn = true
        )
        assertNull(steady.newState)
        assertEquals(2, steady.nextSavedPriorState)

        // Leave FORCE_ON -> restores prior state 2 and clears savedPriorState
        val exit = SogouPayloadSanitizer.resolveCloudMgrStateTransition(
            currentState = 3,
            savedPriorState = steady.nextSavedPriorState,
            forceOn = false
        )
        assertEquals(2, exit.newState)
        assertNull(exit.nextSavedPriorState)

        // Already not FORCE_ON with no saved state -> no-op
        val noop = SogouPayloadSanitizer.resolveCloudMgrStateTransition(
            currentState = 0,
            savedPriorState = exit.nextSavedPriorState,
            forceOn = false
        )
        assertNull(noop.newState)
        assertNull(noop.nextSavedPriorState)
    }

    @Test
    fun `sanitizeWangzaiVpaRequest strips dialogue, device ids, location, and host pkg per rule`() {
        val vpa = FakeVpaRequestBody()
        val report = SogouPayloadSanitizer.sanitizeWangzaiVpaRequest(
            requestBody = vpa,
            stripDeviceDialogue = true,
            stripLocation = true,
            stripHostPackage = true
        )
        assertTrue(report.modified)
        assertNull(vpa.z)
        assertNull(vpa.c)
        assertEquals("", vpa.b?.d)
        assertEquals("", vpa.b?.n)
        assertEquals("", vpa.b?.b)
        assertTrue(
            report.strippedFields.containsAll(
                listOf("vpa.dialogue", "vpa.location", "vpa.androidId", "vpa.accountId", "vpa.clientPkg")
            )
        )
    }

    @Test
    fun `privacyConfig v2 rules include all 30 Sogou rules and default states match spec`() {
        val sogouRules = HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
        assertEquals(30, sogouRules.size)

        val defaultCfg = PrivacyConfig()
        assertFalse(defaultCfg.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertFalse(defaultCfg.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER))
        assertTrue(defaultCfg.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CLIP_SNIFFER_AND_KOULING))
        assertTrue(defaultCfg.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CLIP_DB_HISTORY_SAVE))
        assertTrue(defaultCfg.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_UI_DUAL_SKIN_EMOJI))
        assertEquals(CloudOverride.FOLLOW_GLOBAL, defaultCfg.cloudOverrideFor(ImeTarget.SOGOU))
    }

    @Test
    fun `sogouHookPack source wires all 30 canonical rules and eliminates v1 global flags`() {
        val hookPackFile = listOf(
            File("src/main/java/com/scluzlep/miimeprivacy/xposed/targets/SogouHookPack.kt"),
            File("app/src/main/java/com/scluzlep/miimeprivacy/xposed/targets/SogouHookPack.kt"),
            File("wt-sogou-hooks/app/src/main/java/com/scluzlep/miimeprivacy/xposed/targets/SogouHookPack.kt")
        ).firstOrNull { it.exists() }
        assertNotNull("SogouHookPack.kt must exist for contract check", hookPackFile)
        val source = hookPackFile!!.readText()

        val canonicalRules = listOf(
            "RULE_SOGOU_GATE_ALIVE_HEARTBEAT",
            "RULE_SOGOU_GATE_PRECONSENT_GUARD",
            "RULE_SOGOU_ID_OAID_TGPA",
            "RULE_SOGOU_ID_HARDWARE_AND_SENSOR",
            "RULE_SOGOU_ID_ANDROID_ID_AND_UUID",
            "RULE_SOGOU_TELEMETRY_PINGBACK",
            "RULE_SOGOU_TELEMETRY_WHITEDOG_LOGDIAG",
            "RULE_SOGOU_TELEMETRY_BEACON_QIMEI",
            "RULE_SOGOU_TELEMETRY_THIRDPARTY_ADS_QM",
            "RULE_SOGOU_TELEMETRY_CRASH_APM",
            "RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK",
            "RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE",
            "RULE_SOGOU_CLOUD_WANGZAI_VPA",
            "RULE_SOGOU_CLOUD_VOICE_PRIVACY",
            "RULE_SOGOU_CONTEXT_CURSOR_TEXT",
            "RULE_SOGOU_CONTEXT_HOST_PACKAGE",
            "RULE_SOGOU_CONTEXT_TOUCH_POSITIONS",
            "RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA",
            "RULE_SOGOU_CLIP_SNIFFER_AND_KOULING",
            "RULE_SOGOU_CLIP_AUTO_TRANSLATE_AND_JS",
            "RULE_SOGOU_CLIP_DB_HISTORY_SAVE",
            "RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER",
            "RULE_SOGOU_SCAN_DEVICEINFO_HARVESTER",
            "RULE_SOGOU_SCAN_APP_LIST",
            "RULE_SOGOU_SCAN_CONTACTS_UPLOAD_AND_DICT",
            "RULE_SOGOU_SCAN_LOCATION_WIFI",
            "RULE_SOGOU_NET_DYNAMIC_DEX_AND_JS",
            "RULE_SOGOU_NET_HTTPDNS",
            "RULE_SOGOU_UI_DUAL_SKIN_EMOJI",
            "RULE_SOGOU_OVERLAY_PILL"
        )
        assertEquals(30, canonicalRules.size)
        canonicalRules.forEach { constName ->
            assertTrue("Missing $constName in SogouHookPack.kt", source.contains(constName))
        }

        val forbiddenLegacy = listOf(
            "RULE_SOGOU_PRIVACY_GATE",
            "RULE_SOGOU_TELEMETRY\n",
            "RULE_SOGOU_TELEMETRY)",
            "RULE_SOGOU_CLOUD_SANITIZE",
            "RULE_SOGOU_MUTUAL_DATA",
            "RULE_SOGOU_CLIPBOARD",
            "RULE_SOGOU_CONTACTS_APP_SCAN",
            "RULE_SOGOU_DYNAMIC_DEX\n",
            "RULE_SOGOU_DYNAMIC_DEX)",
            "RULE_SOGOU_HTTPDNS",
            "it.blockTelemetry",
            "it.blockMutualData",
            "it.clipboardPrivacy",
            "it.blockContactsAndAppScan",
            "it.blockUnsignedDynamicDex",
            "it.blockHttpDns"
        )
        forbiddenLegacy.forEach { legacy ->
            assertFalse("Found deprecated v1.x reference '$legacy' in SogouHookPack.kt", source.contains(legacy))
        }
    }
}
