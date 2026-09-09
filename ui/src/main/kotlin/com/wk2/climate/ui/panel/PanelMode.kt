package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.Glyph
import com.wk2.climate.ui.GlyphIcon
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The two mode grids. Every tile's fill is a claim about the vehicle.
 *
 * [live] is false until the vehicle has reported a climate signal. The flags
 * these tiles read are `raw[signal] == 1`, which collapses absent into false,
 * so while [live] is false no tile is filled -- an unfilled tile says "not
 * selected as far as we have been told", a filled one would say "the vehicle
 * told us this is on". The tiles stay tappable: pressing one is what makes the
 * vehicle report.
 */
@Composable
fun PanelMode(
    palette: Palette,
    state: ClimateState,
    live: Boolean,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModeTile(
                palette, active = live && state.autoOn, height = Dimens.modeTileHeight,
                activeFill = palette.accent, activeInk = palette.accentInk,
                onClick = { onCommand(Command.AUTO) }, modifier = Modifier.weight(1f),
            ) { ink -> BasicText("AUTO", style = Type.modeLabelLarge.copy(color = ink)) }

            ModeTile(
                palette, active = live && state.acOn, height = Dimens.modeTileHeight,
                activeFill = palette.cool, activeInk = palette.surface,
                onClick = { onCommand(Command.AC) }, modifier = Modifier.weight(1f),
            ) { ink -> BasicText("A/C", style = Type.modeLabelLarge.copy(color = ink)) }

            ModeTile(
                palette, active = live && state.recircOn, height = Dimens.modeTileHeight,
                activeFill = palette.accent, activeInk = palette.accentInk,
                onClick = { onCommand(Command.RECIRC) }, modifier = Modifier.weight(1f),
            ) { ink ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.recirc, tint = ink, height = 26.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText("RECIRC", style = Type.modeLabelSmall.copy(color = ink))
                }
            }

            ModeTile(
                palette, active = live && state.maxAcOn, height = Dimens.modeTileHeight,
                activeFill = palette.cool, activeInk = palette.surface,
                onClick = { onCommand(Command.MAX_AC) }, modifier = Modifier.weight(1f),
            ) { ink -> BasicText("MAX A/C", style = Type.modeLabelSmall.copy(color = ink)) }
        }

        Spacer(Modifier.height(14.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModeTile(
                palette, active = live && state.frontDefrostOn, height = Dimens.modeTileHeight,
                activeFill = palette.accent, activeInk = palette.accentInk,
                onClick = { onCommand(Command.FRONT_DEFROST) }, modifier = Modifier.weight(1f),
            ) { ink ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.defrost, tint = ink, height = 44.dp)
                    Spacer(Modifier.width(10.dp))
                    BasicText("FRONT DEF", style = Type.modeLabelTiny.copy(color = ink))
                }
            }

            ModeTile(
                palette, active = live && state.rearDefrostOn, height = Dimens.modeTileHeight,
                activeFill = palette.accent, activeInk = palette.accentInk,
                onClick = { onCommand(Command.REAR_DEFROST) }, modifier = Modifier.weight(1f),
            ) { ink ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RearDefrostGlyph(tint = ink)
                    Spacer(Modifier.width(10.dp))
                    BasicText("REAR DEF", style = Type.modeLabelTiny.copy(color = ink))
                }
            }

            // SYNC lives here rather than as a pill on the passenger zone, so no
            // target on this page falls below 96dp. It drives U_AIR_SYNC; the
            // factory label says DUAL, which is why DUAL is the sub-label.
            ModeTile(
                palette, active = live && state.syncOn, height = Dimens.modeTileHeight,
                activeFill = palette.accent, activeInk = palette.accentInk,
                onClick = { onCommand(Command.SYNC) }, modifier = Modifier.weight(1f),
            ) { ink ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText("SYNC", style = Type.modeLabelSmall.copy(color = ink))
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        "DUAL",
                        style = Type.syncSub.copy(color = ink.copy(alpha = 0.4f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTile(
    palette: Palette,
    active: Boolean,
    height: Dp,
    activeFill: Color,
    activeInk: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (ink: Color) -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    Box(
        modifier
            .height(height)
            .background(if (active) activeFill else Color.Transparent, shape)
            // Layered over the resting fill, never replacing it: a filled tile
            // brightens on press rather than fading. Fading would read as the
            // mode switching off at the instant it is touched, on a tile whose
            // fill *is* the statement that the mode is selected.
            .then(if (pressed.value) Modifier.pressedTint(active, palette, shape) else Modifier)
            .then(if (active) Modifier else Modifier.border(Dimens.controlBorderWidth, palette.borderControl, shape))
            .target(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (active) activeInk else palette.inkDim)
    }
}
