package com.scluzlep.miimeprivacy.xposed.overlay

import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeTarget
import kotlin.math.roundToInt

/**
 * Pure JVM-testable state machine, Option A tap semantics, status formatting,
 * and window-relative position/clamping math for the emoji keyboard micro-pill.
 */
object QuickPillStateMachine {

    const val LONG_PRESS_TIMEOUT_MS = 380L
    const val TOUCH_SLOP_DP = 10f

    enum class PillVisualState(val emoji: String) {
        CLOUD_ENABLED("🟢"),
        LOCAL_ONLY("🔒"),
        UNMANAGED_OR_DEGRADED("🔴")
    }

    sealed interface TapOutcome {
        data class UpdateConfig(
            val updatedConfig: PrivacyConfig,
            val newState: PillVisualState
        ) : TapOutcome

        data object OpenPanel : TapOutcome
    }

    data class PillPositionPx(
        val translationX: Float,
        val translationY: Float
    )

    fun shouldShowPill(config: PrivacyConfig): Boolean = config.showKeyboardPill

    fun resolveVisualState(
        target: ImeTarget,
        config: PrivacyConfig,
        hasCriticalFailure: Boolean
    ): PillVisualState {
        if (!config.isImeManaged(target) || hasCriticalFailure) {
            return PillVisualState.UNMANAGED_OR_DEGRADED
        }
        return if (config.effectiveCloudEnabled(target)) {
            PillVisualState.CLOUD_ENABLED
        } else {
            PillVisualState.LOCAL_ONLY
        }
    }

    /**
     * Option A single-tap behavior:
     * - On `🔴` (unmanaged or critical hook failure): MUST NOT mutate managed/cloud config;
     *   opens the Floating Quick Panel so the user can inspect diagnostics or re-enable safely.
     * - On `🟢` / `🔒`: toggles effective cloud input for [target].
     *   - When [PrivacyConfig.cloudOverrideFor] is [CloudOverride.FOLLOW_GLOBAL], flips global
     *     `allowCloudInput` so the pill stays consistent with the Quick Settings Tile and UI.
     *   - When [PrivacyConfig.cloudOverrideFor] is [CloudOverride.FORCE_ON] or [CloudOverride.FORCE_OFF],
     *     flips only that per-IME override (`FORCE_ON <-> FORCE_OFF`) without affecting other IMEs.
     */
    fun onSingleTap(
        target: ImeTarget,
        config: PrivacyConfig,
        hasCriticalFailure: Boolean
    ): TapOutcome {
        val currentState = resolveVisualState(target, config, hasCriticalFailure)
        if (currentState == PillVisualState.UNMANAGED_OR_DEGRADED) {
            return TapOutcome.OpenPanel
        }
        val nextEnabled = currentState != PillVisualState.CLOUD_ENABLED
        val nextConfig = setEffectiveCloudEnabledForTarget(target, config, nextEnabled)
        val nextState = resolveVisualState(target, nextConfig, hasCriticalFailure)
        return TapOutcome.UpdateConfig(
            updatedConfig = nextConfig,
            newState = nextState
        )
    }

    fun onLongPress(): TapOutcome = TapOutcome.OpenPanel

    fun setEffectiveCloudEnabledForTarget(
        target: ImeTarget,
        config: PrivacyConfig,
        enabled: Boolean
    ): PrivacyConfig {
        return when (config.cloudOverrideFor(target)) {
            CloudOverride.FOLLOW_GLOBAL -> config.copy(allowCloudInput = enabled)
            CloudOverride.FORCE_ON,
            CloudOverride.FORCE_OFF -> {
                val nextOverride = if (enabled) CloudOverride.FORCE_ON else CloudOverride.FORCE_OFF
                config.withCloudOverride(target, nextOverride)
            }
        }
    }

    fun panelStatusText(
        target: ImeTarget,
        config: PrivacyConfig,
        hasCriticalFailure: Boolean
    ): String {
        if (!config.isImeManaged(target)) {
            return "🔴 未受管 · 暂停保护"
        }
        if (hasCriticalFailure) {
            return "🔴 关键规则异常，请点开主页检查"
        }
        return if (config.effectiveCloudEnabled(target)) {
            "🟢 受管 · 云输入(已脱敏)"
        } else {
            "🔒 受管 · 纯本地词库"
        }
    }

    fun clampAlpha(alpha: Float): Float {
        if (!alpha.isFinite()) return PrivacyConfig.DEFAULT_PILL_ALPHA
        return alpha.coerceIn(PrivacyConfig.MIN_PILL_ALPHA, PrivacyConfig.MAX_PILL_ALPHA)
    }

    fun computePillTranslation(
        offset: PillOffset,
        visibleTopInsetsPx: Int,
        density: Float,
        containerWidthPx: Int,
        containerHeightPx: Int,
        pillWidthPx: Int,
        pillHeightPx: Int
    ): PillPositionPx {
        val safeDensity = if (density.isFinite() && density > 0f) density else 1f
        val clampedOffsetX = offset.offsetXDp.coerceIn(
            PrivacyConfig.MIN_PILL_OFFSET_DP,
            PrivacyConfig.MAX_PILL_OFFSET_DP
        )
        val clampedOffsetY = offset.offsetYDp.coerceIn(
            PrivacyConfig.MIN_PILL_OFFSET_DP,
            PrivacyConfig.MAX_PILL_OFFSET_DP
        )

        val rawX = clampedOffsetX * safeDensity
        val rawY = visibleTopInsetsPx + (clampedOffsetY * safeDensity)

        val finalX = if (containerWidthPx > 0 && pillWidthPx > 0) {
            val maxX = (containerWidthPx - pillWidthPx).coerceAtLeast(0).toFloat()
            rawX.coerceIn(0f, maxX)
        } else {
            rawX.coerceAtLeast(0f)
        }

        val finalY = if (containerHeightPx > 0 && pillHeightPx > 0) {
            val maxY = (containerHeightPx - pillHeightPx).coerceAtLeast(0).toFloat()
            rawY.coerceIn(0f, maxY)
        } else {
            rawY.coerceAtLeast(0f)
        }

        return PillPositionPx(translationX = finalX, translationY = finalY)
    }

    fun translationToPillOffset(
        translationX: Float,
        translationY: Float,
        visibleTopInsetsPx: Int,
        density: Float
    ): PillOffset {
        val safeDensity = if (density.isFinite() && density > 0f) density else 1f
        val xDp = if (translationX.isFinite()) {
            (translationX / safeDensity).roundToInt()
        } else {
            PillOffset.DEFAULT_OFFSET_X_DP
        }
        val yDp = if (translationY.isFinite()) {
            ((translationY - visibleTopInsetsPx) / safeDensity).roundToInt()
        } else {
            PillOffset.DEFAULT_OFFSET_Y_DP
        }
        return PillOffset(
            offsetXDp = xDp.coerceIn(PrivacyConfig.MIN_PILL_OFFSET_DP, PrivacyConfig.MAX_PILL_OFFSET_DP),
            offsetYDp = yDp.coerceIn(PrivacyConfig.MIN_PILL_OFFSET_DP, PrivacyConfig.MAX_PILL_OFFSET_DP)
        )
    }

    fun isWithinTouchSlop(dxPx: Float, dyPx: Float, touchSlopPx: Float): Boolean {
        return (dxPx * dxPx + dyPx * dyPx) <= (touchSlopPx * touchSlopPx)
    }
}
