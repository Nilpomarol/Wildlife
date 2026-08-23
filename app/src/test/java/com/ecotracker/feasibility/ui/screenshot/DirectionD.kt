package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.ui.theme.DisplayFontFamily
import kotlin.random.Random

/**
 * Direction D - "Ranger's Journal".
 *
 * A dark field-guide pamphlet: a full-bleed landscape header torn away from a
 * contoured page below it, hand-drawn scout patches, and the bundled zoological
 * silhouettes standing in for species not yet found.
 */

private val Ink = Color(0xFF0E1209)
private val Card = Color(0xFF1A2012)
private val CardDeep = Color(0xFF151A0F)
private val EdgeSoft = Color(0xFF283120)
private val Contour = Color(0xFF1D2614)
private val Parchment = Color(0xFFEFE7D2)
private val ParchmentDim = Color(0xFFADAB90)
private val ParchmentFaint = Color(0xFF70705D)
private val Moss = Color(0xFF8FB059)
private val MossDeep = Color(0xFF3E5A2C)
private val Brass = Color(0xFFCFA53E)
private val Plum = Color(0xFFA484DC)

// Landscape layers, back to front.
private val SkyTop = Color(0xFF1C2913)
private val RidgeFar = Color(0xFF32421F)
private val RidgeMid = Color(0xFF212C15)
private val RidgeNear = Color(0xFF0F1508)
private val Moonlight = Color(0xFFE9E1C6)

private val Caps = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.3.sp,
)

private fun rarityTint(r: EncounterRarity?) = when (r) {
    EncounterRarity.VERY_RARE -> Brass
    EncounterRarity.RARE -> Plum
    EncounterRarity.UNCOMMON -> Color(0xFF5FB6AA)
    EncounterRarity.COMMON -> Moss
    else -> ParchmentFaint
}

private fun rarityMark(r: EncounterRarity?) = when (r) {
    EncounterRarity.VERY_RARE -> "rarity_very_rare"
    EncounterRarity.RARE -> "rarity_rare"
    EncounterRarity.UNCOMMON -> "rarity_uncommon"
    else -> null
}

private fun rarityWord(r: EncounterRarity?) = when (r) {
    EncounterRarity.VERY_RARE -> "VERY RARE"
    EncounterRarity.RARE -> "RARE"
    EncounterRarity.UNCOMMON -> "UNCOMMON"
    EncounterRarity.COMMON -> "COMMON"
    else -> "UNRANKED"
}

/**
 * The card frame carries *regional standing*, not rarity: rarity is already said by the
 * pill mark, so the border is free to mean something else.
 */
private fun regionalBorder(item: RedesignItem): Color? = when {
    item.legendary -> Brass
    item.icon -> Parchment
    item.essential -> Moss
    else -> null
}

/** Paper grain so dark surfaces read as printed stock rather than flat screen. */
@Composable
private fun Grain(modifier: Modifier = Modifier, alpha: Float = 0.055f) {
    Canvas(modifier) {
        val rng = Random(7)
        val count = (size.width * size.height / 620f).toInt()
        repeat(count) {
            val x = rng.nextFloat() * size.width
            val y = rng.nextFloat() * size.height
            drawCircle(
                color = Color(0xFFC6BE9E).copy(alpha = alpha * (0.35f + rng.nextFloat())),
                radius = 0.85f,
                center = Offset(x, y),
            )
        }
    }
}

@Composable
fun DirectionDScreen() {
    Box(Modifier.fillMaxSize().background(Ink)) {
        ContourField(Contour, Modifier.fillMaxSize(), lines = 16, seed = 23)
        Grain(Modifier.fillMaxSize())

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {

            RangerHeader()

            Spacer(Modifier.height(6.dp))

            // ---- Search ----
            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardDeep)
                    .border(1.dp, EdgeSoft, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MagnifierGlyph(ParchmentFaint, Modifier.size(17.dp))
                Spacer(Modifier.size(11.dp))
                Text(
                    "Search your guide",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.sp,
                    color = ParchmentFaint,
                )
            }

            // ---- Filters ----
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                JournalTab("ALL", true, Modifier.weight(1f))
                JournalTab("FOUND", false, Modifier.weight(1f))
                JournalTab("MISSING", false, Modifier.weight(1f))
                JournalTab("RARE", false, Modifier.weight(1f))
            }

            // ---- Section rule ----
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).height(1.dp).background(EdgeSoft))
                Sprig(Moss, Modifier.size(width = 58.dp, height = 22.dp))
                Text("  THE GUIDE  ", style = Caps, color = Parchment)
                Sprig(Moss, Modifier.size(width = 58.dp, height = 22.dp), mirrored = true)
                Box(Modifier.weight(1f).height(1.dp).background(EdgeSoft))
            }
            Spacer(Modifier.height(14.dp))

            redesignItems.chunked(2).forEach { row ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { item -> Box(Modifier.weight(1f)) { GuideCard(item) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(26.dp))
        }

        // Lamplight falloff over the whole page.
        Vignette(Color(0xFF050703), Modifier.fillMaxSize(), strength = 0.62f)
    }
}

