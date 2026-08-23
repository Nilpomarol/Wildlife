package com.wildlife.feasibility.ui.art

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.floor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * The drawn furniture of the field guide: contours, ridgelines, botanical rules, paper
 * grain, torn edges and the landscape header scene.
 *
 * All of it is generated rather than bundled as bitmaps, so it scales to any surface
 * and re-tints with the theme. Every generator takes a seed and is deterministic, which
 * keeps screenshot tests stable and stops ornament shimmering across recompositions.
 */

// ---------------------------------------------------------------- fitted SVG art

/**
 * Fits a parsed SVG path into the draw area, preserving aspect ratio.
 *
 * [SvgOrigin.POTRACE] art is mirrored vertically because those exports carry a negative
 * Y scale in the group transform that we drop when reading the raw `d` attribute.
 */
fun DrawScope.drawFittedPath(
    path: Path?,
    color: Color,
    origin: SvgOrigin,
    inset: Float = 0f,
) {
    path ?: return
    val b = path.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    if (w <= 0f || h <= 0f) return
    val s = minOf(w / b.width, h / b.height)
    val ox = inset + (w - s * b.width) / 2f
    val oy = inset + (h - s * b.height) / 2f
    withTransform({
        when (origin) {
            SvgOrigin.POTRACE -> {
                translate(left = ox - b.left * s, top = oy + b.bottom * s)
                scale(scaleX = s, scaleY = -s, pivot = Offset.Zero)
            }
            SvgOrigin.INKSCAPE -> {
                translate(left = ox - b.left * s, top = oy - b.top * s)
                scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
            }
        }
    }) {
        drawPath(path, color)
    }
}

/**
 * A bundled CC0 zoological silhouette for a taxonomic group. Marks a group, never a
 * species identity.
 */
@Composable
fun TaxonSilhouette(
    groupKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    inset: Float = 0f,
) {
    val path = rememberSvgAssetPath("taxon-glyphs/$groupKey.svg")
    Canvas(modifier) { drawFittedPath(path, color, SvgOrigin.POTRACE, inset) }
}

/** A rarity or regional-standing field mark from `assets/field_marks/`. */
@Composable
fun FieldMark(
    name: String,
    color: Color,
    modifier: Modifier = Modifier,
    inset: Float = 0f,
) {
    val path = rememberSvgAssetPath("field_marks/$name.svg")
    Canvas(modifier) { drawFittedPath(path, color, SvgOrigin.INKSCAPE, inset) }
}

/**
 * The owner-drawn badge for a progression level, from `assets/rank_badges/<levelKey>.svg`.
 *
 * Returns false when a level has no badge, so callers can fall back to the drawn patch.
 */
@Composable
fun RankBadgeArt(
    levelKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    inset: Float = 0f,
): Boolean {
    val path = rememberSvgAssetPath("rank_badges/$levelKey.svg") ?: return false
    Canvas(modifier) { drawFittedPath(path, color, SvgOrigin.INKSCAPE, inset) }
    return true
}

/**
 * A region's own emblem from `assets/region_marks/<regionKey>.svg`.
 *
 * Returns false when a region has no artwork yet, so callers can fall back rather than
 * render an empty box: regions are added over time and their marks arrive with them.
 */
@Composable
fun RegionMark(
    regionKey: String,
    color: Color,
    modifier: Modifier = Modifier,
    inset: Float = 0f,
): Boolean {
    val path = rememberSvgAssetPath("region_marks/$regionKey.svg") ?: return false
    Canvas(modifier) { drawFittedPath(path, color, SvgOrigin.INKSCAPE, inset) }
    return true
}

// ---------------------------------------------------------------- page texture

