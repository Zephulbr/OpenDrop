# AGENTS.md

OpenDrop: unofficial Android app (Kotlin + Jetpack Compose) for Moondrop audio
devices, speaking Qualcomm GAIA v3 over RFCOMM. JDK 17 and Android SDK 36
required for app builds.

## Commands

```sh
gradlew.bat :core:protocol:test :core:dsp:test   # pure-JVM unit tests, no Android SDK needed
gradlew.bat :app:assembleDebug                   # APK -> app/build/outputs/apk/debug/
gradlew.bat :app:lintDebug                       # Android lint (the only linter; no ktlint/detekt)
python -m unittest discover -s tools            # tests for the Python protocol tools
```

On Windows PowerShell use `.\gradlew.bat` (or `./gradlew` elsewhere). CI
(android.yml) runs protocol/dsp tests, Python tool tests, assembleDebug and
lint on every push to `main`/`claude/**` and on PRs — all four must pass.

Only `core/protocol` and `core/dsp` have tests; the app module has none. For
protocol or DSP changes, the fast loop is the two `:core:*:test` tasks.

## Layout

| Module | Kind | Contents |
|---|---|---|
| `core/protocol` | pure Kotlin/JVM | GAIA framing/session, model table, capabilities |
| `core/dsp` | pure Kotlin/JVM | biquad EQ math, AutoEQ import/export, presets |
| `core/transport-classic` | Android lib | RFCOMM link (`api`-depends on `:core:protocol`) |
| `core/transport-usb` | Android lib | USB host (read-only) |
| `app` | Android app | Compose UI, widgets, tile, automation intents |

App Kotlin sources live under `app/src/main/java/` (not `src/main/kotlin`).
Dependency versions go in `gradle/libs.versions.toml`. minSdk 26, JVM target
17 everywhere.

## Protocol work

- `docs/protocol/space-travel.md` is the protocol reference; the captures in
  `docs/protocol/captures/` are the source of truth. Tag claims **Verified** /
  **Inferred** / **Reported**; don't upgrade a tag without a capture to back it.
- `python tools/gaia_decode.py <capture.csv>` decodes a capture.
  `tools/gaia_probe.py` probes a live device (queries only, never writes).
- `docs/protocol/moondrop-gaia-features.md` and `usb-dac.md` come from static
  analysis of MOONDROP Link 2.26.2c (**App** evidence, no capture). Nothing
  App-only is sent on the default path; the plan in
  `docs/superpowers/plans/2026-10-17-moondrop-2.26.2c-feature-tiers.md` tiers
  what may ship, what goes behind developer options, and what never ships.
- Never commit vendor APKs (`Moondrop App/`, `*.apk(s)` are gitignored) or
  decompiled vendor code.
- Legal: borrow protocol *facts* from other (GPL/AGPL) projects, never code.
- Privacy: never commit raw `btsnoop_hci.log` files or full bugreports — only
  extracted earbud traffic, and redact Bluetooth MAC addresses first.
- GAIA frames can pack several to one RFCOMM packet; the parser must work on a
  byte stream. Battery arrives via HFP, not GAIA; ANC and game mode are not
  exposed by the Space Travel firmware at all.

## Constraints

- F-Droid listing is a goal: **no proprietary dependencies, no network access,
  no tracking**. Think twice before adding any dependency or permission.
- `app/debug.keystore` is a fixed, committed debug key (not a secret) so CI
  debug builds install over each other. Release builds are unsigned unless the
  `OPENDROP_KEYSTORE*` env vars are set — that's intentional for F-Droid.
- Release: tag `v*` must match `versionName` in `app/build.gradle.kts`, exactly
  one `versionCode`, and a changelog at
  `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (max 500
  chars). See docs/RELEASING.md.

## Conventions

- Translations are machine-assisted in `app/src/main/res/values-<lang>/strings.xml`;
  keep keys and `%1$s`-style placeholders intact. Adding a language means
  adding a new `values-<lang>` directory (locale list is auto-generated).
- Commit messages: `Area: lowercase summary`, e.g. `Widgets: earbuds EQ presets`.
- Useful docs: docs/DESIGN.md (UI direction), docs/automation.md (broadcast
  intents), docs/ROADMAP.md.
