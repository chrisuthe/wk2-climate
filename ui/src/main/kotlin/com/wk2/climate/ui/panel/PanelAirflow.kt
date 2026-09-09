package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.AirflowMode
import com.wk2.climate.bus.Command
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.ui.Glyph
import com.wk2.climate.ui.GlyphIcon
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The four airflow modes as four separately-lit buttons.
 *
 * This is the redesign's second rule: **direct selection, never cycling.** The
 * protocol exposes four idempotent setters, so re-tapping the active mode is a
 * genuine no-op rather than advancing a cycle. The factory bar makes you tap
 * up to four times to land on a mode; this never does.
 *
 * Icon-only by design — no text labels.
 *
 * [live] is false until the vehicle has reported a climate signal. Nothing is
 * lit in that case already — an unreported airflow is [AirflowMode.UNKNOWN],
 * which is not in [AirflowMode.selectable] — but four full-strength glyphs
 * read as four modes we have been told are unselected, so the inactive tint
 * drops to muted ink alongside the rest of the page's climate controls.
 */
@Composable
fun PanelAirflow(
    palette: Palette,
    live: Boolean,
    mode: AirflowMode,
    onSelect: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AirflowMode.selectable.forEach { candidate ->
            AirflowTile(
                palette = palette,
                live = live,
                active = candidate == mode,
                res = candidate.glyphRes(),
                glyphHeight = candidate.glyphHeight(),
                onClick = { candidate.command?.let(onSelect) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AirflowTile(
    palette: Palette,
    live: Boolean,
    active: Boolean,
    res: Int,
    glyphHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    Box(
        modifier
            .height(Dimens.airflowTileHeight)
            .background(if (active) palette.accent else Color.Transparent, shape)
            // Layered over the resting fill, never replacing it: a filled tile
            // brightens on press. Fading it would read as the mode switching
            // off at the instant it is touched, on a tile whose fill *is* the
            // statement that the mode is selected and whose re-tap is a no-op.
            .then(if (pressed.value) Modifier.pressedTint(active, palette, shape) else Modifier)
            .then(if (active) Modifier else Modifier.border(Dimens.controlBorderWidth, palette.borderControl, shape))
            .target(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // One asset per glyph; active vs inactive is a tint, not a different file.
        GlyphIcon(
            res = res,
            tint = when {
                active -> palette.accentInk
                !live -> palette.inkMuted
                else -> palette.ink
            },
            height = glyphHeight,
        )
    }
}

private fun AirflowMode.glyphRes(): Int = when (this) {
    AirflowMode.FACE -> Glyph.face
    AirflowMode.FACE_FEET -> Glyph.faceFeet
    AirflowMode.FEET -> Glyph.feet
    AirflowMode.FEET_GLASS -> Glyph.feetGlass
    AirflowMode.NONE, AirflowMode.UNKNOWN -> Glyph.face   // never drawn; see selectable
}

/** Feet-and-glass is drawn taller than the rest, per the handoff. */
private fun AirflowMode.glyphHeight(): Dp =
    if (this == AirflowMode.FEET_GLASS) 86.dp else 76.dp
