# Device compatibility

Which Moondrop devices OpenDrop could support, and what each one needs.

Sources: MOONDROP Link 2.26.1c for Android (static analysis, facts only) and
its product catalogue, downloaded from Moondrop's API on 2026-10-08.

Chip types in the catalogue:

- `airoha`: 1 Bluetooth
- `bluetrum`: 43 Bluetooth
- `bluetrumusb`: 1 Bluetooth
- `jieli`: 2 Bluetooth
- `qualcomm`: 4 Bluetooth
- `comture`: 5 USB
- `jieli`: 1 USB
- `jieliusb`: 3 USB
- `jiu`: 5 USB
- `spv`: 14 USB
- `synopsys`: 6 USB
- `synopsys`: 22 WIRED

## How a device is identified

Each catalogue entry has a `model` string. For the Space Travel it is
`Moondrop Space Travel`, exactly the GAIA **variant name** the earbuds report
([protocol notes](protocol/space-travel.md)). So OpenDrop can identify a
connected model by its variant name and look it up in the table below.

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

**USB devices: five protocol families.** All on Android USB host:

| Chip type | Link's controls | Models |
|---|---|---|
| `comture` (Comtrue) | Filter, gain level, LED, volume | Dawn 3.5 / 4.4 / Pro, Moonriver 2 Ti, Discdream |
| `spv` | PEQ (presets, pre-gain), CS43131 filter and working mode, DAC/ADC gain, LED, mic gain, L/R swap, firmware version; ANC, battery, buttons and spatial audio where present | Dawn Pro 2, Moonriver 3, FreeDSP Mini / Pro, Rays, Marigold, DHA15, ... |
| `synopsys` (Synaptics / Conexant) | EQ presets and PEQ saved to flash, firmware version | Echo-B, FreeDSP, May, Starlight, Click, Dusk-SP |
| `jiu` | EQ registers | CDSP, CHU2 DSP, JIU, Old Fashioned |
| `jieliusb`, `jieli` | Firmware update seen; EQ path not identified yet | Echo-BP, GM01 Pro, Gaming Maestro, Nicebuds DSP |

A factory SPP tool (serial numbers) is also in the app.

**The model list comes from Moondrop's server**
(`https://cdn-service.moondroplab.tech/api/v1/products/all`), not from the app.
Each entry gives the connection type (`BT`, `USB`, `WIRED`), `chipType`,
the number of EQ bands and a few flags (TWS, speaker, DAC/amp, gaming).

## Device list

