package com.scluzlep.miimeprivacy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.scluzlep.miimeprivacy.config.CloudOverride
import com.scluzlep.miimeprivacy.config.SymbolResolveMode
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import com.scluzlep.miimeprivacy.ui.viewmodel.ImeCardUiModel
import com.scluzlep.miimeprivacy.ui.viewmodel.PillVisualState
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleHealthSeverity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OverviewTab(
    state: PrivacyUiState,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    onRequestScope: () -> Unit,
    onHotReload: () -> Unit,
    onToggleAllowCloud: (Boolean) -> Unit,
    onToggleImeManaged: (ImeTarget, Boolean) -> Unit,
    onChangeImeCloudOverride: (ImeTarget, CloudOverride) -> Unit,
    onChangeImeResolveMode: (ImeTarget, SymbolResolveMode) -> Unit,
    onClearImeSymbolCache: (ImeTarget) -> Unit,
    onOpenImeDiagnostics: (ImeTarget) -> Unit,
    onClearFeedback: () -> Unit = {}
) {
    var configuringTarget by remember { mutableStateOf<ImeTarget?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding()
            )
            .clipToBounds()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 20.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "module_status_title") {
            SmallTitle(
                text = "模块状态",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "module_status_card") {
            ModuleStatusCard(
                state = state,
                showActionFeedback = configuringTarget == null,
                onRequestScope = onRequestScope,
                onHotReload = onHotReload
            )
        }

        item(key = "global_cloud_title") {
            SmallTitle(
                text = "云输入",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "global_cloud_card") {
            GlobalCloudCard(
                allowCloudInput = state.config.allowCloudInput,
                totalBlockedCount = state.totalBlockedCount,
                onToggleAllowCloud = onToggleAllowCloud
            )
        }

        item(key = "ime_targets_title") {
            SmallTitle(
                text = "输入法",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        items(
            items = state.imeCards,
            key = { it.target.packageName }
        ) { cardModel ->
            ImeTargetCard(
                model = cardModel,
                onToggleManaged = { managed -> onToggleImeManaged(cardModel.target, managed) },
                onSelectCloudOverride = { override ->
                    onChangeImeCloudOverride(cardModel.target, override)
                },
                onOpenSymbolConfig = {
                    onClearFeedback()
                    configuringTarget = cardModel.target
                },
                onInspectDiagnostics = { onOpenImeDiagnostics(cardModel.target) }
            )
        }
    }

    val dialogModel = configuringTarget?.let { target ->
        state.imeCards.firstOrNull { it.target == target }
    }
    if (dialogModel != null) {
        ImeSymbolConfigDialog(
            model = dialogModel,
            feedbackText = state.actionFeedback,
            onChangeMode = { mode -> onChangeImeResolveMode(dialogModel.target, mode) },
            onClearCache = { onClearImeSymbolCache(dialogModel.target) },
            onDismiss = {
                configuringTarget = null
                onClearFeedback()
            }
        )
    }
}

@Composable
private fun ModuleStatusCard(
    state: PrivacyUiState,
    showActionFeedback: Boolean,
    onRequestScope: () -> Unit,
    onHotReload: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val svc = state.serviceState
    val bound = svc.isServiceBound
    val apiLabel = if (bound && svc.apiVersion > 0) "${svc.apiVersion}" else "102"
    val frameworkDesc = if (bound) {
        "${svc.frameworkName.ifBlank { "LSPosed" }} ${svc.frameworkVersion}".trim()
    } else {
        "请在 LSPosed 管理器中启用模块"
    }
    val remotePrefsStatus = if (bound) {
        val stamp = state.config.updatedAtMs
        if (stamp > 0L) {
            "已同步 · ${formatTimestamp(stamp)}"
        } else {
            "实时同步"
        }
    } else {
        "本地模式"
    }
    val cardFeedback = if (showActionFeedback) state.actionFeedback else ""
    val feedbackText = cardFeedback.ifBlank {
        svc.hotReloadStatusMessage.ifBlank { svc.scopeRequestMessage }
    }
    val installedTargets = state.imeCards.filter { it.installedInfo.installed }
    val needsScopeRequest = installedTargets.any { !it.isInScope }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (bound) "模块已激活" else "模块未激活",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = frameworkDesc,
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary
                )
            }
            MiStatusBadge(
                text = if (bound) "API $apiLabel" else "未激活",
                containerColor = if (bound) statusColors.okContainer else statusColors.warningContainer,
                contentColor = if (bound) statusColors.onOkContainer else statusColors.onWarningContainer,
                dotColor = if (bound) statusColors.ok else statusColors.warning
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.dividerLine)
        Spacer(modifier = Modifier.height(12.dp))

        KeyValueRow(label = "配置同步", value = remotePrefsStatus)
        Spacer(modifier = Modifier.height(6.dp))
        KeyValueRow(
            label = "作用域",
            value = "${state.scopeCoveredCount} / ${state.totalTargetCount} 款"
        )

        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MiInnerShape,
            color = colors.surfaceVariant
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "运行进程 · ${state.runningTargets.size}",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                if (state.runningTargets.isEmpty()) {
                    Text(
                        text = "暂无运行进程",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary
                    )
                } else {
                    state.runningTargets.forEach { proc ->
                        Text(
                            text = "${proc.processName} · PID ${proc.pid} · ${proc.stateLabel} · v${proc.loadedVersionCode}",
                            style = MiuixTheme.textStyles.footnote1,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceContainerVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (needsScopeRequest) {
                Button(
                    onClick = onRequestScope,
                    enabled = !state.isScopeRequesting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = if (state.isScopeRequesting) "申请中…" else "申请作用域",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = onHotReload,
                enabled = !state.isHotReloading,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(
                    text = if (state.isHotReloading) "重载中…" else "热重载模块",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (feedbackText.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MiInnerShape,
                color = statusColors.infoContainer
            ) {
                Text(
                    text = feedbackText,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = statusColors.onInfoContainer
                )
            }
        }
    }
}

@Composable
private fun GlobalCloudCard(
    allowCloudInput: Boolean,
    totalBlockedCount: Long,
    onToggleAllowCloud: (Boolean) -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = "允许云输入",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "关闭后三款输入法均仅用本地词库",
                    style = MiuixTheme.textStyles.body2,
                    color = colors.onSurfaceVariantSummary
                )
            }
            Switch(
                checked = allowCloudInput,
                onCheckedChange = onToggleAllowCloud
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MiInnerShape,
            color = if (allowCloudInput) statusColors.okContainer else statusColors.infoContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (allowCloudInput) "🟢 云输入已开启" else "🔒 纯本地模式",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = if (allowCloudInput) statusColors.onOkContainer else statusColors.onInfoContainer,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(4.dp))
                Text(
                    text = "拦截 $totalBlockedCount 次",
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Bold,
                    color = if (allowCloudInput) statusColors.onOkContainer else statusColors.onInfoContainer
                )
            }
        }
    }
}

