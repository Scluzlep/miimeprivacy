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

        assertEquals(80, state.ruleItems.size)
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
        assertEquals(31, sogouOverview.totalCount)
        assertEquals(28, sogouOverview.enabledCount)
        assertEquals(4L, sogouOverview.blockedCount)
        assertTrue(sogouOverview.hasOverrides)
        assertTrue(sogouOverview.formattedSummary().startsWith("已启用 28/31 项 · 实测拦截 4 次"))

        val xiaoaiOverview = PrivacyUiStateBuilder.summarizeTargetRules(state, ImeTarget.XIAOAI)
        assertEquals(21, xiaoaiOverview.totalCount)
        assertEquals(17, xiaoaiOverview.enabledCount)
        assertFalse(xiaoaiOverview.hasOverrides)
    }

    @Test
    fun categoryLabelsAndImportJsonValidationWorkCleanly() {
        val expectedLabels = mapOf(
            RuleCategory.PRIVACY_GATE to "隐私门控",
            RuleCategory.DEVICE_ID to "设备标识",
            RuleCategory.TELEMETRY to "遥测与埋点",
            RuleCategory.CLOUD_PRIVACY to "云端脱敏",
            RuleCategory.CONTEXT_STRIP to "上下文剥离",
            RuleCategory.CLIPBOARD to "剪贴板",
            RuleCategory.SCAN_AND_LOCATION to "扫描与定位",
            RuleCategory.DYNAMIC_AND_NET to "动态加载与网络",
            RuleCategory.UI_ENHANCE to "界面与悬浮"
        )
        assertEquals(RuleCategory.entries.toSet(), expectedLabels.keys)
        for (category in RuleCategory.entries) {
            val label = ruleCategoryLabel(category)
            assertEquals(expectedLabels[category], label)
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
    fun ruleTargetFilterDefaultsToAllCollapsedAndMapsAllThreeImesCleanly() {
        val vmSource = listOf(
            java.io.File("src/main/java/com/scluzlep/miimeprivacy/ui/viewmodel/PrivacyUiViewModel.kt"),
            java.io.File("app/src/main/java/com/scluzlep/miimeprivacy/ui/viewmodel/PrivacyUiViewModel.kt")
        ).first { it.exists() }.readText()
        assertTrue(
            "Initial _selectedRuleFilter must be RuleTargetFilter.ALL",
            vmSource.contains("MutableStateFlow(RuleTargetFilter.ALL)")
        )
        assertEquals(null, RuleTargetFilter.ALL.target)
        assertEquals(
            listOf(RuleTargetFilter.SOGOU, RuleTargetFilter.BAIDU, RuleTargetFilter.XIAOAI),
            RuleTargetFilter.targetTabs
        )
        assertEquals(RuleTargetFilter.SOGOU, RuleTargetFilter.fromTarget(ImeTarget.SOGOU))
        assertEquals(RuleTargetFilter.BAIDU, RuleTargetFilter.fromTarget(ImeTarget.BAIDU))
        assertEquals(RuleTargetFilter.XIAOAI, RuleTargetFilter.fromTarget(ImeTarget.XIAOAI))
    }

    @Test
    fun actionFeedbackClearsOnTabSwitchDiagnosticsOpenAndClearFeedback() {
        val controller = PrivacyUiController()

        controller.notifyFeedback("已导出 v2 配置 JSON")
        assertEquals("已导出 v2 配置 JSON", controller.actionFeedback.value)

        controller.selectTab(MainTab.OVERLAY)
        assertEquals("", controller.actionFeedback.value)
        assertEquals(MainTab.OVERLAY, controller.selectedTab.value)

        controller.notifyFeedback("已重置搜狗输入法胶囊位置")
        controller.openDiagnosticsFor(ImeTarget.SOGOU)
        assertEquals("", controller.actionFeedback.value)
        assertEquals(MainTab.RULES, controller.selectedTab.value)
        assertEquals(RuleTargetFilter.SOGOU, controller.selectedRuleFilter.value)

        controller.notifyFeedback("已清除符号缓存")
        controller.clearFeedback()
        assertEquals("", controller.actionFeedback.value)
    }

    @Test
    fun rulesExpandAndSearchStateSurvivesTabSwitchWhileFilterIsOneShot() {
        val controller = PrivacyUiController()
        val telemetryRule = HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
            .first { it.category == RuleCategory.TELEMETRY }
        val state = PrivacyUiStateBuilder.build(
            config = PrivacyConfig(),
            serviceState = ServiceUiState(),
            diagnosticsMap = mapOf(
                ImeTarget.SOGOU to ImeRuntimeDiagnostics(
                    target = ImeTarget.SOGOU,
                    rules = listOf(
                        HookRuleReport(
                            ruleId = telemetryRule.ruleId,
                            status = HookMatchStatus.FAILED,
                            detail = "signature drift"
                        )
                    )
                )
            ),
            installedMap = emptyMap()
        )

        controller.updateRulesKeyword("clipboard")
        controller.toggleRuleTargetExpanded(ImeTarget.BAIDU)
        val baiduClipKey = ruleCategoryExpandKey(ImeTarget.BAIDU, RuleCategory.CLIPBOARD)
        controller.toggleRuleCategoryExpanded(baiduClipKey)

        // Normal tab switches preserve keyword and expand/collapse state and keep filter at ALL
        controller.selectTab(MainTab.OVERVIEW)
        controller.selectTab(MainTab.RULES)
        assertEquals("clipboard", controller.rulesKeyword.value)
        assertEquals(setOf(ImeTarget.BAIDU), controller.expandedRuleTargets.value)
        assertEquals(setOf(baiduClipKey), controller.expandedRuleCategories.value)
        assertEquals(RuleTargetFilter.ALL, controller.selectedRuleFilter.value)

        // One-shot diagnostic jump expands target + issue categories and consumes filter
        controller.openDiagnosticsFor(ImeTarget.SOGOU)
        assertEquals("", controller.rulesKeyword.value)
        assertEquals(RuleTargetFilter.SOGOU, controller.selectedRuleFilter.value)

        controller.expandDiagnosticTarget(ImeTarget.SOGOU, state.ruleItems)
        controller.consumeRuleFilter()
        assertEquals(RuleTargetFilter.ALL, controller.selectedRuleFilter.value)
        assertEquals(setOf(ImeTarget.SOGOU), controller.expandedRuleTargets.value)
        assertEquals(
            setOf(ruleCategoryExpandKey(ImeTarget.SOGOU, RuleCategory.TELEMETRY)),
            controller.expandedRuleCategories.value
        )

        // Returning to Rules tab later does not wipe user's manual collapses
        controller.collapseAllRules()
        controller.selectTab(MainTab.SETTINGS)
        controller.selectTab(MainTab.RULES)
        assertTrue(controller.expandedRuleTargets.value.isEmpty())
        assertTrue(controller.expandedRuleCategories.value.isEmpty())

        // Clicking "查看规则" a second time for the same IME triggers filter again cleanly
        controller.toggleOnlyInstallGated()
        assertTrue(controller.onlyInstallGated.value)
        controller.openDiagnosticsFor(ImeTarget.SOGOU)
        assertFalse(controller.onlyInstallGated.value)
        assertEquals(RuleTargetFilter.SOGOU, controller.selectedRuleFilter.value)
    }
}
