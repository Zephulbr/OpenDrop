# Moondrop GAIA features (0 to 35)

Commands for Moondrop's GAIA v3 feature set (vendor `0x001D`), from static
analysis of **MOONDROP Link 2.26.2c-261008ai** (versionCode 100037). The app
is Flutter, but the GAIA client is the Qualcomm GAIA v3 Java library, R8-
obfuscated into `defpackage`; each feature has a `V3*Plugin` class. This
document replaces the 2.26.1c notes and adds the features the old analysis
missed.

Status tags:

- **App** — exact feature/command ids read from Link 2.26.2c code.
- **Wire** — seen in one of our captures ([captures/](captures/)).
- **Inferred** — consistent with App/Wire, not directly seen.
- **Reported** — from other projects, not checked here.

The Space Travel (ST1) advertises only features **0, 1, 3, 5 and 6**. Our
captures cover those; everything else is **App only** and still needs a
capture from a model that has the feature before OpenDrop writes it.

Framing is the usual GAIA v3 one ([space-travel.md](space-travel.md)):

```
command = (feature << 9) | (type << 7) | cmd
type: 0 command, 1 notification, 2 response, 3 error
```

For example `0a 02` = feature 5, command 2 (get selected EQ preset).
Multi-byte payload values are **big-endian** (GAIA's usual convention), e.g.
the protocol-parameter payload `07 00 00 00 04` is parameter 7 = version 4.
The USB protocols in [usb-dac.md](usb-dac.md) are little-endian instead.

## Feature map

The feature ids below are the app's `r14` enum, so this also corrects the
older 13-to-35 table.

| Id | Feature | Id | Feature |
|---|---|---|---|
| 0 | Basic (core + device information) | 18 | Spatial audio |
| 1 | Earbud | 19 | LED |
| 2 | ANC (legacy) | 20 | One brings two (multipoint) |
| 3 | Voice UI | 21 | Bluetooth address |
| 4 | Debug | 22 | Touch controls v2 |
| 5 | Music processing (EQ) | 23 | Audio resource |
| 6 | Upgrade | 24 | Power control |
| 7 | Handset service | 25 | Power/standby timeout |
| 8 | Audio curation | 26 | Touch controls v3 |
| 9 | Earbud fit | 27 | Dynamic bass |
| 10 | Voice processing | 29 | Audio file storage |
| 11 | Gesture configuration | 30 | L/R channel swap |
| 12 | Statistics | 31 | Touch controls v4 |
| 13 | Battery | 32 | ANC v2 |
| 14 | Voice prompts | 33 | ANC v3 |
| 15 | DAC gain | 34 | Find my earbuds |
| 16 | Codec type | 35 | Dual-mic ENC |
| 17 | Light/wear sensor | | |

The app has no feature 28. Feature 29 (`AUDIO_FILE_STORAGE`) is present in
2.26.2c; it was missing from the old notes.

## How Link starts a GAIA session (App)

The v3 client does this once per connection:

1. Basic command 0: get GAIA version.
2. Basic command 1: get supported features, in chunks. Each chunk starts with
   `more` and is followed by `(feature, version)` pairs.
3. Create a plugin for each supported feature/version.
4. If the Basic plugin is version >= 2, set protocol parameter 7 (protocol
   version = 4), then read/set packet sizes and flow control with commands
   12/13.
5. Register for notifications (Basic command 7 with the feature id).
6. Read device information: application version (5), variant name (4), serial
   (3), user features (14/15), system information (17), TWS status (22).
7. Read the feature state that the UI needs.

The old 2.26.1c capture shows the same sequence with the legacy v2 handshake
first. Its "core `0x0C`/`0x0D`" bytes are Basic commands 12 (get protocol
information) and 13 (set protocol parameter); see
[space-travel.md](space-travel.md) for the capture.

## Basic (0)

App evidence: `V3BasicPlugin` (`ue5`), request builders `rc2`/`ti4`.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 0 | get | none | GAIA version (one byte) |
| 1 | get | none | supported features (chunked) |
| 2 | notif | 1 byte | TWS connection status changed |
| 3 | get | none | serial number (ASCII) |
| 4 | get | none | variant name (ASCII) |
| 5 | get | none | application version (ASCII) |
| 7 | set | 1 byte feature id | register notification |
| 9 | set | `[id u16][?]` | set up a data transfer (Basic v2) |
| 10 | set | `[id u16][offset u32][length u32]` | transfer data |
| 12 | get | 1 byte param id | get protocol information |
| 13 | set | `[param id][value u32 BE]` | set protocol parameter |
| 14 | get | none | user features (chunked) |
| 15 | get | 3 bytes | continue user features |
| 17 | get | 4 bytes | system information (chunked) |
| 18 | get | none | earbud color (one byte) |
| 19 | get | none | earbud language (one byte) |
| 20 | get | none | left earbud serial (ASCII) |
| 21 | get | none | right earbud serial (ASCII) |
| 22 | get | none | TWS connection status |
| 0 | notif | `[2, status]` | charger status (inferred) |
| 1 | notif | three u16s | cross-update required |
| 2 | notif | 1 byte | TWS status; 1..3 valid |

Protocol parameters (command 12/13): 1 max TX packet, 2 optimum TX, 3 max
RX, 4 optimum RX, 5 TX flow control, 6 RX flow control, 7 protocol version.
The old capture's `0x0C`/`0x0D` bytes are these commands 12/13 (`0x0C` = 12,
`0x0D` = 13), not a separate transport protocol.

