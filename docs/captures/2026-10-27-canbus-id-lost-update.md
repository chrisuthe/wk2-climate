# A fresh install sat at "Reading your car…": the canbus id was overwritten

First install of the multi-vehicle build on the truck, app data cleared. The
service bound and every climate signal arrived, but the vehicle status stayed at
`Waiting` with no bar and no setup. The same build worked on the emulator. It was
a race in the bus, and it had been there since the first build.

## What the log showed

```
07:50:28.466 I/ClimateBarService: vehicle: Waiting (no canbus id yet)
07:50:28.485 I/wk2-bus: canbus id 2687350 (0x290176) base=374 variant=41
```

The bus received the id, but the store never saw it: no `vehicle.xml`, no
status change. Clearing the data and rebinding the service repeated the failure
in 2 of 10 starts. The other 8 reached `NeedsSetup` within about 20 ms.

## Instrumented

Two temporary log lines: the thread behind every `onUpdate`, and whether the
state still held the id one second after it arrived. `ClimateState.with` only
ever adds keys, so a missing id can only mean another write replaced the state.

```
run 1:  NeedsSetup  has id: true   threads=binder_1 binder_2 binder_3 binder_4
...
run 6:  Waiting     has id: false  threads=binder_2 binder_3
run 10: Waiting     has id: false  threads=binder_1 binder_2 binder_3
```

Run 6, around the id:

```
DIAG onUpdate m=4 c=2    t=binder:3018_2
DIAG onUpdate m=7 c=1000 t=binder:3018_3
DIAG onUpdate m=0 c=40   t=binder:3018_2
```

## The cause

Each module's callback is its own `Binder`, so `com.syu.ms` replays modules
0, 4 and 7 into this process on different binder pool threads at the same time.
The bus folded each update in with

```kotlin
_state.value = _state.value.with(signal, value)
```

which is a read and then a write. The module 4 thread read the state, the module
7 thread wrote the id, and the module 4 thread wrote back its older copy.

## Why it surfaced only now

The race could already drop any signal on dc5c009. There the loss was invisible:
the re-registration retry re-requests every *expected* signal that has not
arrived, and the vendor replays its cache on each registration. The canbus id is
`diagnostic`, so it is never retried, and the head unit sends it once per
registration. Before multi-vehicle nothing depended on it. Now the whole bar
does.

## The fix

`MutableStateFlow<ClimateState>.record()` in `ClimateState.kt` uses
`update { it.with(signal, value) }`, a compare-and-set loop that retries when
another thread got there first. `RecordTest` releases one thread per signal at
once; with the old write it lost 23 of 24 signals in its first round.

On the truck, 20 of 20 fresh starts then reached `NeedsSetup`.

## Worth knowing for any client of this IPC

Callbacks from different modules arrive concurrently. Anything they share has to
be updated atomically, or confined to one thread.
