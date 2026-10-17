# Moondrop Space Travel (original): protocol notes

Device under test: firmware "1.0.0" (GAIA application version), MOONDROP Link
2.26.1c for the captures, Samsung Galaxy Note 20 Ultra / Android 13. The
static analysis was repeated against **MOONDROP Link 2.26.2c-261008ai**
(versionCode 100037, APK set `MOONDROP_2.26.2c-261008ai_100037.apks`); that
version is Flutter on top of an obfuscated Qualcomm GAIA v3 Java client.
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
- Link 2.26.2c static analysis (not a capture) for the app's feature map and
  command ids; see [moondrop-gaia-features.md](moondrop-gaia-features.md).

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
| Feature map | device reports 0 Basic, 1 Earbud, 3 Voice UI, 5 Music processing, 6 Upgrade; the app's full map is 0–35 with 28 unused | Device list Verified; ids App |

What this Link version offers for this earbud: device card (volume, EQ
Tuning, user guide), Product, Sleep and Settings tabs. **No ANC, gesture or
game mode screens.**

Link 2.26.2c contains a plugin for every feature id in
[moondrop-gaia-features.md](moondrop-gaia-features.md), but that only means
the app supports the family; the ST1 still advertises none of the newer
features.

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

## SPP service UUIDs (App)

Link 2.26.2c uses `createRfcommSocketToServiceRecord`, not a hard-coded
channel. It resolves the device's SDP UUIDs and prefers, in order:

1. `00001101-0000-1000-8000-00805F9B34FB` (standard SPP)
2. `00001107-D102-11E1-9B23-00025B00A5A5` (vendor SPP)
3. `00001101-0000-1000-8000-00805F9B34FB` (standard SPP fallback)

The Airoha SDK also carries `00001101-D102-11E1-9B23-00025B00A5A5`; it is not
used by the main SDP selection above.

The ST1 advertises the standard SPP service on RFCOMM channel 1, which is why
the verified channel-1 result still holds. The app also has GATT/LE transport
classes for Airoha devices, but the Moondrop GAIA path is SPP.

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

**2.26.2c clarification (App):** the bytes `0x0C`/`0x0D` are simply Basic
command **12** (get protocol information) and **13** (set protocol parameter);
they are not a separate legacy protocol. Command 13's payload is
`[param id][value u32]`; parameter 7 is the protocol version (set to 4), and
parameters 1–6 are max/optimum TX/RX packet sizes and flow control. The v3
sequence is: Basic get GAIA version (0), get supported features (1), create
one plugin per feature/version, set protocol parameter 7, register
notifications (Basic command 7 with the feature id), then read application
version (5), variant name (4) and serial (3). This is equivalent to the
capture above; the old "core 0x0C/0x0D" labels were just the hex command ids.

Opening the device screen, Link also sends core commands `0x13`, `0x14`,
`0x15`, `0x16` with no payload. **No reply was captured for any of them.**
The 2.26.2c Basic plugin explains them: the v3 commands are 18 (earbud color),
19 (earbud language), 20 (left earbud serial), 21 (right earbud serial) and
22 (TWS connection status). The captured `0x13`–`0x16` are 19–22, so they are
almost certainly reads for language, left SN, right SN and TWS status. The
ST1 not replying is consistent with it not exposing those fields. A core
notification 0 with payload `00` followed (inferred: charger status = not
charging).

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

The 2.26.2c app's `MusicProcessing` plugin confirms the commands: get 0 (EQ
state), get 1 (available presets, `[count][id...]`), get 2 (selected preset),
notification 1 (selected preset changed), set 3 (select preset). Its preset
enum also has 63 = user and treats other values as presets; the device-reported list (0, 1, 2, 63)
remains the source of truth.

Notes:
- The set response payload was `01` for all three presets (meaning unknown,
  possibly a status). Use the `0a81` notification as the source of truth.
- Notification `0a80` (feature 5, notif 0) carried `00` at connect. Inferred:
  EQ state; meaning of `00` unknown.
- Link warns that switching "may cause a brief pop or restart. Lower the
  volume first." OpenDrop should show the same warning.

## User EQ (Verified exists; 2.26.2c layout)

