package com.scluzlep.miimeprivacy.xposed.overlay

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.HookRuleCatalog
import com.scluzlep.miimeprivacy.model.ImeRuntimeDiagnostics
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.xposed.ConfigBridge
import com.scluzlep.miimeprivacy.xposed.HookRegistry
import kotlin.math.roundToInt

/**
 * Programmatic Android-framework view factory for the in-IME Floating Quick Panel.
 * Uses zero IME XML resources and keeps every view non-focusable so host `EditText`
 * focus and cursor selection are never interrupted.
 */
object QuickPanelViewFactory {

    const val PANEL_TAG = "miime_quick_panel"

    class PanelHandle(
        val rootView: FrameLayout,
        private val badgeView: TextView,
        private val blockedCountView: TextView,
        private val switchManaged: Switch,
        private val switchCloud: Switch,
        private val switchContext: Switch,
        private val switchClipboard: Switch,
        private val target: ImeTarget
    ) {
        private var isBinding: Boolean = false

        fun bind(
            config: PrivacyConfig,
            diagnostics: ImeRuntimeDiagnostics,
            isDark: Boolean
        ) {
            isBinding = true
            try {
                val hasCritical = diagnostics.hasCriticalFailure
                val state = QuickPillStateMachine.resolveVisualState(target, config, hasCritical)
                badgeView.text = QuickPillStateMachine.panelStatusText(target, config, hasCritical)
                styleBadge(badgeView, state, isDark, rootView.context)

                val detailSuffix = if (hasCritical) {
                    val failedNames = diagnostics.rules
                        .filter {
                            it.status == HookMatchStatus.FAILED &&
                                (HookRuleCatalog.find(it.ruleId)?.critical == true)
                        }
                        .joinToString(", ") { it.ruleId.substringAfter('.') }
                    if (failedNames.isNotBlank()) " · 异常: $failedNames" else ""
                } else {
                    ""
                }
                blockedCountView.text = "本次会话已拦截 ${diagnostics.totalBlockedCount} 次隐私行为$detailSuffix"

                if (switchManaged.isChecked != config.isImeManaged(target)) {
                    switchManaged.isChecked = config.isImeManaged(target)
                }
                val cloudEnabled = config.effectiveCloudEnabled(target)
                if (switchCloud.isChecked != cloudEnabled) {
                    switchCloud.isChecked = cloudEnabled
                }
                val contextStripped = config.stripCursorContext && config.maskHostPackage
                if (switchContext.isChecked != contextStripped) {
                    switchContext.isChecked = contextStripped
                }
                if (switchClipboard.isChecked != config.clipboardPrivacy) {
                    switchClipboard.isChecked = config.clipboardPrivacy
                }
            } finally {
                isBinding = false
            }
        }

        fun isBindingState(): Boolean = isBinding
    }

