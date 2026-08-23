package com.wildlife.feasibility.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
