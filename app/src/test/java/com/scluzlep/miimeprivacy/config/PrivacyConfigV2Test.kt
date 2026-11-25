package com.scluzlep.miimeprivacy.config

import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.model.RuleCategory
import com.scluzlep.miimeprivacy.ui.viewmodel.resetPackageDefaults
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyConfigV2Test {

    @Test
    fun all80CatalogRulesHaveExpectedDefaultsAndCounts() {
        val rules = HookRuleCatalog.allRules()
        assertEquals(80, rules.size)
        assertEquals(31, HookRuleCatalog.rulesFor(ImeTarget.SOGOU).size)
        assertEquals(28, HookRuleCatalog.rulesFor(ImeTarget.BAIDU).size)
        assertEquals(21, HookRuleCatalog.rulesFor(ImeTarget.XIAOAI).size)
        assertEquals(9, RuleCategory.entries.size)

        val expectedDefaultOff = setOf(
            HookRuleCatalog.RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER,
            HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS,
            HookRuleCatalog.RULE_SOGOU_NET_NATIVE_ENGINE_STRICT,
            HookRuleCatalog.RULE_BAIDU_CLOUD_SPEECH_ENGINE_STRICT,
            HookRuleCatalog.RULE_BAIDU_CLIP_STRICT_BLOCK_READERS,
            HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL,
            HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS,
            HookRuleCatalog.RULE_BAIDU_NET_IPTCORE_URL_RESOURCE,
            HookRuleCatalog.RULE_BAIDU_NET_OCR_AUTH_REQUEST,
            HookRuleCatalog.RULE_BAIDU_NET_TALOS_AND_SECONDARY_SDKS,
            HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS,
            HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH,
            HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL,
            HookRuleCatalog.RULE_XIAOAI_NET_SMARTENGINE_JNI_STRICT
        )
        val expectedInstallGated = setOf(
            HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS,
            HookRuleCatalog.RULE_SOGOU_NET_NATIVE_ENGINE_STRICT,
            HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS,
            HookRuleCatalog.RULE_BAIDU_NET_IPTCORE_URL_RESOURCE,
            HookRuleCatalog.RULE_BAIDU_NET_OCR_AUTH_REQUEST,
            HookRuleCatalog.RULE_BAIDU_CLOUD_SPEECH_ENGINE_STRICT,
            HookRuleCatalog.RULE_BAIDU_NET_TALOS_AND_SECONDARY_SDKS,
            HookRuleCatalog.RULE_XIAOAI_NET_SMARTENGINE_JNI_STRICT
        )

        val cfg = PrivacyConfig()
        assertEquals(PrivacyConfig.CURRENT_CONFIG_VERSION, cfg.configVersion)
        assertTrue(cfg.ruleOverrides.isEmpty())

        for (spec in rules) {
            val expectedDefault = spec.ruleId !in expectedDefaultOff
            assertEquals("Unexpected defaultEnabled for ${spec.ruleId}", expectedDefault, spec.defaultEnabled)
            assertEquals("Unexpected installGated for ${spec.ruleId}", spec.ruleId in expectedInstallGated, spec.installGated)
            assertEquals("Unexpected isRuleEnabled for ${spec.ruleId}", expectedDefault, cfg.isRuleEnabled(spec.ruleId))
            assertEquals("Unexpected isRuleToggleChecked for ${spec.ruleId}", expectedDefault, cfg.isRuleToggleChecked(spec.ruleId))
        }
    }

    @Test
    fun legacyV1JsonMigratesAllBooleanFlagsToV2RuleOverrides() {
        val legacyJson = JSONObject().apply {
            put(PrivacyConfig.KEY_BLOCK_TELEMETRY, false)
            put(PrivacyConfig.KEY_BLOCK_PASSWORD_STATS, false)
            put(PrivacyConfig.KEY_BLOCK_REMOTE_LOG_SALVAGE, false)
            put(PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD, false)
            put(PrivacyConfig.KEY_STRIP_CURSOR_CONTEXT, false)
            put(PrivacyConfig.KEY_MASK_HOST_PACKAGE, false)
            put(PrivacyConfig.KEY_STRIP_TOUCH_POSITIONS, false)
            put(PrivacyConfig.KEY_CLIPBOARD_PRIVACY, false)
            put(PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN, false)
            put(PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, false)
            put(PrivacyConfig.KEY_BLOCK_MUTUAL_DATA, false)
            put(PrivacyConfig.KEY_BLOCK_HTTP_DNS, true)
            put(PrivacyConfig.KEY_BLOCK_XIAOAI_HOTWORD_TRACKER, false)
            put(PrivacyConfig.KEY_SHOW_KEYBOARD_PILL, false)
            put(PrivacyConfig.KEY_UPDATED_AT_MS, 20261125L)
        }.toString()

        val migrated = PrivacyConfig.fromJson(legacyJson)
        assertEquals(2, migrated.configVersion)

        // Legacy false flags disable matching rules
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_GATE_ALIVE_HEARTBEAT))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_TELEMETRY_REALTIME_STATS))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_NET_SALVAGE_LOGS))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_CONTEXT_CURSOR_AND_AI))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CLIP_SNIFFER_AND_KOULING))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_SCAN_CONTACTS))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_NET_TLS_HARDENING))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL))

        // Legacy true flags enable opt-in rules
        assertTrue(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertTrue(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS))

        // v2.0 keeps xiaoai.context.hotword_aicr enabled by default even when v1.x tracker flag was false
        assertTrue(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR))
    }

    @Test
    fun targetScopedOverridesAndResetPackageDefaultsStayIsolated() {
        val modified = PrivacyConfig()
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS, true)
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK, false)
            .withRuleOverride(HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL, true)
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL, false)

        assertTrue(modified.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertFalse(modified.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK))
        assertTrue(modified.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL))
        assertFalse(modified.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL))

        // Resetting Sogou clears only Sogou overrides and preserves Baidu/XiaoAi overrides
        val resetSogou = modified.resetPackageDefaults(ImeTarget.SOGOU.packageName)
        assertFalse(resetSogou.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertTrue(resetSogou.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK))
        assertTrue(resetSogou.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL))
        assertFalse(resetSogou.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL))

        // Package context & clipboard helpers remain strictly scoped to targetPackage
        val scopedContextOff = PrivacyConfig().withPackageContextRules(ImeTarget.XIAOAI.packageName, false)
        assertFalse(scopedContextOff.areAllPackageContextRulesEnabled(ImeTarget.XIAOAI.packageName))
        assertTrue(scopedContextOff.areAllPackageContextRulesEnabled(ImeTarget.SOGOU.packageName))
        assertTrue(scopedContextOff.areAllPackageContextRulesEnabled(ImeTarget.BAIDU.packageName))

        // Export JSON always includes config_version and rule_overrides
        val jsonObj = JSONObject(modified.toJson())
        assertEquals(2, jsonObj.getInt(PrivacyConfig.KEY_CONFIG_VERSION))
        val overridesObj = jsonObj.getJSONObject(PrivacyConfig.KEY_RULE_OVERRIDES)
        assertEquals(true, overridesObj.getBoolean(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertEquals(false, overridesObj.getBoolean(HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK))
        assertEquals(modified, PrivacyConfig.fromJson(jsonObj.toString()))
    }
}
