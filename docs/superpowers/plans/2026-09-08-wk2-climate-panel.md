# wk2-climate Plan 3: Screen 1d, the Expanded Climate Page

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every climate function in the vehicle, each reachable in one tap with nothing nested, opening over app content from the bar's CLIMATE button.

**Architecture:** New composables in `:ui` under `bar/`'s sibling `panel/`, assembled into one `ClimatePanel` that fills exactly the inset-reduced app area. `:app` gains a second `ComposeOverlayHost` for the panel window, created on open and destroyed on close, plus back-gesture handling. Everything renders from `VehicleBus.state`, exactly as the bar does.

**Tech Stack:** Kotlin 2.3.0, AGP 9.0.1, Gradle 9.1.0, Compose (ui + foundation only).

**Spec:** `docs/superpowers/specs/2026-09-08-wk2-climate-design.md`, and the geometry authority `design_handoff_system_navigation/README.md` (screen **1d**).

## Global Constraints

- **Pressed feedback uses `Modifier.pressedTint(filled, palette, shape)`** from
  `ui/.../Interaction.kt`. A **filled** control brightens (white at 0.2 alpha);
  an **outlined** one gets a `surfaceRaised` wash. Layer it over the resting
  fill, never instead of it, and pass the control's shape or the wash paints
  square corners on a rounded tile. **Never reduce a filled control's alpha on
  press** — it reads as the setting switching off at the moment it is touched.
  `PRESSED_TINT_ALPHA` is only for controls that tint an accent *at rest*.
- **The 1.5dp outlined-control border is `Dimens.controlBorderWidth`.** Never a
  literal.

Everything from Plans 1 and 2 still applies. Restated because they bind every task here:

- **Do NOT apply `org.jetbrains.kotlin.android`** — AGP 9.0 rejects it. No `srcDir` calls.
- **No Material dependency.** `BasicText` only. Never add `ui-tooling` (it pulls `material`).
- **Filtered tests:** `./gradlew :<module>:testDebugUnitTest --tests '<pattern>'`.
- **`:ui` must never depend on `:app`.**
- **1px = 1dp.** Take geometry from the handoff's 1d section, not from this document.
- **96dp minimum on every tappable region**, no exceptions on this screen.
- **Pressed feedback on touch-down. No ripple, no shadows, no hover.**
- **No animation on any numeral.** The 220ms panel transition is the *only* animation on this screen.
- **Render only from `VehicleBus.state`.** A tap dispatches a `Command`; nothing mutates locally.

Plan-3-specific:

- **1d is exactly 1080 × 1693**, which is the inset-reduced app area. It opens *over* app content; the 227dp bar stays visible below it and is never covered.
- **The range track under each zone is a readout, not a drag target.** Drag-to-set was explicitly explored and rejected. If added later it must not shrink the `−`/`+` targets.
- **Airflow tiles are icon-only** — no text labels — and are idempotent setters. Exactly one is active, or none when the vehicle reports `NONE`/`UNKNOWN`.
- **Climate power is destructive.** `HOLD · OFF` requires an ~800ms hold with visible progress. **A single tap must do nothing.** This vehicle has no physical HVAC controls.
- **Controls for hardware that is not fitted are omitted, not disabled.** A permanently greyed tile wastes a 96dp slot.

---

## File Structure

```
ui/src/main/kotlin/com/wk2/climate/ui/panel/
    PanelSection.kt      section header + top-border container — Task 1
    PanelHeader.kt       title, OUT readout, CLOSE — Task 2
    PanelZones.kt        driver/passenger, 110dp steppers, range readout — Task 2
    PanelFan.kt          stepper / 7-bar meter / stepper, AUTO state — Task 3
    PanelAirflow.kt      four icon-only idempotent tiles — Task 4
    PanelMode.kt         AUTO, A/C, RECIRC, MAX A/C, defrosts, SYNC — Task 5
    RearDefrostGlyph.kt  drawn glyph; the icon set has none — Task 5
    PanelComfort.kt      four seat tiles with pips, heated wheel — Task 6
    PanelFooter.kt       explanatory copy, HOLD · OFF — Task 7
    ClimatePanel.kt      assembles 1d — Task 8
ui/src/test/kotlin/com/wk2/climate/ui/panel/
    HoldToConfirmTest.kt — Task 7
ui/src/main/kotlin/com/wk2/climate/ui/
    HoldToConfirm.kt     progress-over-time logic, pure — Task 7

design/src/main/kotlin/com/wk2/climate/design/
    Type.kt              (modified) 1d's text styles — Task 1

app/src/main/kotlin/com/wk2/climate/app/
    ClimateBarService.kt (modified) panel window, transition, back — Task 9
```

---

## Task 1: Section scaffolding and 1d's type styles

**Files:**
- Modify: `design/src/main/kotlin/com/wk2/climate/design/Type.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelSection.kt`

**Interfaces:**
- Consumes: `Palette`, `Type`.
- Produces: added `Type` styles `sectionHeader`, `panelTitle`, `panelStatus`, `closeLabel`, `zoneValueLarge`, `zoneDegreeLarge`, `zoneStepperGlyph`, `fanValue`, `fanValueDenominator`, `fanStepperMinus`, `fanStepperPlus`, `modeLabelLarge`, `modeLabelSmall`, `comfortTitle`, `comfortState`, `wheelLabel`, `footerCopy`, `holdOffLabel`, `syncSub`. Plus `@Composable fun PanelSection(palette: Palette, header: String?, content: @Composable ColumnScope.() -> Unit)`.

- [ ] **Step 1: Add 1d's styles to `Type.kt`**

Append inside `object Type`, after the screen-2a block. Tracking in `em` from
the handoff is converted to `sp` at the stated size (`.16em` at 12px = 1.92sp).

