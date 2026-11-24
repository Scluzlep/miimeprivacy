package com.scluzlep.miimeprivacy.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.scluzlep.miimeprivacy.config.MonetPaletteStyle
import com.scluzlep.miimeprivacy.config.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

// HyperOS fallback palette when Monet dynamic color is disabled or unavailable (< Android 12)
val HyperBlue = Color(0xFF007DFF)
val HyperBlueVariant = Color(0xFF0066D6)
val HyperBackgroundLight = Color(0xFFF4F6F9)
val HyperBackgroundDark = Color(0xFF0C0D0E)
val HyperSurfaceLight = Color(0xFFFFFFFF)
val HyperSurfaceDark = Color(0xFF191A1D)
val HyperCardHighLight = Color(0xFFEEF1F5)
val HyperCardHighDark = Color(0xFF222428)
val HyperCardBorderLight = Color(0xFFE5E7EB)
val HyperCardBorderDark = Color(0xFF2E3136)

val HyperTextPrimaryLight = Color(0xFF111827)
val HyperTextSecondaryLight = Color(0xFF6B7280)
val HyperTextPrimaryDark = Color(0xFFF3F4F6)
val HyperTextSecondaryDark = Color(0xFF9CA3AF)

val MiCardShape = RoundedCornerShape(20.dp)
val MiInnerShape = RoundedCornerShape(14.dp)
val MiChipShape = RoundedCornerShape(10.dp)
val MiPillShape = RoundedCornerShape(50)

@Immutable
data class MiStatusColors(
    val ok: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color
)

private val LightStatusColors = MiStatusColors(
    ok = Color(0xFF179C52),
    okContainer = Color(0xFFE3F7EC),
    onOkContainer = Color(0xFF0B5D30),
    warning = Color(0xFFD97706),
    warningContainer = Color(0xFFFFF3DC),
    onWarningContainer = Color(0xFF8A4500),
    error = Color(0xFFD93025),
    errorContainer = Color(0xFFFDE8E6),
    onErrorContainer = Color(0xFF8C1D18),
    info = Color(0xFF007DFF),
    infoContainer = Color(0xFFE5F1FF),
    onInfoContainer = Color(0xFF004FA3),
    neutral = Color(0xFF6B7280),
    neutralContainer = Color(0xFFEEF1F5),
    onNeutralContainer = Color(0xFF374151)
)

private val DarkStatusColors = MiStatusColors(
    ok = Color(0xFF4AD986),
    okContainer = Color(0xFF123624),
    onOkContainer = Color(0xFFA7F3C7),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0xFF3A280A),
    onWarningContainer = Color(0xFFFFDFAA),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF421719),
    onErrorContainer = Color(0xFFFFC7C7),
    info = Color(0xFF5CA8FF),
    infoContainer = Color(0xFF122C4A),
    onInfoContainer = Color(0xFFC9E2FF),
    neutral = Color(0xFF9CA3AF),
    neutralContainer = Color(0xFF262930),
    onNeutralContainer = Color(0xFFD1D5DB)
)

val LocalMiStatusColors = staticCompositionLocalOf { LightStatusColors }

object MiThemeExtras {
    val statusColors: MiStatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMiStatusColors.current
}

private val HyperMaterialLightColorScheme = lightColorScheme(
    primary = HyperBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5F1FF),
    onPrimaryContainer = HyperBlueVariant,
    secondary = HyperBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5F1FF),
    onSecondaryContainer = HyperBlueVariant,
    tertiary = Color(0xFF179C52),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3F7EC),
    onTertiaryContainer = Color(0xFF0B5D30),
    background = HyperBackgroundLight,
    onBackground = HyperTextPrimaryLight,
    surface = HyperSurfaceLight,
    onSurface = HyperTextPrimaryLight,
    surfaceVariant = HyperCardHighLight,
    onSurfaceVariant = HyperTextSecondaryLight,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = HyperSurfaceLight,
    surfaceContainerHigh = HyperCardHighLight,
    surfaceContainerHighest = HyperCardBorderLight,
    outline = HyperCardBorderLight,
    outlineVariant = HyperCardBorderLight
)

