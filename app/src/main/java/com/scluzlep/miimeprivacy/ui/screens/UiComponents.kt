package com.scluzlep.miimeprivacy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scluzlep.miimeprivacy.ui.theme.MiChipShape
import com.scluzlep.miimeprivacy.ui.theme.MiInnerShape
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val shape = MiChipShape
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = text,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            contentColor = contentColor,
            content = content
        )
    }
}

@Composable
fun MiSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MiuixTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MiInnerShape,
        color = colors.surfaceVariant,
        border = BorderStroke(0.5.dp, colors.dividerLine)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Surface(
                    onClick = { if (enabled && !selected) onSelected(index) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) colors.primaryContainer else Color.Transparent,
                    contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariantSummary
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (!enabled) {
                                colors.disabledOnSurface
                            } else if (selected) {
                                colors.onPrimaryContainer
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

@Composable
fun MiSwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showDivider: Boolean = false
) {
    val colors = MiuixTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth()) {
        BasicComponent(
            title = title,
            summary = summary,
            enabled = enabled,
            onClick = { if (enabled) onCheckedChange(!checked) },
            endActions = {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = enabled
                )
            }
        )
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colors.dividerLine
            )
        }
    }
}

@Composable
fun MiSectionCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(0.dp),
    containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = insideMargin,
        colors = CardDefaults.defaultColors(
            color = containerColor,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainer
        )
    ) {
        content()
    }
}