```kotlin
    // ---- screen 1d ----

    /** Every section header. IBM Plex Mono 600 12px, ls .16em. */
    val sectionHeader = mono(12.sp, FontWeight.SemiBold, 1.92.sp)

    /** "Climate". Manrope 800 30px, ls -0.01em. */
    val panelTitle = ui(30.sp, FontWeight.ExtraBold, (-0.3).sp)

    /** "OUT 41°F". IBM Plex Mono 500 15px. */
    val panelStatus = mono(15.sp, FontWeight.Medium, 0.sp)

    /** CLOSE. Manrope 700 16px. */
    val closeLabel = ui(16.sp, FontWeight.Bold)

    /** The big zone numeral. Manrope 800 96px, ls -0.05em. */
    val zoneValueLarge = ui(96.sp, FontWeight.ExtraBold, (-4.8).sp)

    /** Its degree mark, 38px. */
    val zoneDegreeLarge = ui(38.sp, FontWeight.ExtraBold)

    /** The 110dp steppers' − / +. Manrope 300 52px. */
    val zoneStepperGlyph = ui(52.sp, FontWeight.Light)

    /** Fan value. Manrope 700 20px. */
    val fanValue = ui(20.sp, FontWeight.Bold)

    /** The "/ 7" after it. Manrope 500 14px. */
    val fanValueDenominator = ui(14.sp, FontWeight.Medium)

    /** Fan − / +. Manrope 300 46px. */
    val fanStepperMinus = ui(46.sp, FontWeight.Light)
    val fanStepperPlus = ui(42.sp, FontWeight.Light)

    /** AUTO / A/C tiles. Manrope 800 19px, ls .05em. */
    val modeLabelLarge = ui(19.sp, FontWeight.ExtraBold, 0.95.sp)

    /** RECIRC / MAX A/C / SYNC. Manrope 800 17px, ls .05em. */
    val modeLabelSmall = ui(17.sp, FontWeight.ExtraBold, 0.85.sp)

    /** FRONT DEF / REAR DEF. Manrope 800 15px, ls .04em. */
    val modeLabelTiny = ui(15.sp, FontWeight.ExtraBold, 0.6.sp)

    /** SYNC's "DUAL" sub-label. IBM Plex Mono 500 12px. */
    val syncSub = mono(12.sp, FontWeight.Medium, 0.sp)

    /** Comfort tile titles. Manrope 700 15px, ls .04em. */
    val comfortTitle = ui(15.sp, FontWeight.Bold, 0.6.sp)

    /** OFF / LOW / HIGH. IBM Plex Mono 800 13px, ls .12em. */
    val comfortState = mono(13.sp, FontWeight.Bold, 1.56.sp)

    /** HEATED STEERING WHEEL. Manrope 800 18px, ls .05em. */
    val wheelLabel = ui(18.sp, FontWeight.ExtraBold, 0.9.sp)

    /** Footer explanatory copy. Manrope 400 13px, line-height 1.45. */
    val footerCopy = ui(13.sp, FontWeight.Normal).copy(lineHeight = 18.85.sp)

    /** HOLD · OFF. Manrope 700 15px, ls .06em. */
    val holdOffLabel = ui(15.sp, FontWeight.Bold, 0.9.sp)
```

- [ ] **Step 2: Write `PanelSection.kt`**

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type

/**
 * One section of screen 1d: a 1dp top border, an optional micro-header, then
 * content.
 *
 * Sections are separated by a border rather than by gaps, which is what lets
 * the page's child heights sum to a known total and the footer pin to the
 * bottom with the remaining space.
 */
@Composable
fun PanelSection(
    palette: Palette,
    header: String?,
    modifier: Modifier = Modifier,
    topBorder: Boolean = true,
    /**
     * The handoff's section padding, `20px 34px 24px`.
     *
     * Overridable because the temperature-zone section must pass **zero**: each
     * zone carries its own `30/34/34` padding, and that per-zone padding *is*
     * the page gutter. Applying both would put the gutter at 68dp and squeeze
     * the 110dp steppers and the 96px numeral into ~944dp of a 1080dp page.
     */
    contentPadding: PaddingValues = PaddingValues(
        start = Dimens.pageGutter,
        end = Dimens.pageGutter,
        top = 20.dp,
        bottom = 24.dp,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (topBorder) Modifier.topDivider(palette.divider) else Modifier)
            .padding(contentPadding),
    ) {
        if (header != null) {
            BasicText(
                text = header,
                style = Type.sectionHeader.copy(color = palette.inkMuted),
            )
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

/**
 * A 1dp divider along the top edge.
 *
 * Drawn rather than laid out, so it never participates in measurement — the
 * same reason the bar's dividers are drawn.
 */
fun Modifier.topDivider(color: Color): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(color = color, topLeft = Offset(0f, 0f), size = Size(size.width, h))
}
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add design/src/main/kotlin/com/wk2/climate/design/Type.kt ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelSection.kt
git commit -m "feat(ui): 1d type styles and section scaffolding

Adds screen 1d's text styles to the shared scale, with the handoff's em
tracking converted at each stated size.

PanelSection gives every section a 1dp drawn top border and an optional
micro-header. Sections are separated by borders rather than gaps, which
is what lets the page's child heights sum to a known total so the footer
can pin to the remaining space."
```

---

## Task 2: Header and temperature zones

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelHeader.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelZones.kt`

**Interfaces:**
- Consumes: `Temp`, `Palette`, `Type`, `Dimens`, `rememberPressState`, `target`, `holdRepeatTarget`.
- Produces: `@Composable fun PanelHeader(palette, outsideF: Int?, onClose: () -> Unit)` (112dp tall) and `@Composable fun PanelZones(palette, driver: Temp, passenger: Temp, onDriverDown/Up, onPassengerDown/Up: () -> Unit)`.

- [ ] **Step 1: Write `PanelHeader.kt`**

The status line shows **OUT only**. There is no cabin-temperature signal on
this vehicle — the handoff's `CABIN 64°F` is removed rather than stubbed, per
spec section 5.3. And because outside temperature is not currently decoded
either, `outsideF == null` hides the whole status line rather than showing a
placeholder.

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

@Composable
fun PanelHeader(
    palette: Palette,
    outsideF: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight)
            .padding(horizontal = Dimens.pageGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(text = "Climate", style = Type.panelTitle.copy(color = palette.ink))
            if (outsideF != null) {
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = "OUT ${outsideF}\u00B0F",
                    style = Type.panelStatus.copy(color = palette.inkFaint),
                )
            }
        }

        val (interaction, pressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.closeButtonWidth)
                .height(Dimens.minTarget)
                .border(Dimens.controlBorderWidth, palette.borderControlLarge, RoundedCornerShape(Dimens.radiusPill))
                .then(
                    if (pressed.value) {
                        Modifier.background(palette.surfaceRaised, RoundedCornerShape(Dimens.radiusPill))
                    } else {
                        Modifier
                    },
                )
                .target(interaction, onClick = onClose),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "\u25BC",
                style = Type.barCaret.copy(color = palette.ink.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.width(9.dp))
            BasicText(text = "CLOSE", style = Type.closeLabel.copy(color = palette.ink))
        }
    }
}
```

Add `import androidx.compose.foundation.background`.

- [ ] **Step 2: Write `PanelZones.kt`**

The range track is a **readout**. It shows where the setpoint sits in the
range; it is not draggable, and the `−`/`+` targets are what set the value.

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Temp
import com.wk2.climate.bus.TempUnit
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.holdRepeatTarget
import com.wk2.climate.ui.rememberPressState

@Composable
fun PanelZones(
    palette: Palette,
    driver: Temp,
    passenger: Temp,
    onDriverDown: () -> Unit,
    onDriverUp: () -> Unit,
    onPassengerDown: () -> Unit,
    onPassengerUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        Zone(palette, "DRIVER", driver, onDriverDown, onDriverUp, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(palette.divider),
        )
        Zone(palette, "PASSENGER", passenger, onPassengerDown, onPassengerUp, Modifier.weight(1f))
    }
}

@Composable
private fun Zone(
    palette: Palette,
    label: String,
    temp: Temp,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(
            start = Dimens.pageGutter,
            end = Dimens.pageGutter,
            top = 30.dp,
            bottom = 34.dp,
        ),
    ) {
        BasicText(text = label, style = Type.sectionHeader.copy(color = palette.inkMuted))
        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoneStepper(palette, "\u2212", palette.cool, palette.coolBright, onDown)
            BasicText(text = zoneText(temp, palette), style = Type.zoneValueLarge)
            ZoneStepper(palette, "+", palette.warm, palette.warmBright, onUp)
        }

        Spacer(Modifier.height(24.dp))
        RangeTrack(palette, temp)
    }
}

@Composable
private fun ZoneStepper(
    palette: Palette,
    glyph: String,
    tint: Color,
    glyphColor: Color,
    onFire: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Box(
        Modifier
            .size(Dimens.zoneStepper)
            .background(
                tint.copy(alpha = if (pressed.value) 0.34f else 0.18f),
                RoundedCornerShape(Dimens.radiusStepper),
            )
            .border(Dimens.controlBorderWidth, tint.copy(alpha = 0.5f), RoundedCornerShape(Dimens.radiusStepper))
            .holdRepeatTarget(interaction, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = glyph, style = Type.zoneStepperGlyph.copy(color = glyphColor))
    }
}

@Composable
private fun zoneText(temp: Temp, palette: Palette): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = palette.ink)) {
            when (temp) {
                is Temp.Degrees -> {
                    append(
                        when (temp.unit) {
                            TempUnit.FAHRENHEIT -> temp.value.toInt().toString()
                            TempUnit.CELSIUS -> String.format("%.1f", temp.value)
                        },
                    )
                    withStyle(
                        SpanStyle(
                            fontSize = Type.zoneDegreeLarge.fontSize,
                            baselineShift = BaselineShift.Superscript,
                        ),
                    ) { append("\u00B0") }
                }
                Temp.Lo -> append("LOW")
                Temp.Hi -> append("HIGH")
                Temp.Unavailable -> append("\u2014")
            }
        }
    }

/**
 * Where the setpoint sits in the range. **A readout, not a drag target** —
 * drag-to-set was explored during design and rejected. If it is ever added it
 * must not shrink the 110dp steppers.
 *
 * A sentinel or unknown temperature hides the knob rather than parking it at
 * one end, which would imply a setpoint the vehicle never reported.
 */
@Composable
private fun RangeTrack(palette: Palette, temp: Temp) {
    val fraction = (temp as? Temp.Degrees)
        ?.takeIf { it.unit == TempUnit.FAHRENHEIT }
        ?.let {
            ((it.value - REACHABLE_MIN_F) / (REACHABLE_MAX_F - REACHABLE_MIN_F))
                .coerceIn(0f, 1f)
        }

    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(palette.trackStart, palette.trackEnd))),
        )
        if (fraction != null) {
            Layout(
                content = {
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(palette.ink, CircleShape)
                            .border(3.dp, palette.surface, CircleShape),
                    )
                },
            ) { measurables, constraints ->
                val knob = measurables.first().measure(constraints)
                layout(constraints.maxWidth, knob.height) {
                    val x = ((constraints.maxWidth - knob.width) * fraction).toInt()
                    knob.placeRelative(x, 0)
                }
            }
        }
    }
}
```

