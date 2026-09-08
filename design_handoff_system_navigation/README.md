# Handoff: System Navigation — bottom bar + full-page climate

## Overview

Replacement for the OEM `com.syu.air` bottom navigation/climate bar on a FYT/SYU UIS7870 head unit (Jeep Grand Cherokee WK2), plus the full-page climate view that opens from it.

Two designs are approved for build:

| ID | What it is | Size |
|----|------------|------|
| **2a** | The resting bottom bar, with one temperature-adaptive slot | 1080 × 227 |
| **1d** | The expanded climate page | 1080 × 1693 |

Both are drawn at native device pixels. The panel is 1080 × 1920 at 160 dpi, so **1 px = 1 dp** — every measurement in this document can be used directly as a dp value.

The OEM bar being replaced packs roughly 24 same-weight targets into three rows; nothing in it is taller than ~70 px. The redesign cuts the resting bar to six functions at a 96 px minimum target and moves everything else one tap away onto 1d.

## About the design files

The files in this bundle are **design references created in HTML** — prototypes that show intended look, sizing, and behavior. They are not production code to copy.

The task is to **recreate these designs in the target environment**: an Android system UI replacement (navigation bar overlay + activity) talking to the vehicle over the reverse-engineered command interface in `chrisuthe/7870-Projects`. Use that project's established patterns for command dispatch and state subscription. Nothing in the HTML should be ported literally — read it for geometry, color, type, and state logic.

`System Navigation.dc.html` needs `support.js` beside it to render, and the `icons/` folder for the airflow/defrost/recirc bitmaps. Open it in a browser and use the `2a` and `1d` badges to find the two approved designs. The file also contains earlier explorations (`1a`, `1b`, `1c`, `1e`) — **do not build those**; they are kept for context on why 2a and 1d look the way they do.

## Fidelity

**High fidelity.** Colors, type, and geometry are final and should be reproduced exactly. Two deliberate exceptions:

1. **Airflow, defrost and recirculate icons are final assets** — bitmaps in `icons/`, extracted from a conventional automotive HVAC icon set the user supplied. Use them (or vector equivalents of the same glyphs).
2. **HOME, BACK, volume, seat and wheel glyphs are placeholders** — drawn as primitive shapes with text labels doing the work. Either keep the text labels or substitute a real icon set; do not ship the placeholder rectangles and circles.

Everything else — spacing, sizes, weights, colors, radii — is intentional.

## Design rules that must survive implementation

These are the point of the redesign. If an implementation detail forces a tradeoff, protect these:

1. **96 px minimum on every tappable region in the bar.** Not 88, not 72. This is what fixes the OEM bar's core failure.
2. **Direct selection, never cycling.** The four airflow modes are idempotent setters, so four separately-lit buttons replace the OEM's four-step cycle. Never make the user tap N times to land on a mode.
3. **Fixed geometry.** Controls live at constant coordinates so they can be found by feel. In 2a exactly one 200 × 96 region ever changes its contents; everything else is nailed down.
4. **State visible without reading.** Active = filled and bright. Inactive = outline. No relying on a number or a label to convey on/off.
5. **The bar rests at 227 px and never grows.** 227 px is the framework `navigation_bar_height`; growing it requires root. 1d opens *over* app content instead.
6. **`com.syu.air` keeps running underneath.** This UI is an alternative front end, not a replacement service — a crash in it must never leave the vehicle with no climate control.
7. **Climate power is destructive.** There are no physical HVAC controls in this vehicle. Power-off is press-and-hold only, never a single tap.

---

## Screen: 2a — resting bottom bar

**Purpose:** permanent, glanceable access to the six things the user actually touches while driving: driver temp, passenger temp, AUTO, heated wheel, volume, home/back — plus one slot that adapts to outside temperature.

### Layout

Root: `1080 × 227`, `display: grid`, `grid-template-columns: 156px 768px 156px`. Background `#0b0c0d` (night) / `#eceae6` (day). Font stack: Manrope for UI, IBM Plex Mono for micro-labels and numerics.

Column dividers: `1px solid rgba(255,255,255,.09)` (night) / `rgba(0,0,0,.1)` (day).

#### Left column — 156 px, two rows of 113.5 px

