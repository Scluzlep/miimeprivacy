package com.scluzlep.miimeprivacy.xposed.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Region
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.TextView
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import com.scluzlep.miimeprivacy.xposed.ResolveSource
import com.scluzlep.miimeprivacy.xposed.SymbolResolver
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Non-intrusive `SoftInputWindow` overlay hook installing an emoji-only (`🟢`/`🔒`/`🔴`)
 * micro-pill and an in-window Floating Quick Panel onto `android.R.id.content`.
 *
 * Every hook installed uses the `"overlay:"` hookId prefix so [com.scluzlep.miimeprivacy.xposed.MiImePrivacyModule]
 * preserves the overlay even when privacy hooks are toggled off live (`🔴` unmanaged indicator).
 */
object KeyboardQuickPillHook {

    const val PILL_TAG = "miime_quick_pill"

    private const val HOOK_START_INPUT_VIEW = "overlay:onStartInputView"
    private const val HOOK_FINISH_INPUT_VIEW = "overlay:onFinishInputView"
    private const val HOOK_COMPUTE_INSETS = "overlay:onComputeInsets"
    private const val HOOK_WINDOW_HIDDEN = "overlay:onWindowHidden"
    private const val HOOK_CONFIGURATION_CHANGED = "overlay:onConfigurationChanged"
    private const val HOOK_DESTROY = "overlay:onDestroy"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val overlaysLock = Any()
    private val overlays = WeakHashMap<InputMethodService, ServiceOverlay>()

    @Volatile
    private var activeTarget: ImeTarget? = null

    @Volatile
    private var activeRegistry: HookRegistry? = null

    @Volatile
    private var bridgeListener: ((PrivacyConfig, PrivacyConfig) -> Unit)? = null

    private class ServiceOverlay(
        val contentFrame: FrameLayout,
        val pillView: TextView
    ) {
        var panelHandle: QuickPanelViewFactory.PanelHandle? = null
        var lastVisibleTopInsets: Int = 0
        var lastNightMode: Boolean? = null
        var isDragging: Boolean = false
        var longPressTriggered: Boolean = false
        var downRawX: Float = 0f
        var downRawY: Float = 0f
        var startTransX: Float = 0f
        var startTransY: Float = 0f
        var longPressRunnable: Runnable? = null
        var lastMeasuredWidth: Int = 0
        val tmpLocation: IntArray = IntArray(2)
    }