/**
 * Full-bleed header: the region as a night landscape, with the ranger's emblem and
 * progression sitting on it, torn away from the page below.
 */
@Composable
private fun RangerHeader() {
    Box(Modifier.fillMaxWidth().height(318.dp)) {
        LandscapeScene(
            sky = SkyTop,
            far = RidgeFar,
            mid = RidgeMid,
            near = RidgeNear,
            moon = Moonlight,
            modifier = Modifier.matchParentSize(),
        )
        // Keeps type legible over the busiest part of the scene.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x000E1209),
                        0.34f to Color(0x2B0E1209),
                        0.60f to Color(0xB80E1209),
                        1f to Color(0xCC0E1209),
                    )
                )
        )
        Grain(Modifier.matchParentSize(), alpha = 0.045f)

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 34.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WildlifeEmblem(
                    ring = Moss,
                    field = MossDeep,
                    art = Parchment,
                    accent = Brass,
                    modifier = Modifier.size(58.dp),
                )
                Spacer(Modifier.size(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("FIELD GUIDE", style = Caps, color = Moss)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Mediterranean Europe",
                        fontFamily = DisplayFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 21.sp,
                        lineHeight = 24.sp,
                        color = Parchment,
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RankPatch(
                        levelKey = "explorer",
                        border = Brass,
                        field = MossDeep,
                        art = Parchment,
                        modifier = Modifier.size(width = 42.dp, height = 50.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("EXPLORER", style = Caps, color = Brass)
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("8 OF 12 IN THIS REGION", style = Caps, color = ParchmentDim)
                Text("67%", style = Caps, color = Brass)
            }
            Spacer(Modifier.height(7.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(CircleShape)
                    .background(Color(0x99131A0C))
                    .border(1.dp, Color(0x668FB059), CircleShape),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(8f / 12f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(MossDeep, Moss)))
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat(StatMark.TICK, "9", "CONFIRMED", Moss, Modifier.weight(1f))
                Stat(StatMark.SPARKLE, "3", "RARE", Plum, Modifier.weight(1f))
                Stat(StatMark.ROSETTE, "1240", "XP", Brass, Modifier.weight(1f))
            }
        }

        // The page below is torn out from under the landscape.
        TornEdge(
            Ink,
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(24.dp),
            highlight = Color(0x3D8FB059),
        )
    }
}

/** A raking light sweep across the plate, marking a species of regional standing. */
@Composable
private fun PlateSheen(tint: Color, strength: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.linearGradient(
                0.00f to Color.Transparent,
                0.34f to tint.copy(alpha = strength * 0.35f),
                0.46f to tint.copy(alpha = strength),
                0.58f to tint.copy(alpha = strength * 0.30f),
                1.00f to Color.Transparent,
                start = Offset.Zero,
                end = Offset.Infinite,
            )
        )
    )
}

/**
 * Legendary gilding: a warm corner glow and a scatter of gold motes. In the shipping
 * app this is the natural place for a slow shimmer; here it is a still frame of it.
 */
@Composable
private fun LegendGilding(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            Brush.radialGradient(
                0f to Brass.copy(alpha = 0.17f),
                1f to Color.Transparent,
                center = Offset(size.width * 0.16f, size.height * 0.12f),
                radius = size.maxDimension * 0.55f,
            )
        )
        val rng = Random(19)
        repeat(7) {
            val x = size.width * (0.10f + rng.nextFloat() * 0.82f)
            val y = size.height * (0.10f + rng.nextFloat() * 0.80f)
            val r = size.minDimension * (0.014f + rng.nextFloat() * 0.020f)
            drawStar(
                Brass.copy(alpha = 0.35f + rng.nextFloat() * 0.45f),
                Offset(x, y),
                r,
                points = 4,
            )
        }
    }
}

