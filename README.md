# wk2-climate

A replacement climate and navigation UI for the OEM `com.syu.air` bottom bar on
FYT/SYU **UIS7870** head units, built for a **Jeep Grand Cherokee WK2**.

The OEM bar packs roughly 24 same-weight targets into three rows, none taller
than ~70px, and none of it is adjustable. This replaces the resting bar with six
functions at a 96dp minimum target and moves everything else one tap away onto a
full-page climate view.

**Status: design complete, implementation not started.**

---

## Relationship to 7870-Projects

The reverse-engineering that makes this possible lives in
**[chrisuthe/7870-Projects](https://github.com/chrisuthe/7870-Projects)** and its
[wiki](https://github.com/chrisuthe/7870-Projects/wiki) — the SYU IPC framework,
the 87 `U_AIR_*` read codes, the WK2 command index table, and the screen-space
constraints. That repo is the protocol reference; this one is the app.

Nothing here needs root.

## How it works, in one paragraph

The vendor ships a three-interface AIDL framework (`com.syu.ipc`) that is **not
permission-gated** — an ordinary third-party app can bind it and both read and
write the vehicle bus. This app binds three modules (0 MAIN, 4 SOUND, 7 CANBUS),
subscribes to the climate state, and renders entirely from what the vehicle
reports. A tap dispatches a command and mutates nothing locally; the UI updates
when the vehicle reports the new value.

`com.syu.air` **keeps running underneath**. This is an alternative front end, not
a replacement service — a crash here must never leave a vehicle with no physical
HVAC controls unable to change its climate.

## Design

| Path | |
|---|---|
| [`docs/superpowers/specs/`](docs/superpowers/specs/) | The design spec — architecture, bus layer, reconciliation against the verified protocol, testing strategy, car-session checklist |
| [`design_handoff_system_navigation/`](design_handoff_system_navigation/) | The approved visual design. Authority on geometry, colour, type and spacing |

The design file (`System Navigation.dc.html`) renders in a browser — open it and
use the `2a` and `1d` badges to find the two approved screens. Earlier
explorations (`1a`, `1b`, `1c`, `1e`) are kept for context and are **not** built.

The panel is 1080x1920 at 160dpi, so **1px = 1dp** and every measurement in the
design is used directly as a dp value.

## Portability

Read codes (`U_AIR_*`) are universal across FYT UIS7870 units. **Command indices
are per-vehicle** and the ones here come from `Car_0374_PA_Jeep_All`. On another
vehicle they must be swept and rebuilt — see
[wiki: Sending Commands](https://github.com/chrisuthe/7870-Projects/wiki/6-Sending-Commands).

## Safety

This touches the climate control of a vehicle with no physical HVAC controls.
All development was done on a stationary, parked car. Command index **16** turns
the climate system off; indices **12** and **15** are macros that force
recirculation on and temperatures to a minimum sentinel.

## Licence

Apache 2.0. See [LICENSE](LICENSE).