    fun install(
        target: ImeTarget,
        context: Context?,
        classLoader: ClassLoader,
        @Suppress("UNUSED_PARAMETER") appInfo: ApplicationInfo,
        registry: HookRegistry
    ) {
        activeTarget = target
        activeRegistry = registry
        context?.let { ConfigBridge.attachContext(it) }

        val ruleId = HookRuleCatalog.overlayRuleIdFor(target)
        val imeServiceClass = SymbolResolver.loadClassOrNull(target.imeServiceClassName, classLoader)

        val bridgeSub: (PrivacyConfig, PrivacyConfig) -> Unit = { oldCfg, newCfg ->
            onConfigChanged(oldCfg, newCfg)
        }
        bridgeListener?.let { ConfigBridge.removeListener(it) }
        bridgeListener = bridgeSub
        ConfigBridge.addListener(bridgeSub)

        val (startMethod, startSource) = resolveImeLifecycleMethod(
            imeServiceClass,
            "onStartInputView",
            EditorInfo::class.java,
            Boolean::class.javaPrimitiveType!!
        )
        val startHooked = registry.hookMethod(
            ruleId = ruleId,
            hookId = HOOK_START_INPUT_VIEW,
            executable = startMethod,
            source = startSource.name
        ) { chain ->
            val result = chain.proceed()
            val service = chain.thisObject as? InputMethodService
            if (service != null) {
                ConfigBridge.attachContext(service)
                activeRegistry?.attachContext(service)
                runOnMain {
                    ensurePillMountedAndUpdated(service, target)
                }
            }
            result
        }

        val (finishMethod, finishSource) = resolveImeLifecycleMethod(
            imeServiceClass,
            "onFinishInputView",
            Boolean::class.javaPrimitiveType!!
        )
        val finishHooked = registry.hookMethod(
            ruleId = ruleId,
            hookId = HOOK_FINISH_INPUT_VIEW,
            executable = finishMethod,
            source = finishSource.name
        ) { chain ->
            val service = chain.thisObject as? InputMethodService
            if (service != null) {
                cancelPendingGesture(service)
                runOnMain {
                    dismissPanelForService(service)
                }
            }
            chain.proceed()
        }

        val (insetsMethod, insetsSource) = resolveImeLifecycleMethod(
            imeServiceClass,
            "onComputeInsets",
            InputMethodService.Insets::class.java
        )
        val insetsHooked = registry.hookMethod(
            ruleId = ruleId,
            hookId = HOOK_COMPUTE_INSETS,
            executable = insetsMethod,
            source = insetsSource.name
        ) { chain ->
            val result = chain.proceed()
            val service = chain.thisObject as? InputMethodService
            val insets = chain.getArg(0) as? InputMethodService.Insets
            if (service != null && insets != null) {
                handleOnComputeInsets(service, target, insets)
            }
            result
        }

        val (hiddenMethod, hiddenSource) = resolveImeLifecycleMethod(
            imeServiceClass,
            "onWindowHidden"
        )
        registry.hookMethod(
            ruleId = ruleId,
            hookId = HOOK_WINDOW_HIDDEN,
            executable = hiddenMethod,
            source = hiddenSource.name
        ) { chain ->
            val service = chain.thisObject as? InputMethodService
            if (service != null) {
                cancelPendingGesture(service)
                runOnMain {
                    dismissPanelForService(service)
                }
            }
            chain.proceed()
        }

        val (configMethod, configSource) = resolveImeLifecycleMethod(
            imeServiceClass,
            "onConfigurationChanged",
            Configuration::class.java
        )
        val configHooked = registry.hookMethod(
            ruleId = ruleId,
            hookId = HOOK_CONFIGURATION_CHANGED,
            executable = configMethod,
            source = configSource.name
        ) { chain ->
            val result = chain.proceed()
            val service = chain.thisObject as? InputMethodService
            if (service != null) {
                runOnMain {
                    val cfg = ConfigBridge.current
                    val overlay = synchronized(overlaysLock) { overlays[service] }
                    if (overlay != null) {
                        applyConfigToOverlay(
                            service = service,
                            overlay = overlay,
                            target = target,
                            config = cfg,
                            requestRelayoutIfMoved = true
                        )
                    }
                    if (isLandscape(service) && cfg.hidePillInLandscape) {
                        dismissPanelForService(service)
                    }
                }
            }
            result
        }

        val (destroyMethod, destroySource) = resolveImeLifecycleMethod(
            imeServiceClass,
            "onDestroy"
        )
        registry.hookMethod(
            ruleId = ruleId,
            hookId = HOOK_DESTROY,
            executable = destroyMethod,
            source = destroySource.name
        ) { chain ->
            val service = chain.thisObject as? InputMethodService
            if (service != null) {
                cancelPendingGesture(service)
                runOnMain {
                    cleanupServiceOverlay(service)
                }
            }
            chain.proceed()
        }

        if (!startHooked || !finishHooked || !insetsHooked || !configHooked) {
            registry.logWarn(
                "Overlay pill hooks incomplete: start=$startHooked finish=$finishHooked insets=$insetsHooked config=$configHooked"
            )
        } else if (registry.getReport(ruleId)?.status == null) {
            registry.reportRule(
                ruleId = ruleId,
                status = HookMatchStatus.ACTIVE,
                matchedMethod = target.imeServiceClassName
            )
        }
    }

    fun onConfigChanged(
        @Suppress("UNUSED_PARAMETER") oldConfig: PrivacyConfig,
        newConfig: PrivacyConfig
    ) {
        val target = activeTarget ?: return
        runOnMain {
            val entries = synchronized(overlaysLock) {
                overlays.entries.map { it.key to it.value }
            }
            for ((service, overlay) in entries) {
                applyConfigToOverlay(service, overlay, target, newConfig, requestRelayoutIfMoved = true)
            }
        }
    }

    fun clearOnHotReload() {
        bridgeListener?.let { ConfigBridge.removeListener(it) }
        bridgeListener = null

        val snapshot = synchronized(overlaysLock) {
            val list = overlays.entries.map { it.key to it.value }
            overlays.clear()
            list
        }
        for ((_, overlay) in snapshot) {
            overlay.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
            overlay.longPressRunnable = null
        }

        runOnMain {
            for ((_, overlay) in snapshot) {
                runCatching {
                    removeViewsByTag(overlay.contentFrame, PILL_TAG)
                    removeViewsByTag(overlay.contentFrame, QuickPanelViewFactory.PANEL_TAG)
                    overlay.panelHandle = null
                }
            }
        }

        activeTarget = null
        activeRegistry = null
    }

