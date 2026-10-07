package org.opendrop.protocol

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
        assertArrayEquals(frame("721"), SpaceTravelCommands.handshake())
        assertArrayEquals(frame("725"), SpaceTravelCommands.getSupportedFeatures())
        assertArrayEquals(frame("732"), SpaceTravelCommands.registerNotifications(5))
        assertArrayEquals(frame("739"), SpaceTravelCommands.getApplicationVersion())
        assertArrayEquals(frame("843"), SpaceTravelCommands.getSelectedEq())
        assertArrayEquals(frame("861"), SpaceTravelCommands.setEq(EqPreset.BASSHEAD))
        assertArrayEquals(frame("876"), SpaceTravelCommands.setEq(EqPreset.MONITOR))
        assertArrayEquals(frame("886"), SpaceTravelCommands.setEq(EqPreset.REFERENCE))
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
        fun parse(n: String) = SpaceTravelParser.parse(GaiaStreamDecoder().feed(frame(n)).single())
        assertEquals(SpaceTravelEvent.Handshake(4, 3, 1), parse("724"))
        assertEquals(SpaceTravelEvent.Features(mapOf(3 to 1, 5 to 1, 1 to 1, 6 to 2, 0 to 2)), parse("726"))
        assertEquals(SpaceTravelEvent.FirmwareVersion("1.0.0"), parse("750"))
        assertEquals(SpaceTravelEvent.EqPresetChanged(0), parse("846"))
        assertEquals(SpaceTravelEvent.EqPresetChanged(2), parse("880"))
        assertEquals(SpaceTravelEvent.Other, parse("879")) // set-EQ response, payload 01
    }
}
