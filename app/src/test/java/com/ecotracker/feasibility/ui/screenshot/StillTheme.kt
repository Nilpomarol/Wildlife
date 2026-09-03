package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.wildlife.feasibility.ui.components.LocalPlateEffectsAnimated
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * The app theme with plate effects pinned to a fixed frame.
 *
 * Screenshot hosts must use this rather than [WildlifeTheme] directly. An infinite
 * transition anywhere in the tree keeps the Compose test clock permanently busy, so
 * `waitForIdle` never returns and any capture containing a species grid hangs. Pinning the
 * phase also makes the captures deterministic, which a running animation would not be.
 */
@Composable
fun StillTheme(content: @Composable () -> Unit) {
    WildlifeTheme {
        CompositionLocalProvider(LocalPlateEffectsAnimated provides false) {
            content()
        }
    }
}

/**
 * Renders at a larger type scale without changing the layout density.
 *
 * Robolectric's qualifiers cannot express a font scale, so it is provided directly. The
 * device density is left where the qualifier put it: scaling both would shrink the whole
 * page rather than testing what large type does to it.
 */
@Composable
fun ScaledFont(scale: Float, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, scale),
        content = content,
    )
}
