# OpenDrop

An unofficial, open-source Android app for controlling Moondrop audio devices.
First target: the original **Moondrop Space Travel** earbuds.

**Status:** early development (v0.1, first tested on a Galaxy Note 20 Ultra with Android 13). It connects to
paired Space Travel earbuds and shows firmware version and battery, switches
EQ presets (Reference / Basshead / Monitor), controls media volume, and shows
a packet log.

What the Space Travel firmware does and doesn't allow (for example, ANC and
game mode can't be controlled by any app) is documented in
[docs/protocol/space-travel.md](docs/protocol/space-travel.md). The plan is in
[docs/ROADMAP.md](docs/ROADMAP.md) and the UI direction in
[docs/DESIGN.md](docs/DESIGN.md).

## Install a test build

Every push builds a debug APK on GitHub Actions: open the **Actions** tab,
pick the latest green "Android" run, and download `opendrop-debug-apk`.
Unzip it and install the APK on your phone (allow installs from unknown
sources when asked).

Before connecting: pair the earbuds in Android's Bluetooth settings, take them
out of the case, and close the MOONDROP Link app.

## Build

Requires JDK 17 and the Android SDK (API 35).

```sh
./gradlew :core:protocol:test   # protocol unit tests (no Android SDK needed)
./gradlew :app:assembleDebug    # app/build/outputs/apk/debug/
```

Protocol research tools (Python 3.10+, no dependencies) are in `tools/`.

## License

OpenDrop is free software under the [GNU General Public License v3.0](LICENSE).
Protocol facts from other projects are credited in the docs; no code was copied.

> OpenDrop is not affiliated with or endorsed by Moondrop.
