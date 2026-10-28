# Rear climate page (PA family) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A car on the `Car_0374_PA_Jeep_Wrangler` profile whose driver says it has rear climate gets a FRONT | REAR toggle in the climate window and a REAR page that drives the rear zone. Every other car, and a PA car answered "no", is unchanged.

**Architecture:** Rear commands are `Command` entries on module 7, **code 8**, framed `{n}`; the front stays code 6, `{1, n}`. Their numbers come from `com.syu.canbus`, not `com.syu.air`, so `VehicleCatalog` adds them to the Wrangler profile's table in Kotlin rather than through the generated `vehicles.tsv`. A new `Question.REAR_CLIMATE` gates them. Rear status is seven new module-7 `Signal`s. `:ui` gets a page toggle in `PanelHeader` and a rear page built from the existing zone, fan, airflow and mode-tile pieces.

**Tech Stack:** Kotlin 2.3 / JVM 17, Jetpack Compose (foundation only), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-26-multi-vehicle-design.md` §5 and §7 ("Rear"). **Evidence:** `docs/captures/2026-10-28-pa-rear-climate.md`.

## Global Constraints

- WK2 regression gate: with the rear answer no (or unanswered), id 2687350 gives today's bar, window, header and commands exactly.
- Rear numbers (capture doc): power 16, AUTO 2, temp + 4, temp - 5, fan + 6, fan - 7, face 8, face+feet 9, feet 10, lock 32. Reads: temp 40, power 42, AUTO 43, fan 44, face 46, feet 47, lock 67.
- Rear commands exist only for profile `Car_0374_PA_Jeep_Wrangler` (the 58 ids FYT opens its rear-capable screen for).
- **The rear question does not block the bar.** An existing setup has the four front answers and no rear answer; it must stay Ready, with rear off. New setups are asked it, last. It shows as a switch on the "your car" screen.
- Header: large FRONT | REAR segmented toggle, current half filled; tapping the current half does nothing; the window always opens on FRONT; cars without rear show no toggle and an unchanged header.
- REAR page: REAR ZONE (one temperature, fan, three airflow tiles) and REAR ON / REAR AUTO / LOCK REAR tiles. No HOLD · OFF footer on REAR (that is front power).
- 96dp floor for every target. Kotlin source ASCII. Setup copy in `strings.xml`.
- Commits as the owner, no AI attribution. Never commit `gradle.properties` or APKs.
- Tests: `./gradlew :bus:testDebugUnitTest :ui:testDebugUnitTest`; build `./gradlew assembleDebug`.

## Review Focus

- An existing install (four answers, no rear answer) must resolve Ready with no rear: tested in Task 2.
- A rear command must never go out as `{1, n}` on code 6, and a front one never as `{n}`: tested in Task 1.
- Rear indices collide with front indices (2, 4, 5, ...): uniqueness must be per code, tested in Task 1.
- The Renegade profile (`Car_0374_PA_Jeep_All`) must not be asked the rear question: tested in Task 2.
- Rear signals that a car never reports keep the refresh retry running to its ~8s cap; accepted (bounded, logged), not tested.

---

### Task 1: Rear commands and framing (`:bus`)

**Files:** `Command.kt`, `CommandTable.kt`, `VehicleCatalog.kt`; tests `CommandTableTest.kt`, `VehicleCatalogTest.kt`.

- [ ] Tests: `frame(REAR_TEMP_UP)` on the Durango 13-18 H table (1442166) is `{4}` and goes out on code 8; `frame(TEMP_L_UP)` is still `{1, 4}`; a table with `AUTO=2` and `REAR_AUTO=2` is accepted, two code-6 commands on one index are still refused; 1442166 and 2687350 have all ten rear commands, 374 has none.
- [ ] Run, watch fail.
- [ ] `Command`: `REAR_POWER, REAR_AUTO, REAR_TEMP_UP, REAR_TEMP_DOWN, REAR_FAN_UP, REAR_FAN_DOWN, REAR_FACE, REAR_FACE_FEET, REAR_FEET, REAR_LOCK`, all `(7, 8)`. `CommandTable`: uniqueness per `code`. `frame`: code 6 -> `{1, i}`, code 8 -> `{i}`. `VehicleCatalog`: `REAR_INDICES` added to tables of profile `Car_0374_PA_Jeep_Wrangler`.
- [ ] Run green, commit.

### Task 2: The rear question (`:bus`)

**Files:** `Features.kt`, `VehicleStatus.kt`; tests `FeaturesTest.kt`, `VehicleStatusTest.kt`.

- [ ] Tests: `questionsFor` the WK2 ends with `REAR_CLIMATE`, the Renegade's does not include it; four front answers and no rear answer resolve `Ready` with `hasRear == false`; rear answered yes gives `hasRear` and `has(REAR_AUTO)`; `pendingQuestions` lists the unanswered rear question on that Ready.
- [ ] Run, watch fail.
- [ ] `Question.REAR_CLIMATE(needs = all ten, blocksBar = false)`; `resolveVehicle` requires only `blocksBar` questions; `Features.hasRear`; `pendingQuestions(saved, vehicle)` helper.
- [ ] Run green, commit.

### Task 3: Rear status (`:bus`)

**Files:** `Signal.kt`, `ClimateState.kt`, `FakeVehicleBus.kt`; tests `ClimateStateTest.kt`, `FakeVehicleBusTest.kt`.

- [ ] Tests: codes 40/42/43/44/46/47/67 map to the rear signals; `rearTemp` decodes 72 and the -2 LOW sentinel; `rearAirflow` is FACE / FEET / FACE_FEET / NONE from 46/47 and UNKNOWN when absent; the fake steps rear temp and fan, toggles rear power/AUTO/lock and sets the three airflows.
- [ ] Run, watch fail.
- [ ] Signals `REAR_TEMP(7,40) REAR_POWER(7,42) REAR_AUTO(7,43) REAR_FAN(7,44) REAR_BLOW_BODY(7,46) REAR_BLOW_FOOT(7,47) REAR_LOCK(7,67)`; derived `rearTemp, rearFan, rearAirflow, rearPowerOn, rearAutoOn, rearLockOn`; fake reducers plus `REAR_BASELINE` for the harness.
- [ ] Run green, commit.

### Task 4: The REAR page (`:ui`)

**Files:** `PanelLayout.kt`, `PanelHeader.kt`, `ClimatePanel.kt`, `PanelZones.kt`, `PanelAirflow.kt`, `PanelMode.kt`; test `PanelLayoutTest.kt`.

- [ ] Tests: `rearModeRows` is `[[REAR_POWER, REAR_AUTO, REAR_LOCK]]` with rear yes and empty with rear no; `rearAirflowModes` is FACE, FACE_FEET, FEET mapped to `REAR_FACE`, `REAR_FACE_FEET`, `REAR_FEET`; the WK2 all-yes front sections are unchanged.
- [ ] Run, watch fail.
- [ ] Header toggle (only when `features.hasRear`), page state remembered per open starting at FRONT, REAR page sections; zone label, airflow modes and mode rows become parameters with today's values as defaults.
- [ ] Run green; harness preset "Durango 13-18 H, rear yes"; screenshot both pages on the emulator; commit.

### Task 5: Setup and release

**Files:** `SetupActivity.kt`, `strings.xml`, `README.md`.

- [ ] Rear question screen with no bar preview (it does not change the bar); asked after the front questions in the walkthrough even once Ready; switch on the "your car" screen.
- [ ] `./gradlew test assembleDebug`; on the WK2: existing setup keeps the bar after install, the switch shows and is off, FRONT window unchanged; flipping it on shows the toggle and REAR page (no rear commands tapped on a car without rear climate).
- [ ] README note, PR, then a pre-release for the tester once merged.
