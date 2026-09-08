# wk2-climate Plan 2: Screen 2a and the Overlay Service

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Draw screen 2a as a permanent overlay covering the OEM climate bar on the vehicle, driven entirely by the bus layer from Plan 1, with HOME and BACK working. Ends with the bar installed and verified in the truck.

**Architecture:** A `:ui` module holds the bar composable and shared interaction primitives, depending only on `:bus` and `:design`. A `:app` module holds the single `AccessibilityService` that owns the bus lifecycle, hosts Compose in a raw `WindowManager` window at the verified coordinates, and performs global nav actions. `:harness` gains a mode that renders the real bar so it can be verified on an emulator before it ever reaches the vehicle.

**Tech Stack:** Kotlin 2.3.0, AGP 9.0.1, Gradle 9.1.0, Compose (ui + foundation only), kotlinx-coroutines 1.10.2, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-09-08-wk2-climate-design.md`, and the geometry authority `design_handoff_system_navigation/README.md` (screen **2a**).

## Global Constraints

Everything from Plan 1's Global Constraints still applies, in particular:

- **Do NOT apply `org.jetbrains.kotlin.android`** — AGP 9.0 rejects it.
- **No Material dependency.** `BasicText`, never Material `Text`. `ui-tooling`
  transitively pulls `material:1.0.0`, so do not add it.
- **`:ui` must never depend on `:app`.** No composable may reference an
  `AccessibilityService` or a `WindowManager` token.
- **Filter tests with `:<module>:testDebugUnitTest --tests '<pattern>'`.**
- `compileSdk 36`, `minSdk 26`, `targetSdk 33`, JVM target 17.

Plan-2-specific:

- **1px = 1dp.** Every measurement in the handoff's 2a section is used verbatim
  as dp. Take geometry from the handoff, not from this document.
- **96dp minimum on every tappable region.** The one deliberate exception is the
  35dp volume readout strip, which is **not tappable**.
- **Fixed geometry.** Explicit `.width()`/`.height()` on every slot. `weight()`
  only where the handoff says `1fr`. **No `wrapContentWidth` anywhere** — a font
  metric change must not be able to move a control.
- **Pressed feedback on touch-down, never on release. No ripple, no shadows.**
  `indication = null` plus `collectIsPressedAsState()`.
- **No animation on any numeral.** No `animateIntAsState` on temperature, fan or
  volume. A moving number is unreadable at a glance.
- **Render only from `VehicleBus.state`.** A tap dispatches a `Command` and
  mutates nothing locally.

---

## File Structure

```
ui/build.gradle.kts
ui/src/main/AndroidManifest.xml
ui/src/main/kotlin/com/wk2/climate/ui/
    Interaction.kt        pressed-state + hold-repeat primitives — Task 1
    HoldRepeat.kt         the repeat schedule, pure and testable — Task 1
    Icons.kt              tintable bitmap icon composable — Task 2
    bar/BarNav.kt         left column: HOME / BACK — Task 3
    bar/BarVolume.kt      right column: volume ▲ / readout / ▼ — Task 3
    bar/BarTopRow.kt      wheel, adaptive slot, AUTO, CLIMATE — Task 4
    bar/BarZones.kt       driver / passenger temperature — Task 5
    bar/ClimateBar.kt     assembles 2a — Task 6
ui/src/test/kotlin/com/wk2/climate/ui/
    HoldRepeatTest.kt     — Task 1

design/src/main/kotlin/com/wk2/climate/design/
    Type.kt               font families + the handoff's text styles — Task 1
design/src/main/res/font/
    manrope.ttf                     — Task 1
    ibm_plex_mono_medium.ttf        — Task 1
    ibm_plex_mono_semibold.ttf      — Task 1
    ibm_plex_mono_bold.ttf          — Task 1
design/src/main/res/drawable-nodpi/
    ic_defrost.png ic_recirc.png ic_face.png
    ic_face_feet.png ic_feet.png ic_feet_glass.png   — Task 2

app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/xml/accessibility_config.xml
app/src/main/kotlin/com/wk2/climate/app/
    ComposeOverlayHost.kt  lifecycle shim for Compose in a raw window — Task 7
    ClimateBarService.kt   the AccessibilityService — Task 8

harness/src/main/kotlin/com/wk2/climate/harness/
    HarnessActivity.kt     gains a bar-preview mode — Task 6
```

---

## Task 1: Typography, and the interaction primitives

Two things every subsequent task needs: the handoff's exact text styles, and press handling that behaves the way the design requires rather than the way Compose defaults.

**Files:**
- Create: `ui/build.gradle.kts`, `ui/src/main/AndroidManifest.xml`
- Create: `design/src/main/res/font/` (4 TTFs)
- Create: `design/src/main/kotlin/com/wk2/climate/design/Type.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/HoldRepeat.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/Interaction.kt`
- Modify: `settings.gradle.kts` — add `include(":ui")`
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/HoldRepeatTest.kt`

**Interfaces:**
- Consumes: `Dimens` from `:design`.
- Produces: `object Type` with `manrope`/`plexMono` `FontFamily` plus the named
  `TextStyle`s used by 2a. `object HoldRepeat` with
  `suspend fun run(onFire: () -> Unit)`, `INITIAL_DELAY_MS = 400L`,
  `INTERVAL_MS = 150L`. `@Composable fun Modifier.target(interaction, enabled, onClick)`
  and `@Composable fun Modifier.holdRepeatTarget(interaction, enabled, onFire)`.

- [ ] **Step 1: Fetch the fonts**

Manrope ships a variable TTF covering every weight the design uses (300, 400,
700, 800) in one file. IBM Plex Mono has **no variable release**, so three
static weights are needed. The design asks for Plex Mono 800, which does not
exist — Bold (700) is its heaviest, and that is what 800 maps to.

```bash
FONTS=design/src/main/res/font
mkdir -p "$FONTS"
curl -sSL -o "$FONTS/manrope.ttf" \
  'https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf'
curl -sSL -o "$FONTS/ibm_plex_mono_medium.ttf" \
  'https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexmono/IBMPlexMono-Medium.ttf'
curl -sSL -o "$FONTS/ibm_plex_mono_semibold.ttf" \
  'https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexmono/IBMPlexMono-SemiBold.ttf'
curl -sSL -o "$FONTS/ibm_plex_mono_bold.ttf" \
  'https://raw.githubusercontent.com/google/fonts/main/ofl/ibmplexmono/IBMPlexMono-Bold.ttf'
```

Verify each is a real TrueType file (magic `00010000`) and not an HTML error page:

```bash
for f in "$FONTS"/*.ttf; do
  printf '%s: %s %s bytes\n' "$(basename "$f")" \
    "$(od -An -tx1 -N4 "$f" | tr -d ' ')" "$(stat -c%s "$f")"
done
```

Expected: all four report `00010000`. Both families are OFL-licensed, which is
compatible with this repo's Apache 2.0 and requires only that the licence
travel with the fonts — record that in `design/src/main/res/font/README.md`:

```markdown
# Bundled fonts

- `manrope.ttf` — Manrope, variable weight 200–800. SIL Open Font License 1.1.
  https://github.com/google/fonts/tree/main/ofl/manrope
- `ibm_plex_mono_*.ttf` — IBM Plex Mono, Medium/SemiBold/Bold. SIL Open Font
  License 1.1. https://github.com/google/fonts/tree/main/ofl/ibmplexmono

IBM Plex Mono has no variable release and no weight above Bold (700). The
design's "IBM Plex Mono 800" therefore renders as Bold.
```

