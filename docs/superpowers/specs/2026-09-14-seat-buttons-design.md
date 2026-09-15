# Screen 2a revision: seat buttons and seat menu — design

**Status:** implemented on `feat/seat-buttons` (plan: `../plans/2026-09-14-seat-buttons.md`); awaiting the owner's vehicle pass (§8) and answers to §10. Supersedes the adaptive
slot in `2026-09-08-wk2-climate-design.md` §6 and its row in §8. Everything else
in that spec stands.

**Source of truth for visuals:** the Claude Design project
`61f01850-1113-4014-8d27-ececed8865da`, file `System Navigation.dc.html`. The
copy in `design_handoff_system_navigation/` predates this change and must not be
used for the top row. Measurements below were taken from an owner-supplied
screenshot of that design at ~1.25 px per dp and are **approximate**; where they
disagree with the design file, the design file wins. Export the updated file
into the handoff folder before building if possible.

## 1. What changes

1. **The top row loses its adaptive cells.** It becomes four fixed controls:
   driver seat button, AUTO, CLIMATE, passenger seat button.
2. **The outside-temperature logic that drove those cells is deleted** —
   `AdaptiveSlot`, its bands, hysteresis, dwell and tap lockout, and the 5 s poll.
3. **Each seat button is a live indicator and opens a seat menu** anchored to
   it. The menu holds the controls the adaptive cells used to surface for that
   seat.

Unchanged: the nav column, both temperature zones, the volume column, screen 1d,
window parameters for the bar, the bus layer, and every command.

## 2. Screen 2a geometry

Centre column stays 768 dp. Top row stays 96 dp tall.

| Cell | Width | Notes |
|---|---|---|
| Driver seat button | **128** | leftmost |
| AUTO | **256** | amber fill when engaged, as today |
| CLIMATE ▲/▼ | **256** | opens/closes 1d, as today |
| Passenger seat button | **128** | rightmost |

128 + 256 + 256 + 128 = 768. Every cell clears the 96 dp floor. The row is now
mirror-symmetric about the centre divider, matching the zones beneath it.

Retire `Dimens.slotAdaptiveFirst` and `slotAdaptiveSecond`; set `slotAuto` and
`slotClimate` to 256; add `seatButton = 128.dp`. `BarGeometryTest` keeps
asserting the row sums to `barCenterColumn` and every cell clears `minTarget`.

## 3. Seat button — the indicator

A 128 x 96 cell. It shows state only; it never dispatches a vehicle command
itself. A tap toggles that seat's menu (§4).

| Reported state | Glyph | Below the glyph |
|---|---|---|
| Heat LOW / HIGH | seat-heat variant, `palette.warm` | two pips, 1 or 2 lit warm |
| Cool LOW / HIGH | seat-cool variant, `palette.cool` | two pips, 1 or 2 lit cool |
| Off (both 0) | **plain seat**, `palette.ink` | `OFF`, mono, muted |
| Unknown (not live) | plain seat, `palette.inkFaint` | `—`, never `OFF` |

- **Glyph orientation** follows the existing set: driver uses the `LEFT`
  variants, passenger the `RIGHT` variants.
- **Plain seat glyph** does not exist yet. Take it from the existing path data:
  `SEAT_COOL_LEFT` begins with the seat outline subpath (`M17.025 21H6L3.225
  10.2 … T17.025 20z`) and `SEAT_COOL_RIGHT` likewise (`M6.975 21v-1 … L18
  21z`). That subpath alone is the plain seat, with a silhouette identical to
  the heat and cool variants.
- **Pips** reuse the 1d comfort tile's two-pip indicator (`SeatPips`), sized for
  the smaller cell: approx 14 x 6 dp each, 4 dp gap, centred under the glyph.
- **Glyph** approx 36 dp, centred horizontally, upper part of the cell.
- **Heated wheel badge (driver button only):** when `wheelHeatOn`, draw
  `WheelHeatGlyph` at approx 32 dp in the cell's top-right corner, warm. It is
  independent of seat state — an OFF seat with the wheel on shows the plain seat,
  `OFF`, and the badge.
- **Heat and cool both reported non-zero** should not happen (the protocol makes
  them mutually exclusive) but can arrive transiently. Render heat. Make this a
  pure, unit-tested derivation (e.g. `SeatIndicator.of(heat, vent)`) rather than
  inline branching.
