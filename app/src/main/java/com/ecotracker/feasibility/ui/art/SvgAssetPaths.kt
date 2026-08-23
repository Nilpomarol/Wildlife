package com.wildlife.feasibility.ui.art

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Parses the single-path SVG art bundled in `assets/` into Compose [Path]s.
 *
 * These assets were previously rendered through Coil's SVG decoder. Parsing the path
 * directly means the artwork can be tinted, fitted and composed inside a Canvas
 * alongside drawn ornament, and it removes an async image load from every card.
 *
 * The two asset families need opposite vertical fits, which [SvgOrigin] records.
 */
enum class SvgOrigin {
    /** Potrace exports (`taxon-glyphs/`) carry a negative Y scale and must be mirrored. */
    POTRACE,

    /** Inkscape exports (`field_marks/`) are already in screen orientation. */
    INKSCAPE,
}

object SvgAssetPaths {

    private val cache = ConcurrentHashMap<String, Path>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    private val pathData = Regex("""<path[^>]*?\sd="([^"]+)"""", RegexOption.DOT_MATCHES_ALL)

    /**
     * Returns the parsed path for an asset, or null when the asset is absent or
     * unparseable. Results are cached for the process lifetime; the art is bundled and
     * immutable, so a miss is permanent and is remembered too.
     */
    fun path(context: Context, assetPath: String): Path? {
        cache[assetPath]?.let { return it }
        if (assetPath in missing) return null
        val parsed = runCatching {
            val svg = context.assets.open(assetPath).bufferedReader().use { it.readText() }
            val d = pathData.find(svg)?.groupValues?.get(1) ?: return@runCatching null
            PathParser().parsePathString(d).toPath()
        }.getOrNull()
        if (parsed == null) {
            missing.add(assetPath)
            return null
        }
        cache[assetPath] = parsed
        return parsed
    }
}

@Composable
fun rememberSvgAssetPath(assetPath: String): Path? {
    val context = LocalContext.current
    return remember(assetPath) { SvgAssetPaths.path(context, assetPath) }
}
