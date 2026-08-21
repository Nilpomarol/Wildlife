package com.wildlife.feasibility.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest

/** Offline CC0 zoological silhouettes used for taxonomic controls, never species identity. */
@Composable
fun TaxonGroupGlyph(groupKey: String, tint: Color, contentDescription: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/taxon-glyphs/$groupKey.svg")
            .decoderFactory(SvgDecoder.Factory())
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
