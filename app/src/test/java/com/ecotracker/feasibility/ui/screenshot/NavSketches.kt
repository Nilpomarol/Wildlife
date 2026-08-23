package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.art.Grain
import com.wildlife.feasibility.ui.art.NavMark
import com.wildlife.feasibility.ui.navigation.WildlifeDestination
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeOlive
import com.wildlife.feasibility.ui.theme.WildlifeOliveDark
import com.wildlife.feasibility.ui.theme.WildlifeOliveStrong
import com.wildlife.feasibility.ui.theme.WildlifeOutline
import com.wildlife.feasibility.ui.theme.WildlifeOutlineSubtle
import com.wildlife.feasibility.ui.theme.WildlifeParchment
import com.wildlife.feasibility.ui.theme.WildlifeTextMuted
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * Four structural studies for the navigation bar, to be reviewed as screenshots and then
 * thrown away or promoted — the same sandbox arrangement as the Direction A–D studies.
 *
 * The first attempt kept the Material shape (five equal cells around a raised centre
 * button) and restyled it, which is exactly what was rejected. Each of these changes the
 * *structure*, not the paint:
 *
 * - **A Thumb index** — contiguous page-edge tabs, selection by inversion, no button.
 * - **B Parchment strip** — the bar is paper, not shadow; dark ink on cream.
 * - **C Field desk** — asymmetric: four places on the left, Capture is a stamp on the right.
 * - **D Stitched binding** — the bar is the journal's sewn edge; selection is a thread.
 *
 * Labels are always shown and marks are the current drawn placeholders; the owner's
 * artwork drops in over them later without affecting any of these structures.
 */

private val SketchLabel = FieldLabelStyle.copy(letterSpacing = 0.8.sp)

private val Places = WildlifeDestination.entries.filter { it != WildlifeDestination.CAPTURE }

