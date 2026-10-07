"""Read-only GAIA probe and notification monitor for the Moondrop Space Travel.

Runs on a PC (Linux or Windows, Python 3.10+) paired with the earbuds.
It only sends commands with NO payload that are "get"/query commands, or that
the MOONDROP Link app itself sends on connect. It never sends a "set".

Usage:
  python3 tools/gaia_probe.py AA:BB:CC:DD:EE:FF            # probe, then exit
  python3 tools/gaia_probe.py AA:BB:CC:DD:EE:FF --monitor 120
      # probe, then print every notification for 120 s while you
      # long-press / tap the earbuds, open and close the case, etc.

Before running: close the MOONDROP Link app on your phone (or turn the phone's
Bluetooth off), and make sure the PC is paired and connected to the earbuds.
"""

import argparse
import socket
import sys
import time

from gaia import Decoder, encode, encode_v2

# (label, feature, command). All payload-less.
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
    # Not advertised, but used by other Moondrop models. Expect errors.
    ("anc v1: get", 2, 0x00),
    ("audio curation: get mode", 8, 0x03),
    ("battery: get types", 13, 0x00),
    ("voice prompts: get", 14, 0x01),
    ("dual device: get state", 20, 0x01),
    ("anc v2: get mode", 32, 0x03),
]

REGISTER = [0, 1, 3, 5]  # notification features to register for (same as Link, minus upgrade)


class Link:
    def __init__(self, address: str, channel: int) -> None:
        self.sock = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        self.sock.connect((address, channel))
        self.sock.settimeout(0.2)
        self.decoder = Decoder()

    def send(self, data: bytes) -> None:
        print(f"  TX {data.hex(' ')}")
        self.sock.send(data)

    def read_for(self, seconds: float) -> list:
        frames, end = [], time.monotonic() + seconds
        while time.monotonic() < end:
            try:
                data = self.sock.recv(1024)
            except (socket.timeout, TimeoutError):
                continue
            if not data:
                raise ConnectionError("earbuds closed the connection")
            for fr in self.decoder.feed(data):
                stamp = time.strftime("%H:%M:%S")
                print(f"  RX {stamp} {fr.describe()}")
                frames.append(fr)
        return frames


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("address", help="earbuds Bluetooth address")
    ap.add_argument("--channel", type=int, default=1, help="RFCOMM channel (default 1)")
    ap.add_argument("--monitor", type=int, default=0, metavar="SECONDS",
                    help="after probing, print notifications for this many seconds")
    args = ap.parse_args()

    print(f"Connecting to {args.address} channel {args.channel} ...")
    link = Link(args.address, args.channel)

    print("\n== handshake")
    link.send(encode_v2(0x0300))
    link.read_for(1.0)

    print("\n== register notifications")
    for feature in REGISTER:
        link.send(encode(0, 0x07, bytes([feature])))
        link.read_for(0.5)

    for label, feature, cmd in PROBES:
        print(f"\n== {label}")
        link.send(encode(feature, cmd))
        if not link.read_for(1.0):
            print("  (no reply)")

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
