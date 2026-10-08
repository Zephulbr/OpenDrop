# OpenDrop

[![Releases](https://img.shields.io/github/v/release/Zephulbr/OpenDrop)](https://github.com/Zephulbr/OpenDrop/releases)
[![License](https://img.shields.io/github/license/Zephulbr/OpenDrop)](LICENSE)
[![Build](https://img.shields.io/github/actions/workflow/status/Zephulbr/OpenDrop/android.yml)](https://github.com/Zephulbr/OpenDrop/actions/workflows/android.yml)

OpenDrop is an unofficial, open-source Android app for controlling Moondrop
audio devices. The first supported device is the **Moondrop Space Travel**
earbuds; the goal is to cover more Moondrop hardware over time.

The app is still in early development. It currently talks to paired Space
Travel earbuds over the same GAIA-style protocol the official MOONDROP Link
app uses, plus a generic driver for other Moondrop Bluetooth models.

## Features

- Firmware version and battery status for connected earbuds
- EQ presets (Reference / Basshead / Monitor) and media volume control
- Quick Settings tile for switching EQ presets
- Battery widget, EQ widgets and a low-battery notification
- Phone-side parametric EQ with AutoEQ import, for any headphones
- Read-only USB report for Moondrop USB DACs and DSP cables
- Opt-in automation intents for Tasker and similar apps
  ([docs/automation.md](docs/automation.md))

Some things the Space Travel firmware simply doesn't expose — ANC and game
mode can't be controlled by any app, for instance. What's known is written
up in [docs/protocol/space-travel.md](docs/protocol/space-travel.md).

## Install

Grab the APK from the [Releases page](https://github.com/Zephulbr/OpenDrop/releases).
An F-Droid listing is planned; the metadata for it already lives in
`fastlane/metadata/android/`.

### Test builds

Every push builds a fresh debug APK: open the **Actions** tab, pick the
latest green "Android" run, and download `opendrop-debug-apk`. Unzip it and
install the APK on your phone (allow installs from unknown sources when
asked). CI builds are all signed with the same debug key, so a newer build
installs right over an older one. Builds from before October 2026 used a
random key — uninstall those once first.

Before connecting: pair the earbuds in Android's Bluetooth settings, take
them out of the case, and close the MOONDROP Link app so the two don't
fight over the connection.

## Languages

English, 简体中文, 日本語, Español, Deutsch, Français, العربية, Português,
한국어 and Русский. The translations are machine-assisted; corrections from
native speakers are welcome in `app/src/main/res/values-<language>/strings.xml`
(keep the keys and `%1$s`-style placeholders).

## Building from source

You need JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:protocol:test :core:dsp:test   # unit tests (no Android SDK needed)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
```

The code is split into a few modules:

| Module | What's in it |
|---|---|
| `core/protocol` | GAIA session, model table and capability model — pure Kotlin, tested against captured traces |
| `core/dsp` | Phone-side EQ math (biquads, AutoEQ import/export, presets) — pure Kotlin |
| `core/transport-classic` | Bluetooth Classic (RFCOMM) link |
| `core/transport-usb` | USB host access (read-only for now) |
| `app` | The Android app itself |

There are also protocol research tools in `tools/` (Python 3.10+, no
dependencies).

## Documentation

- [Roadmap](docs/ROADMAP.md) — where the project is headed
- [Supported devices](docs/devices.md) — what works on which hardware
- [Design direction](docs/DESIGN.md) — how the UI is meant to look and feel
- [Automation](docs/automation.md) — Tasker intents and friends
- [Releasing](docs/RELEASING.md) — how a release gets made

## License

OpenDrop is free software under the [GNU General Public License v3.0](LICENSE).
Protocol facts borrowed from other projects are credited in the docs; no
code was copied.

> OpenDrop is not affiliated with or endorsed by Moondrop.
