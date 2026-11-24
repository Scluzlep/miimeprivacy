package com.scluzlep.miimeprivacy.xposed

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.util.DexSignUtil
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

enum class ResolveSource {
    FAST_PATH,
    CACHE,
    DEXKIT,
    STRUCTURAL_FALLBACK,
    MISS
}

data class ResolveResult<T>(
    val value: T?,
    val source: ResolveSource,
    val descriptor: String = "",
    val detail: String = ""
) {
    val isSuccess: Boolean get() = value != null
}

/**
 * 4-tier symbol resolver for hooked IMEs:
 * 1. FastPath direct reflection (zero I/O)
 * 2. Versioned SharedPreferences descriptor cache (`SYMBOL_CACHE_PREFS_NAME`)
 * 3. Lazy `DexKitBridge` (loads `libdexkit.so` from module `nativeLibraryDir` only on cache miss)
 * 4. Structural fallback lambda
 *
 * When [resolveMode] is [SymbolResolveMode.DYNAMIC], targets with a `dexKitFinder` or
 * `structuralFallback` resolve via `Cache -> DexKit -> StructuralFallback -> FastPath`.
 */
class SymbolResolver(
    context: Context?,
    private val classLoader: ClassLoader,
    private val appInfo: ApplicationInfo,
    private val versionCode: Long,
    private val moduleNativeLibDir: String?,
    val resolveMode: SymbolResolveMode = SymbolResolveMode.AUTO,
    val requiredCacheResetAtMs: Long = 0L,
    internal val prefsOverride: SharedPreferences? = null
) : AutoCloseable {

    private val prefs: SharedPreferences? = prefsOverride ?: context?.let { ctx ->
        runCatching {
            ctx.getSharedPreferences(ConfigContract.SYMBOL_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    init {
        if (prefs != null && requiredCacheResetAtMs > 0L) {
            applyPendingResetIfNeeded(prefs, requiredCacheResetAtMs)
        }
    }

    @Volatile
    private var bridge: DexKitBridge? = null

    @Volatile
    private var bridgeInitAttempted: Boolean = false

    private fun <T> runResolutionOrder(
        preferDynamic: Boolean,
        tryFastPath: () -> ResolveResult<T>?,
        tryCache: () -> ResolveResult<T>?,
        tryDexKit: () -> ResolveResult<T>?,
        tryStructural: () -> ResolveResult<T>?
    ): ResolveResult<T> {
        val attempts = if (preferDynamic) {
            listOf(tryCache, tryDexKit, tryStructural, tryFastPath)
        } else {
            listOf(tryFastPath, tryCache, tryDexKit, tryStructural)
        }
        for (attempt in attempts) {
            attempt()?.let { return it }
        }
        return ResolveResult(null, ResolveSource.MISS)
    }

    fun resolveClass(
        cacheKey: String,
        fastPathCandidates: List<String> = emptyList(),
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Class<*>?)? = null
    ): ResolveResult<Class<*>> {
        val storageKey = buildCacheKey(versionCode, cacheKey)
        val preferDynamic = resolveMode == SymbolResolveMode.DYNAMIC &&
            (dexKitFinder != null || structuralFallback != null)
        return runResolutionOrder(
            preferDynamic = preferDynamic,
            tryFastPath = {
                var fastResult: ResolveResult<Class<*>>? = null
                for (name in fastPathCandidates) {
                    val hit = loadClassOrNull(name, classLoader)
                    if (hit != null) {
                        fastResult = ResolveResult(hit, ResolveSource.FAST_PATH, DexClass(hit).toString(), name)
                        break
                    }
                }
                fastResult
            },
            tryCache = {
                val cachedDescriptor = prefs?.getString(storageKey, null)
                if (cachedDescriptor.isNullOrBlank()) {
                    null
                } else {
                    val cachedClass = runCatching {
                        DexClass.deserialize(cachedDescriptor).getInstance(classLoader)
                    }.getOrNull()
                    cachedClass?.let { ResolveResult(it, ResolveSource.CACHE, cachedDescriptor, it.name) }
                }
            },
            tryDexKit = {
                if (dexKitFinder == null) {
                    null
                } else {
                    val dexBridge = getOrCreateBridge()
                    val foundDescriptor = dexBridge?.let { runCatching { dexKitFinder(it) }.getOrNull() }
                    if (foundDescriptor.isNullOrBlank()) {
                        null
                    } else {
                        val resolved = runCatching {
                            DexClass.deserialize(foundDescriptor).getInstance(classLoader)
                        }.getOrNull()
                        if (resolved != null) {
                            saveCacheDescriptor(storageKey, foundDescriptor)
                            ResolveResult(resolved, ResolveSource.DEXKIT, foundDescriptor, resolved.name)
                        } else {
                            null
                        }
                    }
                }
            },
            tryStructural = {
                if (structuralFallback == null) {
                    null
                } else {
                    val fallback = runCatching { structuralFallback(classLoader) }.getOrNull()
                    if (fallback != null) {
                        val descriptor = DexClass(fallback).toString()
                        saveCacheDescriptor(storageKey, descriptor)
                        ResolveResult(fallback, ResolveSource.STRUCTURAL_FALLBACK, descriptor, fallback.name)
                    } else {
                        null
                    }
                }
            }
        )
    }

    fun resolveMethod(
        cacheKey: String,
        fastPath: ((ClassLoader) -> Method?)? = null,
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Method?)? = null
    ): ResolveResult<Method> {
        val execResult = resolveExecutable(
            cacheKey = cacheKey,
            fastPath = fastPath?.let { fn -> { cl -> fn(cl) } },
            dexKitFinder = dexKitFinder,
            structuralFallback = structuralFallback?.let { fn -> { cl -> fn(cl) } }
        )
        val method = execResult.value as? Method
        return if (method != null) {
            ResolveResult(method, execResult.source, execResult.descriptor, execResult.detail)
        } else {
            ResolveResult(null, ResolveSource.MISS)
        }
    }

    fun resolveExecutable(
        cacheKey: String,
        fastPath: ((ClassLoader) -> Executable?)? = null,
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Executable?)? = null
    ): ResolveResult<Executable> {
        val storageKey = buildCacheKey(versionCode, cacheKey)
        val preferDynamic = resolveMode == SymbolResolveMode.DYNAMIC &&
            (dexKitFinder != null || structuralFallback != null)
        return runResolutionOrder(
            preferDynamic = preferDynamic,
            tryFastPath = {
                if (fastPath == null) {
                    null
                } else {
                    val hit = runCatching { fastPath(classLoader) }.getOrNull()
                    if (hit != null) {
                        val descriptor = executableToDescriptor(hit)
                        ResolveResult(hit, ResolveSource.FAST_PATH, descriptor, formatExecutable(hit))
                    } else {
                        null
                    }
                }
            },
            tryCache = {
                val cachedDescriptor = prefs?.getString(storageKey, null)
                if (cachedDescriptor.isNullOrBlank()) {
                    null
                } else {
                    val cached = descriptorToExecutable(cachedDescriptor, classLoader)
                    cached?.let { ResolveResult(it, ResolveSource.CACHE, cachedDescriptor, formatExecutable(it)) }
                }
            },
            tryDexKit = {
                if (dexKitFinder == null) {
                    null
                } else {
                    val dexBridge = getOrCreateBridge()
                    val foundDescriptor = dexBridge?.let { runCatching { dexKitFinder(it) }.getOrNull() }
                    if (foundDescriptor.isNullOrBlank()) {
                        null
                    } else {
                        val resolved = descriptorToExecutable(foundDescriptor, classLoader)
                        if (resolved != null) {
                            saveCacheDescriptor(storageKey, foundDescriptor)
                            ResolveResult(resolved, ResolveSource.DEXKIT, foundDescriptor, formatExecutable(resolved))
                        } else {
                            null
                        }
                    }
                }
            },
            tryStructural = {
                if (structuralFallback == null) {
                    null
                } else {
                    val fallback = runCatching { structuralFallback(classLoader) }.getOrNull()
                    if (fallback != null) {
                        val descriptor = executableToDescriptor(fallback)
                        saveCacheDescriptor(storageKey, descriptor)
                        ResolveResult(fallback, ResolveSource.STRUCTURAL_FALLBACK, descriptor, formatExecutable(fallback))
                    } else {
                        null
                    }
                }
            }
        )
    }

    fun resolveField(
        cacheKey: String,
        fastPath: ((ClassLoader) -> Field?)? = null,
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Field?)? = null
    ): ResolveResult<Field> {
        val storageKey = buildCacheKey(versionCode, cacheKey)
        val preferDynamic = resolveMode == SymbolResolveMode.DYNAMIC &&
            (dexKitFinder != null || structuralFallback != null)
        return runResolutionOrder(
            preferDynamic = preferDynamic,
            tryFastPath = {
                if (fastPath == null) {
                    null
                } else {
                    val hit = runCatching { fastPath(classLoader) }.getOrNull()
                    if (hit != null) {
                        val descriptor = DexField(hit).toString()
                        ResolveResult(hit, ResolveSource.FAST_PATH, descriptor, "${hit.declaringClass.name}#${hit.name}")
                    } else {
                        null
                    }
                }
            },
            tryCache = {
                val cachedDescriptor = prefs?.getString(storageKey, null)
                if (cachedDescriptor.isNullOrBlank()) {
                    null
                } else {
                    val cached = runCatching {
                        DexField.deserialize(cachedDescriptor).getFieldInstance(classLoader)
                    }.getOrNull()
                    cached?.let {
                        ResolveResult(it, ResolveSource.CACHE, cachedDescriptor, "${it.declaringClass.name}#${it.name}")
                    }
                }
            },
            tryDexKit = {
                if (dexKitFinder == null) {
                    null
                } else {
                    val dexBridge = getOrCreateBridge()
                    val foundDescriptor = dexBridge?.let { runCatching { dexKitFinder(it) }.getOrNull() }
                    if (foundDescriptor.isNullOrBlank()) {
                        null
                    } else {
                        val resolved = runCatching {
                            DexField.deserialize(foundDescriptor).getFieldInstance(classLoader)
                        }.getOrNull()
                        if (resolved != null) {
                            saveCacheDescriptor(storageKey, foundDescriptor)
                            ResolveResult(
                                resolved,
                                ResolveSource.DEXKIT,
                                foundDescriptor,
                                "${resolved.declaringClass.name}#${resolved.name}"
                            )
                        } else {
                            null
                        }
                    }
                }
            },
            tryStructural = {
                if (structuralFallback == null) {
                    null
                } else {
                    val fallback = runCatching { structuralFallback(classLoader) }.getOrNull()
                    if (fallback != null) {
                        val descriptor = DexField(fallback).toString()
                        saveCacheDescriptor(storageKey, descriptor)
                        ResolveResult(
                            fallback,
                            ResolveSource.STRUCTURAL_FALLBACK,
                            descriptor,
                            "${fallback.declaringClass.name}#${fallback.name}"
                        )
                    } else {
                        null
                    }
                }
            }
        )
    }

    private fun saveCacheDescriptor(storageKey: String, descriptor: String) {
        if (descriptor.isBlank()) return
        prefs?.edit()?.putString(storageKey, descriptor)?.apply()
    }

    @Synchronized
    private fun getOrCreateBridge(): DexKitBridge? {
        bridge?.let { return it }
        if (bridgeInitAttempted) return null
        bridgeInitAttempted = true
        val sourceDir = appInfo.sourceDir
        if (sourceDir.isNullOrBlank()) return null
        if (!ensureDexKitNativeLoaded(moduleNativeLibDir)) return null
        return runCatching {
            DexKitBridge.create(sourceDir).also { created ->
                created.setThreadNum(2)
                bridge = created
            }
        }.getOrNull()
    }

    @Synchronized
    override fun close() {
        runCatching { bridge?.close() }
        bridge = null
    }

    companion object {
        private val nativeLoaded = AtomicBoolean(false)
        const val KEY_APPLIED_RESET_AT_MS = "_applied_reset_at_ms"

        fun buildCacheKey(versionCode: Long, cacheKey: String): String {
            return "v$versionCode:$cacheKey"
        }

        fun isSymbolCacheEntryKey(key: String): Boolean {
            return key.startsWith("v") && key.contains(':')
        }

        fun countCachedSymbols(prefs: SharedPreferences?): Int {
            prefs ?: return 0
            val allEntries = runCatching { prefs.all }.getOrNull() ?: return 0
            return allEntries.keys.count(::isSymbolCacheEntryKey)
        }

        fun countCachedSymbols(context: Context?): Int {
            val ctx = context ?: return 0
            val prefs = runCatching {
                ctx.getSharedPreferences(ConfigContract.SYMBOL_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()
            return countCachedSymbols(prefs)
        }

        fun clearCacheNow(
            prefs: SharedPreferences?,
            resetAtMs: Long = System.currentTimeMillis()
        ): Int {
            prefs ?: return 0
            val keysToRemove = runCatching {
                prefs.all.keys.filter(::isSymbolCacheEntryKey)
            }.getOrDefault(emptyList())
            val existingReset = runCatching { prefs.getLong(KEY_APPLIED_RESET_AT_MS, 0L) }.getOrDefault(0L)
            val nextReset = maxOf(resetAtMs.coerceAtLeast(0L), existingReset)
            val editor = prefs.edit()
            for (key in keysToRemove) {
                editor.remove(key)
            }
            editor.putLong(KEY_APPLIED_RESET_AT_MS, nextReset)
            editor.commit()
            return keysToRemove.size
        }

        fun clearCacheNow(
            context: Context?,
            resetAtMs: Long = System.currentTimeMillis()
        ): Int {
            val ctx = context ?: return 0
            val prefs = runCatching {
                ctx.getSharedPreferences(ConfigContract.SYMBOL_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            }.getOrNull()
            return clearCacheNow(prefs, resetAtMs)
        }

        fun applyPendingResetIfNeeded(prefs: SharedPreferences?, requiredResetAtMs: Long): Boolean {
            if (prefs == null || requiredResetAtMs <= 0L) return false
            val applied = runCatching { prefs.getLong(KEY_APPLIED_RESET_AT_MS, 0L) }.getOrDefault(0L)
            if (applied >= requiredResetAtMs) return false
            clearCacheNow(prefs, requiredResetAtMs)
            return true
        }

        fun loadClassOrNull(className: String, classLoader: ClassLoader): Class<*>? {
            return runCatching { Class.forName(className, false, classLoader) }.getOrNull()
        }

        fun findDeclaredMethodOrNull(
            clazz: Class<*>?,
            methodName: String,
            vararg parameterTypes: Class<*>
        ): Method? {
            if (clazz == null) return null
            return runCatching { clazz.getDeclaredMethod(methodName, *parameterTypes) }.getOrNull()
        }

        fun findDeclaredConstructorOrNull(
            clazz: Class<*>?,
            vararg parameterTypes: Class<*>
        ): Constructor<*>? {
            if (clazz == null) return null
            return runCatching { clazz.getDeclaredConstructor(*parameterTypes) }.getOrNull()
        }

        fun executableToDescriptor(executable: Executable): String {
            return when (executable) {
                is Method -> DexMethod(executable).toString()
                // Avoid DexKit 2.0.3's DexMethod(Constructor<*>) bug that double-encodes paramTypeNames
                is Constructor<*> -> buildString {
                    append(DexSignUtil.getTypeSign(executable.declaringClass))
                    append("-><init>")
                    append(DexSignUtil.getConstructorSign(executable))
                }
                else -> ""
            }
        }

        fun descriptorToExecutable(descriptor: String, classLoader: ClassLoader): Executable? {
            if (descriptor.isBlank()) return null
            return runCatching {
                val dexMethod = DexMethod.deserialize(descriptor)
                if (dexMethod.isConstructor) {
                    dexMethod.getConstructorInstance(classLoader)
                } else {
                    dexMethod.getMethodInstance(classLoader)
                }
            }.getOrNull()
        }

        fun formatExecutable(executable: Executable): String {
            val params = executable.parameterTypes.joinToString(",") { it.simpleName }
            val name = if (executable is Constructor<*>) "<init>" else executable.name
            return "${executable.declaringClass.name}#$name($params)"
        }

        @Suppress("UnsafeDynamicallyLoadedCode")
        fun ensureDexKitNativeLoaded(moduleNativeLibDir: String?): Boolean {
            if (nativeLoaded.get()) return true
            if (!moduleNativeLibDir.isNullOrBlank()) {
                val soFile = File(moduleNativeLibDir, "libdexkit.so")
                if (soFile.isFile) {
                    val loaded = runCatching {
                        System.load(soFile.absolutePath)
                        true
                    }.getOrDefault(false)
                    if (loaded) {
                        nativeLoaded.set(true)
                        return true
                    }
                }
            }
            val fallback = runCatching {
                System.loadLibrary("dexkit")
                true
            }.getOrDefault(false)
            if (fallback) {
                nativeLoaded.set(true)
            }
            return fallback
        }
    }
}