@Composable
private fun ImeTargetCard(
    model: ImeCardUiModel,
    onToggleManaged: (Boolean) -> Unit,
    onSelectCloudOverride: (CloudOverride) -> Unit,
    onOpenSymbolConfig: () -> Unit,
    onInspectDiagnostics: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val summary = model.healthSummary

    val (summaryBg, summaryFg, summaryDot) = when (summary.severity) {
        RuleHealthSeverity.CRITICAL_ERROR -> Triple(
            statusColors.errorContainer,
            statusColors.onErrorContainer,
            statusColors.error
        )
        RuleHealthSeverity.WARNING -> Triple(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            statusColors.warning
        )
        RuleHealthSeverity.FALLBACK_OK -> Triple(
            statusColors.infoContainer,
            statusColors.onInfoContainer,
            statusColors.info
        )
        RuleHealthSeverity.HEALTHY -> Triple(
            statusColors.okContainer,
            statusColors.onOkContainer,
            statusColors.ok
        )
        RuleHealthSeverity.NEUTRAL -> Triple(
            statusColors.neutralContainer,
            statusColors.onNeutralContainer,
            statusColors.neutral
        )
    }

    val pillBadgeColors = when {
        !model.managed -> statusColors.errorContainer to statusColors.onErrorContainer
        model.pillState == PillVisualState.CLOUD_MANAGED -> statusColors.okContainer to statusColors.onOkContainer
        else -> statusColors.infoContainer to statusColors.onInfoContainer
    }

    val cardBorder = if (summary.severity == RuleHealthSeverity.CRITICAL_ERROR) {
        BorderStroke(1.dp, statusColors.error.copy(alpha = 0.55f))
    } else null

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardDefaults.CornerRadius),
        color = colors.surfaceContainer,
        border = cardBorder
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = model.target.displayName,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceContainer
                        )
                        MiStatusBadge(
                            text = "${model.pillState.emoji} ${model.pillState.label}",
                            containerColor = pillBadgeColors.first,
                            contentColor = pillBadgeColors.second
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (model.installedInfo.installed) {
                            "${model.installedInfo.formattedVersion()} · 基准 ${model.target.expectedVersionCode}"
                        } else {
                            "未安装"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary
                    )
                    if (summary.versionMismatch) {
                        Text(
                            text = "版本与基准不同，已启用适配解析",
                            style = MiuixTheme.textStyles.footnote2,
                            color = statusColors.warning
                        )
                    }
                }
                Switch(
                    checked = model.managed,
                    onCheckedChange = onToggleManaged
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiStatusBadge(
                    text = if (model.isInScope) "作用域已开" else "未在作用域",
                    containerColor = if (model.isInScope) statusColors.neutralContainer else statusColors.warningContainer,
                    contentColor = if (model.isInScope) statusColors.onNeutralContainer else statusColors.onWarningContainer
                )
                if (model.runningProcesses.isNotEmpty()) {
                    MiStatusBadge(
                        text = "PID ${model.runningProcesses.first().pid}",
                        containerColor = statusColors.okContainer,
                        contentColor = statusColors.onOkContainer,
                        dotColor = statusColors.ok
                    )
                }
                if (summary.blockedCount > 0L) {
                    MiStatusBadge(
                        text = "拦截 ${summary.blockedCount}",
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "云输入策略",
                style = MiuixTheme.textStyles.footnote1,
                color = colors.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(6.dp))
            val policyOptions = listOf("跟随全局", "开启", "纯本地")
            val selectedPolicyIdx = when (model.cloudOverride) {
                CloudOverride.FOLLOW_GLOBAL -> 0
                CloudOverride.FORCE_ON -> 1
                CloudOverride.FORCE_OFF -> 2
            }
            MiSegmentedControl(
                options = policyOptions,
                selectedIndex = selectedPolicyIdx,
                enabled = model.managed,
                onSelected = { idx ->
                    val next = when (idx) {
                        0 -> CloudOverride.FOLLOW_GLOBAL
                        1 -> CloudOverride.FORCE_ON
                        else -> CloudOverride.FORCE_OFF
                    }
                    onSelectCloudOverride(next)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                onClick = onOpenSymbolConfig,
                modifier = Modifier.fillMaxWidth(),
                shape = MiInnerShape,
                color = colors.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "符号解析",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "${model.cardResolveSummaryText}  设置 ›",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                onClick = onInspectDiagnostics,
                modifier = Modifier.fillMaxWidth(),
                shape = MiInnerShape,
                color = summaryBg
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiStatusBadge(
                        text = summary.headline,
                        containerColor = summaryBg,
                        contentColor = summaryFg,
                        dotColor = summaryDot,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "查看规则 ↗",
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.SemiBold,
                        color = summaryFg
                    )
                }
            }
        }
    }
}