/** A regional standing mark, matted so it stays readable over photography. */
@Composable
private fun RegionalChip(name: String, tint: Color) {
    Box(
        Modifier
            .size(23.dp)
            .clip(CircleShape)
            .background(Color(0xC20E1209))
            .border(1.dp, tint.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        FieldMark(name, tint, Modifier.size(14.dp))
    }
}

@Composable
private fun Stat(
    mark: StatMark,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.16f))
                .border(1.dp, tint.copy(alpha = 0.40f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            StatGlyph(mark, tint, Modifier.size(17.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            value,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = Parchment,
        )
        Spacer(Modifier.height(1.dp))
        Text(label, style = Caps, color = ParchmentDim)
    }
}

@Composable
private fun JournalTab(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) MossDeep else CardDeep)
            .border(1.dp, if (selected) Moss else EdgeSoft, RoundedCornerShape(9.dp))
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Caps, color = if (selected) Parchment else ParchmentDim)
    }
}

@Composable
private fun GuideCard(item: RedesignItem) {
    val tint = rarityTint(item.rarity)
    val group = item.group ?: "mammals"
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(
                width = if (item.legendary) 2.dp else 1.5.dp,
                color = regionalBorder(item) ?: EdgeSoft,
                shape = RoundedCornerShape(14.dp),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.32f),
        ) {
            if (item.collected) {
                PhotoPlate(
                    key = item.key,
                    collected = true,
                    saturation = 0.30f,
                    lightness = 0.34f,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Not yet found: an unfilled plate in the guide, never a locked wall.
                Box(Modifier.fillMaxSize().background(CardDeep)) {
                    ContourField(Contour, Modifier.fillMaxSize(), lines = 5, seed = item.key.length)
                    TaxonSilhouette(
                        group,
                        Color(0xFF2E3A22),
                        Modifier.fillMaxSize().padding(18.dp),
                    )
                }
            }
            // Icons get a cool sheen; legendaries get a warmer one plus gilding.
            if (item.collected && (item.icon || item.legendary)) {
                PlateSheen(
                    tint = if (item.legendary) Brass else Parchment,
                    strength = if (item.legendary) 0.20f else 0.16f,
                    modifier = Modifier.matchParentSize(),
                )
            }
            if (item.collected && item.legendary) {
                LegendGilding(Modifier.matchParentSize())
            }
            if (item.confirmed && item.collected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(23.dp)
                        .clip(CircleShape)
                        .background(Color(0xE63E5A2C))
                        .border(1.dp, Moss, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    StatGlyph(StatMark.TICK, Parchment, Modifier.size(11.dp))
                }
            }
            Column(
                Modifier.align(Alignment.TopStart).padding(7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // `regional_legend.svg` became `regional_icon.svg` when the Legend tier
                // was removed. Same artwork, so this study's Legend chip is unchanged.
                if (item.legendary) RegionalChip("regional_icon", Brass)
                if (item.icon) RegionalChip("regional_icon", Parchment)
                if (item.essential) RegionalChip("regional_essential", Moss)
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(7.dp)
                    .size(26.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xD90E1209))
                    .border(1.dp, Color(0x33EFE7D2), RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TaxonSilhouette(group, Parchment.copy(alpha = 0.85f), Modifier.size(17.dp))
            }
        }

        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                item.label,
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 17.sp,
                color = if (item.collected) Parchment else ParchmentDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(tint.copy(alpha = 0.16f))
                        .padding(start = 5.dp, end = 6.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        rarityMark(item.rarity)?.takeIf { !item.awaiting }?.let {
                            FieldMark(it, tint, Modifier.size(11.dp))
                            Spacer(Modifier.size(4.dp))
                        }
                        Text(
                            if (item.awaiting) "AWAITING ID" else rarityWord(item.rarity),
                            style = Caps,
                            color = tint,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF232C18)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(if (item.collected) 0.35f else 0f)
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(tint)
                )
            }
        }
    }
}
