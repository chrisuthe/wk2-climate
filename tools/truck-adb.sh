#!/usr/bin/env bash
# adb to the truck's head unit, at home or away.
#
# Android's "wireless debugging" (the TLS listener that mDNS advertises) is
# tied to the Wi-Fi network and shuts itself off when Wi-Fi drops, so it is
# useless once the truck leaves the driveway. The older adb TCP mode is a
# separate listener that does not care about Wi-Fi, and the head unit runs
# Tailscale with its own SIM, so over Tailscale that port is reachable from
# anywhere. The catch: on a non-rooted unit `adb tcpip` does not survive a
# reboot, so it has to be re-armed over Wi-Fi after one.
#
#   tools/truck-adb.sh arm       find the truck on mDNS (home Wi-Fi), verify it
#                                is the truck, and (re-)enable TCP mode on 5555
#   tools/truck-adb.sh arm -w    the same, but keep polling until it shows up
#   tools/truck-adb.sh connect   attach by Tailscale address, from anywhere
#   tools/truck-adb.sh status    what adb sees, and which build is installed
#
# The truck is recognised by the vendor climate service, com.syu.air, never by
# its address alone -- the tablet and the Shield also answer on this network.
# See docs/captures/2026-09-15-remote-adb-over-tailscale.md.

set -u
export MSYS_NO_PATHCONV=1      # Git Bash would otherwise rewrite /sdcard paths

TRUCK_TS_IP=100.108.206.15
PORT=5555
FINGERPRINT=com.syu.air
PKG=com.android.wk2climate

is_truck() {                    # $1 = adb serial
    [ "$(adb -s "$1" shell "pm list packages $FINGERPRINT" 2>/dev/null | tr -d '\r' | grep -c "package:$FINGERPRINT\$")" = 1 ]
}

installed_version() {           # $1 = adb serial
    adb -s "$1" shell "dumpsys package $PKG | grep -E 'versionCode|versionName'" 2>/dev/null \
        | tr -d '\r' | awk '{print $1}' | paste -sd' ' -
}

arm() {
    local watch=${1:-}
    while :; do
        # The TLS service, e.g. "adb-<id>._adb-tls-connect._tcp  10.0.1.15:44533".
        local addr
        addr=$(timeout 8 adb mdns services 2>/dev/null | awk '/_adb-tls-connect/ {print $3}' | head -1)
        if [ -n "$addr" ]; then
            adb connect "$addr" >/dev/null 2>&1
            sleep 2
            local serial
            serial=$(adb devices | awk '/_adb-tls-connect/ && /device$/ {print $1}' | head -1)
            if [ -n "$serial" ] && is_truck "$serial"; then
                local ip=${addr%%:*}
                echo "truck found at $addr"
                adb -s "$serial" tcpip "$PORT"
                sleep 3
                adb connect "$ip:$PORT"
                echo "installed: $(installed_version "$ip:$PORT")"
                return 0
            fi
            echo "$addr answers but is not the truck"
        fi
        [ "$watch" = "-w" ] || { echo "truck not on mDNS -- is it on home Wi-Fi?"; return 1; }
        sleep 15
    done
}

connect() {
    adb connect "$TRUCK_TS_IP:$PORT" || return 1
    sleep 2
    if is_truck "$TRUCK_TS_IP:$PORT"; then
        echo "connected to the truck over Tailscale"
        echo "installed: $(installed_version "$TRUCK_TS_IP:$PORT")"
    else
        echo "connected, but $TRUCK_TS_IP does not look like the truck"; return 1
    fi
}

status() {
    adb devices -l | grep -v '^List'
    for s in $(adb devices | awk '/device$/ && !/^List/ {print $1}'); do
        is_truck "$s" && echo "$s is the truck -- installed: $(installed_version "$s")"
    done
}

case "${1:-}" in
    arm)     arm "${2:-}" ;;
    connect) connect ;;
    status)  status ;;
    *)       sed -n '2,20p' "$0"; exit 2 ;;
esac
