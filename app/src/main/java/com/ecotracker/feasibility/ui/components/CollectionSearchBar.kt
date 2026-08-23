package com.wildlife.feasibility.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wildlife.feasibility.ui.art.ClearGlyph
import com.wildlife.feasibility.ui.art.MagnifierGlyph
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeSurfaceWarm
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * The index line of the guide: a search pill built to match the filter selectors beside it.
 *
 * This is a [BasicTextField] rather than an `OutlinedTextField` because the Material field
 * brings chrome that cannot be styled away — a notched label slot, a 56dp minimum height
 * and fixed internal padding — all of which made it the one control on the screen that
 * still read as stock Material.
 *
 * At rest it is quieter than the selectors below it, since a placeholder is not a choice
 * the user has made. Focus is what promotes it: the border thickens to match theirs and
 * the whole frame takes the olive accent.
 */
@Composable
fun CollectionSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search your collection",
) {
    val colors = WildlifeTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val keyboard = LocalSoftwareKeyboardController.current

    // One animated pair drives the whole frame, so the pill resolves as a single object
    // rather than as a border, a glyph and a ground each arriving on their own.
    val borderColor by animateColorAsState(
        if (focused) colors.oliveStrong.copy(alpha = 0.85f) else colors.oliveDark,
        label = "searchBorder",
    )
    val borderWidth by animateDpAsState(
        if (focused) 1.5.dp else 1.dp,
        label = "searchBorderWidth",
    )
    val glyphColor by animateColorAsState(
        if (focused) colors.oliveStrong else colors.parchmentFaint,
        label = "searchGlyph",
    )
    val glyphScale by animateFloatAsState(
        if (focused) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "searchGlyphScale",
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.parchment),
        cursorBrush = SolidColor(colors.oliveStrong),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(CircleShape)
                    // Translucent rather than opaque, so the page painting still reads
                    // faintly through the control and it sits on the page instead of on
                    // top of it.
                    .background(WildlifeSurfaceWarm.copy(alpha = 0.72f))
                    .border(borderWidth, borderColor, CircleShape)
                    .padding(horizontal = WildlifeSpacing.Card),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
            ) {
                // The magnifier leans in slightly on focus. It is a small move on purpose:
                // the field is a place to type, not a thing to watch.
                MagnifierGlyph(
                    color = glyphColor,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(glyphScale),
                )
                Box(Modifier.weight(1f)) {
                    SearchPlaceholder(text = placeholder, visible = query.isEmpty())
                    field()
                }
                ClearAffordance(query = query, onClear = { onQueryChange("") })
            }
        },
    )
}

/**
 * The placeholder, which fades and lifts out of the way rather than blinking off on the
 * first keystroke — at typing speed the instant swap read as a glitch.
 *
 * It is its own composable so the plain `AnimatedVisibility` resolves here: inside the
 * field's Row, the RowScope overload wins and cannot animate a Box's child.
 */
@Composable
private fun SearchPlaceholder(text: String, visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 3 },
        exit = fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 3 },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = WildlifeTheme.colors.mutedText,
        )
    }
}

/**
 * The clear control, kept out of the row's flow when there is nothing to clear.
 *
 * It is a drawn cross rather than `Icons.Default.Clear`, and it reserves no space when the
 * field is empty — a permanently visible clear button reads as an action the field always
 * offers, which it does not.
 *
 * It grows in from the centre rather than appearing outright, so the text does not appear
 * to jump sideways as the control claims its width on the first keystroke.
 */
@Composable
private fun RowScope.ClearAffordance(query: String, onClear: () -> Unit) {
    AnimatedVisibility(
        visible = query.isNotEmpty(),
        enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.6f) +
            expandHorizontally(tween(180), clip = false),
        exit = fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.6f) +
            shrinkHorizontally(tween(140), clip = false),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable(onClick = onClear)
                .semantics { contentDescription = "Clear search" },
            contentAlignment = Alignment.Center,
        ) {
            ClearGlyph(color = WildlifeTheme.colors.parchmentDim, modifier = Modifier.size(13.dp))
        }
    }
}