- [ ] **Step 2: Write `Type.kt`**

Android resource names must be lowercase with underscores, which is why the
files are named as above rather than after their PostScript names.

```kotlin
package com.wk2.climate.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The handoff's type scale, expressed once.
 *
 * Two families: **Manrope** for UI, values and labels; **IBM Plex Mono** for
 * micro-labels, states and numerics, always uppercase with wide tracking.
 *
 * `1px = 1dp` on this panel, and Compose `sp` follows the user's font scale —
 * which on a head unit is always 1.0 and which we must not let move a control
 * anyway. Sizes are given in `sp` because that is what `TextStyle` takes; the
 * fixed-geometry rule is enforced by the *containers*, never by text metrics.
 */
object Type {

    val manrope = FontFamily(Font(R.font.manrope))

    val plexMono = FontFamily(
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        // Plex Mono has no weight above Bold; the design's 800 lands here.
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )

    private fun ui(
        size: TextUnit,
        weight: FontWeight,
        tracking: TextUnit = 0.sp,
    ) = TextStyle(
        fontFamily = manrope,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = tracking,
    )

    private fun mono(
        size: TextUnit,
        weight: FontWeight,
        tracking: TextUnit,
    ) = TextStyle(
        fontFamily = plexMono,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = tracking,
    )

    // ---- screen 2a ----

    /** HOME / BACK micro-labels. IBM Plex Mono 600 11px, ls .1em. */
    val navLabel = mono(11.sp, FontWeight.SemiBold, 1.1.sp)

    /** BACK chevron. Manrope 300 32px. */
    val navChevron = ui(32.sp, FontWeight.Light)

    /** WHEEL / CLIMATE labels. Manrope 700 16px, ls .04em. */
    val barControlLabel = ui(16.sp, FontWeight.Bold, 0.64.sp)

    /** AUTO. Manrope 800 19px, ls .06em. */
    val barAuto = ui(19.sp, FontWeight.ExtraBold, 1.14.sp)

    /** CLIMATE's ▲. Manrope 400 13px. */
    val barCaret = ui(13.sp, FontWeight.Normal)

    /** Adaptive slot title. Manrope 800 16px, ls .04em. */
    val slotLabel = ui(16.sp, FontWeight.ExtraBold, 0.64.sp)

    /** Adaptive slot's defrost label. Manrope 800 15px, ls .04em. */
    val slotLabelSmall = ui(15.sp, FontWeight.ExtraBold, 0.6.sp)

    /** Adaptive slot OFF / LOW / HIGH. IBM Plex Mono 800 11px, ls .14em. */
    val slotState = mono(11.sp, FontWeight.Bold, 1.54.sp)

    /** Zone temperature numeral. Manrope 700 48px, ls -0.03em. */
    val zoneValue = ui(48.sp, FontWeight.Bold, (-1.44).sp)

    /** The degree mark beside it, 24px. */
    val zoneDegree = ui(24.sp, FontWeight.Bold)

    /** DRIVER / PASSENGER. IBM Plex Mono 600 10px, ls .14em. */
    val zoneLabel = mono(10.sp, FontWeight.SemiBold, 1.4.sp)

    /** The − / + glyphs. Manrope 300 42px. */
    val stepper = ui(42.sp, FontWeight.Light)

    /** Volume ▲ / ▼. Manrope 400 24px. */
    val volumeArrow = ui(24.sp, FontWeight.Normal)

    /** "VOL". IBM Plex Mono 600 10px, ls .12em. */
    val volumeLabel = mono(10.sp, FontWeight.SemiBold, 1.2.sp)

    /** The volume number. Manrope 700 17px. */
    val volumeValue = ui(17.sp, FontWeight.Bold)
}
```

- [ ] **Step 3: Write the failing test for the repeat schedule**

`ui/src/test/kotlin/com/wk2/climate/ui/HoldRepeatTest.kt`:

```kotlin
package com.wk2.climate.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoldRepeatTest {

    @Test
    fun `a tap fires exactly once, immediately`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        // Released before the initial delay elapses.
        advanceTimeBy(50)
        job.cancel()
        assertEquals(1, fired)
    }

    @Test
    fun `nothing repeats before the initial delay`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS - 1)
        assertEquals("only the immediate fire so far", 1, fired)
        job.cancel()
    }

    @Test
    fun `the second fire lands at the initial delay`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + 1)
        assertEquals(2, fired)
        job.cancel()
    }

    @Test
    fun `after the initial delay it repeats on the interval`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        // 400ms -> fire 2, then one per 150ms.
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + HoldRepeat.INTERVAL_MS * 4 + 1)
        assertEquals("1 immediate + 1 at 400ms + 4 intervals", 6, fired)
        job.cancel()
    }

    @Test
    fun `cancelling stops it`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + HoldRepeat.INTERVAL_MS + 1)
        val atCancel = fired
        job.cancel()
        advanceTimeBy(10_000)
        assertEquals("no fires after cancellation", atCancel, fired)
    }

    @Test
    fun `the timings are the ones the design specifies`() = runEmpty {
        assertEquals(400L, HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(150L, HoldRepeat.INTERVAL_MS)
    }

    /** A plain body, so the constants test needs no coroutine scope. */
    private fun runEmpty(body: () -> Unit) = body()
}
```

- [ ] **Step 4: Add the `:ui` module**

Append to `settings.gradle.kts`:

```kotlin
include(":ui")
```

`ui/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wk2.climate.ui"
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
    api(project(":bus"))
    api(project(":design"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`ui/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests '*HoldRepeatTest*'`
Expected: FAIL — unresolved reference `HoldRepeat`.

- [ ] **Step 6: Write `HoldRepeat.kt`**

```kotlin
package com.wk2.climate.ui

import kotlinx.coroutines.delay

/**
 * The press-and-hold repeat schedule for `−` / `+` targets.
 *
 * Fires immediately on touch-down, waits [INITIAL_DELAY_MS], then repeats every
 * [INTERVAL_MS] until cancelled. The immediate first fire is what makes a
 * single tap feel instant; the delay before repeating is what stops a tap from
 * accidentally stepping twice.
 *
 * Kept as a suspend function with no Compose dependency so the timing is
 * verified with virtual time instead of by holding a finger on a screen.
 */
object HoldRepeat {
    const val INITIAL_DELAY_MS = 400L
    const val INTERVAL_MS = 150L

    suspend fun run(onFire: () -> Unit) {
        onFire()
        delay(INITIAL_DELAY_MS)
        while (true) {
            onFire()
            delay(INTERVAL_MS)
        }
    }
}
```

- [ ] **Step 7: Write `Interaction.kt`**

`holdRepeatTarget` uses `pointerInput` rather than `clickable` because it needs
the press *duration*, not a click event, and the repeat must stop the instant
the finger lifts or leaves. It emits `PressInteraction` by hand, which is what
makes `collectIsPressedAsState()` work for it — so a hold-repeat target renders
its pressed state exactly like a plain tap.

```kotlin
package com.wk2.climate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Interaction primitives for a vehicle panel, which differs from a phone in
 * three ways the Compose defaults do not account for:
 *
 *  - **There is no hover.** Never implement hover styling.
 *  - **Feedback must appear on touch-down, not on release.** A driver glancing
 *    away needs to know the press registered before they lift.
 *  - **No ripple and no shadows.** Both read as smudges on a glossy panel in
 *    direct sunlight.
 *
 * So every target here passes `indication = null` and renders its own pressed
 * state from [MutableInteractionSource].
 */

