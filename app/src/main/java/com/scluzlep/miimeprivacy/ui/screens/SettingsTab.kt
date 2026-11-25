package com.scluzlep.miimeprivacy.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scluzlep.miimeprivacy.BuildConfig
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.config.ThemeMode
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import com.scluzlep.miimeprivacy.ui.theme.paletteStyleDescription
import com.scluzlep.miimeprivacy.ui.theme.paletteStyleLabel
import com.scluzlep.miimeprivacy.ui.theme.themeModeLabel
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
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
    val context = LocalContext.current

    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf("") }

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

        item(key = "dexkit_cache_title") {
            SmallTitle(
                text = "DexKit 符号缓存",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "dexkit_cache_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.imeCards.forEach { cardModel ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MiInnerShape,
                            color = colors.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = cardModel.target.displayName,
                                        style = MiuixTheme.textStyles.body2,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.onSurfaceContainer
                                    )
                                    Text(
                                        text = cardModel.cardResolveSummaryText,
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = colors.onSurfaceVariantSummary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val nextMode = if (cardModel.resolveMode == SymbolResolveMode.DYNAMIC) {
                                        SymbolResolveMode.AUTO
                                    } else {
                                        SymbolResolveMode.DYNAMIC
                                    }
                                    TextButton(
                                        text = if (cardModel.resolveMode == SymbolResolveMode.DYNAMIC) "切自动" else "切动态",
                                        onClick = { controller.setImeResolveMode(cardModel.target, nextMode) },
                                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                    TextButton(
                                        text = "清缓存",
                                        enabled = cardModel.canClearSymbolCache,
                                        onClick = { controller.clearImeSymbolCache(cardModel.target) },
                                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = controller::clearAllSymbolCaches,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = statusColors.warningContainer,
                            contentColor = statusColors.onWarningContainer
                        )
                    ) {
                        Text(
                            text = "清除全部 DexKit 符号缓存",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColors.onWarningContainer
                        )
                    }
                }
            }
        }

        item(key = "backup_title") {
            SmallTitle(
                text = "配置备份与迁移",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "backup_card") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "配置格式 v${cfg.configVersion}（含 ${cfg.ruleOverrides.size} 项 rule_overrides 覆盖，支持导入 v1.x/v2.0 JSON）",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val exported = controller.exportConfigJson()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("miimeprivacy_config", exported))
                                controller.notifyFeedback("已复制 v${cfg.configVersion} 配置 JSON 到剪贴板")
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                color = colors.secondaryContainer,
                                contentColor = colors.onSecondaryContainer
                            )
                        ) {
                            Text(
                                text = "导出 JSON",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSecondaryContainer
                            )
                        }
                        Button(
                            onClick = {
                                importError = ""
                                importText = ""
                                showImportDialog = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary()
                        ) {
                            Text(
                                text = "导入 JSON",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onPrimary
                            )
                        }
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
                    summary = "Mi IMEPlus"
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

    if (showImportDialog) {
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(CardDefaults.CornerRadius),
                color = colors.surfaceContainer,
                border = BorderStroke(0.5.dp, colors.dividerLine)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "导入配置 JSON",
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceContainer
                    )
                    Text(
                        text = "粘贴导出的配置 JSON（含 config_version 与 rule_overrides）",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 110.dp, max = 220.dp),
                        shape = MiInnerShape,
                        color = colors.surfaceVariant,
                        border = BorderStroke(0.5.dp, colors.dividerLine)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            if (importText.isEmpty()) {
                                Text(
                                    text = "{\"config_version\": 2, \"rule_overrides\": {...}}",
                                    style = MiuixTheme.textStyles.footnote1,
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.onSurfaceVariantSummary
                                )
                            }
                            BasicTextField(
                                value = importText,
                                onValueChange = {
                                    importText = it
                                    importError = ""
                                },
                                textStyle = MiuixTheme.textStyles.footnote1.copy(
                                    fontFamily = FontFamily.Monospace,
                                    color = colors.onSurfaceContainer
                                ),
                                cursorBrush = SolidColor(colors.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (importError.isNotBlank()) {
                        Text(
                            text = importError,
                            style = MiuixTheme.textStyles.footnote1,
                            color = statusColors.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            text = "从剪贴板粘贴",
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                                if (clipText.isBlank()) {
                                    importError = "剪贴板为空"
                                } else {
                                    importText = clipText
                                    importError = ""
                                }
                            },
                            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        )
                        Box(modifier = Modifier.weight(1f))
                        TextButton(
                            text = "取消",
                            onClick = { showImportDialog = false },
                            insideMargin = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    Button(
                        onClick = {
                            if (controller.importConfigJson(importText)) {
                                showImportDialog = false
                            } else {
                                importError = "JSON 格式无效，请检查后重试"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(
                            text = "确认导入",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onPrimary
                        )
                    }
                }
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
