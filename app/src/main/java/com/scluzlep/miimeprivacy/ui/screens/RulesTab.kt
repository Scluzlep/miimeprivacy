package com.scluzlep.miimeprivacy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import com.scluzlep.miimeprivacy.ui.viewmodel.ImeCardUiModel
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiController
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiState
import com.scluzlep.miimeprivacy.ui.viewmodel.PrivacyUiStateBuilder
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleDisplayItem
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleHealthSeverity
import com.scluzlep.miimeprivacy.ui.viewmodel.RuleTargetFilter
import com.scluzlep.miimeprivacy.ui.viewmodel.TargetRuleOverview
import com.scluzlep.miimeprivacy.ui.viewmodel.formatSourceTokenLabel
import com.scluzlep.miimeprivacy.ui.viewmodel.parseSourceTokens
import com.scluzlep.miimeprivacy.ui.viewmodel.pickPrimarySourceToken
import com.scluzlep.miimeprivacy.ui.viewmodel.ruleCategoryExpandKey
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

    val keyword by controller.rulesKeyword.collectAsState()
    val onlyInstallGated by controller.onlyInstallGated.collectAsState()
    val expandedTargets by controller.expandedRuleTargets.collectAsState()
    val expandedCategories by controller.expandedRuleCategories.collectAsState()

    LaunchedEffect(selectedFilter) {
        val diagnosticTarget = selectedFilter.target ?: return@LaunchedEffect
        controller.expandDiagnosticTarget(diagnosticTarget, state.ruleItems)
        controller.consumeRuleFilter()
    }

    val trimmedQuery = keyword.trim().lowercase()
    val installGatedTotalCount = state.ruleItems.count { it.spec.installGated }

    val targetSections = ImeTarget.entries.mapNotNull { target ->
        val baseTargetRules = state.ruleItems.filter { it.spec.target == target }
        val allTargetRules = if (onlyInstallGated) {
            baseTargetRules.filter { it.spec.installGated }
        } else {
            baseTargetRules
        }
        val filteredTargetRules = if (trimmedQuery.isEmpty()) {
            allTargetRules
        } else {
            val imeNameMatched = target.displayName.lowercase().contains(trimmedQuery) ||
                target.packageName.lowercase().contains(trimmedQuery)
            if (imeNameMatched) {
                allTargetRules
            } else {
                allTargetRules.filter { item ->
                    item.spec.title.lowercase().contains(trimmedQuery) ||
                        item.spec.description.lowercase().contains(trimmedQuery) ||
                        item.spec.ruleId.lowercase().contains(trimmedQuery) ||
                        ruleCategoryLabel(item.spec.category).lowercase().contains(trimmedQuery)
                }
            }
        }
        val categories = RuleCategory.entries.mapNotNull { category ->
            val itemsInCat = filteredTargetRules.filter { it.spec.category == category }
            if (itemsInCat.isEmpty()) null else category to itemsInCat
        }
        if (categories.isEmpty()) {
            null
        } else {
            Triple(
                target,
                PrivacyUiStateBuilder.summarizeTargetRules(state, target),
                categories
            )
        }
    }

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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                                    text = "搜索输入法、分类、Hook 名称或 ID…",
                                    style = MiuixTheme.textStyles.body2,
                                    color = colors.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = keyword,
                                onValueChange = controller::updateRulesKeyword,
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
                                onClick = { controller.updateRulesKeyword("") },
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

                Surface(
                    onClick = { controller.toggleOnlyInstallGated() },
                    shape = MiInnerShape,
                    color = if (onlyInstallGated) colors.primaryContainer else colors.surfaceContainer,
                    border = BorderStroke(
                        0.5.dp,
                        if (onlyInstallGated) colors.primary else colors.dividerLine
                    )
                ) {
                    Text(
                        text = "高耦合 $installGatedTotalCount",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = if (onlyInstallGated) colors.onPrimaryContainer else colors.onSurfaceContainer
                    )
                }

                Surface(
                    onClick = { controller.collapseAllRules() },
                    shape = MiInnerShape,
                    color = colors.surfaceContainer,
                    border = BorderStroke(0.5.dp, colors.dividerLine)
                ) {
                    Text(
                        text = "全部收起",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MiuixTheme.textStyles.footnote1,
                        fontWeight = FontWeight.Medium,
                        color = colors.onSurfaceContainer
                    )
                }
            }
        }

        if (targetSections.isEmpty()) {
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
            targetSections.forEach { (target, overview, groupedCategories) ->
                val cardModel = state.imeCards.firstOrNull { it.target == target }
                val targetExpanded = if (trimmedQuery.isNotEmpty() || onlyInstallGated) {
                    true
                } else {
                    target in expandedTargets
                }

                item(key = "ime_group_${target.name}") {
                    ImeGroupHeaderCard(
                        target = target,
                        overview = overview,
                        cardModel = cardModel,
                        expanded = targetExpanded,
                        onToggle = { controller.toggleRuleTargetExpanded(target) }
                    )
                }

                if (targetExpanded) {
                    item(key = "ime_actions_${target.name}") {
                        ImeActionBar(
                            overview = overview,
                            allCategoriesExpanded = groupedCategories.all { (category, _) ->
                                ruleCategoryExpandKey(target, category) in expandedCategories
                            },
                            onToggleManaged = { managed -> controller.setImeManaged(target, managed) },
                            onToggleAllCategories = { expand ->
                                val keys = groupedCategories.map { (category, _) ->
                                    ruleCategoryExpandKey(target, category)
                                }
                                controller.setRuleCategoriesExpanded(keys, expand)
                            },
                            onResetDefaults = { controller.resetPackageDefaults(target.packageName) }
                        )
                    }

                    groupedCategories.forEach { (category, itemsInCategory) ->
                        val catKey = ruleCategoryExpandKey(target, category)
                        val categoryExpanded = if (trimmedQuery.isNotEmpty() || onlyInstallGated) {
                            true
                        } else {
                            catKey in expandedCategories
                        }
                        val activeInCategory = itemsInCategory.count { it.switchEnabled }
                        val blockedInCategory = itemsInCategory.sumOf { it.report?.blockedCount ?: 0L }
                        val worstStatus = aggregateCategoryStatus(itemsInCategory)

                        item(key = "cat_header_${target.name}_${category.name}") {
                            CategorySectionHeader(
                                category = category,
                                activeCount = activeInCategory,
                                totalCount = itemsInCategory.size,
                                blockedCount = blockedInCategory,
                                worstStatus = worstStatus,
                                expanded = categoryExpanded,
                                onToggle = { controller.toggleRuleCategoryExpanded(catKey) }
                            )
                        }

                        if (categoryExpanded) {
                            items(
                                items = itemsInCategory,
                                key = { it.spec.ruleId }
                            ) { item ->
                                Box(modifier = Modifier.padding(start = 10.dp)) {
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
        }
    }
}

private fun aggregateCategoryStatus(items: List<RuleDisplayItem>): HookMatchStatus {
    val statuses = items.map { it.effectiveStatus }
    return when {
        HookMatchStatus.FAILED in statuses -> HookMatchStatus.FAILED
        HookMatchStatus.DEGRADED in statuses -> HookMatchStatus.DEGRADED
        HookMatchStatus.FALLBACK in statuses -> HookMatchStatus.FALLBACK
        HookMatchStatus.ACTIVE in statuses -> HookMatchStatus.ACTIVE
        HookMatchStatus.PENDING in statuses -> HookMatchStatus.PENDING
        else -> HookMatchStatus.SKIPPED
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImeGroupHeaderCard(
    target: ImeTarget,
    overview: TargetRuleOverview,
    cardModel: ImeCardUiModel?,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val health = cardModel?.healthSummary
    val (healthBg, healthFg, healthDot) = when (health?.severity ?: RuleHealthSeverity.NEUTRAL) {
        RuleHealthSeverity.HEALTHY -> Triple(statusColors.okContainer, statusColors.onOkContainer, statusColors.ok)
        RuleHealthSeverity.FALLBACK_OK -> Triple(statusColors.infoContainer, statusColors.onInfoContainer, statusColors.info)
        RuleHealthSeverity.WARNING -> Triple(statusColors.warningContainer, statusColors.onWarningContainer, statusColors.warning)
        RuleHealthSeverity.CRITICAL_ERROR -> Triple(statusColors.errorContainer, statusColors.onErrorContainer, statusColors.error)
        RuleHealthSeverity.NEUTRAL -> Triple(statusColors.neutralContainer, statusColors.onNeutralContainer, statusColors.neutral)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp)
    ) {
        Surface(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth(),
            color = colors.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = target.displayName,
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurfaceContainer
                        )
                        MiStatusBadge(
                            text = "${overview.totalCount} 项",
                            containerColor = colors.primaryContainer,
                            contentColor = colors.onPrimaryContainer
                        )
                        MiStatusBadge(
                            text = health?.headline ?: "待激活",
                            containerColor = healthBg,
                            contentColor = healthFg,
                            dotColor = healthDot
                        )
                    }
                    Text(
                        text = "已开启 ${overview.enabledCount}/${overview.totalCount} · 拦截 ${overview.blockedCount} 次 · ${overview.resolveSummaryText}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (overview.managed) colors.onSurfaceVariantSummary else statusColors.warning
                    )
                }
                Text(
                    text = if (expanded) "▾" else "▸",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
private fun ImeActionBar(
    overview: TargetRuleOverview,
    allCategoriesExpanded: Boolean,
    onToggleManaged: (Boolean) -> Unit,
    onToggleAllCategories: (Boolean) -> Unit,
    onResetDefaults: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MiInnerShape,
        color = colors.surfaceContainer,
        border = BorderStroke(0.5.dp, colors.dividerLine)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (overview.managed) "已接管" else "暂停接管",
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = if (overview.managed) colors.onSurfaceContainer else statusColors.warning
                    )
                    Switch(
                        checked = overview.managed,
                        onCheckedChange = onToggleManaged
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        text = if (allCategoriesExpanded) "收起分类" else "展开分类",
                        onClick = { onToggleAllCategories(!allCategoriesExpanded) },
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    )
                    TextButton(
                        text = "恢复默认",
                        enabled = overview.hasOverrides,
                        onClick = onResetDefaults,
                        insideMargin = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (!overview.managed) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 6.dp),
                    thickness = 0.5.dp,
                    color = colors.dividerLine
                )
                Text(
                    text = "已暂停接管，恢复接管后按各规则开关生效",
                    style = MiuixTheme.textStyles.footnote1,
                    color = statusColors.warning
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategorySectionHeader(
    category: RuleCategory,
    activeCount: Int,
    totalCount: Int,
    blockedCount: Long,
    worstStatus: HookMatchStatus,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors
    val (statusLabel, statusBg, statusFg, statusDot) = when (worstStatus) {
        HookMatchStatus.ACTIVE -> Quad("正常", statusColors.okContainer, statusColors.onOkContainer, statusColors.ok)
        HookMatchStatus.FALLBACK -> Quad("结构兜底", statusColors.infoContainer, statusColors.onInfoContainer, statusColors.info)
        HookMatchStatus.DEGRADED -> Quad("部分降级", statusColors.warningContainer, statusColors.onWarningContainer, statusColors.warning)
        HookMatchStatus.FAILED -> Quad("未命中", statusColors.errorContainer, statusColors.onErrorContainer, statusColors.error)
        HookMatchStatus.SKIPPED -> Quad("已跳过", statusColors.neutralContainer, statusColors.onNeutralContainer, statusColors.neutral)
        HookMatchStatus.PENDING -> Quad("待激活", statusColors.neutralContainer, statusColors.onNeutralContainer, statusColors.neutral)
    }

    Surface(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
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
            FlowRow(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ruleCategoryLabel(category),
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceContainer
                )
                MiStatusBadge(
                    text = "$activeCount/$totalCount",
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer
                )
                MiStatusBadge(
                    text = statusLabel,
                    containerColor = statusBg,
                    contentColor = statusFg,
                    dotColor = statusDot
                )
                if (blockedCount > 0L) {
                    MiStatusBadge(
                        text = "拦截 $blockedCount 次",
                        containerColor = statusColors.infoContainer,
                        contentColor = statusColors.onInfoContainer
                    )
                }
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                MiStatusBadge(
                    text = badgeLabel,
                    containerColor = badgeBg,
                    contentColor = badgeFg,
                    dotColor = dotColor
                )
                if (!item.spec.defaultEnabled) {
                    MiStatusBadge(
                        text = if (item.spec.installGated) "关闭时不挂载" else "默认关闭",
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
