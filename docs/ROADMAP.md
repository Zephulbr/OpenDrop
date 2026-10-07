# OpenDrop Roadmap

OpenDrop is an unofficial, open-source Android app for controlling Moondrop
audio devices. The first target is the **original Moondrop Space Travel**
(2023, MD-TWS-022) true-wireless earbuds. USB DACs come later.

> OpenDrop is not affiliated with or endorsed by Moondrop. "Moondrop" is used
> only to describe device compatibility.

## Guiding principles

1. **Firmware is the limit.** OpenDrop can only send commands the earbud
   firmware already understands. We expose hidden firmware features; we do not
   invent features the hardware lacks.
2. **Never brick a device.** No firmware flashing, no writes we don't fully
   understand. Unknown commands are only ever tried read-only first.
3. **Protocol first, UI second.** Every feature is backed by a documented,
   captured command before it gets a screen.
4. **Testable without hardware.** Protocol code is pure Kotlin and is unit
   tested against real captured byte traces.
5. **Built for more models.** Space Travel is the first driver behind a generic
   device interface, not a one-off.

## Target device: Moondrop Space Travel (original)

| Property | Value | Source |
|---|---|---|
| SoC | Bluetrum BT8892E (BT889x family) | Teardown (Gough Lui) |
| Bluetooth | 5.3, multipoint (2 devices) | Spec sheet |
| Codecs | SBC, AAC | Spec sheet |
| ANC | Off / ANC / Transparency (3 s hold on either bud) | Manual, reviews |
| Game mode | ~55 ms latency (4x tap) | Manual |
| Official app | MOONDROP Link 2.26: volume (AVRCP) and EQ presets only | Our capture |
| Control channel | Qualcomm **GAIA v3** over Bluetooth Classic SPP/RFCOMM, vendor `0x001D`; no BLE | Our capture (verified) |
| GAIA features | core, earbud, voice UI, music processing (EQ), upgrade. **No ANC, battery or gesture feature** | Our capture (verified) |
| Firmware | 1.0.0 | Our capture |
| Battery reporting | HFP `AT+IPHONEACCEV`, one earbud level in 10 % steps; case not reported | Our capture (verified) |

Protocol details: [protocol/space-travel.md](protocol/space-travel.md).
Other Moondrop models and what they need: [devices.md](devices.md).

## Feature list and feasibility

Legend: ✅ High · 🟡 Medium · ❌ Low / out of scope

### Requested features

| Feature | Feasibility | How |
|---|---|---|
| Volume | ✅ | Link's slider is plain AVRCP absolute volume (verified), so this is Android media volume via `AudioManager`. No on-device volume command exists. |
| EQ presets (Reference / Basshead / Monitor) | ✅ **Verified** | GAIA music processing: set `0a03 [0/1/2]`, get `0a02`, change notification `0a81`. |
| ANC mode (Off / ANC / Transparency) | ❌ **Verified** | No GAIA feature, and long-press changes send no traffic at all. Neither settable nor visible to any app. |
| ANC strength / adaptive ANC | ❌ | Only if firmware exposes levels. Investigate, don't promise. |
| Lock touch controls | 🟡→❌ | No gesture feature advertised and Link has no gesture screen. Depends on finding the command Gadgetbridge reportedly uses for touch actions. |
| Battery % (earbuds) | ✅ **Verified** | One value over HFP, 10 % steps, already in Android. Read it in-app (hidden API via reflection, to be checked). |
| Battery % (left / right / case) | ❌ **Verified** | Not sent anywhere. |
| Bluetooth codec (SBC / AAC) | 🟡 | Android blocks this for normal apps. Options: optional **Shizuku**/root integration calling the privileged A2DP codec API; fallback deep-link to Developer Options. LDAC/aptX are impossible on this hardware. |

### Additional features

Firmware-backed:

| Feature | Feasibility |
|---|---|
| Game / low-latency mode toggle | ❌ **Verified** (4× tap sends no traffic) |
| On-device custom EQ (hidden "User" preset 63, 5 bands) | 🟡→❌ config reads as all zeros, likely a stub; one opt-in test left |
| Per-gesture touch remapping | 🟡 (not in Link 2.26; Gadgetbridge reportedly has it) |
| Multipoint on/off, connected-device list | ❌ (dual-device feature not advertised) |
| Voice prompts: on/off, language, volume | ❌ (prompts feature not advertised) |
| Device rename | ❌ (no feature advertised) |
| Firmware version display | ✅ **Verified** (`"1.0.0"`) |
| Find my earbuds (beep) | ❌ (no feature advertised) |
| Factory reset / clear pairings | 🟡 |

App-side (no firmware dependency):

| Feature | Feasibility |
|---|---|
| Phone-side parametric EQ (10-band, via Android `DynamicsProcessing`) | ✅ |
| AutoEQ profile import / Space Travel target presets | ✅ |
| Quick Settings tile (EQ preset) | ✅ |
| Battery home-screen widget + low-battery notification | ✅ |
| Automations (e.g. game mode when a game launches) | ✅ |
| Tasker / broadcast-intent integration | ✅ |
| Developer packet console (send/receive raw frames, log export) | ✅ |

Explicitly out of scope: firmware updates/flashing, LDAC/aptX, ANC filter
tuning, anything requiring modified firmware.

## Architecture (planned)

