package com.scluzlep.miimeprivacy.bridge

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.service.quicksettings.TileService
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.tile.CloudToggleTileService
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.HotReloadResult
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class ServiceUiState(
    val isServiceBound: Boolean = false,
    val frameworkName: String = "",
    val frameworkVersion: String = "",
    val frameworkVersionCode: Long = 0L,
    val apiVersion: Int = 0,
    val scopePackages: List<String> = emptyList(),
    val runningTargets: List<HookedTarget> = emptyList(),
    val scopeRequestMessage: String = "",
    val hotReloadStatusMessage: String = ""
)

/**
 * Central coordinator in the module app process bridging `XposedService` (`RemotePreferences`,
 * scope queries, hot reload), local mirror preferences, IME diagnostics, and the QS Tile.
 */
object XposedServiceManager {

    private val initialized = AtomicBoolean(false)
    private val configLock = Any()
    private val diagLock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var mirrorPrefs: SharedPreferences? = null

    @Volatile
    private var diagnosticsPrefs: SharedPreferences? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var xposedService: XposedService? = null

    private val _config = MutableStateFlow(PrivacyConfig())
    val config: StateFlow<PrivacyConfig> = _config.asStateFlow()

    private val _serviceState = MutableStateFlow(ServiceUiState())
    val serviceState: StateFlow<ServiceUiState> = _serviceState.asStateFlow()

    private val _diagnostics = MutableStateFlow<Map<ImeTarget, ImeRuntimeDiagnostics>>(emptyMap())
    val diagnostics: StateFlow<Map<ImeTarget, ImeRuntimeDiagnostics>> = _diagnostics.asStateFlow()

