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
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test

class XiaoAiRealDexParityTest {

    @Before
    fun requireRealXiaoAiApk() {
        val apk = RealApkDexSimulationHarness.apkFileFor(ImeTarget.XIAOAI)
        Assume.assumeTrue(
            "Skipping XiaoAi real-APK parity test: missing ${apk.absolutePath}",
            apk.isFile
        )
    }

    @After
    fun restoreConfigBridgeDefaults() {
        val resetPrefs = InMemorySharedPreferences()
        PrivacyConfig(updatedAtMs = ConfigBridge.current.updatedAtMs + 1L)
            .toSharedPreferences(resetPrefs, commitSync = true)
        val xposed = RecordingXposedInterface(
            remotePrefsMap = mapOf(ConfigContract.REMOTE_PREFS_NAME to resetPrefs)
        )
        ConfigBridge.detach()
        ConfigBridge.attachRemote(xposed)
        ConfigBridge.detach()
    }

    private fun allEnabledXiaoAiConfig(): PrivacyConfig {
        var cfg = PrivacyConfig(xiaoaiManaged = true)
        for (spec in HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)) {
            if (!spec.isOverlayPill) {
                cfg = cfg.withRuleOverride(spec.ruleId, true)
            }
        }
        return cfg
    }

    @Test
    fun allFourGatesPassWithZeroFallbackAndExactDescriptorParityAcrossAll19Rules() {
        val allEnabledConfig = allEnabledXiaoAiConfig()
        val expectedRuleIds = HookRuleCatalog.rulesFor(ImeTarget.XIAOAI)
            .filterNot { it.isOverlayPill }
            .map { it.ruleId }
            .toSet()
        assertEquals(19, expectedRuleIds.size)

        // Gate 1: AUTO mode (100% FAST_PATH, 0 MISS, all 19 ACTIVE)
        val autoSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.AUTO,
            config = allEnabledConfig
        )
        val autoResult = autoSession.installPack(XiaoAiHookPack)
        assertEquals(expectedRuleIds, autoResult.reports.map { it.ruleId }.toSet())
        val autoNonActive = autoResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertTrue(
            "Gate 1 AUTO non-ACTIVE (${autoNonActive.size}): ${autoNonActive.joinToString { "${it.ruleId}=${it.status}(${it.detail})" }}",
            autoNonActive.isEmpty()
        )
        assertTrue("Gate 1 AUTO expected non-empty resolutionTraces", autoSession.resolutionTraces.isNotEmpty())
        val autoNonFastPath = autoSession.resolutionTraces.filter { it.source != ResolveSource.FAST_PATH }
        assertTrue(
            "Gate 1 AUTO non-FAST_PATH (${autoNonFastPath.size}): ${autoNonFastPath.joinToString { "${it.kind}:${it.cacheKey}=${it.source}" }}",
            autoNonFastPath.isEmpty()
        )

        // Gate 2: DYNAMIC mode, cold prefs (100% DEXKIT, 0 FAST_PATH, 0 STRUCTURAL_FALLBACK, 0 MISS, all 19 ACTIVE)
        val coldPrefs = InMemorySharedPreferences()
        val dynamicSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allEnabledConfig,
            prefs = coldPrefs
        )
        val dynamicResult = dynamicSession.installPack(XiaoAiHookPack)
        assertEquals(expectedRuleIds, dynamicResult.reports.map { it.ruleId }.toSet())
        val dynamicNonActive = dynamicResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertTrue(
            "Gate 2 DYNAMIC non-ACTIVE (${dynamicNonActive.size}): ${dynamicNonActive.joinToString { "${it.ruleId}=${it.status}(${it.detail})" }}",
            dynamicNonActive.isEmpty()
        )
        val dynamicNonDexKit = dynamicSession.resolutionTraces.filter { it.source != ResolveSource.DEXKIT }
        assertTrue(
            "Gate 2 DYNAMIC non-DEXKIT (${dynamicNonDexKit.size}): ${dynamicNonDexKit.joinToString { "${it.kind}:${it.cacheKey}=${it.source}(${it.descriptor})" }}",
            dynamicNonDexKit.isEmpty()
        )

        // Gate 3: 1-to-1 Descriptor Parity (hookId -> descriptor AND (kind, cacheKey) -> descriptor)
        val autoHookMap = autoSession.xposed.recordedHooks.associate {
            requireNotNull(it.id) { "AUTO hook missing id" } to SymbolResolver.executableToDescriptor(it.executable)
        }
        val dynamicHookMap = dynamicSession.xposed.recordedHooks.associate {
            requireNotNull(it.id) { "DYNAMIC hook missing id" } to SymbolResolver.executableToDescriptor(it.executable)
        }
        assertEquals(
            "Gate 3 hookId -> descriptor count mismatch (AUTO=${autoSession.xposed.recordedHooks.size}, DYNAMIC=${dynamicSession.xposed.recordedHooks.size})",
            autoSession.xposed.recordedHooks.size,
            dynamicSession.xposed.recordedHooks.size
        )
        assertEquals("Gate 3 hookId -> descriptor mismatch", autoHookMap, dynamicHookMap)

        val autoTraceMap = autoSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        val dynamicTraceMap = dynamicSession.resolutionTraces.associate { (it.kind to it.cacheKey) to it.descriptor }
        assertEquals(
            "Gate 3 trace count mismatch (AUTO=${autoSession.resolutionTraces.size}, DYNAMIC=${dynamicSession.resolutionTraces.size})",
            autoSession.resolutionTraces.size,
            dynamicSession.resolutionTraces.size
        )
        assertEquals("Gate 3 (kind, cacheKey) -> descriptor mismatch", autoTraceMap, dynamicTraceMap)

        // Gate 4: Warm CACHE (re-running DYNAMIC on warmed prefs -> 100% CACHE, 0 DEXKIT, 0 MISS, identical hooks)
        val warmSession = RealApkDexSimulationHarness.openSimulation(
            target = ImeTarget.XIAOAI,
            resolveMode = SymbolResolveMode.DYNAMIC,
            config = allEnabledConfig,
            prefs = coldPrefs
        )
        val warmResult = warmSession.installPack(XiaoAiHookPack)
        assertEquals(expectedRuleIds, warmResult.reports.map { it.ruleId }.toSet())
        val warmNonActive = warmResult.reports.filter { it.status != HookMatchStatus.ACTIVE }
        assertTrue(
            "Gate 4 CACHE non-ACTIVE (${warmNonActive.size}): ${warmNonActive.joinToString { "${it.ruleId}=${it.status}(${it.detail})" }}",
            warmNonActive.isEmpty()
        )
        val warmNonCache = warmSession.resolutionTraces.filter { it.source != ResolveSource.CACHE }
        assertTrue(
            "Gate 4 CACHE non-CACHE (${warmNonCache.size}): ${warmNonCache.joinToString { "${it.kind}:${it.cacheKey}=${it.source}" }}",
            warmNonCache.isEmpty()
        )
        val warmHookMap = warmSession.xposed.recordedHooks.associate {
            requireNotNull(it.id) { "CACHE hook missing id" } to SymbolResolver.executableToDescriptor(it.executable)
        }
        assertEquals("Gate 4 CACHE hookId -> descriptor mismatch", autoHookMap, warmHookMap)
        println(
            "XiaoAiRealDexParityTest PASS: rules=${expectedRuleIds.size}, hooks=${autoHookMap.size}, traces=${autoTraceMap.size}"
        )
    }
}
