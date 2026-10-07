package org.opendrop.protocol

/** EQ presets on the original Space Travel (GAIA music processing feature). */
enum class EqPreset(val id: Int, val label: String) {
    REFERENCE(0, "Reference"),
    BASSHEAD(1, "Basshead"),
    MONITOR(2, "Monitor");

    companion object {
        fun of(id: Int): EqPreset? = entries.firstOrNull { it.id == id }
    }
}

/** Commands verified against the MOONDROP Link capture. */
object SpaceTravelCommands {
    const val FEATURE_CORE = 0
    const val FEATURE_MUSIC = 5

    const val CORE_GET_SUPPORTED_FEATURES = 0x01
    const val CORE_GET_APP_VERSION = 0x05
    const val CORE_REGISTER_NOTIFICATION = 0x07

    const val MUSIC_GET_SELECTED_EQ = 0x02
    const val MUSIC_SET_EQ = 0x03
    const val MUSIC_NOTIF_EQ_SET_CHANGED = 0x01

    /** Legacy "get API version"; the reply sets bit 0x8000. */
    const val V2_GET_API_VERSION = 0x0300

    fun handshake(): ByteArray = Gaia.encodeV2(V2_GET_API_VERSION)
    fun getSupportedFeatures(): ByteArray = Gaia.encode(FEATURE_CORE, CORE_GET_SUPPORTED_FEATURES)
    fun registerNotifications(feature: Int): ByteArray =
        Gaia.encode(FEATURE_CORE, CORE_REGISTER_NOTIFICATION, byteArrayOf(feature.toByte()))
    fun getApplicationVersion(): ByteArray = Gaia.encode(FEATURE_CORE, CORE_GET_APP_VERSION)
    fun getSelectedEq(): ByteArray = Gaia.encode(FEATURE_MUSIC, MUSIC_GET_SELECTED_EQ)
    fun setEq(preset: EqPreset): ByteArray =
        Gaia.encode(FEATURE_MUSIC, MUSIC_SET_EQ, byteArrayOf(preset.id.toByte()))
}

/** What a received frame means for the app. */
sealed interface SpaceTravelEvent {
    data class Handshake(val protocol: Int, val apiMajor: Int, val apiMinor: Int) : SpaceTravelEvent
    data class Features(val features: Map<Int, Int>) : SpaceTravelEvent
    data class FirmwareVersion(val version: String) : SpaceTravelEvent
    data class EqPresetChanged(val presetId: Int) : SpaceTravelEvent
    data class Error(val feature: Int, val cmd: Int, val payload: ByteArray) : SpaceTravelEvent
    data object Other : SpaceTravelEvent
}

object SpaceTravelParser {
    fun parse(frame: GaiaFrame): SpaceTravelEvent {
        val p = frame.payload
        if (frame.vendor == Gaia.VENDOR_V2) {
            return if (frame.command == (SpaceTravelCommands.V2_GET_API_VERSION or 0x8000) && p.size >= 4 && p[0].toInt() == 0) {
                SpaceTravelEvent.Handshake(p.u(1), p.u(2), p.u(3))
            } else {
                SpaceTravelEvent.Other
            }
        }
        if (frame.vendor != Gaia.VENDOR_V3) return SpaceTravelEvent.Other
        val key = Triple(frame.feature, frame.type, frame.cmd)
        return when {
            frame.type == GaiaType.ERROR -> SpaceTravelEvent.Error(frame.feature, frame.cmd, p)
            key == Triple(SpaceTravelCommands.FEATURE_CORE, GaiaType.RESPONSE, SpaceTravelCommands.CORE_GET_SUPPORTED_FEATURES) && p.isNotEmpty() ->
                SpaceTravelEvent.Features((1 until p.size - 1 step 2).associate { p.u(it) to p.u(it + 1) })
            key == Triple(SpaceTravelCommands.FEATURE_CORE, GaiaType.RESPONSE, SpaceTravelCommands.CORE_GET_APP_VERSION) ->
                SpaceTravelEvent.FirmwareVersion(String(p, Charsets.US_ASCII))
            key == Triple(SpaceTravelCommands.FEATURE_MUSIC, GaiaType.RESPONSE, SpaceTravelCommands.MUSIC_GET_SELECTED_EQ) && p.isNotEmpty() ->
                SpaceTravelEvent.EqPresetChanged(p.u(0))
            key == Triple(SpaceTravelCommands.FEATURE_MUSIC, GaiaType.NOTIFICATION, SpaceTravelCommands.MUSIC_NOTIF_EQ_SET_CHANGED) && p.isNotEmpty() ->
                SpaceTravelEvent.EqPresetChanged(p.u(0))
            else -> SpaceTravelEvent.Other
        }
    }

    private fun ByteArray.u(i: Int) = this[i].toInt() and 0xFF
}
