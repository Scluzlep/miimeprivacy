package com.scluzlep.miimeprivacy.emoji

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import com.scluzlep.miimeprivacy.xposed.findClass
import com.scluzlep.miimeprivacy.xposed.findMethod
import io.github.libxposed.api.XposedInterface
import org.luckypray.dexkit.query.enums.StringMatchType
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
        resolver: SymbolResolver,
        ruleId: String = HookRuleCatalog.RULE_SOGOU_UI_DUAL_SKIN_EMOJI,
    ) {
        val popupClassRes = resolver.resolveClass(
            cacheKey = "sogou.emoji.MoreColorEmojiPopupView",
            fastPathCandidates = listOf(POPUP_CLASS),
            dexKitFinder = { dk ->
                dk.findClass {
                    searchPackages("com.sogou.expressionplugin.ui")
                    matcher {
                        usingStrings(listOf("MoreColorEmojiPopupView", "_1F3FB"), StringMatchType.Equals)
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val infoClassRes = resolver.resolveClass(
            cacheKey = "sogou.emoji.BaseExpressionInfo",
            fastPathCandidates = listOf(INFO_CLASS),
            dexKitFinder = { dk ->
                dk.findClass {
                    searchPackages("com.sogou.sogou_router_base.base_bean")
                    matcher {
                        addMethod { name("canShowGroupEmojiAnnotation", StringMatchType.Equals) }
                        addMethod { name("isWeiXinCandidate", StringMatchType.Equals) }
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val popupClass = popupClassRes.value
        val infoClass = infoClassRes.value
        if (popupClass == null || infoClass == null) {
            registry.logInfo("SogouDualSkinPopupHook: popup/info classes not present, skipped")
            registry.reportRule(
                ruleId = ruleId,
                status = HookMatchStatus.FAILED,
                source = ResolveSource.MISS.name,
                detail = "miss:dual_skin_classes"
            )
            return
        }

        val popupPkg = popupClass.name.substringBeforeLast('.', "")
        val setEmojiDrawableRes = resolver.resolveMethod(
            cacheKey = "sogou.emoji.MoreColorEmojiPopupView#setEmojiDrawable",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    popupClass,
                    "setEmojiDrawable",
                    infoClass,
                    Rect::class.java,
                    Rect::class.java,
                )
            },
            dexKitFinder = { dk ->
                dk.findMethod {
                    searchPackages(popupPkg)
                    matcher {
                        declaredClass(popupClass.name, StringMatchType.Equals)
                        name("setEmojiDrawable", StringMatchType.Equals)
                        paramTypes(infoClass.name, Rect::class.java.name, Rect::class.java.name)
                        returnType("void")
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val commitEmojiRes = resolver.resolveMethod(
            cacheKey = "sogou.emoji.MoreColorEmojiPopupView#dealMoreColorCommitEmoji",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    popupClass,
                    "dealMoreColorCommitEmoji",
                    Int::class.javaPrimitiveType!!,
                )
            },
            dexKitFinder = { dk ->
                dk.findMethod {
                    searchPackages(popupPkg)
                    matcher {
                        declaredClass(popupClass.name, StringMatchType.Equals)
                        name("dealMoreColorCommitEmoji", StringMatchType.Equals)
                        paramTypes("int")
                        returnType("void")
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val changeCurrentIndexRes = resolver.resolveMethod(
            cacheKey = "sogou.emoji.MoreColorEmojiPopupView#changeCurrentIndex",
            fastPath = {
                SymbolResolver.findDeclaredMethodOrNull(
                    popupClass,
                    "changeCurrentIndex",
                    MotionEvent::class.java,
                )
            },
            dexKitFinder = { dk ->
                dk.findMethod {
                    searchPackages(popupPkg)
                    matcher {
                        declaredClass(popupClass.name, StringMatchType.Equals)
                        name("changeCurrentIndex", StringMatchType.Equals)
                        paramTypes(MotionEvent::class.java.name)
                        returnType("boolean")
                    }
                }.singleOrNull()?.descriptor
            }
        )
        val commitEmoji = commitEmojiRes.value

        registry.hookExecutable(
            ruleId = ruleId,
            hookId = "emoji:sogou_dual_skin#setEmojiDrawable",
            resolveResult = setEmojiDrawableRes,
            priority = XposedInterface.PRIORITY_DEFAULT,
            onFailureStatus = HookMatchStatus.FAILED,
        ) { chain ->
            val popup = chain.thisObject ?: return@hookExecutable chain.proceed()
            markedPopups.remove(popup)
            restoreReusedViews(popup)

            val result = chain.proceed()
            val info = chain.getArg(0) ?: return@hookExecutable result
            if (!isDualSkinParent(info)) return@hookExecutable result

            val rootBounds = chain.getArg(1) as? Rect ?: return@hookExecutable result
            val emojiBounds = chain.getArg(2) as? Rect ?: return@hookExecutable result
            markedPopups[popup] = true
            runCatching {
                collapseToFiveTones(popup, info, rootBounds, emojiBounds)
            }.onFailure { err ->
                registry.logWarn("SogouDualSkinPopupHook collapse failed", err)
            }
            result
        }

        registry.hookExecutable(
            ruleId = ruleId,
            hookId = "emoji:sogou_dual_skin#commitEmoji",
            resolveResult = commitEmojiRes,
            priority = XposedInterface.PRIORITY_DEFAULT,
            onFailureStatus = HookMatchStatus.FAILED,
        ) { chain ->
            val popup = chain.thisObject ?: return@hookExecutable chain.proceed()
            if (isMarked(popup) && chain.getArg(0) == NEUTRAL_INDEX) {
                chain.proceed(arrayOf(visibleActualIndices.first()))
            } else {
                chain.proceed()
            }
        }

        registry.hookExecutable(
            ruleId = ruleId,
            hookId = "emoji:sogou_dual_skin#changeCurrentIndex",
            resolveResult = changeCurrentIndexRes,
            priority = XposedInterface.PRIORITY_DEFAULT,
            onFailureStatus = HookMatchStatus.FAILED,
        ) { chain ->
            val popup = chain.thisObject ?: return@hookExecutable chain.proceed()
            val event = chain.getArg(0) as? MotionEvent
                ?: return@hookExecutable chain.proceed()
            if (!isMarked(popup) || commitEmoji == null) return@hookExecutable chain.proceed()

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
