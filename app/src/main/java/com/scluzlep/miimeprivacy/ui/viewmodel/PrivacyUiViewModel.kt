package com.scluzlep.miimeprivacy.ui.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.scluzlep.miimeprivacy.bridge.ServiceUiState
import com.scluzlep.miimeprivacy.bridge.XposedServiceManager
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.HookRuleSpec
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MainTab(val title: String) {
    OVERVIEW("总览"),
    RULES("规则"),
    OVERLAY("胶囊"),
    EMOJI("表情"),
    SETTINGS("设置")
}

enum class RuleTargetFilter(val label: String, val target: ImeTarget?) {
    ALL("全部", null),
    SOGOU("搜狗", ImeTarget.SOGOU),
    BAIDU("百度", ImeTarget.BAIDU),
    XIAOAI("小爱", ImeTarget.XIAOAI)
}

enum class RuleHealthSeverity {
    HEALTHY,
    FALLBACK_OK,
    WARNING,
    CRITICAL_ERROR,
    NEUTRAL
}

data class InstalledImeInfo(
    val target: ImeTarget,
    val installed: Boolean = false,
    val versionName: String = "",
    val versionCode: Long = 0L
) {
    val isBaselineVersion: Boolean
        get() = installed && versionCode == target.expectedVersionCode

    fun formattedVersion(): String {
        if (!installed) return "未安装"
        val namePart = versionName.ifBlank { "未知版本" }
        return if (versionCode > 0L) "$namePart ($versionCode)" else namePart
    }
}

data class RunningTargetInfo(
    val processName: String,
    val pid: Int,
    val stateLabel: String,
    val loadedVersionCode: Long
) {
    companion object {
        fun fromHookedTarget(target: HookedTarget): RunningTargetInfo {
            val stateName = runCatching { target.state.name }.getOrDefault("UNKNOWN")
            return RunningTargetInfo(
                processName = runCatching { target.processName }.getOrDefault(""),
                pid = runCatching { target.pid }.getOrDefault(0),
                stateLabel = stateName,
                loadedVersionCode = runCatching { target.loadedVersionCode }.getOrDefault(0L)
            )
        }
    }
}

data class ImeHealthSummary(
    val severity: RuleHealthSeverity,
    val headline: String,
    val activeCount: Int = 0,
    val applicableCount: Int = 0,
    val fallbackCount: Int = 0,
    val degradedCount: Int = 0,
    val failedCount: Int = 0,
    val criticalFailedCount: Int = 0,
    val skippedCount: Int = 0,
    val pendingCount: Int = 0,
    val blockedCount: Long = 0L,
    val versionMismatch: Boolean = false
)

enum class PillVisualState(val emoji: String, val label: String) {
    CLOUD_MANAGED("🟢", "云输入"),
    LOCAL_MANAGED("🔒", "纯本地"),
    UNMANAGED_OR_ALERT("🔴", "未接管")
}

data class ImeCardUiModel(
    val target: ImeTarget,
    val installedInfo: InstalledImeInfo,
    val isInScope: Boolean,
    val runningProcesses: List<RunningTargetInfo>,
    val managed: Boolean,
    val cloudOverride: CloudOverride,
    val effectiveCloudEnabled: Boolean,
    val pillOffset: PillOffset,
    val diagnostics: ImeRuntimeDiagnostics?,
    val healthSummary: ImeHealthSummary,
    val pillState: PillVisualState
)

data class RuleDisplayItem(
    val spec: HookRuleSpec,
    val report: HookRuleReport?,
    val effectiveStatus: HookMatchStatus,
    val switchEnabled: Boolean
)

data class PrivacyUiState(
    val config: PrivacyConfig = PrivacyConfig(),
    val serviceState: ServiceUiState = ServiceUiState(),
    val runningTargets: List<RunningTargetInfo> = emptyList(),
    val imeCards: List<ImeCardUiModel> = emptyList(),
    val ruleItems: List<RuleDisplayItem> = emptyList(),
    val totalBlockedCount: Long = 0L,
    val scopeCoveredCount: Int = 0,
    val totalTargetCount: Int = ImeTarget.entries.size,
    val isScopeRequesting: Boolean = false,
    val isHotReloading: Boolean = false,
    val actionFeedback: String = ""
)

object PrivacyUiStateBuilder {