| Element | Spec |
|---|---|
| HOME | 28 × 28 square, `2.5px` border `#f4f4f3`, radius 5. Label: IBM Plex Mono 600 11px, letter-spacing `.1em`, `rgba(244,244,243,.7)`. Gap 7px, column layout, centered. Bottom border `1px` divider. |
| BACK | `‹` in Manrope 300 32px, `margin-top: -4px`. Same label treatment. Gap 3px. |

Both targets are 156 × 113.5 — comfortably over the floor.

#### Center column — 768 px, `grid-template-rows: 96px 1fr`

**Top row (96 px), `grid-template-columns: 210px 200px 174px 184px`:**

| Slot | Width | Content |
|---|---|---|
| Heated wheel | 210 | 19 × 19 circle, `2.5px` border `oklch(0.68 0.16 35)`, radius 50%. Label "WHEEL" Manrope 700 16px, ls `.04em`, `rgba(244,244,243,.85)`. Gap 9px, centered, right divider. |
| **Adaptive slot** | 200 | See state table below. Right divider. |
| AUTO | 174 | Fill `oklch(0.78 0.15 75)`, ink `#141414`, Manrope 800 19px, ls `.06em`, centered. |
| CLIMATE | 184 | Label Manrope 700 16px ls `.04em` + `▲` Manrope 400 13px `rgba(244,244,243,.55)`. Gap 10px. Left divider. Opens 1d. |

**Bottom row (131 px), `grid-template-columns: 1fr 1px 1fr`** — two temperature zones split by a 1px divider `rgba(255,255,255,.09)`.

Each zone: `grid-template-columns: 100px 1fr 100px`.

| Element | Spec |
|---|---|
| `−` | Manrope 300 42px, `oklch(0.72 0.12 235)`. Target 100 × 131. |
| Value | Manrope 700 48px, ls `-0.03em`. Degree symbol 24px, `vertical-align: top`, `line-height: 1.1`. |
| Label | "DRIVER" / "PASSENGER", IBM Plex Mono 600 10px, ls `.14em`, `rgba(244,244,243,.45)`. Gap 2px below value. |
| `+` | Manrope 300 42px, `oklch(0.68 0.16 35)`. Target 100 × 131. |

The center of each zone (the numeral) is not a button in 2a — it is a readout. Only `−` and `+` are tappable.

#### Right column — 156 px, `grid-template-rows: 96px 35px 96px`

| Element | Spec |
|---|---|
| Volume up | `▲` Manrope 400 24px, centered. 156 × 96. |
| Volume readout | Background `rgba(255,255,255,.05)`. "VOL" IBM Plex Mono 600 10px ls `.12em` `rgba(244,244,243,.5)` + value Manrope 700 17px. Gap 6px. **Not tappable** — 35 px is a readout strip, deliberately below target size. |
| Volume down | `▼` Manrope 400 24px. 156 × 96. |

Left border `1px` divider.

### The adaptive slot — the only thing that changes

One 200 × 96 region. Outside temperature selects its contents; **nothing else in the bar moves, resizes, or changes position** at any temperature.

| Condition | Slot holds | Rendering |
|---|---|---|
| Below freezing (< 32 °F) | **FRONT DEFROST** | Amber fill `oklch(0.78 0.15 75)`, ink `#141414`, `box-sizing: border-box`, right border `4px solid #0b0c0d` (separates it from the adjacent amber AUTO). Icon `icons/light/defrost.png` at 34px height + label Manrope 800 15px ls `.04em`. Gap 14px. |
| Cool (32–~80 °F) | **SEAT HEAT** | Column layout, gap 5px. Label Manrope 800 16px ls `.04em` `rgba(244,244,243,.85)`; state IBM Plex Mono 800 11px ls `.14em` `rgba(244,244,243,.4)` reading `OFF` / `LOW` / `HIGH`. |
| Hot (> ~85 °F) | **SEAT COOL** | Same layout; label color `oklch(0.82 0.09 235)`. |

Rules for the slot:

- It always holds a control that **also exists on 1d**. It is a shortcut, never the only route to a function. Do not let it be the sole access point for anything.
- Defrost outranks comfort. Below freezing, clearing glass wins.
- Hysteresis is required on the thresholds — a vehicle sitting at 32 °F must not flip the slot back and forth. Suggest ±3 °F deadband and a minimum dwell time (30 s) before re-evaluating.
- Never change the slot while the user's finger is down, and never within ~1 s of a tap on it.
- Outside temperature is read from the same bus subscription that carries the climate state; it is **not displayed** in the bar (it already appears elsewhere on screen). The bar consumes the value, it doesn't print it.

