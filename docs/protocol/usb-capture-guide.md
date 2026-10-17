# Capture guide: Moondrop USB DACs and DSP cables

The USB control protocols in [usb-dac.md](usb-dac.md) come from MOONDROP
Link's code only (**App** evidence). OpenDrop won't send a vendor command to a
USB device until a capture shows the official software doing the same thing
(roadmap principle 2). This guide records that traffic.

Time needed: about 30 minutes per device.

## What you need

- The DAC or DSP cable, and the OpenDrop **USB report** for it (Home → the
  device → Share USB report). It says which family OpenDrop thinks it is.
- Software that changes the device's settings: MOONDROP Link on Android, or a
  Moondrop desktop/web tool if your device has one.
- A way to record USB traffic between that software and the device:
  - **Linux PC:** `usbmon` + Wireshark.
  - **Windows PC:** [USBPcap](https://desowin.org/usbpcap/) + Wireshark.
  - **Android:** only on a rooted phone (`usbmon` through `adb shell`). An
    unrooted phone can't record its own USB traffic; use a PC tool instead.

## Privacy first

A USB capture records every device on the same bus: keyboards (including what
you type), webcams, storage.

- Unplug other USB devices from that bus, or capture only the DAC's bus.
- Don't type passwords while recording.
- Before sharing, keep only the DAC's traffic (filter below) and remove
  serial-number replies (SPV command `0x2C`, string descriptors with a serial).

## Step 1: Find the device

Linux:

```sh
lsusb                       # note the bus number and the vendor:product id
sudo modprobe usbmon
```

Windows: open the USBPcap tool once to see which root hub the DAC is on.

## Step 2: Start recording

- Linux: Wireshark on `usbmonN` (N = bus number), or
  `sudo dumpcap -i usbmonN -w dac.pcapng`.
- Windows: Wireshark on the `USBPcapN` interface for that root hub.

Display filter for one device: `usb.idVendor == 0x<vendor id>` (from
`lsusb` or the USB report's "USB id") to find it while it enumerates, then
`usb.device_address == <address>`.

## Step 3: Run the action script

Write down the time of each action. **One change per capture file** is
easiest to decode; if that's too slow, wait 5 seconds between actions.

1. Plug in the device (records the descriptors).
2. Open the settings software and wait until it shows the device.
3. Change **one** setting, then change it back:

| Family | Actions |
|---|---|
| SPV (Dawn Pro 2, Moonriver 3, FreeDSP Mini/Pro, ...) | LED off/on; DAC filter 1 → 2 → 1; read battery (if any); switch EQ preset and back |
| Comtrue (Dawn 3.5 / 4.4 / Pro, Moonriver 2 Ti) | DAC filter cycle; gain level; LED |
| Jiu (CHU2 DSP, CDSP, ...) | open the EQ page (reads bands); change one band's gain by 1 dB and back; **don't press save** |
| Synaptics (ECHO-B, May, Click, ...) | open the device page (version read); switch EQ preset and back |

4. Close the software, then stop the capture.

**Never** run a firmware update, factory reset or "save to device" during a
capture. Those are out of scope and can brick or permanently change the device.

## Step 4: Share

Open an issue with:

- the OpenDrop USB report,
- the filtered `.pcapng` (File → Export Specified Packets → displayed only),
- your action list with times, and the software name and version.
