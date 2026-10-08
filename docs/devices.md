# Device compatibility

Which Moondrop devices OpenDrop could support, and what each one needs.

Source: the "supported devices" list in MOONDROP Link (Android), copied by
hand from memory, so some names may be slightly off. Link supports wireless
earbuds, wireless headphones, wireless speakers, USB earbuds (IEMs with a
built-in DSP) and USB dongle DACs.

## Why the names matter

Link shows the Space Travel as `Moondrop Space Travel`, which is exactly the
GAIA **variant name** the earbuds report ([protocol notes](protocol/space-travel.md)).
So the names below are probably the identity strings Link matches against,
not marketing names. That makes them a good key for device detection. The
odd spellings (`ZZZ-ANGELS-OWS`, `H.I.D.E.404_Klukai`, `SUSANOO TWS IOS`)
point the same way. The separate `IOS` entry suggests that some models
connect differently on iOS, most likely over BLE.

## What MOONDROP Link 2.26.1c contains

Static analysis of the Android app's Java/Kotlin layer (Link is a Flutter
app; the native side does the device I/O). Facts only, no code copied.

**Bluetooth: one main protocol, GAIA v3, vendor `0x001D`.** Link's GAIA client
knows these feature ids. 0 to 12 are Qualcomm's standard ones; **13 to 35 are
Moondrop additions** under the same vendor id:

| Id | Feature | Id | Feature |
|---|---|---|---|
| 0 | Basic (core) | 18 | Spatial audio (+ head tracking) |
| 1 | Earbud | 19 | LED |
| 2 | ANC | 20 | "One brings two" (multipoint) |
| 3 | Voice UI | 21 | BT address |
| 4 | Debug | 22 | Touch controls v2 |
| 5 | Music processing (EQ) | 23 | Audio resource (prompt sounds) |
| 6 | Upgrade | 24 | Power control |
| 7 | Handset service | 25 | Power-off / standby timeout |
| 8 | Audio curation | 26 | Touch controls v3 |
| 9 | Earbud fit | 27 | Dynamic bass |
| 10 | Voice processing | 29 | Audio file storage |
| 11 | Gesture configuration | 30 | L/R channel swap |
| 12 | Statistics | 31 | Touch controls v4 (incl. knock) |
| 13 | Battery | 32 | ANC v2 |
| 14 | Voice prompts (language, index) | 33 | ANC v3 (modes, anti-wind) |
| 15 | DAC gain | 34 | Find my earbuds |
| 16 | Codec (LDAC / LHDC state) | 35 | Dual-mic ENC |
| 17 | Light / wear sensor | | |

The Space Travel advertises only 0, 1, 3, 5 and 6, which matches what we
measured: it has no battery, ANC or touch feature. Newer models advertise more
of these, and because the device lists its own features, **one GAIA driver
covers every model that speaks it**; we add features one at a time.

The Space Travel runs on a Bluetrum chip yet speaks GAIA, and Link sends
EQ gains for Bluetrum devices through its GAIA music-processing plugin. So
GAIA is Moondrop's common protocol across chip vendors, not a Qualcomm-only one.

**GAIA is the control channel for every Bluetooth chip family.** Link's Dart
code talks of the "normal connection" for Airoha, Bluetrum and Jieli devices
as a GAIA connection, and disconnects GAIA before handing the device to each
vendor's own firmware-update tool. The chip SDKs are mostly for firmware
updates:

