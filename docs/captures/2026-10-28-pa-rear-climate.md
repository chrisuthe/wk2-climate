# The PA rear climate page, decoded from `com.syu.canbus`

2026-10-28. A Durango 2013-2018 high-trim tester asked for the "Rear page".
Nothing in `docs/data/air-command-tables.json` has rear commands, because
that file is `com.syu.air` -- the factory **bar** -- and its
`Car_0374_PA_Jeep_Wrangler` profile declares none. The rear page lives in the
full-screen climate activity of FYT's car app, `com.syu.canbus`.

Read with `tools/dexlib.py` and `aapt2 dump` against the `canbus.apk` pulled
from the WK2's head unit on 2026-10-26. No decompiler.

**Not yet verified on a vehicle with rear climate.** The WK2 has none.

## Which cars get it

`com.syu.canbus.Receiver.onReceive` handles the `com.syu.canbus.enter.air`
broadcast with a switch on the canbus id (`DataCanbus.DATA[1000]`). Its branch
for `com.syu.carinfo.xp.ziyouguang.PAJeepAirControlAct_SP` is taken by 58 ids
-- exactly the 58 ids `vehicles.tsv` maps to `Car_0374_PA_Jeep_Wrangler`,
the WK2 and both Durango 13-18 trims among them. The Renegade ids (374,
65910, profile `Car_0374_PA_Jeep_All`) are not.

`PAJeepAirControlAct_SP.onCreate` picks one of two layouts:

- `layout_0374_pa_jeep_13gmc_air_sp` for 2031990 (GMC 13), 2294134
  (SONOTEC 13) and 2359670 (Escalade 13). It adds seat-back heat L/R and AUX
  on the *front* channel (`{1, 32}`, `{1, 33}`, `{1, 34}`).
- `layout_0374_pa_jeep_air_sp` for every other id.

Both layouts carry `tv_sp_front` / `tv_sp_rear` tabs and a rear panel
(`veiw_air_rear`, `visibility=gone` until REAR is tapped). The rear panel is
not gated per car: FYT offers it to every id of the class, the WK2 included,
whether or not the vehicle has rear climate. So it cannot tell us which cars
have rear climate; the driver has to.

`PAJeepFrontAirControlAct_SP` and `PAJeepRearAirControlAct_SP` are an older
two-activity version of the same screen. Nothing launches the front one by
name, so both are dead code -- but their button-to-number map agrees with the
live one below, which is a useful cross-check.

## Writes

Front page: `sendCmd(1, n)` -> `PROXY.cmd(6, {1, n})`. This is the frame the
app already sends (module 7, code 6).

Rear page: `sendCmdRear(n)` -> `PROXY.cmd(8, {n})` -- module 7, **code 8**,
**one** int. Sent once, on `ACTION_DOWN`; no release frame. While the REAR
tab is showing (`pagenum == 1`) every button, including the power button in
the header, goes through `sendCmdRear`.

| Rear view id | n | Meaning |
|---|---|---|
| `air_xts_power` (on the REAR tab) | 16 | rear on/off |
| `air_pa_jeep_auto_rear` | 2 | rear AUTO |
| `air_pa_jeep_temp_left_plus_rear`, `..._right_plus_rear` | 4 | rear temperature up |
| `air_pa_jeep_temp_left_munits_rear`, `..._right_munits_rear` | 5 | rear temperature down |
| `air_pa_jeep_wind_plus_rear` | 6 | rear fan up |
| `air_pa_jeep_wind_minuts_rear` | 7 | rear fan down |
| `air_pa_jeep_mode_body_rear` | 8 | rear airflow face |
| `air_pa_jeep_mode_footbody_rear` | 9 | rear airflow face + feet |
| `air_pa_jeep_mode_foot_rear` | 10 | rear airflow feet |
| `air_xts_rearlock` | 32 | lock the rear-seat controls |

The left and right rear temperature buttons send the same numbers: one rear
setpoint, drawn twice.

## Reads (module 7)

The front updaters read exactly the codes `Signal` already uses (10, 11, 12,
13, 16, 18-21, 27-32, 37, 53, 62, 65, 66), so these indices are the same
code space.

| Code | Rear updater | Meaning |
|---|---|---|
| 40 | `mUpdateAirTempRear` | rear setpoint; same sentinels as the front (-2 LOW, -3 HIGH, -1 `---`), unit from 37 |
| 42 | `mUpdatePowerOn` (REAR tab) | rear on |
| 43 | `mUpdateRearAutoOn` | rear AUTO |
| 44 | `mUpdaterAirWindLevelRear` | rear fan, 0..7 (0..4 on 5177718, RAM 13-18 low "win4") |
| 46 | `updateBtnSourceRear` | rear airflow: face |
| 47 | `updateBtnSourceRear` | rear airflow: feet |
| 67 | `mUpdateRearLockOn` | rear-seat controls locked |

Airflow: 46 alone lights `mode_body_rear`, 47 alone lights `mode_foot_rear`,
both light `mode_footbody_rear`.

## What this means for detection

On the WK2, which has no rear climate, code 40 reads -2 -- the LOW sentinel,
not a "not fitted" value. Together with the ungated tab, there is no signal to
detect a rear zone from. It is a setup question.