    fun isRuleSwitchEnabled(config: PrivacyConfig, spec: HookRuleSpec): Boolean {
        return when (spec.configSwitchKey) {
            PrivacyConfig.KEY_BLOCK_TELEMETRY -> config.blockTelemetry
            PrivacyConfig.KEY_BLOCK_PASSWORD_STATS -> config.blockPasswordStats
            PrivacyConfig.KEY_BLOCK_REMOTE_LOG_SALVAGE -> config.blockRemoteLogSalvage
            PrivacyConfig.KEY_SANITIZE_CLOUD_PAYLOAD -> config.sanitizeCloudPayload
            PrivacyConfig.KEY_CLIPBOARD_PRIVACY -> config.clipboardPrivacy
            PrivacyConfig.KEY_BLOCK_CONTACTS_AND_APP_SCAN -> config.blockContactsAndAppScan
            PrivacyConfig.KEY_BLOCK_UNSIGNED_DYNAMIC_DEX -> config.blockUnsignedDynamicDex
            PrivacyConfig.KEY_BLOCK_MUTUAL_DATA -> config.blockMutualData
            PrivacyConfig.KEY_BLOCK_HTTP_DNS -> config.blockHttpDns
            PrivacyConfig.KEY_BLOCK_XIAOAI_HOTWORD_TRACKER -> config.blockXiaoAiHotwordTracker
            PrivacyConfig.KEY_SHOW_KEYBOARD_PILL -> config.showKeyboardPill
            null -> true
            else -> true
        }
    }

    fun summarizeImeHealth(
        target: ImeTarget,
        managed: Boolean,
        installedInfo: InstalledImeInfo,
        isInScope: Boolean,
        diagnostics: ImeRuntimeDiagnostics?
    ): ImeHealthSummary {
        val catalogRules = HookRuleCatalog.rulesFor(target)
        val versionMismatch = installedInfo.installed &&
            installedInfo.versionCode > 0L &&
            installedInfo.versionCode != target.expectedVersionCode

        if (diagnostics == null) {
            val headline = when {
                !installedInfo.installed -> "未安装"
                !managed -> "已暂停接管"
                !isInScope -> "未加入作用域"
                else -> "等待启动 · 共 ${catalogRules.size} 项"
            }
            return ImeHealthSummary(
                severity = RuleHealthSeverity.NEUTRAL,
                headline = headline,
                pendingCount = catalogRules.size,
                versionMismatch = versionMismatch
            )
        }

        val reportMap = diagnostics.rules.associateBy { it.ruleId }
        var activeCount = 0
        var fallbackCount = 0
        var degradedCount = 0
        var failedCount = 0
        var criticalFailedCount = 0
        var skippedCount = 0
        var pendingCount = 0

        for (spec in catalogRules) {
            val status = reportMap[spec.ruleId]?.status ?: HookMatchStatus.PENDING
            when (status) {
                HookMatchStatus.ACTIVE -> activeCount++
                HookMatchStatus.FALLBACK -> {
                    activeCount++
                    fallbackCount++
                }
                HookMatchStatus.DEGRADED -> degradedCount++
                HookMatchStatus.FAILED -> {
                    failedCount++
                    if (spec.critical) criticalFailedCount++
                }
                HookMatchStatus.SKIPPED -> skippedCount++
                HookMatchStatus.PENDING -> pendingCount++
            }
        }

        val applicableCount = (catalogRules.size - skippedCount).coerceAtLeast(0)
        val blockedCount = diagnostics.totalBlockedCount

        val (severity, headline) = when {
            criticalFailedCount > 0 -> {
                RuleHealthSeverity.CRITICAL_ERROR to
                    "${criticalFailedCount} 项关键规则未命中 · $activeCount/$applicableCount 正常"
            }
            failedCount > 0 || degradedCount > 0 -> {
                val issueCount = failedCount + degradedCount
                RuleHealthSeverity.WARNING to
                    "$issueCount 项规则异常 · $activeCount/$applicableCount 正常"
            }
            applicableCount > 0 && activeCount == applicableCount -> {
                if (fallbackCount > 0) {
                    RuleHealthSeverity.FALLBACK_OK to
                        "$activeCount/$applicableCount 正常 · $fallbackCount 项兜底"
                } else {
                    RuleHealthSeverity.HEALTHY to "$activeCount/$applicableCount 正常"
                }
            }
            !managed -> {
                RuleHealthSeverity.NEUTRAL to "已暂停接管 · $skippedCount 项已跳过"
            }
            else -> {
                RuleHealthSeverity.NEUTRAL to
                    "待激活 · $activeCount 正常 · $pendingCount 待检"
            }
        }

        return ImeHealthSummary(
            severity = severity,
            headline = headline,
            activeCount = activeCount,
            applicableCount = applicableCount,
            fallbackCount = fallbackCount,
            degradedCount = degradedCount,
            failedCount = failedCount,
            criticalFailedCount = criticalFailedCount,
            skippedCount = skippedCount,
            pendingCount = pendingCount,
            blockedCount = blockedCount,
            versionMismatch = versionMismatch
        )
    }

