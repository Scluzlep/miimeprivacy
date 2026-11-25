package com.scluzlep.miimeprivacy.ui.viewmodel

import com.scluzlep.miimeprivacy.bridge.ServiceUiState
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.model.RuleCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyUiViewModelTest {

    @Test
    fun ruleStateReflectsPerRuleOverridesAndTargetManagedState() {
        val config = PrivacyConfig(
            sogouManaged = true,
            baiduManaged = false
        )
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS, true)
            .withRuleOverride(HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK, false)
            .withRuleOverride(HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL, true)

        val state = PrivacyUiStateBuilder.build(
            config = config,
            serviceState = ServiceUiState(),
            diagnosticsMap = mapOf(
                ImeTarget.SOGOU to ImeRuntimeDiagnostics(
                    target = ImeTarget.SOGOU,
                    cachedSymbolCount = 12,
                    rules = listOf(
                        HookRuleReport(
                            ruleId = HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS,
                            status = HookMatchStatus.ACTIVE,
                            source = "DEXKIT",
                            blockedCount = 4L
                        )
                    )
                )
            ),
            installedMap = emptyMap()
        )

        assertEquals(74, state.ruleItems.size)
        assertEquals(RuleCategory.entries.toSet(), state.ruleItems.map { it.spec.category }.toSet())

        val sogouHttpDns = state.ruleItems.first { it.spec.ruleId == HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS }
        assertTrue(sogouHttpDns.toggleChecked)
        assertTrue(sogouHttpDns.switchEnabled)
        assertTrue(sogouHttpDns.targetManaged)
        assertEquals(HookMatchStatus.ACTIVE, sogouHttpDns.effectiveStatus)

        val sogouPingback = state.ruleItems.first { it.spec.ruleId == HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK }
        assertFalse(sogouPingback.toggleChecked)
        assertFalse(sogouPingback.switchEnabled)
        assertEquals(HookMatchStatus.SKIPPED, sogouPingback.effectiveStatus)

        // When target is unmanaged, raw toggleChecked stays true while effective switchEnabled is false
        val baiduSms = state.ruleItems.first { it.spec.ruleId == HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL }
        assertTrue(baiduSms.toggleChecked)
        assertFalse(baiduSms.switchEnabled)
        assertFalse(baiduSms.targetManaged)
        assertEquals(HookMatchStatus.SKIPPED, baiduSms.effectiveStatus)

        val sogouOverview = PrivacyUiStateBuilder.summarizeTargetRules(state, ImeTarget.SOGOU)
        assertEquals(30, sogouOverview.totalCount)
        assertEquals(28, sogouOverview.enabledCount)
        assertEquals(4L, sogouOverview.blockedCount)
        assertTrue(sogouOverview.hasOverrides)
        assertTrue(sogouOverview.formattedSummary().startsWith("已启用 28/30 项 · 实测拦截 4 次"))

        val xiaoaiOverview = PrivacyUiStateBuilder.summarizeTargetRules(state, ImeTarget.XIAOAI)
        assertEquals(20, xiaoaiOverview.totalCount)
        assertEquals(17, xiaoaiOverview.enabledCount)
        assertFalse(xiaoaiOverview.hasOverrides)
    }

    @Test
    fun categoryLabelsAndImportJsonValidationWorkCleanly() {
        for (category in RuleCategory.entries) {
            val label = ruleCategoryLabel(category)
            assertTrue(label.isNotBlank())
            assertFalse(label.contains("\u7c73\u8f93\u51c0\u76fe"))
            assertFalse(label.contains("Mi IME" + " Plus"))
        }

        assertTrue(PrivacyUiStateBuilder.parseImportConfigJson("").isFailure)
        assertTrue(PrivacyUiStateBuilder.parseImportConfigJson("not-json").isFailure)

        val exported = PrivacyConfig()
            .withRuleOverride(HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL, true)
            .withRuleOverride(HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL, false)
            .toJson()

        val imported = PrivacyUiStateBuilder.parseImportConfigJson(exported).getOrThrow()
        assertEquals(2, imported.configVersion)
        assertTrue(imported.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL))
        assertFalse(imported.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL))
    }

    @Test
    fun ruleTargetFilterMapsAllThreeImesCleanly() {
        assertEquals(
            listOf(RuleTargetFilter.SOGOU, RuleTargetFilter.BAIDU, RuleTargetFilter.XIAOAI),
            RuleTargetFilter.targetTabs
        )
        assertEquals(RuleTargetFilter.SOGOU, RuleTargetFilter.fromTarget(ImeTarget.SOGOU))
        assertEquals(RuleTargetFilter.BAIDU, RuleTargetFilter.fromTarget(ImeTarget.BAIDU))
        assertEquals(RuleTargetFilter.XIAOAI, RuleTargetFilter.fromTarget(ImeTarget.XIAOAI))
    }
}
