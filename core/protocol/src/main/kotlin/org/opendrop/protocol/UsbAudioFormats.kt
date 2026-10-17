package org.opendrop.protocol

/** What one playback alternate setting of a USB audio streaming interface carries. */
data class UsbStreamFormat(
    val interfaceId: Int,
    val alternate: Int,
    /** Valid bits per sample (bBitResolution). */
    val bits: Int,
    /** Bytes per sample on the wire (bSubslotSize / bSubframeSize). */
    val subslotBytes: Int,
    val channels: Int?,
    /** PCM, or raw data (UAC2 bmFormats bit 31; DACs use it for native DSD). */
    val raw: Boolean,
    /** Discrete rates in Hz; UAC1 only. UAC2 keeps rates in the clock, not the descriptors. */
    val sampleRates: List<Int> = emptyList(),
)

/**
 * Reads the playback formats a USB audio device declares in its standard
 * configuration descriptors (USB Audio Class 1 and 2). Nothing is sent to
 * the device: Android caches these descriptors.
 */
object UsbAudioFormats {
    private const val DESC_INTERFACE = 0x04
    private const val DESC_ENDPOINT = 0x05
    private const val DESC_CS_INTERFACE = 0x24
    private const val AS_GENERAL = 0x01
    private const val FORMAT_TYPE = 0x02
    private const val CLASS_AUDIO = 0x01
    private const val SUBCLASS_STREAMING = 0x02
    private const val PROTOCOL_UAC2 = 0x20
    private const val FORMAT_TYPE_I = 0x01
    private const val RAW_DATA_BIT = 31

    private class Alt(val id: Int, val alternate: Int, val uac2: Boolean) {
        var channels: Int? = null
        var raw = false
        var bits: Int? = null
        var subslot: Int? = null
        var rates: List<Int> = emptyList()
        var playback = false
    }

    /** Playback formats, one per alternate setting with an OUT data endpoint, in descriptor order. */
    fun parse(raw: ByteArray?): List<UsbStreamFormat> {
        if (raw == null) return emptyList()
        val alts = mutableListOf<Alt>()
        var current: Alt? = null
        var i = 0
        while (i + 1 < raw.size) {
            val length = u(raw, i)
            if (length < 2 || i + length > raw.size) break
            val type = u(raw, i + 1)
            when {
                type == DESC_INTERFACE && length >= 9 -> {
                    val streaming = u(raw, i + 5) == CLASS_AUDIO && u(raw, i + 6) == SUBCLASS_STREAMING
                    current = if (streaming && u(raw, i + 4) > 0) {
                        Alt(u(raw, i + 2), u(raw, i + 3), u(raw, i + 7) == PROTOCOL_UAC2).also(alts::add)
                    } else {
                        null
                    }
                }
                type == DESC_CS_INTERFACE && current != null && length >= 3 -> parseClassSpecific(current, raw, i, length)
                type == DESC_ENDPOINT && current != null && length >= 4 -> {
                    val out = (u(raw, i + 2) and 0x80) == 0
                    val data = ((u(raw, i + 3) shr 4) and 0x03) == 0
                    if (out && data) current.playback = true
                }
            }
            i += length
        }
        return alts.filter { it.playback && it.bits != null }.map {
            UsbStreamFormat(it.id, it.alternate, it.bits!!, it.subslot ?: 0, it.channels, it.raw, it.rates)
        }
    }

    private fun parseClassSpecific(alt: Alt, raw: ByteArray, i: Int, length: Int) {
        when (u(raw, i + 2)) {
            AS_GENERAL -> if (alt.uac2 && length >= 11) {
                val formats = (0..3).fold(0L) { acc, b -> acc or (u(raw, i + 6 + b).toLong() shl (8 * b)) }
                alt.raw = (formats and (1L shl RAW_DATA_BIT)) != 0L
                alt.channels = u(raw, i + 10)
            }
            FORMAT_TYPE -> if (u(raw, i + 3) == FORMAT_TYPE_I) {
                if (alt.uac2 && length >= 6) {
                    alt.subslot = u(raw, i + 4)
                    alt.bits = u(raw, i + 5)
                } else if (!alt.uac2 && length >= 8) {
                    alt.channels = u(raw, i + 4)
                    alt.subslot = u(raw, i + 5)
                    alt.bits = u(raw, i + 6)
                    val count = u(raw, i + 7)
                    alt.rates = (0 until count).mapNotNull { r ->
                        val o = i + 8 + 3 * r
                        if (o + 2 < i + length) u(raw, o) or (u(raw, o + 1) shl 8) or (u(raw, o + 2) shl 16) else null
                    }
                }
            }
        }
    }

    private fun u(raw: ByteArray, i: Int) = raw[i].toInt() and 0xFF
}
