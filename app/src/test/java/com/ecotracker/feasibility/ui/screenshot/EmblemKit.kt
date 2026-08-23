package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hand-drawn scout-patch emblem set for the Ranger's Journal direction.
 *
 * Everything here is drawn with paths rather than pulled from Material icons, so the
 * badge language belongs to this app instead of reading as generic UI furniture.
 */

// ---------------------------------------------------------------- primitives

/** A paw print: pad plus four toes on an arc. */
fun DrawScope.drawPaw(color: Color, center: Offset, radius: Float) {
    val pad = Path().apply {
        val w = radius * 0.92f
        val h = radius * 0.72f
        val cy = center.y + radius * 0.42f
        moveTo(center.x - w * 0.5f, cy)
        cubicTo(
            center.x - w * 0.58f, cy - h * 0.62f,
            center.x + w * 0.58f, cy - h * 0.62f,
            center.x + w * 0.5f, cy,
        )
        cubicTo(
            center.x + w * 0.46f, cy + h * 0.70f,
            center.x - w * 0.46f, cy + h * 0.70f,
            center.x - w * 0.5f, cy,
        )
        close()
    }
    drawPath(pad, color)

    // Toes sweep across the top; the outer pair sit lower and smaller.
    val toes = listOf(-1.00f to 0.80f, -0.40f to 1.00f, 0.40f to 1.00f, 1.00f to 0.80f)
    toes.forEach { (offset, scale) ->
        val tx = center.x + offset * radius * 0.62f
        val ty = center.y - radius * (0.40f + (1f - scale) * 0.55f)
        drawOval(
            color = color,
            topLeft = Offset(tx - radius * 0.20f * scale, ty - radius * 0.26f * scale),
            size = Size(radius * 0.40f * scale, radius * 0.52f * scale),
        )
    }
}

/** A single conifer, drawn as three stacked tiers on a short trunk. */
fun DrawScope.drawPine(color: Color, baseCenter: Offset, width: Float, height: Float) {
    val trunkW = width * 0.14f
    drawRect(
        color = color,
        topLeft = Offset(baseCenter.x - trunkW / 2f, baseCenter.y - height * 0.16f),
        size = Size(trunkW, height * 0.18f),
    )
    val tiers = listOf(0.00f to 1.00f, 0.30f to 0.78f, 0.58f to 0.54f)
    tiers.forEach { (lift, spread) ->
        val p = Path()
        val bottom = baseCenter.y - height * lift
        p.moveTo(baseCenter.x - width * 0.5f * spread, bottom)
        p.lineTo(baseCenter.x, bottom - height * 0.46f)
        p.lineTo(baseCenter.x + width * 0.5f * spread, bottom)
        p.close()
        drawPath(p, color)
    }
}

/** A mountain pair used inside emblems. */
fun DrawScope.drawPeaks(color: Color, base: Float, width: Float, height: Float, cx: Float) {
    val p = Path()
    p.moveTo(cx - width * 0.5f, base)
    p.lineTo(cx - width * 0.16f, base - height)
    p.lineTo(cx + width * 0.04f, base - height * 0.55f)
    p.lineTo(cx + width * 0.22f, base - height * 0.86f)
    p.lineTo(cx + width * 0.5f, base)
    p.close()
    drawPath(p, color)
}

/** A pointed-bottom heraldic shield outline. */
private fun shieldPath(w: Float, h: Float, inset: Float = 0f): Path {
    val l = inset
    val r = w - inset
    val t = inset
    val b = h - inset
    return Path().apply {
        moveTo(l, t + h * 0.06f)
        lineTo(r, t + h * 0.06f)
        lineTo(r, b - h * 0.34f)
        cubicTo(r, b - h * 0.12f, (l + r) / 2f + w * 0.16f, b, (l + r) / 2f, b)
        cubicTo((l + r) / 2f - w * 0.16f, b, l, b - h * 0.12f, l, b - h * 0.34f)
        close()
    }
}

