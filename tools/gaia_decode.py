"""Decode a capture CSV into readable GAIA frames (and HFP AT commands).

Accepts both capture formats used so far:
  frame,time,direction,payload_hex
  frame,epoch,direction,payload_hex,kind   (kind "spp" = GAIA, "other" = HFP text)

Usage: python3 tools/gaia_decode.py docs/protocol/captures/2026-10-07-link-eq.csv
"""

import csv
import datetime
import sys

from gaia import Decoder


def fmt_time(value: str) -> str:
    if ":" in value:
        return value
    t = datetime.datetime.fromtimestamp(float(value), datetime.timezone.utc)
    return t.strftime("%H:%M:%S.%f")[:-3]


def main(path: str) -> None:
    decoders = {"0x00": Decoder(), "0x01": Decoder()}
    with open(path, newline="", encoding="utf-8-sig") as f:
        rows = csv.reader(f)
        next(rows)  # header
        for row in rows:
            frame, time, direction, payload = row[:4]
            kind = row[4] if len(row) > 4 else "spp"
            arrow = "phone -> buds" if direction == "0x00" else "buds -> phone"
            data = bytes.fromhex(payload)
            prefix = f"{frame:>5} {fmt_time(time)} {arrow}"
            if kind != "spp":
                text = data.decode("latin-1").strip().replace("\r\n", " | ").replace("\r", " | ")
                print(f"{prefix}  [hfp] {text}")
                continue
            for fr in decoders[direction].feed(data):
                print(f"{prefix}  {fr.describe()}")


if __name__ == "__main__":
    main(sys.argv[1])
