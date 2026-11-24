package com.scluzlep.miimeprivacy.ui.screens

import android.os.Build
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scluzlep.miimeprivacy.BuildConfig
import com.scluzlep.miimeprivacy.config.ConfigContract
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import com.scluzlep.miimeprivacy.ui.theme.paletteStyleDescription
import com.scluzlep.miimeprivacy.ui.theme.paletteStyleLabel
import com.scluzlep.miimeprivacy.ui.theme.themeModeLabel
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsTab(
    state: PrivacyUiState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    controller: PrivacyUiController
) {
    val cfg = state.config
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val supportsSystemMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

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
        item(key = "theme_title") {
            SmallTitle(
                text = "外观与莫奈动态取色",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "theme_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "主题模式",
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceContainer
                    )
                    val modes = ThemeMode.entries
                    MiSegmentedControl(
                        options = modes.map { themeModeLabel(it) },
                        selectedIndex = modes.indexOf(cfg.themeMode).coerceAtLeast(0),
                        onSelected = { idx -> controller.setThemeMode(modes[idx]) }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )

                MiSwitchItem(
                    title = "莫奈动态取色 (Android 12+)",
                    summary = if (supportsSystemMonet) {
                        "开启后随系统壁纸动态生成 Miuix & Material3 配色方案"
                    } else {
                        "当前系统低于 Android 12，将使用 Miuix 动态种子回退配色"
                    },
                    checked = cfg.monetEnabled,
                    onCheckedChange = controller::setMonetEnabled,
                    showDivider = true
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "调色板风格 (MonetPaletteStyle)",
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceContainer
                        )
                        MiStatusBadge(
                            text = paletteStyleLabel(cfg.paletteStyle),
                            containerColor = if (cfg.monetEnabled) colors.primaryContainer else statusColors.neutralContainer,
                            contentColor = if (cfg.monetEnabled) colors.onPrimaryContainer else statusColors.onNeutralContainer
                        )
                    }

                    if (!cfg.monetEnabled) {
                        Text(
                            text = "当前已关闭莫奈取色，界面固定使用 HyperOS #007DFF 经典蓝主题；开启莫奈后以下风格立即生效。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = colors.onSurfaceVariantSummary
                        )
                    }

                    val styles = MonetPaletteStyle.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        styles.chunked(3).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { style ->
                                    val selected = cfg.paletteStyle == style
                                    Surface(
                                        onClick = { controller.setPaletteStyle(style) },
                                        modifier = Modifier.weight(1f),
                                        shape = MiInnerShape,
                                        color = if (selected) colors.primaryContainer else colors.surfaceVariant,
                                        border = if (selected) {
                                            BorderStroke(1.dp, colors.primary)
                                        } else {
                                            BorderStroke(0.5.dp, colors.dividerLine)
                                        }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = paletteStyleLabel(style),
                                                style = MiuixTheme.textStyles.footnote1,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = style.name,
                                                style = MiuixTheme.textStyles.footnote2,
                                                color = if (selected) {
                                                    colors.onPrimaryContainer.copy(alpha = 0.8f)
                                                } else {
                                                    colors.onSurfaceVariantSummary
                                                },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live color swatch bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MiInnerShape,
                        color = colors.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "当前配色实时预览：${paletteStyleDescription(cfg.paletteStyle)}",
                                style = MiuixTheme.textStyles.footnote1,
                                color = colors.onSurfaceVariantSummary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ColorSwatchItem("主色", colors.primary)
                                ColorSwatchItem("主容器", colors.primaryContainer)
                                ColorSwatchItem("次色", colors.secondary)
                                ColorSwatchItem("第三容器", colors.tertiaryContainer)
                                ColorSwatchItem("卡片底", colors.surfaceContainerHigh)
                            }
                        }
                    }
                }
            }
        }

        item(key = "about_title") {
            SmallTitle(
                text = "关于与安全边界",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "about_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "米输净盾 (MiImePrivacy)",
                    summary = "包名 ${ConfigContract.MODULE_PACKAGE} · 版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "Xposed 与签名校验规范",
                    summary = "基于 libxposed API 102（staticScope + autoHotReload + RemotePreferences），发布构建经 android-sig PKCS#12 签名与 SHA256SUMS 校验"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "已审计目标输入法基准版本",
                    summary = "搜狗输入法小米版 20160 · 百度输入法小米版 7108 · 超级小爱输入法 21363（版本变化时通过 DexKit 缓存与结构锚点自适应匹配）"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "透明与克制原则",
                    summary = "不破解任何付费特权或会员状态、不读取或导出明文密钥；任意关键 Hook 未匹配时诚实降级并在面板醒目告警"
                )
            }
        }
    }
}

@Composable
private fun ColorSwatchItem(label: String, swatchColor: Color) {
    val colors = MiuixTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(swatchColor)
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            fontFamily = FontFamily.Default,
            color = colors.onSurfaceVariantSummary
        )
    }
}