Add these constants at the bottom of `PanelZones.kt`:

```kotlin
/**
 * The setpoint range this vehicle can actually reach, measured by sweeping the
 * driver setpoint to both ends: it clamps at 60 and 84 F, then steps into the
 * LOW and HIGH sentinels.
 *
 * Deliberately **not** the protocol's 30..128 window (`Temp.RAW_MIN`/`RAW_MAX`)
 * — that is what the bus will *accept*, not what this car will produce, and
 * using it would park the knob a third of the way along for a mid-range
 * setpoint. Deliberately not `FakeVehicleBus`'s copy either: production UI must
 * not depend on the test fake.
 */
private const val REACHABLE_MIN_F = 60f
private const val REACHABLE_MAX_F = 84f
```

> The track is Fahrenheit-only for now: in Celsius mode the reachable range in
> °C has not been measured, and guessing it would misplace the knob. In that
> mode the knob is simply hidden, which is the same treatment as a sentinel.

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelHeader.kt ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelZones.kt
git commit -m "feat(ui): 1d header and temperature zones

The header shows OUT only. There is no cabin-temperature signal on this
vehicle, so the handoff's CABIN reading is removed rather than stubbed;
and since outside temperature is not decoded either, a null value hides
the status line instead of showing a placeholder.

Zones get 110dp steppers with hold-repeat and a 96px numeral. Sentinels
render as LO or a dash, never as a number.

The range track is a readout. A sentinel hides the knob rather than
parking it at one end, which would imply a setpoint the vehicle never
reported."
```

---

## Task 3: The fan section

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelFan.kt`

**Interfaces:**
- Consumes: `Fan`, `Palette`, `Type`, `Dimens`, `holdRepeatTarget`.
- Produces: `@Composable fun PanelFan(palette: Palette, fan: Fan, onDown: () -> Unit, onUp: () -> Unit)`.

**Seven bars, not twelve.** The handoff drew a 12-bar meter for an assumed
15-step fan. Measurement established the fan is **1–7 with 15 as the AUTO
sentinel**, so the meter is one bar per level and the rounding the handoff
described is deleted. See spec section 5.1.

- [ ] **Step 1: Write `PanelFan.kt`**

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Fan
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.holdRepeatTarget
import com.wk2.climate.ui.rememberPressState

@Composable
fun PanelFan(
    palette: Palette,
    fan: Fan,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = "FAN", style = Type.sectionHeader.copy(color = palette.inkMuted))
            BasicText(text = fanReadout(fan, palette), style = Type.fanValue)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FanStepper(palette, "\u2212", Type.fanStepperMinus, onDown)
            FanMeter(palette, fan, Modifier.weight(1f))
            FanStepper(palette, "+", Type.fanStepperPlus, onUp)
        }
    }
}

