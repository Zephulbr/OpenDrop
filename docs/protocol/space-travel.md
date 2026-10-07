# Moondrop Space Travel (original): protocol notes

Device under test: firmware "1.0.0" (GAIA application version), MOONDROP Link
2.26.1c, Samsung Galaxy Note 20 Ultra / Android 13.
Capture: [captures/2026-10-07-link-eq.csv](captures/2026-10-07-link-eq.csv)
with [notes](captures/2026-10-07-link-eq-notes.md) and
[device info](captures/2026-10-07-device-info.md).

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
| RFCOMM channel | 1 | Reported (Gadgetbridge); confirm with the probe |
| Handshake | legacy GAIA v2 "get API version", reply: protocol v4, API 3.1 | Verified |
| Supported features | core v2, earbud v1, voice UI v1, music processing (EQ) v1, upgrade v2 | Verified |
| ANC over GAIA | **Not advertised** (no feature 2, 8 or 32) | Verified (absence); probe to confirm |
| Battery over GAIA | Not advertised (no feature 13). Battery is sent over HFP | Verified (absence) / Reported |
| EQ presets | Music processing feature, presets 0/1/2 | Verified |
| Volume | Link's slider is plain AVRCP absolute volume, not GAIA | Verified |
| Firmware version | Core "get application version" → `"1.0.0"` | Verified |

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

## Battery

GAIA battery (feature 13) isn't advertised. Reported by Gadgetbridge: the
buds send battery over HFP (`AT+IPHONEACCEV`), which Android already reads;
it is one combined level. To check: what Android shows for the buds in
Settings → Connected devices (one value, or L/R?).

## ANC, gestures, game mode, touch lock

None of these are in Link for this earbud, and the buds don't advertise GAIA
ANC features. Remaining possibilities, to test with the probe's monitor mode:

1. The buds send a notification when you long-press (ANC) or 4×-tap (game
   mode). Then OpenDrop can at least *show* the current mode.
2. Undocumented core commands (`0x13`–`0x16`, or others) control them.
   Unlikely and risky to search for blindly; we won't brute-force "set" commands.
3. Nothing: these are handled entirely on the earbuds, and no app can change them.

Gadgetbridge reportedly supports "touch actions" on Space Travel. If the probe
finds nothing, it's worth finding out which command Gadgetbridge sends (its
source is AGPL; reading it for protocol facts is fine).

## Open questions

- [ ] Confirm RFCOMM channel 1 (probe connects on it).
- [ ] Probe results: replies to the read-only command list in `tools/gaia_probe.py`.
- [ ] Monitor mode: notifications on ANC long-press, game mode 4×-tap, case open/close, one bud in case.
- [ ] Meaning of core `0x0D`, `0x13`–`0x16`, EQ notif `0a80`, set-EQ response `01`.
- [ ] Battery as shown by Android: combined or L/R.
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
