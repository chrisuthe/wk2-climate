# wk2-climate Plan 1: Foundation and Bus Layer

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a unit-tested vehicle-bus layer that turns three vendor Binder modules into one immutable state flow plus one typed command sink, and prove it end-to-end with a debug harness app that runs on a plain emulator with no vehicle attached.

**Architecture:** A `:bus` Android library holds all protocol knowledge behind a `VehicleBus` interface with two implementations — `SyuVehicleBus` (hand-written Binder proxies against the vendor AIDL) and `FakeVehicleBus` (in-memory, and deliberately adversarial: it reproduces every documented vehicle misbehaviour). Read ids (`Signal`) and write indices (`Command`) are separate types so the protocol's two non-overlapping code spaces cannot be confused. All state derivation is pure Kotlin with no Android imports, so it is tested on the JVM.

**Tech Stack:** Kotlin 2.3.0, AGP 9.0.1, Gradle 9.1.0, Jetpack Compose (ui + foundation only — no Material), kotlinx-coroutines 1.10.2, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-09-08-wk2-climate-design.md`

## Global Constraints

- **`compileSdk = 36`, `minSdk = 26`, `targetSdk = 33`.** targetSdk deliberately matches the target device (Android 13 / SDK 33) so no compat-behaviour changes apply to the accessibility service or overlay windows — the riskiest part of this project.
- **JVM target 17** for both Java and Kotlin.
- **Filter tests with `:bus:testDebugUnitTest --tests '<pattern>'`.** The `:bus:test`
  lifecycle task is an aggregate and rejects `--tests` with "Unknown command-line
  option". Bare `:bus:test` is fine for running everything.
- **Do NOT apply `org.jetbrains.kotlin.android`.** AGP 9.0 has built-in Kotlin
  support and *rejects* that plugin with a hard error rather than ignoring it.
  The `kotlin { compilerOptions { jvmTarget } }` extension still works, and
  `src/main/kotlin` is already on the source set — explicit `srcDir` calls are
  both unnecessary and deprecated in Gradle 9.
- **`1px = 1dp`.** The panel is 1080x1920 at 160dpi. Every measurement in the design handoff is used directly as a dp value.
- **No Material dependency.** The design uses no Material components, no ripple, and no shadows. Depend on `compose.ui` and `compose.foundation` only.
- **Read ids and write indices are separate types.** Never add a function that takes a raw `(module, code, payload)` triple. Module 4 code 0 is `C_VOL` for writes and `U_SPECTRUM` for reads — an untyped API makes that collision a live bug.
- **One callback object per module.** The vendor `update()` callback carries the code but *not* the module, and code 2 is `U_STANDBY` on MAIN and `U_VOL` on SOUND.
- **Never subscribe** to `U_SPECTRUM` (module 4, code 0) or `U_CANBUS_FRAME_TO_UI` (module 7, code 1019). They fire at ~10 Hz and ~26 Hz and bury real state changes.
- **`:ui` and `:harness` must never depend on `:app`.** No composable may reference an `AccessibilityService`, a `WindowManager` token, or any platform singleton.
- **Command index 16 is destructive.** It powers off climate in a vehicle with no physical HVAC controls. It must be reachable only behind a hold-to-confirm gesture, and is marked `destructive = true` in code.
- **Command indices are WK2-specific** (from `Car_0374_PA_Jeep_All`). Read codes are universal across FYT UIS7870 units.

---

## File Structure

```
settings.gradle.kts                 module registry
build.gradle.kts                    root, plugins declared apply-false
gradle/libs.versions.toml           version catalog — single source of versions
gradle.properties                   AndroidX, JVM args
gradle/wrapper/                      wrapper jar + properties (copied, see Task 1)

bus/build.gradle.kts
bus/src/main/AndroidManifest.xml
bus/src/main/kotlin/com/wk2/climate/bus/
    Signal.kt                       read ids: (module, code) — Task 1
    Command.kt                      write indices + payloads — Task 1
    Values.kt                       Temp, Fan, SeatLevel sentinel types — Task 2
    AirflowMode.kt                  three-flag -> mode derivation — Task 3
    ClimateState.kt                 immutable snapshot + derived accessors — Task 4
    VehicleBus.kt                   the interface (the seam) — Task 5
    FakeVehicleBus.kt               adversarial in-memory bus — Task 5
    AdaptiveSlot.kt                 hysteresis + dwell state machine — Task 6
    SyuVehicleBus.kt                real Binder implementation — Task 7
bus/src/test/kotlin/com/wk2/climate/bus/
    SignalCommandTableTest.kt       — Task 1
    ValuesTest.kt                   — Task 2
    AirflowModeTest.kt              — Task 3
    ClimateStateTest.kt             — Task 4
    FakeVehicleBusTest.kt           — Task 5
    AdaptiveSlotTest.kt             — Task 6

design/build.gradle.kts
design/src/main/kotlin/com/wk2/climate/design/
    Colors.kt                       night + day palettes — Task 8
    Dimens.kt                       target sizes, radii, gutters — Task 8
    Type.kt                         font families + text styles — Task 8

harness/build.gradle.kts
harness/src/main/AndroidManifest.xml
harness/src/main/kotlin/com/wk2/climate/harness/
    HarnessActivity.kt              debug screen over FakeVehicleBus — Task 9
```

**Not in this plan.** `:ui` (screens 2a and 1d) and `:app` (the `AccessibilityService` and overlay windows) are Plan 2 and Plan 3. This plan deliberately stops at the point where the bus layer is proven, because everything above it is UI work that benefits from having a trustworthy state source first.

---

## Task 1: Project scaffolding and the typed code spaces

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat` (+ copy `gradle/wrapper/gradle-wrapper.jar`)
- Create: `bus/build.gradle.kts`, `bus/src/main/AndroidManifest.xml`
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/Signal.kt`
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/Command.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/SignalCommandTableTest.kt`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: `enum class Signal(val module: Int, val code: Int)` with `Signal.of(module, code): Signal?` and `Signal.modules(): Set<Int>`. `enum class Command(val module: Int, val code: Int, val payload: IntArray, val destructive: Boolean)`. Both in package `com.wk2.climate.bus`.

- [ ] **Step 1: Copy the Gradle wrapper**

There is no standalone Gradle on this machine, so the wrapper jar is copied from an existing local project rather than generated.

```bash
mkdir -p gradle/wrapper
cp /c/CodeProjects/mobile-app/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
cp /c/CodeProjects/mobile-app/gradlew .
cp /c/CodeProjects/mobile-app/gradlew.bat .
chmod +x gradlew
```