private val HyperMaterialDarkColorScheme = darkColorScheme(
    primary = HyperBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF00448A),
    onPrimaryContainer = Color.White,
    secondary = HyperBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF153459),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF4AD986),
    onTertiary = Color(0xFF062916),
    tertiaryContainer = Color(0xFF123624),
    onTertiaryContainer = Color(0xFFA7F3C7),
    background = HyperBackgroundDark,
    onBackground = HyperTextPrimaryDark,
    surface = HyperSurfaceDark,
    onSurface = HyperTextPrimaryDark,
    surfaceVariant = HyperCardHighDark,
    onSurfaceVariant = HyperTextSecondaryDark,
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0C0D0E),
    surfaceContainerLow = Color(0xFF141518),
    surfaceContainer = HyperSurfaceDark,
    surfaceContainerHigh = HyperCardHighDark,
    surfaceContainerHighest = HyperCardBorderDark,
    outline = HyperCardBorderDark,
    outlineVariant = HyperCardBorderDark
)

val MiImeTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp
    )
)

fun isDarkTheme(themeMode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun resolveColorSchemeMode(themeMode: ThemeMode, monetEnabled: Boolean): ColorSchemeMode = when (themeMode) {
    ThemeMode.SYSTEM -> if (monetEnabled) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    ThemeMode.LIGHT -> if (monetEnabled) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
    ThemeMode.DARK -> if (monetEnabled) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
}

fun MonetPaletteStyle.toMiuixPaletteStyle(): ThemePaletteStyle = when (this) {
    MonetPaletteStyle.TonalSpot -> ThemePaletteStyle.TonalSpot
    MonetPaletteStyle.Neutral -> ThemePaletteStyle.Neutral
    MonetPaletteStyle.Vibrant -> ThemePaletteStyle.Vibrant
    MonetPaletteStyle.Expressive -> ThemePaletteStyle.Expressive
    MonetPaletteStyle.Rainbow -> ThemePaletteStyle.Rainbow
    MonetPaletteStyle.FruitSalad -> ThemePaletteStyle.FruitSalad
    MonetPaletteStyle.Monochrome -> ThemePaletteStyle.Monochrome
    MonetPaletteStyle.Fidelity -> ThemePaletteStyle.Fidelity
    MonetPaletteStyle.Content -> ThemePaletteStyle.Content
}

fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

fun paletteStyleLabel(style: MonetPaletteStyle): String = when (style) {
    MonetPaletteStyle.TonalSpot -> "色调点"
    MonetPaletteStyle.Neutral -> "柔和中性"
    MonetPaletteStyle.Vibrant -> "鲜艳明快"
    MonetPaletteStyle.Expressive -> "富于表现"
    MonetPaletteStyle.Rainbow -> "彩虹撞色"
    MonetPaletteStyle.FruitSalad -> "水果拼盘"
    MonetPaletteStyle.Monochrome -> "极简黑白"
    MonetPaletteStyle.Fidelity -> "保真还原"
    MonetPaletteStyle.Content -> "内容跟随"
}

fun paletteStyleDescription(style: MonetPaletteStyle): String = when (style) {
    MonetPaletteStyle.TonalSpot -> "Android 默认莫奈风格，平衡主色与中性背景"
    MonetPaletteStyle.Neutral -> "低饱和度灰调衬底，突出信息层级"
    MonetPaletteStyle.Vibrant -> "提升色度对比，界面重点更鲜明"
    MonetPaletteStyle.Expressive -> "增强辅助色与容器色变化"
    MonetPaletteStyle.Rainbow -> "多色相过渡，保持柔和质感"
    MonetPaletteStyle.FruitSalad -> "互补撞色点缀，强调互动元件"
    MonetPaletteStyle.Monochrome -> "无彩色阶梯，专注高对比阅读"
    MonetPaletteStyle.Fidelity -> "紧贴种子原色亮度与彩度"
    MonetPaletteStyle.Content -> "温和还原提取色彩倾向"
}

private fun resolveSystemSeedColor(context: Context): Color {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val colorInt = runCatching {
            ContextCompat.getColor(context, android.R.color.system_accent1_500)
        }.getOrNull()
        if (colorInt != null && colorInt != 0) {
            return Color(colorInt)
        }
    }
    return HyperBlue
}