## Music processing (5)

App evidence: `V3MusicProcessingPlugin` (`lf5`). Wire: EQ preset switching on
ST1.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 0 | get / notif | 1 byte | EQ state (0/1) |
| 1 | get / notif | none / 1 byte | get: available presets `[count][id...]`; notif: selected preset changed |
| 2 | get / notif | 1 byte / band list | get: selected preset; notif: band change |
| 3 | set | 1 byte | select preset |
| 4 | get | none | user EQ band count (one byte) |
| 5 | get | `[startBand][endBand]` | user EQ configuration |
| 6, 7, 8 | set | n bytes | user EQ writes (exact use still unclear) |

Command 5 reply: `[startBand][endBand]` followed by 7 bytes per band. Each
7-byte band is:

```
[0..1] frequency (u16 BE, Hz)
[2..3] Q × 4096 (u16 BE)
[4]    filter type (sr1 enum)
[5..6] gain × 60 (i16 BE, dB)
```

The filter enum is larger than the SPV one: 0 bypass, 1 low-pass 1, 2
high-pass 1, 3 all-pass 1, 4 low-shelf 1, 5 high-shelf 1, 6 tilt 1, 7 low-pass
2, 8 high-pass 2, 9 all-pass 2, 10 low-shelf 2, 11 high-shelf 2, 12 tilt 2,
13 parametric EQ.

The old ST1 probe sent `0a 05 00 05` (start 0, end 5). That is out of range
for a 5-band EQ; the correct query is `0a 05 00 04`. The old reply was
interpreted as "all zeros", but the new parser shows it is
`start=0, end=1, one zeroed 7-byte band`. The re-probe with `0a 05 00 04`
(2026-10-17) returned exactly the same, so the ST1 user EQ read is a stub.

Preset ids: the app's `sw3` enum has `0 = OFF`, `63 = USER`, everything else
`PRE_SET`. The ST1 firmware reports presets 0, 1, 2 and 63, and the Link UI
names 0/1/2 Reference/Basshead/Monitor, so the device-reported list and the
UI names remain the source of truth. Do not assume 0 means "off" on every
model.

## Earbud (1)

Notifications/response commands: 0 (earbud position / fit state), 1 (default
actions), 2 (current actions; notification means changed). The action payload
is model-specific; this is separate from TouchV2/V3/V4.

## ANC (2, legacy)

App evidence: `V3ANCPlugin` (`me5`). The plugin handles notifications 0..4
and responses 1, 3, 4 and 6. The exact request ids are not reconstructed; the
old Reported table from other projects says get command 1, set command 2
(`04 01` / `04 02`). Treat that as **Reported** until a capture.

## Voice UI (3)

`V3VoiceUiPlugin` (`cf5` variant 2): notification/response command 0 is the
voice-UI state; response command 2 is the available voice list. No command
payload details are used by this app.

## Upgrade (6)

GAIA OTA. Link's vendor OTA paths disconnect GAIA before handing the device to
Airoha/Jieli/Bluetrum tools; see [../devices.md](../devices.md). The GAIA
upgrade plugin itself is not documented here.

## Handset service (7)

`V3HandsetServicePlugin`: notification command 0 reports the multipoint type.
No get/set used by this app.

## Audio curation (8)

`V3AudioCurationPlugin` (`qe5`) is a large Qualcomm feature (adaptive ANC,
noise ID, wind detection, etc.) with many commands. Link uses a subset
through `x0` enums. It is not advertised by ST1 and is not analysed here.

## Earbud fit (9)

`V3EarbudFitPlugin`: command 0 is the fit test (notification and response,
two bytes in the notification). Link only shows the fit result.

## Voice processing (10)

`V3VoiceProcessingPlugin`: reports supported enhancements and their state.
Not used by any of our devices yet.

## Gesture configuration (11) / Statistics (12)

Present as plugins but not analysed; no Moondrop model in our set advertises
them.

## Battery (13)