/** Paper grain, so dark surfaces read as printed stock rather than flat screen. */
fun DrawScope.drawGrain(color: Color, alpha: Float = 0.055f, seed: Int = 7) {
    val rng = Random(seed)
    val count = (size.width * size.height / 620f).toInt().coerceAtMost(20_000)
    repeat(count) {
        drawCircle(
            color = color.copy(alpha = alpha * (0.35f + rng.nextFloat())),
            radius = 0.85f,
            center = Offset(rng.nextFloat() * size.width, rng.nextFloat() * size.height),
        )
    }
}

@Composable
fun Grain(color: Color, modifier: Modifier = Modifier, alpha: Float = 0.055f, seed: Int = 7) {
    Canvas(modifier) { drawGrain(color, alpha, seed) }
}

/** Darkens the page corners so the screen reads as lamplit rather than evenly backlit. */
fun DrawScope.drawVignette(color: Color, strength: Float = 0.55f) {
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to color.copy(alpha = strength),
            center = Offset(size.width / 2f, size.height * 0.42f),
            radius = maxOf(size.width, size.height) * 0.78f,
        )
    )
}

@Composable
fun Vignette(color: Color, modifier: Modifier = Modifier, strength: Float = 0.55f) {
    Canvas(modifier) { drawVignette(color, strength) }
}

/** Faint topographic contour lines — the strongest "this is a park map" signal. */
fun DrawScope.drawContours(
    color: Color,
    lines: Int = 9,
    seed: Int = 11,
    strokeWidth: Float = 1.2f,
) {
    if (lines <= 0) return
    val rng = Random(seed)
    repeat(lines) { i ->
        val baseY = size.height * (i + 0.5f) / lines
        val amp = size.height * (0.020f + rng.nextFloat() * 0.045f)
        val phase = rng.nextFloat() * PI.toFloat() * 2f
        val freq = 1.2f + rng.nextFloat() * 1.6f
        val path = Path()
        var x = 0f
        val step = size.width / 64f
        if (step <= 0f) return
        while (x <= size.width) {
            val t = x / size.width
            val y = baseY +
                sin(t * freq * PI.toFloat() * 2f + phase) * amp +
                sin(t * freq * PI.toFloat() * 4.7f + phase * 1.7f) * amp * 0.30f
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += step
        }
        drawPath(path, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
    }
}

@Composable
fun ContourField(color: Color, modifier: Modifier = Modifier, lines: Int = 9, seed: Int = 11) {
    Canvas(modifier) { drawContours(color, lines = lines, seed = seed) }
}

// ---------------------------------------------------------------- botanical rules

/** A small botanical sprig, used as a rule ornament beside section labels. */
fun DrawScope.drawSprig(color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val stemHalf = size.width * 0.16f
    drawLine(
        color,
        Offset(cx - stemHalf, cy),
        Offset(cx + stemHalf, cy),
        strokeWidth = 1.4f,
        cap = StrokeCap.Round,
    )
    // Swept-back teardrops rather than straight ticks, so the rule reads botanical.
    val leaves = 3
    repeat(leaves) { i ->
        val t = (i + 0.5f) / leaves
        val x = cx - stemHalf * 0.75f + stemHalf * 1.5f * t
        val len = size.height * 0.34f * (0.65f + 0.35f * (1f - t))
        listOf(-1f, 1f).forEach { dir ->
            val leaf = Path()
            leaf.moveTo(x, cy)
            leaf.cubicTo(
                x - len * 0.15f, cy + dir * len * 0.55f,
                x - len * 0.70f, cy + dir * len * 0.80f,
                x - len * 0.95f, cy + dir * len * 0.45f,
            )
            leaf.cubicTo(
                x - len * 0.70f, cy + dir * len * 0.35f,
                x - len * 0.30f, cy + dir * len * 0.20f,
                x, cy,
            )
            drawPath(leaf, color)
        }
    }
}

@Composable
fun Sprig(color: Color, modifier: Modifier = Modifier, mirrored: Boolean = false) {
    Canvas(modifier) {
        if (mirrored) {
            withTransform({ scale(scaleX = -1f, scaleY = 1f, pivot = center) }) { drawSprig(color) }
        } else {
            drawSprig(color)
        }
    }
}

// ---------------------------------------------------------------- plate effects

/** A raking light sweep across a plate, marking a species of regional standing. */
fun DrawScope.drawPlateSheen(tint: Color, strength: Float) {
    drawRect(
        Brush.linearGradient(
            0.00f to Color.Transparent,
            0.34f to tint.copy(alpha = strength * 0.35f),
            0.46f to tint.copy(alpha = strength),
            0.58f to tint.copy(alpha = strength * 0.30f),
            1.00f to Color.Transparent,
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
    )
}

@Composable
fun PlateSheen(tint: Color, strength: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPlateSheen(tint, strength) }
}

/**
 * The Regional Icon effect: a halo behind the specimen and a rim of light around the
 * plate, in the standing's gold.
 *
 * Pairs with [drawGilding] on a recorded Icon: the halo lights the specimen, the gilding
 * dresses the plate around it. The halo is the part that survives on an Icon you have not
 * found yet, so an unrecorded plate is lit as a target without being dressed as a trophy.
 */
fun DrawScope.drawIconHalo(tint: Color, strength: Float = 1f) {
    // Centred a little above the middle, where a specimen's head tends to sit.
    val center = Offset(size.width * 0.5f, size.height * 0.42f)
    drawRect(
        Brush.radialGradient(
            0.00f to tint.copy(alpha = 0.30f * strength),
            0.55f to tint.copy(alpha = 0.13f * strength),
            1.00f to Color.Transparent,
            center = center,
            radius = maxOf(size.width, size.height) * 0.62f,
        )
    )
    // A rim of light just inside the frame, brightest at the top where the mark sits.
    val rim = minOf(size.width, size.height) * 0.16f
    drawRect(
        Brush.verticalGradient(
            0f to tint.copy(alpha = 0.42f * strength),
            1f to Color.Transparent,
            endY = rim,
        ),
    )
    drawRect(
        Brush.horizontalGradient(
            0f to tint.copy(alpha = 0.22f * strength),
            1f to Color.Transparent,
            endX = rim,
        ),
    )
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Transparent,
            1f to tint.copy(alpha = 0.22f * strength),
            startX = size.width - rim,
            endX = size.width,
        ),
    )
}

