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

### Verified window parameters for 2a

**MEASURED ON VEHICLE 2026-09-08 (checklist items 1 and 2 — both PASS).** The
wiki's open question was whether a negative `y` offset could pull the bar over the
OEM region. It is not needed: positioning from the **top** of the unrestricted
screen works directly.

```kotlin
WindowManager.LayoutParams(
    MATCH_PARENT, 227, TYPE_ACCESSIBILITY_OVERLAY /* 2032 */,
    FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT
).apply {
    gravity = Gravity.TOP or Gravity.START
    x = 0
    y = 1693
}
```

Result on device:

```
LANDED at screen (0,1693) size 1080x227  => covers [0,1693][1080,1920]
Window #0 ... ty=2032 ... mAttrs={(0,1693)(fillx227) gr=TOP START
           fl=NOT_FOCUSABLE LAYOUT_IN_SCREEN LAYOUT_NO_LIMITS
```

- Exactly the target region, and **`Window #0`** — topmost, above the OEM
  `NavigationBar`.
- `FLAG_LAYOUT_NO_LIMITS` is load-bearing. Without it, `gravity` resolves against
  the inset-reduced frame and the window cannot enter the nav-bar region at all.
  With `gravity = BOTTOM` it would land at 1693 and sit *above* the bar; using
  `TOP` with an absolute `y` sidesteps that entirely.

**Touches are consumed, not shared (item 2 — PASS).** Injected taps at the OEM
A/C control (`875,1722`) and the OEM airflow button (`545,1793`) were both
delivered to our view with correct local coordinates (`y = 29` and `y = 100`,
i.e. `raw − 1693`), and **no `U_AIR_*` signal changed**. The OEM controls directly
beneath did not fire. Design rule 6 is therefore fully achievable: cover
`com.syu.air`, consume every touch, and keep it running as the safety net.

