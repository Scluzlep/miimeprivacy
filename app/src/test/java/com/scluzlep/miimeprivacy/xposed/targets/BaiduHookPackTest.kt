package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BaiduHookPackTest {

    @Test
    fun sanitizeCloudInfoJson_zerosCpnInsideReportAndPreservesOtherFields() {
        val input = """{"info":{"cpn":99,"city":"北京"},"report":{"pkg":"com.tencent.mm","cpn":14,"tag":"a\"cpn\":9"}}"""
            .toByteArray(Charsets.UTF_8)

        val actual = BaiduJsonSanitizer.sanitizeCloudInfoJson(input, zeroClipboardCount = true)

        val expected = """{"info":{"cpn":99,"city":"北京"},"report":{"pkg":"com.tencent.mm","cpn":0,"tag":"a\"cpn\":9"}}"""
        assertEquals(expected, actual?.toString(Charsets.UTF_8))
    }

    @Test
    fun sanitizeCloudInfoJson_handlesEscapedKeysWhitespaceStringValueAndBom() {
        val json = "\uFEFF  {\n  \"report\" : {\n    \"\\u0063\\u0070\\u006e\" : \"7\",\n    \"ver\" : 1\n  }\n}  "
        val input = json.toByteArray(Charsets.UTF_8)

        val actual = BaiduJsonSanitizer.sanitizeCloudInfoJson(input, zeroClipboardCount = true)

        val expected = "\uFEFF  {\n  \"report\" : {\n    \"\\u0063\\u0070\\u006e\" : \"0\",\n    \"ver\" : 1\n  }\n}  "
        assertEquals(expected, actual?.toString(Charsets.UTF_8))
    }

    @Test
    fun sanitizeCloudInfoJson_returnsSameInstanceWhenZeroOrDisabledOrAbsent() {
        val alreadyZero = """{"report":{"cpn":0,"ver":1}}""".toByteArray(Charsets.UTF_8)
        assertSame(alreadyZero, BaiduJsonSanitizer.sanitizeCloudInfoJson(alreadyZero, zeroClipboardCount = true))

        val nonzero = """{"report":{"cpn":5}}""".toByteArray(Charsets.UTF_8)
        assertSame(nonzero, BaiduJsonSanitizer.sanitizeCloudInfoJson(nonzero, zeroClipboardCount = false))

        val noReport = """{"cpn":5,"info":{"cpn":9}}""".toByteArray(Charsets.UTF_8)
        assertSame(noReport, BaiduJsonSanitizer.sanitizeCloudInfoJson(noReport, zeroClipboardCount = true))

        val noCpn = """{"report":{"ver":1}}""".toByteArray(Charsets.UTF_8)
        assertSame(noCpn, BaiduJsonSanitizer.sanitizeCloudInfoJson(noCpn, zeroClipboardCount = true))
    }

    @Test
    fun sanitizeCloudInfoJson_isResilientToNullEmptyAndMalformedPayloads() {
        assertNull(BaiduJsonSanitizer.sanitizeCloudInfoJson(null, zeroClipboardCount = true))

        val empty = ByteArray(0)
        assertSame(empty, BaiduJsonSanitizer.sanitizeCloudInfoJson(empty, zeroClipboardCount = true))

        val malformedSamples = listOf(
            "not json".toByteArray(Charsets.UTF_8),
            """{"report":{"cpn":5""".toByteArray(Charsets.UTF_8),
            """{"report":{"cpn":5,}}""".toByteArray(Charsets.UTF_8),
            """{"report":{"bad":"\x","cpn":5}}""".toByteArray(Charsets.UTF_8),
            """{"report":{"cpn":5}} trailing""".toByteArray(Charsets.UTF_8),
            "[1,2,3]".toByteArray(Charsets.UTF_8),
            byteArrayOf(0x7B, 0xFF.toByte(), 0x7D)
        )
        for (sample in malformedSamples) {
            val result = BaiduJsonSanitizer.sanitizeCloudInfoJson(sample, zeroClipboardCount = true)
            assertArrayEquals(sample, result)
            assertSame(sample, result)
        }
    }

    @Test
    fun decideIptCloudStreamSend_blocksCloudLog4And5AndPreservesCandidatesWhenCloudEnabled() {
        assertEquals(
            StreamSendDecision.BLOCK_TELEMETRY,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 4, blockTelemetry = true, cloudEnabled = true)
        )
        assertEquals(
            StreamSendDecision.BLOCK_TELEMETRY,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 5, blockTelemetry = true, cloudEnabled = false)
        )
        assertEquals(
            StreamSendDecision.PROCEED,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 4, blockTelemetry = false, cloudEnabled = true)
        )
        assertEquals(
            StreamSendDecision.PROCEED,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 5, blockTelemetry = false, cloudEnabled = true)
        )

        for (candidateType in listOf(0, 1, 2, 3, 6, 7)) {
            assertEquals(
                "Type $candidateType must proceed when cloud is enabled even if blockTelemetry=true",
                StreamSendDecision.PROCEED,
                BaiduJsonSanitizer.decideIptCloudStreamSend(
                    type = candidateType,
                    blockTelemetry = true,
                    cloudEnabled = true
                )
            )
            assertEquals(
                "Type $candidateType must block when cloud is disabled",
                StreamSendDecision.BLOCK_CLOUD_DISABLED,
                BaiduJsonSanitizer.decideIptCloudStreamSend(
                    type = candidateType,
                    blockTelemetry = false,
                    cloudEnabled = false
                )
            )
        }
    }

    @Test
    fun shouldBlockSj0Task_blocksOnlyCase14AndPreservesCase9DictSync() {
        assertTrue(BaiduJsonSanitizer.shouldBlockSj0Task(selector = 14, blockTelemetry = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockSj0Task(selector = 14, blockTelemetry = false))

        assertFalse(BaiduJsonSanitizer.shouldBlockSj0Task(selector = 9, blockTelemetry = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockSj0Task(selector = 9, blockTelemetry = false))

        for (other in listOf(0, 1, 3, 8, 10, 11, 13, 15)) {
            assertFalse(BaiduJsonSanitizer.shouldBlockSj0Task(selector = other, blockTelemetry = true))
        }
    }

    @Test
    fun shouldBlockPrivacyClipboard_fixesInvertedPrivacyModeAndPreservesNormalBar() {
        assertTrue(
            BaiduJsonSanitizer.shouldBlockPrivacyClipboard(
                clipboardPrivacy = true,
                privacyAgreed = true,
                privacyInputMode = true
            )
        )
        assertTrue(
            BaiduJsonSanitizer.shouldBlockPrivacyClipboard(
                clipboardPrivacy = true,
                privacyAgreed = false,
                privacyInputMode = false
            )
        )
        assertTrue(
            BaiduJsonSanitizer.shouldBlockPrivacyClipboard(
                clipboardPrivacy = true,
                privacyAgreed = false,
                privacyInputMode = true
            )
        )
        // Consented foreground typing / clipboard bar when MIUI Privacy Input Mode is OFF must remain 100% usable.
        assertFalse(
            BaiduJsonSanitizer.shouldBlockPrivacyClipboard(
                clipboardPrivacy = true,
                privacyAgreed = true,
                privacyInputMode = false
            )
        )
        assertFalse(
            BaiduJsonSanitizer.shouldBlockPrivacyClipboard(
                clipboardPrivacy = false,
                privacyAgreed = false,
                privacyInputMode = true
            )
        )
    }

    @Test
    fun helperPredicates_gateBit1839SalvageCase5AppScanCase1AndOptionalHttpDns() {
        assertTrue(BaiduJsonSanitizer.shouldBlockBit1839Consent(prefKey = 1839, blockTelemetry = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockBit1839Consent(prefKey = 1839, blockTelemetry = false))
        assertFalse(BaiduJsonSanitizer.shouldBlockBit1839Consent(prefKey = 2439, blockTelemetry = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockBit1839Consent(prefKey = 2506, blockTelemetry = true))

        assertTrue(BaiduJsonSanitizer.shouldBlockSalvageTask(selector = 5, blockRemoteLogSalvage = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockSalvageTask(selector = 5, blockRemoteLogSalvage = false))
        assertFalse(BaiduJsonSanitizer.shouldBlockSalvageTask(selector = 3, blockRemoteLogSalvage = true))

        assertTrue(BaiduJsonSanitizer.shouldBlockAppScanTask(selector = 1, blockContactsAndAppScan = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockAppScanTask(selector = 1, blockContactsAndAppScan = false))
        assertFalse(BaiduJsonSanitizer.shouldBlockAppScanTask(selector = 2, blockContactsAndAppScan = true))

        assertFalse(BaiduJsonSanitizer.shouldBlockHttpDns(PrivacyConfig().blockHttpDns))
        assertTrue(BaiduJsonSanitizer.shouldBlockHttpDns(true))
    }

    @Test
    fun hookRuleCatalog_coversAllSevenNonOverlayBaiduRulesAndRespectsConfigBridgeDefaults() {
        val nonOverlayRules = HookRuleCatalog.rulesFor(ImeTarget.BAIDU)
            .filterNot { it.ruleId == HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL }
        assertEquals(ImeTarget.BAIDU, BaiduHookPack.target)
        assertEquals(7, nonOverlayRules.size)

        // Default ConfigBridge leaves optional strict HTTPDNS disabled while enabling default privacy gates.
        assertFalse(ConfigBridge.isRuleEnabled(ImeTarget.BAIDU) { it.blockHttpDns })
        assertTrue(ConfigBridge.isRuleEnabled(ImeTarget.BAIDU) { it.blockTelemetry })

        val globalDisabled = PrivacyConfig(allowCloudInput = false, baiduCloudOverride = CloudOverride.FOLLOW_GLOBAL)
        assertFalse(globalDisabled.effectiveCloudEnabled(ImeTarget.BAIDU))

        val forcedOn = PrivacyConfig(allowCloudInput = false, baiduCloudOverride = CloudOverride.FORCE_ON)
        assertTrue(forcedOn.effectiveCloudEnabled(ImeTarget.BAIDU))

        val forcedOff = PrivacyConfig(allowCloudInput = true, baiduCloudOverride = CloudOverride.FORCE_OFF)
        assertFalse(forcedOff.effectiveCloudEnabled(ImeTarget.BAIDU))
    }
}
