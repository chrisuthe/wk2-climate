# First on-vehicle install of the replacement bar — 2026-09-08

Head unit `uis7870sc_2h10_nosec`, Android 13, 1080x1920 @ 160dpi.
Build: `957bf95`.

## Result

The bar runs, replaces the factory bar exactly, and renders live vehicle state.
Commands reach the vehicle. `com.syu.air` was never touched and stayed alive
throughout.

`module 7: registered 20/20 codes` — the positive bus path, which had never
executed before this session. Also `module 4: 1/1` and `module 0: 2/2`.

Screenshot evidence: `docs/screenshots/vehicle-bar-install.png` (full screen),
`vehicle-bar-strip.png` (the bar alone), `vehicle-cmd-restored.png`.

## What agreed with the vehicle, independently

- `AUTO` amber — AUTO genuinely engaged
- `VOL 10` — matches the factory status bar's own `10`
- `DRIVER 69° / PASSENGER 69°` — the real setpoints
- `SEAT COOL / OFF`, `WHEEL` off

Command test: tapped driver `+`. Setpoint went 69 -> 70, **and the passenger
zone moved with it**, because SYNC is engaged. Nothing in our code mirrors a
zone — the vehicle reported both and the UI rendered what it reported. That is
the render-only-from-bus-state rule confirmed on hardware. Reverted with driver
`-`; both returned to 69. Zero `dropping` lines.

## Defect this session found and fixed: the bar was 227px too high

`resources.displayMetrics.heightPixels` is the **application** area. On this
unit `dumpsys window displays` reports `cur=1080x1920` but `app=1080x1693` —
the navigation bar is already subtracted. So `heightPixels - barHeight` gave
`1693 - 227 = 1466`, and the window landed at `[0,1466][1080,1693]`: directly
*above* the factory bar rather than over it. Both bars visible, and every touch
227px from where it appeared.

Fixed in `957bf95` by measuring against the real display
(`currentWindowMetrics.bounds.height()`, with a `getRealMetrics` fallback below
API 30). Verified: `frame=[0,1693][1080,1920]`.

**Why it survived review:** the two candidate values differ by *exactly* the bar
height, so the wrong arithmetic produced a plausible-looking number and the
emulator — where `navigation_bar_height` is 56px, not 227 — could not reproduce
the coincidence. The service now logs its resolved geometry for this reason.

## Two tooling traps, both of which produced false negatives

1. **`logcat -s <tag>` returns nothing on this ROM.** Tag filtering is silently
   broken; `logcat -d | grep` works. An `-s` read looks exactly like "the code
   logged nothing," which is how the first check was misread as a dead bus.
2. **The main ring buffer is 256 KiB and wraps in well under a minute** on this
   unit. The install-time `module 7` line had already been evicted by the time
   it was read; a fresh service toggle recovered it. Capture *before* the event
   and read *soon*, or use a device-side `logcat -f` whose filterspec does not
   silence the tag you want (`*:S` silenced `wk2-bus`, since the bus logs under
   `wk2-bus` and not `SyuVehicleBus`).

## Checklist items closed

- **Item 10 (touch consumption / inset on hardware):** closed. Window occupies
  `[0,1693][1080,1920]`, `ty=2032`, flags as designed, and taps at bar
  coordinates reached our controls rather than anything beneath.
- **Item 12 (does the 227px inset survive disabling `com.syu.air`):** closed as
  **unnecessary, do not attempt.** Our window covers the factory bar completely
  while `com.syu.air` keeps running, so there is nothing to gain by disabling it
  and a working fallback to lose. The safety net depends on that process being
  alive.

## Still open

- Item 6: does cmd 15 move `U_AIR_ACMAX` (needs a macro-recovery session)
- Item 13: does index 2 toggle AUTO or only set it
- The bus's `connected` positive path is now proven; a *partial* bind
  (CANBUS absent) has still never been observed, only guarded against.

---

## Cold-start defect, reported after an ignition cycle

**Symptom (owner):** shut the truck off and restarted it; the temperatures came
up blank and the bar "appeared HVAC was off" until a `+`/`-` was pressed on one
side, after which it populated.

