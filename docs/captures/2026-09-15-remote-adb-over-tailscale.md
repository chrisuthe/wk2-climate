# adb to the truck over Tailscale — 2026-09-15

The head unit (Unisoc `uis7870sc_2h10`, Android 13, user build, no root) runs
Tailscale 1.102.3 and has its own SIM, so it is on the tailnet whether or not
it is on home Wi-Fi. This note records how to reach adb on it from anywhere,
what was verified, and what is still unproven.

## What blocks the obvious route

Android 11+'s **wireless debugging** is the TLS listener that `adb mdns
services` advertises as `_adb-tls-connect`. It is tied to the Wi-Fi network:
the system shuts it off when that network drops, and there is no setting on a
user build to keep it up. Off Wi-Fi, that port is simply gone.

## What works instead

The older adb TCP mode is a separate listener and ignores Wi-Fi state:

```
adb tcpip 5555
```

Verified 2026-09-15 08:3x on the vehicle, over the existing TLS connection:
adbd restarted in TCP mode, `adb connect 10.0.1.15:5555` reported `device`
with **no authorisation prompt** (the key paired for wireless debugging is
accepted for plain TCP as well), and the bar's window and `com.syu.air` were
untouched by the adbd restart.

Trade-off: this is plain adb, not TLS. It still requires an authorised key,
the path from the PC runs inside Tailscale's encryption, and the cellular
interface (`sipa_eth8`) sits behind carrier NAT, so nothing inbound reaches
it from the internet. Accepted by the owner.

**It does not survive a reboot.** `service.adb.tcp.port` is not a persistent
property and setting `persist.adb.tcp.port` needs root. After a reboot,
re-arm it while the truck is on home Wi-Fi: `tools/truck-adb.sh arm` (or
`arm -w` to wait for it to appear). The unit had 17.8 days of uptime when
this was set up; it sleeps rather than reboots.

Side effects of the restart: wireless debugging's TLS port changes (44533
became 46775; whatever `adb mdns services` shows is the current one), and
on Wi-Fi the unit now also advertises the TCP listener itself, as
`_adb._tcp 10.0.1.15:5555`.

## Tailscale on the head unit

- Address `100.108.206.15`. Peer traffic confirmed live in logcat
  (`magicsock: disco: node [LTY6Y] … now using [2001:48f8:…]:41641`).
- **Not yet always-on.** `always_on_vpn_app` is unset, so after a reboot
  someone has to open the app (it had been launched by hand at 08:17 on
  2026-09-15). To make it come back on its own, run over any adb connection:
  `adb -s 10.0.1.15:5555 shell settings put secure always_on_vpn_app com.tailscale.ipn`
  and read it back with `settings get secure always_on_vpn_app`. Leave
  lockdown off — a lockdown would black-hole the head unit's own traffic
  while Tailscale is down, which is the wrong failure mode for a car radio.

## From the PC

The PC's own Tailscale install is broken (state store failed a TPM integrity
check; `tailscale status` reports NoState) and is **not** used. The PC
reaches the tailnet through the router, a tailnet node running 24×7.

```
tools/truck-adb.sh connect        # adb connect 100.108.206.15:5555
```

## Unproven, and how to prove it

**Reaching `100.108.206.15` from the PC has not yet succeeded.** With the
truck on home Wi-Fi, ping and `adb connect` to the Tailscale address time
out after the router hop. That test is confounded: both ends are on the same
LAN, so the truck's replies may take the LAN instead of the tunnel. The
decisive test is with the truck **on cellular**:

```
ping 100.108.206.15
tools/truck-adb.sh connect
```

If that fails too, the fix is on the tailnet, not the truck: check in the
admin console that the head unit and the router are on the same tailnet,
that the router's `10.0.0.0/22` subnet route is approved, and that no ACL
blocks router-sourced traffic to the head unit.

## Devices that are not the truck

On this network `adb mdns services` also shows a Relndoo T901 tablet (USB)
and an NVIDIA Shield at `10.0.1.55:5555`. The script recognises the truck by
the presence of `com.syu.air`, never by address.
