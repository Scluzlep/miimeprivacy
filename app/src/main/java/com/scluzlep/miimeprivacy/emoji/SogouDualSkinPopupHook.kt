package com.scluzlep.miimeprivacy.emoji

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Collections
import java.util.WeakHashMap

/**
 * Collapses Sogou's stock 6-slot skin popup into 5 slots for generated dual-skin
 * matrix parents (`hasSelectedColor == true` and two Fitzpatrick modifiers).
 * Installed in Sogou's main process (`com.sohu.inputmethod.sogou.xiaomi`).
 */
internal object SogouDualSkinPopupHook {
    private const val POPUP_CLASS = "com.sogou.expressionplugin.ui.MoreColorEmojiPopupView"
    private const val INFO_CLASS = "com.sogou.sogou_router_base.base_bean.BaseExpressionInfo"
    private const val NEUTRAL_INDEX = 1
    private val visibleActualIndices = intArrayOf(0, 2, 3, 4, 5)
    private val markedPopups = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())

    fun clearOnHotReload() {
        markedPopups.clear()
    }

    fun install(
        registry: HookRegistry,
        classLoader: ClassLoader,
    ) {
        val popupClass = SymbolResolver.loadClassOrNull(POPUP_CLASS, classLoader)
        val infoClass = SymbolResolver.loadClassOrNull(INFO_CLASS, classLoader)
        if (popupClass == null || infoClass == null) {
            registry.logInfo("SogouDualSkinPopupHook: popup/info classes not present, skipped")
            return
        }

        val setEmojiDrawable = SymbolResolver.findDeclaredMethodOrNull(
            popupClass,
            "setEmojiDrawable",
            infoClass,
            Rect::class.java,
            Rect::class.java,
        )
        val commitEmoji = SymbolResolver.findDeclaredMethodOrNull(
            popupClass,
            "dealMoreColorCommitEmoji",
            Int::class.javaPrimitiveType!!,
        )
        val changeCurrentIndex = SymbolResolver.findDeclaredMethodOrNull(
            popupClass,
            "changeCurrentIndex",
            MotionEvent::class.java,
        )
        if (setEmojiDrawable == null || commitEmoji == null || changeCurrentIndex == null) {
            registry.logWarn("SogouDualSkinPopupHook: required popup methods missing")
            return
        }

        runCatching {
            registry.installOrReplaceHook(
                hookId = "emoji:sogou_dual_skin#setEmojiDrawable",
                executable = setEmojiDrawable,
                priority = XposedInterface.PRIORITY_DEFAULT,
            ) { chain ->
                val popup = chain.thisObject ?: return@installOrReplaceHook chain.proceed()
                markedPopups.remove(popup)
                restoreReusedViews(popup)

                val result = chain.proceed()
                val info = chain.getArg(0) ?: return@installOrReplaceHook result
                if (!isDualSkinParent(info)) return@installOrReplaceHook result

                val rootBounds = chain.getArg(1) as? Rect ?: return@installOrReplaceHook result
                val emojiBounds = chain.getArg(2) as? Rect ?: return@installOrReplaceHook result
                markedPopups[popup] = true
                runCatching {
                    collapseToFiveTones(popup, info, rootBounds, emojiBounds)
                }.onFailure { err ->
                    registry.logWarn("SogouDualSkinPopupHook collapse failed", err)
                }
                result
            }

            registry.installOrReplaceHook(
                hookId = "emoji:sogou_dual_skin#commitEmoji",
                executable = commitEmoji,
                priority = XposedInterface.PRIORITY_DEFAULT,
            ) { chain ->
                val popup = chain.thisObject ?: return@installOrReplaceHook chain.proceed()
                if (isMarked(popup) && chain.getArg(0) == NEUTRAL_INDEX) {
                    chain.proceed(arrayOf(visibleActualIndices.first()))
                } else {
                    chain.proceed()
                }
            }

            registry.installOrReplaceHook(
                hookId = "emoji:sogou_dual_skin#changeCurrentIndex",
                executable = changeCurrentIndex,
                priority = XposedInterface.PRIORITY_DEFAULT,
            ) { chain ->
                val popup = chain.thisObject ?: return@installOrReplaceHook chain.proceed()
                val event = chain.getArg(0) as? MotionEvent
                    ?: return@installOrReplaceHook chain.proceed()
                if (!isMarked(popup)) return@installOrReplaceHook chain.proceed()

                runCatching {
                    handleFiveToneMotion(popup, event, commitEmoji)
                }.fold(
                    onSuccess = { true },
                    onFailure = { error ->
                        registry.logWarn("SogouDualSkinPopupHook drag failed", error)
                        chain.proceed()
                    },
                )
            }
            registry.logInfo("SogouDualSkinPopupHook: installed 5x5 popup hooks")
        }.onFailure { err ->
            registry.logWarn("SogouDualSkinPopupHook: failed to install hooks", err)
        }
    }

    private fun isDualSkinParent(info: Any): Boolean {
        val hasSelected = runCatching { info.booleanField("hasSelectedColor") }.getOrDefault(false)
        if (!hasSelected) return false
        val unified = runCatching { info.objectField("unified") as? String }.getOrNull() ?: return false
        return unified.skinModifierCount() == 2
    }

    private fun isMarked(popup: Any): Boolean = markedPopups[popup] == true

    private fun restoreReusedViews(popup: Any) {
        val values = popup.objectField("mEmojiViews") as? Array<*> ?: return
        values.filterIsInstance<ImageView>().forEach { view ->
            view.visibility = View.VISIBLE
            view.isEnabled = true
        }
        (popup.objectField("mLineView") as? View)?.visibility = View.VISIBLE
    }

    private fun collapseToFiveTones(
        popup: Any,
        info: Any,
        rootBounds: Rect,
        emojiBounds: Rect,
    ) {
        val views = emojiViews(popup)
        require(views.size >= 6) { "Sogou skin popup no longer has six slots" }

        views[NEUTRAL_INDEX].visibility = View.GONE
        views[NEUTRAL_INDEX].isEnabled = false
        (popup.objectField("mLineView") as? View)?.visibility = View.GONE

        val paddingLeft = popup.intField("mLayoutPaddingLeft")
        val imageWidth = popup.intField("mImageViewWidth")
        val marginRight = popup.intField("mImageViewMarginRight")
        val cellWidth = imageWidth + marginRight

        visibleActualIndices.forEachIndexed { visibleIndex, actualIndex ->
            val params = views[actualIndex].layoutParams as FrameLayout.LayoutParams
            params.leftMargin = paddingLeft + visibleIndex * cellWidth
            views[actualIndex].layoutParams = params
        }

        val newLayoutWidth = paddingLeft * 2 + visibleActualIndices.size * cellWidth
        popup.setField("mDrawableCount", visibleActualIndices.size)
        popup.setField("mLayoutWidth", newLayoutWidth)
        val selector = popup.objectField("mEmojiSelectorLayout") as FrameLayout
        val selectedActual = info.intField("currentColor")
            .takeIf { it in visibleActualIndices }
            ?: visibleActualIndices.first()
        val selectedVisible = visibleActualIndices.indexOf(selectedActual)
        val selectedCenter = paddingLeft + selectedVisible * cellWidth + imageWidth / 2
        val touchCenter = emojiBounds.centerX() - rootBounds.left
        val minimumLeft = popup.intField("moreColorEmojiPreviewMinLeftMargin")
        val maximumLeft = (rootBounds.width() - minimumLeft - newLayoutWidth)
            .coerceAtLeast(minimumLeft)
        selector.layoutParams = (selector.layoutParams as FrameLayout.LayoutParams).apply {
            width = newLayoutWidth
            leftMargin = (touchCenter - selectedCenter).coerceIn(minimumLeft, maximumLeft)
        }
        views.forEach { it.isSelected = false }
        views[selectedActual].isSelected = true
        popup.setField("mCurrentSelectedIndex", selectedActual)
        popup.setField("mTempSelectedIndex", selectedActual)
        selector.requestLayout()
    }

    private fun handleFiveToneMotion(
        popup: Any,
        event: MotionEvent,
        commitEmoji: Method,
    ) {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> handleMove(popup, event)
            MotionEvent.ACTION_UP -> handleUp(popup, commitEmoji)
            MotionEvent.ACTION_CANCEL -> {
                popup.setField("mIsFirst", true)
                popup.setField("mHasMove", false)
            }
        }
    }

    private fun handleMove(popup: Any, event: MotionEvent) {
        if (popup.booleanField("mIsFirst")) {
            val current = popup.intField("mCurrentSelectedIndex")
            popup.setField("mTempSelectedIndex", current)
            popup.setField("startX", event.x)
            popup.setField("mIsFirst", false)
        }

        val currentActual = popup.intField("mCurrentSelectedIndex")
        val currentVisible = visibleActualIndices.indexOf(currentActual).coerceAtLeast(0)
        val moveDistance = popup.intField("mMoveDistanceForChangeIndex").coerceAtLeast(1)
        val delta = ((event.x - popup.floatField("startX")) / moveDistance).toInt()
        val nextActual = visibleActualIndices[
            (currentVisible + delta).coerceIn(visibleActualIndices.indices)
        ]
        val previousActual = popup.intField("mTempSelectedIndex")
        if (nextActual == previousActual) return

        val views = emojiViews(popup)
        views.getOrNull(previousActual)?.isSelected = false
        views[nextActual].isSelected = true
        popup.setField("mHasMove", true)
        popup.setField("mTempSelectedIndex", nextActual)
    }

    private fun handleUp(popup: Any, commitEmoji: Method) {
        popup.setField("mIsFirst", true)
        val info = popup.objectField("mBaseExpressionInfo") ?: return
        val shouldCommit = popup.booleanField("mHasMove") ||
            info.booleanField("hasSelectedColor")
        if (!shouldCommit) return

        val selectedActual = popup.intField("mTempSelectedIndex")
            .takeIf { it in visibleActualIndices }
            ?: popup.intField("mCurrentSelectedIndex")
                .takeIf { it in visibleActualIndices }
            ?: visibleActualIndices.first()
        popup.setField("mCurrentSelectedIndex", selectedActual)
        popup.setField("mTempSelectedIndex", -1)
        commitEmoji.isAccessible = true
        commitEmoji.invoke(popup, selectedActual)

        val keyboard = popup.objectField("mExpressionKeyboardEmoji") ?: return
        val commitOnKeyboard = findMethodSafe(keyboard, "Z", info, selectedActual)
        commitOnKeyboard?.invoke(keyboard, info, selectedActual)
    }

    private fun emojiViews(popup: Any): Array<ImageView> {
        val values = popup.objectField("mEmojiViews") as Array<*>
        return Array(values.size) { index -> values[index] as ImageView }
    }

    private fun Any.objectField(name: String): Any? = findField(name).get(this)

    private fun Any.booleanField(name: String): Boolean = findField(name).getBoolean(this)

    private fun Any.intField(name: String): Int = findField(name).getInt(this)

    private fun Any.floatField(name: String): Float = findField(name).getFloat(this)

    private fun Any.setField(name: String, value: Any) {
        findField(name).set(this, value)
    }

    private fun Any.findField(name: String): Field {
        var type: Class<*>? = javaClass
        while (type != null) {
            runCatching { type.getDeclaredField(name) }.getOrNull()?.let { field ->
                field.isAccessible = true
                return field
            }
            type = type.superclass
        }
        error("Field $name not found in ${javaClass.name}")
    }

    private fun findMethodSafe(target: Any, name: String, vararg args: Any): Method? {
        var type: Class<*>? = target.javaClass
        while (type != null) {
            val declared = runCatching { type.declaredMethods }.getOrDefault(emptyArray())
            declared.firstOrNull { method ->
                method.name == name &&
                    method.parameterTypes.size == args.size &&
                    method.parameterTypes.zip(args).all { (parameterType, argument) ->
                        parameterType.boxed().isAssignableFrom(argument.javaClass)
                    }
            }?.let { method ->
                method.isAccessible = true
                return method
            }
            type = type.superclass
        }
        return null
    }

    private fun Class<*>.boxed(): Class<*> = when (this) {
        Integer.TYPE -> Int::class.javaObjectType
        java.lang.Boolean.TYPE -> Boolean::class.javaObjectType
        java.lang.Float.TYPE -> Float::class.javaObjectType
        else -> this
    }
}
