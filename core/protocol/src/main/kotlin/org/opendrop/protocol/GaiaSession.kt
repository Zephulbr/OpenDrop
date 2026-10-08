package org.opendrop.protocol

data class GaiaDeviceState(
    val handshakeDone: Boolean = false,
    /** Feature id to version, complete once [featuresKnown]. */
    val features: Map<Int, Int> = emptyMap(),
    val featuresKnown: Boolean = false,
    val variantName: String? = null,
    val firmwareVersion: String? = null,
    /** Preset ids the device offers, once it has said. */
    val availableEqPresets: List<Int>? = null,
    /** Raw preset id; [EqPreset.of] maps it to a known preset. */
    val eqPresetId: Int? = null,
) {
    /** The catalogue entry for this device, or null if it isn't a known model (yet). */
    val model: MoondropModel? get() = MoondropModels.find(variantName)

    val supportsEq: Boolean get() = GaiaFeature.MUSIC_PROCESSING in features

    /**
     * Whether OpenDrop sends anything that changes the device. Only for models
     * in the catalogue; anything else stays read-only (state and packet log).
     */
    val canControl: Boolean get() = model != null

    /**
     * Presets the user can switch to: named ones the device says it has. Only
     * models whose presets we know the names of; others show the id read-only.
     */
    val switchableEqPresets: List<EqPreset>
        get() = if (canControl && supportsEq && model?.spaceTravelPresets == true) {
            EqPreset.entries.filter { availableEqPresets == null || it.id in availableEqPresets }
        } else {
            emptyList()
        }

    val eqPreset: EqPreset? get() = eqPresetId?.let(EqPreset::of)

    /**
     * The switchable preset after the current one, wrapping around; the first
     * one if the current preset isn't switchable. For one-tap cycling (tile,
     * automations). Null if nothing is switchable.
     */
    val nextEqPreset: EqPreset?
        get() {
            val options = switchableEqPresets
            if (options.isEmpty()) return null
            return options[(options.indexOf(namedEqPreset) + 1) % options.size]
        }

    /** [eqPreset], but only for models whose preset ids mean the Space Travel's names. */
    val namedEqPreset: EqPreset? get() = eqPreset?.takeIf { model?.spaceTravelPresets == true }
}

/**
 * Transport-independent GAIA v3 session for any Moondrop Bluetooth device.
 * The caller owns the connection: it passes outgoing bytes to [send] and feeds
 * everything it reads into [onBytes]. Not thread-safe; call it from one thread.
 */
class GaiaSession(
    private val send: (ByteArray) -> Unit,
    private val onState: (GaiaDeviceState) -> Unit = {},
    private val onFrame: (incoming: Boolean, frame: GaiaFrame) -> Unit = { _, _ -> },
) {
    private val decoder = GaiaStreamDecoder()

    var state = GaiaDeviceState()
        private set

    /** Start the same connect sequence MOONDROP Link uses. */
    fun start() = transmit(GaiaCommands.handshake())

    /** Returns false (and sends nothing) unless [preset] is in [GaiaDeviceState.switchableEqPresets]. */
    fun setEq(preset: EqPreset): Boolean {
        if (preset !in state.switchableEqPresets) return false
        transmit(GaiaCommands.setEq(preset))
        return true
    }

    fun refreshEq() {
        if (state.supportsEq) transmit(GaiaCommands.getSelectedEq())
    }

    fun onBytes(data: ByteArray) {
        for (frame in decoder.feed(data)) {
            onFrame(true, frame)
            handle(GaiaParser.parse(frame))
        }
    }

    private fun handle(event: GaiaEvent) {
        when (event) {
            is GaiaEvent.Handshake -> {
                update(state.copy(handshakeDone = true))
                transmit(GaiaCommands.getSupportedFeatures())
            }
            is GaiaEvent.Features -> {
                if (state.featuresKnown) return
                val features = state.features + event.features
                if (event.more) {
                    update(state.copy(features = features))
                    transmit(GaiaCommands.getSupportedFeaturesNext())
                } else {
                    update(state.copy(features = features, featuresKnown = true))
                    queryDevice()
                }
            }
            is GaiaEvent.VariantName -> update(state.copy(variantName = event.name))
            is GaiaEvent.FirmwareVersion -> update(state.copy(firmwareVersion = event.version))
            is GaiaEvent.AvailableEqPresets -> update(state.copy(availableEqPresets = event.ids))
            is GaiaEvent.EqPresetChanged -> update(state.copy(eqPresetId = event.presetId))
            is GaiaEvent.Error, GaiaEvent.Other -> Unit
        }
    }

    /** Link's sequence first, then the read-only queries our probe verified. */
    private fun queryDevice() {
        val music = state.supportsEq
        transmit(GaiaCommands.registerNotifications(GaiaFeature.CORE))
        if (music) transmit(GaiaCommands.registerNotifications(GaiaFeature.MUSIC_PROCESSING))
        transmit(GaiaCommands.getApplicationVersion())
        if (music) transmit(GaiaCommands.getSelectedEq())
        transmit(GaiaCommands.getVariantName())
        if (music) transmit(GaiaCommands.getAvailableEq())
    }

    private fun transmit(bytes: ByteArray) {
        GaiaStreamDecoder().feed(bytes).firstOrNull()?.let { onFrame(false, it) }
        send(bytes)
    }

    private fun update(new: GaiaDeviceState) {
        if (new != state) {
            state = new
            onState(new)
        }
    }
}
