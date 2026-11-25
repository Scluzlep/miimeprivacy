package com.scluzlep.miimeprivacy

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.model.RuleCategory
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigAndSymbolTest {

    @Test
    fun imeTargetLookupMatchesExpectedPackages() {
        assertEquals(ImeTarget.SOGOU, ImeTarget.fromPackage("com.sohu.inputmethod.sogou.xiaomi"))
        assertEquals(ImeTarget.BAIDU, ImeTarget.fromPackage("com.baidu.input_mi"))
        assertEquals(ImeTarget.XIAOAI, ImeTarget.fromPackage("com.xiaomi.type"))
        assertNull(ImeTarget.fromPackage(null))
        assertNull(ImeTarget.fromPackage("com.other.ime"))
    }

    @Test
    fun privacyConfigDefaultsMatchSpecification() {
        val cfg = PrivacyConfig()
        assertTrue(cfg.sogouManaged)
        assertTrue(cfg.baiduManaged)
        assertTrue(cfg.xiaoaiManaged)
        assertTrue(cfg.allowCloudInput)
        assertEquals(CloudOverride.FOLLOW_GLOBAL, cfg.sogouCloudOverride)
        assertEquals(CloudOverride.FOLLOW_GLOBAL, cfg.baiduCloudOverride)
        assertEquals(CloudOverride.FOLLOW_GLOBAL, cfg.xiaoaiCloudOverride)

        // Purification defaults
        assertTrue(cfg.blockTelemetry)
        assertTrue(cfg.blockPasswordStats)
        assertTrue(cfg.blockRemoteLogSalvage)
        assertTrue(cfg.sanitizeCloudPayload)
        assertTrue(cfg.stripCursorContext)
        assertTrue(cfg.maskHostPackage)
        assertTrue(cfg.stripTouchPositions)
        assertTrue(cfg.clipboardPrivacy)
        assertTrue(cfg.blockContactsAndAppScan)
        assertTrue(cfg.blockUnsignedDynamicDex)
        assertTrue(cfg.blockMutualData)

        // Strict defaults
        assertFalse(cfg.blockHttpDns)
        assertFalse(cfg.blockXiaoAiHotwordTracker)

        // Pill & theme defaults
        assertTrue(cfg.showKeyboardPill)
        assertTrue(cfg.hidePillInLandscape)
        assertEquals(0.10f, PrivacyConfig.MIN_PILL_ALPHA, 0.0001f)
        assertEquals(0.88f, cfg.pillAlpha, 0.0001f)
        for (target in ImeTarget.entries) {
            assertEquals(PillOffset(12, 2), cfg.pillOffsetFor(target))
        }
        assertEquals(ThemeMode.SYSTEM, cfg.themeMode)
        assertTrue(cfg.monetEnabled)
        assertEquals(MonetPaletteStyle.TonalSpot, cfg.paletteStyle)
        assertEquals(0L, cfg.updatedAtMs)
    }

    @Test
    fun effectiveCloudAndOverridesRespectPerImePolicy() {
        val base = PrivacyConfig(allowCloudInput = false)
            .withCloudOverride(ImeTarget.SOGOU, CloudOverride.FORCE_ON)
            .withCloudOverride(ImeTarget.BAIDU, CloudOverride.FORCE_OFF)
            .withCloudOverride(ImeTarget.XIAOAI, CloudOverride.FOLLOW_GLOBAL)

        assertTrue(base.effectiveCloudEnabled(ImeTarget.SOGOU))
        assertFalse(base.effectiveCloudEnabled(ImeTarget.BAIDU))
        assertFalse(base.effectiveCloudEnabled(ImeTarget.XIAOAI))

        val globalOn = base.copy(allowCloudInput = true)
        assertTrue(globalOn.effectiveCloudEnabled(ImeTarget.SOGOU))
        assertFalse(globalOn.effectiveCloudEnabled(ImeTarget.BAIDU))
        assertTrue(globalOn.effectiveCloudEnabled(ImeTarget.XIAOAI))
    }

    @Test
    fun sanitizedClampsAlphaOffsetsAndTimestamp() {
        assertEquals(0.10f, PrivacyConfig.MIN_PILL_ALPHA, 0.0001f)
        assertEquals(-2000, PrivacyConfig.MIN_PILL_OFFSET_DP)
        assertEquals(2000, PrivacyConfig.MAX_PILL_OFFSET_DP)

        val dirty = PrivacyConfig(
            pillAlpha = Float.NaN,
            sogouPillOffsetXDp = 3500,
            sogouPillOffsetYDp = -3500,
            updatedAtMs = -42L
        ).sanitized()

        assertEquals(PrivacyConfig.DEFAULT_PILL_ALPHA, dirty.pillAlpha, 0.0001f)
        assertEquals(PrivacyConfig.MAX_PILL_OFFSET_DP, dirty.sogouPillOffsetXDp)
        assertEquals(PrivacyConfig.MIN_PILL_OFFSET_DP, dirty.sogouPillOffsetYDp)
        assertEquals(0L, dirty.updatedAtMs)

        val lowAlpha = PrivacyConfig(pillAlpha = 0.01f).sanitized()
        assertEquals(0.10f, lowAlpha.pillAlpha, 0.0001f)
        assertEquals(PrivacyConfig.MIN_PILL_ALPHA, lowAlpha.pillAlpha, 0.0001f)

        val updated = PrivacyConfig().withPillOffset(ImeTarget.BAIDU, 3500, -3500)
        assertEquals(
            PillOffset(PrivacyConfig.MAX_PILL_OFFSET_DP, PrivacyConfig.MIN_PILL_OFFSET_DP),
            updated.pillOffsetFor(ImeTarget.BAIDU)
        )

        val rightEdge = PrivacyConfig().withPillOffset(ImeTarget.XIAOAI, 1200, -1200)
        assertEquals(PillOffset(1200, -1200), rightEdge.pillOffsetFor(ImeTarget.XIAOAI))
    }

    @Test
    fun jsonRoundTripAndMalformedFallbackWork() {
        val original = PrivacyConfig(
            sogouManaged = false,
            allowCloudInput = false,
            baiduCloudOverride = CloudOverride.FORCE_ON,
            blockHttpDns = true,
            blockXiaoAiHotwordTracker = true,
            hidePillInLandscape = false,
            pillAlpha = 0.65f,
            xiaoaiPillOffsetXDp = 24,
            xiaoaiPillOffsetYDp = 8,
            themeMode = ThemeMode.DARK,
            monetEnabled = false,
            paletteStyle = MonetPaletteStyle.Expressive,
            updatedAtMs = 1700000000123L
        )

        val parsed = PrivacyConfig.fromJson(original.toJson())
        assertEquals(original, parsed)
        assertFalse(parsed.hidePillInLandscape)
        assertTrue(PrivacyConfig.fromJson("{}").hidePillInLandscape)
        assertEquals(PrivacyConfig(), PrivacyConfig.fromJson("{not-json"))
        assertEquals(PrivacyConfig(), PrivacyConfig.fromJson(""))
    }

    @Test
    fun sharedPreferencesRoundTripAndMergeNewerWork() {
        val prefs = InMemorySharedPreferences()
        val cfg = PrivacyConfig(
            allowCloudInput = false,
            blockHttpDns = true,
            hidePillInLandscape = false,
            sogouPillOffsetXDp = 30,
            paletteStyle = MonetPaletteStyle.Vibrant,
            updatedAtMs = 5000L
        )
        cfg.toSharedPreferences(prefs, commitSync = true)
        assertEquals(cfg, PrivacyConfig.fromSharedPreferences(prefs))

        // Verify legacy typed-key fallback when KEY_CONFIG_JSON is absent
        prefs.edit().remove(ConfigContract.KEY_CONFIG_JSON).commit()
        assertEquals(cfg, PrivacyConfig.fromSharedPreferences(prefs))

        val older = cfg.copy(allowCloudInput = true, updatedAtMs = 4000L)
        val newer = cfg.copy(allowCloudInput = true, updatedAtMs = 6000L)
        assertEquals(cfg, PrivacyConfig.mergeNewer(cfg, older))
        assertEquals(newer, PrivacyConfig.mergeNewer(cfg, newer))
    }

    @Test
    fun diagnosticsRoundTripAndCriticalFailureDetectionWork() {
        val catalogRules = HookRuleCatalog.allRules()
        assertEquals(catalogRules.size, catalogRules.map { it.ruleId }.toSet().size)
        for (target in ImeTarget.entries) {
            assertTrue(HookRuleCatalog.rulesFor(target).isNotEmpty())
        }

        val diag = ImeRuntimeDiagnostics(
            target = ImeTarget.XIAOAI,
            packageVersionCode = 21363L,
            packageVersionName = "0.2.1363",
            processName = "com.xiaomi.type",
            moduleApiVersion = 102,
            frameworkName = "LSPosed",
            activeCloudEnabled = false,
            lastUpdatedAtMs = 99999L,
            rules = listOf(
                HookRuleReport(
                    ruleId = HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK,
                    status = HookMatchStatus.FAILED,
                    blockedCount = 0L,
                    detail = "method missing"
                ),
                HookRuleReport(
                    ruleId = HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD,
                    status = HookMatchStatus.ACTIVE,
                    matchedMethod = "ClipboardMonitor#onChanged",
                    source = "DEXKIT",
                    blockedCount = 7L
                )
            ),
            note = "partial"
        )

        val decoded = ImeRuntimeDiagnostics.fromJson(diag.toJson())
        assertNotNull(decoded)
        assertEquals(diag, decoded)
        assertEquals(7L, decoded!!.totalBlockedCount)
        assertTrue(decoded.hasCriticalFailure)

        val nonCriticalFail = diag.copy(
            rules = listOf(
                HookRuleReport(
                    ruleId = HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD,
                    status = HookMatchStatus.FAILED
                )
            )
        )
        assertFalse(nonCriticalFail.hasCriticalFailure)
    }

    @Test
    fun v2CatalogCountsCategoriesAndLookupHelpersMatchSpecification() {
        assertEquals(9, RuleCategory.entries.size)
        assertEquals(74, HookRuleCatalog.allRules().size)
        assertEquals(30, HookRuleCatalog.rulesFor(ImeTarget.SOGOU).size)
        assertEquals(24, HookRuleCatalog.rulesFor(ImeTarget.BAIDU).size)
        assertEquals(20, HookRuleCatalog.rulesFor(ImeTarget.XIAOAI).size)

        assertEquals(29, HookRuleCatalog.privacyRuleIdsForPackage(ImeTarget.SOGOU.packageName).size)
        assertEquals(23, HookRuleCatalog.privacyRuleIdsForPackage(ImeTarget.BAIDU.packageName).size)
        assertEquals(19, HookRuleCatalog.privacyRuleIdsForPackage(ImeTarget.XIAOAI.packageName).size)

        val overlayRules = HookRuleCatalog.allRules().filter { it.isOverlayPill }
        assertEquals(
            setOf(
                HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL,
                HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL,
                HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL
            ),
            overlayRules.map { it.ruleId }.toSet()
        )

        val defaultOffRules = HookRuleCatalog.allRules()
            .filterNot { it.defaultEnabled }
            .map { it.ruleId }
            .toSet()
        assertEquals(
            setOf(
                HookRuleCatalog.RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER,
                HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS,
                HookRuleCatalog.RULE_BAIDU_CLIP_STRICT_BLOCK_READERS,
                HookRuleCatalog.RULE_BAIDU_SCAN_SMS_OTP_LOCAL,
                HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS,
                HookRuleCatalog.RULE_XIAOAI_GATE_UNLOCK_DEGRADE_BYPASS,
                HookRuleCatalog.RULE_XIAOAI_CLOUD_VOICEASSIST_LAUNCH,
                HookRuleCatalog.RULE_XIAOAI_NET_SYSTEM_CLOUD_CONTROL
            ),
            defaultOffRules
        )

        val hotwordSpec = HookRuleCatalog.findById(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR)
        assertNotNull(hotwordSpec)
        assertTrue(hotwordSpec!!.defaultEnabled)
        assertTrue(hotwordSpec.critical)
        assertEquals(RuleCategory.CONTEXT_STRIP, hotwordSpec.category)
    }

    @Test
    fun v2RuleOverridesSanitizationPackageHelpersAndMigrationWork() {
        val base = PrivacyConfig()
        assertEquals(2, base.configVersion)
        assertTrue(base.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR))
        assertFalse(base.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertTrue(base.areAllPackageContextRulesEnabled(ImeTarget.SOGOU.packageName))
        assertTrue(base.arePackageClipboardGuardsEnabled(ImeTarget.SOGOU.packageName))
        assertFalse(base.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CLIP_STRICT_BLOCK_LISTENER))

        // Package-scoped quick panel context & clipboard toggles affect only target package
        val sogouContextOff = base.withPackageContextRules(ImeTarget.SOGOU.packageName, false)
        assertFalse(sogouContextOff.areAllPackageContextRulesEnabled(ImeTarget.SOGOU.packageName))
        assertTrue(sogouContextOff.areAllPackageContextRulesEnabled(ImeTarget.BAIDU.packageName))
        assertTrue(sogouContextOff.areAllPackageContextRulesEnabled(ImeTarget.XIAOAI.packageName))
        assertFalse(sogouContextOff.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT))

        val baiduClipOff = base.withPackageClipboardGuards(ImeTarget.BAIDU.packageName, false)
        assertFalse(baiduClipOff.arePackageClipboardGuardsEnabled(ImeTarget.BAIDU.packageName))
        assertTrue(baiduClipOff.arePackageClipboardGuardsEnabled(ImeTarget.SOGOU.packageName))
        val baiduClipBackOn = baiduClipOff.withPackageClipboardGuards(ImeTarget.BAIDU.packageName, true)
        assertTrue(baiduClipBackOn.arePackageClipboardGuardsEnabled(ImeTarget.BAIDU.packageName))
        assertFalse(baiduClipBackOn.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_CLIP_STRICT_BLOCK_READERS))

        // Unknown rule IDs are stripped on sanitized(), JSON, and SharedPreferences round-trips
        val dirtyOverrides = base.copy(
            ruleOverrides = mapOf(
                HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS to true,
                "unknown.rule.id" to false
            ),
            updatedAtMs = 12345L
        ).sanitized()
        assertEquals(mapOf(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS to true), dirtyOverrides.ruleOverrides)
        assertEquals(dirtyOverrides, PrivacyConfig.fromJson(dirtyOverrides.toJson()))

        val prefs = InMemorySharedPreferences()
        dirtyOverrides.toSharedPreferences(prefs, commitSync = true)
        assertEquals(dirtyOverrides, PrivacyConfig.fromSharedPreferences(prefs))
        prefs.edit().remove(ConfigContract.KEY_CONFIG_JSON).commit()
        assertEquals(dirtyOverrides, PrivacyConfig.fromSharedPreferences(prefs))

        // v1.x JSON migration without config_version or rule_overrides
        val legacyJson = """
            {
              "blockTelemetry": false,
              "blockPasswordStats": false,
              "blockHttpDns": true,
              "blockXiaoAiHotwordTracker": false,
              "showKeyboardPill": false,
              "updatedAtMs": 9001
            }
        """.trimIndent()
        val migrated = PrivacyConfig.fromJson(legacyJson)
        assertEquals(2, migrated.configVersion)
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_GATE_ALIVE_HEARTBEAT))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION))
        assertTrue(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertTrue(migrated.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS))
        assertFalse(migrated.isRuleEnabled(HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL))
        // v2.0 default remains true even when v1.x blockXiaoAiHotwordTracker was false
        assertTrue(migrated.isRuleEnabled(HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR))

        // ConfigBridge.isPointEnabled & isRuleEnabled delegate cleanly
        ConfigBridge.detach()
        assertTrue(ConfigBridge.isPointEnabled(ImeTarget.XIAOAI.packageName, HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR))
        assertFalse(ConfigBridge.isPointEnabled(ImeTarget.SOGOU.packageName, HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS))
        assertTrue(ConfigBridge.isRuleEnabled(HookRuleCatalog.RULE_BAIDU_GATE_CONSENT_AND_1839))
    }

    @Test
    fun symbolResolverDescriptorsAndCacheKeyRoundTrip() {
        assertEquals("v20160:sogou.cloud", SymbolResolver.buildCacheKey(20160L, "sogou.cloud"))

        val loader = checkNotNull(SampleTarget::class.java.classLoader)
        val method = SampleTarget::class.java.getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType)
        val methodDesc = SymbolResolver.executableToDescriptor(method)
        assertEquals(method, SymbolResolver.descriptorToExecutable(methodDesc, loader))

        val ctor = SampleTarget::class.java.getDeclaredConstructor(String::class.java)
        val ctorDesc = SymbolResolver.executableToDescriptor(ctor)
        assertEquals(ctor, SymbolResolver.descriptorToExecutable(ctorDesc, loader))
    }

    @Test
    fun subHookAggregationCoversAllFailureDegradedFallbackAndActiveStates() {
        val ruleId = HookRuleCatalog.RULE_SOGOU_GATE_ALIVE_HEARTBEAT

        // All failed -> FAILED
        val allFailed = HookRegistry.aggregateSubHookOutcomes(
            ruleId = ruleId,
            outcomes = listOf(
                HookRegistry.SubHookOutcome("h1", HookMatchStatus.FAILED, detail = "missing A"),
                HookRegistry.SubHookOutcome("h2", HookMatchStatus.FAILED, detail = "missing B")
            )
        )
        assertEquals(HookMatchStatus.FAILED, allFailed.status)
        assertTrue(allFailed.detail.contains("h1"))
        assertTrue(allFailed.detail.contains("h2"))

        // Mixed success + failure -> DEGRADED
        val partialFail = HookRegistry.aggregateSubHookOutcomes(
            ruleId = ruleId,
            outcomes = listOf(
                HookRegistry.SubHookOutcome("h1", HookMatchStatus.ACTIVE, "A#m", "FAST_PATH"),
                HookRegistry.SubHookOutcome("h2", HookMatchStatus.FAILED, detail = "missing B")
            ),
            blockedCount = 5L
        )
        assertEquals(HookMatchStatus.DEGRADED, partialFail.status)
        assertEquals(5L, partialFail.blockedCount)
        assertTrue(partialFail.detail.contains("h2"))

        // No failures + any FALLBACK -> FALLBACK
        val fallbackAgg = HookRegistry.aggregateSubHookOutcomes(
            ruleId = ruleId,
            outcomes = listOf(
                HookRegistry.SubHookOutcome("h1", HookMatchStatus.ACTIVE, "A#m", "FAST_PATH"),
                HookRegistry.SubHookOutcome("h2", HookMatchStatus.FALLBACK, "B#n", "STRUCTURAL_FALLBACK")
            )
        )
        assertEquals(HookMatchStatus.FALLBACK, fallbackAgg.status)

        // All ACTIVE -> ACTIVE
        val activeAgg = HookRegistry.aggregateSubHookOutcomes(
            ruleId = ruleId,
            outcomes = listOf(
                HookRegistry.SubHookOutcome("h1", HookMatchStatus.ACTIVE, "A#m", "FAST_PATH"),
                HookRegistry.SubHookOutcome("h2", HookMatchStatus.ACTIVE, "B#n", "DEXKIT")
            )
        )
        assertEquals(HookMatchStatus.ACTIVE, activeAgg.status)
        assertEquals("FAST_PATH,DEXKIT", activeAgg.source)
    }

    @Test
    fun resolveModeAndCacheResetRoundTripInConfigAndDiagnostics() {
        val defaults = PrivacyConfig()
        for (target in ImeTarget.entries) {
            assertEquals(SymbolResolveMode.AUTO, defaults.resolveModeFor(target))
            assertEquals(0L, defaults.cacheResetAtMsFor(target))
        }

        val configured = defaults
            .withResolveMode(ImeTarget.SOGOU, SymbolResolveMode.DYNAMIC)
            .withResolveMode(ImeTarget.BAIDU, SymbolResolveMode.DYNAMIC)
            .withCacheResetAtMs(ImeTarget.SOGOU, -99L)
            .withCacheResetAtMs(ImeTarget.BAIDU, 1732500000123L)
            .withCacheResetAtMs(ImeTarget.XIAOAI, 1732500000999L)
            .sanitized()
        assertEquals(0L, configured.sogouCacheResetAtMs)
        assertEquals(1732500000123L, configured.baiduCacheResetAtMs)
        assertEquals(1732500000999L, configured.xiaoaiCacheResetAtMs)

        val jsonDecoded = PrivacyConfig.fromJson(configured.toJson())
        assertEquals(configured, jsonDecoded)

        val prefs = InMemorySharedPreferences()
        configured.toSharedPreferences(prefs, commitSync = true)
        assertEquals(configured, PrivacyConfig.fromSharedPreferences(prefs))

        // Verify legacy typed-key fallback preserves resolveMode and cacheResetAtMs
        prefs.edit().remove(ConfigContract.KEY_CONFIG_JSON).commit()
        assertEquals(configured, PrivacyConfig.fromSharedPreferences(prefs))

        val diag = ImeRuntimeDiagnostics(
            target = ImeTarget.BAIDU,
            resolveMode = "DYNAMIC",
            cachedSymbolCount = 24
        )
        val diagDecoded = ImeRuntimeDiagnostics.fromJson(diag.toJson())
        assertNotNull(diagDecoded)
        assertEquals("DYNAMIC", diagDecoded!!.resolveMode)
        assertEquals(24, diagDecoded.cachedSymbolCount)
        assertEquals(0, diag.copy(cachedSymbolCount = -5).let { ImeRuntimeDiagnostics.fromJson(it.toJson()) }!!.cachedSymbolCount)
    }

    @Test
    fun symbolResolverDynamicModePendingResetAndClearCacheWork() {
        val prefs = InMemorySharedPreferences()
        val loader = checkNotNull(SampleTarget::class.java.classLoader)
        val appInfo = ApplicationInfo().apply { sourceDir = "" }
        val staleKey = SymbolResolver.buildCacheKey(20160L, "sogou.stale")
        prefs.edit().putString(staleKey, "Lcom/example/Missing;").commit()
        assertEquals(1, SymbolResolver.countCachedSymbols(prefs))

        // Pending reset timestamp clears stale cache entries on SymbolResolver construction
        val autoResolver = SymbolResolver(
            context = null,
            classLoader = loader,
            appInfo = appInfo,
            versionCode = 20160L,
            moduleNativeLibDir = null,
            resolveMode = SymbolResolveMode.AUTO,
            requiredCacheResetAtMs = 5000L,
            prefsOverride = prefs
        )
        autoResolver.use {
            assertEquals(0, SymbolResolver.countCachedSymbols(prefs))
            assertEquals(5000L, prefs.getLong(SymbolResolver.KEY_APPLIED_RESET_AT_MS, 0L))

            val fastMethod = it.resolveMethod(
                cacheKey = "sample.hello",
                fastPath = { cl -> cl.loadClass(SampleTarget::class.java.name).getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType) },
                structuralFallback = { cl -> cl.loadClass(SampleTarget::class.java.name).getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType) }
            )
            assertEquals(ResolveSource.FAST_PATH, fastMethod.source)
        }

        // Dynamic mode uses Cache -> (DexKit) -> StructuralFallback -> FastPath, populates cache on fallback
        val dynamicResolver = SymbolResolver(
            context = null,
            classLoader = loader,
            appInfo = appInfo,
            versionCode = 20160L,
            moduleNativeLibDir = null,
            resolveMode = SymbolResolveMode.DYNAMIC,
            requiredCacheResetAtMs = 5000L,
            prefsOverride = prefs
        )
        dynamicResolver.use { resolver ->
            val clsRes1 = resolver.resolveClass(
                cacheKey = "sample.cls",
                fastPathCandidates = listOf(SampleTarget::class.java.name),
                structuralFallback = { SampleTarget::class.java }
            )
            assertEquals(ResolveSource.STRUCTURAL_FALLBACK, clsRes1.source)
            assertEquals(1, SymbolResolver.countCachedSymbols(prefs))

            val methodRes1 = resolver.resolveMethod(
                cacheKey = "sample.hello",
                fastPath = { SampleTarget::class.java.getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType) },
                structuralFallback = { SampleTarget::class.java.getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType) }
            )
            assertEquals(ResolveSource.STRUCTURAL_FALLBACK, methodRes1.source)
            assertEquals(2, SymbolResolver.countCachedSymbols(prefs))

            // Second lookup hits CACHE ahead of FastPath/Fallback
            val clsRes2 = resolver.resolveClass(
                cacheKey = "sample.cls",
                fastPathCandidates = listOf(SampleTarget::class.java.name),
                structuralFallback = { SampleTarget::class.java }
            )
            val methodRes2 = resolver.resolveMethod(
                cacheKey = "sample.hello",
                fastPath = { SampleTarget::class.java.getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType) },
                structuralFallback = { SampleTarget::class.java.getDeclaredMethod("hello", String::class.java, Int::class.javaPrimitiveType) }
            )
            assertEquals(ResolveSource.CACHE, clsRes2.source)
            assertEquals(ResolveSource.CACHE, methodRes2.source)

            // No-op when same resetAtMs already applied
            assertFalse(SymbolResolver.applyPendingResetIfNeeded(prefs, 5000L))
            assertEquals(2, SymbolResolver.countCachedSymbols(prefs))

            // Clear cache removes v* entries and advances applied reset timestamp
            val removed = SymbolResolver.clearCacheNow(prefs, 9000L)
            assertEquals(2, removed)
            assertEquals(0, SymbolResolver.countCachedSymbols(prefs))
            assertEquals(9000L, prefs.getLong(SymbolResolver.KEY_APPLIED_RESET_AT_MS, 0L))
        }
    }

    private class SampleTarget(val label: String) {
        @Suppress("unused")
        fun hello(name: String, count: Int): String = "$label:$name:$count"
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
