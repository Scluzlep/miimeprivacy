package com.scluzlep.miimeprivacy.xposed

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.config.ConfigContract
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
 */
class SymbolResolver(
    context: Context?,
    private val classLoader: ClassLoader,
    private val appInfo: ApplicationInfo,
    private val versionCode: Long,
    private val moduleNativeLibDir: String?
) : AutoCloseable {

    private val prefs: SharedPreferences? = context?.let { ctx ->
        runCatching {
            ctx.getSharedPreferences(ConfigContract.SYMBOL_CACHE_PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull()
    }

    @Volatile
    private var bridge: DexKitBridge? = null

    @Volatile
    private var bridgeInitAttempted: Boolean = false

    fun resolveClass(
        cacheKey: String,
        fastPathCandidates: List<String> = emptyList(),
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Class<*>?)? = null
    ): ResolveResult<Class<*>> {
        // 1. FastPath
        for (name in fastPathCandidates) {
            val hit = loadClassOrNull(name, classLoader)
            if (hit != null) {
                val descriptor = DexClass(hit).toString()
                return ResolveResult(hit, ResolveSource.FAST_PATH, descriptor, name)
            }
        }

        // 2. Versioned SharedPreferences cache
        val storageKey = buildCacheKey(versionCode, cacheKey)
        val cachedDescriptor = prefs?.getString(storageKey, null)
        if (!cachedDescriptor.isNullOrBlank()) {
            val cachedClass = runCatching {
                DexClass.deserialize(cachedDescriptor).getInstance(classLoader)
            }.getOrNull()
            if (cachedClass != null) {
                return ResolveResult(cachedClass, ResolveSource.CACHE, cachedDescriptor, cachedClass.name)
            }
        }

        // 3. Lazy DexKit
        if (dexKitFinder != null) {
            val dexBridge = getOrCreateBridge()
            if (dexBridge != null) {
                val foundDescriptor = runCatching { dexKitFinder(dexBridge) }.getOrNull()
                if (!foundDescriptor.isNullOrBlank()) {
                    val resolved = runCatching {
                        DexClass.deserialize(foundDescriptor).getInstance(classLoader)
                    }.getOrNull()
                    if (resolved != null) {
                        saveCacheDescriptor(storageKey, foundDescriptor)
                        return ResolveResult(resolved, ResolveSource.DEXKIT, foundDescriptor, resolved.name)
                    }
                }
            }
        }

        // 4. Structural fallback
        if (structuralFallback != null) {
            val fallback = runCatching { structuralFallback(classLoader) }.getOrNull()
            if (fallback != null) {
                val descriptor = DexClass(fallback).toString()
                saveCacheDescriptor(storageKey, descriptor)
                return ResolveResult(fallback, ResolveSource.STRUCTURAL_FALLBACK, descriptor, fallback.name)
            }
        }

        return ResolveResult(null, ResolveSource.MISS)
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
        // 1. FastPath
        if (fastPath != null) {
            val hit = runCatching { fastPath(classLoader) }.getOrNull()
            if (hit != null) {
                val descriptor = executableToDescriptor(hit)
                return ResolveResult(hit, ResolveSource.FAST_PATH, descriptor, formatExecutable(hit))
            }
        }

        // 2. Versioned SharedPreferences cache
        val storageKey = buildCacheKey(versionCode, cacheKey)
        val cachedDescriptor = prefs?.getString(storageKey, null)
        if (!cachedDescriptor.isNullOrBlank()) {
            val cached = descriptorToExecutable(cachedDescriptor, classLoader)
            if (cached != null) {
                return ResolveResult(cached, ResolveSource.CACHE, cachedDescriptor, formatExecutable(cached))
            }
        }

        // 3. Lazy DexKit
        if (dexKitFinder != null) {
            val dexBridge = getOrCreateBridge()
            if (dexBridge != null) {
                val foundDescriptor = runCatching { dexKitFinder(dexBridge) }.getOrNull()
                if (!foundDescriptor.isNullOrBlank()) {
                    val resolved = descriptorToExecutable(foundDescriptor, classLoader)
                    if (resolved != null) {
                        saveCacheDescriptor(storageKey, foundDescriptor)
                        return ResolveResult(resolved, ResolveSource.DEXKIT, foundDescriptor, formatExecutable(resolved))
                    }
                }
            }
        }

        // 4. Structural fallback
        if (structuralFallback != null) {
            val fallback = runCatching { structuralFallback(classLoader) }.getOrNull()
            if (fallback != null) {
                val descriptor = executableToDescriptor(fallback)
                saveCacheDescriptor(storageKey, descriptor)
                return ResolveResult(fallback, ResolveSource.STRUCTURAL_FALLBACK, descriptor, formatExecutable(fallback))
            }
        }

        return ResolveResult(null, ResolveSource.MISS)
    }

    fun resolveField(
        cacheKey: String,
        fastPath: ((ClassLoader) -> Field?)? = null,
        dexKitFinder: ((DexKitBridge) -> String?)? = null,
        structuralFallback: ((ClassLoader) -> Field?)? = null
    ): ResolveResult<Field> {
        // 1. FastPath
        if (fastPath != null) {
            val hit = runCatching { fastPath(classLoader) }.getOrNull()
            if (hit != null) {
                val descriptor = DexField(hit).toString()
                return ResolveResult(hit, ResolveSource.FAST_PATH, descriptor, "${hit.declaringClass.name}#${hit.name}")
            }
        }

        // 2. Cache
        val storageKey = buildCacheKey(versionCode, cacheKey)
        val cachedDescriptor = prefs?.getString(storageKey, null)
        if (!cachedDescriptor.isNullOrBlank()) {
            val cached = runCatching {
                DexField.deserialize(cachedDescriptor).getFieldInstance(classLoader)
            }.getOrNull()
            if (cached != null) {
                return ResolveResult(cached, ResolveSource.CACHE, cachedDescriptor, "${cached.declaringClass.name}#${cached.name}")
            }
        }

        // 3. Lazy DexKit
        if (dexKitFinder != null) {
            val dexBridge = getOrCreateBridge()
            if (dexBridge != null) {
                val foundDescriptor = runCatching { dexKitFinder(dexBridge) }.getOrNull()
                if (!foundDescriptor.isNullOrBlank()) {
                    val resolved = runCatching {
                        DexField.deserialize(foundDescriptor).getFieldInstance(classLoader)
                    }.getOrNull()
                    if (resolved != null) {
                        saveCacheDescriptor(storageKey, foundDescriptor)
                        return ResolveResult(resolved, ResolveSource.DEXKIT, foundDescriptor, "${resolved.declaringClass.name}#${resolved.name}")
                    }
                }
            }
        }

        // 4. Structural fallback
        if (structuralFallback != null) {
            val fallback = runCatching { structuralFallback(classLoader) }.getOrNull()
            if (fallback != null) {
                val descriptor = DexField(fallback).toString()
                saveCacheDescriptor(storageKey, descriptor)
                return ResolveResult(fallback, ResolveSource.STRUCTURAL_FALLBACK, descriptor, "${fallback.declaringClass.name}#${fallback.name}")
            }
        }

        return ResolveResult(null, ResolveSource.MISS)
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

        fun buildCacheKey(versionCode: Long, cacheKey: String): String {
            return "v$versionCode:$cacheKey"
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
