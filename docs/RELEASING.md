# Releasing

## Once: a release key

```sh
keytool -genkeypair -v -keystore opendrop-release.jks -alias opendrop \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 opendrop-release.jks   # paste into the secret below
```

Keep the `.jks` and its passwords somewhere safe: every future update must
be signed with the same key. Add these repository secrets (Settings →
Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `OPENDROP_KEYSTORE_BASE64` | the base64 of the `.jks` |
| `OPENDROP_KEYSTORE_PASSWORD` | keystore password |
| `OPENDROP_KEY_ALIAS` | `opendrop` |
| `OPENDROP_KEY_PASSWORD` | key password |

Without them the release workflow still runs and attaches an unsigned APK.

## Each release

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`
   (500 characters at most; F-Droid shows it).
3. Merge to `main`, then tag that commit and push the tag:
   ```sh
   git tag -a v0.5.0 -m "OpenDrop 0.5.0"
   git push origin v0.5.0
   ```
4. The **Release** workflow checks the tag matches `versionName`, runs the
   tests, builds the release APK and publishes a GitHub Release with it.

## F-Droid

F-Droid builds from source and signs with its own key, so it uses the
unsigned release build (no secrets needed). The app has no proprietary
dependencies, no network access and no tracking. The listing text is in
`fastlane/metadata/android/` (F-Droid reads it from the repository).

To get listed, open a "Request For Packaging" or a merge request with a
build recipe at https://gitlab.com/fdroid/fdroiddata. A recipe that builds
each `v*` tag:

```yaml
Categories:
  - Multimedia
License: GPL-3.0-only
SourceCode: https://github.com/Zephulbr/OpenDrop
IssueTracker: https://github.com/Zephulbr/OpenDrop/issues

AutoName: OpenDrop

RepoType: git
Repo: https://github.com/Zephulbr/OpenDrop.git

Builds:
  - versionName: 0.5.0
    versionCode: 5
    commit: v0.5.0
    subdir: app
    gradle:
      - yes

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 0.5.0
CurrentVersionCode: 5
```

To publish the APK under your own key as well, F-Droid supports
reproducible builds against the GitHub Release APK; that is optional.
