# Seat Buttons and Seat Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace screen 2a's two adaptive cells with a static row — driver seat button, AUTO, CLIMATE, passenger seat button — where each seat button is a live heat/cool indicator that opens a per-seat menu in its own overlay window, and delete the outside-temperature slot logic that drove the old cells.

**Architecture:** Two new pure pieces carry the logic and are unit-tested: `SeatIndicator` (`:ui`) derives what a button shows from a seat's two levels, and `SeatMenuLatch` (`:bus`) decides which menu is open, including the re-tap race between the menu window's `ACTION_OUTSIDE` and the bar's click. `ComposeOverlayHost` gains an outside-touch hook; `ClimateBarService` creates and destroys one menu window on demand exactly as it does the panel. `AdaptiveSlot` and everything that fed it is removed.

**Tech Stack:** Kotlin 2.3.0, AGP 9.0.1, Gradle 9.1.0, Compose ui + foundation 1.10.3 (no Material), kotlinx-coroutines 1.10.2, JUnit 4.13.2.

**Spec:** `docs/superpowers/specs/2026-09-14-seat-buttons-design.md`. It supersedes §6 "The adaptive slot" and the §8 "Tap adaptive slot" row of `docs/superpowers/specs/2026-09-08-wk2-climate-design.md`; everything else in that spec stands.

## Global Constraints

- **Commits carry no AI attribution.** No `Co-Authored-By`, no `Claude-Session`, no "Claude" or "AI-generated" anywhere in a message. Write as the owner. This repo squashes on merge; the branch is `feat/seat-buttons`.
- **Do NOT apply `org.jetbrains.kotlin.android`** — AGP 9.0 rejects it.
- **No Material dependency.** `BasicText`, never Material `Text`. Do not add `ui-tooling`.
- **`:ui` must never depend on `:app`.** No composable references a `WindowManager` or an `AccessibilityService`. `:design` depends on nothing in the project, so nothing in `Dimens` may name `SeatSide`.
- **Filter tests with `./gradlew :<module>:testDebugUnitTest --tests '<pattern>'`.** The full suite is `./gradlew test`. Baseline on 2026-09-14, verified in this working copy with `./gradlew test --rerun-tasks`: **153 tests, 0 failures**. The suite must be green after every task, and the final count must be higher than 153 by the tests this plan adds minus the 25 in `AdaptiveSlotTest`.
- **1px = 1dp.** Spec §2–§4 measurements are approximate (read off a screenshot at ~1.25 px/dp); use them as given here and note them as approximate in KDoc where they land in `Dimens`.
- **96dp minimum on every tappable region.** Every top-row cell and every menu row.
- **Fixed geometry.** Explicit `.width()`/`.height()` on every cell and row. Nothing in the bar moves, resizes or changes position when state changes — a seat button's readout row has a fixed height so the glyph does not shift between pips and text.
- **Pressed feedback on touch-down, never on release. No ripple, no shadows.** Use `rememberPressState()` + `Modifier.target()` + `pressedTint()` from `ui/Interaction.kt`.
- **No animation on open or close, and no value is ever animated.**
- **Render only from `VehicleBus.state`.** Every tap dispatches a `Command` and mutates nothing locally. The menu paints reported state and stays open across row taps.
- **Never create a window that cannot drive the vehicle.** `openMenu` is a no-op without a connected bus; a dead bus and `teardown()` remove the menu first, then the panel, then the bar.
- **Vehicle safety:** never test the dead-bus path by disabling `com.syu.ms`. It is the vendor service the factory bar depends on and this vehicle has no physical HVAC controls.
- **`outsideF()` in `ClimateBarService` stays.** Screen 1d's header still shows it. Only its KDoc changes.

---

## File Structure

```
bus/src/main/kotlin/com/wk2/climate/bus/
    SeatMenuLatch.kt        NEW  SeatSide enum + the open/close state machine with the re-tap rule — Task 1
    AdaptiveSlot.kt         DELETE — Task 8
bus/src/test/kotlin/com/wk2/climate/bus/
    SeatMenuLatchTest.kt    NEW — Task 1
    AdaptiveSlotTest.kt     DELETE — Task 8

ui/src/main/kotlin/com/wk2/climate/ui/
    SeatPips.kt             NEW  the two-pip indicator, extracted from PanelComfort, sized by the caller — Task 3
    Glyphs.kt               MODIFY  plain seat glyphs; the four seat path constants become internal — Task 3
    bar/SeatIndicator.kt    NEW  pure derivation: heat/cool/off/unknown + lit pips — Task 2
    bar/SeatButton.kt       NEW  the 128 x 96 bar cell — Task 5
    bar/BarTopRow.kt        REWRITE  four fixed cells — Task 5
    bar/ClimateBar.kt       MODIFY  new parameters — Task 5
    bar/SeatMenu.kt         NEW  menu content + row count / height / x helpers — Task 6
    panel/PanelComfort.kt   MODIFY  use the shared SeatPips; KDoc — Task 3, Task 8
ui/src/test/kotlin/com/wk2/climate/ui/
    SeatIndicatorTest.kt    NEW — Task 2
    GlyphsTest.kt           NEW  the plain seat is the cool seat's outline subpath — Task 3
    BarGeometryTest.kt      MODIFY  new top row; menu geometry — Task 4, Task 6

design/src/main/kotlin/com/wk2/climate/design/
    Dimens.kt               MODIFY  seatButton, 256/256, seat menu geometry, radiusMenu; retire slotAdaptive* — Task 4, Task 5
    Type.kt                 MODIFY  menuLabel, seatButtonState; retire slotLabel, slotLabelSmall, slotState — Task 4, Task 5

app/src/main/kotlin/com/wk2/climate/app/
    ComposeOverlayHost.kt   MODIFY  optional outside-touch callback — Task 7
    ClimateBarService.kt    MODIFY  drop the slot (Task 5); menu window, latch, BACK/HOME/CLIMATE/dead-bus wiring (Task 7); outsideF KDoc (Task 8)

harness/src/main/kotlin/com/wk2/climate/harness/
    HarnessActivity.kt      MODIFY  drop the slot (Task 5, Task 8); seat screen and inline menu (Task 9)

docs/, README.md            Task 10
```

---

### Task 1: `SeatSide` and the menu state machine

**Files:**
- Create: `bus/src/main/kotlin/com/wk2/climate/bus/SeatMenuLatch.kt`
- Test: `bus/src/test/kotlin/com/wk2/climate/bus/SeatMenuLatchTest.kt`

**Interfaces:**
- Produces: `enum class SeatSide { DRIVER, PASSENGER }`
- Produces: `class SeatMenuLatch(retapWindowMillis: Long = 400L)` with `val open: SeatSide?`, `fun onButtonTap(side: SeatSide, nowMillis: Long): SeatSide?`, `fun onOutsideTouch(nowMillis: Long): SeatSide?`, `fun close(): SeatSide?`. Every mutator returns the new `open` so the caller can apply it in one line.

