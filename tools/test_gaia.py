"""Checks tools/gaia.py against the 2026-10-07 Link capture. Run: python3 -m unittest discover tools"""

import csv
import pathlib
import unittest

from gaia import FEATURES, VENDOR_V3, Decoder, Frame, encode, encode_v2

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


def _eq_reply(payload: bytes) -> Frame:
    return Frame(4, 0, VENDOR_V3, (5 << 9) | (2 << 7) | 0x05, payload)


class UserEqDecodeTest(unittest.TestCase):
    def test_five_bands(self):
        band = bytes([0x03, 0xE8, 0x0B, 0x50, 13, 0xFF, 0x2E])  # 1000 Hz, Q 0.707, PEQ, -3.5 dB
        text = _eq_reply(bytes([0, 4]) + band + bytes(7 * 4)).describe()
        self.assertIn("start 0, end 4, 5 bands", text)
        self.assertIn("1000 Hz, Q 0.707, type 13 (PEQ), -3.5 dB", text)

    def test_captured_short_reply(self):
        # 2026-10-07 probe: start 0, end 1, one zeroed band.
        text = _eq_reply(bytes([0, 1]) + bytes(7)).describe()
        self.assertIn("start 0, end 1, 1 bands", text)

    def test_malformed_reply_does_not_raise(self):
        self.assertIn("malformed user EQ (8 bytes)", _eq_reply(bytes([0, 4]) + bytes(6)).describe())
        self.assertIn("malformed user EQ (1 bytes)", _eq_reply(bytes([0])).describe())


class FeatureNamesTest(unittest.TestCase):
    def test_full_map_without_28(self):
        self.assertEqual(sorted(FEATURES), [i for i in range(36) if i != 28])
        self.assertEqual(FEATURES[20], "multipoint")
        self.assertIn("feature_28", Frame(4, 0, VENDOR_V3, 28 << 9, b"").describe())


if __name__ == "__main__":
    unittest.main()
