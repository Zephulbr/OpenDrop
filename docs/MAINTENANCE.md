# Maintenance

A living register of known bugs, robustness gaps and maintenance work, from a
full review of the repo on 2026-10-08 (commit `eccbdd0`). The fixes live in
[superpowers/plans/2026-10-08-maintenance-remediation.md](superpowers/plans/2026-10-08-maintenance-remediation.md),
which references each finding by its ID.

**How to use this file:** tick a box when the fix is merged to `main`. Move an
item to "Won't fix" or "Deferred" with a reason rather than deleting it.
Line numbers refer to the review commit and may drift.

Severity: **High** = corrupts state or misleads the user today; **Medium** =
wrong under realistic conditions, security or platform-risk; **Low** =
robustness or hygiene.

## A. High

- [ ] **F1** Connect-supersession race: `start()` calls `stop()` while an
  older attempt is still blocked inside `RfcommLink.connect()`; native socket
  connects don't observe coroutine cancellation, so the stale coroutine then
  publishes `Connected`, overwrites `link`/`session` and briefly leaves two
  sockets open. Switching devices (or disconnecting) mid-connect shows the
  wrong state. `app/.../device/DeviceController.kt:210-217, 254-273, 305-311`
- [ ] **F2** Phone EQ can silently fail to start: `PhoneEqService.start()` is
  only called from `Application.onCreate` (Android 12+ refuses
  foreground-service starts from the background) and from a user toggle, and
  the refusal is swallowed. `phoneEq.failed` is only set inside a *running*
  service, so the UI/widgets keep showing the EQ as on while nothing is
  applied, and nothing retries. `app/.../phoneeq/PhoneEqService.kt:136-142`,
  `app/.../OpenDropApplication.kt:40`, `app/.../phoneeq/PhoneEq.kt:51`
- [ ] **F3** `AutoEq.write` is lossy (Fc 0 decimals, gain/preamp 1, Q 2), and
  `PhoneEq` persists custom curves through it — a user's 105.7 Hz / −2.65 dB
  band mutates to 106 Hz / −2.7 dB on every app restart. The round-trip test
  uses only integral preset values, so CI can't catch it.
  `core/dsp/.../AutoEq.kt:76-92`, `app/.../phoneeq/PhoneEq.kt:49`,
  `core/dsp/src/test/.../EqTest.kt:107-115`

## B. Medium — protocol and DSP correctness

- [ ] **F4** The GAIA decoder counts a checksum byte in `total` but never
  validates (or strips for verification) it; a corrupted checksummed frame is
  passed upstream. No Moondrop capture has used `FLAG_CHECKSUM`, so the
  algorithm is unknown — verification needs a capture first (see Deferred).
  Neither the checksum nor the `FLAG_LENGTH_16` decode path has any test.
  `core/protocol/.../Gaia.kt:94-108`
- [ ] **F5** `GaiaSession` discards `GaiaEvent.Error`: if the device rejects
  `MUSIC_SET_EQ` with an ERROR frame, the UI stays on the old preset with no
  feedback (only the packet log shows it). `core/protocol/.../GaiaSession.kt:112`
- [ ] **F6** Non-finite numbers reach the DSP math: `coerceIn`/`coerceAtLeast`
  pass NaN through, and a very long digit string in an AutoEQ file parses to
  Infinity → NaN biquad coefficients → NaN dB into `DynamicsProcessing.EqBand`.
  `core/dsp/.../Eq.kt:51-54`, `core/dsp/.../AutoEq.kt:20-24, 59-62, 70-72`
- [x] **F7** *Disproven during planning:* `GraphicEq.curveDb` looked like it
  could hit 0/0 with duplicate points, but the interpolation bounds guarantee
  `f0 < hz ≤ f1`, so `ln(f1) − ln(f0)` is never zero and duplicates are
  harmless. The real NaN vector was non-finite dB values, covered by F6;
  the plan adds only a characterization test pinning duplicate tolerance.
  `core/dsp/.../Eq.kt:107-116`