Why 400 ms rather than the spec's "approx 300": Android's own definition of a tap is a press released inside the long-press timeout, which is 400 ms (`ViewConfiguration.DEFAULT_LONG_PRESS_TIMEOUT`). `ACTION_OUTSIDE` arrives on the touch's DOWN and the bar's click fires on its UP, so the window has to cover a whole tap; a press held past 400 ms is not a tap by the platform's definition and reopening on it is acceptable.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeatMenuLatchTest {

    private val window = SeatMenuLatch.DEFAULT_RETAP_WINDOW_MS

    @Test
    fun `starts with no menu open`() {
        assertNull(SeatMenuLatch().open)
    }

    @Test
    fun `a tap on a seat button opens that side`() {
        val latch = SeatMenuLatch()
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 0))
        assertEquals(SeatSide.DRIVER, latch.open)
    }

    @Test
    fun `a tap on the other seat button switches sides`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertEquals(SeatSide.PASSENGER, latch.onButtonTap(SeatSide.PASSENGER, 1_000))
    }

    @Test
    fun `a tap on the open side's own button with no outside touch first closes it`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertNull(latch.onButtonTap(SeatSide.DRIVER, 1_000))
        assertNull(latch.open)
    }

    @Test
    fun `an outside touch closes the menu`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertNull(latch.onOutsideTouch(1_000))
        assertNull(latch.open)
    }

    @Test
    fun `an outside touch with nothing open is a no-op`() {
        val latch = SeatMenuLatch()
        assertNull(latch.onOutsideTouch(0))
        assertNull(latch.open)
    }

    @Test
    fun `re-tap race - the own-button click that follows the outside touch inside the window is the same tap and stays closed`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)                  // the DOWN, seen by the menu window
        assertNull(latch.onButtonTap(SeatSide.DRIVER, 5_000 + window - 1))   // the UP, seen by the bar
        assertNull(latch.open)
    }

    @Test
    fun `an own-button tap after the window is a fresh tap and reopens`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 5_000 + window))
    }

    @Test
    fun `the other side's button inside the window is a switch, not a swallowed re-tap`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        assertEquals(SeatSide.PASSENGER, latch.onButtonTap(SeatSide.PASSENGER, 5_000 + 50))
    }

    @Test
    fun `a swallowed re-tap consumes the record so an immediate second tap opens`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        latch.onButtonTap(SeatSide.DRIVER, 5_050)    // swallowed
        // The menu is closed, so no window exists and no ACTION_OUTSIDE precedes this click.
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 5_200))
    }

    @Test
    fun `close is not an outside touch - a tap right after it opens`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertNull(latch.close())
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 10))
    }

    @Test
    fun `an outside touch on app content followed much later by a tap opens normally`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.PASSENGER, 0)
        latch.onOutsideTouch(1_000)
        assertEquals(SeatSide.PASSENGER, latch.onButtonTap(SeatSide.PASSENGER, 60_000))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :bus:testDebugUnitTest --tests 'com.wk2.climate.bus.SeatMenuLatchTest'`
Expected: compilation failure, `Unresolved reference: SeatMenuLatch` / `SeatSide`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.wk2.climate.bus

/** Which seat a button, a menu or a command belongs to. Driver is the left seat on this vehicle. */
enum class SeatSide { DRIVER, PASSENGER }

/**
 * Decides which seat menu, if any, is open.
 *
 * The menu lives in its own overlay window above the bar, opened by a tap on
 * its seat button and closed by a touch anywhere outside it. Those two facts
 * collide on one gesture: **tapping the owning seat button while its menu is
 * open is also a touch outside the menu window.** The window sees the touch's
 * DOWN as `ACTION_OUTSIDE` and closes; the bar then sees the same touch's UP
 * as a click on the seat button and, with nothing open, would reopen it. The
 * driver taps once and the menu flickers shut and open again.
 *
 * The rule here: an outside touch closes the menu and records which side it
 * closed and when. A click on *that* side's button inside [retapWindowMillis]
 * is the UP of the same tap, so it is swallowed. A click on the other side, or
 * on the same side after the window, is a fresh tap and opens. The record is
 * consumed by the first click that consults it, so a quick second tap after a
 * swallowed one opens as the driver expects.
 *
 * 400 ms is the platform's own long-press timeout: a touch released inside it
 * is a tap by Android's definition, and one held past it is not. The outside
 * touch is timestamped at DOWN and the click at UP, so the window has to cover
 * a whole tap.
 *
 * Pure Kotlin with an injected clock, alongside [ConnectionGate] and
 * [RefreshRetry], so the race is proven here rather than found in the car.
 * Checking `ACTION_OUTSIDE` coordinates against the button rect would be the
 * other way to do this; those coordinates are only populated for touches on
 * windows of the same UID, which happens to be true here, but a rule that
 * does not depend on it is simpler to reason about.
 *
 * Every mutator returns the new [open] so the caller can apply it in one
 * expression. Row taps inside the menu never come here: the menu stays open
 * across them, because cycling a seat to LOW takes two taps.
 */
class SeatMenuLatch(private val retapWindowMillis: Long = DEFAULT_RETAP_WINDOW_MS) {

    var open: SeatSide? = null
        private set

    private var outsideClosed: SeatSide? = null
    private var outsideClosedAt = Long.MIN_VALUE / 4

    /** A click on a seat button, from the bar. */
    fun onButtonTap(side: SeatSide, nowMillis: Long): SeatSide? {
        val sameTapAsTheOutsideTouch =
            open == null && side == outsideClosed && nowMillis - outsideClosedAt < retapWindowMillis
        outsideClosed = null
        open = when {
            sameTapAsTheOutsideTouch -> null
            open == side -> null
            else -> side
        }
        return open
    }

    /** `ACTION_OUTSIDE` on the open menu's window. */
    fun onOutsideTouch(nowMillis: Long): SeatSide? {
        val was = open ?: return null
        open = null
        outsideClosed = was
        outsideClosedAt = nowMillis
        return null
    }

    /** Every non-touch close: BACK, HOME, CLIMATE, a dead bus, teardown. Leaves no re-tap record. */
    fun close(): SeatSide? {
        open = null
        outsideClosed = null
        return null
    }

    companion object {
        const val DEFAULT_RETAP_WINDOW_MS = 400L
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :bus:testDebugUnitTest --tests 'com.wk2.climate.bus.SeatMenuLatchTest'`
Expected: BUILD SUCCESSFUL, 12 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add bus/src/main/kotlin/com/wk2/climate/bus/SeatMenuLatch.kt bus/src/test/kotlin/com/wk2/climate/bus/SeatMenuLatchTest.kt
git commit -m "feat(bus): SeatSide and the seat-menu latch that settles the re-tap race"
```

---

### Task 2: `SeatIndicator` — what a seat button shows

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatIndicator.kt`
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/SeatIndicatorTest.kt`

**Interfaces:**
- Consumes: `SeatLevel` (`bus/Values.kt`): `OFF, LOW, HIGH, UNAVAILABLE`, `isOn`, `litPips`.
- Produces: `data class SeatIndicator(val kind: Kind, val litPips: Int)` with `enum class Kind { HEAT, COOL, OFF, UNKNOWN }` and `companion fun of(heat: SeatLevel, vent: SeatLevel): SeatIndicator`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.wk2.climate.ui

import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.ui.bar.SeatIndicator
import com.wk2.climate.ui.bar.SeatIndicator.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class SeatIndicatorTest {

    @Test
    fun `heat LOW is one warm pip and heat HIGH is two`() {
        assertEquals(SeatIndicator(Kind.HEAT, 1), SeatIndicator.of(SeatLevel.LOW, SeatLevel.OFF))
        assertEquals(SeatIndicator(Kind.HEAT, 2), SeatIndicator.of(SeatLevel.HIGH, SeatLevel.OFF))
    }

    @Test
    fun `cool LOW is one cool pip and cool HIGH is two`() {
        assertEquals(SeatIndicator(Kind.COOL, 1), SeatIndicator.of(SeatLevel.OFF, SeatLevel.LOW))
        assertEquals(SeatIndicator(Kind.COOL, 2), SeatIndicator.of(SeatLevel.OFF, SeatLevel.HIGH))
    }

    @Test
    fun `both reported off is OFF`() {
        assertEquals(SeatIndicator(Kind.OFF, 0), SeatIndicator.of(SeatLevel.OFF, SeatLevel.OFF))
    }

    @Test
    fun `both unreported is UNKNOWN, never OFF`() {
        assertEquals(SeatIndicator(Kind.UNKNOWN, 0), SeatIndicator.of(SeatLevel.UNAVAILABLE, SeatLevel.UNAVAILABLE))
    }

    @Test
    fun `one off and the other unreported is still UNKNOWN`() {
        assertEquals(SeatIndicator(Kind.UNKNOWN, 0), SeatIndicator.of(SeatLevel.OFF, SeatLevel.UNAVAILABLE))
        assertEquals(SeatIndicator(Kind.UNKNOWN, 0), SeatIndicator.of(SeatLevel.UNAVAILABLE, SeatLevel.OFF))
    }

    @Test
    fun `a reported level wins over an unreported one`() {
        assertEquals(SeatIndicator(Kind.HEAT, 2), SeatIndicator.of(SeatLevel.HIGH, SeatLevel.UNAVAILABLE))
        assertEquals(SeatIndicator(Kind.COOL, 1), SeatIndicator.of(SeatLevel.UNAVAILABLE, SeatLevel.LOW))
    }

    @Test
    fun `heat and cool both on renders heat, at heat's level`() {
        // The protocol makes them mutually exclusive; this can only arrive transiently.
        assertEquals(SeatIndicator(Kind.HEAT, 2), SeatIndicator.of(SeatLevel.HIGH, SeatLevel.LOW))
        assertEquals(SeatIndicator(Kind.HEAT, 1), SeatIndicator.of(SeatLevel.LOW, SeatLevel.HIGH))
    }

    @Test
    fun `every combination lights pips only when heat or cool is shown`() {
        for (heat in SeatLevel.entries) for (vent in SeatLevel.entries) {
            val i = SeatIndicator.of(heat, vent)
            val expectedPips = when (i.kind) {
                Kind.HEAT -> heat.litPips
                Kind.COOL -> vent.litPips
                Kind.OFF, Kind.UNKNOWN -> 0
            }
            assertEquals("$heat / $vent", expectedPips, i.litPips)
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.SeatIndicatorTest'`
Expected: compilation failure, `Unresolved reference: SeatIndicator`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.wk2.climate.ui.bar

import com.wk2.climate.bus.SeatLevel

/**
 * What a seat button shows, derived once from the seat's heat and vent levels.
 *
 * One derivation rather than branches inline in the composable, so the four
 * states -- and the one the protocol says cannot happen -- are decided in a
 * place a unit test can reach. Heat and vent are mutually exclusive on the
 * vehicle, so both non-zero can only arrive transiently between two frames;
 * heat wins, arbitrarily but consistently.
 *
 * OFF is a positive claim and is only made when **both** levels are reported
 * as off. Anything short of that with nothing on is UNKNOWN and renders as an
 * em dash, matching every other readout in the bar: claiming a control is off
 * when we do not know is worse than admitting we do not know. `SeatLevel`
 * already carries UNAVAILABLE for an unreported signal, and
 * `hasClimateData == false` means both levels are UNAVAILABLE by construction,
 * so no second `live` gate is needed here.
 */
data class SeatIndicator(val kind: Kind, val litPips: Int) {

    enum class Kind { HEAT, COOL, OFF, UNKNOWN }

    companion object {
        fun of(heat: SeatLevel, vent: SeatLevel): SeatIndicator = when {
            heat.isOn -> SeatIndicator(Kind.HEAT, heat.litPips)
            vent.isOn -> SeatIndicator(Kind.COOL, vent.litPips)
            heat == SeatLevel.OFF && vent == SeatLevel.OFF -> SeatIndicator(Kind.OFF, 0)
            else -> SeatIndicator(Kind.UNKNOWN, 0)
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.SeatIndicatorTest'`
Expected: BUILD SUCCESSFUL, 8 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatIndicator.kt ui/src/test/kotlin/com/wk2/climate/ui/SeatIndicatorTest.kt
git commit -m "feat(ui): SeatIndicator derives a seat button's glyph and pips from its two levels"
```

---

### Task 3: Plain seat glyphs and the shared `SeatPips`

**Files:**
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/Glyphs.kt`
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/SeatPips.kt`
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt` (the private `SeatPips` at the bottom of the file and its two call sites in `ComfortTile`)
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/GlyphsTest.kt`

**Interfaces:**
- Produces: `@Composable fun SeatPlainLeftGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier)` and `SeatPlainRightGlyph(...)`.
- Produces: `internal const val SEAT_PLAIN_LEFT`, `SEAT_PLAIN_RIGHT`; `SEAT_COOL_LEFT` and `SEAT_COOL_RIGHT` change from `private` to `internal` so the test can compare them.
- Produces: `@Composable fun SeatPips(palette: Palette, litPips: Int, litColor: Color, pipHeight: Dp, gap: Dp, modifier: Modifier = Modifier, pipWidth: Dp? = null)` in package `com.wk2.climate.ui`. `pipWidth == null` means each pip takes `weight(1f)` of the row, as the panel's tiles need; a fixed width is what the bar button needs.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.wk2.climate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plain seat is the seat-outline subpath that opens each cool-seat glyph,
 * so the silhouette is identical to the heat and cool variants by
 * construction. These pin that: the plain path must be a closed prefix of the
 * cool path, and what follows it must be the snowflake's relative moveto.
 */
class GlyphsTest {

    @Test
    fun `the plain left seat is exactly the cool left seat's outline subpath`() {
        assertTrue(SEAT_PLAIN_LEFT.endsWith("z"))
        assertTrue(SEAT_COOL_LEFT.startsWith(SEAT_PLAIN_LEFT))
        assertEquals('m', SEAT_COOL_LEFT[SEAT_PLAIN_LEFT.length])
    }

    @Test
    fun `the plain right seat is exactly the cool right seat's outline subpath`() {
        assertTrue(SEAT_PLAIN_RIGHT.endsWith("z"))
        assertTrue(SEAT_COOL_RIGHT.startsWith(SEAT_PLAIN_RIGHT))
        assertEquals('m', SEAT_COOL_RIGHT[SEAT_PLAIN_RIGHT.length])
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.GlyphsTest'`
Expected: compilation failure, `Unresolved reference: SEAT_PLAIN_LEFT` (and `SEAT_COOL_LEFT` is private).

- [ ] **Step 3: Add the glyphs to `Glyphs.kt`**

After `SeatCoolRightGlyph`, add:

```kotlin
/**
 * The driver's seat with no heat or cool mark: the bar's seat button when the
 * seat is off or unreported.
 *
 * The icon set has no plain seat, so this is the seat-outline subpath that
 * opens `material-symbols:seat-cool-left-sharp` -- [SEAT_COOL_LEFT] begins
 * with exactly this path and then draws its snowflake. Taking the outline from
 * the same source keeps the silhouette identical to the heat and cool variants,
 * so the button's glyph does not change shape between states, only its mark.
 * `GlyphsTest` pins the relationship.
 */
@Composable
fun SeatPlainLeftGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = SEAT_PLAIN_LEFT, tint = tint, size = size, modifier = modifier)

/** The passenger's seat with no mark. The outline subpath of [SEAT_COOL_RIGHT]; see [SeatPlainLeftGlyph]. */
@Composable
fun SeatPlainRightGlyph(tint: Color, size: Dp, modifier: Modifier = Modifier) =
    VectorGlyph(pathData = SEAT_PLAIN_RIGHT, tint = tint, size = size, modifier = modifier)
```

Change `private const val SEAT_COOL_LEFT` and `private const val SEAT_COOL_RIGHT` to `internal const val`. Then add, directly above `SEAT_COOL_LEFT`:

```kotlin
/** The seat outline alone: [SEAT_COOL_LEFT] up to and including its first `z`. */
internal const val SEAT_PLAIN_LEFT =
    "M17.025 21H6L3.225 10.2q-.1-.375-.162-.762T3 8.65q0-.7.163-1.375" +
        "t.487-1.3q.225-.45.638-.712T5.2 5q.575 0 1 .425t.425 1q0 .275-.1" +
        ".525t-.3.45q-.475.5-.562 1.163T5.85 9.85l.5 1.05q.725 1.575 1.18" +
        "8 3.25T8 17.575v1.1q.425-.275.9-.475t1-.2H15q.85 0 1.438.588T17." +
        "025 20z"

/** The seat outline alone: [SEAT_COOL_RIGHT] up to and including its first `z`. */
internal const val SEAT_PLAIN_RIGHT =
    "M6.975 21v-1q0-.825.588-1.412T9 18h5.075q.525 0 1.013.2t.912.475" +
        "v-1.1q0-1.75.462-3.425t1.188-3.25l.5-1.05q.275-.625.188-1.287T17" +
        ".775 7.4q-.2-.2-.3-.45t-.1-.525q0-.575.413-1T18.775 5q.5 0 .925." +
        "263t.65.712q.325.625.488 1.3T21 8.65q0 .4-.05.788t-.175.762L18 2" +
        "1z"
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.GlyphsTest'`
Expected: BUILD SUCCESSFUL, 2 tests, 0 failures. If a prefix assertion fails, the constant was mis-split at a `+` boundary; compare character by character against `SEAT_COOL_*` in the same file.

- [ ] **Step 5: Extract `SeatPips` to its own file**

Create `ui/src/main/kotlin/com/wk2/climate/ui/SeatPips.kt`:

```kotlin
package com.wk2.climate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.wk2.climate.design.Palette

/**
 * The two-pip level indicator, shared by screen 1d's seat tiles and screen
 * 2a's seat buttons.
 *
 * Two pips carry three states: none lit is OFF, one is LOW, both are HIGH.
 * That is not a compromise -- the vehicle's own cycle is `0 -> 3 -> 1 -> 0`
 * and never visits 2, so every tap changes what the driver sees. Takes the
 * lit count rather than a `SeatLevel` so the bar button, which has already
 * decided between heat and cool, can pass whichever level it chose.
 *
 * The caller sizes it: the panel's tiles let each pip take half the row
 * ([pipWidth] null, so `weight(1f)`) at 10dp tall with an 8dp gap; the bar's
 * 128dp button fixes them at 14 x 6 with a 4dp gap. Extracted so the two
 * share one definition rather than mirroring a copy.
 */
@Composable
fun SeatPips(
    palette: Palette,
    litPips: Int,
    litColor: Color,
    pipHeight: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    pipWidth: Dp? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
            Box(
                Modifier
                    .then(if (pipWidth != null) Modifier.width(pipWidth) else Modifier.weight(1f))
                    .height(pipHeight)
                    .background(
                        if (index < litPips) litColor else palette.ink.copy(alpha = 0.13f),
                        RoundedCornerShape(pipHeight / 2),
                    ),
            )
            if (index == 0) Spacer(Modifier.width(gap))
        }
    }
}
```

- [ ] **Step 6: Point `PanelComfort` at it**

In `PanelComfort.kt`:
- Add `import com.wk2.climate.ui.SeatPips`.
- Replace both `SeatPips(palette, level, litColor, Modifier.weight(1f))` calls inside `ComfortTile` with:

```kotlin
                // 8dp between the two pips, per the handoff mockup's
                // `display:flex;gap:8px` pip group (System Navigation.dc.html
                // lines 438/442/446/450). Not the fan meter's `gap: 6px`
                // (README:180) -- that is a different control. Slimmer than
                // the handoff's 14dp: the glyph is the tile's subject and the
                // pips are the qualifier.
                SeatPips(palette, level.litPips, litColor, pipHeight = 10.dp, gap = 8.dp, Modifier.weight(1f))
```
  (The `mirrored` branch and the plain branch each have one call; update both. Put the comment on the first only.)
- Delete the whole private `SeatPips` composable and its KDoc at the bottom of the file (the block starting `/** * The two-pip level indicator.` through its closing brace). Leave `SEAT_GLYPH` and `SeatState` alone.
- Remove any import that only the deleted function used: check `Box` — it is still used by nothing else in the file, so remove `import androidx.compose.foundation.layout.Box`. `RoundedCornerShape`, `height`, `width`, `Spacer`, `background` are all still used elsewhere in the file; keep them.

- [ ] **Step 7: Run the module's tests and compile**

Run: `./gradlew :ui:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all `:ui` tests green (the existing 5 files plus `SeatIndicatorTest` and `GlyphsTest`).

- [ ] **Step 8: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/Glyphs.kt ui/src/main/kotlin/com/wk2/climate/ui/SeatPips.kt ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt ui/src/test/kotlin/com/wk2/climate/ui/GlyphsTest.kt
git commit -m "feat(ui): plain seat glyphs from the cool seat's outline; SeatPips shared with the bar"
```

---

### Task 4: Geometry and type tokens

**Files:**
- Modify: `design/src/main/kotlin/com/wk2/climate/design/Dimens.kt` (the top-row block, lines 30–44; the radii block)
- Modify: `design/src/main/kotlin/com/wk2/climate/design/Type.kt`
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/BarGeometryTest.kt`

**Interfaces:**
- Produces in `Dimens`: `seatButton = 128.dp`, `slotAuto = 256.dp`, `slotClimate = 256.dp`, `seatMenuWidth = 360.dp`, `seatMenuRow = 96.dp`, `seatMenuDriverX`, `seatMenuPassengerX`, `seatMenuIconSize = 28.dp`, `seatMenuIconInset = 27.dp`, `seatMenuLabelX = 74.dp`, `seatMenuStateInset = 24.dp`, `radiusMenu = 16.dp`. `slotAdaptiveFirst`/`slotAdaptiveSecond` **stay until Task 5**, because `BarTopRow` still reads them and the build must stay green.
- Produces in `Type`: `menuLabel`, `seatButtonState`. `slotLabel`, `slotLabelSmall`, `slotState` **stay until Task 5** for the same reason.

- [ ] **Step 1: Rewrite the top-row list in `BarGeometryTest` and add the symmetry test**

Replace the `topRow` property and add one test:

```kotlin
    /** The top row, left to right, as `BarTopRow` lays it out. */
    private val topRow: List<Pair<String, Dp>> = listOf(
        "driver seat" to Dimens.seatButton,
        "AUTO" to Dimens.slotAuto,
        "CLIMATE" to Dimens.slotClimate,
        "passenger seat" to Dimens.seatButton,
    )

    @Test
    fun `AUTO and CLIMATE meet on the centre divider, so the row mirrors the zones beneath it`() {
        assertEquals(Dimens.barCenterColumn / 2, Dimens.seatButton + Dimens.slotAuto)
    }
```

- [ ] **Step 2: Run the geometry test to verify it fails**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.BarGeometryTest'`
Expected: compilation failure, `Unresolved reference: seatButton`.

- [ ] **Step 3: Update `Dimens.kt`**

Replace the comment and the four `slot*` values (from `// The top row, left to right.` through `val slotClimate = 184.dp`) with:

```kotlin
    // The top row, left to right. These four must sum to [barCenterColumn] --
    // asserted in ui's BarGeometryTest, because an overflow here would push
    // CLIMATE off the edge silently rather than failing.
    //
    // Mirror-symmetric about the centre divider, like the zones beneath: a
    // seat button at each end, AUTO and CLIMATE between them. Nothing in this
    // row changes what it holds any more -- the two adaptive cells that used
    // to sit between AUTO and CLIMATE are gone, and every cell can be found
    // by feel.
    val seatButton = 128.dp
    val slotAuto = 256.dp
    val slotAdaptiveFirst = 200.dp     // retired in the next commit
    val slotAdaptiveSecond = 174.dp    // retired in the next commit
    val slotClimate = 256.dp

    // ---- the seat menus, which float above the bar in their own window ----
    // Measured from a screenshot of the design at ~1.25 px/dp, so approximate;
    // where the design file disagrees, the file wins. The anchors are exact:
    // the driver menu is left-aligned to the driver button, which is the bar's
    // left column, and the passenger menu is right-aligned to the passenger
    // button, which is the centre column's right edge.
    val seatMenuWidth = 360.dp
    val seatMenuRow = 96.dp
    val seatMenuDriverX = barSideColumn
    val seatMenuPassengerX = barSideColumn + barCenterColumn - seatMenuWidth
    val seatMenuIconSize = 28.dp
    val seatMenuIconInset = 27.dp     // icon's left edge from the menu's left edge
    val seatMenuLabelX = 74.dp        // label's left edge from the menu's left edge
    val seatMenuStateInset = 24.dp    // state text's right edge from the menu's right edge
```

In the radii block add `val radiusMenu = 16.dp` after `radiusPip`.

- [ ] **Step 4: Update `Type.kt`**

After `barCaret` add:

```kotlin
    /** The seat button's OFF, and its em dash when unreported. IBM Plex Mono 800 11px, ls .14em. */
    val seatButtonState = mono(11.sp, FontWeight.Bold, 1.54.sp)

    // ---- the seat menus ----

    /** SEAT HEAT / SEAT COOL / STEERING WHEEL. Manrope 700 18px, ls .05em. */
    val menuLabel = ui(18.sp, FontWeight.Bold, 0.9.sp)
```

Leave `slotLabel`, `slotLabelSmall`, `slotState` in place for now.

- [ ] **Step 5: Run the geometry test to verify it passes**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.BarGeometryTest'`
Expected: BUILD SUCCESSFUL, 6 tests, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add design/src/main/kotlin/com/wk2/climate/design/Dimens.kt design/src/main/kotlin/com/wk2/climate/design/Type.kt ui/src/test/kotlin/com/wk2/climate/ui/BarGeometryTest.kt
git commit -m "feat(design): geometry for the four-cell top row and the seat menus"
```

---

### Task 5: `SeatButton`, the rewritten top row, and every call site

This is the one task where the row's shape changes, so the bar, the service and the harness all move together and the build stays green. The service and harness get a **placeholder** wiring here (`seatMenuOpen = null`, `onSeatButton = {}`); Task 7 and Task 9 replace it.

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatButton.kt`
- Rewrite: `ui/src/main/kotlin/com/wk2/climate/ui/bar/BarTopRow.kt`
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/bar/ClimateBar.kt`
- Modify: `design/src/main/kotlin/com/wk2/climate/design/Dimens.kt` (remove `slotAdaptiveFirst`, `slotAdaptiveSecond`)
- Modify: `design/src/main/kotlin/com/wk2/climate/design/Type.kt` (remove `slotLabel`, `slotLabelSmall`, `slotState`)
- Modify: `app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt` (the `slot` field, `BarContent`, `SLOT_POLL_MS`)
- Modify: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt` (the `ClimateBar` call only)

**Interfaces:**
- Consumes: `SeatSide` (Task 1), `SeatIndicator` (Task 2), `SeatPlainLeftGlyph`/`SeatPlainRightGlyph`, `SeatPips` (Task 3), `Dimens.seatButton`, `Type.seatButtonState` (Task 4).
- Produces: `@Composable fun SeatButton(palette: Palette, side: SeatSide, indicator: SeatIndicator, wheelOn: Boolean, menuOpen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)`.
- Produces: `@Composable fun BarTopRow(palette, live, autoOn, wheelOn, panelOpen, driver: SeatIndicator, passenger: SeatIndicator, openMenu: SeatSide?, onAuto, onSeatButton: (SeatSide) -> Unit, onClimate, modifier)`.
- Produces: `@Composable fun ClimateBar(state, onCommand, onHome, onBack, panelOpen, onToggleClimate, seatMenuOpen: SeatSide?, onSeatButton: (SeatSide) -> Unit, modifier)` — `band` and `onSlotPressChange` are gone.

- [ ] **Step 1: Write `SeatButton.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.SeatCoolLeftGlyph
import com.wk2.climate.ui.SeatCoolRightGlyph
import com.wk2.climate.ui.SeatHeatLeftGlyph
import com.wk2.climate.ui.SeatHeatRightGlyph
import com.wk2.climate.ui.SeatPips
import com.wk2.climate.ui.SeatPlainLeftGlyph
import com.wk2.climate.ui.SeatPlainRightGlyph
import com.wk2.climate.ui.WheelHeatGlyph
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * A seat button: one of the two 128 x 96 cells at the ends of the bar's top
 * row. It **shows** the seat's state and **opens** that seat's menu; it never
 * dispatches a vehicle command itself.
 *
 * What it shows is decided by [SeatIndicator], not here: a heat or cool
 * variant of the seat glyph in its lit colour with one or two pips beneath,
 * the plain seat with `OFF` when both levels are reported off, and the plain
 * seat with an em dash when they are not reported. The glyph's silhouette is
 * the same in every state (see `SeatPlainLeftGlyph`), and the readout row
 * under it has a fixed height, so nothing moves when the state changes.
 *
 * Driver uses the LEFT glyph variants and passenger the RIGHT, as the panel's
 * comfort tiles do. The driver's button also carries the heated-wheel badge
 * in its top-right corner when the wheel is on -- independent of the seat, so
 * an OFF seat with the wheel on shows the plain seat, `OFF`, and the badge.
 * [wheelOn] is passed already gated on `live` by the caller, like every other
 * wheel flag in the bar.
 *
 * While its menu is up the cell's background is `surfaceRaised`, the same
 * treatment as the pressed state, so the driver can see which side is open.
 */
@Composable
fun SeatButton(
    palette: Palette,
    side: SeatSide,
    indicator: SeatIndicator,
    wheelOn: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    val tint = when (indicator.kind) {
        SeatIndicator.Kind.HEAT -> palette.warm
        SeatIndicator.Kind.COOL -> palette.cool
        SeatIndicator.Kind.OFF -> palette.ink
        SeatIndicator.Kind.UNKNOWN -> palette.inkFaint
    }

    Box(
        modifier
            .width(Dimens.seatButton)
            .fillMaxHeight()
            .background(
                if (menuOpen || pressed.value) palette.surfaceRaised else Color.Transparent,
            )
            .target(interaction, onClick = onClick),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SeatGlyph(side, indicator.kind, tint, SEAT_BUTTON_GLYPH)
            Spacer(Modifier.height(6.dp))
            // Fixed height whether it holds pips or a word, so the glyph
            // above it sits at the same y in every state.
            Box(Modifier.height(READOUT_HEIGHT), contentAlignment = Alignment.Center) {
                when (indicator.kind) {
                    SeatIndicator.Kind.HEAT, SeatIndicator.Kind.COOL -> SeatPips(
                        palette, indicator.litPips, tint,
                        pipHeight = 6.dp, gap = 4.dp, pipWidth = 14.dp,
                    )
                    SeatIndicator.Kind.OFF -> BasicText(
                        text = "OFF",
                        style = Type.seatButtonState.copy(color = palette.inkMuted),
                    )
                    // An em dash, never OFF: one spelling of "we do not know"
                    // across the bar, matching Temp.Unavailable in the zones.
                    SeatIndicator.Kind.UNKNOWN -> BasicText(
                        text = "—",
                        style = Type.seatButtonState.copy(color = palette.inkFaint),
                    )
                }
            }
        }

        if (side == SeatSide.DRIVER && wheelOn) {
            WheelHeatGlyph(
                tint = palette.warm,
                size = WHEEL_BADGE,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun SeatGlyph(side: SeatSide, kind: SeatIndicator.Kind, tint: Color, size: Dp) {
    val driver = side == SeatSide.DRIVER
    when (kind) {
        SeatIndicator.Kind.HEAT ->
            if (driver) SeatHeatLeftGlyph(tint, size) else SeatHeatRightGlyph(tint, size)
        SeatIndicator.Kind.COOL ->
            if (driver) SeatCoolLeftGlyph(tint, size) else SeatCoolRightGlyph(tint, size)
        SeatIndicator.Kind.OFF, SeatIndicator.Kind.UNKNOWN ->
            if (driver) SeatPlainLeftGlyph(tint, size) else SeatPlainRightGlyph(tint, size)
    }
}

/** Spec §3: approx 36dp, centred, upper part of the cell. */
private val SEAT_BUTTON_GLYPH = 36.dp

/** Spec §3: approx 32dp, top-right, driver only. */
private val WHEEL_BADGE = 32.dp

/** Tall enough for the 11sp state word; the 6dp pips centre inside it. */
private val READOUT_HEIGHT = 14.dp
```

- [ ] **Step 2: Rewrite `BarTopRow.kt`**

Replace the whole file:

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row: driver seat button, AUTO, CLIMATE,
 * passenger seat button.
 *
 * Widths are 128 / 256 / 256 / 128 and are **fixed** -- see [Dimens.seatButton]
 * and its neighbours, which are the single source of truth for them. Every
 * cell lives at a constant coordinate and never changes what it holds, so it
 * can be found by feel; the row is mirror-symmetric about the centre divider,
 * matching the zones beneath it. The two adaptive cells that used to sit
 * between AUTO and CLIMATE, and the outside-temperature machine that swapped
 * them, are gone -- what they surfaced now lives in each seat's menu.
 *
 * [live] is false until the vehicle has reported a climate signal. Every fill
 * in this row is a positive claim -- an amber AUTO says AUTO is engaged -- so
 * while [live] is false nothing here is filled and the ink is muted. The
 * controls stay tappable: a press is how the driver forces the vehicle to
 * report in the first place, and it was the owner's own workaround. The seat
 * buttons need no gate of their own: [SeatIndicator] is UNKNOWN by
 * construction while the seat levels are unreported.
 */
@Composable
fun BarTopRow(
    palette: Palette,
    live: Boolean,
    autoOn: Boolean,
    wheelOn: Boolean,
    panelOpen: Boolean,
    driver: SeatIndicator,
    passenger: SeatIndicator,
    openMenu: SeatSide?,
    onAuto: () -> Unit,
    onSeatButton: (SeatSide) -> Unit,
    onClimate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barTopRow),
    ) {
        // ---- driver seat, 128dp. Leftmost. Carries the wheel badge. ----
        SeatButton(
            palette = palette,
            side = SeatSide.DRIVER,
            indicator = driver,
            wheelOn = live && wheelOn,
            menuOpen = openMenu == SeatSide.DRIVER,
            onClick = { onSeatButton(SeatSide.DRIVER) },
        )

        // ---- AUTO, 256dp ----
        val (autoInteraction, autoPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotAuto)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = true)
                // `autoOn` is already false while indeterminate -- flag() reads
                // an absent signal as false -- but that is the bug, not the
                // guard. Gating the fill on `live` makes the amber a claim we
                // only ever paint from data we actually have.
                .background(if (live && autoOn) palette.accent else Color.Transparent)
                .then(
                    if (autoPressed.value) {
                        Modifier.pressedTint(live && autoOn, palette)
                    } else {
                        Modifier
                    },
                )
                .target(autoInteraction, onClick = onAuto),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "AUTO",
                style = Type.barAuto.copy(
                    color = when {
                        !live -> palette.inkMuted
                        autoOn -> palette.accentInk
                        else -> palette.ink
                    },
                ),
            )
        }

        // ---- CLIMATE, 256dp ----
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
                // Points the way the tap will move the panel: up to open,
                // down to dismiss. The same control does both.
                text = if (panelOpen) "▼" else "▲",
                style = Type.barCaret.copy(color = palette.ink.copy(alpha = 0.55f)),
            )
        }

        // ---- passenger seat, 128dp. Rightmost. No wheel badge. ----
        SeatButton(
            palette = palette,
            side = SeatSide.PASSENGER,
            indicator = passenger,
            wheelOn = false,
            menuOpen = openMenu == SeatSide.PASSENGER,
            onClick = { onSeatButton(SeatSide.PASSENGER) },
            modifier = Modifier.sideDivider(palette.divider, start = true),
        )
    }
}
```

- [ ] **Step 3: Update `ClimateBar.kt`**

Replace the imports `SlotBand`, `SlotCell`, `SlotContent` with `import com.wk2.climate.bus.SeatSide`. Replace the signature and the `BarTopRow` call:

```kotlin
/**
 * Screen 2a: the resting bar. 1080 x 227, and it never grows — 227dp is the
 * framework's `navigation_bar_height` and claiming more would cover app
 * content. Screen 1d opens *over* app content instead.
 *
 * Seven controls live here permanently: HOME, BACK, the two seat buttons,
 * AUTO, CLIMATE, and the two zones' steppers, plus volume. Everything else is
 * one tap away on 1d or in a seat's menu. Rendered entirely from [state];
 * every tap dispatches a [Command] and mutates nothing locally.
 *
 * [seatMenuOpen] is which seat's menu is up, so its button can paint itself
 * open; the menu itself is a separate window the service owns, and
 * [onSeatButton] is how a tap reaches it. Nothing about the menu is vehicle
 * state, so the bar only ever reads it.
 *
 * Until the vehicle has reported its first climate signal the climate half of
 * the bar renders *indeterminate* — see [ClimateState.hasClimateData]. Nothing
 * is filled, nothing that would read OFF says OFF, and the ink is muted. The
 * navigation column and the volume column do not depend on climate and stay
 * fully live: HOME and BACK are the only route a third-party app has to those
 * keys, and volume arrives on a different module entirely.
 */
