package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

/** Packet-log lines from [GaiaFrame.describe]. */
class GaiaDescribeTest {
    private fun v3(feature: Int, type: Int, cmd: Int, vararg payload: Int) =
        GaiaFrame(4, 0, Gaia.VENDOR_V3, (feature shl 9) or (type shl 7) or cmd, payload.map { it.toByte() }.toByteArray())

    @Test
    fun namesKnownFeatureAndType() {
        assertEquals("f5 music_processing response 0x02 00", v3(5, 2, 0x02, 0).toString())
    }

    @Test
    fun unknownFeatureStaysReadable() {
        assertEquals("f28 feature_28 command 0x00", v3(28, 0, 0).toString())
    }

    @Test
    fun legacyHandshakeKeepsItsShape() {
        val frame = GaiaFrame(1, 0, Gaia.VENDOR_V2, 0x8300, byteArrayOf(0, 4, 3, 1))
        assertEquals("v2 0x8300 00 04 03 01", frame.toString())
    }

    @Test
    fun serialRepliesAreRedactedByDefault() {
        val serial = v3(0, 2, 0x03, 0x41, 0x42, 0x31)
        assertEquals("f0 core response 0x03 [redacted]", serial.toString())
        assertEquals("f0 core response 0x03 41 42 31", serial.describe(redactSerials = false))
        assertEquals("f0 core response 0x14 [redacted]", v3(0, 2, 0x14, 0x41).toString())
        assertEquals("f0 core response 0x15 [redacted]", v3(0, 2, 0x15, 0x41).toString())
    }

    @Test
    fun serialRequestsAndOtherFeaturesAreNotMasked() {
        assertEquals("f0 core command 0x03", v3(0, 0, 0x03).toString())
        assertEquals("f5 music_processing response 0x03 01", v3(5, 2, 0x03, 1).toString())
    }

    @Test
    fun everyKnownFeatureHasAKey() {
        (0..35).filter { it != 28 }.forEach { id ->
            assertEquals(false, GaiaFeature.key(id).startsWith("feature_"))
        }
    }
}