/**
 * A bead of light travelling around the plate's edge.
 *
 * [phase] is the light's position around the loop, 0..1. It is a parameter rather than an
 * animation held inside the draw so the effect can be rendered at a fixed point for a
 * screenshot — an infinite animation inside the card would leave the Compose test clock
 * permanently busy and hang every capture that includes a grid.
 */
fun DrawScope.drawEdgeLight(
    tint: Color,
    phase: Float,
    cornerRadiusPx: Float,
    strokePx: Float,
    /**
     * Distance from the card's outer edge.
     *
     * The bead must run *inside* the frame, not on it. Drawn at the edge it lands in the
     * same band as the card's standing border, and on a brass-framed plate the border
     * masks it completely — which is exactly how this shipped invisible the first time.
     */
    insetPx: Float,
    /** Fraction of the loop the bead covers. */
    band: Float = 0.18f,
    alpha: Float = 1f,
) {
    val steps = 48
    fun stops(scale: Float) = Array(steps + 1) { i ->
        val t = i / steps.toFloat()
        val raw = abs(t - phase)
        // The loop wraps, so the far side of 0/1 is near, not distant.
        val distance = if (raw > 0.5f) 1f - raw else raw
        val falloff = (1f - distance / band).coerceAtLeast(0f)
        t to tint.copy(alpha = (falloff * falloff * alpha * scale).coerceIn(0f, 1f))
    }

    // Three passes: a wide soft bloom, a mid body, and a crisp core. Without the bloom the
    // bead reads as a moving hairline rather than as light.
    val passes = listOf(
        strokePx * 3.2f to 0.22f,
        strokePx * 1.7f to 0.45f,
        strokePx to 0.95f,
    )
    passes.forEach { (width, scale) ->
        val offset = insetPx + width / 2f
        if (size.width - offset * 2f <= 0f || size.height - offset * 2f <= 0f) return@forEach
        drawRoundRect(
            brush = Brush.sweepGradient(*stops(scale), center = center),
            topLeft = Offset(offset, offset),
            size = Size(size.width - offset * 2f, size.height - offset * 2f),
            cornerRadius = CornerRadius(
                (cornerRadiusPx - offset).coerceAtLeast(0f),
                (cornerRadiusPx - offset).coerceAtLeast(0f),
            ),
            style = Stroke(width = width),
        )
    }
}

