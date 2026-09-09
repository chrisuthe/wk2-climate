# Why the bar disappears: `com.syu.ms`'s sleep sweep

Decompiled `com.syu.ms` (8 MB, 3689 classes, jadx) to find what force-stops us
and whether it can be avoided. It can — but only by renaming our package.

## The mechanism

`a/i.java`:

```java
public static void A() {                       // the sweep
    ...
    for (RunningAppProcessInfo p : am.getRunningAppProcesses()) {
        if (cVar.d(p.processName, 0) == 0
            && !u.j0(p.processName)
            && !p.processName.startsWith("com.antutu")) {
            B(p.processName);
        }
    }
}

public static void B(String str) {             // the kill
    if (str.startsWith(":")) str = str.substring(0, str.indexOf(":"));
    if (a.b.d(str) || s0.j(str, u.I())) {
        return;                                // <-- protected, not killed
    }
    W(str);
    new i1.c("forceStopPackage").g(am).d(str).a();   // reflection, hidden API
}
```

`i1.c` is the reflection helper whose log tag is `Reflex` — the tag seen in the
vehicle capture. It reaches `ActivityManager.forceStopPackage(String)`, which is
`@hide` and normally unreachable; `com.syu.ms` runs as **uid 1000**, so it can.

## The trigger: sleep

`g/d.java:202`, inside the sleep handler:

```java
if (t0.b.n4 == 0) {
    a.f.p().s("休砧");                 // "sleep"
    Log.d("ms", "sSleepWakeup 89 53 sleep sleep");
    a.i.C();                                   // -> A(), the sweep
    a.i.Y("audio.hw.allow_close", "1");
}
```

So it fires when the head unit sleeps — ignition/ACC off. Not a timer, not
memory pressure, and nothing to do with our app's behaviour. It swept **27
packages** in the observed run, `system` and Google's stack among them.

## The exclusion list — and it is a regex

`a.b.d(str)` matches against a `Pattern` compiled from an **APK asset**,
`assets/app_category/protected_app.txt`. `o1.a.c()` builds it by stripping `#`
comments and joining every line as `(line1)|(line2)|...`, with a leading `%`
meaning "this line is already a regex" (the marker is simply dropped).

**85 alternatives.** Then `pattern.matcher(pkg).find()` — and that is the
important detail:

- **`find()` is unanchored**, so a package name is protected if any alternative
  appears *anywhere* in it as a substring;
- the literal entries are **not escaped**, so their `.` matches any character;
- two entries are explicit wildcards: `%com\.android\..` and `%com\.antutu.`

Reproduced the builder and tested real names against it:

| package | result |
|---|---|
| `com.wk2.climate` | **killed** |
| `com.syu.air` | protected — literally listed, commented `#空调控制` ("air-con control") |
| `com.autolauncher.motorcar` | **killed** (the owner's launcher is swept too) |
| `io.homeassistant.companion.android` | **killed** — matches the capture |
| `com.android.wk2climate` | **protected**, via `com.android.w` |
| `com.syu.airbar` | **protected**, via the `com.syu.air` substring |

`com.syu.air` being on the list is exactly why the factory bar survives every
sweep while ours does not.

## What this rules out

- **A foreground service with a notification would not have helped.**
  `forceStopPackage` is unconditional — importance, notifications and
  `mProcState` are irrelevant to it. Every earlier theory was built on the
  `PowerController.RecogA` classifier fields, which are not the mechanism.
- **The `deviceidle` whitelist is irrelevant** for the same reason.
- **The list cannot be edited.** It is an asset inside a system-signed APK, and
  adb here is `uid=2000(shell)` with no root.
- **Nothing in our app can recover afterwards.** A force-stopped package has no
  process left to re-enable its own accessibility entry, and the entry is
  pruned at the framework level.

## The only fix is a package rename

Because the match is an unanchored substring search, our `applicationId` can be
chosen to land inside a protected alternative. Two candidates:

1. **`com.android.wk2climate`** — matches the AOSP wildcard `com\.android\..`.
2. **`com.syu.airbar`** (or any `com.syu.air*`) — matches the vendor's own
   air-control entry. Caveat: other vendor code paths branch on
   `startsWith("com.syu")` (e.g. `f1/c.java:182` exempts such packages from an
   audio-focus path), so this changes more than the kill decision.

Both are namespace squatting. Cost either way: a rename is a new package — a
fresh install, the accessibility entry re-added, and every reference to
`com.wk2.climate` in this repo updated.

Doing nothing is also viable: re-toggle the accessibility entry after a sleep.

Not decided here — this is the owner's call.

## Done: renamed to `com.android.wk2climate`

Owner's call, taken on the reasoning that `com.android.*` is the cleaner of the
two hacks — no vendor code branches on it the way `f1/c.java:182` branches on
`startsWith("com.syu")`, so the only behaviour it changes is the kill decision.

Only the **`applicationId`** moved. The Kotlin packages stay
`com.wk2.climate.*`, because the sweep matches on *process name* and that
defaults to the applicationId. So the accessibility component is now the
slightly odd-looking but correct:

```
com.android.wk2climate/com.wk2.climate.app.ClimateBarService
```

Switched on the vehicle: new component enabled, old entry removed, old package
uninstalled, `deviceidle` exemption moved across (harmless either way — it was
never the mechanism). Bar came up at `frame=[0,1693][1080,1920]` and
`com.syu.air` was untouched throughout.

**Verification status, honestly:** the regex match is *proven* — the pattern was
rebuilt from the extracted asset and this exact string tested against it. What
is **not** yet proven is the end-to-end outcome, because the sweep only runs on
sleep and sleep cannot be forced from adb. **The real test is the next
ignition-off:** if the bar is still there on restart with no accessibility
toggle, this worked.

If it does *not* survive, the next thing to check is `s0.j(str, u.I())` — the
second exclusion in `a.i.B()`, which was never traced because the first one
already explained the behaviour.