- [ ] **F8** The available-EQ "count byte" heuristic
  (`p.drop(if (p.u(0) == p.size - 1) 1 else 0)`) is ambiguous: an ids-only
  payload `[1, 2]` misparses as `[2]`. Works for the captured Space Travel
  reply but is fragile against other firmware.
  `core/protocol/.../GaiaCommands.kt:94-96`
- [ ] **F9** A device that answers "more features" forever makes the session
  loop `getSupportedFeaturesNext` without any cap.
  `core/protocol/.../GaiaSession.kt:100-103`
- [ ] **F10** The connection is published as `Connected` as soon as the socket
  opens, before the GAIA handshake; a device that accepts RFCOMM but never
  answers leaves the UI "Connected" with empty state and no watchdog.
  `app/.../device/DeviceController.kt:262-273` (see Deferred)

## C. Medium — security and manifest

- [ ] **F11** `DeviceController` registers for `BATTERY_LEVEL_CHANGED` and
  `VOLUME_CHANGED_ACTION` with `RECEIVER_EXPORTED`; neither is a protected
  broadcast, so any app can spoof battery/volume in the UI and widgets.
  System broadcasts still reach `RECEIVER_NOT_EXPORTED` receivers.
  `app/.../device/DeviceController.kt:131`
- [ ] **F12** `UsbController` registers its receiver as `RECEIVER_EXPORTED`;
  its own permission broadcast is explicit (package-set), so `NOT_EXPORTED`
  works for both it and the system attach/detach actions.
  `app/.../device/UsbController.kt:60-68`
- [ ] **F13** `AclReceiver` is `exported="true"` in the manifest
  unnecessarily; `ACL_CONNECTED` is a protected system broadcast.
  `app/src/main/AndroidManifest.xml:36-42`
- [ ] **F14** `AutomationReceiver` is exported with no permission guard: once
  the user opts in, *every* installed app can send SET_EQ / NEXT_EQ /
  CONNECT / DISCONNECT. A shared-secret token keeps Tasker compatibility
  while blocking apps that don't know it.
  `app/src/main/AndroidManifest.xml:44-53`, `app/.../automation/Automation.kt:75`

## D. Medium — threading and UX

- [ ] **F15** AutoEQ import reads and parses the file on the main thread
  (ANR risk on slow storage) and silently truncates files at 64 KB — a cut-off
  file can parse into a wrong curve with no notice.
  `app/.../ui/PhoneEqScreen.kt:92-94, 175-191`
- [ ] **F16** `PhoneEqEngine` creates and reconfigures the `DynamicsProcessing`
  effect (binder work) on the main thread on every state emission.
  `app/.../phoneeq/PhoneEqEngine.kt:21-36`, `app/.../phoneeq/PhoneEqService.kt:34`
- [ ] **F17** The response-graph axis labels are spread evenly (0/25/50/75/100 %
  of width) while the gridlines sit at the log positions of 100 Hz / 1 kHz /
  10 kHz, so the labels don't line up with their lines.
  `app/.../ui/PhoneEqScreen.kt:246-267`

## E. Medium — build and dependencies

- [ ] **F18** `targetSdk`/`compileSdk` are 35; as of October 2026 Play and
  F-Droid expect 36 for app updates. `app/build.gradle.kts:9,14`,
  `core/transport-*/build.gradle.kts:8`
- [ ] **F19** The dependency set is about a year stale (AGP 8.7.3, Kotlin
  2.0.21, Compose BOM 2024.12.01, coroutines 1.9.0, lifecycle 2.8.7,
  activity-compose 1.9.3, core-ktx 1.15.0, datastore 1.1.1). Mutually
  consistent, so nothing is broken. `gradle/libs.versions.toml`
- [ ] **F20** Release builds don't run R8 (`isMinifyEnabled = false`): no
  shrinking, no obfuscation, larger APKs. `app/build.gradle.kts:44`

## F. Low — robustness and code hygiene

- [ ] **F21** `GaiaType.of` throws `NoSuchElementException` on an unknown id;
  safe today only because callers mask to 2 bits. `core/protocol/.../Gaia.kt:51-57`
