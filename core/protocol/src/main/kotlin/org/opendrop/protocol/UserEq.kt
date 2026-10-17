package org.opendrop.protocol

/** One user EQ band as music processing command 5 returns it (Link 2.26.2c). */
data class UserEqBand(
    val frequencyHz: Int,
    /** Q × 4096 on the wire. */
    val q: Double,
    /** 0 bypass, 1–12 pass/shelf/tilt variants, 13 parametric EQ. */
    val filterType: Int,
    /** dB × 60 on the wire, signed. */
    val gainDb: Double,
)

/**
 * A user EQ reply. [startBand] and [endBand] are kept as sent: whether `end`
 * is inclusive isn't settled (the 2026-10-07 ST1 reply is start 0, end 1 with
 * one band), so callers shouldn't check them against [bands].
 */
data class UserEqConfig(val startBand: Int, val endBand: Int, val bands: List<UserEqBand>)

object UserEq {
    private const val BAND_SIZE = 7

    /** Null when the payload isn't `[start][end]` + 7 bytes per band. */
    fun parse(payload: ByteArray): UserEqConfig? {
        if (payload.size < 2 || (payload.size - 2) % BAND_SIZE != 0) return null
        val bands = (2 until payload.size step BAND_SIZE).map { o ->
            UserEqBand(
                frequencyHz = payload.u16(o),
                q = payload.u16(o + 2) / 4096.0,
                filterType = payload[o + 4].toInt() and 0xFF,
                gainDb = payload.u16(o + 5).toShort() / 60.0,
            )
        }
        return UserEqConfig(payload[0].toInt() and 0xFF, payload[1].toInt() and 0xFF, bands)
    }

    private fun ByteArray.u16(i: Int) = ((this[i].toInt() and 0xFF) shl 8) or (this[i + 1].toInt() and 0xFF)
}
