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
}
