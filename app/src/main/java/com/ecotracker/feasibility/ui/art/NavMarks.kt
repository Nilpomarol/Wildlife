package com.wildlife.feasibility.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The navigation bar's mark set.
 *
 * The journal carries no Material icons anywhere else (`docs/style.md` §30), and the bar
 * was the last place they survived — a stock Home/PhotoLibrary/Person row under a
 * hand-drawn field guide read as two apps stacked on top of each other.
 *
 * Every mark here is stroke-drawn at a single weight so the row looks engraved by one
 * hand, and each is a *place in the field*, not a UI abstraction: a basecamp cabin, an
 * open guide, a field camera, a compass, a ranger's hat.
 *
 * ## Supplying real artwork
 *
 * [NavMark] prefers `assets/nav_marks/<key>.svg` and only falls back to the drawn mark
 * when the file is absent — the same contract as [RegionMark] and [RankBadgeArt]. Drop in
 * a single-path Inkscape export named for the destination key (`home`, `collection`,
 * `capture`, `explore`, `profile`) and it replaces the drawing with no code change.
 */

/** Keys are the [com.wildlife.feasibility.ui.navigation.WildlifeDestination] routes. */
enum class NavMarkKind { HOME, COLLECTION, CAPTURE, EXPLORE, PROFILE }

/**
 * A navigation mark, from bundled artwork where it exists and drawn otherwise.
 *
 * Unlike the region and rank marks this never renders empty, so it takes no boolean
 * return: the bar must always have five legible icons.
 */
@Composable
fun NavMark(
    kind: NavMarkKind,
    assetKey: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val supplied = rememberSvgAssetPath("nav_marks/$assetKey.svg")
    // Fitting art into a box preserves its aspect, which means a wide mark and a tall mark
    // given the same box cover very different amounts of it and read at different weights.
    // Scaling by the square root of the aspect deviation holds the covered *area* roughly
    // constant instead, so a row of mixed-proportion artwork sits at one optical size.
    //
    // Done here rather than by sizing each mark at the call site, so the bar keeps its
    // drop-in contract: new artwork of any proportion balances itself.
    val gain = if (supplied == null) 1f else remember(supplied) {
        val b = supplied.getBounds()
        if (b.width <= 0f || b.height <= 0f) {
            1f
        } else {
            val aspect = b.width / b.height
            sqrt(max(aspect, 1f / aspect))
        }
    }
    Canvas(modifier.scale(gain)) {
        if (supplied != null) {
            drawFittedPath(supplied, color, SvgOrigin.INKSCAPE)
        } else {
            drawNavMark(kind, color)
        }
    }
}

fun DrawScope.drawNavMark(kind: NavMarkKind, color: Color) {
    when (kind) {
        NavMarkKind.HOME -> drawCabinMark(color)
        NavMarkKind.COLLECTION -> drawGuideMark(color)
        NavMarkKind.CAPTURE -> drawCameraMark(color)
        NavMarkKind.EXPLORE -> drawCompassMark(color)
        NavMarkKind.PROFILE -> drawRangerMark(color)
    }
}

// ---------------------------------------------------------------- drawing helpers

/** One weight for the whole set, so no mark looks bolder than its neighbours. */
private val DrawScope.markStroke: Float
    get() = minOf(size.width, size.height) * 0.085f

