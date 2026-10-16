# OpenDrop Research

One place for how OpenDrop learned what Moondrop devices can do, what it
found, and what is still unknown. The detail lives in the linked documents;
this file is the map and the scoreboard.

> OpenDrop is not affiliated with or endorsed by Moondrop. Facts from other
> projects (several are GPL/AGPL) are used as facts only; no code is copied.

Last updated: 2026-10-13, at v0.5.1.

## Contents

1. [Summary](#summary)
2. [Methodology](#methodology)
3. [Results](#results)
4. [Control matrix](#control-matrix)
5. [Open questions](#open-questions)
6. [Contributing data](#contributing-data)
7. [Where everything lives](#where-everything-lives)

## Summary

- **One protocol covers every Bluetooth model.** MOONDROP Link talks GAIA v3
  (vendor `0x001D`) over Bluetooth Classic SPP/RFCOMM to all of the 51
  Bluetooth models in its catalogue, whatever the chip (Bluetrum, Airoha,
  Jieli or Qualcomm). The chip SDKs are mostly used for firmware updates.
- **Devices report their own features.** The GAIA "get supported features"
  reply says what a unit can do, so OpenDrop shows only that.
- **The Space Travel is a small device.** It advertises only core, earbud,
  voice UI, music processing (EQ) and upgrade. That is why it has EQ presets
  and a firmware version, and no ANC, gestures or per-bud battery in any app.
- **Only the Space Travel has been measured.** Everything else is static
  analysis of the Link app and is marked unverified until a capture confirms it.
- **USB is a separate unknown.** Five USB chip families exist. They have been
  catalogued and a read-only USB report exists, but no USB protocol is decoded.

## Methodology

The aim is to know a command's bytes and meaning before OpenDrop sends it
(roadmap principle 2: never brick a device, unknown commands are tried
read-only first).

### Evidence ladder

Every claim carries one of these tags in the protocol docs:

| Tag | Meaning | May OpenDrop send it? |
|---|---|---|
| **Verified** | Seen on the wire in our own capture or probe, or confirmed in the app | Yes |
| **Inferred** | Consistent with a capture and with Qualcomm GAIA v3, not directly confirmed | Read-only only |
| **Reported** | From another project or from app analysis; not seen by us | No, needs a capture first |

### Techniques

1. **HCI snoop capture of MOONDROP Link.** Turn on Android's Bluetooth HCI
   snoop log, drive Link through a timed script of actions (open the device,
   change EQ, long-press, tap, put buds in the case), pull the log over `adb`
   and extract only the earbud's control traffic. Actions are timestamped and
   matched to frames afterwards. Raw logs are never published, because they
   include other devices and addresses. Full procedure:
   [capture-guide.md](protocol/capture-guide.md).
2. **Decoding.** `tools/gaia_decode.py` parses the extracted CSV into GAIA
   frames. Framing is `FF | version | flags | length | vendor | command |
   payload`, with `command = (feature << 9) | (type << 7) | cmd`.
   Frames can be packed several to a packet, so the parser works on a byte
   stream. `tools/gaia.py` holds the shared codec and `tools/test_gaia.py`
   tests it.
3. **Read-only probe from a PC.** `tools/gaia_probe.py` connects over RFCOMM
   and sends only "get" queries or commands Link itself sends on connect. It
   then monitors notifications while a person long-presses, taps and moves
   the buds in and out of the case. Its one write is the opt-in
   `--try-user-eq`, which selects preset 63 for 15 s and restores the previous
   preset.
4. **Negative testing.** Absence is a result. If a physical action produces no
   GAIA or HFP traffic, no app can see or change it. We recorded this for ANC
   and game mode.
5. **Static analysis of MOONDROP Link 2.26.1c.** The Android app's
   Java/Kotlin layer gave the GAIA feature ids, the Moondrop feature list
   (13 to 35), command ids and payload sizes, and the five USB families. Link
   is a Flutter app and the device I/O is native, so some payload meanings
   are not recoverable this way.
6. **Catalogue download.** Link's model list comes from Moondrop's API (107
   products, fetched 2026-10-08). It gives each model's connection type, chip
   type and EQ band count. A device's GAIA variant name matches the catalogue
   `model` string, which is how OpenDrop identifies units.
7. **Other projects' documented facts.** Gadgetbridge's Moondrop support,
   HyperPods-for-Moondrop and moondrop-control, used as leads and cross-checks
   only (see [ROADMAP.md](ROADMAP.md#references)).
8. **Replay in unit tests.** Captured byte traces are test fixtures for
   `core/protocol`, so protocol code is tested without hardware.
9. **App as a probe.** OpenDrop's own "Share device report" exports model,
   firmware, features and packet log, and the USB report exports descriptors.
   Both let owners of other models contribute without a PC.

### Test setups

| What | Hardware |
|---|---|
| First captures (2026-10-07) | Space Travel firmware 1.0.0, Galaxy Note 20 Ultra, Android 13, Link 2.26.1c |
| PC probe | Windows PC, Python 3.10+, RFCOMM channel 1 |
| App hardware test pass (2026-10-08) | Space Travel, Galaxy S24 Ultra, Android 16 |

Only one earbud model and two phones. That is the project's main limit.

## Results

### Space Travel (original), verified

Full detail: [space-travel.md](protocol/space-travel.md).

| Finding | Result |
|---|---|
| Transport | Bluetooth Classic only (SPP, HFP, A2DP sink, AVRCP), no BLE; RFCOMM channel 1 |
| Control protocol | GAIA v3, vendor `0x001D`; handshake starts with a legacy v2 "get API version" (protocol 4, API 3.1) |
| Chip | Bluetrum BT8892E (from a third-party teardown) |
| Features advertised | Core v2, earbud v1, voice UI v1, music processing v1, upgrade v2 |
| Identity | Variant name `Moondrop Space Travel`; firmware `1.0.0`; the serial is a placeholder |
| EQ | Presets 0 Reference, 1 Basshead, 2 Monitor; `set` returns status `01` and then a notification, which is the source of truth |
| Battery | Not GAIA. HFP `AT+IPHONEACCEV`, one value in 10 % steps, earbuds only, no case |
| Volume | Plain AVRCP absolute volume, not GAIA |
| ANC, game mode | No GAIA or HFP traffic at all when toggled. Invisible to apps |
| Gestures, touch lock | No feature advertised, no screen in Link |
| Hidden user EQ | Preset 63 is listed, 5 bands, but the config reads all zeros (likely an unimplemented stub) |
| Connect sequence | Reproduced exactly by OpenDrop (see the 2026-10-08 report in `captures/`) |

### Moondrop GAIA features 13 to 35, unverified

Command tables from app analysis:
[moondrop-gaia-features.md](protocol/moondrop-gaia-features.md).

| Known in part | Only ids and sizes known |
|---|---|
| Battery (13), ANC v3 (33), touch controls v2 (22) and v4 (31), find my earbuds (34) | Voice prompts, DAC gain, codec, wear sensor, spatial audio, LED, multipoint, prompt sounds, auto power-off, dynamic bass, L/R swap, user EQ writes |

Not analysed yet: ANC (2), ANC v2 (32), touch controls v3 (26).

For touch controls the gesture slot order and action codes are unknown. For
ANC v3 the 5-byte switch configuration layout is unknown. The Moondrop
features 13 to 35 are not advertised by the Space Travel and cannot be checked
on our unit.

### Device catalogue

Full list: [devices.md](devices.md).

| Connection | Count | Chip families |
|---|---|---|
| Bluetooth (GAIA) | 51 | Bluetrum 44 (one is the USB-OTA variant), Qualcomm 4, Jieli 2, Airoha 1 |
| USB | 34 | SPV 14, Synopsys/Synaptics 6, Comtrue 5, Jiu 5, Jieli USB 4 |
| Wired (no electronics) | 22 | n/a |

The counts in `devices.md` are the source of truth; chip names there follow
Link's spelling.

### USB, catalogued but not decoded

The five families and the controls Link exposes for each are in
[devices.md](devices.md#what-moondrop-link-2261c-contains). The Android USB
host transport and a read-only USB report are built. They send only standard
descriptor requests and never claim an interface, so audio keeps playing. No
USB report has come from a real unit yet.

### Product decisions that came out of the research

- ANC and game mode control for the Space Travel are "won't do". The
  firmware doesn't allow them.
- Battery on the Space Travel comes from Android's HFP level. There is no
  case or per-bud battery.
- EQ switching is offered only where preset names are known (the Space Travel).
  Other models show the preset id.
- Unknown models are strictly read-only.
- Phone-side parametric EQ with AutoEQ import gives custom EQ to any
  headphones, since on-device user EQ isn't usable on the Space Travel.

## Control matrix

What can be done, by what it is waiting on. "Untested" means the code exists
but has never run on a phone with the device.

| Control | Space Travel | Other Bluetooth models | Blocked on |
|---|---|---|---|
| Connect, reconnect, auto-connect | Works (hardware-tested) | Generic driver, untested | None |
| Firmware version, device info | Works | Works in code | None |
| EQ preset switching | Works | Needs preset names per model | Fetch Link's preset names (`/api/v1/ota-config-equalizers/all`) |
| Battery | Works, single HFP level | Feature 13 would give L / R / case | Capture from a model that has it |
| Volume | Works (Android media volume) | Same | None |
| Quick Settings tile, widgets, low-battery alert, automation intents | Built, untested | Same | A hardware test pass |
| Phone-side parametric EQ, AutoEQ | Built, untested | Any headphones | A hardware test pass; some phones block effects on the output mix |
| ANC and transparency modes | **Not possible** | ANC v3 (33) if the model has it | Capture |
| Game mode | **Not possible** | Unknown | Capture, if any model exposes it |
| Touch controls, gestures | **Not possible** (no feature) | Features 22 / 26 / 31 | Capture and slot / action mapping |
| Touch lock | **Not possible** | Unknown, with touch controls | Capture |
| Find my earbuds | **Not possible** | Feature 34 | Capture |
| Codec, LED, wear sensor, multipoint, spatial audio, dynamic bass, L/R swap, auto power-off, voice prompts | **Not possible** | Features 14 to 35 | Capture per feature |
| On-device user EQ | Probably not possible (stub) | Where the firmware supports it | `--try-user-eq`, then a capture |
| Bluetooth codec switch | Android-side | Android-side | Shizuku or hidden APIs, needs a real phone; Developer options link works today |
| USB DAC and DSP controls | n/a | Five USB families | USB report plus a capture per family |
| Firmware updates | **Out of scope** | **Out of scope** | Bricking risk |

"Not possible" for the Space Travel means its firmware does not advertise or
expose the control. It does not mean the app is missing it.

## Open questions

Ordered roughly by cost, cheapest first.

- [ ] Run `gaia_probe.py --try-user-eq` and check whether preset 63 selects
      and changes the sound. This settles on-device custom EQ for the Space Travel.
- [ ] Meaning of core commands `0x13` to `0x16`, EQ notification `0a80`
      and the `01` in the set-EQ response.
- [ ] Fetch and record per-model EQ preset names from Link's API.
- [ ] Touch actions on the Space Travel: which command Gadgetbridge sends,
      if it is one at all.
- [ ] Hardware test pass for M3 (tile, widgets, alerts, intents), M4 (phone
      EQ) and the onboarding flow.
- [ ] Device reports from owners of other models. Each one turns a guessed
      feature list into a measured one.
- [ ] Captures for battery (13), ANC v3 (33) and touch controls (22 / 31)
      from a model that has them.
- [ ] Analyse ANC (2), ANC v2 (32) and touch controls v3 (26).
- [ ] USB reports and captures: SPV first, since it covers the most models.
- [ ] Whether any model needs BLE GATT on Android.

Related lists: [space-travel.md](protocol/space-travel.md#open-questions),
[ROADMAP.md](ROADMAP.md).

## Contributing data

| You have | Send |
|---|---|
| Any Moondrop Bluetooth model, with OpenDrop installed | Device info, then Share device report |
| A model with ANC, touch controls or a battery feature, and a PC | A capture following [capture-guide.md](protocol/capture-guide.md), doing the feature's action in Link |
| A USB DAC or DSP cable | OpenDrop's USB report, then a Link capture while changing a setting |

Never upload a raw HCI log. Extract the earbud traffic and redact the Bluetooth
address first.

## Where everything lives

| Path | What |
|---|---|
| `docs/protocol/space-travel.md` | Verified protocol notes, connect sequence, open questions |
| `docs/protocol/moondrop-gaia-features.md` | Unverified command tables for features 13 to 35 |
| `docs/protocol/capture-guide.md` | How to capture and probe |
| `docs/protocol/captures/` | Extracted captures, probe output, timed action notes, OpenDrop device report |
| `docs/devices.md` | Catalogue of 107 products, Link analysis, app support plan |
| `docs/ROADMAP.md` | Milestones, status, principles, risks, references |
| `tools/gaia.py`, `gaia_decode.py`, `gaia_probe.py`, `test_gaia.py` | Codec, decoder, probe, tests |
| `core/protocol/src/test/` | Unit tests that replay captured traces |
