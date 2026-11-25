package com.scluzlep.miimeprivacy

import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.xposed.targets.XiaoAiPrivacyPolicy
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaoAiV2SanitizerTest {

    @Test
    fun sanitizeAgentPromptPreservesTrailingLangAndBodyAfterCursorAndDict() {
        // lb.a + r8.b mixed structure: [cursor:] & [dict:] followed by [lang:] and body text
        val lbPrompt = """
            translate
            [app:com.tencent.mm]
            [cursor:secret text before cursor [nested]
            second sensitive line]
            [dict:热词1, 热词2]
            [lang:en]
            Please translate this sentence.
            Second body line stays intact.
        """.trimIndent()

        val sanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(lbPrompt, PrivacyConfig())
        assertFalse(sanitized.contains("[app:"))
        assertFalse(sanitized.contains("[cursor:"))
        assertFalse(sanitized.contains("secret text before cursor"))
        assertFalse(sanitized.contains("second sensitive line"))
        assertFalse(sanitized.contains("[dict:"))
        assertTrue(sanitized.contains("translate"))
        assertTrue(sanitized.contains("[lang:en]"))
        assertTrue(sanitized.contains("Please translate this sentence."))
        assertTrue(sanitized.contains("Second body line stays intact."))
    }

    @Test
    fun sanitizeAgentPromptHandlesSelectedAndXmlTextBoundaryAfterUnclosedCursor() {
        val r8Prompt = """
            [app:com.example.chat]
            [cursor:unclosed cursor context without bracket
            <text>保留用户输入正文</text>
            [selected:selected secret]
        """.trimIndent()

        val sanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(r8Prompt, PrivacyConfig())
        assertFalse(sanitized.contains("[app:"))
        assertFalse(sanitized.contains("[cursor:"))
        assertFalse(sanitized.contains("[selected:"))
        assertTrue(sanitized.contains("<text>保留用户输入正文</text>"))
    }

    @Test
    fun sanitizeAgentPromptPreservesCursorWhenCursorRuleDisabledWhileStrippingApp() {
        val prompt = """
            [app:com.tencent.mm]
            [cursor:hello cursor]
            [dict:term1,term2]
            [lang:zh]
            body
        """.trimIndent()

        val cfg = PrivacyConfig()
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP, false)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG, true)

        val sanitized = XiaoAiPrivacyPolicy.sanitizeAgentPrompt(prompt, cfg)
        assertFalse(sanitized.contains("[app:"))
        assertTrue(sanitized.contains("[cursor:hello cursor]"))
        assertTrue(sanitized.contains("[dict:term1,term2]"))
        assertTrue(sanitized.contains("[lang:zh]"))
        assertTrue(sanitized.contains("body"))
    }

    @Test
    fun sanitizeHotwordJsonScrubsAppAndAsrText() {
        val raw = """{"from_app":"com.tencent.mm","asr_text":"私密语音转写","limit":50}"""
        val sanitized = XiaoAiPrivacyPolicy.sanitizeHotwordJson(raw)
        val obj = JSONObject(sanitized)
        assertEquals("", obj.optString("from_app"))
        assertFalse(obj.has("asr_text"))
        assertEquals(50, obj.optInt("limit"))
    }

    @Test
    fun sanitizeAntiHookEnvJsonNeutralizesDetectionSignals() {
        val raw = """
            {
              "type": 2,
              "env": {
                "xposed": 1,
                "substrate": 1,
                "root": 1,
                "vpn": 1,
                "debug": 1,
                "simulator": 1,
                "virtualapp": 1,
                "never_lock_screen": 1,
                "device_id": "abc123",
                "boot_time": 123456,
                "install_time": 7890,
                "brand_model": "Xiaomi 15"
              }
            }
        """.trimIndent()

        val sanitized = XiaoAiPrivacyPolicy.sanitizeAntiHookEnvJson(raw)
        assertNotNull(sanitized)
        val env = JSONObject(sanitized!!).getJSONObject("env")
        assertEquals(0, env.getInt("xposed"))
        assertEquals(0, env.getInt("substrate"))
        assertEquals(0, env.getInt("root"))
        assertEquals(0, env.getInt("vpn"))
        assertEquals(0, env.getInt("debug"))
        assertEquals(0, env.getInt("simulator"))
        assertEquals(0, env.getInt("virtualapp"))
        assertEquals(0, env.getInt("never_lock_screen"))
        assertEquals("", env.getString("device_id"))
        assertEquals(0L, env.getLong("boot_time"))
        assertEquals(0L, env.getLong("install_time"))
        assertEquals("Xiaomi 15", env.getString("brand_model"))
    }

    @Test
    fun sanitizeDictSyncParamsRemovesSensitiveIdentifiersAndPreservesCatalogKeys() {
        val raw = mapOf(
            "catalogVersion" to "12",
            "oaid" to "secret-oaid",
            "deviceId" to "secret-dev",
            "fromApp" to "com.tencent.mm",
            "locale" to "zh-CN"
        )
        val sanitized = XiaoAiPrivacyPolicy.sanitizeDictSyncParams(raw)
        assertNotNull(sanitized)
        assertEquals("12", sanitized!!["catalogVersion"])
        assertEquals("zh-CN", sanitized["locale"])
        assertFalse(sanitized.containsKey("oaid"))
        assertFalse(sanitized.containsKey("deviceId"))
        assertFalse(sanitized.containsKey("fromApp"))

        val cleanOnly = mapOf("catalogVersion" to "12", "locale" to "zh-CN")
        assertNull(XiaoAiPrivacyPolicy.sanitizeDictSyncParams(cleanOnly))
    }

    @Test
    fun privacyDataTypeMapsToCorrectIdentityRuleAndRespectsOverrides() {
        val defaultCfg = PrivacyConfig()
        assertEquals(
            HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT,
            XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(defaultCfg, "android_id")
        )
        assertEquals(
            HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT,
            XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(defaultCfg, "device_id")
        )
        assertEquals(
            HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM,
            XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(defaultCfg, "oaid")
        )
        assertEquals(
            HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM,
            XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(defaultCfg, "line_1_number")
        )

        val oaidOff = defaultCfg.withRuleOverride(HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM, false)
        assertNull(XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(oaidOff, "oaid"))
        assertEquals(
            HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT,
            XiaoAiPrivacyPolicy.shouldBlockPrivacyDataType(oaidOff, "device_id")
        )
    }
}
