"""Checks the live probe table against the 2.26.2c corrections. Run: python3 -m unittest discover tools"""

import unittest

from gaia_probe import PROBES


class ProbeTableTest(unittest.TestCase):
    def test_user_eq_queries_stay_in_range(self):
        self.assertIn(("music: user eq config, bands 0-4", 5, 0x05, bytes([0, 4])), PROBES)
        self.assertIn(("music: user eq config, bands 1-4", 5, 0x05, bytes([1, 4])), PROBES)

    def test_queries_only(self):
        # Set commands that must never appear in the default probe list.
        writes = {(0, 0x07), (0, 0x0D), (5, 0x03), (5, 0x06), (5, 0x07), (5, 0x08)}
        self.assertFalse({(p[1], p[2]) for p in PROBES} & writes)


if __name__ == "__main__":
    unittest.main()