/**
 * `5 / 7`, or `AUTO`.
 *
 * 15 is never rendered as a number: it is the sentinel the vehicle reports
 * while AUTO owns the blower, and measurement showed the fan physically runs
 * at 3 while 15 is reported.
 */
@Composable
private fun fanReadout(fan: Fan, palette: Palette) = buildAnnotatedString {
    when (fan) {
        is Fan.Level -> {
            withStyle(SpanStyle(color = palette.ink)) { append(fan.step.toString()) }
            withStyle(
                SpanStyle(
                    color = palette.inkFaint,
                    fontSize = Type.fanValueDenominator.fontSize,
                    fontWeight = Type.fanValueDenominator.fontWeight,
                ),
            ) { append(" / ${Fan.MAX_STEP}") }
        }
        Fan.Auto -> withStyle(SpanStyle(color = palette.accent)) { append("AUTO") }
        Fan.Unavailable -> withStyle(SpanStyle(color = palette.inkFaint)) { append("\u2014") }
    }
}

@Composable
private fun FanStepper(
    palette: Palette,
    glyph: String,
    style: androidx.compose.ui.text.TextStyle,
    onFire: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Box(
        Modifier
            .width(Dimens.fanStepperWidth)
            .height(Dimens.fanMeterHeight)
            .then(
                if (pressed.value) {
                    Modifier.background(palette.surfaceRaised, RoundedCornerShape(Dimens.radiusTile))
                } else {
                    Modifier
                },
            )
            .border(Dimens.controlBorderWidth, palette.borderControlLarge, RoundedCornerShape(Dimens.radiusTile))
            .holdRepeatTarget(interaction, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = glyph, style = style.copy(color = palette.ink))
    }
}

/**
 * One bar per fan level. A readout; the steppers drive the value.
 *
 * Under AUTO every bar lights in the accent colour but dimmed, so the meter
 * reads as "not driver-set" rather than as maximum fan.
 */
@Composable
private fun FanMeter(palette: Palette, fan: Fan, modifier: Modifier = Modifier) {
    // The handoff's 12-bar ramp re-spread across 7 bars.
    val ramp = listOf(0.30f, 0.42f, 0.53f, 0.65f, 0.76f, 0.88f, 1.00f)
    val filled = when (fan) {
        is Fan.Level -> fan.step
        Fan.Auto -> Fan.MAX_STEP
        Fan.Unavailable -> 0
    }
    val auto = fan == Fan.Auto

    Row(
        modifier
            .height(Dimens.fanMeterHeight)
            .background(palette.surfaceRaised, RoundedCornerShape(Dimens.radiusTile))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(if (auto) Modifier.alpha(0.55f) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ramp.forEachIndexed { index, fractionOfHeight ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fractionOfHeight)
                    .background(
                        if (index < filled) palette.accent else palette.ink.copy(alpha = 0.13f),
                        RoundedCornerShape(Dimens.radiusPip),
                    ),
            )
        }
    }
}
```

- [ ] **Step 2: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelFan.kt
git commit -m "feat(ui): 1d fan section

Seven bars, one per level. The handoff drew twelve for an assumed
15-step fan; measurement established 1-7 with 15 as the AUTO sentinel,
so the rounding it described is deleted and the bar count means
something again.

Under AUTO every bar lights but dimmed, so the meter reads as
not-driver-set rather than as maximum fan -- which is what showing 15 as
a level would have implied."
```

---

## Task 4: Airflow tiles

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelAirflow.kt`

**Interfaces:**
- Consumes: `AirflowMode`, `Command`, `Glyph`, `GlyphIcon`, `Palette`, `Dimens`.
- Produces: `@Composable fun PanelAirflow(palette: Palette, mode: AirflowMode, onSelect: (Command) -> Unit)`.

- [ ] **Step 1: Write `PanelAirflow.kt`**

```kotlin
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
import androidx.compose.runtime.getValue
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
 */