    fun create(
        context: Context,
        target: ImeTarget,
        registry: HookRegistry?,
        initialConfig: PrivacyConfig,
        onConfigUpdated: (PrivacyConfig) -> Unit,
        onDismiss: () -> Unit
    ): PanelHandle {
        val isDark = isNightMode(context)
        val palette = resolvePalette(context, isDark)
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f

        fun dp(value: Float): Float = value * density
        fun dpInt(value: Float): Int = (value * density).roundToInt()

        val root = FrameLayout(context).apply {
            tag = PANEL_TAG
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            elevation = dp(16f)
            setBackgroundColor(Color.argb(0x6E, 0, 0, 0))
            setPadding(dpInt(16f), dpInt(16f), dpInt(16f), dpInt(16f))
            setOnClickListener { onDismiss() }
        }

        val screenWidth = context.resources.displayMetrics.widthPixels
        val maxCardWidth = dpInt(340f)
        val availableWidth = (screenWidth - dpInt(32f)).coerceAtLeast(dpInt(220f))
        val cardWidth = minOf(maxCardWidth, availableWidth)

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            elevation = dp(12f)
            setPadding(dpInt(16f), dpInt(14f), dpInt(16f), dpInt(14f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(22f)
                setColor(palette.cardBg)
                setStroke(dpInt(1f).coerceAtLeast(1), palette.cardStroke)
            }
            setOnClickListener { /* Consume clicks inside card so backdrop does not dismiss */ }
        }

        // Header row: IME name + close button
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = false
        }
        val titleCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(context).apply {
            text = target.displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            isFocusable = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val brandView = TextView(context).apply {
            text = "米输净盾 · 快捷隐私面板"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(palette.textSecondary)
            isFocusable = false
        }
        titleCol.addView(titleView)
        titleCol.addView(brandView)

        val closeBtn = TextView(context).apply {
            text = "✕"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(palette.textSecondary)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            val size = dpInt(30f)
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.rowBg)
            }
            setOnClickListener { onDismiss() }
        }
        headerRow.addView(titleCol)
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // Status badge
        val badgeView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setTypeface(typeface, Typeface.BOLD)
            isFocusable = false
            setPadding(dpInt(10f), dpInt(5f), dpInt(10f), dpInt(5f))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(10f)
            }
        }
        card.addView(badgeView)

        // Session blocked count
        val blockedCountView = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(palette.textSecondary)
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(6f)
                bottomMargin = dpInt(10f)
            }
        }
        card.addView(blockedCountView)

        // Scrollable toggle container so short landscape IME windows never clip controls
        val scrollView = ScrollView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val togglesCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = false
        }

        lateinit var handle: PanelHandle

        fun makeSwitchRow(
            title: String,
            subtitle: String,
            onToggle: (Boolean) -> Unit
        ): Switch {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = false
                isFocusableInTouchMode = false
                isClickable = true
                setPadding(dpInt(12f), dpInt(9f), dpInt(10f), dpInt(9f))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14f)
                    setColor(palette.rowBg)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpInt(7f)
                }
            }

            val labels = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dpInt(8f)
                }
            }
            val titleTv = TextView(context).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                setTextColor(palette.textPrimary)
                isFocusable = false
            }
            val subTv = TextView(context).apply {
                text = subtitle
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(palette.textSecondary)
                isFocusable = false
            }
            labels.addView(titleTv)
            labels.addView(subTv)

            val toggle = createStyledSwitch(context, palette)
            toggle.setOnCheckedChangeListener { _, isChecked ->
                if (!handle.isBindingState()) {
                    onToggle(isChecked)
                }
            }
            row.setOnClickListener {
                toggle.isChecked = !toggle.isChecked
            }

            row.addView(labels)
            row.addView(toggle)
            togglesCol.addView(row)
            return toggle
        }

        val switchManaged = makeSwitchRow(
            title = "接管此输入法",
            subtitle = "关闭后保留悬浮胶囊但暂停隐私拦截"
        ) { enabled ->
            val updated = ConfigBridge.persistConfigUpdateFromIme(context) { cfg ->
                cfg.withImeManaged(target, enabled)
            }
            onConfigUpdated(updated)
        }

        val switchCloud = makeSwitchRow(
            title = "允许云输入 (自动脱敏)",
            subtitle = "关闭即切换纯本地词库输入"
        ) { enabled ->
            val updated = ConfigBridge.persistConfigUpdateFromIme(context) { cfg ->
                QuickPillStateMachine.setEffectiveCloudEnabledForTarget(target, cfg, enabled)
            }
            onConfigUpdated(updated)
        }

        val switchContext = makeSwitchRow(
            title = "剥离光标上下文与宿主包名",
            subtitle = "移除前后文对话缓存并隐藏正在使用的应用"
        ) { enabled ->
            val updated = ConfigBridge.persistConfigUpdateFromIme(context) { cfg ->
                cfg.copy(stripCursorContext = enabled, maskHostPackage = enabled)
            }
            onConfigUpdated(updated)
        }

        val switchClipboard = makeSwitchRow(
            title = "剪贴板隐私保护",
            subtitle = "拦截非主动粘贴的后台剪贴板读取与落盘"
        ) { enabled ->
            val updated = ConfigBridge.persistConfigUpdateFromIme(context) { cfg ->
                cfg.copy(clipboardPrivacy = enabled)
            }
            onConfigUpdated(updated)
        }

        scrollView.addView(togglesCol)
        card.addView(scrollView)

        // Action buttons row
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(6f)
            }
        }

        val resetBtn = TextView(context).apply {
            text = "重置位置"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(palette.textPrimary)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            setPadding(dpInt(12f), dpInt(10f), dpInt(12f), dpInt(10f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f)
                setColor(palette.rowBg)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpInt(8f)
            }
            setOnClickListener {
                val updated = ConfigBridge.persistConfigUpdateFromIme(context) { cfg ->
                    cfg.withPillOffset(target, PillOffset())
                }
                onConfigUpdated(updated)
            }
        }

        val openMainBtn = TextView(context).apply {
            text = "快捷进入模块主页 ↗"
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.accentTextOnFill)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            setPadding(dpInt(12f), dpInt(10f), dpInt(12f), dpInt(10f))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(12f)
                setColor(palette.accentColor)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f)
            setOnClickListener {
                runCatching {
                    val intent = Intent(ConfigContract.ACTION_OPEN_SETTINGS).apply {
                        setClassName(ConfigContract.MODULE_PACKAGE, ConfigContract.MAIN_ACTIVITY_CLASS_NAME)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }
                    context.startActivity(intent)
                }.onFailure { err ->
                    registry?.logWarn("Failed to launch module MainActivity from quick panel", err)
                }
                onDismiss()
            }
        }

        actionsRow.addView(resetBtn)
        actionsRow.addView(openMainBtn)
        card.addView(actionsRow)

        root.addView(
            card,
            FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        )

        handle = PanelHandle(
            rootView = root,
            badgeView = badgeView,
            blockedCountView = blockedCountView,
            switchManaged = switchManaged,
            switchCloud = switchCloud,
            switchContext = switchContext,
            switchClipboard = switchClipboard,
            target = target
        )

        val snapshot = registry?.snapshotDiagnostics(
            activeCloudEnabled = initialConfig.effectiveCloudEnabled(target)
        ) ?: ImeRuntimeDiagnostics(
            target = target,
            activeCloudEnabled = initialConfig.effectiveCloudEnabled(target)
        )
        handle.bind(initialConfig, snapshot, isDark)
        return handle
    }

    fun isNightMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun createStyledSwitch(context: Context, palette: PanelPalette): Switch {
        return Switch(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            showText = false
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            thumbTintList = ColorStateList(
                states,
                intArrayOf(palette.switchThumbOn, palette.switchThumbOff)
            )
            trackTintList = ColorStateList(
                states,
                intArrayOf(palette.accentColor, palette.switchTrackOff)
            )
        }
    }

    private fun styleBadge(
        badge: TextView,
        state: QuickPillStateMachine.PillVisualState,
        isDark: Boolean,
        context: Context
    ) {
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val (bgColor, textColor) = when (state) {
            QuickPillStateMachine.PillVisualState.CLOUD_ENABLED -> {
                if (isDark) 0x3322C55E to 0xFF86EFAC.toInt() else 0x1A16A34A to 0xFF15803D.toInt()
            }
            QuickPillStateMachine.PillVisualState.LOCAL_ONLY -> {
                if (isDark) 0x3338BDF8 to 0xFF93C5FD.toInt() else 0x1A0D84FF to 0xFF0066CC.toInt()
            }
            QuickPillStateMachine.PillVisualState.UNMANAGED_OR_DEGRADED -> {
                if (isDark) 0x33EF4444 to 0xFFFCA5A5.toInt() else 0x1ADC2626 to 0xFFB91C1C.toInt()
            }
        }
        badge.setTextColor(textColor)
        badge.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 999f * density
            setColor(bgColor)
        }
    }

    private data class PanelPalette(
        val cardBg: Int,
        val cardStroke: Int,
        val rowBg: Int,
        val textPrimary: Int,
        val textSecondary: Int,
        val accentColor: Int,
        val accentTextOnFill: Int,
        val switchThumbOn: Int,
        val switchThumbOff: Int,
        val switchTrackOff: Int
    )

    private fun resolvePalette(context: Context, isDark: Boolean): PanelPalette {
        val dynamicAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.getColor(
                    if (isDark) android.R.color.system_accent1_300 else android.R.color.system_accent1_600
                )
            }.getOrNull()
        } else {
            null
        }
        val accent = dynamicAccent ?: if (isDark) 0xFF4CA3FF.toInt() else 0xFF0D84FF.toInt()
        val accentText = if (isDark && dynamicAccent != null) 0xFF0E1726.toInt() else Color.WHITE

        return if (isDark) {
            PanelPalette(
                cardBg = 0xF51D2025.toInt(),
                cardStroke = 0x26FFFFFF,
                rowBg = 0xFF2A2E35.toInt(),
                textPrimary = 0xFFF2F4F8.toInt(),
                textSecondary = 0xFFA2AAB8.toInt(),
                accentColor = accent,
                accentTextOnFill = accentText,
                switchThumbOn = Color.WHITE,
                switchThumbOff = 0xFFD0D5DD.toInt(),
                switchTrackOff = 0xFF474D57.toInt()
            )
        } else {
            PanelPalette(
                cardBg = 0xFAFFFFFF.toInt(),
                cardStroke = 0x1A000000,
                rowBg = 0xFFF3F5F9.toInt(),
                textPrimary = 0xFF14161A.toInt(),
                textSecondary = 0xFF5F6775.toInt(),
                accentColor = accent,
                accentTextOnFill = accentText,
                switchThumbOn = Color.WHITE,
                switchThumbOff = Color.WHITE,
                switchTrackOff = 0xFFC8CFD9.toInt()
            )
        }
    }
}