From Link's product catalogue (107 entries: 51 Bluetooth, 34 USB, 22 wired). Some models appear more than once, once per app language or with an `IOS` display name; the `model` string is what identifies the device. EQ bands is the catalogue's `eqBands` (the Space Travel's 5 matches the hidden user EQ we probed).

### Bluetooth (GAIA)

| Model | Kind | Chip type | EQ bands |
|---|---|---|---|
| MOONDROP The Garden | TWS earbuds | `airoha` | 10 |
| BIANCA:STIGMATA | Headphones / neckband | `bluetrum` | 5 |
| H.I.D.E.404_Klukai | TWS earbuds | `bluetrum` | 5 |
| LAPLACE-OBA-Ⅱ | TWS earbuds | `bluetrum` | 5 |
| Me 163 Komet MOCA | TWS earbuds | `bluetrum` | 5 |
| MOONDROP EDGE | Headphones / neckband | `bluetrum` | 5 |
| MOONDROP EDGE 2 | Headphones / neckband | `bluetrum` | 5 |
| Moondrop EVO 2 | TWS earbuds | `bluetrum` | 10 |
| MOONDROP EVO 2 | TWS earbuds | `bluetrum` | 10 |
| Moondrop Golden Ages | TWS earbuds | `bluetrum` | 5 |
| Moondrop Golden Ages 2 | TWS earbuds | `bluetrum` | 5 |
| MOONDROP MOCA | TWS earbuds | `bluetrum` | 5 |
| MOONDROP MOON TRAVEL | TWS earbuds | `bluetrum` | 5 |
| Moondrop Nekocake | TWS earbuds | `bluetrum` | 5 |
| Moondrop Nekocake Acht Acht Limited | TWS earbuds | `bluetrum` | 5 |
| Moondrop Nekocake QBZ-191 | TWS earbuds | `bluetrum` | 5 |
| MOONDROP Pill (zh-CN) | TWS earbuds | `bluetrum` | 5 |
| MOONDROP PILL (shown as `PILL`) (ja-JP) | TWS earbuds | `bluetrum` | 5 |
| Moondrop PUNISHING;GRAY RAVEN (shown as `Moondrop PUNISHING;GRAY RAVEN IOS`) | TWS earbuds | `bluetrum` | 5 |
| Moondrop SINGER HEADPHONE (shown as `Moondrop SINGER HEADPHONE IOS`) | Headphones / neckband | `bluetrum` | 5 |
| Moondrop Space Travel | TWS earbuds | `bluetrum` | 5 |
| Moondrop SUSANOO TWS (shown as `Moondrop SUSANOO TWS IOS`) | TWS earbuds | `bluetrum` | 5 |
| Moondrop U.C.T.S. | TWS earbuds | `bluetrum` | 5 |
| MOONDROP Ultrasonic | TWS earbuds | `bluetrum` | 5 |
| MOONDROP x YASUNO KIYONO | TWS earbuds | `bluetrum` | 5 |
| PANDAER Open Air Pill | TWS earbuds | `bluetrum` | 5 |
| PANDAER Space Travel 2 | TWS earbuds | `bluetrum` | 5 |
| Pill Gotoh Hitori | TWS earbuds | `bluetrum` | 5 |
| Pill Ijichi Nijika | TWS earbuds | `bluetrum` | 5 |
| Pill Kita Ikuyo | TWS earbuds | `bluetrum` | 5 |
| Pill Yamada Ryo | TWS earbuds | `bluetrum` | 5 |
| PUNISHING:GRAY RAVEN | TWS earbuds | `bluetrum` | 5 |
| Robin's earphones (ko-KR) | TWS earbuds | `bluetrum` | 5 |
| ROBIN'S Earphones (en-US) | TWS earbuds | `bluetrum` | 5 |
| ROBIN'S EARPHONES (ja-JP) | TWS earbuds | `bluetrum` | 5 |
| Robin's Earphones (zh-CN) | TWS earbuds | `bluetrum` | 5 |
| SINGER HEADPHONE | Headphones / neckband | `bluetrum` | 5 |
| Space Force | TWS earbuds | `bluetrum` | 5 |
| SPACE TRAVEL 2 (ja-JP) | TWS earbuds | `bluetrum` | 5 |
| Space Travel 2 (zh-CN) | TWS earbuds | `bluetrum` | 5 |
| SPACE TRAVEL 2 ULTRA (ja-JP) | TWS earbuds | `bluetrum` | 5 |
| Space Travel 2 Ultra (en-US) | TWS earbuds | `bluetrum` | 5 |
| SUSANOO TWS | TWS earbuds | `bluetrum` | 5 |
| ZZZ-ANGELS-OWS | TWS earbuds | `bluetrum` | 5 |
| MOONDROP MM3A | Speaker | `bluetrumusb` | 8 |
| MOONDROP MIRAGE | TWS earbuds | `jieli` | 10 |
| MOONDROP Pudding | TWS earbuds | `jieli` | 10 |
| Moondrop Alice | TWS earbuds | `qualcomm` | 5 |
| MOONDROP littlewhite | Headphones / neckband | `qualcomm` | 5 |
| Moondrop Sparks | TWS earbuds | `qualcomm` | 5 |
| MOONDROP Voyager | Headphones / neckband | `qualcomm` | 5 |

### USB

| Model | Kind | Chip type | EQ bands |
|---|---|---|---|
| DISCDREAM | DSP IEM / cable | `comture` | 5 |
| MOONDROP DAWN 3.5 | DAC/amp | `comture` | 5 |
| MOONDROP DAWN 4.4 | DAC/amp | `comture` | 5 |
| MOONDROP DAWN PRO | DAC/amp | `comture` | 5 |
| MOONDROP Moonriver2 Ti | DAC/amp | `comture` | 5 |
| MOONDROP Nicebuds DSP | DSP IEM / cable | `jieli` | 10 |
| Echo-BP | DAC/amp | `jieliusb` | 32 |
| MOONDROP Gaming Maestro | Gaming | `jieliusb` | 10 |
| MOONDROP GM01 Pro | Gaming | `jieliusb` | 10 |
| CDSP | DSP IEM / cable | `jiu` | 5 |
| CHU2 DSP | DSP IEM / cable | `jiu` | 5 |
| MOONDROP CDSP | DSP IEM / cable | `jiu` | 5 |
| MOONDROP JIU | DSP IEM / cable | `jiu` | 5 |
| Moondrop Old Fashioned | DSP IEM / cable | `jiu` | 5 |
| DA-016 BLUE ROSE | DSP IEM / cable | `spv` | 8 |
| DAWN PRO2 | DAC/amp | `spv` | 8 |
| ddHiFi DSP IEM - Memory | DSP IEM / cable | `spv` | 8 |
| Deco Audio System | DSP IEM / cable | `spv` | 8 |
| E.S.combo | DSP IEM / cable | `spv` | 8 |
| FreeDSP Mini | DAC/amp | `spv` | 8 |
| FreeDSP Pro | DAC/amp | `spv` | 8 |
| INN Deco75-DH Audio | DSP IEM / cable | `spv` | 8 |
| Moondrop DHA15 | DSP IEM / cable | `spv` | 8 |
| MOONDROP Marigold | DSP IEM / cable | `spv` | 8 |
| MOONDROP Position | Gaming | `spv` | 8 |
| MOONDROP Rays | Gaming | `spv` | 8 |
| MOONDROP X AG Rays | Gaming | `spv` | 8 |
| MOONRIVER 3 | DAC/amp | `spv` | 8 |
| DUSK-SP | DSP IEM / cable | `synopsys` | 9 |
| ECHO-B | DAC/amp | `synopsys` | 9 |
| FreeDSP | DAC/amp | `synopsys` | 9 |
| MAY | DSP IEM / cable | `synopsys` | 9 |
| MOONDROP Click | DAC/amp | `synopsys` | 5 |
| Starlight | DSP IEM / cable | `synopsys` | 9 |

### Wired (no electronics)

Passive IEMs. Link lists them for EQ targets and frequency-response data, applied through a DSP dongle; there is nothing to connect to.

| Model | Kind | Chip type | EQ bands |
|---|---|---|---|
| MOONDROP Aria | Passive IEM | `synopsys` | 5 |
| MOONDROP Aria SE | Passive IEM | `synopsys` | 5 |
| MOONDROP Blessing3 | Passive IEM | `synopsys` | 5 |
| MOONDROP Chu | Passive IEM | `synopsys` | 5 |
| MOONDROP Chu II | Passive IEM | `synopsys` | 5 |
| MOONDROP Dark Saber | Passive IEM | `synopsys` | 5 |
| MOONDROP Illumination | Passive IEM | `synopsys` | 5 |
| MOONDROP Joker | Passive IEM | `synopsys` | 5 |
| MOONDROP Kadenz | Passive IEM | `synopsys` | 5 |
| MOONDROP KATO | Passive IEM | `synopsys` | 5 |
| MOONDROP Lan | Passive IEM | `synopsys` | 5 |
| MOONDROP PARA | Passive IEM | `synopsys` | 5 |
| MOONDROP Quarks | Passive IEM | `synopsys` | 5 |
| MOONDROP Solis | Passive IEM | `synopsys` | 5 |
| MOONDROP Solis II | Passive IEM | `synopsys` | 5 |
| MOONDROP SSP | Passive IEM | `synopsys` | 5 |
| MOONDROP Starfield II | Passive IEM | `synopsys` | 5 |
| MOONDROP Stellaris | Passive IEM | `synopsys` | 5 |
| MOONDROP Variations | Passive IEM | `synopsys` | 5 |
| MOONDROP Venus | Passive IEM | `synopsys` | 5 |
| MOONDROP Void | Passive IEM | `synopsys` | 5 |
| MOONDROP X Threebody Droplet | Passive IEM | `synopsys` | 5 |

## Still unknown

- **Each model's GAIA feature list.** It is reported by the device, so one read-only
  command per model (from a capture or the packet log) tells us what it supports.
- **Command bytes for Moondrop features 13 to 35.** To be read from the
  decompiled app and confirmed with captures before OpenDrop sends them.
- **USB protocols.** Five families, none decoded yet.

## How the app uses this

Done (`core/protocol`: `GaiaSession`, `MoondropModels`, `GaiaFeature`):

- One GAIA v3 session for every Bluetooth model. After Link's connect
  sequence it also reads the variant name and the available EQ presets (both
  read-only, verified on the Space Travel), and follows feature lists that
  come in several parts.
- The variant name is looked up in the model table above. Device info shows
  the model, chip and the features the device reports.
- **Unknown devices are read-only**: OpenDrop shows what they report and the
  packet log, and sends nothing that changes them.
- EQ switching is offered only for models whose preset names we know (the
  Space Travel for now), and only for presets the device says it has.
  Other known models show the current preset id.

Next:

- Moondrop features 13 to 35, one at a time, as we confirm their commands
  with captures (battery, ANC v3 and touch controls first). What the app
  analysis found so far: [protocol/moondrop-gaia-features.md](protocol/moondrop-gaia-features.md).
- Preset names for other models (Link downloads them per model).
- Airoha's own SDK protocol only if an Airoha model turns out to need it for
  something GAIA doesn't offer (firmware updates stay out of scope).
- USB devices get their own transport and drivers, later (roadmap "Later"),
  starting with the family that covers the most models people own.