App evidence: `BatteryPlugin` (`ve5`), request builder `rc2`. Wire: ST1 has
no battery over GAIA.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 0 | get / notif | none / list | which batteries exist |
| 1 | get | list of battery ids | levels; reply is `[id, level]` pairs |
| 0, 1 | notif | same as replies | changed |

Battery ids are confirmed by the app: **0 single device, 1 left, 2 right,
3 charging case**. On models that report it, this gives separate L/R/case
levels.

## Voice prompts (14)

App evidence: `V3VoicePlugin` (`hg5`), setter `ti4`.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | get | none | voice index and configuration |
| 2 | set | `[1, index]` or 3 bytes | select voice / V2 configuration |

With plugin version >= 2 the response is 3 bytes; older firmware returns 1–2
bytes (index only). The app's 3-byte form is also used for the language /
prompt configuration; the exact field order is not decoded here.

## DAC gain (15)

`V3DacGainPlugin` (`gf5`): get 1 returns the gain index (one byte); set 2
writes it. This is the BT-side DAC gain index, not the USB DAC volume.

## Codec type (16)

`V3CodecPlugin` (`ye5`): one-byte 0/1 states.

| Codec | Get | Set |
|---|---|---|
| LC3 | 1 | 3 |
| LDAC | 2 | 4 |
| LHDC | 5 | 6 |

## Light/wear sensor (17)

`V3LightSensorPlugin` (`wf5`): get 1, set 2, one byte 0/1. The app calls it
`SENSOR_STATE`; models use it for wear detection.

## Spatial audio (18)

`V3SpatialAudioPlugin` (`yf5`): get 1 returns enabled, get 3 returns head
tracking; set 2 writes enabled, set 4 writes head tracking. One byte each.

## LED (19)

`V3LedPlugin` (`if5`): get 1, set 2, one byte 0/1.

## One brings two / multipoint (20)

`V3OneBringTwoPlugin` (`nf5`), setters `rc2`/`pu0`.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | get / notif | 1 byte | enabled state |
| 2 | set | 1 byte | enable/disable |
| 3 | get / notif | 1 byte | timeout |
| 4 | set | 1 byte | set timeout |
| 5 | get / notif | device entry | current device / link |
| 6 | get / notif | device entry | next device / link |
| 7 | set | device entry | connect/disconnect a device |

A device entry (`ja1`) is `[1 byte type][6 bytes][UTF-8 name]`. The 6-byte
field is probably the address; the app only displays the name.

## Bluetooth address (21)

`V3BtAddressPlugin` (`we5`): get 1 returns the raw 6-byte address; set 2
writes it. ST1 reports `ABCDEF0123456789` as its *serial*, not this feature.

## Touch controls v2 (22)

`V3TouchV2Plugin` (`ag5`): get 1 current actions, get 2 default actions,
set 3. Payload is 5 bytes = 10 nibbles, high nibble first, one action per
gesture slot. The app's action names array is: play/pause, previous, next,
volume up, volume down, answer/hang up, reject call, start voice assistant,
cancel voice assistant, ANC on, ANC off.

## Audio resource (23)

`V3AudioResourcePlugin` (`se5`): get 1 returns the index; set 2 writes it.
Index 0 is the Bluetooth resource (the app logs `setAudioResource-BT(0)`);
`-1` is used as "no resource" in the Dart state.

## Power control (24)

`V3PowerControlPlugin` (`sf5`): set 1 with 0/1 (the app sends command 1 to
power off). There is no getter. This is separate from the timeout feature.

## Power/standby timeout (25)

`V3PowerTimeoutPlugin` (`uf5`), setter `pu0`.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | get | none | power-off setting |
| 2 | set | `[enabled, minutes]` | power-off setting |
| 3 | get | none | standby setting |
| 4 | set | `[enabled, minutes]` | standby setting |

## Touch controls v3 (26)

`V3TouchV3Plugin` (`cg5`): get 1 default actions, get 2 current actions,
set 3. Payload is **7 bytes = 14 nibbles** (high nibble first), unlike v2/v4.
The app checks feature id `0x1A` in the supported-feature list and emits
`syncTouchV3FeatureMissing` if the firmware did not enumerate it. This is the
command set the old notes called "different structure; not analysed".

## Dynamic bass (27)

`V3DybassPlugin` (`bf5`): get 1, set 2, one byte 0/1; notifications 1 and 2
also carry the state.

## Audio file storage (29)

`V3AudioFileStoragePlugin` (`re5`), command enum `po`. This is a file/playback
feature, not EQ. Exact command ids:

