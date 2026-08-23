package com.wildlife.feasibility.ui.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wildlife.feasibility.ui.art.Grain
import com.wildlife.feasibility.ui.art.NavMark
import com.wildlife.feasibility.ui.art.NavMarkKind
import com.wildlife.feasibility.ui.theme.FieldLabelStyle
import com.wildlife.feasibility.ui.theme.WildlifeBackground
import com.wildlife.feasibility.ui.theme.WildlifeOliveDark
import com.wildlife.feasibility.ui.theme.WildlifeOliveStrong
import com.wildlife.feasibility.ui.theme.WildlifeParchment
import com.wildlife.feasibility.ui.theme.WildlifeTheme

enum class WildlifeDestination(
    val route: String,
    val label: String,
    val mark: NavMarkKind,
) {
    HOME("home", "Home", NavMarkKind.HOME),
    COLLECTION("collection", "Collection", NavMarkKind.COLLECTION),
    CAPTURE("capture", "Capture", NavMarkKind.CAPTURE),
    EXPLORE("explore", "Explore", NavMarkKind.EXPLORE),
    PROFILE("profile", "Profile", NavMarkKind.PROFILE),
}

/**
 * The guide's index strip.
 *
 * There is no raised centre button and no tinted-icon selection — that shape *is* the stock
 * Android bottom bar, and no amount of repainting stops it reading as one. What is left is
 * a printed strip: the page's own dark ground, closed by a **printed double rule**, with
 * five equal cells set on it.
 *
 * Selection is an **inversion**. The current destination is a block of cream stock carrying
 * dark ink, the way the open page's tab is the printed one in a thumb-indexed guide. It is
 * the only light thing on the screen, so it needs no accent, no underline and no outline to
 * be found.
 *
 * Capture shares that block's exact geometry but is stamped in olive rather than printed in
 * cream — the same object in a different material, so the row reads as one set while the
 * action stays distinct from the four places. It stays **olive, never brass**: gold is
 * reserved for earned prestige (`docs/style.md` §30).
 */
