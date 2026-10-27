"""Build the per-vehicle command tables straight from com.syu.air's APK.

usage: python -I tools/extract-vehicles.py <air.apk> <air-canbus-ids.json> <out.json> <out.tsv>

Reads every `Car_*` profile class with tools/dexlib.py -- no decompiler -- and
writes two things:

  out.json  every profile: its table, how its sendCmd frames an index, and
            whether we trust and support it.
  out.tsv   only the profiles the app may drive, plus their canbus ids. This
            is `vehicles.tsv`, the resource `VehicleCatalog` loads.

A profile is **trusted** only if its own sendCmd is exactly the frame the app
sends -- one `sendInt(7, 6, {1, cmd})`, nothing posted after it, no branch on
the canbus id -- and no two controls share an index. 2 of 228 pass. The rest
press-and-release, use another command code, or switch per id; sending them
our frame would be a guess. It is **supported** if it also has driver
temperature up and down plus A/C or a fan, the owner's minimum for taking over
the factory bar.

Two sources per profile, because profiles are not written to one pattern:

  1. `C_NAME = <int>` constant assignments, mapped through SYN. The names are
     semantic, which is what makes the table readable.
  2. literal `sendCmd(<int>)` calls, attributed to the enclosing method and
     mapped through METHOD_CANON. Some profiles skip the constants and inline
     the index.

The vendor spells the same feature up to seven ways, hence the synonym map.
"""
import collections
import hashlib
import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from dexlib import load_apk, method_code  # noqa: E402

SYN = {
    "AC": ["C_AC", "C_AIR_AC"],
    "AUTO": ["C_AUTO", "C_AIR_AUTO"],
    "RECIRC": ["C_CYCLE", "C_AIR_CYCLE"],
    "TEMP_L_UP": ["C_TEMP_LEFT_UP", "C_AIR_TEMP_LEFT_ADD", "C_AIR_TEMP_LEFT_UP"],
    "TEMP_L_DOWN": ["C_TEMP_LEFT_DOWN", "C_AIR_TEMP_LEFT_SUB",
                    "C_AIR_TEMP_LEFT_DOWN"],
    "TEMP_R_UP": ["C_TEMP_RIGHT_UP", "C_AIR_TEMP_RIGHT_ADD",
                  "C_AIR_TEMP_RIGHT_UP"],
    "TEMP_R_DOWN": ["C_TEMP_RIGHT_DOWN", "C_AIR_TEMP_RIGHT_SUB",
                    "C_AIR_TEMP_RIGHT_DOWN"],
    "FAN_UP": ["C_AIRVOL_UP", "C_WIND_UP", "C_AIR_WIND_ADD"],
    "FAN_DOWN": ["C_AIRVOL_DOWN", "C_WIND_DOWN", "C_AIR_WIND_SUB"],
    "FRONT_DEFROST": ["C_FRONT_DEFOG", "C_FRONT_DEFROG", "C_AIR_FRONT_DEFROST",
                      "C_AIR_RONT_DEFROST", "C_AIR_FRONT_WINDOW"],
    "REAR_DEFROST": ["C_REAR_DEFOG", "C_REAR_DEFROG", "C_AIR_REAR_DEFROST",
                     "C_AIR_REAR_DEFOG", "C_AIR_REAR_DEFROG",
                     "C_AIR_REAR_WINDOW_DEFROST"],
    "SYNC": ["C_SYNC", "C_DUAL", "C_AIR_DUAL"],
    "MAX_AC": ["C_MAX_AC", "C_AC_MAX", "C_ACMAX", "C_MAXAC", "C_AIR_AC_MAX",
               "C_AIR_MAXAC"],
    "CLIMATE_POWER": ["C_AIR_POWER", "C_POWER"],
    "SEAT_HEAT_L": ["C_SEAT_HEAT_LEFT", "C_SEATHEAT_LEFT", "C_SEATHEAT_L",
                    "C_LEFTSEAT_HEAT", "C_LEFT_SEATHEAT", "C_AIR_LEFT_HEAT",
                    "C_AIR_SEAT_HOT_LEFT"],
    "SEAT_HEAT_R": ["C_SEAT_HEAT_RIGHT", "C_SEATHEAT_RIGHT", "C_SEATHEAT_R",
                    "C_RIGHTSEAT_HEAT", "C_RIGHT_SEATHEAT", "C_AIR_RIGHT_HEAT",
                    "C_AIR_SEAT_HOT_RIGHT"],
    "SEAT_VENT_L": ["C_SEAT_COLD_LEFT", "C_SEATCOLD_LEFT", "C_SEAT_BLOW_LEFT",
                    "C_AIR_LEFT_COLD"],
    "SEAT_VENT_R": ["C_SEAT_COLD_RIGHT", "C_SEATCOLD_RIGHT",
                    "C_SEAT_BLOW_RIGHT", "C_AIR_RIGHT_COLD"],
    "WHEEL_HEAT": ["C_STEER_HEAT"],
}
LOOKUP = {n: canon for canon, names in SYN.items() for n in names}