@Composable
fun PanelAirflow(
    palette: Palette,
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
            // Layered over the resting fill, never replacing it. A filled tile
            // BRIGHTENS on press; reducing its alpha reads as the setting
            // switching off at the instant it is touched. See
            // Modifier.pressedTint in ui/.../Interaction.kt.
            .then(if (pressed.value) Modifier.pressedTint(active, palette, shape) else Modifier)
            .then(if (active) Modifier else Modifier.border(Dimens.controlBorderWidth, palette.borderControl, shape))
            .target(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // One asset per glyph; active vs inactive is a tint, not a different file.
        GlyphIcon(
            res = res,
            tint = if (active) palette.accentInk else palette.ink,
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
```

- [ ] **Step 2: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelAirflow.kt
git commit -m "feat(ui): 1d airflow tiles, direct selection

Four separately-lit icon-only tiles over the protocol's four idempotent
setters, so re-tapping the active mode is a genuine no-op rather than
advancing a cycle. The factory bar makes you tap up to four times to
land on a mode; this never does.

When the vehicle reports NONE -- its resting state while AUTO owns
airflow -- or an unrecognised flag combination, no tile lights. Lighting
a wrong tile is worse than lighting none."
```

---

## Task 5: The mode grids

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/RearDefrostGlyph.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelMode.kt`

**Interfaces:**
- Consumes: `ClimateState`, `Command`, `Glyph`, `GlyphIcon`, `Palette`, `Dimens`.
- Produces: `@Composable fun RearDefrostGlyph(tint: Color, modifier: Modifier)` (50×44) and `@Composable fun PanelMode(palette: Palette, state: ClimateState, onCommand: (Command) -> Unit)`.

- [ ] **Step 1: Write `RearDefrostGlyph.kt`**

The supplied icon set has no rear-defrost glyph, so it is drawn: a rounded
rectangle with three wave strokes, at `50 × 44` with a `3.5dp` stroke chosen to
match the bitmaps' visual weight.

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Rear-window defrost.
 *
 * Drawn rather than shipped as an asset because the supplied automotive icon
 * set has no rear-defrost glyph. Stroke weight is tuned to sit alongside the
 * bitmap glyphs rather than to any independent standard.
 */
@Composable
fun RearDefrostGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 50.dp, height = 44.dp)) {
        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f

        // The window outline.
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke.width, size.height - stroke.width),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = stroke,
        )

        // Three heat waves rising through it.
        val left = size.width * 0.28f
        val right = size.width * 0.72f
        val amplitude = size.width * 0.07f
        listOf(0.34f, 0.54f, 0.74f).forEach { yFraction ->
            val y = size.height * yFraction
            val path = Path().apply {
                moveTo(left, y)
                cubicTo(
                    left + (right - left) * 0.33f, y - amplitude,
                    left + (right - left) * 0.66f, y + amplitude,
                    right, y,
                )
            }
            drawPath(path, color = tint, style = stroke)
        }
    }
}
```

- [ ] **Step 2: Write `PanelMode.kt`**

`SYNC` lives here rather than as a small pill on the passenger zone,
specifically so no target on this page falls below 96dp.

```kotlin
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.Glyph
import com.wk2.climate.ui.GlyphIcon
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

@Composable
fun PanelMode(
    palette: Palette,
    state: ClimateState,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModeTile(
                palette, active = state.autoOn, height = Dimens.modeTileRow1,
                activeFill = palette.accent, activeInk = palette.accentInk,
                onClick = { onCommand(Command.AUTO) }, modifier = Modifier.weight(1f),
            ) { ink -> BasicText("AUTO", style = Type.modeLabelLarge.copy(color = ink)) }

            ModeTile(
                palette, active = state.acOn, height = Dimens.modeTileRow1,
                activeFill = palette.cool, activeInk = palette.surface,
                onClick = { onCommand(Command.AC) }, modifier = Modifier.weight(1f),
            ) { ink -> BasicText("A/C", style = Type.modeLabelLarge.copy(color = ink)) }

            ModeTile(
                palette, active = state.recircOn, height = Dimens.modeTileRow1,
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
                palette, active = state.maxAcOn, height = Dimens.modeTileRow1,
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
                palette, active = state.frontDefrostOn, height = Dimens.modeTileRow2,
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
                palette, active = state.rearDefrostOn, height = Dimens.modeTileRow2,
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
                palette, active = state.syncOn, height = Dimens.modeTileRow2,
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
            // Layered over the resting fill, never replacing it. A filled tile
            // BRIGHTENS on press; reducing its alpha reads as the setting
            // switching off at the instant it is touched. See
            // Modifier.pressedTint in ui/.../Interaction.kt.
            .then(if (pressed.value) Modifier.pressedTint(active, palette, shape) else Modifier)
            .then(if (active) Modifier else Modifier.border(Dimens.controlBorderWidth, palette.borderControl, shape))
            .target(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content(if (active) activeInk else palette.inkDim)
    }
}
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelMode.kt ui/src/main/kotlin/com/wk2/climate/ui/panel/RearDefrostGlyph.kt
git commit -m "feat(ui): 1d mode grids

AUTO, A/C, RECIRC and MAX A/C at 104dp; front and rear defrost and SYNC
at 96dp. SYNC lives here rather than as a pill on the passenger zone
precisely so no target on this page falls below the 96dp floor. Its
sub-label reads DUAL because that is what the factory bar calls it,
while the signal it actually drives is U_AIR_SYNC.

Rear defrost is drawn on a Canvas -- the supplied icon set has no such
glyph -- with a stroke weight tuned to sit alongside the bitmaps."
```

---

## Task 6: The comfort grid

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt`

**Interfaces:**
- Consumes: `SeatLevel`, `ClimateState`, `Command`, `Palette`, `Dimens`.
- Produces: `@Composable fun PanelComfort(palette: Palette, state: ClimateState, onCommand: (Command) -> Unit)`.

**Two pips, three states.** Measurement established the vehicle's own cycle is
`0 → 3 → 1 → 0`, never visiting 2, so `SeatLevel`'s three presented states lose
nothing and every tap changes what the driver sees. See spec section 5.2.

- [ ] **Step 1: Write `PanelComfort.kt`**

```kotlin
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

@Composable
fun PanelComfort(
    palette: Palette,
    state: ClimateState,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ComfortTile(
                palette, "SEAT HEAT \u00B7 L", state.seatHeatL, palette.warm,
                { onCommand(Command.SEAT_HEAT_L) }, Modifier.weight(1f),
            )
            ComfortTile(
                palette, "SEAT HEAT \u00B7 R", state.seatHeatR, palette.warm,
                { onCommand(Command.SEAT_HEAT_R) }, Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ComfortTile(
                palette, "SEAT COOL \u00B7 L", state.seatVentL, palette.cool,
                { onCommand(Command.SEAT_VENT_L) }, Modifier.weight(1f),
            )
            ComfortTile(
                palette, "SEAT COOL \u00B7 R", state.seatVentR, palette.cool,
                { onCommand(Command.SEAT_VENT_R) }, Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))
        HeatedWheel(palette, state.wheelHeatOn) { onCommand(Command.WHEEL_HEAT) }
    }
}

/**
 * One seat control.
 *
 * Two pips carry three states: none lit is OFF, one is LOW, both are HIGH.
 * That is not a compromise — the vehicle's own cycle is `0 -> 3 -> 1 -> 0` and
 * never visits 2, so every tap changes what the driver sees.
 *
 * An UNAVAILABLE level shows a dash and no lit pips rather than OFF: claiming
 * a control is off when we do not know is worse than admitting we do not.
 */
@Composable
private fun ComfortTile(
    palette: Palette,
    title: String,
    level: SeatLevel,
    litColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    val on = level.isOn

    Column(
        modifier
            .height(Dimens.comfortTileHeight)
            .background(
                if (pressed.value) palette.ink.copy(alpha = 0.08f) else palette.surfaceRaised,
                shape,
            )
            .border(
                Dimens.controlBorderWidth,
                if (on) litColor.copy(alpha = 0.5f) else palette.ink.copy(alpha = 0.14f),
                shape,
            )
            .target(interaction, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(text = title, style = Type.comfortTitle.copy(color = palette.ink))

        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(
                            if (index < level.litPips) litColor else palette.ink.copy(alpha = 0.13f),
                            RoundedCornerShape(7.dp),
                        ),
                )
                if (index == 0) Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.width(14.dp))
            BasicText(
                text = when (level) {
                    SeatLevel.OFF -> "OFF"
                    SeatLevel.LOW -> "LOW"
                    SeatLevel.HIGH -> "HIGH"
                    SeatLevel.UNAVAILABLE -> "\u2014"
                },
                style = Type.comfortState.copy(
                    color = if (on) litColor else palette.inkFaint,
                ),
            )
        }
    }
}

@Composable
private fun HeatedWheel(palette: Palette, on: Boolean, onClick: () -> Unit) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.comfortTileHeight)
            .background(
                palette.warm.copy(alpha = if (pressed.value) 0.24f else if (on) 0.12f else 0.04f),
                shape,
            )
            .border(Dimens.controlBorderWidth, palette.warm.copy(alpha = if (on) 0.5f else 0.2f), shape)
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .then(
                    if (on) {
                        Modifier.background(palette.warmBright, CircleShape)
                    } else {
                        Modifier.border(3.dp, palette.warmBright, CircleShape)
                    },
                ),
        )
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = "HEATED STEERING WHEEL",
            style = Type.wheelLabel.copy(color = palette.ink),
        )
    }
}
```

- [ ] **Step 2: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt
git commit -m "feat(ui): 1d comfort grid

Four seat tiles and the heated wheel, all at 96dp. Two pips carry three
states, which is not a compromise: the vehicle's own cycle is
0 -> 3 -> 1 -> 0 and never visits 2, so every tap changes what the driver
sees.

An UNAVAILABLE level shows a dash and no lit pips rather than OFF.
Claiming a control is off when we do not know is worse than admitting we
do not."
```