/** Remembers an interaction source and its live pressed state together. */
@Composable
fun rememberPressState(): Pair<MutableInteractionSource, State<Boolean>> {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    return interaction to pressed
}

/**
 * A single-shot tappable region. Deliberately no indication — the caller
 * renders the pressed state itself.
 */
@Composable
fun Modifier.target(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

/**
 * A tappable region that repeats while held, on [HoldRepeat]'s schedule.
 * Used by every `-` / `+`.
 */
@Composable
fun Modifier.holdRepeatTarget(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onFire: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(enabled, onFire) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = PressInteraction.Press(down.position)
            scope.launch { interaction.emit(press) }

            val repeater = scope.launch { HoldRepeat.run(onFire) }

            // Stop repeating the instant the finger lifts or the gesture is
            // cancelled -- a target that kept firing after release would run
            // the temperature away from the driver.
            val up = waitForUpOrCancellation()
            repeater.cancel()
            scope.launch {
                interaction.emit(
                    if (up == null) PressInteraction.Cancel(press)
                    else PressInteraction.Release(press),
                )
            }
        }
    }
}
```

- [ ] **Step 8: Verify `:ui` compiles**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Run the tests and build**

Run: `./gradlew :ui:testDebugUnitTest :ui:assembleDebug`
Expected: PASS, 6 tests. BUILD SUCCESSFUL.

Confirm the fonts are packaged and no Material crept in:

```bash
./gradlew :ui:dependencies --configuration debugRuntimeClasspath | grep -c 'compose.material'
```

Expected: `0`.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts ui design/src/main/res/font design/src/main/kotlin/com/wk2/climate/design/Type.kt
git commit -m "feat(ui): typography and vehicle-panel interaction primitives

Bundles Manrope (variable, covers 300-800 in one file) and three static
IBM Plex Mono weights. Plex Mono has no variable release and nothing
above Bold, so the design's 'Plex Mono 800' renders as Bold -- recorded
in the font README alongside the OFL notices.

Type expresses the handoff's scale once, with em tracking converted to sp.

Interaction encodes three ways a vehicle panel differs from a phone:
there is no hover, feedback must appear on touch-down rather than
release, and ripples and shadows read as smudges in sunlight. So every
target passes indication = null and renders its own pressed state.

holdRepeatTarget emits PressInteraction by hand, which is what lets a
hold-repeat target render its pressed state exactly like a plain tap.
Its schedule is a plain suspend function so the 400ms delay and 150ms
interval are verified with virtual time rather than by holding a finger
on a screen."
```

---

## Task 2: Tintable icons

The airflow, defrost and recirculate glyphs are final assets. They ship as a
single bitmap set and are **tinted at runtime**, which halves the asset count
and turns the active state into a colour change rather than an asset swap.

**Files:**
- Create: `design/src/main/res/drawable-nodpi/ic_*.png` (6 files, copied)
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/Icons.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `@Composable fun GlyphIcon(res: Int, tint: Color, height: Dp, modifier: Modifier)`, and `object Glyph` exposing `defrost`, `recirc`, `face`, `faceFeet`, `feet`, `feetGlass` as drawable ids.

- [ ] **Step 1: Copy the assets**

The handoff ships `dark/` (light glyph) and `light/` (dark glyph) variants. Only
the `dark/` set is needed: those are `#f4f4f3` glyphs on transparency, so a
`ColorFilter` can tint them to any colour including the dark ink used on an
active amber tile.

`drawable-nodpi` is deliberate. These are fixed-height glyphs rendered at exact
dp sizes from the handoff; density-scaling them would fight that.

```bash
DEST=design/src/main/res/drawable-nodpi
SRC=design_handoff_system_navigation/icons/dark
mkdir -p "$DEST"
cp "$SRC/defrost.png"     "$DEST/ic_defrost.png"
cp "$SRC/recirc.png"      "$DEST/ic_recirc.png"
cp "$SRC/face.png"        "$DEST/ic_face.png"
cp "$SRC/face-feet.png"   "$DEST/ic_face_feet.png"
cp "$SRC/feet.png"        "$DEST/ic_feet.png"
cp "$SRC/feet-glass.png"  "$DEST/ic_feet_glass.png"
ls -1 "$DEST"
```

> Converting these to `VectorDrawable` is preferable eventually, as the handoff
> notes, but requires tracing and buys only scalability we do not need at a
> fixed dp size. Tinting a bitmap achieves the actual goal — one asset per
> glyph instead of two.

- [ ] **Step 2: Write `Icons.kt`**

```kotlin
package com.wk2.climate.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.wk2.climate.design.R

/** The final glyph assets, extracted from a conventional automotive HVAC set. */
object Glyph {
    val defrost = R.drawable.ic_defrost
    val recirc = R.drawable.ic_recirc
    val face = R.drawable.ic_face
    val faceFeet = R.drawable.ic_face_feet
    val feet = R.drawable.ic_feet
    val feetGlass = R.drawable.ic_feet_glass
}

/**
 * A glyph drawn at a fixed height with its width free, tinted at runtime.
 *
 * The assets are light glyphs on transparency, so one bitmap serves both
 * themes and both active and inactive states — the difference is [tint], not
 * a different file.
 */
@Composable
fun GlyphIcon(
    @DrawableRes res: Int,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.height(height),
        contentScale = ContentScale.FillHeight,
        colorFilter = ColorFilter.tint(tint),
    )
}
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add design/src/main/res/drawable-nodpi ui/src/main/kotlin/com/wk2/climate/ui/Icons.kt
git commit -m "feat(ui): tintable glyph assets

Ships only the handoff's dark/ variants -- light glyphs on transparency
-- and tints them at runtime, so one bitmap serves both themes and both
active and inactive states. That halves the asset count and turns an
airflow tile's active state into a colour change rather than an asset
swap.

drawable-nodpi is deliberate: these are rendered at exact dp heights
from the handoff, so density scaling would fight the fixed geometry.

Vector conversion is still preferable eventually but requires tracing
and buys only scalability we do not need at a fixed size."
```

---

## Task 3: The bar's side columns

Starting at the edges, because they are the simplest and establish the
divider and fixed-size conventions the middle relies on.

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarNav.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarVolume.kt`

**Interfaces:**
- Consumes: `Type`, `Palette`, `Dimens`, `rememberPressState`, `Modifier.target`.
- Produces: `@Composable fun BarNav(palette: Palette, onHome: () -> Unit, onBack: () -> Unit)` sized 156×227, and `@Composable fun BarVolume(palette: Palette, volume: Int?, onUp: () -> Unit, onDown: () -> Unit)` sized 156×227.

Geometry is in the handoff, screen 2a, "Left column" and "Right column". The
values below are repeated only where a reader needs them to follow the code.

- [ ] **Step 1: Write `BarNav.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The bar's left column: HOME above BACK, 156 x 113.5 each.
 *
 * Both are comfortably over the 96dp floor. These glyphs are the handoff's
 * placeholders — a bordered square and a chevron, with the text labels doing
 * the work. Real icons are a later choice; what must not ship is a bare
 * rectangle with no label.
 */
