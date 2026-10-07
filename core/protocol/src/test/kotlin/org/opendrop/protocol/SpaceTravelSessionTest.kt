package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plays the earbuds' side of the Link capture against the session. */
class SpaceTravelSessionTest {
    private val sent = mutableListOf<String>()
    private val session = SpaceTravelSession(send = { sent += it.toHex() })

    private fun buds(hex: String) = session.onBytes(hex.hexToBytes())

    @Test
    fun connectSequence() {
        session.start()
        assertEquals(listOf("ff 01 00 00 00 0a 03 00"), sent)

        buds("ff040004000a830000040301")
        assertTrue(session.state.handshakeDone)
        assertEquals("ff 04 00 00 00 1d 00 01", sent.last())

        sent.clear()
        buds("ff04000b001d01010003010501010106020002")
        assertEquals(
            listOf(
                "ff 04 00 01 00 1d 00 07 00", // register core
                "ff 04 00 01 00 1d 00 07 05", // register music processing
                "ff 04 00 00 00 1d 00 05", // get application version
                "ff 04 00 00 00 1d 0a 02", // get selected EQ
            ),
            sent,
        )
        assertTrue(session.state.supportsEq)

        // Registration acks and the state the buds push, then the replies.
        buds("ff040000001d0107ff040000001d0107ff040001001d0a8000ff040001001d0a8100")
        buds("ff040005001d0105312e302e30")
        buds("ff040001001d0b0200")
        assertEquals("1.0.0", session.state.firmwareVersion)
        assertEquals(EqPreset.REFERENCE, session.state.eqPreset)
    }

    @Test
    fun setEqFollowsNotification() {
        session.setEq(EqPreset.MONITOR)
        assertEquals("ff 04 00 01 00 1d 0a 03 02", sent.single())
        buds("ff040001001d0b0301") // response alone doesn't change state
        assertEquals(null, session.state.eqPreset)
        buds("ff040001001d0a8102")
        assertEquals(EqPreset.MONITOR, session.state.eqPreset)
    }

    @Test
    fun stateCallbackFiresOnChangeOnly() {
        val states = mutableListOf<SpaceTravelState>()
        val s = SpaceTravelSession(send = {}, onState = { states += it })
        s.onBytes("ff040001001d0a8101".hexToBytes())
        s.onBytes("ff040001001d0a8101".hexToBytes())
        assertEquals(1, states.size)
    }
}