| Chip type (Link's names) | Beyond GAIA, Link uses | Transport |
|---|---|---|
| `qualcomm` | Nothing extra | SPP |
| `bluetrum`, `bluetrum10`, `bluetrumusb` | Firmware update (FOTA over SPP, USB HID OTA) | SPP, BLE service `0xAE00`, USB HID |
| `airoha` (AB1562, AB1562E, AB1565, AB1568, incl. dual/V3 variants) | Airoha SDK: firmware update, and PEQ and ANC on some models | SPP |
| `jieli`, `jieliusb` | Firmware update (RCSP OTA, USB OTA) | SPP / USB |
| `actions`, `synaptics` | Listed as chip types; no Bluetooth code seen | ? |

**USB devices: three protocol families.** All on Android USB host:

| Family | Link's controls |
|---|---|
| Comtrue | Filter, gain level, LED, volume |
| "SPV" | PEQ (with presets, pre-gain), CS43131 filter and working mode, DAC/ADC gain, LED, mic gain, L/R swap, firmware version; also ANC, battery, buttons and spatial audio for products that have them |
| Synaptics / Conexant | EQ presets and PEQ saved to flash, firmware version |

Plus a fourth, smaller "Jiu" EQ path and a factory SPP tool (serial numbers).

**Which model uses which chip comes from Moondrop's server.** Only a few model
names are built into the app (`ZZZ-ANGELS-OWS`, `ECHO-BP`, `MOONDROP MM3A`,
`Moondrop U.C.T.S.`, `MOONDROP Marigold`, `MOONDROP Rays`, the four `Pill`
editions, `PANDAER Open Air Pill`), for special cases. The product catalogue,
with fields such as `chipType`, `connectionType` and `deviceFuncList`, is
downloaded from `https://cdn-service.moondroplab.tech/api/v1/products/all`.
For Bluetooth models the GAIA feature list matters more anyway: the device
reports it itself.

## Device list

Category: **Known** = confirmed by a product page or review, **Guess** = from
the name or memory, needs checking, **?** = no idea yet.

| Link name | Category | Confidence | Protocol |
|---|---|---|---|
| Moondrop Space Travel | TWS earbuds | Known | **GAIA v3 over SPP (verified)** |
| PANDAER Space Travel 2 | TWS earbuds | Known | ? |
| Space Travel 2 Ultra | TWS earbuds (BT 6.0, LDAC) | Known | ? |
| MOONDROP MOON TRAVEL | TWS earbuds | Guess | ? |
| Space Force | TWS earbuds | Guess | ? |
| Moondrop Golden Ages | TWS earbuds | Guess | ? |
| Moondrop Golden Ages 2 | TWS earbuds | Guess | ? |
| Moondrop Nekocake | TWS earbuds | Known | ? |
| Moondrop Nekocake Acht Acht Limited | TWS earbuds (Nekocake edition) | Guess | ? |
| Moondrop Nekocake QBZ-191 | TWS earbuds (Nekocake edition) | Guess | ? |
| MOONDROP MOCA | TWS earbuds (BT 5.4, ANC, game mode) | Known | ? |
| Me 163 Komet MOCA | TWS earbuds (MOCA edition) | Guess | ? |
| Moondrop Sparks | TWS earbuds | Guess | ? |
| Moondrop Alice | TWS earbuds | Guess | ? |
| MOONDROP Ultrasonic | TWS earbuds | Guess | ? |
| MOONDROP MIRAGE | TWS earbuds | Guess | ? |
| MOONDROP Pudding | TWS earbuds | Guess | ? |
| MOONDROP Rays | TWS earbuds | Guess | ? |
| MOONDROP X AG Rays | TWS earbuds (Rays edition) | Guess | ? |
| SUSANOO TWS | TWS earbuds | Known (name) | ? |
| Moondrop SUSANOO TWS IOS | Same earbuds, iOS entry | Guess | ? (likely BLE) |
| ZZZ-ANGELS-OWS | Open-ear earbuds (OWS) | Guess | ? |
| PANDAER Open Air Pill | Open-ear earbuds | Guess | ? |
| Pill Yamada Ryo | PANDAER Pill edition; maybe the speaker set | ? | ? |
| Pill Kita Ikuyo | PANDAER Pill edition; maybe the speaker set | ? | ? |
| Pill Ijichi Nijika | PANDAER Pill edition; maybe the speaker set | ? | ? |
| Pill Gotoh Hitori | PANDAER Pill edition; maybe the speaker set | ? | ? |
| H.I.D.E.404_Klukai | Collab edition, probably TWS | Guess | ? |
| PUNISHING:GRAY RAVEN | Collab edition, probably TWS | Guess | ? |
| BIANCA:STIGMATA | Collab edition, probably TWS | Guess | ? |
| ROBIN'S Earphones | Collab edition, probably TWS | Guess | ? |
| MOONDROP x YASUNO KIYONO | Collab edition | ? | ? |
| MOONDROP EDGE | Wireless ANC headphones | Known | ? |
| MOONDROP EDGE 2 | Wireless ANC headphones (BT 6.0, LDAC, LHDC) | Known | ? |
| SINGER HEADPHONE | Headphones | Guess | ? |
| MOONDROP Voyager | Bluetooth neckband | Known | ? |
| DUSK-SP | Speaker? | ? | ? |
| MOONDROP DAWN 3.5 | USB dongle DAC | Known | ? |
| MOONDROP DAWN 4.4 | USB dongle DAC | Known | ? |
| MOONDROP DAWN PRO | USB dongle DAC | Known | ? |
| DAWN PRO2 | USB dongle DAC | Guess | ? |
| MOONDROP Moonriver2 Ti | USB DAC | Known | ? |
| MOONRIVER 3 | USB DAC | Guess | ? |
| FreeDSP Mini | USB DSP cable | Known | ? |
| FreeDSP Pro | USB DSP cable | Known | ? |
| MOONDROP CDSP | USB DSP cable | Guess | ? |
| ECHO-B | USB-C DSP dongle (app DSP) | Known | ? |
| Echo-BP | Probably an Echo-B variant | Guess | ? |
| CHU2 DSP | USB-C IEM with DSP | Known | ? |
| MAY | USB-C IEM with DSP | Guess | ? |
| Starlight | USB-C IEM with DSP (Japan only, EQ in Link) | Known | ? |
| MOONDROP little white | ? | ? | ? |
| MOONDROP Click | ? | ? | ? |
| MOONDROP MM3A | ? | ? | ? |
| Moondrop DHA15 | ? | ? | ? |
| LAPLACE-OBA-II | ? | ? | ? |
| Moondrop U.C.T.S. | ? | ? | ? |
| Moondrop Old Fashioned | ? | ? | ? |
| MOONDROP Marigold | ? | ? | ? |

Rough count: 37 Bluetooth earbuds, headphones, neckbands and speakers, 14 USB
devices and 8 unknown.

## How to fill in the Protocol column

1. **Link's product catalogue** (see above): chip and connection type per model.
2. **Prior art.** Gadgetbridge supports some Moondrop models over GAIA. Check
   which ones and credit them (facts only, no code; see the legal note in the
   protocol notes).
3. **Community captures.** Owners of other models record an HCI snoop log
   while using Link ([capture guide](protocol/capture-guide.md)), or a USB
   capture for DACs. The GAIA features list alone (one read-only command)
   already tells us most of what a model supports.

## How the app will use this

- Detect the model by its GAIA variant name, falling back to the Bluetooth
  name, then pick a driver.
- One generic GAIA v3 driver that builds the UI from the features the device
  reports, with small per-model overrides (like the Space Travel EQ pop warning).
  Moondrop features (13 to 35) are added one at a time as we confirm their
  commands with captures.
- Airoha's own SDK protocol only if an Airoha model turns out to need it for
  something GAIA doesn't offer (firmware updates stay out of scope).
- Unknown devices connect in a read-only experimental mode with the packet
  log, so owners can send us captures.
- USB devices get their own transport and driver, later (roadmap "Later").
