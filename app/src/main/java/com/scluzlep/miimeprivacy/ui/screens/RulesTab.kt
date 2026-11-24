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
                text = "遥测与日志隔离",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "telemetry_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "拦截独立行为遥测",
                    summary = "阻断 kpi2 / instantpb / OneTrack / 周期打点及未同意隐私放行保活",
                    checked = cfg.blockTelemetry,
                    onCheckedChange = controller::setBlockTelemetry,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截密码框计数",
                    summary = "阻止密码输入框统计计数与状态位回传（含超级小爱先计数后拦截缺陷）",
                    checked = cfg.blockPasswordStats,
                    onCheckedChange = controller::setBlockPasswordStats,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截远端日志回捞与 TDDiag",
                    summary = "阻断 mobile_log_salvage 静默捞取、TDDiag 日志打包及远端调试指令",
                    checked = cfg.blockRemoteLogSalvage,
                    onCheckedChange = controller::setBlockRemoteLogSalvage,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "拦截搜狗 MutualData",
                    summary = "关闭小米版默认开启的搜索词、应用切换与地理坐标 bigdata.db 持久化",
                    checked = cfg.blockMutualData,
                    onCheckedChange = controller::setBlockMutualData,
                    showDivider = false
                )
            }
        }

        item(key = "cloud_payload_title") {
            SmallTitle(
                text = "云输入载荷最小化（开启云输入时生效）",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "cloud_payload_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "启用载荷脱敏",
                    summary = "剥离聊天上下文 dialogue、位置坐标、IMEI/IMSI 及百度城市定位字段",
                    checked = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setSanitizeCloudPayload,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "剥离光标前后 100 字上下文",
                    summary = "仅发送当前拼音串/候选请求，阻止上传编辑框周围聊天记录",
                    checked = cfg.stripCursorContext,
                    enabled = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setStripCursorContext,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "脱敏宿主包名",
                    summary = "对云请求与 AI 接口隐藏当前正在输入的前台应用包名",
                    checked = cfg.maskHostPackage,
                    enabled = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setMaskHostPackage,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "剥离触控坐标",
                    summary = "清除输入击键坐标与轨迹统计字段，仅保留必要候选识别参数",
                    checked = cfg.stripTouchPositions,
                    enabled = cfg.sanitizeCloudPayload,
                    onCheckedChange = controller::setStripTouchPositions,
                    showDivider = false
                )
            }
        }

        item(key = "privacy_strict_title") {
            SmallTitle(
                text = "隐私权限与严格开关",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        item(key = "privacy_strict_card") {
            Card(modifier = Modifier.fillMaxWidth()) {
                MiSwitchItem(
                    title = "剪贴板隐私保护",
                    summary = "保留正常手动粘贴，阻断隐私模式反向采集、后台静默读取与 cpn 计数上报",
                    checked = cfg.clipboardPrivacy,
                    onCheckedChange = controller::setClipboardPrivacy,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "通讯录 / 短信 / 扫包拦截",
                    summary = "拦截通讯录联系人词汇上传、短信收件箱查询及全量应用列表扫描",
                    checked = cfg.blockContactsAndAppScan,
                    onCheckedChange = controller::setBlockContactsAndAppScan,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "未签名 hj 动态 DEX 拦截",
                    summary = "阻止仅校验 MD5 的远端任务 DEX、热补丁及动态脚本下载执行",
                    checked = cfg.blockUnsignedDynamicDex,
                    onCheckedChange = controller::setBlockUnsignedDynamicDex,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "阻断搜狗 / 百度 HTTPDNS（严格·默认关）",
                    summary = "阻断 MSDKDnsResolver / httpsdns.baidu.com 绕过系统 DNS 解析",
                    checked = cfg.blockHttpDns,
                    onCheckedChange = controller::setBlockHttpDns,
                    showDivider = true
                )
                MiSwitchItem(
                    title = "阻断超级小爱热词纠错跨应用追踪（严格·默认关）",
                    summary = "拦截 HotwordRemoteClient 向 com.xiaomi.aicr 上报 before_text/final_text",
                    checked = cfg.blockXiaoAiHotwordTracker,
                    onCheckedChange = controller::setBlockXiaoAiHotwordTracker,
                    showDivider = false
                )
            }
        }

        item(key = "diag_title") {
            SmallTitle(
                text = "逐条 Hook 真实命中诊断",
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

    val (badgeLabel, badgeBg, badgeFg, dotColor) = when (status) {
        HookMatchStatus.ACTIVE -> {
            val sourceTag = formatActiveSource(report?.source.orEmpty())
            Quad("ACTIVE$sourceTag", statusColors.okContainer, statusColors.onOkContainer, statusColors.ok)
        }
        HookMatchStatus.FALLBACK -> Quad(
            "FALLBACK 结构兜底",
            statusColors.infoContainer,
            statusColors.onInfoContainer,
            statusColors.info
        )
        HookMatchStatus.DEGRADED -> Quad(
            "DEGRADED 部分降级",
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            statusColors.warning
        )
        HookMatchStatus.FAILED -> Quad(
            if (item.spec.critical) "FAILED 关键未匹配" else "FAILED 未匹配告警",
            statusColors.errorContainer,
            statusColors.onErrorContainer,
            statusColors.error
        )
        HookMatchStatus.SKIPPED -> Quad(
            "SKIPPED 已跳过",
            statusColors.neutralContainer,
            statusColors.onNeutralContainer,
            statusColors.neutral
        )
        HookMatchStatus.PENDING -> Quad(
            "PENDING 等待激活",
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
                            text = "规则 ID: ${item.spec.ruleId}",
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariantSummary
                        )
                        Text(
                            text = "累计拦截：$blockedCount 次",
                            style = MiuixTheme.textStyles.footnote1,
                            fontWeight = FontWeight.SemiBold,
                            color = if (blockedCount > 0L) colors.primary else colors.onSurfaceVariantSummary
                        )
                    }
                    if (matchedMethod != null) {
                        Text(
                            text = "命中签名：$matchedMethod",
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceContainer
                        )
                    }
                    if (detail != null) {
                        Text(
                            text = "说明：$detail",
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
                                "等待目标输入法进程上报 Hook 命中结果"
                            } else {
                                "对应开关未启用或目标已停用接管"
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

private fun formatActiveSource(source: String): String {
    if (source.isBlank()) return ""
    return when {
        source == "FAST_PATH" -> " FastPath"
        source == "DEXKIT" -> " DEXKIT"
        source == "CACHE" -> " 缓存"
        else -> " $source"
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
