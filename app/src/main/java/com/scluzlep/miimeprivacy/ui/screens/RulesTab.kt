package com.scluzlep.miimeprivacy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleDisplayItem
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleTargetFilter
import com.scluzlep.miimeprivacy.ui.viewmodel.formatSourceTokenLabel
import com.scluzlep.miimeprivacy.ui.viewmodel.parseSourceTokens
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RulesTab(
    state: PrivacyUiState,
    selectedFilter: RuleTargetFilter,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    controller: PrivacyUiController
) {
    val cfg = state.config
    val filteredRules = if (selectedFilter.target == null) {
        state.ruleItems
    } else {
        state.ruleItems.filter { it.spec.target == selectedFilter.target }
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
        item(key = "telemetry_title") {
            SmallTitle(
                text = "遥测与日志",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "telemetry_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "拦截行为遥测",
                    summary = "阻断行为统计、打点上报与后台保活",
                    checked = cfg.blockTelemetry,
                    onCheckedChange = controller::setBlockTelemetry,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截密码框统计",
                    summary = "阻止密码输入框状态统计与次数回传",
                    checked = cfg.blockPasswordStats,
                    onCheckedChange = controller::setBlockPasswordStats,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截远端日志提取",
                    summary = "阻断静默日志打包、上传与远端调试指令",
                    checked = cfg.blockRemoteLogSalvage,
                    onCheckedChange = controller::setBlockRemoteLogSalvage,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截搜狗行为记录",
                    summary = "关闭搜索词、应用切换与定位记录入库",
                    checked = cfg.blockMutualData,
                    onCheckedChange = controller::setBlockMutualData,
                    showDivider = false
                )
            }
        }

        item(key = "cloud_payload_title") {
            SmallTitle(
                text = "云输入脱敏",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "cloud_payload_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "云输入脱敏",
                    summary = "开启云输入时清除设备标识与定位字段",
                    checked = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setSanitizeCloudPayload,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "隐藏光标前后文字",
                    summary = "只发送当前输入，不上传编辑框内已有内容",
                    checked = cfg.stripCursorContext,
                    enabled = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setStripCursorContext,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "隐藏当前应用包名",
                    summary = "不向云端与 AI 接口传递前台应用信息",
                    checked = cfg.maskHostPackage,
                    enabled = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setMaskHostPackage,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "隐藏按键坐标",
                    summary = "清除输入击键坐标与滑动轨迹字段",
                    checked = cfg.stripTouchPositions,
                    enabled = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setStripTouchPositions,
                    showDivider = false
                )
            }
        }

        item(key = "privacy_strict_title") {
            SmallTitle(
                text = "权限与严格拦截",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "privacy_strict_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "剪贴板保护",
                    summary = "保留正常粘贴，拦截后台静默读取与统计",
                    checked = cfg.clipboardPrivacy,
                    onCheckedChange = controller::setClipboardPrivacy,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截通讯录与扫包",
                    summary = "禁止读取通讯录、短信记录与已安装应用",
                    checked = cfg.blockContactsAndAppScan,
                    onCheckedChange = controller::setBlockContactsAndAppScan,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截动态代码加载",
                    summary = "阻止远端下发并执行未签名脚本或 DEX",
                    checked = cfg.blockUnsignedDynamicDex,
                    onCheckedChange = controller::setBlockUnsignedDynamicDex,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截 HTTPDNS",
                    summary = "禁止搜狗与百度绕过系统 DNS 解析，默认关闭",
                    checked = cfg.blockHttpDns,
                    onCheckedChange = controller::setBlockHttpDns,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截小爱热词追踪",
                    summary = "禁止跨应用上报热词纠错前后的文本，默认关闭",
                    checked = cfg.blockXiaoAiHotwordTracker,
                    onCheckedChange = controller::setBlockXiaoAiHotwordTracker,
                    showDivider = false
                )
            }
        }

        item(key = "diag_title") {
            SmallTitle(
                text = "规则状态",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "diag_filter") {
            val filters = RuleTargetFilter.entries
            MiSegmentedControl(
                options = filters.map { it.label },
                selectedIndex = filters.indexOf(selectedFilter).coerceAtLeast(0),
                onSelected = { idx -> controller.selectRuleFilter(filters[idx]) }
            )
        }

        items(
            items = filteredRules,
            key = { it.spec.ruleId }
        ) { item ->
            HookRuleDiagnosticCard(item = item)
        }
    }
}

