package com.scluzlep.miimeprivacy.emoji

import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SogouDualSkinPopupReuseTest {

    @Test
    fun fiveToneCollapseThenSixToneReuseRestoresMarginsWidthCountAndVisibility() {
        val paddingLeft = 10
        val imageWidth = 40
        val marginRight = 8
        val cellWidth = imageWidth + marginRight
        val initialWidth = paddingLeft * 2 + 6 * cellWidth

        val views = Array<ImageView>(6) { index ->
            FakeImageView(
                initialLayoutParams = FrameLayout.LayoutParams(imageWidth, imageWidth).apply {
                    leftMargin = paddingLeft + index * cellWidth
                }
            )
        }
        val selector = FakeFrameLayout(
            initialLayoutParams = FrameLayout.LayoutParams(initialWidth, imageWidth)
        )
        val lineView = FakeView()
        val popup = FakePopupView(
            mEmojiViews = views,
            mLineView = lineView,
            mLayoutPaddingLeft = paddingLeft,
            mImageViewWidth = imageWidth,
            mImageViewMarginRight = marginRight,
            mDrawableCount = 6,
            mLayoutWidth = initialWidth,
            mEmojiSelectorLayout = selector,
            moreColorEmojiPreviewMinLeftMargin = 12,
            mCurrentSelectedIndex = 0,
            mTempSelectedIndex = 0
        )
        val info = FakeExpressionInfo(
            hasSelectedColor = true,
            currentColor = 2
        )

        SogouDualSkinPopupHook.collapseToFiveTones(
            popup = popup,
            info = info,
            rootBounds = Rect(0, 0, 1080, 1920),
            emojiBounds = Rect(200, 500, 260, 560)
        )

        assertEquals(View.GONE, views[1].visibility)
        assertFalse(views[1].isEnabled)
        assertEquals(View.GONE, lineView.visibility)
        assertEquals(5, popup.mDrawableCount)
        assertEquals(paddingLeft * 2 + 5 * cellWidth, popup.mLayoutWidth)
        assertEquals(paddingLeft * 2 + 5 * cellWidth, selector.layoutParams.width)
        assertEquals(paddingLeft + 0 * cellWidth, (views[0].layoutParams as FrameLayout.LayoutParams).leftMargin)
        assertEquals(paddingLeft + 1 * cellWidth, (views[2].layoutParams as FrameLayout.LayoutParams).leftMargin)
        assertEquals(paddingLeft + 4 * cellWidth, (views[5].layoutParams as FrameLayout.LayoutParams).leftMargin)
        assertTrue(views[2].isSelected)
        assertEquals(1, selector.requestLayoutCount)

        // Reuse the same popup instance for a standard 6-tone emoji
        SogouDualSkinPopupHook.restoreReusedViews(popup)

        assertEquals(View.VISIBLE, lineView.visibility)
        assertEquals(6, popup.mDrawableCount)
        assertEquals(initialWidth, popup.mLayoutWidth)
        assertEquals(initialWidth, selector.layoutParams.width)
        assertEquals(2, selector.requestLayoutCount)

        for (index in views.indices) {
            assertEquals("Slot $index visibility", View.VISIBLE, views[index].visibility)
            assertTrue("Slot $index enabled", views[index].isEnabled)
            assertFalse("Slot $index deselected", views[index].isSelected)
            assertEquals(
                "Slot $index leftMargin must not collide",
                paddingLeft + index * cellWidth,
                (views[index].layoutParams as FrameLayout.LayoutParams).leftMargin
            )
        }
    }

    @Test
    fun restoreReusedViewsToleratesMissingOptionalFieldsWithoutThrowing() {
        val views = Array<ImageView>(6) {
            FakeImageView(FrameLayout.LayoutParams(40, 40)).apply {
                visibility = View.GONE
                isEnabled = false
                isSelected = true
            }
        }
        val minimalPopup = MinimalPopupView(mEmojiViews = views)

        SogouDualSkinPopupHook.restoreReusedViews(minimalPopup)

        for (view in views) {
            assertEquals(View.VISIBLE, view.visibility)
            assertTrue(view.isEnabled)
            assertFalse(view.isSelected)
        }
    }

    private class FakeView : View(ContextThemeWrapper()) {
        private var currentVisibility: Int = VISIBLE

        override fun setVisibility(visibility: Int) {
            currentVisibility = visibility
        }

        override fun getVisibility(): Int = currentVisibility
    }

    private class FakeImageView(initialLayoutParams: ViewGroup.LayoutParams) : ImageView(ContextThemeWrapper()) {
        private var params: ViewGroup.LayoutParams = initialLayoutParams
        private var currentVisibility: Int = VISIBLE
        private var enabledState: Boolean = true
        private var selectedState: Boolean = false

        override fun getLayoutParams(): ViewGroup.LayoutParams = params

        override fun setLayoutParams(params: ViewGroup.LayoutParams) {
            this.params = params
        }

        override fun setVisibility(visibility: Int) {
            currentVisibility = visibility
        }

        override fun getVisibility(): Int = currentVisibility

        override fun setEnabled(enabled: Boolean) {
            enabledState = enabled
        }

        override fun isEnabled(): Boolean = enabledState

        override fun setSelected(selected: Boolean) {
            selectedState = selected
        }

        override fun isSelected(): Boolean = selectedState
    }

    private class FakeFrameLayout(initialLayoutParams: ViewGroup.LayoutParams) : FrameLayout(ContextThemeWrapper()) {
        private var params: ViewGroup.LayoutParams = initialLayoutParams
        var requestLayoutCount: Int = 0

        override fun getLayoutParams(): ViewGroup.LayoutParams = params

        override fun setLayoutParams(params: ViewGroup.LayoutParams) {
            this.params = params
        }

        override fun requestLayout() {
            requestLayoutCount++
        }
    }

    @Suppress("unused")
    private class FakePopupView(
        val mEmojiViews: Array<ImageView>,
        val mLineView: View,
        val mLayoutPaddingLeft: Int,
        val mImageViewWidth: Int,
        val mImageViewMarginRight: Int,
        var mDrawableCount: Int,
        var mLayoutWidth: Int,
        val mEmojiSelectorLayout: FrameLayout,
        val moreColorEmojiPreviewMinLeftMargin: Int,
        var mCurrentSelectedIndex: Int,
        var mTempSelectedIndex: Int
    )

    @Suppress("unused")
    private class MinimalPopupView(
        val mEmojiViews: Array<ImageView>
    )

    @Suppress("unused")
    private class FakeExpressionInfo(
        val hasSelectedColor: Boolean,
        val currentColor: Int
    )
}
