# Moondrop Space Travel (original): protocol notes

Device under test: firmware "1.0.0" (GAIA application version), MOONDROP Link
2.26.1c, Samsung Galaxy Note 20 Ultra / Android 13.
Data (all in [captures/](captures/)):
- `2026-10-07-link-eq.csv` + notes: first Link capture (EQ, volume).
- `2026-10-07b-physical.csv` + notes: second capture with GAIA **and HFP**
  traffic, while long-pressing (ANC), 4×-tapping (game mode) and moving
  buds in and out of the case.
- `2026-10-07-probe.txt`: read-only probe from a Windows PC.
- `2026-10-07-device-info.md`.
- `2026-10-08-opendrop-report.txt`: OpenDrop's own connect sequence and EQ
  switching on the Space Travel, including the variant name and available
  presets (verified in the app).

Decode any capture with `python3 tools/gaia_decode.py <file.csv>`.
Status tags: **Verified** = seen in our capture. **Inferred** = consistent with
the capture and Qualcomm GAIA v3, not directly confirmed. **Reported** = from
other projects, not yet seen by us.

Legal note: we only use protocol *facts* from other projects (several are
GPL/AGPL) and don't copy their source code.

## Summary

| Item | Value | Status |
|---|---|---|
| Transport | Bluetooth Classic only (no BLE/GATT). SDP: SPP, HFP, A2DP sink, AVRCP | Verified |
| Control protocol | Qualcomm GAIA v3 (vendor `0x001D`) over SPP/RFCOMM | Verified |
| RFCOMM channel | 1 | Verified (probe) |
| Handshake | legacy GAIA v2 "get API version", reply: protocol v4, API 3.1 | Verified |
| Supported features | core v2, earbud v1, voice UI v1, music processing (EQ) v1, upgrade v2 | Verified |
| ANC | Not advertised; queries for features 2, 8, 32 get no reply; **no traffic at all** when ANC is changed by long-press | Verified |
| Game mode | **No traffic** when toggled by 4× tap | Verified |
| Battery | Not over GAIA (feature 13: no reply). Sent over HFP as `AT+IPHONEACCEV=1,1,N`, one value in 10 % steps | Verified |
| Hidden "User" EQ | Available presets include id 63 ("User"); user EQ has 5 bands | Verified (probe) |
| EQ presets | Music processing feature, presets 0/1/2 | Verified |
| Volume | Link's slider is plain AVRCP absolute volume, not GAIA | Verified |
| Firmware version | Core "get application version" → `"1.0.0"` | Verified |
| Identity | variant name `"Moondrop Space Travel"`; serial `"ABCDEF0123456789"` (a placeholder, same on every unit presumably); GAIA protocol version `03 01` | Verified (probe) |

What this Link version offers for this earbud: device card (volume, EQ
Tuning, user guide), Product, Sleep and Settings tabs. **No ANC, gesture or
game mode screens.**

## Framing (RFCOMM)

```
FF | version | flags | length | vendor (u16 BE) | command (u16 BE) | payload (length bytes)
```

- **Verified:** version `01` for the first (v2) handshake frame, `04` for
  everything after. Flags `00` (no checksum, 1-byte length). `length` counts
  the payload only (excludes vendor + command).
- **Verified:** one RFCOMM packet can hold several frames (frame 830), so the
  parser must work on a byte stream.

```
command = (feature << 9) | (type << 7) | (cmd & 0x7F)
type: 0 command, 1 notification, 2 response, 3 error
```

Legacy v2 frame (vendor `0x000A`): command `0x0300` = get API version; the
reply sets bit `0x8000` (ack). Reply payload `00 04 03 01` = status 0,
protocol 4, API 3.1.

## Connect sequence used by Link (Verified)

| Step | TX (phone → buds) | RX (buds → phone) |
|---|---|---|
| 1 | v2 get API version `ff 01 00 00 000a 0300` | `... 000a 8300 00 04 03 01` |
| 2 | core get supported features `001d 0001` | `001d 0101 00 0301 0501 0101 0602 0002` |
| 3 | core cmd 0x0D `07 00 00 00 04` (likely "set transport parameter: protocol version = 4", inferred) | no direct reply seen |
| 4 | core register notification for features 0, 3, 5, 1, 6 (`001d 0007 XX`) | response `001d 0107` per registration |
| 5 | core get application version `001d 0005` | `001d 0105 "1.0.0"` |
| — | | after registering, the buds push current state: voice UI notif 0 = `00`, EQ notif 0 = `00`, EQ notif 1 = `00` (current preset) |

Supported-features payload: `[more=00]` then `(feature, version)` pairs:
`(3,1) (5,1) (1,1) (6,2) (0,2)`.

On some connects Link also sends core `0x0D` "set transport parameter"
`01 00 01 00 09` (reply `01 00 00 00 30`) and core `0x0C` "get transport
info" for keys 4, 2, 3, 6 (replies `04 00 00 03 52`, `02 00 00 00 30`,
`03 00 00 06 40`; key 6 no reply). Inferred: packet-size negotiation
(e.g. 850, 48, 1600). OpenDrop doesn't need these to work.