- **Menu open:** the owning button's background becomes `palette.surfaceRaised`
  for as long as its menu is up.
- **Day mode:** luminance only, geometry identical, as for the rest of the bar.

## 4. Seat menu

### Contents

| Row | Driver | Passenger | Tap sends | State text |
|---|---|---|---|---|
| Seat heat | yes | yes | `SEAT_HEAT_L` / `_R` | `OFF` / `LOW` / `HIGH` |
| Seat cool | yes | yes | `SEAT_VENT_L` / `_R` | `OFF` / `LOW` / `HIGH` |
| Steering wheel | yes | **no** | `WHEEL_HEAT` | `OFF` / `ON` |

**Each tap cycles, exactly as today** (owner decision, 2026-09-14). No direct
level selection and no multi-command sequencing: heat goes `OFF → HIGH → LOW →
OFF` per tap, cool likewise, and the vehicle's own mutual exclusion clears the
other. As everywhere else, the menu renders reported state only and never
mutates on tap.

Row visual rules follow the 1d comfort tiles: icon tinted with the lit colour
when on, `palette.ink` when off, `palette.inkFaint` when unknown; state text lit
colour when on, muted when off, `—` when unknown.

### Geometry (approximate)

- Width **360 dp**. Rows **96 dp** each: driver menu 288 dp tall, passenger 192.
- **Bottom edge flush with the top of the bar.** It never overlaps the bar.
- **Driver menu** left-aligned to the driver button: left edge at x = 156.
- **Passenger menu** right-aligned to the passenger button: right edge at
  x = 924, so left edge at x = 564. (Mirror of the driver menu; the design shows
  only the driver menu.)
- Surface `palette.surfaceRaised`, corner radius approx 16 dp, 1 dp row dividers.
- Row layout: icon approx 28 dp at approx 27 dp from the left; label (Manrope,
  approx 18 sp bold) at approx 74 dp; state text (IBM Plex Mono, letter-spaced)
  right-aligned approx 24 dp from the right edge.

### Behaviour

- **Opens** on a tap of its seat button. Only one menu at a time: tapping the
  other seat button switches to that menu.
- **Stays open across row taps.** Cycling to LOW needs two taps, so a row tap
  must never dismiss.
- **Closes** on: a second tap of its own button; any touch outside the menu;
  BACK; HOME; opening 1d via CLIMATE; the bus going away; service teardown.
- **An outside touch still does what it landed on.** The menu window is not
  touch-modal, so tapping AUTO with a menu open closes the menu *and* toggles
  AUTO, and a touch on app content reaches the app. A touch on either seat button
  is resolved by the re-tap rule in section 5: its own button closes, the other
  switches.
- **No auto-dismiss timeout** (assumed — confirm with the owner).
- **No animation** on open or close (assumed — the design shows none). Values
  are never animated in any case.
- Opening a menu while 1d is open is allowed; the menu draws above it. Tapping
  CLIMATE to close 1d also closes the menu.

## 5. Architecture

### A third overlay window

The menu draws above the bar, over app content, so it cannot live inside the
227 dp bar window. It gets its own window, created and destroyed on demand
exactly like the 1d panel:

- One `ComposeOverlayHost` per open menu, held in a nullable field; null means
  no menu window exists.