    internal fun resolveImeLifecycleMethod(
        imeServiceClass: Class<*>?,
        methodName: String,
        vararg parameterTypes: Class<*>
    ): Pair<Method?, ResolveSource> {
        var current: Class<*>? = imeServiceClass
        while (current != null &&
            current != InputMethodService::class.java &&
            current != Any::class.java
        ) {
            val declared = SymbolResolver.findDeclaredMethodOrNull(current, methodName, *parameterTypes)
            if (declared != null) {
                return declared to ResolveSource.FAST_PATH
            }
            current = current.superclass
        }

        val baseMethod = SymbolResolver.findDeclaredMethodOrNull(
            InputMethodService::class.java,
            methodName,
            *parameterTypes
        )
        return if (baseMethod != null) {
            baseMethod to ResolveSource.STRUCTURAL_FALLBACK
        } else {
            null to ResolveSource.MISS
        }
    }

    private fun ensurePillMountedAndUpdated(service: InputMethodService, target: ImeTarget) {
        val contentFrame = findImeContentFrame(service) ?: return
        val existing = synchronized(overlaysLock) { overlays[service] }

        if (existing != null &&
            existing.contentFrame === contentFrame &&
            existing.pillView.parent === contentFrame
        ) {
            applyConfigToOverlay(service, existing, target, ConfigBridge.current, requestRelayoutIfMoved = false)
            existing.pillView.bringToFront()
            existing.panelHandle?.rootView?.let { panelRoot ->
                if (panelRoot.parent === contentFrame) {
                    panelRoot.bringToFront()
                }
            }
            if (existing.pillView.width == 0) {
                contentFrame.requestLayout()
            }
            return
        }

        if (existing != null) {
            cleanupServiceOverlay(service)
        }
        removeViewsByTag(contentFrame, PILL_TAG)
        removeViewsByTag(contentFrame, QuickPanelViewFactory.PANEL_TAG)

        val pillView = createPillView(service)
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.LEFT
        )
        val overlay = ServiceOverlay(contentFrame = contentFrame, pillView = pillView)
        attachPillGestureHandler(service, overlay, target)
        pillView.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val newWidth = view.width
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && newWidth > 0 && view.height > 0) {
                view.systemGestureExclusionRects = listOf(Rect(0, 0, newWidth, view.height))
            }
            if (newWidth > 0 && overlay.lastMeasuredWidth == 0) {
                overlay.lastMeasuredWidth = newWidth
                applyPositionToPill(service, overlay, target, ConfigBridge.current)
                contentFrame.post { contentFrame.requestLayout() }
            } else {
                overlay.lastMeasuredWidth = newWidth
            }
        }

        contentFrame.addView(pillView, lp)
        synchronized(overlaysLock) {
            overlays[service] = overlay
        }
        applyConfigToOverlay(service, overlay, target, ConfigBridge.current, requestRelayoutIfMoved = true)
    }

    private fun createPillView(context: Context): TextView {
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val padH = (6f * density).roundToInt()
        val padV = (2f * density).roundToInt()

        return TextView(context).apply {
            tag = PILL_TAG
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(padH, padV, padH, padV)
            elevation = 4f * density
            background = createPillBackground(context, QuickPanelViewFactory.isNightMode(context))
        }
    }

    private fun createPillBackground(context: Context, isNight: Boolean): GradientDrawable {
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val strokePx = (0.75f * density).roundToInt().coerceAtLeast(1)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            if (isNight) {
                setColor(Color.argb(0xBD, 0x22, 0x25, 0x2B))
                setStroke(strokePx, Color.argb(0x42, 0xFF, 0xFF, 0xFF))
            } else {
                setColor(Color.argb(0xD4, 0xFF, 0xFF, 0xFF))
                setStroke(strokePx, Color.argb(0x28, 0x00, 0x00, 0x00))
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachPillGestureHandler(
        service: InputMethodService,
        overlay: ServiceOverlay,
        target: ImeTarget
    ) {
        overlay.pillView.setOnTouchListener { view, event ->
            val density = service.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
            val touchSlopPx = QuickPillStateMachine.TOUCH_SLOP_DP * density

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    overlay.downRawX = event.rawX
                    overlay.downRawY = event.rawY
                    overlay.startTransX = view.translationX
                    overlay.startTransY = view.translationY
                    overlay.isDragging = false
                    overlay.longPressTriggered = false

                    overlay.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    val runnable = Runnable {
                        if (!overlay.isDragging && !overlay.longPressTriggered && view.isAttachedToWindow) {
                            overlay.longPressTriggered = true
                            runCatching { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
                            showPanelForService(service, overlay, target)
                        }
                    }
                    overlay.longPressRunnable = runnable
                    mainHandler.postDelayed(runnable, QuickPillStateMachine.LONG_PRESS_TIMEOUT_MS)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (overlay.longPressTriggered) {
                        return@setOnTouchListener true
                    }
                    val dx = event.rawX - overlay.downRawX
                    val dy = event.rawY - overlay.downRawY
                    if (!overlay.isDragging &&
                        !QuickPillStateMachine.isWithinTouchSlop(dx, dy, touchSlopPx)
                    ) {
                        overlay.isDragging = true
                        overlay.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                        overlay.longPressRunnable = null
                    }
                    if (overlay.isDragging) {
                        val maxX = (effectiveContainerWidthPx(service, overlay) - view.width).coerceAtLeast(0).toFloat()
                        val maxY = (effectiveContainerHeightPx(service, overlay) - view.height).coerceAtLeast(0).toFloat()
                        view.translationX = (overlay.startTransX + dx).coerceIn(0f, maxX)
                        view.translationY = (overlay.startTransY + dy).coerceIn(0f, maxY)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    overlay.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    overlay.longPressRunnable = null

                    if (overlay.longPressTriggered) {
                        overlay.longPressTriggered = false
                        return@setOnTouchListener true
                    }

                    if (overlay.isDragging) {
                        overlay.isDragging = false
                        val newOffset = QuickPillStateMachine.translationToPillOffset(
                            translationX = view.translationX,
                            translationY = view.translationY,
                            visibleTopInsetsPx = overlay.lastVisibleTopInsets,
                            density = density
                        )
                        ConfigBridge.persistConfigUpdateFromIme(service) { cfg ->
                            cfg.withPillOffset(target, newOffset)
                        }
                        overlay.contentFrame.requestLayout()
                        return@setOnTouchListener true
                    }

                    // Single tap (<380ms, move <=10dp)
                    val hasCritical = activeRegistry?.hasCriticalFailure() == true
                    when (val outcome = QuickPillStateMachine.onSingleTap(target, ConfigBridge.current, hasCritical)) {
                        is QuickPillStateMachine.TapOutcome.UpdateConfig -> {
                            runCatching { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                            ConfigBridge.persistConfigUpdateFromIme(service, outcome.updatedConfig)
                        }
                        QuickPillStateMachine.TapOutcome.OpenPanel -> {
                            showPanelForService(service, overlay, target)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    overlay.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
                    overlay.longPressRunnable = null
                    if (overlay.isDragging) {
                        overlay.isDragging = false
                        applyPositionToPill(service, overlay, target, ConfigBridge.current)
                    }
                    overlay.longPressTriggered = false
                    true
                }

                else -> false
            }
        }
    }

    private fun handleOnComputeInsets(
        service: InputMethodService,
        target: ImeTarget,
        insets: InputMethodService.Insets
    ) {
        val overlay = synchronized(overlaysLock) { overlays[service] } ?: return
        val contentFrame = overlay.contentFrame
        overlay.lastVisibleTopInsets = insets.visibleTopInsets

        val panelRoot = overlay.panelHandle?.rootView
        val isPanelVisible = panelRoot != null &&
            panelRoot.parent === contentFrame &&
            panelRoot.visibility == View.VISIBLE

        val cfg = ConfigBridge.current
        val landscape = isLandscape(service)
        if (isPanelVisible) {
            if (landscape && cfg.hidePillInLandscape) {
                mainHandler.post { dismissPanelForService(service) }
            }
            val width = effectiveContainerWidthPx(service, overlay)
            val height = effectiveContainerHeightPx(service, overlay)
            insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
            if (width > 0 && height > 0) {
                insets.touchableRegion.set(0, 0, width, height)
            }
            return
        }

        val hasKeyboardSurface = contentFrame.height <= 0 || insets.visibleTopInsets < contentFrame.height
        val shouldShow = QuickPillStateMachine.shouldShowPill(cfg, landscape) && hasKeyboardSurface
        val desiredVisibility = if (shouldShow) View.VISIBLE else View.GONE
        if (overlay.pillView.visibility != desiredVisibility) {
            overlay.pillView.visibility = desiredVisibility
        }
        if (!shouldShow) return

        refreshPillAppearance(service, overlay, target, cfg)

        if (!overlay.isDragging) {
            applyPositionToPill(service, overlay, target, cfg)
        }

        val pill = overlay.pillView
        if (pill.width > 0 && pill.height > 0) {
            val contentLoc = overlay.tmpLocation
            contentFrame.getLocationInWindow(contentLoc)
            val left = (contentLoc[0] + pill.left + pill.translationX).roundToInt()
            val top = (contentLoc[1] + pill.top + pill.translationY).roundToInt()
            if (insets.touchableInsets != InputMethodService.Insets.TOUCHABLE_INSETS_REGION &&
                insets.touchableInsets != InputMethodService.Insets.TOUCHABLE_INSETS_FRAME &&
                top < insets.visibleTopInsets
            ) {
                val width = effectiveContainerWidthPx(service, overlay)
                val height = effectiveContainerHeightPx(service, overlay)
                if (width > 0 && height > 0) {
                    insets.touchableRegion.set(
                        0,
                        insets.visibleTopInsets.coerceIn(0, height),
                        width,
                        height
                    )
                    insets.touchableInsets = InputMethodService.Insets.TOUCHABLE_INSETS_REGION
                }
            }
            insets.touchableRegion.op(
                left,
                top,
                left + pill.width,
                top + pill.height,
                Region.Op.UNION
            )
        }
    }

    private fun applyConfigToOverlay(
        service: InputMethodService,
        overlay: ServiceOverlay,
        target: ImeTarget,
        config: PrivacyConfig,
        requestRelayoutIfMoved: Boolean
    ) {
        val landscape = isLandscape(service)
        val isVisible = QuickPillStateMachine.shouldShowPill(config, landscape)
        val targetVis = if (isVisible) View.VISIBLE else View.GONE
        val visChanged = overlay.pillView.visibility != targetVis
        if (visChanged) {
            overlay.pillView.visibility = targetVis
        }

        refreshPillAppearance(service, overlay, target, config)

        var posChanged = false
        if (!overlay.isDragging) {
            val prevX = overlay.pillView.translationX
            val prevY = overlay.pillView.translationY
            applyPositionToPill(service, overlay, target, config)
            posChanged = prevX != overlay.pillView.translationX || prevY != overlay.pillView.translationY
        }

        if (landscape && config.hidePillInLandscape) {
            dismissPanelForService(service)
        } else {
            overlay.panelHandle?.let { handle ->
                if (handle.rootView.parent === overlay.contentFrame) {
                    val currentNight = QuickPanelViewFactory.isNightMode(service)
                    if (handle.createdNightMode != currentNight) {
                        dismissPanelForService(service)
                        showPanelForService(service, overlay, target)
                    } else {
                        val diag = currentDiagnostics(target, config)
                        handle.bind(config, diag, currentNight)
                    }
                }
            }
        }

        if (requestRelayoutIfMoved && (visChanged || posChanged)) {
            overlay.contentFrame.requestLayout()
        }
    }

    private fun refreshPillAppearance(
        service: InputMethodService,
        overlay: ServiceOverlay,
        target: ImeTarget,
        config: PrivacyConfig
    ) {
        val hasCritical = activeRegistry?.hasCriticalFailure() == true
        val state = QuickPillStateMachine.resolveVisualState(target, config, hasCritical)
        if (overlay.pillView.text?.toString() != state.emoji) {
            overlay.pillView.text = state.emoji
        }
        val targetAlpha = QuickPillStateMachine.clampAlpha(config.pillAlpha)
        if (overlay.pillView.alpha != targetAlpha) {
            overlay.pillView.alpha = targetAlpha
        }
        val nightMode = QuickPanelViewFactory.isNightMode(service)
        if (overlay.lastNightMode != nightMode) {
            overlay.lastNightMode = nightMode
            overlay.pillView.background = createPillBackground(service, nightMode)
        }
    }

    private fun applyPositionToPill(
        service: InputMethodService,
        overlay: ServiceOverlay,
        target: ImeTarget,
        config: PrivacyConfig
    ) {
        val density = service.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val pos = QuickPillStateMachine.computePillTranslation(
            offset = config.pillOffsetFor(target),
            visibleTopInsetsPx = overlay.lastVisibleTopInsets,
            density = density,
            containerWidthPx = effectiveContainerWidthPx(service, overlay),
            containerHeightPx = effectiveContainerHeightPx(service, overlay),
            pillWidthPx = overlay.pillView.width,
            pillHeightPx = overlay.pillView.height
        )
        overlay.pillView.translationX = pos.translationX
        overlay.pillView.translationY = pos.translationY
    }

    private fun isLandscape(service: InputMethodService): Boolean {
        return service.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun effectiveContainerWidthPx(service: InputMethodService, overlay: ServiceOverlay): Int {
        return overlay.contentFrame.width.takeIf { it > 0 }
            ?: service.window?.window?.decorView?.width?.takeIf { it > 0 }
            ?: service.resources.displayMetrics.widthPixels
    }

    private fun effectiveContainerHeightPx(service: InputMethodService, overlay: ServiceOverlay): Int {
        return overlay.contentFrame.height.takeIf { it > 0 }
            ?: service.window?.window?.decorView?.height?.takeIf { it > 0 }
            ?: service.resources.displayMetrics.heightPixels
    }

    private fun showPanelForService(
        service: InputMethodService,
        overlay: ServiceOverlay,
        target: ImeTarget
    ) {
        val contentFrame = overlay.contentFrame
        val currentHandle = overlay.panelHandle
        val cfg = ConfigBridge.current
        val currentNight = QuickPanelViewFactory.isNightMode(service)
        if (currentHandle != null &&
            currentHandle.rootView.parent === contentFrame &&
            currentHandle.createdNightMode == currentNight
        ) {
            currentHandle.rootView.visibility = View.VISIBLE
            currentHandle.bind(cfg, currentDiagnostics(target, cfg), currentNight)
            currentHandle.rootView.bringToFront()
            contentFrame.requestLayout()
            return
        }
        if (currentHandle != null) {
            dismissPanelForService(service)
        }

        removeViewsByTag(contentFrame, QuickPanelViewFactory.PANEL_TAG)
        val handle = QuickPanelViewFactory.create(
            context = service,
            target = target,
            registry = activeRegistry,
            initialConfig = cfg,
            onConfigUpdated = { updated ->
                applyConfigToOverlay(service, overlay, target, updated, requestRelayoutIfMoved = true)
            },
            onDismiss = {
                dismissPanelForService(service)
            }
        )
        overlay.panelHandle = handle
        contentFrame.addView(
            handle.rootView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        handle.rootView.bringToFront()
        contentFrame.requestLayout()
    }

    private fun dismissPanelForService(service: InputMethodService) {
        val overlay = synchronized(overlaysLock) { overlays[service] } ?: return
        val handle = overlay.panelHandle
        if (handle != null) {
            (handle.rootView.parent as? ViewGroup)?.removeView(handle.rootView)
            overlay.panelHandle = null
        }
        removeViewsByTag(overlay.contentFrame, QuickPanelViewFactory.PANEL_TAG)
        overlay.contentFrame.requestLayout()
    }

    private fun cleanupServiceOverlay(service: InputMethodService) {
        val removed = synchronized(overlaysLock) { overlays.remove(service) } ?: return
        removed.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        removed.longPressRunnable = null
        removed.panelHandle = null
        runCatching {
            removeViewsByTag(removed.contentFrame, PILL_TAG)
            removeViewsByTag(removed.contentFrame, QuickPanelViewFactory.PANEL_TAG)
        }
    }

    private fun cancelPendingGesture(service: InputMethodService) {
        val overlay = synchronized(overlaysLock) { overlays[service] } ?: return
        overlay.longPressRunnable?.let { mainHandler.removeCallbacks(it) }
        overlay.longPressRunnable = null
        overlay.isDragging = false
        overlay.longPressTriggered = false
    }

    private fun currentDiagnostics(target: ImeTarget, config: PrivacyConfig): ImeRuntimeDiagnostics {
        return activeRegistry?.snapshotDiagnostics(
            activeCloudEnabled = config.effectiveCloudEnabled(target)
        ) ?: ImeRuntimeDiagnostics(
            target = target,
            activeCloudEnabled = config.effectiveCloudEnabled(target)
        )
    }

    private fun findImeContentFrame(service: InputMethodService): FrameLayout? {
        return runCatching {
            service.window?.window?.findViewById<FrameLayout>(android.R.id.content)
        }.getOrNull()
    }

    private fun removeViewsByTag(container: ViewGroup, tag: String) {
        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            if (child?.tag == tag) {
                container.removeViewAt(i)
            }
        }
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
