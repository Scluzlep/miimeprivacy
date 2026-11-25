package com.scluzlep.miimeprivacy.xposed

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.emoji.ImeEmojiRuntimeBridge
import com.scluzlep.miimeprivacy.emoji.SogouDualSkinPopupHook
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.overlay.KeyboardQuickPillHook
import com.scluzlep.miimeprivacy.xposed.targets.BaiduHookPack
import com.scluzlep.miimeprivacy.xposed.targets.SogouHookPack
import com.scluzlep.miimeprivacy.xposed.targets.XiaoAiHookPack
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Main libxposed API 102 module entry point for `com.scluzlep.miimeprivacy`.
 */
class MiImePrivacyModule : XposedModule() {

    private val targetPacksInstalled = AtomicBoolean(false)
    private val overlayInstalled = AtomicBoolean(false)
    private val initialInstallStarted = AtomicBoolean(false)
    private val lifecycleEpoch = AtomicInteger(0)
    private val installLock = Any()
    private val reanalyzeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "miime-reanalyze").apply { isDaemon = true }
    }

    @Volatile
    private var isSystemServerProcess: Boolean = false

    @Volatile
    private var currentProcessName: String = ""

    @Volatile
    private var currentTarget: ImeTarget? = null

    @Volatile
    private var currentClassLoader: ClassLoader? = null

    @Volatile
    private var currentAppInfo: ApplicationInfo? = null

    @Volatile
    private var activeRegistry: HookRegistry? = null

    @Volatile
    private var configListener: ((PrivacyConfig, PrivacyConfig) -> Unit)? = null

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        isSystemServerProcess = param.isSystemServer
        currentProcessName = param.processName
        if (param.isSystemServer) return
        ConfigBridge.attachRemote(this)
        log(Log.INFO, TAG, "Loaded API $apiVersion module in process=${param.processName}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (isSystemServerProcess || !param.isFirstPackage) return
        val target = ImeTarget.fromPackage(param.packageName) ?: return

        lifecycleEpoch.incrementAndGet()
        initialInstallStarted.set(false)
        currentTarget = target
        currentClassLoader = param.classLoader
        currentAppInfo = param.applicationInfo
        targetPacksInstalled.set(false)
        overlayInstalled.set(false)

        val registry = createRegistry(
            target = target,
            appInfo = param.applicationInfo,
            context = currentApplicationByReflection(),
            oldById = null
        )
        activeRegistry = registry
        registerConfigListener(target, param.classLoader, param.applicationInfo, registry)

        val existingApp = currentApplicationByReflection()
        if (existingApp != null) {
            val ctx = existingApp.applicationContext ?: existingApp
            ConfigBridge.attachContext(ctx)
            registry.attachContext(ctx)
            ImeEmojiRuntimeBridge.attachIfMainProcess(target, ctx, registry.processName)
            installTargetPacksIfManaged(target, ctx, param.classLoader, param.applicationInfo, registry)
            return
        }

        // Hook Application.attach(Context) so privacy & SDK init hooks are active before Application.onCreate()
        val attachMethod = runCatching {
            Application::class.java.getDeclaredMethod("attach", Context::class.java)
        }.getOrNull() ?: runCatching {
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
        }.getOrNull()

        if (attachMethod != null) {
            runCatching {
                registry.installOrReplaceHook(HOOK_APP_ATTACH, attachMethod, XposedInterface.PRIORITY_HIGHEST) { chain ->
                    val baseCtx = chain.getArg(0) as? Context
                    if (baseCtx != null) {
                        ConfigBridge.attachContext(baseCtx)
                        registry.attachContext(baseCtx)
                        installTargetPacksIfManaged(
                            target = target,
                            context = baseCtx,
                            classLoader = param.classLoader,
                            appInfo = param.applicationInfo,
                            registry = registry
                        )
                    }
                    val result = chain.proceed()
                    (chain.thisObject as? Application)?.applicationContext?.let { appCtx ->
                        ConfigBridge.attachContext(appCtx)
                        registry.attachContext(appCtx)
                        ImeEmojiRuntimeBridge.attachIfMainProcess(target, appCtx, registry.processName)
                    }
                    result
                }
            }.onFailure { err ->
                log(Log.WARN, TAG, "Failed hooking Application.attach for ${target.packageName}", err)
            }
        }

        val onCreateMethod = runCatching {
            Application::class.java.getDeclaredMethod("onCreate")
        }.getOrNull()
        if (onCreateMethod != null) {
            runCatching {
                registry.installOrReplaceHook(HOOK_APP_ON_CREATE, onCreateMethod) { chain ->
                    val appCtx = (chain.thisObject as? Application)?.applicationContext
                        ?: (chain.thisObject as? Context)
                    if (appCtx != null) {
                        ConfigBridge.attachContext(appCtx)
                        registry.attachContext(appCtx)
                        ImeEmojiRuntimeBridge.attachIfMainProcess(target, appCtx, registry.processName)
                        installTargetPacksIfManaged(
                            target = target,
                            context = appCtx,
                            classLoader = param.classLoader,
                            appInfo = param.applicationInfo,
                            registry = registry
                        )
                    }
                    chain.proceed()
                }
            }
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        lifecycleEpoch.incrementAndGet()
        initialInstallStarted.set(false)
        configListener?.let { ConfigBridge.removeListener(it) }
        configListener = null
        ConfigBridge.detach()
        KeyboardQuickPillHook.clearOnHotReload()
        SogouDualSkinPopupHook.clearOnHotReload()
        ImeEmojiRuntimeBridge.detachOnHotReload()
        SogouHookPack.resetCaches()
        param.setSavedInstanceState(currentTarget?.packageName)
        log(Log.INFO, TAG, "Hot reloading initiated for target=${currentTarget?.packageName}")
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        currentProcessName = param.processName
        if (param.isSystemServer) return
        lifecycleEpoch.incrementAndGet()
        initialInstallStarted.set(false)

        ConfigBridge.attachRemote(this)
        val app = currentApplicationByReflection()
        val pkgName = (param.savedInstanceState as? String)?.takeIf { it.isNotBlank() }
            ?: app?.packageName
            ?: param.processName.substringBefore(':')
        val target = ImeTarget.fromPackage(pkgName)

        val oldById = linkedMapOf<String, XposedInterface.HookHandle>()
        param.oldHookHandles.forEachIndexed { index, handle ->
            val key = handle.id ?: "legacy#$index:${handle.executable.toGenericString()}"
            oldById[key] = handle
        }

        if (target == null || app == null) {
            for (handle in oldById.values) {
                runCatching { handle.unhook() }
            }
            return
        }

        currentTarget = target
        val appCtx = app.applicationContext ?: app
        val classLoader = app.classLoader ?: javaClass.classLoader ?: ClassLoader.getSystemClassLoader()
        val appInfo = app.applicationInfo
        currentClassLoader = classLoader
        currentAppInfo = appInfo
        ConfigBridge.attachContext(appCtx)
        ImeEmojiRuntimeBridge.attachIfMainProcess(target, appCtx, currentProcessName.ifBlank { target.packageName })

        targetPacksInstalled.set(false)
        overlayInstalled.set(false)
        val registry = createRegistry(
            target = target,
            appInfo = appInfo,
            context = appCtx,
            oldById = oldById
        )
        activeRegistry = registry
        registerConfigListener(target, classLoader, appInfo, registry)
        installTargetPacksIfManaged(target, appCtx, classLoader, appInfo, registry)
        registry.unhookRemaining()
        log(Log.INFO, TAG, "Hot reload completed for ${target.packageName} activeHooks=${registry.activeHookCount()}")
    }

    private fun registerConfigListener(
        target: ImeTarget,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ) {
        configListener?.let { ConfigBridge.removeListener(it) }
        val listener: (PrivacyConfig, PrivacyConfig) -> Unit = listener@{ oldCfg, newCfg ->
            KeyboardQuickPillHook.onConfigChanged(oldCfg, newCfg)
            if (!initialInstallStarted.get()) return@listener
            val ctx = registry.attachedContext ?: currentApplicationByReflection()?.let { it.applicationContext ?: it }
            val modeChanged = oldCfg.resolveModeFor(target) != newCfg.resolveModeFor(target)
            val cacheResetRequested = newCfg.cacheResetAtMsFor(target) > oldCfg.cacheResetAtMsFor(target)
            val installGatedChanged = HookRuleCatalog.rulesFor(target).any { spec ->
                spec.installGated && oldCfg.isRuleEnabled(spec.ruleId) != newCfg.isRuleEnabled(spec.ruleId)
            }
            val overlayRuleId = HookRuleCatalog.overlayRuleIdFor(target)

            if (oldCfg.isImeManaged(target) && !newCfg.isImeManaged(target)) {
                if (cacheResetRequested && ctx != null) {
                    val epoch = lifecycleEpoch.get()
                    reanalyzeExecutor.execute {
                        synchronized(installLock) {
                            if (lifecycleEpoch.get() != epoch) return@execute
                            SymbolResolver.clearCacheNow(ctx, newCfg.cacheResetAtMsFor(target))
                            if (ConfigBridge.isTargetManaged(target)) {
                                reinstallTargetPacksLocked(target, ctx, classLoader, appInfo, registry, overlayRuleId)
                            } else {
                                unmanageTargetHooksLocked(ctx, registry, overlayRuleId)
                            }
                        }
                    }
                } else {
                    synchronized(installLock) {
                        if (!ConfigBridge.isTargetManaged(target)) {
                            unmanageTargetHooksLocked(ctx, registry, overlayRuleId)
                        }
                    }
                }
            } else if (modeChanged || cacheResetRequested || installGatedChanged) {
                if (ctx != null) {
                    val epoch = lifecycleEpoch.get()
                    reanalyzeExecutor.execute {
                        synchronized(installLock) {
                            if (lifecycleEpoch.get() != epoch) return@execute
                            if (cacheResetRequested) {
                                SymbolResolver.clearCacheNow(ctx, newCfg.cacheResetAtMsFor(target))
                            }
                            if (ConfigBridge.isTargetManaged(target)) {
                                reinstallTargetPacksLocked(target, ctx, classLoader, appInfo, registry, overlayRuleId)
                            } else {
                                unmanageTargetHooksLocked(ctx, registry, overlayRuleId)
                            }
                        }
                    }
                }
            } else if (!targetPacksInstalled.get() && newCfg.isImeManaged(target)) {
                if (ctx != null) {
                    val epoch = lifecycleEpoch.get()
                    reanalyzeExecutor.execute {
                        synchronized(installLock) {
                            if (lifecycleEpoch.get() != epoch) return@execute
                            if (ConfigBridge.isTargetManaged(target)) {
                                installTargetPacksIfManaged(target, ctx, classLoader, appInfo, registry)
                            }
                        }
                    }
                }
            } else if (oldCfg.effectiveCloudEnabled(target) != newCfg.effectiveCloudEnabled(target) ||
                oldCfg.isImeManaged(target) != newCfg.isImeManaged(target)
            ) {
                registry.pushDiagnosticsNow(ctx)
            }
        }
        configListener = listener
        ConfigBridge.addListener(listener)
    }

    private fun unmanageTargetHooksLocked(
        ctx: Context?,
        registry: HookRegistry,
        overlayRuleId: String
    ) {
        registry.unhookActiveTargetHooks(keepPrefixes = TARGET_KEEP_PREFIXES)
        targetPacksInstalled.set(false)
        registry.reportAllSkipped(
            reason = "已关闭对该输入法的隐私接管",
            excludeRuleIds = setOf(overlayRuleId)
        )
        registry.pushDiagnosticsNow(ctx, note = "未接管")
    }

    private fun reinstallTargetPacksLocked(
        target: ImeTarget,
        ctx: Context,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry,
        overlayRuleId: String
    ) {
        if (target == ImeTarget.SOGOU) {
            SogouHookPack.resetCaches()
        }
        registry.unhookActiveTargetHooks(keepPrefixes = TARGET_KEEP_PREFIXES)
        registry.resetTargetRuleReports(excludeRuleIds = setOf(overlayRuleId))
        targetPacksInstalled.set(false)
        installTargetPacksIfManaged(target, ctx, classLoader, appInfo, registry)
    }

    private fun installOverlayIfNeeded(
        target: ImeTarget,
        context: Context,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ) {
        if (!overlayInstalled.compareAndSet(false, true)) return
        runCatching {
            KeyboardQuickPillHook.install(
                target = target,
                context = context,
                classLoader = classLoader,
                appInfo = appInfo,
                registry = registry
            )
        }.onFailure { err ->
            overlayInstalled.set(false)
            registry.logWarn("KeyboardQuickPillHook install failed", err)
        }
    }

    private fun installTargetPacksIfManaged(
        target: ImeTarget,
        context: Context,
        classLoader: ClassLoader,
        appInfo: ApplicationInfo,
        registry: HookRegistry
    ) {
        synchronized(installLock) {
            initialInstallStarted.set(true)
            registry.attachContext(context)
            installOverlayIfNeeded(target, context, classLoader, appInfo, registry)

            if (!ConfigBridge.isTargetManaged(target)) {
                val overlayRuleId = HookRuleCatalog.overlayRuleIdFor(target)
                registry.reportAllSkipped(
                    reason = "已关闭对该输入法的隐私接管",
                    excludeRuleIds = setOf(overlayRuleId)
                )
                registry.pushDiagnosticsNow(context, note = "未接管")
                return
            }
            if (!targetPacksInstalled.compareAndSet(false, true)) return

            val pack = packFor(target)
            val packResult = runCatching {
                pack.install(context, classLoader, appInfo, registry)
            }.onFailure { err ->
                registry.logWarn("Target pack install threw exception", err)
            }.getOrNull()

            registry.pushDiagnosticsNow(
                context = context,
                note = packResult?.note.orEmpty()
            )
            registry.logInfo("Installed hooks for ${target.packageName}, activeHandles=${registry.activeHookCount()}")
        }
    }

    private fun packFor(target: ImeTarget): ImeHookPack = when (target) {
        ImeTarget.XIAOAI -> XiaoAiHookPack
        ImeTarget.SOGOU -> SogouHookPack
        ImeTarget.BAIDU -> BaiduHookPack
    }

    private fun createRegistry(
        target: ImeTarget,
        appInfo: ApplicationInfo,
        context: Context?,
        oldById: MutableMap<String, XposedInterface.HookHandle>?
    ): HookRegistry {
        val (versionCode, versionName) = resolveVersionInfo(context, appInfo, target)
        return HookRegistry(
            xposed = this,
            target = target,
            appInfo = appInfo,
            packageVersionCode = versionCode,
            packageVersionName = versionName,
            processName = currentProcessName.ifBlank { target.packageName },
            oldById = oldById
        ).apply {
            attachContext(context)
        }
    }

    private fun resolveVersionInfo(
        context: Context?,
        appInfo: ApplicationInfo,
        target: ImeTarget
    ): Pair<Long, String> {
        if (context != null) {
            val pkgInfo = runCatching {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(appInfo.packageName, 0)
            }.getOrNull()
            if (pkgInfo != null) {
                val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                return code to (pkgInfo.versionName ?: "")
            }
        }
        return target.expectedVersionCode to ""
    }

    private fun currentApplicationByReflection(): Application? {
        return runCatching {
            val atClass = Class.forName("android.app.ActivityThread")
            atClass.getMethod("currentApplication").invoke(null) as? Application
        }.getOrNull()
    }

    companion object {
        private const val TAG = "MiImePrivacyModule"
        private const val HOOK_APP_ATTACH = "lifecycle:Application.attach"
        private const val HOOK_APP_ON_CREATE = "lifecycle:Application.onCreate"
        private val TARGET_KEEP_PREFIXES = setOf("lifecycle:", "overlay:")
    }
}
