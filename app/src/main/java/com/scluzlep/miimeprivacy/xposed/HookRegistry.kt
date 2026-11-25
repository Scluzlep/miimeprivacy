package com.scluzlep.miimeprivacy.xposed

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates hook installation, hot-reload handle replacement, per-rule status/block counters,
 * and throttled diagnostics sync from a hooked IME process back to the module app.
 */
class HookRegistry(
    private val xposed: XposedInterface,
    val target: ImeTarget,
    val appInfo: ApplicationInfo,
    val packageVersionCode: Long,
    val packageVersionName: String,
    val processName: String,
    private val oldById: MutableMap<String, XposedInterface.HookHandle>? = null,
    internal val dexKitExecutorOverride: DexKitExecutor? = null,
    internal val symbolPrefsOverride: SharedPreferences? = null,
    internal val onResolveTrace: ((SymbolResolutionTrace) -> Unit)? = null
) {
    private val activeHandles = linkedMapOf<String, XposedInterface.HookHandle>()
    private val ruleReports = ConcurrentHashMap<String, HookRuleReport>()
    private val subHookOutcomes = ConcurrentHashMap<String, MutableList<SubHookOutcome>>()
    private val blockCounters = ConcurrentHashMap<String, AtomicLong>()
    private val lastPushMs = AtomicLong(0L)

    @Volatile
    var attachedContext: Context? = null
        private set

    fun attachContext(context: Context?) {
        if (context != null) {
            attachedContext = context.applicationContext ?: context
        }
    }

    @Synchronized
    fun installOrReplaceHook(
        hookId: String,
        executable: Executable,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        hooker: XposedInterface.Hooker
    ): XposedInterface.HookHandle? {
        activeHandles[hookId]?.let { return it }
        val oldHandle = oldById?.remove(hookId)
        val handle = if (oldHandle != null) {
            runCatching {
                oldHandle.replaceHook(hooker)
            }.getOrElse {
                runCatching { oldHandle.unhook() }
                createNewHook(hookId, executable, priority, hooker)
            }
        } else {
            createNewHook(hookId, executable, priority, hooker)
        }
        activeHandles[hookId] = handle
        return handle
    }

    fun hookExecutable(
        ruleId: String,
        hookId: String,
        resolveResult: ResolveResult<out Executable>,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        onFailureStatus: HookMatchStatus = HookMatchStatus.FAILED,
        hooker: XposedInterface.Hooker
    ): Boolean {
        val executable = resolveResult.value
        if (executable == null) {
            recordSubHookOutcome(
                ruleId = ruleId,
                outcome = SubHookOutcome(
                    hookId = hookId,
                    status = onFailureStatus,
                    matchedMethod = "",
                    source = resolveResult.source.name,
                    detail = resolveResult.detail.ifBlank { "symbol not resolved" }
                )
            )
            return false
        }
        return runCatching {
            installOrReplaceHook(hookId, executable, priority, hooker)
            val status = if (resolveResult.source == ResolveSource.STRUCTURAL_FALLBACK) {
                HookMatchStatus.FALLBACK
            } else {
                HookMatchStatus.ACTIVE
            }
            recordSubHookOutcome(
                ruleId = ruleId,
                outcome = SubHookOutcome(
                    hookId = hookId,
                    status = status,
                    matchedMethod = resolveResult.detail.ifBlank { SymbolResolver.formatExecutable(executable) },
                    source = resolveResult.source.name,
                    detail = resolveResult.descriptor
                )
            )
            true
        }.getOrElse { err ->
            logWarn("Hook install failed for $hookId ($ruleId)", err)
            recordSubHookOutcome(
                ruleId = ruleId,
                outcome = SubHookOutcome(
                    hookId = hookId,
                    status = HookMatchStatus.FAILED,
                    matchedMethod = SymbolResolver.formatExecutable(executable),
                    source = resolveResult.source.name,
                    detail = err.message ?: err.javaClass.simpleName
                )
            )
            false
        }
    }

    fun hookMethod(
        ruleId: String,
        hookId: String,
        executable: Executable?,
        source: String = ResolveSource.FAST_PATH.name,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        hooker: XposedInterface.Hooker
    ): Boolean {
        val resolveSource = runCatching { ResolveSource.valueOf(source) }
            .getOrDefault(ResolveSource.FAST_PATH)
        val result = ResolveResult(
            value = executable,
            source = if (executable != null) resolveSource else ResolveSource.MISS,
            descriptor = executable?.let { SymbolResolver.executableToDescriptor(it) }.orEmpty(),
            detail = executable?.let { SymbolResolver.formatExecutable(it) }.orEmpty()
        )
        return hookExecutable(
            ruleId = ruleId,
            hookId = hookId,
            resolveResult = result,
            priority = priority,
            hooker = hooker
        )
    }

    fun hookAllMethods(
        ruleId: String,
        hookIdPrefix: String,
        clazz: Class<*>?,
        methodName: String,
        source: String = ResolveSource.FAST_PATH.name,
        priority: Int = XposedInterface.PRIORITY_DEFAULT,
        hooker: XposedInterface.Hooker
    ): Int {
        if (clazz == null) {
            recordSubHookOutcome(
                ruleId = ruleId,
                outcome = SubHookOutcome(
                    hookId = hookIdPrefix,
                    status = HookMatchStatus.FAILED,
                    matchedMethod = "",
                    source = ResolveSource.MISS.name,
                    detail = "missing class for $methodName"
                )
            )
            return 0
        }
        val lookup = collectOverloadsForHook(clazz, methodName, hookIdPrefix)
        if (lookup.failureOutcome != null) {
            if (lookup.error != null) {
                logWarn("declaredMethods failed for ${clazz.name}#$methodName", lookup.error)
            }
            recordSubHookOutcome(ruleId = ruleId, outcome = lookup.failureOutcome)
            return 0
        }
        val targets = lookup.targets
        var installed = 0
        targets.forEachIndexed { idx, method ->
            val subId = "$hookIdPrefix#$idx"
            val ok = runCatching {
                installOrReplaceHook(subId, method, priority, hooker)
                true
            }.getOrElse { err ->
                logWarn("hookAllMethods failed for ${clazz.name}#$methodName", err)
                recordSubHookOutcome(
                    ruleId = ruleId,
                    outcome = SubHookOutcome(
                        hookId = subId,
                        status = HookMatchStatus.FAILED,
                        matchedMethod = SymbolResolver.formatExecutable(method),
                        source = source,
                        detail = err.message ?: err.javaClass.simpleName
                    )
                )
                false
            }
            if (ok) {
                installed++
                recordSubHookOutcome(
                    ruleId = ruleId,
                    outcome = SubHookOutcome(
                        hookId = subId,
                        status = HookMatchStatus.ACTIVE,
                        matchedMethod = SymbolResolver.formatExecutable(method),
                        source = source,
                        detail = ""
                    )
                )
            }
        }
        return installed
    }

    fun reportRule(
        ruleId: String,
        status: HookMatchStatus,
        matchedMethod: String = "",
        source: String = "",
        detail: String = ""
    ) {
        if (status == HookMatchStatus.SKIPPED || status == HookMatchStatus.PENDING) {
            subHookOutcomes.remove(ruleId)
        }
        val count = blockCounters.computeIfAbsent(ruleId) { AtomicLong(0L) }.get()
        ruleReports[ruleId] = HookRuleReport(
            ruleId = ruleId,
            status = status,
            matchedMethod = matchedMethod,
            source = source,
            blockedCount = count,
            detail = detail
        )
    }

    fun reportSkipped(ruleId: String, reason: String = "规则已关闭") {
        reportRule(
            ruleId = ruleId,
            status = HookMatchStatus.SKIPPED,
            detail = reason
        )
    }

    inline fun installRuleIfEnabled(
        ruleId: String,
        disabledReason: String = "规则已关闭",
        block: () -> Unit
    ): Boolean {
        if (!ConfigBridge.isPointEnabled(target.packageName, ruleId)) {
            reportSkipped(ruleId, disabledReason)
            return false
        }
        block()
        return true
    }

    fun reportAllSkipped(reason: String, excludeRuleIds: Set<String> = emptySet()) {
        for (spec in HookRuleCatalog.rulesFor(target)) {
            if (spec.ruleId in excludeRuleIds) continue
            subHookOutcomes.remove(spec.ruleId)
            reportRule(
                ruleId = spec.ruleId,
                status = HookMatchStatus.SKIPPED,
                detail = reason
            )
        }
        val extraKeys = (ruleReports.keys + subHookOutcomes.keys)
            .filter { ruleId ->
                ruleId !in excludeRuleIds && HookRuleCatalog.rulesFor(target).none { it.ruleId == ruleId }
            }
        for (extraId in extraKeys) {
            subHookOutcomes.remove(extraId)
            reportRule(
                ruleId = extraId,
                status = HookMatchStatus.SKIPPED,
                detail = reason
            )
        }
    }

    @Synchronized
    fun resetTargetRuleReports(excludeRuleIds: Set<String> = emptySet()) {
        for (spec in HookRuleCatalog.rulesFor(target)) {
            if (spec.ruleId in excludeRuleIds) continue
            subHookOutcomes.remove(spec.ruleId)
            ruleReports.remove(spec.ruleId)
        }
        val extraKeys = (ruleReports.keys + subHookOutcomes.keys)
            .filter { ruleId ->
                ruleId !in excludeRuleIds && HookRuleCatalog.rulesFor(target).none { it.ruleId == ruleId }
            }
        for (extraId in extraKeys) {
            subHookOutcomes.remove(extraId)
            ruleReports.remove(extraId)
        }
    }

    private fun recordSubHookOutcome(ruleId: String, outcome: SubHookOutcome) {
        val snapshot: List<SubHookOutcome>
        val list = subHookOutcomes.computeIfAbsent(ruleId) { mutableListOf() }
        synchronized(list) {
            val existingIdx = list.indexOfFirst { it.hookId == outcome.hookId }
            if (existingIdx >= 0) {
                list[existingIdx] = outcome
            } else {
                list.add(outcome)
            }
            snapshot = list.toList()
        }
        val count = blockCounters.computeIfAbsent(ruleId) { AtomicLong(0L) }.get()
        ruleReports[ruleId] = aggregateSubHookOutcomes(ruleId, snapshot, count)
    }

    fun incrementBlocked(ruleId: String, delta: Long = 1L) {
        if (delta <= 0L) return
        val updated = blockCounters.computeIfAbsent(ruleId) { AtomicLong(0L) }.addAndGet(delta)
        ruleReports.compute(ruleId) { _, existing ->
            (existing ?: HookRuleReport(ruleId = ruleId, status = HookMatchStatus.ACTIVE))
                .copy(blockedCount = updated)
        }
        maybePushDiagnosticsThrottled()
    }

    fun getReport(ruleId: String): HookRuleReport? = ruleReports[ruleId]

    fun hasCriticalFailure(): Boolean {
        return ruleReports.values.any { report ->
            report.status == HookMatchStatus.FAILED &&
                (HookRuleCatalog.find(report.ruleId)?.critical == true)
        }
    }

    fun snapshotDiagnostics(
        activeCloudEnabled: Boolean = ConfigBridge.isCloudEnabled(target),
        note: String = ""
    ): ImeRuntimeDiagnostics {
        val orderedReports = HookRuleCatalog.rulesFor(target).map { spec ->
            val current = ruleReports[spec.ruleId]
            val count = blockCounters[spec.ruleId]?.get() ?: current?.blockedCount ?: 0L
            current?.copy(blockedCount = count) ?: HookRuleReport(
                ruleId = spec.ruleId,
                status = if (ConfigBridge.isPointEnabled(target.packageName, spec.ruleId)) {
                    HookMatchStatus.PENDING
                } else {
                    HookMatchStatus.SKIPPED
                },
                blockedCount = count
            )
        }
        val extraReports = ruleReports.values
            .filter { r -> orderedReports.none { it.ruleId == r.ruleId } }
            .map { r ->
                val count = blockCounters[r.ruleId]?.get() ?: r.blockedCount
                r.copy(blockedCount = count)
            }
        val cfg = ConfigBridge.current
        return ImeRuntimeDiagnostics(
            target = target,
            packageVersionCode = packageVersionCode,
            packageVersionName = packageVersionName,
            processName = processName,
            moduleApiVersion = xposed.apiVersion,
            frameworkName = runCatching { xposed.frameworkName }.getOrDefault(""),
            activeCloudEnabled = activeCloudEnabled,
            resolveMode = cfg.resolveModeFor(target).name,
            cachedSymbolCount = SymbolResolver.countCachedSymbols(attachedContext),
            lastUpdatedAtMs = System.currentTimeMillis(),
            rules = orderedReports + extraReports,
            note = note
        )
    }

    fun pushDiagnosticsNow(context: Context? = attachedContext, note: String = "") {
        val ctx = context ?: attachedContext ?: return
        lastPushMs.set(System.currentTimeMillis())
        ConfigBridge.pushDiagnosticsFromIme(ctx, snapshotDiagnostics(note = note))
    }

    private fun maybePushDiagnosticsThrottled() {
        val ctx = attachedContext ?: return
        val now = System.currentTimeMillis()
        val prev = lastPushMs.get()
        if (now - prev >= DIAGNOSTICS_PUSH_INTERVAL_MS && lastPushMs.compareAndSet(prev, now)) {
            ConfigBridge.pushDiagnosticsFromIme(ctx, snapshotDiagnostics())
        }
    }

    @Synchronized
    fun unhookRemaining() {
        val remaining = oldById ?: return
        for (handle in remaining.values) {
            runCatching { handle.unhook() }
        }
        remaining.clear()
    }

    @Synchronized
    fun unhookActiveTargetHooks(keepPrefixes: Set<String> = setOf("lifecycle:")) {
        val iterator = activeHandles.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (keepPrefixes.any { entry.key.startsWith(it) }) continue
            runCatching { entry.value.unhook() }
            iterator.remove()
        }
    }

    @Synchronized
    fun activeHookCount(): Int = activeHandles.size

    fun createSymbolResolver(): SymbolResolver {
        val moduleLibDir = runCatching { xposed.moduleApplicationInfo.nativeLibraryDir }.getOrNull()
        val cfg = ConfigBridge.current
        return SymbolResolver(
            context = attachedContext,
            classLoader = appInfo.className?.let { attachedContext?.classLoader }
                ?: attachedContext?.classLoader
                ?: ClassLoader.getSystemClassLoader(),
            appInfo = appInfo,
            versionCode = packageVersionCode,
            moduleNativeLibDir = moduleLibDir,
            resolveMode = cfg.resolveModeFor(target),
            requiredCacheResetAtMs = cfg.cacheResetAtMsFor(target),
            prefsOverride = symbolPrefsOverride,
            dexKitExecutorOverride = dexKitExecutorOverride,
            onResolveTrace = onResolveTrace
        )
    }

    fun createSymbolResolver(classLoader: ClassLoader): SymbolResolver {
        val moduleLibDir = runCatching { xposed.moduleApplicationInfo.nativeLibraryDir }.getOrNull()
        val cfg = ConfigBridge.current
        return SymbolResolver(
            context = attachedContext,
            classLoader = classLoader,
            appInfo = appInfo,
            versionCode = packageVersionCode,
            moduleNativeLibDir = moduleLibDir,
            resolveMode = cfg.resolveModeFor(target),
            requiredCacheResetAtMs = cfg.cacheResetAtMsFor(target),
            prefsOverride = symbolPrefsOverride,
            dexKitExecutorOverride = dexKitExecutorOverride,
            onResolveTrace = onResolveTrace
        )
    }

    fun logInfo(message: String) {
        xposed.log(Log.INFO, TAG, "[${target.name}] $message")
    }

    fun logWarn(message: String, error: Throwable? = null) {
        if (error != null) {
            xposed.log(Log.WARN, TAG, "[${target.name}] $message", error)
        } else {
            xposed.log(Log.WARN, TAG, "[${target.name}] $message")
        }
    }

    fun logDebug(message: String) {
        xposed.log(Log.DEBUG, TAG, "[${target.name}] $message")
    }

    private fun createNewHook(
        hookId: String,
        executable: Executable,
        priority: Int,
        hooker: XposedInterface.Hooker
    ): XposedInterface.HookHandle {
        return xposed.hook(executable)
            .setId(hookId)
            .setPriority(priority)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(hooker)
    }

    data class SubHookOutcome(
        val hookId: String,
        val status: HookMatchStatus,
        val matchedMethod: String = "",
        val source: String = "",
        val detail: String = ""
    )

    internal data class OverloadLookupResult(
        val targets: List<Method>,
        val failureOutcome: SubHookOutcome? = null,
        val error: Throwable? = null
    )

    companion object {
        private const val TAG = "MiImePrivacy"
        private const val DIAGNOSTICS_PUSH_INTERVAL_MS = 1500L

        internal fun collectOverloadsForHook(
            clazz: Class<*>,
            methodName: String,
            hookIdPrefix: String
        ): OverloadLookupResult {
            val declaredMethods = runCatching { clazz.declaredMethods.toList() }.getOrElse { error ->
                return OverloadLookupResult(
                    targets = emptyList(),
                    failureOutcome = SubHookOutcome(
                        hookId = hookIdPrefix,
                        status = HookMatchStatus.FAILED,
                        matchedMethod = "${clazz.name}#$methodName",
                        source = ResolveSource.MISS.name,
                        detail = "declaredMethods reflection failed: ${error.javaClass.simpleName}: ${error.message ?: "unknown"}"
                    ),
                    error = error
                )
            }
            val targets = declaredMethods.filter { it.name == methodName }
            if (targets.isEmpty()) {
                return OverloadLookupResult(
                    targets = emptyList(),
                    failureOutcome = SubHookOutcome(
                        hookId = hookIdPrefix,
                        status = HookMatchStatus.FAILED,
                        matchedMethod = "${clazz.name}#$methodName",
                        source = ResolveSource.MISS.name,
                        detail = "no overloads matched"
                    )
                )
            }
            return OverloadLookupResult(targets = targets)
        }

        fun aggregateSubHookOutcomes(
            ruleId: String,
            outcomes: List<SubHookOutcome>,
            blockedCount: Long = 0L
        ): HookRuleReport {
            if (outcomes.isEmpty()) {
                return HookRuleReport(
                    ruleId = ruleId,
                    status = HookMatchStatus.PENDING,
                    blockedCount = blockedCount
                )
            }
            val failed = outcomes.filter { it.status == HookMatchStatus.FAILED }
            val degraded = outcomes.filter { it.status == HookMatchStatus.DEGRADED }
            val succeeded = outcomes.filter {
                it.status == HookMatchStatus.ACTIVE ||
                    it.status == HookMatchStatus.FALLBACK
            }
            val aggregatedStatus = when {
                succeeded.isEmpty() && failed.isNotEmpty() -> HookMatchStatus.FAILED
                degraded.isNotEmpty() || failed.isNotEmpty() -> HookMatchStatus.DEGRADED
                succeeded.isEmpty() -> {
                    if (outcomes.all { it.status == HookMatchStatus.SKIPPED }) {
                        HookMatchStatus.SKIPPED
                    } else {
                        HookMatchStatus.PENDING
                    }
                }
                outcomes.any { it.status == HookMatchStatus.FALLBACK } -> HookMatchStatus.FALLBACK
                else -> HookMatchStatus.ACTIVE
            }

            val distinctMethods = outcomes.map { it.matchedMethod }.filter { it.isNotBlank() }.distinct()
            val matchedMethodSummary = when {
                distinctMethods.isEmpty() -> ""
                distinctMethods.size == 1 && outcomes.size == 1 -> distinctMethods.first()
                else -> "${succeeded.size}/${outcomes.size} (${distinctMethods.take(3).joinToString(", ")})"
            }
            val sourceSummary = outcomes.map { it.source }.filter { it.isNotBlank() }.distinct().joinToString(",")
            val issues = failed + degraded
            val detailSummary = if (issues.isNotEmpty()) {
                issues.joinToString("; ") { "${it.hookId}: ${it.detail.ifBlank { "未命中" }}" }
            } else {
                outcomes.map { it.detail }.firstOrNull { it.isNotBlank() }.orEmpty()
            }

            return HookRuleReport(
                ruleId = ruleId,
                status = aggregatedStatus,
                matchedMethod = matchedMethodSummary,
                source = sourceSummary,
                blockedCount = blockedCount,
                detail = detailSummary
            )
        }
    }
}
