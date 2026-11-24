package com.scluzlep.miimeprivacy.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom crisp rounded vector icons complementing `material-icons-core` for the Miuix navigation bar
 * and feature headers without pulling external icon dependencies.
 */
object MiImeIcons {

    val ShieldCheck: ImageVector by lazy {
        ImageVector.Builder(
            name = "ShieldCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                lineTo(4f, 5.4f)
                verticalLineTo(11.1f)
                curveTo(4f, 16.3f, 7.4f, 21.0f, 12f, 22.3f)
                curveTo(16.6f, 21.0f, 20f, 16.3f, 20f, 11.1f)
                verticalLineTo(5.4f)
                lineTo(12f, 2f)
                close()
                moveTo(10.7f, 15.6f)
                lineTo(7.5f, 12.4f)
                lineTo(8.9f, 11.0f)
                lineTo(10.7f, 12.8f)
                lineTo(15.2f, 8.3f)
                lineTo(16.6f, 9.7f)
                lineTo(10.7f, 15.6f)
                close()
            }
        }.build()
    }

    val OverlayPill: ImageVector by lazy {
        ImageVector.Builder(
            name = "OverlayPill",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Rounded pill frame
                moveTo(7f, 6f)
                horizontalLineTo(17f)
                curveTo(20.31f, 6f, 23f, 8.69f, 23f, 12f)
                curveTo(23f, 15.31f, 20.31f, 18f, 17f, 18f)
                horizontalLineTo(7f)
                curveTo(3.69f, 18f, 1f, 15.31f, 1f, 12f)
                curveTo(1f, 8.69f, 3.69f, 6f, 7f, 6f)
                close()
                // Inner toggle dot
                moveTo(16.5f, 9f)
                curveTo(14.84f, 9f, 13.5f, 10.34f, 13.5f, 12f)
                curveTo(13.5f, 13.66f, 14.84f, 15f, 16.5f, 15f)
                curveTo(18.16f, 15f, 19.5f, 13.66f, 19.5f, 12f)
                curveTo(19.5f, 10.34f, 18.16f, 9f, 16.5f, 9f)
                close()
            }
        }.build()
    }

    val Palette: ImageVector by lazy {
        ImageVector.Builder(
            name = "Palette",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.EvenOdd
            ) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                curveTo(2f, 17.52f, 6.48f, 22f, 12f, 22f)
                curveTo(13.38f, 22f, 14.5f, 20.88f, 14.5f, 19.5f)
                curveTo(14.5f, 18.89f, 14.27f, 18.33f, 13.9f, 17.89f)
                curveTo(13.55f, 17.48f, 13.33f, 16.95f, 13.33f, 16.38f)
                curveTo(13.33f, 15.0f, 14.45f, 13.88f, 15.83f, 13.88f)
                horizontalLineTo(17.5f)
                curveTo(19.99f, 13.88f, 22f, 11.87f, 22f, 9.38f)
                curveTo(22f, 5.14f, 17.52f, 2f, 12f, 2f)
                close()
                // Color dots
                moveTo(6.5f, 11.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 9.5f, 11.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 6.5f, 11.5f)
                close()
                moveTo(9.5f, 7.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 12.5f, 7.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 9.5f, 7.5f)
                close()
                moveTo(14.5f, 7.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 17.5f, 7.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 14.5f, 7.5f)
                close()
            }
        }.build()
    }

    val EmojiFace: ImageVector by lazy {
        ImageVector.Builder(
            name = "EmojiFace",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd
            ) {
                // Outer circle (center 12,12 radius 10)
                moveTo(12f, 2f)
                arcTo(10f, 10f, 0f, true, true, 12f, 22f)
                arcTo(10f, 10f, 0f, true, true, 12f, 2f)
                close()
                // Left eye (center 8.5, 9.5 radius 1.5)
                moveTo(7f, 9.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 10f, 9.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 7f, 9.5f)
                close()
                // Right eye (center 15.5, 9.5 radius 1.5)
                moveTo(14f, 9.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 17f, 9.5f)
                arcTo(1.5f, 1.5f, 0f, true, true, 14f, 9.5f)
                close()
                // Rounded smile arc cutout
                moveTo(7.15f, 14.80f)
                arcTo(5.6f, 5.6f, 0f, false, false, 16.85f, 14.80f)
                arcTo(0.8f, 0.8f, 0f, false, false, 15.46f, 14.00f)
                arcTo(4.0f, 4.0f, 0f, false, true, 8.54f, 14.00f)
                arcTo(0.8f, 0.8f, 0f, false, false, 7.15f, 14.80f)
                close()
            }
        }.build()
    }
}
