"""Checks tools/gaia.py against the 2026-10-07 Link capture. Run: python3 -m unittest discover tools"""

import csv
import pathlib
import unittest

from gaia import Decoder, encode, encode_v2

CAPTURE = pathlib.Path(__file__).parent.parent / "docs/protocol/captures/2026-10-07-link-eq.csv"


def captured(frame: str) -> bytes:
    with open(CAPTURE, newline="") as f:
        for row in csv.reader(f):
            if row[0] == frame:
                return bytes.fromhex(row[3])
    raise KeyError(frame)


class EncodeTest(unittest.TestCase):
    def test_matches_link(self):
        self.assertEqual(encode_v2(0x0300), captured("721"))
        self.assertEqual(encode(0, 0x01), captured("725"))
        self.assertEqual(encode(0, 0x07, b"\x05"), captured("732"))
        self.assertEqual(encode(5, 0x02), captured("843"))
        self.assertEqual(encode(5, 0x03, b"\x01"), captured("861"))  # Basshead
        self.assertEqual(encode(5, 0x03, b"\x02"), captured("876"))  # Monitor
        self.assertEqual(encode(5, 0x03, b"\x00"), captured("886"))  # Reference


class DecodeTest(unittest.TestCase):
    def test_two_frames_in_one_packet(self):
        frames = Decoder().feed(captured("830"))
        self.assertEqual([(f.feature, f.type, f.cmd) for f in frames], [(0, 0, 0x14), (0, 0, 0x15)])

    def test_split_frame(self):
        data = captured("726")
        dec = Decoder()
        self.assertEqual(dec.feed(data[:5]), [])
        (frame,) = dec.feed(data[5:])
        self.assertEqual((frame.feature, frame.type, frame.cmd), (0, 2, 0x01))
        self.assertEqual(frame.payload.hex(), "0003010501010106020002")

    def test_eq_notification(self):
        (frame,) = Decoder().feed(captured("880"))
        self.assertEqual((frame.feature, frame.type, frame.cmd, frame.payload), (5, 1, 0x01, b"\x02"))

    def test_firmware_version(self):
        (frame,) = Decoder().feed(captured("750"))
        self.assertEqual(frame.payload, b"1.0.0")


if __name__ == "__main__":
    unittest.main()