- `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL |
  FLAG_WATCH_OUTSIDE_TOUCH`, plus the bar's `FLAG_LAYOUT_IN_SCREEN |
  FLAG_LAYOUT_NO_LIMITS`.
- Position in **display** coordinates on the same basis as the bar, so the menu
  is flush with the bar on any hardware: `y = displayHeightPx() -
  designBarHeightPx() - menuHeightPx`, `gravity = TOP | START`, `x` per §4.
- `ComposeOverlayHost` gains an optional outside-touch callback, wired as a
  `View.OnTouchListener` on the `ComposeView` that reacts to
  `MotionEvent.ACTION_OUTSIDE`.
- Every rule the panel follows for a dead bus applies, and more simply: no bus
  means no menu. `hidePanelAndBar()` and `teardown()` remove it; `openMenu` is a
  no-op without a connected bus.
- BACK: `onKeyEvent` closes an open menu before it would close the panel.

### The re-tap race

Tapping the owning seat button while its menu is open is **also** a touch
outside the menu window. `ACTION_OUTSIDE` reaches the menu (closing it) before
the click reaches the bar, whose handler would then reopen it. A re-tap must
close, never flicker open again.

Make the open/close decision a small pure state machine with an injected clock,
alongside `ConnectionGate` and `RefreshRetry`, so the race is unit-tested rather
than found in the car. The simplest rule that works: an outside touch closes the
menu and records which side closed and when; a seat-button click for that same
side within a short window (approx 300 ms) is treated as the close it already
was, not as an open. Checking `ACTION_OUTSIDE` coordinates against the button
rect is an alternative, but coordinates for outside events are only reliable for
same-UID windows, so do not rely on them alone.

### State and wiring

- The service holds `seatMenu: MutableState<SeatSide?>` (compose-observable, like
  `panelOpen`) so the bar can paint the owning button as open.
- `ClimateBar` loses `band` and `onSlotPressChange` and gains the open side plus
  `onSeatButton(SeatSide)`.
- `BarTopRow` is rewritten around the four cells in §2. `AdaptiveSlotCell` and
  `SeatSlot` go.
- New UI: `SeatButton` (bar cell) and `SeatMenu` (menu content), in `ui/bar/`.

## 6. Removals

| Where | What |
|---|---|
| `:bus` | `AdaptiveSlot.kt` (`AdaptiveSlot`, `SlotBand`, `SlotCell`, `SlotContent`) and `AdaptiveSlotTest.kt` |
| `:app` | the `slot` field, the poll loop in `BarContent`, `onSlotPressChange` wiring, `SLOT_POLL_MS` |
| `:ui` | `AdaptiveSlotCell`, `SeatSlot`, the `band` / `onSlot` / `onSlotPressChange` parameters |
| `:design` | `slotAdaptiveFirst`, `slotAdaptiveSecond` |
| `:harness` | its `AdaptiveSlot`, the band/cells readouts, and any keys that only fed the slot |

**Keep `outsideF()`.** Screen 1d's header still shows outside temperature; only
the slot's use of it goes. Update its KDoc, which currently justifies itself by
`AdaptiveSlot`.

## 7. Testing

- `BarGeometryTest`: new top-row list; both menu heights are whole multiples of
  a >= 96 dp row; driver menu spans x 156..516 and passenger 564..924, both
  inside the centre column.
- `SeatIndicator` derivation: every heat/cool level combination, unknown, and
  heat-and-cool-both-set.
- Menu state machine: open, switch side, row tap keeps open, outside close,
  re-tap within the window closes and stays closed, re-tap after the window
  reopens, dead bus closes.
- Harness: render both seat buttons in every indicator state, and the menu
  inline, in night and day.
- Full suite green before and after. Baseline on 2026-09-14: 153 tests, 0
  failures, genuine uncached run.

## 8. Vehicle checklist

1. Both buttons indicate correctly: off, heat LOW/HIGH, cool LOW/HIGH, unknown
   at cold start.
2. Wheel badge follows the wheel from both the menu and screen 1d.
3. Menus sit flush on the bar, at the anchors in §4.
4. Heat row cycles OFF → HIGH → LOW → OFF; switching heat to cool clears heat.
5. Menu stays open across row taps.
6. Re-tap own button closes it and it stays closed.
7. Outside tap, BACK, HOME and CLIMATE each close it.
8. With the bus forced down the same way the panel's dead-bus behaviour was
   verified, the menu and bar both go and the factory bar is usable. Do **not**
   disable `com.syu.ms` to test this: it is the vendor service the factory bar
   depends on, and this vehicle has no physical HVAC controls.

## 9. Docs to update alongside the code

- `2026-09-08-wk2-climate-design.md` §6 "The adaptive slot" and §8 "Tap adaptive
  slot": mark superseded and link here.
- `README.md` "What is built" row for screen 2a.
- Wiki page 12 (System Navigation Design): the 2a top-row description and the
  adaptive-slot table.
- New vehicle screenshots once built. The existing `bar-2a-*defrost*` and
  `*seatcool*` captures show the retired slot.

## 10. Open questions for the owner

1. Passenger menu has two rows (no steering wheel) — assumed.
2. No auto-dismiss timeout — assumed.
3. No open/close animation — assumed.
4. Can the updated `System Navigation.dc.html` be exported into
   `design_handoff_system_navigation/` so the build works from the file, not a
   screenshot?