    fun build(
        config: PrivacyConfig,
        serviceState: ServiceUiState,
        diagnosticsMap: Map<ImeTarget, ImeRuntimeDiagnostics>,
        installedMap: Map<ImeTarget, InstalledImeInfo>,
        runningTargetsOverride: List<RunningTargetInfo>? = null,
        isScopeRequesting: Boolean = false,
        isHotReloading: Boolean = false,
        actionFeedback: String = ""
    ): PrivacyUiState {
        val runningTargets = runningTargetsOverride
            ?: serviceState.runningTargets.map { RunningTargetInfo.fromHookedTarget(it) }
        val scopeSet = serviceState.scopePackages.toSet()

        val cards = ImeTarget.entries.map { target ->
            val installed = installedMap[target] ?: InstalledImeInfo(target = target)
            val isInScope = target.packageName in scopeSet
            val runningForTarget = runningTargets.filter { info ->
                info.processName == target.packageName ||
                    info.processName.startsWith("${target.packageName}:")
            }
            val managed = config.isImeManaged(target)
            val effectiveCloud = config.effectiveCloudEnabled(target)
            val diag = diagnosticsMap[target]
            val summary = summarizeImeHealth(
                target = target,
                managed = managed,
                installedInfo = installed,
                isInScope = isInScope,
                diagnostics = diag
            )
            val pillState = when {
                !managed -> PillVisualState.UNMANAGED_OR_ALERT
                effectiveCloud -> PillVisualState.CLOUD_MANAGED
                else -> PillVisualState.LOCAL_MANAGED
            }
            ImeCardUiModel(
                target = target,
                installedInfo = installed,
                isInScope = isInScope,
                runningProcesses = runningForTarget,
                managed = managed,
                cloudOverride = config.cloudOverrideFor(target),
                effectiveCloudEnabled = effectiveCloud,
                pillOffset = config.pillOffsetFor(target),
                diagnostics = diag,
                healthSummary = summary,
                pillState = pillState
            )
        }

        val ruleItems = HookRuleCatalog.allRules().map { spec ->
            val diag = diagnosticsMap[spec.target]
            val report = diag?.rules?.firstOrNull { it.ruleId == spec.ruleId }
            val switchEnabled = config.isImeManaged(spec.target) && isRuleSwitchEnabled(config, spec)
            val effectiveStatus = report?.status ?: if (!switchEnabled) {
                HookMatchStatus.SKIPPED
            } else {
                HookMatchStatus.PENDING
            }
            RuleDisplayItem(
                spec = spec,
                report = report,
                effectiveStatus = effectiveStatus,
                switchEnabled = switchEnabled
            )
        }

        val totalBlocked = diagnosticsMap.values.sumOf { it.totalBlockedCount }
        val scopeCovered = ImeTarget.entries.count { it.packageName in scopeSet }

        return PrivacyUiState(
            config = config,
            serviceState = serviceState,
            runningTargets = runningTargets,
            imeCards = cards,
            ruleItems = ruleItems,
            totalBlockedCount = totalBlocked,
            scopeCoveredCount = scopeCovered,
            totalTargetCount = ImeTarget.entries.size,
            isScopeRequesting = isScopeRequesting,
            isHotReloading = isHotReloading,
            actionFeedback = actionFeedback
        )
    }
}

/**
 * UI controller bridging [XposedServiceManager] flows, on-device IME package metadata,
 * and transient tab/filter/action feedback states for the Compose screens.
 */
class PrivacyUiController(context: Context) {

    private val appContext = context.applicationContext ?: context

    private val _selectedTab = MutableStateFlow(MainTab.OVERVIEW)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _selectedRuleFilter = MutableStateFlow(RuleTargetFilter.ALL)
    val selectedRuleFilter: StateFlow<RuleTargetFilter> = _selectedRuleFilter.asStateFlow()

    private val _installedImes = MutableStateFlow<Map<ImeTarget, InstalledImeInfo>>(emptyMap())
    val installedImes: StateFlow<Map<ImeTarget, InstalledImeInfo>> = _installedImes.asStateFlow()

    private val _isScopeRequesting = MutableStateFlow(false)
    val isScopeRequesting: StateFlow<Boolean> = _isScopeRequesting.asStateFlow()

    private val _isHotReloading = MutableStateFlow(false)
    val isHotReloading: StateFlow<Boolean> = _isHotReloading.asStateFlow()

    private val _actionFeedback = MutableStateFlow("")
    val actionFeedback: StateFlow<String> = _actionFeedback.asStateFlow()

    init {
        XposedServiceManager.init(appContext)
        refreshInstalledImes()
    }

    fun selectTab(tab: MainTab) {
        _selectedTab.value = tab
    }

    fun selectRuleFilter(filter: RuleTargetFilter) {
        _selectedRuleFilter.value = filter
    }