Opening the device screen, Link also sends core commands `0x13`, `0x14`,
`0x15`, `0x16` with no payload. **No reply was captured for any of them**;
meaning unknown. A core notification 0 with payload `00` followed (inferred:
charger status = not charging).

## EQ presets (Verified)

Music processing = feature 5.

| Action | TX | RX |
|---|---|---|
| Get selected preset | `ff04 0000 001d 0a02` | response `001d 0b02 [preset]` |
| Set preset | `ff04 0001 001d 0a03 [preset]` | response `001d 0b03 01`, then notification `001d 0a81 [preset]` |

| Preset id | Link name |
|---|---|
| 0 | Reference |
| 1 | Basshead |
| 2 | Monitor |

Notes:
- The set response payload was `01` for all three presets (meaning unknown,
  possibly a status). Use the `0a81` notification as the source of truth.
- Notification `0a80` (feature 5, notif 0) carried `00` at connect. Inferred:
  EQ state; meaning of `00` unknown.
- Link warns that switching "may cause a brief pop or restart. Lower the
  volume first." OpenDrop should show the same warning.

## User EQ (hidden, Verified exists; contents unknown)

| Query | Reply | Meaning |
|---|---|---|
| get EQ state `0a00` | `01` | EQ enabled (inferred) |
| get available presets `0a01` | `04 00 01 02 3f` | 4 presets: 0, 1, 2 and **63** (Qualcomm's id for the user-configurable set) |
| get user set band count `0a04` | `05` | 5 bands |

| get user set config `0a05 00 05` | `00 01 00 00 00 00 00 00 00` | start band 0, then all zeros |
| get user set config `0a05 01 05` | `01 01 00 00 00 00 00 00 00` | start band 1, then all zeros |

([probe output](captures/2026-10-07-probe-user-eq.txt))

Link never shows a user EQ for this earbud, but the firmware reports one.
Its configuration reads back as **all zeros**: not just 0 dB gains but
also frequency 0 and Q 0, which aren't valid filter settings. The exact
byte layout is unclear (each reply seems to describe one band, not five).
Most likely the user EQ is an unimplemented stub, kept because the firmware
copies Qualcomm's command set.

Remaining cheap test: `gaia_probe.py --try-user-eq` selects preset 63 for
15 s and then restores the previous preset (the same "set EQ preset"
command Link uses, with a different value). If the earbuds reject 63 or the
sound doesn't change, we drop on-device custom EQ and rely on the phone-side
EQ. We won't try writing band values to a stub.

## Battery (Verified)

Over HFP. The buds enable Apple's battery extension (`AT+XAPL=000D-0001-0101,2`)
and send `AT+IPHONEACCEV=1,1,N`, where key 1 = battery and `N` 0–9 means
(N+1)×10 %. Android turns this into the single level shown in Settings and
the widget. GAIA has no battery feature on ST1.

Observed: `7` (80 %) when both buds connect, flipping to `8` (90 %) and back
as single buds went in and out of the case. So the value is **the earbuds,
not the case**: whichever bud is reporting, or the lower of the two. The case
level isn't sent anywhere.

## ANC and game mode (Verified: not visible to apps)

During the second capture (rows 7–8 of the notes) the long-presses and 4×
taps produced **no GAIA traffic and no HFP traffic** apart from battery
updates and one `AT+BVRA=1` (voice-assistant activation, probably one of the
presses being read as the assistant gesture). The probe's 180 s monitor also
received nothing. ANC and game mode live entirely on the earbuds: an app can
neither change nor see them.

## Gestures and touch lock

No gesture feature advertised, no gesture screen in Link 2.26. Gadgetbridge
reportedly supports "touch actions" on Space Travel; the remaining lead is
to find out which command it sends (AGPL source; protocol facts are fine to
reuse). Until then: not possible.

## Open questions

- [x] Confirm RFCOMM channel 1.
- [x] Probe results (see `captures/2026-10-07-probe.txt`).
- [x] ANC / game mode / case notifications: none.
- [x] Battery: one HFP value, earbuds not case.
- [x] Read the user EQ configuration (`0a05`): all zeros.
- [ ] Optional: `--try-user-eq` (does preset 63 select, does sound change?).
- [ ] Meaning of core `0x13`–`0x16`, EQ notif `0a80`, set-EQ response `01`.
- [ ] Touch actions: which command (if any) Gadgetbridge uses.

## Feature reference from other Moondrop models (Reported)

From HyperPods-for-Moondrop `PROTOCOL.md` (GPL-3.0) and moondrop-control
(GPL-3.0). **None of these features are advertised by ST1.**

| Feature | ID | Commands |
|---|---|---|
| ANC v1 | 2 | get `04 01`, set `04 02 [0/1]` |
| Audio curation | 8 | get `10 03`, set `10 04 [bitmask]` |
| Battery | 13 | types `1A 00`, levels `1A 01 [types]` |
| Voice prompts | 14 | get `1C 01`, set `1C 02 [en][vol][idx]` |
| Dual device | 20 | get `28 01`, set `28 02 [0/1]` |
| ANC v2 | 32 | get `40 03`, set `40 04 [mode]` |