@Composable
fun EdgeLight(
    tint: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    stroke: Dp = 2.dp,
    /** Gap between the card's outer edge and the bead — clears the standing border. */
    inset: Dp = 4.dp,
    periodMillis: Int = 3800,
    /**
     * Shifts this bead's starting position around the loop, 0..1.
     *
     * A grid of these all starting together pulses in unison, which reads as one machine
     * rather than as several specimens catching the light. Callers should derive it from
     * something stable about the item — never a fresh random, which would jump on every
     * recomposition and make screenshots non-deterministic.
     */
    phaseOffset: Float = 0f,
    /** Scales the whole bead, for showing the same effect at two levels of emphasis. */
    alpha: Float = 1f,
    animated: Boolean = true,
    staticPhase: Float = 0.12f,
) {
    val base = if (animated) {
        val transition = rememberInfiniteTransition(label = "edgeLight")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(periodMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "edgeLightPhase",
        ).value
    } else {
        staticPhase
    }
    // Wrapped, so an offset past the end of the loop comes back round the front.
    val phase = (base + phaseOffset).let { it - floor(it) }
    Canvas(modifier) {
        drawEdgeLight(
            tint = tint,
            phase = phase,
            cornerRadiusPx = cornerRadius.toPx(),
            strokePx = stroke.toPx(),
            insetPx = inset.toPx(),
            alpha = alpha,
        )
    }
}

@Composable
fun IconHalo(tint: Color, modifier: Modifier = Modifier, strength: Float = 1f) {
    Canvas(modifier) { drawIconHalo(tint, strength) }
}

/**
 * The gilding of an earned Icon: a warm corner glow and a scatter of gold motes. The
 * natural home for a slow shimmer later — the sheen is a gradient whose offsets can be
 * animated.
 */
fun DrawScope.drawGilding(tint: Color, seed: Int = 19, motes: Int = 7) {
    drawRect(
        Brush.radialGradient(
            0f to tint.copy(alpha = 0.17f),
            1f to Color.Transparent,
            center = Offset(size.width * 0.16f, size.height * 0.12f),
            radius = maxOf(size.width, size.height) * 0.55f,
        )
    )
    val rng = Random(seed)
    repeat(motes) {
        val x = size.width * (0.10f + rng.nextFloat() * 0.82f)
        val y = size.height * (0.10f + rng.nextFloat() * 0.80f)
        val r = minOf(size.width, size.height) * (0.014f + rng.nextFloat() * 0.020f)
        drawFourPointStar(tint.copy(alpha = 0.35f + rng.nextFloat() * 0.45f), Offset(x, y), r)
    }
}

@Composable
fun Gilding(tint: Color, modifier: Modifier = Modifier, seed: Int = 19) {
    Canvas(modifier) { drawGilding(tint, seed) }
}

/** Shared by the gilding motes and the rare-finds stat mark. */
internal fun DrawScope.drawFourPointStar(color: Color, center: Offset, outer: Float) {
    val inner = outer * 0.30f
    val p = Path()
    val steps = 8
    repeat(steps) { i ->
        val r = if (i % 2 == 0) outer else inner
        val a = -PI / 2 + i * PI / 4
        val x = center.x + (r * kotlin.math.cos(a)).toFloat()
        val y = center.y + (r * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}
