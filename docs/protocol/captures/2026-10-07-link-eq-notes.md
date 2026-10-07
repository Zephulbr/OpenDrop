# Timed action notes (phone clock, = UTC; automated over adb from a PC)

Capture covers 20:09:31 - 20:24:23. Only rows available in MOONDROP Link (2.26.1c-260930ai) were run.
The Link app on this earbud only offers: device screen (volume slider, EQ Tuning, User Guide), Product, Sleep, Settings tabs.
No ANC, game mode, or gesture screens were seen. Physical rows (case open/close, taps) were NOT done.

| Time | Action | Matching traffic in spacetravel-capture.csv |
|---|---|---|
| 20:12:09 | Opened Link app, device card shows "Version: 1.0.0" | frames 721-750 (handshake; reply to 000a/1d 01 05 = ASCII "1.0.0") |
| 20:12:48 | Tapped device card (device screen: volume slider, EQ Tuning, User Guide) | frames 827-837 |
| 20:13:08 | Opened EQ Tuning (Reference "In Use", Basshead, Monitor) | frames 843/846: query `0a02`, reply `0b02 00`... |
| 20:13:32 | Tapped Basshead "Use": confirm dialog "Apply this configuration? Switching may cause a brief pop or restart. Lower the volume first." -> Cancelled (no change) | none (no traffic) |
| 20:18:03 | EQ -> Basshead (Use + Confirm) | frame 861 `ff04 0001 001d 0a03 01`; reply 866/867 |
| 20:18:25 | EQ -> Monitor | frame 876 `... 0a03 02`; reply 879/880 |
| 20:18:47 | EQ -> Reference (original restored) | frame 886 `... 0a03 00`; reply 889/890 |
| 20:20:12 | Volume slider dragged lower (Link slider = phone AVRCP absolute volume) | AVRCP SetAbsoluteVolume 20% (not in CSV; not SPP) |
| 20:20:31 | Slider dragged back | AVRCP 26% |
| 20:23:xx | Slider dragged up to original | AVRCP 33% (= original) |
| 20:24 | Closed Link app (home + force-stop). Earbuds left connected, still in ears-off state | - |

Not done: earbuds in/out of case, one-earbud case test, tap gestures, ANC, game mode, gestures (not in Link), Settings/Sleep tabs.