**Global nav actions work (item 3 — PASS).** `GLOBAL_ACTION_HOME`,
`GLOBAL_ACTION_BACK` and `GLOBAL_ACTION_RECENTS` all returned `true` from the
service, and HOME/BACK were confirmed by observed focus changes, not by the
return value alone.

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
    TEMP_OUT(0, 40),   // packed; see section 5.3 for the decode ILLUMINATION(0, 4),
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
    Triple(0, 0, 0) -> NONE         // normal: AUTO owns airflow — see below
    else            -> UNKNOWN      // no tile lit — do not guess
}
```

**MEASURED 2026-09-08: `(0, 0, 0)` is the vehicle's resting state, not an edge
case.** With `U_AIR_AUTO = 1`, all three flags read 0 — the vehicle reports no
airflow mode at all while AUTO owns it. On the first `FAN_UP`, `AUTO` went
`1 → 0` and `BLOW_BODY_LEFT` went `0 → 1` in the same update, i.e. leaving AUTO
materialises a concrete airflow mode (FACE).

This is why `NONE` is separated from `UNKNOWN`. They render identically — **no
tile lit** — but they mean different things:

- `NONE` is expected and correct whenever AUTO is engaged. Lighting a tile here
  would be actively wrong: the driver has not selected a mode.
- `UNKNOWN` is a combination we do not recognise, e.g. after the command 12
  macro. It should be logged; `NONE` should not.

Either way, lighting a *wrong* tile violates design rule 4 more badly than
lighting none, so both fall back to the same safe rendering. The practical
consequence for 1d: **with AUTO on, the airflow row correctly shows four
unlit tiles.**

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

**MEASURED 2026-09-08 — confirmed.** From a baseline of `WIND_LEVEL = 15` with
`AUTO = 1`, ten successive `FAN_UP` (command 6) commands produced:

```
15 -> 3 -> 4 -> 5 -> 6 -> 7 -> 7 -> 7 -> 7 -> 7 -> 7
```

- **Max is 7**, and the command is **idempotent at the ceiling** — five further
  presses produced no update at all, so the UI needs no clamping of its own.
- The first press exited AUTO and landed on **3**, not 15 or 1. So 15 is
  genuinely a sentinel and not a level, exactly as wiki p5 says; the fan was
  physically at 3 while AUTO reported 15.
- `AUTO` and `BLOW_BODY_LEFT` changed in the same update — see the airflow note
  in section 4.

Sending command 2 (AUTO) restored `AUTO = 1`, `WIND_LEVEL = 15` and
`BLOW_BODY_LEFT = 0` in one action, matching the pre-test baseline exactly.

### 5.2 Seat heat is a 3-state cycle — the handoff was right

**MEASURED ON VEHICLE 2026-09-08. This section previously specified a
double-send workaround; that was based on an inference which the measurement
disproved. The workaround is removed — it would have been a bug.**

Wiki p5 records `U_AIR_SEAT_HOT_*` as range **0–3**, cycling "downward from 3",
from which this spec originally inferred a 4-state cycle `0 → 3 → 2 → 1 → 0`.

Measured by sending command 17 five times from a known baseline of 0 and reading
`U_AIR_SEAT_HOT_LEFT` (code 29) after each:

```
tap 1 -> 3      tap 4 -> 3
tap 2 -> 1      tap 5 -> 1
tap 3 -> 0
```

The actual cycle is **`0 → 3 → 1 → 0`**. **State 2 is never visited.** The range
is 0–3, but only `{0, 1, 3}` are reachable through the cycle command.

**Resolved:** the handoff's original design is exactly correct with no loss.

| Bus | Rendered | Pips |
|---|---|---|
| 3 | HIGH | both lit |
| 1 | LOW | one lit |
| 0 | OFF | none lit |
| 2 | HIGH | both lit — defensive only; unreachable via the cycle |

- **Two pips, three states, no double-send.** A single command per tap.
- Driver-visible cycle is `OFF → HIGH → LOW → OFF`, which is what the vehicle
  natively does.
- Value 2 is still *mapped* (to HIGH) because the cycle command is not
  necessarily the only writer, but no code should rely on reaching it.

Mutual exclusion with `SEAT_BLOW_*` was **not** exercised: seat vent was already
0, so it never reported a change. Still modelled per section 4 — the protocol
enforces it and we render both.

### 5.3 Cabin temperature has no signal; outside temperature is DECODED

The 1d header specifies `"CABIN 64°F · OUT 41°F"`. There is **no
interior-temperature code in any of the five extracted tables**, so CABIN is
removed — not stubbed, not faked, not shown as a placeholder.

Outside temperature is `U_TEMP_OUT = 40` on **module 0 (MAIN)**, and it **is a
live sensor after all**. A stationary reading looked static because ambient
barely moves in a parked car; a driving capture settled it.

**DECODED 2026-09-08 (session 2, driving capture):**

```
degreesF = ((raw and 0xFFFF) - 1000) / 10.0     // 0.1 °F resolution
valid    = (raw shr 28) and 1 == 1              // bit 28 is a present/valid flag
```

Evidence — three values observed across a ten-minute drive, against the head
unit's own status-bar reading as ground truth:

| Raw | Hex | low 16 | Decoded | When |
|---|---|---|---|---|
| 268437296 | `0x10000730` | 1840 | **84.0 °F** | moving, air over the sensor |
| 268437306 | `0x1000073A` | 1850 | **85.0 °F** | slowing |
| 268437316 | `0x10000744` | 1860 | **86.0 °F** | parked, heat-soaking |

The status bar read **86 °F** while raw was `268437316`, and the formula gives
exactly 86.0. The offset of 1000 is the usual automotive trick for keeping
sub-zero values unsigned: 0 °F encodes as 1000, −40 °F as 600.

**Consequence: the adaptive slot works as designed.** Section 6's
pinned-to-`SEAT_HEAT` fallback is no longer the shipping path — it remains only
as the behaviour when the valid bit is clear.

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

### 5.7 MAX A/C's fan effect is observed but not instrumented

Owner-verified on 2026-09-09: tapping MAX A/C does "what is expected — LOW,
recirc, max fan". The LO setpoints and forced recirculation are already modelled
in `FakeVehicleBus`. **The fan is not**, and deliberately stays unmodelled until
measured, because the observation is physical (audible blower) and it is not yet
known whether the vehicle *reports* it:

- if `WIND_LEVEL` goes to 7, the fan meter should fill solid and drop its AUTO
  label, and `FakeVehicleBus` should set it;
- if `WIND_LEVEL` stays at the AUTO sentinel (15) while the blower physically
  maxes, then the boost is invisible to the protocol, the UI is already correct,
  and the fake must **not** set it — inventing a value there would make the fake
  lie in the one direction it exists to prevent.

A screenshot of the fan row taken **while MAX A/C is engaged** settles it in one
frame. The capture taken during session 6 arrived after the owner had toggled it
back off (AUTO and A/C lit, MAX A/C and RECIRC clear), so it does not answer the
question.

Added as car-session checklist item 14.

### 5.6 AUTO is an idempotent setter, not a toggle

The handoff's interaction table lists AUTO alongside A/C, RECIRC, MAX A/C,
defrost and SYNC as a **toggle**. The evidence says it is not.

The repo's command table labels index 2 as **"AUTO on — macro"**, while labelling
indices 1, 3, 13, 14 and 16 explicitly as **"toggle"**. That distinction was made
by whoever swept the command space, and it is the only index given a directional
name. There is also **no "AUTO off" command anywhere in the table** — the way you
leave AUTO is to move the fan, which index 6 does as a documented side effect,
and which the factory UI relies on too.

**Resolved: AUTO is an idempotent set.** Tapping it engages AUTO; tapping it
again does nothing. This is consistent with how the four airflow modes already
work — the design explicitly specifies re-tapping an active airflow tile as a
no-op — so it is a natural fit rather than a wart, and design rule 2 ("direct
selection, never cycling") already establishes idempotent setters as the house
pattern.

`FakeVehicleBus` models it this way, which is what surfaced the discrepancy:
Plan 2's verification asked for "tapping AUTO unfills it" and that behaviour is
unreachable. Both halves that *are* verifiable were confirmed instead — the AUTO
cell renders unfilled when the bus reports `autoOn = false`, and tapping it
dispatches index 2 and drives the bus to `autoOn = true`.

**Not yet measured on the vehicle:** what index 2 does when AUTO is already
engaged. Added as car-session checklist item 13. If it turns out to toggle, the
fake and this section change; the UI does not, because it renders from bus state
either way.

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

**Outside temperature is decoded** — see section 5.3 — so the slot is fully
functional. The fallback survives for the case where the valid bit is clear or
the signal has not yet arrived: the slot pins to `SEAT_HEAT`, the middle band,
which is always safe. It is never left blank and the geometry never changes.

Pure Kotlin with an injected clock, so hysteresis and dwell are unit-tested against
a synthetic temperature series rather than by sitting in a cold car.

### Theme

Day/night follows `U_LAMPLET` (module 0, code 4), **not a clock**. Day is a
luminance change only — no layout change, so muscle memory holds.

**Polarity confirmed 2026-09-08 (session 2):** headlights on drove
`U_LAMPLET` `0 → 1`, and off drove it back to `0`. So **1 means night**, and
`ClimateState.isNight = (raw == 1)` is correct as written. No inversion needed.

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
| Tap A/C / RECIRC / MAX A/C / defrost / SYNC | Toggle. |
| Tap AUTO | **Idempotent set, not a toggle** — see section 5.6. Re-tapping while AUTO is engaged is a no-op. |
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

## 11. Car-session results

**Session 1: 2026-09-08.** Stationary vehicle (`vel=0.0` confirmed via the head
unit's own GPS), `U_ACC_ON = 1`. Full 210-code baseline captured before any
command and verified restored afterwards. A throwaway `AccessibilityService`
(`com.wk2.spike`) provided the 2032 overlay; the existing probe provided state
observation and command dispatch.

| # | Question | Result |
|---|---|---|
| 1 | 2032 overlay positionable over `[0,1693][1080,1920]`? | **PASS** — exact, no negative offset needed. Params in section 3 |
| 2 | Does our overlay consume touches, or leak to `com.syu.air`? | **PASS** — fully consumed, zero leakage. Section 3 |
| 3 | Do `GLOBAL_ACTION_HOME` / `BACK` work? | **PASS** — both, confirmed by focus change. Section 3 |
| 4 | Decode `U_TEMP_OUT` | **SOLVED in session 2** — see section 5.3 |
| 5 | Seat heat cycle order | **ANSWERED — spec was wrong.** `0→3→1→0`, state 2 unreachable. Section 5.2 |
| 6 | `U_AIR_ACMAX` moves on command 15 | **NOT TESTED** — requires the MAX A/C macro. Below |
| 7 | Airflow flags after command 12 | **SUPERSEDED** — a better finding emerged without the macro. Section 4 |
| 8 | `U_LAMPLET` polarity | **SOLVED in session 2** — 1 = night |
| 9 | Does `VOL_HIDE_OSD` suppress the OEM OSD? | **LIKELY MOOT** — see below |
| 10 | Are the `_RIGHT` fan/blow codes live? | **ANSWERED** — inert. Single fan, left is authoritative |
| 11 | Fan ceiling | **ANSWERED** — max 7, clamps idempotently. Section 5.1 |
| 12 | Does the 227px inset survive disabling `com.syu.air`? | **NOT TESTED** — and now low priority, since items 1–2 passed |

### Item 4 — `U_TEMP_OUT` is probably NOT the live outside temperature

```
MAIN U_TEMP_OUT c=40 [268437316]   =  0x10000744
CANBUS U_EXIST_TEMP_OUT c=1012 [1] =  outside-temp sensor IS fitted
```

Ground truth was obtained from the head unit's **own status bar**, which renders
outside temperature: it read **86 °F**.

`0x10000744` does not decode to 86 °F, nor to 30 °C, under any of: low 8 bits
(68), low 12 bits (1860), low 16 bits (1860), BCD (`744` → 74.4), ÷10 (186.0),
÷100 (18.60), or °C→°F conversions of those. Byte-wise it is
`[0x10, 0x00, 0x07, 0x44]`.

Two further observations:

- **The value did not change** across two reads 12 minutes apart. Codes are
  push-on-change, so a live ambient temperature should have moved, or at least
  not be guaranteed static.
- **No code in the whole 210-code baseline holds 86, 30, 300 or 860** as a plain
  value.

**Revised hypothesis: `U_TEMP_OUT` on module 0 is a configuration or calibration
word, not a live reading.** It sits among `U_BRIGHT_LEVEL`, `U_LAMPLET` and other
settings in the MAIN module, and its top nibble (`0x1`) looks like a flag field.
The wiki's "live, but the value is packed" may have been an over-read of the same
static value.

**Where to look next (session 2):** the live value the status bar renders is most
likely in the **raw MCU frame stream** (module 7, code 1019). Use the wiki p11
method with the probe's `RECORD` mode, which already writes TSV with per-row
array lengths for exactly this. A useful correlation handle: a parked car's
outside-temp reading drifts steadily in sun, so a 30-minute stationary recording
should produce a slowly-moving byte while almost everything else stays frozen —
the same set-difference approach that isolated steering angle.

One frame already looks like the climate carrier and is worth starting from.
Observed on screen: `2E 21 08 CC 0F 44 44 01 30 …` — id `0x21`, where `0x0F` = 15
matches `WIND_LEVEL` (AUTO) and `0x44 0x44` = 68, 68 matches both set
temperatures. Whatever carries ambient may be adjacent.

**This does not block the build.** Section 6's fallback applies: the adaptive slot
pins to `SEAT_HEAT` and the 1d header omits the status line. Every other signal
both screens need is verified.

### Item 6 — deliberately not tested

Requires command 15 (MAX A/C), a macro that forces temperatures to the `-2`
sentinel and toggles against command 12; wiki p6 records that unwinding it
through `cmd()` takes several rounds. `U_AIR_ACMAX = 53` exists and reads 0, so
the code is live. Left for a session where restoring state by tap injection on
the OEM UI is acceptable.

### Item 9 — probably unnecessary

`C_VOL` writes work: `{-1}` moved `U_VOL` 10 → 11, `{-2}` returned it to 10.
But `U_IS_VOLUI_SHOW` (SOUND code 25 — a read code not previously in this spec)
stayed at 0 throughout, meaning the OEM volume OSD **never appeared** for a
bus-driven volume change. If that holds, `VOL_HIDE_OSD` is redundant. Keep the
command defined; do not rely on needing it. Worth one visual confirmation, since
"never fired" only proves "never changed".

### Incidental findings worth keeping

| Finding | Why it matters |
|---|---|
| `U_AIR_SYNC = 1` while `U_AIR_DUAL = 0` | Confirms wiki p5's "label that lies" on live hardware |
| `U_AIR_REAR_TEMP_LEFT = -2` | The `-2` sentinel also means **unavailable** on unfitted hardware, not only LO. Section 4's sentinel rule covers both |
| `U_AIR_TEMP_UNIT = 1` | Fahrenheit confirmed |
| `U_EXIST_AIR = 1`, `U_EXIST_TEMP_OUT = 1`, `U_EXIST_AIR_CONTROL = 0` | Capability flags usable to drive section 7's omit-absent-hardware rule |
| `U_HANDBRAKE = 1` fired | Wiki p10 records it as never firing. It does |
| `U_BRIGHT_LEVEL_DAY = 100`, `_NIGHT = 0` | Useful alongside `U_LAMPLET` for the theme signal |
| `U_SPECTRUM_ENABLE = 0` | The ~10 Hz spectrum flood is currently off, but section 4 still excludes it — it is user-toggleable |

### Remaining

14. **Does MAX A/C move `U_AIR_WIND_LEVEL`, or only the physical blower?**
    Engage MAX A/C and screenshot the panel's fan row *while it is on*. If the
    meter fills solid with no AUTO label, the fan reports 7 and
    `FakeVehicleBus.MAX_AC` needs it; if the AUTO label is still showing, the
    boost is not reported and the fake is already correct. See section 5.7.
    One screenshot, no commands sent.


13. **Does command index 2 toggle AUTO, or only set it?** Send index 2 while
    `U_AIR_AUTO` already reads 1 and see whether it goes to 0. The command table
    calls it "AUTO on" rather than "toggle" and there is no AUTO-off index, so a
    set is expected — but it has never been sent from the already-on state. See
    section 5.6. Trivial and safe: one command, reversible by sending it again.

### Remaining for session 2

- Item 4: outside temperature ground truth (**blocks the adaptive slot**).
- Item 8: toggle headlights, observe `U_LAMPLET`.
- Item 9: one screenshot during a bus-driven volume change.
- Item 6: only if a macro-recovery session is acceptable.
- Item 12: optional now that items 1–2 passed.

### Session 2: 2026-09-08, driving capture

**The probe's `RECORD` mode does not work.** Its external files directory was
never created and it logged nothing at all; its own README flagged the path as
never hardware-tested, and that was accurate. Do not rely on it.

**The replacement is better and needs no custom code:** a detached, rotating,
device-side `logcat` capture.

```bash
adb shell "nohup logcat -v time -s HUPROBE:V Gps:V \
  -f /sdcard/hu-drive.log -r 20480 -n 40 > /dev/null 2>&1 &"
