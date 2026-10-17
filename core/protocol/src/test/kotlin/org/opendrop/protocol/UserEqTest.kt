package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserEqTest {
    @Test
    fun decodesTheCapturedReply() {
        // 2026-10-07 probe: start 0, end 1, one zeroed band.
        val config = UserEq.parse(byteArrayOf(0, 1) + ByteArray(7))!!
        assertEquals(0, config.startBand)
        assertEquals(1, config.endBand)
        assertEquals(listOf(UserEqBand(0, 0.0, 0, 0.0)), config.bands)
    }

    @Test
    fun decodesFrequencyQFilterAndSignedGain() {
        // 1000 Hz, Q 0x0B50/4096, PEQ (13), -210/60 dB.
        val band = byteArrayOf(0x03, 0xE8.toByte(), 0x0B, 0x50, 13, 0xFF.toByte(), 0x2E)
        val config = UserEq.parse(byteArrayOf(0, 4) + band + ByteArray(7 * 4))!!
        assertEquals(5, config.bands.size)
        assertEquals(1000, config.bands[0].frequencyHz)
        assertEquals(0.707, config.bands[0].q, 0.001)
        assertEquals(13, config.bands[0].filterType)
        assertEquals(-3.5, config.bands[0].gainDb, 1e-9)
    }

    @Test
    fun decodesLargeUnsignedFields() {
        // 20 kHz and Q 15.99 must not wrap negative; +12 dB is 720.
        val band = byteArrayOf(0x4E, 0x20, 0xFF.toByte(), 0xF0.toByte(), 4, 0x02, 0xD0.toByte())
        val b = UserEq.parse(byteArrayOf(2, 2) + band)!!.bands.single()
        assertEquals(20000, b.frequencyHz)
        assertEquals(65520 / 4096.0, b.q, 1e-9)
        assertEquals(12.0, b.gainDb, 1e-9)
    }

    @Test
    fun headerOnlyHasNoBands() {
        assertEquals(UserEqConfig(0, 4, emptyList()), UserEq.parse(byteArrayOf(0, 4)))
    }

    @Test
    fun rejectsImpossibleLengths() {
        assertNull(UserEq.parse(ByteArray(0)))
        assertNull(UserEq.parse(byteArrayOf(0)))
        assertNull(UserEq.parse(byteArrayOf(0, 4) + ByteArray(6)))
    }
}
