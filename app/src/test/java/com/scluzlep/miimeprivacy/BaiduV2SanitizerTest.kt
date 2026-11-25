package com.scluzlep.miimeprivacy

import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.targets.BaiduJsonSanitizer
import com.scluzlep.miimeprivacy.xposed.targets.StreamSendDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession

class BaiduV2SanitizerTest {

    @Test
    fun `shouldBlockUnauthorizedClipboard blocks token, JS, background, and DB even when consented`() {
        assertTrue(
            BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                clipRuleEnabled = true,
                isBackgroundOrTokenOrJs = true,
                privacyAgreed = true,
                privacyInputMode = false
            )
        )
        assertFalse(
            BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                clipRuleEnabled = false,
                isBackgroundOrTokenOrJs = true,
                privacyAgreed = true,
                privacyInputMode = false
            )
        )
    }

    @Test
    fun `shouldBlockUnauthorizedClipboard keeps foreground reader usable unless strict mode or unconsented`() {
        assertFalse(
            BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                clipRuleEnabled = true,
                isBackgroundOrTokenOrJs = false,
                privacyAgreed = true,
                privacyInputMode = false,
                strictReadersEnabled = false
            )
        )
        assertTrue(
            BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                clipRuleEnabled = true,
                isBackgroundOrTokenOrJs = false,
                privacyAgreed = true,
                privacyInputMode = false,
                strictReadersEnabled = true
            )
        )
        assertTrue(
            BaiduJsonSanitizer.shouldBlockUnauthorizedClipboard(
                clipRuleEnabled = true,
                isBackgroundOrTokenOrJs = false,
                privacyAgreed = false,
                privacyInputMode = false,
                strictReadersEnabled = false
            )
        )
    }

    @Test
    fun `shouldBlockAppListSelector blocks both case 1 and case 4 instl`() {
        assertTrue(BaiduJsonSanitizer.shouldBlockAppListSelector(1, blockAppList = true))
        assertTrue(BaiduJsonSanitizer.shouldBlockAppListSelector(4, blockAppList = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockAppListSelector(2, blockAppList = true))
        assertFalse(BaiduJsonSanitizer.shouldBlockAppListSelector(1, blockAppList = false))
        assertFalse(BaiduJsonSanitizer.shouldBlockAppListSelector(4, blockAppList = false))
    }

    @Test
    fun `sanitizeDeviceInfoQuery strips cuid oaid oid ecs while preserving other parameters`() {
        val raw = "&ua=bd_1_2&uc=12&cuid=secretCuid&oaid=secretOaid&cfrom=mi&oid=secretOid&abi=arm64&ecs=secretEcs"
        val sanitized = BaiduJsonSanitizer.sanitizeDeviceInfoQuery(raw, stripIdentifiers = true)
        assertEquals("&ua=bd_1_2&uc=12&cfrom=mi&abi=arm64", sanitized)
        assertEquals(raw, BaiduJsonSanitizer.sanitizeDeviceInfoQuery(raw, stripIdentifiers = false))
        assertNull(BaiduJsonSanitizer.sanitizeDeviceInfoQuery(null, stripIdentifiers = true))
    }

    @Test
    fun `verifyTlsHostname delegates to platform verifier and rejects null or blank`() {
        val session = java.lang.reflect.Proxy.newProxyInstance(
            SSLSession::class.java.classLoader,
            arrayOf(SSLSession::class.java)
        ) { _, method, _ ->
            when (method.returnType) {
                Boolean::class.javaPrimitiveType -> false
                Int::class.javaPrimitiveType -> 0
                Long::class.javaPrimitiveType -> 0L
                else -> null
            }
        } as SSLSession

        val strictVerifier = HostnameVerifier { host, _ -> host == "sug.ime.baidu.com" }
        assertTrue(BaiduJsonSanitizer.verifyTlsHostname("sug.ime.baidu.com", session, strictVerifier))
        assertFalse(BaiduJsonSanitizer.verifyTlsHostname("evil.example.com", session, strictVerifier))
        assertFalse(BaiduJsonSanitizer.verifyTlsHostname("", session, strictVerifier))
        assertFalse(BaiduJsonSanitizer.verifyTlsHostname("sug.ime.baidu.com", null, strictVerifier))
    }

    @Test
    fun `decideIptCloudStreamSend routes telemetry types 4 and 5 independently of cloud gate`() {
        assertEquals(
            StreamSendDecision.BLOCK_TELEMETRY,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 4, blockTelemetry = true, cloudEnabled = true)
        )
        assertEquals(
            StreamSendDecision.PROCEED,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 4, blockTelemetry = false, cloudEnabled = false)
        )
        assertEquals(
            StreamSendDecision.BLOCK_CLOUD_DISABLED,
            BaiduJsonSanitizer.decideIptCloudStreamSend(type = 1, blockTelemetry = true, cloudEnabled = false)
        )
    }

    @Test
    fun `BaiduHookPack references all 24 canonical Baidu rule IDs and removes deprecated v1 flags`() {
        val source = File(
            "src/main/java/com/scluzlep/miimeprivacy/xposed/targets/BaiduHookPack.kt"
        ).readText()

        val allRuleIds = HookRuleCatalog.rulesFor(ImeTarget.BAIDU).map { it.ruleId }
        assertEquals(24, allRuleIds.size)
        for (ruleId in allRuleIds) {
            val constantName = "RULE_" + ruleId.uppercase().replace('.', '_')
            assertTrue("Missing $constantName ($ruleId) in BaiduHookPack.kt", source.contains(constantName))
        }
        assertFalse("isRuleFlagEnabled must be removed", source.contains("isRuleFlagEnabled"))
        assertFalse("t6.a literal should not appear as fastPath candidate", source.contains("\"t6.a\""))
    }
}