An earlier iteration put the heated wheel in this slot as a "default" state — that was rejected. The slot always holds a seat control (or defrost); the heated wheel is now a permanent, fixed control in the 210 px position.

### Day theme

Same geometry, different palette. Day is a luminance change only — no layout change, so muscle memory holds.

| Token | Night | Day |
|---|---|---|
| Surface | `#0b0c0d` | `#eceae6` |
| Ink | `#f4f4f3` | `#16181a` |
| Divider | `rgba(255,255,255,.09)` | `rgba(0,0,0,.1)` |
| Accent (AUTO, active) | `oklch(0.78 0.15 75)` on `#141414` ink | `oklch(0.62 0.13 75)` on `#fff` ink |
| Cool / `−` | `oklch(0.72 0.12 235)` | `oklch(0.52 0.14 235)` |
| Warm / `+` | `oklch(0.68 0.16 35)` | `oklch(0.55 0.16 35)` |
| Micro-label | `rgba(244,244,243,.45)` | `rgba(0,0,0,.5)` |
| Inset strip | `rgba(255,255,255,.05)` | `rgba(0,0,0,.05)` |

Switching should follow the vehicle's day/night signal, not a clock.

---

## Screen: 1d — expanded climate page

**Purpose:** every climate function in the vehicle, each reachable in one tap, nothing nested. This is what the 1693 px is for.

### Layout

Root: `1080 × 1693`, `display: flex; flex-direction: column`, background `#0b0c0d`, ink `#f4f4f3`. Opens over app content; the 227 px bar stays visible below it. Sections are separated by `1px solid rgba(255,255,255,.09)` top borders. Every section header is IBM Plex Mono 600 12px, ls `.16em`, `rgba(244,244,243,.45)`.

Total of child heights measures 1664 px, leaving the footer pinned by `margin-top: auto`.

#### Header — 112 px

Padding `0 34px`, space-between.

- Title "Climate" Manrope 800 30px, ls `-0.01em`.
- Status "CABIN 64°F · OUT 41°F" IBM Plex Mono 500 15px, `rgba(244,244,243,.4)`. Gap 14px, baseline-aligned.
- CLOSE button 130 × 96, radius 26, border `1.5px solid rgba(255,255,255,.24)`, `▼` 15px `rgba(.6)` + label Manrope 700 16px, gap 9px.

#### Temperature zones — `grid-template-columns: 1fr 1px 1fr`, divider `rgba(255,255,255,.09)`

Each zone: padding `30px 34px 34px`.

| Element | Spec |
|---|---|
| Zone label | "DRIVER" / "PASSENGER", section-header style. |
| `−` | 110 × 110, radius 20, bg `oklch(0.72 0.12 235 / .18)`, border `1.5px solid oklch(0.72 0.12 235 / .5)`, glyph Manrope 300 52px `oklch(0.78 0.11 235)`. |
| Value | Manrope 800 96px, ls `-0.05em`; degree 38px `vertical-align: top`, `line-height: 1.4`. |
| `+` | 110 × 110, radius 20, bg `oklch(0.68 0.16 35 / .18)`, border `1.5px solid oklch(0.68 0.16 35 / .5)`, glyph Manrope 300 52px `oklch(0.74 0.15 35)`. |
| Range track | Height 8, radius 4, `linear-gradient(90deg, oklch(0.6 0.13 235), oklch(0.72 0.14 35))`, margin-top 24. Knob 18 × 18 circle `#f4f4f3` with `3px solid #0b0c0d` ring, positioned by percentage through the range. |

Row is space-between; `margin-top: 18px` under the label.

The track is a **readout** in 1d, not a drag target (an earlier option explored drag-to-set; it is not part of the approved design). If you want drag later, it must not shrink the `−`/`+` targets.

#### Fan — padding `20px 34px 24px`