| Query | Reply | Meaning |
|---|---|---|
| get EQ state `0a00` | `01` | EQ enabled (inferred) |
| get available presets `0a01` | `04 00 01 02 3f` | 4 presets: 0, 1, 2 and **63** (Qualcomm's id for the user-configurable set) |
| get user set band count `0a04` | `05` | 5 bands |
| get user set config `0a05 00 04` | `[0][4]` + 5 × 7 bytes | start band 0, end band 4 (all five bands) |
| get user set config `0a05 01 04` | `[1][4]` + 4 × 7 bytes | start band 1, end band 4 |

The request payload is `[start band][end band]`, **not** "start band then
five zeros". Each band is 7 bytes, decoded by the 2.26.2c MusicProcessing
plugin:

```
[0..1] frequency (u16 BE, Hz)
[2..3] Q x 4096 (u16 BE)
[4]    filter type: 0 bypass, 1 LP1, 2 HP1, 3 AP1, 4 LS1, 5 HS1,
       6 tilt1, 7 LP2, 8 HP2, 9 AP2, 10 LS2, 11 HS2, 12 tilt2,
       13 parametric EQ
[5..6] gain x 60 (i16 BE, dB)
```

The old probe queried `0a05 00 05` (end band 5, out of range for 5 bands).
The replies `00 01 00...` and `01 01 00...` therefore only prove that one
zeroed band (band 1) came back; the earlier "all zeros" conclusion was too
strong. Re-probe with `00 04` before deciding the user EQ is a stub.

Link never shows a user EQ for this earbud, but the firmware reports one.
The most likely explanation is still an unimplemented stub (a real band would
not have frequency 0 and Q 0), but that now needs the corrected query.

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

## Factory SPP (App)

Link 2.26.2c also has a second, unrelated SPP channel
(`com.moondroplab.moondrop/factory_spp`) used for serial numbers and a
microphone test. It uses the standard SPP UUID
`00001101-0000-1000-8000-00805F9B34FB` like the GAIA channel, but its own
framing:

```
55 AA 43 58 57   magic "U*CXW" (5 bytes)
00               reserved
<length u16 LE>  payload length + 4
<command u16 LE>
<payload len u16 LE>
<payload ...>
<crc16 u16 LE>   CRC-16/IBM over the whole frame (poly 0xA001, init 0)
```

The parser searches the byte stream for the 5-byte magic, validates the
length and CRC, then walks `[id u16][len u16][value]` TLVs. The factory code
uses:

- Command `21`: set the 20-byte ASCII serial number. The response is command
  `22` with a 20-byte serial TLV (ASCII, `0xFF` padding means "empty").
- Microphone commands: `callMain`, `defaultCall`, `testFf`, `testFb`, with
  fixed payloads such as `09 0B 01 CC 55 30 37 01 00 00`.

This channel is separate from GAIA; the normal device UI never sends it.

## Open questions

- [x] Confirm RFCOMM channel 1.
- [x] Probe results (see `captures/2026-10-07-probe.txt`).
- [x] ANC / game mode / case notifications: none.
- [x] Battery: one HFP value, earbuds not case.
- [x] Read the user EQ configuration (`0a05`): the 7-byte band layout is now
  known from 2.26.2c. The old probe asked for end band 5, so its "all zeros"
  result only covers band 1. The user EQ may still be a stub.
- [ ] Re-probe with `0a05 00 04` (all five bands).
- [ ] Optional: `--try-user-eq` (does preset 63 select, does sound change?).
- [x] Core `0x13`–`0x16`: 2.26.2c Basic commands 19–22 (language, left SN,
  right SN, TWS status). Wire replies still not captured.
- [ ] Meaning of EQ notif `0a80` and set-EQ response `01`.
- [ ] Touch actions: TouchV2/V3/V4 command layouts are now decoded from
  2.26.2c, but ST1 advertises no touch feature. The remaining question is
  which command (if any) Gadgetbridge uses for ST1.

## Feature reference from other Moondrop models

From Link 2.26.2c (App) and, where noted, HyperPods-for-Moondrop
`PROTOCOL.md` (GPL-3.0) / moondrop-control (GPL-3.0). **None of these
features are advertised by ST1.**

| Feature | ID | Commands |
|---|---|---|
| ANC v1 (Reported) | 2 | get command 1 (`04 01`), set command 2 (`04 02 [0/1]`) |
| Audio curation (Reported subset) | 8 | get `10 03`, set `10 04 [bitmask]` |
| Battery (App) | 13 | types: get 0 (`1A 00`); levels: get 1 (`1A 01 [types]`) |
| Voice prompts (App) | 14 | get 1 (`1C 01`); set 2 (`1C 02 [1,index]` or 3 bytes) |
| One brings two (App) | 20 | get 1/3/5/6, set 2/4/7 (`28 01` / `28 02 [0/1]`, etc.) |
| Power/standby timeout (App) | 25 | get 1/3, set 2/4 (`32 01`, `32 02 [en,min]`, ...) |
| Touch v3 (App) | 26 | get 1/2, set 3, 7 bytes (`34 03`) |
| ANC v2 (App) | 32 | get 3 (`40 03`), set 4 (`40 04 [mode]`); get 41/set 42 switch conf |
| ANC v3 (App) | 33 | get 3/set 4 (`42 03`/`42 04`); get 5/set 6 anti-wind; get 41/set 42 switch conf |
| Find my earbuds (App) | 34 | set 1 (`44 01 [0/1/2]`), stop 2 |
| Dual-mic ENC (App) | 35 | get 1 (`46 01`), set 2 (`46 02 [0/1]`) |

The full command/payload tables, including features 0, 1, 5, 21–31, are in
[moondrop-gaia-features.md](moondrop-gaia-features.md). Feature 29 (audio file
storage) is also present in 2.26.2c.
