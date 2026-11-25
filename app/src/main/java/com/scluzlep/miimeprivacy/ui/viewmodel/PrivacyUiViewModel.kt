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
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.HookRuleSpec
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.model.RuleCategory
import io.github.libxposed.service.HookedTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

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
    XIAOAI("小爱", ImeTarget.XIAOAI);

    companion object {
        val targetTabs: List<RuleTargetFilter> = listOf(SOGOU, BAIDU, XIAOAI)

        fun fromTarget(target: ImeTarget): RuleTargetFilter = when (target) {
            ImeTarget.SOGOU -> SOGOU
            ImeTarget.BAIDU -> BAIDU
            ImeTarget.XIAOAI -> XIAOAI
        }
    }
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

data class SymbolResolutionSummary(
    val cachedSymbolCount: Int = 0,
    val activeResolveMethodText: String = "待启动",
    val cardResolveSummaryText: String = "自动 · 待启动",
    val canClearSymbolCache: Boolean = false
)

data class TargetRuleOverview(
    val target: ImeTarget,
    val managed: Boolean,
    val enabledCount: Int,
    val totalCount: Int,
    val blockedCount: Long,
    val resolveSummaryText: String,
    val hasOverrides: Boolean
) {
    fun formattedSummary(): String {
        return "已启用 $enabledCount/$totalCount 项 · 实测拦截 $blockedCount 次 · $resolveSummaryText"
    }
}

fun ruleCategoryLabel(category: RuleCategory): String = when (category) {
    RuleCategory.PRIVACY_GATE -> "隐私门控"
    RuleCategory.DEVICE_ID -> "设备标识"
    RuleCategory.TELEMETRY -> "遥测与埋点"
    RuleCategory.CLOUD_PRIVACY -> "云端脱敏"
    RuleCategory.CONTEXT_STRIP -> "上下文剥离"
    RuleCategory.CLIPBOARD -> "剪贴板"
    RuleCategory.SCAN_AND_LOCATION -> "扫描与定位"
    RuleCategory.DYNAMIC_AND_NET -> "动态加载与网络"
    RuleCategory.UI_ENHANCE -> "界面与悬浮"
}

/**
 * Resets all rule overrides belonging to [targetPackage] back to their catalog defaults
 * without altering overrides for other target packages.
 */
fun PrivacyConfig.resetPackageDefaults(targetPackage: String): PrivacyConfig {
    val targetRuleIds = HookRuleCatalog.rulesForPackage(targetPackage)
        .map { it.ruleId }
        .toSet()
    if (targetRuleIds.isEmpty()) return this
    val remaining = ruleOverrides.filterKeys { it !in targetRuleIds }
    return if (remaining.size == ruleOverrides.size) {
        this
    } else {
        copy(ruleOverrides = remaining)
    }
}

internal fun parseSourceTokens(rawSource: String): List<String> {
    if (rawSource.isBlank()) return emptyList()
    return rawSource.split(',')
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .distinct()
}

internal fun formatSourceTokenLabel(token: String): String {
    return when (token.trim().uppercase()) {
        "DEXKIT" -> "DexKit"
        "CACHE" -> "缓存"
        "STRUCTURAL_FALLBACK" -> "结构兜底"
        "FAST_PATH" -> "FastPath"
        else -> token.trim()
    }
}

internal fun pickPrimarySourceToken(tokens: Collection<String>): String {
    val normalized = tokens.map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()
    return when {
        "DEXKIT" in normalized -> "DEXKIT"
        "CACHE" in normalized -> "CACHE"
        "STRUCTURAL_FALLBACK" in normalized -> "STRUCTURAL_FALLBACK"
        "FAST_PATH" in normalized -> "FAST_PATH"
        else -> normalized.firstOrNull().orEmpty()
    }
}

data class ImeCardUiModel(
    val target: ImeTarget,
    val installedInfo: InstalledImeInfo,
    val isInScope: Boolean,
    val runningProcesses: List<RunningTargetInfo>,
    val managed: Boolean,
    val cloudOverride: CloudOverride,
    val effectiveCloudEnabled: Boolean,
    val resolveMode: SymbolResolveMode = SymbolResolveMode.AUTO,
    val cachedSymbolCount: Int = 0,
    val activeResolveMethodText: String = "待启动",
    val cardResolveSummaryText: String = "自动 · 待启动",
    val canClearSymbolCache: Boolean = false,
    val pillOffset: PillOffset,
    val diagnostics: ImeRuntimeDiagnostics?,
    val healthSummary: ImeHealthSummary,
    val pillState: PillVisualState
)

