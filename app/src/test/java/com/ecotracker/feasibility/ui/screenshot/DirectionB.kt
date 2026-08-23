package com.wildlife.feasibility.ui.screenshot

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.EncounterRarity

/**
 * Direction B - "Expedition".
 * Cold ink-blue night ground, photography edge to edge, one high-visibility signal
 * accent, and data presented as instrument readouts rather than game chips.
 */

private val Night = Color(0xFF080D12)
private val NightRaised = Color(0xFF0E151C)
private val Steel = Color(0xFF1B2833)
private val Frost = Color(0xFFE8F0F5)
private val Haze = Color(0xFF7C8D9B)
private val Signal = Color(0xFFFF5F3D)

private val Mono = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.4.sp,
)

private fun rarityColor(r: EncounterRarity?) = when (r) {
    EncounterRarity.VERY_RARE -> Signal
    EncounterRarity.RARE -> Color(0xFFFFA03D)
    EncounterRarity.UNCOMMON -> Color(0xFF54C7B0)
    EncounterRarity.COMMON -> Color(0xFF44586B)
    else -> Steel
}

@Composable
fun DirectionBScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Night)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header band - reads like a trip readout, not a dashboard card.
        Column(
            Modifier
                .fillMaxWidth()
                .background(NightRaised)
                .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 20.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MEDITERRANEAN EUROPE", style = Mono, color = Signal)
                Text("2025.1", style = Mono, color = Haze)
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "8",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 68.sp,
                    lineHeight = 66.sp,
                    letterSpacing = (-3).sp,
                    color = Frost,
                )
                Text(
                    "/12",
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = Haze,
                    modifier = Modifier.padding(bottom = 9.dp, start = 2.dp),
                )
                Spacer(Modifier.weight(1f))
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Text("RANK", style = Mono, color = Haze)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "NOVICE",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = Frost,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Segmented progress - one notch per species, an instrument not a bar.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(12) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(if (i < 8) Signal else Steel)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                Readout("9", "CONFIRMED", Modifier.weight(1f))
                VDivider()
                Readout("3", "RARE", Modifier.weight(1f))
                VDivider()
                Readout("1240", "XP", Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SquareTab("ALL", true)
            SquareTab("FOUND", false)
            SquareTab("MISSING", false)
            SquareTab("RARE", false)
        }
        Spacer(Modifier.height(18.dp))

        redesignItems.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { item -> Box(Modifier.weight(1f)) { ExpeditionCard(item) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun VDivider() {
    Box(Modifier.width(1.dp).height(34.dp).background(Steel))
}

@Composable
private fun Readout(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = Frost,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = Mono, color = Haze)
    }
}

@Composable
private fun SquareTab(label: String, selected: Boolean) {
    Box(
        Modifier
            .background(if (selected) Signal else Color.Transparent)
            .border(1.dp, if (selected) Signal else Steel)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(label, style = Mono, color = if (selected) Night else Haze)
    }
}

@Composable
private fun ExpeditionCard(item: RedesignItem) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clip(RoundedCornerShape(2.dp))
            .background(NightRaised),
    ) {
        PhotoPlate(
            key = item.key,
            collected = item.collected,
            saturation = 0.34f,
            lightness = 0.34f,
            modifier = Modifier.fillMaxSize(),
        )
        // Scrim so the name always stays readable over photography.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.35f to Color.Transparent,
                        1f to Color(0xF2060A0E),
                    )
                )
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(3.dp)
                .background(rarityColor(item.rarity))
        )
        if (!item.collected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .border(1.dp, Haze)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) { Text("?", style = Mono, color = Haze) }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(11.dp),
        ) {
            Text(
                if (item.awaiting) "AWAITING ID" else item.rarity.shortLabel().uppercase(),
                style = Mono,
                color = if (item.collected) rarityColor(item.rarity) else Haze,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item.label,
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 19.sp,
                letterSpacing = (-0.3).sp,
                color = if (item.collected) Frost else Haze,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.legendary) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 11.dp, start = 8.dp)
                    .background(Signal)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            ) { Text("LEGEND", style = Mono, color = Night) }
        }
    }
}
