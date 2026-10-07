"""GAIA-over-RFCOMM framing and decoding, as observed on the Moondrop Space Travel.

Stdlib only. Used by gaia_decode.py (offline captures) and gaia_probe.py (live).
See docs/protocol/space-travel.md for the protocol notes.
"""

from dataclasses import dataclass

SOF = 0xFF
FLAG_CHECKSUM = 0x01
FLAG_LENGTH_16 = 0x02

VENDOR_V2 = 0x000A  # legacy GAIA, used only for the first version handshake
VENDOR_V3 = 0x001D  # GAIA v3 (QTIL)

TYPES = {0: "command", 1: "notification", 2: "response", 3: "error"}

# Feature ids from Qualcomm GAIA v3. Only the ones the Space Travel advertises
# (or that other Moondrop models use) are named.
FEATURES = {
    0: "core",
    1: "earbud",
    2: "anc",
    3: "voice_ui",
    5: "music_processing",
    6: "upgrade",
    8: "audio_curation",
    13: "battery",
    14: "voice_prompts",
    20: "dual_device",
    32: "anc_v2",
}

# (feature, type, command) -> name, for what we have seen or are probing.
NAMES = {
    (0, 0, 0x00): "get_protocol_version",
    (0, 0, 0x01): "get_supported_features",
    (0, 0, 0x02): "get_supported_features_next",
    (0, 0, 0x03): "get_serial_number",
    (0, 0, 0x04): "get_variant_name",
    (0, 0, 0x05): "get_application_version",
    (0, 0, 0x07): "register_notification",
    (0, 0, 0x0D): "set_transport_parameter?",
    (0, 1, 0x00): "charger_status?",
    (5, 0, 0x02): "get_selected_eq_set",
    (5, 0, 0x03): "set_eq_set",
    (5, 1, 0x00): "eq_state?",
    (5, 1, 0x01): "eq_set_changed",
}

EQ_PRESETS = {0: "Reference", 1: "Basshead", 2: "Monitor"}


@dataclass
class Frame:
    version: int
    flags: int
    vendor: int
    command: int
    payload: bytes

    @property
    def feature(self) -> int:
        return self.command >> 9

    @property
    def type(self) -> int:
        return (self.command >> 7) & 0x03

    @property
    def cmd(self) -> int:
        return self.command & 0x7F

    def describe(self) -> str:
        if self.vendor == VENDOR_V2:
            # Legacy GAIA: 0x0300 get API version, 0x8000 bit = acknowledgement.
            ack = " ack" if self.command & 0x8000 else ""
            text = f"v2 0x{self.command & 0x7FFF:04x}{ack} {self.payload.hex(' ')}".rstrip()
            extra = annotate(self)
            return f"{text}  -> {extra}" if extra else text
        feat = FEATURES.get(self.feature, f"feature_{self.feature}")
        typ = TYPES[self.type]
        name = NAMES.get((self.feature, self.type, self.cmd))
        if name is None and self.type in (2, 3):
            name = NAMES.get((self.feature, 0, self.cmd))
        name = name or f"cmd_0x{self.cmd:02x}"
        text = f"{feat}.{name} [{typ}] {self.payload.hex(' ')}".rstrip()
        extra = annotate(self)
        return f"{text}  -> {extra}" if extra else text


def annotate(f: Frame) -> str:
    p = f.payload
    if f.vendor == VENDOR_V2 and f.command == 0x8300 and len(p) >= 4:
        return f"status={p[0]} protocol=v{p[1]} api={p[2]}.{p[3]}"
    if f.vendor != VENDOR_V3:
        return ""
    if (f.feature, f.cmd) == (0, 0x01) and f.type == 2 and p:
        pairs = [(p[i], p[i + 1]) for i in range(1, len(p) - 1, 2)]
        feats = ", ".join(f"{FEATURES.get(a, a)} v{b}" for a, b in pairs)
        return f"more={p[0]} features: {feats}"
    if (f.feature, f.cmd) == (0, 0x05) and f.type == 2:
        return f'"{p.decode("ascii", "replace")}"'
    if f.feature == 5 and f.cmd in (0x02, 0x03) and f.type == 0 and p:
        return EQ_PRESETS.get(p[0], f"preset {p[0]}")
    if (f.feature, f.cmd) == (5, 0x02) and f.type == 2 and p:
        return EQ_PRESETS.get(p[0], f"preset {p[0]}")
    if (f.feature, f.type, f.cmd) == (5, 1, 0x01) and p:
        return EQ_PRESETS.get(p[0], f"preset {p[0]}")
    return ""


def encode(feature: int, cmd: int, payload: bytes = b"", type_: int = 0, version: int = 4) -> bytes:
    command = (feature << 9) | (type_ << 7) | (cmd & 0x7F)
    if len(payload) > 0xFF:
        raise ValueError("payload too long for 1-byte length")
    return bytes([SOF, version, 0x00, len(payload)]) + VENDOR_V3.to_bytes(2, "big") \
        + command.to_bytes(2, "big") + payload


def encode_v2(command: int, payload: bytes = b"") -> bytes:
    return bytes([SOF, 0x01, 0x00, len(payload)]) + VENDOR_V2.to_bytes(2, "big") \
        + command.to_bytes(2, "big") + payload


class Decoder:
    """Streaming decoder: RFCOMM packets may hold several frames (or partial ones)."""

    def __init__(self) -> None:
        self.buf = bytearray()

    def feed(self, data: bytes) -> list[Frame]:
        self.buf += data
        frames = []
        while True:
            start = self.buf.find(SOF)
            if start < 0:
                self.buf.clear()
                break
            del self.buf[:start]
            if len(self.buf) < 4:
                break
            flags = self.buf[2]
            if flags & FLAG_LENGTH_16:
                if len(self.buf) < 5:
                    break
                length = int.from_bytes(self.buf[3:5], "big")
                hdr = 5
            else:
                length = self.buf[3]
                hdr = 4
            total = hdr + 4 + length + (1 if flags & FLAG_CHECKSUM else 0)
            if len(self.buf) < total:
                break
            body = bytes(self.buf[hdr:hdr + 4 + length])
            frames.append(Frame(
                version=self.buf[1],
                flags=flags,
                vendor=int.from_bytes(body[0:2], "big"),
                command=int.from_bytes(body[2:4], "big"),
                payload=body[4:],
            ))
            del self.buf[:total]
        return frames
