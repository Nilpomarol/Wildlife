package com.wildlife.feasibility.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.R

private val Lora = FontFamily(
    Font(R.font.lora_variable, weight = FontWeight.Normal),
    Font(R.font.lora_variable, weight = FontWeight.Medium),
    Font(R.font.lora_variable, weight = FontWeight.SemiBold),
    Font(R.font.lora_italic_variable, weight = FontWeight.Normal, style = FontStyle.Italic),
)

// Fraunces is the characterful display face for large titles, species identity and
// game/level moments. Lora remains the calmer secondary serif; sans stays for body/UI.
private val Fraunces = FontFamily(
    Font(R.font.fraunces_variable, weight = FontWeight.Normal),
    Font(R.font.fraunces_variable, weight = FontWeight.Medium),
    Font(R.font.fraunces_variable, weight = FontWeight.SemiBold),
    Font(R.font.fraunces_variable, weight = FontWeight.Bold),
    Font(R.font.fraunces_italic_variable, weight = FontWeight.Normal, style = FontStyle.Italic),
)

// Baloo 2 is the chunky, high-weight "game" face. Used for the gamified Collection surface —
// big collector numbers, rank/level moments, filter and region chips — where the app should
// feel like a friendly explorer game rather than a reference book. Heavier than the body sans,
// it carries the playful, weighty personality the collector screens ask for.
private val Baloo = FontFamily(
    Font(R.font.baloo2_variable, weight = FontWeight.Medium),
    Font(R.font.baloo2_variable, weight = FontWeight.SemiBold),
    Font(R.font.baloo2_variable, weight = FontWeight.Bold),
    Font(R.font.baloo2_variable, weight = FontWeight.ExtraBold),
)

val DisplayFontFamily = Fraunces

/** Chunky, high-weight display face for the gamified collector surfaces. */
val GameFontFamily = Baloo

val WildlifeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Fraunces,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Lora,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

val ScientificNameStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontStyle = FontStyle.Italic,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)
