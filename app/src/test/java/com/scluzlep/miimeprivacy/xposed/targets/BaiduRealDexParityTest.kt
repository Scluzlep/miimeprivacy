package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.InMemorySharedPreferences
import com.scluzlep.miimeprivacy.xposed.RealApkDexSimulationHarness
import com.scluzlep.miimeprivacy.xposed.RecordingXposedInterface
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class BaiduRealDexParityTest {

    private val allBaiduNonOverlayRules by lazy {
        HookRuleCatalog.rulesFor(ImeTarget.BAIDU)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }
    }

    @Before
    fun requireRealBaiduApk() {
        val apk = RealApkDexSimulationHarness.apkFileFor(ImeTarget.BAIDU)
        Assume.assumeTrue(
            "Skipping real-APK Baidu parity test: missing ${apk.absolutePath}",
            apk.isFile
        )
    }

    @After
    fun resetConfigBridge() {
        val defaultPrefs = InMemorySharedPreferences()
        PrivacyConfig(updatedAtMs = ConfigBridge.current.updatedAtMs + 1L)
            .toSharedPreferences(defaultPrefs, commitSync = true)
        ConfigBridge.detach()
        ConfigBridge.attachRemote(
            RecordingXposedInterface(
                remotePrefsMap = mapOf(ConfigContract.REMOTE_PREFS_NAME to defaultPrefs)
            )
        )
        ConfigBridge.detach()
    }

    private fun allEnabledBaiduConfig(): PrivacyConfig {
        var cfg = PrivacyConfig(
            baiduManaged = true,
            blockHttpDns = true
        )
        for (ruleId in allBaiduNonOverlayRules) {
            cfg = cfg.withRuleOverride(ruleId, true)
        }
        return cfg
    }

    @Test
    fun allFourGates_autoFastPath_dynamicDexKit_descriptorParity_andWarmCache() {
        val config = allEnabledBaiduConfig()
        assertEquals(23, allBaiduNonOverlayRules.size)
        for (ruleId in allBaiduNonOverlayRules) {
            assertTrue("Expected $ruleId enabled in test config", config.isRuleEnabled(ruleId))
        }

        // Gate 1: AUTO mode -> 100% FAST_PATH, all 23 rules ACTIVE
        val autoSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.AUTO,
            config = config,
            prefs = InMemorySharedPreferences()
        )
        val autoResult = autoSession.installPack(BaiduHookPack)

        val autoNonFastTraces = autoSession.resolutionTraces.filter { it.source != ResolveSource.FAST_PATH }
        val autoMissTraces = autoSession.resolutionTraces.filter { it.source == ResolveSource.MISS }
        val autoBadReports = autoResult.reports.filter { it.status != HookMatchStatus.ACTIVE }

        // Gate 2: DYNAMIC mode (cold prefs) -> 100% DEXKIT, 0 FAST_PATH, 0 STRUCTURAL_FALLBACK, 0 MISS
        val dynamicPrefs = InMemorySharedPreferences()
        val dynamicSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = config,
            prefs = dynamicPrefs
        )
        val dynamicResult = dynamicSession.installPack(BaiduHookPack)

        val dynNonDexKitTraces = dynamicSession.resolutionTraces.filter { it.source != ResolveSource.DEXKIT }
        val dynBadReports = dynamicResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("FAST_PATH") ||
                it.source.contains("STRUCTURAL_FALLBACK") ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:", ignoreCase = true)
        }

        // Gate 3: 1-to-1 Descriptor Parity (recordedHooks & resolutionTraces)
        val autoHooksById = autoSession.xposed.recordedHooks.associate { hook ->
            requireNotNull(hook.id) { "Null hookId in AUTO mode" } to
                SymbolResolver.executableToDescriptor(hook.executable)
        }
        val dynamicHooksById = dynamicSession.xposed.recordedHooks.associate { hook ->
            requireNotNull(hook.id) { "Null hookId in DYNAMIC mode" } to
                SymbolResolver.executableToDescriptor(hook.executable)
        }

        val autoTraceMap = autoSession.resolutionTraces.associate {
            (it.kind to it.cacheKey) to it.descriptor
        }
        val dynamicTraceMap = dynamicSession.resolutionTraces.associate {
            (it.kind to it.cacheKey) to it.descriptor
        }

        // Gate 4: Warm CACHE mode (re-run DYNAMIC on warmed dynamicPrefs)
        val cacheSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.BAIDU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = config,
            prefs = dynamicPrefs
        )
        val cacheResult = cacheSession.installPack(BaiduHookPack)
        val cacheNonCacheTraces = cacheSession.resolutionTraces.filter { it.source != ResolveSource.CACHE }
        val cacheBadReports = cacheResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        val cacheHooksById = cacheSession.xposed.recordedHooks.associate { hook ->
            requireNotNull(hook.id) { "Null hookId in CACHE mode" } to
                SymbolResolver.executableToDescriptor(hook.executable)
        }

        // Aggregate diagnostic report before failing so all gaps are visible in one run
        val failures = buildString {
            if (autoNonFastTraces.isNotEmpty() || autoMissTraces.isNotEmpty()) {
                appendLine("=== GATE 1 (AUTO) non-FAST_PATH traces (${autoNonFastTraces.size}) ===")
                autoNonFastTraces.forEach { appendLine("  $it") }
            }
            if (autoBadReports.isNotEmpty()) {
                appendLine("=== GATE 1 (AUTO) non-ACTIVE reports (${autoBadReports.size}) ===")
                autoBadReports.forEach { appendLine("  $it") }
            }
            if (dynNonDexKitTraces.isNotEmpty()) {
                appendLine("=== GATE 2 (DYNAMIC) non-DEXKIT traces (${dynNonDexKitTraces.size}) ===")
                dynNonDexKitTraces.forEach { appendLine("  $it") }
            }
            if (dynBadReports.isNotEmpty()) {
                appendLine("=== GATE 2 (DYNAMIC) bad reports (${dynBadReports.size}) ===")
                dynBadReports.forEach { appendLine("  $it") }
            }
            if (autoHooksById != dynamicHooksById) {
                appendLine("=== GATE 3 hookId->descriptor diff (AUTO=${autoHooksById.size}, DYNAMIC=${dynamicHooksById.size}) ===")
                val allKeys = (autoHooksById.keys + dynamicHooksById.keys).sorted()
                for (k in allKeys) {
                    if (autoHooksById[k] != dynamicHooksById[k]) {
                        appendLine("  $k: AUTO=${autoHooksById[k]} | DYNAMIC=${dynamicHooksById[k]}")
                    }
                }
            }
            if (autoTraceMap != dynamicTraceMap) {
                appendLine("=== GATE 3 (kind,cacheKey)->descriptor diff ===")
                val allKeys = (autoTraceMap.keys + dynamicTraceMap.keys).sortedBy { "${it.first}:${it.second}" }
                for (k in allKeys) {
                    if (autoTraceMap[k] != dynamicTraceMap[k]) {
                        appendLine("  $k: AUTO=${autoTraceMap[k]} | DYNAMIC=${dynamicTraceMap[k]}")
                    }
                }
            }
            if (cacheNonCacheTraces.isNotEmpty() || cacheBadReports.isNotEmpty() || cacheHooksById != dynamicHooksById) {
                appendLine("=== GATE 4 (CACHE) issues: nonCacheTraces=${cacheNonCacheTraces.size}, badReports=${cacheBadReports.size} ===")
                cacheNonCacheTraces.forEach { appendLine("  $it") }
                cacheBadReports.forEach { appendLine("  $it") }
            }
        }
        assertTrue("Parity gate failures:\n$failures", failures.isEmpty())

        // Explicit assertions for each gate
        assertEquals(23, autoResult.reports.size)
        assertTrue(autoSession.resolutionTraces.isNotEmpty())
        assertTrue(autoNonFastTraces.isEmpty())
        assertTrue(autoBadReports.isEmpty())

        assertEquals(23, dynamicResult.reports.size)
        assertTrue(dynamicSession.resolutionTraces.isNotEmpty())
        assertTrue(dynNonDexKitTraces.isEmpty())
        assertTrue(dynBadReports.isEmpty())
        assertFalse(dynamicResult.reports.any { it.source.contains("FAST_PATH") })
        assertFalse(dynamicResult.reports.any { it.source.contains("STRUCTURAL_FALLBACK") })

        assertEquals(autoSession.xposed.recordedHooks.size, dynamicSession.xposed.recordedHooks.size)
        assertEquals(autoHooksById, dynamicHooksById)
        assertEquals(autoTraceMap, dynamicTraceMap)

        assertEquals(23, cacheResult.reports.size)
        assertTrue(cacheNonCacheTraces.isEmpty())
        assertTrue(cacheBadReports.isEmpty())
        assertEquals(dynamicHooksById, cacheHooksById)

        println(
            "[BaiduRealDexParityTest] AUTO hooks=${autoHooksById.size}, traces=${autoSession.resolutionTraces.size} | " +
                "DYNAMIC hooks=${dynamicHooksById.size}, traces=${dynamicSession.resolutionTraces.size} | " +
                "CACHE hooks=${cacheHooksById.size}, traces=${cacheSession.resolutionTraces.size}"
        )
    }
}