@Composable
private fun HookRuleDiagnosticCard(item: RuleDisplayItem) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val report = item.report
    val status = item.effectiveStatus
    val sourceTokens = parseSourceTokens(report?.source.orEmpty())
    val badgeLabelText = formatRuleStatusBadge(
        status = status,
        rawSource = report?.source.orEmpty(),
        critical = item.spec.critical
    )

    val (badgeLabel, badgeBg, badgeFg, dotColor) = when (status) {
        HookMatchStatus.ACTIVE -> Quad(
            badgeLabelText,
            statusColors.okContainer,
            statusColors.onOkContainer,
            statusColors.ok
        )
        HookMatchStatus.FALLBACK -> Quad(
            badgeLabelText,
            statusColors.infoContainer,
            statusColors.onInfoContainer,
            statusColors.info
        )
        HookMatchStatus.DEGRADED -> Quad(
            badgeLabelText,
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            statusColors.warning
        )
        HookMatchStatus.FAILED -> Quad(
            badgeLabelText,
            statusColors.errorContainer,
            statusColors.onErrorContainer,
            statusColors.error
        )
        HookMatchStatus.SKIPPED -> Quad(
            badgeLabelText,
            statusColors.neutralContainer,
            statusColors.onNeutralContainer,
            statusColors.neutral
        )
        HookMatchStatus.PENDING -> Quad(
            badgeLabelText,
            statusColors.neutralContainer,
            statusColors.onNeutralContainer,
            statusColors.neutral
        )
    }

    val borderStroke = when {
        status == HookMatchStatus.FAILED && item.spec.critical ->
            BorderStroke(1.dp, statusColors.error.copy(alpha = 0.65f))
        status == HookMatchStatus.FAILED || status == HookMatchStatus.DEGRADED ->
            BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.55f))
        else -> null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardDefaults.CornerRadius),
        color = colors.surfaceContainer,
        border = borderStroke
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiStatusBadge(
                        text = item.spec.target.displayName.removeSuffix("小米版").removeSuffix("输入法"),
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                    if (item.spec.critical) {
                        MiStatusBadge(
                            text = "关键",
                            containerColor = statusColors.warningContainer,
                            contentColor = statusColors.onWarningContainer
                        )
                    }
                }
                MiStatusBadge(
                    text = badgeLabel,
                    containerColor = badgeBg,
                    contentColor = badgeFg,
                    dotColor = dotColor
                )
            }

            Text(
                text = item.spec.title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurfaceContainer
            )
            Text(
                text = item.spec.description,
                style = MiuixTheme.textStyles.body2,
                color = colors.onSurfaceVariantSummary
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MiInnerShape,
                color = colors.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val matchedMethod = report?.matchedMethod?.takeIf { it.isNotBlank() }
                    val detail = report?.detail?.takeIf { it.isNotBlank() }
                    val blockedCount = report?.blockedCount ?: 0L

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ID: ${item.spec.ruleId}",
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariantSummary
                        )
                        Text(
                            text = "拦截 $blockedCount 次",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            color = if (blockedCount > 0L) colors.primary else colors.onSurfaceVariantSummary
                        )
                    }
                    if (matchedMethod != null) {
                        Text(
                            text = "方法：$matchedMethod",
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceContainer
                        )
                    }
                    if (sourceTokens.size > 1) {
                        val labels = sourceTokens.map(::formatSourceTokenLabel).distinct()
                        Text(
                            text = "来源：${labels.joinToString(", ")}",
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary
                        )
                    }
                    if (detail != null) {
                        Text(
                            text = "详情：$detail",
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (status == HookMatchStatus.FAILED || status == HookMatchStatus.DEGRADED) {
                                statusColors.error
                            } else {
                                colors.onSurfaceVariantSummary
                            }
                        )
                    } else if (report == null) {
                        Text(
                            text = if (item.switchEnabled) {
                                "等待输入法启动后上报"
                            } else {
                                "规则未开启"
                            },
                            style = MiuixTheme.textStyles.footnote2,
                            color = colors.onSurfaceVariantSummary
                        )
                    }
                }
            }
        }
    }
}

internal fun formatRuleStatusBadge(
    status: HookMatchStatus,
    rawSource: String,
    critical: Boolean = false
): String {
    val tokens = parseSourceTokens(rawSource)
    val primaryToken = when {
        "DEXKIT" in tokens -> "DEXKIT"
        "CACHE" in tokens -> "CACHE"
        "STRUCTURAL_FALLBACK" in tokens -> "STRUCTURAL_FALLBACK"
        "FAST_PATH" in tokens -> "FAST_PATH"
        else -> tokens.firstOrNull().orEmpty()
    }
    val sourceLabel = if (primaryToken.isNotBlank()) formatSourceTokenLabel(primaryToken) else ""
    return when (status) {
        HookMatchStatus.ACTIVE -> if (sourceLabel.isNotBlank()) "正常 · $sourceLabel" else "正常"
        HookMatchStatus.FALLBACK -> "结构兜底"
        HookMatchStatus.DEGRADED -> if (sourceLabel.isNotBlank()) "部分降级 · $sourceLabel" else "部分降级"
        HookMatchStatus.FAILED -> if (critical) "关键未命中" else "未命中"
        HookMatchStatus.SKIPPED -> "已跳过"
        HookMatchStatus.PENDING -> "待激活"
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
