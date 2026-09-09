# Extracting the 228 vehicle command tables from `com.syu.air`

**Question:** can the app configure itself for an arbitrary vehicle - discover
that a car has seat heaters, seat coolers, a heated wheel, and learn which
command indices drive them - instead of shipping one hand-swept table for a
WK2?

**Short answer:** the vendor's own tables are accurate but incomplete, and they
cannot be pooled. A profile is a trustworthy source for the features it
declares and says nothing at all about the rest.

## Where the data is

All **228** `Car_NNNN_*` profiles live in `com.syu.air`, decompiled with jadx.
Each is a subclass of `com.syu.air.console.Air`. The base class holds no default
command table, so a profile that does not declare an index does not inherit one.

Indices reach the MCU through one call:

```java
protected void sendCmd(int cmd) {
    this.mTools.sendInt(7, this.CMD_TYPE, cmd, 0);   // module 7, cmd 6, prefix 1
}
```

which is exactly the encoding in our `Command` enum.

Profiles declare their indices two ways, and both had to be handled:

1. **Named constants** - `this.C_SEAT_HEAT_LEFT = 13;` - then `sendCmd(this.C_SEAT_HEAT_LEFT)`.
   160 profiles. The names are semantic, which is what makes the table readable.
2. **Inlined literals** - `public void airAc(View v) { sendCmd(1); }`. The
   method name carries the meaning instead.

The vendor spells the same feature up to seven ways
(`C_SEAT_HEAT_LEFT`, `C_SEATHEAT_LEFT`, `C_SEATHEAT_L`, `C_LEFTSEAT_HEAT`,
`C_LEFT_SEATHEAT`, `C_AIR_LEFT_HEAT`, `C_AIR_SEAT_HOT_LEFT`), across **236
distinct constant names**, so extraction needs a synonym map onto canonical
features. `tools/extract-air-profiles.py` holds it; the output is
`docs/data/air-command-tables.json`.

## It is accurate when it declares something

Validated against the only table we can actually verify - the WK2's, swept by
hand on the vehicle:

| profile | features declared | agree with vehicle | disagree |
|---|---|---|---|
| `Car_0374_PA_Jeep_Wrangler` | 18 | **18** | **0** |
| `Car_0374_PA_Jeep_All` | 6 | 6 | 0 |

Zero wrong values. The Wrangler profile's only gap is `MAX_AC` (15), which its
UI does not expose but which is fitted and working on this WK2.

## It is badly incomplete

`Car_0374_PA_Jeep_All` is the profile **our own truck runs**, and it declares
6 of the 19 commands we use. Auto-configuring from it would have produced a bar
with A/C, recirculate and two temperature pairs - no fan, no sync, no seat
heat, no seat cooling, no power.

The reason is that a profile encodes what `com.syu.air`'s UI offers for that
vehicle, not what the vehicle accepts. The index space is implemented in the MCU
firmware; `com.syu.air` is only one client of it, and an unexposed control is
simply an empty method:

```java
public void setSeatHeating(boolean auto, int area, int param) { if (2 != area) { } }
public void setSeatVentilation(boolean auto, int area, boolean on) { }
public void acMax(boolean on) { }
public void sync(boolean on) { }
```

All four are empty on our profile. All four work on the vehicle.

Across all 228 profiles, declaration rates for the features this app draws:

| feature | profiles | % |
|---|---|---|
| AC | 188 | 82% |
| RECIRC | 187 | 82% |
| TEMP_L_UP / DOWN | 157 | 69% |
| TEMP_R_UP / DOWN | 149 | 65% |
| AUTO | 139 | 61% |
| CLIMATE_POWER | 146 | 64% |
| FAN_UP / DOWN | 130 | 57% |
| REAR_DEFROST | 128 | 56% |
| FRONT_DEFROST | 118 | 52% |
| SYNC | 119 | 52% |
| MAX_AC | 34 | 15% |
| SEAT_HEAT_L / R | 51 | 22% |
| SEAT_VENT_L / R | 25 | 11% |
| WHEEL_HEAT | 3 | 1% |

30 profiles declare nothing at all; 12 declare 18 features. The comfort features
that prompted the question are the *worst* covered: seat heat 22%, seat cooling
11%, heated wheel **1%** - three profiles out of 228.

## Pooling sibling profiles does not work

The tempting shortcut - our profile is thin, so borrow from others sharing the
`0374` prefix - is wrong, and quietly so.

