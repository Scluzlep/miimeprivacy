package com.scluzlep.miimeprivacy.xposed.targets

import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.emoji.SogouDualSkinPopupHook
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.InMemorySharedPreferences
import com.scluzlep.miimeprivacy.xposed.RealApkDexSimulationHarness
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Executable

class SogouRealDexParityTest {

    private val allSogouRulesEnabledConfig: PrivacyConfig by lazy {
        var cfg = PrivacyConfig(sogouManaged = true)
        for (spec in HookRuleCatalog.rulesFor(ImeTarget.SOGOU)) {
            if (!spec.isOverlayPill) {
                cfg = cfg.withRuleOverride(spec.ruleId, true)
            }
        }
        cfg
    }

    private val expectedNonOverlayRuleIds: Set<String> by lazy {
        HookRuleCatalog.rulesFor(ImeTarget.SOGOU)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }
            .toSet()
    }

    @Before
    fun requireRealSogouApk() {
        val apk = RealApkDexSimulationHarness.apkFileFor(ImeTarget.SOGOU)
        Assume.assumeTrue("Missing real Sogou APK: ${apk.absolutePath}", apk.isFile)
    }

    @After
    fun restoreConfigBridgeAndCaches() {
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        if (RealApkDexSimulationHarness.apkFileFor(ImeTarget.SOGOU).isFile) {
            RealApkDexSimulationHarness.openSimulation(
                target = ImeTarget.SOGOU,
                resolveMode = SymbolResolveMode.AUTO,
                config = PrivacyConfig()
            )
        }
        ConfigBridge.detach()
    }

    @Test
    fun allFourGates_autoDynamicParityAndWarmCache_againstRealSogouApk() {
        val failures = mutableListOf<String>()

        // ---------------------------------------------------------------------
        // Gate 1: AUTO mode (100% FAST_PATH, all 29 rules ACTIVE, 0 MISS)
        // ---------------------------------------------------------------------
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val autoSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.AUTO,
            config = allSogouRulesEnabledConfig
        )
        val autoResult = autoSession.installPack(SogouHookPack)
        val autoReportsByRule = autoResult.reports.associateBy { it.ruleId }

        assertEquals(29, expectedNonOverlayRuleIds.size)
        assertEquals(expectedNonOverlayRuleIds, autoReportsByRule.keys)

        val autoNonFastTraces = autoSession.resolutionTraces.filter { it.source != ResolveSource.FAST_PATH }
        if (autoNonFastTraces.isNotEmpty()) {
            failures += "Gate 1 [AUTO] non-FAST_PATH traces (${autoNonFastTraces.size}):\n" +
                autoNonFastTraces.joinToString("\n") { "  ${it.kind} ${it.cacheKey} -> ${it.source} (${it.descriptor})" }
        }
        assertTrue("Gate 1 [AUTO] resolutionTraces should not be empty", autoSession.resolutionTraces.isNotEmpty())

        val autoBadRules = autoResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:") ||
                it.source.startsWith("FAST_PATH,")
        }
        if (autoBadRules.isNotEmpty()) {
            failures += "Gate 1 [AUTO] non-ACTIVE rules (${autoBadRules.size}):\n" +
                autoBadRules.joinToString("\n") { "  ${it.ruleId}: status=${it.status} source=${it.source} detail=${it.detail}" }
        }

        // ---------------------------------------------------------------------
        // Gate 2: DYNAMIC mode on cold prefs (100% DEXKIT, 0 FAST_PATH, 0 STRUCTURAL_FALLBACK, 0 MISS)
        // ---------------------------------------------------------------------
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val dynamicPrefs = InMemorySharedPreferences()
        val dynamicSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allSogouRulesEnabledConfig,
            prefs = dynamicPrefs
        )
        val dynamicResult = dynamicSession.installPack(SogouHookPack)
        val dynamicReportsByRule = dynamicResult.reports.associateBy { it.ruleId }

        assertEquals(expectedNonOverlayRuleIds, dynamicReportsByRule.keys)

        val dynamicNonDexKitTraces = dynamicSession.resolutionTraces.filter { it.source != ResolveSource.DEXKIT }
        if (dynamicNonDexKitTraces.isNotEmpty()) {
            failures += "Gate 2 [DYNAMIC] non-DEXKIT traces (${dynamicNonDexKitTraces.size}):\n" +
                dynamicNonDexKitTraces.joinToString("\n") { "  ${it.kind} ${it.cacheKey} -> ${it.source} (${it.descriptor})" }
        }
        assertTrue("Gate 2 [DYNAMIC] resolutionTraces should not be empty", dynamicSession.resolutionTraces.isNotEmpty())

        val dynamicBadRules = dynamicResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("FAST_PATH") ||
                it.source.contains("STRUCTURAL_FALLBACK") ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:")
        }
        if (dynamicBadRules.isNotEmpty()) {
            failures += "Gate 2 [DYNAMIC] non-ACTIVE/non-DEXKIT rules (${dynamicBadRules.size}):\n" +
                dynamicBadRules.joinToString("\n") { "  ${it.ruleId}: status=${it.status} source=${it.source} detail=${it.detail}" }
        }

        // ---------------------------------------------------------------------
        // Gate 3: 1-to-1 Descriptor Parity (AUTO vs DYNAMIC)
        // ---------------------------------------------------------------------
        val autoHookMap = buildHookDescriptorMap(autoSession.xposed.recordedHooks.map { it.id to it.executable })
        val dynamicHookMap = buildHookDescriptorMap(dynamicSession.xposed.recordedHooks.map { it.id to it.executable })
        if (autoHookMap != dynamicHookMap) {
            val missingInDyn = autoHookMap.keys - dynamicHookMap.keys
            val extraInDyn = dynamicHookMap.keys - autoHookMap.keys
            val diffValues = autoHookMap.keys.intersect(dynamicHookMap.keys)
                .filter { autoHookMap[it] != dynamicHookMap[it] }
            failures += buildString {
                appendLine("Gate 3 [PARITY] recordedHooks mismatch (auto=${autoHookMap.size}, dynamic=${dynamicHookMap.size}):")
                if (missingInDyn.isNotEmpty()) appendLine("  missingInDynamic(${missingInDyn.size}): $missingInDyn")
                if (extraInDyn.isNotEmpty()) appendLine("  extraInDynamic(${extraInDyn.size}): $extraInDyn")
                diffValues.forEach { k ->
                    appendLine("  diff[$k]: auto=${autoHookMap[k]} vs dynamic=${dynamicHookMap[k]}")
                }
            }.trimEnd()
        }

        val autoTraceMap = autoSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        val dynamicTraceMap = dynamicSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        if (autoTraceMap != dynamicTraceMap) {
            val allKeys = autoTraceMap.keys + dynamicTraceMap.keys
            val diffs = allKeys.filter { autoTraceMap[it] != dynamicTraceMap[it] }
            failures += "Gate 3 [PARITY] trace mismatches (${diffs.size}):\n" +
                diffs.joinToString("\n") { k -> "  $k: auto=${autoTraceMap[k]} vs dynamic=${dynamicTraceMap[k]}" }
        }

        // ---------------------------------------------------------------------
        // Gate 4: Warm CACHE (DYNAMIC re-run on warmed prefs -> 100% CACHE, 0 DEXKIT, 0 MISS)
        // ---------------------------------------------------------------------
        SogouHookPack.resetCaches()
        SogouDualSkinPopupHook.clearOnHotReload()
        val warmSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.SOGOU,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allSogouRulesEnabledConfig,
            prefs = dynamicPrefs
        )
        val warmResult = warmSession.installPack(SogouHookPack)

        val warmNonCacheTraces = warmSession.resolutionTraces.filter { it.source != ResolveSource.CACHE }
        if (warmNonCacheTraces.isNotEmpty()) {
            failures += "Gate 4 [WARM CACHE] non-CACHE traces (${warmNonCacheTraces.size}):\n" +
                warmNonCacheTraces.joinToString("\n") { "  ${it.kind} ${it.cacheKey} -> ${it.source} (${it.descriptor})" }
        }

        val warmBadRules = warmResult.reports.filter {
            it.status != HookMatchStatus.ACTIVE ||
                it.source.contains("DEXKIT") ||
                it.source.contains("FAST_PATH") ||
                it.source.contains("STRUCTURAL_FALLBACK") ||
                it.source.contains("MISS") ||
                it.detail.contains("miss:")
        }
        if (warmBadRules.isNotEmpty()) {
            failures += "Gate 4 [WARM CACHE] non-ACTIVE/non-CACHE rules (${warmBadRules.size}):\n" +
                warmBadRules.joinToString("\n") { "  ${it.ruleId}: status=${it.status} source=${it.source} detail=${it.detail}" }
        }

        val warmHookMap = buildHookDescriptorMap(warmSession.xposed.recordedHooks.map { it.id to it.executable })
        if (autoHookMap != warmHookMap) {
            failures += "Gate 4 [WARM CACHE] recordedHooks mismatch vs AUTO (auto=${autoHookMap.size}, warm=${warmHookMap.size})"
        }

        assertTrue(failures.joinToString("\n\n"), failures.isEmpty())
        println(
            "SogouRealDexParityTest SUMMARY: rules=${autoResult.reports.size}, " +
                "autoHooks=${autoHookMap.size}, dynamicHooks=${dynamicHookMap.size}, warmHooks=${warmHookMap.size}, " +
                "autoTraces=${autoSession.resolutionTraces.size}, dynamicTraces=${dynamicSession.resolutionTraces.size}, warmTraces=${warmSession.resolutionTraces.size}"
        )
    }

    private fun buildHookDescriptorMap(hooks: List<Pair<String?, Executable>>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for ((id, exec) in hooks) {
            val key = id ?: error("Hook recorded with null id for $exec")
            val desc = SymbolResolver.executableToDescriptor(exec)
            val prev = map.put(key, desc)
            assertTrue("Duplicate hookId recorded: $key (prev=$prev, new=$desc)", prev == null)
        }
        return map
    }
}
