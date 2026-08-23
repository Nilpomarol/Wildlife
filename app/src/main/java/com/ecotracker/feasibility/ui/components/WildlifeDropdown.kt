package com.wildlife.feasibility.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.wildlife.feasibility.ui.art.ChevronGlyph
import com.wildlife.feasibility.ui.art.StatGlyph
import com.wildlife.feasibility.ui.art.StatMark
import com.wildlife.feasibility.ui.theme.WildlifeSpacing
import com.wildlife.feasibility.ui.theme.WildlifeSurfaceElevated
import com.wildlife.feasibility.ui.theme.WildlifeTheme

/**
 * A field-guide selector, shared for filter axes, catalogue and region choices.
 *
 * [leading] is an optional slot rendered before the label in both the closed pill and each
 * open row. [accent] tints the pill's chrome and the selected row, so a row of selectors
 * can be told apart by colour before any label is read.
 */
@Composable
fun <T> WildlifeDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color? = null,
    leading: (@Composable (T) -> Unit)? = null,
    /**
     * Optional shorter text for the closed pill. A filter axis whose neutral option reads
     * "Any standing" in the menu can name just the axis when collapsed, which is what
     * keeps a row of selectors inside a phone's width.
     */
    pillLabel: ((T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val tint = accent ?: WildlifeTheme.colors.oliveStrong
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    // Opening brightens the pill the way focus brightens the search field, so the control
    // and the menu it owns read as one object while the menu is up.
    val borderColor by animateColorAsState(
        tint.copy(alpha = if (expanded) 0.90f else 0.55f),
        label = "pillBorder",
    )
    val fillColor by animateColorAsState(
        tint.copy(alpha = if (expanded) 0.22f else 0.14f),
        label = "pillFill",
    )
    val markScale by animateFloatAsState(
        if (expanded) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "pillMark",
    )

    // The position provider works in pixels, so the gap and edge margin are converted here
    // where a Density is available rather than inside it. It reports back whether it had
    // to flip above the anchor, so the menu can grow from the edge nearest the pill
    // instead of always from its top.
    val density = LocalDensity.current
    val flippedAbove = remember { mutableStateOf(false) }
    val position = remember(density) {
        with(density) {
            AnchoredBelow(
                gapPx = 6.dp.roundToPx(),
                marginPx = 8.dp.roundToPx(),
                flippedAbove = flippedAbove,
            )
        }
    }

    // Kept mounted through the exit transition; dismissing would otherwise tear the popup
    // down before it had a chance to animate out.
    val menuState = remember { MutableTransitionState(false) }
    menuState.targetState = expanded

    // The pill and its menu share a Box so the menu has an anchor. Without one the popup
    // took its position from whatever laid the selector out — a filter row — and every
    // menu opened at that row's left edge instead of under the control that was tapped.
    Box(modifier) {
        Surface(
            shape = CircleShape,
            color = fillColor,
            border = BorderStroke(1.5.dp, borderColor),
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .clickable { expanded = true }
                    .padding(start = WildlifeSpacing.Card, end = WildlifeSpacing.Small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Small),
            ) {
                if (leading != null) {
                    Box(Modifier.scale(markScale)) { leading(selected) }
                }
                Text(
                    text = (pillLabel ?: label)(selected),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = WildlifeTheme.colors.parchment,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Chevron(tint, chevronRotation)
            }
        }

        if (menuState.currentState || menuState.targetState) {
            Popup(
                popupPositionProvider = position,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = menuState,
                    // Grows from the edge nearest the pill, so the menu reads as coming
                    // out of the control rather than arriving over it.
                    enter = fadeIn(tween(150)) + scaleIn(
                        animationSpec = tween(150),
                        initialScale = 0.92f,
                        transformOrigin = menuOrigin(flippedAbove.value),
                    ),
                    exit = fadeOut(tween(110)) + scaleOut(
                        animationSpec = tween(110),
                        targetScale = 0.94f,
                        transformOrigin = menuOrigin(flippedAbove.value),
                    ),
                ) {
                    DropdownMenuSurface(
                        options = options,
                        selected = selected,
                        label = label,
                        leading = leading,
                        tint = tint,
                        onSelected = {
                            onSelected(it)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

/** The chevron, drawn rather than taken from Material so the pill carries no icon font. */
@Composable
private fun Chevron(tint: Color, rotation: Float) {
    ChevronGlyph(
        tint,
        Modifier
            .size(16.dp)
            .rotate(rotation),
    )
}

@Composable
private fun <T> DropdownMenuSurface(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    leading: (@Composable (T) -> Unit)?,
    tint: Color,
    onSelected: (T) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = WildlifeSurfaceElevated,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f)),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 180.dp, max = 260.dp)
                .padding(WildlifeSpacing.Micro),
        ) {
            options.forEach { option ->
                val chosen = option == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (chosen) tint.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { onSelected(option) }
                        .padding(
                            horizontal = WildlifeSpacing.Card,
                            vertical = WildlifeSpacing.Small,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WildlifeSpacing.Card),
                ) {
                    // A fixed slot, so labels line up whether or not an option has a mark.
                    Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                        if (leading != null) leading(option)
                    }
                    Text(
                        text = label(option),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (chosen) {
                            WildlifeTheme.colors.parchment
                        } else {
                            WildlifeTheme.colors.parchmentDim
                        },
                    )
                    if (chosen) {
                        StatGlyph(StatMark.TICK, tint, Modifier.size(15.dp))
                    } else {
                        Spacer(Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

/**
 * Places a menu directly under its anchor, kept inside the window.
 *
 * A menu opened from a control near the right edge would otherwise run off the screen, and
 * one opened near the bottom would be clipped — so it flips above the anchor when there is
 * no room below.
 */
private class AnchoredBelow(
    private val gapPx: Int,
    private val marginPx: Int,
    private val flippedAbove: MutableState<Boolean>,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - popupContentSize.height - gapPx
        val fitsBelow = below + popupContentSize.height <= windowSize.height || above < marginPx
        flippedAbove.value = !fitsBelow
        val y = if (fitsBelow) below else above

        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = anchorBounds.left.coerceIn(marginPx, maxX)

        return IntOffset(x, y)
    }
}

/** Top edge for a menu that hangs below its anchor, bottom edge for one that flips above. */
private fun menuOrigin(flippedAbove: Boolean): TransformOrigin =
    TransformOrigin(pivotFractionX = 0.15f, pivotFractionY = if (flippedAbove) 1f else 0f)
