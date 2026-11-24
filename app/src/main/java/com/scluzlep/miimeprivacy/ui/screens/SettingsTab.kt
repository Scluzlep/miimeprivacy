package com.scluzlep.miimeprivacy.ui.screens

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scluzlep.miimeprivacy.BuildConfig
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

private const val GITHUB_REPO_URL = "https://github.com/Scluzlep/miimeprivacy"

@Composable
fun SettingsTab(
    state: PrivacyUiState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    controller: PrivacyUiController
) {
    val cfg = state.config
    val svc = state.serviceState
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val supportsSystemMonet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val uriHandler = LocalUriHandler.current

    val lsposedVersionText = if (svc.isServiceBound) {
        val fwName = svc.frameworkName.ifBlank { "LSPosed" }
        val fwVer = svc.frameworkVersion.trim()
        val base = if (fwVer.isNotBlank()) "$fwName $fwVer" else fwName
        if (svc.apiVersion > 0) "$base (API ${svc.apiVersion})" else base
    } else {
        "未连接"
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
        item(key = "theme_title") {
            SmallTitle(
                text = "外观",
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
                    title = "莫奈取色",
                    summary = if (supportsSystemMonet) {
                        "随系统壁纸自动取色"
                    } else {
                        "系统低于 Android 12，使用默认取色"
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
                            text = "配色风格",
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
                            text = "已关闭莫奈取色，当前使用默认蓝主题，开启后生效",
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
                                text = "配色预览 · ${paletteStyleDescription(cfg.paletteStyle)}",
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
                                ColorSwatchItem("辅色", colors.secondary)
                                ColorSwatchItem("强调色", colors.tertiaryContainer)
                                ColorSwatchItem("卡片", colors.surfaceContainerHigh)
                            }
                        }
                    }
                }
            }
        }

        item(key = "about_title") {
            SmallTitle(
                text = "关于",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "about_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                BasicComponent(
                    title = "软件名称",
                    summary = "米输净盾"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "软件版本",
                    summary = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "模块开发者",
                    summary = "Scluzlep"
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "GitHub",
                    summary = GITHUB_REPO_URL,
                    onClick = {
                        runCatching { uriHandler.openUri(GITHUB_REPO_URL) }
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                BasicComponent(
                    title = "LSPosed 版本",
                    summary = lsposedVersionText
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