### What it is not

Not a lost cache and not a dead bus. The head unit restarts on an ignition
cycle, so the process comes up with an empty state map — that part is expected.

### The actual cause has two halves

**1. Registration only subscribes to changes.** `IRemoteModule.register` makes
the service notify on *change*. Nothing replays the current value, so a client
that comes up while the vehicle is quiet sees nothing until something moves.
Pressing `+` was the first change, which is why one press populated the zone.

The OEM has a mechanism for this: `Registrar.notify(int... codes)` simply
**re-registers** the same callback for those codes — re-registration is how the
factory apps force a refresh.

**2. `flag()` collapses unknown into off.** `ClimateState.flag()` is
`raw[signal] == 1`, so an *absent* signal is indistinguishable from a signal
reporting zero. Every boolean control therefore rendered a confident OFF for
state we did not have. The temperatures were honest (em dash); the toggles lied.
That is what made it read as "HVAC is off" rather than "no data yet", and it is
the more serious half — a driver acting on it would conclude the system is
powered down.

### Measured, with the vehicle OFF

`IRemoteModule.get` (transaction 2) was implemented against the vendor's own
proxy format, including the empty `int[]` params the OEM passes (`int...` with
no arguments is `new int[0]`, not the `-1` length `writeIntArray(null)` writes).

Result: **`seeded 0/20`, `0/1`, `0/2` — with no exception.** The service returns
presence `0`, so it holds nothing for these codes rather than rejecting the
call. Consistent with the MCU reporting no climate frames while the ignition is
off. Whether `get` answers with the engine running is **not yet known** — the
screen was fully black (`extrema (0,0)`) while the OS reported `Awake`, which is
how an ignition-off head unit presents, so this session could not test it.

Committed in `1ae97cf` as necessary-but-probably-insufficient.

### The fix still to make

