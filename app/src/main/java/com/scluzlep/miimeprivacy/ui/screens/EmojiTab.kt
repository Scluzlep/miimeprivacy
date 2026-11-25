package com.scluzlep.miimeprivacy.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.scluzlep.miimeprivacy.emoji.EmojiCatalog
import com.scluzlep.miimeprivacy.emoji.EmojiPatchConfig
import com.scluzlep.miimeprivacy.emoji.EmojiPatchController
import com.scluzlep.miimeprivacy.emoji.EmojiPatchProtocol
import com.scluzlep.miimeprivacy.emoji.EmojiTargetUiState
import com.scluzlep.miimeprivacy.emoji.InstalledEmojiGroupCounts
import com.scluzlep.miimeprivacy.emoji.PatchPhase
import com.scluzlep.miimeprivacy.ui.theme.MiChipShape
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import com.scluzlep.miimeprivacy.ui.theme.MiThemeExtras
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EmojiTab(
    scrollBehavior: ScrollBehavior,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val controller = remember(context) { EmojiPatchController.getInstance(context) }

    val config by controller.config.collectAsState()
    val versions by controller.versions.collectAsState()
    val refreshingVersions by controller.refreshingVersions.collectAsState()
    val archiveSummary by controller.archiveSummary.collectAsState()
    val emojiTestCached by controller.emojiTestCached.collectAsState()
    val archiveBusy by controller.archiveBusy.collectAsState()
    val archiveMessage by controller.archiveMessage.collectAsState()
    val sogouState by controller.sogouState.collectAsState()
    val baiduState by controller.baiduState.collectAsState()

    val zipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            controller.importArchiveZip(uri)
        }
    }

    DisposableEffect(controller) {
        controller.attachUi()
        onDispose { controller.detachUi() }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner, controller) {
        val lifecycle = lifecycleOwner?.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                controller.refreshLocalCacheStatus()
                controller.refreshTargets()
            }
        }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }

    val hasNotoArchive = archiveSummary != null
    val orderGroupsById = remember { EmojiCatalog.orderGroups.associateBy { it.packageId } }

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
            bottom = 20.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 1. 素材与版本
        item(key = "header_assets") {
            SmallTitle(
                text = "素材与版本",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item(key = "card_assets") {
            val colors = MiuixTheme.colorScheme
            val statusColors = MiThemeExtras.statusColors
            MiSectionCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "目标 Unicode 版本",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceContainer,
                            )
                            Surface(
                                onClick = { if (!refreshingVersions) controller.refreshOnlineVersions() },
                                enabled = !refreshingVersions,
                                shape = MiChipShape,
                                color = colors.surfaceVariant,
                            ) {
                                Text(
                                    text = if (refreshingVersions) "刷新中…" else "检查更新",
                                    fontSize = 12.sp,
                                    color = colors.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            versions.forEach { option ->
                                val selected = option.unicodeFolder == config.unicodeFolder
                                Surface(
                                    onClick = { controller.selectVersion(option) },
                                    shape = MiChipShape,
                                    color = if (selected) colors.primaryContainer else colors.surfaceVariant,
                                    border = if (selected) {
                                        BorderStroke(1.dp, colors.primary)
                                    } else {
                                        BorderStroke(0.5.dp, colors.dividerLine)
                                    },
                                ) {
                                    Text(
                                        text = option.emojiVersion,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = colors.dividerLine,
                    )

                    MiSwitchItem(
                        title = "仅安装系统支持字形",
                        summary = "跳过系统字体无法渲染的编码序列",
                        checked = !config.includeUnsupportedSystemEmoji,
                        onCheckedChange = controller::setOnlySupportedGlyphs,
                        showDivider = true,
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Noto 素材包 ${EmojiCatalog.NOTO_VERSION}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceContainer,
                            )
                            MiStatusBadge(
                                text = archiveSummary ?: "未缓存",
                                containerColor = if (hasNotoArchive) {
                                    statusColors.okContainer
                                } else {
                                    statusColors.warningContainer
                                },
                                contentColor = if (hasNotoArchive) {
                                    statusColors.onOkContainer
                                } else {
                                    statusColors.onWarningContainer
                                },
                            )
                        }
                        Text(
                            text = if (emojiTestCached) {
                                "版本清单：已缓存 emoji-test-${config.unicodeFolder}.txt"
                            } else {
                                "版本清单：安装或下载素材时自动缓存 emoji-test-${config.unicodeFolder}.txt"
                            },
                            fontSize = 12.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                        if (archiveMessage.isNotBlank()) {
                            Text(
                                text = archiveMessage,
                                fontSize = 12.sp,
                                color = colors.primary,
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = { controller.downloadAssets() },
                                enabled = !archiveBusy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(if (archiveBusy) "处理中…" else "下载素材")
                            }
                            Button(
                                onClick = {
                                    zipPicker.launch(
                                        arrayOf("application/zip", "application/x-zip-compressed", "*/*"),
                                    )
                                },
                                enabled = !archiveBusy,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    color = colors.secondaryContainer,
                                    contentColor = colors.onSecondaryContainer,
                                ),
                            ) {
                                Text("导入 ZIP")
                            }
                        }
                    }
                }
            }
        }

        // 2. 搜狗输入法小米版
        item(key = "header_sogou") {
            SmallTitle(
                text = "搜狗输入法小米版",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item(key = "card_sogou") {
            val colors = MiuixTheme.colorScheme
            MiSectionCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TargetHeaderSection(
                        state = sogouState,
                        subtitleDefault = "支持安装 Unicode 全量 Emoji、双肤色 5x5 面板与分组排序",
                        hasNotoArchive = hasNotoArchive,
                        onInstall = { controller.installTarget(EmojiPatchProtocol.TARGET_SOGOU) },
                        onRestore = { controller.restoreTarget(EmojiPatchProtocol.TARGET_SOGOU) },
                    )

                    val activeCounts = sogouState.groupEntryCounts
                    if (activeCounts.values.any { it >= 0 }) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.5.dp,
                            color = colors.dividerLine,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val totalInstalled = activeCounts.values.filter { it > 0 }.sum()
                            Text(
                                text = "当前分组条目 · 共 $totalInstalled 项",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.onSurfaceContainer,
                            )
                            Text(
                                text = EmojiCatalog.groups.joinToString(" · ") { g ->
                                    val count = activeCounts[g.packageId] ?: InstalledEmojiGroupCounts.MISSING_GROUP
                                    val countStr = if (count >= 0) count.toString() else "-"
                                    "${g.chineseName}:$countStr"
                                },
                                fontSize = 12.sp,
                                color = colors.onSurfaceVariantSummary,
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = colors.dividerLine,
                    )

                    SogouGroupOrderSection(
                        config = config,
                        orderGroupsById = orderGroupsById,
                        canApply = sogouState.canAct,
                        onMoveUp = controller::moveGroupUp,
                        onMoveDown = controller::moveGroupDown,
                        onReset = controller::resetGroupOrder,
                        onSave = controller::applySogouOrder,
                    )
                }
            }
        }

        // 3. 百度输入法小米版
        item(key = "header_baidu") {
            SmallTitle(
                text = "百度输入法小米版",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item(key = "card_baidu") {
            MiSectionCard {
                TargetHeaderSection(
                    state = baiduState,
                    subtitleDefault = "内置仅 Emoji 5.0，可补全至最新版本",
                    hasNotoArchive = hasNotoArchive,
                    onInstall = { controller.installTarget(EmojiPatchProtocol.TARGET_BAIDU) },
                    onRestore = { controller.restoreTarget(EmojiPatchProtocol.TARGET_BAIDU) },
                )
            }
        }

        // 4. 超级小爱输入法
        item(key = "header_xiaoai") {
            SmallTitle(
                text = "超级小爱输入法",
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
            )
        }
        item(key = "card_xiaoai") {
            val colors = MiuixTheme.colorScheme
            val statusColors = MiThemeExtras.statusColors
            MiSectionCard(insideMargin = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "系统原生渲染",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceContainer,
                        )
                        Text(
                            text = "内置 Emoji 16.0，共 1901 项，系统字体直出，无需补全",
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariantSummary,
                        )
                    }
                    MiStatusBadge(
                        text = "无需补全",
                        containerColor = statusColors.okContainer,
                        contentColor = statusColors.onOkContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetHeaderSection(
    state: EmojiTargetUiState,
    subtitleDefault: String,
    hasNotoArchive: Boolean,
    onInstall: () -> Unit,
    onRestore: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    val statusColors = MiThemeExtras.statusColors

    val (badgeText, badgeContainer, badgeContent) = when {
        !state.targetInstalled -> Triple("未安装", statusColors.neutralContainer, statusColors.onNeutralContainer)
        state.isRunning -> Triple("进行中", statusColors.infoContainer, statusColors.onInfoContainer)
        !state.hookReady -> Triple("等待连接", statusColors.warningContainer, statusColors.onWarningContainer)
        state.installedVersion != null -> Triple(
            "Emoji ${state.installedVersion}",
            statusColors.okContainer,
            statusColors.onOkContainer,
        )
        else -> Triple("已连接", statusColors.infoContainer, statusColors.onInfoContainer)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = subtitleDefault,
                fontSize = 13.sp,
                color = colors.onSurfaceVariantSummary,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            )
            MiStatusBadge(
                text = badgeText,
                containerColor = badgeContainer,
                contentColor = badgeContent,
            )
        }

        Text(
            text = state.message,
            fontSize = 13.sp,
            color = if (state.phase == PatchPhase.ERROR && !state.targetInstalled) {
                colors.onSurfaceVariantSummary
            } else if (state.phase == PatchPhase.ERROR) {
                statusColors.error
            } else {
                colors.onSurfaceContainer
            },
        )

        if (state.isRunning) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.surfaceVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.progress)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.primary),
                    )
                }
                if (state.total > 0) {
                    Text(
                        text = "${state.current} / ${state.total}",
                        fontSize = 11.sp,
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onInstall,
                enabled = state.canAct && hasNotoArchive,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(if (state.isRunning) "处理中…" else "安装补全")
            }
            Button(
                onClick = onRestore,
                enabled = state.canAct && state.hasBackup,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                ),
            ) {
                Text("恢复默认")
            }
        }
    }
}

