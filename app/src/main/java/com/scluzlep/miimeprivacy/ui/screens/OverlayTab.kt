package com.scluzlep.miimeprivacy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scluzlep.miimeprivacy.config.PillOffset
import com.scluzlep.miimeprivacy.config.PrivacyConfig
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiPillShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun OverlayTab(
    state: PrivacyUiState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    controller: PrivacyUiController
) {
    val cfg = state.config
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors

    var draftAlpha by remember { mutableFloatStateOf(cfg.pillAlpha) }
    LaunchedEffect(cfg.pillAlpha) {
        draftAlpha = cfg.pillAlpha
    }

    // Preview pill mode: 0 = 🟢 云输入, 1 = 🔒 纯本地, 2 = 🔴 未接管/异常
    var previewMode by remember(cfg.allowCloudInput) {
        mutableIntStateOf(if (cfg.allowCloudInput) 0 else 1)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "pill_preview_title") {
            SmallTitle(
                text = "键盘微胶囊实时预览与 Option A 交互",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "pill_preview_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = "键盘顶部微胶囊示意图（点击状态标签可预览三种呈现态）",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Simulated IME keyboard top toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MiInnerShape,
                    color = colors.surfaceVariant,
                    border = BorderStroke(0.5.dp, colors.dividerLine)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .background(colors.surface, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.align(Alignment.CenterEnd),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "语音  •  表情  •  设置",
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = colors.onSurfaceVariantSummary
                                )
                            }

                            if (cfg.showKeyboardPill) {
                                val emoji = when (previewMode) {
                                    0 -> "🟢"
                                    1 -> "🔒"
                                    else -> "🔴"
                                }
                                Surface(
                                    onClick = {
                                        previewMode = when (previewMode) {
                                            0 -> 1
                                            1 -> 0
                                            else -> 2
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(top = 4.dp)
                                        .alpha(draftAlpha),
                                    shape = MiPillShape,
                                    color = colors.surfaceContainerHigh,
                                    border = BorderStroke(0.75.dp, colors.outline.copy(alpha = 0.5f))
                                ) {
                                    Box(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = emoji, fontSize = 13.sp)
                                    }
                                }
                            } else {
                                Text(
                                    text = "微胶囊已隐藏",
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.onSurfaceVariantSummary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MiSegmentedControl(
                            options = listOf("🟢 受管云输入", "🔒 受管纯本地", "🔴 未接管/异常"),
                            selectedIndex = previewMode,
                            onSelected = { previewMode = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InteractionGuideLine(
                        badge = "单击",
                        desc = "在 🟢（云输入）↔ 🔒（纯本地）间秒切当前输入法模式；点击 🔴 直接弹出快捷面板防误触"
                    )
                    InteractionGuideLine(
                        badge = "长按",
                        desc = "弹出浮动快捷面板，支持快捷切换全局/单输入法净化与「快捷进入模块主页 ↗」"
                    )
                    InteractionGuideLine(
                        badge = "拖动",
                        desc = "按住微胶囊可自由拖拽调整位置，并按三款输入法独立记录 X/Y 偏移量"
                    )
                }
            }
        }

        item(key = "pill_config_title") {
            SmallTitle(
                text = "微胶囊外观与位置调节",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "pill_config_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "显示键盘角落微胶囊",
                    summary = "关闭后隐藏悬浮微胶囊，不影响后台 Hook 净化与控制中心磁贴",
                    checked = cfg.showKeyboardPill,
                    onCheckedChange = controller::setShowKeyboardPill,
                    showDivider = true
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    val alphaPercent = (draftAlpha * 100f).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "胶囊不透明度",
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceContainer
                        )
                        MiStatusBadge(
                            text = "$alphaPercent%",
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = draftAlpha,
                        onValueChange = { draftAlpha = it },
                        onValueChangeFinished = { controller.setPillAlpha(draftAlpha) },
                        enabled = cfg.showKeyboardPill,
                        valueRange = PrivacyConfig.MIN_PILL_ALPHA..PrivacyConfig.MAX_PILL_ALPHA
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "25%",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary
                        )
                        Text(
                            text = "默认 88%",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary
                        )
                        Text(
                            text = "100%",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "各输入法偏移量（默认 ${PillOffset.DEFAULT_OFFSET_X_DP}dp, ${PillOffset.DEFAULT_OFFSET_Y_DP}dp）",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceContainer
                    )
                    ImeTarget.entries.forEach { target ->
                        val offset = cfg.pillOffsetFor(target)
                        val isDefault = offset.offsetXDp == PillOffset.DEFAULT_OFFSET_X_DP &&
                            offset.offsetYDp == PillOffset.DEFAULT_OFFSET_Y_DP
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MiInnerShape,
                            color = colors.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.displayName,
                                        style = MiuixTheme.textStyles.body2,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurfaceContainer
                                    )
                                    Text(
                                        text = "X = ${offset.offsetXDp}dp, Y = ${offset.offsetYDp}dp",
                                        style = MiuixTheme.textStyles.footnote1,
                                        fontFamily = FontFamily.Monospace,
                                        color = colors.onSurfaceVariantSummary
                                    )
                                }
                                TextButton(
                                    text = if (isDefault) "已默认" else "重置",
                                    enabled = !isDefault,
                                    onClick = { controller.resetPillOffset(target) },
                                    insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = controller::resetAllPillOffsets,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        )
                    ) {
                        Text(
                            text = "一键重置全部输入法位置 (${PillOffset.DEFAULT_OFFSET_X_DP}dp, ${PillOffset.DEFAULT_OFFSET_Y_DP}dp)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSecondaryContainer
                        )
                    }

                    if (state.actionFeedback.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MiInnerShape,
                            color = statusColors.infoContainer
                        ) {
                            Text(
                                text = state.actionFeedback,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                style = MiuixTheme.textStyles.footnote1,
                                color = statusColors.onInfoContainer
                            )
                        }
                    }
                }
            }
        }

        item(key = "qs_tile_title") {
            SmallTitle(
                text = "系统控制中心快捷磁贴",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "qs_tile_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = "添加控制中心「云输入净化」磁贴",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. 从屏幕顶部下拉打开 HyperOS 控制中心，点击右上角「编辑」按钮。\n" +
                        "2. 在下方可选快捷开关中找到「输入法云输入」（米输净盾）并拖入常用面板。\n" +
                        "3. 后续在任何场景下点按该磁贴，即可一键全局切换 🟢 云输入 / 🔒 纯本地离线，并自动同步至键盘微胶囊。",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun InteractionGuideLine(badge: String, desc: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        MiStatusBadge(
            text = badge,
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        )
        Text(
            text = desc,
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
    }
}
