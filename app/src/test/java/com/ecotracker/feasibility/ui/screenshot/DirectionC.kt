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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.EncounterRarity
import com.wildlife.feasibility.ui.theme.DisplayFontFamily

/**
 * Direction C - "Specimen Case".
 * A warm, lit cabinet: brass rules, recessed velvet slots and a dense tiled grid.
 * Rarity is carried by the frame around a specimen, never by badges stuck on top.
 */

private val Case = Color(0xFF0F0B07)
private val Velvet = Color(0xFF19120B)
private val VelvetDeep = Color(0xFF120D08)
private val Brass = Color(0xFFB98A3F)
private val BrassDim = Color(0xFF5A431F)
private val Amber = Color(0xFFEDA92C)
private val Bone = Color(0xFFF2E7D2)
private val BoneDim = Color(0xFF9A8B72)

private val Engraved = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 9.sp,
    lineHeight = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.8.sp,
)

private fun frameColor(item: RedesignItem) = when {
    !item.collected -> Color(0xFF241A10)
    item.legendary -> Amber
    item.rarity == EncounterRarity.VERY_RARE -> Amber
    item.rarity == EncounterRarity.RARE -> Brass
    item.rarity == EncounterRarity.UNCOMMON -> BrassDim
    else -> Color(0xFF3A2C19)
}

@Composable
fun DirectionCScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Case)
            .verticalScroll(rememberScrollState()),
    ) {
        // Case label plaque.
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Velvet, Case))
                )
                .padding(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 18.dp),
        ) {
            Text("CASE I", style = Engraved, color = Brass)
            Spacer(Modifier.height(8.dp))
            Text(
                "Mediterranean Europe",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                color = Bone,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Eight of twelve specimens acquired",
                fontFamily = FontFamily.SansSerif,
                fontStyle = FontStyle.Italic,
                fontSize = 13.sp,
                color = BoneDim,
            )
            Spacer(Modifier.height(16.dp))
            BrassRule()
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Plaque("NOVICE", "RANK")
                Plaque("9", "CONFIRMED")
                Plaque("3", "RARE")
                Plaque("1240", "XP")
            }
            Spacer(Modifier.height(14.dp))
            BrassRule()
        }

        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CaseTab("ALL", true, Modifier.weight(1f))
            CaseTab("ACQUIRED", false, Modifier.weight(1f))
            CaseTab("VACANT", false, Modifier.weight(1f))
            CaseTab("RARE", false, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))

        // Dense three-up grid so the case reads as a full cabinet at a glance.
        redesignItems.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { item -> Box(Modifier.weight(1f)) { SpecimenSlot(item) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun BrassRule() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BrassDim))
}

@Composable
private fun Plaque(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = Amber,
        )
        Spacer(Modifier.height(3.dp))
        Text(label, style = Engraved, color = BoneDim)
    }
}

@Composable
private fun CaseTab(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = Engraved, color = if (selected) Amber else BoneDim)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(if (selected) Amber else Color.Transparent)
        )
    }
}

@Composable
private fun SpecimenSlot(item: RedesignItem) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.92f)
                .background(if (item.collected) Velvet else VelvetDeep)
                .border(if (item.legendary) 2.dp else 1.dp, frameColor(item))
                .padding(if (item.legendary) 3.dp else 4.dp),
        ) {
            if (item.collected) {
                PhotoPlate(
                    key = item.key,
                    collected = true,
                    saturation = 0.26f,
                    lightness = 0.30f,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    "VACANT",
                    style = Engraved,
                    color = Color(0xFF3A2C19),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (item.awaiting) {
                Text(
                    "?",
                    fontFamily = DisplayFontFamily,
                    fontSize = 26.sp,
                    color = Brass,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.label,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            color = if (item.collected) Bone else BoneDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