- Header row: "FAN" + value "7 / 15" (Manrope 700 20px; the "/ 15" is 500 14px `rgba(.4)`).
- Row, gap 14px, `margin-top: 16px`:
  - `−` 104 × 96, radius 18, border `1.5px solid rgba(255,255,255,.24)`, glyph Manrope 300 46px.
  - Meter: flex 1, height 96, radius 18, bg `rgba(255,255,255,.05)`, padding `14px 16px`, 12 bars `gap: 6px`, each `flex: 1`, radius 3, bottom-aligned. Bar heights ramp `26% 31% 37% 43% 49% 55% 61% 67% 73% 79% 85% 100%`. Filled bars `oklch(0.78 0.15 75)`, unfilled `rgba(255,255,255,.13)`.
  - `+` 104 × 96, radius 18, glyph Manrope 300 42px.
- The meter is a readout; `−`/`+` drive the value. Fan has 15 steps shown across 12 bars — round to the nearest bar.

#### Airflow — padding `20px 34px 24px`

Header: "AIRFLOW — four modes, direct".

`grid-template-columns: repeat(4, 1fr)`, gap 14px, `margin-top: 16px`. Each tile height **136**, radius 18, centered, **no text label** — icon only.

| Mode | Icon (inactive) | Icon height |
|---|---|---|
| Face | `icons/dark/face.png` | 76 |
| Face + feet | `icons/dark/face-feet.png` | 76 |
| Feet | `icons/dark/feet.png` | 76 |
| Feet + glass | `icons/dark/feet-glass.png` | 86 |

- **Active tile:** fill `oklch(0.78 0.15 75)` and swap to the `icons/light/*` variant (dark ink on amber).
- **Inactive tile:** border `1.5px solid rgba(255,255,255,.2)`, `icons/dark/*`.
- These are idempotent setters — tapping an already-active mode is a no-op, not a cycle.
- Exactly one is active at a time.

#### Mode — padding `20px 34px 24px`

Two grids, gap 14px, tiles height **104**, radius 18.

Row 1, `repeat(4, 1fr)`:

| Tile | Active rendering |
|---|---|
| AUTO | Fill `oklch(0.78 0.15 75)`, ink `#141414`, Manrope 800 19px ls `.05em`. |
| A/C | Fill `oklch(0.72 0.12 235)`, ink `#0b0c0d`, Manrope 800 19px ls `.05em`. |
| RECIRC | `icons/dark/recirc.png` at 26px + label Manrope 800 17px ls `.05em`; icon `margin-right: 10px`. |
| MAX A/C | Manrope 800 17px ls `.05em`. |

Row 2, `repeat(3, 1fr)`, `margin-top: 14px`:

| Tile | Content |
|---|---|
| FRONT DEF | `icons/dark/defrost.png` at 44px + label Manrope 800 15px ls `.04em`, gap 10px. |
| REAR DEF | Rear-window glyph — currently an inline SVG (rounded rect + three wave strokes) drawn at 50 × 44 with `stroke-width: 3.5` to match the bitmap's weight. The supplied icon set has no rear-defrost glyph; if you have one, use it. |
| SYNC | Label Manrope 800 17px ls `.06em` + "DUAL" IBM Plex Mono 500 12px `rgba(.4)`, gap 10px. |

Inactive style throughout: border `1.5px solid rgba(255,255,255,.2)`, label `rgba(244,244,243,.8)` / `.85`.

SYNC lives here rather than as a small pill on the passenger zone, so no target on the page falls below 96 px.

#### Comfort — padding `20px 34px 24px`

`grid-template-columns: 1fr 1fr`, gap 14px, four tiles height **96**, radius 18, bg `rgba(255,255,255,.05)`, padding `16px 18px`, column layout, space-between:

- SEAT HEAT · L, SEAT HEAT · R, SEAT COOL · L, SEAT COOL · R.
- Title Manrope 700 15px ls `.04em`.
- State row: two pips (`flex: 1`, height 14, radius 7) + state word IBM Plex Mono 800 13px ls `.12em`, gap 14px between the pip group and the word.
- **Three states only: OFF / LOW / HIGH.** Two pips: none lit = OFF, one lit = LOW, both lit = HIGH. Lit color `oklch(0.68 0.16 35)` for heat, `oklch(0.72 0.12 235)` for cool; unlit `rgba(255,255,255,.13)`. State word matches the lit color when on, `rgba(244,244,243,.4)` when OFF.
- Active tile also takes border `1.5px solid oklch(0.68 0.16 35 / .5)` (heat) or the cool equivalent; inactive `1.5px solid rgba(255,255,255,.14)`.

