# OpenDrop

An unofficial, open-source Android app for controlling Moondrop audio devices.
First target: the original **Moondrop Space Travel** earbuds.

**Status:** early development (v0.1, first tested on a Galaxy Note 20 Ultra with Android 13). It connects to
paired Space Travel earbuds and shows firmware version and battery, switches
EQ presets (Reference / Basshead / Monitor), controls media volume, and shows
a packet log. Also: a Quick Settings tile for the EQ preset, a battery
widget, a low-battery notification and opt-in automation intents for Tasker
([docs/automation.md](docs/automation.md)).

What the Space Travel firmware does and doesn't allow (for example, ANC and
game mode can't be controlled by any app) is documented in
[docs/protocol/space-travel.md](docs/protocol/space-travel.md). The plan is in
[docs/ROADMAP.md](docs/ROADMAP.md), the device list in
[docs/devices.md](docs/devices.md) and the UI direction in
[docs/DESIGN.md](docs/DESIGN.md).

## Install a test build

Every push builds a debug APK on GitHub Actions: open the **Actions** tab,
pick the latest green "Android" run, and download `opendrop-debug-apk`.
Unzip it and install the APK on your phone (allow installs from unknown
sources when asked). Builds are signed with the same debug key, so a newer
build installs over an older one; builds from before October 2026 used a
random key, so uninstall those once first.

Before connecting: pair the earbuds in Android's Bluetooth settings, take them
out of the case, and close the MOONDROP Link app.

## Build

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:protocol:test :core:dsp:test   # unit tests (no Android SDK needed)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
```

Modules:

- `core/protocol`: GAIA session, model table and capability model, pure
  Kotlin and unit tested against captured traces.
- `core/dsp`: phone-side EQ math (biquads, AutoEQ import/export, presets),
  pure Kotlin and unit tested.
- `core/transport-classic`: the Bluetooth Classic (RFCOMM) link.
- `app`: the Android app.

Protocol research tools (Python 3.10+, no dependencies) are in `tools/`.

## License

OpenDrop is free software under the [GNU General Public License v3.0](LICENSE).
Protocol facts from other projects are credited in the docs; no code was copied.

> OpenDrop is not affiliated with or endorsed by Moondrop.
