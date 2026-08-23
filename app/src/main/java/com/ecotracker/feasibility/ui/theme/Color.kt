package com.wildlife.feasibility.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------------
// Ranger's Journal palette.
//
// The app reads as a field-guide pamphlet studied by lamplight: a warm, dark forest
// ground rather than a neutral dark theme. Greens carry identity, brass is reserved
// for earned prestige, and parchment carries text.
// ---------------------------------------------------------------------------------

val WildlifeBackground = Color(0xFF0E1209)
val WildlifeSurface = Color(0xFF1A2012)
val WildlifeSurfaceElevated = Color(0xFF212819)
val WildlifeSurfaceWarm = Color(0xFF151A0F)
val WildlifeOutline = Color(0xFF35402A)
val WildlifeOutlineSubtle = Color(0xFF283120)

/** Faint topographic contour lines drawn under pages and plates. */
val WildlifeContour = Color(0xFF1D2614)

val WildlifeTextPrimary = Color(0xFFEFE7D2)
val WildlifeTextSecondary = Color(0xFFADAB90)
val WildlifeTextMuted = Color(0xFF70705D)

val WildlifeOlive = Color(0xFF6E8B45)
val WildlifeOliveStrong = Color(0xFF8FB059)
val WildlifeOliveDark = Color(0xFF3E5A2C)
val WildlifeParchment = Color(0xFFEFE7D2)
val WildlifeGold = Color(0xFFCFA53E)
// Progression level accents, one per level in `GeneratedProgressionConfig.levels`.
//
// Read as an ascending ramp — humble stone, through living green and verdigris, into
// worked metal, ending in gold. A rank should be recognisable by colour alone before its
// badge or label is read, so each step is deliberately a different material.
val LevelTourist = Color(0xFF9A9482)
val LevelExplorer = Color(0xFF8FB059)
val LevelNaturalist = Color(0xFF4E9E86)
val LevelTracker = Color(0xFFC0763C)
val LevelFieldRanger = Color(0xFFCFA53E)
val LevelMasterRanger = Color(0xFFC6CFD4)
val LevelLegendaryRanger = Color(0xFFF2CB57)

/** Accent for a progression level key, falling back to the entry level for unknown keys. */
fun levelAccent(levelKey: String): Color = when (levelKey) {
    "tourist" -> LevelTourist
    "explorer" -> LevelExplorer
    "naturalist" -> LevelNaturalist
    "tracker" -> LevelTracker
    "field_ranger" -> LevelFieldRanger
    "master_ranger" -> LevelMasterRanger
    "legendary_ranger" -> LevelLegendaryRanger
    else -> LevelTourist
}

// Region accents. Each installed region owns one colour, used for its emblem tint and
// its selector chip — never to recolour a surface. They are landscape colours, not UI
// colours: terracotta earth, savanna ochre, shallow-water turquoise.
val MediterraneanTerracotta = Color(0xFFC76D3A)
val EastAfricaOchre = Color(0xFFA9763F)
val CaribbeanTurquoise = Color(0xFF3E8F91)
val WildlifeConfirmed = Color(0xFF8FB059)
// Neutral-warm, not olive: a green silhouette on a grey plate reintroduced the
// colour the plate exists to avoid.
val WildlifeSilhouette = Color(0xFF383B33)

// Rarity tier accents (style.md §4). Used as marks, pills and labels only — never to
// recolour a whole surface. In v1 these are a labelled visual placeholder, not biology.
val WildlifeRarityCommon = Color(0xFF8FB059)
val WildlifeRarityUncommon = Color(0xFF5FB6AA)
val WildlifeRarityRare = Color(0xFFA484DC)
val WildlifeRarityVeryRare = Color(0xFFCFA53E)

// Species plates. Deliberately near-neutral rather than another green: with a painted
// forest behind them and green marks, borders and controls on top, olive cards left the
// screen reading as one colour. A touch of warmth keeps them from going cold slate.
val WildlifePlate = Color(0xFF1C1E1A)
val WildlifePlateHigh = Color(0xFF252722)
/** Contour lines drawn on an unfound plate — neutral, so the plate stays grey. */
val WildlifePlateContour = Color(0xFF272A24)

// Filter-axis accents. Each axis owns a hue so a row of selectors reads as four distinct
// controls rather than one green mass — with every control on oliveStrong the row could
// only be parsed by reading every label.
//
// Verdigris and copper repeat the Naturalist and Tracker level accents. The palette has a
// limited number of materials in it, and the two vocabularies never share a surface: a
// progression badge and a filter pill are never adjacent.
val WildlifeAxisStatus = Color(0xFF8FB059)   // moss
val WildlifeAxisStanding = Color(0xFFCFA53E) // brass
val WildlifeAxisRarity = Color(0xFF4E9E86)   // verdigris
val WildlifeAxisGroup = Color(0xFFC0763C)    // copper

// Regional standing. These carry the *card frame*, deliberately a different axis from
// rarity so a plate never says the same thing twice.
val WildlifeEssential = Color(0xFF8FB059)
// Gold, and the only gold on a plate. An Icon is the rarest standing a species can hold,
// so it takes the strongest material in the palette; Essential keeps the quieter olive.
// This was once a bone tone, held back because a separate Legend tier owned the gold — the
// two were always the same five species, so the tier went and the Icon inherited its metal.
val WildlifeIcon = WildlifeGold

@Immutable
data class WildlifeColors(
    val parchment: Color,
    val parchmentDim: Color,
    val parchmentFaint: Color,
    val olive: Color,
    val oliveStrong: Color,
    val oliveDark: Color,
    val gold: Color,
    val silhouette: Color,
    val contour: Color,
    val plate: Color,
    val plateHigh: Color,
    val plateContour: Color,
    val confirmed: Color,
    val mutedText: Color,
    val rarityCommon: Color,
    val rarityUncommon: Color,
    val rarityRare: Color,
    val rarityVeryRare: Color,
    val essential: Color,
    val icon: Color,
    val axisStatus: Color,
    val axisStanding: Color,
    val axisRarity: Color,
    val axisGroup: Color,
)

/** Single source of the palette, shared by the theme and its composition-local default. */
fun wildlifeColors() = WildlifeColors(
    parchment = WildlifeParchment,
    parchmentDim = WildlifeTextSecondary,
    parchmentFaint = WildlifeTextMuted,
    olive = WildlifeOlive,
    oliveStrong = WildlifeOliveStrong,
    oliveDark = WildlifeOliveDark,
    gold = WildlifeGold,
    silhouette = WildlifeSilhouette,
    contour = WildlifeContour,
    plate = WildlifePlate,
    plateHigh = WildlifePlateHigh,
    plateContour = WildlifePlateContour,
    confirmed = WildlifeConfirmed,
    mutedText = WildlifeTextMuted,
    rarityCommon = WildlifeRarityCommon,
    rarityUncommon = WildlifeRarityUncommon,
    rarityRare = WildlifeRarityRare,
    rarityVeryRare = WildlifeRarityVeryRare,
    essential = WildlifeEssential,
    icon = WildlifeIcon,
    axisStatus = WildlifeAxisStatus,
    axisStanding = WildlifeAxisStanding,
    axisRarity = WildlifeAxisRarity,
    axisGroup = WildlifeAxisGroup,
)

internal val LocalWildlifeColors = staticCompositionLocalOf { wildlifeColors() }
