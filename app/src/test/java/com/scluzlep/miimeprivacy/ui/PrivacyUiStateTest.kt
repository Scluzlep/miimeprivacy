package com.scluzlep.miimeprivacy.ui

import com.scluzlep.miimeprivacy.bridge.ServiceUiState
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.ui.theme.isDarkTheme
import com.scluzlep.miimeprivacy.ui.theme.paletteStyleDescription
import com.scluzlep.miimeprivacy.ui.theme.paletteStyleLabel
import com.scluzlep.miimeprivacy.ui.theme.resolveColorSchemeMode
import com.scluzlep.miimeprivacy.ui.theme.themeModeLabel
import com.scluzlep.miimeprivacy.ui.theme.toMiuixPaletteStyle
import com.scluzlep.miimeprivacy.ui.viewmodel.InstalledImeInfo
import com.scluzlep.miimeprivacy.ui.viewmodel.PillVisualState
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiStateBuilder
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleHealthSeverity
import com.scluzlep.miimeprivacy.ui.viewmodel.RunningTargetInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle

class PrivacyUiStateTest {

    @Test
    fun themeAndMonetMappingCoversAllModesAndPalettes() {
        assertEquals(ColorSchemeMode.MonetSystem, resolveColorSchemeMode(ThemeMode.SYSTEM, monetEnabled = true))
        assertEquals(ColorSchemeMode.MonetLight, resolveColorSchemeMode(ThemeMode.LIGHT, monetEnabled = true))
        assertEquals(ColorSchemeMode.MonetDark, resolveColorSchemeMode(ThemeMode.DARK, monetEnabled = true))
        assertEquals(ColorSchemeMode.System, resolveColorSchemeMode(ThemeMode.SYSTEM, monetEnabled = false))
        assertEquals(ColorSchemeMode.Light, resolveColorSchemeMode(ThemeMode.LIGHT, monetEnabled = false))
        assertEquals(ColorSchemeMode.Dark, resolveColorSchemeMode(ThemeMode.DARK, monetEnabled = false))

        assertTrue(isDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = true))
        assertFalse(isDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = false))
        assertFalse(isDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertTrue(isDarkTheme(ThemeMode.DARK, systemInDarkTheme = false))

        val expectedPalettes = mapOf(
            MonetPaletteStyle.TonalSpot to ThemePaletteStyle.TonalSpot,
            MonetPaletteStyle.Neutral to ThemePaletteStyle.Neutral,
            MonetPaletteStyle.Vibrant to ThemePaletteStyle.Vibrant,
            MonetPaletteStyle.Expressive to ThemePaletteStyle.Expressive,
            MonetPaletteStyle.Rainbow to ThemePaletteStyle.Rainbow,
            MonetPaletteStyle.FruitSalad to ThemePaletteStyle.FruitSalad,
            MonetPaletteStyle.Monochrome to ThemePaletteStyle.Monochrome,
            MonetPaletteStyle.Fidelity to ThemePaletteStyle.Fidelity,
            MonetPaletteStyle.Content to ThemePaletteStyle.Content
        )
        assertEquals(MonetPaletteStyle.entries.size, expectedPalettes.size)
        for (style in MonetPaletteStyle.entries) {
            assertEquals(expectedPalettes[style], style.toMiuixPaletteStyle())
            assertTrue(paletteStyleLabel(style).isNotBlank())
            assertTrue(paletteStyleDescription(style).isNotBlank())
        }
        for (mode in ThemeMode.entries) {
            assertTrue(themeModeLabel(mode).isNotBlank())
        }
    }

    @Test
    fun healthSummaryReportsHealthy8Of8WhenStrictHttpDnsSkipped() {
        val sogouRules = HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
        val reports = sogouRules.map { spec ->
            if (spec.ruleId == HookRuleCatalog.RULE_SOGOU_HTTPDNS) {
                HookRuleReport(ruleId = spec.ruleId, status = HookMatchStatus.SKIPPED)
            } else {
                HookRuleReport(
                    ruleId = spec.ruleId,
                    status = HookMatchStatus.ACTIVE,
                    matchedMethod = "Example#hook",
                    source = "FAST_PATH",
                    blockedCount = 2L
                )
            }
        }
        val diag = ImeRuntimeDiagnostics(
            target = ImeTarget.SOGOU,
            packageVersionCode = 20160L,
            packageVersionName = "11.50",
            rules = reports
        )
        val installed = InstalledImeInfo(
            target = ImeTarget.SOGOU,
            installed = true,
            versionName = "11.50",
            versionCode = 20160L
        )

        val summary = PrivacyUiStateBuilder.summarizeImeHealth(
            target = ImeTarget.SOGOU,
            managed = true,
            installedInfo = installed,
            isInScope = true,
            diagnostics = diag
        )

        assertEquals(RuleHealthSeverity.HEALTHY, summary.severity)
        assertEquals(8, summary.activeCount)
        assertEquals(8, summary.applicableCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(16L, summary.blockedCount)
        assertFalse(summary.versionMismatch)
        assertTrue(summary.headline.startsWith("8/8 正常"))
    }

    @Test
    fun healthSummaryFlagsCriticalFailureAndFallbackHonesty() {
        val xiaoaiRules = HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)
        val criticalFailReports = xiaoaiRules.map { spec ->
            if (spec.ruleId == HookRuleCatalog.RULE_XIAOAI_ONETRACK) {
                HookRuleReport(
                    ruleId = spec.ruleId,
                    status = HookMatchStatus.FAILED,
                    detail = "signature drift"
                )
            } else {
                HookRuleReport(ruleId = spec.ruleId, status = HookMatchStatus.ACTIVE)
            }
        }
        val installedMismatch = InstalledImeInfo(
            target = ImeTarget.XIAOAI,
            installed = true,
            versionName = "0.2.9999",
            versionCode = 29999L
        )

        val criticalSummary = PrivacyUiStateBuilder.summarizeImeHealth(
            target = ImeTarget.XIAOAI,
            managed = true,
            installedInfo = installedMismatch,
            isInScope = true,
            diagnostics = ImeRuntimeDiagnostics(target = ImeTarget.XIAOAI, rules = criticalFailReports)
        )
        assertEquals(RuleHealthSeverity.CRITICAL_ERROR, criticalSummary.severity)
        assertEquals(1, criticalSummary.criticalFailedCount)
        assertTrue(criticalSummary.versionMismatch)
        assertTrue(criticalSummary.headline.contains("关键规则未命中"))
        assertEquals("1 项关键规则未命中 · 5/6 正常", criticalSummary.headline)

        val fallbackReports = xiaoaiRules.map { spec ->
            if (spec.ruleId == HookRuleCatalog.RULE_XIAOAI_CLIPBOARD) {
                HookRuleReport(
                    ruleId = spec.ruleId,
                    status = HookMatchStatus.FALLBACK,
                    source = "STRUCTURAL_FALLBACK"
                )
            } else {
                HookRuleReport(ruleId = spec.ruleId, status = HookMatchStatus.ACTIVE)
            }
        }
        val fallbackSummary = PrivacyUiStateBuilder.summarizeImeHealth(
            target = ImeTarget.XIAOAI,
            managed = true,
            installedInfo = installedMismatch.copy(versionCode = 21363L),
            isInScope = true,
            diagnostics = ImeRuntimeDiagnostics(target = ImeTarget.XIAOAI, rules = fallbackReports)
        )
        assertEquals(RuleHealthSeverity.FALLBACK_OK, fallbackSummary.severity)
        assertEquals(1, fallbackSummary.fallbackCount)
        assertFalse(fallbackSummary.versionMismatch)
        assertTrue(fallbackSummary.headline.contains("兜底"))
    }

    @Test
    fun hookRuleCatalogCopyIsConciseAndFreeOfParentheses() {
        val rules = HookRuleCatalog.allRules()
        assertEquals(23, rules.size)
        for (spec in rules) {
            assertTrue("Title too long (${spec.ruleId}): ${spec.title}", spec.title.length <= 12)
            assertTrue("Description too long (${spec.ruleId}): ${spec.description}", spec.description.length <= 24)
            assertFalse("Parentheses in title (${spec.ruleId})", spec.title.contains('(') || spec.title.contains('（'))
            assertFalse("Parentheses in description (${spec.ruleId})", spec.description.contains('(') || spec.description.contains('（'))
        }
    }

    @Test
    fun uiStateBuilderComputesPillStatesTotalsAndRuleSwitches() {
        val config = PrivacyConfig(
            sogouManaged = true,
            baiduManaged = true,
            xiaoaiManaged = false,
            allowCloudInput = false,
            sogouCloudOverride = CloudOverride.FORCE_ON,
            baiduCloudOverride = CloudOverride.FOLLOW_GLOBAL,
            blockHttpDns = false,
            blockXiaoAiHotwordTracker = true
        )
        val serviceState = ServiceUiState(
            isServiceBound = true,
            frameworkName = "LSPosed",
            frameworkVersion = "1.10.1",
            apiVersion = 102,
            scopePackages = listOf(ImeTarget.SOGOU.packageName, ImeTarget.BAIDU.packageName)
        )
        val diagMap = mapOf(
            ImeTarget.SOGOU to ImeRuntimeDiagnostics(
                target = ImeTarget.SOGOU,
                rules = listOf(
                    HookRuleReport(HookRuleCatalog.RULE_SOGOU_CLOUD_SANITIZE, HookMatchStatus.ACTIVE, blockedCount = 10L)
                )
            ),
            ImeTarget.BAIDU to ImeRuntimeDiagnostics(
                target = ImeTarget.BAIDU,
                rules = listOf(
                    HookRuleReport(HookRuleCatalog.RULE_BAIDU_TELEMETRY, HookMatchStatus.ACTIVE, blockedCount = 5L)
                )
            )
        )
        val installedMap = mapOf(
            ImeTarget.SOGOU to InstalledImeInfo(ImeTarget.SOGOU, installed = true, versionName = "11.50", versionCode = 20160L),
            ImeTarget.BAIDU to InstalledImeInfo(ImeTarget.BAIDU, installed = true, versionName = "12.0", versionCode = 7108L),
            ImeTarget.XIAOAI to InstalledImeInfo(ImeTarget.XIAOAI, installed = false)
        )
        val running = listOf(
            RunningTargetInfo(processName = ImeTarget.SOGOU.packageName, pid = 4321, stateLabel = "ACTIVE", loadedVersionCode = 20160L)
        )

        val state = PrivacyUiStateBuilder.build(
            config = config,
            serviceState = serviceState,
            diagnosticsMap = diagMap,
            installedMap = installedMap,
            runningTargetsOverride = running
        )

        assertEquals(15L, state.totalBlockedCount)
        assertEquals(2, state.scopeCoveredCount)
        assertEquals(3, state.totalTargetCount)

        val sogouCard = state.imeCards.first { it.target == ImeTarget.SOGOU }
        val baiduCard = state.imeCards.first { it.target == ImeTarget.BAIDU }
        val xiaoaiCard = state.imeCards.first { it.target == ImeTarget.XIAOAI }

        assertEquals(PillVisualState.CLOUD_MANAGED, sogouCard.pillState)
        assertEquals(1, sogouCard.runningProcesses.size)
        assertEquals(PillVisualState.LOCAL_MANAGED, baiduCard.pillState)
        assertEquals(PillVisualState.UNMANAGED_OR_ALERT, xiaoaiCard.pillState)

        val sogouHttpDns = state.ruleItems.first { it.spec.ruleId == HookRuleCatalog.RULE_SOGOU_HTTPDNS }
        assertFalse(sogouHttpDns.switchEnabled)
        assertEquals(HookMatchStatus.SKIPPED, sogouHttpDns.effectiveStatus)

        val xiaoaiHotword = state.ruleItems.first { it.spec.ruleId == HookRuleCatalog.RULE_XIAOAI_HOTWORD_TRACKER }
        // xiaoaiManaged is false, so switchEnabled is false even though blockXiaoAiHotwordTracker is true
        assertFalse(xiaoaiHotword.switchEnabled)
    }
}
