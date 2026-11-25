package com.scluzlep.miimeprivacy.xposed

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import io.github.libxposed.api.XposedInterface
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Process-local config & diagnostics bridge inside each hooked IME process.
 * Merges `XposedInterface.getRemotePreferences(REMOTE_PREFS_NAME)` with the IME's local
 * `LOCAL_OVERRIDE_PREFS_NAME` using monotonic `updatedAtMs` timestamps, and pushes local
 * quick-pill overrides & diagnostics back to the module app via explicit broadcast + provider call.
 */
object ConfigBridge {

    private val lock = Any()
    private val listeners = CopyOnWriteArrayList<(oldConfig: PrivacyConfig, newConfig: PrivacyConfig) -> Unit>()
    private val syncExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "miime-sync").apply { isDaemon = true }
    }

    @Volatile
    var current: PrivacyConfig = PrivacyConfig()
        private set

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var remotePrefs: SharedPreferences? = null

    @Volatile
    private var localOverridePrefs: SharedPreferences? = null

    @Volatile
    private var remoteConfig: PrivacyConfig = PrivacyConfig()

    @Volatile
    private var localConfig: PrivacyConfig = PrivacyConfig()

    private val remoteListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadMergedLocked(fromRemote = true)
    }

    private val localListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        reloadMergedLocked(fromRemote = false)
    }

    fun attachRemote(xposed: XposedInterface) {
        val prefs = runCatching {
            xposed.getRemotePreferences(ConfigContract.REMOTE_PREFS_NAME)
        }.getOrNull() ?: return

        val transition: Pair<PrivacyConfig, PrivacyConfig>
        synchronized(lock) {
            if (remotePrefs !== prefs) {
                runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(remoteListener) }
                remotePrefs = prefs
                runCatching { prefs.registerOnSharedPreferenceChangeListener(remoteListener) }
            }
            val loaded = runCatching { PrivacyConfig.fromSharedPreferences(prefs) }.getOrDefault(remoteConfig)
            remoteConfig = loaded
            val merged = PrivacyConfig.mergeNewer(localConfig, remoteConfig)
            val previous = current
            current = merged
            transition = previous to merged
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    fun attachContext(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        val prefs = runCatching {
            appCtx.getSharedPreferences(ConfigContract.LOCAL_OVERRIDE_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return

        val transition: Pair<PrivacyConfig, PrivacyConfig>
        synchronized(lock) {
            if (localOverridePrefs !== prefs) {
                runCatching { localOverridePrefs?.unregisterOnSharedPreferenceChangeListener(localListener) }
                localOverridePrefs = prefs
                runCatching { prefs.registerOnSharedPreferenceChangeListener(localListener) }
            }
            val loaded = runCatching { PrivacyConfig.fromSharedPreferences(prefs) }.getOrDefault(localConfig)
            localConfig = loaded
            val merged = PrivacyConfig.mergeNewer(localConfig, remoteConfig)
            val previous = current
            current = merged
            transition = previous to merged
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    fun isTargetManaged(target: ImeTarget): Boolean = current.isImeManaged(target)

    fun isTargetManaged(targetPackage: String): Boolean =
        ImeTarget.fromPackage(targetPackage)?.let { current.isImeManaged(it) } ?: false

    fun isPointEnabled(targetPackage: String, ruleId: String): Boolean {
        val cfg = current
        val target = ImeTarget.fromPackage(targetPackage)
        if (target != null) {
            val spec = HookRuleCatalog.findById(ruleId)
            if ((spec == null || !spec.isOverlayPill) && !cfg.isImeManaged(target)) {
                return false
            }
        }
        return cfg.isRuleEnabled(ruleId)
    }

    fun isRuleEnabled(ruleId: String): Boolean = current.isRuleEnabled(ruleId)

    fun isRuleEnabled(target: ImeTarget, selector: (PrivacyConfig) -> Boolean): Boolean {
        val cfg = current
        return cfg.isImeManaged(target) && selector(cfg)
    }

    fun isCloudEnabled(target: ImeTarget): Boolean = current.effectiveCloudEnabled(target)

    fun persistConfigUpdateFromIme(
        context: Context,
        updatedConfig: PrivacyConfig
    ): PrivacyConfig {
        return commitConfigUpdateFromIme(context) { updatedConfig }
    }

    fun persistConfigUpdateFromIme(
        context: Context,
        transform: (PrivacyConfig) -> PrivacyConfig
    ): PrivacyConfig {
        return commitConfigUpdateFromIme(context, transform)
    }

    private fun commitConfigUpdateFromIme(
        context: Context,
        buildNext: (PrivacyConfig) -> PrivacyConfig
    ): PrivacyConfig {
        val appCtx = context.applicationContext ?: context
        attachContext(appCtx)

        val previous: PrivacyConfig
        val committed: PrivacyConfig
        synchronized(lock) {
            previous = current
            val candidate = buildNext(previous)
            val stamp = PrivacyConfig.nextTimestamp(
                maxOf(previous.updatedAtMs, localConfig.updatedAtMs, remoteConfig.updatedAtMs, candidate.updatedAtMs)
            )
            committed = candidate.sanitized().copy(updatedAtMs = stamp)
            localConfig = committed
            current = committed
            localOverridePrefs?.let { prefs ->
                runCatching { committed.toSharedPreferences(prefs, commitSync = true) }
            }
        }

        if (previous != committed) {
            notifyListeners(previous, committed)
        }

        val configJson = committed.toJson()
        val sourcePkg = appCtx.packageName
        syncExecutor.execute {
            sendSyncBroadcast(
                ctx = appCtx,
                syncType = ConfigContract.SYNC_TYPE_CONFIG,
                payloadExtra = ConfigContract.EXTRA_CONFIG_JSON,
                payloadJson = configJson,
                sourcePackage = sourcePkg
            )
            runCatching {
                val bundle = Bundle().apply {
                    putString(ConfigContract.EXTRA_CONFIG_JSON, configJson)
                }
                appCtx.contentResolver.call(
                    Uri.parse(ConfigContract.SYNC_CONTENT_URI),
                    ConfigContract.METHOD_PUSH_CONFIG,
                    null,
                    bundle
                )
            }
        }
        return committed
    }

    fun pushDiagnosticsFromIme(context: Context?, diagnostics: ImeRuntimeDiagnostics) {
        val ctx = context?.applicationContext ?: context ?: appContext ?: return
        val diagJson = diagnostics.toJson()
        val sourcePkg = ctx.packageName
        syncExecutor.execute {
            sendSyncBroadcast(
                ctx = ctx,
                syncType = ConfigContract.SYNC_TYPE_DIAGNOSTICS,
                payloadExtra = ConfigContract.EXTRA_DIAGNOSTICS_JSON,
                payloadJson = diagJson,
                sourcePackage = sourcePkg
            )
            runCatching {
                val bundle = Bundle().apply {
                    putString(ConfigContract.EXTRA_DIAGNOSTICS_JSON, diagJson)
                }
                ctx.contentResolver.call(
                    Uri.parse(ConfigContract.SYNC_CONTENT_URI),
                    ConfigContract.METHOD_PUSH_DIAGNOSTICS,
                    null,
                    bundle
                )
            }
        }
    }

    fun addListener(listener: (oldConfig: PrivacyConfig, newConfig: PrivacyConfig) -> Unit) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: (oldConfig: PrivacyConfig, newConfig: PrivacyConfig) -> Unit) {
        listeners.remove(listener)
    }

    fun detach() {
        synchronized(lock) {
            runCatching { remotePrefs?.unregisterOnSharedPreferenceChangeListener(remoteListener) }
            runCatching { localOverridePrefs?.unregisterOnSharedPreferenceChangeListener(localListener) }
            remotePrefs = null
            localOverridePrefs = null
        }
        listeners.clear()
    }

    private fun reloadMergedLocked(fromRemote: Boolean) {
        val transition: Pair<PrivacyConfig, PrivacyConfig>
        synchronized(lock) {
            if (fromRemote) {
                val rPrefs = remotePrefs ?: return
                remoteConfig = runCatching { PrivacyConfig.fromSharedPreferences(rPrefs) }.getOrDefault(remoteConfig)
            } else {
                val lPrefs = localOverridePrefs ?: return
                localConfig = runCatching { PrivacyConfig.fromSharedPreferences(lPrefs) }.getOrDefault(localConfig)
            }
            val merged = PrivacyConfig.mergeNewer(localConfig, remoteConfig)
            val previous = current
            current = merged
            transition = previous to merged
        }
        if (transition.first != transition.second) {
            notifyListeners(transition.first, transition.second)
        }
    }

    private fun sendSyncBroadcast(
        ctx: Context,
        syncType: String,
        payloadExtra: String,
        payloadJson: String,
        sourcePackage: String
    ) {
        runCatching {
            val intent = Intent(ConfigContract.ACTION_SYNC_FROM_IME).apply {
                component = ComponentName(ConfigContract.MODULE_PACKAGE, ConfigContract.RECEIVER_CLASS_NAME)
                putExtra(ConfigContract.EXTRA_SYNC_TYPE, syncType)
                putExtra(payloadExtra, payloadJson)
                putExtra(ConfigContract.EXTRA_SOURCE_PACKAGE, sourcePackage)
            }
            ctx.sendBroadcast(intent)
        }
    }

    private fun notifyListeners(oldConfig: PrivacyConfig, newConfig: PrivacyConfig) {
        for (listener in listeners) {
            runCatching { listener(oldConfig, newConfig) }
        }
    }
}