Below, full width: **HEATED STEERING WHEEL**, height 96, radius 18, border `1.5px solid oklch(0.68 0.16 35 / .5)`, bg `oklch(0.68 0.16 35 / .12)`, 26px circle glyph `3px solid oklch(0.74 0.15 35)` + label Manrope 800 18px ls `.05em`, gap 14px, `margin-top: 14px`.

#### Footer — padding `22px 34px`, pinned with `margin-top: auto`

- Explanatory copy Manrope 400 13px/1.45 `rgba(244,244,243,.4)`, `max-width: 640px`.
- **HOLD · OFF** button 180 × 96, radius 26, border `1.5px solid rgba(255,255,255,.18)`, 16px dot `rgba(244,244,243,.35)` + label Manrope 700 15px ls `.06em` `rgba(244,244,243,.6)`.

---

## Interactions & behavior

| Interaction | Behavior |
|---|---|
| Tap CLIMATE (2a) | 1d slides up over app content, 227 px bar remains. Suggest 220 ms ease-out translate-Y; no fade on the controls themselves. |
| Tap CLOSE / `▼` (1d) | Reverse. Also close on back gesture. |
| Tap `−` / `+` | One step per tap. Press-and-hold repeats at ~150 ms intervals after a 400 ms delay. |
| Tap airflow mode | Sets that mode directly. Idempotent — re-tapping the active mode does nothing. |
| Tap AUTO / A/C / RECIRC / MAX A/C / defrost / SYNC | Toggle. |
| Tap seat heat / seat cool | Cycles OFF → LOW → HIGH → OFF. |
| Tap adaptive slot | Acts on whatever it currently holds. |
| Press-and-hold HOLD · OFF | ~800 ms to power climate off, with visible fill progress. Single tap does nothing. |
| Any state change | Reflect from the vehicle's reported state, not optimistically from the tap — see below. |

**Feedback:** every target needs a pressed state (suggest `brightness(1.25)` on filled tiles, background `rgba(255,255,255,.08)` on outlined ones) and it must appear on touch-down, not on release. There is no hover on this device; do not implement hover styling.

**Do not animate value changes.** Temperature and fan numerals should snap. A moving numeral is unreadable at a glance.

## State management

State is owned by the vehicle, not the UI. The pattern that matters:

1. Subscribe to the climate state feed on mount; render entirely from it.
2. A tap dispatches a command and does **not** mutate local state.
3. The UI updates when the vehicle reports the new value.
4. If a command produces no state change within ~600 ms, show the control's pressed state resolving anyway (so the UI never feels dead) but keep the displayed value truthful.

State needed for these two screens:

