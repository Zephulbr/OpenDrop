# Moondrop USB DAC protocols

Source: MOONDROP Link **2.26.2c-261008ai** (versionCode 100037), APK set
`MOONDROP_2.26.2c-261008ai_100037.apks`, base APK plus the arm64 split.
Everything here is from static analysis of the Android Java layer (jadx
1.5.6); **no USB capture has been made yet**. Treat writes as unsafe until a
capture confirms them.

Status tags:

- **App** — read directly from Link 2.26.2c code (class/method names below).
- **Wire** — seen in a USB capture. None yet.
- **Inferred** — consistent with the code but not directly observed.

Link treats USB DACs as five independent protocol families. The family is
chosen from Moondrop's product catalogue (`chipType` / function list), not
probed from the device, except for the Synopsys/Conexant family which has a
VID/PID and USB-string test.

| Family | Link methods | Link control surface | Catalogue models |
|---|---|---|---|
| Comture | `comture*` | DAC filter, gain level, LED, volume | Dawn 3.5 / 4.4 / Pro, Moonriver2 Ti, Discdream |
| Jiu | `getJiuEq`, `setJiuEq`, `saveJiuRegs` | 5-band PEQ | CDSP, CHU2 DSP, JIU, Old Fashioned |
| SPV | `getSpv*` / `setSpv*` | full PEQ, DAC/ADC gain, filters, LED, sensor, ANC, battery, prompt tones, spatial audio, buttons | Dawn Pro 2, Moonriver 3, FreeDSP Mini/Pro, Rays, Marigold, DHA15, ... |
| Synopsys / Conexant | `SvcModClient` / `com.conexant.*` | EQ presets and 9-band PEQ, filter, feature flags, firmware update | DUSK-SP, ECHO-B, FreeDSP, MAY, Click, Starlight |
| Jieli / Bluetrum USB | `jieli*`, `bluetrum*` | firmware update only in the Java layer | Echo-BP, GM01 Pro, Gaming Maestro, Nicebuds DSP, MM3A |

The same app also has a `com.moondroplab.communication.usb.media.UsbMediaBridgeService`
that plays local audio through a USB device. That is Android USB audio, not a
vendor control protocol.

**OpenDrop status (2026-10-17):** the app identifies the family from the
catalogue name, the Synaptics vendor/product ids, the `CAPE` string and the
HID report descriptor (report id `0x4B` for SPV, usage page `0xFF02` for
Jieli), all with standard read-only requests, and shows it in the USB report.
It sends no vendor command on the default path. With developer options on,
the USB device screen offers the Comtrue get commands (`A3` filter/gain/LED,
`A2` volume) as console reads; no set command is sent. Other families and
reversible writes are planned behind developer options (Tier 2 of
[the 2.26.2c plan](../superpowers/plans/2026-10-17-moondrop-2.26.2c-feature-tiers.md));
flash, NVM and OTA commands are out of scope. To help, record a capture:
[usb-capture-guide.md](usb-capture-guide.md).

## Common USB mechanics

- Android USB host; the app requests USB permission with the standard
  `UsbManager` flow. `AndroidManifest.xml` requires `android.hardware.usb.host`.
- Settings interfaces are **HID class 3** (subclass 0, protocol 0). Link reads
  the HID report descriptor (`bmRequestType 0x81`, `bRequest 6`,
  `wValue 0x2200`, `wIndex` = interface, up to 4096 bytes) and looks for
  interrupt IN/OUT endpoint pairs. It prefers endpoints numbered 4
  ("app-command EP4") and otherwise takes the first pair.
- Some families use the HID interrupt endpoints; some use control transfers.
  Link retries transfers (typically 3 times, 5–100 ms apart) and may poll both
  the chosen endpoint and other interrupt-IN endpoints of the same interface.
- The volume/gain commands below change the **device's own** volume or gain;
  Android's media stream volume is separate.
- All multi-byte values below are **little-endian** unless stated otherwise.

## Comture

Transport: **vendor control transfers** on the HID interface.

Original Dawn Pro (device report, 2026-10-18,
`captures/2026-10-18-dawn-pro-usb-report.txt`): USB id `2fc6:f06a`,
manufacturer `MOONDROP`, product `MOONDROP Dawn Pro`, bcdDevice 100.107.
UAC2 audio plus one HID interface (2) with 8-byte interrupt endpoints OUT
`0x05` / IN `0x86`.

| Direction | `bmRequestType` | `bRequest` | `wValue` | `wIndex` | Length |
|---|---|---|---|---|---|
| OUT | `0x43` | `0xA0` | `0` | `0x09A0` | 7 |
| IN | `0xC3` | **`0xA1`** | `0` | `0x09A0` | 7 |