```

This survives losing Wi-Fi because it is the unit's own process writing to the
unit's own storage — verified by tearing down adb entirely and confirming the
same PID kept writing. It captures the frame stream **and** the head unit's own
GPS `vel=` lines in one timestamped file, which is the ground truth the probe's
bespoke feature existed to provide. ~12 MB/hour.

Ten-minute capture, 22 543 lines, GPS to 22.4 m/s (~50 mph).

| # | Question | Result |
|---|---|---|
| 4 | Decode `U_TEMP_OUT` | **SOLVED** — see section 5.3. The stationary reading looked static only because ambient barely moves in a parked car |
| 8 | `U_LAMPLET` polarity | **SOLVED** — 1 = night. `isNight` is correct as written |
| 10 | Are the `_RIGHT` codes live? | Still inert, confirming single-fan |

Newly answered, beyond the checklist:

| Signal | Finding |
|---|---|
| `U_CUR_SPEED` (7/1031) | **Live, and the unit is km/h.** Mean `GPS-km/h ÷ code` = 1.020 across 485 paired samples; the mph ratio is 0.63. Code range 0–80 against a GPS range of 0–49.8 mph (= 80.1 km/h). Wiki p10 records it as "never fired", which was only ever true of a stationary vehicle |
| `U_ENGINE_SPEED` (7/1032) | Live across 595–3102 rpm |
| `U_STEER_ANGLE` (0/41) | **Still inert** — a flat `[80, 160, 80]` through the entire drive, confirming wiki p10 |
| `U_RADAR` (0/13) | Fired, value `[0]` |

Frame-stream volume by message id, for anyone decoding further: `2E5A` (3997),
`2E52` (3710), `2E60` (2168), `2E28` (952), `2E29` (928, steering angle),
`2E4D` (918), `2E21` (599, the climate carrier).

**Method note.** The stationary session concluded `U_TEMP_OUT` was probably a
configuration word, because two readings twelve minutes apart were identical
and no simple decoding matched the display. That inference was wrong, and the
reason is worth keeping: *a static value carries no information about which
byte holds the signal.* Wiki p10's rule — "registered but silent means
unchanged, not unfitted" — applies to the value's *content* as much as to
whether the callback fires. The fix was not cleverer arithmetic; it was making
the signal move.

### Session 3: 2026-09-08, decompiling the vendor apps

Pulled `com.syu.canbus` (94 MB), `com.syu.air` (8 MB), `com.syu.ms` (8 MB) and
`com.syu.ss` (6.7 MB) from the unit and decompiled with jadx 1.5.6.

**`com.syu.air` is the valuable target, not `com.syu.canbus`.** Canbus's 94 MB is
79.5 MB of `res/` and 22.7 MB of `assets/`, almost entirely `.webp` — per-vehicle
door and parking graphics. Its 3248 decompiled classes hold no MCU frame or
protocol code. All **228** `Car_NNNN_*` vehicle profiles live in `com.syu.air`,
including our `Car_0374_PA_Jeep_All`.

#### The command table, confirmed from source

`Car_0374_PA_Jeep_Wrangler` uses indices **1–18 and 20–24**, with **19 and 25
absent entirely** — so omitting them was correct, and not merely "no effect when
swept". Method names give authoritative meanings, and every one matches the
empirically-swept table:

| N | Source method | Our `Command` |
|---|---|---|
| 1 | `airAc` | `AC` |
| 2 | `airAuto` | `AUTO` |
| 3 | `airCycle` | `RECIRC` |
| 4 / 5 | `airTempLeftP` / `M` | `TEMP_L_UP` / `_DOWN` |
| 6 / 7 | `airVolLeftP` / `M` | `FAN_UP` / `_DOWN` — "Vol" is *air volume* |
| 8–11 | `airMode` (four branches) | the four `AIRFLOW_*` setters |
| 12 | `airFront` | `FRONT_DEFROST` |
| 13 | **`airDual`** | `SYNC` — the label lies in the source too |
| 14 | `airRear` | `REAR_DEFROST` |
| 16 | `airPower` | `CLIMATE_POWER` |
| 17 / 18 | `airLeftSeatHot` / `airRightSeatHot` | `SEAT_HEAT_L` / `_R` |
| 20 / 21 | `airTempRightP` / `M` | `TEMP_R_UP` / `_DOWN` |
| 22 / 23 | `airLeftSeatBlow` / `airRightSeatBlow` | `SEAT_VENT_L` / `_R` |
| 24 | `airSteer` | `WHEEL_HEAT` |

Index **15** (MAX A/C) is absent from the Wrangler profile because its UI does
not expose it, but it is fitted and functional on this WK2 — consistent with
wiki p3 listing `U_AIR_ACMAX` as fitted but not on the bar.

`Car_0374_PA_Jeep_All` confirms `C_TEMP_LEFT_UP = 4`, `LEFT_DOWN = 5`,
`RIGHT_UP = 20`, `RIGHT_DOWN = 21` as explicit constants.

#### The complete temperature model — corrects section 5.5

From `Car_0374_PA_Jeep_All.updateTemp(boolean auto, int area, int arg, int format)`,
verbatim logic:

```java
if      (arg == -2) tempStr = "LOW";
else if (arg == -3) tempStr = "HIGH";
else if (arg == -1) tempStr = "---";
else if (format == 0) {                      // format is U_AIR_TEMP_UNIT
    if (arg >= 30 && arg <= 128) tempStr = (arg * 5) / 10.0f + "℃";
    else                         tempStr = "---";
} else if (arg >= 30 && arg <= 128) {
    tempStr = arg / 1.0f + "℉";
} else                           tempStr = "---";
```

Four things follow, two of which are bugs in what is already committed:

1. **`-3` is HIGH.** Confirmed by source and by an on-vehicle sweep
   (`…83, 84, −3` climbing). `Temp` did not model this at all, so a HIGH setting
   rendered as a dash. **Add `Temp.Hi`.**
2. **The valid numeric range is `30..128`, not `60..84`.** This WK2 clamps at
   60..84, but the *protocol* accepts 30..128 and the OEM renders anything
   outside it as `---`. `Temp.from` currently returns `Degrees(raw)` for any
   non-negative value, so `0` or `500` would render as a temperature.
   **Restrict `Degrees` to `30..128`.**
3. **`-1` is "unavailable".** Our mapping of `-1` to `Unavailable` was already
   correct.
4. **`U_AIR_TEMP_UNIT` (37) changes the meaning of the raw value.** `0` means
   Celsius, and in that mode the raw value is **half-degrees Celsius**
   (`arg * 5 / 10`); non-zero means Fahrenheit and the raw value is degrees
   directly. This unit reads `1`, so °F is correct today — but the code ignores
   `TEMP_UNIT` entirely, which is a latent bug for anyone who switches the head
   unit to Celsius. **Decode against `TEMP_UNIT`.**

Presented labels: the OEM renders `"LOW"` / `"HIGH"` for this vehicle (other
profiles use `"LO"` / `"HI"`). Matching the OEM is the safer choice for driver
familiarity.

#### Cabin temperature does not exist — settled exhaustively

`FinalCanbus` was searched for `CABIN|INSIDE|INDOOR|IN_CAR|INCAR|ROOM|INNER`:
**no matches.** The only temperature codes in the entire table are
`U_AIR_TEMP_LEFT` (27), `_RIGHT` (28), `U_AIR_TEMP_UNIT` (37),
`U_AIR_REAR_TEMP_LEFT/_RIGHT` (40/41), `U_AIR_TEMP_TYPE` (75) and
`U_EXIST_TEMP_OUT` (1012), plus `U_TEMP_OUT` (40) on MAIN.

So section 5.5's removal of `CABIN` from 1d's header is not a workaround for a
signal we could not find — the signal does not exist.

#### The research table is exact

`FinalCanbus` defines **89** `U_AIR_*` constants, and the repo's extracted
`data/U_AIR_table.txt` matches it entry for entry with zero differences in
either direction. Only the wiki's prose count of "87" is off by two — worth a
one-line fix upstream.

### Safety

All bench work on a **stationary, parked** vehicle. Take a baseline with
`scripts/hu-state.sh` before experimenting and diff back to clean afterwards.
Command **16** leaves this vehicle with no way to change climate until sent again.
Commands **12** and **15** toggle into each other and take several rounds to
unwind through `cmd()`; tap injection on the OEM airflow button
(`input tap 545 1793`) clears the state in one action.

Restoring `com.syu.air` after a disable needs **both** `pm enable com.syu.air`
**and** `am start-service -n com.syu.air/.AirService` — `pm enable` alone leaves
an enabled package with no bar, since it only self-starts on `BOOT_COMPLETED`.

---

## 12. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| ~~2032 cannot be positioned over the nav-bar region~~ | — | **RETIRED 2026-09-08** — measured working. Section 3 |
| ~~Overlay does not consume touches~~ | — | **RETIRED 2026-09-08** — measured fully consumed, zero leakage. Section 3 |
| Our service dies while `com.syu.air` is disabled | **No climate control at all** in a vehicle with no physical HVAC controls; module holds last state | The reason design rule 6 exists. Since items 1–2 passed, we never need to disable it — v1 keeps it running and simply covers it |
| ~~`U_TEMP_OUT` packing undecodable~~ | — | **RETIRED 2026-09-08** — decoded from a driving capture. Section 5.3 |
| The accessibility service is disabled by the user or an OS update | Bar disappears | `com.syu.air` still underneath and functional, so climate is never lost. Detect and prompt on next app launch |
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
