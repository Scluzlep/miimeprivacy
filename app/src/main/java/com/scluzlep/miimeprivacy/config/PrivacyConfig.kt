package com.scluzlep.miimeprivacy.config

import android.content.SharedPreferences
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.model.RuleCategory
import org.json.JSONObject

enum class CloudOverride {
    FOLLOW_GLOBAL,
    FORCE_ON,
    FORCE_OFF
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class MonetPaletteStyle {
    TonalSpot,
    Neutral,
    Vibrant,
    Expressive,
    Rainbow,
    FruitSalad,
    Monochrome,
    Fidelity,
    Content
}

enum class SymbolResolveMode {
    AUTO,
    DYNAMIC
}

data class PillOffset(
    val offsetXDp: Int = DEFAULT_OFFSET_X_DP,
    val offsetYDp: Int = DEFAULT_OFFSET_Y_DP
) {
    companion object {
        const val DEFAULT_OFFSET_X_DP = 12
        const val DEFAULT_OFFSET_Y_DP = 2
    }
}

/**
 * Immutable privacy, cloud-input, quick-pill, per-rule override, and appearance configuration
 * shared across the module UI, Quick Settings tile, RemotePreferences, and IME-local overrides.
 */
data class PrivacyConfig(
    val configVersion: Int = CURRENT_CONFIG_VERSION,
    val sogouManaged: Boolean = true,
    val baiduManaged: Boolean = true,
    val xiaoaiManaged: Boolean = true,
    val allowCloudInput: Boolean = true,
    val sogouCloudOverride: CloudOverride = CloudOverride.FOLLOW_GLOBAL,
    val baiduCloudOverride: CloudOverride = CloudOverride.FOLLOW_GLOBAL,
    val xiaoaiCloudOverride: CloudOverride = CloudOverride.FOLLOW_GLOBAL,
    val blockTelemetry: Boolean = true,
    val blockPasswordStats: Boolean = true,
    val blockRemoteLogSalvage: Boolean = true,
    val sanitizeCloudPayload: Boolean = true,
    val stripCursorContext: Boolean = true,
    val maskHostPackage: Boolean = true,
    val stripTouchPositions: Boolean = true,
    val clipboardPrivacy: Boolean = true,
    val blockContactsAndAppScan: Boolean = true,
    val blockUnsignedDynamicDex: Boolean = true,
    val blockMutualData: Boolean = true,
    val blockHttpDns: Boolean = false,
    val blockXiaoAiHotwordTracker: Boolean = false,
    val showKeyboardPill: Boolean = true,
    val hidePillInLandscape: Boolean = true,
    val pillAlpha: Float = DEFAULT_PILL_ALPHA,
    val sogouPillOffsetXDp: Int = PillOffset.DEFAULT_OFFSET_X_DP,
    val sogouPillOffsetYDp: Int = PillOffset.DEFAULT_OFFSET_Y_DP,
    val baiduPillOffsetXDp: Int = PillOffset.DEFAULT_OFFSET_X_DP,
    val baiduPillOffsetYDp: Int = PillOffset.DEFAULT_OFFSET_Y_DP,
    val xiaoaiPillOffsetXDp: Int = PillOffset.DEFAULT_OFFSET_X_DP,
    val xiaoaiPillOffsetYDp: Int = PillOffset.DEFAULT_OFFSET_Y_DP,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val monetEnabled: Boolean = true,
    val paletteStyle: MonetPaletteStyle = MonetPaletteStyle.TonalSpot,
    val sogouResolveMode: SymbolResolveMode = SymbolResolveMode.AUTO,
    val baiduResolveMode: SymbolResolveMode = SymbolResolveMode.AUTO,
    val xiaoaiResolveMode: SymbolResolveMode = SymbolResolveMode.AUTO,
    val sogouCacheResetAtMs: Long = 0L,
    val baiduCacheResetAtMs: Long = 0L,
    val xiaoaiCacheResetAtMs: Long = 0L,
    val ruleOverrides: Map<String, Boolean> = emptyMap(),
    val updatedAtMs: Long = 0L
) {
    fun resolveModeFor(target: ImeTarget): SymbolResolveMode = when (target) {
        ImeTarget.SOGOU -> sogouResolveMode
        ImeTarget.BAIDU -> baiduResolveMode
        ImeTarget.XIAOAI -> xiaoaiResolveMode
    }

    fun withResolveMode(target: ImeTarget, mode: SymbolResolveMode): PrivacyConfig = when (target) {
        ImeTarget.SOGOU -> copy(sogouResolveMode = mode)
        ImeTarget.BAIDU -> copy(baiduResolveMode = mode)
        ImeTarget.XIAOAI -> copy(xiaoaiResolveMode = mode)
    }

    fun cacheResetAtMsFor(target: ImeTarget): Long = when (target) {
        ImeTarget.SOGOU -> sogouCacheResetAtMs
        ImeTarget.BAIDU -> baiduCacheResetAtMs
        ImeTarget.XIAOAI -> xiaoaiCacheResetAtMs
    }

    fun withCacheResetAtMs(target: ImeTarget, resetAtMs: Long): PrivacyConfig {
        val clean = resetAtMs.coerceAtLeast(0L)
        return when (target) {
            ImeTarget.SOGOU -> copy(sogouCacheResetAtMs = clean)
            ImeTarget.BAIDU -> copy(baiduCacheResetAtMs = clean)
            ImeTarget.XIAOAI -> copy(xiaoaiCacheResetAtMs = clean)
        }
    }

    fun isImeManaged(target: ImeTarget): Boolean = when (target) {
        ImeTarget.SOGOU -> sogouManaged
        ImeTarget.BAIDU -> baiduManaged
        ImeTarget.XIAOAI -> xiaoaiManaged
    }

    fun withImeManaged(target: ImeTarget, managed: Boolean): PrivacyConfig = when (target) {
        ImeTarget.SOGOU -> copy(sogouManaged = managed)
        ImeTarget.BAIDU -> copy(baiduManaged = managed)
        ImeTarget.XIAOAI -> copy(xiaoaiManaged = managed)
    }

    fun cloudOverrideFor(target: ImeTarget): CloudOverride = when (target) {
        ImeTarget.SOGOU -> sogouCloudOverride
        ImeTarget.BAIDU -> baiduCloudOverride
        ImeTarget.XIAOAI -> xiaoaiCloudOverride
    }

    fun withCloudOverride(target: ImeTarget, override: CloudOverride): PrivacyConfig = when (target) {
        ImeTarget.SOGOU -> copy(sogouCloudOverride = override)
        ImeTarget.BAIDU -> copy(baiduCloudOverride = override)
        ImeTarget.XIAOAI -> copy(xiaoaiCloudOverride = override)
    }

    fun effectiveCloudEnabled(target: ImeTarget): Boolean {
        return when (cloudOverrideFor(target)) {
            CloudOverride.FOLLOW_GLOBAL -> allowCloudInput
            CloudOverride.FORCE_ON -> true
            CloudOverride.FORCE_OFF -> false
        }
    }

    fun pillOffsetFor(target: ImeTarget): PillOffset = when (target) {
        ImeTarget.SOGOU -> PillOffset(sogouPillOffsetXDp, sogouPillOffsetYDp)
        ImeTarget.BAIDU -> PillOffset(baiduPillOffsetXDp, baiduPillOffsetYDp)
        ImeTarget.XIAOAI -> PillOffset(xiaoaiPillOffsetXDp, xiaoaiPillOffsetYDp)
    }

    fun withPillOffset(target: ImeTarget, offsetXDp: Int, offsetYDp: Int): PrivacyConfig {
        val clampedX = offsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP)
        val clampedY = offsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP)
        return when (target) {
            ImeTarget.SOGOU -> copy(sogouPillOffsetXDp = clampedX, sogouPillOffsetYDp = clampedY)
            ImeTarget.BAIDU -> copy(baiduPillOffsetXDp = clampedX, baiduPillOffsetYDp = clampedY)
            ImeTarget.XIAOAI -> copy(xiaoaiPillOffsetXDp = clampedX, xiaoaiPillOffsetYDp = clampedY)
        }
    }

    fun withPillOffset(target: ImeTarget, offset: PillOffset): PrivacyConfig {
        return withPillOffset(target, offset.offsetXDp, offset.offsetYDp)
    }

    /**
     * Resolves the effective enablement of a canonical v2.0 rule ID, respecting target management
     * (`isImeManaged`) for non-overlay rules, `showKeyboardPill` for overlay rules, and `ruleOverrides`.
     */
    fun isRuleEnabled(ruleId: String): Boolean {
        val spec = HookRuleCatalog.findById(ruleId) ?: return ruleOverrides[ruleId] ?: true
        if (spec.isOverlayPill) {
            return ruleOverrides[ruleId] ?: showKeyboardPill
        }
        if (!isImeManaged(spec.target)) return false
        return isRuleToggleChecked(ruleId)
    }

    /**
     * Resolves the raw switch state of a rule independent of whether its IME target is currently
     * managed, used by UI and quick-panel toggles so unmanaging does not wipe per-rule settings.
     */
    fun isRuleToggleChecked(ruleId: String): Boolean {
        val spec = HookRuleCatalog.findById(ruleId) ?: return ruleOverrides[ruleId] ?: true
        if (spec.isOverlayPill) {
            return ruleOverrides[ruleId] ?: showKeyboardPill
        }
        return ruleOverrides[ruleId] ?: spec.defaultEnabled
    }

    fun withRuleOverride(ruleId: String, enabled: Boolean): PrivacyConfig {
        val spec = HookRuleCatalog.findById(ruleId) ?: return this
        val nextOverrides = ruleOverrides.toMutableMap()
        if (enabled == spec.defaultEnabled && !spec.isOverlayPill) {
            nextOverrides.remove(ruleId)
        } else {
            nextOverrides[ruleId] = enabled
        }
        return copy(ruleOverrides = nextOverrides)
    }

    fun withRulesForPackage(
        targetPackage: String,
        predicate: (com.scluzlep.miimeprivacy.model.HookRuleSpec) -> Boolean,
        enabled: Boolean
    ): PrivacyConfig {
        val matching = HookRuleCatalog.rulesForPackage(targetPackage).filter(predicate)
        if (matching.isEmpty()) return this
        val nextOverrides = ruleOverrides.toMutableMap()
        for (spec in matching) {
            if (enabled == spec.defaultEnabled && !spec.isOverlayPill) {
                nextOverrides.remove(spec.ruleId)
            } else {
                nextOverrides[spec.ruleId] = enabled
            }
        }
        return copy(ruleOverrides = nextOverrides)
    }

    fun areAllPackageContextRulesEnabled(targetPackage: String): Boolean {
        val contextRules = HookRuleCatalog.rulesForPackage(targetPackage)
            .filter { it.category == RuleCategory.CONTEXT_STRIP }
        if (contextRules.isEmpty()) return false
        return contextRules.all { isRuleToggleChecked(it.ruleId) }
    }

    fun withPackageContextRules(targetPackage: String, enabled: Boolean): PrivacyConfig {
        return withRulesForPackage(
            targetPackage = targetPackage,
            predicate = { it.category == RuleCategory.CONTEXT_STRIP },
            enabled = enabled
        )
    }

    fun arePackageClipboardGuardsEnabled(targetPackage: String): Boolean {
        val guardRules = HookRuleCatalog.rulesForPackage(targetPackage)
            .filter { it.category == RuleCategory.CLIPBOARD && it.defaultEnabled }
        if (guardRules.isEmpty()) return false
        return guardRules.all { isRuleToggleChecked(it.ruleId) }
    }

    fun withPackageClipboardGuards(targetPackage: String, enabled: Boolean): PrivacyConfig {
        val predicate: (com.scluzlep.miimeprivacy.model.HookRuleSpec) -> Boolean = if (enabled) {
            { it.category == RuleCategory.CLIPBOARD && it.defaultEnabled }
        } else {
            { it.category == RuleCategory.CLIPBOARD }
        }
        return withRulesForPackage(
            targetPackage = targetPackage,
            predicate = predicate,
            enabled = enabled
        )
    }

    fun sanitized(): PrivacyConfig {
        val cleanAlpha = if (pillAlpha.isFinite()) {
            pillAlpha.coerceIn(MIN_PILL_ALPHA, MAX_PILL_ALPHA)
        } else {
            DEFAULT_PILL_ALPHA
        }
        val cleanOverrides = if (ruleOverrides.isEmpty()) {
            emptyMap()
        } else {
            ruleOverrides.filterKeys { HookRuleCatalog.findById(it) != null }.toMap()
        }
        return copy(
            configVersion = configVersion.coerceAtLeast(CURRENT_CONFIG_VERSION),
            pillAlpha = cleanAlpha,
            sogouPillOffsetXDp = sogouPillOffsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            sogouPillOffsetYDp = sogouPillOffsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            baiduPillOffsetXDp = baiduPillOffsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            baiduPillOffsetYDp = baiduPillOffsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            xiaoaiPillOffsetXDp = xiaoaiPillOffsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            xiaoaiPillOffsetYDp = xiaoaiPillOffsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            sogouCacheResetAtMs = sogouCacheResetAtMs.coerceAtLeast(0L),
            baiduCacheResetAtMs = baiduCacheResetAtMs.coerceAtLeast(0L),
            xiaoaiCacheResetAtMs = xiaoaiCacheResetAtMs.coerceAtLeast(0L),
            ruleOverrides = cleanOverrides,
            updatedAtMs = updatedAtMs.coerceAtLeast(0L)
        )
    }

    fun mergeNewer(other: PrivacyConfig): PrivacyConfig = mergeNewer(this, other)

    fun toJsonObject(): JSONObject {
        val clean = sanitized()
        return JSONObject().apply {
            put(KEY_CONFIG_VERSION, clean.configVersion)
            put(KEY_SOGOU_MANAGED, clean.sogouManaged)
            put(KEY_BAIDU_MANAGED, clean.baiduManaged)
            put(KEY_XIAOAI_MANAGED, clean.xiaoaiManaged)
            put(KEY_ALLOW_CLOUD_INPUT, clean.allowCloudInput)
            put(KEY_SOGOU_CLOUD_OVERRIDE, clean.sogouCloudOverride.name)
            put(KEY_BAIDU_CLOUD_OVERRIDE, clean.baiduCloudOverride.name)
            put(KEY_XIAOAI_CLOUD_OVERRIDE, clean.xiaoaiCloudOverride.name)
            put(KEY_BLOCK_TELEMETRY, clean.blockTelemetry)
            put(KEY_BLOCK_PASSWORD_STATS, clean.blockPasswordStats)
            put(KEY_BLOCK_REMOTE_LOG_SALVAGE, clean.blockRemoteLogSalvage)
            put(KEY_SANITIZE_CLOUD_PAYLOAD, clean.sanitizeCloudPayload)
            put(KEY_STRIP_CURSOR_CONTEXT, clean.stripCursorContext)
            put(KEY_MASK_HOST_PACKAGE, clean.maskHostPackage)
            put(KEY_STRIP_TOUCH_POSITIONS, clean.stripTouchPositions)
            put(KEY_CLIPBOARD_PRIVACY, clean.clipboardPrivacy)
            put(KEY_BLOCK_CONTACTS_AND_APP_SCAN, clean.blockContactsAndAppScan)
            put(KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, clean.blockUnsignedDynamicDex)
            put(KEY_BLOCK_MUTUAL_DATA, clean.blockMutualData)
            put(KEY_BLOCK_HTTP_DNS, clean.blockHttpDns)
            put(KEY_BLOCK_XIAOAI_HOTWORD_TRACKER, clean.blockXiaoAiHotwordTracker)
            put(KEY_SHOW_KEYBOARD_PILL, clean.showKeyboardPill)
            put(KEY_HIDE_PILL_IN_LANDSCAPE, clean.hidePillInLandscape)
            put(KEY_PILL_ALPHA, clean.pillAlpha.toDouble())
            put(KEY_SOGOU_PILL_OFFSET_X, clean.sogouPillOffsetXDp)
            put(KEY_SOGOU_PILL_OFFSET_Y, clean.sogouPillOffsetYDp)
            put(KEY_BAIDU_PILL_OFFSET_X, clean.baiduPillOffsetXDp)
            put(KEY_BAIDU_PILL_OFFSET_Y, clean.baiduPillOffsetYDp)
            put(KEY_XIAOAI_PILL_OFFSET_X, clean.xiaoaiPillOffsetXDp)
            put(KEY_XIAOAI_PILL_OFFSET_Y, clean.xiaoaiPillOffsetYDp)
            put(KEY_THEME_MODE, clean.themeMode.name)
            put(KEY_MONET_ENABLED, clean.monetEnabled)
            put(KEY_PALETTE_STYLE, clean.paletteStyle.name)
            put(KEY_SOGOU_RESOLVE_MODE, clean.sogouResolveMode.name)
            put(KEY_BAIDU_RESOLVE_MODE, clean.baiduResolveMode.name)
            put(KEY_XIAOAI_RESOLVE_MODE, clean.xiaoaiResolveMode.name)
            put(KEY_SOGOU_CACHE_RESET_AT_MS, clean.sogouCacheResetAtMs)
            put(KEY_BAIDU_CACHE_RESET_AT_MS, clean.baiduCacheResetAtMs)
            put(KEY_XIAOAI_CACHE_RESET_AT_MS, clean.xiaoaiCacheResetAtMs)
            put(KEY_RULE_OVERRIDES, overridesToJsonObject(clean.ruleOverrides))
            put(KEY_UPDATED_AT_MS, clean.updatedAtMs)
        }
    }

    fun toJson(): String = toJsonObject().toString()

    fun toSharedPreferences(prefs: SharedPreferences, commitSync: Boolean = false) {
        val clean = sanitized()
        val editor = prefs.edit()
        editor.putString(ConfigContract.KEY_CONFIG_JSON, clean.toJson())
        editor.putLong(ConfigContract.KEY_UPDATED_AT_MS, clean.updatedAtMs)
        editor.putInt(KEY_CONFIG_VERSION, clean.configVersion)
        editor.putBoolean(KEY_SOGOU_MANAGED, clean.sogouManaged)
        editor.putBoolean(KEY_BAIDU_MANAGED, clean.baiduManaged)
        editor.putBoolean(KEY_XIAOAI_MANAGED, clean.xiaoaiManaged)
        editor.putBoolean(KEY_ALLOW_CLOUD_INPUT, clean.allowCloudInput)
        editor.putString(KEY_SOGOU_CLOUD_OVERRIDE, clean.sogouCloudOverride.name)
        editor.putString(KEY_BAIDU_CLOUD_OVERRIDE, clean.baiduCloudOverride.name)
        editor.putString(KEY_XIAOAI_CLOUD_OVERRIDE, clean.xiaoaiCloudOverride.name)
        editor.putBoolean(KEY_BLOCK_TELEMETRY, clean.blockTelemetry)
        editor.putBoolean(KEY_BLOCK_PASSWORD_STATS, clean.blockPasswordStats)
        editor.putBoolean(KEY_BLOCK_REMOTE_LOG_SALVAGE, clean.blockRemoteLogSalvage)
        editor.putBoolean(KEY_SANITIZE_CLOUD_PAYLOAD, clean.sanitizeCloudPayload)
        editor.putBoolean(KEY_STRIP_CURSOR_CONTEXT, clean.stripCursorContext)
        editor.putBoolean(KEY_MASK_HOST_PACKAGE, clean.maskHostPackage)
        editor.putBoolean(KEY_STRIP_TOUCH_POSITIONS, clean.stripTouchPositions)
        editor.putBoolean(KEY_CLIPBOARD_PRIVACY, clean.clipboardPrivacy)
        editor.putBoolean(KEY_BLOCK_CONTACTS_AND_APP_SCAN, clean.blockContactsAndAppScan)
        editor.putBoolean(KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, clean.blockUnsignedDynamicDex)
        editor.putBoolean(KEY_BLOCK_MUTUAL_DATA, clean.blockMutualData)
        editor.putBoolean(KEY_BLOCK_HTTP_DNS, clean.blockHttpDns)
        editor.putBoolean(KEY_BLOCK_XIAOAI_HOTWORD_TRACKER, clean.blockXiaoAiHotwordTracker)
        editor.putBoolean(KEY_SHOW_KEYBOARD_PILL, clean.showKeyboardPill)
        editor.putBoolean(KEY_HIDE_PILL_IN_LANDSCAPE, clean.hidePillInLandscape)
        editor.putFloat(KEY_PILL_ALPHA, clean.pillAlpha)
        editor.putInt(KEY_SOGOU_PILL_OFFSET_X, clean.sogouPillOffsetXDp)
        editor.putInt(KEY_SOGOU_PILL_OFFSET_Y, clean.sogouPillOffsetYDp)
        editor.putInt(KEY_BAIDU_PILL_OFFSET_X, clean.baiduPillOffsetXDp)
        editor.putInt(KEY_BAIDU_PILL_OFFSET_Y, clean.baiduPillOffsetYDp)
        editor.putInt(KEY_XIAOAI_PILL_OFFSET_X, clean.xiaoaiPillOffsetXDp)
        editor.putInt(KEY_XIAOAI_PILL_OFFSET_Y, clean.xiaoaiPillOffsetYDp)
        editor.putString(KEY_THEME_MODE, clean.themeMode.name)
        editor.putBoolean(KEY_MONET_ENABLED, clean.monetEnabled)
        editor.putString(KEY_PALETTE_STYLE, clean.paletteStyle.name)
        editor.putString(KEY_SOGOU_RESOLVE_MODE, clean.sogouResolveMode.name)
        editor.putString(KEY_BAIDU_RESOLVE_MODE, clean.baiduResolveMode.name)
        editor.putString(KEY_XIAOAI_RESOLVE_MODE, clean.xiaoaiResolveMode.name)
        editor.putLong(KEY_SOGOU_CACHE_RESET_AT_MS, clean.sogouCacheResetAtMs)
        editor.putLong(KEY_BAIDU_CACHE_RESET_AT_MS, clean.baiduCacheResetAtMs)
        editor.putLong(KEY_XIAOAI_CACHE_RESET_AT_MS, clean.xiaoaiCacheResetAtMs)
        editor.putString(KEY_RULE_OVERRIDES, overridesToJsonObject(clean.ruleOverrides).toString())
        if (commitSync) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    companion object {
        const val CURRENT_CONFIG_VERSION = 2

        const val DEFAULT_PILL_ALPHA = 0.88f
        const val MIN_PILL_ALPHA = 0.10f
        const val MAX_PILL_ALPHA = 1.0f
        const val MIN_PILL_OFFSET_DP = -2000
        const val MAX_PILL_OFFSET_DP = 2000

        const val KEY_CONFIG_VERSION = "config_version"
        const val KEY_RULE_OVERRIDES = "rule_overrides"
        const val KEY_SOGOU_MANAGED = "sogouManaged"
        const val KEY_BAIDU_MANAGED = "baiduManaged"
        const val KEY_XIAOAI_MANAGED = "xiaoaiManaged"
        const val KEY_ALLOW_CLOUD_INPUT = "allowCloudInput"
        const val KEY_SOGOU_CLOUD_OVERRIDE = "sogouCloudOverride"
        const val KEY_BAIDU_CLOUD_OVERRIDE = "baiduCloudOverride"
        const val KEY_XIAOAI_CLOUD_OVERRIDE = "xiaoaiCloudOverride"
        const val KEY_BLOCK_TELEMETRY = "blockTelemetry"
        const val KEY_BLOCK_PASSWORD_STATS = "blockPasswordStats"
        const val KEY_BLOCK_REMOTE_LOG_SALVAGE = "blockRemoteLogSalvage"
        const val KEY_SANITIZE_CLOUD_PAYLOAD = "sanitizeCloudPayload"
        const val KEY_STRIP_CURSOR_CONTEXT = "stripCursorContext"
        const val KEY_MASK_HOST_PACKAGE = "maskHostPackage"
        const val KEY_STRIP_TOUCH_POSITIONS = "stripTouchPositions"
        const val KEY_CLIPBOARD_PRIVACY = "clipboardPrivacy"
        const val KEY_BLOCK_CONTACTS_AND_APP_SCAN = "blockContactsAndAppScan"
        const val KEY_BLOCK_UNSIGNED_DYNAMIC_DEX = "blockUnsignedDynamicDex"
        const val KEY_BLOCK_MUTUAL_DATA = "blockMutualData"
        const val KEY_BLOCK_HTTP_DNS = "blockHttpDns"
        const val KEY_BLOCK_XIAOAI_HOTWORD_TRACKER = "blockXiaoAiHotwordTracker"
        const val KEY_SHOW_KEYBOARD_PILL = "showKeyboardPill"
        const val KEY_HIDE_PILL_IN_LANDSCAPE = "hidePillInLandscape"
        const val KEY_PILL_ALPHA = "pillAlpha"
        const val KEY_SOGOU_PILL_OFFSET_X = "sogouPillOffsetXDp"
        const val KEY_SOGOU_PILL_OFFSET_Y = "sogouPillOffsetYDp"
        const val KEY_BAIDU_PILL_OFFSET_X = "baiduPillOffsetXDp"
        const val KEY_BAIDU_PILL_OFFSET_Y = "baiduPillOffsetYDp"
        const val KEY_XIAOAI_PILL_OFFSET_X = "xiaoaiPillOffsetXDp"
        const val KEY_XIAOAI_PILL_OFFSET_Y = "xiaoaiPillOffsetYDp"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_MONET_ENABLED = "monetEnabled"
        const val KEY_PALETTE_STYLE = "paletteStyle"
        const val KEY_SOGOU_RESOLVE_MODE = "sogouResolveMode"
        const val KEY_BAIDU_RESOLVE_MODE = "baiduResolveMode"
        const val KEY_XIAOAI_RESOLVE_MODE = "xiaoaiResolveMode"
        const val KEY_SOGOU_CACHE_RESET_AT_MS = "sogouCacheResetAtMs"
        const val KEY_BAIDU_CACHE_RESET_AT_MS = "baiduCacheResetAtMs"
        const val KEY_XIAOAI_CACHE_RESET_AT_MS = "xiaoaiCacheResetAtMs"
        const val KEY_UPDATED_AT_MS = "updatedAtMs"

        fun nextTimestamp(previousMs: Long = 0L): Long {
            return maxOf(System.currentTimeMillis(), previousMs + 1L)
        }

        fun mergeNewer(local: PrivacyConfig, remote: PrivacyConfig): PrivacyConfig {
            val cleanLocal = local.sanitized()
            val cleanRemote = remote.sanitized()
            return if (cleanRemote.updatedAtMs > cleanLocal.updatedAtMs) cleanRemote else cleanLocal
        }

        fun fromJson(json: String?): PrivacyConfig {
            if (json.isNullOrBlank()) return PrivacyConfig()
            val obj = runCatching { JSONObject(json) }.getOrNull() ?: return PrivacyConfig()
            return fromJsonObject(obj)
        }

        fun fromJsonObject(obj: JSONObject): PrivacyConfig {
            val defaults = PrivacyConfig()
            val rawVersion = obj.optInt(KEY_CONFIG_VERSION, 1)
            val blockTelemetry = obj.optBoolean(KEY_BLOCK_TELEMETRY, defaults.blockTelemetry)
            val blockPasswordStats = obj.optBoolean(KEY_BLOCK_PASSWORD_STATS, defaults.blockPasswordStats)
            val blockRemoteLogSalvage = obj.optBoolean(KEY_BLOCK_REMOTE_LOG_SALVAGE, defaults.blockRemoteLogSalvage)
            val sanitizeCloudPayload = obj.optBoolean(KEY_SANITIZE_CLOUD_PAYLOAD, defaults.sanitizeCloudPayload)
            val stripCursorContext = obj.optBoolean(KEY_STRIP_CURSOR_CONTEXT, defaults.stripCursorContext)
            val maskHostPackage = obj.optBoolean(KEY_MASK_HOST_PACKAGE, defaults.maskHostPackage)
            val stripTouchPositions = obj.optBoolean(KEY_STRIP_TOUCH_POSITIONS, defaults.stripTouchPositions)
            val clipboardPrivacy = obj.optBoolean(KEY_CLIPBOARD_PRIVACY, defaults.clipboardPrivacy)
            val blockContactsAndAppScan = obj.optBoolean(KEY_BLOCK_CONTACTS_AND_APP_SCAN, defaults.blockContactsAndAppScan)
            val blockUnsignedDynamicDex = obj.optBoolean(KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, defaults.blockUnsignedDynamicDex)
            val blockMutualData = obj.optBoolean(KEY_BLOCK_MUTUAL_DATA, defaults.blockMutualData)
            val blockHttpDns = obj.optBoolean(KEY_BLOCK_HTTP_DNS, defaults.blockHttpDns)
            val blockXiaoAiHotwordTracker = obj.optBoolean(
                KEY_BLOCK_XIAOAI_HOTWORD_TRACKER,
                defaults.blockXiaoAiHotwordTracker
            )
            val showKeyboardPill = obj.optBoolean(KEY_SHOW_KEYBOARD_PILL, defaults.showKeyboardPill)

            val hasRuleOverrides = obj.has(KEY_RULE_OVERRIDES) && !obj.isNull(KEY_RULE_OVERRIDES)
            val resolvedOverrides = when {
                hasRuleOverrides -> {
                    val rawObj = obj.optJSONObject(KEY_RULE_OVERRIDES)
                    if (rawObj != null) {
                        parseOverridesJsonObject(rawObj)
                    } else {
                        parseOverridesJsonString(obj.optString(KEY_RULE_OVERRIDES, ""))
                    }
                }
                rawVersion < CURRENT_CONFIG_VERSION -> migrateLegacyBooleansToOverrides(
                    blockTelemetry = blockTelemetry,
                    blockPasswordStats = blockPasswordStats,
                    blockRemoteLogSalvage = blockRemoteLogSalvage,
                    sanitizeCloudPayload = sanitizeCloudPayload,
                    stripCursorContext = stripCursorContext,
                    maskHostPackage = maskHostPackage,
                    stripTouchPositions = stripTouchPositions,
                    clipboardPrivacy = clipboardPrivacy,
                    blockContactsAndAppScan = blockContactsAndAppScan,
                    blockUnsignedDynamicDex = blockUnsignedDynamicDex,
                    blockMutualData = blockMutualData,
                    blockHttpDns = blockHttpDns,
                    blockXiaoAiHotwordTracker = blockXiaoAiHotwordTracker,
                    showKeyboardPill = showKeyboardPill
                )
                else -> emptyMap()
            }

            return PrivacyConfig(
                configVersion = CURRENT_CONFIG_VERSION,
                sogouManaged = obj.optBoolean(KEY_SOGOU_MANAGED, defaults.sogouManaged),
                baiduManaged = obj.optBoolean(KEY_BAIDU_MANAGED, defaults.baiduManaged),
                xiaoaiManaged = obj.optBoolean(KEY_XIAOAI_MANAGED, defaults.xiaoaiManaged),
                allowCloudInput = obj.optBoolean(KEY_ALLOW_CLOUD_INPUT, defaults.allowCloudInput),
                sogouCloudOverride = enumValueOrDefault(
                    obj.optString(KEY_SOGOU_CLOUD_OVERRIDE, defaults.sogouCloudOverride.name),
                    defaults.sogouCloudOverride
                ),
                baiduCloudOverride = enumValueOrDefault(
                    obj.optString(KEY_BAIDU_CLOUD_OVERRIDE, defaults.baiduCloudOverride.name),
                    defaults.baiduCloudOverride
                ),
                xiaoaiCloudOverride = enumValueOrDefault(
                    obj.optString(KEY_XIAOAI_CLOUD_OVERRIDE, defaults.xiaoaiCloudOverride.name),
                    defaults.xiaoaiCloudOverride
                ),
                blockTelemetry = blockTelemetry,
                blockPasswordStats = blockPasswordStats,
                blockRemoteLogSalvage = blockRemoteLogSalvage,
                sanitizeCloudPayload = sanitizeCloudPayload,
                stripCursorContext = stripCursorContext,
                maskHostPackage = maskHostPackage,
                stripTouchPositions = stripTouchPositions,
                clipboardPrivacy = clipboardPrivacy,
                blockContactsAndAppScan = blockContactsAndAppScan,
                blockUnsignedDynamicDex = blockUnsignedDynamicDex,
                blockMutualData = blockMutualData,
                blockHttpDns = blockHttpDns,
                blockXiaoAiHotwordTracker = blockXiaoAiHotwordTracker,
                showKeyboardPill = showKeyboardPill,
                hidePillInLandscape = obj.optBoolean(KEY_HIDE_PILL_IN_LANDSCAPE, defaults.hidePillInLandscape),
                pillAlpha = obj.optDouble(KEY_PILL_ALPHA, defaults.pillAlpha.toDouble()).toFloat(),
                sogouPillOffsetXDp = obj.optInt(KEY_SOGOU_PILL_OFFSET_X, defaults.sogouPillOffsetXDp),
                sogouPillOffsetYDp = obj.optInt(KEY_SOGOU_PILL_OFFSET_Y, defaults.sogouPillOffsetYDp),
                baiduPillOffsetXDp = obj.optInt(KEY_BAIDU_PILL_OFFSET_X, defaults.baiduPillOffsetXDp),
                baiduPillOffsetYDp = obj.optInt(KEY_BAIDU_PILL_OFFSET_Y, defaults.baiduPillOffsetYDp),
                xiaoaiPillOffsetXDp = obj.optInt(KEY_XIAOAI_PILL_OFFSET_X, defaults.xiaoaiPillOffsetXDp),
                xiaoaiPillOffsetYDp = obj.optInt(KEY_XIAOAI_PILL_OFFSET_Y, defaults.xiaoaiPillOffsetYDp),
                themeMode = enumValueOrDefault(
                    obj.optString(KEY_THEME_MODE, defaults.themeMode.name),
                    defaults.themeMode
                ),
                monetEnabled = obj.optBoolean(KEY_MONET_ENABLED, defaults.monetEnabled),
                paletteStyle = enumValueOrDefault(
                    obj.optString(KEY_PALETTE_STYLE, defaults.paletteStyle.name),
                    defaults.paletteStyle
                ),
                sogouResolveMode = enumValueOrDefault(
                    obj.optString(KEY_SOGOU_RESOLVE_MODE, defaults.sogouResolveMode.name),
                    defaults.sogouResolveMode
                ),
                baiduResolveMode = enumValueOrDefault(
                    obj.optString(KEY_BAIDU_RESOLVE_MODE, defaults.baiduResolveMode.name),
                    defaults.baiduResolveMode
                ),
                xiaoaiResolveMode = enumValueOrDefault(
                    obj.optString(KEY_XIAOAI_RESOLVE_MODE, defaults.xiaoaiResolveMode.name),
                    defaults.xiaoaiResolveMode
                ),
                sogouCacheResetAtMs = obj.optLong(KEY_SOGOU_CACHE_RESET_AT_MS, defaults.sogouCacheResetAtMs),
                baiduCacheResetAtMs = obj.optLong(KEY_BAIDU_CACHE_RESET_AT_MS, defaults.baiduCacheResetAtMs),
                xiaoaiCacheResetAtMs = obj.optLong(KEY_XIAOAI_CACHE_RESET_AT_MS, defaults.xiaoaiCacheResetAtMs),
                ruleOverrides = resolvedOverrides,
                updatedAtMs = obj.optLong(KEY_UPDATED_AT_MS, defaults.updatedAtMs)
            ).sanitized()
        }

        fun fromSharedPreferences(prefs: SharedPreferences): PrivacyConfig {
            val rawJson = runCatching { prefs.getString(ConfigContract.KEY_CONFIG_JSON, null) }.getOrNull()
            if (!rawJson.isNullOrBlank()) {
                val parsed = fromJson(rawJson)
                val topStamp = runCatching { prefs.getLong(ConfigContract.KEY_UPDATED_AT_MS, 0L) }.getOrDefault(0L)
                return parsed.copy(updatedAtMs = maxOf(parsed.updatedAtMs, topStamp)).sanitized()
            }
            val defaults = PrivacyConfig()
            val rawVersion = runCatching { prefs.getInt(KEY_CONFIG_VERSION, 1) }.getOrDefault(1)
            val blockTelemetry = prefs.getBoolean(KEY_BLOCK_TELEMETRY, defaults.blockTelemetry)
            val blockPasswordStats = prefs.getBoolean(KEY_BLOCK_PASSWORD_STATS, defaults.blockPasswordStats)
            val blockRemoteLogSalvage = prefs.getBoolean(KEY_BLOCK_REMOTE_LOG_SALVAGE, defaults.blockRemoteLogSalvage)
            val sanitizeCloudPayload = prefs.getBoolean(KEY_SANITIZE_CLOUD_PAYLOAD, defaults.sanitizeCloudPayload)
            val stripCursorContext = prefs.getBoolean(KEY_STRIP_CURSOR_CONTEXT, defaults.stripCursorContext)
            val maskHostPackage = prefs.getBoolean(KEY_MASK_HOST_PACKAGE, defaults.maskHostPackage)
            val stripTouchPositions = prefs.getBoolean(KEY_STRIP_TOUCH_POSITIONS, defaults.stripTouchPositions)
            val clipboardPrivacy = prefs.getBoolean(KEY_CLIPBOARD_PRIVACY, defaults.clipboardPrivacy)
            val blockContactsAndAppScan = prefs.getBoolean(KEY_BLOCK_CONTACTS_AND_APP_SCAN, defaults.blockContactsAndAppScan)
            val blockUnsignedDynamicDex = prefs.getBoolean(KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, defaults.blockUnsignedDynamicDex)
            val blockMutualData = prefs.getBoolean(KEY_BLOCK_MUTUAL_DATA, defaults.blockMutualData)
            val blockHttpDns = prefs.getBoolean(KEY_BLOCK_HTTP_DNS, defaults.blockHttpDns)
            val blockXiaoAiHotwordTracker = prefs.getBoolean(
                KEY_BLOCK_XIAOAI_HOTWORD_TRACKER,
                defaults.blockXiaoAiHotwordTracker
            )
            val showKeyboardPill = prefs.getBoolean(KEY_SHOW_KEYBOARD_PILL, defaults.showKeyboardPill)

            val rawOverrides = runCatching { prefs.getString(KEY_RULE_OVERRIDES, null) }.getOrNull()
            val resolvedOverrides = when {
                rawOverrides != null -> parseOverridesJsonString(rawOverrides)
                rawVersion < CURRENT_CONFIG_VERSION -> migrateLegacyBooleansToOverrides(
                    blockTelemetry = blockTelemetry,
                    blockPasswordStats = blockPasswordStats,
                    blockRemoteLogSalvage = blockRemoteLogSalvage,
                    sanitizeCloudPayload = sanitizeCloudPayload,
                    stripCursorContext = stripCursorContext,
                    maskHostPackage = maskHostPackage,
                    stripTouchPositions = stripTouchPositions,
                    clipboardPrivacy = clipboardPrivacy,
                    blockContactsAndAppScan = blockContactsAndAppScan,
                    blockUnsignedDynamicDex = blockUnsignedDynamicDex,
                    blockMutualData = blockMutualData,
                    blockHttpDns = blockHttpDns,
                    blockXiaoAiHotwordTracker = blockXiaoAiHotwordTracker,
                    showKeyboardPill = showKeyboardPill
                )
                else -> emptyMap()
            }

            return PrivacyConfig(
                configVersion = CURRENT_CONFIG_VERSION,
                sogouManaged = prefs.getBoolean(KEY_SOGOU_MANAGED, defaults.sogouManaged),
                baiduManaged = prefs.getBoolean(KEY_BAIDU_MANAGED, defaults.baiduManaged),
                xiaoaiManaged = prefs.getBoolean(KEY_XIAOAI_MANAGED, defaults.xiaoaiManaged),
                allowCloudInput = prefs.getBoolean(KEY_ALLOW_CLOUD_INPUT, defaults.allowCloudInput),
                sogouCloudOverride = enumValueOrDefault(
                    prefs.getString(KEY_SOGOU_CLOUD_OVERRIDE, defaults.sogouCloudOverride.name),
                    defaults.sogouCloudOverride
                ),
                baiduCloudOverride = enumValueOrDefault(
                    prefs.getString(KEY_BAIDU_CLOUD_OVERRIDE, defaults.baiduCloudOverride.name),
                    defaults.baiduCloudOverride
                ),
                xiaoaiCloudOverride = enumValueOrDefault(
                    prefs.getString(KEY_XIAOAI_CLOUD_OVERRIDE, defaults.xiaoaiCloudOverride.name),
                    defaults.xiaoaiCloudOverride
                ),
                blockTelemetry = blockTelemetry,
                blockPasswordStats = blockPasswordStats,
                blockRemoteLogSalvage = blockRemoteLogSalvage,
                sanitizeCloudPayload = sanitizeCloudPayload,
                stripCursorContext = stripCursorContext,
                maskHostPackage = maskHostPackage,
                stripTouchPositions = stripTouchPositions,
                clipboardPrivacy = clipboardPrivacy,
                blockContactsAndAppScan = blockContactsAndAppScan,
                blockUnsignedDynamicDex = blockUnsignedDynamicDex,
                blockMutualData = blockMutualData,
                blockHttpDns = blockHttpDns,
                blockXiaoAiHotwordTracker = blockXiaoAiHotwordTracker,
                showKeyboardPill = showKeyboardPill,
                hidePillInLandscape = prefs.getBoolean(KEY_HIDE_PILL_IN_LANDSCAPE, defaults.hidePillInLandscape),
                pillAlpha = prefs.getFloat(KEY_PILL_ALPHA, defaults.pillAlpha),
                sogouPillOffsetXDp = prefs.getInt(KEY_SOGOU_PILL_OFFSET_X, defaults.sogouPillOffsetXDp),
                sogouPillOffsetYDp = prefs.getInt(KEY_SOGOU_PILL_OFFSET_Y, defaults.sogouPillOffsetYDp),
                baiduPillOffsetXDp = prefs.getInt(KEY_BAIDU_PILL_OFFSET_X, defaults.baiduPillOffsetXDp),
                baiduPillOffsetYDp = prefs.getInt(KEY_BAIDU_PILL_OFFSET_Y, defaults.baiduPillOffsetYDp),
                xiaoaiPillOffsetXDp = prefs.getInt(KEY_XIAOAI_PILL_OFFSET_X, defaults.xiaoaiPillOffsetXDp),
                xiaoaiPillOffsetYDp = prefs.getInt(KEY_XIAOAI_PILL_OFFSET_Y, defaults.xiaoaiPillOffsetYDp),
                themeMode = enumValueOrDefault(
                    prefs.getString(KEY_THEME_MODE, defaults.themeMode.name),
                    defaults.themeMode
                ),
                monetEnabled = prefs.getBoolean(KEY_MONET_ENABLED, defaults.monetEnabled),
                paletteStyle = enumValueOrDefault(
                    prefs.getString(KEY_PALETTE_STYLE, defaults.paletteStyle.name),
                    defaults.paletteStyle
                ),
                sogouResolveMode = enumValueOrDefault(
                    prefs.getString(KEY_SOGOU_RESOLVE_MODE, defaults.sogouResolveMode.name),
                    defaults.sogouResolveMode
                ),
                baiduResolveMode = enumValueOrDefault(
                    prefs.getString(KEY_BAIDU_RESOLVE_MODE, defaults.baiduResolveMode.name),
                    defaults.baiduResolveMode
                ),
                xiaoaiResolveMode = enumValueOrDefault(
                    prefs.getString(KEY_XIAOAI_RESOLVE_MODE, defaults.xiaoaiResolveMode.name),
                    defaults.xiaoaiResolveMode
                ),
                sogouCacheResetAtMs = prefs.getLong(KEY_SOGOU_CACHE_RESET_AT_MS, defaults.sogouCacheResetAtMs),
                baiduCacheResetAtMs = prefs.getLong(KEY_BAIDU_CACHE_RESET_AT_MS, defaults.baiduCacheResetAtMs),
                xiaoaiCacheResetAtMs = prefs.getLong(KEY_XIAOAI_CACHE_RESET_AT_MS, defaults.xiaoaiCacheResetAtMs),
                ruleOverrides = resolvedOverrides,
                updatedAtMs = prefs.getLong(ConfigContract.KEY_UPDATED_AT_MS, defaults.updatedAtMs)
            ).sanitized()
        }

        internal fun migrateLegacyBooleansToOverrides(
            blockTelemetry: Boolean = true,
            blockPasswordStats: Boolean = true,
            blockRemoteLogSalvage: Boolean = true,
            sanitizeCloudPayload: Boolean = true,
            stripCursorContext: Boolean = true,
            maskHostPackage: Boolean = true,
            stripTouchPositions: Boolean = true,
            clipboardPrivacy: Boolean = true,
            blockContactsAndAppScan: Boolean = true,
            blockUnsignedDynamicDex: Boolean = true,
            blockMutualData: Boolean = true,
            blockHttpDns: Boolean = false,
            blockXiaoAiHotwordTracker: Boolean = false,
            showKeyboardPill: Boolean = true
        ): Map<String, Boolean> {
            val overrides = linkedMapOf<String, Boolean>()

            if (!blockTelemetry) {
                listOf(
                    HookRuleCatalog.RULE_SOGOU_GATE_ALIVE_HEARTBEAT,
                    HookRuleCatalog.RULE_SOGOU_GATE_PRECONSENT_GUARD,
                    HookRuleCatalog.RULE_SOGOU_ID_OAID_TGPA,
                    HookRuleCatalog.RULE_SOGOU_ID_HARDWARE_AND_SENSOR,
                    HookRuleCatalog.RULE_SOGOU_ID_ANDROID_ID_AND_UUID,
                    HookRuleCatalog.RULE_SOGOU_TELEMETRY_PINGBACK,
                    HookRuleCatalog.RULE_SOGOU_TELEMETRY_WHITEDOG_LOGDIAG,
                    HookRuleCatalog.RULE_SOGOU_TELEMETRY_BEACON_QIMEI,
                    HookRuleCatalog.RULE_SOGOU_TELEMETRY_THIRDPARTY_ADS_QM,
                    HookRuleCatalog.RULE_SOGOU_TELEMETRY_CRASH_APM,
                    HookRuleCatalog.RULE_BAIDU_GATE_CONSENT_AND_1839,
                    HookRuleCatalog.RULE_BAIDU_GATE_NOTICENTER_LOCK,
                    HookRuleCatalog.RULE_BAIDU_ID_OAID_AND_HELIOS,
                    HookRuleCatalog.RULE_BAIDU_ID_CUID_ANDROIDID_SOFIRE,
                    HookRuleCatalog.RULE_BAIDU_TELEMETRY_REALTIME_STATS,
                    HookRuleCatalog.RULE_BAIDU_TELEMETRY_PERIODIC_UBC_MOBSTAT,
                    HookRuleCatalog.RULE_BAIDU_TELEMETRY_VOICE_AND_ADS,
                    HookRuleCatalog.RULE_BAIDU_TELEMETRY_CRASH_DUMP,
                    HookRuleCatalog.RULE_XIAOAI_GATE_CTA_FAIL_OPEN,
                    HookRuleCatalog.RULE_XIAOAI_ID_ANTIHOOK_AND_SEC_REPORT,
                    HookRuleCatalog.RULE_XIAOAI_ID_OAID_INSID_PHONENUM,
                    HookRuleCatalog.RULE_XIAOAI_TELEMETRY_ONETRACK,
                    HookRuleCatalog.RULE_XIAOAI_TELEMETRY_TAIYI_AND_JANK,
                    HookRuleCatalog.RULE_XIAOAI_TELEMETRY_BUGREPORT_AND_XCRASH
                ).forEach { overrides[it] = false }
            }

            if (!blockPasswordStats) {
                overrides[HookRuleCatalog.RULE_XIAOAI_TELEMETRY_PASSWORD_AND_SESSION] = false
            }

            if (!blockRemoteLogSalvage) {
                overrides[HookRuleCatalog.RULE_BAIDU_NET_SALVAGE_LOGS] = false
            }

            if (!sanitizeCloudPayload) {
                listOf(
                    HookRuleCatalog.RULE_SOGOU_CLOUD_GATEWAY_AND_PIGGYBACK,
                    HookRuleCatalog.RULE_SOGOU_CLOUD_PAYLOAD_DEVICE_DIALOGUE,
                    HookRuleCatalog.RULE_SOGOU_CLOUD_WANGZAI_VPA,
                    HookRuleCatalog.RULE_SOGOU_CLOUD_VOICE_PRIVACY,
                    HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT,
                    HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE,
                    HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS,
                    HookRuleCatalog.RULE_BAIDU_CLOUD_STREAM_GATE,
                    HookRuleCatalog.RULE_BAIDU_CLOUD_PAYLOAD_SANITIZE,
                    HookRuleCatalog.RULE_BAIDU_CONTEXT_CURSOR_AND_AI,
                    HookRuleCatalog.RULE_BAIDU_CONTEXT_HOST_PACKAGE,
                    HookRuleCatalog.RULE_XIAOAI_CLOUD_MICLAW_LLM,
                    HookRuleCatalog.RULE_XIAOAI_CLOUD_SCREEN_CAPTURE,
                    HookRuleCatalog.RULE_XIAOAI_CLOUD_SPEECH_ASR,
                    HookRuleCatalog.RULE_XIAOAI_CLOUD_TRANSLATE_AND_TAIYI,
                    HookRuleCatalog.RULE_XIAOAI_CLOUD_DICTPACK_SYNC,
                    HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG,
                    HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP
                ).forEach { overrides[it] = false }
            }

            if (!stripCursorContext) {
                overrides[HookRuleCatalog.RULE_SOGOU_CONTEXT_CURSOR_TEXT] = false
                overrides[HookRuleCatalog.RULE_BAIDU_CONTEXT_CURSOR_AND_AI] = false
                overrides[HookRuleCatalog.RULE_XIAOAI_CONTEXT_CURSOR_PROMPT_STRIP] = false
            }

            if (!maskHostPackage) {
                overrides[HookRuleCatalog.RULE_SOGOU_CONTEXT_HOST_PACKAGE] = false
                overrides[HookRuleCatalog.RULE_BAIDU_CONTEXT_HOST_PACKAGE] = false
                overrides[HookRuleCatalog.RULE_XIAOAI_CONTEXT_TOP_ACTIVITY_AND_PKG] = false
            }

            if (!stripTouchPositions) {
                overrides[HookRuleCatalog.RULE_SOGOU_CONTEXT_TOUCH_POSITIONS] = false
            }

            if (!blockMutualData) {
                overrides[HookRuleCatalog.RULE_SOGOU_CONTEXT_MUTUAL_BIGDATA] = false
            }

            if (!clipboardPrivacy) {
                listOf(
                    HookRuleCatalog.RULE_SOGOU_CLIP_SNIFFER_AND_KOULING,
                    HookRuleCatalog.RULE_SOGOU_CLIP_AUTO_TRANSLATE_AND_JS,
                    HookRuleCatalog.RULE_SOGOU_CLIP_DB_HISTORY_SAVE,
                    HookRuleCatalog.RULE_BAIDU_CLIP_TOKEN_UPLOAD_AND_JS,
                    HookRuleCatalog.RULE_BAIDU_CLIP_BACKGROUND_AND_DB,
                    HookRuleCatalog.RULE_XIAOAI_CLIP_BACKGROUND_GUARD
                ).forEach { overrides[it] = false }
            }

            if (!blockContactsAndAppScan) {
                listOf(
                    HookRuleCatalog.RULE_SOGOU_SCAN_DEVICEINFO_HARVESTER,
                    HookRuleCatalog.RULE_SOGOU_SCAN_APP_LIST,
                    HookRuleCatalog.RULE_SOGOU_SCAN_CONTACTS_UPLOAD_AND_DICT,
                    HookRuleCatalog.RULE_SOGOU_SCAN_LOCATION_WIFI,
                    HookRuleCatalog.RULE_BAIDU_SCAN_CONTACTS,
                    HookRuleCatalog.RULE_BAIDU_SCAN_APP_LIST,
                    HookRuleCatalog.RULE_BAIDU_SCAN_LOCATION_WIFI_CELL
                ).forEach { overrides[it] = false }
            }

            if (!blockUnsignedDynamicDex) {
                overrides[HookRuleCatalog.RULE_SOGOU_NET_DYNAMIC_DEX_AND_JS] = false
                overrides[HookRuleCatalog.RULE_BAIDU_NET_DYNAMIC_DEX_AND_JS] = false
                overrides[HookRuleCatalog.RULE_BAIDU_NET_TLS_HARDENING] = false
            }

            if (blockHttpDns) {
                overrides[HookRuleCatalog.RULE_SOGOU_NET_HTTPDNS] = true
                overrides[HookRuleCatalog.RULE_BAIDU_NET_HTTPDNS] = true
            }

            // Keep v2.0 default xiaoai.context.hotword_aicr=true intact; only write if legacy explicitly enabled.
            if (blockXiaoAiHotwordTracker) {
                overrides[HookRuleCatalog.RULE_XIAOAI_CONTEXT_HOTWORD_AICR] = true
            }

            if (!showKeyboardPill) {
                overrides[HookRuleCatalog.RULE_SOGOU_OVERLAY_PILL] = false
                overrides[HookRuleCatalog.RULE_BAIDU_OVERLAY_PILL] = false
                overrides[HookRuleCatalog.RULE_XIAOAI_OVERLAY_PILL] = false
            }

            return overrides
        }

        private fun overridesToJsonObject(overrides: Map<String, Boolean>): JSONObject {
            return JSONObject().apply {
                for ((ruleId, enabled) in overrides) {
                    put(ruleId, enabled)
                }
            }
        }

        private fun parseOverridesJsonString(raw: String?): Map<String, Boolean> {
            if (raw.isNullOrBlank()) return emptyMap()
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
            return parseOverridesJsonObject(obj)
        }

        private fun parseOverridesJsonObject(obj: JSONObject): Map<String, Boolean> {
            val result = linkedMapOf<String, Boolean>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (HookRuleCatalog.findById(key) == null) continue
                val value = obj.opt(key)
                if (value is Boolean) {
                    result[key] = value
                }
            }
            return result
        }

        private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, defaultValue: T): T {
            if (raw.isNullOrBlank()) return defaultValue
            return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: defaultValue
        }
    }
}
