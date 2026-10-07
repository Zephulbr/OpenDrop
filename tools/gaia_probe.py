"""Read-only GAIA probe and notification monitor for the Moondrop Space Travel.

Runs on a PC (Linux or Windows, Python 3.10+) paired with the earbuds.
By default it only sends "get"/query commands, or ones the MOONDROP Link app
itself sends on connect. The one exception is the opt-in --try-user-eq test,
which selects EQ preset 63 for 15 s and then restores the previous preset.

Usage:
  python tools/gaia_probe.py AA:BB:CC:DD:EE:FF            # probe, then exit
  python tools/gaia_probe.py AA:BB:CC:DD:EE:FF --monitor 180
      # probe, then print every notification for 180 s while you
      # long-press / tap the earbuds, open and close the case, etc.
  python tools/gaia_probe.py --port COM5 --monitor 180
      # Windows fallback: use the outgoing COM port Windows created for the
      # earbuds' "Serial Port" service (needs: pip install pyserial)

Before running: close the MOONDROP Link app on your phone (or turn the phone's
Bluetooth off), and make sure the PC is paired and connected to the earbuds.
"""

import argparse
import socket
import sys
import time

from gaia import Decoder, encode, encode_v2

# (label, feature, command[, payload]). Queries only.
PROBES = [
    # Core: identification. Standard GAIA v3 "get" commands.
    ("core: protocol version", 0, 0x00),
    ("core: supported features", 0, 0x01),
    ("core: serial number", 0, 0x03),
    ("core: variant name", 0, 0x04),
    ("core: application version", 0, 0x05),
    # Core: commands Link sends on the device screen (meaning unknown).
    ("core: link init 0x13", 0, 0x13),
    ("core: link init 0x14", 0, 0x14),
    ("core: link init 0x15", 0, 0x15),
    ("core: link init 0x16", 0, 0x16),
    # Advertised features.
    ("earbud: cmd 0", 1, 0x00),
    ("voice_ui: cmd 0", 3, 0x00),
    ("music: eq state", 5, 0x00),
    ("music: available eq presets", 5, 0x01),
    ("music: selected eq set", 5, 0x02),
    ("music: user eq band count", 5, 0x04),
    # Read the hidden "User" EQ (preset 63, 5 bands). Payload = first, last band.
    ("music: user eq config, bands 0-5", 5, 0x05, bytes([0, 5])),
    ("music: user eq config, bands 1-5", 5, 0x05, bytes([1, 5])),
    # Not advertised, but used by other Moondrop models. Expect errors.
    ("anc v1: get", 2, 0x00),
    ("audio curation: get mode", 8, 0x03),
    ("battery: get types", 13, 0x00),
    ("voice prompts: get", 14, 0x01),
    ("dual device: get state", 20, 0x01),
    ("anc v2: get mode", 32, 0x03),
]

REGISTER = [0, 1, 3, 5]  # notification features to register for (same as Link, minus upgrade)


class RfcommSocket:
    def __init__(self, address: str, channel: int) -> None:
        self.sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        self.sock.settimeout(10)
        self.sock.connect((address, channel))
        self.sock.settimeout(0.2)

    def write(self, data: bytes) -> None:
        self.sock.sendall(data)

    def read(self) -> bytes:
        try:
            data = self.sock.recv(1024)
        except (socket.timeout, TimeoutError):
            return b""
        if not data:
            raise ConnectionError("earbuds closed the connection")
        return data


class ComPort:
    def __init__(self, port: str) -> None:
        import serial  # pyserial, only needed for this mode
        self.ser = serial.Serial(port, timeout=0.2)

    def write(self, data: bytes) -> None:
        self.ser.write(data)

    def read(self) -> bytes:
        return self.ser.read(1024)


class Link:
    def __init__(self, transport) -> None:
        self.transport = transport
        self.decoder = Decoder()

    def send(self, data: bytes) -> None:
        print(f"  TX {data.hex(' ')}")
        self.transport.write(data)

    def read_for(self, seconds: float) -> list:
        frames, end = [], time.monotonic() + seconds
        while time.monotonic() < end:
            data = self.transport.read()
            if not data:
                continue
            for fr in self.decoder.feed(data):
                stamp = time.strftime("%H:%M:%S")
                print(f"  RX {stamp} {fr.describe()}")
                frames.append(fr)
        return frames


def selected_preset(link: Link):
    link.send(encode(5, 0x02))
    for fr in link.read_for(1.0):
        if (fr.feature, fr.type, fr.cmd) == (5, 2, 0x02) and fr.payload:
            return fr.payload[0]
    return None


def try_user_eq(link: Link) -> None:
    print("\n== user EQ test: lower the volume first (switching may pop)")
    original = selected_preset(link)
    if original is None:
        print("  could not read the current preset; skipping")
        return
    print(f"  current preset: {original}")
    link.send(encode(5, 0x03, bytes([63])))
    link.read_for(2.0)
    print("  now on preset 63 (if accepted). Listen for 15 s: does the sound change?")
    link.read_for(13.0)
    print(f"  selected preset reads as: {selected_preset(link)}")
    link.send(encode(5, 0x03, bytes([original])))
    link.read_for(2.0)
    print(f"  restored; selected preset reads as: {selected_preset(link)}")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("address", nargs="?", help="earbuds Bluetooth address")
    ap.add_argument("--port", help="use a serial COM port instead of a Bluetooth socket")
    ap.add_argument("--channel", type=int, default=1, help="RFCOMM channel (default 1)")
    ap.add_argument("--try-user-eq", action="store_true",
                    help="select the hidden User EQ preset (63) for 15 s, then restore")
    ap.add_argument("--monitor", type=int, default=0, metavar="SECONDS",
                    help="after probing, print notifications for this many seconds")
    args = ap.parse_args()

    if args.port:
        print(f"Opening {args.port} ...")
        link = Link(ComPort(args.port))
    elif args.address:
        print(f"Connecting to {args.address} channel {args.channel} ...")
        link = Link(RfcommSocket(args.address, args.channel))
    else:
        ap.error("give the earbuds' address, or --port COMx")

    print("\n== handshake")
    link.send(encode_v2(0x0300))
    link.read_for(1.0)

    print("\n== register notifications")
    for feature in REGISTER:
        link.send(encode(0, 0x07, bytes([feature])))
        link.read_for(0.5)

    for label, feature, cmd, *payload in PROBES:
        print(f"\n== {label}")
        link.send(encode(feature, cmd, payload[0] if payload else b""))
        if not link.read_for(1.0):
            print("  (no reply)")

    if args.try_user_eq:
        try_user_eq(link)

    if args.monitor:
        print(f"\n== monitoring notifications for {args.monitor} s. "
              "Note the time of each thing you do.")
        link.read_for(args.monitor)

    print("\nDone.")


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(1)
