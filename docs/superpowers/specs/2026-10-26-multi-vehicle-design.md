# Multi-vehicle support: detect the car, ask once, draw only what it has

**Status:** design, approved in conversation 2026-10-26; not yet planned.
**Builds on:** `2026-09-08-wk2-climate-design.md` (§6 bar, §7 screen 1d) and
`2026-09-14-seat-buttons-design.md`. Data: `docs/captures/2026-09-09-air-profile-extraction.md`,
`docs/data/air-command-tables.json`, `docs/data/air-canbus-ids.json`.

## 1. Intent

Today the app is a WK2 Grand Cherokee app: one hand-swept command table in
`Command.kt`, one fixed bar, one fixed climate window.

The goal, in the owner's words: *anyone who has this radio and a supported,
defined canbus architecture installs it, and it detects what is supported and
renders a proper HVAC bar, along with the correct options on the main window.*

Design for the heaviest vehicle, then define how the bar and window look as
features drop away. The WK2 is the reference vehicle and the only one we can
test on hardware.

## 2. Goals, non-goals, anti-goals

**Goals**

- Pick the command table automatically from the head unit's canbus id.
- Ask the driver a few one-time yes/no questions about what the car has,
  editable later.
- Render the bar and the climate window from (what we can send) ∩ (what the
  driver says the car has).
- MAX A/C on every car that has at least A/C and a fan, native or synthesized.
- Design a rear zone and rear seat controls on a separate rear page, for
  cars whose profile declares them. Built when a supported car needs it;
  none does today (§4).

**Non-goals**

- Mining other apps' configuration (e.g. `com.syu.canbus`'s
  `ConstAllAirDara`, its `U_AIR_HAVE_REAR` flags) to answer "what does the car
  have". The setup questions answer that. Revisit later if questions prove
  annoying.
- A vehicle picker. The head unit's canbus setting is the source of truth.
- WK2 rear heated seats. Measured 2026-10-26: read codes 88-91 never changed
  while the owner cycled both rear seats; no vendor app has a command for
  them on this vehicle. They are not on the canbus.
