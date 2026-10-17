package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbProbesTest {
    private val state = UsbProbes.reads.single { it.id == "comtrue.state" }
    private val volume = UsbProbes.reads.single { it.id == "comtrue.volume" }

    @Test
    fun comtruePacketsAreTheTwoGets() {
        assertEquals("c0 a5 a3 00 00 00 00", state.packetBytes().toHex())
        assertEquals("c0 a5 a2 00 00 00 00", volume.packetBytes().toHex())
        assertEquals("c0 a5 a3 00 00 00 00 · 0x43/0xa0, 0xc3/0xa1 idx 0x09a0", state.wire())
        assertEquals(0xC3, state.requestTypeIn)
        assertEquals(0xA1, state.requestIn)
    }

    @Test
    fun onlyReadsAreOffered() {
        // Comtrue set commands: filter 01, gain 02, volume 04, LED 06.
        val commands = UsbProbes.reads.filter { it.family == UsbFamily.COMTRUE }.map { it.packet[2] }.toSet()
        assertEquals(setOf(0xA2, 0xA3), commands)
    }

    @Test
    fun readsFollowTheFamily() {
        assertEquals(2, UsbProbes.readsFor(UsbFamily.COMTRUE).size)
        assertTrue(UsbProbes.readsFor(UsbFamily.SPV).isEmpty())
        assertTrue(UsbProbes.readsFor(null).isEmpty())
    }

    @Test
    fun decodesReplies() {
        assertEquals(
            "filter 2 (slow roll-off, low latency), gain 1 (high), LED 0 (on)  (c0 a5 a3 02 01 00 00)",
            UsbProbes.describe(state, "c0a5a302010000".hexToBytes()),
        )
        assertEquals(
            "filter 9 (unknown), gain 0 (low), LED 2 (off)  (c0 a5 a3 09 00 02 00)",
            UsbProbes.describe(state, "c0a5a309000200".hexToBytes()),
        )
        assertEquals(
            "volume 50% (raw 0x38; 0x00 loudest, 0x70 quietest)  (c0 a5 a2 00 38 00 00)",
            UsbProbes.describe(volume, "c0a5a200380000".hexToBytes()),
        )
        assertEquals(
            "volume 100% (raw 0x00; 0x00 loudest, 0x70 quietest)  (c0 a5 a2 00 00 00 00)",
            UsbProbes.describe(volume, "c0a5a200000000".hexToBytes()),
        )
        assertEquals(
            "volume 0% (raw 0xff; 0x00 loudest, 0x70 quietest)  (c0 a5 a2 00 ff 00 00)",
            UsbProbes.describe(volume, "c0a5a200ff0000".hexToBytes()),
        )
        assertEquals("c0 a5", UsbProbes.describe(state, "c0a5".hexToBytes()))
        assertEquals("(empty)", UsbProbes.describe(state, ByteArray(0)))
    }
}
