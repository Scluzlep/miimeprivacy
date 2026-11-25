package com.scluzlep.miimeprivacy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scluzlep.miimeprivacy.model.HookMatchStatus
import com.scluzlep.miimeprivacy.model.ImeTarget
import com.scluzlep.miimeprivacy.model.RuleCategory
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiStateBuilder
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleDisplayItem
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleTargetFilter
import com.scluzlep.miimeprivacy.ui.viewmodel.formatSourceTokenLabel
import com.scluzlep.miimeprivacy.ui.viewmodel.parseSourceTokens
import com.scluzlep.miimeprivacy.ui.viewmodel.pickPrimarySourceToken
import com.scluzlep.miimeprivacy.ui.viewmodel.ruleCategoryLabel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RulesTab(
    state: PrivacyUiState,
    selectedFilter: RuleTargetFilter,
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
    controller: PrivacyUiController
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val selectedTarget = selectedFilter.target ?: ImeTarget.SOGOU
    val targetFilters = RuleTargetFilter.targetTabs

    var keyword by remember { mutableStateOf("") }
    val expandedCategories = remember {
        mutableStateMapOf<RuleCategory, Boolean>().apply {
            RuleCategory.entries.forEach { put(it, true) }
        }
    }

    val targetOverview = PrivacyUiStateBuilder.summarizeTargetRules(state, selectedTarget)
    val targetRules = state.ruleItems.filter { it.spec.target == selectedTarget }
    val trimmedQuery = keyword.trim().lowercase()
    val filteredRules = if (trimmedQuery.isEmpty()) {
        targetRules
    } else {
        targetRules.filter { item ->
            item.spec.title.lowercase().contains(trimmedQuery) ||
                item.spec.description.lowercase().contains(trimmedQuery) ||
                item.spec.ruleId.lowercase().contains(trimmedQuery) ||
                ruleCategoryLabel(item.spec.category).lowercase().contains(trimmedQuery)
        }
    }

    val groupedCategories = RuleCategory.entries.mapNotNull { category ->
        val itemsInCategory = filteredRules.filter { it.spec.category == category }
        if (itemsInCategory.isEmpty()) null else category to itemsInCategory
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
        item(key = "ime_selector") {
            MiSegmentedControl(
                options = targetFilters.map { filter ->
                    val count = state.ruleItems.count { it.spec.target == filter.target }
                    "${filter.label} ($count)"
                },
                selectedIndex = targetFilters.indexOfFirst { it.target == selectedTarget }.coerceAtLeast(0),
                onSelected = { idx ->
                    targetFilters[idx].target?.let(controller::selectRuleTarget)
                }
            )
        }

        item(key = "target_master_card") {
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
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedTarget.displayName,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceContainer
                        )
                        Text(
                            text = targetOverview.formattedSummary(),
                            style = MiuixTheme.textStyles.footnote1,
                            color = if (targetOverview.managed) {
                                colors.onSurfaceVariantSummary
                            } else {
                                statusColors.warning
                            }
                        )
                    }
                    Switch(
                        checked = targetOverview.managed,
                        onCheckedChange = { managed ->
                            controller.setImeManaged(selectedTarget, managed)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(thickness = 0.5.dp, color = colors.dividerLine)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (targetOverview.managed) {
                            "9 大分类共 ${targetOverview.totalCount} 项独立开关"
                        } else {
                            "已暂停接管，恢复接管后按各规则开关生效"
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = colors.onSurfaceVariantSummary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    TextButton(
                        text = "恢复默认",
                        enabled = targetOverview.hasOverrides,
                        onClick = { controller.resetPackageDefaults(selectedTarget.packageName) },
                        insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        item(key = "rule_search_and_collapse") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MiInnerShape,
                    color = colors.surfaceContainer,
                    border = BorderStroke(0.5.dp, colors.dividerLine)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (keyword.isEmpty()) {
                                Text(
                                    text = "筛选规则标题、说明或 ID…",
                                    style = MiuixTheme.textStyles.body2,
                                    color = colors.onSurfaceVariantSummary
                                )
                            }
                            BasicTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                singleLine = true,
                                textStyle = MiuixTheme.textStyles.body2.copy(
                                    color = colors.onSurfaceContainer
                                ),
                                cursorBrush = SolidColor(colors.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (keyword.isNotEmpty()) {
                            Surface(
                                onClick = { keyword = "" },
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceVariant
                            ) {
                                Text(
                                    text = "✕",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = colors.onSurfaceVariantSummary
                                )
                            }
                        }
                    }
                }

                val allExpanded = groupedCategories.all { (category, _) ->
                    expandedCategories[category] != false
                }
                Surface(
                    onClick = {
                        val nextState = !allExpanded
                        RuleCategory.entries.forEach { expandedCategories[it] = nextState }
                    },
                    shape = MiInnerShape,
                    color = colors.surfaceContainer,
                    border = BorderStroke(0.5.dp, colors.dividerLine)
                ) {
                    Text(
                        text = if (allExpanded) "全部收起" else "全部展开",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceContainer
                    )
                }
            }
        }

        if (groupedCategories.isEmpty()) {
            item(key = "empty_search") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(18.dp)
                ) {
                    Text(
                        text = "未找到匹配「$keyword」的规则",
                        style = MiuixTheme.textStyles.body2,
                        color = colors.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            groupedCategories.forEach { (category, itemsInCategory) ->
                val isExpanded = if (trimmedQuery.isNotEmpty()) {
                    true
                } else {
                    expandedCategories[category] != false
                }
                val activeInCategory = itemsInCategory.count { it.switchEnabled }

                item(key = "cat_header_${selectedTarget.name}_${category.name}") {
                    CategorySectionHeader(
                        category = category,
                        activeCount = activeInCategory,
                        totalCount = itemsInCategory.size,
                        expanded = isExpanded,
                        onToggle = {
                            expandedCategories[category] = !(expandedCategories[category] != false)
                        }
                    )
                }

                if (isExpanded) {
                    items(
                        items = itemsInCategory,
                        key = { it.spec.ruleId }
                    ) { item ->
                        GranularRuleCard(
                            item = item,
                            onToggle = { checked ->
                                controller.setRuleEnabled(item.spec.ruleId, checked)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(
    category: RuleCategory,
    activeCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = MiInnerShape,
        color = colors.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ruleCategoryLabel(category),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                MiStatusBadge(
                    text = "$activeCount/$totalCount",
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
            }
            Text(
                text = if (expanded) "▾" else "▸",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariantSummary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GranularRuleCard(
    item: RuleDisplayItem,
    onToggle: (Boolean) -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val report = item.report
    val status = item.effectiveStatus
    val sourceTokens = parseSourceTokens(report?.source.orEmpty())
    val primaryToken = pickPrimarySourceToken(sourceTokens)
    val sourceLabel = if (primaryToken.isNotBlank()) formatSourceTokenLabel(primaryToken) else ""
    val badgeLabelText = formatRuleStatusBadge(
        status = status,
        rawSource = report?.source.orEmpty(),
        critical = item.spec.critical
    )
    val switchInteractable = item.spec.isOverlayPill || item.targetManaged

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
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
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
                }
                Switch(
                    checked = item.toggleChecked,
                    onCheckedChange = onToggle,
                    enabled = switchInteractable
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MiStatusBadge(
                    text = badgeLabel,
                    containerColor = badgeBg,
                    contentColor = badgeFg,
                    dotColor = dotColor
                )
                if (!item.spec.defaultEnabled) {
                    MiStatusBadge(
                        text = "默认关闭",
                        containerColor = statusColors.neutralContainer,
                        contentColor = statusColors.onNeutralContainer
                    )
                }
                if (item.spec.critical) {
                    MiStatusBadge(
                        text = "核心防回传",
                        containerColor = statusColors.warningContainer,
                        contentColor = statusColors.onWarningContainer
                    )
                }
                if (item.spec.isOverlayPill) {
                    MiStatusBadge(
                        text = "悬浮入口",
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer
                    )
                }
                if (sourceLabel.isNotBlank()) {
                    MiStatusBadge(
                        text = sourceLabel,
                        containerColor = statusColors.infoContainer,
                        contentColor = statusColors.onInfoContainer
                    )
                }
            }

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
                            text = item.spec.ruleId,
                            style = MiuixTheme.textStyles.footnote2,
                            fontFamily = FontFamily.Monospace,
                            color = colors.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
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
                            text = when {
                                !switchInteractable -> "已暂停接管当前输入法"
                                item.switchEnabled -> "等待输入法启动后上报"
                                else -> "规则未开启"
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
    val primaryToken = pickPrimarySourceToken(tokens)
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
