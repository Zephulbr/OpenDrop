# Moondrop Space Travel (original): protocol notes

Status: **prior-art summary, not yet verified on our hardware.** Every item
below is tagged with where it comes from. Nothing moves to "verified" until we
see it in our own HCI capture (see [capture-guide.md](capture-guide.md)).

Legal note: we only use protocol *facts* (frame layouts, command numbers)
from other projects. Several of them are GPL/AGPL, so we do **not** copy their
source code into OpenDrop. Our implementation is written from these notes.

## Summary

Despite the Bluetrum BT8892E chip, the Space Travel speaks Qualcomm's **GAIA**
control protocol (Bluetrum firmware implements a GAIA-compatible protocol,
likely so Moondrop could reuse one app across models).

| Item | Value | Source | Status |
|---|---|---|---|
| Control protocol | GAIA V3 | Gadgetbridge [GB] | Reported |
| Transport | Bluetooth Classic RFCOMM, channel 1 | [GB] | Reported |
| Byte order | Big-endian | [GB] | Reported |
| Vendor ID | `0x001D` (QTIL V3) | [GB], [HP] | Reported |
| Features in Gadgetbridge | EQ preset, touch actions | [GB] | Reported |
| Battery | **Not** sent over GAIA; sent over HFP with `AT+IPHONEACCEV` | [GB] | Reported |
| Possible parallel protocol | Bluetrum "9ECA" private GATT service | [HP] | Unknown for ST1 |

[GB] Gadgetbridge, "Add support for Moondrop Space Travel" (Codeberg PR #3857)
and gadgetbridge.org/gadgets/headphones/moondrop/. AGPL-3.0.
[HP] HyperPods-for-Moondrop `PROTOCOL.md` (github.com/huime180/HyperPods-for-Moondrop),
GPL-3.0. Lists Space Travel 1 as "profile-inferred, unverified".
[MC] moondrop-control (github.com/FEAKEuser/moondrop-control), GPL-3.0.

## GAIA PDU

```
+-----------+-------------------+-------------+
| vendor    | command value     | payload ... |
| u16 BE    | u16 BE            |             |
+-----------+-------------------+-------------+

command value = (feature << 9) | (type << 7) | (command & 0x7F)
type: 0 = command, 1 = notification, 2 = response, 3 = error
```

Example: `00 1D 10 03` = vendor 0x001D, feature 8, type 0 (command), command 3.

Version probe (V1/V2 vendor 0x000A) [HP]:
`TX 00 0A 03 00` → `RX 00 0A 83 00 [status][protocol][major][minor]`

## RFCOMM transport framing

Over RFCOMM, each PDU is wrapped [HP], [MC]:

```
0xFF | version (1) | flags (1) | length (1 or 2) | PDU | [checksum (1)]
flags: 0x01 = checksum present, 0x02 = 2-byte length
length = payload length (excludes the 4 bytes of vendor + command value)
```

Some devices send raw `00 1D ...` PDUs without the `0xFF` wrapper [HP].
**To verify on ST1:** which framing, which version byte, checksum or not.

## Features (from other Moondrop models)

These IDs come from other Moondrop GAIA devices [HP]. Whether ST1 implements
each one is an open question. Phase 0 checks each one with read-only "get"
commands only.

| Feature | ID | Commands (hex PDU) | Relevance to our feature list |
|---|---|---|---|
| Basic | 0 | capabilities, notification registration | Device info, firmware version? |
| ANC V1 | 2 | get `00 1D 04 01`, set `00 1D 04 02 [0/1]` | ANC on/off |
| Audio curation | 8 | get `00 1D 10 03`, set `00 1D 10 04 [bitmask 01/02/04]` | Off / ANC / Transparency |
| Battery | 13 (0x0D) | types `00 1D 1A 00`, levels `00 1D 1A 01 [types]` | L/R/case battery (GB says ST1 doesn't use it) |
| Voice prompts | 14 (0x0E) | get `00 1D 1C 01`, set `00 1D 1C 02 [en][vol][idx]` | Prompt on/off, volume |
| DAC gain | 15 (0x0F) | | Probably not on ST1 |
| Codec type | 16 (0x10) | get `00 1D 20 05`, set `00 1D 20 06 [0/1]` (LHDC) | Not applicable: ST1 is SBC/AAC |
| Dual device | 20 (0x14) | get `00 1D 28 01`, set `00 1D 28 02 [0/1]`, list `00 1D 28 05` | Multipoint toggle, device list |
| ANC V2 | 32 (0x20) | get `00 1D 40 03`, set `00 1D 40 04 [mode 0-5]` | Off / ANC / Transparency (newer models) |

**Not yet documented anywhere we can reach:**
- EQ preset command (Gadgetbridge implements it for ST1)
- Touch-action command and action ids (Gadgetbridge implements it for ST1)
- Game mode
- Any lock or "no action" gesture value

These are the main targets for our first capture.

## Battery

Reported by [GB]: ST1 sends battery with the standard HFP `AT+IPHONEACCEV`
command. Android already reads this (it's what shows in Bluetooth settings).
That is one combined level, not separate left/right.

Options for OpenDrop:
1. Read Android's stored level for the device. The getter is a hidden API, so
   it needs reflection; check it works on current Android versions.
2. Try GAIA feature 13 anyway, in case ST1 answers it.
3. Accept a single combined value for ST1.

## Codec

ST1 supports SBC and AAC only. Codec choice is made by Android's A2DP stack,
not over GAIA. See the roadmap for the Shizuku / Developer Options approach.

## Open questions (Phase 0 checklist)

- [ ] Confirm RFCOMM channel 1 and the SPP/GAIA service UUID in the SDP record.
- [ ] Confirm framing (`0xFF` wrapper, version byte, checksum flag).
- [ ] Capture EQ preset change: feature id, command, values for each preset.
- [ ] Capture touch-action change: slots (L/R × gesture), action ids, any "none".
- [ ] Capture ANC mode change from the Link app (if it exposes it) and from the
      earbud long-press (look for a notification).
- [ ] Capture game mode toggle (4× tap): is there a notification?
- [ ] Check whether ST1 also exposes the 9ECA GATT service or the GAIA GATT
      service (`00001100-d102-11e1-9b23-00025b00a5a5`).
- [ ] Read-only probes for features 0, 2, 8, 13, 14, 20, 32.