- Changing how the seat buttons look.
- Right-hand drive (owner: doesn't matter). The driver is on the left:
  DRIVER = `TEMP_L` / `SEAT_*_L`, and the AUTO cell and heated-wheel slot
  stay on the left.

**Anti-goals** (failure even if it technically works)

- Any visible or behavioural change on the WK2, other than the new setup
  questions and the synthesized-MAX path it does not use.
- Sending an index that did not come from a trusted table (§4). A mis-sent
  index on a car with no physical HVAC controls can have no undo.
- Greyed-out or disabled controls. *Controls for absent hardware are omitted,
  not disabled* (base spec §7) holds for every vehicle.
- Pooling command tables across profiles, including siblings that share a
  base id. Measured: 157 feature-slots agree, 163 conflict.

## 3. Detecting the car

- Module 7, read code **1000**, `ints[0]`, already registered as
  `Signal.CANBUS_ID` and decoded by `CanbusId`. Measured on the WK2: arrives
  ~40 ms after registration on a cold service start, unprompted, value
  `2687350` (`CAR_PA_GrandCherokee_14_22`, base 374, variant 41).
- The id maps to a vendor profile class through `docs/data/air-canbus-ids.json`
  (1200 ids → 222 classes). Exact id match only; never fall back to the base
  id or a sibling.
- **Caching (my call):** persist the last id seen. On start, use the cached id
  immediately so the bar comes up without waiting; when code 1000 arrives,
  compare. A different id means the head unit was reconfigured: discard the
  setup answers and run setup again. Not yet observed: the ignition-off boot,
  where `com.syu.ms` may come up after us. With no cached id and no code 1000,
  draw nothing (same as an unknown car).
- **Unknown car:** an id with no trusted table, or no id at all. Stay out of
  the way: no bar, nothing sent, the factory bar stays. The setup screen says
  so and shows id, base and variant so they can be reported.

## 4. What we can send: the command table

A vehicle's table maps each semantic control (AC, AUTO, TEMP_L_UP, …) to its
module-7 index. Sources, in priority order:

1. **Extracted profile tables**, regenerated from the `com.syu.air` APK
   (below). Accurate when they declare something (18/18 against the WK2),
   silent about the rest. Trust what is declared; infer nothing from what is
   omitted.
2. **Verified additions**, hand-swept on a real vehicle. Today there is one:
   `MAX_AC` = 15 on the Grand Cherokee, which the vehicle accepts and its
   profile does not declare.

A control absent from the car's table does not exist for that car, whatever
the setup answers say.

### Scope: the PA family only (owner's decision, 2026-10-26)

Every profile has its own `sendCmd`, and they turn an index into a bus frame
in different ways. Disassembled across all 228:

| send format | profiles |
|---|---|
| exactly one `sendInt(7, 6, {1, cmd})`, nothing else | **2** |
| a press frame, then a `postDelayed` release frame | 122 |
| one frame with another command code (0, 2, 3, 22, 24, …) | ~60 |
| several frames, or a branch on `mCurrCanbus` | ~35 |

Our bus speaks only the first, which is the one verified on the WK2. So a
profile is **trusted** only if its `sendCmd` body is exactly that: a single
`RemoteTools.sendInt` with module const 7, code const 6, and a two-element
int array whose element 0 is const 1 and element 1 is the method's argument,
with no `postDelayed` and no branch. Two profiles pass:
`Car_0374_PA_Jeep_Wrangler` (58 ids) and `Car_0374_PA_Jeep_All` (2 ids).
Every other profile is treated as an unknown car. Supporting other send
formats is a separate, later piece of work, ideally with a tester who owns
such a car. The public list is wiki page 14, *Supported Vehicles*.

Two further checks apply to trusted tables and are kept because they are
cheap: no two controls may share an index, and the table must meet the
**minimum (owner's decision)**: `TEMP_L_UP` and `TEMP_L_DOWN`, plus `AC` or
`FAN_UP`/`FAN_DOWN`. Below that, our bar would take controls away from the
driver, so the car is treated as unknown. Both PA profiles pass.

**Verified additions are keyed by id, not by class (my call).** `MAX_AC` = 15
applies to `CAR_PA_GrandCherokee_14_22` (2687350) and
`CAR_PA_GrandCherokee_14_22_Amp` (5308790) only. The other 56 Wrangler-class
ids get synthesized MAX (§8) instead of a blind index 15.

**Airflow 8-11 comes from the profile, not a verified addition.** The
Wrangler class's `airMode` method sends literal 8, 9, 10 and 11, and the WK2
sweep established their meanings (FACE, FACE+FEET, FEET, FEET+GLASS) through
that same class code. They apply to every id of the class. Profiles without
per-mode setters get no AIRFLOW section (my call: the tiles promise direct
selection, which a mode-cycle command cannot give).

**Rear zone: designed, not built.** Neither trusted profile declares rear
commands, so no supported car has a rear page. §7's rear design stands for
when one does.

**Where the extraction input comes from.** The jadx output behind the
committed JSON is gone, and running jadx on this machine was refused as
external code. Instead, a stdlib-only DEX parser was written on 2026-10-26
(`dexconst.py`: static int values, const→iput/sput assignments, literal
`sendCmd(n)` calls, per class → JSON; `dumpmethod.py`: a crude disassembly of
one method, used for the send-format survey above). It runs on the
`com.syu.air` APK pulled from the head unit with `adb pull`. The extractor
moves to it and reads the APK directly, so the data is reproducible. The
committed JSON has no `constants` field, so it came from an earlier script
version; regenerate it rather than patching it.

**Code shape (left to the builder, constrained):** `Command` currently bakes
WK2 indices into enum constants, and the UI takes `onCommand: (Command) -> Unit`.
Split the semantic control from the index so the UI keeps speaking in
controls and a per-vehicle table resolves the payload at send time. Volume
commands (module 4) are not per-vehicle and stay as they are.
`CLIMATE_POWER` stays behind hold-to-confirm on every vehicle.

## 5. What the car has: setup questions

The table says what we **can** send. The driver says what the car **has**.

- Asked once, in `SetupActivity` (the launcher activity that already exists
  to enable the accessibility service), after the service is enabled and the
  car is identified.
- **Only asked for controls the table contains.** If the table has no seat
  cooling commands, the cooled-seats question is never asked.
- Questions, each shown only when the table supports it:
  - Heated front seats? (`SEAT_HEAT_L/R`)
  - Cooled front seats? (`SEAT_VENT_L/R`)
  - Heated steering wheel? (`WHEEL_HEAT`)
  - Separate driver and passenger temperatures? (`TEMP_R_UP/DOWN`)
  - Rear climate controls? (any rear-zone command)
  - Heated rear seats? (rear seat heat commands; none known today)
- Seat features are asked per pair, not per seat (my call).
- Everything else the table declares (A/C, fan, recirc, defrost, AUTO, sync,
  airflow) is assumed fitted, no question (my call). The questions are for
  trim-level options.

**Screens** (mockups: `.superpowers/brainstorm/812-1793058654/content/setup-flow.html`,
not committed):

1. *We found your vehicle.* A readable name derived from the vendor constant
   (`CAR_PA_GrandCherokee_14_22` → "Grand Cherokee 2014–2022"), and the
   protocol and id in small mono type. CONTINUE. If the name is wrong, the
   head unit's canbus setting is wrong; we don't offer a fix here. Ids whose
   constant is unknown (`"const": null` in the data) show the profile class
   and id instead (my call).
2. One question per screen, big YES / NO, "question n of m", and a small
   preview of the bar that updates as answers are given.
3. Reopening the app later shows the answers as switches, changeable any
   time; the bar updates immediately. ASK ME AGAIN reruns the questions.
4. Unknown car: *This car isn't supported yet*, the id/base/variant, CLOSE.

Before setup is complete, the bar is not drawn (my call — drawing with
guessed capabilities would show controls the car may not have).

**The WK2 answers** are heated seats yes, cooled seats yes, heated wheel yes,
separate temperatures yes, which reproduces today's bar and window exactly.

## 6. The bar

Mockup: `bar-ladder.html`. Approved.

- The top row is two halves, each **384dp** and each directly above its own
  temperature. A half is either *seat slot 128 + main cell 256* or *main cell
  384*. The centre line never moves. For a given car the layout is fixed, so
  every cell keeps a constant coordinate and can still be found by feel
  (`BarTopRow`'s existing principle).
- Driver half main cell: **AUTO**, or **A/C** if the car has no AUTO (my
  guess, not objected to). Passenger half main cell: **CLIMATE**, always —
  it opens the window.
- Seat slot, per side, exists if that side has anything: seat heat, seat cool,
  or (driver only) heated wheel.
  - **Two or more controls:** today's seat button and seat menu, exactly as
    they are. The menu lists only the rows that side has.
  - **Exactly one control:** the same seat button, but a tap sends that
    control's command directly, with no menu, like a factory switch. Seat
    commands are already single step presses (the menu rows send one
    `SEAT_HEAT_*` per tap), so the level order is whatever the car steps
    through. Wheel only: a wheel on/off button in the driver slot.
  - **Nothing:** the slot is removed and that half's main cell becomes 384.
- **Seat buttons keep their current glyphs, pips and OFF / `—` states.** The
  owner rejected any new icon treatment.
- Single zone: one temperature spanning the full 768dp centre column.
- Ladder, heaviest to lightest (all approved): WK2; heated seats + wheel with
  no cooling (same look, passenger skips the menu); wheel only (`WHEEL |
  AUTO | CLIMATE 384`); no comfort (`AUTO 384 | CLIMATE 384`); single zone, no
  AUTO, no comfort (`A/C 384 | CLIMATE 384`, one temperature).

## 7. The climate window (screen 1d)

Mockups: `panel-ladder.html`, `panel-header.html`. Approved.

- Same rule: omit what the car lacks; the rest closes up.
- **Zones:** DRIVER / PASSENGER, or one TEMPERATURE zone at full width.
- **FAN:** the AUTO label only if the car has AUTO.
- **AIRFLOW:** only with idempotent setters (§4).
- **MODE:** row one AUTO, A/C, RECIRC, MAX A/C; row two FRONT DEF, REAR DEF,
  SYNC. Absent tiles are dropped and the remaining tiles in a row share its
  width evenly. SYNC only on dual-zone cars.
- **COMFORT:** only the rows the car has (heat pair, cool pair, wheel). No
  rows, no section.
- **Height (my call; owner said it looks good):** the window shrinks to fit
  its content, so a lighter car covers less of the app beneath. It still
  slides up from the bar as today. **Deferred to a later plan:** the panel
  window is full app height and consumes every touch, so a shorter page means
  a shorter window anchored above the bar, not just a shorter layout. Until
  then omitted sections leave space above the footer, which is today's
  behaviour for the Spacer.
- **Rear:** cars with a rear zone or rear seat controls get a large
  **FRONT | REAR** segmented toggle in the header, current page filled in
  (owner's choice over a single flip button and over a bottom placement). The
  header grows ~60dp on those cars only. Tapping the half you are on does
  nothing. FRONT is exactly the front window; REAR holds a REAR ZONE section
  (temperature ±, fan ±, REAR AUTO, REAR ON, LOCK REAR — each only if
  declared) and a REAR SEATS section. Cars with nothing in the rear, including
  the WK2, show no toggle and an unchanged header.
- Which page the window opens on: FRONT, always (my call).

Rear read codes exist in `FinalCanbus` (rear temps 40/41, `REAR_POWER` 42,
`REAR_AUTO` 43, rear seat heat 88/89, rear seat vent 90/91) and are not yet in
`Signal`.

## 8. MAX A/C

- **Native** when the table has `MAX_AC`: as today.
- **Synthesized** otherwise, when the car has at least A/C and fan: fan to
  maximum, airflow to FACE (if idempotent airflow exists), A/C on, both
  temperatures to their lowest, recirc on. Each step is driven from live state
  (send a toggle only if it is not already in the target state; step fan and
  temperatures until the reported value stops changing or hits its limit).
- **Pressing again restores exactly the prior state** (owner's decision):
  AUTO, fan level, airflow, A/C, recirc, both temperatures.
- **Leaving the MAX state ends MAX without restoring** (my call): the driver
  has taken over. Once the sequence has settled, if any value MAX set (fan,
  airflow, A/C, recirc, either temperature) is reported different from its
  MAX target, MAX ends and the snapshot is dropped. This one rule covers a
  tap in our app, a physical knob on cars that have them, and the car's own
  logic, and needs no tracking of which change was ours. Changes while the
  sequence is still running are its own steps and are ignored.
- **The snapshot persists across restarts** (my call), so an ignition cycle
  mid-MAX can still restore. On start, the same rule applies: if the car no
  longer reports the MAX targets, the snapshot is dropped rather than
  restored over settings the driver has since changed.
- The tile shows active while synthesized MAX is engaged, from our own state
  (there is no `U_AIR_ACMAX` reading to drive it).
- Composite features in general (e.g. a synthesized max defrost) follow the
  same pattern but are out of scope here.

## 9. Testing

- Unit tests (JVM, `:bus` style): id → table lookup including unknown and
  null-const ids; question list from a table; capabilities = table ∩ answers;
  bar layout derivation for every ladder rung (extend `BarGeometryTest`);
  window section/tile derivation; synthesized MAX sequence and restore
  against `FakeVehicleBus`, including exit-on-touch and persisted snapshot.
- **Regression gate:** the WK2 id plus the WK2 answers must produce today's
  bar and window exactly; existing screenshots and harness captures stay
  valid.
- Harness: a vehicle picker that renders each ladder rung, since only the
  WK2 exists on hardware.
- On the WK2: setup flow end to end; bar and window unchanged afterwards.

## 10. Left to the builder

- The persistence mechanism for the cached id, answers and MAX snapshot.
- The readable-name rule for vendor constants.
- Exact header and toggle dimensions within the 96dp minimum target.
- How rear signals and controls are named in `Signal` and the control enum.