@Composable
private fun SogouGroupOrderSection(
    config: EmojiPatchConfig,
    orderGroupsById: Map<Int, com.scluzlep.miimeprivacy.emoji.EmojiGroup>,
    canApply: Boolean,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "分组排序 · 含组合",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceContainer,
        )

        config.groupOrder.forEachIndexed { index, packageId ->
            val group = orderGroupsById[packageId]
            val title = group?.chineseName ?: "分组 $packageId"
            Surface(
                shape = MiInnerShape,
                color = colors.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}. $title",
                        fontSize = 13.sp,
                        color = colors.onSurfaceContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OrderStepButton(
                            label = "上移",
                            enabled = index > 0,
                            onClick = { onMoveUp(packageId) },
                        )
                        OrderStepButton(
                            label = "下移",
                            enabled = index < config.groupOrder.lastIndex,
                            onClick = { onMoveDown(packageId) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                ),
            ) {
                Text("重置排序")
            }
            Button(
                onClick = onSave,
                enabled = canApply,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存排序")
            }
        }
    }
}

@Composable
private fun OrderStepButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        onClick = { if (enabled) onClick() },
        enabled = enabled,
        shape = MiChipShape,
        color = colors.surfaceContainer,
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = if (enabled) colors.primary else colors.disabledOnSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
