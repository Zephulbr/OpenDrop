package org.opendrop.protocol

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Byte-level checks against docs/protocol/captures/2026-10-07-link-eq.csv. */
class GaiaTest {
    private val capture: Map<String, ByteArray> by lazy {
        val file = generateSequence(File("").absoluteFile) { it.parentFile }
            .map { File(it, "docs/protocol/captures/2026-10-07-link-eq.csv") }
            .first { it.exists() }
        file.readLines().drop(1).filter { it.isNotBlank() }.associate { line ->
            val cols = line.split(",")
            cols[0] to cols[3].hexToBytes()
        }
    }

    private fun frame(n: String) = capture.getValue(n)

    @Test
    fun encoderMatchesLink() {
        assertArrayEquals(frame("721"), GaiaCommands.handshake())
        assertArrayEquals(frame("725"), GaiaCommands.getSupportedFeatures())
        assertArrayEquals(frame("732"), GaiaCommands.registerNotifications(5))
        assertArrayEquals(frame("739"), GaiaCommands.getApplicationVersion())
        assertArrayEquals(frame("843"), GaiaCommands.getSelectedEq())
        assertArrayEquals(frame("861"), GaiaCommands.setEq(EqPreset.BASSHEAD))
        assertArrayEquals(frame("876"), GaiaCommands.setEq(EqPreset.MONITOR))
        assertArrayEquals(frame("886"), GaiaCommands.setEq(EqPreset.REFERENCE))
    }

    @Test
    fun decodesTwoFramesInOnePacket() {
        val frames = GaiaStreamDecoder().feed(frame("830"))
        assertEquals(listOf(0x14, 0x15), frames.map { it.cmd })
    }

    @Test
    fun decodesFrameSplitAcrossReads() {
        val data = frame("726")
        val decoder = GaiaStreamDecoder()
        assertTrue(decoder.feed(data.copyOfRange(0, 5)).isEmpty())
        val frames = decoder.feed(data.copyOfRange(5, data.size))
        assertEquals(1, frames.size)
        assertEquals(GaiaType.RESPONSE, frames[0].type)
    }

    @Test
    fun parsesEvents() {
        fun parse(n: String) = GaiaParser.parse(GaiaStreamDecoder().feed(frame(n)).single())
        assertEquals(GaiaEvent.Handshake(4, 3, 1), parse("724"))
        assertEquals(GaiaEvent.Features(mapOf(3 to 1, 5 to 1, 1 to 1, 6 to 2, 0 to 2)), parse("726"))
        assertEquals(GaiaEvent.FirmwareVersion("1.0.0"), parse("750"))
        assertEquals(GaiaEvent.EqPresetChanged(0), parse("846"))
        assertEquals(GaiaEvent.EqPresetChanged(2), parse("880"))
        assertEquals(GaiaEvent.Other, parse("879")) // set-EQ response, payload 01
    }

    @Test
    fun decodesA16BitLengthFrame() {
        val raw = byteArrayOf(
            0xFF.toByte(), 0x04, 0x02, 0x00, 0x01, // SOF, v3, FLAG_LENGTH_16, len=1
            0x00, 0x1D, 0x00, 0x01, 0x42, // vendor, command, payload
        )
        val frames = GaiaStreamDecoder().feed(raw)
        assertEquals(1, frames.size)
        assertEquals(Gaia.VENDOR_V3, frames[0].vendor)
        assertEquals(1, frames[0].payload.size)
        assertEquals(0x42, frames[0].payload[0].toInt())
    }

    @Test
    fun checksummedFrameConsumesItsChecksumByte() {
        val raw = byteArrayOf(
            0xFF.toByte(), 0x04, 0x01, 0x01, // SOF, v3, FLAG_CHECKSUM, len=1
            0x00, 0x1D, 0x0A, 0x02, 0x00, // vendor, command, payload
            0x7F, // checksum byte
        )
        val decoder = GaiaStreamDecoder()
        val frames = decoder.feed(raw)
        assertEquals(1, frames.size)
        assertEquals(Gaia.FLAG_CHECKSUM, frames[0].flags)
        assertEquals(1, frames[0].payload.size)
        // The checksum byte must be consumed, not parsed as the next frame:
        val next = decoder.feed(Gaia.encode(0, 1, byteArrayOf(1)))
        assertEquals(1, next.size)
    }

    @Test
    fun resyncsAfterGarbage() {
        val garbage = byteArrayOf(0x01, 0x02, 0x03)
        val frame = Gaia.encode(0, GaiaCommands.CORE_GET_VARIANT_NAME)
        val frames = GaiaStreamDecoder().feed(garbage + frame)
        assertEquals(1, frames.size)
        assertEquals(GaiaCommands.CORE_GET_VARIANT_NAME, frames[0].cmd)
    }

    @Test
    fun sofInsideAPayloadDoesNotBreakDecoding() {
        val first = Gaia.encode(0, 1, byteArrayOf(0xFF.toByte(), 0x04))
        val second = Gaia.encode(0, 2, byteArrayOf(9))
        val frames = GaiaStreamDecoder().feed(first + second)
        assertEquals(listOf(1, 2), frames.map { it.cmd })
    }

    @Test
    fun availableEqCountByteHeuristic() {
        // The Space Travel sends "count, ids…"; ids-only payloads are ambiguous
        // (a leading id equal to count-1 looks like a count byte). Pinned until
        // a second model's capture says which reading is right.
        fun ids(hex: String) =
            GaiaParser.parse(GaiaStreamDecoder().feed(hex.hexToBytes()).single())
        assertEquals(GaiaEvent.AvailableEqPresets(listOf(0, 1, 2, 63)), ids("ff040005001d0b01040001023f"))
        assertEquals(GaiaEvent.AvailableEqPresets(listOf(2)), ids("ff040002001d0b010102"))
    }

    @Test
    fun rejectsOutOfBandIdsAndBadHex() {
        assertThrows(IllegalArgumentException::class.java) { Gaia.encode(0x80, 1) }
        assertThrows(IllegalArgumentException::class.java) { Gaia.encode(0, 0x80) }
        assertThrows(IllegalArgumentException::class.java) { "zz".hexToBytes() }
        // Callers mask type ids to 2 bits; anything else falls back, not throws.
        assertEquals(GaiaType.COMMAND, GaiaType.of(9))
    }
}
