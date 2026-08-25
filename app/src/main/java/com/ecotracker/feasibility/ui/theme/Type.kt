package com.wildlife.feasibility.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.R

/**
 * Three families, and only three.
 *
 * Eczar is the display serif: titles, species identity, stat numbers and level moments.
 * Barlow is the sans: body copy, metadata, controls and every small-caps field label.
 * IBM Plex Mono is the *record* voice, and it is the narrowest of the three in scope:
 * datelines, coordinates, tallies and stamps — text a ranger would have typed onto a form
 * rather than written into a sentence. It exists because Home is a dated entry and the
 * small-caps sans label could not distinguish "SUNDAY 23 AUGUST" from a section heading.
 *
 * Lora and Baloo 2 were removed — Lora duplicated the display serif at one size, and
 * Baloo's chunky "game" voice belonged to the earlier gamified direction rather than to
 * Ranger's Journal.
 *
 * The mono is deliberately *not* a handwriting face. A script would read as scrapbook,
 * which `style.md` §25 prohibits; a typewriter reads as record, which is the brief.
 *
 * All three faces are bundled under the SIL Open Font License (see `docs/licenses/`).
 */

/**
 * A static file per weight, not one variable file declared four times.
 *
 * An earlier display family pointed all four weights at a single variable TTF with no
 * `FontVariation` settings, which yields four identical faces at the file's default
 * weight — every "Medium" and "Bold" in the app was the same rendering, with Android
 * synthesising a difference where it managed one at all.
 *
 * The files are **subset to Latin**. Eczar ships Devanagari as well, which is 264KB per
 * weight; nothing in this app draws it, and four full weights would have added a megabyte
 * to the APK for glyphs that never render.
 *
 * The display face carries no italic: the only italic style in the app
 * ([ScientificNameStyle]) is set in the body sans, so shipping one would be dead weight.
 */
private val Eczar = FontFamily(
    Font(R.font.eczar_regular, weight = FontWeight.Normal),
    Font(R.font.eczar_medium, weight = FontWeight.Medium),
    Font(R.font.eczar_semibold, weight = FontWeight.SemiBold),
    Font(R.font.eczar_bold, weight = FontWeight.Bold),
)

/**
 * Barlow's narrow proportions matter here: the design leans on letterspaced small-caps
 * labels, and longer localised strings need the horizontal room.
 */
private val Barlow = FontFamily(
    Font(R.font.barlow_regular, weight = FontWeight.Normal),
    Font(R.font.barlow_medium, weight = FontWeight.Medium),
    Font(R.font.barlow_semibold, weight = FontWeight.SemiBold),
    Font(R.font.barlow_bold, weight = FontWeight.Bold),
    // Real italic: scientific names and metadata are italicised often enough that a
    // synthesised oblique would show.
    Font(R.font.barlow_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
)

/**
 * The record voice. Three static weights, no italic: nothing stamped on a form is italic,
 * and the family is Latin-only as shipped, so no subsetting step is needed.
 */
private val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, weight = FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold),
)

/** The app's sans, declared once so the face is a single decision. */
val BodyFontFamily = Barlow

/** The app's mono, for stamped records only — never body copy, never a heading. */
val MonoFontFamily = PlexMono

val DisplayFontFamily = Eczar

val WildlifeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Eczar,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Eczar,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Eczar,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Eczar,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

/**
 * The journal's small-caps label: section rules, stat captions, rarity pills and
 * regional standing. Letterspaced so short uppercase runs stay readable at 10sp.
 */
val FieldLabelStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    letterSpacing = 1.3.sp,
)

/**
 * The dateline stamp: what a ranger types at the head of an entry.
 *
 * Tracked wide because mono at 10sp with no tracking reads as code rather than as a
 * stamped field. Uppercase is applied at the call site, as with [FieldLabelStyle].
 */
val FieldStampStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.1.sp,
)

/**
 * A stamped tally: the numerals in a record line, one step up from [FieldStampStyle].
 *
 * Mono rather than Eczar because these are counts *read off a form* — XP remaining, report
 * counts, slip totals. Eczar numerals stay with identity and achievement.
 */
val FieldTallyStyle = TextStyle(
    fontFamily = MonoFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 13.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.4.sp,
)

val ScientificNameStyle = TextStyle(
    fontFamily = BodyFontFamily,
    fontStyle = FontStyle.Italic,
    fontSize = 12.sp,
    lineHeight = 16.sp,
)
