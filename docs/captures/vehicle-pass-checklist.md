# On-vehicle verification pass

Everything outstanding on `task/climate-panel` that can only be settled in the
truck. Ordered so a failure early does not waste the rest of the session.

## 0. Before touching anything — read the captures

Two captures were armed on 2026-09-09 and should have survived an ignition
cycle:

```
adb -s <serial> shell "grep -iE 'forceStop|Force stopping|BgClean|CHECK_APPSTATE|A11yState' /sdcard/wk2-kill.log" | tail -40
adb -s <serial> shell "ls -l /sdcard/wk2-persist.log*"
```

`wk2-kill.log` is the grep-filtered one and cannot overflow. The rotating
`wk2-persist.log*` set fills a 2 MB file in under a minute while the unit is
awake, so treat it as a fallback only.

**What decides the persistence fix:**

1. What actually force-stopped `com.wk2.climate` — `PowerController`/`BgClean`
   or something else.
2. Whether the `deviceidle` whitelist (`user,com.wk2.climate,10179`, applied
   2026-09-09) changed anything.
3. **Whether the kill happens at ignition-off, during the off period, or at
   ignition-on.** These point at different mitigations, which is why no fix was
   built on inference.

Baseline to diff against is in the scratchpad `precycle.txt`: a11y entry
present, window 1, `stopped=false`, whitelist applied, uptime 11.84 days.

## 1. Install — and do NOT force-stop

```
adb -s <serial> install -r -g app-debug.apk   # applicationId is com.android.wk2climate
```

Then toggle the accessibility entry to get a fresh service instance:

```
# append-only: the list already holds com.syu.fytgesture and com.autolauncher.motorcar
adb -s <serial> shell "settings put secure enabled_accessibility_services '<the two originals>'"
adb -s <serial> shell "settings put secure enabled_accessibility_services '<the two originals>:com.android.wk2climate/com.wk2.climate.app.ClimateBarService'"
adb -s <serial> shell "settings get secure enabled_accessibility_services"   # READ IT BACK
```

- **Never `am force-stop`.** It sets the package's stopped flag, which prunes
  our accessibility entry. That is what broke persistence once already.
- The **first** CLIMATE tap after `install -r` over a live service can throw
  `BadTokenException` from a stale window token (caught, logged, bar stays
  usable). The a11y toggle above avoids it.
- `logcat -s <tag>` **silently returns nothing on this ROM.** Use
  `logcat -d | grep`. An `-s` read looks exactly like "the code logged nothing".
- The main ring buffer is 256 KiB and wraps in under a minute. Read soon or
  capture first.

## 2. Bar regressions to confirm (it was already working)

- `frame=[0,1693][1080,1920]`, `ty=2032`, window count 1.
- Live values: setpoints, `VOL`, AUTO lit iff engaged.
- One `+` tap moves the setpoint on the vehicle; the UI follows the *vehicle*
  (with SYNC on, both zones move — that is correct).
- **New since the last install:** the day palette's raised surface is now 5%
  black rather than opaque white, so the `VOL` strip should be a faint recess
  and not a white block. Only visible with the headlights off.
- `com.syu.air` alive throughout.
- Log line `bar window: y=… height=… display=…` — one line, confirms geometry.

## 3. Cold-start behaviour

- `module 7: registered 20/20 codes`.
- `re-register attempt N/4 — … signals never reported: …` then
  `all 23 expected signals reported after N re-registration(s)`.
- **If a settling line still names signals after attempt 4**, the vendor takes
  longer than the 8 s schedule to learn them and `DEFAULT_DELAYS_MS` needs
  extending. That is the one number to watch.
- With the headlights on at ignition-on, the bar should come up in the **night**
  palette. Unknown illumination now defaults to night deliberately — a white
  bar at night is a hazard, a dark one is a nuisance.

## 4. Panel — never verified on hardware

- CLIMATE opens it; **the bar stays visible below it**, panel at
  `[0,0][1080,1693]`, bar at `[0,1693][1080,1920]`, window count **2**.
- It slides up, 220 ms, translate-Y only, no fade on controls.
- CLOSE and the **back gesture** both close it, and the close reverses.
  Back is *unproven*: injected key events bypass the accessibility filter, so
  the emulator could not test it. Also check whether `com.syu.fytgesture` eats
  the gesture first.
- Every control drives the real vehicle and the UI follows reported state.
- Re-tapping the **active** airflow tile is a genuine no-op.
- `MAX A/C` side effects (forces recirc; drives both setpoints to the LO
  sentinel) render correctly.
- **No touch leakage beneath the panel.**
- The double-open guard — untestable on the emulator, where the panel covers
  the bar.
- **`HOLD · OFF`: confirm it powers climate off AND that you can turn it back
  on**, before doing anything else with it. There are no physical HVAC
  controls in this vehicle. Also confirm a single **tap** does nothing.

## 5. Still-open protocol questions

- **Item 6:** does command index 15 move `U_AIR_ACMAX`? Needs a
  macro-recovery session.
- **Item 13:** does index 2 **toggle** AUTO or only set it? Send it while
  `U_AIR_AUTO` already reads 1. Trivial and reversible.

## Rules that hold for the whole session

- `com.syu.air` is never disabled, stopped or interfered with — it is the
  fallback the entire safety design rests on. Item 12 is closed as
  **unnecessary, do not attempt**.
- Append to `enabled_accessibility_services`, never overwrite: the owner's
  gesture nav and launcher both live there. Always read it back.
- The vehicle is a TCP transport, so `adb -e` matches it as well as an
  emulator. Address devices by serial, always.