@Composable
fun BarNav(
    palette: Palette,
    onHome: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(Dimens.barSideColumn)
            .height(Dimens.barHeight),
    ) {
        NavCell(
            palette = palette,
            label = "HOME",
            gap = 7.dp,
            bottomDivider = true,
            onClick = onHome,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .border(2.5.dp, palette.ink, RoundedCornerShape(5.dp)),
            )
        }
        NavCell(
            palette = palette,
            label = "BACK",
            gap = 3.dp,
            bottomDivider = false,
            onClick = onBack,
        ) {
            BasicText(
                text = "\u2039",
                style = Type.navChevron.copy(color = palette.ink),
                modifier = Modifier.offset(y = (-4).dp),
            )
        }
    }
}

@Composable
private fun NavCell(
    palette: Palette,
    label: String,
    gap: androidx.compose.ui.unit.Dp,
    bottomDivider: Boolean,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Column(
        Modifier
            .fillMaxWidth()
            .height(Dimens.barNavRow)
            .then(if (bottomDivider) Modifier.bottomDivider(palette.divider) else Modifier)
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        glyph()
        androidx.compose.foundation.layout.Spacer(Modifier.height(gap))
        BasicText(
            text = label,
            style = Type.navLabel.copy(color = palette.ink.copy(alpha = 0.7f)),
        )
    }
}
```

**Create `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarDividers.kt`** for the
two shared divider modifiers. They belong in their own file because three of
the bar's files consume them; burying a shared primitive inside `BarNav.kt`
would make that dependency invisible.

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
```

then the two modifiers:

```kotlin
/**
 * A 1dp divider along the bottom edge.
 *
 * Drawn with `drawBehind` rather than as a layout node, so a 1dp line never
 * participates in measurement and cannot shift a control by a pixel.
 */
fun Modifier.bottomDivider(color: Color): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(color = color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
}

/** A 1dp divider along the given vertical edge. */
fun Modifier.sideDivider(color: Color, start: Boolean): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRect(
        color = color,
        topLeft = Offset(if (start) 0f else size.width - w, 0f),
        size = Size(w, size.height),
    )
}
```

In `BarNav.kt`, the BACK chevron's nudge uses
`androidx.compose.foundation.layout.offset` -- import that one.

- [ ] **Step 2: Write `BarVolume.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The bar's right column: volume up, a readout strip, volume down.
 *
 * The 35dp readout is **deliberately not tappable** and deliberately below the
 * 96dp floor — it is a readout, and making it a target would put a
 * below-minimum control on the bar.
 */
@Composable
fun BarVolume(
    palette: Palette,
    volume: Int?,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(Dimens.barSideColumn)
            .height(Dimens.barHeight)
            .sideDivider(palette.divider, start = true),
    ) {
        VolumeArrow(palette, "\u25B2", onUp)

        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.volumeReadout)
                .background(palette.surfaceRaised),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "VOL",
                style = Type.volumeLabel.copy(color = palette.ink.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                // Never animate the numeral: a moving number is unreadable at a glance.
                text = volume?.toString() ?: "--",
                style = Type.volumeValue.copy(color = palette.ink),
            )
        }

        VolumeArrow(palette, "\u25BC", onDown)
    }
}

@Composable
private fun VolumeArrow(palette: Palette, glyph: String, onClick: () -> Unit) {
    val (interaction, pressed) = rememberPressState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.barTopRow)   // 96dp — the floor
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = glyph, style = Type.volumeArrow.copy(color = palette.ink))
    }
}
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar
git commit -m "feat(ui): bar side columns

HOME/BACK at 156x113.5 each and volume up/readout/down at 96/35/96.
Both columns are fixed-size with explicit dp; nothing sizes to content.

The 35dp volume readout is deliberately below the 96dp target floor
because it is a readout, not a control -- making it tappable would put a
below-minimum target on the bar.

Dividers are drawn with drawBehind rather than as layout nodes, so a
1dp line never participates in measurement and cannot shift a control.

Pressed state is a background change on touch-down with no ripple, per
the panel's interaction rules."
```

---

## Task 4: The bar's centre top row

The row containing the one region that ever changes.

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarTopRow.kt`

**Interfaces:**
- Consumes: `SlotContent`, `SeatLevel` from `:bus`; `Type`, `Palette`, `Dimens`; `Glyph`, `GlyphIcon`.
- Produces: `@Composable fun BarTopRow(palette: Palette, wheelOn: Boolean, autoOn: Boolean, slot: SlotContent, seatHeat: SeatLevel, seatVent: SeatLevel, onWheel: () -> Unit, onSlot: () -> Unit, onAuto: () -> Unit, onClimate: () -> Unit, onSlotPressChange: (Boolean) -> Unit)`, 768×96.

- [ ] **Step 1: Write `BarTopRow.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.bus.SlotContent
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.Glyph
import com.wk2.climate.ui.GlyphIcon
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row: heated wheel, the adaptive slot, AUTO,
 * and the CLIMATE opener.
 *
 * Widths are 210 / 200 / 174 / 184 and are **fixed**. Controls live at constant
 * coordinates so they can be found by feel, and the adaptive slot is the only
 * region in the whole bar whose contents ever change.
 */
@Composable
fun BarTopRow(
    palette: Palette,
    wheelOn: Boolean,
    autoOn: Boolean,
    slot: SlotContent,
    seatHeat: SeatLevel,
    seatVent: SeatLevel,
    onWheel: () -> Unit,
    onSlot: () -> Unit,
    onAuto: () -> Unit,
    onClimate: () -> Unit,
    onSlotPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barTopRow),
    ) {
        // ---- heated wheel, 210dp ----
        val (wheelInteraction, wheelPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotWheel)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = false)
                .background(
                    if (wheelPressed.value) palette.surfaceRaised else Color.Transparent,
                )
                .target(wheelInteraction, onClick = onWheel),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(19.dp)
                    .then(
                        if (wheelOn) {
                            Modifier.background(palette.warm, CircleShape)
                        } else {
                            Modifier.border(2.5.dp, palette.warm, CircleShape)
                        },
                    ),
            )
            Spacer(Modifier.width(9.dp))
            BasicText(
                text = "WHEEL",
                style = Type.barControlLabel.copy(color = palette.ink.copy(alpha = 0.85f)),
            )
        }

        // ---- the adaptive slot, 200dp ----
        AdaptiveSlotCell(
            palette = palette,
            slot = slot,
            seatHeat = seatHeat,
            seatVent = seatVent,
            onClick = onSlot,
            onPressChange = onSlotPressChange,
        )

        // ---- AUTO, 174dp ----
        val (autoInteraction, autoPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotAuto)
                .fillMaxHeight()
                .background(if (autoOn) palette.accent else Color.Transparent)
                .then(if (autoPressed.value) Modifier.pressedTint(autoOn, palette) else Modifier)
                .target(autoInteraction, onClick = onAuto),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "AUTO",
                style = Type.barAuto.copy(
                    color = if (autoOn) palette.accentInk else palette.ink,
                ),
            )
        }

        // ---- CLIMATE, 184dp ----
        val (climateInteraction, climatePressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotClimate)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = true)
                .background(
                    if (climatePressed.value) palette.surfaceRaised else Color.Transparent,
                )
                .target(climateInteraction, onClick = onClimate),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "CLIMATE",
                style = Type.barControlLabel.copy(color = palette.ink),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "\u25B2",
                style = Type.barCaret.copy(color = palette.ink.copy(alpha = 0.55f)),
            )
        }
    }
}

