package com.lakshyakumar.flmw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class FlmwExtendedColors(
    val surfaceSubtle: Color,
    val hairline: Color,
    val muted: Color,
    val accentSoft: Color,
    val ok: Color,
    val okSoft: Color
)

val LocalFlmwColors = staticCompositionLocalOf {
    FlmwExtendedColors(
        surfaceSubtle = PaperSurfaceSubtle,
        hairline = PaperHairline,
        muted = PaperMuted,
        accentSoft = BurntOrangeSoftLight,
        ok = SageOkLight,
        okSoft = SageOkSoftLight
    )
}

private val LightColors = lightColorScheme(
    primary = BurntOrangeLight,
    onPrimary = Color.White,
    background = PaperBg,
    onBackground = PaperInk,
    surface = PaperSurface,
    onSurface = PaperInk,
    surfaceVariant = PaperSurfaceSubtle,
    onSurfaceVariant = PaperMuted,
    outline = PaperHairline,
    outlineVariant = PaperHairline
)

private val DarkColors = darkColorScheme(
    primary = BurntOrangeDark,
    onPrimary = Color(0xFF161512),
    background = ObsidianBg,
    onBackground = ObsidianInk,
    surface = ObsidianSurface,
    onSurface = ObsidianInk,
    surfaceVariant = ObsidianSurfaceSubtle,
    onSurfaceVariant = ObsidianMuted,
    outline = ObsidianHairline,
    outlineVariant = ObsidianHairline
)

@Composable
fun FlmwTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extended = if (darkTheme) {
        FlmwExtendedColors(
            surfaceSubtle = ObsidianSurfaceSubtle,
            hairline = ObsidianHairline,
            muted = ObsidianMuted,
            accentSoft = BurntOrangeSoftDark,
            ok = SageOkDark,
            okSoft = SageOkSoftDark
        )
    } else {
        FlmwExtendedColors(
            surfaceSubtle = PaperSurfaceSubtle,
            hairline = PaperHairline,
            muted = PaperMuted,
            accentSoft = BurntOrangeSoftLight,
            ok = SageOkLight,
            okSoft = SageOkSoftLight
        )
    }

    CompositionLocalProvider(LocalFlmwColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = FlmwTypography,
            content = content
        )
    }
}

@Composable
fun flmwSwitchColors() = SwitchDefaults.colors(
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
)
