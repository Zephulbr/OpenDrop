package org.opendrop.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DevProbesTest {
    @Test
    fun readsAreUniqueKnownAndAllowed() {
        assertEquals(DevProbes.reads.size, DevProbes.reads.map { it.id }.toSet().size)
        DevProbes.reads.forEach {
            assertFalse("${it.id} uses an unknown feature", GaiaFeature.key(it.feature).startsWith("feature_"))
            assertFalse("${it.id} touches a denied feature", it.feature in DevProbes.NEVER_READ)
            assertTrue("${it.id} cmd out of range", it.cmd in 0..0x7F)
        }
    }

    @Test
    fun readsNeverUseKnownSetCommands() {
        // (feature, cmd) pairs that are setters in Link 2.26.2c. A read must never reuse one.
        val setters = setOf(
            0 to 0x07, 0 to 0x09, 0 to 0x0A, 0 to 0x0D, 5 to 0x03, 5 to 0x06, 5 to 0x07, 5 to 0x08,
            14 to 2, 15 to 2, 16 to 3, 16 to 4, 16 to 6, 17 to 2, 18 to 2, 18 to 4, 19 to 2,
            20 to 2, 20 to 4, 20 to 7, 22 to 3, 23 to 2, 25 to 2, 25 to 4, 26 to 3, 27 to 2,
            30 to 2, 31 to 2, 31 to 5, 32 to 4, 32 to 42, 33 to 4, 33 to 6, 33 to 42, 34 to 1, 34 to 2, 35 to 2,
        )
        assertTrue(DevProbes.reads.none { (it.feature to it.cmd) in setters })
    }

    @Test
    fun identifyingReadsAreFlagged() {
        val identifying = DevProbes.reads.filter { it.identifying }.map { it.feature to it.cmd }.toSet()
        assertEquals(setOf(0 to 0x03, 0 to 0x14, 0 to 0x15, 20 to 5, 20 to 6), identifying)
    }

    @Test
    fun readsForFiltersByFeatureAndPrivacy() {
        val st1 = setOf(0, 1, 3, 5, 6)
        val reads = DevProbes.readsFor(st1, showIdentifying = false)
        assertTrue(reads.all { it.feature in st1 && !it.identifying })
        assertTrue(reads.any { it.id == "eq.user_0_4" })
        assertTrue(DevProbes.readsFor(st1, showIdentifying = true).any { it.id == "core.serial" })
        assertEquals(emptyList<DevRead>(), DevProbes.readsFor(emptySet(), showIdentifying = true))
    }

    @Test
    fun userEqReadMatchesTheCorrectedProbe() {
        val read = DevProbes.find("eq.user_0_4")!!
        assertArrayEquals(Gaia.encode(5, 0x05, byteArrayOf(0, 4)), read.encode())
    }

    @Test
    fun matchesOnlyTheReplyToTheSameCommand() {
        val read = DevProbes.find("eq.selected")!!
        fun frame(type: Int, feature: Int = 5, cmd: Int = 2) =
            GaiaFrame(4, 0, Gaia.VENDOR_V3, (feature shl 9) or (type shl 7) or cmd, ByteArray(0))
        assertTrue(read.matches(frame(type = 2)))
        assertTrue(read.matches(frame(type = 3)))
        assertFalse(read.matches(frame(type = 0)))
        assertFalse(read.matches(frame(type = 1))) // notification, not a reply
        assertFalse(read.matches(frame(type = 2, cmd = 1)))
        assertFalse(read.matches(frame(type = 2, feature = 1)))
    }

    @Test
    fun describesReplies() {
        fun d(id: String, vararg b: Int, reveal: Boolean = false) =
            DevProbes.describe(DevProbes.find(id)!!, b.map { it.toByte() }.toByteArray(), reveal)
        assertEquals("63  (3f)", d("eq.selected", 63))
        assertEquals("\"1.0.0\"  (31 2e 30 2e 30)", d("core.app_version", 0x31, 0x2E, 0x30, 0x2E, 0x30))
        assertEquals("[hidden: 2 bytes; turn on \"show serial numbers\"]", d("core.serial", 0x41, 0x42))
        assertEquals("\"AB\"  (41 42)", d("core.serial", 0x41, 0x42, reveal = true))
        assertEquals("start 0, end 1, 1 bands: 0 Hz Q 0.000 type 0 +0.0 dB  (00 01 00 00 00 00 00 00 00)", d("eq.user_0_4", 0, 1, 0, 0, 0, 0, 0, 0, 0))
        assertEquals("malformed user EQ  (00)", d("eq.user_0_4", 0))
        assertEquals("id 1: 80, id 2: 75  (01 50 02 4b)", d("battery.levels", 1, 80, 2, 75))
        assertEquals("258  (01 00 00 01 02)", d("core.protocol_info.1", 1, 0, 0, 1, 2))
        assertEquals("(empty)", d("led"))
    }

    @Test
    fun sessionSendsOnlyAdvertisedReviewedReads() {
        val sent = mutableListOf<ByteArray>()
        val session = GaiaSession(send = { sent += it })
        val led = DevProbes.find("led")!!
        assertFalse(session.probe(led)) // features not known yet
        session.onBytes(Gaia.encode(0, 0x01, byteArrayOf(0, 5, 1, 19, 1), type = GaiaType.RESPONSE))
        sent.clear()
        assertTrue(session.probe(led))
        assertArrayEquals(led.encode(), sent.single())
        sent.clear()
        assertFalse(session.probe(DevProbes.find("anc_v3.mode")!!)) // not advertised
        assertFalse(session.probe(led.copy(id = "made_up"))) // not in the reviewed list
        assertFalse(session.probe(led.copy(payload = listOf(1)))) // altered payload
        assertTrue(sent.isEmpty())
    }
}
