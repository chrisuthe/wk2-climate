# wk2-climate — design

Replacement climate and navigation UI for the OEM `com.syu.air` bottom bar on a
FYT/SYU UIS7870 head unit in a Jeep Grand Cherokee WK2.

- **Repo:** `chrisuthe/wk2-climate`, Apache 2.0, linked from `chrisuthe/7870-Projects`.
- **Visual design:** `design_handoff_system_navigation/` — screens **2a** (bar, 1080x227)
  and **1d** (climate page, 1080x1693). That README is the authority on geometry,
  colour, type and spacing. **This spec records only deltas from it.**
- **Protocol research:** [7870-Projects wiki](https://github.com/chrisuthe/7870-Projects/wiki).
  Cited below as `wiki pN`.

---

## 1. Scope

**In scope.** An `AccessibilityService` that draws 2a as a permanent overlay over
the OEM bar's 227px region, opens 1d over app content on demand, and drives the
vehicle through the SYU vendor IPC bus. HOME and BACK via global accessibility
actions. Volume via module 4.

**Out of scope.** Root, `navigation_bar_height` changes, RROs (wiki p8 — root buys
only screen space and costs a firmware downgrade with no `.pac` recovery net).
Rear climate zone, massage and lumbar seats (not fitted — wiki p5). Recents.
Raw frame-stream decoding beyond the `U_TEMP_OUT` item in section 11.

**Explicitly deferred.** Drag-to-set on the 1d range track (handoff open question
4). Fog-driven promotion of the adaptive slot (handoff open question 1) — the
fixed temperature threshold ships first. Real icons for the placeholder glyphs
(section 8).

---

## 2. Load-bearing constraints

Verified on hardware; each is a hard boundary rather than a preference.

| Constraint | Source |
|---|---|
| The bar is `com.syu.air` owning a `TYPE_NAVIGATION_BAR` window titled `NavigationBar`, **not** SystemUI | wiki p3 |
| `TYPE_NAVIGATION_BAR` (2019) is **refused** to third-party apps; 2032 and 2038 work | wiki p7 |
| An overlay creates **no inset**. 227px is `android:dimen/navigation_bar_height`, a framework resource | wiki p7 |
| Reads (`U_AIR_*`, 10–96) and writes (command indices 1–25) are **non-overlapping code spaces**. Writing a read id succeeds and does nothing | wiki p6 |
| The IPC bus is **not permission-gated** — traced through `com.syu.ms`, no uid or permission check | wiki p4 |
| Codes are unique **only within a module**; the callback carries code but not module | wiki p10 |
| Registering a code delivers its **current value immediately** | wiki p4 |
| Updates are **push-on-change, not on a timer**. "Silent" means "unchanged", not "unfitted" | wiki p10 |
| `<queries><package android:name="com.syu.ms" /></queries>` is required or `bindService` fails **silently** on Android 11+ | wiki p4, probe manifest |
| Command index **16** powers climate off; **12** and **15** are macros that force recirc on and temps to the `-2` sentinel | wiki p6 |
| Command indices are **WK2-specific**. Read codes are universal | wiki p5, p6 |
| `com.syu.air` is one *client* of the bus, not the gateway — it can be covered without losing climate control | wiki p3 |

### Design rules that must survive implementation

From the handoff, restated because implementation tradeoffs must protect them:

1. **96dp minimum on every tappable region.** This is the OEM bar's core failure.
2. **Direct selection, never cycling** for the four airflow modes.
3. **Fixed geometry.** In 2a exactly one 200x96 region ever changes contents.
4. **State visible without reading.** Active = filled and bright; inactive = outline.
5. **The bar rests at 227dp and never grows.** 1d opens *over* app content.
6. **`com.syu.air` keeps running underneath.** A crash in our UI must never leave
   the vehicle with no climate control.
7. **Climate power is destructive** — press-and-hold only. There are no physical
   HVAC controls in this vehicle.

---

## 3. Architecture

### Component topology

```
ClimateBarService : AccessibilityService
├── VehicleBus lifecycle (bind com.syu.ms on connect, unbind on destroy)
├── BarWindow    2032 overlay, 1080x227  over [0,1693][1080,1920]  — always present
├── PanelWindow  2032 overlay, 1080x1693 at [0,0]                  — added on open
└── performGlobalAction(GLOBAL_ACTION_HOME | GLOBAL_ACTION_BACK)
```

One `AccessibilityService` rather than a foreground service plus 2038 overlay,
because:

- HOME/BACK have **no other route** for a third-party app, so an
  `AccessibilityService` is required regardless. This adds nothing else.
- No `SYSTEM_ALERT_WINDOW`.
- 2032 is a *trusted* overlay; the system does not hide it during permission
  dialogs as it can hide 2038.
- Precedent on this exact unit: `com.syu.fytgesture` already uses 2032 for the
  same purpose (wiki p3).

`climatePageOpen` — the only genuinely UI-local state — is "is PanelWindow attached".

**1d needs no offset trickery.** 1080x1693 is exactly the inset-reduced app area
`[0,0][1080,1693]`, so it fills that area natively with the 227px bar visible below.
Only 2a needs the negative-y positioning that section 11 item 1 must confirm.

Compose in a raw `WindowManager` window has no lifecycle owner, so a
`ComposeOverlayHost` (~40 lines, shared by both windows) creates the `ComposeView`
and attaches `ViewTreeLifecycleOwner`, `ViewTreeSavedStateRegistryOwner` and
`ViewTreeViewModelStoreOwner`.

### Module layout

| Module | Contents | JVM-testable |
|---|---|---|
| `:bus` | `VehicleBus`, `Signal`, `Command`, `ClimateState`, `SyuVehicleBus`, `FakeVehicleBus`, adaptive-slot state machine | **yes** |
| `:design` | colour and dimension tokens, type scale, icons as `VectorDrawable` | — |
| `:ui` | Compose composables for 2a and 1d | previewable |
| `:app` | `ClimateBarService`, `ComposeOverlayHost`, wiring | — |
| `:harness` | debug-only Activity hosting 2a + 1d against `FakeVehicleBus` | runs on a plain AVD |

`:ui` depends only on `:bus` and `:design` — **never** on `:app`. That is what makes
`:harness` possible: no composable may touch an `AccessibilityService`,
`WindowManager` token, or any other platform singleton.

**Stack:** Kotlin, Jetpack Compose, Gradle. The research probe's no-Gradle Java is
deliberate for dependency-free research tooling and is not inherited here.

**Development target:** AVD at **1080x1920, 160dpi** reproduces the panel exactly.
1px = 1dp, so every measurement in the handoff is used directly as a dp value.

---

## 4. The bus layer

### Typed code spaces

The wiki's most expensive mistake (wiki p6, p9) was conflating read ids with write
indices. Making that unrepresentable is this layer's primary job.

```kotlin
enum class Signal(val module: Int, val code: Int) {
    // module 7 — CANBUS
    TEMP_LEFT(7, 27), TEMP_RIGHT(7, 28), WIND_LEVEL(7, 21),
    BLOW_UP(7, 18), BLOW_BODY(7, 19), BLOW_FOOT(7, 20),
    AC(7, 11), AUTO(7, 13), CYCLE(7, 12), ACMAX(7, 53),
    FRONT_DEFROST(7, 65), REAR_DEFROST(7, 16), SYNC(7, 62),
    SEAT_HOT_L(7, 29), SEAT_HOT_R(7, 30),
    SEAT_BLOW_L(7, 31), SEAT_BLOW_R(7, 32),
    HOT_STEER(7, 66), POWER(7, 10), TEMP_UNIT(7, 37),
    // module 4 — SOUND
    VOLUME(4, 2),
    // module 0 — MAIN
    TEMP_OUT(0, 40), ILLUMINATION(0, 4),
}

enum class Command(val module: Int, val code: Int, val payload: IntArray) {
    AC(7, 6, intArrayOf(1, 1)),
    AUTO(7, 6, intArrayOf(1, 2)),               // macro: sets A/C, clears body-blow, fan -> 15
    RECIRC(7, 6, intArrayOf(1, 3)),
    TEMP_L_UP(7, 6, intArrayOf(1, 4)),   TEMP_L_DOWN(7, 6, intArrayOf(1, 5)),
    FAN_UP(7, 6, intArrayOf(1, 6)),      FAN_DOWN(7, 6, intArrayOf(1, 7)),
    AIRFLOW_FACE(7, 6, intArrayOf(1, 8)),
    AIRFLOW_FACE_FEET(7, 6, intArrayOf(1, 9)),
    AIRFLOW_FEET(7, 6, intArrayOf(1, 10)),
    AIRFLOW_FEET_GLASS(7, 6, intArrayOf(1, 11)),
    FRONT_DEFROST(7, 6, intArrayOf(1, 12)),     // macro: forces recirc on, fan -> 6
    SYNC(7, 6, intArrayOf(1, 13)),
    REAR_DEFROST(7, 6, intArrayOf(1, 14)),
    MAX_AC(7, 6, intArrayOf(1, 15)),            // macro: temps -> -2 sentinel
    CLIMATE_POWER(7, 6, intArrayOf(1, 16)),     // DESTRUCTIVE — hold-to-confirm only
    SEAT_HEAT_L(7, 6, intArrayOf(1, 17)), SEAT_HEAT_R(7, 6, intArrayOf(1, 18)),
    TEMP_R_UP(7, 6, intArrayOf(1, 20)), TEMP_R_DOWN(7, 6, intArrayOf(1, 21)),
    SEAT_VENT_L(7, 6, intArrayOf(1, 22)), SEAT_VENT_R(7, 6, intArrayOf(1, 23)),
    WHEEL_HEAT(7, 6, intArrayOf(1, 24)),
    // module 4 — SOUND
    VOL_UP(4, 0, intArrayOf(-1)),
    VOL_DOWN(4, 0, intArrayOf(-2)),
    VOL_HIDE_OSD(4, 0, intArrayOf(-7)),
}
```

`bus.subscribe(Signal)` and `bus.send(Command)` take different types, so the
silent-discard failure cannot be written. Two further benefits:

- It **unifies two unrelated calling conventions**: climate writes are
  `module 7, code 6, {1, N}`; volume writes are `module 4, code 0, {-1}`.
  Callers never see the difference.
- It defuses a real collision: **module 4 code 0 is `C_VOL` for writes and
  `U_SPECTRUM` for reads.** With one untyped `send(module, code, payload)` this
  would eventually become a bug.

Command index 19 is unmapped and 25 duplicates 10 (wiki p6); both are omitted.

### Three callbacks, one per module

Wiki p10 names this the single most likely thing to get wrong. `update()` carries
the code but **not** the module, and code 2 is `U_STANDBY` on MAIN and `U_VOL` on
SOUND. `SyuVehicleBus` therefore holds one `Callback` per module (0, 4, 7), each
closing over its own module id. Hand-written Binder proxies use the transaction ids
verified in wiki p4 and the probe: `IRemoteToolkit.getRemoteModule` = 1;
`IRemoteModule` `cmd` = 1, `get` = 2, `register` = 3, `unregister` = 4;
`IModuleCallback.update` = 1. Register flag = 1, the value `com.syu.air` passes.

**Never subscribed:** `U_SPECTRUM` (4/0, ~10 Hz) and `U_CANBUS_FRAME_TO_UI`
(7/1019, ~26 Hz). Excluding at subscription beats filtering afterwards (wiki p11).

### The seam

```kotlin
interface VehicleBus {
    val state: StateFlow<ClimateState>
    val connected: StateFlow<Boolean>
    fun send(cmd: Command)
}
```

`ClimateState` is an immutable data class holding the full snapshot. Both
`SyuVehicleBus` and `FakeVehicleBus` implement the interface; `:ui` cannot tell
which it has.

### Sentinels and derived state

| Raw | Rendered |
|---|---|
| `WIND_LEVEL == 15` | **AUTO** — not a fan speed (wiki p5) |
| `TEMP_LEFT/RIGHT == -2` | **LO** — the minimum sentinel, not a temperature (wiki p5) |
| any unavailable / macro-active sentinel | dimmed, non-committal. **Never a number, never OFF** |

Airflow arrives as three independent booleans, and only 4 of 8 combinations are
named (wiki p5):

```kotlin
val airflow: AirflowMode = when (Triple(blowUp, blowBody, blowFoot)) {
    Triple(0, 1, 0) -> FACE
    Triple(0, 1, 1) -> FACE_FEET
    Triple(0, 0, 1) -> FEET
    Triple(1, 0, 1) -> FEET_GLASS
    else            -> UNKNOWN      // no tile lit — do not guess
}
```

`UNKNOWN` is load-bearing, not defensive padding. Command 12 is a macro and may
leave the flags in an unnamed combination; lighting a *wrong* tile violates design
rule 4 more badly than lighting none.

### What "render from bus state" gives free

The UI **never** mutates local state on tap; it dispatches a command and waits for
the bus. This makes three documented traps disappear:

- **Seat heat/vent mutual exclusion** is enforced *in the protocol* — setting
  `SEAT_HOT_L` zeroes `SEAT_BLOW_L` (wiki p5). Render both; exclusion appears.
- **Macro side effects** (12 and 15 forcing recirc on and temps to `-2`) render
  truthfully with no special-casing.
- **`FAN_UP` exiting AUTO** as a side effect (wiki p6) unlights the AUTO tile on
  its own.

Consequence to surface in 1d's footer copy: tapping MAX A/C **will** discard the
user's temperature settings, because that is what the vehicle does.

If a command produces no state change within ~600 ms, resolve the control's
pressed state anyway so the UI never feels dead, but keep the displayed value
truthful.

---

## 5. Reconciliation: handoff assumptions vs the verified bus

The handoff was written from the design side and states that command mapping must
be taken from the repo rather than the document, and that its protocol assumptions
must be verified before building. Four disagreements were found and resolved.

### 5.1 Fan is 7 steps, not 15

Handoff models `fanLevel (0–15)` and renders `"7 / 15"` across 12 bars with
rounding. `U_AIR_WIND_LEVEL_LEFT` is **1–7**, and **15 is the AUTO sentinel**
(wiki p5).

**Resolved:** **7 bars, one per level.** Readout is `"5 / 7"`. Rounding logic is
deleted. When `WIND_LEVEL == 15` the meter renders an **AUTO** treatment — all
bars in the accent colour, dimmed to read as not-driver-set — and the numeral is
replaced by `AUTO`. Bar ramp heights from the handoff are re-spread across 7 bars.

### 5.2 Seat heat/cool has 4 protocol states, presented as 3

`U_AIR_SEAT_HOT_*` and `_BLOW_*` are **0–3** and cycle **downward from 3**
(wiki p5). The handoff specifies two pips and ascending `OFF → LOW → HIGH`.

**Resolved:** keep the handoff's **two pips and three presented states**. Mapping:

| Bus | Rendered |
|---|---|
| 3 | HIGH |
| 2 | HIGH |
| 1 | LOW |
| 0 | OFF |

The descending raw cycle `0 → 3 → 2 → 1 → 0` would otherwise render as
`OFF → HIGH → HIGH → LOW → OFF`, i.e. one tap that visibly does nothing.
**Fix: when the current bus value is 3, a single tap sends the command twice**,
landing on 1. Driver-visible cycle becomes `OFF → HIGH → LOW → OFF`, matching
the design, while still rendering from bus state.

Cycle order is *inferred* from wiki p5's "cycles down from 3" and is item 5 in the
car-session checklist. If the order differs, the double-send rule is revised —
the presentation does not change.

### 5.3 Cabin temperature has no signal; outside temperature is packed

The 1d header specifies `"CABIN 64°F · OUT 41°F"`. There is **no interior-temperature
code in any of the five extracted tables**. Outside temperature exists but is
`U_TEMP_OUT = 40` on **module 0 (MAIN)** — not module 7 — and is recorded as
"live, but the value is packed, not a plain temperature" (wiki p10).

**Resolved:**

- **CABIN is removed** from the 1d header. It is not stubbed, faked, or shown as
  a placeholder — no signal exists.
- **`U_TEMP_OUT` is decoded** in a car session (checklist item 4). Header renders
  `OUT 41°F` only.
- If the decode fails, the header shows the title alone and the adaptive slot
  falls back per section 6.

### 5.4 The app needs three modules, not one

The handoff implies a single climate subscription.

| Signal | Module | Code |
|---|---|---|
| All climate state | 7 CANBUS | `U_AIR_*` 10–96 |
| Volume | 4 SOUND | read `U_VOL` 2, write `C_VOL` 0 with `{-1}` / `{-2}` |
| Outside temperature | 0 MAIN | `U_TEMP_OUT` 40 — packed |
| Day/night theme | 0 MAIN | `U_LAMPLET` 4 |

**Resolved** by the three-callback design in section 4. Bonus: `C_VOL` accepts
`VOL_HIDE_UI = -7` (wiki p10), so our bar can own the volume readout without the
OEM volume OSD painting over it.

---

## 6. Screen 2a — the resting bar

Geometry, colour, type and spacing are **as specified in the handoff README**.
Deltas only:

- Fixed geometry is a correctness requirement (design rule 3). Explicit `.width()`
  and `.height()` on every slot; `weight()` used **only** where the handoff says
  `1fr`. No `wrapContentWidth` anywhere — a font metric change must not be able to
  move a control.
- The zone numerals are **readouts, not buttons**. Only `−` and `+` are tappable.
- The 35dp volume readout strip is **deliberately not tappable**.

### The adaptive slot

The one variable region (200x96), driven by a pure state machine in `:bus`:

```
outsideTemp → SlotContent { FRONT_DEFROST | SEAT_HEAT | SEAT_COOL }
```

- Thresholds: `< 32°F` → FRONT_DEFROST, `32–85°F` → SEAT_HEAT, `> 85°F` → SEAT_COOL.
- Hysteresis: **±3°F deadband**, **30 s minimum dwell** before re-evaluating.
- **Never** change while a finger is down on the slot, and never within **1 s** of
  a tap on it.
- Defrost outranks comfort: below freezing, clearing glass wins.
- Whatever the slot holds **also exists on 1d**. It is a shortcut, never the sole
  route to a function.
- Outside temperature is consumed, **not displayed**, in the bar.

**Fallback if `U_TEMP_OUT` cannot be decoded:** the slot pins to `SEAT_HEAT`, which
is the middle band and always safe. It is never left blank, and the geometry does
not change.

Pure Kotlin with an injected clock, so hysteresis and dwell are unit-tested against
a synthetic temperature series rather than by sitting in a cold car.

### Theme

Day/night follows `U_LAMPLET` (module 0, code 4), **not a clock**. Day is a
luminance change only — no layout change, so muscle memory holds. Polarity is
checklist item 8.

---

## 7. Screen 1d — the climate page

Geometry as specified in the handoff README. Structural notes:

- A `Column` of fixed-height sections; the handoff's `margin-top: auto` footer
  becomes `Spacer(Modifier.weight(1f))`.
- Header status line is `OUT 41°F` only (section 5.3).
- Fan section per section 5.1 (7 bars).
- Comfort section per section 5.2 (two pips, double-send at 3).
- Airflow tiles: **icon only, no text label**, exactly one active, idempotent
  setters, `UNKNOWN` lights none.
- **SYNC lives in the Mode section**, not as a pill on the passenger zone, so no
  target falls below 96dp.
- The range track is a **readout**, not a drag target.

### Controls for absent hardware are omitted, not disabled

A permanently greyed tile wastes a 96dp slot. Fitted on this WK2 and therefore
built (wiki p5): MAX A/C, heated steering wheel, SYNC, both seat heaters, both
seat ventilators, dual-zone temp, front and rear defrost, recirculate, AUTO, all
three airflow flags. Not fitted and therefore absent: rear climate zone, massage
and lumbar seats.

---

## 8. Interaction rules

| Interaction | Behaviour |
|---|---|
| Tap CLIMATE (2a) | 1d slides up over app content, 220 ms ease-out translate-Y. Bar remains. No fade on controls. |
| Tap CLOSE / back gesture | Reverse. |
| Tap `−` / `+` | One step per tap. Press-and-hold repeats at ~150 ms after a 400 ms delay. |
| Tap airflow mode | Direct idempotent set. Re-tapping the active mode is a genuine no-op. |
| Tap AUTO / A/C / RECIRC / MAX A/C / defrost / SYNC | Toggle. |
| Tap seat heat / cool | Presented cycle `OFF → HIGH → LOW → OFF` via section 5.2. |
| Tap adaptive slot | Acts on whatever it currently holds. |
| Press-and-hold HOLD · OFF | ~800 ms with visible fill progress. **Single tap does nothing.** |
| Any state change | Reflected from the bus, never optimistically from the tap. |

Three rules cut against Compose defaults and need explicit handling:

- **Pressed state on touch-down, not release**, and **no ripple** — there is no
  hover on this device and shadows/ripples read as smudges on a glossy panel in
  sunlight. Use `indication = null` plus
  `interactionSource.collectIsPressedAsState()` driving the handoff's own pressed
  rendering (`brightness(1.25)` on filled tiles, `rgba(255,255,255,.08)` on
  outlined ones).
- **Hold-repeat** as a coroutine loop tied to the press, on every `−`/`+`.
- **No animation on values.** Explicitly **no** `animateIntAsState` on any numeral.
  A moving numeral is unreadable at a glance.

**No shadows anywhere.** Elevation is carried by surface tint and border only.

### Icons

Airflow, defrost and recirc are **final assets** — traced from the handoff's
`icons/` PNGs to `VectorDrawable` and tinted at runtime. This collapses the
`dark/` + `light/` duplication to one asset per glyph, and the airflow tiles'
active state becomes a tint change rather than an asset swap.

HOME, BACK, volume, seat and wheel glyphs are **placeholders** in the handoff. For
the first build, keep the handoff's text labels and drawn chevron glyphs — the
handoff permits this and it ships no placeholder rectangles. Real icons are chosen
after the first round of hardware testing.

---

## 9. Design tokens

`1px = 1dp` throughout (1080x1920 at 160dpi).

Android has no `oklch()`. Colours are converted once and committed as hex with the
oklch source retained in a comment, so the design file stays the reference.

| Token | oklch | sRGB | |
|---|---|---|---|
| accent (night) | `0.78 0.15 75` | `#EFA831` | |
| accent (day) | `0.62 0.13 75` | `#B37903` | |
| cool (night) | `0.72 0.12 235` | `#4CB0E5` | |
| cool-bright (night) | `0.78 0.11 235` | `#6BC3F4` | |
| cool-label (night) | `0.82 0.09 235` | `#89CEF6` | |
| cool (day) | `0.52 0.14 235` | `#0073AD` | **gamut-clamped** |
| warm (night) | `0.68 0.16 35` | `#E96E50` | |
| warm-bright (night) | `0.74 0.15 35` | `#FA8467` | |
| warm (day) | `0.55 0.16 35` | `#BC4527` | |
| track start (night) | `0.60 0.13 235` | `#008BC2` | **gamut-clamped** |
| track end (night) | `0.72 0.14 35` | `#EE8266` | |

Both clamped tokens are blues asking for more chroma than sRGB provides at that
lightness; the clamp is mild (slightly negative red channel). They will read very
slightly less saturated than the browser mockup. This is a display-gamut limit,
not a design change.

Non-oklch tokens (surfaces, inks, dividers, alphas), the type scale, spacing steps
and radii are taken directly from the handoff's token block.

Typography: **Manrope** for UI, **IBM Plex Mono** for micro-labels and numerics,
bundled as app assets.

---

## 10. Testing strategy

### The fake bus is adversarial

`FakeVehicleBus` reproduces the documented misbehaviour rather than returning tidy
values, so bugs surface on an AVD instead of in the driveway:

| Modelled behaviour | Catches |
|---|---|
| Seat heat cycles **downward** `0 → 3 → 2 → 1 → 0` | The double-send rule; any ascending assumption |
| `AUTO` forces A/C on, clears body-blow, fan → **15** | Fan meter rendering AUTO rather than "level 15" |
| `FAN_UP` **exits AUTO** as a side effect | AUTO tile unlighting from bus state |
| `FRONT_DEFROST` / `MAX_AC` force recirc on, temps → `-2` | LO rendering; RECIRC self-lighting |
| `FRONT_DEFROST` leaves airflow flags in an unnamed combo | The `UNKNOWN` path |
| Fan clamps at 7 | Off-by-one at the top of the meter |
| Airflow setters are idempotent | Re-tap being a genuine no-op |
| Seat heat/vent mutual exclusion | State agreeing with the vehicle |

### Layers

- **JVM unit tests in `:bus`** — where the real coverage lives. Sentinel mapping
  (`15 → AUTO`, `-2 → LO`), airflow triple → mode including `UNKNOWN`, the
  adaptive-slot hysteresis and 30 s dwell against a synthetic series with an
  injected clock, the seat double-send rule, `Signal`/`Command` table integrity.
- **`:harness` on an AVD** at 1080x1920/160dpi, with a debug drawer to inject
  awkward states directly: LO sentinel, fan AUTO, unknown airflow, bus
  disconnected, day and night.
- **Hardware sessions** for section 11 only.

`:bus` logic is written test-first. No claim that anything passes without the
command output to show for it.

---

## 11. Car-session checklist

Everything answerable only by the vehicle, ordered by what it blocks. Items 5–11
are readable from a stationary vehicle with the existing probe — one session, no
new code.

**Blocking — the overlay spike**

1. Can a `TYPE_ACCESSIBILITY_OVERLAY` (2032) window from an `AccessibilityService`
   be positioned over `[0,1693][1080,1920]`? Negative-y is the untested claim in
   wiki p7. The probe's `OVERLAY` broadcast uses an Activity context, so 2032
   needs a ~30-line throwaway service. **All of 2a depends on this.**
2. **Does our overlay consume the touches, or do they also reach `com.syu.air`
   underneath?** If they pass through, tapping our AUTO may also hit whatever OEM
   control sits beneath it. Not addressed anywhere in the wiki, and as decisive as
   item 1.
3. Do `GLOBAL_ACTION_HOME` and `GLOBAL_ACTION_BACK` work from our service on this
   ROM.

**Blocking the adaptive slot**

4. Decode `U_TEMP_OUT` (module 0, code 40). Log the full `int[]` against a known
   outside temperature. Fallback is section 6.

**Confirming assumptions this spec makes**

5. Seat heat cycle order — is it really `0 → 3 → 2 → 1 → 0`?
6. `U_AIR_ACMAX = 53` moves when command 15 is sent. The pairing is inferred from
   two tables; nothing states it.
7. What the airflow flags read **after** command 12 — tells us whether `UNKNOWN`
   is a real state or a theoretical one.
8. `U_LAMPLET` (0/4) polarity and semantics — right day/night signal, and which
   value is night.
9. Does `VOL_HIDE_OSD` (`C_VOL` with `{-7}`) actually suppress the OEM volume OSD?
10. Are the `_RIGHT` fan/auto/blow codes live, or is this vehicle single-fan? The
    command table has only one fan pair (6/7) with no right variant, suggesting
    left-only is authoritative.
11. Fan really tops out at 7; temperature min and max in °F, and when `-2` appears.

**Measuring the fallback, in case items 1–2 fail**

12. With `com.syu.air` disabled (`pm disable-user --user 0 com.syu.air`):
    does the **227px inset survive**, or is it released to apps? And does
    SystemUI claim the vacated nav-bar slot and draw its own back/home/recents?
    Wiki p3 confirms disabling is clean and that climate keeps working, and
    wiki p7 implies the inset depends on `com.syu.air` holding the window — but
    neither is measured. Compare `dumpsys window windows` app-area frames before
    and after.

    Worth measuring in the same session even though v1 keeps `com.syu.air`
    running, because disabling it is the fallback if item 2 shows touch leakage,
    and it may remove the need for item 1's negative-y offset entirely. Restore
    with **both** `pm enable com.syu.air` **and**
    `am start-service -n com.syu.air/.AirService` — `pm enable` alone leaves an
    enabled package with no bar, since it only self-starts on `BOOT_COMPLETED`.

### Safety

All bench work on a **stationary, parked** vehicle. Take a baseline with
`scripts/hu-state.sh` before experimenting and diff back to clean afterwards.
Command **16** leaves this vehicle with no way to change climate until sent again.
Commands **12** and **15** toggle into each other and take several rounds to
unwind through `cmd()`; tap injection on the OEM airflow button
(`input tap 545 1793`) clears the state in one action.

---

## 12. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| 2032 cannot be positioned over the nav-bar region (item 1) | 2a cannot sit where the design puts it | Spike first, before any window code. Fallbacks: 2038 with the same offset; or rest 2a in the app area above the OEM bar (handoff shape B, costs 227px of app content permanently) |
| Overlay does not consume touches (item 2) | Double-actuation — our tap also hits an OEM control | Spike alongside item 1. If touches leak, disabling `com.syu.air` is the only fix — verified clean and climate survives it (wiki p3), but it violates design rule 6 and must be re-decided. Item 12 measures its cost in the same session |
| Our service dies with `com.syu.air` disabled | **No climate control at all** in a vehicle with no physical HVAC controls; module holds last state | The reason design rule 6 exists. Recovery needs two adb commands and cannot be done from the unit itself, so v1 keeps `com.syu.air` running |
| `U_TEMP_OUT` packing undecodable | Adaptive slot loses its premise | Pin the slot to `SEAT_HEAT`; geometry unchanged. Frame stream (7/1019) is a later avenue |
| Service killed by the system | Bar disappears | `com.syu.air` still running underneath, so climate control is never lost (design rule 6). This is the reason for rule 6 |
| Compose overlay lifecycle quirks | Panel fails to attach or leaks | Single shared `ComposeOverlayHost`; exercised on the AVD before hardware |
| `-2` and other sentinels rendered as numbers | Driver misled about vehicle state | Sentinels handled once in the mapper; adversarial fake covers them |

---

## 13. Open questions carried forward

1. Should the adaptive slot react to actual fogging rather than a fixed ambient
   threshold? (handoff Q1) — deferred; fixed threshold ships first.
2. Should the 1d range track become drag-to-set? (handoff Q4) — deferred; if
   added, it must not shrink the `−`/`+` targets.
3. Real icons for HOME, BACK, volume, seat and wheel — chosen after the first
   hardware session.

Handoff Q2 (threshold values) is answered in section 6. Handoff Q3 (which
functions are fitted) is answered by wiki p5 and recorded in section 7.
