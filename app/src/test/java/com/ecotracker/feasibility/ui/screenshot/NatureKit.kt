package com.wildlife.feasibility.ui.screenshot

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Nature ornament kit for the redesign sandbox: the real bundled zoological
 * silhouettes plus drawn field-guide furniture (contours, ridgelines, sprigs).
 *
 * Silhouettes are the project's existing offline CC0 potrace assets. They mark a
 * taxonomic group, never a species identity.
 */

private val pathCache = mutableMapOf<String, Path?>()

private fun assetFile(relative: String): File? {
    val candidates = listOf(
        File("src/main/assets/$relative"),
        File("app/src/main/assets/$relative"),
        File("../app/src/main/assets/$relative"),
    )
    return candidates.firstOrNull { it.exists() }
}

/** Pulls the single `d` attribute out of an SVG asset and parses it into a Compose Path. */
private fun svgAssetPath(relative: String): Path? = pathCache.getOrPut(relative) {
    val file = assetFile(relative) ?: return@getOrPut null
    val text = file.readText()
    val d = Regex("""<path[^>]*?\sd="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)
        .find(text)?.groupValues?.get(1) ?: return@getOrPut null
    runCatching { PathParser().parsePathString(d).toPath() }.getOrNull()
}

private fun taxonPath(group: String): Path? = svgAssetPath("taxon-glyphs/$group.svg")

/**
 * Draws a taxon silhouette fitted into the current draw area. The source paths come
 * from potrace with a flipped Y axis, so the fit here mirrors vertically.
 */
fun DrawScope.drawTaxonSilhouette(group: String, color: Color, inset: Float = 0f) {
    drawFittedPath(taxonPath(group), color, inset, flipY = true)
}

/**
 * Fits a parsed SVG path into the draw area. Potrace exports carry a negative Y scale
 * in their group transform, so they need mirroring; Inkscape exports do not.
 */
fun DrawScope.drawFittedPath(
    path: Path?,
    color: Color,
    inset: Float = 0f,
    flipY: Boolean,
) {
    path ?: return
    val b = path.getBounds()
    if (b.width <= 0f || b.height <= 0f) return
    val w = size.width - inset * 2
    val h = size.height - inset * 2
    val s = minOf(w / b.width, h / b.height)
    val ox = inset + (w - s * b.width) / 2f
    val oy = inset + (h - s * b.height) / 2f
    withTransform({
        if (flipY) {
            translate(left = ox - b.left * s, top = oy + b.bottom * s)
            scale(scaleX = s, scaleY = -s, pivot = Offset.Zero)
        } else {
            translate(left = ox - b.left * s, top = oy - b.top * s)
            scale(scaleX = s, scaleY = s, pivot = Offset.Zero)
        }
    }) {
        drawPath(path, color)
    }
}

/** The project's Inkscape-drawn rarity and regional field marks. */
fun DrawScope.drawFieldMark(name: String, color: Color, inset: Float = 0f) {
    drawFittedPath(svgAssetPath("field_marks/$name.svg"), color, inset, flipY = false)
}

@Composable
fun FieldMark(name: String, color: Color, modifier: Modifier = Modifier, inset: Float = 0f) {
    Canvas(modifier) { drawFieldMark(name, color, inset) }
}

@Composable
fun TaxonSilhouette(
    group: String,
    color: Color,
    modifier: Modifier = Modifier,
    inset: Float = 0f,
) {
    Canvas(modifier) { drawTaxonSilhouette(group, color, inset) }
}

/** Faint topographic contour lines — the single strongest "this is a park map" signal. */
fun DrawScope.drawContours(
    color: Color,
    lines: Int = 9,
    seed: Int = 11,
    strokeWidth: Float = 1.2f,
) {
    val rng = Random(seed)
    repeat(lines) { i ->
        val baseY = size.height * (i + 0.5f) / lines
        val amp = size.height * (0.020f + rng.nextFloat() * 0.045f)
        val phase = rng.nextFloat() * PI.toFloat() * 2f
        val freq = 1.2f + rng.nextFloat() * 1.6f
        val path = Path()
        var x = 0f
        val step = size.width / 64f
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

/** A conifer ridgeline, drawn as overlapping tree silhouettes along the bottom edge. */
fun DrawScope.drawPineRidge(
    color: Color,
    seed: Int = 5,
    scale: Float = 0.75f,
    baselineY: Float = Float.NaN,
    bandHeight: Float = Float.NaN,
) {
    val rng = Random(seed)
    val band = if (bandHeight.isNaN()) size.height else bandHeight
    val baseline = if (baselineY.isNaN()) size.height else baselineY
    val path = Path()
    path.moveTo(0f, baseline)
    // Trees are sized off the band height so the ridge reads as forest at any size.
    var x = -band * 0.3f
    while (x < size.width + band * 0.4f) {
        val treeW = band * scale * (0.75f + rng.nextFloat() * 0.6f)
        val treeH = band * (0.55f + rng.nextFloat() * 0.45f)
        val baseY = baseline
        val apexX = x + treeW / 2f
        // Three stacked tiers give the fir its notched profile.
        path.moveTo(x, baseY)
        path.lineTo(apexX, baseY - treeH)
        path.lineTo(x + treeW, baseY)
        path.close()
        path.moveTo(x + treeW * 0.10f, baseY - treeH * 0.30f)
        path.lineTo(apexX, baseY - treeH * 0.92f)
        path.lineTo(x + treeW * 0.90f, baseY - treeH * 0.30f)
        path.close()
        x += treeW * 0.62f
    }
    drawPath(path, color)
}

@Composable
fun PineRidge(color: Color, modifier: Modifier = Modifier, seed: Int = 5) {
    Canvas(modifier) { drawPineRidge(color, seed) }
}

/** A small botanical sprig used as a rule ornament between sections. */
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
    // Leaves are swept-back teardrops rather than straight ticks, so the rule reads botanical.
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

/**
 * A torn paper edge: a filled band whose lower boundary is a ragged deckle, used to
 * separate sections the way a pasted-in guide page would.
 */
fun DrawScope.drawTornEdge(
    color: Color,
    seed: Int = 3,
    ragged: Float = 0.55f,
    highlight: Color? = null,
) {
    val rng = Random(seed)
    val path = Path()
    path.moveTo(0f, 0f)
    path.lineTo(size.width, 0f)
    var x = size.width
    val step = size.width / 44f
    path.lineTo(x, size.height * (0.35f + rng.nextFloat() * ragged))
    while (x > 0f) {
        x -= step
        // Occasional deeper nicks keep the tear from reading as a regular zigzag.
        val depth = if (rng.nextFloat() < 0.18f) 0.85f else 0.30f + rng.nextFloat() * ragged
        path.lineTo(x, size.height * depth)
    }
    path.lineTo(0f, 0f)
    path.close()
    drawPath(path, color)
    // The lit fibre along a real tear.
    highlight?.let {
        drawPath(path, it, style = Stroke(width = 1.6f))
    }
}

@Composable
fun TornEdge(
    color: Color,
    modifier: Modifier = Modifier,
    seed: Int = 3,
    highlight: Color? = null,
) {
    Canvas(modifier) { drawTornEdge(color, seed, highlight = highlight) }
}

/** Darkens the page corners so the screen reads as lamplit rather than evenly backlit. */
@Composable
fun Vignette(color: Color, modifier: Modifier = Modifier, strength: Float = 0.55f) {
    Canvas(modifier) {
        drawRect(
            Brush.radialGradient(
                0.55f to Color.Transparent,
                1f to color.copy(alpha = strength),
                center = Offset(size.width / 2f, size.height * 0.42f),
                radius = maxOf(size.width, size.height) * 0.78f,
            )
        )
    }
}

/**
 * A layered landscape for the full-bleed header: haze, a moon, two receding ridges
 * of peaks and a foreground conifer line.
 */
fun DrawScope.drawLandscape(
    sky: Color,
    far: Color,
    mid: Color,
    near: Color,
    moon: Color,
) {
    drawRect(Brush.verticalGradient(listOf(sky, far.copy(alpha = 0.35f))))

    val moonCenter = Offset(size.width * 0.74f, size.height * 0.17f)
    val moonR = size.height * 0.052f
    drawCircle(
        Brush.radialGradient(
            0f to moon.copy(alpha = 0.30f),
            1f to Color.Transparent,
            center = moonCenter,
            radius = moonR * 3.4f,
        ),
        radius = moonR * 3.4f,
        center = moonCenter,
    )
    drawCircle(moon, radius = moonR, center = moonCenter)

    // Far ridge: low, soft, many small peaks.
    fun ridge(color: Color, baseline: Float, amplitude: Float, peaks: Int, seed: Int) {
        val rng = Random(seed)
        val p = Path()
        p.moveTo(0f, size.height)
        p.lineTo(0f, baseline)
        val step = size.width / peaks
        var x = 0f
        var up = true
        while (x < size.width + step) {
            val h = if (up) baseline - amplitude * (0.45f + rng.nextFloat() * 0.75f)
            else baseline - amplitude * (0.05f + rng.nextFloat() * 0.25f)
            p.lineTo(x + step / 2f, h)
            x += step / 2f
            up = !up
        }
        p.lineTo(size.width, baseline)
        p.lineTo(size.width, size.height)
        p.close()
        drawPath(p, color)
    }

    ridge(far, size.height * 0.72f, size.height * 0.34f, 7, 21)
    ridge(mid, size.height * 0.86f, size.height * 0.26f, 5, 9)
    // Foreground conifers sit on the very bottom edge.
    drawPineRidge(
        near,
        seed = 14,
        scale = 0.55f,
        baselineY = size.height,
        bandHeight = size.height * 0.30f,
    )
}

@Composable
fun LandscapeScene(
    sky: Color,
    far: Color,
    mid: Color,
    near: Color,
    moon: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) { drawLandscape(sky, far, mid, near, moon) }
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