/** An n-pointed star. */
fun DrawScope.drawStar(color: Color, center: Offset, outer: Float, points: Int = 5) {
    val inner = outer * 0.44f
    val p = Path()
    repeat(points * 2) { i ->
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / points
        val x = center.x + (r * cos(a)).toFloat()
        val y = center.y + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

/** A four-point sparkle, for the rare-finds readout. */
fun DrawScope.drawSparkle(color: Color, center: Offset, radius: Float) {
    val p = Path()
    val waist = radius * 0.22f
    p.moveTo(center.x, center.y - radius)
    p.cubicTo(
        center.x + waist, center.y - waist,
        center.x + waist, center.y - waist,
        center.x + radius, center.y,
    )
    p.cubicTo(
        center.x + waist, center.y + waist,
        center.x + waist, center.y + waist,
        center.x, center.y + radius,
    )
    p.cubicTo(
        center.x - waist, center.y + waist,
        center.x - waist, center.y + waist,
        center.x - radius, center.y,
    )
    p.cubicTo(
        center.x - waist, center.y - waist,
        center.x - waist, center.y - waist,
        center.x, center.y - radius,
    )
    p.close()
    drawPath(p, color)
}

/** A field tick, drawn with a weighted stroke rather than an icon glyph. */
fun DrawScope.drawTick(color: Color, center: Offset, radius: Float) {
    val p = Path()
    p.moveTo(center.x - radius * 0.62f, center.y + radius * 0.04f)
    p.lineTo(center.x - radius * 0.16f, center.y + radius * 0.50f)
    p.lineTo(center.x + radius * 0.66f, center.y - radius * 0.52f)
    drawPath(
        p,
        color,
        style = Stroke(width = radius * 0.30f, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** A rosette: medal disc with two ribbon tails, for the XP readout. */
fun DrawScope.drawRosette(color: Color, center: Offset, radius: Float) {
    val ribbon = Path()
    ribbon.moveTo(center.x - radius * 0.44f, center.y + radius * 0.22f)
    ribbon.lineTo(center.x - radius * 0.62f, center.y + radius * 1.12f)
    ribbon.lineTo(center.x - radius * 0.16f, center.y + radius * 0.80f)
    ribbon.lineTo(center.x + radius * 0.16f, center.y + radius * 1.10f)
    ribbon.lineTo(center.x + radius * 0.10f, center.y + radius * 0.30f)
    ribbon.close()
    drawPath(ribbon, color.copy(alpha = 0.75f))
    drawCircle(color, radius = radius * 0.62f, center = Offset(center.x, center.y - radius * 0.10f))
    drawCircle(
        Color.Black.copy(alpha = 0.35f),
        radius = radius * 0.30f,
        center = Offset(center.x, center.y - radius * 0.10f),
    )
}

// ---------------------------------------------------------------- emblems

/**
 * The app emblem: a paw above a ridge of peaks and conifers, inside a double ring.
 * This is the lockup the reference mockup builds its identity from.
 */
fun DrawScope.drawWildlifeEmblem(
    ring: Color,
    field: Color,
    art: Color,
    accent: Color,
) {
    val r = minOf(size.width, size.height) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)

    drawCircle(field, radius = r * 0.97f, center = c)
    drawCircle(ring, radius = r * 0.97f, center = c, style = Stroke(width = r * 0.09f))
    drawCircle(ring.copy(alpha = 0.55f), radius = r * 0.80f, center = c, style = Stroke(width = r * 0.03f))

    // Landscape occupies the lower half of the disc.
    val base = c.y + r * 0.46f
    drawPeaks(art.copy(alpha = 0.85f), base, r * 1.14f, r * 0.62f, c.x)
    val treeBase = base + r * 0.02f
    listOf(-0.62f to 0.52f, -0.30f to 0.40f, 0.30f to 0.42f, 0.64f to 0.54f).forEach { (dx, h) ->
        drawPine(art, Offset(c.x + r * dx, treeBase), r * 0.30f, r * h)
    }
    // Ground line closes the scene.
    drawLine(
        art,
        Offset(c.x - r * 0.72f, treeBase),
        Offset(c.x + r * 0.72f, treeBase),
        strokeWidth = r * 0.05f,
        cap = StrokeCap.Round,
    )
    drawPaw(accent, Offset(c.x, c.y - r * 0.30f), r * 0.40f)
}

@Composable
fun WildlifeEmblem(
    ring: Color,
    field: Color,
    art: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) { drawWildlifeEmblem(ring, field, art, accent) }
}

/**
 * A rank patch: a shield carrying a mark that escalates with the progression level,
 * so the seven XP levels read as a visibly earned sequence.
 */
fun DrawScope.drawRankPatch(
    levelKey: String,
    border: Color,
    field: Color,
    art: Color,
) {
    val w = size.width
    val h = size.height
    drawPath(shieldPath(w, h), field)
    drawPath(shieldPath(w, h), border, style = Stroke(width = w * 0.075f, join = StrokeJoin.Round))
    drawPath(
        shieldPath(w, h, inset = w * 0.15f),
        border.copy(alpha = 0.45f),
        style = Stroke(width = w * 0.03f, join = StrokeJoin.Round),
    )

    val c = Offset(w / 2f, h * 0.46f)
    val u = w * 0.5f
    when (levelKey) {
        "tourist" -> drawPine(art, Offset(c.x, c.y + u * 0.42f), u * 0.62f, u * 0.90f)
        "explorer" -> {
            drawStar(art, c, u * 0.44f, points = 4)
            drawCircle(art, radius = u * 0.60f, center = c, style = Stroke(width = u * 0.09f))
        }
        "naturalist" -> {
            drawPine(art, Offset(c.x - u * 0.30f, c.y + u * 0.46f), u * 0.46f, u * 0.72f)
            drawPine(art, Offset(c.x + u * 0.30f, c.y + u * 0.46f), u * 0.46f, u * 0.86f)
        }
        "tracker" -> drawPaw(art, c, u * 0.62f)
        "field_ranger" -> {
            drawPaw(art, Offset(c.x, c.y - u * 0.10f), u * 0.50f)
            drawStar(art, Offset(c.x, c.y + u * 0.66f), u * 0.24f)
        }
        "master_ranger" -> {
            drawPaw(art, Offset(c.x, c.y - u * 0.06f), u * 0.46f)
            listOf(-0.42f, 0f, 0.42f).forEach {
                drawStar(art, Offset(c.x + u * it, c.y + u * 0.70f), u * 0.20f)
            }
        }
        "legendary_ranger" -> {
            drawPaw(art, Offset(c.x, c.y - u * 0.04f), u * 0.46f)
            // A laurel of rays marks the final level.
            repeat(9) { i ->
                val a = -PI + i * PI / 8
                val x1 = c.x + (u * 0.74f * cos(a)).toFloat()
                val y1 = c.y + (u * 0.74f * sin(a)).toFloat()
                val x2 = c.x + (u * 0.96f * cos(a)).toFloat()
                val y2 = c.y + (u * 0.96f * sin(a)).toFloat()
                drawLine(art, Offset(x1, y1), Offset(x2, y2), strokeWidth = u * 0.07f, cap = StrokeCap.Round)
            }
            drawStar(art, Offset(c.x, c.y + u * 0.72f), u * 0.22f)
        }
        else -> drawPaw(art, c, u * 0.58f)
    }
}

@Composable
fun RankPatch(
    levelKey: String,
    border: Color,
    field: Color,
    art: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) { drawRankPatch(levelKey, border, field, art) }
}

/** Drawn stat marks, replacing the Material icons in the readout row. */
enum class StatMark { TICK, SPARKLE, ROSETTE, PAW }

@Composable
fun StatGlyph(mark: StatMark, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = minOf(size.width, size.height) / 2f
        when (mark) {
            StatMark.TICK -> drawTick(color, c, r * 0.86f)
            StatMark.SPARKLE -> drawSparkle(color, c, r * 0.92f)
            StatMark.ROSETTE -> drawRosette(color, Offset(c.x, c.y - r * 0.16f), r * 0.72f)
            StatMark.PAW -> drawPaw(color, c, r * 0.84f)
        }
    }
}

/** A drawn magnifier, so the search field carries no Material glyph. */
@Composable
fun MagnifierGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = minOf(size.width, size.height) / 2f
        val lensC = Offset(size.width * 0.42f, size.height * 0.42f)
        val lensR = r * 0.58f
        drawCircle(color, radius = lensR, center = lensC, style = Stroke(width = r * 0.20f))
        drawLine(
            color,
            Offset(lensC.x + lensR * 0.72f, lensC.y + lensR * 0.72f),
            Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = r * 0.22f,
            cap = StrokeCap.Round,
        )
    }
}

/** Rotates a small motif around a circle, used for the patch's ring of pines. */
fun DrawScope.drawPineRing(color: Color, count: Int = 12, radiusFactor: Float = 0.80f) {
    val r = minOf(size.width, size.height) / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    repeat(count) { i ->
        val angle = i * 360f / count
        withTransform({ rotate(angle, pivot = c) }) {
            drawPine(color, Offset(c.x, c.y - r * radiusFactor), r * 0.20f, r * 0.24f)
        }
    }
}
