package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioFormatsTest {
    /** Configuration descriptors of the original Dawn Pro (captures/2026-10-18-dawn-pro-usb-report.txt). */
    private val dawnPro = """
        12 01 00 02 ef 02 01 40 c6 2f 6a f0 a7 a0 01 02
        03 01 09 02 57 01 03 01 00 80 32 08 0b 00 02 01
        00 20 00 09 04 00 00 00 01 01 20 02 09 24 01 00
        02 04 40 00 00 08 24 0a 05 03 07 00 00 11 24 02
        01 01 01 00 05 02 03 00 00 00 00 00 00 00 12 24
        06 03 01 03 00 00 00 0c 00 00 00 0c 00 00 00 00
        0c 24 03 04 01 03 00 03 05 00 00 00 09 04 01 00
        00 01 02 20 04 09 04 01 01 02 01 02 20 00 10 24
        01 01 05 01 01 00 00 00 02 03 00 00 00 00 06 24
        02 01 02 10 07 05 01 05 08 03 01 08 25 01 00 00
        00 00 00 07 05 81 11 04 00 04 09 04 01 02 02 01
        02 20 00 10 24 01 01 05 01 01 00 00 00 02 03 00
        00 00 00 06 24 02 01 03 18 07 05 01 05 08 03 01
        08 25 01 00 00 00 00 00 07 05 81 11 04 00 04 09
        04 01 03 02 01 02 20 00 10 24 01 01 05 01 01 00
        00 00 02 03 00 00 00 00 06 24 02 01 04 20 07 05
        01 05 08 03 01 08 25 01 00 00 00 00 00 07 05 81
        11 04 00 04 09 04 01 04 02 01 02 20 00 10 24 01
        01 05 01 00 00 00 80 02 03 00 00 00 00 06 24 02
        01 04 20 07 05 01 05 08 03 01 08 25 01 00 00 00
        00 00 07 05 81 11 04 00 04 09 04 02 00 02 03 00
        00 07 09 21 11 01 00 01 22 2b 00 07 05 05 03 08
        00 00 07 05 86 03 08 00 0a
    """.trimIndent().replace(Regex("\\s+"), "").hexToBytes()

    @Test
    fun readsTheDawnProFormats() {
        val formats = UsbAudioFormats.parse(dawnPro)
        assertEquals(listOf(1, 2, 3, 4), formats.map { it.alternate })
        assertEquals(listOf(16, 24, 32, 32), formats.map { it.bits })
        assertEquals(listOf(2, 3, 4, 4), formats.map { it.subslotBytes })
        assertEquals(listOf(false, false, false, true), formats.map { it.raw })
        assertTrue(formats.all { it.channels == 2 && it.interfaceId == 1 && it.sampleRates.isEmpty() })
    }

    @Test
    fun readsUac1Rates() {
        val uac1 = (
            "090401010101020000" + // interface 1 alt 1, 1 endpoint, audio streaming, UAC1
                "07240101010100" + // AS_GENERAL (ignored for UAC1)
                "0e2402010203100244ac0080bb00" + // type I, 2 ch, 3 bytes, 16 bit, 44.1 + 48 kHz
                "0705010d000201" // OUT 0x01, isochronous data
            ).hexToBytes()
        val format = UsbAudioFormats.parse(uac1).single()
        assertEquals(16, format.bits)
        assertEquals(2, format.channels)
        assertEquals(listOf(44100, 48000), format.sampleRates)
    }

    @Test
    fun toleratesNothingAndJunk() {
        assertTrue(UsbAudioFormats.parse(null).isEmpty())
        assertTrue(UsbAudioFormats.parse(byteArrayOf(0x00, 0x01)).isEmpty())
        assertTrue(UsbAudioFormats.parse("09040100".hexToBytes()).isEmpty())
    }

    @Test
    fun comtrueStateMergesReplies() {
        val state = ComtrueState()
            .with("a0a5a303010200".hexToBytes())
            .with("a0a5a200580000".hexToBytes())
        assertEquals(ComtrueState(filter = 3, gain = 1, led = 2, volumeRaw = 0x58), state)
        assertEquals(21, state.volumePercent)
        assertEquals(state, state.with("a0a5".hexToBytes()))
    }
}