/**
 * The one variable region. 200 x 96, and **nothing else in the bar moves,
 * resizes or changes position** when its contents change.
 *
 * Whatever it holds also exists on screen 1d — it is a shortcut, never the
 * only route to a function.
 */
@Composable
private fun AdaptiveSlotCell(
    palette: Palette,
    slot: SlotContent,
    seatHeat: SeatLevel,
    seatVent: SeatLevel,
    onClick: () -> Unit,
    onPressChange: (Boolean) -> Unit,
) {
    val (interaction, pressed) = rememberPressState()

    // The slot must not change contents while a finger is on it, so the state
    // machine needs to know about the press.
    LaunchedEffect(pressed.value) { onPressChange(pressed.value) }

    val isDefrost = slot == SlotContent.FRONT_DEFROST

    Box(
        Modifier
            .width(Dimens.slotAdaptive)
            .fillMaxHeight()
            .then(
                if (isDefrost) {
                    // Amber fill, with a 4dp surface-coloured right border to
                    // separate it from the adjacent amber AUTO.
                    Modifier
                        .background(palette.accent)
                        .padding(end = 4.dp)
                } else {
                    Modifier.sideDivider(palette.divider, start = false)
                },
            )
            .then(if (pressed.value) Modifier.pressedTint(isDefrost, palette) else Modifier)
            .target(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (slot) {
            SlotContent.FRONT_DEFROST -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.defrost, tint = palette.accentInk, height = 34.dp)
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = "FRONT DEFROST",
                    style = Type.slotLabelSmall.copy(color = palette.accentInk),
                )
            }

            SlotContent.SEAT_HEAT -> SeatSlot(
                palette = palette,
                title = "SEAT HEAT",
                level = seatHeat,
                labelColor = palette.ink.copy(alpha = 0.85f),
            )

            SlotContent.SEAT_COOL -> SeatSlot(
                palette = palette,
                title = "SEAT COOL",
                level = seatVent,
                labelColor = palette.coolLabel,
            )
        }
    }
}

@Composable
private fun SeatSlot(
    palette: Palette,
    title: String,
    level: SeatLevel,
    labelColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(text = title, style = Type.slotLabel.copy(color = labelColor))
        Spacer(Modifier.height(5.dp))
        BasicText(
            // OFF / LOW / HIGH. UNAVAILABLE renders as a dash rather than as
            // OFF, because claiming a control is off when we do not know is
            // worse than admitting we do not know.
            text = when (level) {
                SeatLevel.OFF -> "OFF"
                SeatLevel.LOW -> "LOW"
                SeatLevel.HIGH -> "HIGH"
                SeatLevel.UNAVAILABLE -> "\u2014"
            },
            style = Type.slotState.copy(color = palette.ink.copy(alpha = 0.4f)),
        )
    }
}

/**
 * The pressed treatment: brightness on a filled target, a light wash on an
 * outlined one. Applied on touch-down.
 */
private fun Modifier.pressedTint(filled: Boolean, palette: Palette): Modifier =
    if (filled) {
        // brightness(1.25) equivalent — overlay white at low alpha.
        background(Color.White.copy(alpha = 0.2f))
    } else {
        background(palette.surfaceRaised)
    }
```

- [ ] **Step 2: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL. `Modifier.pressedTint` needs
`androidx.compose.foundation.background` imported as a `Modifier` extension —
it already is at the top of the file.

- [ ] **Step 3: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar/BarTopRow.kt
git commit -m "feat(ui): bar centre top row with the adaptive slot

Heated wheel, adaptive slot, AUTO and CLIMATE at fixed 210/200/174/184.
The slot is the only region in the entire bar whose contents ever change,
and nothing around it moves when they do.

Below freezing it becomes FRONT DEFROST with an amber fill and a 4dp
surface-coloured right border, which is what stops it merging visually
with the amber AUTO beside it.

The slot reports its own press state upward, because the state machine
must not swap contents while a finger is on it -- otherwise the driver's
next tap lands on a control they did not aim at.

An UNAVAILABLE seat level renders as a dash rather than OFF: claiming a
control is off when we do not know is worse than admitting we do not."
```

---

## Task 5: The bar's temperature zones

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarZones.kt`

**Interfaces:**
- Consumes: `Temp` from `:bus`; `Type`, `Palette`, `Dimens`; `holdRepeatTarget`.
- Produces: `@Composable fun BarZones(palette: Palette, driver: Temp, passenger: Temp, onDriverUp/Down, onPassengerUp/Down: () -> Unit)`, 768×131.

- [ ] **Step 1: Write `BarZones.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Temp
import com.wk2.climate.bus.TempUnit
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.holdRepeatTarget
import com.wk2.climate.ui.rememberPressState

/**
 * The centre column's 131dp bottom row: two temperature zones split by a 1dp
 * divider.
 *
 * **The numeral is a readout, not a button.** Only `−` and `+` are tappable,
 * at 100 x 131 each. That is well over the 96dp floor in both axes, which is
 * the whole point — the OEM bar's equivalents are ~70px tall.
 */
@Composable
fun BarZones(
    palette: Palette,
    driver: Temp,
    passenger: Temp,
    onDriverDown: () -> Unit,
    onDriverUp: () -> Unit,
    onPassengerDown: () -> Unit,
    onPassengerUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barBottomRow),
    ) {
        Zone(
            palette = palette,
            label = "DRIVER",
            temp = driver,
            onDown = onDriverDown,
            onUp = onDriverUp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(palette.divider),
        )
        Zone(
            palette = palette,
            label = "PASSENGER",
            temp = passenger,
            onDown = onPassengerDown,
            onUp = onPassengerUp,
            modifier = Modifier.weight(1f),
        )
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
    Row(modifier.fillMaxHeight()) {
        Stepper(palette, "\u2212", palette.cool, onDown)

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(text = tempText(temp, palette), style = Type.zoneValue)
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = label,
                style = Type.zoneLabel.copy(color = palette.ink.copy(alpha = 0.45f)),
            )
        }

        Stepper(palette, "+", palette.warm, onUp)
    }
}

/**
 * The numeral, with its degree mark at half size.
 *
 * Sentinels never render as a number. The words match the factory UI for this
 * vehicle profile — `Car_0374_PA_Jeep_All.updateTemp` renders `"LOW"`,
 * `"HIGH"` and `"---"` — so a driver sees the same vocabulary they already
 * know. Showing a number the vehicle did not report is the failure this guards
 * against.
 *
 * Fahrenheit renders as a whole number; Celsius carries a half-degree, so it
 * renders with one decimal.
 */
@Composable
private fun tempText(temp: Temp, palette: Palette): AnnotatedString =
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
                            fontSize = Type.zoneDegree.fontSize,
                            baselineShift = androidx.compose.ui.text.style.BaselineShift.Superscript,
                        ),
                    ) {
                        append("\u00B0")
                    }
                }
                Temp.Lo -> append("LOW")
                Temp.Hi -> append("HIGH")
                Temp.Unavailable -> append("\u2014")
            }
        }
    }