`Car_0374_XP_KeLeiAo` declares 16 features on a completely different numbering:
`AC = 17`, `AUTO = 20`, `TEMP_L_UP = 31`, `SEAT_HEAT_L = 36`. Pooled with ours it
yields confidently wrong values for 16 of 19 features. The leading four digits
are not a shared index space; the `PA` / `XP` / `WC` infix marks different canbus
box vendors, and those number their commands independently.

Measured across every shared leading id: **157 feature-slots agree, 163
conflict.** A coin flip.

There is a trap worth naming here. Comparing the *set of index numbers* a
protocol family uses makes pooling look excellent - the union for `0374` is 23
indices against the 23 we use, a near-perfect overlap. That comparison is
meaningless. Only mapping feature to index exposes that the numbers agree while
the meanings do not.

## What this means for multi-vehicle support

- **A profile is a source of known-correct starting values, not a capability
  map.** Trust what it declares. Infer nothing from what it omits.
- **Never pool across profiles**, including siblings sharing a prefix.
- **The vehicle does not need to be asked for.** The canbus id is a readable
  signal - see below - so profile selection is automatic. No picker.
- **Everything a profile omits still needs sweeping**, which is the part that
  cannot be automated safely - `CLIMATE_POWER` is in that space, and on a vehicle
  with no physical HVAC controls a mis-sent index has no undo.
- The read side does not rescue this. `U_AIR_*` read ids are universal, but
  `IRemoteModule.get` reports presence 0 for every climate code and registration
  notifies only on change - so an unfitted feature and an untouched one look
  identical. See `2026-09-08-vehicle-install.md`.

Reproduce with:

```
python tools/extract-air-profiles.py <jadx-out>/sources/com/syu/air/canbus docs/data/air-command-tables.json
```

---

## The vehicle identifies itself: module 7, read code 1000

The open question above - whether the selected profile is discoverable at
runtime - is answered in `CarPx3`, and the answer is yes, over the IPC we
already use:

```java
sCodes[sIndex] = 1000;
this.mTools.enableModule(7, sCodes);
// ...
case 1000:
    if (JTools.check(ints, 0) && this.mCurrCanbus != ints[0]) {
        this.mCurrCanbus = ints[0];
        this.mAir = AirFactory.create(this.mContext, this.mCurrCanbus, instance);
        this.mContext.startService(new Intent("com.syu.air.AirService"));
    }
```

**Module 7, read code 1000, `ints[0]`.** The same module and the same
registration mechanism as every `U_AIR_*` code we already consume - adding it
costs one entry in our signal set. `com.syu.air` does not query it either; it
waits for the callback, which is consistent with registration being the only
read path.

`AirFactory.create` is then a switch mapping that id to one of **222** profile
classes across **1200** distinct ids, extracted to
`docs/data/air-canbus-ids.json`.

### The id is a packed pair

`canbus & 0xFFFF` is the base protocol and matches the four-digit number in the
class name; `canbus >> 16` is the vehicle variant. The low-16 rule holds for
1077 of the 1200 mappings. So `0x190176` is base 374, variant 25,
`CAR_PA_Wrangler_18_20_Low`.

### Which profile is this truck actually running?

Unresolved, and the mapping makes the current assumption look wrong.

`Car_0374_PA_Jeep_All` was attributed to this vehicle from `updateTemp`'s
LOW/HIGH rendering. But it is wired to only two ids, both
`CAR_XP1_ZiYouGuang` - a Renegade - and it declares 6 of the 19 commands we use.
`Car_0374_PA_Jeep_Wrangler` declares 18 and agrees with the vehicle on all 18,
and is wired to the `CAR_PA_*` family: Wrangler, RAM, Durango, GMC, Escalade.

Both are consistent with what we have measured, since `_All`'s six values are a
subset and neither conflicts. The command table points at Wrangler.

**Test, next time the vehicle is online:** register module 7 code 1000 and read
`ints[0]`. Predictions:

- low 16 bits == 374
- the variant resolves, through `air-canbus-ids.json`, to
  `Car_0374_PA_Jeep_Wrangler` rather than `_All`

If it resolves to `_All` instead, then the profile a unit runs does **not**
bound which indices the MCU accepts - our vehicle answers 18 commands its own
profile never sends - and the extracted tables are a floor rather than a
contract. Either result is worth knowing; the second is the more useful.