---

## Task 7: The footer and hold-to-confirm power

The only destructive control in the app. This vehicle has **no physical HVAC
controls**, so powering climate off leaves no way to change anything until it
is sent again — which is why a single tap must do nothing at all.

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/HoldToConfirm.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelFooter.kt`
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/HoldToConfirmTest.kt`

**Interfaces:**
- Consumes: `Palette`, `Dimens`, `Type`.
- Produces: `object HoldToConfirm` with `fun progressAt(heldMs: Long, requiredMs: Long): Float` and `fun isConfirmed(heldMs: Long, requiredMs: Long): Boolean`; `@Composable fun PanelFooter(palette: Palette, onPowerOff: () -> Unit)`.

- [ ] **Step 1: Write the failing test**

`ui/src/test/kotlin/com/wk2/climate/ui/HoldToConfirmTest.kt`:

```kotlin
package com.wk2.climate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldToConfirmTest {

    private val required = 800L

    @Test
    fun `a tap makes no progress worth showing and never confirms`() {
        // The whole point: this vehicle has no physical HVAC controls, so a
        // stray tap must not be able to power climate off.
        assertFalse(HoldToConfirm.isConfirmed(0, required))
        assertFalse(HoldToConfirm.isConfirmed(50, required))
        assertFalse(HoldToConfirm.isConfirmed(required - 1, required))
    }

    @Test
    fun `it confirms exactly at the required hold`() {
        assertTrue(HoldToConfirm.isConfirmed(required, required))
        assertTrue(HoldToConfirm.isConfirmed(required + 100, required))
    }

    @Test
    fun `progress runs zero to one across the hold`() {
        assertEquals(0f, HoldToConfirm.progressAt(0, required), 0.001f)
        assertEquals(0.5f, HoldToConfirm.progressAt(400, required), 0.001f)
        assertEquals(1f, HoldToConfirm.progressAt(800, required), 0.001f)
    }

    @Test
    fun `progress is clamped past the hold`() {
        assertEquals(1f, HoldToConfirm.progressAt(5_000, required), 0.001f)
    }

    @Test
    fun `negative elapsed time is treated as no progress`() {
        assertEquals(0f, HoldToConfirm.progressAt(-100, required), 0.001f)
        assertFalse(HoldToConfirm.isConfirmed(-100, required))
    }

    @Test
    fun `a zero or negative requirement never confirms by accident`() {
        // Guards against a misconfiguration turning the destructive control
        // into a single tap.
        assertFalse(HoldToConfirm.isConfirmed(0, 0))
        assertFalse(HoldToConfirm.isConfirmed(10, 0))
        assertEquals(0f, HoldToConfirm.progressAt(10, 0), 0.001f)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests '*HoldToConfirmTest*'`
Expected: FAIL — unresolved reference `HoldToConfirm`.

- [ ] **Step 3: Write `HoldToConfirm.kt`**

```kotlin
package com.wk2.climate.ui

/**
 * Hold-to-confirm arithmetic for the one destructive control in the app.
 *
 * Kept separate and pure because the property that matters — **a tap can never
 * confirm** — is worth asserting in a test rather than trusting to gesture
 * plumbing. This vehicle has no physical HVAC controls, so an accidental
 * power-off leaves no way to change anything until it is sent again.
 */
object HoldToConfirm {

    /** Fraction of the required hold elapsed, clamped to 0..1. */
    fun progressAt(heldMs: Long, requiredMs: Long): Float {
        if (requiredMs <= 0L || heldMs <= 0L) return 0f
        return (heldMs.toFloat() / requiredMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * True only once the full hold has elapsed. A non-positive requirement
     * never confirms — a misconfiguration must fail closed, not turn the
     * destructive control into a single tap.
     */
    fun isConfirmed(heldMs: Long, requiredMs: Long): Boolean =
        requiredMs > 0L && heldMs >= requiredMs
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `./gradlew :ui:testDebugUnitTest --tests '*HoldToConfirmTest*'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Write `PanelFooter.kt`**

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.HoldToConfirm
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PanelFooter(
    palette: Palette,
    onPowerOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.pageGutter, vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "This vehicle has no physical climate controls. Turning the " +
                "system off leaves no way to change temperature, fan or defrost " +
                "until it is turned back on, so this control needs a press and hold.",
            style = Type.footerCopy.copy(color = palette.inkFaint),
            modifier = Modifier.widthIn(max = 640.dp),
        )
        Spacer(Modifier.width(Dimens.pageGutter))
        HoldOffButton(palette, onPowerOff)
    }
}

/**
 * `HOLD · OFF`.
 *
 * Fills with progress while held and fires only when the hold completes. A
 * single tap does nothing, and releasing early resets the fill to zero — the
 * progress is the affordance that tells the driver a hold is required.
 */
