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
| Official app | MOONDROP Link (EQ presets, gestures, ANC mode) | Reviews |
| Control channel | Qualcomm-style **GAIA V3** over Bluetooth Classic RFCOMM (channel 1), vendor `0x001D` | Gadgetbridge; **verify in Phase 0** |
| Battery reporting | HFP `AT+IPHONEACCEV` (combined level), not GAIA | Gadgetbridge; **verify in Phase 0** |

Protocol details: [protocol/space-travel.md](protocol/space-travel.md).

## Feature list and feasibility

Legend: ✅ High · 🟡 Medium · ❌ Low / out of scope

### Requested features

| Feature | Feasibility | How |
|---|---|---|
| Volume | ✅ | Phone volume via `AudioManager`. Any on-device volume setting (max / prompt volume) via a captured vendor command. |
| EQ presets (Reference / Balanced / Monitor) | ✅ | Replay the command the Link app already sends. |
| ANC mode (Off / ANC / Transparency) | ✅ | Command almost certainly exists since the touch gesture does it. |
| ANC strength / adaptive ANC | ❌ | Only if firmware exposes levels. Investigate, don't promise. |
| Lock touch controls | 🟡→✅ | Native lock command if one exists (check APK); otherwise emulate by remapping all gestures to "none" and restoring the saved mapping on unlock. |
| Battery % (combined) | ✅ | Sent over HFP; read Android's stored level for the device (hidden API via reflection, to be checked). |
| Battery % (left / right / case) | 🟡 | Only if ST1 answers the GAIA battery feature, which Gadgetbridge says it doesn't. Probe in Phase 0. |
| Bluetooth codec (SBC / AAC) | 🟡 | Android blocks this for normal apps. Options: optional **Shizuku**/root integration calling the privileged A2DP codec API; fallback deep-link to Developer Options. LDAC/aptX are impossible on this hardware. |

### Additional features

Firmware-backed (to confirm in Phase 0):

| Feature | Feasibility |
|---|---|
| Game / low-latency mode toggle | ✅ likely |
| Per-gesture touch remapping | ✅ likely (Link has it) |
| Multipoint on/off, connected-device list | 🟡 |
| Voice prompts: on/off, language, volume | 🟡 |
| Device rename | 🟡 |
| Firmware version display | ✅ likely |
| Find my earbuds (beep) | 🟡 |
| Factory reset / clear pairings | 🟡 |

App-side (no firmware dependency):

| Feature | Feasibility |
|---|---|
| Phone-side parametric EQ (10-band, via Android `DynamicsProcessing`) | ✅ |
| AutoEQ profile import / Space Travel target presets | ✅ |
| Quick Settings tiles (ANC mode, game mode) | ✅ |
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
- [ ] **Device info + optional GATT survey**: SDP UUIDs from `dumpsys`,
      nRF Connect check for GAIA-over-GATT / Bluetrum 9ECA services.
- [ ] **HCI snoop capture** of the Link app following
      [protocol/capture-guide.md](protocol/capture-guide.md).
- [ ] **Wireshark analysis**: confirm RFCOMM framing; decode EQ, touch
      action, ANC and game mode commands and notifications.
- [ ] **Safe probing**: read-only GAIA "get" commands for features
      0, 2, 8, 13, 14, 20, 32 using the dev console (Phase 1).
- [ ] **APK static analysis (optional)**: `jadx` on MOONDROP Link to find
      commands it never shows for ST1 (lock, game mode, prompts).
- [ ] Update `space-travel.md` to "verified"; commit sanitized captures as
      test fixtures under `docs/protocol/captures/`.

Exit criteria: documented commands for battery, ANC mode, EQ preset and
gesture mapping, each verified on real hardware.

### Phase 1: Project scaffold

- [ ] Gradle multi-module project, CI (build + unit tests + lint) on GitHub Actions.
- [ ] `core/protocol` with frame codec and unit tests from Phase 0 captures.
- [ ] `core/transport-classic`: RFCOMM connect to the bonded earbuds, GAIA
      framing, reconnect on A2DP connect.
- [ ] `core/transport-ble`: kept for later models that use GAIA over GATT.
- [ ] Developer packet console screen (useful right away for more research).

### Phase 2: MVP (v0.1)

- [ ] Device screen: connection state, battery L/R (+ case if available).
- [ ] ANC mode selector (Off / ANC / Transparency).
- [ ] EQ preset selector (Reference / Balanced / Monitor).
- [ ] Game mode toggle.
- [ ] Volume control.
- [ ] State stays in sync when changed from the earbuds themselves.

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
