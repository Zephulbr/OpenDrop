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
| [M1](#m1-space-travel-mvp-v01) Space Travel MVP (v0.1) | Everything the Space Travel can do | ✅ Done; v0.1.0 is `dcbfe23` |
| [M2](#m2-every-moondrop-bluetooth-model-v02) Every Bluetooth model (v0.2) | One GAIA driver for every Bluetooth model | 🟡 Driver done, features need captures |
| [M3](#m3-everyday-convenience-v03) Everyday convenience (v0.3) | Tiles, widget, notifications | 🟡 Built, untested on hardware; touch lock waits on M2 |
| [M4](#m4-phone-side-audio-v04) Phone-side audio (v0.4) | PEQ, AutoEQ, codec switching | 🟡 PEQ and AutoEQ built, untested on hardware; Shizuku codec switch open |
| [M5](#m5-usb-devices-v05) USB devices (v0.5) | Dawn, Moonriver, FreeDSP, DSP IEMs | 🟡 Read-only transport and USB report; protocols need captures |
| [M6](#m6-release-v10) Release (v1.0) | F-Droid and GitHub Releases | 🟡 Onboarding, errors, release pipeline done; translations and first release open |

**Next up:** try the M3 and M4 features on a phone (tile, widget, low-battery
alert, automation, phone EQ); collect device reports and USB reports from
owners of other models, then captures, to switch on M2 features and the M5
USB families.

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
- [x] Connection lives outside the screens (`DeviceController`) and runs in a
      foreground service while connected, with an ongoing notification and a
      Disconnect action.
- [x] Remember the last device; connect on launch and when the earbuds connect
      to the phone, also when Android had closed the app (manifest receiver).
- [x] Home screen: connection state, firmware, battery (Android's HFP level).
- [x] EQ presets Reference / Basshead / Monitor, kept in sync by the earbuds'
      notification.
- [x] Volume (Android media volume).
- [x] Device info, packet log and device report; appearance settings.
- [x] Device picker shows Moondrop devices; other paired devices sit behind a
      "Show other Bluetooth devices" toggle.
- [x] **Hardware test pass** on the Space Travel (Galaxy S24 Ultra, Android 16,
      2026-10-08):
  - [x] Connects; firmware `1.0.0`; model "Moondrop Space Travel"; features
        core, earbud, voice assistant, EQ, firmware update.
  - [x] Battery matches Android's Bluetooth settings.
  - [x] Each EQ preset switches, and a change made in Link shows up in OpenDrop.
  - [x] Reconnects after taking the buds out of range and back.
  - [x] Ongoing notification while connected, with a working Disconnect.
  - [x] Auto-connect, without tapping Disconnect first (an explicit
        Disconnect pauses auto-connect on purpose):
    - [x] App open: buds in the case for 30 s, then out. OpenDrop reconnects.
    - [x] App swiped away from recents: buds in the case, then out.
          OpenDrop connects on its own (check its notification).
- [ ] Fix whatever the test pass finds, then tag v0.1.

Won't do (firmware doesn't allow it): ANC mode and game mode control or
display, separate left / right / case battery.

## M2: Every Moondrop Bluetooth model (v0.2)

Foundation:

- [x] Generic GAIA session: model detection by variant name, feature list
      (including multi-part lists), read-only mode for unknown models.
- [x] Model table from Link's catalogue; Device info shows model, chip and
      reported features.
- [x] "Share device report" in Device info: model, firmware, feature list and
      packet log as text through the share sheet (no Bluetooth address), so
      owners of other models can contribute without a PC. *Untested on hardware.*
- [ ] EQ preset names per model, so switching works beyond the Space Travel.
- [x] Split `core/transport-classic` out of `app` and add a capability model
      in `core`: each reported capability is *control*, *read only* or
      *needs capture*; Device info, the home screen and the device report
      list what needs a capture.
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

- [x] Settings screen (gear on Home): Appearance, Notifications, Automation,
      About.
- [x] Quick Settings tile for EQ preset: shows the preset, a tap switches to
      the next one; while disconnected a tap connects. *Untested on hardware.*
- [x] Battery widget (name, battery, EQ preset or connection state) and a
      low-battery notification at 20 %, on by default. *Untested on hardware.*
- [x] More widgets: earbuds EQ (a button per preset, current one highlighted;
      tap to connect while disconnected) and a Phone EQ on/off toggle.
      *Untested on hardware.*
- [ ] Touch lock, native where a model has it, otherwise not offered. No
      model's touch lock command is known yet; it comes with touch controls
      (M2, needs a capture), so nothing is offered for now.
- [x] Automations: opt-in broadcast intents for Tasker and similar apps
      (set / next EQ, connect, disconnect, state broadcast), see
      [automation.md](automation.md). *Untested on hardware.*

## M4: Phone-side audio (v0.4)

- [x] Parametric EQ on the phone (Android `DynamicsProcessing` on the output
      mix, Android 9+), with a response graph and a filter editor. The curve
      (`core/dsp`, RBJ biquads) is sampled into a 64-band pre-EQ; the preamp
      is the input gain and a limiter at -1 dB catches the rest. A foreground
      service holds the effect while it's on. *Untested on hardware*; some
      phones refuse effects on the output mix, and the screen says so.
- [x] AutoEQ import (ParametricEQ.txt and GraphicEQ.txt, from a file or the
      clipboard), export through the share sheet, and built-in presets (Flat,
      Bass boost, Warm, V-shape, Vocal, Treble boost). Not measured target
      curves: those come per headphone from AutoEQ.
- [x] Codec: Settings → Audio → Bluetooth codec opens Developer options
      (with a hint when they're off).
- [ ] Codec switcher through Shizuku. Not built: it needs hidden Bluetooth
      APIs whose permission checks changed with the Bluetooth mainline module,
      so it can only be written against a real phone.

## M5: USB devices (v0.5)

- [x] USB host transport and permission flow (`core/transport-usb`): attached
      USB audio and Moondrop devices show on Home; a device screen asks for
      access and shares a **USB report** (ids, names, interfaces, raw and HID
      report descriptors, read with standard requests only). It never claims
      an interface, so audio keeps playing. USB model table from Link's
      catalogue, matched loosely by product string. *Untested on hardware.*
- Each family below needs a USB report plus a capture of MOONDROP Link
  changing a setting (principle 2) before OpenDrop sends anything.
- [ ] SPV family first (14 models: Dawn Pro 2, Moonriver 3, FreeDSP, Rays, ...).
- [ ] Comtrue (Dawn 3.5 / 4.4 / Pro, Moonriver 2 Ti).
- [ ] Synaptics (Echo-B, May, Starlight, Click, ...).
- [ ] Jiu (CHU2 DSP, CDSP, ...) and Jieli USB (Echo-BP, gaming headsets).

## M6: Release (v1.0)

- [x] Onboarding: a first-run screen (pair first, close Link, honest
      controls, phone EQ and USB). *Untested on hardware.*
- [x] Error handling: connection failures are sorted into causes (no answer,
      refused by another app, permission, Bluetooth off) with advice and a
      Try again button; the raw error stays visible as a detail.
- [ ] Translations. The UI strings are still in the Compose code; they move
      to `strings.xml` before the first translation.
- [x] Release pipeline: pushing a `v*` tag builds the release APK (signed
      when the key secrets exist) and publishes a GitHub Release; F-Droid
      listing text in `fastlane/`; steps in [RELEASING.md](RELEASING.md).
- [ ] First GitHub Release, and the F-Droid submission (fdroiddata merge
      request), once the M3/M4 features pass a hardware test.

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
