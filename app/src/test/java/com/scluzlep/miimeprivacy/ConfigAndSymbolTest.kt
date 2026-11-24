package com.scluzlep.miimeprivacy

import android.content.SharedPreferences
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.HookRuleReport
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.HookRegistry
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
        val dirty = PrivacyConfig(
            pillAlpha = Float.NaN,
            sogouPillOffsetXDp = 999,
            sogouPillOffsetYDp = -999,
            updatedAtMs = -42L
        ).sanitized()

        assertEquals(PrivacyConfig.DEFAULT_PILL_ALPHA, dirty.pillAlpha, 0.0001f)
        assertEquals(PrivacyConfig.MAX_PILL_OFFSET_DP, dirty.sogouPillOffsetXDp)
        assertEquals(PrivacyConfig.MIN_PILL_OFFSET_DP, dirty.sogouPillOffsetYDp)
        assertEquals(0L, dirty.updatedAtMs)

        val lowAlpha = PrivacyConfig(pillAlpha = 0.01f).sanitized()
        assertEquals(PrivacyConfig.MIN_PILL_ALPHA, lowAlpha.pillAlpha, 0.0001f)

        val updated = PrivacyConfig().withPillOffset(ImeTarget.BAIDU, 500, -400)
        assertEquals(
            PillOffset(PrivacyConfig.MAX_PILL_OFFSET_DP, PrivacyConfig.MIN_PILL_OFFSET_DP),
            updated.pillOffsetFor(ImeTarget.BAIDU)
        )
    }

    @Test
    fun jsonRoundTripAndMalformedFallbackWork() {
        val original = PrivacyConfig(
            sogouManaged = false,
            allowCloudInput = false,
            baiduCloudOverride = CloudOverride.FORCE_ON,
            blockHttpDns = true,
            blockXiaoAiHotwordTracker = true,
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
        assertEquals(PrivacyConfig(), PrivacyConfig.fromJson("{not-json"))
        assertEquals(PrivacyConfig(), PrivacyConfig.fromJson(""))
    }

    @Test
    fun sharedPreferencesRoundTripAndMergeNewerWork() {
        val prefs = InMemorySharedPreferences()
        val cfg = PrivacyConfig(
            allowCloudInput = false,
            blockHttpDns = true,
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
                    ruleId = HookRuleCatalog.RULE_XIAOAI_ONETRACK,
                    status = HookMatchStatus.FAILED,
                    blockedCount = 0L,
                    detail = "method missing"
                ),
                HookRuleReport(
                    ruleId = HookRuleCatalog.RULE_XIAOAI_CLIPBOARD,
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
                    ruleId = HookRuleCatalog.RULE_XIAOAI_CLIPBOARD,
                    status = HookMatchStatus.FAILED
                )
            )
        )
        assertFalse(nonCriticalFail.hasCriticalFailure)
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
        val ruleId = HookRuleCatalog.RULE_SOGOU_PRIVACY_GATE

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
