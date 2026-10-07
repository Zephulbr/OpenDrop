"""Decode a capture CSV (frame,time,direction,payload_hex) into readable GAIA frames.

Usage: python3 tools/gaia_decode.py docs/protocol/captures/2026-10-07-link-eq.csv
"""

import csv
import sys

from gaia import Decoder


def main(path: str) -> None:
    decoders = {"0x00": Decoder(), "0x01": Decoder()}
    with open(path, newline="") as f:
        rows = csv.reader(f)
        next(rows)  # header
        for frame, time, direction, payload in rows:
            arrow = "phone -> buds" if direction == "0x00" else "buds -> phone"
            for fr in decoders[direction].feed(bytes.fromhex(payload)):
                print(f"{frame:>5} {time} {arrow}  {fr.describe()}")


if __name__ == "__main__":
    main(sys.argv[1])
