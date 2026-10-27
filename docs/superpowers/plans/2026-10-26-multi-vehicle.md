# Multi-vehicle support (PA family) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app identifies the car from canbus id (module 7, code 1000), loads that car's command table, asks the driver a few one-time questions, and draws a bar and climate window containing only what the car has. Scope is the PA canbus family (60 ids), and the WK2 behaves exactly as today.

**Architecture:** A Python extractor reads the `com.syu.air` APK and emits `vehicles.tsv`, a Java resource in `:bus` that lists the trusted profiles' tables and their ids. In `:bus`, `VehicleCatalog` loads it, `Features` combines a table with the setup answers, and `resolveVehicle` turns (id, answers) into a `VehicleStatus`. `:ui` derives its layouts from `Features` through pure functions. `:app` persists the id and answers, gates the bar on `Ready`, hands the table to `SyuVehicleBus`, and runs setup in `SetupActivity`.

**Tech Stack:** Kotlin 2.3 / JVM 17, Jetpack Compose (foundation only), JUnit 4, kotlinx-coroutines. Python 3 standard library for tools.

**Spec:** `docs/superpowers/specs/2026-10-26-multi-vehicle-design.md`

**Not in this plan:** synthesized MAX A/C (spec §8), shrinking the panel window (spec §7, deferred), rear zone (spec §4: no supported car has one). Until synthesized MAX lands, the MAX A/C tile appears only where `MAX_AC` is in the table, which today means the two Grand Cherokee ids.

## Global Constraints

