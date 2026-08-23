package com.wildlife.feasibility.ui.components

/**
 * Columns for the species grid.
 *
 * A field guide is browsed by scanning, so a phone shows three plates across: more of the
 * region is visible at once and the marks, not the captions, do the identifying. Cards get
 * proportionally taller to pay for the lost width — see [speciesCardAspectRatio].
 *
 * Large text scales drop a column rather than letting captions overrun their plates.
 */
internal fun responsiveSpeciesGridColumns(widthDp: Float, fontScale: Float): Int = when {
    widthDp < 280f || fontScale >= 1.8f -> 1
    widthDp < 340f || fontScale >= 1.5f -> 2
    widthDp < 620f -> 3
    else -> 4
}

/**
 * Card proportion for a column count. Lower ratio = taller card.
 *
 * Cards are square: with the caption laid over the plate rather than under it, there is no
 * fixed block of text to make room for, so the tile can be the artwork's own shape. A
 * single column is the exception — a full-width square would be an enormous tile — and
 * stays a little taller than wide.
 */
internal fun speciesCardAspectRatio(columns: Int): Float = when (columns) {
    1 -> 1.15f
    else -> 1f
}
