package org.opendrop.protocol

import java.util.Locale

/**
 * A vendor control transfer pair: OUT with [packet], wait, then IN with the
 * same length. Fields come from docs/protocol/usb-dac.md (Link 2.26.2c, and
 * mdrop's working Dawn Pro reads; no capture), so these are offered only in
 * the developer console, and only for the family the device was identified as.
 */
data class UsbRead(
    val id: String,
    val family: UsbFamily,
    val label: String,
    val requestTypeOut: Int,
    val requestTypeIn: Int,
    val request: Int,
    val requestIn: Int,
    val value: Int,
    val index: Int,
    val packet: List<Int>,
    /** Pause between OUT and IN, as Link waits. */
    val replyDelayMs: Long,
) {
    fun packetBytes(): ByteArray = ByteArray(packet.size) { packet[it].toByte() }

    /** "c0 a5 a3 00 00 00 00 · 0x43/0xa0, 0xc3/0xa1 idx 0x09a0", what goes on the wire. */
    fun wire(): String = packet.joinToString(" ") { String.format(Locale.ROOT, "%02x", it) } +
        String.format(Locale.ROOT, " · 0x%02x/0x%02x, 0x%02x/0x%02x idx 0x%04x", requestTypeOut, request, requestTypeIn, requestIn, index)
}

/**
 * A reversible developer-console setting: [cmd] with one of [values] (index =
 * wire value), sent like [UsbRead]'s OUT and then checked by running
 * [readBack] and comparing byte [readBackIndex]. Nothing is saved to flash.
 */
data class UsbWrite(
    val id: String,
    val label: String,
    val cmd: Int,
    val values: List<String>,
    val readBack: UsbRead,
    val readBackIndex: Int,
) {
    val family: UsbFamily get() = readBack.family

    /** The OUT packet for [value]; same framing as [readBack]'s. */
    fun packet(value: Int): ByteArray {
        require(value in values.indices) { "value $value out of range for $id" }
        return readBack.packet.toMutableList().also {
            it[2] = cmd
            it[3] = value
        }.let { p -> ByteArray(p.size) { p[it].toByte() } }
    }

    /** The value the read-back reply holds, or null if it's too short or isn't [readBack]'s reply. */
    fun readBackValue(reply: ByteArray): Int? =
        if (reply.size > readBackIndex && (reply[2].toInt() and 0xFF) == readBack.packet[2]) {
            reply[readBackIndex].toInt() and 0xFF
        } else {
            null
        }
}

object UsbProbes {
    /**
     * Comtrue (Dawn 3.5 / 4.4 / Pro, Moonriver2 Ti, Discdream): 7-byte
     * `C0 A5 <cmd> <value> 00 00 00` packets over vendor control transfers.
     * The two get commands, and of the set commands only filter and LED (see
     * [writes]); gain and volume are left out on purpose.
     */
    object Comtrue {
        const val REQUEST_TYPE_OUT = 0x43
        const val REQUEST_TYPE_IN = 0xC3
        const val REQUEST = 0xA0

        /** The reply is read with 0xA1 (mdrop); 0xA0 gets a stall on the Dawn Pro. */
        const val REQUEST_IN = 0xA1
        const val INDEX = 0x09A0
        const val GET_VOLUME = 0xA2
        const val GET_STATE = 0xA3
        const val SET_FILTER = 0x01
        const val SET_LED = 0x06
        const val REPLY_DELAY_MS = 100L

        /** Raw volume runs from 0x00 (loudest) to 0x70 (quietest), per mdrop. */
        const val VOLUME_QUIETEST = 0x70

        val FILTERS = listOf(
            "fast roll-off, low latency",
            "fast roll-off, phase-compensated",
            "slow roll-off, low latency",
            "slow roll-off, phase-compensated",
            "non-oversampling",
        )
        val GAINS = listOf("low", "high")
        val LEDS = listOf("on", "off until replug", "off")

        fun read(id: String, label: String, cmd: Int) = UsbRead(
            id, UsbFamily.COMTRUE, label, REQUEST_TYPE_OUT, REQUEST_TYPE_IN, REQUEST, REQUEST_IN, 0, INDEX,
            listOf(0xC0, 0xA5, cmd, 0, 0, 0, 0), REPLY_DELAY_MS,
        )
    }

    private val comtrueState = Comtrue.read("comtrue.state", "Filter, gain and LED", Comtrue.GET_STATE)

    val reads: List<UsbRead> = listOf(
        comtrueState,
        Comtrue.read("comtrue.volume", "Device volume", Comtrue.GET_VOLUME),
    )

    /**
     * The only USB writes: reversible, harmless if wrong, and checked by a
     * read-back. Gain (0x02) and volume (0x04) can be suddenly loud, so they
     * stay out until someone measures them.
     */
    val writes: List<UsbWrite> = listOf(
        UsbWrite("comtrue.filter", "Filter", Comtrue.SET_FILTER, Comtrue.FILTERS, comtrueState, readBackIndex = 3),
        UsbWrite("comtrue.led", "LED", Comtrue.SET_LED, Comtrue.LEDS, comtrueState, readBackIndex = 5),
    )

    fun readsFor(family: UsbFamily?): List<UsbRead> = reads.filter { it.family == family }

    fun writesFor(family: UsbFamily?): List<UsbWrite> = writes.filter { it.family == family }

    /** The console's text for a reply: decoded per Link's reading of it, then the raw bytes. */
    fun describe(read: UsbRead, reply: ByteArray): String {
        if (reply.isEmpty()) return "(empty)"
        val hex = reply.toHex()
        fun u(i: Int) = reply[i].toInt() and 0xFF
        fun named(names: List<String>, v: Int) = "$v (${names.getOrNull(v) ?: "unknown"})"
        val decoded = when (read.packet.getOrNull(2)) {
            Comtrue.GET_STATE -> if (reply.size >= 6) {
                "filter ${named(Comtrue.FILTERS, u(3))}, gain ${named(Comtrue.GAINS, u(4))}, " +
                    "LED ${named(Comtrue.LEDS, u(5))}"
            } else {
                null
            }
            Comtrue.GET_VOLUME -> if (reply.size >= 5) {
                val raw = u(4)
                val percent = (Comtrue.VOLUME_QUIETEST - raw.coerceAtMost(Comtrue.VOLUME_QUIETEST)) * 100 /
                    Comtrue.VOLUME_QUIETEST
                String.format(Locale.ROOT, "volume %d%% (raw 0x%02x; 0x00 loudest, 0x70 quietest)", percent, raw)
            } else {
                null
            }
            else -> null
        }
        return if (decoded == null) hex else "$decoded  ($hex)"
    }
}
