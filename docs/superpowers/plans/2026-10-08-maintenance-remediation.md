# Maintenance Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every actionable finding from the 2026-10-08 repo review, in five phases ordered by user impact.

**Architecture:** Pure-Kotlin fixes in `core/protocol` and `core/dsp` land first (Phases 1–2) because they are unit-testable in CI and two of them corrupt data or state today. Android-layer security and threading fixes follow (Phase 3), then build/dependency maintenance (Phase 4, which touches everything and goes last so its churn doesn't hide code regressions), then quality/UX polish (Phase 5). Findings are tracked in `docs/MAINTENANCE.md` as `F1`…`F45`; deferred and won't-fix items live there and are intentionally not in this plan.

**Tech Stack:** Kotlin 2.0 (JVM + Android), Gradle (Kotlin DSL, version catalog), JUnit 4, Compose, no new dependencies except version bumps in Task 14.

**Spec:** `docs/MAINTENANCE.md` (the findings register). Executors should read a finding's entry there when a task references an ID.

## Global Constraints

- Unit tests: `./gradlew :core:protocol:test :core:dsp:test` (pure JVM, no Android SDK needed). On Windows run `.\gradlew.bat …` instead of `./gradlew …`.
- App verification: `./gradlew :app:assembleDebug :app:lintDebug` — the app module has no test sources (finding F45 / deferred X7), so app-layer tasks verify with build + lint + the listed manual checks.
- Repo principles (ROADMAP): never send a command to a device that wasn't captured first; protocol code must match the captures in `docs/protocol/captures/`; unit tests reference real captured bytes.
- Follow the repo's KDoc style when a change alters documented behavior; no other new comments.
- Any new `strings.xml` key must be added to all nine translated `values-*/strings.xml` files (machine-assisted is the established norm; keep keys and `%1$s`-style placeholders).
- Commit style: `Area: summary`, e.g. `DSP: lossless AutoEQ write`. Never push or commit unless the human asked.
- Don't bump dependencies or SDK levels outside Task 14; don't refactor outside the listed files.

## Review Focus

The five input classes most likely to bite a user that no existing test exercises. Each is pinned by a test or manual step in the owning task.

1. **Split/garbled/corrupted GAIA byte streams** (frames split across reads, garbage prefixes, SOF bytes inside payloads, 16-bit lengths, checksummed frames) → pinned by the new decoder tests in Task 4.
2. **Malformed AutoEQ text** (non-finite numbers, shelves without Q, mixed formats, duplicate graphic points) → pinned by Task 6's tests.
3. **Rapid device switching while a connect is in flight** → generation-token fix in Task 2; manual check on the next hardware pass (no app test infra).
4. **Oversized or slow-storage import files** → Task 10's off-main-thread import with a truncation notice; manual check.
5. **Devices that stall the handshake or answer endless feature pages** → feature-page cap in Task 4; the handshake watchdog stays deferred (X2 in MAINTENANCE.md).

---

## Phase 1 — High-severity correctness fixes

*These corrupt user data or app state today. Do them first.*

### Task 1: Lossless AutoEQ persistence (F3)

**Files:**
- Modify: `core/dsp/src/main/kotlin/org/opendrop/dsp/AutoEq.kt:75-92`
- Test: `core/dsp/src/test/kotlin/org/opendrop/dsp/EqTest.kt`

**Interfaces:**
- Produces: `AutoEq.write(eq: EqCurve): String` (unchanged signature) whose output parses back to a `Double`-identical curve. `PhoneEq` persistence (`PhoneEq.kt:49`) needs no change.

- [ ] **Step 1: Write the failing round-trip test**

Add to `EqTest.kt` (after `writeThenParseRoundTrips`):

```kotlin
@Test
fun roundTripsFractionalValuesLosslessly() {
    // PhoneEq persists custom curves as AutoEq text (PhoneEq.kt); a lossy
    // write mutated user data on every restart (MAINTENANCE F3).
    val parametric = ParametricEq(
        -6.25,
        listOf(
            PeqFilter(FilterType.LOW_SHELF, 105.7, 6.5, 0.7),
            PeqFilter(FilterType.PEAK, 158.25, -2.65, 0.43),
            PeqFilter(FilterType.HIGH_PASS, 31.5, 0.0, 0.707),
        ),
    )
    assertEquals(parametric, AutoEq.parse(AutoEq.write(parametric)))

    val graphic = GraphicEq(-2.375, listOf(20.5 to -1.25, 1_050.0 to 0.75))
    assertEquals(graphic, AutoEq.parse(AutoEq.write(graphic)))
}
```

- [ ] **Step 2: Run it and confirm it fails**

Run: `./gradlew :core:dsp:test --tests "org.opendrop.dsp.EqTest.roundTripsFractionalValuesLosslessly"`
Expected: FAIL — `expected ...105.7..., got ...106.0...` (Fc loses decimals).

- [ ] **Step 3: Make `fmt` lossless**

In `AutoEq.kt`, add the import and replace the formatter (drop the `decimals` parameter):

```kotlin
import java.math.BigDecimal
```

```kotlin
/**
 * Shortest text that parses back to exactly [value]: `Double.toString` is
 * round-trip-exact, and BigDecimal only strips the trailing ".0".
 */
private fun fmt(value: Double): String =
    BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
```

The existing `write` body stays the same — its call sites pass no decimals now, and integral values still print as `105`, `0.7`, `-6`.

- [ ] **Step 4: Run the full DSP test suite**

Run: `./gradlew :core:dsp:test`
Expected: PASS (including `writeThenParseRoundTrips` and `parsesAutoEqParametric`).

- [ ] **Step 5: Commit**

```
git add core/dsp/src/main/kotlin/org/opendrop/dsp/AutoEq.kt core/dsp/src/test/kotlin/org/opendrop/dsp/EqTest.kt
git commit -m "DSP: lossless AutoEQ write so saved curves don't drift"
```

### Task 2: Connect-supersession race (F1)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/device/DeviceController.kt:92-94, 210-218, 224-249, 254-295, 305-311`

**Interfaces:**
- Produces: no API change. `start()`/`disconnect()` remain the only entry points; a superseded attempt exits via a `generation` token instead of publishing state.

- [ ] **Step 1: Add the generation token**

In `DeviceController`, next to the `link`/`session` fields (line ~92):

```kotlin
@Volatile private var link: GaiaLink? = null
@Volatile private var session: GaiaSession? = null
private var readJob: Job? = null

/** Bumped by every [stop]; an attempt whose token is stale exits quietly. */
private var generation = 0
```

(`@Volatile` also fixes finding F29 for these two fields.)

- [ ] **Step 2: Bump in `stop()`, capture in `start()`**

```kotlin
private fun start(target: PairedDevice, auto: Boolean) {
    val device = bluetooth?.getRemoteDevice(target.address) ?: return
    stop()
    _state.update {
        it.copy(selected = target, connection = Connection.Connecting, device = GaiaDeviceState(), log = emptyList())
    }
    readBattery(device)
    val gen = generation
    readJob = scope.launch(Dispatchers.IO) { keepConnected(device, auto, gen) }
}
```

```kotlin
private fun stop() {
    generation++ // supersedes whatever attempt is still in flight
    readJob?.cancel()
    readJob = null
    link?.close()
    link = null
    session = null
}
```

- [ ] **Step 3: Check the token in `keepConnected` and `connectOnce`**

```kotlin
private suspend fun keepConnected(device: BluetoothDevice, auto: Boolean, gen: Int) {
    var failures = 0
    var everConnected = false
    while (true) {
        val ended = connectOnce(device, gen)
        if (gen != generation) return // superseded by a newer connect/disconnect
        if (!currentCoroutineContext().isActive) return // user disconnected
        // ... rest unchanged ...
```

```kotlin
private suspend fun connectOnce(device: BluetoothDevice, gen: Int): Ended {
    val newLink: GaiaLink = RfcommLink(device)
    try {
        newLink.connect()
    } catch (e: Exception) {
        newLink.close()
        return Ended(e.message ?: e.javaClass.simpleName, connected = false, lastedMs = 0)
    }
    if (gen != generation) {
        // A newer connect() or disconnect() won while the native socket call
        // blocked; drop this attempt without touching link/session/state.
        newLink.close()
        return Ended("Superseded", connected = false, lastedMs = 0)
    }
    // ... rest unchanged (link = newLink onward) ...
```

- [ ] **Step 4: Build and lint**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual check (next hardware pass; add to the ROADMAP M1 checklist)**

With the earbuds paired: tap device A, and while it shows *Connecting* tap device B → B must end up the connected device; then tap Disconnect mid-connect → must stay *Disconnected*, no zombie "Connected" state, and reconnecting A afterwards works.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/org/opendrop/app/device/DeviceController.kt
git commit -m "Connection: superseded connect attempts no longer publish state"
```

### Task 3: Phone EQ silent start failure (F2)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/phoneeq/PhoneEqService.kt:40-60, 75-93, 121-143`
- Modify: `app/src/main/java/org/opendrop/app/MainActivity.kt:46-50`
- Modify: `app/src/main/java/org/opendrop/app/ui/OpenDropApp.kt:52, 126-132`

**Interfaces:**
- Produces: `PhoneEqService.running: StateFlow<Boolean>` — true while the service holds the effect; the UI treats "EQ on but not running" as failed.

- [ ] **Step 1: Expose a `running` StateFlow on the service**

In `PhoneEqService` companion (and set it in the lifecycle methods):

```kotlin
companion object {
    private val _running = MutableStateFlow(false)

    /** True while the service holds the effect; "EQ on but not running" is a failure. */
    val running: StateFlow<Boolean> = _running.asStateFlow()
    // ... existing TAG/CHANNEL_ID/NOTIFICATION_ID/ACTION_TURN_OFF/createChannel/start ...
}
```

Imports: `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.StateFlow`, `kotlinx.coroutines.flow.asStateFlow`.

In `onStartCommand`, set it once the watcher is ensured:

```kotlin
if (watcher == null) {
    // ... existing watcher setup unchanged ...
}
_running.value = true
return START_NOT_STICKY
```

In `stop()` add `_running.value = false` as the first line; in `onDestroy()` add `_running.value = false` before `super.onDestroy()` (the system can kill the service without `stop()`).

- [ ] **Step 2: Retry the start when the app is in the foreground**

`MainActivity.onResume` (the process may have started in the background, where Android 12+ refused the service start):

```kotlin
override fun onResume() {
    super.onResume()
    // Pick up permission or pairing changes made in system settings.
    viewModel.refresh()
    // Android 12+ may have refused the service start while the process was in
    // the background; bring the phone EQ back now that we're in the foreground.
    if ((application as OpenDropApplication).phoneEq.state.value.enabled) {
        PhoneEqService.start(this)
    }
}
```

Imports in `MainActivity.kt`: `org.opendrop.app.phoneeq.PhoneEqService`.

- [ ] **Step 3: Show "failed" while enabled-but-not-running**

In `OpenDropApp.kt`:

```kotlin
val phoneEqRunning by PhoneEqService.running.collectAsStateWithLifecycle()
```

and pass the combined failure at the existing call site:

```kotlin
Destination.PhoneEq -> PhoneEqScreen(
    state = phoneEq,
    failed = phoneEqFailed || (phoneEq.enabled && !phoneEqRunning),
    onChange = viewModel::updatePhoneEq,
    onCustom = viewModel::setCustomEq,
    onBack = back,
)
```

Import `org.opendrop.app.phoneeq.PhoneEqService`. (`PhoneEqScreen` already gates the note on `state.enabled && failed`.)

- [ ] **Step 4: Build and lint**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manual check (next hardware pass; add to the ROADMAP M4 checklist)**

Enable the phone EQ, then `adb shell am force-stop org.opendrop.app` and relaunch from the launcher → the EQ notification/effect must come back, and the Phone EQ screen must not show the failure note while it's applied.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/org/opendrop/app/phoneeq/PhoneEqService.kt app/src/main/java/org/opendrop/app/MainActivity.kt app/src/main/java/org/opendrop/app/ui/OpenDropApp.kt
git commit -m "Phone EQ: retry the service in the foreground and surface a dead effect"
```

---

## Phase 2 — Protocol and DSP robustness

*Pure Kotlin, unit-tested in CI. TDD throughout.*

### Task 4: GAIA decoder hardening and feature-page cap (F4, F8, F9, F24)

**Files:**
- Modify: `core/protocol/src/main/kotlin/org/opendrop/protocol/Gaia.kt:79-113` (decoder), `:7-11` (KDoc)
- Modify: `core/protocol/src/main/kotlin/org/opendrop/protocol/GaiaSession.kt:91-114` (page cap)
- Test: `core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaTest.kt`, `GaiaSessionTest.kt`

**Interfaces:**
- Produces: `GaiaStreamDecoder.feed` behavior unchanged for well-formed streams (byte-exact against the capture tests); framing now consumes checksum bytes and 16-bit lengths correctly with O(n) buffering. `GaiaSession` stops asking for feature pages after 16.

- [ ] **Step 1: Add decoder characterization tests (these pin behavior that was never tested)**

In `GaiaTest.kt`:

```kotlin
@Test
fun decodesA16BitLengthFrame() {
    val raw = byteArrayOf(
        0xFF.toByte(), 0x04, 0x02, 0x00, 0x01, // SOF, v3, FLAG_LENGTH_16, len=1
        0x00, 0x1D, 0x00, 0x01, 0x42, // vendor, command, payload
    )
    val frames = GaiaStreamDecoder().feed(raw)
    assertEquals(1, frames.size)
    assertEquals(Gaia.VENDOR_V3, frames[0].vendor)
    assertEquals(1, frames[0].payload.size)
    assertEquals(0x42, frames[0].payload[0].toInt())
}

@Test
fun checksummedFrameConsumesItsChecksumByte() {
    val raw = byteArrayOf(
        0xFF.toByte(), 0x04, 0x01, 0x01, // SOF, v3, FLAG_CHECKSUM, len=1
        0x00, 0x1D, 0x0A, 0x02, 0x00, // vendor, command, payload
        0x7F, // checksum byte
    )
    val decoder = GaiaStreamDecoder()
    val frames = decoder.feed(raw)
    assertEquals(1, frames.size)
    assertEquals(Gaia.FLAG_CHECKSUM, frames[0].flags)
    assertEquals(1, frames[0].payload.size)
    // The checksum byte must be consumed, not parsed as the next frame:
    val next = decoder.feed(Gaia.encode(0, 1, byteArrayOf(1)))
    assertEquals(1, next.size)
}

@Test
fun resyncsAfterGarbage() {
    val garbage = byteArrayOf(0x01, 0x02, 0x03)
    val frame = Gaia.encode(0, GaiaCommands.CORE_GET_VARIANT_NAME)
    val frames = GaiaStreamDecoder().feed(garbage + frame)
    assertEquals(1, frames.size)
    assertEquals(GaiaCommands.CORE_GET_VARIANT_NAME, frames[0].cmd)
}

@Test
fun sofInsideAPayloadDoesNotBreakDecoding() {
    val first = Gaia.encode(0, 1, byteArrayOf(0xFF.toByte(), 0x04))
    val second = Gaia.encode(0, 2, byteArrayOf(9))
    val frames = GaiaStreamDecoder().feed(first + second)
    assertEquals(listOf(1, 2), frames.map { it.cmd })
}
```

Run: `./gradlew :core:protocol:test --tests "org.opendrop.protocol.GaiaTest"`
Expected: PASS — these pin current framing behavior before the refactor in Step 3.

- [ ] **Step 2: Add the failing feature-page cap test**

In `GaiaSessionTest.kt`:

```kotlin
@Test
fun stopsAskingAfterTooManyFeaturePages() {
    session.start()
    buds("ff040004000a830000040301")
    sent.clear()
    repeat(20) {
        buds("ff040005001d01010100020101") // more=1: core v2, earbud v1
    }
    assertTrue(session.state.featuresKnown)
    assertEquals(16, sent.count { it == "ff 04 00 00 00 1d 00 02" })
}
```

Run: `./gradlew :core:protocol:test --tests "org.opendrop.protocol.GaiaSessionTest.stopsAskingAfterTooManyFeaturePages"`
Expected: FAIL — the session keeps asking forever, `featuresKnown` stays false.

- [ ] **Step 3: Implement the page cap**

In `GaiaSession.kt`:

```kotlin
private var featurePages = 0

private fun handle(event: GaiaEvent) {
    when (event) {
        is GaiaEvent.Handshake -> { /* unchanged */ }
        is GaiaEvent.Features -> {
            if (state.featuresKnown) return
            featurePages++
            val features = state.features + event.features
            if (event.more && featurePages < MAX_FEATURE_PAGES) {
                update(state.copy(features = features))
                transmit(GaiaCommands.getSupportedFeaturesNext())
            } else {
                // End of the list — or a misbehaving device that never ends it.
                update(state.copy(features = features, featuresKnown = true))
                queryDevice()
            }
        }
        // ... other branches unchanged ...
    }
}

private companion object {
    /** Link sends at most a few pages; 16 is generous and bounds a stuck device. */
    const val MAX_FEATURE_PAGES = 16
}
```

- [ ] **Step 4: Refactor the decoder to consume by index (kills the O(n²) buffering)**

Replace `GaiaStreamDecoder` in `Gaia.kt`:

```kotlin
class GaiaStreamDecoder {
    private var buf = ByteArray(0)
    private var start = 0

    fun feed(data: ByteArray): List<GaiaFrame> {
        buf += data
        val frames = mutableListOf<GaiaFrame>()
        while (true) {
            val headerStart = indexOfSof()
            if (headerStart < 0) {
                buf = ByteArray(0)
                start = 0
                break
            }
            start = headerStart
            val available = buf.size - start
            if (available < 4) break
            val flags = u8(start + 2)
            val (length, header) = if (flags and Gaia.FLAG_LENGTH_16 != 0) {
                if (available < 5) break
                ((u8(start + 3) shl 8) or u8(start + 4)) to 5
            } else {
                u8(start + 3) to 4
            }
            // A checksummed frame carries one more byte after the payload. It
            // is consumed but not verified: no Moondrop capture uses one, so
            // the algorithm is unknown (MAINTENANCE.md, deferred X1).
            val total = header + 4 + length + if (flags and Gaia.FLAG_CHECKSUM != 0) 1 else 0
            if (available < total) break
            frames += GaiaFrame(
                version = u8(start + 1),
                flags = flags,
                vendor = (u8(start + header) shl 8) or u8(start + header + 1),
                command = (u8(start + header + 2) shl 8) or u8(start + header + 3),
                payload = buf.copyOfRange(start + header + 4, start + header + 4 + length),
            )
            start += total
            if (start > 8_192) { // don't let a long stream grow the buffer forever
                buf = buf.copyOfRange(start, buf.size)
                start = 0
            }
        }
        return frames
    }

    private fun indexOfSof(): Int {
        var i = start
        while (i < buf.size) {
            if (buf[i].toInt() and 0xFF == Gaia.SOF) return i
            i++
        }
        return -1
    }

    private fun u8(i: Int): Int = buf[i].toInt() and 0xFF
}
```

- [ ] **Step 5: Pin the available-EQ count-byte heuristic (F8)**

Add to `GaiaTest.kt` — documents the known ambiguity until another model's capture settles it:

```kotlin
@Test
fun availableEqCountByteHeuristic() {
    // The Space Travel sends "count, ids…"; ids-only payloads are ambiguous
    // (a leading id equal to count-1 looks like a count byte). Pinned until
    // a second model's capture says which reading is right.
    fun ids(hex: String) =
        GaiaParser.parse(GaiaStreamDecoder().feed(hex.hexToBytes()).single())
    assertEquals(GaiaEvent.AvailableEqPresets(listOf(0, 1, 2, 63)), ids("ff040005001d0b01040001023f"))
    assertEquals(GaiaEvent.AvailableEqPresets(listOf(2)), ids("ff040002001d0b01040102"))
}
```

- [ ] **Step 6: Run the whole protocol suite**

Run: `./gradlew :core:protocol:test`
Expected: PASS (capture-based tests prove the refactor didn't change framing).

- [ ] **Step 7: Commit**

```
git add core/protocol/src/main/kotlin/org/opendrop/protocol/Gaia.kt core/protocol/src/main/kotlin/org/opendrop/protocol/GaiaSession.kt core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaTest.kt core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaSessionTest.kt
git commit -m "Protocol: decoder hardening, checksum note and feature-page cap"
```

### Task 5: Surface GAIA errors; drop dead refreshEq (F5, F25)

**Files:**
- Modify: `core/protocol/src/main/kotlin/org/opendrop/protocol/GaiaSession.kt:60-82, 91-114`
- Modify: `app/src/main/java/org/opendrop/app/device/DeviceController.kt:263-273`
- Test: `core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaSessionTest.kt`

**Interfaces:**
- Produces: `GaiaSession(send, onState, onFrame, onError: (GaiaEvent.Error) -> Unit = {})` — new optional last parameter; existing callers compile unchanged.
- Removes: `GaiaSession.refreshEq()` (dead code; grep confirmed no callers).

- [ ] **Step 1: Write the failing test**

In `GaiaSessionTest.kt`:

```kotlin
@Test
fun reportsErrorFrames() {
    val errors = mutableListOf<GaiaEvent.Error>()
    val s = GaiaSession(send = {}, onError = { errors += it })
    s.onBytes("ff040001001d0b8302".hexToBytes()) // ERROR, feature 5, cmd 3 (set EQ)
    assertEquals(1, errors.size)
    assertEquals(GaiaFeature.MUSIC_PROCESSING, errors[0].feature)
    assertEquals(GaiaCommands.MUSIC_SET_EQ, errors[0].cmd)
}
```

Run: `./gradlew :core:protocol:test --tests "org.opendrop.protocol.GaiaSessionTest.reportsErrorFrames"`
Expected: FAIL — `onError` parameter doesn't exist (compile error).

- [ ] **Step 2: Implement, and remove the dead method**

In `GaiaSession.kt`:

```kotlin
class GaiaSession(
    private val send: (ByteArray) -> Unit,
    private val onState: (GaiaDeviceState) -> Unit = {},
    private val onFrame: (incoming: Boolean, frame: GaiaFrame) -> Unit = { _, _ -> },
    /** The device answered an OpenDrop command with a GAIA ERROR frame. */
    private val onError: (GaiaEvent.Error) -> Unit = {},
) {
```

In `handle`:

```kotlin
is GaiaEvent.Error -> onError(event)
GaiaEvent.Other -> Unit
```

Delete `refreshEq()` (lines 80-82) — verified dead: `grep -rn "refreshEq" .` returns only the definition.

- [ ] **Step 3: Log device rejections in the app**

In `DeviceController.connectOnce`, add the callback to the `GaiaSession(...)` construction:

```kotlin
val newSession = GaiaSession(
    send = { bytes -> newLink.write(bytes) },
    onState = { s -> _state.update { it.copy(device = s) } },
    onFrame = ::logFrame,
    onError = { e ->
        logEvent("Device rejected f${e.feature} 0x${"%02x".format(e.cmd)}: ${e.payload.toHex()}")
    },
)
```

(The log line stays English on purpose — see MAINTENANCE.md "Won't fix".)

- [ ] **Step 4: Run both suites and build the app**

Run: `./gradlew :core:protocol:test :app:assembleDebug`
Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```
git add core/protocol/src/main/kotlin/org/opendrop/protocol/GaiaSession.kt core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaSessionTest.kt app/src/main/java/org/opendrop/app/device/DeviceController.kt
git commit -m "Protocol: surface GAIA ERROR frames; remove dead refreshEq"
```

### Task 6: DSP input hardening (F6, F26, F27)

**Files:**
- Modify: `core/dsp/src/main/kotlin/org/opendrop/dsp/AutoEq.kt:28-73`
- Modify: `core/dsp/src/main/kotlin/org/opendrop/dsp/Eq.kt:50-54`
- Test: `core/dsp/src/test/kotlin/org/opendrop/dsp/EqTest.kt`

**Interfaces:**
- Produces: unchanged public API; `AutoEq.parse` now rejects non-finite values, shelves without Q default to 0.70 (upstream AutoEQ behavior), and duplicate GraphicEQ points are pinned as tolerated (F7 was disproven: the interpolation bounds prevent the 0/0 case).

- [ ] **Step 1: Write the failing tests**

In `EqTest.kt`:

```kotlin
@Test
fun rejectsNonFiniteValues() {
    val nines = "9".repeat(400) // parses to Infinity as a double
    assertNull(AutoEq.parse("Filter 1: ON PK Fc $nines Hz Gain 4.0 dB Q 1.0"))
    assertNull(AutoEq.parse("Filter 1: ON PK Fc 1000 Hz Gain $nines.0 dB Q 1.0"))
    assertNull(AutoEq.parse("GraphicEQ: 20 $nines"))
    // A non-finite preamp falls back to the safe preamp instead.
    val eq = AutoEq.parse("Preamp: -$nines dB\nFilter 1: ON PK Fc 1000 Hz Gain 4.0 dB Q 1.0")!!
    near(-4.0, eq.preamp, 0.1)
}

@Test
fun filterMathSurvivesNonFiniteInput() {
    val f = PeqFilter(FilterType.PEAK, Double.NaN, 6.0, 1.0)
    assertTrue(f.responseDb(1_000.0).isFinite())
}

@Test
fun graphicEqToleratesDuplicatePoints() {
    // F7 looked like duplicates could hit 0/0; the bounds f0 < hz <= f1 make
    // that unreachable. This pins the harmless behavior.
    val eq = GraphicEq(0.0, listOf(50.0 to 1.0, 100.0 to 2.0, 100.0 to 3.0, 1_000.0 to 4.0))
    assertEquals(2.0, eq.curveDb(100.0), 0.0)
    assertTrue(eq.curveDb(500.0).isFinite())
}

@Test
fun shelvesDefaultToAutoEqQ() {
    val shelf = AutoEq.parse("Filter 1: ON LSC Fc 105 Hz Gain 6.0 dB") as ParametricEq
    assertEquals(0.70, shelf.filters[0].q, 1e-9)
    val peak = AutoEq.parse("Filter 1: ON PK Fc 105 Hz Gain 6.0 dB") as ParametricEq
    assertEquals(0.707, peak.filters[0].q, 1e-9)
}
```

Run: `./gradlew :core:dsp:test`
Expected: `rejectsNonFiniteValues`, `filterMathSurvivesNonFiniteInput` and `shelvesDefaultToAutoEqQ` FAIL; `graphicEqToleratesDuplicatePoints` passes (characterization).

- [ ] **Step 2: Harden `AutoEq.parse`**

In `AutoEq.kt`:

```kotlin
for (line in text.lineSequence()) {
    preampRegex.find(line)?.let { preamp = it.groupValues[1].toDoubleOrNull()?.takeIf(Double::isFinite) }
    filterRegex.find(line)?.let { m -> parseFilter(m)?.let(filters::add) }
    graphicRegex.find(line)?.let { m -> graphic = parseGraphic(m.groupValues[1]) }
}
// AutoEQ files never mix the two; when a text holds both, the filters win
// and the GraphicEQ line is ignored.
```

```kotlin
private fun parseFilter(m: MatchResult): PeqFilter? {
    if (!m.groupValues[1].equals("ON", ignoreCase = true)) return null
    val type = when (m.groupValues[2].uppercase(Locale.ROOT)) {
        // ... unchanged type mapping ...
    }
    val frequency = m.groupValues[3].toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return null
    val gain = (m.groupValues[4].toDoubleOrNull() ?: 0.0).takeIf(Double::isFinite) ?: return null
    // AutoEQ's default when a filter has no Q: 0.70 for shelves, 0.707 otherwise.
    val q = m.groupValues[5].toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: defaultQ(type)
    return PeqFilter(type, frequency, if (type.hasGain) gain else 0.0, q)
}

private fun defaultQ(type: FilterType): Double =
    if (type == FilterType.LOW_SHELF || type == FilterType.HIGH_SHELF) 0.70 else 0.707
```

```kotlin
private fun parseGraphic(body: String): List<Pair<Double, Double>> =
    body.split(';').mapNotNull { part ->
        val bits = part.trim().split(Regex("\\s+"))
        if (bits.size < 2) return@mapNotNull null
        val hz = bits[0].toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() } ?: return@mapNotNull null
        val db = bits[1].toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@mapNotNull null
        hz to db
    }
```

- [ ] **Step 3: Guard `coefficients`**

In `Eq.kt`, `PeqFilter.coefficients` — NaN would sail through `coerceIn`/`coerceAtLeast`:

```kotlin
private fun coefficients(fs: Double): DoubleArray {
    val g = if (gain.isFinite()) gain else 0.0
    val f = if (frequency.isFinite()) frequency.coerceIn(1.0, fs / 2 - 1) else 1_000.0
    val qv = if (q.isFinite() && q > 0.0) q.coerceAtLeast(0.01) else 0.7
    val a = 10.0.pow(g / 40)
    val w0 = 2 * PI * f / fs
    val cw = cos(w0)
    val alpha = sin(w0) / (2 * qv)
    val sa = 2 * sqrt(a) * alpha
    return when (type) { /* ... unchanged bodies, now using qv ... */ }
}
```

(`GraphicEq.curveDb` needs no guard: its bounds guarantee `f0 < hz ≤ f1`, so the log-ratio denominator is never zero — pinned by the characterization test in Step 1.)

- [ ] **Step 4: Run the DSP suite**

Run: `./gradlew :core:dsp:test`
Expected: PASS (`parsesAutoEqParametric` gives explicit Qs, so the 0.707→0.70 shelf change doesn't touch it; `presetsDontClip` still holds).

- [ ] **Step 5: Commit**

```
git add core/dsp/src/main/kotlin/org/opendrop/dsp/AutoEq.kt core/dsp/src/main/kotlin/org/opendrop/dsp/Eq.kt core/dsp/src/test/kotlin/org/opendrop/dsp/EqTest.kt
git commit -m "DSP: reject non-finite input, AutoEQ shelf Q defaults"
```

### Task 7: Small protocol API guards (F21, F22, F23)

**Files:**
- Modify: `core/protocol/src/main/kotlin/org/opendrop/protocol/Gaia.kt:26-29, 51-57, 119-123`
- Test: `core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaTest.kt`

**Interfaces:**
- Produces: `Gaia.encode` now throws `IllegalArgumentException` for feature/cmd ids over 0x7F (was silent overflow); `GaiaType.of` returns `COMMAND` for unknown ids (was throw); `hexToBytes` fails with a clear `IllegalArgumentException`.

- [ ] **Step 1: Write the failing test**

In `GaiaTest.kt` (import `org.junit.Assert.assertThrows`):

```kotlin
@Test
fun rejectsOutOfBandIdsAndBadHex() {
    assertThrows(IllegalArgumentException::class.java) { Gaia.encode(0x80, 1) }
    assertThrows(IllegalArgumentException::class.java) { Gaia.encode(0, 0x80) }
    assertThrows(IllegalArgumentException::class.java) { "zz".hexToBytes() }
    // Callers mask type ids to 2 bits; anything else falls back, not throws.
    assertEquals(GaiaType.COMMAND, GaiaType.of(9))
}
```

Run: `./gradlew :core:protocol:test --tests "org.opendrop.protocol.GaiaTest.rejectsOutOfBandIdsAndBadHex"`
Expected: FAIL — `encode(0x80, 1)` silently succeeds today.

- [ ] **Step 2: Implement**

In `Gaia.kt`:

```kotlin
fun encode(feature: Int, cmd: Int, payload: ByteArray = ByteArray(0), type: GaiaType = GaiaType.COMMAND): ByteArray {
    require(feature in 0..0x7F) { "feature id $feature doesn't fit the 7-bit field" }
    require(cmd in 0..0x7F) { "command id $cmd doesn't fit the 7-bit field" }
    val command = (feature shl 9) or (type.id shl 7) or cmd
    return frame(VERSION_V3, VENDOR_V3, command, payload)
}
```

```kotlin
companion object {
    /** Frame.type masks ids to 2 bits; anything else falls back to COMMAND. */
    fun of(id: Int): GaiaType = entries.firstOrNull { it.id == id } ?: COMMAND
}
```

```kotlin
fun String.hexToBytes(): ByteArray {
    val clean = filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd hex length" }
    require(clean.matches(Regex("[0-9a-fA-F]*"))) { "not hex: \"$clean\"" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
```

- [ ] **Step 3: Run the full protocol suite**

Run: `./gradlew :core:protocol:test`
Expected: PASS (all real command constants are ≤ 0x7F).

- [ ] **Step 4: Commit**

```
git add core/protocol/src/main/kotlin/org/opendrop/protocol/Gaia.kt core/protocol/src/test/kotlin/org/opendrop/protocol/GaiaTest.kt
git commit -m "Protocol: guard command ids, bad hex and unknown frame types"
```

---

## Phase 3 — Android security and threading

### Task 8: Receiver export flags (F11, F12, F13)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/device/DeviceController.kt:125-133`
- Modify: `app/src/main/java/org/opendrop/app/device/UsbController.kt:60-70`
- Modify: `app/src/main/AndroidManifest.xml:36-42`

**Interfaces:** none — behavior for system broadcasts is unchanged; third-party spoofing stops.

- [ ] **Step 1: Flip both runtime registrations to NOT_EXPORTED**

`DeviceController.kt:131`:

```kotlin
// The battery and volume actions aren't protected broadcasts; NOT_EXPORTED
// still receives the system's copies but ignores third-party spoofs.
ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
```

`UsbController.kt:68` (replace the old comment):

```kotlin
// NOT_EXPORTED: system attach/detach broadcasts still arrive, and the
// permission-grant broadcast is explicit to this package.
ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
```

- [ ] **Step 2: Stop exporting the ACL receiver**

`AndroidManifest.xml:36-42`:

```xml
<receiver
    android:name=".device.AclReceiver"
    android:exported="false">
```

(`ACL_CONNECTED` is a protected broadcast; the system delivers to non-exported receivers, and this receiver is also the process-revival path, which is unaffected.)

- [ ] **Step 3: Build, lint, and check for stragglers**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Then: `grep -rn "RECEIVER_EXPORTED" app/src` → expected: no matches.

- [ ] **Step 4: Manual check (next hardware pass)**

Earbuds out of the case with the app closed → auto-connect still fires (ACL receiver); battery and volume still update on the home screen.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/org/opendrop/app/device/DeviceController.kt app/src/main/java/org/opendrop/app/device/UsbController.kt app/src/main/AndroidManifest.xml
git commit -m "Security: stop exporting receivers for spoofable broadcasts"
```

### Task 9: Automation token (F14)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/settings/AppSettings.kt:9-49`
- Modify: `app/src/main/java/org/opendrop/app/automation/Automation.kt:30, 72-105`
- Modify: `app/src/main/java/org/opendrop/app/ui/SettingsScreen.kt:88-103`
- Modify: `app/src/main/res/values/strings.xml` (+ all nine `values-*/strings.xml`)
- Modify: `docs/automation.md`

**Interfaces:**
- Produces: `Behavior.automationToken: String` (default `""` = accept without token — keeps existing users' automations working); `Automation.EXTRA_TOKEN = "token"`.

- [ ] **Step 1: Add the setting**

In `AppSettings.kt`:

```kotlin
data class Behavior(
    /** Notify once when the earbuds' battery drops to [LOW_BATTERY_PERCENT]. */
    val lowBatteryAlert: Boolean = true,
    /** Accept commands from other apps (Tasker etc.) and broadcast state changes. */
    val automation: Boolean = false,
    /** Shared secret automation commands must carry; empty accepts any (pre-token setups). */
    val automationToken: String = "",
    /** The first-run screen was dismissed. */
    val onboarded: Boolean = false,
)
```

Persist it in `update` (`putString(KEY_AUTOMATION_TOKEN, next.automationToken)`) and load it in the initial `_state` (`prefs.getString(KEY_AUTOMATION_TOKEN, "") ?: ""`), with `private const val KEY_AUTOMATION_TOKEN = "automation_token"`.

- [ ] **Step 2: Require the token in the receiver**

In `Automation.kt`, add next to `EXTRA_PRESET`:

```kotlin
/** [ACTION_*]: the shared secret from Settings; required once the user has one. */
const val EXTRA_TOKEN = "token"
```

In `AutomationReceiver.onReceive`, after the automation-off check:

```kotlin
val behavior = app.settings.state.value
if (!behavior.automation) { /* existing ignore-and-return */ }
val expected = behavior.automationToken
if (expected.isNotEmpty() && intent.getStringExtra(Automation.EXTRA_TOKEN) != expected) {
    Log.w(TAG, "Ignored ${intent.action}: wrong automation token")
    return
}
```

- [ ] **Step 3: Generate and show the token in Settings**

In `SettingsScreen.kt`, the automation toggle (line ~94):

```kotlin
onCheckedChange = { on ->
    onChange {
        when {
            on && it.automationToken.isEmpty() -> it.copy(automation = true, automationToken = newToken())
            !on -> it.copy(automation = false)
            else -> it.copy(automation = true)
        }
    }
}
```

with a private helper (imports `java.util.UUID`):

```kotlin
private fun newToken(): String = UUID.randomUUID().toString().replace("-", "").take(8)
```

Inside the existing `AnimatedVisibility(behavior.automation)` block, below `automationHelp()`:

```kotlin
Text(
    stringResource(R.string.automation_token, behavior.automationToken),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
)
```

- [ ] **Step 4: Add the string in all ten files**

`values/strings.xml`:

```xml
<string name="automation_token">Commands must carry this token: an extra named \"token\" with the value %1$s. Any app that knows it can switch your EQ, so keep it private.</string>
```

Then add the same key to each `values-*/strings.xml` (mirroring the file's existing style):

| Locale | Value |
|---|---|
| `values-de` | `Befehle müssen dieses Token enthalten: ein Extra namens \"token\" mit dem Wert %1$s. Jede App, die es kennt, kann deine EQ wechseln – halte es geheim.` |
| `values-es` | `Los comandos deben llevar este token: un extra llamado \"token\" con el valor %1$s. Cualquier app que lo conozca puede cambiar tu EQ; mantenlo en privado.` |
| `values-fr` | `Les commandes doivent contenir ce jeton : un extra nommé « token » avec la valeur %1$s. Toute application qui le connaît peut changer votre EQ ; gardez-le privé.` |
| `values-ja` | `コマンドにはこのトークンが必要です。「token」という名前の extra に %1$s を設定してください。トークンを知るアプリは EQ を切り替えられるので、公開しないでください。` |
| `values-ko` | `명령에 이 토큰이 포함되어야 합니다: \"token\"이라는 이름의 extra에 %1$s 값을 넣으세요. 이 값을 아는 앱은 EQ를 변경할 수 있으니 비공개로 유지하세요.` |
| `values-pt` | `Os comandos devem levar este token: um extra chamado \"token\" com o valor %1$s. Qualquer app que o conheça pode mudar o seu EQ; mantenha-o privado.` |
| `values-ru` | `Команды должны содержать этот токен: extra с именем \"token\" и значением %1$s. Любое приложение, которое его знает, может переключить ваш эквалайзер — держите его в секрете.` |
| `values-ar` | `يجب أن تحمل الأوامر هذا الرمز: إضافة باسم \"token\" بقيمة %1$s. أي تطبيق يعرفه يمكنه تغيير معدِّل الاستدراك، فاحتفظ به سرًّا.` |
| `values-zh-rCN` | `命令必须携带此令牌：添加一个名为 \"token\" 的 extra，值为 %1$s。任何知道它的应用都能切换你的 EQ，请保密。` |

- [ ] **Step 5: Update `docs/automation.md`**

Add a `token` row to the commands table and an extra to the Tasker example:

```markdown
Commands sent while a token is set (Settings → Automation shows it) must also
carry a `token` extra with that value; commands without it are ignored.

| Action | Extras | Does |
|---|---|---|
| `org.opendrop.app.action.SET_EQ` | `preset` …, `token` | Switches the EQ preset |
| `org.opendrop.app.action.NEXT_EQ` | `token` | Switches to the next preset |
| `org.opendrop.app.action.CONNECT` | `token` | Connects to the remembered earbuds |
| `org.opendrop.app.action.DISCONNECT` | `token` | Disconnects … |
```

and in the Tasker example: `Extra: preset:basshead` → `Extras: preset:basshead` / `token:<the value from Settings>`.

- [ ] **Step 6: Build and lint**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Manual check (next hardware pass)**

Automation on → command with the token switches the preset; same command with a wrong token is ignored; a pre-existing install upgraded with automation already on keeps working without a token until it's re-toggled.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/org/opendrop/app/settings/AppSettings.kt app/src/main/java/org/opendrop/app/automation/Automation.kt app/src/main/java/org/opendrop/app/ui/SettingsScreen.kt app/src/main/res/values*/strings.xml docs/automation.md
git commit -m "Automation: shared-secret token for broadcast commands"
```

### Task 10: Import off the main thread + truncation notice (F15, F32)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/ui/PhoneEqScreen.kt:89-94, 163, 174-191`
- Modify: `app/src/main/res/values/strings.xml` (+ all nine `values-*/strings.xml`)

**Interfaces:**
- Produces: none — internal to `PhoneEqScreen`.

- [ ] **Step 1: Read and parse on Dispatchers.IO**

In `PhoneEqScreen.kt`:

```kotlin
val scope = rememberCoroutineScope()
val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
        scope.launch(Dispatchers.IO) {
            val text = readText(context, uri) // now returns ImportedText
            val result = importCurve(context.resources, text, onCustom)
            withContext(Dispatchers.Main) { message = result }
        }
    }
}
```

Imports: `androidx.compose.runtime.rememberCoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.launch`, `kotlinx.coroutines.withContext`.

- [ ] **Step 2: Report truncation instead of silently cutting the file**

Replace `readText` and `importCurve`:

```kotlin
private data class ImportedText(val text: String?, val truncated: Boolean)

private fun readText(context: Context, uri: Uri): ImportedText = runCatching {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
        val buffer = CharArray(MAX_IMPORT_CHARS + 1)
        val n = reader.read(buffer)
        when {
            n <= 0 -> ImportedText(null, truncated = false)
            n > MAX_IMPORT_CHARS -> ImportedText(String(buffer, 0, MAX_IMPORT_CHARS), truncated = true)
            else -> ImportedText(String(buffer, 0, n), truncated = false)
        }
    } ?: ImportedText(null, truncated = false)
}.getOrDefault(ImportedText(null, truncated = false))

private fun importCurve(res: Resources, imported: ImportedText, onCustom: (EqCurve) -> Unit): String {
    val curve = imported.text?.let(AutoEq::parse)
        ?: return res.getString(R.string.peq_import_invalid)
    onCustom(curve)
    return buildString {
        append(
            when (curve) {
                is ParametricEq -> res.getString(R.string.peq_imported_parametric, curve.filters.size, fmt(curve.preamp))
                is GraphicEq -> res.getString(R.string.peq_imported_graphic, curve.points.size, fmt(curve.preamp))
            },
        )
        if (imported.truncated) append(' ').append(res.getString(R.string.peq_import_truncated, MAX_IMPORT_CHARS / 1_000))
    }
}
```

Update the clipboard paste call site (~line 164) to wrap the text: `importCurve(context.resources, ImportedText(clipboard.getText()?.text?.take(MAX_IMPORT_CHARS), truncated = false), onCustom)`.

- [ ] **Step 3: Accept octet-stream files**

Line ~163:

```kotlin
OutlinedButton(onClick = { import.launch(arrayOf("text/*", "application/octet-stream")) }) { Text(stringResource(R.string.peq_import_file)) }
```

- [ ] **Step 4: Add the truncation string in all ten files**

`values/strings.xml`:

```xml
<string name="peq_import_truncated">Only the first %1$d KB could be read; the curve may be cut off.</string>
```

| Locale | Value |
|---|---|
| `values-de` | `Nur die ersten %1$d KB konnten gelesen werden; die Kurve ist möglicherweise abgeschnitten.` |
| `values-es` | `Solo se pudieron leer los primeros %1$d KB; la curva puede estar cortada.` |
| `values-fr` | `Seuls les %1$d premiers Ko ont pu être lus ; la courbe est peut-être tronquée.` |
| `values-ja` | `最初の %1$d KB しか読み込めませんでした。カーブが途切れている可能性があります。` |
| `values-ko` | `처음 %1$d KB만 읽을 수 있었습니다. 커브가 잘렸을 수 있습니다.` |
| `values-pt` | `Só foi possível ler os primeiros %1$d KB; a curva pode estar cortada.` |
| `values-ru` | `Удалось прочитать только первые %1$d КБ; кривая может быть обрезана.` |
| `values-ar` | `تمت قراءة أول %1$d كيلوبايت فقط؛ وقد يكون المنحنى مقطوعًا.` |
| `values-zh-rCN` | `只能读取前 %1$d KB；曲线可能被截断。` |

- [ ] **Step 5: Build, lint, manual check**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Manual (any device): import a >64 KB AutoEQ file → note appears; import a normal file → unchanged behavior; UI stays responsive while a large file parses.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/org/opendrop/app/ui/PhoneEqScreen.kt app/src/main/res/values*/strings.xml
git commit -m "Phone EQ: parse imports off the main thread and warn on truncation"
```

### Task 11: Phone EQ engine off the main thread (F16)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/phoneeq/PhoneEqService.kt:47-60`

- [ ] **Step 1: Run the watcher's binder work on Dispatchers.Default**

```kotlin
if (watcher == null) {
    val eq = PhoneEqEngine().also { engine = it }
    watcher = scope.launch(Dispatchers.Default) {
        phoneEq.state.collect { state ->
            if (!state.enabled) {
                withContext(Dispatchers.Main) { stop() }
            } else {
                // DynamicsProcessing creation/config is binder work; keep it
                // off the main thread so 64-band updates can't jank the UI.
                phoneEq.failed.value = !eq.apply(state.curve)
            }
        }
    }
}
```

Import `kotlinx.coroutines.withContext` (`Dispatchers` is already imported).

- [ ] **Step 2: Build, lint, manual check**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Manual (next hardware pass): toggle the phone EQ on and drag a filter slider — the UI must stay smooth and the EQ audibly tracks.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/org/opendrop/app/phoneeq/PhoneEqService.kt
git commit -m "Phone EQ: apply the effect off the main thread"
```

### Task 12: Transport hardening (F28, F29, F38)

**Files:**
- Modify: `core/transport-classic/src/main/kotlin/org/opendrop/transport/classic/RfcommLink.kt:12-46`
- Modify: `core/transport-usb/src/main/kotlin/org/opendrop/transport/usb/UsbProbe.kt:82-89`
- Modify: `app/src/main/java/org/opendrop/app/device/DeviceController.kt:92-93` (if Task 2 hasn't already added `@Volatile`)

- [ ] **Step 1: Keep the actionable cause when the fallback is unavailable**

In `RfcommLink.kt`:

```kotlin
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT first
class RfcommLink(private val device: BluetoothDevice) : GaiaLink {
    @Volatile private var socket: BluetoothSocket? = null

    /** Connects via the SPP service record; falls back to channel 1 (verified on Space Travel). */
    override fun connect() {
        socket = try {
            open(device.createRfcommSocketToServiceRecord(SPP_UUID))
        } catch (first: IOException) {
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                open(method.invoke(device, FALLBACK_CHANNEL) as BluetoothSocket)
            } catch (fallback: Exception) {
                // Hidden-API fallback unavailable (or it failed too): surface
                // the original SPP failure — it carries the actionable cause
                // that LinkError classifies.
                throw IOException(first.message ?: first.javaClass.simpleName, first)
            }
        }
    }
    // open/write/read/close unchanged
```

- [ ] **Step 2: Raise the HID report-descriptor cap**

In `UsbProbe.kt` companion:

```kotlin
/** HID report descriptors are usually <1 KB; 16 KB covers exotic ones. */
const val MAX_REPORT_DESCRIPTOR = 16_384
```

- [ ] **Step 3: Verify and commit**

Run: `./gradlew :core:protocol:test :app:assembleDebug :app:lintDebug`
Manual (next hardware pass): normal connect still works; with Bluetooth off, the error shown is still the classified "Bluetooth turned off…" one.

```
git add core/transport-classic/src/main/kotlin/org/opendrop/transport/classic/RfcommLink.kt core/transport-usb/src/main/kotlin/org/opendrop/transport/usb/UsbProbe.kt
git commit -m "Transports: keep the original connect error, volatile socket, larger HID cap"
```

---

## Phase 4 — Build and dependency maintenance

*Do the code phases first; this churn makes regressions harder to spot.*

### Task 13: Gradle DSL modernization (F39, F40)

**Files:**
- Modify: `app/build.gradle.kts:60-62`, `core/transport-classic/build.gradle.kts:19-21`, `core/transport-usb/build.gradle.kts:19-21`
- Modify: `core/protocol/build.gradle.kts`, `core/dsp/build.gradle.kts` (add `jvmToolchain`)
- Modify: `gradle.properties`

- [ ] **Step 1: Replace `kotlinOptions` with `compilerOptions` in the three Android modules**

In each of `app/build.gradle.kts`, `core/transport-classic/build.gradle.kts`, `core/transport-usb/build.gradle.kts`:

- Delete the `kotlinOptions { jvmTarget = "17" }` block from inside `android {}`.
- Add at the top of the file: `import org.jetbrains.kotlin.gradle.dsl.JvmTarget`
- Add after the `android {}` block:

```kotlin
kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
```

- [ ] **Step 2: Pin the toolchain in the two JVM modules**

In `core/protocol/build.gradle.kts` and `core/dsp/build.gradle.kts`:

```kotlin
kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
```

- [ ] **Step 3: Turn on build caching and parallel execution**

Append to `gradle.properties`:

```properties
org.gradle.parallel=true
org.gradle.caching=true
```

- [ ] **Step 4: Verify and commit**

Run: `./gradlew :core:protocol:test :core:dsp:test :app:assembleDebug :app:lintDebug`
Expected: everything passes with no `kotlinOptions` deprecation warnings.

```
git add app/build.gradle.kts core/*/build.gradle.kts gradle.properties
git commit -m "Build: compilerOptions DSL, JVM toolchains, parallel+caching"
```

### Task 14: SDK 36 and dependency refresh (F18, F19)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts:9,14`, `core/transport-classic/build.gradle.kts:8`, `core/transport-usb/build.gradle.kts:8`
- Modify: `.github/workflows/android.yml` (only if the runner lacks platform 36)

**Interfaces:** none — version bumps only.

- [ ] **Step 1: Bump the version catalog**

Set each entry to the newest stable available at execution time (check the Gradle Plugin Portal / Google Maven; do **not** use pre-release versions). **Floors** (minimums, not targets):

| Entry | Floor | Why |
|---|---|---|
| `agp` | 8.9.1 | compileSdk 36 support |
| `kotlin` | 2.1.0 | the compose-compiler plugin follows the Kotlin version |
| `coroutines` | 1.10.1 | bug fixes |
| `coreKtx` | 1.16.0 | built against SDK 36 |
| `activityCompose` | 1.10.0 | |
| `lifecycle` | 2.9.0 | |
| `composeBom` | 2025.05.00 | |
| `datastore` | 1.1.1 | current |

Also check whether the current Gradle wrapper (8.14.3) satisfies the new AGP; if AGP requires a newer Gradle, run `./gradlew wrapper --gradle-version <newest AGP-compatible>` and commit the wrapper change in this task.

- [ ] **Step 2: Bump the SDK levels**

`app/build.gradle.kts`: `compileSdk = 36`, `targetSdk = 36`. Both transport modules: `compileSdk = 36`. (`minSdk` stays 26 everywhere.)

- [ ] **Step 3: Verify the whole pipeline**

Run: `./gradlew :core:protocol:test :core:dsp:test :app:assembleDebug :app:lintDebug`
Fix whatever the new versions flag (expect at most deprecation warnings; any error gets fixed in this task, not deferred). Read the target-36 behavior-change notes for anything affecting: foreground services, broadcast receivers, notifications — all three were reviewed as safe in the 2026-10-08 pass.

If CI fails on a missing platform, add to `android.yml` before the test step:

```yaml
      - name: Install Android platform 36
        run: yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" > /dev/null || true
```

- [ ] **Step 4: Manual smoke test (emulator is fine)**

Install the debug APK: launch, connect flow to the picker, phone EQ screen, settings. Push a `v*`-less branch and let CI produce the debug APK artifact once.

- [ ] **Step 5: Commit**

```
git add gradle/libs.versions.toml app/build.gradle.kts core/transport-classic/build.gradle.kts core/transport-usb/build.gradle.kts .github/workflows/android.yml gradle/wrapper
git commit -m "Build: SDK 36 and dependency refresh"
```

### Task 15: R8 for release builds (F20)

**Files:**
- Create: `app/proguard-rules.pro`
- Modify: `app/build.gradle.kts:42-47`

- [ ] **Step 1: Enable minification**

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        // Debug-signed when no release key is set (commit f85e3c8's intent),
        // so the artifact installs for smoke tests. F-Droid rebuilds anyway.
        signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
    }
}
```

- [ ] **Step 2: Create the rules file**

`app/proguard-rules.pro`:

```proguard
# OpenDrop has no reflection over its own classes; manifest-referenced
# components (services, receivers, widgets, the tile) are kept by AAPT rules,
# and every library ships its own consumer rules. Add -keep rules here if a
# minified build ever drops something.
```

- [ ] **Step 3: Verify a minified build and smoke-test it**

Run: `./gradlew :app:assembleRelease :app:lintRelease`
Install the (debug-signed) release APK: launch, open the phone EQ, add a widget, toggle the QS tile, check the low-battery channel — anything R8 could have stripped is in those paths.

- [ ] **Step 4: Commit**

```
git add app/proguard-rules.pro app/build.gradle.kts
git commit -m "Build: shrink and obfuscate release builds"
```

---

## Phase 5 — Quality and UX polish

### Task 16: Shared presentation helpers, threshold dedupe, USB title (F36, F41, F42)

**Files:**
- Create: `app/src/main/java/org/opendrop/app/device/Presentation.kt`
- Modify: `app/src/main/java/org/opendrop/app/widget/EqWidget.kt:37-51`, `app/src/main/java/org/opendrop/app/widget/BatteryWidget.kt:31-45`, `app/src/main/java/org/opendrop/app/ui/Hero.kt:54, 215`
- Modify: `app/src/main/java/org/opendrop/app/device/UsbController.kt:36, 85`, `app/src/main/java/org/opendrop/app/ui/HomeScreen.kt:262`, `app/src/main/java/org/opendrop/app/ui/UsbDeviceScreen.kt:37, 111`
- Modify: `app/src/main/res/values/strings.xml` (+ all nine `values-*/strings.xml`)

**Interfaces:**
- Produces: `Presentation.device(s: UiState): PairedDevice?`, `Presentation.eqPresets(s: UiState): List<EqPreset>`, `Presentation.isDisconnected(s: UiState): Boolean`; `UsbEntry.title: String?` (was non-null with a hardcoded English fallback).

- [ ] **Step 1: Create the shared presenter**

`Presentation.kt`:

```kotlin
package org.opendrop.app.device

import org.opendrop.protocol.EqPreset

/**
 * The UiState-to-display rules shared by the widgets, the tile and the
 * connection notification, so they can't drift apart (MAINTENANCE F41).
 * Each surface keeps its own fallback strings and layout.
 */
object Presentation {
    /** The device a widget, tile or notification should talk about. */
    fun device(s: UiState): PairedDevice? = s.selected ?: s.autoConnect

    /** Presets worth showing buttons or a switcher for; only while connected. */
    fun eqPresets(s: UiState): List<EqPreset> =
        if (s.connection == Connection.Connected) s.device.switchableEqPresets else emptyList()

    /** Picked, but not linked (or the last link failed). */
    fun isDisconnected(s: UiState): Boolean =
        s.connection == Connection.Disconnected || s.connection is Connection.Failed
}
```

- [ ] **Step 2: Use it in the widgets**

`EqWidget.Content.of` (lines 38, 40-41):

```kotlin
val name = Presentation.device(s)?.name ?: "OpenDrop"
val presets = Presentation.eqPresets(s)
val disconnected = Presentation.isDisconnected(s)
```

(add `import org.opendrop.app.device.Presentation`). `BatteryWidget.WidgetContent.of` line 32: `val name = Presentation.device(s)?.name ?: "OpenDrop"`. The tile keeps its own richer state mapping (its states differ by design); the notification's name only shows while a connection is active, when `selected` is always set — no change there.

- [ ] **Step 3: Dedupe the 20 % threshold**

In `Hero.kt`: delete `private const val LOW_BATTERY = 20` (line 54) and use `AppSettings.LOW_BATTERY_PERCENT` at line 215 (`if (level <= AppSettings.LOW_BATTERY_PERCENT)`), importing `org.opendrop.app.settings.AppSettings`.

- [ ] **Step 4: Localize the USB title fallback**

In `UsbController.kt`:

```kotlin
/** Name for lists and screens; null when only the id is known (UIs fall back to a resource). */
val title: String? get() = info.model?.name ?: info.product
```

and line 85: `thenBy { it.title ?: "" }`.

Call sites:
- `HomeScreen.kt:262`: `title = entry.title ?: stringResource(R.string.usb_device_id, entry.info.id)`
- `UsbDeviceScreen.kt:37`: `ScreenTopBar(entry?.let { it.title ?: stringResource(R.string.usb_device_id, it.info.id) } ?: stringResource(R.string.usb_device), onBack)`
- `UsbDeviceScreen.kt:111` (report subject; English on purpose — see MAINTENANCE.md): `"OpenDrop USB report: ${entry.title ?: "USB device ${entry.info.id}"}"`

`values/strings.xml`: `<string name="usb_device_id">USB device %1$d</string>` — and in each `values-*/strings.xml` mirror the existing `usb_device` entry with the id appended:

| Locale | Value |
|---|---|
| `values-de` | `USB-Gerät %1$d` |
| `values-es` | `Dispositivo USB %1$d` |
| `values-fr` | `Appareil USB %1$d` |
| `values-ja` | `USB デバイス %1$d` |
| `values-ko` | `USB 기기 %1$d` |
| `values-pt` | `Dispositivo USB %1$d` |
| `values-ru` | `USB-устройство %1$d` |
| `values-ar` | `جهاز USB %1$d` |
| `values-zh-rCN` | `USB 设备 %1$d` |

- [ ] **Step 5: Verify and commit**

Run: `./gradlew :app:assembleDebug :app:lintDebug` — then `grep -rn "\"USB device" app/src` → no matches.

```
git add app/src/main/java/org/opendrop/app/device/Presentation.kt app/src/main/java/org/opendrop/app/device/UsbController.kt app/src/main/java/org/opendrop/app/widget/EqWidget.kt app/src/main/java/org/opendrop/app/widget/BatteryWidget.kt app/src/main/java/org/opendrop/app/ui/Hero.kt app/src/main/java/org/opendrop/app/ui/HomeScreen.kt app/src/main/java/org/opendrop/app/ui/UsbDeviceScreen.kt app/src/main/res/values*/strings.xml
git commit -m "UI: shared UiState presenter, one battery threshold, localized USB title"
```

### Task 17: UX and hygiene batch (F17, F31, F33, F34, F37)

**Files:**
- Modify: `app/src/main/java/org/opendrop/app/ui/PhoneEqScreen.kt:263-267, 272-291, 295, 325-333`
- Modify: `app/src/main/java/org/opendrop/app/settings/Appearance.kt:86-114`
- Modify: `app/src/main/java/org/opendrop/app/device/DeviceController.kt:76-79, 367-370`
- Modify: `app/src/main/java/org/opendrop/app/phoneeq/PhoneEq.kt:41-52`, `app/src/main/java/org/opendrop/app/settings/AppSettings.kt:34-42`

- [ ] **Step 1: Put the graph labels under their gridlines (F17)**

In `PhoneEqScreen.kt`, replace the `Row(...) { listOf("20 Hz", ...) }` (lines 263-267) with a custom layout, and keep the same label texts:

```kotlin
AxisLabels()
```

```kotlin
/** Labels centered under their gridlines; the end labels sit at the edges. */
@Composable
private fun AxisLabels() {
    val colors = MaterialTheme.colorScheme
    val span = ln(MAX_HZ) - ln(MIN_HZ)
    fun position(hz: Double) = ((ln(hz) - ln(MIN_HZ)) / span).toFloat()
    val labels = listOf(
        "20 Hz" to 0f,
        "100" to position(100.0),
        "1 kHz" to position(1_000.0),
        "10 kHz" to position(10_000.0),
        "±${GRAPH_RANGE_DB.toInt()} dB" to 1f,
    )
    Layout(
        content = {
            labels.forEach { (text, _) ->
                Text(text, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { i, placeable ->
                val x = (labels[i].second * width - placeable.width / 2)
                    .roundToInt()
                    .coerceIn(0, width - placeable.width)
                placeable.placeRelative(x, 0)
            }
        }
    }
}
```

Imports: `androidx.compose.ui.layout.Layout`, `kotlin.math.roundToInt`.

- [ ] **Step 2: Stop dropping the first Appearance tap (F31)**

In `Appearance.kt`:

```kotlin
private var pending: ((Appearance) -> Appearance)? = null

init {
    viewModelScope.launch {
        _state.value = store.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()
            .toAppearance()
        // A tap that landed before the load applies now; last one wins.
        pending?.let { p -> pending = null; update(transform = p) }
    }
}

fun update(debounce: Boolean = false, transform: (Appearance) -> Appearance) {
    val current = _state.value ?: run {
        pending = transform
        return
    }
    val next = transform(current)
    // ... unchanged from here ...
}
```

- [ ] **Step 3: Symmetric slider rounding (F33)**

In `PhoneEqScreen.kt`, replace the four `Math.round(...)` slider conversions with `roundToInt()` (HALF_EVEN, symmetric around zero):
- line ~280: `onPreamp(Math.round(it * 2) / 2.0)` → `onPreamp((it * 2).roundToInt() / 2.0)`
- line ~325: `Math.round(fromLogPosition(it, MIN_HZ, MAX_HZ)).toDouble()` → `fromLogPosition(it, MIN_HZ, MAX_HZ).roundToInt().toDouble()`
- line ~329: `Math.round((it * 24 - 12) * 2) / 2.0` → `((it * 24 - 12) * 2).roundToInt() / 2.0`
- line ~333: `Math.round(fromLogPosition(it, 0.1, 10.0) * 100) / 100.0` → `(fromLogPosition(it, 0.1, 10.0) * 100).roundToInt() / 100.0`

- [ ] **Step 4: Thread-safe clock for the packet log (F34)**

In `DeviceController.kt`, replace the per-line `SimpleDateFormat` with one immutable formatter (java.time; minSdk 26):

```kotlin
private val logClock: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

private fun log(text: String) {
    _state.update { it.copy(log = (it.log + "${logClock.format(Instant.now())} $text").takeLast(LOG_LINES)) }
}
```

Imports: `java.time.Instant`, `java.time.ZoneId`, `java.time.format.DateTimeFormatter`; drop `java.text.SimpleDateFormat` and `java.util.Date`.

- [ ] **Step 5: Stale KDoc and threading contracts (F37 + MAINTENANCE note)**

`DeviceController.kt:76-79` — "Screens, the foreground service and (later) tiles and widgets all read…" → "Screens, the foreground service, the tile and the widgets all read…".

One-line KDoc on `PhoneEq.update` and `AppSettings.update`: `/** Main-thread only; the read-modify-write isn't atomic across threads. */`

- [ ] **Step 6: Verify and commit**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Manual (any device/emulator): open the phone EQ screen — the "100"/"1 kHz"/"10 kHz" labels sit under their gridlines; open Appearance immediately after a cold start and tap a theme — it applies.

```
git add app/src/main/java/org/opendrop/app/ui/PhoneEqScreen.kt app/src/main/java/org/opendrop/app/settings/Appearance.kt app/src/main/java/org/opendrop/app/device/DeviceController.kt app/src/main/java/org/opendrop/app/phoneeq/PhoneEq.kt app/src/main/java/org/opendrop/app/settings/AppSettings.kt
git commit -m "UI: aligned graph labels, first-tap fix, log clock, rounding"
```

---

## After all phases

- [ ] Tick the fixed findings (`F1`-`F45` as applicable) in `docs/MAINTENANCE.md`, and move anything that surfaced new work into its section.
- [ ] Add the accumulated manual checks (Tasks 2, 3, 8, 9, 11, 12) to the ROADMAP's hardware test checklist under the matching milestone.
