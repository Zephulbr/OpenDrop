package org.opendrop.app.device

import org.opendrop.protocol.EqPreset

/**
 * The UiState-to-display rules shared by the widgets, the tile and the
 * connection notification, so they can't drift apart (MAINTENANCE F41).
 * Each surface keeps its own fallback strings and layout.
 */
object Presentation {
    /** The device a widget, tile or notification should talk about. */
    fun device(s: UiState): PairedDevice? = s.selected ?: s.autoConnect

    /** Presets worth showing buttons or a switcher for; only while connected. */
    fun eqPresets(s: UiState): List<EqPreset> =
        if (s.connection == Connection.Connected) s.device.switchableEqPresets else emptyList()

    /** Picked, but not linked (or the last link failed). */
    fun isDisconnected(s: UiState): Boolean =
        s.connection == Connection.Disconnected || s.connection is Connection.Failed
}
