package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plays the earbuds' side of the Link capture and the probe against the session. */
class GaiaSessionTest {
    private val sent = mutableListOf<String>()
    private val session = GaiaSession(send = { sent += it.toHex() })

    private fun buds(hex: String) = session.onBytes(hex.hexToBytes())

    private fun String.hex() = toByteArray().toHex().replace(" ", "")

    /** Replies of the Space Travel, from the Link capture and the read-only probe. */
    private fun connectSpaceTravel() {
        session.start()
        buds("ff040004000a830000040301")
        buds("ff04000b001d01010003010501010106020002")
        buds("ff040005001d0105312e302e30")
        buds("ff040001001d0b0200")
        buds("ff040015001d0104" + "Moondrop Space Travel".hex())
        buds("ff040005001d0b01040001023f")
    }

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
                // Link's sequence, unchanged
                "ff 04 00 01 00 1d 00 07 00", // register core
                "ff 04 00 01 00 1d 00 07 05", // register music processing
                "ff 04 00 00 00 1d 00 05", // get application version
                "ff 04 00 00 00 1d 0a 02", // get selected EQ
                // read-only, verified by the probe
                "ff 04 00 00 00 1d 00 04", // get variant name
                "ff 04 00 00 00 1d 0a 01", // get available EQ presets
            ),
            sent,
        )
        assertTrue(session.state.featuresKnown)
        assertTrue(session.state.supportsEq)
    }

    @Test
    fun identifiesTheSpaceTravel() {
        connectSpaceTravel()
        val state = session.state
        assertEquals("1.0.0", state.firmwareVersion)
        assertEquals("Moondrop Space Travel", state.variantName)
        assertEquals("Moondrop Space Travel", state.model?.name)
        assertEquals(listOf(0, 1, 2, 63), state.availableEqPresets)
        assertEquals(EqPreset.REFERENCE, state.eqPreset)
        assertTrue(state.canControl)
        assertEquals(EqPreset.entries, state.switchableEqPresets)
    }

    @Test
    fun setEqFollowsNotification() {
        connectSpaceTravel()
        sent.clear()
        assertTrue(session.setEq(EqPreset.MONITOR))
        assertEquals("ff 04 00 01 00 1d 0a 03 02", sent.single())
        buds("ff040001001d0b0301") // response alone doesn't change state
        assertEquals(EqPreset.REFERENCE, session.state.eqPreset)
        buds("ff040001001d0a8102")
        assertEquals(EqPreset.MONITOR, session.state.eqPreset)
    }

    @Test
    fun unknownDeviceIsReadOnly() {
        session.start()
        buds("ff040004000a830000040301")
        buds("ff04000b001d01010003010501010106020002")
        buds("ff040007001d0104" + "Unknown".hex())
        buds("ff040001001d0b0200")
        sent.clear()
        assertFalse(session.state.canControl)
        assertTrue(session.state.switchableEqPresets.isEmpty())
        assertFalse(session.setEq(EqPreset.BASSHEAD))
        assertTrue(sent.isEmpty())
        assertEquals(EqPreset.REFERENCE, session.state.eqPreset) // still shown
    }

    @Test
    fun nothingIsWrittenBeforeTheModelIsKnown() {
        session.start()
        buds("ff040004000a830000040301")
        buds("ff04000b001d01010003010501010106020002")
        sent.clear()
        assertFalse(session.setEq(EqPreset.BASSHEAD))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun followsAFeatureListInSeveralParts() {
        session.start()
        buds("ff040004000a830000040301")
        sent.clear()
        buds("ff040005001d01010100020101") // more=1: core v2, earbud v1
        assertEquals(listOf("ff 04 00 00 00 1d 00 02"), sent)
        assertFalse(session.state.featuresKnown)
        buds("ff040005001d0102000d012101") // more=0: battery v1, ANC v3 v1
        assertTrue(session.state.featuresKnown)
        assertEquals(mapOf(0 to 2, 1 to 1, 13 to 1, 33 to 1), session.state.features)
        assertFalse(session.state.supportsEq)
    }

    @Test
    fun stateCallbackFiresOnChangeOnly() {
        val states = mutableListOf<GaiaDeviceState>()
        val s = GaiaSession(send = {}, onState = { states += it })
        s.onBytes("ff040001001d0a8101".hexToBytes())
        s.onBytes("ff040001001d0a8101".hexToBytes())
        assertEquals(1, states.size)
    }

    @Test
    fun findsModelsIgnoringCase() {
        assertEquals("Space Travel 2 Ultra", MoondropModels.find("SPACE TRAVEL 2 ULTRA")?.name)
        assertEquals(Chip.QUALCOMM, MoondropModels.find("Moondrop Sparks")?.chip)
        assertNull(MoondropModels.find("Galaxy Buds"))
    }

    @Test
    fun nextEqPresetWraps() {
        connectSpaceTravel() // on Reference
        assertEquals(EqPreset.BASSHEAD, session.state.nextEqPreset)
        buds("ff040001001d0a8102") // Monitor
        assertEquals(EqPreset.REFERENCE, session.state.nextEqPreset)
        buds("ff040001001d0a813f") // hidden user EQ: start from the first
        assertEquals(EqPreset.REFERENCE, session.state.nextEqPreset)
    }

    @Test
    fun noNextEqPresetWithoutNames() {
        assertNull(GaiaDeviceState().nextEqPreset)
    }

    @Test
    fun parsesEqPresetFromAutomations() {
        assertEquals(EqPreset.BASSHEAD, EqPreset.parse("basshead"))
        assertEquals(EqPreset.MONITOR, EqPreset.parse(" MONITOR "))
        assertEquals(EqPreset.REFERENCE, EqPreset.parse("0"))
        assertNull(EqPreset.parse("63"))
        assertNull(EqPreset.parse("loud"))
        assertNull(EqPreset.parse(null))
    }
}
