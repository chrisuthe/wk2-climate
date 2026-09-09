"""Build a canonical per-vehicle command table from com.syu.air's Car_* classes.

Two sources per profile, because profiles are not written to one pattern:

  1. `this.C_NAME = <int>;` constant initialisers. 160 of the 228 profiles use
     these and the names are semantic, which is what makes the table readable.
  2. literal `sendCmd(<int>)` calls, attributed to the enclosing method. Some
     profiles skip the constants and inline the index.

Both feed a synonym map onto the same canonical feature names, because the
vendor spells the same feature up to six ways (C_SEAT_HEAT_LEFT,
C_SEATHEAT_LEFT, C_SEATHEAT_L, C_LEFTSEAT_HEAT, C_LEFT_SEATHEAT,
C_AIR_LEFT_HEAT, C_AIR_SEAT_HOT_LEFT).
"""
import collections, json, re, sys
from pathlib import Path

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

RE_CONST = re.compile(r"this\.(C_[A-Z_0-9]+)\s*=\s*(-?\d+)\s*;")
RE_METHOD = re.compile(
    r"^    (?:public|protected|private)\s+(?:static\s+)?"
    r"[\w.<>\[\]]+\s+(\w+)\s*\([^)]*\)\s*\{", re.M)
RE_SENDCMD_LIT = re.compile(r"\bsendCmd\s*\(\s*(-?\d+)\s*\)")
RE_SENDCMD_CONST = re.compile(r"\bsendCmd\s*\(\s*(?:this\.)?(C_[A-Z_0-9]+)\s*\)")
RE_SENDINT = re.compile(r"mTools\.sendInt\s*\(\s*(\d+)\s*,\s*([\w.]+)")


def body_of(text, brace):
    depth, i = 0, brace
    while i < len(text):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return text[brace + 1:i]
        i += 1
    return ""


def parse(path):
    text = path.read_text(encoding="utf-8", errors="replace")
    consts = {m.group(1): int(m.group(2)) for m in RE_CONST.finditer(text)}
    table, evidence = {}, {}
    for name, val in consts.items():
        canon = LOOKUP.get(name)
        if canon and canon not in table:
            table[canon] = val
            evidence[canon] = name
    for m in RE_METHOD.finditer(text):
        method = m.group(1)
        canon = METHOD_CANON.get(method)
        if not canon:
            continue
        body = body_of(text, m.end() - 1)
        lit = RE_SENDCMD_LIT.findall(body)
        ref = RE_SENDCMD_CONST.findall(body)
        val = None
        if lit:
            val = int(lit[0])
        elif ref and ref[0] in consts:
            val = consts[ref[0]]
        if val is not None and canon not in table:
            table[canon] = val
            evidence[canon] = method + "()"
    modules = sorted({int(m.group(1)) for m in RE_SENDINT.finditer(text)})
    return {"profile": path.stem, "table": table, "evidence": evidence,
            "constants": consts, "modules": modules}


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    rows = [parse(p) for p in sorted(src.glob("Car_*.java"))]
    out.write_text(json.dumps(rows, indent=1), encoding="utf-8")

    CORE = ["AC", "AUTO", "RECIRC", "TEMP_L_UP", "TEMP_L_DOWN",
            "FAN_UP", "FAN_DOWN", "FRONT_DEFROST", "REAR_DEFROST"]
    print("profiles: %d" % len(rows))
    hist = collections.Counter(len(r["table"]) for r in rows)
    usable = [r for r in rows if all(k in r["table"] for k in CORE)]
    print("with a full core set (%s): %d" % ("+".join(CORE[:4]) + "+...", len(usable)))
    print("with nothing at all: %d" % hist[0])
    print()
    print("feature availability across all 228:")
    for feat in SYN:
        n = sum(1 for r in rows if feat in r["table"])
        print("  %-15s %3d profiles (%2d%%)" % (feat, n, round(100 * n / len(rows))))
    return rows


if __name__ == "__main__":
    main()