```
app/                    Jetpack Compose UI, navigation, tiles, widgets
core/model/             Capability model (Battery, AncMode, EqPreset, Gesture, ...)
core/protocol/          Pure Kotlin: frame encode/decode, checksums, command table
core/transport-classic/ RFCOMM/SPP transport (Space Travel)
core/transport-ble/     BLE GATT transport (newer models, GAIA over GATT)
core/audio/             Phone-side EQ (DynamicsProcessing), codec helper (Shizuku)
devices/spacetravel/    Space Travel driver: maps capabilities -> protocol commands
```

- Each device driver implements a common `DeviceDriver` interface and advertises
  a **capability set**; the UI shows only what the connected device supports.
- State is a `StateFlow<DeviceState>`, updated from device notifications.
- Kotlin, Jetpack Compose, Coroutines/Flow, Hilt. `minSdk 26`, target latest.
- Android 12+ permissions: `BLUETOOTH_CONNECT` (plus `BLUETOOTH_SCAN` for BLE models)
  with `neverForLocation`. Device association via `CompanionDeviceManager` so the
  app can find the buds without location permission and stay connected.

## Phases

### Phase 0: Protocol discovery (research, no app code)

Goal: a written protocol spec for every feature above.

Head start: Gadgetbridge already supports ST1 (EQ preset, touch actions)
over GAIA, and other projects document GAIA feature ids for newer Moondrop
models. Phase 0 is now mainly **verifying** that on our hardware and finding
the commands nobody has published (EQ, touch, game mode, lock).

- [x] **Prior-art survey**: summarised in
      [protocol/space-travel.md](protocol/space-travel.md).
- [x] **Device info**: Classic only (SPP, HFP, A2DP, AVRCP), no BLE.
- [x] **HCI snoop capture** of the Link app (EQ, volume, connect):
      [captures/](protocol/captures/).
- [x] **Analysis**: framing, connect sequence, EQ commands verified;
      decoder and tests in `tools/`.
- [ ] **Safe probing** with `tools/gaia_probe.py` from a PC: read-only
      commands, plus notification monitor while pressing/tapping the buds.
- [ ] **Touch actions**: find the command Gadgetbridge reportedly uses.
- [ ] **APK static analysis (optional)**: `jadx` on MOONDROP Link to find
      commands it never shows for ST1 (lock, game mode, prompts).
- [ ] Update `space-travel.md` to "verified"; commit sanitized captures as
      test fixtures under `docs/protocol/captures/`.

Exit criteria: EQ preset verified (done); probe results recorded for
every feature that ST1 might support; a clear yes/no on ANC, gestures
and game mode.

### Phase 1: Project scaffold

- [x] Gradle project (`app`, `core:protocol`), CI on GitHub Actions
      (protocol tests, Python tool tests, debug APK, lint).
- [x] `core/protocol`: frame codec, Space Travel commands, session state
      machine; unit tests replay the Phase 0 captures.
- [x] RFCOMM connection to the paired earbuds (in `app` for now; split into
      `core/transport-classic` when a second model needs it).
- [x] Reconnect automatically (with backoff) when the earbuds drop the link.
- [x] Remember the last device; connect on launch and when the earbuds connect to the phone. *Untested on hardware.*
- [ ] `core/transport-ble`: later models that use GAIA over GATT.
- [x] Packet log on the device screen (read-only console).

### Phase 2: MVP (v0.1)

- [x] Device screen: connection state, firmware version, battery (from Android). *Untested on hardware.*
- [ ] ~~ANC mode indicator~~ (not possible: buds don't report it).
- [x] EQ preset selector (Reference / Basshead / Monitor), with the pop warning. *Untested on hardware.*
- [ ] ~~Game mode indicator~~ (not possible: buds don't report it).
- [x] Volume control (Android media volume).
- [x] EQ stays in sync via the earbuds' change notification.

### Phase 3: Controls and quality of life (v0.2)

- [ ] Touch gesture remapping.
- [ ] Touch lock (native or emulated).
- [ ] Quick Settings tiles, battery widget, low-battery notification.
- [ ] Device info: firmware version, name; rename, find-my-buds, prompts
      (whatever Phase 0 confirms).

### Phase 4: Audio (v0.3)

- [ ] Phone-side parametric EQ with response graph, AutoEQ import, presets.
- [ ] Codec switcher: Shizuku integration (optional), Developer Options fallback.

### Phase 5: Polish and release (v1.0)

- [ ] Automations and Tasker intents.
- [ ] Onboarding, error handling, translations.
- [ ] Release on GitHub Releases and F-Droid.

### Later

- Moondrop USB DACs (Dawn / Dawn Pro / Moonriver / FreeDSP) via Android USB host.
- More Moondrop TWS models via community-contributed captures.

## Risks

| Risk | Mitigation |
|---|---|
| OpenDrop overlaps with Gadgetbridge (which already supports ST1) | Focus on what it doesn't do: a dedicated headphone UI, touch lock, codec switching, phone-side PEQ, tiles/widgets. Credit and cross-link. |
| Protocol is encrypted or authenticated | APK analysis shows how; Frida hooks on the Link app as a fallback. |
| Firmware updates change the protocol | Read firmware version; gate commands per version. |
| A wrong write breaks a setting | Read-before-write, keep a backup of settings, never send unknown commands outside the dev console. |
| Link app and OpenDrop fighting over the connection | Document "close the Link app"; handle disconnects cleanly. |
| Only one test device | Capture-based unit tests; packet console for community testers. |

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