Correction (2026-10-18, **Reported**): an earlier reading of Link had `0xA0`
for the IN request too. On the original Dawn Pro, IN with `0xA0` stalls
(Android returns -1) while the OUT is accepted. The MIT-licensed
[mdrop](https://github.com/frahz/mdrop) tool, which works with the Dawn Pro,
reads with `bRequest 0xA1` and writes 3-byte packets (`C0 A5 <cmd>[ <value>]`)
with no wait between OUT and IN. Facts only; no code borrowed. Value meanings
from mdrop (Reported, not yet checked here):

**Verified (2026-10-18, original Dawn Pro, OpenDrop console):** OUT `0x43/0xA0`
with the 7-byte packet, a 100 ms pause, then IN `0xC3/0xA1`,
answers both gets. Replies are 7 bytes, `A0 A5 <cmd>` then data:
`A0 A5 A3 03 01 02 00` (filter 3, gain 1, LED 2) and `A0 A5 A2 00 58 00 00`
(volume raw `0x58`). The byte positions match Link and mdrop. Changing
volume with the DAC's buttons, and the LED and gain, showed up in the next
read (owner check, same day), so the volume, gain and LED fields are
**Verified**. Filter names are still per mdrop.

- Volume raw: `0x00` loudest to `0x70` quietest (not `100 - volume`).
- Filter: 0 fast roll-off low latency, 1 fast roll-off phase-compensated,
  2 slow roll-off low latency, 3 slow roll-off phase-compensated,
  4 non-oversampling.
- Gain: 0 low, 1 high.
- LED: 0 on, 1 off until replug, 2 off.

App evidence: `UsbMessageTransmitter` (`hb5.x`/`hb5.o`) and
`DacCommandHandler` (`l01`).

### Packet

Seven bytes:

```
C0 A5 <cmd> <value> 00 00 00
```

`C0 A5` is the fixed prefix. The command byte is one of:

| Command | Meaning | Value byte |
|---|---|---|
| `01` | set DAC filter | filter index |
| `02` | set gain level | gain index |
| `04` | set volume | `0` → `0xFF`, otherwise `100 - volume` |
| `06` | set LED state | state |
| `A2` | get volume | ignored |
| `A3` | get filter + gain + LED | ignored |

Get replies use the same 7-byte buffer:

- `A3`: `[3]` = DAC filter, `[4]` = gain level, `[5]` = LED state.
- `A2`: `[4]` = raw volume; `0xFF` means 0, otherwise volume = `100 - raw`.

The app sends the packet, waits 100 ms, then reads 7 bytes. There is no
checksum in the packet.

## Jiu

Transport: 11-byte OUT and 11-byte IN transfers on the selected endpoint
pair (the app calls `bulkTransfer`; the endpoint descriptor type is not fully
traced). It uses a 300 ms timeout and retries up to three times.

### Packet

`l01` builds an 11-byte buffer:

```
4B <sub> 00 00 00 <cmd> 00 <d0> <d1> <d2> <d3>
```

- `[0] = 0x4B` (fixed marker)
- `[1] = subcommand/band selector`
- `[5] = command`: `0x52` read, `0x57` write, `0x53` save
- bytes `[7..10]` are command data

### 5-band PEQ

Read (`cmd 0x52`):

| `[1]` | Reply fields |
|---|---|
| `0x26 + 2*band` | `[7]` = gain × 10 (signed byte), `[9..10]` = frequency (u16) |
| `0x27 + 2*band` | `[7..8]` = Q × 1000 (u16) |

Write (`cmd 0x57`):

| Packet | `[1]` | Data |
|---|---|---|
| gain/frequency | `0x26 + 2*band` | `[7]` = gain × 10 signed, `[8]` = `0xFF` if negative else `0`, `[9..10]` = frequency u16 |
| Q | `0x27 + 2*band` | `[7..8]` = Q × 1000 u16 |

The app writes both packets per band, with 100 ms between writes. A valid
read reply is 11 bytes with `[5] == 0x52`.

Save (`cmd 0x53`): `[1] = 0`, no data.

The 5 bands are band 0..4, so `[1]` ranges `0x26..0x2E` and `0x27..0x2F`.

## SPV

Transport: **HID reports**, report ID `0x4B` (75), 64 bytes.

The normal settings path uses the interface's interrupt OUT/IN endpoints
(asynchronous `UsbRequest`, 500 ms, three tries). The update/OTA path uses
control transfers on the same interface instead:

| Direction | `bmRequestType` | `bRequest` | `wValue` | `wIndex` | Length |
|---|---|---|---|---|---|
| OUT (SET_REPORT) | `0x21` | `0x09` | `0x024B` | interface id | 64 |
| IN (GET_REPORT) | `0xA1` | `0x01` | `0x014B` | interface id | 64 |

App evidence: `SpvProtocol` (`lq4`), `UsbMessageTransmitter` (`hb5`),
`HidControlTransfer` (`pr6`). The `u` flag in `hb5` selects the control-transfer
path; it is used for update mode/OTA, not the ordinary settings page.

### Frame

```
4B <type> <cmd> <len> <payload...> 00...   (padded to 64)
```

- `[0] = 0x4B`
- `[1] = 0x01` for a set request, `0x80` for a get request/response
- `[2] = command`
- `[3] = payload length` (0..60)
- `[4..] = payload`

A response carries the same command in `[2]`; the app matches it before
parsing. The 64-byte report is padded with zeros.

### Command map (App)

All commands are 8-bit. "Get" means request type `0x80` (`[1]=0x80`); "Set"
means `[1]=0x01`.

| Cmd | Get | Set payload | Meaning |
|---|---|---|---|
| `0x01` | — | none | save PEQ to flash |
| `0x02` | Q8.8 ADC volume | Q8.8 at `[4..5]`; or mic index u16 at `[4..5]` + Q8.8 gain at `[6..7]` | ADC volume / mic gain |
| `0x03` | Q8.8 DAC gain | Q8.8 at `[4..5]` | DAC gain |
| `0x04` | — | none | save DAC/ADC settings to flash |
| `0x05` | — | `[4]=3` | reset EQ |
| `0x09` | PEQ band | PEQ band (below) | get/set PEQ band |
| `0x0A` | — | `[4]=leftMask`, `[5]=rightMask` | update EQ params to registers |
| `0x0C` | ASCII firmware version | — | firmware version string |
| `0x0F` | selected EQ index | `[4]=index` | get/select EQ set |
| `0x11` | CS43131 filter type | `[4]=1..5` | CS43131 filter |
| `0x17` | — | none | reset factory settings |
| `0x18` | DAC LED enabled | `[4]=0/1` | DAC LED switch |
| `0x1D` | CS43131 working mode | `[4]=mode` | CS43131 working mode |
| `0x23` | Q8.8 pre-gain | Q8.8 at `[4..5]` | PEQ pre-gain |
| `0x24` | 5-byte button actions | 5 bytes | button actions (nibbles) |
| `0x25` | ANC mode | `[4]=0xFF`, `[5]=mapped mode` | ANC mode |
| `0x26` | preset gain index | `[4]=0..2` | preset gain |
| `0x27` | battery info | — | battery |
| `0x28` | LED state | `[4]=0/1` | LED |
| `0x29` | sensor state | `[4]=0/1` | wear/light sensor |
| `0x2A` | spatial state | `[4]=enabled`, `[5]=head-tracking state` | spatial audio |
| `0x2B` | prompt tone info | `[4]=enabled`, `[5]=volume`, `[6]=conf` | prompt tones |
| `0x2C` | SKU/SN info | — | color, language, serial |
| `0x2D` | audio resource index | `[4]=index` | audio resource |
| `0x2E` | power/standby timeouts | 4 bytes (below) | power/standby timeouts |
| `0x2F` | — | none | physical shutdown |
| `0x30` | L/R swapped | `[4]=0/1` | L/R channel swap |
| `0x31` | dongle status | — | dongle connected status |
| `0x32` | anti-wind | `[4]=0/1` | ANC v3 anti-wind |
| `0x33` | ANC v3 mode | `[4]=0..4` | ANC v3 mode |
| `0x34` | dual-mic ENC | `[4]=0/1` | dual-mic ENC |
| `0x35` | ANC v3 switch config | 5 bytes | ANC v3 button order |
| `0x36` | — | none | clear dongle headset pairing |
| `0x60` | — | none | finish web PEQ write |
| `0xFF` | — | none | enter update mode |

Notes:

- **Q8.8** = signed 16-bit little-endian, value / 256.
- **ANC mode (`0x25`)**: the app's UI modes 0..5 map to firmware modes
  `[1, 2, 4, 3, 5, 6]` and are sent as `[4]=0xFF, [5]=mapped`.
- **Power timeouts (`0x2E`)**: `[enabled][minutes][standbyEnabled][standbyMinutes]`.
- **Spatial (`0x2A`)**: get returns `[enabled][headTrackingState]`;
  head-tracking state is 0..2.
- **Button actions (`0x24`)**: 5 bytes = 10 nibbles, high nibble first.
  The app maps the five bytes to single, double, triple, "ones", "threes"
  gestures and each nibble to left/right.
- **ANC v3 switch config (`0x35`)**: five bytes `state, ancOn, ancOff, tp,
  order`; the first four are 0, 1 or `0xFF`, `order` is 0..5 or `0xFF`.
- **Battery (`0x27`)**: two formats. If the payload is at least 5 bytes and
  bytes 1..3 are zero, it is two u32 LE values at 0 and 4 (left, right).
  Otherwise it is six bytes `leftEnabled, left, rightEnabled, right,
  caseEnabled, case`. Levels are 0..100; 101 means unavailable.
- **SKU/SN (`0x2C`)**: `[color][language][serial ASCII]`.
- **Reset/factory**: `0x05` resets EQ (`[4]=3`), `0x17` resets factory
  settings, `0x36` clears dongle pairing.

### PEQ (`0x09`)

The band payload has two forms.

Short form (5-band devices):

```
[4]=0, [5]=band,
[8..9]=frequency u16,
[10..11]=Q Q8.8,
[12..13]=gain Q8.8,
[14]=filter type
```

Long form (8-band devices): the app writes computed biquad coefficients at
`[8..27]`, then repeats the parameters at `[28..34]`:

```
[28..29]=frequency, [30..31]=Q, [32..33]=gain, [34]=filter type, [36]=7
```

Get replies are decoded by trying the long block at offset 28 first, then the
short block at offset 8. A block is `freq u16, Q Q8.8, gain Q8.8, filter u8`.
For the long form, the app also checks `[35]==0` and `[36]` in `{7,9}`.

Related commands:

- `0x0F` select EQ set; `0x01` save it.
- `0x26` set preset gain index (0..2).
- `0x0A` update band registers; the app sends masks for the first 8 bands
  (`0xFF` for 8 or more).

The app reads the requested band count (`w95.k`; the catalogue lists 8 bands
for SPV models) and enables the first 8 bands with the register mask.

### Update mode / firmware

`0xFF` puts the device in update mode, then the app runs the `ab_usb_ota`
read-only probe and a vendor OTA. The OTA transport is not documented here.

## Synopsys / Conexant (Freeman)

This family is the old `com.conexant.*` / `com.syna.*` code and is also called
"Freeman", "Freeman2", "Freeman3" or `CAPE` in the app.

### Identification

- USB string descriptor index **41** must contain `CAPE`
  (`FREEMAN3_ID_STRING`). The app reads it with a standard string-descriptor
  control transfer (`0x80`, `GET_DESCRIPTOR (6)`, `wValue 0x0300 | 41`,
  `wIndex 0x0409`), UTF-16LE.
- `res/xml/devicelist.xml`: vendor id **13784** (`0x35D8`), product ids
  **5270..5276** (`0x1496..0x149C`).
- HID interface: class 3, subclass 0, protocol 0, interface id 3.
  Interface id 4 indicates a dual-interface device.

### Transfers

| Direction | `bmRequestType` | `bRequest` | `wValue` | `wIndex` | Length |
|---|---|---|---|---|---|
| OUT (SET_REPORT) | `0x21` | `0x09` | `0x0201` (output report 1) | 3 | 10 + 4·N |
| IN (GET_REPORT) | `0xA1` | `0x01` | `0x0105` (feature report 5) | 3 | same buffer |

App evidence: `CafCmdHelper`, `UsbHelper`, `FreemanCnxtUsbDevice`.

### Command frame

`CnxtUsbCommand` builds:

```
[0..1]  sequence (u16 LE)
[2..5]  header (u32 LE):
          bits 0..7   = number of 32-bit data words (N)
          bits 16..30 = command id (15 bits)
          bit 31      = reply flag
[6..9]  app_module_id (u32 LE)
[10..]  N × u32 LE data words
```

`app_module_id` for `"CTRL"` is `0xB32D2300` (bytes `00 23 2D B3` in the
frame). `getCafId` packs four ASCII characters as
`(c0-32)<<8 | (c1-32)<<14 | (c2-32)<<20 | (c3-32)<<26`.

A reply is accepted when `[5] & 0x80` is set (reply bit) and the word count is
non-negative. Data starts at byte 10; each parameter is read as a 32-bit LE
word at `10 + 4*i`, or as a byte at `10 + i`.

### Commands

| Cmd | Name | Data | Reply |
|---|---|---|---|
| `0x103` (259) | GET_VERSION | `{1}` → chip code; `{0}` → firmware version | chip code at `[10]`; version bytes at `[10]`, `[14]`, `[18]`, `[22]` (major/minor/patch/build) |
| `0x15A` (346) | GET_PARAMETER_VALUE | `{param}` | parameter value; see below |
| `0x05A` (90) | SET_PARAMETER_VALUE | `{param, value}` | ack |
| `0x0BB` (187) | set EQ config bypass | `{0}` | ack |
| `0x0BC` (188) | set EQ enable | `{1}` | ack |
| `0x0BE` (190) | set EQ band | `{0, band, gain, B0, B1, B2, A0, A1, 0...}` | ack |
| `0x0DC` (220) | save EQ / coefficients | see below | ack |
| `0x1BA` (442) | get EQ config | none | sample-rate index at `[10]` |
| `0x1BE` (446) | get EQ band | `{0, band}` | sample rate `[10]`, band `[14]`, freq `[18]`, Q×256 `[22]`, filter `[26]`, gain `[30]`; coefficient view: gain `[18]`, B0/B1/B2/A0/A1 at `[22]/[26]/[30]/[34]/[38]` |
| `0x1DD` (477) | get EQ param list | `{band 1..9}` | sample rate `[10]`, band `[14]`, freq `[18]`, Q×256 `[22]`, filter `[26]`, gain (signed) `[30]` |

Parameter ids for `0x15A`:

| Param | Meaning | Reply |
|---|---|---|
| 62 | current sample rate | u32 at `[14]` |
| 90 | saved EQ mode index | u32 at `[14]` |
| 84 | feature availability bitfield | byte at `[14]` |
| 64 | feature enabled bitfield | byte at `[14]` |

Feature bits (availability 84 / enabled 64): bit 0 feature control, bit 1
diagnose, bit 2 HiFi FM, bit 3 dongle L/R detect, bit 4 LPM, bit 5 EQ in
firmware. The enabled mask starts at bit 1.

### EQ format and flash save

- EQ band parameters use `freq` in Hz, `Q × 256`, filter type, and gain in
  dB × 256.
- The app converts to biquad coefficients (`B0, B1, B2, A0, A1`) and a gain
  byte with `Eq2Coeff`; 16-bit and 24-bit variants exist. Coefficients are
  signed integers.
- `0x0BE` writes one band in RAM. `0x0DC` is the flash path:
  1. For custom EQ, send `{0, band, freq, Q×256, filter, gain, 0...}` for
     each band.
  2. For each sample-rate index 4..8 (44.1, 48, 96, 192, 384 kHz), send
     `{rateIndex, band, gain, B0, B1, B2, A0, A1, 0...}`.
  3. Send `{255}` to commit.
- `SET_PARAMETER_VALUE` param 90 switches the saved EQ mode; the mode index is
  read back with param 90.
- EQ enable is checked by reading feature report 5 (bit 1) and written with
  `0x0BC`.

### Firmware update

`com.syna.FirmwareUpdate` (`libCxAudioHidLib`) performs the update; the Java
side only calls `connectSynaDevice`, `claimSynaDeviceInterface`,
`downloadFirmware`, `deviceReset`. It temporarily disconnects the app's HID
claim and reconnects after the reset. The HID update report format is not
documented here.

## Jieli USB / Bluetrum USB

These are firmware-update transports, not settings protocols:

- **Jieli USB**: HID collection with usage page `0xFF02`; dedicated session,
  file extension `.ufw`, re-attach handling. The normal Jieli Bluetooth
  settings still use GAIA.
- **Bluetrum USB**: HID collection with usage page `0xFF90`; dedicated
  session, `bluetrum-usb-ota.bin`.
- **SPV**: update mode `0xFF` followed by the `ab_usb_ota` probe and OTA.
- **Synopsys**: native Syna update above.

## Open questions

- All of this is **App** only. A USB capture is needed before OpenDrop writes
  anything, especially SPV PEQ and Synopsys flash writes.
- The `Comture` family's catalogue says 5 EQ bands but the Java code only
  exposes filter/gain/LED/volume. PEQ may live in the Dart layer or be absent.
- The Jiu reply header (`[0]`, `[2..4]`, `[6]`) is not fully decoded; only
  `[5]`, `[7..10]` are used by the app.
- SPV Q8.8 ranges and filter-type enums need a capture; the app only clamps a
  few values.
- The Synopsys `0x0DC` coefficient order is confirmed by the reader offsets,
  but the exact rounding in `Eq2Coeff` is native.