data class RuleDisplayItem(
    val spec: HookRuleSpec,
    val report: HookRuleReport?,
    val effectiveStatus: HookMatchStatus,
    val switchEnabled: Boolean,
    val toggleChecked: Boolean = switchEnabled,
    val targetManaged: Boolean = true
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
        return config.isRuleToggleChecked(spec.ruleId)
    }

    fun summarizeTargetRules(state: PrivacyUiState, target: ImeTarget): TargetRuleOverview {
        val targetItems = state.ruleItems.filter { it.spec.target == target }
        val card = state.imeCards.firstOrNull { it.target == target }
        val targetRuleIds = targetItems.map { it.spec.ruleId }.toSet()
        val hasOverrides = state.config.ruleOverrides.keys.any { it in targetRuleIds }
        return TargetRuleOverview(
            target = target,
            managed = state.config.isImeManaged(target),
            enabledCount = targetItems.count { it.switchEnabled },
            totalCount = targetItems.size,
            blockedCount = card?.healthSummary?.blockedCount ?: 0L,
            resolveSummaryText = card?.cardResolveSummaryText ?: "自动 · 待启动",
            hasOverrides = hasOverrides
        )
    }

    fun parseImportConfigJson(rawJson: String): Result<PrivacyConfig> {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("配置内容为空"))
        }
        val jsonObj = runCatching { JSONObject(trimmed) }.getOrElse { err ->
            return Result.failure(IllegalArgumentException("JSON 解析失败：${err.message ?: "格式错误"}"))
        }
        return Result.success(PrivacyConfig.fromJsonObject(jsonObj))
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
            val resolveMode = config.resolveModeFor(target)
            val diag = diagnosticsMap[target]
            val resolveSummary = summarizeSymbolResolution(target, resolveMode, diag)
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
                resolveMode = resolveMode,
                cachedSymbolCount = resolveSummary.cachedSymbolCount,
                activeResolveMethodText = resolveSummary.activeResolveMethodText,
                cardResolveSummaryText = resolveSummary.cardResolveSummaryText,
                canClearSymbolCache = resolveSummary.canClearSymbolCache,
                pillOffset = config.pillOffsetFor(target),
                diagnostics = diag,
                healthSummary = summary,
                pillState = pillState
            )
        }

        val ruleItems = HookRuleCatalog.allRules().map { spec ->
            val diag = diagnosticsMap[spec.target]
            val report = diag?.rules?.firstOrNull { it.ruleId == spec.ruleId }
            val targetManaged = config.isImeManaged(spec.target)
            val toggleChecked = isRuleSwitchEnabled(config, spec)
            val switchEnabled = config.isRuleEnabled(spec.ruleId)
            val effectiveStatus = report?.status ?: if (!switchEnabled) {
                HookMatchStatus.SKIPPED
            } else {
                HookMatchStatus.PENDING
            }
            RuleDisplayItem(
                spec = spec,
                report = report,
                effectiveStatus = effectiveStatus,
                switchEnabled = switchEnabled,
                toggleChecked = toggleChecked,
                targetManaged = targetManaged
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

    fun summarizeSymbolResolution(
        target: ImeTarget,
        resolveMode: SymbolResolveMode,
        diagnostics: ImeRuntimeDiagnostics?
    ): SymbolResolutionSummary {
        val cachedCount = (diagnostics?.cachedSymbolCount ?: 0).coerceAtLeast(0)
        val overlayRuleId = HookRuleCatalog.overlayRuleIdFor(target)
        val tokens = diagnostics?.rules.orEmpty()
            .asSequence()
            .filter { it.ruleId != overlayRuleId }
            .filter {
                it.status == HookMatchStatus.ACTIVE ||
                    it.status == HookMatchStatus.FALLBACK ||
                    it.status == HookMatchStatus.DEGRADED
            }
            .flatMap { parseSourceTokens(it.source).asSequence() }
            .toSet()

        val primaryToken = pickPrimarySourceToken(tokens)
        val (methodText, shortText) = when (primaryToken) {
            "DEXKIT" -> "DexKit 实时搜索" to "DexKit"
            "CACHE" -> "DexKit 符号缓存" to "缓存"
            "STRUCTURAL_FALLBACK" -> "结构兜底" to "结构兜底"
            "FAST_PATH" -> "FastPath 反射" to "FastPath"
            else -> "待启动" to "待启动"
        }
        val modeLabel = if (resolveMode == SymbolResolveMode.DYNAMIC) "动态" else "自动"
        val cardSummary = when {
            shortText == "缓存" && cachedCount > 0 -> "$modeLabel · 缓存 $cachedCount 项"
            cachedCount > 0 -> "$modeLabel · $shortText · 缓存 $cachedCount 项"
            else -> "$modeLabel · $shortText"
        }
        return SymbolResolutionSummary(
            cachedSymbolCount = cachedCount,
            activeResolveMethodText = methodText,
            cardResolveSummaryText = cardSummary,
            canClearSymbolCache = resolveMode == SymbolResolveMode.DYNAMIC || cachedCount > 0
        )
    }
}

internal fun ruleCategoryExpandKey(target: ImeTarget, category: RuleCategory): String =
    "${target.name}:${category.name}"

/**
 * UI controller bridging [XposedServiceManager] flows, on-device IME package metadata,
 * and transient tab/filter/action feedback states for the Compose screens.
 */
class PrivacyUiController private constructor(
    private val appContext: Context?,
    initBridge: Boolean
) {

    constructor(context: Context) : this(
        appContext = context.applicationContext ?: context,
        initBridge = true
    )

    internal constructor() : this(
        appContext = null,
        initBridge = false
    )

    private val _selectedTab = MutableStateFlow(MainTab.OVERVIEW)
    val selectedTab: StateFlow<MainTab> = _selectedTab.asStateFlow()

    private val _selectedRuleFilter = MutableStateFlow(RuleTargetFilter.ALL)
    val selectedRuleFilter: StateFlow<RuleTargetFilter> = _selectedRuleFilter.asStateFlow()

    private val _rulesKeyword = MutableStateFlow("")
    val rulesKeyword: StateFlow<String> = _rulesKeyword.asStateFlow()

    private val _onlyInstallGated = MutableStateFlow(false)
    val onlyInstallGated: StateFlow<Boolean> = _onlyInstallGated.asStateFlow()

    private val _expandedRuleTargets = MutableStateFlow<Set<ImeTarget>>(emptySet())
    val expandedRuleTargets: StateFlow<Set<ImeTarget>> = _expandedRuleTargets.asStateFlow()

    private val _expandedRuleCategories = MutableStateFlow<Set<String>>(emptySet())
    val expandedRuleCategories: StateFlow<Set<String>> = _expandedRuleCategories.asStateFlow()

    private val _installedImes = MutableStateFlow<Map<ImeTarget, InstalledImeInfo>>(emptyMap())
    val installedImes: StateFlow<Map<ImeTarget, InstalledImeInfo>> = _installedImes.asStateFlow()

    private val _isScopeRequesting = MutableStateFlow(false)
    val isScopeRequesting: StateFlow<Boolean> = _isScopeRequesting.asStateFlow()

    private val _isHotReloading = MutableStateFlow(false)
    val isHotReloading: StateFlow<Boolean> = _isHotReloading.asStateFlow()

    private val _actionFeedback = MutableStateFlow("")
    val actionFeedback: StateFlow<String> = _actionFeedback.asStateFlow()

    init {
        if (initBridge && appContext != null) {
            XposedServiceManager.init(appContext)
            refreshInstalledImes()
        }
    }

    fun selectTab(tab: MainTab) {
        _actionFeedback.value = ""
        _selectedRuleFilter.value = RuleTargetFilter.ALL
        _selectedTab.value = tab
    }

    fun selectRuleFilter(filter: RuleTargetFilter) {
        _selectedRuleFilter.value = filter
    }

    fun selectRuleTarget(target: ImeTarget) {
        _selectedRuleFilter.value = RuleTargetFilter.fromTarget(target)
    }

    fun openDiagnosticsFor(target: ImeTarget) {
        _actionFeedback.value = ""
        _rulesKeyword.value = ""
        _onlyInstallGated.value = false
        _selectedRuleFilter.value = RuleTargetFilter.fromTarget(target)
        _selectedTab.value = MainTab.RULES
    }

    fun consumeRuleFilter() {
        _selectedRuleFilter.value = RuleTargetFilter.ALL
    }

    fun clearFeedback() {
        _actionFeedback.value = ""
    }

    fun updateRulesKeyword(keyword: String) {
        _rulesKeyword.value = keyword
    }

    fun toggleOnlyInstallGated() {
        _onlyInstallGated.value = !_onlyInstallGated.value
    }

    fun toggleRuleTargetExpanded(target: ImeTarget) {
        val current = _expandedRuleTargets.value
        _expandedRuleTargets.value = if (target in current) current - target else current + target
    }

    fun toggleRuleCategoryExpanded(key: String) {
        val current = _expandedRuleCategories.value
        _expandedRuleCategories.value = if (key in current) current - key else current + key
    }

    fun setRuleCategoriesExpanded(keys: Collection<String>, expanded: Boolean) {
        val current = _expandedRuleCategories.value
        _expandedRuleCategories.value = if (expanded) current + keys else current - keys.toSet()
    }

    fun collapseAllRules() {
        _expandedRuleTargets.value = emptySet()
        _expandedRuleCategories.value = emptySet()
    }

    fun expandDiagnosticTarget(target: ImeTarget, ruleItems: List<RuleDisplayItem>) {
        _expandedRuleTargets.value = setOf(target)
        val issueKeys = ruleItems.asSequence()
            .filter {
                it.spec.target == target &&
                    (it.effectiveStatus == HookMatchStatus.FAILED || it.effectiveStatus == HookMatchStatus.DEGRADED)
            }
            .map { ruleCategoryExpandKey(target, it.spec.category) }
            .toSet()
        _expandedRuleCategories.value = issueKeys
    }

    fun refreshAll() {
        XposedServiceManager.refreshServiceStatus()
        refreshInstalledImes()
    }

    fun refreshInstalledImes() {
        val pm = appContext?.packageManager ?: return
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

    fun setImeResolveMode(target: ImeTarget, mode: SymbolResolveMode) {
        XposedServiceManager.updateConfig { it.withResolveMode(target, mode) }
        val modeLabel = if (mode == SymbolResolveMode.DYNAMIC) "动态 DexKit" else "自动 FastPath"
        _actionFeedback.value = "${target.displayName} 已切换为$modeLabel"
    }

    fun clearImeSymbolCache(target: ImeTarget) {
        _actionFeedback.value = XposedServiceManager.clearSymbolCacheForTarget(target)
    }

    fun clearAllSymbolCaches() {
        for (target in ImeTarget.entries) {
            XposedServiceManager.clearSymbolCacheForTarget(target)
        }
        _actionFeedback.value = "已清除全部输入法 DexKit 符号缓存"
    }

    fun setRuleEnabled(ruleId: String, enabled: Boolean) {
        XposedServiceManager.updateConfig { it.withRuleOverride(ruleId, enabled) }
    }

    fun resetPackageDefaults(targetPackage: String) {
        val target = ImeTarget.fromPackage(targetPackage)
        XposedServiceManager.updateConfig { it.resetPackageDefaults(targetPackage) }
        val name = target?.displayName ?: targetPackage
        _actionFeedback.value = "已恢复 $name 默认规则"
    }

    fun resetTargetDefaults(target: ImeTarget) {
        resetPackageDefaults(target.packageName)
    }

    fun exportConfigJson(): String {
        val cfg = XposedServiceManager.config.value
        val json = cfg.toJson()
        _actionFeedback.value = "已生成 v${cfg.configVersion} 配置（含 ${cfg.ruleOverrides.size} 项规则覆盖）"
        return json
    }

    fun importConfigJson(rawJson: String): Boolean {
        val parsed = PrivacyUiStateBuilder.parseImportConfigJson(rawJson).getOrElse { err ->
            _actionFeedback.value = err.message ?: "配置 JSON 格式无效"
            return false
        }
        val committed = XposedServiceManager.updateConfig(parsed)
        _actionFeedback.value = "已导入 v${committed.configVersion} 配置（含 ${committed.ruleOverrides.size} 项规则覆盖）"
        return true
    }

    fun notifyFeedback(message: String) {
        _actionFeedback.value = message
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