    private val remotePrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, _ ->
        synchronized(configLock) {
            val remote = runCatching { PrivacyConfig.fromSharedPreferences(prefs) }.getOrNull() ?: return@synchronized
            val merged = PrivacyConfig.mergeNewer(_config.value, remote)
            if (merged != _config.value) {
                _config.value = merged
                mirrorPrefs?.let { runCatching { merged.toSharedPreferences(it, commitSync = false) } }
                requestTileUpdate()
            }
        }
    }

    private val serviceListener = object : XposedServiceHelper.OnServiceListener {
        override fun onServiceBind(service: XposedService) {
            xposedService = service
            val rPrefs = runCatching { service.getRemotePreferences(ConfigContract.REMOTE_PREFS_NAME) }.getOrNull()
            synchronized(configLock) {
                if (remotePrefs !== rPrefs) {
                    runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(remotePrefsListener) }
                    remotePrefs = rPrefs
                    if (rPrefs != null) {
                        runCatching { rPrefs.registerOnSharedPreferenceChangeListener(remotePrefsListener) }
                    }
                }
                if (rPrefs != null) {
                    val remote = runCatching { PrivacyConfig.fromSharedPreferences(rPrefs) }.getOrDefault(PrivacyConfig())
                    val local = _config.value
                    val merged = PrivacyConfig.mergeNewer(local, remote)
                    _config.value = merged
                    if (merged.updatedAtMs > remote.updatedAtMs || !rPrefs.contains(ConfigContract.KEY_CONFIG_JSON)) {
                        runCatching { merged.toSharedPreferences(rPrefs, commitSync = false) }
                    }
                    if (merged.updatedAtMs > local.updatedAtMs) {
                        mirrorPrefs?.let { runCatching { merged.toSharedPreferences(it, commitSync = false) } }
                    }
                }
            }
            refreshServiceState(service)
            requestTileUpdate()
        }

        override fun onServiceDied(service: XposedService) {
            if (xposedService === service) {
                synchronized(configLock) {
                    runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(remotePrefsListener) }
                    remotePrefs = null
                }
                xposedService = null
                _serviceState.value = ServiceUiState(isServiceBound = false)
            }
        }
    }

    fun init(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appCtx = context.applicationContext ?: context
        appContext = appCtx

        val mPrefs = appCtx.getSharedPreferences(ConfigContract.MIRROR_PREFS_NAME, Context.MODE_PRIVATE)
        val dPrefs = appCtx.getSharedPreferences(ConfigContract.DIAGNOSTICS_PREFS_NAME, Context.MODE_PRIVATE)
        mirrorPrefs = mPrefs
        diagnosticsPrefs = dPrefs

        synchronized(configLock) {
            _config.value = PrivacyConfig.fromSharedPreferences(mPrefs)
        }
        synchronized(diagLock) {
            val restored = buildMap {
                for (target in ImeTarget.entries) {
                    val raw = dPrefs.getString(ConfigContract.KEY_DIAGNOSTICS_PREFIX + target.name, null)
                    val parsed = ImeRuntimeDiagnostics.fromJson(raw)
                    if (parsed != null) put(target, parsed)
                }
            }
            _diagnostics.value = restored
        }

        runCatching {
            XposedServiceHelper.registerListener(serviceListener)
        }
    }

    fun updateConfig(newConfig: PrivacyConfig): PrivacyConfig {
        val committed: PrivacyConfig
        synchronized(configLock) {
            val stamp = PrivacyConfig.nextTimestamp(maxOf(_config.value.updatedAtMs, newConfig.updatedAtMs))
            committed = newConfig.sanitized().copy(updatedAtMs = stamp)
            _config.value = committed
            mirrorPrefs?.let { runCatching { committed.toSharedPreferences(it, commitSync = false) } }
            remotePrefs?.let { runCatching { committed.toSharedPreferences(it, commitSync = false) } }
        }
        requestTileUpdate()
        return committed
    }

    fun updateConfig(transform: (PrivacyConfig) -> PrivacyConfig): PrivacyConfig {
        val committed: PrivacyConfig
        synchronized(configLock) {
            val current = _config.value
            val candidate = transform(current)
            val stamp = PrivacyConfig.nextTimestamp(maxOf(current.updatedAtMs, candidate.updatedAtMs))
            committed = candidate.sanitized().copy(updatedAtMs = stamp)
            _config.value = committed
            mirrorPrefs?.let { runCatching { committed.toSharedPreferences(it, commitSync = false) } }
            remotePrefs?.let { runCatching { committed.toSharedPreferences(it, commitSync = false) } }
        }
        requestTileUpdate()
        return committed
    }

    fun onConfigPushedFromIme(pushedConfig: PrivacyConfig): PrivacyConfig {
        val merged: PrivacyConfig
        val changed: Boolean
        synchronized(configLock) {
            val current = _config.value
            merged = PrivacyConfig.mergeNewer(current, pushedConfig)
            changed = merged.updatedAtMs > current.updatedAtMs
            if (changed) {
                _config.value = merged
                mirrorPrefs?.let { runCatching { merged.toSharedPreferences(it, commitSync = false) } }
                remotePrefs?.let { runCatching { merged.toSharedPreferences(it, commitSync = false) } }
            }
        }
        if (changed) {
            requestTileUpdate()
        }
        return merged
    }

    fun onDiagnosticsPushedFromIme(diagnostics: ImeRuntimeDiagnostics) {
        synchronized(diagLock) {
            val currentMap = _diagnostics.value
            val existing = currentMap[diagnostics.target]
            if (existing != null && diagnostics.lastUpdatedAtMs < existing.lastUpdatedAtMs) {
                return
            }
            val nextMap = currentMap.toMutableMap().apply {
                put(diagnostics.target, diagnostics)
            }
            _diagnostics.value = nextMap
            diagnosticsPrefs?.edit()
                ?.putString(ConfigContract.KEY_DIAGNOSTICS_PREFIX + diagnostics.target.name, diagnostics.toJson())
                ?.apply()
        }
    }

    fun refreshServiceStatus() {
        val service = xposedService ?: return
        refreshServiceState(service)
    }

    fun requestScopeForAll(onResult: ((approved: Boolean, message: String) -> Unit)? = null) {
        val service = xposedService
        if (service == null) {
            val msg = "XposedService 未连接，请在 LSPosed 管理器中启用模块作用域"
            _serviceState.value = _serviceState.value.copy(scopeRequestMessage = msg)
            onResult?.invoke(false, msg)
            return
        }
        val targetPackages = ImeTarget.entries.map { it.packageName }
        runCatching {
            service.requestScope(targetPackages, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: MutableList<String>) {
                    refreshServiceState(service)
                    val msg = "已获作用域授权：${approved.joinToString()}"
                    _serviceState.value = _serviceState.value.copy(scopeRequestMessage = msg)
                    onResult?.invoke(true, msg)
                }

                override fun onScopeRequestFailed(message: String) {
                    val msg = "作用域申请失败：$message"
                    _serviceState.value = _serviceState.value.copy(scopeRequestMessage = msg)
                    onResult?.invoke(false, msg)
                }
            })
        }.onFailure { err ->
            val msg = "作用域申请异常：${err.message ?: err.javaClass.simpleName}"
            _serviceState.value = _serviceState.value.copy(scopeRequestMessage = msg)
            onResult?.invoke(false, msg)
        }
    }

    fun hotReloadAll(onComplete: ((String) -> Unit)? = null) {
        val service = xposedService
        if (service == null) {
            val msg = "XposedService 未连接，无法热重载"
            _serviceState.value = _serviceState.value.copy(hotReloadStatusMessage = msg)
            onComplete?.invoke(msg)
            return
        }
        val targets = runCatching { service.getRunningTargets() }.getOrDefault(emptyList())
        if (targets.isEmpty()) {
            val msg = "当前没有运行中的已注入输入法进程"
            _serviceState.value = _serviceState.value.copy(hotReloadStatusMessage = msg)
            onComplete?.invoke(msg)
            return
        }
        val remaining = AtomicInteger(targets.size)
        val summary = java.util.Collections.synchronizedList(mutableListOf<String>())
        for (target in targets) {
            runCatching {
                service.hotReloadModule(target, null) { hookedTarget, result ->
                    val status = result.status()
                    val detail = result.message().orEmpty()
                    summary.add(
                        "${hookedTarget.processName}: ${status.name}" +
                            if (detail.isNotBlank()) " ($detail)" else ""
                    )
                    if (remaining.decrementAndGet() == 0) {
                        refreshServiceState(service)
                        val combined = summary.joinToString("; ")
                        _serviceState.value = _serviceState.value.copy(hotReloadStatusMessage = combined)
                        onComplete?.invoke(combined)
                    }
                }
            }.onFailure { err ->
                summary.add("${target.processName}: FAILED (${err.message ?: "error"})")
                if (remaining.decrementAndGet() == 0) {
                    refreshServiceState(service)
                    val combined = summary.joinToString("; ")
                    _serviceState.value = _serviceState.value.copy(hotReloadStatusMessage = combined)
                    onComplete?.invoke(combined)
                }
            }
        }
    }

    fun requestTileUpdate() {
        val ctx = appContext ?: return
        runCatching {
            TileService.requestListeningState(
                ctx,
                ComponentName(ctx, CloudToggleTileService::class.java)
            )
        }
    }

    private fun refreshServiceState(service: XposedService) {
        val scope = runCatching { service.scope }.getOrDefault(emptyList())
        val targets = runCatching { service.getRunningTargets() }.getOrDefault(emptyList())
        _serviceState.value = _serviceState.value.copy(
            isServiceBound = true,
            frameworkName = runCatching { service.frameworkName }.getOrDefault(""),
            frameworkVersion = runCatching { service.frameworkVersion }.getOrDefault(""),
            frameworkVersionCode = runCatching { service.frameworkVersionCode }.getOrDefault(0L),
            apiVersion = runCatching { service.apiVersion }.getOrDefault(0),
            scopePackages = scope,
            runningTargets = targets
        )
    }
}