| Cmd | Type | Name |
|---|---|---|
| 1 | set | START_FILE_UPLOAD |
| 2 | set | UPLOAD_FILE_CHUNK |
| 3 | set | END_FILE_UPLOAD |
| 4 | get | GET_UPLOAD_STATE |
| 5 | set | ABORT_FILE_UPLOAD |
| 16 | get | GET_FILE_LIST |
| 17 | get | GET_FILE_INFO |
| 32 | set | DELETE_FILE |
| 33 | set | FORMAT_STORAGE |
| 48 | set | PLAY_FILE |
| 49 | set | STOP_PLAYBACK |
| 50 | get | GET_PLAYBACK_STATUS |
| 51 | set | NEXT_TRACK |
| 52 | set | PREVIOUS_TRACK |
| 53 | set | PAUSE_PLAYBACK |
| 54 | set | SET_PLAYBACK_POSITION |
| 55 | set | PLAYBACK_SWITCH |
| 56 | set | SET_VOLUME |
| 57 | get | GET_VOLUME |
| 64 | set | SET_FILE_LIST_ORDER |
| 65 | get | GET_FILE_LIST_ORDER |
| 66 | set | SET_PLAYBACK_TIME |
| 67 | get | GET_PLAYBACK_TIME |

The app sends these with long timeouts (20 s) and handles them as
notifications/responses through the same command id. Payload layouts were not
needed for OpenDrop and are not documented.

## L/R channel swap (30)

`V3LrChannelPlugin` (`jf5`): get 1, set 2, one byte 0/1.

## Touch controls v4 (31)

`V3TouchV4Plugin` (`eg5`).

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | get | `[0xFF]` | knock state; reply one byte 0/1 |
| 2 | set | 1 byte 0/1 | knock state |
| 3 | get | none | default actions |
| 4 | get | none | current actions |
| 5 | set | 5 bytes | set actions |

The action payload is 5 bytes = 10 nibbles, same layout as TouchV2. The app
validates action values 0..7 in this path.

## ANC v2 (32)

`V3AncV2Plugin` (`ne5`). This is the "40 03 / 40 04" family from the old
Reported notes.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | notif | 1 byte | mode changed |
| 2 | notif | 5 bytes | switch configuration changed |
| 3 | get | none | current mode |
| 4 | set | 1 byte | set mode |
| 41 | get | none | current switch configuration |
| 42 | set | 5 bytes | set switch configuration |

The mode range is model-dependent; ST1 never replies because it does not
advertise feature 32.

## ANC v3 (33)

`V3AncV3Plugin` (`oe5`), setters `td`. This is the family the UI exposes for
newer models.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | notif | 1 byte | mode changed (0..4) |
| 2 | notif | 5 bytes | switch configuration changed |
| 3 | get | none | mode |
| 4 | set | 1 byte 0..4 | mode |
| 5 | get | none | anti-wind |
| 6 | set | 1 byte 0/1 | anti-wind |
| 41 | get | none | switch configuration |
| 42 | set | 5 bytes | switch configuration |

Switch configuration is `[state, ancOn, ancOff, tp, order]`; the first four
are 0, 1 or `0xFF`, `order` is 0..5 or `0xFF`. The UI mode names from Link are
off, ANC, transparency, anti-wind and adaptive (the exact id-to-name mapping
is model/UI dependent).

## Find my earbuds (34)

`V3FindEarbudPlugin` (`ff5`): set 1 with the target (0 left, 1 right, 2 both)
starts the find tone; set 2 stops it. There is no getter.

## Dual-mic ENC (35)

`V3DualMicEncPlugin` (`af5`), setter `ua1`.

| Cmd | Dir | Payload | Meaning |
|---|---|---|---|
| 1 | get | none | state (one byte 0/1) |
| 2 | set | 1 byte 0/1 | enable/disable |

This feature is **App** only and was not in the old notes. The UI string is
"dual-microphone ENC".

## Cross-check with the Space Travel

The ST1 advertises `0, 1, 3, 5, 6`; this is exactly what the old capture and
probe found. Consequences:

- Basic: commands 0, 1, 3, 4, 5, 7 are usable; 12/13 only if the Basic plugin
  version is >= 2 (the ST1 report says Basic v2, so it may support them).
- Music processing: commands 0, 1, 2, 3, 4, 5 work; 6/7/8 are unverified.
- Voice UI: notification 0 at connect.
- No ANC, battery, touch, codec, spatial, LED or file-storage replies.
- The EQ preset ids 0/1/2 are Reference/Basshead/Monitor in Link. The hidden
  preset 63 exists.
- User EQ: `0a 05 00 04` returns one all-zero band, not five (stub).

## How to verify

1. Record an HCI snoop log while using the setting in Link
   ([capture guide](capture-guide.md)); redact Bluetooth addresses.
2. Decode with `python tools/gaia_decode.py <capture.csv>`.
3. Mark the row **Wire**, add the capture as a fixture, and only then
   implement a write in `GaiaSession`.
4. For models that do not advertise a feature, the app/plugin must not send
   it; ST1 proves that a device can advertise a feature set much smaller than
   the app's UI.