@Composable
private fun Stepper(
    palette: Palette,
    glyph: String,
    color: Color,
    onFire: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Box(
        Modifier
            .width(Dimens.barStepper)
            .fillMaxHeight()
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            // One step per tap; press-and-hold repeats at 150ms after 400ms.
            .holdRepeatTarget(interaction, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = glyph, style = Type.stepper.copy(color = color))
    }
}
```

- [ ] **Step 2: Verify it builds**

Run: `./gradlew :ui:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar/BarZones.kt
git commit -m "feat(ui): bar temperature zones

Two zones at 100/1fr/100 across 131dp, split by a 1dp divider. The
numeral is a readout; only the steppers are tappable, at 100x131 each --
well over the 96dp floor in both axes, which is the entire point, since
the OEM equivalents are about 70px tall.

Steppers use holdRepeatTarget, so one tap is one step and holding
repeats every 150ms after a 400ms delay.

Sentinels never render as numbers, and the words match the factory UI
for this vehicle profile -- LOW, HIGH and a dash -- so the driver sees
vocabulary they already know. Showing a number the vehicle never
reported is the failure this guards against.

No animation on the numeral -- a moving number is unreadable at a glance."
```

---

## Task 6: Assemble the bar and verify it on an emulator

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/ClimateBar.kt`
- Modify: `harness/build.gradle.kts` — add `implementation(project(":ui"))`
- Modify: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt`

**Interfaces:**
- Consumes: everything from Tasks 3–5, plus `VehicleBus`, `ClimateState`, `AdaptiveSlot`, `Command`.
- Produces: `@Composable fun ClimateBar(state: ClimateState, slot: SlotContent, onCommand: (Command) -> Unit, onHome: () -> Unit, onBack: () -> Unit, onOpenClimate: () -> Unit, onSlotPressChange: (Boolean) -> Unit)`.

- [ ] **Step 1: Write `ClimateBar.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.SlotContent
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette

/**
 * Screen 2a: the resting bar. 1080 x 227, and it never grows — 227dp is the
 * framework's `navigation_bar_height` and claiming more would cover app
 * content. Screen 1d opens *over* app content instead.
 *
 * Six functions live here permanently, plus one adaptive slot. Everything else
 * is one tap away on 1d. Rendered entirely from [state]; every tap dispatches
 * a [Command] and mutates nothing locally.
 */
@Composable
fun ClimateBar(
    state: ClimateState,
    slot: SlotContent,
    onCommand: (Command) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onOpenClimate: () -> Unit,
    onSlotPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Day and night differ in luminance only, never in layout, so muscle
    // memory holds. Driven by the vehicle's illumination signal, not a clock.
    val palette = Palette.forNight(state.isNight)

    Row(
        modifier
            .width(Dimens.barWidth)
            .height(Dimens.barHeight)
            .background(palette.surface),
    ) {
        BarNav(palette = palette, onHome = onHome, onBack = onBack)

        Column(Modifier.width(Dimens.barCenterColumn).height(Dimens.barHeight)) {
            BarTopRow(
                palette = palette,
                wheelOn = state.wheelHeatOn,
                autoOn = state.autoOn,
                slot = slot,
                seatHeat = state.seatHeatL,
                seatVent = state.seatVentL,
                onWheel = { onCommand(Command.WHEEL_HEAT) },
                onSlot = {
                    onCommand(
                        when (slot) {
                            SlotContent.FRONT_DEFROST -> Command.FRONT_DEFROST
                            SlotContent.SEAT_HEAT -> Command.SEAT_HEAT_L
                            SlotContent.SEAT_COOL -> Command.SEAT_VENT_L
                        },
                    )
                },
                onAuto = { onCommand(Command.AUTO) },
                onClimate = onOpenClimate,
                onSlotPressChange = onSlotPressChange,
            )
            BarZones(
                palette = palette,
                driver = state.tempLeft,
                passenger = state.tempRight,
                onDriverDown = { onCommand(Command.TEMP_L_DOWN) },
                onDriverUp = { onCommand(Command.TEMP_L_UP) },
                onPassengerDown = { onCommand(Command.TEMP_R_DOWN) },
                onPassengerUp = { onCommand(Command.TEMP_R_UP) },
            )
        }

        BarVolume(
            palette = palette,
            volume = state.volume,
            onUp = { onCommand(Command.VOL_UP) },
            onDown = { onCommand(Command.VOL_DOWN) },
        )
    }
}
```

- [ ] **Step 2: Add the bar to the harness**

Add to `harness/build.gradle.kts` dependencies:

```kotlin
    implementation(project(":ui"))
```

In `HarnessActivity.kt`, add a mode toggle so the harness can show either the
inspector or the real bar. Add these imports:

```kotlin
import com.wk2.climate.bus.SlotContent
import com.wk2.climate.ui.bar.ClimateBar
```

Add to the `Harness` composable, immediately after the `scrub` function:

```kotlin
    var showBar by remember { mutableStateOf(false) }

    if (showBar) {
        Column(
            Modifier.fillMaxSize().background(palette.surface),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Key("INSPECTOR", palette) { showBar = false }
            ClimateBar(
                state = state,
                slot = slotContent,
                onCommand = { bus.send(it) },
                onHome = {},
                onBack = {},
                onOpenClimate = {},
                onSlotPressChange = { down ->
                    if (down) slot.onFingerDown() else slot.onFingerUp()
                },
            )
        }
        return
    }
```

And add a `BAR` key to the inject row:

```kotlin
            Key("BAR", palette) { showBar = true }