@Composable
private fun SketchLabelText(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = SketchLabel,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------- A · Thumb index

/**
 * The page-edge thumb index of a printed guide: five blocks butted together, divided by
 * hairlines, with no gaps and nothing floating.
 *
 * Selection is **inversion** — the open page's tab is the printed one, so it is cream with
 * dark ink while its neighbours are the page's own dark ground. Capture is a block like
 * any other, permanently olive, because a raised circle is the single most Material thing
 * a bottom bar can contain.
 */
@Composable
fun NavSketchThumbIndex(selected: WildlifeDestination) {
    val colors = WildlifeTheme.colors
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        Row(Modifier.fillMaxSize()) {
            WildlifeDestination.entries.forEachIndexed { index, destination ->
                val isCapture = destination == WildlifeDestination.CAPTURE
                val isSelected = destination == selected
                val fill = when {
                    isCapture -> WildlifeOlive
                    isSelected -> WildlifeParchment
                    else -> WildlifeBackground.copy(alpha = 0.97f)
                }
                val ink = when {
                    isCapture -> WildlifeParchment
                    isSelected -> WildlifeBackground
                    else -> colors.parchmentDim.copy(alpha = 0.70f)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(fill),
                    contentAlignment = Alignment.Center,
                ) {
                    Grain(
                        if (isSelected && !isCapture) WildlifeBackground else colors.parchment,
                        Modifier.fillMaxSize(),
                        alpha = 0.035f,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NavMark(destination.mark, destination.route, ink, Modifier.size(23.dp))
                        Spacer(Modifier.height(6.dp))
                        SketchLabelText(destination.label, ink)
                    }
                    // A hairline between blocks, drawn on the leading edge only so the
                    // index reads as cut page edges rather than as five separate chips.
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(WildlifeOutlineSubtle)
                                .align(Alignment.CenterStart)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- B · Parchment strip

/**
 * The bar as paper: a strip of the guide's own stock along the bottom of the dark page,
 * closed by a printed double rule.
 *
 * Inverting the value is the strongest available way to say "this is not a system chrome
 * surface". Selection is olive ink plus a rule under the word, the way a printed index
 * underscores the current section; Capture is a stamped block.
 */
@Composable
fun NavSketchParchmentStrip(selected: WildlifeDestination) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(WildlifeParchment),
    ) {
        Grain(WildlifeBackground, Modifier.fillMaxSize(), alpha = 0.05f)
        // The printed double rule: one firm, one hairline, as a page edge is set.
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            drawLine(
                WildlifeOliveDark,
                Offset(0f, 0.8f.dp.toPx()),
                Offset(size.width, 0.8f.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
            )
            drawLine(
                WildlifeOliveDark.copy(alpha = 0.45f),
                Offset(0f, 4.6f.dp.toPx()),
                Offset(size.width, 4.6f.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }
        Row(Modifier.fillMaxSize().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            WildlifeDestination.entries.forEach { destination ->
                if (destination == WildlifeDestination.CAPTURE) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(9.dp))
                                .background(WildlifeOliveDark),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            NavMark(
                                destination.mark,
                                destination.route,
                                WildlifeParchment,
                                Modifier.size(22.dp),
                            )
                            Spacer(Modifier.height(5.dp))
                            SketchLabelText(destination.label, WildlifeParchment)
                        }
                    }
                } else {
                    val isSelected = destination == selected
                    val ink = if (isSelected) WildlifeOliveDark else WildlifeBackground.copy(alpha = 0.48f)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NavMark(destination.mark, destination.route, ink, Modifier.size(23.dp))
                        Spacer(Modifier.height(6.dp))
                        SketchLabelText(destination.label, ink)
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .height(2.dp)
                                .width(if (isSelected) 26.dp else 0.dp)
                                .background(WildlifeOliveDark)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- C · Field desk

/**
 * Asymmetric, because the row is not five of one thing: it is four places and one action,
 * and spacing them evenly is what makes every bottom bar look the same.
 *
 * The four places run as a quiet group on the left. Capture is a stamp block on the right
 * — the shape a "new entry" control takes in a paper ledger — carrying its own word, so it
 * needs no raised disc to be found.
 */
@Composable
fun NavSketchFieldDesk(selected: WildlifeDestination) {
    val colors = WildlifeTheme.colors
    Box(Modifier.fillMaxWidth().height(72.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to WildlifeBackground.copy(alpha = 0.90f),
                        1f to WildlifeBackground.copy(alpha = 0.995f),
                    )
                )
        )
        Grain(colors.parchment, Modifier.fillMaxSize(), alpha = 0.026f)
        Box(Modifier.fillMaxWidth().height(1.dp).background(WildlifeOutline))

        Row(
            Modifier.fillMaxSize().padding(start = 4.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Places.forEach { destination ->
                val isSelected = destination == selected
                val ink = if (isSelected) colors.parchment else colors.parchmentDim.copy(alpha = 0.62f)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // A struck dot above the mark: the reader's pencil tick against the
                    // section they are in. Small, because the ink change already says it.
                    Box(
                        Modifier
                            .size(if (isSelected) 4.dp else 0.dp)
                            .background(WildlifeOliveStrong, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(if (isSelected) 4.dp else 8.dp))
                    NavMark(destination.mark, destination.route, ink, Modifier.size(23.dp))
                    Spacer(Modifier.height(5.dp))
                    SketchLabelText(destination.label, if (isSelected) colors.parchment else colors.parchmentFaint)
                }
            }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .width(104.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WildlifeOliveDark)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                NavMark(
                    WildlifeDestination.CAPTURE.mark,
                    WildlifeDestination.CAPTURE.route,
                    colors.parchment,
                    Modifier.size(21.dp),
                )
                Spacer(Modifier.width(7.dp))
                SketchLabelText(WildlifeDestination.CAPTURE.label, colors.parchment)
            }
        }
    }
}

// ---------------------------------------------------------------- D · Stitched binding

/**
 * The bar as the journal's sewn edge: two rules with stitch ticks running between them,
 * the marks sitting in the band.
 *
 * Selection is a **thread** — the stitching under the current place runs solid olive
 * instead of broken. Capture sits in the centre as a knot: a small ringed disc on the
 * band, level with everything else rather than floating above it.
 */
@Composable
fun NavSketchStitchedBinding(selected: WildlifeDestination) {
    val colors = WildlifeTheme.colors
    Box(Modifier.fillMaxWidth().height(74.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to WildlifeBackground.copy(alpha = 0.90f),
                        1f to WildlifeBackground.copy(alpha = 0.995f),
                    )
                )
        )
        Grain(colors.parchment, Modifier.fillMaxSize(), alpha = 0.026f)

        val selectedIndex = WildlifeDestination.entries.indexOf(selected)
        Canvas(Modifier.fillMaxSize()) {
            val topRule = 0.5f.dp.toPx()
            val stitchRule = 46.dp.toPx()
            drawLine(
                WildlifeOutline,
                Offset(0f, topRule),
                Offset(size.width, topRule),
                strokeWidth = 1.dp.toPx(),
            )
            // Broken stitching along the lower rule, solid under the open page.
            val cell = size.width / WildlifeDestination.entries.size
            val dash = 7.dp.toPx()
            val gap = 5.dp.toPx()
            var x = 0f
            while (x < size.width) {
                val inSelected = x >= cell * selectedIndex && x < cell * (selectedIndex + 1)
                drawLine(
                    if (inSelected) WildlifeOliveStrong else WildlifeTextMuted.copy(alpha = 0.85f),
                    Offset(x, stitchRule),
                    Offset(minOf(x + dash, size.width), stitchRule),
                    strokeWidth = if (inSelected) 2.4.dp.toPx() else 1.4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                x += dash + gap
            }
        }

        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Top) {
            WildlifeDestination.entries.forEach { destination ->
                val isCapture = destination == WildlifeDestination.CAPTURE
                val isSelected = destination == selected
                val ink = when {
                    isCapture -> colors.parchment
                    isSelected -> colors.parchment
                    else -> colors.parchmentDim.copy(alpha = 0.62f)
                }
                Column(
                    Modifier.weight(1f).padding(top = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (isCapture) {
                        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val r = size.minDimension / 2f
                                val c = Offset(size.width / 2f, size.height / 2f)
                                drawCircle(WildlifeOliveDark, radius = r, center = c)
                                drawCircle(
                                    WildlifeOliveStrong,
                                    radius = r,
                                    center = c,
                                    style = Stroke(width = r * 0.14f),
                                )
                            }
                            NavMark(destination.mark, destination.route, ink, Modifier.size(15.dp))
                        }
                    } else {
                        NavMark(destination.mark, destination.route, ink, Modifier.size(23.dp))
                    }
                    Spacer(Modifier.height(15.dp))
                    SketchLabelText(
                        destination.label,
                        when {
                            isCapture -> WildlifeOliveStrong
                            isSelected -> colors.parchment
                            else -> colors.parchmentFaint
                        },
                    )
                }
            }
        }
    }
}