@Composable
private fun HoldOffButton(palette: Palette, onPowerOff: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(Dimens.radiusPill)

    Box(
        Modifier
            .width(Dimens.holdOffWidth)
            .height(Dimens.minTarget)
            .clip(shape)
            .border(Dimens.controlBorderWidth, palette.ink.copy(alpha = 0.18f), shape)
            .pointerInput(Unit) {
                // Structured exactly like Modifier.holdRepeatTarget in
                // ui/.../Interaction.kt -- read that first. The ticker is a
                // child of THIS coroutineScope, and cleanup lives in a
                // `finally`, so a cancellation arriving at the
                // waitForUpOrCancellation suspension point can neither skip the
                // cleanup nor orphan the ticker.
                //
                // That matters more here than anywhere else in the app: an
                // orphaned ticker would keep polling and eventually call
                // onPowerOff(), which dispatches Command.CLIMATE_POWER -- the
                // one command that leaves this vehicle with no way to change
                // climate until it is sent again.
                coroutineScope {
                    val gestureScope = this
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val start = System.currentTimeMillis()
                        val ticker = gestureScope.launch {
                            var fired = false
                            while (isActive && !fired) {
                                val held = System.currentTimeMillis() - start
                                progress = HoldToConfirm.progressAt(held, Dimens.POWER_HOLD_MS)
                                if (HoldToConfirm.isConfirmed(held, Dimens.POWER_HOLD_MS)) {
                                    fired = true
                                    onPowerOff()
                                }
                                delay(16)
                            }
                        }
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            ticker.cancel()
                            // Reset on release, whether or not it fired.
                            progress = 0f
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The fill, drawn behind the label.
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(palette.warm.copy(alpha = 0.35f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(palette.ink.copy(alpha = 0.35f), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "HOLD \u00B7 OFF",
                style = Type.holdOffLabel.copy(color = palette.ink.copy(alpha = 0.6f)),
            )
        }
    }
}
```

Required imports for `PanelFooter.kt`, in addition to those already listed:

```kotlin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
```

- [ ] **Step 6: Verify build and tests**

Run: `./gradlew :ui:testDebugUnitTest :ui:assembleDebug`
Expected: PASS, 12 tests in `:ui` (6 `HoldRepeat` + 6 `HoldToConfirm`). BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/HoldToConfirm.kt ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelFooter.kt ui/src/test/kotlin/com/wk2/climate/ui/HoldToConfirmTest.kt
git commit -m "feat(ui): 1d footer and hold-to-confirm power off

The only destructive control in the app. This vehicle has no physical
HVAC controls, so powering climate off leaves no way to change anything
until it is sent again -- which is why a single tap must do nothing.

The arithmetic is pure and tested, because the property that matters is
that a tap can never confirm, and that is worth asserting rather than
trusting to gesture plumbing. A non-positive requirement also fails
closed, so a misconfiguration cannot turn the control into a single tap.

The fill resets on release whether or not it fired; the progress is the
affordance that tells the driver a hold is required."
```

---

## Task 8: Assemble the panel and verify it on an emulator

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/ClimatePanel.kt`
- Modify: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt`

**Interfaces:**
- Consumes: every panel composable.
- Produces: `@Composable fun ClimatePanel(state: ClimateState, outsideF: Int?, onCommand: (Command) -> Unit, onClose: () -> Unit)`.

- [ ] **Step 1: Write `ClimatePanel.kt`**

```kotlin
package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.design.Palette

/**
 * Screen 1d: every climate function in the vehicle, each one tap away,
 * nothing nested. 1080 x 1693, which is exactly the inset-reduced app area, so
 * it fills that region with the 227dp bar still visible below it.
 *
 * Rendered entirely from [state]. Every tap dispatches a [Command] and mutates
 * nothing locally, which is what makes the protocol's side effects — the
 * macros forcing recirculation on, AUTO clearing when the fan moves — appear
 * correctly with no special-casing.
 */
@Composable
fun ClimatePanel(
    state: ClimateState,
    outsideF: Int?,
    onCommand: (Command) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Palette.forNight(state.isNight)

    Column(
        modifier
            .fillMaxSize()
            .background(palette.surface)
            // The handoff's section heights sum to 1664 of the 1693 available, so
            // this fits without scrolling on the target panel. The scroll is a
            // safety net for a different density, never the intended interaction.
            .verticalScroll(rememberScrollState()),
    ) {
        PanelHeader(palette = palette, outsideF = outsideF, onClose = onClose)

        // Zero padding: each zone supplies the page gutter itself. See
        // PanelSection's contentPadding doc.
        PanelSection(
            palette = palette,
            header = null,
            topBorder = true,
            contentPadding = PaddingValues(0.dp),
        ) {
            PanelZones(
                palette = palette,
                driver = state.tempLeft,
                passenger = state.tempRight,
                onDriverDown = { onCommand(Command.TEMP_L_DOWN) },
                onDriverUp = { onCommand(Command.TEMP_L_UP) },
                onPassengerDown = { onCommand(Command.TEMP_R_DOWN) },
                onPassengerUp = { onCommand(Command.TEMP_R_UP) },
            )
        }

        PanelSection(palette = palette, header = null) {
            PanelFan(
                palette = palette,
                fan = state.fan,
                onDown = { onCommand(Command.FAN_DOWN) },
                onUp = { onCommand(Command.FAN_UP) },
            )
        }

        PanelSection(palette = palette, header = "AIRFLOW \u2014 FOUR MODES, DIRECT") {
            PanelAirflow(palette = palette, mode = state.airflow, onSelect = onCommand)
        }

        PanelSection(palette = palette, header = "MODE") {
            PanelMode(palette = palette, state = state, onCommand = onCommand)
        }

        PanelSection(palette = palette, header = "COMFORT") {
            PanelComfort(palette = palette, state = state, onCommand = onCommand)
        }

        Spacer(Modifier.height(0.dp))
        PanelFooter(palette = palette, onPowerOff = { onCommand(Command.CLIMATE_POWER) })
    }
}
```

> The zone section passes `header = null` because each zone draws its own
> `DRIVER` / `PASSENGER` label, and the fan section draws its header inline
> beside the value.

- [ ] **Step 2: Add a panel mode to the harness**

In `HarnessActivity.kt`, add `import com.wk2.climate.ui.panel.ClimatePanel`, a
`var showPanel by remember { mutableStateOf(false) }`, a `PANEL` key beside
`BAR`, and this branch beside the existing `showBar` one:

```kotlin
    if (showPanel) {
        Column(Modifier.fillMaxSize()) {
            Key("INSPECTOR", palette) { showPanel = false }
            ClimatePanel(
                state = state,
                outsideF = outsideF,
                onCommand = { bus.send(it) },
                onClose = { showPanel = false },
            )
        }
        return
    }
```

- [ ] **Step 3: Build, install, and verify on the panel-geometry emulator**

```bash
export ANDROID_HOME="C:/Users/chris/AppData/Local/Android/Sdk"
"$ANDROID_HOME/emulator/emulator.exe" -avd wk2_panel -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect &
until [ "$(adb -e shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
./gradlew :harness:installDebug
adb -e shell am start -n com.wk2.climate.harness/.HarnessActivity
```

- [ ] **Step 4: Verify by hand**

Tap `PANEL`, then confirm with a screenshot and `uiautomator dump`:

1. Every section renders: header, both zones, fan, airflow, mode, comfort, footer.
2. Both zones read `68°` with 110dp steppers; the range knob sits about a third along.
3. Fan reads `AUTO` in amber with all seven bars lit but dimmed.
4. **No airflow tile is lit** — the fake bus starts at `NONE`, which is correct.
5. `AUTO` is filled amber in the mode grid; `A/C` is filled blue.
6. All four comfort tiles read `OFF` with no lit pips.
7. Tapping fan `+` drops fan to `Level(3)`, lights three bars, and lights the `FACE` airflow tile.
8. Tapping `MAX A/C` makes both zones read `LO` and lights `RECIRC`.
9. **Tapping `HOLD · OFF` once does nothing**; holding it ~800ms fills it and toggles power.
10. `NIGHT` in the inspector flips the palette with no layout change.

```bash
adb -e shell screencap -p /sdcard/panel.png
adb -e pull /sdcard/panel.png docs/screenshots/panel-1d.png
```

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/panel/ClimatePanel.kt harness docs/screenshots
git commit -m "feat(ui): assemble screen 1d and verify on the panel geometry

Every climate function one tap away, nothing nested, at 1080x1693 --
exactly the inset-reduced app area, so the 227dp bar stays visible below.

A verticalScroll is present as a safety net for a different density; the
handoff's sections sum to 1664 of 1693, so it never scrolls on the target
panel and scrolling is not an intended interaction.

Verified in the harness on an emulator at 1080x1920/160dpi, including
that a single tap on HOLD - OFF does nothing."
```

---

## Task 9: Wire the panel into the service, and verify in the truck

**Files:**
- Modify: `app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt`

- [ ] **Step 1: Add the panel window to the service**

The panel window fills the **inset-reduced app area** — `[0,0][1080,1693]` — so
it covers app content while leaving the bar visible. That is the design's
pattern C, and the factory UI already does exactly this, so it is known to work
on this hardware.

```kotlin
    private var panelHost: ComposeOverlayHost? = null

    /**
     * The panel covers app content but never the bar.
     *
     * Height is the inset-reduced app area, which is the screen minus the
     * navigation-bar inset — the same 227px the bar occupies. No
     * FLAG_LAYOUT_NO_LIMITS here: unlike the bar, we *want* the default
     * inset-reduced frame.
     */
    private fun panelWindowParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            resources.displayMetrics.heightPixels - navigationBarHeightPx(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

    private fun openPanel() {
        if (panelHost != null) return
        val host = ComposeOverlayHost(this)
        panelHost = host
        host.show(panelWindowParams()) {
            val state by bus.state.collectAsState()

            // 220ms ease-out slide up. The ONLY animation on this screen --
            // values must never animate.
            val offset = remember { Animatable(1f) }
            LaunchedEffect(Unit) {
                offset.animateTo(0f, tween(Dimens.PANEL_TRANSITION_MS, easing = EaseOut))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = offset.value * size.height },
            ) {
                ClimatePanel(
                    state = state,
                    outsideF = outsideF(state),
                    onCommand = { bus.send(it) },
                    onClose = { closePanel() },
                )
            }
        }
    }

    private fun closePanel() {
        panelHost?.destroy()
        panelHost = null
    }
```

Wire it to the bar by replacing the bar's `onOpenClimate` no-op:

```kotlin
                onOpenClimate = { openPanel() },
```

and extend `teardown()`:

```kotlin
        closePanel()
```

Required imports:

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.wk2.climate.ui.panel.ClimatePanel
```

- [ ] **Step 2: Close on the back gesture**

The design says the panel closes on back as well as on CLOSE. The service
already owns `GLOBAL_ACTION_BACK` for the bar, but the *system* back gesture
goes to the focused app, not to us — and our window is `FLAG_NOT_FOCUSABLE`.

Rather than take focus (which would disturb whatever app is running), intercept
back through the accessibility event we already receive:

```kotlin
    override fun onKeyEvent(event: android.view.KeyEvent?): Boolean {
        if (event?.keyCode == android.view.KeyEvent.KEYCODE_BACK && panelHost != null) {
            if (event.action == android.view.KeyEvent.ACTION_UP) closePanel()
            return true   // consume, so the underlying app does not also go back
        }
        return super.onKeyEvent(event)
    }
```

and add `android:canRequestFilterKeyEvents="true"` plus
`android:accessibilityFlags="flagDefault|flagRequestFilterKeyEvents"` to
`accessibility_config.xml`.

> If `onKeyEvent` does not fire on this ROM — the gesture-nav service
> `com.syu.fytgesture` may consume back before us — fall back to closing on a
> downward swipe on the panel, and record which route worked. The CLOSE button
> is always available regardless, so this is an enhancement rather than the
> only exit.

- [ ] **Step 3: Verify on the emulator, then the vehicle**

Emulator first, as in Plan 2 Task 8: enable the service, confirm two windows
appear when the panel opens (`1080x227` at `y=1693` and `1080x1693` at `y=0`),
and confirm no crash.

Then, on a **stationary vehicle** with a baseline taken:

1. Tapping `CLIMATE` on the bar slides the panel up in ~220ms; **the bar stays
   visible below it**.
2. Every control drives the real vehicle and the UI follows the vehicle's
   reported state, not the tap.
3. Airflow tiles: tapping the active one is a genuine no-op.
4. `MAX A/C` sets both zones to `LO` and lights `RECIRC` — the macro's real
   side effects, rendered truthfully.
5. `CLOSE` and the back gesture both dismiss it.
6. `HOLD · OFF` does nothing on a tap; holding powers climate off — **and
   confirm you can turn it back on**, since this vehicle has no physical
   controls.
7. Nothing beneath the panel actuates when you tap it.

- [ ] **Step 4: Commit**

```bash
git add app docs/screenshots
git commit -m "feat(app): open screen 1d from the bar

The panel fills the inset-reduced app area, so it covers app content but
never the bar -- the design's pattern C, which the factory UI already
uses on this hardware.

A 220ms ease-out slide is the only animation on the screen; values never
animate, because a moving numeral is unreadable at a glance.

Back is intercepted through key filtering rather than by taking focus,
since taking focus would disturb whatever app is running underneath. The
CLOSE button is always available, so back is an enhancement rather than
the only exit."
```

---

## Definition of done for Plan 3

- [ ] `./gradlew build` succeeds.
- [ ] `:bus` 71 tests, `:ui` 12 tests, all passing.
- [ ] No Material artifact on any runtime classpath and no Material import in any source.
- [ ] All ten checks in Task 8 Step 4 pass in the harness on a 1080x1920/160dpi emulator.
- [ ] On the vehicle: the panel opens over app content with the bar still visible, every control drives the vehicle, `CLOSE` works, and `HOLD · OFF` cannot be triggered by a tap.
- [ ] `com.syu.air` still alive and `app=1080x1693` unchanged after the whole session.

## Carried forward

- Outside temperature remains undecoded, so 1d's header omits its status line
  and the bar's adaptive slot stays pinned to `SEAT_HEAT`. The drive capture is
  the next attempt.
- Real icons for HOME, BACK, volume, seat and wheel — the handoff's text labels
  ship in the meantime.
- Drag-to-set on the range track, if ever wanted, must not shrink the 110dp
  steppers.
- Vector conversion of the glyph bitmaps.