```

- [ ] **Step 3: Build and install on the panel-geometry emulator**

```bash
export ANDROID_HOME="C:/Users/chris/AppData/Local/Android/Sdk"
"$ANDROID_HOME/emulator/emulator.exe" -avd wk2_panel -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect &
# wait for boot
until [ "$(adb -e shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 5; done
adb -e shell wm size    # must report 1080x1920
adb -e shell wm density # must report 160
./gradlew :harness:installDebug
adb -e shell am start -n com.wk2.climate.harness/.HarnessActivity
```

- [ ] **Step 4: Verify the bar renders and behaves**

Tap `BAR`, then confirm by screenshot and `uiautomator dump`:

1. The bar occupies exactly 1080 × 227 at the bottom of the screen.
2. `AUTO` is filled amber (the fake bus starts with AUTO engaged).
3. Both zones read `68°`, labelled `DRIVER` and `PASSENGER`.
4. The adaptive slot reads `SEAT HEAT` / `OFF`.
5. `VOL 10` in the right column.
6. Tapping driver `+` steps to `69°`; **holding it** climbs continuously.
7. Tapping `AUTO` unfills it and the fan leaves AUTO.
8. Tapping the slot cycles the seat state `OFF → HIGH → LOW → OFF`.
9. `NIGHT` in the inspector flips the bar's palette with **no layout change**.

```bash
adb -e shell screencap -p /sdcard/bar.png
adb -e pull /sdcard/bar.png docs/screenshots/bar-2a-night.png
```

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar/ClimateBar.kt harness docs/screenshots
git commit -m "feat(ui): assemble screen 2a and verify it on the panel geometry

ClimateBar composes the three columns into the full 1080x227 bar,
rendered entirely from ClimateState with every tap dispatching a Command
and mutating nothing locally.

CLIMATE takes an onOpenClimate lambda rather than owning navigation, so
plan 3 can wire the panel to it without touching this file.

The harness gains a BAR mode so the real bar is verified on an emulator
at 1080x1920/160dpi -- the target panel exactly -- before it ever reaches
the vehicle."
```

---

## Task 7: Compose in a raw window

`WindowManager.addView` gives Compose no lifecycle owner, no saved-state
registry and no ViewModel store, and `ComposeView` requires all three. This is
the shim, written once and shared by the bar window and (in Plan 3) the panel.

**Files:**
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/wk2/climate/app/ComposeOverlayHost.kt`
- Modify: `settings.gradle.kts` — add `include(":app")`

**Interfaces:**
- Consumes: `:ui`, `:bus`, `:design`.
- Produces: `class ComposeOverlayHost(context: Context)` with `fun show(params: WindowManager.LayoutParams, content: @Composable () -> Unit)`, `fun hide()`, `val isShowing: Boolean`, and `fun destroy()`.

- [ ] **Step 1: Add the `:app` module**

Append to `settings.gradle.kts`:

```kotlin
include(":app")
```

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wk2.climate.app"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.wk2.climate"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.savedstate)
}
```

Add to `gradle/libs.versions.toml` under `[versions]`:

```toml
lifecycleRuntime = "2.9.4"
savedstate = "1.3.1"
```

and under `[libraries]`:

```toml
androidx-lifecycle-runtime = { module = "androidx.lifecycle:lifecycle-runtime", version.ref = "lifecycleRuntime" }
androidx-savedstate = { module = "androidx.savedstate:savedstate", version.ref = "savedstate" }
```

> **Verify these resolve before writing any code**, and bump to the newest
> stable if they do not:
>
> ```bash
> ./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep -E 'lifecycle|savedstate'
> ```
>
> Plan 1 lost two build rounds to exactly this class of guess: a coordinate
> lifted from a neighbouring project that had no such release. Also confirm
> which artifact actually carries `setViewTreeLifecycleOwner`,
> `setViewTreeViewModelStoreOwner` and `setViewTreeSavedStateRegistryOwner` --
> they may live in `lifecycle-runtime-ktx` / `savedstate-ktx` rather than the
> base artifacts. Let the compiler decide, and record what you used.

- [ ] **Step 2: Write `ComposeOverlayHost.kt`**

```kotlin
package com.wk2.climate.app

import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts Compose content in a raw `WindowManager` window.
 *
 * `ComposeView` needs a lifecycle owner, a saved-state registry and a
 * ViewModel store, all of which an Activity supplies and a bare window does
 * not. Without them the view attaches and then renders nothing, which is a
 * confusing failure — it looks like a layout bug rather than a missing owner.
 *
 * One instance owns one window. The bar keeps one alive for the life of the
 * service; the climate panel (plan 3) creates and destroys one on demand.
 */
class ComposeOverlayHost(private val context: Context) :
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    init {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun show(params: WindowManager.LayoutParams, content: @Composable () -> Unit) {
        if (view != null) return
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ComposeOverlayHost)
            setViewTreeViewModelStoreOwner(this@ComposeOverlayHost)
            setViewTreeSavedStateRegistryOwner(this@ComposeOverlayHost)
            setContent { content() }
        }
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        windowManager.addView(composeView, params)
        view = composeView
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { windowManager.removeView(v) }
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        hide()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
```

- [ ] **Step 3: Verify it builds**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml app
git commit -m "feat(app): Compose host for a raw WindowManager window

ComposeView needs a lifecycle owner, a saved-state registry and a
ViewModel store. An Activity supplies all three; a bare window supplies
none, and without them the view attaches and renders nothing -- which
looks like a layout bug rather than a missing owner, so it is worth
having in one place with a comment.

One instance owns one window. The bar holds one for the life of the
service; the climate panel will create and destroy one on demand."
```

---

## Task 8: The service, and the bar in the truck

**Files:**
- Create: `app/src/main/res/xml/accessibility_config.xml`
- Create: `app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `ComposeOverlayHost`, `ClimateBar`, `SyuVehicleBus`, `AdaptiveSlot`.
- Produces: an installable app whose accessibility service draws the bar.

- [ ] **Step 1: Write the manifest and accessibility config**

`app/src/main/res/xml/accessibility_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canPerformGestures="false"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100"
    android:description="@string/service_description" />
```

`app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">WK2 Climate</string>
    <string name="service_description">Draws the replacement climate and navigation bar over the factory bar, and provides its Home and Back actions.</string>
</resources>
```

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Required or bindService to the vendor toolkit fails SILENTLY on
         Android 11+, even though the service is exported. -->
    <queries>
        <package android:name="com.syu.ms" />
    </queries>

    <application
        android:label="@string/app_name"
        android:allowBackup="false">

        <service
            android:name=".ClimateBarService"
            android:exported="true"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_config" />
        </service>
    </application>
</manifest>
```

- [ ] **Step 2: Write `ClimateBarService.kt`**

```kotlin
package com.wk2.climate.app

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wk2.climate.bus.AdaptiveSlot
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Signal
import com.wk2.climate.bus.SyuVehicleBus
import com.wk2.climate.ui.bar.ClimateBar
import kotlinx.coroutines.delay

/**
 * Owns the replacement bar.
 *
 * An `AccessibilityService` rather than a foreground service plus a 2038
 * overlay, because HOME and BACK have **no other route** for a third-party app
 * — so one of these is required regardless, and this adds nothing else. It also
 * needs no `SYSTEM_ALERT_WINDOW`, and a 2032 window is a *trusted* overlay that
 * the system does not hide during permission dialogs.
 *
 * `com.syu.air` keeps running underneath. This is an alternative front end, not
 * a replacement service: if this dies, the factory bar is still there, and a
 * vehicle with no physical HVAC controls is never left unable to change its
 * climate.
 */
class ClimateBarService : AccessibilityService() {

    private lateinit var bus: SyuVehicleBus
    private lateinit var barHost: ComposeOverlayHost
    private val slot = AdaptiveSlot()

    override fun onServiceConnected() {
        bus = SyuVehicleBus(this).also { it.connect() }
        barHost = ComposeOverlayHost(this)
        showBar()
    }

    private fun showBar() {
        barHost.show(barWindowParams()) {
            val state by bus.state.collectAsState()

            // The slot is re-evaluated on a timer rather than per state change:
            // its own hysteresis and dwell decide whether anything moves, and
            // outside temperature is not currently a live signal at all.
            var slotContent by remember { mutableStateOf(slot.content) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    slotContent = slot.update(outsideF(state), System.currentTimeMillis())
                    delay(SLOT_POLL_MS)
                }
            }

            ClimateBar(
                state = state,
                slot = slotContent,
                onCommand = { bus.send(it) },
                onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
                onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
                onOpenClimate = { /* screen 1d arrives in plan 3 */ },
                onSlotPressChange = { down ->
                    if (down) slot.onFingerDown() else slot.onFingerUp()
                },
            )
        }
    }

    /**
     * Outside temperature, or null when we do not have it.
     *
     * Currently always null: `U_TEMP_OUT` reads a static packed word that does
     * not match what the head unit's own status bar displays, so it is treated
     * as undecoded. [AdaptiveSlot] pins to SEAT HEAT in that case, which is why
     * the slot is never blank and the geometry never changes.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun outsideF(state: ClimateState): Int? = null

    /**
     * The verified parameters for covering the factory bar.
     *
     * Measured on the vehicle: this lands exactly on `[0,1693][1080,1920]` as
     * the topmost window. `FLAG_LAYOUT_NO_LIMITS` is load-bearing — without it
     * gravity resolves against the inset-reduced frame and the window cannot
     * enter the nav-bar region at all. Positioning from the TOP with an
     * absolute y sidesteps the negative-offset problem entirely.
     */
    private fun barWindowParams(): WindowManager.LayoutParams {
        val height = navigationBarHeightPx()
        val y = resources.displayMetrics.heightPixels - height
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            this.y = y
        }
    }

    /**
     * The bar's height is the framework's own `navigation_bar_height`, which is
     * where the 227px comes from in the first place. Reading it keeps us aligned
     * with the inset the system reserves instead of asserting a number.
     */
    private fun navigationBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else BAR_HEIGHT_FALLBACK_PX
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        if (::barHost.isInitialized) barHost.destroy()
        if (::bus.isInitialized) bus.disconnect()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private companion object {
        /**
         * Fallback for the framework's `navigation_bar_height`, which this ROM
         * raises from AOSP's 48dp to 227px. Read the real value where possible
         * rather than trusting this.
         */
        const val BAR_HEIGHT_FALLBACK_PX = 227

        /** The slot's own dwell is 30s, so polling faster than this buys nothing. */
        const val SLOT_POLL_MS = 5_000L
    }
}
```

- [ ] **Step 3: Build and verify on the emulator first**

The emulator has no vendor bus, so the bar will render from an empty
`ClimateState` — dashes and unlit controls. That is exactly what we want to
check: **it must not crash when the bus is absent**, which is also what happens
on the vehicle if `com.syu.ms` is slow to bind.

```bash
./gradlew :app:installDebug
adb -e shell settings put secure enabled_accessibility_services \
  com.wk2.climate/com.wk2.climate.app.ClimateBarService
