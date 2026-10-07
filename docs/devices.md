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

## What decides the work per device

| Group | Connection | Likely protocol | Work |
|---|---|---|---|
| Bluetooth, Qualcomm-style | Classic SPP (RFCOMM) or BLE GATT | GAIA v3 | Shared driver. Features are self-describing, so a new model may work with no code |
| Bluetooth, other chip vendors | Usually BLE | Vendor protocol (Airoha, Bluetrum, BES, Realtek, ...) | One driver per chip family, from captures |
| USB DACs and DSP IEMs/cables | Android USB host | Vendor HID or USB control transfers | Separate USB transport; probably one protocol shared across several models |

We only know the protocol for the Space Travel so far. For every other model
the protocol is **unknown** until we capture its traffic or read it out of the
Link APK (see below).

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

1. **Link APK static analysis (best value).** Decompile Link with `jadx`. The
   list above is almost certainly a table in the app that maps each name to a
   connection type and protocol class. One pass would fill in most of the
   Protocol column and give us the command sets.
2. **Prior art.** Gadgetbridge supports some Moondrop models over GAIA. Check
   which ones and credit them (facts only, no code; see the legal note in the
   protocol notes).
3. **Community captures.** Owners of other models record an HCI snoop log
   while using Link ([capture guide](protocol/capture-guide.md)), or a USB
   capture for DACs.

## How the app will use this

- Detect the model by its GAIA variant name, falling back to the Bluetooth
  name, then pick a driver.
- One generic GAIA v3 driver that builds the UI from the features the device
  reports, with small per-model overrides (like the Space Travel EQ pop warning).
- Unknown devices connect in a read-only experimental mode with the packet
  log, so owners can send us captures.
- USB devices get their own transport and driver, later (roadmap "Later").