@Composable
fun WildlifeBottomBar(
    selected: WildlifeDestination,
    onSelect: (WildlifeDestination) -> Unit,
) {
    // The inset is absorbed into the bar's own height rather than padded around it, so the
    // printed ground runs to the screen edge instead of stopping above the gesture area.
    val inset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        Modifier
            .fillMaxWidth()
            .height(BarHeight + inset),
    ) {
        BarGround()
        PrintedRule()
        Row(
            Modifier
                .fillMaxSize()
                .padding(bottom = inset)
                .selectableGroup(),
        ) {
            WildlifeDestination.entries.forEach { destination ->
                // Capture launches an Activity and can never become the selected cell, so
                // it must not announce itself as a tab.
                if (destination == WildlifeDestination.CAPTURE) {
                    StampCell(
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    IndexCell(
                        destination = destination,
                        selected = destination == selected,
                        onClick = { onSelect(destination) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val BarHeight = 72.dp

/** Insets every block off its cell, so the strip's ground shows between them. */
private val BlockInsetHorizontal = 4.dp
private val BlockInsetVertical = 8.dp
private val BlockShape = RoundedCornerShape(9.dp)

/**
 * The mark's layout box. The artwork is scaled about its centre by [NavMark]'s own optical
 * balancing, so a mark's drawn width can exceed this — the cells are far wider than they
 * are tall, and the overflow is horizontal, where the room is.
 */
private val MarkSize = 30.dp

/**
 * The capture mark runs larger than the tabs' because it has the block to itself.
 *
 * Sized to the space the four named cells give their mark *and* their label together, so
 * the stamp fills its block to the same optical density as its neighbours fill theirs.
 */
private val StampMarkSize = 42.dp

/**
 * The journal's field label, tracked in tighter than [FieldLabelStyle].
 *
 * That style's 1.3sp is set for section rules with a whole line to spread across; a
 * five-up strip has roughly 75dp per label and "COLLECTION" does not fit at it.
 */
private val NavLabelStyle = FieldLabelStyle.copy(letterSpacing = 0.6.sp)

/**
 * The strip's ground: the page's own darkness, deepening downward.
 *
 * Deliberately not fully opaque at the top — the painted canopy behind the page bleeds
 * through the first few pixels, so the strip sits *on* the page rather than in front of it.
 */
@Composable
private fun BarGround() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to WildlifeBackground.copy(alpha = 0.90f),
                    0.45f to WildlifeBackground.copy(alpha = 0.975f),
                    1f to WildlifeBackground.copy(alpha = 0.995f),
                )
            ),
    ) {
        Grain(WildlifeTheme.colors.parchment, Modifier.fillMaxSize(), alpha = 0.026f)
    }
}

/**
 * A printed double rule: one firm line and one hairline below it.
 *
 * This is what closes the page in the guide's own typography, and it is the whole reason
 * the strip needs no elevation, shadow or border to separate itself from the content.
 */
@Composable
private fun PrintedRule() {
    val accent = WildlifeTheme.colors.oliveStrong
    Canvas(Modifier.fillMaxWidth().height(6.dp)) {
        drawLine(
            accent.copy(alpha = 0.45f),
            Offset(0f, 0.8f.dp.toPx()),
            Offset(size.width, 0.8f.dp.toPx()),
            strokeWidth = 1.6.dp.toPx(),
        )
        drawLine(
            accent.copy(alpha = 0.16f),
            Offset(0f, 4.4f.dp.toPx()),
            Offset(size.width, 4.4f.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/**
 * One destination. Cream stock and dark ink when it is the open page, bare ground and
 * dimmed ink when it is not.
 *
 * The block and the ink cross-fade on the same fraction, so selection moves rather than
 * blinks — a hard swap between a light and a dark cell is jarring at this contrast.
 */
@Composable
private fun IndexCell(
    destination: WildlifeDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val presence by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "cell-presence",
    )
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .fillMaxHeight()
            .selectable(
                selected = selected,
                interactionSource = interaction,
                // The press is drawn on the block below, not here: a ripple on this node
                // would fill the whole cell as a hard rectangle, which is neither the shape
                // of the thing being pressed nor anything else in the design.
                indication = null,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = BlockInsetHorizontal, vertical = BlockInsetVertical),
        contentAlignment = Alignment.Center,
    ) {
        if (presence > 0.005f) {
            PrintedBlock(
                fill = WildlifeParchment,
                grain = WildlifeBackground,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(presence),
            )
        }
        // Drawn above the block and below the ink. An unselected cell has no block yet, and
        // still presses in its shape — the press previews what the cell is about to become.
        BlockPress(
            interaction = interaction,
            // Light ink on the dark ground, dark ink once the cream block is under it,
            // crossfading on the same fraction as everything else in the cell.
            color = lerp(colors.parchment, WildlifeBackground, presence),
        )
        CellContents(
            markKind = destination.mark,
            assetKey = destination.route,
            label = destination.label,
            markInk = lerp(colors.parchmentDim.copy(alpha = 0.68f), WildlifeBackground, presence),
            labelInk = lerp(colors.parchmentFaint, WildlifeBackground, presence),
        )
    }
}

/**
 * Capture: the same block, stamped in olive instead of printed in cream.
 *
 * It is always filled, because the one action in the row should never be the quietest thing
 * in it — and being permanently a block is what lets it keep the centre without being
 * lifted out of the strip.
 *
 * It carries **no word**. The four places are named because five marks are not
 * self-evident; the stamp needs no name because it is the only filled olive thing in the
 * app and the only one that is not a place. Dropping the label lets the mark run larger and
 * sit centred in its block, which is what makes it read as a stamp rather than as a fifth
 * tab that happens to be coloured in.
 *
 * The word survives for screen readers as the block's [contentDescription], so nothing is
 * lost to anyone navigating by name.
 */
@Composable
private fun StampCell(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = WildlifeTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                role = Role.Button,
                onClickLabel = "Record a new sighting",
            )
            .semantics { contentDescription = WildlifeDestination.CAPTURE.label }
            .padding(horizontal = BlockInsetHorizontal, vertical = BlockInsetVertical),
        contentAlignment = Alignment.Center,
    ) {
        PrintedBlock(
            fill = WildlifeOliveDark,
            grain = colors.parchment,
            border = WildlifeOliveStrong.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(),
        )
        BlockPress(interaction = interaction, color = colors.parchment)
        NavMark(
            kind = NavMarkKind.CAPTURE,
            assetKey = WildlifeDestination.CAPTURE.route,
            color = colors.parchment,
            modifier = Modifier.size(StampMarkSize),
        )
    }
}

/**
 * Press feedback in the block's own shape.
 *
 * The touch target stays the full cell — a cell is 72dp tall and its block only 56dp, and
 * shrinking the target to the artwork would make the bar harder to hit to make it prettier.
 * So the cell owns the gesture with its indication switched off, and the ripple is drawn
 * here instead, clipped to [BlockShape] and to the block's bounds. Pressing anywhere in the
 * cell lights up the block, and the feedback is the shape of the thing being pressed.
 */
@Composable
private fun BlockPress(
    interaction: MutableInteractionSource,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .clip(BlockShape)
            .indication(interaction, ripple(color = color)),
    )
}

/** The shared block: a grained rectangle of stock, optionally edged. */
@Composable
private fun PrintedBlock(
    fill: Color,
    grain: Color,
    modifier: Modifier = Modifier,
    border: Color? = null,
) {
    Box(
        modifier
            .clip(BlockShape)
            .background(fill)
            .then(if (border != null) Modifier.border(1.dp, border, BlockShape) else Modifier),
    ) {
        Grain(grain, Modifier.fillMaxSize(), alpha = 0.05f)
    }
}

/** Mark over label, with the same metrics in every cell so the row sits on one baseline. */
@Composable
private fun CellContents(
    markKind: NavMarkKind,
    assetKey: String,
    label: String,
    markInk: Color,
    labelInk: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        NavMark(
            kind = markKind,
            assetKey = assetKey,
            color = markInk,
            modifier = Modifier.size(MarkSize),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label.uppercase(),
            style = NavLabelStyle,
            color = labelInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
