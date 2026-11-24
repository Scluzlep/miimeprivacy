package com.scluzlep.miimeprivacy.config

import android.content.SharedPreferences
import com.scluzlep.miimeprivacy.model.ImeTarget
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
 * Immutable privacy, cloud-input, quick-pill, and appearance configuration shared across the module
 * UI, Quick Settings tile, RemotePreferences, and IME-local SharedPreferences overrides.
 */
data class PrivacyConfig(
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
    val updatedAtMs: Long = 0L
) {
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

    fun sanitized(): PrivacyConfig {
        val cleanAlpha = if (pillAlpha.isFinite()) {
            pillAlpha.coerceIn(MIN_PILL_ALPHA, MAX_PILL_ALPHA)
        } else {
            DEFAULT_PILL_ALPHA
        }
        return copy(
            pillAlpha = cleanAlpha,
            sogouPillOffsetXDp = sogouPillOffsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            sogouPillOffsetYDp = sogouPillOffsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            baiduPillOffsetXDp = baiduPillOffsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            baiduPillOffsetYDp = baiduPillOffsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            xiaoaiPillOffsetXDp = xiaoaiPillOffsetXDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            xiaoaiPillOffsetYDp = xiaoaiPillOffsetYDp.coerceIn(MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP),
            updatedAtMs = updatedAtMs.coerceAtLeast(0L)
        )
    }

    fun mergeNewer(other: PrivacyConfig): PrivacyConfig = mergeNewer(this, other)

    fun toJsonObject(): JSONObject {
        val clean = sanitized()
        return JSONObject().apply {
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
            put(KEY_UPDATED_AT_MS, clean.updatedAtMs)
        }
    }

    fun toJson(): String = toJsonObject().toString()

    fun toSharedPreferences(prefs: SharedPreferences, commitSync: Boolean = false) {
        val clean = sanitized()
        val editor = prefs.edit()
        editor.putString(ConfigContract.KEY_CONFIG_JSON, clean.toJson())
        editor.putLong(ConfigContract.KEY_UPDATED_AT_MS, clean.updatedAtMs)
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
        if (commitSync) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    companion object {
        const val DEFAULT_PILL_ALPHA = 0.88f
        const val MIN_PILL_ALPHA = 0.10f
        const val MAX_PILL_ALPHA = 1.0f
        const val MIN_PILL_OFFSET_DP = -2000
        const val MAX_PILL_OFFSET_DP = 2000

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
            return PrivacyConfig(
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
                blockTelemetry = obj.optBoolean(KEY_BLOCK_TELEMETRY, defaults.blockTelemetry),
                blockPasswordStats = obj.optBoolean(KEY_BLOCK_PASSWORD_STATS, defaults.blockPasswordStats),
                blockRemoteLogSalvage = obj.optBoolean(KEY_BLOCK_REMOTE_LOG_SALVAGE, defaults.blockRemoteLogSalvage),
                sanitizeCloudPayload = obj.optBoolean(KEY_SANITIZE_CLOUD_PAYLOAD, defaults.sanitizeCloudPayload),
                stripCursorContext = obj.optBoolean(KEY_STRIP_CURSOR_CONTEXT, defaults.stripCursorContext),
                maskHostPackage = obj.optBoolean(KEY_MASK_HOST_PACKAGE, defaults.maskHostPackage),
                stripTouchPositions = obj.optBoolean(KEY_STRIP_TOUCH_POSITIONS, defaults.stripTouchPositions),
                clipboardPrivacy = obj.optBoolean(KEY_CLIPBOARD_PRIVACY, defaults.clipboardPrivacy),
                blockContactsAndAppScan = obj.optBoolean(KEY_BLOCK_CONTACTS_AND_APP_SCAN, defaults.blockContactsAndAppScan),
                blockUnsignedDynamicDex = obj.optBoolean(KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, defaults.blockUnsignedDynamicDex),
                blockMutualData = obj.optBoolean(KEY_BLOCK_MUTUAL_DATA, defaults.blockMutualData),
                blockHttpDns = obj.optBoolean(KEY_BLOCK_HTTP_DNS, defaults.blockHttpDns),
                blockXiaoAiHotwordTracker = obj.optBoolean(KEY_BLOCK_XIAOAI_HOTWORD_TRACKER, defaults.blockXiaoAiHotwordTracker),
                showKeyboardPill = obj.optBoolean(KEY_SHOW_KEYBOARD_PILL, defaults.showKeyboardPill),
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
            return PrivacyConfig(
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
                blockTelemetry = prefs.getBoolean(KEY_BLOCK_TELEMETRY, defaults.blockTelemetry),
                blockPasswordStats = prefs.getBoolean(KEY_BLOCK_PASSWORD_STATS, defaults.blockPasswordStats),
                blockRemoteLogSalvage = prefs.getBoolean(KEY_BLOCK_REMOTE_LOG_SALVAGE, defaults.blockRemoteLogSalvage),
                sanitizeCloudPayload = prefs.getBoolean(KEY_SANITIZE_CLOUD_PAYLOAD, defaults.sanitizeCloudPayload),
                stripCursorContext = prefs.getBoolean(KEY_STRIP_CURSOR_CONTEXT, defaults.stripCursorContext),
                maskHostPackage = prefs.getBoolean(KEY_MASK_HOST_PACKAGE, defaults.maskHostPackage),
                stripTouchPositions = prefs.getBoolean(KEY_STRIP_TOUCH_POSITIONS, defaults.stripTouchPositions),
                clipboardPrivacy = prefs.getBoolean(KEY_CLIPBOARD_PRIVACY, defaults.clipboardPrivacy),
                blockContactsAndAppScan = prefs.getBoolean(KEY_BLOCK_CONTACTS_AND_APP_SCAN, defaults.blockContactsAndAppScan),
                blockUnsignedDynamicDex = prefs.getBoolean(KEY_BLOCK_UNSIGNED_DYNAMIC_DEX, defaults.blockUnsignedDynamicDex),
                blockMutualData = prefs.getBoolean(KEY_BLOCK_MUTUAL_DATA, defaults.blockMutualData),
                blockHttpDns = prefs.getBoolean(KEY_BLOCK_HTTP_DNS, defaults.blockHttpDns),
                blockXiaoAiHotwordTracker = prefs.getBoolean(KEY_BLOCK_XIAOAI_HOTWORD_TRACKER, defaults.blockXiaoAiHotwordTracker),
                showKeyboardPill = prefs.getBoolean(KEY_SHOW_KEYBOARD_PILL, defaults.showKeyboardPill),
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
                updatedAtMs = prefs.getLong(ConfigContract.KEY_UPDATED_AT_MS, defaults.updatedAtMs)
            ).sanitized()
        }

        private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, defaultValue: T): T {
            if (raw.isNullOrBlank()) return defaultValue
            return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: defaultValue
        }
    }
}
