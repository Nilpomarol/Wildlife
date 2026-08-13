package com.wildlife.feasibility.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val WildlifeBackground = Color(0xFF080B09)
val WildlifeSurface = Color(0xFF0D110D)
val WildlifeSurfaceElevated = Color(0xFF141810)
val WildlifeSurfaceWarm = Color(0xFF191A11)
val WildlifeOutline = Color(0xFF303126)
val WildlifeOutlineSubtle = Color(0xFF22261D)
val WildlifeTextPrimary = Color(0xFFF1E6CF)
val WildlifeTextSecondary = Color(0xFFC5B99F)
val WildlifeTextMuted = Color(0xFF8F8978)
val WildlifeOlive = Color(0xFF7D8530)
val WildlifeOliveStrong = Color(0xFF9AA23D)
val WildlifeOliveDark = Color(0xFF4F551D)
val WildlifeParchment = Color(0xFFDFCDAA)
val WildlifeGold = Color(0xFFD9A441)
val WildlifeGoldStrong = Color(0xFFF0AA2A)
val WildlifeConfirmed = Color(0xFF78882D)
val WildlifeSilhouette = Color(0xFF343832)

@Immutable
data class WildlifeColors(
    val parchment: Color,
    val olive: Color,
    val oliveStrong: Color,
    val gold: Color,
    val silhouette: Color,
    val confirmed: Color,
    val mutedText: Color,
)

internal val LocalWildlifeColors = staticCompositionLocalOf {
    WildlifeColors(
        parchment = WildlifeParchment,
        olive = WildlifeOlive,
        oliveStrong = WildlifeOliveStrong,
        gold = WildlifeGold,
        silhouette = WildlifeSilhouette,
        confirmed = WildlifeConfirmed,
        mutedText = WildlifeTextMuted,
    )
}