- [ ] **F22** `hexToBytes` fails with a bare `NumberFormatException` and no
  useful message on non-hex input. `core/protocol/.../Gaia.kt:119-123`
- [ ] **F23** `Gaia.encode` neither masks nor requires the feature id to fit
  7 bits; an id over 0x7F would silently overflow the command field. It also
  silently truncates cmd ids over 0x7F. `core/protocol/.../Gaia.kt:26-29`
- [ ] **F24** The stream decoder rebuilds buffers per byte chunk (`buf += data`
  plus `copyOfRange` per frame), O(n²) on partial frames; a corrupted
  16-bit length can buffer up to ~64 KB. `core/protocol/.../Gaia.kt:79-113`
- [ ] **F25** `refreshEq` is dead code (no caller in the repo); the on-device
  preset is never re-queried after connect. `core/protocol/.../GaiaSession.kt:80-82`
- [ ] **F26** Comment/code mismatch: AutoEQ shelf filters default Q 0.707 in
  code, but the comment (and upstream AutoEQ) say shelves default to 0.70.
  `core/dsp/.../AutoEq.kt:61-62`
- [ ] **F27** Text with both parametric filters and a `GraphicEQ:` line
  silently ignores the graphic points (filters win) with no note.
  `core/dsp/.../AutoEq.kt:37-44`
- [ ] **F28** `RfcommLink`'s hidden-API fallback lets `NoSuchMethodException`
  escape as a non-IOException and discards the original SPP failure, so
  `LinkError` can't classify the real cause.
  `core/transport-classic/.../RfcommLink.kt:16-23`
- [ ] **F29** `socket`/`link`/`session` are written on one thread and read on
  others without `@Volatile` (benign today, formally a data race).
  `core/transport-classic/.../RfcommLink.kt:13`, `app/.../device/DeviceController.kt:92-93`
- [ ] **F30** BT model matching is exact-key (trim + lowercase), unlike USB's
  loose product-string match, so near-miss variant names stay read-only.
  `core/protocol/.../MoondropModels.kt` (see Deferred)
- [ ] **F31** First interaction with Appearance before DataStore loads is
  silently dropped. `app/.../settings/Appearance.kt:101-103`
- [ ] **F32** `OpenDocument` filters on `"text/*"` only; extension-less AutoEQ
  exports can be hidden by providers. `app/.../ui/PhoneEqScreen.kt:163`
- [ ] **F33** Slider rounding uses `Math.round`, which is asymmetric for
  negative halves (+0.5 rounds up, −0.5 rounds toward zero).
  `app/.../ui/PhoneEqScreen.kt:280, 325, 329, 333`
- [ ] **F34** `SimpleDateFormat` is allocated on every packet-log line,
  including bursts; also implicitly requires per-call allocation because it
  is called from IO threads. `app/.../device/DeviceController.kt:367-370`
- [ ] **F35** `EqWidget`'s button map is hardcoded to the three Space Travel
  presets; another model with switchable presets would show an empty row.
  Currently unreachable (preset switching is Space-Travel-gated).
  `app/.../widget/EqWidget.kt:55-59` (see Deferred)
- [ ] **F36** `UsbEntry.title`'s fallback is a hardcoded English string while
  the app ships 10 translations. `app/.../device/UsbController.kt:36`
- [ ] **F37** Stale KDoc: DeviceController "owns … (later) tiles and widgets"
  — tiles and widgets exist. `app/.../device/DeviceController.kt:76-79`
- [ ] **F38** `UsbProbe` truncates HID report descriptors over 4 KB (rare but
  legal). `core/transport-usb/.../UsbProbe.kt:87`
- [ ] **F39** `kotlinOptions { jvmTarget = … }` (deprecated DSL) in the three
  Android modules; the two JVM modules already use `compilerOptions`.
  `app/build.gradle.kts:60-62`, `core/transport-*/build.gradle.kts:19-21`
- [ ] **F40** `gradle.properties` has no `org.gradle.parallel`/`org.gradle.caching`
  flags (build performance only).

