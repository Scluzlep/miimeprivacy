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
                text = "预览",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "pill_preview_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = "点击切换胶囊状态",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.height(10.dp))

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
                                    text = "悬浮胶囊已隐藏",
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = colors.onSurfaceVariantSummary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MiSegmentedControl(
                            options = listOf("🟢 云输入", "🔒 纯本地", "🔴 未接管"),
                            selectedIndex = previewMode,
                            onSelected = { previewMode = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InteractionGuideLine(
                        badge = "单击",
                        desc = "切换云输入或纯本地模式，未接管时打开面板"
                    )
                    InteractionGuideLine(
                        badge = "长按",
                        desc = "打开快捷面板，调整开关或进入应用"
                    )
                    InteractionGuideLine(
                        badge = "拖动",
                        desc = "自由调整胶囊位置，各输入法独立保存"
                    )
                }
            }
        }

        item(key = "pill_config_title") {
            SmallTitle(
                text = "设置",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "pill_config_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "显示悬浮胶囊",
                    summary = "隐藏后不影响拦截功能与快捷磁贴",
                    checked = cfg.showKeyboardPill,
                    onCheckedChange = controller::setShowKeyboardPill,
                    showDivider = true
                )

                MiSwitchItem(
                    title = "横屏隐藏",
                    summary = "横屏时自动隐藏悬浮胶囊",
                    checked = cfg.hidePillInLandscape,
                    enabled = cfg.showKeyboardPill,
                    onCheckedChange = controller::setHidePillInLandscape,
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
                            text = "不透明度",
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
                        value = draftAlpha.coerceIn(PrivacyConfig.MIN_PILL_ALPHA, PrivacyConfig.MAX_PILL_ALPHA),
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
                            text = "10%",
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
                        text = "胶囊位置",
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
                                        text = "X ${offset.offsetXDp}dp · Y ${offset.offsetYDp}dp",
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
                            text = "全部重置",
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
                text = "快捷磁贴",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "qs_tile_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Text(
                    text = "控制中心开关",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. 下拉打开控制中心，点击编辑按钮。\n" +
                        "2. 将「输入法云输入」拖入快捷面板。\n" +
                        "3. 点击即可全局切换云输入或纯本地模式。",
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
        verticalAlignment = Alignment.CenterVertically
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