@Composable
fun ClimateBar(
    state: ClimateState,
    onCommand: (Command) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    panelOpen: Boolean,
    onToggleClimate: () -> Unit,
    seatMenuOpen: SeatSide?,
    onSeatButton: (SeatSide) -> Unit,
    modifier: Modifier = Modifier,
) {
```

and

```kotlin
            BarTopRow(
                palette = palette,
                live = live,
                autoOn = state.autoOn,
                wheelOn = state.wheelHeatOn,
                panelOpen = panelOpen,
                driver = SeatIndicator.of(state.seatHeatL, state.seatVentL),
                passenger = SeatIndicator.of(state.seatHeatR, state.seatVentR),
                openMenu = seatMenuOpen,
                onAuto = { onCommand(Command.AUTO) },
                onSeatButton = onSeatButton,
                onClimate = onToggleClimate,
            )
```

- [ ] **Step 4: Retire the orphaned tokens**

In `Dimens.kt` delete the two lines `val slotAdaptiveFirst = 200.dp` and `val slotAdaptiveSecond = 174.dp` (with their `// retired` comments). In `Type.kt` delete `slotLabel`, `slotLabelSmall` and `slotState` with their KDoc lines.

- [ ] **Step 5: Drop the slot from `ClimateBarService`**

- Delete `import com.wk2.climate.bus.AdaptiveSlot`, `import android.os.SystemClock`, `import kotlinx.coroutines.delay`, `import androidx.compose.runtime.setValue`.
- Delete the field `private val slot = AdaptiveSlot()`.
- In `BarContent`, delete everything from the comment `// The adaptive pair is re-evaluated on a timer` through the closing brace of the `LaunchedEffect(Unit) { while (true) { ... } }` block.
- In the `ClimateBar(...)` call, delete `band = band,` and the whole `onSlotPressChange = { cell, down -> ... },` argument including its comment, and add after `onToggleClimate = ...`:

```kotlin
            // Wired in the seat-menu commit; the bar paints no menu open until then.
            seatMenuOpen = null,
            onSeatButton = {},
```

- In the companion, delete the KDoc and `const val SLOT_POLL_MS = 5_000L`.
- Do **not** touch `outsideF()` or its KDoc yet (Task 8).

- [ ] **Step 6: Drop the slot from the harness `ClimateBar` call**

In `HarnessActivity.kt`, in the `ClimateBar(` call inside `if (showBar)`, delete `band = band,` and the `onSlotPressChange = { cell, down -> ... },` argument, and add `seatMenuOpen = null, onSeatButton = {},`. Leave the rest of the harness (the `slot` field, `scrub`, the band readouts) for Task 8 — it still compiles because `AdaptiveSlot` still exists.

- [ ] **Step 7: Build every module and run the suite**

Run: `./gradlew test :app:assembleDebug :harness:assembleDebug`
Expected: BUILD SUCCESSFUL. Test count is the baseline 153 plus 12 (`SeatMenuLatchTest`) + 8 (`SeatIndicatorTest`) + 2 (`GlyphsTest`) + 1 (`BarGeometryTest` symmetry) = **176**, 0 failures.

- [ ] **Step 8: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatButton.kt ui/src/main/kotlin/com/wk2/climate/ui/bar/BarTopRow.kt ui/src/main/kotlin/com/wk2/climate/ui/bar/ClimateBar.kt design/src/main/kotlin/com/wk2/climate/design/Dimens.kt design/src/main/kotlin/com/wk2/climate/design/Type.kt app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt
git commit -m "feat(ui): seat buttons replace the adaptive cells in the bar's top row"
```

---

### Task 6: `SeatMenu` content and its geometry helpers

**Files:**
- Create: `ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatMenu.kt`
- Test: `ui/src/test/kotlin/com/wk2/climate/ui/BarGeometryTest.kt` (add four tests)

**Interfaces:**
- Consumes: `Dimens.seatMenu*`, `Dimens.radiusMenu`, `Type.menuLabel`, `Type.comfortState`, `bottomDivider` (`bar/BarDividers.kt`), `pressedTint`, the seat and wheel glyphs.
- Produces: `fun seatMenuRows(side: SeatSide): Int`, `fun seatMenuHeight(side: SeatSide): Dp`, `fun seatMenuX(side: SeatSide): Dp` — plain functions in `com.wk2.climate.ui.bar`, used by the service to size and place the window.
- Produces: `@Composable fun SeatMenu(palette: Palette, side: SeatSide, state: ClimateState, onCommand: (Command) -> Unit, modifier: Modifier = Modifier)`.

- [ ] **Step 1: Add the failing geometry tests**

Append to `BarGeometryTest`, adding `import com.wk2.climate.bus.SeatSide`, `import com.wk2.climate.ui.bar.seatMenuHeight`, `import com.wk2.climate.ui.bar.seatMenuRows`, `import com.wk2.climate.ui.bar.seatMenuX`:

```kotlin
    @Test
    fun `each seat menu is a whole number of rows and every row clears the touch floor`() {
        assertTrue(Dimens.seatMenuRow >= Dimens.minTarget)
        for (side in SeatSide.entries) {
            assertEquals(Dimens.seatMenuRow * seatMenuRows(side), seatMenuHeight(side))
        }
        assertEquals(3, seatMenuRows(SeatSide.DRIVER))
        assertEquals(2, seatMenuRows(SeatSide.PASSENGER))
    }

    @Test
    fun `the driver menu spans 156 to 516, left-aligned to the driver button`() {
        assertEquals(156.dp, seatMenuX(SeatSide.DRIVER))
        assertEquals(516.dp, seatMenuX(SeatSide.DRIVER) + Dimens.seatMenuWidth)
    }

    @Test
    fun `the passenger menu spans 564 to 924, right-aligned to the passenger button`() {
        assertEquals(564.dp, seatMenuX(SeatSide.PASSENGER))
        assertEquals(924.dp, seatMenuX(SeatSide.PASSENGER) + Dimens.seatMenuWidth)
    }

    @Test
    fun `both menus sit inside the centre column and mirror each other about the bar's centre`() {
        val columnStart = Dimens.barSideColumn
        val columnEnd = Dimens.barSideColumn + Dimens.barCenterColumn
        for (side in SeatSide.entries) {
            assertTrue("$side menu starts left of the centre column", seatMenuX(side) >= columnStart)
            assertTrue("$side menu overruns the centre column", seatMenuX(side) + Dimens.seatMenuWidth <= columnEnd)
        }
        assertEquals(
            seatMenuX(SeatSide.DRIVER),
            Dimens.barWidth - (seatMenuX(SeatSide.PASSENGER) + Dimens.seatMenuWidth),
        )
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :ui:testDebugUnitTest --tests 'com.wk2.climate.ui.BarGeometryTest'`
Expected: compilation failure, `Unresolved reference: seatMenuRows`.

- [ ] **Step 3: Write `SeatMenu.kt`**

```kotlin
package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.SeatCoolLeftGlyph
import com.wk2.climate.ui.SeatCoolRightGlyph
import com.wk2.climate.ui.SeatHeatLeftGlyph
import com.wk2.climate.ui.SeatHeatRightGlyph
import com.wk2.climate.ui.WheelHeatGlyph
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/** Rows in a side's menu: the driver's has the heated wheel, the passenger's does not. */
fun seatMenuRows(side: SeatSide): Int = when (side) {
    SeatSide.DRIVER -> 3
    SeatSide.PASSENGER -> 2
}

/** The menu's height: whole rows, each on the 96dp floor. */
fun seatMenuHeight(side: SeatSide): Dp = Dimens.seatMenuRow * seatMenuRows(side)

/** The menu's left edge in bar coordinates. Driver left-aligns to its button; passenger right-aligns to its own. */
fun seatMenuX(side: SeatSide): Dp = when (side) {
    SeatSide.DRIVER -> Dimens.seatMenuDriverX
    SeatSide.PASSENGER -> Dimens.seatMenuPassengerX
}

/**
 * One seat's menu: seat heat, seat cool, and -- for the driver -- the heated
 * wheel. 360dp wide, 96dp rows, flush on top of the bar.
 *
 * Each tap **cycles**, exactly as the same control does on screen 1d: heat
 * goes `OFF -> HIGH -> LOW -> OFF` per tap, cool likewise, and the vehicle's
 * own mutual exclusion clears the other. There is no direct level selection
 * and no multi-command sequencing, by the owner's decision. Like everywhere
 * else this renders reported state only and never mutates on tap; the menu
 * stays open across row taps because reaching LOW takes two of them.
 *
 * Row rules follow the 1d comfort tiles: icon in the lit colour when on,
 * `ink` when off, `inkFaint` when unknown; state text in the lit colour when
 * on, muted when off, an em dash when unknown. The seat rows read "unknown"
 * off the level itself; the wheel row needs [ClimateState.hasClimateData]
 * because its flag collapses absent into false.
 *
 * The window this draws in is translucent over app content, so the menu
 * paints [Palette.surface] under its `surfaceRaised` wash: the wash alone is a
 * 5% overlay that would vanish over whatever app is behind it. The corners
 * are clipped so a row's pressed wash respects them.
 */
@Composable
fun SeatMenu(
    palette: Palette,
    side: SeatSide,
    state: ClimateState,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    val driver = side == SeatSide.DRIVER
    val live = state.hasClimateData
    val heat = if (driver) state.seatHeatL else state.seatHeatR
    val vent = if (driver) state.seatVentL else state.seatVentR
    val shape = RoundedCornerShape(Dimens.radiusMenu)

    Column(
        modifier
            .width(Dimens.seatMenuWidth)
            .height(seatMenuHeight(side))
            .background(palette.surface, shape)
            .background(palette.surfaceRaised, shape)
            .clip(shape),
    ) {
        MenuRow(
            palette = palette,
            icon = { tint ->
                if (driver) SeatHeatLeftGlyph(tint, Dimens.seatMenuIconSize)
                else SeatHeatRightGlyph(tint, Dimens.seatMenuIconSize)
            },
            label = "SEAT HEAT",
            stateText = levelText(heat),
            on = heat.isOn,
            known = heat != SeatLevel.UNAVAILABLE,
            litColor = palette.warm,
            divider = true,
            onClick = { onCommand(if (driver) Command.SEAT_HEAT_L else Command.SEAT_HEAT_R) },
        )
        MenuRow(
            palette = palette,
            icon = { tint ->
                if (driver) SeatCoolLeftGlyph(tint, Dimens.seatMenuIconSize)
                else SeatCoolRightGlyph(tint, Dimens.seatMenuIconSize)
            },
            label = "SEAT COOL",
            stateText = levelText(vent),
            on = vent.isOn,
            known = vent != SeatLevel.UNAVAILABLE,
            litColor = palette.cool,
            divider = driver,
            onClick = { onCommand(if (driver) Command.SEAT_VENT_L else Command.SEAT_VENT_R) },
        )
        if (driver) {
            MenuRow(
                palette = palette,
                icon = { tint -> WheelHeatGlyph(tint, Dimens.seatMenuIconSize) },
                label = "STEERING WHEEL",
                stateText = when {
                    !live -> "—"
                    state.wheelHeatOn -> "ON"
                    else -> "OFF"
                },
                on = live && state.wheelHeatOn,
                known = live,
                litColor = palette.warm,
                divider = false,
                onClick = { onCommand(Command.WHEEL_HEAT) },
            )
        }
    }
}

/**
 * One 96dp row: icon at 27dp, label at 74dp, state text right-aligned 24dp
 * from the edge. Pressed wash on touch-down, no ripple.
 */
@Composable
private fun MenuRow(
    palette: Palette,
    icon: @Composable (Color) -> Unit,
    label: String,
    stateText: String,
    on: Boolean,
    known: Boolean,
    litColor: Color,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.seatMenuRow)
            .then(if (divider) Modifier.bottomDivider(palette.divider) else Modifier)
            .then(if (pressed.value) Modifier.pressedTint(filled = false, palette = palette) else Modifier)
            .target(interaction, onClick = onClick)
            .padding(start = Dimens.seatMenuIconInset, end = Dimens.seatMenuStateInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(
            when {
                !known -> palette.inkFaint
                on -> litColor
                else -> palette.ink
            },
        )
        Spacer(Modifier.width(Dimens.seatMenuLabelX - Dimens.seatMenuIconInset - Dimens.seatMenuIconSize))
        BasicText(
            text = label,
            style = Type.menuLabel.copy(color = if (known) palette.ink else palette.inkMuted),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = stateText,
            style = Type.comfortState.copy(color = if (on) litColor else palette.inkFaint),
        )
    }
}

/** OFF / LOW / HIGH, and an em dash for UNAVAILABLE -- never `OFF` for a level we do not have. */
private fun levelText(level: SeatLevel): String = when (level) {
    SeatLevel.OFF -> "OFF"
    SeatLevel.LOW -> "LOW"
    SeatLevel.HIGH -> "HIGH"
    SeatLevel.UNAVAILABLE -> "—"
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :ui:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; `BarGeometryTest` now 10 tests, all `:ui` tests green.

- [ ] **Step 5: Commit**

```bash
git add ui/src/main/kotlin/com/wk2/climate/ui/bar/SeatMenu.kt ui/src/test/kotlin/com/wk2/climate/ui/BarGeometryTest.kt
git commit -m "feat(ui): the seat menu, and where each side's menu sits on the bar"
```

---

### Task 7: The menu window — `ComposeOverlayHost` outside-touch hook and the service wiring

**Files:**
- Modify: `app/src/main/kotlin/com/wk2/climate/app/ComposeOverlayHost.kt` (`show()`)
- Modify: `app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt`

**Interfaces:**
- Consumes: `SeatMenuLatch`, `SeatSide` (Task 1); `SeatMenu`, `seatMenuHeight`, `seatMenuX` (Task 6); `ClimateBar`'s `seatMenuOpen` / `onSeatButton` (Task 5).
- Produces: `ComposeOverlayHost.show(params: WindowManager.LayoutParams, onOutsideTouch: (() -> Unit)? = null, content: @Composable () -> Unit)`. Existing callers pass a trailing lambda and are unchanged.

There is no unit test for this task: it is Android window wiring, and the logic it wires is already under test in Task 1. The verification is a clean build plus the vehicle checklist in the spec's §8.

- [ ] **Step 1: Add the outside-touch hook to `ComposeOverlayHost`**

Add `import android.annotation.SuppressLint` and `import android.view.MotionEvent`. Replace `show()`:

```kotlin
    /**
     * [onOutsideTouch] fires on `MotionEvent.ACTION_OUTSIDE`, which a window
     * only receives with `FLAG_WATCH_OUTSIDE_TOUCH` set in [params]. Only the
     * touch's DOWN is delivered, and only as this one event: the rest of the
     * gesture goes to whatever window it landed on. That is how the seat menu
     * dismisses on a touch anywhere else while still letting that touch do
     * what it landed on.
     *
     * Wired as an `OnTouchListener` on the `ComposeView` rather than inside
     * Compose, because an outside event has no position inside this window
     * for Compose's pointer input to route it to. The listener returns false
     * for every other action so Compose handles the window's own touches
     * exactly as before. `ClickableViewAccessibility` is suppressed on that
     * basis: nothing here consumes a click, so there is no `performClick` to
     * call.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun show(
        params: WindowManager.LayoutParams,
        onOutsideTouch: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        if (view != null) return
        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ComposeOverlayHost)
            setViewTreeViewModelStoreOwner(this@ComposeOverlayHost)
            setViewTreeSavedStateRegistryOwner(this@ComposeOverlayHost)
            setContent { content() }
            if (onOutsideTouch != null) {
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        onOutsideTouch()
                        true
                    } else {
                        false
                    }
                }
            }
        }
        // Must be RESUMED before attach: ComposeView will not compose while the
        // owner it found in the view tree is below STARTED.
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        windowManager.addView(composeView, params)
        view = composeView
    }
```

Update the class KDoc's last paragraph: "the climate panel and the seat menu each create and destroy one on demand".

- [ ] **Step 2: Add the menu state to `ClimateBarService`**

Add imports: `import android.os.SystemClock`, `import com.wk2.climate.bus.SeatMenuLatch`, `import com.wk2.climate.bus.SeatSide`, `import com.wk2.climate.design.Palette`, `import com.wk2.climate.ui.bar.SeatMenu`, `import com.wk2.climate.ui.bar.seatMenuHeight`, `import com.wk2.climate.ui.bar.seatMenuX`.

After the `panelOpen` field add:

```kotlin
    /**
     * The open seat menu's window, which exists only while a menu is open.
     *
     * Same lifetime story as [panelHost]: one host owns one window, created in
     * [applySeatMenu] and discarded there too, and null means "no menu window
     * exists". Switching sides destroys one window and creates another rather
     * than moving the same one.
     */
    private var menuHost: ComposeOverlayHost? = null

    /**
     * Which menu is open and why a tap does what it does -- in particular the
     * re-tap race, where the bar's click on the owning seat button arrives
     * *after* the menu window has already seen the same touch as
     * `ACTION_OUTSIDE`. See [SeatMenuLatch]; this class only feeds it events
     * and applies what it returns.
     *
     * `SystemClock.elapsedRealtime()` for the latch's clock, never the wall
     * clock: this head unit sets its clock from GPS and the network while the
     * bar is live, and a forward sync inside the 400ms window would turn a
     * swallowed re-tap into a reopen.
     */
    private val seatMenuLatch = SeatMenuLatch()

    /**
     * Which seat's menu is open, for the bar to paint that button as open.
     *
     * Compose state rather than a read of `menuHost`, which is a plain field
     * the bar cannot observe -- the same reason [panelOpen] exists. Set and
     * cleared only in [applySeatMenu], together with the window.
     */
    private val seatMenu = mutableStateOf<SeatSide?>(null)
```

- [ ] **Step 3: Add the menu methods**

After `closePanel()` add:

```kotlin
    /** A seat button click, from the bar. */
    private fun onSeatButton(side: SeatSide) {
        applySeatMenu(seatMenuLatch.onButtonTap(side, SystemClock.elapsedRealtime()))
    }

    /** `ACTION_OUTSIDE` on the open menu's window. */
    private fun onMenuOutsideTouch() {
        applySeatMenu(seatMenuLatch.onOutsideTouch(SystemClock.elapsedRealtime()))
    }

    /**
     * Every non-touch route to a closed menu: BACK, HOME, CLIMATE, a dead bus
     * and teardown. Safe with no menu open.
     */
    private fun closeSeatMenu() {
        applySeatMenu(seatMenuLatch.close())
    }

    /**
     * Makes the menu window match what the latch decided: none, or one side's.
     *
     * The single place a menu window is ever added or removed. A different
     * side means the old window goes and a new one is created -- there is
     * never a second window over the first, and never one being moved.
     *
     * The bus check is the same one [openPanel] makes, for the same reason:
     * **never create a window that cannot drive the vehicle.** With the bus
     * gone our bar is already gone with it, so the factory bar is exposed and
     * the tap that got here is dropped rather than answered with a menu whose
     * every command would be lost. The latch is told, so it does not believe a
     * menu is open that was never shown.
     *
     * `destroy()` rather than `hide()`, as for the panel: the host is never
     * reused, so its lifecycle must reach DESTROYED and its ViewModel store
     * must be cleared, or every open leaks one. No animation on the way in or
     * out -- the spec shows none, and nothing in this project animates a value.
     */
    private fun applySeatMenu(side: SeatSide?) {
        if (side != null && side == seatMenu.value) return
        menuHost?.destroy()
        menuHost = null
        seatMenu.value = null
        if (side == null) return
        if (scope == null || !::bus.isInitialized || !bus.connected.value) {
            Log.w(TAG, "seat menu ignored — no vehicle bus, so it could drive nothing")
            seatMenuLatch.close()
            return
        }
        val host = ComposeOverlayHost(this)
        menuHost = host
        try {
            host.show(seatMenuWindowParams(side), onOutsideTouch = ::onMenuOutsideTouch) {
                SeatMenuContent(side)
            }
            seatMenu.value = side
        } catch (t: Throwable) {
            Log.e(TAG, "could not add the seat menu window; the bar remains usable", t)
            host.destroy()
            menuHost = null
            seatMenuLatch.close()
        }
    }

    @Composable
    private fun SeatMenuContent(side: SeatSide) {
        val state by bus.state.collectAsState()
        SeatMenu(
            palette = Palette.forNight(state.isNight),
            side = side,
            state = state,
            onCommand = { bus.send(it) },
        )
    }

    /**
     * The menu sits flush on top of the bar, inside the centre column, in
     * **display** coordinates on the same basis as the bar -- so it is flush
     * on any hardware, whatever the framework's inset says. Its top is the
     * bar's top less the menu's own height.
     *
     * `FLAG_NOT_TOUCH_MODAL` so a touch outside it still reaches whatever it
     * landed on -- AUTO toggles, the app underneath gets its tap -- and
     * `FLAG_WATCH_OUTSIDE_TOUCH` so that same touch also tells us to close.
     * Otherwise the bar's own flags: a trusted overlay the system does not
     * hide during permission dialogs, not focusable, laid out in screen
     * coordinates with no limits.
     */
    private fun seatMenuWindowParams(side: SeatSide): WindowManager.LayoutParams {
        val density = resources.displayMetrics.density
        val width = (Dimens.seatMenuWidth.value * density).roundToInt()
        val height = (seatMenuHeight(side).value * density).roundToInt()
        val left = (seatMenuX(side).value * density).roundToInt()
        val top = displayHeightPx() - designBarHeightPx() - height
        Log.i(TAG, "seat menu window ($side): x=$left y=$top ${width}x${height}px")
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = left
            y = top
        }
    }
```

- [ ] **Step 4: Wire the bar, the nav keys, CLIMATE, the dead bus, BACK and teardown**

In `BarContent`'s `ClimateBar(...)` call:
- replace the placeholder `seatMenuOpen = null, onSeatButton = {},` (and its comment) with `seatMenuOpen = seatMenu.value, onSeatButton = ::onSeatButton,`
- `onHome`: add `closeSeatMenu()` as the first line — a menu left over the launcher is the same bug as the panel left over it.
- `onToggleClimate = { closeSeatMenu(); if (panelOpen.value) requestPanelClose() else openPanel() }` — the CLIMATE tap already closed the menu as an outside touch; this makes the spec's rule hold even if it did not.
- `onBack` is unchanged: the bar's BACK is an outside touch, which already closes the menu, and then does what BACK does.

In `hidePanelAndBar()` add `closeSeatMenu()` as the first line, and add to its KDoc: "The menu goes first: it is the topmost window and the one with the least reason to exist without a bus."

In `teardown()` add `closeSeatMenu()` immediately before `closePanel()`.

In `onKeyEvent`, before the existing panel check:

```kotlin
        if (event?.keyCode == KeyEvent.KEYCODE_BACK && menuHost != null) {
            if (event.action == KeyEvent.ACTION_UP) closeSeatMenu()
            return true
        }
```

and add to its KDoc: "A menu is closed before a panel would be: BACK peels the topmost thing."

- [ ] **Step 5: Build and run the suite**

Run: `./gradlew test :app:assembleDebug`
Expected: BUILD SUCCESSFUL, 180 tests (176 + the 4 menu geometry tests from Task 6), 0 failures. Also run `./gradlew :app:lintDebug` and confirm no new errors (warnings are acceptable; `ClickableViewAccessibility` must not appear, since it is suppressed).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/wk2/climate/app/ComposeOverlayHost.kt app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt
git commit -m "feat(app): the seat menu window, closed by outside touch, BACK, HOME, CLIMATE and a dead bus"
```

---

### Task 8: Delete `AdaptiveSlot` and clean up what referred to it

**Files:**
- Delete: `bus/src/main/kotlin/com/wk2/climate/bus/AdaptiveSlot.kt`, `bus/src/test/kotlin/com/wk2/climate/bus/AdaptiveSlotTest.kt`
- Modify: `app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt` (`outsideF` KDoc only)
- Modify: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt`
- Modify: `ui/src/main/kotlin/com/wk2/climate/ui/Glyphs.kt` (`FrontDefrostGlyph` KDoc), `ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt` (KDoc)

- [ ] **Step 1: Delete the slot**

```bash
git rm bus/src/main/kotlin/com/wk2/climate/bus/AdaptiveSlot.kt bus/src/test/kotlin/com/wk2/climate/bus/AdaptiveSlotTest.kt
```

- [ ] **Step 2: Fix the harness so it compiles**

In `HarnessActivity.kt`:
- Delete `import com.wk2.climate.bus.AdaptiveSlot`, the field `private val slot = AdaptiveSlot()`, and change `setContent { Harness(bus, slot) }` to `setContent { Harness(bus) }` and the signature to `private fun Harness(bus: FakeVehicleBus)`.
- Replace the block from `// Drive the adaptive slot from a scrubbable temperature` through the end of `fun scrub(...)` with:

```kotlin
    // Screen 1d's header shows the outside temperature; OUT below cycles it
    // through the values that used to matter, plus null for "not decoded".
    var outsideF by remember { mutableStateOf<Int?>(null) }
```

- In the readouts `LazyColumn`, delete the three `item { ... }` lines under `--- adaptive slot ---` and the header item itself, and add `item { Mono("seatHeatR   ${state.seatHeatR}", palette.ink) }` after `seatHeatL` and `item { Mono("seatVentR   ${state.seatVentR}", palette.ink) }` after `seatVentL`.
- Replace the last `Row` (COLD / MILD / HOT / NULL) with:

```kotlin
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("OUT ${outsideF ?: "—"}", palette) {
                outsideF = when (outsideF) { null -> 20; 20 -> 95; else -> null }
            }
            Key("SEAT R", palette) { bus.send(Command.SEAT_HEAT_R) }
            Key("VENT L", palette) { bus.send(Command.SEAT_VENT_L) }
            Key("WHEEL", palette) { bus.send(Command.WHEEL_HEAT) }
        }
```

- [ ] **Step 3: Rewrite the `outsideF` KDoc in the service**

Replace the two paragraphs that begin "Only trusted when the vehicle is reporting Fahrenheit. [AdaptiveSlot]'s thresholds..." and "An invalid, out-of-range or non-Fahrenheit reading returns null, and [AdaptiveSlot] pins..." with:

```kotlin
     * Only trusted when the vehicle is reporting Fahrenheit. Whether
     * `U_TEMP_OUT` is unit-scaled the way `TEMP_LEFT`/`TEMP_RIGHT` are is
     * **unverified** — the vehicle observation above was taken with the unit
     * set to Fahrenheit, so it does not distinguish the two. If it is scaled,
     * a 30 C day would display as 30°F. So this fails safe rather than
     * guessing.
     *
     * An invalid, out-of-range or non-Fahrenheit reading returns null, which
     * screen 1d's header renders as an em dash. This used to also drive the
     * bar's adaptive cells; those are gone, and the header is its only reader.
```

- [ ] **Step 4: Two KDoc mentions in `:ui`**

In `Glyphs.kt`, `FrontDefrostGlyph`'s KDoc: change "it stays crisp at the two sizes it is used at -- 34dp in the bar's adaptive cell and 44dp on the panel's mode tile." to "it stays crisp at any size; today that is 44dp on the panel's mode tile."

In `PanelComfort.kt`, the class-level KDoc: change "-- one honest source, no second gate -- matching `BarTopRow`'s SeatSlot." to "-- one honest source, no second gate -- as the bar's `SeatIndicator` does."

- [ ] **Step 5: Confirm nothing else refers to the slot**

Run: `grep -rn "AdaptiveSlot\|SlotBand\|SlotCell\|SlotContent\|slotAdaptive\|onSlotPressChange\|SLOT_POLL" --include=*.kt --include=*.kts .` (excluding `build/` and `.superpowers/`)
Expected: no matches.

- [ ] **Step 6: Build everything and run the suite**

Run: `./gradlew test :app:assembleDebug :harness:assembleDebug`
Expected: BUILD SUCCESSFUL, **155 tests** (180 − 25 from `AdaptiveSlotTest`), 0 failures.

- [ ] **Step 7: Commit**

```bash
git add -A bus/src app/src/main/kotlin/com/wk2/climate/app/ClimateBarService.kt harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt ui/src/main/kotlin/com/wk2/climate/ui/Glyphs.kt ui/src/main/kotlin/com/wk2/climate/ui/panel/PanelComfort.kt
git commit -m "chore: delete the adaptive slot; outsideF now feeds only screen 1d's header"
```

---

### Task 9: Harness — every seat-button state, and the menu inline

**Files:**
- Modify: `harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt`

**Interfaces:**
- Consumes: `SeatButton`, `SeatIndicator`, `SeatMenu`, `seatMenuX` from `com.wk2.climate.ui.bar`; `SeatSide`.

- [ ] **Step 1: Add the imports**

```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.ui.bar.SeatButton
import com.wk2.climate.ui.bar.SeatIndicator
import com.wk2.climate.ui.bar.SeatMenu
import com.wk2.climate.ui.bar.seatMenuX
```

- [ ] **Step 2: Give the BAR view a working menu**

Replace the `if (showBar) { ... }` block with:

```kotlin
    // The harness has no second window, so the menu is drawn inline, directly
    // above the bar at the x the service would place its window. Re-tapping
    // the same button closes it; the other button switches. The service's
    // outside-touch dismissal has no equivalent here.
    var menuSide by remember { mutableStateOf<SeatSide?>(null) }
    if (showBar) {
        Column(
            Modifier.fillMaxSize().background(palette.surface),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Key("INSPECTOR", palette) { showBar = false; menuSide = null }
            menuSide?.let { side ->
                Row {
                    Spacer(Modifier.width(seatMenuX(side)))
                    SeatMenu(palette = palette, side = side, state = state, onCommand = { bus.send(it) })
                }
            }
            ClimateBar(
                state = state,
                onCommand = { bus.send(it) },
                onHome = {},
                onBack = {},
                // The harness shows the bar and the panel as separate views, so
                // there is no panel over this bar to toggle: the caret stays up.
                panelOpen = false,
                onToggleClimate = { menuSide = null },
                seatMenuOpen = menuSide,
                onSeatButton = { side -> menuSide = if (menuSide == side) null else side },
            )
        }
        return
    }
```

- [ ] **Step 3: Add a SEATS view showing every indicator state**

After the `if (showBar)` block, add:

```kotlin
    var showSeats by remember { mutableStateOf(false) }
    if (showSeats) {
        // Every state a seat button can be in, both sides, on the current
        // palette. NIGHT toggles the palette from the inspector. The first
        // driver button also shows the wheel badge; the last on each row is
        // painted as "menu open".
        val samples = listOf(
            "heat LOW" to SeatIndicator(SeatIndicator.Kind.HEAT, 1),
            "heat HIGH" to SeatIndicator(SeatIndicator.Kind.HEAT, 2),
            "cool LOW" to SeatIndicator(SeatIndicator.Kind.COOL, 1),
            "cool HIGH" to SeatIndicator(SeatIndicator.Kind.COOL, 2),
            "off" to SeatIndicator(SeatIndicator.Kind.OFF, 0),
            "unknown" to SeatIndicator(SeatIndicator.Kind.UNKNOWN, 0),
        )
        Column(
            Modifier.fillMaxSize().background(palette.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Key("INSPECTOR", palette) { showSeats = false }
            for (side in SeatSide.entries) {
                Mono(side.name, palette.inkMuted)
                Row(Modifier.height(Dimens.barTopRow)) {
                    samples.forEachIndexed { i, (_, indicator) ->
                        SeatButton(
                            palette = palette,
                            side = side,
                            indicator = indicator,
                            wheelOn = side == SeatSide.DRIVER && i == 0,
                            menuOpen = i == samples.lastIndex,
                            onClick = {},
                        )
                    }
                }
                Row { samples.forEach { (name, _) -> Box(Modifier.width(Dimens.seatButton)) { Mono(name, palette.inkMuted, 11.sp) } } }
            }
            Mono("menus, live off the fake bus", palette.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeatMenu(palette = palette, side = SeatSide.DRIVER, state = state, onCommand = { bus.send(it) })
                SeatMenu(palette = palette, side = SeatSide.PASSENGER, state = state, onCommand = { bus.send(it) })
            }
        }
        return
    }
```

Add `Key("SEATS", palette) { showSeats = true }` to the row that holds `BAR` and `PANEL`.

- [ ] **Step 4: Build the harness**

Run: `./gradlew :harness:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Look at it (if an emulator is available)**

Install on the `wk2_panel` AVD (1080x1920 @ 160dpi) and open SEATS in night and day (toggle NIGHT from the inspector first). Check: six states per row, driver's first button has the badge, the last button on each row is raised, the driver menu has three rows and the passenger menu two, and tapping a menu row cycles OFF → HIGH → LOW → OFF on the fake bus. Then open BAR, tap each seat button and confirm the menu appears above the bar at its anchor and the button paints raised. If no emulator is available, note that in the commit body and move on; the vehicle checklist covers it.

- [ ] **Step 6: Commit**

```bash
git add harness/src/main/kotlin/com/wk2/climate/harness/HarnessActivity.kt
git commit -m "feat(harness): every seat-button state, and the seat menus inline"
```

---

### Task 10: Documentation

**Files:**
- Modify: `README.md` (line 45, the screen 2a row)
- Modify: `docs/superpowers/specs/2026-09-08-wk2-climate-design.md` (§6 "The adaptive slot", §8 "Tap adaptive slot")
- Modify: `docs/superpowers/specs/2026-09-14-seat-buttons-design.md` (status line)

- [ ] **Step 1: README**

Replace the screen 2a row's description with:

`Replaces the factory bar in place at \`[0,1693][1080,1920]\`. Nav, volume, a seat button at each end that shows that seat's heat or cool level and opens a menu for it, AUTO, both setpoints, and CLIMATE.`

- [ ] **Step 2: Old spec, §6**

Directly under the heading `### The adaptive slot`, insert:

```markdown
> **Superseded 2026-09-14.** The adaptive slot is gone. The top row is now
> driver seat button / AUTO / CLIMATE / passenger seat button, and each seat
> button opens a menu holding what the slot used to surface for that seat. See
> `2026-09-14-seat-buttons-design.md`. The text below is kept for the record.
```

- [ ] **Step 3: Old spec, §8**

Replace the row `| Tap adaptive slot | Acts on whatever it currently holds. |` with:

`| Tap seat button | Opens that seat's menu; re-tap closes it. Superseded row — see \`2026-09-14-seat-buttons-design.md\` §4. |`

- [ ] **Step 4: New spec status**

Change `**Status:** spec written, not yet reviewed by the owner.` to `**Status:** implemented on \`feat/seat-buttons\` (plan: \`../plans/2026-09-14-seat-buttons.md\`); awaiting the owner's vehicle pass (§8) and answers to §10.`

- [ ] **Step 5: Commit**

```bash
git add README.md docs/superpowers/specs/2026-09-08-wk2-climate-design.md docs/superpowers/specs/2026-09-14-seat-buttons-design.md
git commit -m "docs: the adaptive slot is superseded by the seat buttons"
```

Out of repo, for the owner: wiki page 12 (System Navigation Design) needs its 2a top-row description and adaptive-slot table updated, and the `bar-2a-*defrost*` / `*seatcool*` screenshots in `docs/screenshots/` show the retired slot and want replacing after the vehicle pass.

---

### Task 11: Final verification

- [ ] **Step 1: Full uncached suite**

Run: `./gradlew test --rerun-tasks --console=plain`
Expected: BUILD SUCCESSFUL, 0 failures. Count the tests:

```bash
for f in $(find . -path "*/build/test-results/*" -name "*.xml"); do grep -o 'tests="[0-9]*"' $f | head -1; done | awk -F'"' '{s+=$2} END {print s}'
```

Expected: **155** (baseline 153 − 25 `AdaptiveSlotTest` + 12 + 8 + 2 + 5 new).

- [ ] **Step 2: What CI runs**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL — assemble, unit tests and lint for every module, with no lint *error*.

- [ ] **Step 3: No animation crept in**

Run: `grep -rn "animate\|Crossfade\|AnimatedVisibility" --include=*.kt ui/ app/` 
Expected: only the two `Animatable`/`animateTo` uses in `ClimateBarService.PanelContent` that predate this work.

- [ ] **Step 4: Push**

```bash
git push -u origin feat/seat-buttons
```

Then hand the owner the spec's §8 vehicle checklist and the §10 open questions. The dead-bus item (§8.8) must be exercised the same way the panel's was, never by disabling `com.syu.ms`.