## G. Code quality

- [ ] **F41** The "UiState → displayable content" rule is written four times
  with subtly different fallbacks (`EqWidget.Content.of`,
  `BatteryWidget.WidgetContent.of`, `EqTileService.TileContent.of`,
  `ConnectionService.Content.of`) — a shared presenter would prevent drift.
- [ ] **F42** The 20 % low-battery threshold is duplicated
  (`AppSettings.LOW_BATTERY_PERCENT`, `Hero.kt:54`).
- [ ] **F43** The foreground-service scaffolding in `ConnectionService` and
  `PhoneEqService` is near-identical.
- [ ] **F44** `DeviceController` is a de-facto God class (393 lines: permission,
  device list, state machine, reconnect, volume, battery, logging, prefs,
  receivers). See Deferred for the split.
- [ ] **F45** No test sources in `core/transport-classic`, `core/transport-usb`
  or the `app` module; CI only runs `:core:protocol:test :core:dsp:test`.
  Test gaps tied to specific findings are listed with them (F3, F4, F6, F7,
  F9, F23, F26). See Deferred for app-module test infra.

## Won't fix (by design; no action)

| Item | Reason |
|---|---|
| Checked-in debug keystore with literal passwords | Intentional (comment in `app/build.gradle.kts:19-27`); debug keys aren't secret and CI builds update each other. |
| Deprecated `startActivityAndCollapse(Intent)` below API 34 | Unavoidable there; version-gated and suppressed (`EqTileService.kt:73-82`). |
| Packet log / device report text stays English | Deliberate (ROADMAP M6: "Device and USB reports stay in English on purpose"); raw link-error details likewise. |
| `SharedPreferences` for `AppSettings` | Deliberate: receivers and the tile read synchronously (KDoc). |
| Hidden APIs `isConnected`/`getBatteryLevel` via reflection | Wrapped in `runCatching` with graceful degradation; revisit when Android blocks them. |
| Response-graph numeric labels are not in `strings.xml` | "20 Hz", "1 kHz", "±12 dB" are locale-neutral numbers; only their alignment is wrong (F17). |
| `Math.round`-style preamp drag glitch when removing the expanded filter | Removal always collapses the editor, so the stale index is never shown. |
| Non-atomic `update` in `PhoneEq`/`AppSettings` | Single-main-thread by contract; worth a KDoc note (in the plan) but synchronization would be noise. |
| `peakDb()` undersamples very narrow peaks | The limiter in `PhoneEqEngine` is the intended safety net; more samples cost runtime for no user-visible gain. |
| Receivers/executor in process singletons never unregistered | `DeviceController`/`UsbController` are lazily-created process singletons; lifecycle hooks would be dead code today. |

## Deferred (needs a capture, hardware or a separate plan)

| ID | Item | Unblock when |
|---|---|---|
| X1 | Verify (vs. consume) checksummed GAIA frames (F4) | A capture from a model that sends `FLAG_CHECKSUM` frames, so the algorithm is known, not guessed. |
| X2 | Handshake watchdog: don't show `Connected` before the GAIA handshake answers (F10) | Next hardware test pass; needs care not to fight slow-but-healthy connects. |
| X3 | Read watchdog for `BluetoothSocket.close()` vs a blocked `read()` (platform quirk; a cancelled read loop can pin an IO thread) | Next hardware test pass; can't be validated on the emulator. |
| X4 | Looser BT model matching, e.g. punctuation-insensitive keys (F30) | Captures of real variant-name spellings from other models; guessing could pair the wrong catalogue entry. |
| X5 | Per-model EQ widget buttons instead of the Space Travel map (F35) | M2's per-model preset names land. |
| X6 | Split `DeviceController` (F44) | After F1's race fix lands and has had a hardware pass. |
| X7 | App-module test infrastructure (Robolectric) and tests for the transports (F45) | A dedicated plan; not blocking any fix above. |
| X8 | `EqWidget`/`ConnectionService` FGS scaffolding dedupe (F43) | With X7, so the shared helper is unit tested. |
