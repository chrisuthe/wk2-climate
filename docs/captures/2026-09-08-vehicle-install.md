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