adb -e shell settings put secure accessibility_enabled 1
sleep 3
adb -e shell "dumpsys window windows | grep -A4 com.wk2.climate"
adb -e shell screencap -p /sdcard/svc.png
adb -e pull /sdcard/svc.png docs/screenshots/bar-emulator-nobus.png
```

Expected: a window of type 2032, 1080×227, at `[0,1693][1080,1920]`, and no
`FATAL` in `adb logcat -d -s AndroidRuntime:E`.

Then restore the emulator:

```bash
adb -e shell settings put secure enabled_accessibility_services ""
```

- [ ] **Step 4: Install in the vehicle**

**Take a baseline first.** Then install without disturbing the existing
accessibility services — the OEM gesture service and the launcher's service are
both enabled, and overwriting the list breaks gesture navigation.

```bash
adb connect $(adb mdns services | grep '_adb-tls-connect' | awk '{print $3}' | head -1)

# 1. Baseline, so we can restore exactly.
adb shell settings get secure enabled_accessibility_services > a11y-baseline.txt
cat a11y-baseline.txt

# 2. Install.
./gradlew :app:installDebug

# 3. APPEND our service; never replace the list.
PREV="$(cat a11y-baseline.txt)"
adb shell "settings put secure enabled_accessibility_services '$PREV:com.wk2.climate/com.wk2.climate.app.ClimateBarService'"
adb shell settings put secure accessibility_enabled 1
```

- [ ] **Step 5: Verify in the vehicle**

Confirm before touching anything:

```bash
# Our window is topmost and exactly over the factory bar.
adb shell "dumpsys window windows | grep -B2 -A6 com.wk2.climate" | head -20

# The factory bar is STILL RUNNING underneath — this is design rule 6.
adb shell "ps -A -o NAME | grep syu.air"

# The inset is intact, so no app content was lost.
adb shell "dumpsys window displays | grep -oE 'app=[0-9]+x[0-9]+'"
```

Expected: our window at `[0,1693][1080,1920]` type 2032; `com.syu.air` alive;
`app=1080x1693`.

Then, on a **stationary vehicle**, check by hand:

1. The bar renders live vehicle state — real temperatures, real volume, AUTO
   lit if AUTO is engaged.
2. Driver `+` raises the temperature by one degree; holding it ramps.
3. `AUTO` toggles and the tile fill follows the vehicle, not the tap.
4. The adaptive slot cycles seat heat `OFF → HIGH → LOW → OFF`.
5. Volume `▲`/`▼` move the volume, and the OEM volume OSD does not appear over
   the bar.
6. `HOME` and `BACK` work.
7. Turning the headlights on switches the bar to the night palette — **this
   also finally answers checklist item 8**, `U_LAMPLET` polarity. If the
   palette goes the wrong way, invert `ClimateState.isNight`.
8. Nothing beneath the bar actuates when you tap it.

- [ ] **Step 6: Restore if anything is wrong**

```bash
adb shell "settings put secure enabled_accessibility_services '$(cat a11y-baseline.txt)'"
adb uninstall com.wk2.climate
```

The factory bar is untouched throughout, so this restores the vehicle
completely.

- [ ] **Step 7: Commit**

```bash
git add app docs/screenshots
git commit -m "feat(app): the bar service, running on the vehicle

ClimateBarService owns the bus, draws screen 2a in a 2032 window at the
measured coordinates, and performs HOME and BACK.

An AccessibilityService rather than a foreground service plus a 2038
overlay, because HOME and BACK have no other route for a third-party app
-- one of these is required regardless, so this adds nothing else. It
needs no SYSTEM_ALERT_WINDOW, and 2032 is a trusted overlay the system
does not hide during permission dialogs.

com.syu.air keeps running underneath. If this service dies the factory
bar is still there, so a vehicle with no physical HVAC controls is never
left unable to change its climate.

Verified on the emulator with no vendor bus present -- it renders dashes
rather than crashing, which is also what happens on the vehicle if
com.syu.ms is slow to bind -- and then on the vehicle itself."
```

---

## Definition of done for Plan 2

- [ ] `./gradlew build` succeeds.
- [ ] `:bus` still reports 71 passing tests; `:ui` adds 6 (77 total).
- [ ] No Material artifact on any runtime classpath and no Material import in any source.
- [ ] `:ui` does not depend on `:app` — `./gradlew :ui:dependencies` shows no `project :app`.
- [ ] The bar renders correctly in the harness on a 1080x1920/160dpi emulator, all nine checks in Task 6 Step 4.
- [ ] The service draws a 2032 window at `[0,1693][1080,1920]` on the vehicle, with `com.syu.air` still alive and `app=1080x1693` unchanged.
- [ ] HOME, BACK, temperature, AUTO, the adaptive slot and volume all work on the vehicle.
- [ ] `U_LAMPLET` polarity confirmed by toggling the headlights (closes checklist item 8).

## Carried forward to Plan 3

Screen 1d, wired to the `onOpenClimate` lambda this plan leaves as a no-op:
the panel window, the 220ms transition, the fan meter, the four airflow tiles,
the mode and comfort grids, and the hold-to-confirm power gesture.