@Composable
fun MiImeTheme(
    themeMode: ThemeMode,
    monetEnabled: Boolean,
    paletteStyle: MonetPaletteStyle,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val darkTheme = isDarkTheme(themeMode, systemDark)
    val schemeMode = resolveColorSchemeMode(themeMode, monetEnabled)
    val miuixPaletteStyle = paletteStyle.toMiuixPaletteStyle()
    val colorSpec = if (Build.VERSION.SDK_INT >= 36) ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021

    // When Monet + TonalSpot on Android 12+, pass keyColor = null so Miuix reads system wallpaper palette directly.
    // When user chooses another MonetPaletteStyle (or < Android 12 fallback), derive from system seed (or HyperBlue)
    // so the selected paletteStyle visibly updates all Miuix surfaces in real time.
    val seedKeyColor = remember(monetEnabled, paletteStyle, context) {
        if (!monetEnabled) {
            null
        } else if (paletteStyle == MonetPaletteStyle.TonalSpot && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            null
        } else {
            resolveSystemSeedColor(context)
        }
    }

    val hyperLightColors = remember {
        miuixLightColorScheme(
            primary = HyperBlue,
            primaryVariant = HyperBlueVariant,
            primaryContainer = Color(0xFFE5F1FF),
            onPrimaryContainer = HyperBlueVariant,
            background = HyperBackgroundLight,
            surface = HyperBackgroundLight,
            surfaceVariant = HyperCardHighLight,
            surfaceContainer = HyperSurfaceLight,
            surfaceContainerHigh = HyperCardHighLight,
            outline = HyperCardBorderLight,
            dividerLine = HyperCardBorderLight
        )
    }

    val hyperDarkColors = remember {
        miuixDarkColorScheme(
            primary = HyperBlue,
            primaryVariant = HyperBlueVariant,
            primaryContainer = Color(0xFF00448A),
            onPrimaryContainer = Color.White,
            background = HyperBackgroundDark,
            surface = HyperBackgroundDark,
            surfaceVariant = HyperCardHighDark,
            surfaceContainer = HyperSurfaceDark,
            surfaceContainerHigh = HyperCardHighDark,
            outline = HyperCardBorderDark,
            dividerLine = HyperCardBorderDark
        )
    }

    val forcedDarkFlag = when (themeMode) {
        ThemeMode.SYSTEM -> null
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val controller = remember(schemeMode, seedKeyColor, colorSpec, miuixPaletteStyle, forcedDarkFlag) {
        ThemeController(
            colorSchemeMode = schemeMode,
            lightColors = hyperLightColors,
            darkColors = hyperDarkColors,
            keyColor = seedKeyColor,
            colorSpec = colorSpec,
            paletteStyle = miuixPaletteStyle,
            isDark = forcedDarkFlag
        )
    }

    MiuixTheme(controller = controller) {
        val miuixColors = MiuixTheme.colorScheme
        val materialColorScheme = remember(
            monetEnabled,
            darkTheme,
            paletteStyle,
            miuixColors.primary,
            miuixColors.primaryContainer,
            miuixColors.background,
            miuixColors.surfaceContainer
        ) {
            syncMaterialColorScheme(
                context = context,
                darkTheme = darkTheme,
                monetEnabled = monetEnabled,
                paletteStyle = paletteStyle,
                miuixColors = miuixColors
            )
        }
        val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors

        CompositionLocalProvider(LocalMiStatusColors provides statusColors) {
            MaterialTheme(
                colorScheme = materialColorScheme,
                typography = MiImeTypography,
                content = content
            )
        }
    }
}

private fun syncMaterialColorScheme(
    context: Context,
    darkTheme: Boolean,
    monetEnabled: Boolean,
    paletteStyle: MonetPaletteStyle,
    miuixColors: top.yukonga.miuix.kmp.theme.Colors
): ColorScheme {
    val base = if (monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) HyperMaterialDarkColorScheme else HyperMaterialLightColorScheme
    }
    if (!monetEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return base
    }
    // Keep MaterialTheme tokens color-synchronized with the active Miuix palette style
    return if (monetEnabled || paletteStyle != MonetPaletteStyle.TonalSpot) {
        base.copy(
            primary = miuixColors.primary,
            onPrimary = miuixColors.onPrimary,
            primaryContainer = miuixColors.primaryContainer,
            onPrimaryContainer = miuixColors.onPrimaryContainer,
            secondary = miuixColors.secondary,
            onSecondary = miuixColors.onSecondary,
            secondaryContainer = miuixColors.secondaryContainer,
            onSecondaryContainer = miuixColors.onSecondaryContainer,
            background = miuixColors.background,
            onBackground = miuixColors.onBackground,
            surface = miuixColors.surface,
            onSurface = miuixColors.onSurface,
            surfaceVariant = miuixColors.surfaceVariant,
            surfaceContainer = miuixColors.surfaceContainer,
            surfaceContainerHigh = miuixColors.surfaceContainerHigh,
            surfaceContainerHighest = miuixColors.surfaceContainerHighest,
            outline = miuixColors.outline,
            error = miuixColors.error,
            onError = miuixColors.onError,
            errorContainer = miuixColors.errorContainer,
            onErrorContainer = miuixColors.onErrorContainer
        )
    } else {
        base
    }
}