- `tempDriver`, `tempPassenger` (°F, plus LO/HI sentinels)
- `fanLevel` (0–15)
- `airflowMode` (one of four)
- `acOn`, `autoOn`, `recircOn`, `maxAcOn`
- `defrostFront`, `defrostRear`
- `syncOn` (dual-zone)
- `seatHeatL`, `seatHeatR`, `seatCoolL`, `seatCoolR` (OFF/LOW/HIGH)
- `wheelHeatOn`
- `climatePowerOn`
- `cabinTemp`, `outsideTemp` (read-only; outside temp drives the adaptive slot)
- `volume` (0–n)
- `theme` (day/night, from the vehicle's illumination signal)
- `climatePageOpen` (UI-local — the only genuinely local state here)

**Sentinel values:** the reverse-engineered interface uses out-of-range sentinels (e.g. `−1`, `−2`) for "unavailable" and "macro active" conditions. Render those as a dimmed, non-committal state — never as a number, and never as OFF. Confirm the exact sentinel semantics against the project wiki before wiring.

**Command mapping** is deliberately not reproduced here — take it from the repo's command table rather than this document, since it is vehicle-specific and was reverse-engineered. What this design assumes about it:

- Temperature is a stepped command per zone, not an absolute setter.
- Fan is a stepped command.
- The four airflow modes are **separate idempotent setters** (this is what makes rule 2 possible — verify before building the four-button UI).
- Seat heat/cool are three-state cycles per side.
- Climate power is a single destructive command.
- Not every WK2 is fitted with every function. Controls for absent hardware should be omitted, not disabled — a permanently greyed tile wastes a 96 px slot.

## Design tokens

**Color — night**

```
surface            #0b0c0d
surface-raised     rgba(255,255,255,.05)
surface-inset      rgba(255,255,255,.035)
ink                #f4f4f3
ink-dim            rgba(244,244,243,.8)
ink-muted          rgba(244,244,243,.45)
ink-faint          rgba(244,244,243,.4)
divider            rgba(255,255,255,.09)
border-control     rgba(255,255,255,.2)   /* .24 on larger buttons */
accent             oklch(0.78 0.15 75)    /* amber — active/AUTO */
accent-ink         #141414
cool               oklch(0.72 0.12 235)   /* A/C, cooling, − */
cool-bright        oklch(0.78 0.11 235)
cool-label         oklch(0.82 0.09 235)
warm               oklch(0.68 0.16 35)    /* heat, + */
warm-bright        oklch(0.74 0.15 35)
tint-fill          <color> / .12–.18
tint-border        <color> / .5
```

**Color — day**

```
surface            #eceae6
surface-card       #ffffff
ink                #16181a
divider            rgba(0,0,0,.1)
accent             oklch(0.62 0.13 75)  on #fff ink
cool               oklch(0.52 0.14 235)
warm               oklch(0.55 0.16 35)
ink-muted          rgba(0,0,0,.5)
```

**Typography**

- UI: **Manrope** — 700/800 for values and labels, 300 for `+`/`−` glyphs.
- Micro-labels, states, numerics: **IBM Plex Mono** — 500/600/800, letter-spacing `.1em`–`.16em`, uppercase.
- Scale in use: 96 / 52 / 48 / 46 / 42 / 30 / 26 / 24 / 20 / 19 / 18 / 17 / 16 / 15 / 13 / 12 / 11 / 10 px.
- Negative tracking on large numerals only: `-0.05em` at 96px, `-0.03em` at 48px, `-0.02em` at 30px.

**Spacing** — 2 / 3 / 5 / 6 / 7 / 9 / 10 / 14 / 16 / 18 / 20 / 22 / 24 / 26 / 30 / 34 px. Section padding is `20px 34px 24px`; page gutter is 34 px.

**Radius** — 3 (pips/bars) · 5 · 14 · 18 (control tiles) · 20 (steppers) · 26 (pill buttons) · 50% (circles).

**Target sizes** — 96 px floor. In practice: 96 (comfort, mode row 2, pills), 104 (mode row 1, fan steppers), 110 (zone steppers), 113.5 (bar nav), 131 (bar temp steppers), 136 (airflow).

No shadows anywhere. Elevation is carried by surface tint and border only — shadows read as smudges on a glossy panel in sunlight.

## Assets

`icons/` — 12 PNGs with transparency, in `dark/` (light glyph, `#f4f4f3`) and `light/` (dark glyph, `#16181a`) variants:

```
face.png    face-feet.png    feet.png    feet-glass.png
defrost.png recirc.png
```

These were extracted and separated from a conventional automotive HVAC icon strip supplied by the user, then recolored per theme. Native sizes are ~93–149 px wide; they are rendered at fixed heights (see the 1d spec) with `width: auto`.

Prefer converting these to vectors for production so they scale and can be tinted at runtime rather than shipped twice.

Placeholder glyphs still to be replaced with real icons: HOME, BACK, volume up/down, seat heat/cool, heated wheel.

## Files

| File | What it is |
|---|---|
| `System Navigation.dc.html` | The design. Contains 2a and 1d (approved) plus 1a/1b/1c/1e explorations and the design brief. Open in a browser. |
| `support.js` | Runtime the HTML needs to render. Not part of the design. |
| `icons/` | Final icon assets, both themes. |
| `oem-bar.png` | Photo of the OEM bar being replaced (referenced by the brief section of the HTML). |
| `README.md` | This document. |

Approved: **2a** (bar) and **1d** (climate page). Everything else in the HTML is context.

## Open questions the build will need answered

1. Should the adaptive slot react to vehicle conditions rather than just ambient temperature — e.g. promote defrost when the windshield is actually fogging, instead of at a fixed 32 °F threshold?
2. Exact threshold values and hysteresis for the slot (this document proposes < 32 °F defrost, > 85 °F seat cool, ±3 °F deadband, 30 s dwell).
3. Which functions are actually fitted on the target vehicle, so absent controls can be omitted.
4. Whether the 1d range track should become drag-to-set later, and if so how to preserve the `−`/`+` target sizes.
