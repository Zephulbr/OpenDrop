package org.opendrop.protocol

/**
 * Plain-text report of what a device told us, for owners of models OpenDrop
 * doesn't support yet to share with the project. Holds no Bluetooth address.
 */
object DeviceReport {
    fun build(
        state: GaiaDeviceState,
        /** Lines such as app version, phone and Bluetooth name, in order. */
        context: List<Pair<String, String>>,
        log: List<String>,
    ): String = buildString {
        appendLine("OpenDrop device report")
        appendLine()
        context.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine("Variant name: ${state.variantName ?: "not reported"}")
        appendLine("Known model: ${state.model?.let { "${it.name} (${it.chip.name.lowercase()})" } ?: "no"}")
        appendLine("Firmware: ${state.firmwareVersion ?: "not reported"}")
        appendLine()
        appendLine("GAIA features (id: name, version):")
        if (state.features.isEmpty()) appendLine("  none reported")
        state.features.toSortedMap().forEach { (id, version) ->
            appendLine("  $id: ${GaiaFeature.name(id)}, v$version")
        }
        if (state.featuresKnown) {
            appendLine()
            appendLine("OpenDrop support:")
            if (state.capabilities.isEmpty()) appendLine("  none")
            state.capabilities.forEach { (capability, support) ->
                appendLine("  ${capability.label}: ${support.name.lowercase().replace('_', ' ')}")
            }
        }
        if (state.supportsEq) {
            appendLine()
            appendLine("EQ presets available: ${state.availableEqPresets?.joinToString(", ") ?: "not reported"}")
            appendLine("EQ preset selected: ${state.eqPresetId ?: "not reported"}")
        }
        appendLine()
        appendLine("Packet log (${log.size} lines):")
        log.forEach { appendLine(it) }
    }
}
