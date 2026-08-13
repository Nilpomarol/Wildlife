package com.wildlife.feasibility.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

private val WildlifeDarkColorScheme = darkColorScheme(
    primary = WildlifeOliveStrong,
    onPrimary = WildlifeBackground,
    primaryContainer = WildlifeOliveDark,
    onPrimaryContainer = WildlifeTextPrimary,
    secondary = WildlifeParchment,
    onSecondary = WildlifeBackground,
    secondaryContainer = WildlifeSurfaceWarm,
    onSecondaryContainer = WildlifeTextPrimary,
    tertiary = WildlifeGold,
    onTertiary = WildlifeBackground,
    background = WildlifeBackground,
    onBackground = WildlifeTextPrimary,
    surface = WildlifeSurface,
    onSurface = WildlifeTextPrimary,
    surfaceVariant = WildlifeSurfaceElevated,
    onSurfaceVariant = WildlifeTextSecondary,
    outline = WildlifeOutline,
    outlineVariant = WildlifeOutlineSubtle,
)

object WildlifeTheme {
    val colors: WildlifeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalWildlifeColors.current
}

@Composable
fun WildlifeTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalWildlifeColors provides WildlifeColors(
            parchment = WildlifeParchment,
            olive = WildlifeOlive,
            oliveStrong = WildlifeOliveStrong,
            gold = WildlifeGold,
            silhouette = WildlifeSilhouette,
            confirmed = WildlifeConfirmed,
            mutedText = WildlifeTextMuted,
            rarityCommon = WildlifeRarityCommon,
            rarityUncommon = WildlifeRarityUncommon,
            rarityRare = WildlifeRarityRare,
            rarityVeryRare = WildlifeRarityVeryRare,
            rarityLegendary = WildlifeRarityLegendary,
        ),
    ) {
        MaterialTheme(
            colorScheme = WildlifeDarkColorScheme,
            typography = WildlifeTypography,
            shapes = WildlifeShapes,
            content = content,
        )
    }
}
