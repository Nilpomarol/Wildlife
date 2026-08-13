package com.wildlife.feasibility.ui.components

internal fun responsiveSpeciesGridColumns(widthDp: Float, fontScale: Float): Int = when {
    widthDp < 280f || fontScale >= 1.8f -> 1
    widthDp < 360f || fontScale >= 1.3f -> 2
    else -> 3
}