Write `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.1.0-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 2: Write the version catalog**

Write `gradle/libs.versions.toml`:

```toml
[versions]
agp = "9.0.1"
kotlin = "2.3.0"
compileSdk = "36"
minSdk = "26"
targetSdk = "33"
coroutines = "1.10.2"
composeUi = "1.10.3"
activityCompose = "1.12.2"
lifecycle = "2.9.6"
coreKtx = "1.17.0"
junit = "4.13.2"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
compose-ui = { module = "androidx.compose.ui:ui", version.ref = "composeUi" }
compose-foundation = { module = "androidx.compose.foundation:foundation", version.ref = "composeUi" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview", version.ref = "composeUi" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling", version.ref = "composeUi" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

> If dependency resolution fails on any pinned version, bump only that entry to the newest stable and record the change in the commit message. Do not switch to a BOM — explicit versions are intentional here.

- [ ] **Step 3: Write the root build files**

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wk2-climate"
include(":bus")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
org.gradle.caching=true
org.gradle.parallel=true
```

- [ ] **Step 4: Write the `:bus` module build file**

`bus/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.wk2.climate.bus"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`bus/src/main/AndroidManifest.xml` — the package-visibility query is load-bearing. Without it, `bindService` to the vendor service fails **silently** on Android 11+ even though the service is exported.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <queries>
        <package android:name="com.syu.ms" />
    </queries>
</manifest>
```

- [ ] **Step 5: Point Gradle at the SDK**

`local.properties` is gitignored, so it must be created locally. Use **forward
slashes** — backslash-escaped Windows paths fail with `java.io.IOException:
Invalid file path`.

```bash
echo 'sdk.dir=C:/Users/chris/AppData/Local/Android/Sdk' > local.properties
```

- [ ] **Step 6: Write the failing test**

`bus/src/test/kotlin/com/wk2/climate/bus/SignalCommandTableTest.kt`:

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalCommandTableTest {

    @Test
    fun `signal module and code pairs are unique`() {
        val pairs = Signal.entries.map { it.module to it.code }
        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `signal lookup round trips`() {
        Signal.entries.forEach { s ->
            assertEquals(s, Signal.of(s.module, s.code))
        }
    }

    @Test
    fun `signal lookup returns null for an unknown pair`() {
        assertNull(Signal.of(7, 9999))
    }

    @Test
    fun `signals span exactly the three modules we bind`() {
        assertEquals(setOf(0, 4, 7), Signal.modules())
    }

    @Test
    fun `we never subscribe to the high rate flood codes`() {
        // U_SPECTRUM is module 4 code 0; U_CANBUS_FRAME_TO_UI is module 7 code 1019.
        assertNull(Signal.of(4, 0))
        assertNull(Signal.of(7, 1019))
    }

    @Test
    fun `climate commands all use the fixed code 6 and a leading 1`() {
        Command.entries.filter { it.module == 7 }.forEach { c ->
            assertEquals("${c.name} must use command code 6", 6, c.code)
            assertEquals("${c.name} payload must be {1, index}", 2, c.payload.size)
            assertEquals("${c.name} payload[0] must be 1", 1, c.payload[0])
        }
    }

    @Test
    fun `climate command indices are unique and inside the known space`() {
        val indices = Command.entries.filter { it.module == 7 }.map { it.payload[1] }
        assertEquals(indices.size, indices.toSet().size)
        indices.forEach { assertTrue("index $it out of range 1..25", it in 1..25) }
    }

    @Test
    fun `unmapped and duplicate vehicle indices are omitted`() {
        val indices = Command.entries.filter { it.module == 7 }.map { it.payload[1] }.toSet()
        // 19 has no effect on this vehicle; 25 duplicates 10 (airflow -> feet).
        assertTrue("index 19 is unmapped and must not be exposed", 19 !in indices)
        assertTrue("index 25 duplicates 10 and must not be exposed", 25 !in indices)
    }

    @Test
    fun `climate power is the only destructive command`() {
        val destructive = Command.entries.filter { it.destructive }
        assertEquals(listOf(Command.CLIMATE_POWER), destructive)
        assertEquals(16, Command.CLIMATE_POWER.payload[1])
    }

    @Test
    fun `volume commands use the sound module convention`() {
        listOf(Command.VOL_UP to -1, Command.VOL_DOWN to -2, Command.VOL_HIDE_OSD to -7)
            .forEach { (cmd, sentinel) ->
                assertEquals("${cmd.name} is on the SOUND module", 4, cmd.module)
                assertEquals("${cmd.name} uses C_VOL code 0", 0, cmd.code)
                assertEquals("${cmd.name} payload is a single sentinel", 1, cmd.payload.size)
                assertEquals(sentinel, cmd.payload[0])
            }
    }
}
```

- [ ] **Step 7: Run the test to verify it fails**

Run: `./gradlew :bus:test`
Expected: FAIL — compilation error, `Signal` and `Command` are unresolved references.

- [ ] **Step 8: Write `Signal.kt`**

```kotlin
package com.wk2.climate.bus

/**
 * A **read** id on the vendor bus — what you subscribe to and receive through
 * the callback. Codes are universal across FYT UIS7870 units.
 *
 * These are NOT the values you write. Sending a read id to the vendor `cmd()`
 * is accepted and silently discarded, which looks exactly like a permission
 * failure and is not one. Writes are [Command].
 *
 * A code is unique only *within* a module: code 2 is `U_STANDBY` on MAIN and
 * `U_VOL` on SOUND, which is why every entry carries its module.
 */
enum class Signal(val module: Int, val code: Int) {

    // ---- module 7: CANBUS (climate) ----
    POWER(7, 10),
    AC(7, 11),
    RECIRC(7, 12),
    AUTO(7, 13),
    REAR_DEFROST(7, 16),
    BLOW_UP(7, 18),
    BLOW_BODY(7, 19),
    BLOW_FOOT(7, 20),
    WIND_LEVEL(7, 21),
    TEMP_LEFT(7, 27),
    TEMP_RIGHT(7, 28),
    SEAT_HEAT_L(7, 29),
    SEAT_HEAT_R(7, 30),
    SEAT_VENT_L(7, 31),
    SEAT_VENT_R(7, 32),
    TEMP_UNIT(7, 37),
    AC_MAX(7, 53),
    SYNC(7, 62),
    FRONT_DEFROST(7, 65),
    WHEEL_HEAT(7, 66),

    // ---- module 4: SOUND ----
    VOLUME(4, 2),

    // ---- module 0: MAIN ----
    /**
     * Outside temperature. **Measured 2026-09-08: reads a static packed word
     * (0x10000744) that does not correspond to the temperature the head unit's
     * own status bar displays.** Treated as unavailable until decoded; see the
     * spec, section 11 item 4. Subscribed anyway so a future decode has data.
     */
    TEMP_OUT(0, 40),

    /** Illumination / headlights. Drives the day/night theme, not a clock. */
    ILLUMINATION(0, 4),
    ;

    companion object {
        private val byModuleCode: Map<Long, Signal> =
            entries.associateBy { key(it.module, it.code) }

        private fun key(module: Int, code: Int): Long =
            (module.toLong() shl 32) or (code.toLong() and 0xFFFFFFFFL)

        fun of(module: Int, code: Int): Signal? = byModuleCode[key(module, code)]

        /** The modules we must bind and register one callback each against. */
        fun modules(): Set<Int> = entries.mapTo(LinkedHashSet()) { it.module }

        fun inModule(module: Int): List<Signal> = entries.filter { it.module == module }
    }
}
```

- [ ] **Step 9: Write `Command.kt`**

```kotlin
package com.wk2.climate.bus

/**
 * A **write** on the vendor bus.
 *
 * This type deliberately hides two unrelated calling conventions:
 *  - climate is `module 7, code 6, {1, index}` where index is per-vehicle
 *  - volume is `module 4, code 0, {sentinel}`
 *
 * Keeping it separate from [Signal] is not decoration. Module 4 code 0 is
 * `C_VOL` for writes and `U_SPECTRUM` for reads — with a single untyped
 * `send(module, code, payload)` that collision eventually becomes a bug.
 *
 * Indices are **specific to the Jeep Grand Cherokee WK2** (vehicle profile
 * `Car_0374_PA_Jeep_All`). Another vehicle needs its own table.
 */
enum class Command(
    val module: Int,
    val code: Int,
    val payload: IntArray,
    /** True only for actions that can leave the vehicle without climate control. */
    val destructive: Boolean = false,
) {
    // ---- module 7: CANBUS. code 6 is fixed; payload[1] is the vehicle index ----
    AC(7, 6, intArrayOf(1, 1)),

    /** Macro: also forces A/C on, clears body-blow, and sets fan to the AUTO sentinel. */
    AUTO(7, 6, intArrayOf(1, 2)),

    RECIRC(7, 6, intArrayOf(1, 3)),
    TEMP_L_UP(7, 6, intArrayOf(1, 4)),
    TEMP_L_DOWN(7, 6, intArrayOf(1, 5)),

    /** Exits AUTO as a side effect, landing on fan level 3. Idempotent at 7. */
    FAN_UP(7, 6, intArrayOf(1, 6)),
    FAN_DOWN(7, 6, intArrayOf(1, 7)),

    // Idempotent mode setters. This is what makes direct selection possible
    // instead of the OEM bar's four-step cycle.
    AIRFLOW_FACE(7, 6, intArrayOf(1, 8)),
    AIRFLOW_FACE_FEET(7, 6, intArrayOf(1, 9)),
    AIRFLOW_FEET(7, 6, intArrayOf(1, 10)),
    AIRFLOW_FEET_GLASS(7, 6, intArrayOf(1, 11)),

    /** Macro: forces recirculation on and fan to 6. Toggles against MAX_AC. */
    FRONT_DEFROST(7, 6, intArrayOf(1, 12)),

    /** The control the OEM bar labels DUAL. It drives `U_AIR_SYNC`, not `U_AIR_DUAL`. */
    SYNC(7, 6, intArrayOf(1, 13)),

    REAR_DEFROST(7, 6, intArrayOf(1, 14)),

    /** Macro: drives both temperatures to the LO sentinel. Discards the user's setpoints. */
    MAX_AC(7, 6, intArrayOf(1, 15)),

    /**
     * Powers the climate system off. This vehicle has no physical HVAC
     * controls, so this leaves no way to change anything until sent again.
     * Reachable only behind a hold-to-confirm gesture.
     */
    CLIMATE_POWER(7, 6, intArrayOf(1, 16), destructive = true),

    SEAT_HEAT_L(7, 6, intArrayOf(1, 17)),
    SEAT_HEAT_R(7, 6, intArrayOf(1, 18)),
    TEMP_R_UP(7, 6, intArrayOf(1, 20)),
    TEMP_R_DOWN(7, 6, intArrayOf(1, 21)),
    SEAT_VENT_L(7, 6, intArrayOf(1, 22)),
    SEAT_VENT_R(7, 6, intArrayOf(1, 23)),
    WHEEL_HEAT(7, 6, intArrayOf(1, 24)),

    // ---- module 4: SOUND. code 0 is C_VOL; the payload is a single sentinel ----
    VOL_UP(4, 0, intArrayOf(-1)),
    VOL_DOWN(4, 0, intArrayOf(-2)),

    /**
     * Hides the OEM volume OSD. Measured 2026-09-08: a bus-driven volume change
     * did not raise the OSD at all, so this is probably unnecessary. Kept
     * defined; do not build anything that depends on needing it.
     */
    VOL_HIDE_OSD(4, 0, intArrayOf(-7)),
    ;

    /** The per-vehicle index, for module 7 commands only. */
    val vehicleIndex: Int? get() = if (module == 7) payload.getOrNull(1) else null
}
```

- [ ] **Step 10: Run the test to verify it passes**

Run: `./gradlew :bus:test`
Expected: PASS, 10 tests.

If the first Gradle invocation fails on toolchain selection, point Gradle at the Studio JBR (both system JDKs are 25, which some AGP versions reject):

```bash
./gradlew :bus:test -Dorg.gradle.java.home="C:/Program Files/Android/Android Studio/jbr"
```

If that is needed, persist it in `gradle.properties` as `org.gradle.java.home` and note it in the commit.

- [ ] **Step 11: Add a .gitignore entry check and commit**

Confirm the existing root `.gitignore` already covers `.gradle/`, `build/`, and `local.properties`. It does. Then:

```bash
git add gradle gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties bus
git commit -m "feat(bus): typed read and write code spaces

Signal carries (module, code) for subscriptions; Command carries the
per-vehicle write index plus its payload. They are separate types so the
protocol's two non-overlapping code spaces cannot be confused -- sending a
read id to the vendor cmd() is accepted and silently discarded.

Command also hides two unrelated conventions behind one type: climate is
module 7 code 6 {1,index}, volume is module 4 code 0 {sentinel}. That
matters because module 4 code 0 is C_VOL for writes and U_SPECTRUM for
reads, so an untyped send() would eventually cross them.

Tests assert table integrity: unique pairs, round-trip lookup, the flood
codes are absent, unmapped index 19 and duplicate index 25 are omitted,
and exactly one command is marked destructive."
```

---

## Task 2: Sentinel value types

The bus reports out-of-range sentinels for conditions that are not numbers. Rendering them as numbers would mislead a driver about the state of the vehicle; rendering them as OFF would be worse. This task makes them unrepresentable as plain integers.

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/Values.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/ValuesTest.kt`

**Interfaces:**
- Consumes: nothing from Task 1 at compile time.
- Produces: `sealed interface Temp` with `Temp.Degrees(fahrenheit: Int)`, `Temp.Lo`, `Temp.Unavailable`, and `Temp.from(raw: Int?): Temp`. `sealed interface Fan` with `Fan.Level(step: Int)`, `Fan.Auto`, `Fan.Unavailable`, `Fan.from(raw: Int?): Fan`, and `Fan.MAX_STEP = 7`. `enum class SeatLevel { OFF, LOW, HIGH, UNAVAILABLE }` with `SeatLevel.from(raw: Int?): SeatLevel`.

- [ ] **Step 1: Write the failing test**

`bus/src/test/kotlin/com/wk2/climate/bus/ValuesTest.kt`:

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class ValuesTest {

    // ---- Temp ----

    @Test
    fun `a plain temperature reads as degrees`() {
        assertEquals(Temp.Degrees(68), Temp.from(68))
    }

    @Test
    fun `minus two is the LO sentinel, not a temperature below zero`() {
        assertEquals(Temp.Lo, Temp.from(-2))
    }

    @Test
    fun `other negatives are unavailable, never a number and never OFF`() {
        assertEquals(Temp.Unavailable, Temp.from(-1))
        assertEquals(Temp.Unavailable, Temp.from(-99))
    }

    @Test
    fun `a missing temperature is unavailable`() {
        assertEquals(Temp.Unavailable, Temp.from(null))
    }

    // ---- Fan ----

    @Test
    fun `fan levels one to seven read as levels`() {
        (1..7).forEach { assertEquals(Fan.Level(it), Fan.from(it)) }
    }

    @Test
    fun `fifteen is the AUTO sentinel, not a fan speed`() {
        // Measured on vehicle: with AUTO engaged the bus reports 15 while the
        // fan physically runs at 3. Rendering 15 as a level shows a speed that
        // does not exist.
        assertEquals(Fan.Auto, Fan.from(15))
    }

    @Test
    fun `fan level zero is a real level meaning off`() {
        assertEquals(Fan.Level(0), Fan.from(0))
    }

    @Test
    fun `fan values above the ceiling but below the sentinel are unavailable`() {
        assertEquals(Fan.Unavailable, Fan.from(8))
        assertEquals(Fan.Unavailable, Fan.from(14))
    }

    @Test
    fun `a missing fan value is unavailable`() {
        assertEquals(Fan.Unavailable, Fan.from(null))
    }

    @Test
    fun `the fan ceiling is seven`() {
        assertEquals(7, Fan.MAX_STEP)
    }

    // ---- SeatLevel ----

    @Test
    fun `seat levels map to the three presented states`() {
        assertEquals(SeatLevel.OFF, SeatLevel.from(0))
        assertEquals(SeatLevel.LOW, SeatLevel.from(1))
        assertEquals(SeatLevel.HIGH, SeatLevel.from(3))
    }

    @Test
    fun `seat level two maps to HIGH even though the cycle never reaches it`() {
        // Measured cycle is 0 -> 3 -> 1 -> 0; state 2 is never visited. Mapped
        // defensively because the cycle command is not necessarily the only writer.
        assertEquals(SeatLevel.HIGH, SeatLevel.from(2))
    }

    @Test
    fun `unknown seat values are unavailable`() {
        assertEquals(SeatLevel.UNAVAILABLE, SeatLevel.from(null))
        assertEquals(SeatLevel.UNAVAILABLE, SeatLevel.from(-1))
        assertEquals(SeatLevel.UNAVAILABLE, SeatLevel.from(4))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :bus:testDebugUnitTest --tests '*ValuesTest*'`
Expected: FAIL — unresolved references `Temp`, `Fan`, `SeatLevel`.

- [ ] **Step 3: Write `Values.kt`**

```kotlin
package com.wk2.climate.bus

/**
 * A zone setpoint temperature.
 *
 * The bus uses out-of-range sentinels for conditions that are not
 * temperatures. Those must render as a dimmed, non-committal state — never as
 * a number, and never as OFF.
 */
sealed interface Temp {
    data class Degrees(val fahrenheit: Int) : Temp
    /** The minimum / LO sentinel. Set by the MAX A/C macro among others. */
    data object Lo : Temp
    /** No value, or a sentinel we do not have a meaning for. */
    data object Unavailable : Temp

    companion object {
        const val SENTINEL_LO = -2

        fun from(raw: Int?): Temp = when {
            raw == null -> Unavailable
            raw == SENTINEL_LO -> Lo
            raw < 0 -> Unavailable
            else -> Degrees(raw)
        }
    }
}

/**
 * Blower level.
 *
 * The manual range is 0..7. **15 is not a fan speed** — it is the sentinel the
 * vehicle reports while AUTO owns the blower. Measured on vehicle: with AUTO
 * engaged the bus reported 15 while the first FAN_UP landed on 3, so 15 does
 * not even indicate a high speed.
 */
sealed interface Fan {
    data class Level(val step: Int) : Fan
    /** AUTO owns the blower. Render as AUTO, never as a number. */
    data object Auto : Fan
    data object Unavailable : Fan

    companion object {
        /** Measured on vehicle: FAN_UP clamps here and stops reporting updates. */
        const val MAX_STEP = 7
        const val SENTINEL_AUTO = 15

        fun from(raw: Int?): Fan = when {
            raw == null -> Unavailable
            raw == SENTINEL_AUTO -> Auto
            raw in 0..MAX_STEP -> Level(raw)
            else -> Unavailable
        }
    }
}

/**
 * Seat heat / ventilation level, as presented.
 *
 * The protocol range is 0..3, but the cycle command visits only 0, 3 and 1 —
 * measured on vehicle as `0 -> 3 -> 1 -> 0`. State 2 is unreachable through
 * the cycle, so three presented states lose nothing.
 */
enum class SeatLevel {
    OFF, LOW, HIGH, UNAVAILABLE;

    val isOn: Boolean get() = this == LOW || this == HIGH

    /** Number of lit pips in the two-pip indicator. */
    val litPips: Int get() = when (this) {
        OFF, UNAVAILABLE -> 0
        LOW -> 1
        HIGH -> 2
    }

    companion object {
        fun from(raw: Int?): SeatLevel = when (raw) {
            0 -> OFF
            1 -> LOW
            2, 3 -> HIGH
            else -> UNAVAILABLE
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :bus:testDebugUnitTest --tests '*ValuesTest*'`
Expected: PASS, 13 tests.

- [ ] **Step 5: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/Values.kt \
        bus/src/test/kotlin/com/wk2/climate/bus/ValuesTest.kt
git commit -m "feat(bus): sentinel-aware value types

Temp, Fan and SeatLevel replace raw ints so the bus's out-of-range
sentinels cannot be rendered as numbers. Fan 15 is AUTO rather than a
speed -- measured on vehicle the bus reported 15 while the blower ran at
3, so showing it as a level would show a speed that does not exist.
Temp -2 is the LO sentinel, and also turns up as unavailable on hardware
that is not fitted.

SeatLevel presents three states because the vehicle's own cycle visits
only 0, 3 and 1. Value 2 still maps to HIGH defensively, since the cycle
command is not necessarily the only writer."
```

---

## Task 3: Airflow mode derivation

Airflow arrives as three independent booleans, and only four of the eight representable combinations are named. A fifth combination — all zero — turns out to be the vehicle's resting state.

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/AirflowMode.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/AirflowModeTest.kt`

**Interfaces:**
- Consumes: `Command` from Task 1.
- Produces: `enum class AirflowMode { FACE, FACE_FEET, FEET, FEET_GLASS, NONE, UNKNOWN }` with `AirflowMode.from(up: Int?, body: Int?, foot: Int?): AirflowMode`, the property `isDriverSelected: Boolean`, the property `command: Command?`, and `AirflowMode.selectable: List<AirflowMode>`.

- [ ] **Step 1: Write the failing test**

`bus/src/test/kotlin/com/wk2/climate/bus/AirflowModeTest.kt`:

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirflowModeTest {

    @Test
    fun `the four named combinations map to the four modes`() {
        assertEquals(AirflowMode.FACE, AirflowMode.from(0, 1, 0))
        assertEquals(AirflowMode.FACE_FEET, AirflowMode.from(0, 1, 1))
        assertEquals(AirflowMode.FEET, AirflowMode.from(0, 0, 1))
        assertEquals(AirflowMode.FEET_GLASS, AirflowMode.from(1, 0, 1))
    }

    @Test
    fun `all flags clear is NONE, the resting state under AUTO`() {
        // Measured on vehicle: with U_AIR_AUTO = 1 all three flags read 0.
        // This is normal, not an error -- AUTO owns airflow and the driver has
        // selected nothing, so no tile should light.
        assertEquals(AirflowMode.NONE, AirflowMode.from(0, 0, 0))
    }

    @Test
    fun `unnamed combinations are UNKNOWN`() {
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(1, 1, 1))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(1, 1, 0))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(1, 0, 0))
    }

    @Test
    fun `a missing flag is UNKNOWN rather than a guess`() {
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(null, 1, 0))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(0, null, 0))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(0, 1, null))
    }

    @Test
    fun `only the four real modes count as driver selected`() {
        listOf(
            AirflowMode.FACE, AirflowMode.FACE_FEET,
            AirflowMode.FEET, AirflowMode.FEET_GLASS,
        ).forEach { assertTrue("$it should be driver selected", it.isDriverSelected) }

        assertFalse(AirflowMode.NONE.isDriverSelected)
        assertFalse(AirflowMode.UNKNOWN.isDriverSelected)
    }

    @Test
    fun `NONE and UNKNOWN both light no tile`() {
        // They render identically. The difference is only that UNKNOWN is worth
        // logging and NONE is not.
        assertFalse(AirflowMode.NONE.isDriverSelected)
        assertFalse(AirflowMode.UNKNOWN.isDriverSelected)
    }

    @Test
    fun `each selectable mode maps to its idempotent setter command`() {
        assertEquals(Command.AIRFLOW_FACE, AirflowMode.FACE.command)
        assertEquals(Command.AIRFLOW_FACE_FEET, AirflowMode.FACE_FEET.command)
        assertEquals(Command.AIRFLOW_FEET, AirflowMode.FEET.command)
        assertEquals(Command.AIRFLOW_FEET_GLASS, AirflowMode.FEET_GLASS.command)
    }

    @Test
    fun `NONE and UNKNOWN have no command - they are readings, not choices`() {
        assertNull(AirflowMode.NONE.command)
        assertNull(AirflowMode.UNKNOWN.command)
    }

    @Test
    fun `selectable lists exactly the four tiles in design order`() {
        assertEquals(
            listOf(
                AirflowMode.FACE,
                AirflowMode.FACE_FEET,
                AirflowMode.FEET,
                AirflowMode.FEET_GLASS,
            ),
            AirflowMode.selectable,
        )
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :bus:testDebugUnitTest --tests '*AirflowModeTest*'`
Expected: FAIL — unresolved reference `AirflowMode`.

- [ ] **Step 3: Write `AirflowMode.kt`**

```kotlin
package com.wk2.climate.bus

/**
 * Airflow destination.
 *
 * The bus does not expose this as an enum. It exposes three independent
 * booleans — `BLOW_UP`, `BLOW_BODY`, `BLOW_FOOT` — of which only four
 * combinations are named, plus all-clear.
 *
 * The write side *is* four discrete idempotent setters, which is what lets the
 * UI offer direct selection instead of the OEM bar's four-step cycle.
 */
enum class AirflowMode {
    FACE,
    FACE_FEET,
    FEET,
    FEET_GLASS,

    /**
     * No airflow flag set. **This is the vehicle's resting state while AUTO
     * owns airflow**, measured on vehicle with `U_AIR_AUTO = 1`. Expected and
     * correct: the driver has selected nothing, so no tile lights.
     */
    NONE,

    /**
     * A flag combination we do not recognise. Reachable after the front
     * defrost macro. Renders exactly like [NONE] — lighting a *wrong* tile is
     * worse than lighting none — but unlike NONE it is worth logging.
     */
    UNKNOWN,
    ;

    /** True only when the driver has actively chosen this mode. Drives tile fill. */
    val isDriverSelected: Boolean get() = this in selectable

    /** The idempotent setter for this mode, or null for the two readings. */
    val command: Command?
        get() = when (this) {
            FACE -> Command.AIRFLOW_FACE
            FACE_FEET -> Command.AIRFLOW_FACE_FEET
            FEET -> Command.AIRFLOW_FEET
            FEET_GLASS -> Command.AIRFLOW_FEET_GLASS
            NONE, UNKNOWN -> null
        }

    companion object {
        /** The four tiles, in the order screen 1d draws them. */
        val selectable: List<AirflowMode> = listOf(FACE, FACE_FEET, FEET, FEET_GLASS)

        fun from(up: Int?, body: Int?, foot: Int?): AirflowMode {
            if (up == null || body == null || foot == null) return UNKNOWN
            return when {
                up == 0 && body == 1 && foot == 0 -> FACE
                up == 0 && body == 1 && foot == 1 -> FACE_FEET
                up == 0 && body == 0 && foot == 1 -> FEET
                up == 1 && body == 0 && foot == 1 -> FEET_GLASS
                up == 0 && body == 0 && foot == 0 -> NONE
                else -> UNKNOWN
            }
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :bus:testDebugUnitTest --tests '*AirflowModeTest*'`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/AirflowMode.kt \
        bus/src/test/kotlin/com/wk2/climate/bus/AirflowModeTest.kt
git commit -m "feat(bus): derive airflow mode from the three flags

The bus exposes airflow as three independent booleans, not an enum, and
only four combinations are named. Two more outcomes matter:

NONE is all-flags-clear, which measurement showed is the vehicle's
resting state while AUTO owns airflow -- so four unlit tiles is the
correct rendering, not a bug. UNKNOWN is an unrecognised combination,
reachable after the defrost macro. Both light no tile; only UNKNOWN is
worth logging.

Each selectable mode carries its idempotent setter, so the UI can offer
direct selection rather than reproducing the OEM's four-step cycle."
```

---

## Task 4: The immutable state snapshot

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/ClimateState.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/ClimateStateTest.kt`

**Interfaces:**
- Consumes: `Signal`, `Temp`, `Fan`, `SeatLevel`, `AirflowMode`.
- Produces: `class ClimateState` with `with(signal: Signal, value: Int): ClimateState`, `operator fun get(signal: Signal): Int?`, `has(signal: Signal): Boolean`, and derived read-only properties: `tempLeft`, `tempRight`, `fan`, `airflow`, `seatHeatL`, `seatHeatR`, `seatVentL`, `seatVentR`, `acOn`, `autoOn`, `recircOn`, `maxAcOn`, `frontDefrostOn`, `rearDefrostOn`, `syncOn`, `wheelHeatOn`, `powerOn`, `volume`, `isNight`, `isEmpty`. Also `ClimateState.EMPTY`.

- [ ] **Step 1: Write the failing test**

`bus/src/test/kotlin/com/wk2/climate/bus/ClimateStateTest.kt`:

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateStateTest {

    @Test
    fun `an empty state reports every derived value as unavailable`() {
        val s = ClimateState.EMPTY
        assertTrue(s.isEmpty)
        assertEquals(Temp.Unavailable, s.tempLeft)
        assertEquals(Temp.Unavailable, s.tempRight)
        assertEquals(Fan.Unavailable, s.fan)
        assertEquals(AirflowMode.UNKNOWN, s.airflow)
        assertEquals(SeatLevel.UNAVAILABLE, s.seatHeatL)
        assertNull(s.volume)
    }

    @Test
    fun `an empty state reports flags as false rather than throwing`() {
        val s = ClimateState.EMPTY
        assertFalse(s.acOn)
        assertFalse(s.autoOn)
        assertFalse(s.powerOn)
        assertFalse(s.isNight)
    }

    @Test
    fun `with stores a raw value and derives from it`() {
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        assertEquals(68, s[Signal.TEMP_LEFT])
        assertEquals(Temp.Degrees(68), s.tempLeft)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `with returns the same instance when the value is unchanged`() {
        // The bus is push-on-change, but a re-registration replays current
        // values. Returning the identical instance keeps StateFlow from
        // re-emitting and re-composing the whole bar for nothing.
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        assertSame(s, s.with(Signal.TEMP_LEFT, 68))
    }

    @Test
    fun `with returns a new instance when the value changes`() {
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        val t = s.with(Signal.TEMP_LEFT, 69)
        assertEquals(Temp.Degrees(69), t.tempLeft)
        assertEquals("original must be untouched", Temp.Degrees(68), s.tempLeft)
    }

    @Test
    fun `the measured vehicle baseline derives correctly`() {
        // Exactly the snapshot captured from the vehicle on 2026-09-08.
        val s = ClimateState.EMPTY
            .with(Signal.POWER, 1)
            .with(Signal.AC, 1)
            .with(Signal.AUTO, 1)
            .with(Signal.SYNC, 1)
            .with(Signal.RECIRC, 0)
            .with(Signal.WIND_LEVEL, 15)
            .with(Signal.TEMP_LEFT, 68)
            .with(Signal.TEMP_RIGHT, 68)
            .with(Signal.BLOW_UP, 0)
            .with(Signal.BLOW_BODY, 0)
            .with(Signal.BLOW_FOOT, 0)
            .with(Signal.SEAT_HEAT_L, 0)
            .with(Signal.SEAT_HEAT_R, 0)
            .with(Signal.WHEEL_HEAT, 0)
            .with(Signal.VOLUME, 10)
            .with(Signal.ILLUMINATION, 0)

        assertTrue(s.powerOn)
        assertTrue(s.acOn)
        assertTrue(s.autoOn)
        assertTrue(s.syncOn)
        assertFalse(s.recircOn)
        assertEquals(Fan.Auto, s.fan)
        assertEquals(Temp.Degrees(68), s.tempLeft)
        assertEquals(Temp.Degrees(68), s.tempRight)
        assertEquals(AirflowMode.NONE, s.airflow)
        assertEquals(SeatLevel.OFF, s.seatHeatL)
        assertFalse(s.wheelHeatOn)
        assertEquals(10, s.volume)
        assertFalse("illumination 0 is day", s.isNight)
    }

    @Test
    fun `illumination one is night`() {
        assertTrue(ClimateState.EMPTY.with(Signal.ILLUMINATION, 1).isNight)
    }

    @Test
    fun `the MAX AC macro outcome renders as LO, not as a number`() {
        val s = ClimateState.EMPTY
            .with(Signal.TEMP_LEFT, -2)
            .with(Signal.TEMP_RIGHT, -2)
            .with(Signal.RECIRC, 1)
            .with(Signal.AC_MAX, 1)

        assertEquals(Temp.Lo, s.tempLeft)
        assertEquals(Temp.Lo, s.tempRight)
        assertTrue("the macro forces recirc on, and we must show it", s.recircOn)
        assertTrue(s.maxAcOn)
    }

    @Test
    fun `has distinguishes a missing signal from a zero one`() {
        val s = ClimateState.EMPTY.with(Signal.AC, 0)
        assertTrue(s.has(Signal.AC))
        assertFalse(s.has(Signal.AUTO))
        assertFalse(s.acOn)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :bus:testDebugUnitTest --tests '*ClimateStateTest*'`
Expected: FAIL — unresolved reference `ClimateState`.

- [ ] **Step 3: Write `ClimateState.kt`**

```kotlin
package com.wk2.climate.bus

/**
 * An immutable snapshot of everything the vehicle has told us.
 *
 * Raw values are kept in a map keyed by [Signal] and every meaningful value is
 * *derived*. That is deliberate:
 *
 *  - the UI renders entirely from this, so there is one place sentinels are
 *    interpreted and no opportunity to interpret them differently twice;
 *  - adding a signal does not mean editing a wide constructor;
 *  - protocol behaviour we do not model — seat heat and vent are mutually
 *    exclusive, the macros force recirculation on — appears on its own,
 *    because we never write local state and the vehicle reports the truth.
 */
class ClimateState private constructor(private val raw: Map<Signal, Int>) {

    /** Returns `this` unchanged if the value is already stored, so StateFlow does not re-emit. */
    fun with(signal: Signal, value: Int): ClimateState =
        if (raw[signal] == value) this else ClimateState(raw + (signal to value))

    operator fun get(signal: Signal): Int? = raw[signal]

    /** True if the vehicle has ever reported this signal. Distinguishes absent from zero. */
    fun has(signal: Signal): Boolean = raw.containsKey(signal)

    val isEmpty: Boolean get() = raw.isEmpty()

    // ---- derived: zone temperatures ----
    val tempLeft: Temp get() = Temp.from(raw[Signal.TEMP_LEFT])
    val tempRight: Temp get() = Temp.from(raw[Signal.TEMP_RIGHT])

    // ---- derived: blower ----
    val fan: Fan get() = Fan.from(raw[Signal.WIND_LEVEL])

    // ---- derived: airflow ----
    val airflow: AirflowMode
        get() = AirflowMode.from(
            up = raw[Signal.BLOW_UP],
            body = raw[Signal.BLOW_BODY],
            foot = raw[Signal.BLOW_FOOT],
        )

    // ---- derived: comfort ----
    val seatHeatL: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_HEAT_L])
    val seatHeatR: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_HEAT_R])
    val seatVentL: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_VENT_L])
    val seatVentR: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_VENT_R])
    val wheelHeatOn: Boolean get() = flag(Signal.WHEEL_HEAT)

    // ---- derived: mode toggles ----
    val acOn: Boolean get() = flag(Signal.AC)
    val autoOn: Boolean get() = flag(Signal.AUTO)
    val recircOn: Boolean get() = flag(Signal.RECIRC)
    val maxAcOn: Boolean get() = flag(Signal.AC_MAX)
    val frontDefrostOn: Boolean get() = flag(Signal.FRONT_DEFROST)
    val rearDefrostOn: Boolean get() = flag(Signal.REAR_DEFROST)

    /** The control the OEM labels DUAL. It drives SYNC, not `U_AIR_DUAL`. */
    val syncOn: Boolean get() = flag(Signal.SYNC)

    val powerOn: Boolean get() = flag(Signal.POWER)

    // ---- derived: other modules ----
    val volume: Int? get() = raw[Signal.VOLUME]

    /** Day/night follows the vehicle's illumination signal, never a clock. */
    val isNight: Boolean get() = flag(Signal.ILLUMINATION)

    private fun flag(signal: Signal): Boolean = raw[signal] == 1

    override fun equals(other: Any?): Boolean = other is ClimateState && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()
    override fun toString(): String =
        "ClimateState(" + raw.entries.joinToString { "${it.key}=${it.value}" } + ")"

    companion object {
        val EMPTY = ClimateState(emptyMap())

        fun of(vararg pairs: Pair<Signal, Int>): ClimateState =
            pairs.fold(EMPTY) { acc, (s, v) -> acc.with(s, v) }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :bus:testDebugUnitTest --tests '*ClimateStateTest*'`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/ClimateState.kt \
        bus/src/test/kotlin/com/wk2/climate/bus/ClimateStateTest.kt
git commit -m "feat(bus): immutable state snapshot with derived accessors

Raw signal values are stored in a map and everything meaningful is
derived, so sentinels are interpreted in exactly one place and adding a
signal does not mean editing a wide constructor.

with() returns the same instance when a value is unchanged. Registration
replays current values, so this keeps StateFlow from re-emitting and
recomposing the whole bar for nothing.

One test asserts against the exact snapshot captured from the vehicle,
including the AUTO resting state where fan reads the 15 sentinel and all
three airflow flags read zero."
```

---

## Task 5: The seam — `VehicleBus` and an adversarial fake

This is the task that makes off-vehicle development possible. The fake is not a stub that echoes what you set: it reproduces the vehicle's documented and measured misbehaviour, so UI bugs surface on an emulator instead of in a parked car.

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/VehicleBus.kt`
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/FakeVehicleBus.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/FakeVehicleBusTest.kt`

**Interfaces:**
- Consumes: `Signal`, `Command`, `ClimateState`, `Fan`, `SeatLevel`, `AirflowMode`.
- Produces: `interface VehicleBus { val state: StateFlow<ClimateState>; val connected: StateFlow<Boolean>; fun send(command: Command) }`. `class FakeVehicleBus(initial: ClimateState = VEHICLE_BASELINE)` implementing it, plus `FakeVehicleBus.VEHICLE_BASELINE: ClimateState`, `fun inject(signal: Signal, value: Int)`, `fun setConnected(value: Boolean)`, and `val sent: List<Command>`.

- [ ] **Step 1: Write the failing test**

`bus/src/test/kotlin/com/wk2/climate/bus/FakeVehicleBusTest.kt`:

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVehicleBusTest {

    private fun bus() = FakeVehicleBus()
    private val FakeVehicleBus.now get() = state.value

    @Test
    fun `it starts at the measured vehicle baseline`() {
        val s = bus().now
        assertTrue(s.powerOn)
        assertTrue(s.autoOn)
        assertEquals(Fan.Auto, s.fan)
        assertEquals(Temp.Degrees(68), s.tempLeft)
        assertEquals(AirflowMode.NONE, s.airflow)
    }

    // ---- seat heat: the measured 3-state cycle ----

    @Test
    fun `seat heat cycles off high low off, never visiting two`() {
        val b = bus()
        assertEquals(SeatLevel.OFF, b.now.seatHeatL)

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.HIGH, b.now.seatHeatL)
        assertEquals("raw must be 3", 3, b.now[Signal.SEAT_HEAT_L])

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.LOW, b.now.seatHeatL)
        assertEquals("raw must be 1, skipping 2", 1, b.now[Signal.SEAT_HEAT_L])

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.OFF, b.now.seatHeatL)
    }

    @Test
    fun `every seat heat tap changes the presented state`() {
        // This is the regression guard for the double-send workaround that was
        // specified and then removed. If a tap ever produces no visible change,
        // this fails.
        val b = bus()
        var previous = b.now.seatHeatL
        repeat(6) {
            b.send(Command.SEAT_HEAT_L)
            assertNotEquals("a tap must always change the presented state", previous, b.now.seatHeatL)
            previous = b.now.seatHeatL
        }
    }

    @Test
    fun `turning seat heat on zeroes seat vent, as the protocol does`() {
        val b = bus()
        b.inject(Signal.SEAT_VENT_L, 3)
        assertEquals(SeatLevel.HIGH, b.now.seatVentL)

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.HIGH, b.now.seatHeatL)
        assertEquals("mutual exclusion is enforced in the protocol", SeatLevel.OFF, b.now.seatVentL)
    }

    // ---- fan ----

    @Test
    fun `the first fan up exits AUTO and lands on three`() {
        val b = bus()
        b.send(Command.FAN_UP)
        assertFalse("AUTO must clear", b.now.autoOn)
        assertEquals(Fan.Level(3), b.now.fan)
        assertEquals("leaving AUTO materialises FACE", AirflowMode.FACE, b.now.airflow)
    }

    @Test
    fun `fan climbs to seven then clamps`() {
        val b = bus()
        repeat(10) { b.send(Command.FAN_UP) }
        assertEquals(Fan.Level(7), b.now.fan)
    }

    @Test
    fun `fan down stops at zero`() {
        val b = bus()
        repeat(12) { b.send(Command.FAN_DOWN) }
        assertEquals(Fan.Level(0), b.now.fan)
    }

    @Test
    fun `AUTO restores the baseline in one command`() {
        val b = bus()
        repeat(4) { b.send(Command.FAN_UP) }
        b.send(Command.AUTO)
        assertTrue(b.now.autoOn)
        assertTrue("the AUTO macro also forces A C on", b.now.acOn)
        assertEquals(Fan.Auto, b.now.fan)
        assertEquals("the AUTO macro clears body blow", AirflowMode.NONE, b.now.airflow)
    }

    // ---- airflow ----

    @Test
    fun `airflow setters are idempotent`() {
        val b = bus()
        b.send(Command.AIRFLOW_FEET)
        val once = b.now
        b.send(Command.AIRFLOW_FEET)
        assertEquals("re-tapping the active mode must be a no-op", once, b.now)
        assertEquals(AirflowMode.FEET, b.now.airflow)
    }

    @Test
    fun `each airflow setter selects exactly its own mode`() {
        AirflowMode.selectable.forEach { mode ->
            val b = bus()
            b.send(mode.command!!)
            assertEquals(mode, b.now.airflow)
        }
    }

    // ---- macros ----

    @Test
    fun `MAX AC drives temperatures to the LO sentinel and forces recirc`() {
        val b = bus()
        b.send(Command.MAX_AC)
        assertEquals(Temp.Lo, b.now.tempLeft)
        assertEquals(Temp.Lo, b.now.tempRight)
        assertTrue(b.now.recircOn)
        assertTrue(b.now.maxAcOn)
    }

    @Test
    fun `front defrost forces recirc on and leaves airflow unrecognised`() {
        val b = bus()
        b.send(Command.FRONT_DEFROST)
        assertTrue(b.now.frontDefrostOn)
        assertTrue(b.now.recircOn)
        assertEquals("this is what UNKNOWN exists for", AirflowMode.UNKNOWN, b.now.airflow)
    }

    // ---- temperature and volume ----

    @Test
    fun `temperature steps one degree per command per zone`() {
        val b = bus()
        b.send(Command.TEMP_L_UP)
        assertEquals(Temp.Degrees(69), b.now.tempLeft)
        assertEquals("the other zone must not move", Temp.Degrees(68), b.now.tempRight)
        b.send(Command.TEMP_L_DOWN)
        assertEquals(Temp.Degrees(68), b.now.tempLeft)
    }

    @Test
    fun `volume steps and never goes negative`() {
        val b = bus()
        b.send(Command.VOL_UP)
        assertEquals(11, b.now.volume)
        repeat(30) { b.send(Command.VOL_DOWN) }
        assertEquals(0, b.now.volume)
    }

    @Test
    fun `hiding the volume OSD changes no state`() {
        val b = bus()
        val before = b.now
        b.send(Command.VOL_HIDE_OSD)
        assertEquals(before, b.now)
    }

    // ---- observability ----

    @Test
    fun `it records what was sent, so UI tests can assert dispatch`() {
        val b = bus()
        b.send(Command.AC)
        b.send(Command.SYNC)
        assertEquals(listOf(Command.AC, Command.SYNC), b.sent)
    }

    @Test
    fun `connection state is observable and settable`() {
        val b = bus()
        assertTrue(b.connected.value)
        b.setConnected(false)
        assertFalse(b.connected.value)
    }

    @Test
    fun `commands are ignored while disconnected`() {
        val b = bus()
        b.setConnected(false)
        val before = b.now
        b.send(Command.AC)
        assertEquals(before, b.now)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :bus:testDebugUnitTest --tests '*FakeVehicleBusTest*'`
Expected: FAIL — unresolved references `VehicleBus`, `FakeVehicleBus`.

- [ ] **Step 3: Write `VehicleBus.kt`**

```kotlin
package com.wk2.climate.bus

import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the UI and the vehicle.
 *
 * State is owned by the vehicle, not by us. A tap dispatches a [Command] and
 * mutates nothing locally; the UI updates when the vehicle reports the new
 * value through [state]. That single rule is what makes the protocol's
 * side effects — mutual exclusion, macro fallout, AUTO clearing itself when
 * the fan moves — appear correctly with no special-casing.
 */
interface VehicleBus {

    /** The full snapshot. Seeded on subscription, then updated on change. */
    val state: StateFlow<ClimateState>

    /** Whether we currently hold a live connection to the vendor service. */
    val connected: StateFlow<Boolean>

    /** Dispatch a write. Never update [state] optimistically in response. */
    fun send(command: Command)
}
```

- [ ] **Step 4: Write `FakeVehicleBus.kt`**

```kotlin
package com.wk2.climate.bus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-memory [VehicleBus] for development and tests.
 *
 * This is **deliberately adversarial**. A fake that simply echoes what you set
 * proves nothing. Every rule below is documented or measured vehicle
 * behaviour, so a UI that works against this fake is a UI that will not be
 * surprised by the car:
 *
 *  - seat heat cycles `0 -> 3 -> 1 -> 0`, skipping 2 (measured)
 *  - the AUTO macro forces A/C on, clears body-blow and sets fan to 15
 *  - `FAN_UP` exits AUTO as a side effect, landing on level 3 (measured)
 *  - fan clamps at 7 and reports nothing further (measured)
 *  - `FRONT_DEFROST` and `MAX_AC` force recirculation on
 *  - `MAX_AC` drives both temperatures to the LO sentinel
 *  - `FRONT_DEFROST` leaves airflow in an unrecognised combination
 *  - seat heat and seat vent are mutually exclusive
 *  - airflow setters are idempotent
 */
class FakeVehicleBus(initial: ClimateState = VEHICLE_BASELINE) : VehicleBus {

    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<ClimateState> = _state.asStateFlow()

    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _sent = mutableListOf<Command>()

    /** Everything dispatched so far, so a UI test can assert on dispatch. */
    val sent: List<Command> get() = _sent.toList()

    /** Push a value as though the vehicle reported it. For exercising awkward states. */
    fun inject(signal: Signal, value: Int) {
        _state.value = _state.value.with(signal, value)
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    override fun send(command: Command) {
        if (!_connected.value) return
        _sent += command
        _state.value = apply(_state.value, command)
    }

    private fun apply(s: ClimateState, command: Command): ClimateState = when (command) {
        Command.AC -> s.toggle(Signal.AC)
        Command.RECIRC -> s.toggle(Signal.RECIRC)
        Command.SYNC -> s.toggle(Signal.SYNC)
        Command.REAR_DEFROST -> s.toggle(Signal.REAR_DEFROST)
        Command.WHEEL_HEAT -> s.toggle(Signal.WHEEL_HEAT)

        // Macro: A/C on, body-blow cleared, fan to the AUTO sentinel.
        Command.AUTO -> s
            .with(Signal.AUTO, 1)
            .with(Signal.AC, 1)
            .with(Signal.WIND_LEVEL, Fan.SENTINEL_AUTO)
            .with(Signal.BLOW_UP, 0)
            .with(Signal.BLOW_BODY, 0)
            .with(Signal.BLOW_FOOT, 0)

        Command.FAN_UP -> fanUp(s)
        Command.FAN_DOWN -> fanDown(s)

        Command.AIRFLOW_FACE -> s.airflow(up = 0, body = 1, foot = 0)
        Command.AIRFLOW_FACE_FEET -> s.airflow(up = 0, body = 1, foot = 1)
        Command.AIRFLOW_FEET -> s.airflow(up = 0, body = 0, foot = 1)
        Command.AIRFLOW_FEET_GLASS -> s.airflow(up = 1, body = 0, foot = 1)

        // Macro: recirc forced on, fan to 6, airflow left unrecognised.
        Command.FRONT_DEFROST -> s
            .toggle(Signal.FRONT_DEFROST)
            .with(Signal.RECIRC, 1)
            .with(Signal.AUTO, 0)
            .with(Signal.WIND_LEVEL, 6)
            .airflow(up = 1, body = 1, foot = 0)

        // Macro: temperatures to the LO sentinel, recirc forced on.
        Command.MAX_AC -> s
            .toggle(Signal.AC_MAX)
            .with(Signal.AC, 1)
            .with(Signal.RECIRC, 1)
            .with(Signal.TEMP_LEFT, Temp.SENTINEL_LO)
            .with(Signal.TEMP_RIGHT, Temp.SENTINEL_LO)

        Command.CLIMATE_POWER -> s.toggle(Signal.POWER)

        Command.TEMP_L_UP -> s.stepTemp(Signal.TEMP_LEFT, +1)
        Command.TEMP_L_DOWN -> s.stepTemp(Signal.TEMP_LEFT, -1)
        Command.TEMP_R_UP -> s.stepTemp(Signal.TEMP_RIGHT, +1)
        Command.TEMP_R_DOWN -> s.stepTemp(Signal.TEMP_RIGHT, -1)

        Command.SEAT_HEAT_L -> s.cycleSeat(Signal.SEAT_HEAT_L, Signal.SEAT_VENT_L)
        Command.SEAT_HEAT_R -> s.cycleSeat(Signal.SEAT_HEAT_R, Signal.SEAT_VENT_R)
        Command.SEAT_VENT_L -> s.cycleSeat(Signal.SEAT_VENT_L, Signal.SEAT_HEAT_L)
        Command.SEAT_VENT_R -> s.cycleSeat(Signal.SEAT_VENT_R, Signal.SEAT_HEAT_R)

        Command.VOL_UP -> s.stepVolume(+1)
        Command.VOL_DOWN -> s.stepVolume(-1)
        Command.VOL_HIDE_OSD -> s   // affects the OEM overlay only, no state
    }

    // ---- behaviour helpers ----

    private fun fanUp(s: ClimateState): ClimateState {
        // Measured: the first press exits AUTO and lands on 3, materialising FACE.
        if (s.autoOn) {
            return s.with(Signal.AUTO, 0)
                .with(Signal.WIND_LEVEL, 3)
                .airflow(up = 0, body = 1, foot = 0)
        }
        val current = s[Signal.WIND_LEVEL] ?: 0
        // Measured: clamps at 7 and reports no further update.
        if (current >= Fan.MAX_STEP) return s
        return s.with(Signal.WIND_LEVEL, current + 1)
    }

    private fun fanDown(s: ClimateState): ClimateState {
        if (s.autoOn) {
            return s.with(Signal.AUTO, 0)
                .with(Signal.WIND_LEVEL, 3)
                .airflow(up = 0, body = 1, foot = 0)
        }
        val current = s[Signal.WIND_LEVEL] ?: 0
        if (current <= 0) return s
        return s.with(Signal.WIND_LEVEL, current - 1)
    }

    private fun ClimateState.toggle(signal: Signal): ClimateState =
        with(signal, if (this[signal] == 1) 0 else 1)

    private fun ClimateState.airflow(up: Int, body: Int, foot: Int): ClimateState =
        with(Signal.BLOW_UP, up).with(Signal.BLOW_BODY, body).with(Signal.BLOW_FOOT, foot)

    private fun ClimateState.stepTemp(signal: Signal, delta: Int): ClimateState {
        val current = this[signal] ?: return this
        // Stepping out of the LO sentinel returns to the bottom of the range.
        if (current == Temp.SENTINEL_LO) {
            return if (delta > 0) with(signal, TEMP_MIN) else this
        }
        return with(signal, (current + delta).coerceIn(TEMP_MIN, TEMP_MAX))
    }

    private fun ClimateState.cycleSeat(level: Signal, opposite: Signal): ClimateState {
        // Measured cycle: 0 -> 3 -> 1 -> 0. State 2 is never visited.
        val next = when (this[level] ?: 0) {
            0 -> 3
            3 -> 1
            else -> 0
        }
        val stepped = with(level, next)
        // Mutual exclusion is enforced in the protocol, not just the UI.
        return if (next != 0) stepped.with(opposite, 0) else stepped
    }

    private fun ClimateState.stepVolume(delta: Int): ClimateState {
        val current = this[Signal.VOLUME] ?: 0
        return with(Signal.VOLUME, (current + delta).coerceIn(0, VOLUME_MAX))
    }

    companion object {
        const val TEMP_MIN = 60
        const val TEMP_MAX = 85
        const val VOLUME_MAX = 40

        /**
         * The exact snapshot captured from the vehicle on 2026-09-08: engine
         * accessory on, AUTO engaged, both zones at 68 F, fan reporting the
         * AUTO sentinel, and all three airflow flags clear.
         */
        val VEHICLE_BASELINE: ClimateState = ClimateState.of(
            Signal.POWER to 1,
            Signal.AC to 1,
            Signal.AUTO to 1,
            Signal.SYNC to 1,
            Signal.RECIRC to 0,
            Signal.AC_MAX to 0,
            Signal.FRONT_DEFROST to 0,
            Signal.REAR_DEFROST to 0,
            Signal.WIND_LEVEL to Fan.SENTINEL_AUTO,
            Signal.TEMP_LEFT to 68,
            Signal.TEMP_RIGHT to 68,
            Signal.TEMP_UNIT to 1,
            Signal.BLOW_UP to 0,
            Signal.BLOW_BODY to 0,
            Signal.BLOW_FOOT to 0,
            Signal.SEAT_HEAT_L to 0,
            Signal.SEAT_HEAT_R to 0,
            Signal.SEAT_VENT_L to 0,
            Signal.SEAT_VENT_R to 0,
            Signal.WHEEL_HEAT to 0,
            Signal.VOLUME to 10,
            Signal.ILLUMINATION to 0,
        )
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew :bus:testDebugUnitTest --tests '*FakeVehicleBusTest*'`
Expected: PASS, 18 tests.

- [ ] **Step 6: Run the whole suite**

Run: `./gradlew :bus:test`
Expected: PASS, 59 tests total (AdaptiveSlot's 12 arrive in Task 6).

- [ ] **Step 7: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/VehicleBus.kt \
        bus/src/main/kotlin/com/wk2/climate/bus/FakeVehicleBus.kt \
        bus/src/test/kotlin/com/wk2/climate/bus/FakeVehicleBusTest.kt
git commit -m "feat(bus): VehicleBus seam and an adversarial fake

VehicleBus is the interface the UI talks to, so screens can be built and
tested with no vehicle attached.

FakeVehicleBus deliberately misbehaves the way the car does rather than
echoing what you set, because a well-behaved fake proves nothing. It
models the measured seat cycle that skips state 2, FAN_UP exiting AUTO
onto level 3, the clamp at 7 that reports no further update, both macros
forcing recirculation on, MAX A/C discarding setpoints to the LO
sentinel, defrost leaving airflow unrecognised, and seat heat/vent
mutual exclusion.

Its starting state is the exact snapshot captured from the vehicle."
```

---

## Task 6: The adaptive slot state machine

Screen 2a has exactly one region whose contents change. Everything about *when* it changes is timing logic that would be miserable to verify by sitting in a cold car, so it is pure Kotkin with an injected clock.

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/AdaptiveSlot.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/AdaptiveSlotTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `enum class SlotContent { FRONT_DEFROST, SEAT_HEAT, SEAT_COOL }`. `class AdaptiveSlot(deadbandF: Int = 3, dwellMillis: Long = 30_000, tapLockoutMillis: Long = 1_000, initial: SlotContent = SlotContent.SEAT_HEAT)` with `fun update(outsideF: Int?, nowMillis: Long): SlotContent`, `fun onTap(nowMillis: Long)`, `fun onFingerDown()`, `fun onFingerUp()`, and `val content: SlotContent`.

- [ ] **Step 1: Write the failing test**

`bus/src/test/kotlin/com/wk2/climate/bus/AdaptiveSlotTest.kt`:

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveSlotTest {

    private val dwell = 30_000L

    /** Advances past the dwell window so a legitimate change is allowed. */
    private fun AdaptiveSlot.settle(outsideF: Int?, at: Long): SlotContent =
        update(outsideF, at)

    @Test
    fun `it starts on seat heat, the safe middle band`() {
        assertEquals(SlotContent.SEAT_HEAT, AdaptiveSlot().content)
    }

    @Test
    fun `a null outside temperature pins to seat heat`() {
        // This is the shipping path until U_TEMP_OUT is decoded. The slot must
        // never be blank and the geometry must never change.
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.SEAT_HEAT, slot.update(null, 0))
        assertEquals(SlotContent.SEAT_HEAT, slot.update(null, 10 * dwell))
    }

    @Test
    fun `below freezing promotes front defrost`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, dwell + 1))
    }

    @Test
    fun `hot promotes seat cool`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.SEAT_COOL, slot.update(95, dwell + 1))
    }

    @Test
    fun `the comfortable band holds seat heat`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.SEAT_HEAT, slot.update(60, dwell + 1))
    }

    // ---- hysteresis ----

    @Test
    fun `a vehicle sitting exactly at freezing does not flip back and forth`() {
        // The whole point of the deadband. Without it, 32 / 31 / 32 / 31 would
        // swap the control under the driver's thumb repeatedly.
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(31, t))

        // Crossing back above 32 is not enough; it must clear 32 + 3.
        t += dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(33, t))
        t += dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(34, t))
        t += dwell + 1
        assertEquals(SlotContent.SEAT_HEAT, slot.update(36, t))
    }

    @Test
    fun `leaving seat cool requires clearing the deadband downward`() {
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotContent.SEAT_COOL, slot.update(90, t))
        t += dwell + 1
        assertEquals("84 is inside the deadband", SlotContent.SEAT_COOL, slot.update(84, t))
        t += dwell + 1
        assertEquals(SlotContent.SEAT_HEAT, slot.update(81, t))
    }

    // ---- dwell ----

    @Test
    fun `it will not change again inside the dwell window`() {
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, t))

        // A big swing arriving too soon is held.
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(95, t + 1_000))
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(95, t + dwell - 1))
        assertEquals(SlotContent.SEAT_COOL, slot.update(95, t + dwell + 1))
    }

    // ---- touch safety ----

    @Test
    fun `it never changes while a finger is down`() {
        val slot = AdaptiveSlot()
        slot.onFingerDown()
        assertEquals(SlotContent.SEAT_HEAT, slot.update(20, 10 * dwell))
        slot.onFingerUp()
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, 20 * dwell))
    }

    @Test
    fun `it never changes within a second of a tap`() {
        // Swapping the control immediately after it is used would mean the
        // driver's next tap hits something they did not aim at.
        val slot = AdaptiveSlot()
        val t = 10 * dwell
        slot.onTap(t)
        assertEquals(SlotContent.SEAT_HEAT, slot.update(20, t + 500))
        assertEquals(SlotContent.SEAT_HEAT, slot.update(20, t + 999))
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, t + 1_001))
    }

    @Test
    fun `defrost outranks comfort even in a hot swing from cold`() {
        val slot = AdaptiveSlot(initial = SlotContent.SEAT_COOL)
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(10, dwell + 1))
    }

    @Test
    fun `an unchanged target does not restart the dwell clock`() {
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, t))
        // Repeatedly reporting the same band must not extend the lockout.
        repeat(5) { slot.update(20, t + it * 100L) }
        assertEquals(SlotContent.SEAT_COOL, slot.update(95, t + dwell + 1))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :bus:testDebugUnitTest --tests '*AdaptiveSlotTest*'`
Expected: FAIL — unresolved references `AdaptiveSlot`, `SlotContent`.

- [ ] **Step 3: Write `AdaptiveSlot.kt`**

```kotlin
package com.wk2.climate.bus

/** What the one variable region of screen 2a is currently holding. */
enum class SlotContent { FRONT_DEFROST, SEAT_HEAT, SEAT_COOL }

/**
 * Decides the contents of screen 2a's single adaptive slot.
 *
 * Screen 2a has fixed geometry so controls can be found by feel. Exactly one
 * 200x96 region ever changes what it holds, and this decides when. Three rules
 * matter more than the thresholds:
 *
 *  - **Hysteresis.** A vehicle sitting at the boundary must not swap the
 *    control repeatedly. A band must be cleared by [deadbandF] to be left.
 *  - **Dwell.** At most one change per [dwellMillis].
 *  - **Touch safety.** Never change under a finger, and never within
 *    [tapLockoutMillis] of a tap — otherwise the driver's next press lands on
 *    something they did not aim at.
 *
 * Pure Kotlin with an injected clock, so all of it is unit-tested against a
 * synthetic temperature series rather than by sitting in a cold car.
 *
 * A null outside temperature pins to [SlotContent.SEAT_HEAT]. That is the
 * current shipping path: `U_TEMP_OUT` has not been decoded, and the slot must
 * never be blank nor change the bar's geometry.
 */
class AdaptiveSlot(
    private val deadbandF: Int = 3,
    private val dwellMillis: Long = 30_000L,
    private val tapLockoutMillis: Long = 1_000L,
    initial: SlotContent = SlotContent.SEAT_HEAT,
) {
    var content: SlotContent = initial
        private set

    private var lastChangeAt = Long.MIN_VALUE / 4
    private var lockedUntil = Long.MIN_VALUE / 4
    private var fingerDown = false

    fun onFingerDown() { fingerDown = true }

    fun onFingerUp() { fingerDown = false }

    fun onTap(nowMillis: Long) {
        lockedUntil = nowMillis + tapLockoutMillis
    }

    /**
     * Re-evaluate against the current outside temperature and return what the
     * slot should hold. Safe to call as often as you like.
     */
    fun update(outsideF: Int?, nowMillis: Long): SlotContent {
        val target = if (outsideF == null) SlotContent.SEAT_HEAT else targetFor(outsideF)

        if (target == content) return content
        if (fingerDown) return content
        if (nowMillis < lockedUntil) return content
        if (nowMillis - lastChangeAt < dwellMillis) return content

        content = target
        lastChangeAt = nowMillis
        return content
    }

    /**
     * The band for a temperature, biased to keep the current content.
     *
     * The deadband is applied **on exit only**. Entering a band happens at its
     * nominal threshold (32 F, 85 F); leaving it requires clearing that
     * threshold by [deadbandF]. Applying the band on entry as well would push
     * the effective thresholds to 29 F and 88 F, which is not what the design
     * specifies.
     *
     * Defrost outranks comfort: below freezing, clearing glass wins.
     */
    private fun targetFor(t: Int): SlotContent = when (content) {
        // Committed to defrost — must clear 35 F to leave.
        SlotContent.FRONT_DEFROST ->
            if (t >= FREEZING + deadbandF) bandOf(t) else SlotContent.FRONT_DEFROST

        // Committed to cooling — must fall to 82 F to leave.
        SlotContent.SEAT_COOL ->
            if (t <= HOT - deadbandF) bandOf(t) else SlotContent.SEAT_COOL

        // Neutral — enter either extreme at its nominal threshold.
        SlotContent.SEAT_HEAT -> bandOf(t)
    }

    private fun bandOf(t: Int): SlotContent = when {
        t < FREEZING -> SlotContent.FRONT_DEFROST
        t > HOT -> SlotContent.SEAT_COOL
        else -> SlotContent.SEAT_HEAT
    }

    companion object {
        const val FREEZING = 32
        const val HOT = 85
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :bus:testDebugUnitTest --tests '*AdaptiveSlotTest*'`
Expected: PASS, 12 tests.

- [ ] **Step 5: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/AdaptiveSlot.kt \
        bus/src/test/kotlin/com/wk2/climate/bus/AdaptiveSlotTest.kt
git commit -m "feat(bus): adaptive slot state machine

Decides the contents of screen 2a's one variable region. The thresholds
matter less than the three safety rules: a 3 F deadband so a vehicle
sitting at freezing does not swap the control repeatedly, a 30 s dwell so
at most one change happens per window, and a touch lockout so the slot
never changes under a finger or within a second of a tap -- otherwise the
driver's next press lands on a control they did not aim at.

Pure Kotlin with an injected clock, so the timing is verified against a
synthetic series instead of by sitting in a cold car.

A null outside temperature pins to SEAT HEAT, which is the shipping path
while U_TEMP_OUT remains undecoded. The slot is never blank and the bar's
geometry never changes."
```

---

## Task 7: The real bus — Binder proxies against the vendor AIDL

The vendor framework is reached with hand-written Binder proxies. Transaction ids are assigned by declaration order, so a plausible-looking guess calls the wrong method; the values below are recovered from the vendor APK and verified on hardware.

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/SyuVehicleBus.kt`
- Modify: `bus/build.gradle.kts` (no change needed — listed for clarity)

**Interfaces:**
- Consumes: `VehicleBus`, `Signal`, `Command`, `ClimateState`.
- Produces: `class SyuVehicleBus(context: Context)` implementing `VehicleBus`, plus `fun connect()` and `fun disconnect()`.

There is no unit test for this task: it is pure platform-boundary code whose only meaningful verification is on the vehicle. It is exercised by Task 9's harness in fake mode and validated on hardware at the start of Plan 2. Keeping it free of derivation logic is what makes that acceptable — every decision it could get wrong lives in Tasks 2–4, which are tested.

- [ ] **Step 1: Write `SyuVehicleBus.kt`**

```kotlin
package com.wk2.climate.bus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real [VehicleBus], talking to the SYU vendor IPC framework.
 *
 * The framework is a three-interface AIDL surface in `com.syu.ipc` and is
 * **not permission-gated** — an ordinary third-party app may bind it and both
 * read and write. Proxies are hand-written because we do not have the AIDL.
 *
 * Transaction ids are assigned by declaration order, so guessing calls the
 * wrong method. These are recovered from the vendor APK and verified live:
 *
 * ```
 * IRemoteToolkit  getRemoteModule = 1
 * IRemoteModule   cmd = 1, get = 2, register = 3, unregister = 4
 * IModuleCallback update = 1
 * ```
 *
 * Two things are easy to get wrong and are handled here:
 *
 *  - **One callback per module.** `update()` carries the code but not the
 *    module, and code 2 is `U_STANDBY` on MAIN and `U_VOL` on SOUND. A shared
 *    callback cannot tell them apart.
 *  - **Package visibility.** The `<queries>` entry in this module's manifest
 *    is required or `bindService` fails *silently* on Android 11+ even though
 *    the vendor service is exported.
 */
class SyuVehicleBus(private val context: Context) : VehicleBus {

    private val _state = MutableStateFlow(ClimateState.EMPTY)
    override val state: StateFlow<ClimateState> = _state.asStateFlow()

    private val _connected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Module id -> the module's remote binder. */
    private val modules = mutableMapOf<Int, IBinder>()

    /** Module id -> the callback registered against it. Held to keep it alive. */
    private val callbacks = mutableMapOf<Int, ModuleCallback>()

    private var toolkit: IBinder? = null

    fun connect() {
        val intent = Intent(TOOLKIT_ACTION).setPackage(VENDOR_PACKAGE)
        val requested = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!requested) {
            Log.e(TAG, "bindService refused — check the <queries> package visibility entry")
        }
    }

    fun disconnect() {
        unregisterAll()
        runCatching { context.unbindService(serviceConnection) }
        modules.clear()
        toolkit = null
        _connected.value = false
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            toolkit = service
            modules.clear()
            for (module in Signal.modules()) {
                val binder = runCatching { getRemoteModule(service, module) }.getOrNull()
                if (binder == null) {
                    Log.e(TAG, "module $module unavailable")
                    continue
                }
                modules[module] = binder
            }
            registerAll()
            _connected.value = modules.isNotEmpty()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _connected.value = false
            modules.clear()
            callbacks.clear()
            toolkit = null
        }
    }

    // ---- IRemoteToolkit.getRemoteModule(int) = txn 1 ----

    private fun getRemoteModule(toolkit: IBinder, moduleId: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(TOOLKIT_DESC)
            data.writeInt(moduleId)
            toolkit.transact(TXN_GET_MODULE, data, reply, 0)
            reply.readException()
            reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // ---- IRemoteModule.register(cb, code, flag) = txn 3 ----

    private fun registerAll() {
        for ((module, binder) in modules) {
            val callback = callbacks.getOrPut(module) { ModuleCallback(module) }
            var ok = 0
            for (signal in Signal.inModule(module)) {
                val success = runCatching { register(binder, callback, signal.code) }.isSuccess
                if (success) ok++ else Log.w(TAG, "register failed for $signal")
            }
            Log.i(TAG, "module $module: registered $ok/${Signal.inModule(module).size} codes")
        }
    }

    private fun register(module: IBinder, callback: ModuleCallback, code: Int) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MODULE_DESC)
            data.writeStrongBinder(callback.asBinder())
            data.writeInt(code)
            data.writeInt(REGISTER_FLAG)
            module.transact(TXN_REGISTER, data, reply, 0)
            reply.readException()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun unregisterAll() {
        for ((module, binder) in modules) {
            val callback = callbacks[module] ?: continue
            for (signal in Signal.inModule(module)) {
                runCatching {
                    val data = Parcel.obtain()
                    val reply = Parcel.obtain()
                    try {
                        data.writeInterfaceToken(MODULE_DESC)
                        data.writeStrongBinder(callback.asBinder())
                        data.writeInt(signal.code)
                        binder.transact(TXN_UNREGISTER, data, reply, 0)
                        reply.readException()
                    } finally {
                        reply.recycle()
                        data.recycle()
                    }
                }
            }
        }
        callbacks.clear()
    }

    // ---- IRemoteModule.cmd(code, int[], float[], String[]) = txn 1 ----

    override fun send(command: Command) {
        val binder = modules[command.module]
        if (binder == null) {
            Log.w(TAG, "dropping $command — module ${command.module} not bound")
            return
        }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(MODULE_DESC)
            data.writeInt(command.code)
            data.writeIntArray(command.payload)
            data.writeFloatArray(null)
            data.writeStringArray(null)
            binder.transact(TXN_CMD, data, reply, 0)
            reply.readException()
        } catch (t: Throwable) {
            Log.e(TAG, "send $command failed", t)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // ---- IModuleCallback.update(code, int[], float[], String[]) = txn 1 ----

    /**
     * One instance per module. The module id is captured here because the
     * vendor callback does not carry it, and codes are unique only within a
     * module.
     */
    private inner class ModuleCallback(private val module: Int) : Binder(), IInterface {

        init {
            attachInterface(this, CALLBACK_DESC)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(CALLBACK_DESC)
                return true
            }
            if (code != TXN_UPDATE) return super.onTransact(code, data, reply, flags)

            data.enforceInterface(CALLBACK_DESC)
            val signalCode = data.readInt()
            val ints = data.createIntArray()
            // Read the remaining parameters even though we do not use them:
            // the parcel must be consumed in declaration order.
            data.createFloatArray()
            data.createStringArray()

            onUpdate(module, signalCode, ints)
            reply?.writeNoException()
            return true
        }
    }

    private fun onUpdate(module: Int, code: Int, ints: IntArray?) {
        val signal = Signal.of(module, code) ?: return
        val value = ints?.firstOrNull() ?: return
        _state.value = _state.value.with(signal, value)
    }

    companion object {
        private const val TAG = "wk2-bus"

        private const val VENDOR_PACKAGE = "com.syu.ms"
        private const val TOOLKIT_ACTION = "com.syu.ms.toolkit"

        private const val TOOLKIT_DESC = "com.syu.ipc.IRemoteToolkit"
        private const val MODULE_DESC = "com.syu.ipc.IRemoteModule"
        private const val CALLBACK_DESC = "com.syu.ipc.IModuleCallback"

        private const val TXN_GET_MODULE = 1
        private const val TXN_CMD = 1
        private const val TXN_REGISTER = 3
        private const val TXN_UNREGISTER = 4
        private const val TXN_UPDATE = 1

        /** The flag value the OEM's own client passes. */
        private const val REGISTER_FLAG = 1
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :bus:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify the whole test suite still passes**

Run: `./gradlew :bus:test`
Expected: PASS, 71 tests. (No new tests — see the note above this task.)

- [ ] **Step 4: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/SyuVehicleBus.kt
git commit -m "feat(bus): real bus over the vendor AIDL

Hand-written Binder proxies against com.syu.ipc, whose transaction ids
are assigned by declaration order -- a plausible guess calls the wrong
method, so the values are the ones recovered from the vendor APK and
verified live.

Holds one callback per module because update() carries the code but not
the module, and code 2 is U_STANDBY on MAIN and U_VOL on SOUND. Reads all
four callback parameters even though only two are used, since the parcel
must be consumed in declaration order.

Carries no derivation logic at all -- every interpretation that could be
wrong lives in the tested value types -- so the absence of unit tests
here costs little. It is validated on hardware at the start of Plan 2."
```

---

## Task 8: Design tokens

**Files:**
- Create: `design/build.gradle.kts`, `design/src/main/AndroidManifest.xml`
- Create: `design/src/main/kotlin/com/wk2/climate/design/Colors.kt`
- Create: `design/src/main/kotlin/com/wk2/climate/design/Dimens.kt`
- Modify: `settings.gradle.kts` — add `include(":design")`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class Palette(...)` with `Palette.NIGHT` and `Palette.DAY`; `object Dimens` with target sizes and radii. Package `com.wk2.climate.design`.

- [ ] **Step 1: Add the module to settings and write its build file**

Append to `settings.gradle.kts`:

```kotlin
include(":design")
```

`design/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wk2.climate.design"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.compose.ui)
    api(libs.compose.foundation)
}
```

`design/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 2: Write `Colors.kt`**

The design is authored in `oklch`, which Android has no support for. Values are converted once and committed as hex with the source retained, so the design file stays the reference.

```kotlin
package com.wk2.climate.design

import androidx.compose.ui.graphics.Color

/**
 * The colour tokens for one theme.
 *
 * Day and night differ in luminance only — never in layout — so muscle memory
 * holds. Switching follows the vehicle's illumination signal, not a clock.
 *
 * Accent colours are authored in `oklch`, which Android cannot express, so they
 * are converted once here with the source value in the comment. Two of the
 * blues ask for more chroma than sRGB provides at that lightness and are
 * gamut-clamped; they read very slightly less saturated than the browser
 * mockup, which is a display limit rather than a design change.
 *
 * No shadows anywhere. Elevation is carried by surface tint and border only —
 * shadows read as smudges on a glossy panel in sunlight.
 */
data class Palette(
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceInset: Color,
    val ink: Color,
    val inkDim: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val divider: Color,
    val borderControl: Color,
    val borderControlLarge: Color,
    val accent: Color,
    val accentInk: Color,
    val cool: Color,
    val coolBright: Color,
    val coolLabel: Color,
    val warm: Color,
    val warmBright: Color,
    val trackStart: Color,
    val trackEnd: Color,
) {
    companion object {
        val NIGHT = Palette(
            surface            = Color(0xFF0B0C0D),
            surfaceRaised      = Color(0x0DFFFFFF),   // rgba(255,255,255,.05)
            surfaceInset       = Color(0x09FFFFFF),   // rgba(255,255,255,.035)
            ink                = Color(0xFFF4F4F3),
            inkDim             = Color(0xCCF4F4F3),   // .8
            inkMuted           = Color(0x73F4F4F3),   // .45
            inkFaint           = Color(0x66F4F4F3),   // .4
            divider            = Color(0x17FFFFFF),   // rgba(255,255,255,.09)
            borderControl      = Color(0x33FFFFFF),   // .2
            borderControlLarge = Color(0x3DFFFFFF),   // .24
            accent             = Color(0xFFEFA831),   // oklch(0.78 0.15 75)
            accentInk          = Color(0xFF141414),
            cool               = Color(0xFF4CB0E5),   // oklch(0.72 0.12 235)
            coolBright         = Color(0xFF6BC3F4),   // oklch(0.78 0.11 235)
            coolLabel          = Color(0xFF89CEF6),   // oklch(0.82 0.09 235)
            warm               = Color(0xFFE96E50),   // oklch(0.68 0.16 35)
            warmBright         = Color(0xFFFA8467),   // oklch(0.74 0.15 35)
            trackStart         = Color(0xFF008BC2),   // oklch(0.60 0.13 235) — gamut-clamped
            trackEnd           = Color(0xFFEE8266),   // oklch(0.72 0.14 35)
        )

        val DAY = Palette(
            surface            = Color(0xFFECEAE6),
            surfaceRaised      = Color(0xFFFFFFFF),
            surfaceInset       = Color(0x0D000000),   // rgba(0,0,0,.05)
            ink                = Color(0xFF16181A),
            inkDim             = Color(0xCC16181A),
            inkMuted           = Color(0x80000000),   // rgba(0,0,0,.5)
            inkFaint           = Color(0x66000000),
            divider            = Color(0x1A000000),   // rgba(0,0,0,.1)
            borderControl      = Color(0x33000000),
            borderControlLarge = Color(0x3D000000),
            accent             = Color(0xFFB37903),   // oklch(0.62 0.13 75)
            accentInk          = Color(0xFFFFFFFF),
            cool               = Color(0xFF0073AD),   // oklch(0.52 0.14 235) — gamut-clamped
            coolBright         = Color(0xFF0073AD),
            coolLabel          = Color(0xFF0073AD),
            warm               = Color(0xFFBC4527),   // oklch(0.55 0.16 35)
            warmBright         = Color(0xFFBC4527),
            trackStart         = Color(0xFF0073AD),
            trackEnd           = Color(0xFFBC4527),
        )

        fun forNight(isNight: Boolean): Palette = if (isNight) NIGHT else DAY
    }
}
```

- [ ] **Step 3: Write `Dimens.kt`**

```kotlin
package com.wk2.climate.design

import androidx.compose.ui.unit.dp

/**
 * Fixed geometry.
 *
 * The panel is 1080x1920 at 160dpi, so 1px = 1dp and every measurement in the
 * design handoff is used directly.
 *
 * A 96dp floor on every tappable region is the point of the redesign — the OEM
 * bar packs ~24 same-weight targets into three rows, none taller than ~70px.
 * If an implementation detail forces a tradeoff, this is what must survive.
 */
object Dimens {
    /** The absolute minimum for anything tappable. Not 88. Not 72. */
    val minTarget = 96.dp

    // ---- screen 2a: the resting bar ----
    val barHeight = 227.dp            // framework navigation_bar_height; never grows
    val barWidth = 1080.dp
    val barSideColumn = 156.dp
    val barCenterColumn = 768.dp
    val barTopRow = 96.dp
    val barBottomRow = 131.dp
    val barNavRow = 113.5.dp
    val slotWheel = 210.dp
    val slotAdaptive = 200.dp         // the only region whose contents change
    val slotAuto = 174.dp
    val slotClimate = 184.dp
    val barStepper = 100.dp
    val volumeReadout = 35.dp         // deliberately below minTarget: not tappable

    // ---- screen 1d: the climate page ----
    val pageWidth = 1080.dp
    val pageHeight = 1693.dp          // exactly the inset-reduced app area
    val pageGutter = 34.dp
    val headerHeight = 112.dp
    val closeButtonWidth = 130.dp
    val zoneStepper = 110.dp
    val fanStepperWidth = 104.dp
    val fanMeterHeight = 96.dp
    val airflowTileHeight = 136.dp
    val modeTileRow1 = 104.dp
    val modeTileRow2 = 96.dp
    val comfortTileHeight = 96.dp
    val holdOffWidth = 180.dp

    // ---- radii ----
    val radiusPip = 3.dp
    val radiusSmall = 5.dp
    val radiusTile = 18.dp
    val radiusStepper = 20.dp
    val radiusPill = 26.dp

    // ---- interaction timing ----
    const val HOLD_REPEAT_DELAY_MS = 400L
    const val HOLD_REPEAT_INTERVAL_MS = 150L
    const val POWER_HOLD_MS = 800L
    const val PANEL_TRANSITION_MS = 220
    /** How long to wait for the vehicle before resolving a pressed state anyway. */
    const val COMMAND_SETTLE_MS = 600L
}
```

- [ ] **Step 4: Verify it compiles**

Run: `./gradlew :design:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts design
git commit -m "feat(design): colour and dimension tokens

Palette carries night and day, which differ in luminance only so muscle
memory holds. The design is authored in oklch, which Android cannot
express, so accents are converted once and committed as hex with the
source value in the comment. Two blues are gamut-clamped -- they ask for
more chroma than sRGB has at that lightness -- and will read slightly
less saturated than the mockup.

Dimens fixes the geometry. The 96dp target floor is the point of the
redesign and is documented as the thing to protect if anything forces a
tradeoff. The 35dp volume readout is marked as deliberately below it."
```

---

## Task 9: The harness — prove the seam without a vehicle

The deliverable that makes the rest of the project cheap: an app that renders live bus state and dispatches real commands, running on an ordinary emulator.

**Files:**
- Create: `harness/build.gradle.kts`, `harness/src/main/AndroidManifest.xml`
- Create: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt`
- Modify: `settings.gradle.kts` — add `include(":harness")`

**Interfaces:**
- Consumes: `FakeVehicleBus`, `VehicleBus`, `Command`, `ClimateState`, `AdaptiveSlot`, `SlotContent`, `Palette`, `Dimens`.
- Produces: an installable debug app, `com.wk2.climate.harness`.

- [ ] **Step 1: Add the module and write its build file**

Append to `settings.gradle.kts`:

```kotlin
include(":harness")
```

`harness/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wk2.climate.harness"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.wk2.climate.harness"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":bus"))
    implementation(project(":design"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    debugImplementation(libs.compose.ui.tooling)
}
```

`harness/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="WK2 Harness"
        android:allowBackup="false">
        <activity
            android:name=".HarnessActivity"
            android:exported="true"
            android:screenOrientation="portrait">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 2: Write `HarnessActivity.kt`**

```kotlin
package com.wk2.climate.harness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wk2.climate.bus.AdaptiveSlot
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.FakeVehicleBus
import com.wk2.climate.bus.Signal
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette

/**
 * Development harness. **Not shipped.**
 *
 * Renders the live bus state and dispatches real commands against
 * [FakeVehicleBus], so the whole bus layer is exercised on an ordinary
 * emulator with no vehicle attached. Run it on an AVD configured
 * **1080x1920 at 160dpi** to match the target panel exactly.
 *
 * It is not a preview of screens 2a or 1d — it is a state inspector plus a
 * command keypad. Its job is to prove the seam works and to make awkward
 * states easy to reach.
 */
class HarnessActivity : ComponentActivity() {

    private val bus = FakeVehicleBus()
    private val slot = AdaptiveSlot()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Harness(bus, slot) }
    }
}

@Composable
private fun Harness(bus: FakeVehicleBus, slot: AdaptiveSlot) {
    val state by bus.state.collectAsStateWithLifecycle()
    val connected by bus.connected.collectAsStateWithLifecycle()
    val palette = Palette.forNight(state.isNight)

    // Drive the adaptive slot from a scrubbable temperature so its hysteresis
    // and dwell can be watched without waiting on weather.
    //
    // slot.update() mutates the state machine, so it must NOT be called during
    // composition — recomposition would advance it unpredictably. It is driven
    // from the button handlers below and its result held in Compose state.
    var outsideF by remember { mutableStateOf<Int?>(null) }
    var clock by remember { mutableStateOf(0L) }
    var slotContent by remember { mutableStateOf(slot.content) }

    fun scrub(toF: Int?) {
        outsideF = toF
        clock += 60_000L                      // step past the dwell window
        slotContent = slot.update(toF, clock)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Mono("WK2 HARNESS — fake bus", palette.ink, 16.sp)
        Mono(
            if (connected) "bus: connected" else "bus: DISCONNECTED",
            if (connected) palette.accent else palette.warm,
        )

        LazyColumn(
            Modifier.fillMaxWidth().height(300.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Mono("--- derived ---", palette.inkMuted) }
            item { Mono("tempLeft    ${state.tempLeft}", palette.ink) }
            item { Mono("tempRight   ${state.tempRight}", palette.ink) }
            item { Mono("fan         ${state.fan}", palette.ink) }
            item { Mono("airflow     ${state.airflow}", palette.ink) }
            item { Mono("seatHeatL   ${state.seatHeatL}", palette.ink) }
            item { Mono("seatHeatR   ${state.seatHeatR}", palette.ink) }
            item { Mono("seatVentL   ${state.seatVentL}", palette.ink) }
            item { Mono("ac/auto     ${state.acOn} / ${state.autoOn}", palette.ink) }
            item { Mono("recirc/max  ${state.recircOn} / ${state.maxAcOn}", palette.ink) }
            item { Mono("sync/wheel  ${state.syncOn} / ${state.wheelHeatOn}", palette.ink) }
            item { Mono("power       ${state.powerOn}", palette.ink) }
            item { Mono("volume      ${state.volume}", palette.ink) }
            item { Mono("night       ${state.isNight}", palette.ink) }
            item { Mono("--- adaptive slot ---", palette.inkMuted) }
            item { Mono("outside     ${outsideF ?: "undecoded (null)"}", palette.ink) }
            item { Mono("slot holds  $slotContent", palette.accent) }
        }

        Mono("commands", palette.inkMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("AUTO", palette) { bus.send(Command.AUTO) }
            Key("FAN+", palette) { bus.send(Command.FAN_UP) }
            Key("FAN-", palette) { bus.send(Command.FAN_DOWN) }
            Key("A/C", palette) { bus.send(Command.AC) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("T+", palette) { bus.send(Command.TEMP_L_UP) }
            Key("T-", palette) { bus.send(Command.TEMP_L_DOWN) }
            Key("SEAT L", palette) { bus.send(Command.SEAT_HEAT_L) }
            Key("MAX", palette) { bus.send(Command.MAX_AC) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("DEFR", palette) { bus.send(Command.FRONT_DEFROST) }
            Key("FACE", palette) { bus.send(Command.AIRFLOW_FACE) }
            Key("FEET", palette) { bus.send(Command.AIRFLOW_FEET) }
            Key("SYNC", palette) { bus.send(Command.SYNC) }
        }

        Mono("inject awkward states", palette.inkMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("LO", palette) { bus.inject(Signal.TEMP_LEFT, -2) }
            Key("NIGHT", palette) {
                bus.inject(Signal.ILLUMINATION, if (state.isNight) 0 else 1)
            }
            Key("DROP", palette) { bus.setConnected(!connected) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("COLD", palette) { scrub(20) }
            Key("MILD", palette) { scrub(60) }
            Key("HOT", palette) { scrub(95) }
            Key("NULL", palette) { scrub(null) }
        }
    }
}

/**
 * Uses [BasicText] rather than Material's `Text`. Reaching for `Text` out of
 * habit is the single most likely way this project accidentally acquires the
 * Material dependency the design does not want — there are no Material
 * components in either screen, no ripple, and no shadows.
 */
@Composable
private fun Mono(
    text: String,
    color: Color,
    size: TextUnit = 13.sp,
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = FontFamily.Monospace,
        ),
    )
}

/**
 * A harness key. Uses the design's pressed-state rule deliberately: feedback
 * on touch-down with no ripple, because there is no hover on the target device
 * and ripples read as smudges on a glossy panel in sunlight.
 */
@Composable
private fun Key(label: String, palette: Palette, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .height(56.dp)
            .background(
                if (pressed) palette.accent else palette.surfaceRaised,
                RoundedCornerShape(Dimens.radiusTile),
            )
            .border(
                1.5.dp,
                palette.borderControl,
                RoundedCornerShape(Dimens.radiusTile),
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (pressed) palette.accentInk else palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}
```

- [ ] **Step 3: Verify the whole project builds**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, and `:bus:test` reports 71 passing tests.

Confirm no Material dependency crept in:

Run: `./gradlew :harness:dependencies --configuration debugRuntimeClasspath`
Expected: no `androidx.compose.material` or `androidx.compose.material3` entry.

- [ ] **Step 4: Create the emulator and run it**

```bash
SDK="$HOME/AppData/Local/Android/Sdk"
"$SDK/cmdline-tools/latest/bin/avdmanager.bat" create avd \
  -n wk2_panel -k "system-images;android-33;google_apis;x86_64" -d pixel_6 --force
```

Then edit `~/.android/avd/wk2_panel.avd/config.ini` to match the target panel exactly:

```ini
hw.lcd.width=1080
hw.lcd.height=1920
hw.lcd.density=160
```

Launch and install:

```bash
"$SDK/emulator/emulator.exe" -avd wk2_panel -no-snapshot -no-boot-anim &
./gradlew :harness:installDebug
adb shell am start -n com.wk2.climate.harness/.HarnessActivity
```

If the `android-33` system image is not installed, install it first:

```bash
"$SDK/cmdline-tools/latest/bin/sdkmanager.bat" "system-images;android-33;google_apis;x86_64"
```

- [ ] **Step 5: Verify the behaviours by hand**

Confirm on the emulator, and capture a screenshot for the commit:

1. Initial state shows `fan Auto`, `airflow NONE`, `tempLeft Degrees(68)`.
2. Tapping `FAN+` once → `autoOn false`, `fan Level(3)`, `airflow FACE`.
3. Tapping `FAN+` five more times → `fan Level(7)` and it stops climbing.
4. Tapping `SEAT L` three times → `HIGH`, `LOW`, `OFF`, with every tap visibly changing.
5. Tapping `MAX` → `tempLeft Lo` and `recirc true`.
6. Tapping `DEFR` → `airflow UNKNOWN`.
7. Tapping `NIGHT` → the whole surface switches palette.
8. Tapping `DROP` → `bus: DISCONNECTED`, and command keys stop having any effect.
9. Tapping `COLD` → `slot holds FRONT_DEFROST`; `HOT` → `SEAT_COOL`; `NULL` → `SEAT_HEAT`.

```bash
adb shell screencap -p /sdcard/harness.png
adb pull /sdcard/harness.png docs/harness-screenshot.png
adb shell rm /sdcard/harness.png
```

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts harness docs/harness-screenshot.png
git commit -m "feat(harness): debug app proving the bus seam off-vehicle

An emulator-hosted state inspector and command keypad over
FakeVehicleBus, so the whole bus layer is exercised with no vehicle
attached. Run on an AVD at 1080x1920/160dpi to match the target panel,
where 1px = 1dp.

It is not a preview of either screen -- it is the thing that makes
building them cheap. The inject row reaches states that are awkward to
produce in a car: the LO sentinel, night illumination, a dropped
connection, and each adaptive-slot temperature band.

Uses BasicText rather than Material Text deliberately; the design has no
Material components, no ripple and no shadows, and the build asserts the
dependency stays out."
```

---

## Definition of done for Plan 1

- [ ] `./gradlew build` succeeds.
- [ ] `./gradlew :bus:test` reports **71 passing tests** (10 table + 13 values + 9 airflow + 9 state + 18 fake bus + 12 slot).
- [ ] `./gradlew :harness:dependencies --configuration debugRuntimeClasspath` shows no Material artifact.
- [ ] The harness runs on a 1080x1920/160dpi AVD and all nine behaviours in Task 9 Step 5 are observed.
- [ ] No module other than `:app` (which does not exist yet) references `AccessibilityService` or `WindowManager`.

## What Plan 2 and Plan 3 cover

**Plan 2 — screen 2a and the overlay service.** The `:ui` module's bar composable, the `:app` module's `ClimateBarService`, the `ComposeOverlayHost` lifecycle shim, and the verified window parameters from spec section 3. Opens with a hardware session validating `SyuVehicleBus` against the real bus, since Task 7 here is the one piece with no automated coverage.

**Plan 3 — screen 1d.** The climate page composable, the panel window, the 220 ms transition, hold-repeat on all steppers, and the hold-to-confirm power gesture.

**Carried forward, not blocking.** Decoding outside temperature from the raw MCU frame stream (spec section 11 item 4), `U_LAMPLET` polarity, and real icons for the placeholder glyphs.