# Method names on the literal-sendCmd path, mapped to the same canon.
METHOD_CANON = {
    "ac": "AC", "cycle": "RECIRC", "auto": "AUTO", "sync": "SYNC",
    "acMax": "MAX_AC", "power": "CLIMATE_POWER",
    "leftTempUp": "TEMP_L_UP", "leftTempDown": "TEMP_L_DOWN",
    "rightTempUp": "TEMP_R_UP", "rightTempDown": "TEMP_R_DOWN",
    "airVolUp": "FAN_UP", "airVolDown": "FAN_DOWN",
    "frontDefog": "FRONT_DEFROST", "rearDefog": "REAR_DEFROST",
    "airLeftSeatHot": "SEAT_HEAT_L", "airRightSeatHot": "SEAT_HEAT_R",
    "airLeftSeatBlow": "SEAT_VENT_L", "airRightSeatBlow": "SEAT_VENT_R",
    "airSteer": "WHEEL_HEAT",
    # The air* family, used by profiles that inline the index rather than
    # declaring a C_ constant. "Vol" here is air volume, i.e. the fan.
    "airAc": "AC", "airAuto": "AUTO", "airCycle": "RECIRC",
    "airTempLeftP": "TEMP_L_UP", "airTempLeftM": "TEMP_L_DOWN",
    "airTempRightP": "TEMP_R_UP", "airTempRightM": "TEMP_R_DOWN",
    "airVolLeftP": "FAN_UP", "airVolLeftM": "FAN_DOWN",
    "airFront": "FRONT_DEFROST", "airRear": "REAR_DEFROST",
    "airDual": "SYNC", "airPower": "CLIMATE_POWER", "airAcMax": "MAX_AC",
}

# Verified on the vehicle, not declared by name in the profile.
#
# The Wrangler class's airMode() sends the literals 8-11; the WK2 sweep fixed
# their meanings through that same class code, so they hold for every id the
# class serves.
CLASS_ADDITIONS = {
    "Car_0374_PA_Jeep_Wrangler": {
        "AIRFLOW_FACE": 8, "AIRFLOW_FACE_FEET": 9,
        "AIRFLOW_FEET": 10, "AIRFLOW_FEET_GLASS": 11,
    },
}
CLASS_ADDITION_METHOD = "airMode"

# Index 15 is fitted on the Grand Cherokee and absent from the class, so it is
# keyed by id: the class's other 56 ids never get a blind 15.
ID_ADDITIONS = {
    2687350: {"MAX_AC": 15},   # CAR_PA_GrandCherokee_14_22
    5308790: {"MAX_AC": 15},   # CAR_PA_GrandCherokee_14_22_Amp
}

WK2_SHAPE = "7/6/{1,cmd}"
SEND_INT = "Lcom/syu/remote/RemoteTools;.sendInt"


def table_from_record(rec):
    """Constants first, then method literals.

    A constant takes its last nonzero assignment: some profiles zero every C_
    field in <init> and assign the real index later, and jadx -- which the
    tables were first built from -- drops the zeroing. A method literal fills
    only a control no constant named.
    """
    table = {}
    for _, field, value in rec.get("iput", []):
        canon = LOOKUP.get(field)
        if canon and value != 0:
            table[canon] = value
    for method, value in rec.get("sendcmd", []):
        canon = METHOD_CANON.get(method)
        if canon and canon not in table:
            table[canon] = value
    return table


def supported(table):
    return ("TEMP_L_UP" in table and "TEMP_L_DOWN" in table
            and ("AC" in table or ("FAN_UP" in table and "FAN_DOWN" in table)))


def has_duplicates(table):
    return len(set(table.values())) != len(table)


