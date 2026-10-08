package org.opendrop.protocol

/** EQ presets on the original Space Travel (GAIA music processing feature). */
enum class EqPreset(val id: Int, val label: String) {
    REFERENCE(0, "Reference"),
    BASSHEAD(1, "Basshead"),
    MONITOR(2, "Monitor");

    companion object {
        /** The hidden on-device user EQ (5 bands on the Space Travel); never selected by OpenDrop. */
        const val USER_ID = 63

        fun of(id: Int): EqPreset? = entries.firstOrNull { it.id == id }

        /** By name ("basshead", any case) or id ("1"), as automations send it. */
        fun parse(value: String?): EqPreset? {
            val text = value?.trim() ?: return null
            return entries.firstOrNull { it.name.equals(text, ignoreCase = true) } ?: text.toIntOrNull()?.let(::of)
        }
    }
}

/**
 * Read commands and the EQ write that MOONDROP Link sends. All verified on the
 * Space Travel by capture or read-only probe (docs/protocol/space-travel.md).
 */
object GaiaCommands {
    const val CORE_GET_SUPPORTED_FEATURES = 0x01
    const val CORE_GET_SUPPORTED_FEATURES_NEXT = 0x02
    const val CORE_GET_VARIANT_NAME = 0x04
    const val CORE_GET_APP_VERSION = 0x05
    const val CORE_REGISTER_NOTIFICATION = 0x07

    const val MUSIC_GET_AVAILABLE_EQ = 0x01
    const val MUSIC_GET_SELECTED_EQ = 0x02
    const val MUSIC_SET_EQ = 0x03
    const val MUSIC_NOTIF_EQ_SET_CHANGED = 0x01

    /** Legacy "get API version"; the reply sets bit 0x8000. */
    const val V2_GET_API_VERSION = 0x0300

    fun handshake(): ByteArray = Gaia.encodeV2(V2_GET_API_VERSION)
    fun getSupportedFeatures(): ByteArray = Gaia.encode(GaiaFeature.CORE, CORE_GET_SUPPORTED_FEATURES)
    fun getSupportedFeaturesNext(): ByteArray = Gaia.encode(GaiaFeature.CORE, CORE_GET_SUPPORTED_FEATURES_NEXT)
    fun registerNotifications(feature: Int): ByteArray =
        Gaia.encode(GaiaFeature.CORE, CORE_REGISTER_NOTIFICATION, byteArrayOf(feature.toByte()))
    fun getVariantName(): ByteArray = Gaia.encode(GaiaFeature.CORE, CORE_GET_VARIANT_NAME)
    fun getApplicationVersion(): ByteArray = Gaia.encode(GaiaFeature.CORE, CORE_GET_APP_VERSION)
    fun getAvailableEq(): ByteArray = Gaia.encode(GaiaFeature.MUSIC_PROCESSING, MUSIC_GET_AVAILABLE_EQ)
    fun getSelectedEq(): ByteArray = Gaia.encode(GaiaFeature.MUSIC_PROCESSING, MUSIC_GET_SELECTED_EQ)
    fun setEq(presetId: Int): ByteArray =
        Gaia.encode(GaiaFeature.MUSIC_PROCESSING, MUSIC_SET_EQ, byteArrayOf(presetId.toByte()))
    fun setEq(preset: EqPreset): ByteArray = setEq(preset.id)
}

/** What a received frame means for the app. */
sealed interface GaiaEvent {
    data class Handshake(val protocol: Int, val apiMajor: Int, val apiMinor: Int) : GaiaEvent
    /** Feature id to version. [more] means the list continues (ask with "next"). */
    data class Features(val features: Map<Int, Int>, val more: Boolean = false) : GaiaEvent
    data class VariantName(val name: String) : GaiaEvent
    data class FirmwareVersion(val version: String) : GaiaEvent
    data class AvailableEqPresets(val ids: List<Int>) : GaiaEvent
    data class EqPresetChanged(val presetId: Int) : GaiaEvent
    data class Error(val feature: Int, val cmd: Int, val payload: ByteArray) : GaiaEvent
    data object Other : GaiaEvent
}

object GaiaParser {
    fun parse(frame: GaiaFrame): GaiaEvent {
        val p = frame.payload
        if (frame.vendor == Gaia.VENDOR_V2) {
            return if (frame.command == (GaiaCommands.V2_GET_API_VERSION or 0x8000) && p.size >= 4 && p[0].toInt() == 0) {
                GaiaEvent.Handshake(p.u(1), p.u(2), p.u(3))
            } else {
                GaiaEvent.Other
            }
        }
        if (frame.vendor != Gaia.VENDOR_V3) return GaiaEvent.Other
        if (frame.type == GaiaType.ERROR) return GaiaEvent.Error(frame.feature, frame.cmd, p)
        val response = frame.type == GaiaType.RESPONSE
        return when (frame.feature) {
            GaiaFeature.CORE -> when {
                !response -> GaiaEvent.Other
                (frame.cmd == GaiaCommands.CORE_GET_SUPPORTED_FEATURES ||
                    frame.cmd == GaiaCommands.CORE_GET_SUPPORTED_FEATURES_NEXT) && p.isNotEmpty() ->
                    GaiaEvent.Features((1 until p.size - 1 step 2).associate { p.u(it) to p.u(it + 1) }, more = p.u(0) != 0)
                frame.cmd == GaiaCommands.CORE_GET_VARIANT_NAME -> GaiaEvent.VariantName(p.text())
                frame.cmd == GaiaCommands.CORE_GET_APP_VERSION -> GaiaEvent.FirmwareVersion(p.text())
                else -> GaiaEvent.Other
            }
            GaiaFeature.MUSIC_PROCESSING -> when {
                p.isEmpty() -> GaiaEvent.Other
                response && frame.cmd == GaiaCommands.MUSIC_GET_AVAILABLE_EQ ->
                    // Count, then ids: "04 00 01 02 3f" on the Space Travel.
                    GaiaEvent.AvailableEqPresets(p.drop(if (p.u(0) == p.size - 1) 1 else 0).map { it.toInt() and 0xFF })
                response && frame.cmd == GaiaCommands.MUSIC_GET_SELECTED_EQ -> GaiaEvent.EqPresetChanged(p.u(0))
                frame.type == GaiaType.NOTIFICATION && frame.cmd == GaiaCommands.MUSIC_NOTIF_EQ_SET_CHANGED ->
                    GaiaEvent.EqPresetChanged(p.u(0))
                else -> GaiaEvent.Other
            }
            else -> GaiaEvent.Other
        }
    }

    private fun ByteArray.u(i: Int) = this[i].toInt() and 0xFF

    private fun ByteArray.text() = String(this, Charsets.UTF_8).trimEnd('\u0000')
}
