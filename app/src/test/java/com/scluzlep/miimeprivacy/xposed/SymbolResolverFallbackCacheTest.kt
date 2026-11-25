package com.scluzlep.miimeprivacy.xposed

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.FindField
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.wrap.DexClass
import org.luckypray.dexkit.wrap.DexField

class SymbolResolverFallbackCacheTest {

    @Test
    fun structuralFallbackDoesNotWriteSymbolCacheForClassMethodOrField() {
        val prefs = InMemorySharedPreferences()
        val loader = checkNotNull(FallbackTarget::class.java.classLoader)
        val appInfo = ApplicationInfo().apply { sourceDir = "" }
        val noMatchDexKit = object : DexKitExecutor {
            override fun findClass(query: FindClass): List<DexDescriptorResult> = emptyList()
            override fun findMethod(query: FindMethod): List<DexDescriptorResult> = emptyList()
            override fun findField(query: FindField): List<DexDescriptorResult> = emptyList()
        }

        for (mode in listOf(SymbolResolveMode.AUTO, SymbolResolveMode.DYNAMIC)) {
            val subPrefs = InMemorySharedPreferences()
            SymbolResolver(
                context = null,
                classLoader = loader,
                appInfo = appInfo,
                versionCode = 20160L,
                moduleNativeLibDir = null,
                resolveMode = mode,
                prefsOverride = subPrefs,
                dexKitExecutorOverride = noMatchDexKit
            ).use { resolver ->
                repeat(2) { attempt ->
                    val cls = resolver.resolveClass(
                        cacheKey = "fallback.class",
                        fastPathCandidates = listOf("com.scluzlep.missing.ClassName"),
                        dexKitFinder = { dk -> dk.findClass(FindClass.create()).firstOrNull()?.descriptor },
                        structuralFallback = { FallbackTarget::class.java }
                    )
                    assertEquals("mode=$mode attempt=$attempt class", ResolveSource.STRUCTURAL_FALLBACK, cls.source)
                    assertEquals(FallbackTarget::class.java, cls.value)

                    val method = resolver.resolveMethod(
                        cacheKey = "fallback.method",
                        fastPath = { null },
                        dexKitFinder = { dk -> dk.findMethod(FindMethod.create()).firstOrNull()?.descriptor },
                        structuralFallback = { cl ->
                            cl.loadClass(FallbackTarget::class.java.name)
                                .getDeclaredMethod("ping", String::class.java)
                        }
                    )
                    assertEquals("mode=$mode attempt=$attempt method", ResolveSource.STRUCTURAL_FALLBACK, method.source)
                    assertNotNull(method.value)

                    val field = resolver.resolveField(
                        cacheKey = "fallback.field",
                        fastPath = { null },
                        dexKitFinder = { dk -> dk.findField(FindField.create()).firstOrNull()?.descriptor },
                        structuralFallback = { cl ->
                            cl.loadClass(FallbackTarget::class.java.name).getDeclaredField("token")
                        }
                    )
                    assertEquals("mode=$mode attempt=$attempt field", ResolveSource.STRUCTURAL_FALLBACK, field.source)
                    assertNotNull(field.value)

                    assertEquals(
                        "Structural fallbacks must never populate symbol cache (mode=$mode attempt=$attempt)",
                        0,
                        SymbolResolver.countCachedSymbols(subPrefs)
                    )
                }
            }
        }

        // Verify a structural fallback in run 1 does NOT block DexKit from running & caching in run 2
        SymbolResolver(
            context = null,
            classLoader = loader,
            appInfo = appInfo,
            versionCode = 20160L,
            moduleNativeLibDir = null,
            resolveMode = SymbolResolveMode.DYNAMIC,
            prefsOverride = prefs,
            dexKitExecutorOverride = noMatchDexKit
        ).use { firstRun ->
            val cls = firstRun.resolveClass(
                cacheKey = "fallback.upgrade",
                fastPathCandidates = listOf(FallbackTarget::class.java.name),
                dexKitFinder = { dk -> dk.findClass(FindClass.create()).firstOrNull()?.descriptor },
                structuralFallback = { FallbackTarget::class.java }
            )
            assertEquals(ResolveSource.STRUCTURAL_FALLBACK, cls.source)
            assertEquals(0, SymbolResolver.countCachedSymbols(prefs))
        }

        val matchingDexKit = object : DexKitExecutor {
            override fun findClass(query: FindClass): List<DexDescriptorResult> =
                listOf(DexDescriptorResult(DexClass(FallbackTarget::class.java).toString(), FallbackTarget::class.java.name))

            override fun findMethod(query: FindMethod): List<DexDescriptorResult> =
                listOf(
                    DexDescriptorResult(
                        SymbolResolver.executableToDescriptor(
                            FallbackTarget::class.java.getDeclaredMethod("ping", String::class.java)
                        ),
                        FallbackTarget::class.java.name
                    )
                )

            override fun findField(query: FindField): List<DexDescriptorResult> =
                listOf(
                    DexDescriptorResult(
                        DexField(FallbackTarget::class.java.getDeclaredField("token")).toString(),
                        FallbackTarget::class.java.name
                    )
                )
        }

        SymbolResolver(
            context = null,
            classLoader = loader,
            appInfo = appInfo,
            versionCode = 20160L,
            moduleNativeLibDir = null,
            resolveMode = SymbolResolveMode.DYNAMIC,
            prefsOverride = prefs,
            dexKitExecutorOverride = matchingDexKit
        ).use { secondRun ->
            val cls = secondRun.resolveClass(
                cacheKey = "fallback.upgrade",
                fastPathCandidates = listOf(FallbackTarget::class.java.name),
                dexKitFinder = { dk -> dk.findClass(FindClass.create()).firstOrNull()?.descriptor },
                structuralFallback = { FallbackTarget::class.java }
            )
            assertEquals(ResolveSource.DEXKIT, cls.source)
            assertEquals(1, SymbolResolver.countCachedSymbols(prefs))
        }
    }

    private class FallbackTarget(@Suppress("unused") val token: String) {
        @Suppress("unused")
        fun ping(arg: String): String = "$token:$arg"
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = data.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = data[key] as? String ?: defValue

        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (data[key] as? Set<String>)?.toMutableSet() ?: defValues

        override fun getInt(key: String?, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = data.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = hashSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = values?.toSet()
            }

            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = apply {
                if (key != null) pending[key] = value
            }

            override fun remove(key: String?): SharedPreferences.Editor = apply {
                if (key != null) removals.add(key)
            }

            override fun clear(): SharedPreferences.Editor = apply {
                clearAll = true
            }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) data.clear()
                removals.forEach { data.remove(it) }
                pending.forEach { (k, v) ->
                    if (v == null) data.remove(k) else data[k] = v
                }
            }
        }
    }
}