- WK2 regression gate: id 2687350 with every answer YES must produce today's bar and window exactly — same cells, widths, rows, tiles, and commands.
- Only the send format `sendInt(7, 6, {1, index})` exists. A climate command not in the vehicle's table is never sent.
- Minimum table (owner): `TEMP_L_UP` and `TEMP_L_DOWN`, plus `AC` or both `FAN_UP` and `FAN_DOWN`.
- 96dp is the floor for every tappable region (`Dimens.minTarget`).
- Controls for absent hardware are omitted, never disabled or greyed.
- Seat buttons keep their current glyphs, pips and `OFF` / `—` states.
- `MAX_AC` = 15 is a verified addition for ids 2687350 and 5308790 only. Airflow 8/9/10/11 = FACE / FACE_FEET / FEET / FEET_GLASS applies to class `Car_0374_PA_Jeep_Wrangler`.
- No new Gradle dependencies. Tools use only the Python standard library and run with `python -I`.
- Kotlin source stays ASCII (use `–` for an en dash). User-facing setup copy lives in `app/src/main/res/values/strings.xml`.
- Commit messages are written as the owner. No "Claude", no AI attribution, no `Co-Authored-By` lines.
- Never commit `gradle.properties` (it holds the owner's unrelated local edit). Never commit APKs.
- Tests: `./gradlew :bus:testDebugUnitTest`, `./gradlew :ui:testDebugUnitTest`; full build `./gradlew assembleDebug`.

## Review Focus

1. **The cached id exists but code 1000 never arrives** (ignition-off boot, `com.syu.ms` late). The bar comes up from the cache with that car's table, and nothing waits on the live id. → Task 4 test `cached id is used before any live id`.
2. **The live id differs from the cached one** (the head unit's canbus setting was changed). The answers are discarded, the bar is pulled, and nothing is sent with the old table. → Task 4 test `a different live id discards the answers`; Task 8 step: `bus.table` is cleared whenever status is not Ready.
3. **The driver answers YES to something the table lacks.** This can't happen through the questions, but stored answers can outlive a table change: the control stays absent. → Task 3 test `a yes answer cannot add a command the table lacks`.
4. **A climate tap arrives before any table is set** (bar from a previous status, a tap in flight). The command is dropped and logged, never sent with a guessed index. → Task 2 test `no table means no frame`.
5. **Setup abandoned midway** (two of four answers given, then the app is closed). The status stays NeedsSetup, there's no bar, and reopening resumes at the first unanswered question. → Task 4 test `partial answers are not Ready`; Task 9 resumes from `status.questions.first { it !in answers }`.

---

### Task 1: Extractor reads the APK and emits `vehicles.tsv`

**Files:**
- Create: `tools/dexlib.py` (DEX reading and disassembly, moved from the session scratchpad: `...\scratchpad\scripts\dexconst_lib.py` and `dumpmethod.py`)
- Create: `tools/extract-vehicles.py`
- Create: `tools/test_extract_vehicles.py`
- Delete: `tools/extract-air-profiles.py` (its jadx input no longer exists; its `SYN` and `METHOD_CANON` maps move into the new script unchanged)
- Regenerate: `docs/data/air-command-tables.json`
- Create: `bus/src/main/resources/com/wk2/climate/bus/vehicles.tsv`
- Modify: `docs/captures/2026-09-09-air-profile-extraction.md` (the reproduce command; a short section on the send-format survey and its table from the spec §4)

**Interfaces:**
- Produces `vehicles.tsv`, UTF-8, `#` comment lines, tab-separated records:
  - `P<TAB><profile><TAB><CONTROL>=<index>,...` with controls named exactly like `Command` entries and sorted by index.
  - `V<TAB><id><TAB><profile><TAB><vendor const or -><TAB><extra CONTROL=index list or ->`
  - Only profiles that are trusted and supported get a `P` line; `V` lines appear only for those profiles.
- Inputs: the `com.syu.air` APK path, plus `docs/data/air-canbus-ids.json` (the id→class map, kept as is).

- [ ] **Step 1: Copy the parser in.** Move `Dex`, `uleb`, `W`, the class walk (static values, const→iput/sput, literal `sendCmd(n)` per method) and the `dis()` disassembler into `tools/dexlib.py`. Add `def method_code(dex, class_desc, method_name) -> list[Insn]` returning decoded instructions (`Insn(op:int, regs:tuple, const:int|None, ref:str|None)`).

- [ ] **Step 2: Write the failing unit tests** in `tools/test_extract_vehicles.py` for the pure functions:

```python
def test_table_from_record_maps_synonyms_and_method_literals(self):
    rec = {"iput": [["<init>", "C_SEAT_HEAT_LEFT", 13]], "sendcmd": [["airAc", 1], ["airTempLeftP", 4]]}
    self.assertEqual(table_from_record(rec), {"SEAT_HEAT_L": 13, "AC": 1, "TEMP_L_UP": 4})

def test_supported_needs_driver_temps_and_ac_or_fan(self):
    self.assertTrue(supported({"TEMP_L_UP": 4, "TEMP_L_DOWN": 5, "AC": 1}))
    self.assertTrue(supported({"TEMP_L_UP": 4, "TEMP_L_DOWN": 5, "FAN_UP": 6, "FAN_DOWN": 7}))
    self.assertFalse(supported({"TEMP_L_UP": 4, "TEMP_L_DOWN": 5, "FAN_UP": 6}))
    self.assertFalse(supported({"AC": 1, "AUTO": 2}))

def test_duplicate_indices_are_rejected(self):
    self.assertTrue(has_duplicates({"AC": 1, "AUTO": 1}))

def test_wk2_send_shape_is_recognised(self):
    # const/4 v5,1; iget mTools; const/4 v1,7; const/4 v2,6; const/4 v3,2; new-array;
    # const/4 v4,0; aput v5,[v4]; aput v7,[v5]; invoke sendInt v0,v1,v2,v3; return-void
    self.assertEqual(classify_send(WK2_SENDCMD_INSNS, arg_reg=7), "7/6/{1,cmd}")

def test_press_release_shape_is_not(self):
    self.assertNotEqual(classify_send(KMR_SENDCMD_INSNS, arg_reg=7), "7/6/{1,cmd}")

def test_tsv_round_trip_lines(self):
    self.assertEqual(render_v(2687350, "Car_0374_PA_Jeep_Wrangler", "CAR_PA_GrandCherokee_14_22", {"MAX_AC": 15}),
                     "V\t2687350\tCar_0374_PA_Jeep_Wrangler\tCAR_PA_GrandCherokee_14_22\tMAX_AC=15")
```

`WK2_SENDCMD_INSNS` and `KMR_SENDCMD_INSNS` are hand-written `Insn` lists transcribed from the disassembly in the spec survey (Wrangler: one `sendInt` with consts 7 and 6 and array `[1, arg]`; KMR: code 25, array `[arg, 1]`, then `postDelayed`).

- [ ] **Step 3: Run them to verify they fail.** Run `python -I -m unittest discover -s tools -p "test_*.py"`. Expected: ImportError / NameError.

- [ ] **Step 4: Implement** `table_from_record(rec) -> dict`, `supported(table) -> bool`, `has_duplicates(table) -> bool`, `classify_send(insns, arg_reg) -> str` (returns `"7/6/{1,cmd}"` only for: exactly one invoke of `Lcom/syu/remote/RemoteTools;.sendInt`, its module and code registers holding consts 7 and 6, a 2-element array whose index-0 element is const 1 and index-1 element is `arg_reg`, no `postDelayed` invoke, no switch opcodes `0x2b`/`0x2c`, and no read of `mCurrCanbus`; otherwise a descriptive string), and `render_p` / `render_v` in `tools/extract-vehicles.py`. `arg_reg` is `registers_size - 1` for the one-int-argument instance method.

  `main(apk, ids_json, out_json, out_tsv)`:
  - For every `Lcom/syu/air/canbus/Car_*;` class, build the table, classify `sendCmd`, and set trusted = shape match and no duplicates, and ok = trusted and supported.
  - Apply the verified additions held in the script:
    ```python
    CLASS_ADDITIONS = {"Car_0374_PA_Jeep_Wrangler": {"AIRFLOW_FACE": 8, "AIRFLOW_FACE_FEET": 9, "AIRFLOW_FEET": 10, "AIRFLOW_FEET_GLASS": 11}}
    ID_ADDITIONS = {2687350: {"MAX_AC": 15}, 5308790: {"MAX_AC": 15}}
    ```
    Assert that each class addition's literals appear in that class's `airMode` `sendcmd` entries, and fail loudly if they don't. Class additions merge into the `P` line; id additions go in the `V` extra column.
  - Write the JSON with `profile, table, send_format, trusted, supported` per profile, and the TSV with a header comment naming the APK's `versionName` (from `AndroidManifest.xml` is not parseable in stdlib; use the APK file name and SHA-256 instead).

- [ ] **Step 5: Run the unit tests.** Expected: all pass.

- [ ] **Step 6: Generate from the real APK.** Pull it if needed: `adb -s <device> shell pm path com.syu.air`, then `adb pull <path> <scratch>/air.apk` (keep APKs out of the repo). Run `python -I tools/extract-vehicles.py <scratch>/air.apk docs/data/air-canbus-ids.json docs/data/air-command-tables.json bus/src/main/resources/com/wk2/climate/bus/vehicles.tsv`.
  Expected stdout includes `trusted+supported: 2 profiles, 60 ids`. `vehicles.tsv` has exactly 2 `P` lines, and the Wrangler `P` line reads `AC=1,AUTO=2,RECIRC=3,TEMP_L_UP=4,TEMP_L_DOWN=5,FAN_UP=6,FAN_DOWN=7,AIRFLOW_FACE=8,AIRFLOW_FACE_FEET=9,AIRFLOW_FEET=10,AIRFLOW_FEET_GLASS=11,FRONT_DEFROST=12,SYNC=13,REAR_DEFROST=14,CLIMATE_POWER=16,SEAT_HEAT_L=17,SEAT_HEAT_R=18,TEMP_R_UP=20,TEMP_R_DOWN=21,SEAT_VENT_L=22,SEAT_VENT_R=23,WHEEL_HEAT=24`.

- [ ] **Step 7: Commit**

```bash
git add tools/ docs/data/air-command-tables.json bus/src/main/resources/com/wk2/climate/bus/vehicles.tsv docs/captures/2026-09-09-air-profile-extraction.md
git commit -m "tools: extract vehicle tables straight from the air APK; emit vehicles.tsv for the PA family"
```

---

### Task 2: Per-vehicle command tables in `:bus`

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/CommandTable.kt`
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/VehicleCatalog.kt`
- Modify: `bus/src/main/kotlin/com/wk2/climate/bus/Command.kt` (climate entries lose their index; `vehicleIndex` removed; KDoc says indices come from `CommandTable`)
- Modify: `bus/src/main/kotlin/com/wk2/climate/bus/SyuVehicleBus.kt:217-239` (`send` resolves through `table`)
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/CommandTableTest.kt`, `bus/src/test/kotlin/com/wk2/climate/bus/VehicleCatalogTest.kt`
- Modify test: `bus/src/test/kotlin/com/wk2/climate/bus/SignalCommandTableTest.kt` (the index assertions move to `VehicleCatalogTest`; the volume and destructive assertions stay)

**Interfaces:**
- Produces:
  - `enum class Command(val module: Int, val code: Int, val payload: IntArray = IntArray(0), val destructive: Boolean = false)`. Climate entries are `AC(7, 6)` and so on; volume entries are unchanged.
  - `class CommandTable(indices: Map<Command, Int>)` with `operator fun get(command: Command): Int?`, `operator fun contains(command: Command): Boolean`, `val commands: Set<Command>`. The `init` block requires every key on module 7 and distinct values.
  - `fun CommandTable?.frame(command: Command): IntArray?`: `{1, index}` for climate commands in the table, `command.payload` for module 4, and null otherwise.
  - `data class Vehicle(val id: Int, val profile: String, val vendorName: String?, val table: CommandTable)` with `val displayName: String`.
  - `class VehicleCatalog(byId: Map<Int, Vehicle>)` with `operator fun get(id: Int): Vehicle?` and `val ids: Set<Int>`; `companion object { fun parse(text: String): VehicleCatalog; fun bundled(): VehicleCatalog }`. `bundled()` reads `VehicleCatalog::class.java.getResourceAsStream("vehicles.tsv")`.
  - `fun vehicleDisplayName(vendorConst: String?, profile: String): String`
  - `SyuVehicleBus`: `@Volatile var table: CommandTable? = null`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
// CommandTableTest
@Test fun `a climate command in the table frames as 1, index`() =
    assertArrayEquals(intArrayOf(1, 4), CommandTable(mapOf(Command.TEMP_L_UP to 4)).frame(Command.TEMP_L_UP))
@Test fun `a climate command missing from the table has no frame`() =
    assertNull(CommandTable(mapOf(Command.AC to 1)).frame(Command.MAX_AC))
@Test fun `no table means no frame`() = assertNull((null as CommandTable?).frame(Command.AC))
@Test fun `volume frames without a table`() = assertArrayEquals(intArrayOf(-1), (null as CommandTable?).frame(Command.VOL_UP))
@Test(expected = IllegalArgumentException::class) fun `two commands on one index are refused`() { CommandTable(mapOf(Command.AC to 1, Command.AUTO to 1)) }
@Test(expected = IllegalArgumentException::class) fun `volume cannot be in a table`() { CommandTable(mapOf(Command.VOL_UP to 1)) }

// VehicleCatalogTest -- against the bundled resource
private val catalog = VehicleCatalog.bundled()
@Test fun `the WK2 table is the hand-swept one`() = assertEquals(
    mapOf(AC to 1, AUTO to 2, RECIRC to 3, TEMP_L_UP to 4, TEMP_L_DOWN to 5, FAN_UP to 6, FAN_DOWN to 7,
          AIRFLOW_FACE to 8, AIRFLOW_FACE_FEET to 9, AIRFLOW_FEET to 10, AIRFLOW_FEET_GLASS to 11,
          FRONT_DEFROST to 12, SYNC to 13, REAR_DEFROST to 14, MAX_AC to 15, CLIMATE_POWER to 16,
          SEAT_HEAT_L to 17, SEAT_HEAT_R to 18, TEMP_R_UP to 20, TEMP_R_DOWN to 21,
          SEAT_VENT_L to 22, SEAT_VENT_R to 23, WHEEL_HEAT to 24),
    catalog[2687350]!!.table.commands.associateWith { catalog[2687350]!!.table[it]!! })
@Test fun `the amplified Grand Cherokee also gets native MAX A-C`() = assertEquals(15, catalog[5308790]!!.table[MAX_AC])
@Test fun `a KL Cherokee shares the class but not index 15`() = assertNull(catalog[2621814]!!.table[MAX_AC])
@Test fun `the bundled catalog is exactly the PA family`() = assertEquals(60, catalog.ids.size)
@Test fun `an unknown id is not in the catalog`() = assertNull(catalog[1234567])
@Test fun `index 19 is never exposed`() = assertTrue(catalog.ids.all { 19 !in catalog[it]!!.table.commands.map { c -> catalog[it]!!.table[c] } })
@Test fun `display names`() {
    assertEquals("Grand Cherokee 2014–2022", vehicleDisplayName("CAR_PA_GrandCherokee_14_22", "x"))
    assertEquals("Grand Cherokee 2014–2022, amplified audio", vehicleDisplayName("CAR_PA_GrandCherokee_14_22_Amp", "x"))
    assertEquals("RAM 2013–2018, low trim", vehicleDisplayName("CAR_PA_RAM_13_18_Low", "x"))
    assertEquals("Wrangler 2018–2020, high trim", vehicleDisplayName("CAR_PA_Wrangler_18_20_H", "x"))
    assertEquals("Ford F150 2020, CD", vehicleDisplayName("CAR_PA_Ford_F150_20_CD", "x"))
    assertEquals("Car_0374_PA_Jeep_All", vehicleDisplayName(null, "Car_0374_PA_Jeep_All"))
}
@Test(expected = IllegalArgumentException::class) fun `a V line for an unknown profile is refused`() {
    VehicleCatalog.parse("V\t1\tNoSuchProfile\t-\t-\n")
}
```

- [ ] **Step 2: Run them to verify they fail.** Run `./gradlew :bus:testDebugUnitTest --tests "*CommandTableTest" --tests "*VehicleCatalogTest"`. Expected: compilation errors.

- [ ] **Step 3: Implement** the interfaces above. Display name rule:
  - Drop the first two `_` tokens (`CAR_PA`, `CAR_XP1`).
  - Split CamelCase at lower→upper boundaries.
  - Two consecutive 2-digit tokens become `20a–20b`; one becomes `20a`.
  - Trailing `Amp`, `CD`, `H`, `Low` become `, amplified audio`, `, CD`, `, high trim`, `, low trim`.
  - Any other token is kept as is.
  - A null const returns `profile`.

  `SyuVehicleBus.send` gets its payload from `table.frame(command)`. On null it logs `dropping $command: not in this vehicle's table` and returns.

- [ ] **Step 4: Run all `:bus` tests.** `./gradlew :bus:testDebugUnitTest`. Expected: all pass, including the edited `SignalCommandTableTest`.

- [ ] **Step 5: Commit**

```bash
git add bus/
git commit -m "bus: per-vehicle command tables from vehicles.tsv; a command outside the table is never sent"
```

---

### Task 3: Setup questions and `Features`

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/Features.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/FeaturesTest.kt`

**Interfaces:**
- Consumes: `CommandTable`, `Command`, `SeatSide` (existing, `SeatMenuLatch.kt`).
- Produces:
  - `enum class Question(val needs: Set<Command>) { SEAT_HEAT, SEAT_VENT, WHEEL_HEAT, DUAL_ZONE }` with needs `{SEAT_HEAT_L, SEAT_HEAT_R}`, `{SEAT_VENT_L, SEAT_VENT_R}`, `{WHEEL_HEAT}` and `{TEMP_R_UP, TEMP_R_DOWN}`.
  - `fun questionsFor(table: CommandTable): List<Question>`: enum order, keeping only questions whose `needs` are all in the table.
  - `class Features(val table: CommandTable, val answers: Map<Question, Boolean>)` with:
    - `fun has(command: Command): Boolean`
    - `val dualZone: Boolean`
    - `val hasAirflow: Boolean` (all four `AIRFLOW_*`)
    - `fun seatSlot(side: SeatSide): SeatSlot`
    - `companion object { fun allYes(table: CommandTable): Features }`
  - `sealed interface SeatSlot { data object None; data class Direct(val command: Command); data class Menu(val rows: List<Command>) }`. Rows are in the order heat, vent, then wheel for DRIVER.

- [ ] **Step 1: Write the failing tests.**

```kotlin
private val wk2 = VehicleCatalog.bundled()[2687350]!!.table
@Test fun `the WK2 asks all four questions`() = assertEquals(Question.entries, questionsFor(wk2))
@Test fun `a table without seat cooling never asks about it`() =
    assertEquals(listOf(Question.DUAL_ZONE), questionsFor(VehicleCatalog.bundled()[374]!!.table))
@Test fun `a yes answer cannot add a command the table lacks`() =
    assertFalse(Features(CommandTable(mapOf(Command.AC to 1)), mapOf(Question.SEAT_VENT to true)).has(Command.SEAT_VENT_L))
@Test fun `a no answer removes the commands it covers`() =
    assertFalse(Features(wk2, Question.entries.associateWith { it != Question.SEAT_VENT }).has(Command.SEAT_VENT_R))
@Test fun `single zone removes the passenger temperature and SYNC`() {
    val f = Features(wk2, Question.entries.associateWith { it != Question.DUAL_ZONE })
    assertFalse(f.dualZone); assertFalse(f.has(Command.TEMP_R_UP)); assertFalse(f.has(Command.SYNC))
}
@Test fun `WK2 all-yes keeps today's seat menus`() {
    val f = Features.allYes(wk2)
    assertEquals(SeatSlot.Menu(listOf(SEAT_HEAT_L, SEAT_VENT_L, WHEEL_HEAT)), f.seatSlot(SeatSide.DRIVER))
    assertEquals(SeatSlot.Menu(listOf(SEAT_HEAT_R, SEAT_VENT_R)), f.seatSlot(SeatSide.PASSENGER))
}
@Test fun `one control on a side is a direct tap`() {
    val f = Features(wk2, mapOf(SEAT_HEAT to true, SEAT_VENT to false, WHEEL_HEAT to true, DUAL_ZONE to true))
    assertEquals(SeatSlot.Menu(listOf(SEAT_HEAT_L, WHEEL_HEAT)), f.seatSlot(SeatSide.DRIVER))
    assertEquals(SeatSlot.Direct(SEAT_HEAT_R), f.seatSlot(SeatSide.PASSENGER))
}
@Test fun `wheel only is a direct wheel tap on the driver side and nothing on the passenger side`() {
    val f = Features(wk2, mapOf(SEAT_HEAT to false, SEAT_VENT to false, WHEEL_HEAT to true, DUAL_ZONE to true))
    assertEquals(SeatSlot.Direct(WHEEL_HEAT), f.seatSlot(SeatSide.DRIVER))
    assertEquals(SeatSlot.None, f.seatSlot(SeatSide.PASSENGER))
}
@Test fun `commands no question covers follow the table`() =
    assertTrue(Features(wk2, emptyMap()).has(Command.REAR_DEFROST))
```

- [ ] **Step 2: Run to verify failure.** `./gradlew :bus:testDebugUnitTest --tests "*FeaturesTest"`. Expected: compilation errors.
- [ ] **Step 3: Implement** `Features.has`. The command must be in the table, and if a `Question` covers it, that question's answer must be `true`. `SYNC` additionally requires `dualZone`. `dualZone` = `has(TEMP_R_UP) && has(TEMP_R_DOWN)`.
- [ ] **Step 4: Run all `:bus` tests.** Expected: pass.
- [ ] **Step 5: Commit** — `git commit -m "bus: setup questions and the features they leave"`

---

### Task 4: Resolving the vehicle from id and answers

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/VehicleStatus.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/VehicleStatusTest.kt`

**Interfaces:**
- Consumes: `VehicleCatalog`, `Vehicle`, `Features`, `Question`, `questionsFor`.
- Produces:
  - `data class SavedVehicle(val id: Int?, val answers: Map<Question, Boolean>)` with `fun onLiveId(live: Int): SavedVehicle`. If `live == id` it returns `this`; otherwise it returns `SavedVehicle(live, emptyMap())`. `companion object { val NONE = SavedVehicle(null, emptyMap()) }`.
  - `sealed interface VehicleStatus { data object Waiting; data class Unknown(val id: Int); data class NeedsSetup(val vehicle: Vehicle, val questions: List<Question>, val answers: Map<Question, Boolean>); data class Ready(val vehicle: Vehicle, val features: Features) }`
  - `fun resolveVehicle(saved: SavedVehicle, catalog: VehicleCatalog): VehicleStatus`. A null id gives `Waiting`; an id not in the catalog gives `Unknown`. The status is `Ready` only when every question in `questionsFor(table)` has an answer; otherwise it's `NeedsSetup`.

- [ ] **Step 1: Write the failing tests.**

```kotlin
private val catalog = VehicleCatalog.bundled()
private val allYes = Question.entries.associateWith { true }
@Test fun `no id is Waiting`() = assertEquals(VehicleStatus.Waiting, resolveVehicle(SavedVehicle.NONE, catalog))
@Test fun `an id outside the catalog is Unknown`() = assertEquals(VehicleStatus.Unknown(1234567), resolveVehicle(SavedVehicle(1234567, emptyMap()), catalog))
@Test fun `cached id is used before any live id`() = assertTrue(resolveVehicle(SavedVehicle(2687350, allYes), catalog) is VehicleStatus.Ready)
@Test fun `the same live id keeps the answers`() = assertEquals(allYes, SavedVehicle(2687350, allYes).onLiveId(2687350).answers)
@Test fun `a different live id discards the answers`() = assertEquals(SavedVehicle(2621814, emptyMap()), SavedVehicle(2687350, allYes).onLiveId(2621814))
@Test fun `partial answers are not Ready`() {
    val s = resolveVehicle(SavedVehicle(2687350, mapOf(Question.SEAT_HEAT to true, Question.SEAT_VENT to false)), catalog)
    assertTrue(s is VehicleStatus.NeedsSetup)
    assertEquals(Question.WHEEL_HEAT, (s as VehicleStatus.NeedsSetup).questions.first { it !in s.answers })
}
@Test fun `a car with no questions is Ready at once`() {
    val tiny = VehicleCatalog.parse("P\tX\tAC=1,TEMP_L_UP=4,TEMP_L_DOWN=5\nV\t9\tX\t-\t-\n")
    assertTrue(resolveVehicle(SavedVehicle(9, emptyMap()), tiny) is VehicleStatus.Ready)
}
```

- [ ] **Step 2: Run to verify failure.** `./gradlew :bus:testDebugUnitTest --tests "*VehicleStatusTest"`.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run all `:bus` tests.** Expected: pass.
- [ ] **Step 5: Commit** — `git commit -m "bus: resolve the vehicle from the cached id and the setup answers"`

---

### Task 5: The bar draws only what the car has

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/TopRowLayout.kt`
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/bar/ClimateBar.kt` (new `features: Features` parameter; passes it down)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarTopRow.kt` (cells from `topRowLayout`; the driver main cell is AUTO or A/C; a seat cell renders `SeatButton` for `Menu`/`Direct` of a seat command, and `WheelButton` for `Direct(WHEEL_HEAT)`)
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/WheelButton.kt` (`WheelHeatGlyph` + `ON` / `OFF` / `—` state text, the same press treatment as `SeatButton`)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarZones.kt` (when `dualZone` is false, one zone labelled `TEMPERATURE` spanning `Dimens.barCenterColumn`)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatMenu.kt` (`seatMenuRows(side, features)`, `seatMenuHeight(side, features)`; renders only `SeatSlot.Menu.rows`)
- Modify: `design/src/main/kotlin/com/wk2/climate/design/Dimens.kt` (add `val barHalf = 384.dp`)
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/BarGeometryTest.kt`

**Interfaces:**
- Consumes: `Features`, `SeatSlot`, `CommandTable`, `VehicleCatalog` (tests).
- Produces:
  - `enum class TopCell { DRIVER_SEAT, DRIVER_MAIN, CLIMATE, PASSENGER_SEAT }`
  - `data class TopRowCell(val cell: TopCell, val width: Dp)`
  - `fun topRowLayout(features: Features): List<TopRowCell>`
  - `fun driverMainCommand(features: Features): Command?`: `AUTO` if present, else `AC`, else null.
  - `ClimateBar(state, features, onCommand, onHome, onBack, panelOpen, onToggleClimate, seatMenuOpen, onSeatButton, modifier)`
  - `fun seatMenuRows(side: SeatSide, features: Features): Int`, `fun seatMenuHeight(side: SeatSide, features: Features): Dp`

  Layout rule:
  - Each half is `[seat 128, main 256]`, or `[main 384]` when that side's `seatSlot` is `None`.
  - If `driverMainCommand` is null, the driver half has no main cell and CLIMATE absorbs that width (a car that doesn't exist in the bundled catalog, but it's pinned so the row never overflows).

- [ ] **Step 1: Write the failing tests** (replacing the fixed `topRow` list with derived layouts).

```kotlin
private val wk2 = VehicleCatalog.bundled()[2687350]!!.table
private fun f(heat: Boolean = true, vent: Boolean = true, wheel: Boolean = true, dual: Boolean = true, table: CommandTable = wk2) =
    Features(table, mapOf(Question.SEAT_HEAT to heat, Question.SEAT_VENT to vent, Question.WHEEL_HEAT to wheel, Question.DUAL_ZONE to dual))

@Test fun `WK2 all yes is today's row`() = assertEquals(
    listOf(DRIVER_SEAT to 128.dp, DRIVER_MAIN to 256.dp, CLIMATE to 256.dp, PASSENGER_SEAT to 128.dp),
    topRowLayout(f()).map { it.cell to it.width })
@Test fun `wheel only widens CLIMATE to 384`() = assertEquals(
    listOf(DRIVER_SEAT to 128.dp, DRIVER_MAIN to 256.dp, CLIMATE to 384.dp),
    topRowLayout(f(heat = false, vent = false)).map { it.cell to it.width })
@Test fun `no comfort is two halves`() = assertEquals(
    listOf(DRIVER_MAIN to 384.dp, CLIMATE to 384.dp),
    topRowLayout(f(heat = false, vent = false, wheel = false)).map { it.cell to it.width })
@Test fun `without AUTO the driver main cell is A-C`() =
    assertEquals(Command.AC, driverMainCommand(Features.allYes(VehicleCatalog.bundled()[374]!!.table)))
@Test fun `every derived row fills the centre column and clears the floor`() {
    for (h in listOf(true, false)) for (v in listOf(true, false)) for (w in listOf(true, false)) {
        val row = topRowLayout(f(h, v, w))
        assertEquals(Dimens.barCenterColumn, row.fold(0.dp) { a, c -> a + c.width })
        assertTrue(row.all { it.width >= Dimens.minTarget })
    }
}
@Test fun `the centre line never moves when both halves have a main cell`() {
    for (h in listOf(true, false)) for (w in listOf(true, false)) {
        val row = topRowLayout(f(heat = h, vent = false, wheel = w))
        val driverHalf = row.takeWhile { it.cell != CLIMATE && it.cell != PASSENGER_SEAT }.fold(0.dp) { a, c -> a + c.width }
        assertEquals(Dimens.barHalf, driverHalf)
    }
}
@Test fun `menus have one row per control`() {
    assertEquals(3, seatMenuRows(SeatSide.DRIVER, f()))
    assertEquals(2, seatMenuRows(SeatSide.PASSENGER, f()))
    assertEquals(2, seatMenuRows(SeatSide.DRIVER, f(vent = false)))
}
```

The existing tests that use `seatMenuRows(side)` switch to `seatMenuRows(side, f())` with the same expected values (3 and 2).

- [ ] **Step 2: Run to verify failure.** `./gradlew :ui:testDebugUnitTest --tests "*BarGeometryTest"`.
- [ ] **Step 3: Implement** `TopRowLayout.kt`, then rewire `BarTopRow`, `ClimateBar`, `BarZones`, `SeatMenu` and the new `WheelButton`. `BarTopRow` takes `features` and iterates `topRowLayout(features)`. `onSeatButton(side)` remains the only tap callback for seat cells; the service decides direct vs menu (Task 7).
- [ ] **Step 4: Run all `:ui` tests and build.** `./gradlew :ui:testDebugUnitTest :harness:assembleDebug`. Expected: pass. The harness compiles once it passes `Features.allYes(...)` (minimal edit here; the picker comes in Task 9).
- [ ] **Step 5: Commit** — `git commit -m "ui: the bar lays itself out from the vehicle's features"`

---

### Task 6: The climate window draws only what the car has

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelLayout.kt`
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/panel/ClimatePanel.kt` (new `features` parameter; each section is conditional)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelZones.kt` (single `TEMPERATURE` zone when not dual)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelFan.kt` (AUTO label only if `features.has(AUTO)`)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelMode.kt` (rows from `modeRows`; each tile keeps its current content and styling, keyed by command)
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt` (rows from `comfortRows`)
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/PanelLayoutTest.kt`

**Interfaces:**
- Consumes: `Features`.
- Produces:
  - `fun modeRows(features: Features): List<List<Command>>`. It filters `[AUTO, AC, RECIRC, MAX_AC]` and `[FRONT_DEFROST, REAR_DEFROST, SYNC]` by `features.has` and drops empty rows.
  - `data class ComfortRows(val heat: Boolean, val vent: Boolean, val wheel: Boolean) { val any: Boolean }`, built from `fun comfortRows(features: Features): ComfortRows`. Heat means both `SEAT_HEAT_L` and `_R`; vent means both `SEAT_VENT_*`.
  - `enum class PanelSectionId { ZONES, FAN, AIRFLOW, MODE, COMFORT, FOOTER }`, with `fun panelSections(features: Features): List<PanelSectionId>`.
    - FAN needs `FAN_UP` and `FAN_DOWN`.
    - AIRFLOW needs `hasAirflow`.
    - MODE needs a non-empty `modeRows`.
    - COMFORT needs `comfortRows.any`.
    - FOOTER needs `CLIMATE_POWER`.
  - `ClimatePanel(state, features, outsideF, onCommand, onClose, modifier)`

- [ ] **Step 1: Write the failing tests.**

```kotlin
@Test fun `WK2 all yes keeps every section and today's tiles`() {
    val f = Features.allYes(wk2)
    assertEquals(PanelSectionId.entries, panelSections(f))
    assertEquals(listOf(listOf(AUTO, AC, RECIRC, MAX_AC), listOf(FRONT_DEFROST, REAR_DEFROST, SYNC)), modeRows(f))
    assertEquals(ComfortRows(heat = true, vent = true, wheel = true), comfortRows(f))
}
@Test fun `a KL Cherokee has no MAX A-C tile until synthesized MAX lands`() =
    assertEquals(listOf(AUTO, AC, RECIRC), modeRows(Features.allYes(VehicleCatalog.bundled()[2621814]!!.table)).first())
@Test fun `single zone drops SYNC`() = assertFalse(SYNC in modeRows(f(dual = false)).flatten())
@Test fun `no comfort answers drop the section`() =
    assertFalse(PanelSectionId.COMFORT in panelSections(f(heat = false, vent = false, wheel = false)))
@Test fun `the minimal Jeep profile has zones and mode only`() = assertEquals(
    listOf(PanelSectionId.ZONES, PanelSectionId.MODE),
    panelSections(Features.allYes(VehicleCatalog.bundled()[374]!!.table)))
```

- [ ] **Step 2: Run to verify failure.** `./gradlew :ui:testDebugUnitTest --tests "*PanelLayoutTest"`.
- [ ] **Step 3: Implement**, then gate each `PanelSection` in `ClimatePanel` on `panelSections`. Mode rows render with `Modifier.weight(1f)` per present tile, which already shares the width evenly. Keep the bottom `Spacer(weight(1f))` and the footer's position.
- [ ] **Step 4: Run `:ui` tests and build the harness.** Expected: pass.
- [ ] **Step 5: Commit** — `git commit -m "ui: the climate window omits what the vehicle lacks"`

---

### Task 7: Service wiring: persistence, gating, table, seat taps

**Files:**
- Create: `app/src/main/kotlin/com/wk2/climate/app/VehicleStore.kt`
- Modify: `app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt` (`onServiceConnected`, `followBus`, `BarContent`, `PanelContent`, `SeatMenuContent`, `onSeatButton`, `seatMenuWindowParams`)

**Interfaces:**
- Consumes: `SavedVehicle`, `resolveVehicle`, `VehicleStatus`, `VehicleCatalog.bundled()`, `Features.seatSlot`, `SyuVehicleBus.table`, `ClimateBar(..., features, ...)`, `ClimatePanel(..., features, ...)`, `seatMenuHeight(side, features)`.
- Produces: `class VehicleStore(context: Context)` with `val saved: StateFlow<SavedVehicle>` (backed by `SharedPreferences` "vehicle": key `id` Int, absent = null; keys `answer.<QUESTION>` Boolean) and `fun save(value: SavedVehicle)`. It listens with `OnSharedPreferenceChangeListener`, so the activity and the service see each other's writes in the same process.

- [ ] **Step 1: Implement `VehicleStore`.**
- [ ] **Step 2: Wire the service.** In `onServiceConnected`, create `store` and `catalog`, and launch in `scope`:
  - `bus.state.mapNotNull { it[Signal.CANBUS_ID] }.distinctUntilChanged()` → `store.save(store.saved.value.onLiveId(id))`.
  - `status = store.saved.map { resolveVehicle(it, catalog) }.stateIn(scope, Eagerly, resolveVehicle(store.saved.value, catalog))`.
  - Collect `status`: `bus.table = (it as? VehicleStatus.Ready)?.vehicle?.table`. Log the status once per change (`vehicle: Ready Grand Cherokee 2014-2022 (2687350)`).
  - `followBus` gates on `combine(bus.connected, status) { c, s -> c && s is VehicleStatus.Ready }`, using the same `followConnection` and `BUS_GRACE_MS`.
  - `BarContent`, `PanelContent` and `SeatMenuContent` read `features` from the `Ready` status. If the status isn't Ready they render nothing, since the gate already removed the windows.
  - `onSeatButton(side)`: `when (features.seatSlot(side))`. `Direct` → `bus.send(command)`; `Menu` → today's toggle; `None` is unreachable from the UI, so it's ignored.
  - `seatMenuWindowParams(side)` uses `seatMenuHeight(side, features)`.
- [ ] **Step 3: Build.** `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -m "app: identify the vehicle, keep its answers, and show the bar only for a ready vehicle"`

---

### Task 8: Setup flow in `SetupActivity`

**Files:**
- Modify: `app/src/main/kotlin/com/wk2/climate/app/SetupActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `VehicleStore`, `VehicleCatalog.bundled()`, `resolveVehicle`, `VehicleStatus`, `Question`, `questionsFor`, `Features`, `ClimateBar` (preview), `CanbusId` (existing; base and variant for the unknown screen).

Screens, chosen by (accessibility state, `VehicleStatus`):

| condition | screen |
|---|---|
| service disabled / no settings screen | today's screens, unchanged |
| enabled, `Waiting` | "Reading your car…" (copy: `setup_waiting_title/body`) |
| `Unknown(id)` | "This car isn't supported yet." + `ID <id> · BASE <base> · VARIANT <variant>` + CLOSE |
| `NeedsSetup`, nothing answered yet and not confirmed | "We found your vehicle." + `vehicle.displayName` + `PA CANBUS · <id>` + CONTINUE |
| `NeedsSetup`, confirmed | a question screen for `questions.first { it !in answers }`: `QUESTION n OF m`, title, hint, the bar preview, NO / YES |
| `Ready` | "Your car": name, one switch per `questionsFor(table)` entry, ASK ME AGAIN (`store.save(SavedVehicle(id, emptyMap()))`) |

- "Confirmed" is activity-local state; reopening during setup shows the found-your-car screen again, then resumes at the first unanswered question.
- The preview is the real `ClimateBar`, rendered with `ClimateState.EMPTY`, `Features(table, answers + (current question to <the answer being previewed>))` (it shows YES until tapped), no-op callbacks, and `Modifier.graphicsLayer { scaleX = 0.8f; scaleY = 0.8f }`.
- YES/NO buttons are at least `Dimens.minTarget` tall.

Copy (exact):
- `SEAT_HEAT`: "Do your front seats have heating?" / "Look for a seat with wavy lines on the seat button."
- `SEAT_VENT`: "Do your front seats have cooling?" / "Sometimes called ventilated seats. Look for a fan symbol on the seat button."
- `WHEEL_HEAT`: "Does your steering wheel have heating?" / ""
- `DUAL_ZONE`: "Can the driver and passenger set different temperatures?" / ""

- [ ] **Step 1: Add strings** for every title and body above plus `setup_found_title`, `setup_unknown_title`, `setup_unknown_body` ("The head unit reports a canbus id that has no climate profile we can trust. The factory climate bar stays as it is."), `setup_continue`, `setup_yes`, `setup_no`, `setup_close`, `setup_ask_again`, `setup_your_car`.
- [ ] **Step 2: Implement the screens** in `SetupScreen()` from `store.saved` collected as state and `resolveVehicle`. Answer taps call `store.save(saved.copy(answers = saved.answers + (q to yes)))`.
- [ ] **Step 3: Build.** `./gradlew assembleDebug`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Commit** — `git commit -m "app: one-time setup questions, editable later"`

---

### Task 9: Harness vehicle picker

**Files:**
- Modify: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt`

**Interfaces:**
- Consumes: `VehicleCatalog.bundled()`, `Features`, `Question`, `ClimateBar(..., features, ...)`, `ClimatePanel(..., features, ...)`, `SeatMenu`.
- Produces: a `Preset(name: String, features: Features)` list cycled by a harness button. It contains:
  1. `WK2 (2687350), all yes`
  2. `WK2, heat + wheel, no cooling`
  3. `WK2, wheel only`
  4. `WK2, no comfort`
  5. `KL Cherokee (2621814), all yes` — no MAX A/C tile
  6. `Jeep_All (374), single zone`

  The seat button in the harness follows `features.seatSlot` exactly as the service does.

- [ ] **Step 1: Implement** the presets and the picker button. Keep the existing views.
- [ ] **Step 2: Run on an AVD.** `./gradlew :harness:installDebug`, step through each preset, and capture a screenshot of bar + panel per preset into `docs/screenshots/vehicles/` named `<n>-<slug>-bar.png` and `-panel.png`.
  Expected:
  - Preset 1 matches `docs/screenshots/vehicle-bar.png`'s current-layout successors pixel for pixel in the top row.
  - Presets 3, 4 and 6 show the widened cells.
  - Preset 6 has no FAN, AIRFLOW, COMFORT or footer.
- [ ] **Step 3: Commit** — `git commit -m "harness: step through vehicle presets"`

---

### Task 10: On the truck, and docs

**Files:**
- Modify: `README.md` (supported vehicles: link to wiki page 14; setup questions in the install steps)
- Modify: `docs/superpowers/specs/2026-10-26-multi-vehicle-design.md` (status line: implemented on `feat/multi-vehicle`)

- [ ] **Step 1: Install on the WK2.**
  ```
  ./gradlew :app:assembleDebug
  adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
  ```
  Clear the app's data first (`adb shell pm clear com.android.wk2climate`) so setup runs from scratch. Accessibility will need re-enabling after a clear.
- [ ] **Step 2: Verify with logcat** (`adb logcat -s wk2-bus:* wk2-climate:*`). Expected:
  - `canbus id 2687350 (0x290176) base=374 variant=41`
  - `vehicle: NeedsSetup`
  - after answering all YES in the app: `vehicle: Ready Grand Cherokee 2014-2022 (2687350)`
  - the bar appears and looks exactly like today's.
- [ ] **Step 3: The owner's vehicle pass.** Tap every bar and window control once. Each does what it did on `dc5c009`. Then turn "cooled front seats" off in the app: the cool rows and tiles vanish, and the passenger button heats directly. Turn it back on.
- [ ] **Step 4: Ignition cycle.** With the ignition off and back on, the bar returns without setup, from the cached id.
- [ ] **Step 5: Update the README and the spec status, then commit and push.** Run `git push -u origin feat/multi-vehicle` (the owner tests from the pushed branch).
