# Moondrop GAIA features (13 to 35)

Commands for Moondrop's own GAIA v3 features (vendor `0x001D`), from static
analysis of MOONDROP Link 2.26.1c for Android. Facts only, no code copied.

**Status: unverified.** None of this has been seen on the wire yet. The Space
Travel advertises none of these features, so it can't be checked on our test
unit. Before OpenDrop sends any **set** command below, it needs a capture from
a model that has the feature. Read commands are lower risk but still need a
capture to confirm the reply format.

Framing is the usual GAIA v3 one ([space-travel.md](space-travel.md)):
`command = (feature << 9) | (type << 7) | cmd`. "Notif" is a notification the
device sends after `register notification` for that feature.

## Battery (13)

| Cmd | Type | Payload | Meaning |
|---|---|---|---|
| 0 | get | none | Which batteries exist. Reply: one byte per battery id |
| 1 | get | (inferred: battery ids) | Levels. Reply: pairs of `id, level` |
| 0, 1 | notif | same as the replies | Batteries or levels changed |

Battery ids: `0` single device, `1` left, `2` right, `3` charging case.
This would give separate left / right / case levels on models that have it.

## ANC v3 (33)

| Cmd | Type | Payload | Meaning |
|---|---|---|---|
| 3 | get | none | Mode. Reply: 1 byte, 0 to 4 |
| 4 | set | 1 byte mode | Set mode |
| 5 | get | none | Anti-wind. Reply: 1 byte, 0 or 1 |
| 6 | set | 1 byte | Set anti-wind |
| 41 | get | none | "Switch configuration". Reply: 5 bytes |
| 42 | set | 5 bytes | Set switch configuration |
| 1 | notif | 1 byte | Mode changed |
| 2 | notif | 5 bytes | Switch configuration changed |
| 3 | notif | 1 byte | Anti-wind changed |

Modes (from the app's enum names, inferred): `0` off, `1` ANC,
`2` transparency, `3` anti-wind, `4` adaptive. The 5-byte switch
configuration is probably which modes the press-and-hold gesture cycles
through; its layout is not known.

ANC (2) and ANC v2 (32) are older variants; not analysed yet.

## Touch controls v2 (22)

| Cmd | Type | Payload | Meaning |
|---|---|---|---|
| 1 | get | none | Current actions. Reply: 5 bytes |
| 2 | get | none | Default actions. Reply: 5 bytes |
| 3 | set | 5 bytes | Set actions |

The 5 bytes are 10 nibbles (high nibble first), one action per gesture slot.
Which slot is which gesture, and the action codes, are not known. The app's
action names are: play/pause, previous, next, volume up, volume down,
answer/hang up, reject call, start voice assistant, cancel voice assistant,
ANC on, ANC off.

## Touch controls v4 (31)

| Cmd | Type | Payload | Meaning |
|---|---|---|---|
| 1 | get | none | Knock state |
| 2 | set | 1 byte | Set knock state |
| 3 | get | none | Default actions (same 10-nibble layout as v2) |
| 4 | get | none | Current actions |
| 5 | set | 5 bytes | Set actions |

Touch controls v3 (26) uses a different structure; not analysed yet.

## Find my earbuds (34)

| Cmd | Type | Payload | Meaning |
|---|---|---|---|
| 1 | set | 1 byte | Start (the byte is probably which earbud) |
| 2 | set | none | Stop (inferred) |

## Other features, set commands seen

Only the command ids and payload sizes are known so far:

| Feature | Cmd | Payload |
|---|---|---|
| Voice prompts (14) | get 1; set 2 | set: `01, index` or 3 bytes |
| DAC gain (15) | set 2 | 1 byte |
| Codec (16) | set 3, 4, 6 | 1 byte each (LDAC / LHDC related) |
| Wear sensor (17) | set 2 | 1 byte |
| Spatial audio (18) | set 2, 4 | 1 byte each (enable, head tracking) |
| LED (19) | set 2 | 1 byte |
| Multipoint (20) | set 2, 4, 7 | 1 byte, 1 byte, n bytes |
| Prompt sounds (23) | set 2 | 1 byte |
| Auto power-off (25) | set 2, 4 | n bytes |
| Dynamic bass (27) | set 2 | 1 byte, 0 or 1 |
| Left/right swap (30) | set 2 | 1 byte |
| Music processing (5) | 5, 6, 7, 8 | user EQ writes (n bytes) |

## How to verify

With a model that advertises the feature (Device info lists them):

1. Record an HCI snoop log while using the setting in MOONDROP Link
   ([capture guide](capture-guide.md)).
2. Decode it with `python3 tools/gaia_decode.py`.
3. Mark each row here as verified, add the capture as a test fixture, then
   implement it in `GaiaSession`.