    fun openDiagnosticsFor(target: ImeTarget) {
        _selectedRuleFilter.value = when (target) {
            ImeTarget.SOGOU -> RuleTargetFilter.SOGOU
            ImeTarget.BAIDU -> RuleTargetFilter.BAIDU
            ImeTarget.XIAOAI -> RuleTargetFilter.XIAOAI
        }
        _selectedTab.value = MainTab.RULES
    }

    fun refreshAll() {
        XposedServiceManager.refreshServiceStatus()
        refreshInstalledImes()
    }

    fun refreshInstalledImes() {
        val pm = appContext.packageManager
        val result = linkedMapOf<ImeTarget, InstalledImeInfo>()
        for (target in ImeTarget.entries) {
            result[target] = queryPackageInfo(pm, target)
        }
        _installedImes.value = result
    }

    fun requestScopeForAll() {
        if (_isScopeRequesting.value) return
        _isScopeRequesting.value = true
        _actionFeedback.value = "正在申请作用域…"
        XposedServiceManager.requestScopeForAll { _, message ->
            _isScopeRequesting.value = false
            _actionFeedback.value = message
        }
    }

    fun hotReloadAll() {
        if (_isHotReloading.value) return
        _isHotReloading.value = true
        _actionFeedback.value = "正在热重载模块…"
        XposedServiceManager.hotReloadAll { message ->
            _isHotReloading.value = false
            _actionFeedback.value = message
        }
    }

    fun setAllowCloudInput(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(allowCloudInput = enabled) }
    }

    fun setImeManaged(target: ImeTarget, managed: Boolean) {
        XposedServiceManager.updateConfig { it.withImeManaged(target, managed) }
    }

    fun setImeCloudOverride(target: ImeTarget, override: CloudOverride) {
        XposedServiceManager.updateConfig { it.withCloudOverride(target, override) }
    }

    fun setBlockTelemetry(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockTelemetry = enabled) }
    }

    fun setBlockPasswordStats(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockPasswordStats = enabled) }
    }

    fun setBlockRemoteLogSalvage(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockRemoteLogSalvage = enabled) }
    }

    fun setBlockMutualData(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockMutualData = enabled) }
    }

    fun setSanitizeCloudPayload(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(sanitizeCloudPayload = enabled) }
    }

    fun setStripCursorContext(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(stripCursorContext = enabled) }
    }

    fun setMaskHostPackage(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(maskHostPackage = enabled) }
    }

    fun setStripTouchPositions(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(stripTouchPositions = enabled) }
    }

    fun setClipboardPrivacy(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(clipboardPrivacy = enabled) }
    }

    fun setBlockContactsAndAppScan(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockContactsAndAppScan = enabled) }
    }

    fun setBlockUnsignedDynamicDex(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockUnsignedDynamicDex = enabled) }
    }

    fun setBlockHttpDns(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockHttpDns = enabled) }
    }

    fun setBlockXiaoAiHotwordTracker(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(blockXiaoAiHotwordTracker = enabled) }
    }

    fun setShowKeyboardPill(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(showKeyboardPill = enabled) }
    }

    fun setHidePillInLandscape(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(hidePillInLandscape = enabled) }
    }

    fun setPillAlpha(alpha: Float) {
        XposedServiceManager.updateConfig { it.copy(pillAlpha = alpha) }
    }

    fun resetPillOffset(target: ImeTarget) {
        XposedServiceManager.updateConfig {
            it.withPillOffset(target, PillOffset.DEFAULT_OFFSET_X_DP, PillOffset.DEFAULT_OFFSET_Y_DP)
        }
        _actionFeedback.value = "已重置 ${target.displayName} 胶囊位置"
    }

    fun resetAllPillOffsets() {
        XposedServiceManager.updateConfig { current ->
            var next = current
            for (target in ImeTarget.entries) {
                next = next.withPillOffset(target, PillOffset.DEFAULT_OFFSET_X_DP, PillOffset.DEFAULT_OFFSET_Y_DP)
            }
            next
        }
        _actionFeedback.value = "已重置全部输入法胶囊位置"
    }

    fun setThemeMode(mode: ThemeMode) {
        XposedServiceManager.updateConfig { it.copy(themeMode = mode) }
    }

    fun setMonetEnabled(enabled: Boolean) {
        XposedServiceManager.updateConfig { it.copy(monetEnabled = enabled) }
    }

    fun setPaletteStyle(style: MonetPaletteStyle) {
        XposedServiceManager.updateConfig { it.copy(paletteStyle = style) }
    }

    @Suppress("DEPRECATION")
    private fun queryPackageInfo(pm: PackageManager, target: ImeTarget): InstalledImeInfo {
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(target.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getPackageInfo(target.packageName, 0)
            }
            InstalledImeInfo(
                target = target,
                installed = true,
                versionName = info.versionName.orEmpty(),
                versionCode = info.longVersionCode
            )
        }.getOrElse {
            InstalledImeInfo(target = target, installed = false)
        }
    }
}