private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.strokeMark(color: Color, width: Float = markStroke, block: Path.() -> Unit) {
    drawPath(
        Path().apply(block),
        color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun Path.moveTo(p: Offset) = moveTo(p.x, p.y)
private fun Path.lineTo(p: Offset) = lineTo(p.x, p.y)
private fun Path.cubicTo(a: Offset, b: Offset, c: Offset) = cubicTo(a.x, a.y, b.x, b.y, c.x, c.y)

// ---------------------------------------------------------------- the marks

/** Basecamp: a ranger's cabin with a lit door and a chimney. */
private fun DrawScope.drawCabinMark(color: Color) {
    strokeMark(color) {
        moveTo(at(0.10f, 0.50f))
        lineTo(at(0.50f, 0.17f))
        lineTo(at(0.90f, 0.50f))
    }
    strokeMark(color) {
        moveTo(at(0.21f, 0.45f))
        lineTo(at(0.21f, 0.85f))
        lineTo(at(0.79f, 0.85f))
        lineTo(at(0.79f, 0.45f))
    }
    // The doorway is the detail that keeps this from reading as a plain roof glyph.
    strokeMark(color, markStroke * 0.82f) {
        moveTo(at(0.42f, 0.85f))
        lineTo(at(0.42f, 0.62f))
        lineTo(at(0.58f, 0.62f))
        lineTo(at(0.58f, 0.85f))
    }
    strokeMark(color, markStroke * 0.78f) {
        moveTo(at(0.69f, 0.31f))
        lineTo(at(0.69f, 0.13f))
    }
}

/** The guide: an open book, spine centred, one ruled line per page. */
private fun DrawScope.drawGuideMark(color: Color) {
    listOf(-1f, 1f).forEach { side ->
        strokeMark(color) {
            moveTo(at(0.5f, 0.30f))
            cubicTo(
                at(0.5f + side * 0.13f, 0.21f),
                at(0.5f + side * 0.29f, 0.19f),
                at(0.5f + side * 0.42f, 0.24f),
            )
            lineTo(at(0.5f + side * 0.42f, 0.72f))
            cubicTo(
                at(0.5f + side * 0.29f, 0.67f),
                at(0.5f + side * 0.13f, 0.69f),
                at(0.5f, 0.78f),
            )
        }
        // Ruled lines, held back so the page shape still reads first at 22dp.
        strokeMark(color.copy(alpha = 0.55f), markStroke * 0.62f) {
            moveTo(at(0.5f + side * 0.14f, 0.47f))
            lineTo(at(0.5f + side * 0.33f, 0.44f))
        }
    }
    strokeMark(color, markStroke * 0.85f) {
        moveTo(at(0.5f, 0.30f))
        lineTo(at(0.5f, 0.78f))
    }
}

/** A field camera: body, viewfinder hump, lens. */
private fun DrawScope.drawCameraMark(color: Color) {
    val r = minOf(size.width, size.height)
    strokeMark(color) {
        moveTo(at(0.34f, 0.30f))
        lineTo(at(0.41f, 0.17f))
        lineTo(at(0.61f, 0.17f))
        lineTo(at(0.68f, 0.30f))
    }
    drawRoundRect(
        color = color,
        topLeft = at(0.09f, 0.29f),
        size = Size(size.width * 0.82f, size.height * 0.54f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.13f),
        style = Stroke(width = markStroke, join = StrokeJoin.Round),
    )
    drawCircle(color, radius = r * 0.16f, center = at(0.5f, 0.56f), style = Stroke(markStroke))
    drawCircle(color, radius = r * 0.038f, center = at(0.76f, 0.40f))
}

/** A compass rose: ring plus a weighted needle, north lit and south held back. */
private fun DrawScope.drawCompassMark(color: Color) {
    val r = minOf(size.width, size.height)
    val c = at(0.5f, 0.5f)
    drawCircle(color, radius = r * 0.40f, center = c, style = Stroke(markStroke))

    // Off-axis, the way a needle actually sits — a needle drawn straight up reads as an
    // arrow, not a compass.
    val heading = -PI / 4
    fun point(angle: Double, radius: Float) =
        Offset(c.x + (radius * cos(angle)).toFloat(), c.y + (radius * sin(angle)).toFloat())

    listOf(heading to 1f, heading + PI to 0.42f).forEach { (angle, alpha) ->
        val needle = Path().apply {
            moveTo(point(angle, r * 0.30f))
            lineTo(point(angle + PI / 2, r * 0.10f))
            lineTo(point(angle - PI / 2, r * 0.10f))
            close()
        }
        drawPath(needle, color.copy(alpha = alpha))
    }
}

/** A ranger's campaign hat: brim, crown and hatband. */
private fun DrawScope.drawRangerMark(color: Color) {
    drawOval(
        color = color,
        topLeft = at(0.06f, 0.58f),
        size = Size(size.width * 0.88f, size.height * 0.24f),
        style = Stroke(width = markStroke),
    )
    strokeMark(color) {
        moveTo(at(0.29f, 0.66f))
        cubicTo(at(0.28f, 0.36f), at(0.36f, 0.24f), at(0.50f, 0.24f))
        cubicTo(at(0.64f, 0.24f), at(0.72f, 0.36f), at(0.71f, 0.66f))
    }
    strokeMark(color, markStroke * 0.72f) {
        moveTo(at(0.29f, 0.55f))
        lineTo(at(0.71f, 0.55f))
    }
}
