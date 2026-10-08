package org.opendrop.protocol

/**
 * GAIA-over-RFCOMM framing, as observed on the Moondrop Space Travel.
 * See docs/protocol/space-travel.md.
 *
 * ```
 * FF | version | flags | length | vendor (u16 BE) | command (u16 BE) | payload
 * command = (feature << 9) | (type << 7) | (cmd & 0x7F)
 * ```
 */
object Gaia {
    const val SOF = 0xFF
    const val FLAG_CHECKSUM = 0x01
    const val FLAG_LENGTH_16 = 0x02

    /** Legacy GAIA vendor, used only for the first version handshake. */
    const val VENDOR_V2 = 0x000A

    /** GAIA v3 (QTIL). */
    const val VENDOR_V3 = 0x001D

    /** Transport version byte used after the handshake. */
    const val VERSION_V3 = 0x04

    fun encode(feature: Int, cmd: Int, payload: ByteArray = ByteArray(0), type: GaiaType = GaiaType.COMMAND): ByteArray {
        require(feature in 0..0x7F) { "feature id $feature doesn't fit the 7-bit field" }
        require(cmd in 0..0x7F) { "command id $cmd doesn't fit the 7-bit field" }
        val command = (feature shl 9) or (type.id shl 7) or cmd
        return frame(VERSION_V3, VENDOR_V3, command, payload)
    }

    /** Legacy v2 frame (only the "get API version" handshake uses this). */
    fun encodeV2(command: Int, payload: ByteArray = ByteArray(0)): ByteArray =
        frame(0x01, VENDOR_V2, command, payload)

    private fun frame(version: Int, vendor: Int, command: Int, payload: ByteArray): ByteArray {
        require(payload.size <= 0xFF) { "payload too long for 1-byte length" }
        val out = ByteArray(8 + payload.size)
        out[0] = SOF.toByte()
        out[1] = version.toByte()
        out[2] = 0
        out[3] = payload.size.toByte()
        out[4] = (vendor shr 8).toByte()
        out[5] = vendor.toByte()
        out[6] = (command shr 8).toByte()
        out[7] = command.toByte()
        payload.copyInto(out, 8)
        return out
    }
}

enum class GaiaType(val id: Int) {
    COMMAND(0), NOTIFICATION(1), RESPONSE(2), ERROR(3);

    companion object {
        /** Frame.type masks ids to 2 bits; anything else falls back to COMMAND. */
        fun of(id: Int): GaiaType = entries.firstOrNull { it.id == id } ?: COMMAND
    }
}

class GaiaFrame(
    val version: Int,
    val flags: Int,
    val vendor: Int,
    val command: Int,
    val payload: ByteArray,
) {
    val feature: Int get() = command shr 9
    val type: GaiaType get() = GaiaType.of((command shr 7) and 0x03)
    val cmd: Int get() = command and 0x7F

    override fun toString(): String =
        if (vendor == Gaia.VENDOR_V2) {
            "v2 0x%04x %s".format(command, payload.toHex()).trimEnd()
        } else {
            "f%d %s 0x%02x %s".format(feature, type.name.lowercase(), cmd, payload.toHex()).trimEnd()
        }
}

/** Streaming decoder: one RFCOMM read may hold several frames, or part of one. */
class GaiaStreamDecoder {
    private var buf = ByteArray(0)
    private var start = 0

    fun feed(data: ByteArray): List<GaiaFrame> {
        buf += data
        val frames = mutableListOf<GaiaFrame>()
        while (true) {
            val headerStart = indexOfSof()
            if (headerStart < 0) {
                buf = ByteArray(0)
                start = 0
                break
            }
            start = headerStart
            val available = buf.size - start
            if (available < 4) break
            val flags = u8(start + 2)
            val (length, header) = if (flags and Gaia.FLAG_LENGTH_16 != 0) {
                if (available < 5) break
                ((u8(start + 3) shl 8) or u8(start + 4)) to 5
            } else {
                u8(start + 3) to 4
            }
            // A checksummed frame carries one more byte after the payload. It
            // is consumed but not verified: no Moondrop capture uses one, so
            // the algorithm is unknown (MAINTENANCE.md, deferred X1).
            val total = header + 4 + length + if (flags and Gaia.FLAG_CHECKSUM != 0) 1 else 0
            if (available < total) break
            frames += GaiaFrame(
                version = u8(start + 1),
                flags = flags,
                vendor = (u8(start + header) shl 8) or u8(start + header + 1),
                command = (u8(start + header + 2) shl 8) or u8(start + header + 3),
                payload = buf.copyOfRange(start + header + 4, start + header + 4 + length),
            )
            start += total
            if (start > 8_192) { // don't let a long stream grow the buffer forever
                buf = buf.copyOfRange(start, buf.size)
                start = 0
            }
        }
        return frames
    }

    private fun indexOfSof(): Int {
        var i = start
        while (i < buf.size) {
            if (buf[i].toInt() and 0xFF == Gaia.SOF) return i
            i++
        }
        return -1
    }

    private fun u8(i: Int): Int = buf[i].toInt() and 0xFF
}

fun ByteArray.toHex(): String = joinToString(" ") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    val clean = filterNot { it.isWhitespace() }
    require(clean.length % 2 == 0) { "odd hex length" }
    require(clean.matches(Regex("[0-9a-fA-F]*"))) { "not hex: \"$clean\"" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
