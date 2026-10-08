# OpenDrop Roadmap

OpenDrop is an unofficial, open-source Android app for controlling Moondrop
audio devices. It started with the **original Moondrop Space Travel** and now
aims at every Moondrop Bluetooth model MOONDROP Link supports (51 entries, all
controlled over GAIA), with USB DACs and DSP dongles after that. The model
list is in [devices.md](devices.md).

> OpenDrop is not affiliated with or endorsed by Moondrop. "Moondrop" is used
> only to describe device compatibility.

**How to use this file:** tick a box when the work is merged to `main`.
Mark things that work in code but haven't been tried on a real device with
*Untested on hardware.* Move an item to "Won't do" with the reason rather than
deleting it.

## Status at a glance

| Milestone | Goal | Status |
|---|---|---|
| [M0](#m0-research) Research | Know how Moondrop devices talk | ✅ Done (USB protocols still open) |
| [M1](#m1-space-travel-mvp-v01) Space Travel MVP (v0.1) | Everything the Space Travel can do | 🟡 Built, needs hardware testing |
| [M2](#m2-every-moondrop-bluetooth-model-v02) Every Bluetooth model (v0.2) | One GAIA driver for every Bluetooth model | 🟡 Driver done, features need captures |
| [M3](#m3-everyday-convenience-v03) Everyday convenience (v0.3) | Tiles, widget, notifications | ⬜ Not started |
| [M4](#m4-phone-side-audio-v04) Phone-side audio (v0.4) | PEQ, AutoEQ, codec switching | ⬜ Not started |
| [M5](#m5-usb-devices-v05) USB devices (v0.5) | Dawn, Moonriver, FreeDSP, DSP IEMs | ⬜ Not started |
| [M6](#m6-release-v10) Release (v1.0) | F-Droid and GitHub Releases | ⬜ Not started |

**Next up:** test M1 on the Space Travel, then get captures from owners of
newer models to switch on M2 features.

## Guiding principles

1. **Firmware is the limit.** OpenDrop can only send commands the firmware
   already understands. We expose hidden firmware features; we do not invent
   features the hardware lacks.
2. **Never brick a device.** No firmware flashing, no writes we don't fully
   understand. Unknown commands are only ever tried read-only first, and a
   command found only by app analysis needs a capture before OpenDrop sends it.
3. **Protocol first, UI second.** Every feature is backed by a documented,
   captured command before it gets a screen.
4. **Testable without hardware.** Protocol code is pure Kotlin and is unit
   tested against real captured byte traces.
5. **Driven by what the device reports.** GAIA devices list their own
   features; the UI shows only what the connected device supports. Unknown
   models are read-only.

## M0: Research

- [x] Space Travel: HCI capture of MOONDROP Link, decoder and tests
      ([space-travel.md](protocol/space-travel.md), [captures/](protocol/captures/)).
- [x] Space Travel: read-only probe from a PC (`tools/gaia_probe.py`); clear
      no on ANC, game mode, gestures and per-bud battery over GAIA.
- [x] MOONDROP Link static analysis: GAIA is the control protocol for every
      Bluetooth chip family; Moondrop features 13 to 35 identified.
- [x] Model catalogue: chip and connection type for all 107 products
      ([devices.md](devices.md)).
- [x] Commands for Moondrop features from the app (unverified):
      [moondrop-gaia-features.md](protocol/moondrop-gaia-features.md).
- [ ] Space Travel hidden "User" EQ (preset 63): run the one opt-in test
      (`gaia_probe.py --try-user-eq`) to settle whether it works.
- [ ] Preset names per model: Link downloads them
      (`/api/v1/ota-config-equalizers/all`); fetch once and record them.
- [ ] Touch controls: map the 10 gesture slots and the action codes.
- [ ] Older ANC (2) and ANC v2 (32), touch controls v3 (26): analyse.
- [ ] USB protocols (SPV, Comtrue, Synaptics, Jiu, Jieli USB): analyse.

## M1: Space Travel MVP (v0.1)

- [x] Gradle project, CI (protocol tests, Python tool tests, debug APK, lint).
- [x] RFCOMM connection; reconnect with backoff when the link drops.
- [x] Remember the last device; connect on launch and when the earbuds connect
      to the phone. *Untested on hardware.*
- [x] Home screen: connection state, firmware, battery (Android's HFP level).
      *Untested on hardware.*
- [x] EQ presets Reference / Basshead / Monitor, kept in sync by the earbuds'
      notification. *Untested on hardware.*
- [x] Volume (Android media volume).
- [x] Device info and packet log; appearance settings.
- [ ] **Hardware test pass** on the Space Travel:
  - [ ] Connects; firmware shows `1.0.0`; model shows "Moondrop Space Travel".
  - [ ] Battery matches Android's Bluetooth settings.
  - [ ] Each EQ preset switches, and a change made in Link shows up in OpenDrop.
  - [ ] Reconnects after taking the buds out of range and back.
  - [ ] Auto-connect works when the buds connect to the phone.
- [ ] Fix whatever the test pass finds, then tag v0.1.

Won't do (firmware doesn't allow it): ANC mode and game mode control or
display, separate left / right / case battery.

## M2: Every Moondrop Bluetooth model (v0.2)

Foundation:

- [x] Generic GAIA session: model detection by variant name, feature list
      (including multi-part lists), read-only mode for unknown models.
- [x] Model table from Link's catalogue; Device info shows model, chip and
      reported features.
- [ ] "Send a report" button: exports the packet log, feature list and model
      name, so owners of other models can contribute without a PC.
- [ ] EQ preset names per model, so switching works beyond the Space Travel.
- [ ] Split `core/transport-classic` out of `app` and add a capability model
      in `core`, before the UI grows per-feature screens.
- [ ] BLE GATT transport, only if a model turns out to need it on Android.

Moondrop features (each needs a capture from a model that has it, then code,
tests and a screen):

- [ ] Battery (13): left, right and case levels.
- [ ] ANC v3 (33): mode (off, ANC, transparency, anti-wind, adaptive), anti-wind.
- [ ] ANC (2) / ANC v2 (32) for older models.
- [ ] Touch controls v2 / v3 / v4 (22, 26, 31): remap gestures.
- [ ] Find my earbuds (34).
- [ ] Codec (16): LDAC / LHDC on models that have it.
- [ ] Auto power-off (25), LED (19), wear sensor (17), multipoint (20).
- [ ] Spatial audio and head tracking (18), dynamic bass (27), left/right
      swap (30), dual-mic noise reduction (35), voice prompts (14).
- [ ] On-device user EQ (music processing 5 to 8) where the firmware supports it.

## M3: Everyday convenience (v0.3)

- [ ] Quick Settings tile for EQ preset.
- [ ] Battery widget and low-battery notification.
- [ ] Touch lock, native where a model has it, otherwise not offered.
- [ ] Automations and Tasker / broadcast intents.

## M4: Phone-side audio (v0.4)

- [ ] Parametric EQ on the phone (Android `DynamicsProcessing`), with a
      response graph.
- [ ] AutoEQ import and target-curve presets.
- [ ] Codec switcher: optional Shizuku integration, Developer Options fallback.

## M5: USB devices (v0.5)

- [ ] USB host transport and permission flow.
- [ ] SPV family first (14 models: Dawn Pro 2, Moonriver 3, FreeDSP, Rays, ...).
- [ ] Comtrue (Dawn 3.5 / 4.4 / Pro, Moonriver 2 Ti).
- [ ] Synaptics (Echo-B, May, Starlight, Click, ...).
- [ ] Jiu (CHU2 DSP, CDSP, ...) and Jieli USB (Echo-BP, gaming headsets).

## M6: Release (v1.0)

- [ ] Onboarding and error handling for first-time users.
- [ ] Translations.
- [ ] Release on GitHub Releases and F-Droid.

Out of scope: firmware updates or flashing, LDAC/aptX on hardware that lacks
it, ANC filter tuning, anything requiring modified firmware.

## Space Travel facts

| Property | Value | Source |
|---|---|---|
| SoC | Bluetrum BT8892E (BT889x family) | Teardown (Gough Lui) |
| Control channel | GAIA v3 over Bluetooth Classic SPP/RFCOMM, vendor `0x001D`; no BLE | Our capture (verified) |
| GAIA features | core, earbud, voice UI, music processing (EQ), upgrade | Our capture (verified) |
| Firmware | 1.0.0 | Our capture |
| Battery | HFP `AT+IPHONEACCEV`, one level in 10 % steps; case not reported | Our capture (verified) |
| ANC, game mode | Long-press and 4× tap send no traffic; not visible to any app | Our capture (verified) |

Details: [protocol/space-travel.md](protocol/space-travel.md).

## Risks

| Risk | Mitigation |
|---|---|
| OpenDrop overlaps with Gadgetbridge (which supports some Moondrop models) | Focus on what it doesn't do: a dedicated headphone UI, touch lock, codec switching, phone-side PEQ, tiles/widgets. Credit and cross-link. |
| Protocol is encrypted or authenticated | APK analysis shows how; Frida hooks on the Link app as a fallback. |
| Firmware updates change the protocol | Read firmware version; gate commands per version. |
| A wrong write breaks a setting | Read-before-write, keep a backup of settings, never send unknown commands outside the dev console. |
| Link app and OpenDrop fighting over the connection | Document "close the Link app"; handle disconnects cleanly. |
| Only one test device | Capture-based unit tests; read-only mode and a report export for community testers. |

## References

- Gough Lui, Space Travel quick review (Bluetrum BT8892E):
  https://goughlui.com/2025/07/19/quick-review-moondrop-space-travel-2023-true-wireless-stereo-anc-earbuds-md-tws-022/
- wearfit-pro-ble, Bluetrum `0xAB` BLE protocol: https://github.com/milcorix/wearfit-pro-ble
- Bluetrum AB5682 hacking notes: https://github.com/atc1441/Bluetrum_AB5682_Hacking
- Gadgetbridge, BT protocol reverse engineering guide:
  https://codeberg.org/Freeyourgadget/Gadgetbridge/wiki/BT-Protocol-Reverse-Engineering
- Gadgetbridge, Moondrop support (AGPL-3.0, facts only, no code copied):
  https://codeberg.org/Freeyourgadget/Gadgetbridge/pulls/3857
- HyperPods-for-Moondrop `PROTOCOL.md` (GPL-3.0, facts only):
  https://github.com/huime180/HyperPods-for-Moondrop
- moondrop-control (GPL-3.0, facts only): https://github.com/FEAKEuser/moondrop-control
- Prior Moondrop projects: https://github.com/topics/moondrop