def classify_send(insns, arg_reg):
    """Name how a sendCmd(int) body frames its argument; WK2_SHAPE only for an exact match."""
    consts = {}
    arrays = collections.defaultdict(dict)  # array reg -> {index: value reg}
    value_consts = {}                       # (array reg, index) -> const held by the value reg
    sends = []
    for ins in insns:
        if ins.op in (0x2b, 0x2c):
            return "branches (switch)"
        if ins.ref and ins.ref.endswith(".mCurrCanbus"):
            return "branches on the canbus id"
        if ins.ref and ins.ref.endswith(".postDelayed"):
            return "press then delayed release"
        if ins.op in (0x12, 0x13, 0x14):
            consts[ins.regs[0]] = ins.const
        elif ins.op == 0x23:
            arrays[ins.regs[0]] = {}
            consts.pop(ins.regs[0], None)
        elif 0x4b <= ins.op <= 0x51:
            value, array, index = ins.regs
            if index in consts:
                arrays[array][consts[index]] = value
                value_consts[(array, consts[index])] = consts.get(value)
        elif ins.ref == SEND_INT:
            sends.append((ins.regs, dict(consts)))
    if len(sends) != 1:
        return "%d sendInt calls" % len(sends)
    regs, at = sends[0]
    if len(regs) != 4:
        return "sendInt with %d registers" % len(regs)
    _, mod, code, arr = regs
    if at.get(mod) != 7 or at.get(code) != 6:
        return "sendInt(%s, %s, ...)" % (at.get(mod), at.get(code))
    elements = arrays.get(arr, {})
    if sorted(elements) != [0, 1] or value_consts.get((arr, 0)) != 1 or elements[1] != arg_reg:
        return "7/6 with another payload"
    return WK2_SHAPE


def _pairs(table):
    return ",".join("%s=%d" % (k, v) for k, v in sorted(table.items(), key=lambda kv: (kv[1], kv[0])))


def render_p(profile, table):
    return "P\t%s\t%s" % (profile, _pairs(table))


def render_v(vid, profile, vendor_const, extra):
    return "V\t%d\t%s\t%s\t%s" % (vid, profile, vendor_const or "-", _pairs(extra) if extra else "-")


def main(apk, ids_json, out_json, out_tsv):
    dexes = load_apk(apk)
    records = {}
    for d in dexes:
        for name, rec in d.records():
            if name.startswith("Lcom/syu/air/canbus/Car_") and "$" not in name:
                records[name[len("Lcom/syu/air/canbus/"):-1]] = (name, rec)

    rows, ok = [], {}
    shapes = collections.Counter()
    for profile in sorted(records):
        desc, rec = records[profile]
        table = table_from_record(rec)
        found = method_code(dexes, desc, "sendCmd")
        shape = classify_send(found[1], found[0] - 1) if found else "no sendCmd"
        shapes[shape] += 1
        trusted = shape == WK2_SHAPE and not has_duplicates(table)
        is_supported = trusted and supported(table)
        if is_supported and profile in CLASS_ADDITIONS:
            sent = {v for m, v in rec["sendcmd"] if m == CLASS_ADDITION_METHOD}
            add = CLASS_ADDITIONS[profile]
            missing = set(add.values()) - sent
            if missing:
                sys.exit("%s.%s no longer sends %s; the verified airflow indices do not hold"
                         % (profile, CLASS_ADDITION_METHOD, sorted(missing)))
            table = {**table, **add}
            if has_duplicates(table):
                sys.exit("%s: the airflow additions collide with a declared index" % profile)
        rows.append({"profile": profile, "table": table, "send_format": shape,
                     "trusted": trusted, "supported": is_supported})
        if is_supported:
            ok[profile] = table

    ids = json.loads(pathlib.Path(ids_json).read_text(encoding="utf-8"))
    for vid in ID_ADDITIONS:
        if not any(e["id"] == vid for p in ok for e in ids.get(p, [])):
            sys.exit("ID_ADDITIONS names %d, which no supported profile serves" % vid)

    digest = hashlib.sha256(pathlib.Path(apk).read_bytes()).hexdigest()
    lines = [
        "# vehicles.tsv -- generated by tools/extract-vehicles.py; do not edit by hand.",
        "# source: %s sha256 %s" % (pathlib.Path(apk).name, digest),
        "# P<TAB>profile<TAB>CONTROL=index,...   a profile the app may drive",
        "# V<TAB>id<TAB>profile<TAB>vendor const<TAB>extra CONTROL=index,...   a canbus id it serves",
    ]
    n_ids = 0
    for profile in sorted(ok):
        lines.append(render_p(profile, ok[profile]))
        for e in sorted(ids.get(profile, []), key=lambda e: e["id"]):
            lines.append(render_v(e["id"], profile, e["const"], ID_ADDITIONS.get(e["id"], {})))
            n_ids += 1
    pathlib.Path(out_tsv).write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")
    pathlib.Path(out_json).write_text(json.dumps(rows, indent=1) + "\n", encoding="utf-8", newline="\n")

    print("profiles: %d" % len(rows))
    for shape, n in shapes.most_common():
        print("  %4d  %s" % (n, shape))
    print("trusted+supported: %d profiles, %d ids" % (len(ok), n_ids))
    return rows


if __name__ == "__main__":
    if len(sys.argv) != 5:
        sys.exit(__doc__)
    main(*sys.argv[1:])
