package org.opendrop.protocol

data class SpaceTravelState(
    val handshakeDone: Boolean = false,
    val firmwareVersion: String? = null,
    val features: Map<Int, Int> = emptyMap(),
    /** Raw preset id; [EqPreset.of] maps it to a known preset. */
    val eqPresetId: Int? = null,
) {
    val eqPreset: EqPreset? get() = eqPresetId?.let(EqPreset::of)
    val supportsEq: Boolean get() = SpaceTravelCommands.FEATURE_MUSIC in features
}

/**
 * Transport-independent session logic. The caller owns the connection: it
 * passes outgoing bytes to [send] and feeds everything it reads into [onBytes].
 * Not thread-safe; call it from one thread.
 */
class SpaceTravelSession(
    private val send: (ByteArray) -> Unit,
    private val onState: (SpaceTravelState) -> Unit = {},
    private val onFrame: (incoming: Boolean, frame: GaiaFrame) -> Unit = { _, _ -> },
) {
    private val decoder = GaiaStreamDecoder()

    var state = SpaceTravelState()
        private set

    /** Start the same connect sequence MOONDROP Link uses. */
    fun start() = transmit(SpaceTravelCommands.handshake())

    fun setEq(preset: EqPreset) = transmit(SpaceTravelCommands.setEq(preset))

    fun refreshEq() = transmit(SpaceTravelCommands.getSelectedEq())

    fun onBytes(data: ByteArray) {
        for (frame in decoder.feed(data)) {
            onFrame(true, frame)
            handle(SpaceTravelParser.parse(frame))
        }
    }

    private fun handle(event: SpaceTravelEvent) {
        when (event) {
            is SpaceTravelEvent.Handshake -> {
                update(state.copy(handshakeDone = true))
                transmit(SpaceTravelCommands.getSupportedFeatures())
            }
            is SpaceTravelEvent.Features -> {
                update(state.copy(features = event.features))
                transmit(SpaceTravelCommands.registerNotifications(SpaceTravelCommands.FEATURE_CORE))
                if (SpaceTravelCommands.FEATURE_MUSIC in event.features) {
                    transmit(SpaceTravelCommands.registerNotifications(SpaceTravelCommands.FEATURE_MUSIC))
                }
                transmit(SpaceTravelCommands.getApplicationVersion())
                if (SpaceTravelCommands.FEATURE_MUSIC in event.features) {
                    transmit(SpaceTravelCommands.getSelectedEq())
                }
            }
            is SpaceTravelEvent.FirmwareVersion -> update(state.copy(firmwareVersion = event.version))
            is SpaceTravelEvent.EqPresetChanged -> update(state.copy(eqPresetId = event.presetId))
            is SpaceTravelEvent.Error, SpaceTravelEvent.Other -> Unit
        }
    }

    private fun transmit(bytes: ByteArray) {
        GaiaStreamDecoder().feed(bytes).firstOrNull()?.let { onFrame(false, it) }
        send(bytes)
    }

    private fun update(new: SpaceTravelState) {
        if (new != state) {
            state = new
            onState(new)
        }
    }
}