@Composable
private fun ImeSymbolConfigDialog(
    model: ImeCardUiModel,
    feedbackText: String,
    onChangeMode: (SymbolResolveMode) -> Unit,
    onClearCache: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val modeOptions = listOf("自动 FastPath", "动态 DexKit")
    val selectedModeIdx = if (model.resolveMode == SymbolResolveMode.DYNAMIC) 1 else 0

    Dialog(onDismissRequest = onDismiss) {
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
                    text = "${model.target.displayName} · 符号解析",
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MiInnerShape,
                    color = colors.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        KeyValueRow(
                            label = "当前解析方式",
                            value = model.activeResolveMethodText
                        )
                        KeyValueRow(
                            label = "符号缓存",
                            value = if (model.cachedSymbolCount > 0) {
                                "${model.cachedSymbolCount} 项"
                            } else {
                                "无缓存"
                            }
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "解析模式",
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary
                    )
                    MiSegmentedControl(
                        options = modeOptions,
                        selectedIndex = selectedModeIdx,
                        onSelected = { idx ->
                            val next = if (idx == 1) SymbolResolveMode.DYNAMIC else SymbolResolveMode.AUTO
                            onChangeMode(next)
                        }
                    )
                }

                if (feedbackText.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MiInnerShape,
                        color = statusColors.infoContainer
                    ) {
                        Text(
                            text = feedbackText,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MiuixTheme.textStyles.footnote1,
                            color = statusColors.onInfoContainer
                        )
                    }
                }

                if (model.canClearSymbolCache) {
                    Button(
                        onClick = onClearCache,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            color = statusColors.warningContainer,
                            contentColor = statusColors.onWarningContainer
                        )
                    ) {
                        Text(
                            text = "清除 DexKit 符号缓存",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = statusColors.onWarningContainer
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        color = colors.secondaryContainer,
                        contentColor = colors.onSecondaryContainer
                    )
                ) {
                    Text(
                        text = "关闭",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyValueRow(label: String, value: String) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = colors.onSurfaceVariantSummary
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceContainer
        )
    }
}

private fun formatTimestamp(ms: Long): String {
    return runCatching {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
    }.getOrDefault(ms.toString())
}
