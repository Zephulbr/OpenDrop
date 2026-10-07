# Capture guide: Moondrop Space Travel + MOONDROP Link

This guide records the Bluetooth traffic between the MOONDROP Link app and
the Space Travel earbuds, so we can confirm the protocol in
[space-travel.md](space-travel.md).

Time needed: about 30–45 minutes.

## What you need

- An Android phone with the Space Travel paired and the MOONDROP Link app installed.
- A computer with `adb` (Android platform-tools) and Wireshark 4.x.
- A USB cable, USB debugging enabled on the phone.
- Optional: the **nRF Connect** app (Nordic Semiconductor).
- A notepad (or the phone's clock app) to write down action times.

## Privacy first

An HCI snoop log records **all** Bluetooth traffic on the phone while it's on:
other headphones, watches (including notification text), car kits, and
Bluetooth MAC addresses.

- Disconnect or turn off other Bluetooth devices during the capture.
- **Don't upload the raw log to GitHub.** Step 6 extracts only the earbud
  control traffic, which is safe to share.
- Turn the snoop log off when you're done.

## Step 1: Device info (no capture needed)

With the earbuds connected:

```sh
adb shell dumpsys bluetooth_manager > bt_dumpsys.txt
```

Search the file for the Space Travel entry and copy:
- the device name as shown,
- its list of **UUIDs** (service UUIDs from the SDP record).

Don't share the whole file; it contains every paired device.

Also note:
- Phone model and Android version.
- Link app version (Settings → Apps → MOONDROP Link).
- Earbud firmware version, if Link shows it.

## Step 2: Optional GATT survey (nRF Connect)

1. Open nRF Connect → Scanner. Look for an entry for the Space Travel (it may
   advertise under its name or show up as an unnamed device near the phone).
2. If you find one, Connect, then screenshot the list of services and
   characteristics.
3. We're especially looking for:
   - `00001100-d102-11e1-9b23-00025b00a5a5` (GAIA over GATT)
   - `9eca0000-7f3a-4f32-9a38-a91b2c6e0100` (Bluetrum private)
4. Disconnect nRF Connect before continuing.

Not finding anything is a useful result too: it means the earbuds are
RFCOMM-only.

## Step 3: Turn on HCI snoop logging

1. Settings → About phone → tap **Build number** 7 times to unlock Developer options.
2. Settings → System → Developer options → **Enable Bluetooth HCI snoop log**.
   - If it offers **Disabled / Filtered / Enabled**, pick **Enabled**.
     "Filtered" strips the payloads we need.
3. Turn Bluetooth **off and on again** (some phones need a reboot) so logging starts.
4. Close Gadgetbridge or any other app that may talk to the earbuds.

## Step 4: Run the action script

Do the actions in order, **one at a time**, with **at least 10 seconds**
between them. For each, write down the clock time (to the second) and what you did.
If something in the list isn't in the Link app, write "not available" and move on.
That's useful information too.

| # | Action |
|---|---|
| 1 | Earbuds in the case, lid closed. Note the time. |
| 2 | Open the case, take the earbuds out, wait until connected. |
| 3 | Open the Link app and wait for it to show the device. |
| 4 | Open each screen in Link once (device info, EQ, gestures, settings). Note firmware version if shown. |
| 5 | EQ: switch to each preset in turn, e.g. Reference → Basshead → Monitor → back to the original. One action per row in your notes. |
| 6 | ANC: if Link has ANC controls, switch Off → ANC → Transparency → Off. |
| 7 | ANC by touch: long-press (3 s) an earbud, wait, repeat until you've cycled all modes. Note which mode you hear each time. |
| 8 | Game mode: 4× tap to turn it on, wait, 4× tap to turn it off. |
| 9 | Gestures: for **one** slot (e.g. left double-tap), change the action to each available option, one at a time. Note the option names exactly as Link shows them. |
| 10 | Gestures: if there's a "None"/"Disabled" option, set it on one slot. |
| 11 | Gestures: restore your original mapping. |
| 12 | Any other setting Link offers (prompts, multipoint, name, reset to default…): change it once and change it back. **Skip firmware update and factory reset.** |
| 13 | Put one earbud back in the case, wait, take it out. |
| 14 | Close the Link app. Disconnect the earbuds (put both in the case). |

Tip: play some music during steps 5–8 so you can hear the changes, and note
anything surprising.

## Step 5: Pull the log

Easiest, works on most phones:

```sh
adb bugreport bugreport.zip
```

Unzip it and find `btsnoop_hci.log`. It's usually under
`FS/data/misc/bluetooth/logs/`. On some phones it's under `FS/data/log/bt/`
or `FS/sdcard/`. Search the zip for `btsnoop`.

Then **turn off** "Enable Bluetooth HCI snoop log" again.

## Step 6: Extract only the earbud traffic

Open `btsnoop_hci.log` in Wireshark.

1. Find the earbuds' connection: Statistics → Conversations → Bluetooth tab.
2. Apply a display filter to see only control traffic:
   ```
   btrfcomm.channel == 1 || btspp || btatt
   ```
   If nothing shows up, try just `btrfcomm`.
3. Check: you should see payloads starting with `FF` or `00 1D`.
4. File → Export Specified Packets → "All displayed" → save as
   `spacetravel-capture.pcapng`.

Or with `tshark` (bundled with Wireshark), producing a plain-text dump:

```sh
tshark -r btsnoop_hci.log \
  -Y "btrfcomm.channel == 1 || btspp || btatt" \
  -T fields -E separator=, \
  -e frame.time -e hci_h4.direction -e btspp.data -e btatt.value \
  > spacetravel-capture.csv
```

(`hci_h4.direction`: `0x00` = phone → earbuds, `0x01` = earbuds → phone.)

## Step 7: Share

Send:
- `spacetravel-capture.csv` (or `.pcapng`),
- your timed action notes from Step 4,
- the info from Step 1 (UUID list, versions),
- nRF Connect screenshots if you did Step 2.

Put them in `docs/protocol/captures/` in a commit, or attach them to the PR.
Only the extracted files, never the full bugreport or raw `btsnoop_hci.log`.
