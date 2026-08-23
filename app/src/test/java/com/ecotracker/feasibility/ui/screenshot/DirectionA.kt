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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.theme.DisplayFontFamily

/**
 * Direction A — "Naturalist's Plate".
 * A light, printed-page inversion of the app: warm paper, ink type, hairline rules and
 * numbered specimen plates. Restraint and editorial hierarchy instead of badges.
 */

private val Paper = Color(0xFFF3EDE1)
private val PaperDeep = Color(0xFFE8E0CF)
private val Ink = Color(0xFF191610)
private val InkSoft = Color(0xFF6B6355)
private val Rule = Color(0xFFCFC5AF)
private val Seal = Color(0xFF8A5A2B)

private val SmallCaps = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 10.sp,
    lineHeight = 13.sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = 1.6.sp,
)

@Composable
fun DirectionAScreen() {
    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp)) {
            Text("MEDITERRANEAN EUROPE · EDITION 2025.1", style = SmallCaps, color = InkSoft)
            Spacer(Modifier.height(10.dp))
            Text(
                "The Collection",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                color = Ink,
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "08",
                    fontFamily = DisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 52.sp,
                    lineHeight = 52.sp,
                    color = Ink,
                )
                Text(
                    " / 12 species recorded",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = InkSoft,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            // Progress as a printed measure rule rather than a game bar.
            Row(Modifier.fillMaxWidth().height(6.dp)) {
                Box(Modifier.weight(8f).fillMaxSize().background(Ink))
                Box(Modifier.weight(4f).fillMaxSize().background(Rule))
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("NOVICE", style = SmallCaps, color = Ink)
                Text("2 MORE TO OBSERVER", style = SmallCaps, color = InkSoft)
            }
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                EditorialTab("ALL", true)
                EditorialTab("RECORDED", false)
                EditorialTab("MISSING", false)
                EditorialTab("RARE", false)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
        }

        redesignItems.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEachIndexed { i, item ->
                    Box(Modifier.weight(1f)) { PlateCard(item) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun EditorialTab(label: String, selected: Boolean) {
    Column {
        Text(label, style = SmallCaps, color = if (selected) Ink else InkSoft)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .width(if (selected) 22.dp else 0.dp)
                .height(2.dp)
                .background(Seal)
        )
    }
}

@Composable
private fun PlateCard(item: RedesignItem) {
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.86f)
                .background(if (item.collected) PaperDeep else Paper)
                .border(1.dp, if (item.collected) Ink else Rule),
        ) {
            if (item.collected) {
                PhotoPlate(
                    key = item.key,
                    collected = true,
                    saturation = 0.22f,
                    lightness = 0.58f,
                    modifier = Modifier.fillMaxSize().padding(7.dp),
                )
            }
            // Plate number, letterpress style, bottom-left inside the frame.
            Text(
                text = "No. %02d".format(redesignItems.indexOf(item) + 1),
                style = SmallCaps,
                color = if (item.collected) Paper else InkSoft,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
            if (item.legendary) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Seal)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) { Text("LEGEND", style = SmallCaps, color = Paper) }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            item.label,
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            lineHeight = 21.sp,
            color = if (item.collected) Ink else InkSoft,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            if (item.awaiting) "Awaiting identification" else item.rarity.shortLabel(),
            fontFamily = FontFamily.SansSerif,
            fontStyle = FontStyle.Italic,
            fontSize = 12.sp,
            color = InkSoft,
        )
    }
}
