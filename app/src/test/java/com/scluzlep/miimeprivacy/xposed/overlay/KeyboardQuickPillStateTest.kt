package com.scluzlep.miimeprivacy.xposed.overlay

import android.inputmethodservice.InputMethodService
import android.view.inputmethod.EditorInfo
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardQuickPillStateTest {

    @Test
    fun visualStateCoversManagedCloudLocalUnmanagedAndCriticalFailure() {
        for (target in ImeTarget.entries) {
            val cloudOn = PrivacyConfig(allowCloudInput = true)
            assertEquals(
                QuickPillStateMachine.PillVisualState.CLOUD_ENABLED,
                QuickPillStateMachine.resolveVisualState(target, cloudOn, hasCriticalFailure = false)
            )
            assertEquals("🟢", QuickPillStateMachine.PillVisualState.CLOUD_ENABLED.emoji)

            val cloudOff = PrivacyConfig(allowCloudInput = false)
            assertEquals(
                QuickPillStateMachine.PillVisualState.LOCAL_ONLY,
                QuickPillStateMachine.resolveVisualState(target, cloudOff, hasCriticalFailure = false)
            )
            assertEquals("🔒", QuickPillStateMachine.PillVisualState.LOCAL_ONLY.emoji)

            val unmanaged = cloudOn.withImeManaged(target, false)
            assertEquals(
                QuickPillStateMachine.PillVisualState.UNMANAGED_OR_DEGRADED,
                QuickPillStateMachine.resolveVisualState(target, unmanaged, hasCriticalFailure = false)
            )
            assertEquals("🔴", QuickPillStateMachine.PillVisualState.UNMANAGED_OR_DEGRADED.emoji)

            // Critical hook failure degrades pill to 🔴 even when managed and cloud is enabled
            assertEquals(
                QuickPillStateMachine.PillVisualState.UNMANAGED_OR_DEGRADED,
                QuickPillStateMachine.resolveVisualState(target, cloudOn, hasCriticalFailure = true)
            )
            assertEquals(
                QuickPillStateMachine.PillVisualState.UNMANAGED_OR_DEGRADED,
                QuickPillStateMachine.resolveVisualState(target, cloudOff, hasCriticalFailure = true)
            )
        }
    }

    @Test
    fun optionASingleTapWithFollowGlobalFlipsGlobalCloudInput() {
        val initial = PrivacyConfig(
            allowCloudInput = true,
            sogouCloudOverride = CloudOverride.FOLLOW_GLOBAL,
            baiduCloudOverride = CloudOverride.FOLLOW_GLOBAL
        )

        // 🟢 -> 🔒
        val toLocal = QuickPillStateMachine.onSingleTap(
            target = ImeTarget.SOGOU,
            config = initial,
            hasCriticalFailure = false
        )
        assertTrue(toLocal is QuickPillStateMachine.TapOutcome.UpdateConfig)
        val localOutcome = toLocal as QuickPillStateMachine.TapOutcome.UpdateConfig
        assertEquals(QuickPillStateMachine.PillVisualState.LOCAL_ONLY, localOutcome.newState)
        assertFalse(localOutcome.updatedConfig.allowCloudInput)
        assertEquals(CloudOverride.FOLLOW_GLOBAL, localOutcome.updatedConfig.cloudOverrideFor(ImeTarget.SOGOU))
        assertFalse(localOutcome.updatedConfig.effectiveCloudEnabled(ImeTarget.SOGOU))
        // Matches QS Tile semantics: other FOLLOW_GLOBAL IMEs reflect the flipped global flag
        assertFalse(localOutcome.updatedConfig.effectiveCloudEnabled(ImeTarget.BAIDU))

        // 🔒 -> 🟢
        val backToCloud = QuickPillStateMachine.onSingleTap(
            target = ImeTarget.SOGOU,
            config = localOutcome.updatedConfig,
            hasCriticalFailure = false
        )
        assertTrue(backToCloud is QuickPillStateMachine.TapOutcome.UpdateConfig)
        val cloudOutcome = backToCloud as QuickPillStateMachine.TapOutcome.UpdateConfig
        assertEquals(QuickPillStateMachine.PillVisualState.CLOUD_ENABLED, cloudOutcome.newState)
        assertTrue(cloudOutcome.updatedConfig.allowCloudInput)
        assertTrue(cloudOutcome.updatedConfig.effectiveCloudEnabled(ImeTarget.SOGOU))
    }

    @Test
    fun optionASingleTapWithPerImeOverrideFlipsOnlyTargetOverride() {
        val initial = PrivacyConfig(
            allowCloudInput = false,
            baiduCloudOverride = CloudOverride.FORCE_ON,
            xiaoaiCloudOverride = CloudOverride.FORCE_OFF
        )

        // FORCE_ON (🟢) -> FORCE_OFF (🔒) without changing global allowCloudInput or other IMEs
        val firstTap = QuickPillStateMachine.onSingleTap(
            target = ImeTarget.BAIDU,
            config = initial,
            hasCriticalFailure = false
        ) as QuickPillStateMachine.TapOutcome.UpdateConfig
        assertEquals(QuickPillStateMachine.PillVisualState.LOCAL_ONLY, firstTap.newState)
        assertEquals(CloudOverride.FORCE_OFF, firstTap.updatedConfig.cloudOverrideFor(ImeTarget.BAIDU))
        assertFalse(firstTap.updatedConfig.allowCloudInput)
        assertEquals(CloudOverride.FORCE_OFF, firstTap.updatedConfig.cloudOverrideFor(ImeTarget.XIAOAI))

        // FORCE_OFF (🔒) -> FORCE_ON (🟢) even while global allowCloudInput remains false
        val secondTap = QuickPillStateMachine.onSingleTap(
            target = ImeTarget.BAIDU,
            config = firstTap.updatedConfig,
            hasCriticalFailure = false
        ) as QuickPillStateMachine.TapOutcome.UpdateConfig
        assertEquals(QuickPillStateMachine.PillVisualState.CLOUD_ENABLED, secondTap.newState)
        assertEquals(CloudOverride.FORCE_ON, secondTap.updatedConfig.cloudOverrideFor(ImeTarget.BAIDU))
        assertFalse(secondTap.updatedConfig.allowCloudInput)
    }

    @Test
    fun optionASingleTapOnRedOpensPanelWithoutMutatingConfig() {
        val unmanagedCfg = PrivacyConfig(
            sogouManaged = false,
            allowCloudInput = true,
            sogouCloudOverride = CloudOverride.FOLLOW_GLOBAL
        )
        val unmanagedSnapshot = unmanagedCfg.copy()
        val unmanagedOutcome = QuickPillStateMachine.onSingleTap(
            target = ImeTarget.SOGOU,
            config = unmanagedCfg,
            hasCriticalFailure = false
        )
        assertEquals(QuickPillStateMachine.TapOutcome.OpenPanel, unmanagedOutcome)
        assertEquals(unmanagedSnapshot, unmanagedCfg)

        val degradedCfg = PrivacyConfig(
            xiaoaiManaged = true,
            allowCloudInput = true,
            xiaoaiCloudOverride = CloudOverride.FORCE_ON
        )
        val degradedSnapshot = degradedCfg.copy()
        val degradedOutcome = QuickPillStateMachine.onSingleTap(
            target = ImeTarget.XIAOAI,
            config = degradedCfg,
            hasCriticalFailure = true
        )
        assertEquals(QuickPillStateMachine.TapOutcome.OpenPanel, degradedOutcome)
        assertEquals(degradedSnapshot, degradedCfg)
    }

    @Test
    fun longPressAndStatusFormattingAndVisibilityBehaveAsExpected() {
        assertEquals(QuickPillStateMachine.TapOutcome.OpenPanel, QuickPillStateMachine.onLongPress())

        val base = PrivacyConfig(allowCloudInput = true, showKeyboardPill = true)
        assertTrue(QuickPillStateMachine.shouldShowPill(base))
        assertFalse(QuickPillStateMachine.shouldShowPill(base.copy(showKeyboardPill = false)))

        assertEquals(
            "🟢 受管 · 云输入(已脱敏)",
            QuickPillStateMachine.panelStatusText(ImeTarget.SOGOU, base, hasCriticalFailure = false)
        )
        assertEquals(
            "🔒 受管 · 纯本地词库",
            QuickPillStateMachine.panelStatusText(
                ImeTarget.SOGOU,
                base.copy(allowCloudInput = false),
                hasCriticalFailure = false
            )
        )
        assertEquals(
            "🔴 未受管 · 暂停保护",
            QuickPillStateMachine.panelStatusText(
                ImeTarget.SOGOU,
                base.withImeManaged(ImeTarget.SOGOU, false),
                hasCriticalFailure = false
            )
        )
        assertEquals(
            "🔴 关键规则异常，请点开主页检查",
            QuickPillStateMachine.panelStatusText(ImeTarget.SOGOU, base, hasCriticalFailure = true)
        )
    }

    @Test
    fun pillTranslationClampingAndOffsetRoundTripAreAccurate() {
        val density = 3f
        val containerW = 1080
        val containerH = 2400
        val pillW = 96
        val pillH = 60
        val visibleTop = 1500

        // Default (12dp, 2dp) -> (36px, 1506px)
        val defaultPos = QuickPillStateMachine.computePillTranslation(
            offset = PillOffset(),
            visibleTopInsetsPx = visibleTop,
            density = density,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
            pillWidthPx = pillW,
            pillHeightPx = pillH
        )
        assertEquals(36f, defaultPos.translationX, 0.001f)
        assertEquals(1506f, defaultPos.translationY, 0.001f)

        val roundTrip = QuickPillStateMachine.translationToPillOffset(
            translationX = defaultPos.translationX,
            translationY = defaultPos.translationY,
            visibleTopInsetsPx = visibleTop,
            density = density
        )
        assertEquals(PillOffset(12, 2), roundTrip)

        // Negative Y offset (pill dragged slightly above keyboard top bar)
        val negativeYPos = QuickPillStateMachine.computePillTranslation(
            offset = PillOffset(20, -15),
            visibleTopInsetsPx = visibleTop,
            density = density,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
            pillWidthPx = pillW,
            pillHeightPx = pillH
        )
        assertEquals(60f, negativeYPos.translationX, 0.001f)
        assertEquals(1455f, negativeYPos.translationY, 0.001f)
        assertEquals(
            PillOffset(20, -15),
            QuickPillStateMachine.translationToPillOffset(
                negativeYPos.translationX,
                negativeYPos.translationY,
                visibleTop,
                density
            )
        )

        // Out-of-bounds right/bottom offsets clamp cleanly to container edges
        val clampedHigh = QuickPillStateMachine.computePillTranslation(
            offset = PillOffset(500, 500),
            visibleTopInsetsPx = visibleTop,
            density = density,
            containerWidthPx = 400,
            containerHeightPx = 1600,
            pillWidthPx = pillW,
            pillHeightPx = pillH
        )
        assertEquals((400 - pillW).toFloat(), clampedHigh.translationX, 0.001f)
        assertEquals((1600 - pillH).toFloat(), clampedHigh.translationY, 0.001f)

        // Out-of-bounds negative offsets clamp to 0f
        val clampedLow = QuickPillStateMachine.computePillTranslation(
            offset = PillOffset(-500, -500),
            visibleTopInsetsPx = 100,
            density = density,
            containerWidthPx = containerW,
            containerHeightPx = containerH,
            pillWidthPx = pillW,
            pillHeightPx = pillH
        )
        assertEquals(0f, clampedLow.translationX, 0.001f)
        assertEquals(0f, clampedLow.translationY, 0.001f)

        // Extreme drag coordinates clamp to PrivacyConfig [MIN_PILL_OFFSET_DP, MAX_PILL_OFFSET_DP]
        val extremeOffset = QuickPillStateMachine.translationToPillOffset(
            translationX = 5000f,
            translationY = -5000f,
            visibleTopInsetsPx = 1500,
            density = density
        )
        assertEquals(
            PillOffset(PrivacyConfig.MAX_PILL_OFFSET_DP, PrivacyConfig.MIN_PILL_OFFSET_DP),
            extremeOffset
        )

        // Pre-layout (0x0 container) preserves non-negative raw offset instead of collapsing to 0
        val preLayout = QuickPillStateMachine.computePillTranslation(
            offset = PillOffset(12, 2),
            visibleTopInsetsPx = 0,
            density = density,
            containerWidthPx = 0,
            containerHeightPx = 0,
            pillWidthPx = 0,
            pillHeightPx = 0
        )
        assertEquals(36f, preLayout.translationX, 0.001f)
        assertEquals(6f, preLayout.translationY, 0.001f)
    }

    @Test
    fun alphaClampingAndTouchSlopChecksWork() {
        assertEquals(PrivacyConfig.DEFAULT_PILL_ALPHA, QuickPillStateMachine.clampAlpha(Float.NaN), 0.0001f)
        assertEquals(PrivacyConfig.MIN_PILL_ALPHA, QuickPillStateMachine.clampAlpha(0.05f), 0.0001f)
        assertEquals(PrivacyConfig.MAX_PILL_ALPHA, QuickPillStateMachine.clampAlpha(2.0f), 0.0001f)
        assertEquals(0.75f, QuickPillStateMachine.clampAlpha(0.75f), 0.0001f)

        assertTrue(QuickPillStateMachine.isWithinTouchSlop(6f, 8f, 10f)) // 6^2 + 8^2 == 10^2
        assertFalse(QuickPillStateMachine.isWithinTouchSlop(7f, 8f, 10f)) // 49 + 64 > 100
    }

    @Test
    fun lifecycleMethodResolverWalksSuperclassChainHonesty() {
        // Direct subclass override -> FAST_PATH
        val (directInsets, directSource) = KeyboardQuickPillHook.resolveImeLifecycleMethod(
            ChildFakeImeService::class.java,
            "onComputeInsets",
            InputMethodService.Insets::class.java
        )
        assertNotNull(directInsets)
        assertEquals(ResolveSource.FAST_PATH, directSource)
        assertEquals(ChildFakeImeService::class.java, directInsets?.declaringClass)

        // Inherited app superclass override (like Baidu ObservableImeService) -> FAST_PATH
        val (superStart, superSource) = KeyboardQuickPillHook.resolveImeLifecycleMethod(
            ChildFakeImeService::class.java,
            "onStartInputView",
            EditorInfo::class.java,
            Boolean::class.javaPrimitiveType!!
        )
        assertNotNull(superStart)
        assertEquals(ResolveSource.FAST_PATH, superSource)
        assertEquals(BaseFakeImeService::class.java, superStart?.declaringClass)

        // Unoverridden method falls back to InputMethodService -> STRUCTURAL_FALLBACK
        val (fallbackFinish, fallbackSource) = KeyboardQuickPillHook.resolveImeLifecycleMethod(
            ChildFakeImeService::class.java,
            "onFinishInputView",
            Boolean::class.javaPrimitiveType!!
        )
        assertNotNull(fallbackFinish)
        assertEquals(ResolveSource.STRUCTURAL_FALLBACK, fallbackSource)
        assertEquals(InputMethodService::class.java, fallbackFinish?.declaringClass)
    }

    private open class BaseFakeImeService : InputMethodService() {
        override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
            super.onStartInputView(info, restarting)
        }
    }

    private class ChildFakeImeService : BaseFakeImeService() {
        override fun onComputeInsets(outInsets: Insets?) {
            super.onComputeInsets(outInsets)
        }
    }
}