- Re-register on a bounded retry (the OEM's `notify()` mechanism) until climate
  data arrives, to shorten the blind window as far as the vehicle allows.
- Distinguish *unknown* from *off* in `ClimateState`, and give the bar an
  explicit indeterminate rendering until the first climate frame lands. Absence
  must never paint as a confident state.
- Re-test `get` with the engine running before deciding whether to keep it.

---

## Session 2, engine running: both open questions settled

`20:59:06`, ignition on, MCU actively reporting:

```
module 7: registered 20/20 codes
module 4: registered 1/1 codes
module 0: registered 2/2 codes
module 7: seeded 0/20 values
module 4: seeded 0/1 values
module 0: seeded 0/2 values
climate data present after 0 re-registration(s)
```

### `IRemoteModule.get` is not the mechanism — removed

`seeded 0/20` **with the engine running and data flowing**. Presence `0`, no
exception, for every code. The service answers the call and holds nothing, so
this is not a permissions or format problem — `get` simply does not serve these
codes. The parcel format was verified against the vendor's own proxy first,
including the empty `int[]` params, so the implementation was not the fault.

Removed in `18de2be`: 60 lines and 23 synchronous binder round-trips on the main
thread during connect, for a call that never returned a value. Recorded here so
it is not implemented a second time.

### Registration alone is sufficient when the vehicle is talking

`climate data present after 0 re-registration(s)` — values landed within a
second of registering, before the retry schedule made a single attempt. So the
service *does* push on registration; what it will not do is answer a `get`.

The bounded re-registration retry stays as an unexercised safety net for the
genuine cold-start case (head unit up before the MCU reports anything). It cost
nothing here and fired zero times. Its value remains unproven.

### The real cause of "didn't show on startup"

Not blank data — the bar was **not running at all**. Our entry had been dropped
from `enabled_accessibility_services`, which came back at `20:58:00` holding only
the two original services.

The trigger is visible earlier in the log:

```
20:31:15 ActivityManager: forceStop Package:com.wk2.climate from pid:2354 uid:1000
20:31:15 ActivityManager: Force stopping com.wk2.climate appid=10179 user=0
```

A force-stopped package enters the **stopped** state, and a stopped package's
accessibility service is not started and is pruned from the enabled list at
boot. This ROM also runs a `PowerController.BgClean` that logs
`new APK:com.wk2.climate is installed!` on each install, so it is a candidate
for the force-stop as well.

**Consequence for install procedure: never `am force-stop` this package.** To
restart the service, toggle it out of and back into
`enabled_accessibility_services` instead — that leaves the package's stopped
flag alone. The install flow used here did force-stop it, to get a cold process
for testing seeding, which is what broke persistence across the ignition cycle.

`stopped=false` after the reinstall, so the state is clean again. Whether the
entry now survives an ignition cycle is **still unverified** — it needs one
cycle with no force-stop anywhere in the preceding session.

---

## Session 3: re-registration DOES replay. The retry is the right mechanism.

The open question was whether re-registering forces the vendor to push, or
whether it is inert and only live changes ever arrive. It had never been
answered because the retry never needed to fire — climate always arrived on its
own.

**Experiment (engine running, no force-stop):** toggled our entry out of and
back into `enabled_accessibility_services`. That tears the service down —
`teardown()`, `bus.disconnect()` — and brings it back with a **new
`SyuVehicleBus` and an empty state map**. Volume was deliberately not touched.

```
21:06:53.461 module 7: registered 20/20 codes
21:06:53.462 module 4: registered 1/1 codes
21:06:53.463 module 0: registered 2/2 codes
21:06:53.465 module 7: seeded 0/20 values
21:06:53.469 climate data present after 0 re-registration(s)
```

**Result: the bar came back with `VOL 10` already populated**, from an empty map,
with no user action. Screenshot: `docs/screenshots/bar-reregister-replay.png`
(top before the toggle, bottom after).

### What this establishes

1. **`register` replays whatever the service currently holds.** Not just future
   changes. And it does so *synchronously inside the register transaction* — the
   completion line lands **1ms** after the last register call, which is only
   possible if the vendor called back into our callback binder before the
   transact returned. We use a non-oneway transact, which is what allows that.

2. **`get` is useless while `register` replays — same service, same codes,
   same moment.** `seeded 0/20` sits four lines above a state that arrived in
   full. That is a strong confirmation that removing `get` in `18de2be` was
   right, and that the replay path is registration alone.

3. **So why was volume blank at startup?** Because at ignition-on the *vendor*
   did not know it yet either — its own cache was empty, so a replay had nothing
   to replay. The value arrives at the vendor slightly later, and by then we
   have already registered and will only be told if it *changes*.

### Consequence

The bounded re-registration retry is **not inert** — it is exactly the right
mechanism, and it was only failing to help because its stopping condition was
"any climate signal", which climate satisfies within a millisecond. Generalising
that condition to "nothing expected is still missing" makes it pick up volume
and illumination as the vendor learns them.

Also observed: `WHEEL` rendered with a filled ring in both frames, so wheel heat
was on and reported correctly across the reconnect.

---

## Session 4: the generalised retry, verified on hardware

Installed `3027a08` and reconnected with an accessibility toggle (no
force-stop). Engine running.

```
21:13:30.265 module 7: registered 20/20 codes
21:13:30.267 module 4: registered 1/1 codes
21:13:30.268 module 0: registered 2/2 codes
21:13:31.284 re-register attempt 1/4 — 3 of 23 signals never reported: POWER, TEMP_OUT, ILLUMINATION
21:13:32.293 all 23 expected signals reported after 1 re-registration(s)
```

**It works.** The initial registration replayed 20 of 23 signals; a single
re-registration one second later collected the remaining three. Total time from
service start to complete state: **about two seconds**, with no user input.

### The log caught the original complaint's exact mechanism

The three signals the first registration missed were `POWER`, `TEMP_OUT` and
`ILLUMINATION`. **`POWER` is a climate signal on module 7** — and
`ClimateState.powerOn` is `flag(Signal.POWER)`, i.e. `raw[signal] == 1`, so an
absent `POWER` renders as a confident *off*.

That is precisely the owner's original report — "appeared HVAC was off" — now
visible in a log line rather than inferred. It also shows the previous
completion condition ("any climate signal present") was worse than it looked:
the other 19 climate signals had arrived, so the retry would have declared
itself finished **while `POWER` was still missing**, leaving the bar claiming the
system was powered down.

`ILLUMINATION` was likewise missing at first, which is the day/night hazard from
the same session — the bar would have chosen its palette from an absent reading.
Both are now filled within a second, and if they are ever *not* filled, the
night default keeps the bar dark rather than white.

`VOLUME` did not appear in the missing set this time, because the vendor's cache
already held it after the earlier manual change — consistent with session 3's
finding that registration replays the vendor's cache, and that the gap at
ignition-on is the *vendor's* cache being cold rather than ours.

### Health after the change

Window count 1, `com.syu.air` alive, `stopped=false`, and all three
accessibility services intact.

### Still unproven

The true cold start. Every session so far has reconnected while the vehicle was
already running and the vendor's cache at least partly warm. What remains
untested is an ignition-on from cold, where the vendor may take longer than the
8s schedule to learn `VOLUME` and `ILLUMINATION`. If a settling line ever names
signals after attempt 4, the schedule needs extending — that is the one number
to watch.

Also still unproven: that the accessibility entry survives an ignition cycle now
that nothing has force-stopped the package.

---

## Session 5 (2026-09-09 morning): the bar did not come back, and the reason is the ROM

Owner started the truck; factory bar showed, ours did not.

### State found

```
enabled_accessibility_services = <the two originals only, ours gone>
accessibility_enabled          = 1
our window count               = 0
package stopped flag           = stopped=true
uptime                         = 11.8 days
com.syu.air                    = ALIVE
```

Restored by re-adding our entry to the accessibility list. Window count went to
1 and the stopped flag cleared on its own.

### Correction to session 4's theory

Session 4 recorded that a force-stopped package "is pruned from the enabled list
**at boot**". The boot half is wrong: **uptime is 11.8 days**, so the head unit
does not restart on an ignition cycle. It stays powered and merely blanks the
display. Nothing was pruned at boot because there was no boot — something
force-stopped the package and dropped its accessibility entry **while the system
kept running**.

That also means the earlier `am force-stop` was not the root cause it appeared
to be. It was *a* way to reach the stopped state; the ROM has its own.

### What is doing it

The ROM ships a vendor power manager that is visible in its own logs:

```
PowerController.BgClean:  new APK:<pkg> is installed! for userId:0
PowerController.RecogA:   packageName:<pkg> mFgEvent:... mProcState:...
                          mNotificationState:0 mHasNoClearNotificationWhenNavi:false
BroadcastQueue:           com.android.server.powercontroller.CHECK_APPSTATE
```

and it keeps per-app state in `/data/system/appPowerSaveConfig.xml`,
`powercontroller.xml` and `power_info.db`. **`appPowerSaveConfig.xml` has an
mtime of 08:00 today** — the same window in which our service disappeared. The
files are `-rw-------` system-owned and adb here is `uid=2000(shell)` with no
root, so they cannot be read or edited from outside.

The classifier's own log lines name what it weighs: `mFgEvent`, `mProcState`,
`mNotificationState`, `mHasNoClearNotificationWhenNavi`. **Our app scores
nothing on any of them** — an accessibility service with no activity, no
notification and no audio is exactly the profile a background cleaner is built
to kill.

### Mitigation applied

`dumpsys deviceidle whitelist +com.wk2.climate` — now shows
`user,com.wk2.climate,10179`. This exempts us from **AOSP** doze. It may well
not bind the vendor's PowerController, which is a separate mechanism, so this is
a first attempt rather than a fix.

### Evidence capture left running

A rotating whole-buffer capture is now on the device:

```
logcat -f /sdcard/wk2-persist.log -r 2048 -n 6 -v time
```

Whole-buffer deliberately: this ROM silently ignores `logcat` tag filters, and a
filtered capture writes an **empty file**, which reads exactly like "the code
logged nothing" (it cost most of session 2 to that misreading). 12 MB of
rotation is enough to survive an ignition cycle, where the 256 KiB ring buffer
wraps in under a minute.

The next cycle should therefore capture the actual `forceStop` line and whatever
precedes it.

### Candidate real fixes, not yet chosen

1. **A foreground service with an ongoing, non-dismissible notification.** Raises
   `mProcState` and sets `mNotificationState`, both of which the ROM's own
   recognizer reads. Standard Android answer, and it costs a permanent
   notification on the head unit.
2. **The vendor's own whitelist UI**, if one is reachable in Settings — no code
   change, but a manual step that may not survive a factory reset.
3. Both.

Deliberately not chosen unilaterally: option 1 changes what the app puts on the
owner's screen.

### The vendor-whitelist route is closed, and why

The owner found a setting called **"Control custom application"**, sitting
beside "navi app" and "voice app", currently unset — and reported no per-app
power-saving settings anywhere on the unit.

It cannot help us, for a reason that matters more than the setting does:

```
cmd package query-activities --brief -a android.intent.action.MAIN     -c android.intent.category.LAUNCHER   ->  no com.wk2.climate
```

**Our app has no activity at all.** No launcher entry, no launchable component,
so it is absent from every app picker on the device including that one.

That absence is also the mechanism of the problem. `PowerController.RecogA`
weighs `mFgEvent`, `mLastLaunchTime`, `mProcState` and `mNotificationState`. An
app with no launchable component can never generate a launch or foreground
event, so **every input that classifier uses to decide an app matters reads zero
for us, permanently.** We are not a borderline case it happens to misjudge; we
are the exact profile a background cleaner is built to reap.

("Control custom application" is most likely a hardware-key binding —
`com.syu.steer` is installed and FYT units have a custom steering-wheel key —
rather than power management. But the existence of a vendor "designated app"
concept is consistent with the classifier's `mHasNoClearNotificationWhenNavi`
field.)

### Decision: wait for the capture before building anything

Owner's call, and the right one. The two candidate fixes — a foreground service
with an ongoing notification, and adding a launcher activity — are both
inferred from **log field names**, not from an observed cause. The
`deviceidle` whitelist already applied may be sufficient on its own.

So: change nothing, let the next ignition cycle produce the actual `forceStop`
line, and build against evidence. Cost is one cycle where the bar may disappear
again, against the alternative of putting a permanent notification on the
owner's head unit to fix something we have not yet proven.

**What to read from the capture next session:** grep
`/sdcard/wk2-persist.log*` for `forceStop`, `Force stopping`, `BgClean`,
`CHECK_APPSTATE` and `com.wk2.climate`, and establish (a) what killed it,
(b) whether the `deviceidle` whitelist changed anything, and (c) whether the
kill happens at ignition-off, during the off period, or at ignition-on.

---

## Session 6 (2026-09-09): the panel runs, and the killer is identified

### It is `com.syu.ms` — the vendor's own MCU service

Reproduced again on restart: our accessibility entry gone, window count 0,
`stopped=true`, uptime **11.95 days** (so still no reboot). The armed capture
survived and holds the answer:

```
08:18:46.774 ActivityManager: forceStop Package:com.wk2.climate from pid:2354 uid:1000
08:18:46.775 ActivityManager: Force stopping com.wk2.climate appid=10179 user=0: from pid 2354
08:18:46.777 ActivityManager: Killing 17659:com.wk2.climate/u0a179 (adj 100)
```

`pid 2354` is **`com.syu.ms`** — the SYU MCU/toolkit service, *the very process
we bind to for the climate bus*. It reaches the hidden
`ActivityManager.forceStopPackage(String)` by reflection (tag `E/Reflex`) and
sweeps **27 packages** in one pass:

`android.ext.services`, `android.process.acore`, `com.google.*` (docs,
messaging, calendar, dialer, quicksearchbox, gearhead, webview, gapps,
gservices), `com.sprd.srmi`, `com.spreadtrum.ims`, `com.syu.bt`, `com.syu.cs`,
`com.syu.music`, `com.syu.screensaver`, `com.syu.systemupdate`,
`io.homeassistant.companion.android`, `com.tmobile.tuesdays`, **`system`**, and
us.

It force-stops `system` and its own siblings. This is an indiscriminate
RAM-cleaner, not a battery optimiser and not a judgement about our app.

### Two conclusions that change the fix

1. **A foreground service with an ongoing notification would NOT have helped.**
   `forceStopPackage` is unconditional — process importance, notifications and
   `mProcState` are all irrelevant to it. Every earlier theory was built on the
   `PowerController.RecogA` classifier fields, which turn out not to be the
   mechanism at all. **Waiting for evidence avoided shipping a permanent
   notification to the owner's dash for nothing.**
2. **The `deviceidle` whitelist is irrelevant** for the same reason. Leave it;
   it costs nothing, but it is not the fix.

### What is actually left to try

- **Decompile `com.syu.ms`** and find the sweep and any exclusion list. That is
  the one place a real fix could live, and we have the tooling — `air` and
  `canbus` are already decompiled; `ms` is not.
- Establish the **trigger**. 08:18:46 was well after the ignition cycle, so it
  is not an off/on hook. A timer, an idle threshold, or a memory-pressure
  threshold are all plausible.
- Nothing in our app can prevent it. A force-stopped package cannot re-enable
  its own accessibility entry, because there is no process left to do it.

### The panel, first run on hardware — it works

Installed and enabled with the a11y toggle (no force-stop). Log:

```
bar window: y=1693 height=227 display=1920px
module 7: registered 20/20 codes
re-register attempt 1/4 -- 2 of 23 signals never reported: VOLUME, TEMP_OUT
all 23 expected signals reported after 1 re-registration(s)
panel window: y=0 app height=1693px (display=1920px bar=227px)
```

- **Both windows, exact geometry:** panel `frame=[0,0][1080,1693]`, bar
  `frame=[0,1693][1080,1920]`. The bar stays visible below the panel — the half
  the emulator could never show, because the AVD reserves no nav inset.
- **The retry fix earned itself:** `VOLUME` and `TEMP_OUT` were both missing
  from the first registration and filled in one second later without the owner
  touching anything. That is the original "volume didn't show" complaint,
  closed on hardware.
- **State cross-checks against the vehicle independently:** `OUT 66°F`,
  `VOL 9`, AUTO amber, A/C blue, fan meter dimmed under AUTO, and
  `SEAT COOL · L` reading **HIGH** with both pips lit while the other three read
  OFF — the owner had set that, and the panel drew what the vehicle reported.
- The footer is fenced at the bottom edge with no dead band: the
  `heightIn(min = maxHeight)` pin working on real geometry.
- The day-palette fix is visible — fan meter and seat tiles are recessed greys,
  not the opaque white cards they were before `fcee733`.
- `com.syu.air` alive throughout.

### Owner-verified checklist

- `CLOSE` closes the panel — **pass**
- `HOLD . OFF` powers climate off **and back on** — **pass** (the one test that
  could have left the vehicle in a bad state)
- Holding `-`/`+` repeats — **pass**, which settles the final review's F1
  dispute on hardware: press-and-hold was never broken, and the relay of that
  finding as fact was wrong.
- Back **failed**, and was fixed in `c07488b` — see below. Re-verified: **BACK
  and HOME both close the panel.**

### The back defect: a fourth composition-only bug

Our accessibility config was correct all along
(`canRequestFilterKeyEvents` + `flagRequestFilterKeyEvents`) and `onKeyEvent`
was wired properly. The bug was that **the bar's own BACK button never reaches
`onKeyEvent`**: `performGlobalAction(GLOBAL_ACTION_BACK)` dispatches to the
*focused* app, the panel window is `FLAG_NOT_FOCUSABLE` and so is never the
target, and a global action does not route through our own key filter either.
Back went to the app underneath while the panel sat on top.

`c07488b`: BACK closes the panel when it is open, else performs a global back;
HOME closes the panel first, because going home would otherwise leave the panel
covering the launcher.

Both halves were correct in isolation and the seam had no handler — the same
shape as the `verticalScroll` that created two power-off paths, and the bar and
panel wanting different screen heights from the same expression.
