package org.opendrop.protocol

/**
 * Something a Moondrop device can do that OpenDrop could show a control for.
 * One capability can come in several GAIA feature versions; [features] lists
 * them all. The ones the device reports decide whether it has the capability.
 */
enum class Capability(val label: String, vararg ids: Int) {
    EQ_PRESETS("EQ presets", GaiaFeature.MUSIC_PROCESSING),
    BATTERY("Battery (left, right, case)", GaiaFeature.BATTERY),
    NOISE_CONTROL("Noise cancelling", GaiaFeature.ANC, GaiaFeature.ANC_V2, GaiaFeature.ANC_V3),
    TOUCH_CONTROLS("Touch controls", GaiaFeature.TOUCH_V2, GaiaFeature.TOUCH_V3, GaiaFeature.TOUCH_V4),
    FIND_EARBUDS("Find my earbuds", GaiaFeature.FIND_EARBUDS),
    CODEC("Codec", GaiaFeature.CODEC),
    AUTO_POWER_OFF("Auto power-off", GaiaFeature.POWER_TIMEOUT),
    LED("LED", GaiaFeature.LED),
    WEAR_SENSOR("Wear sensor", GaiaFeature.WEAR_SENSOR),
    MULTIPOINT("Multipoint", GaiaFeature.MULTIPOINT),
    SPATIAL_AUDIO("Spatial audio", GaiaFeature.SPATIAL_AUDIO),
    DYNAMIC_BASS("Dynamic bass", GaiaFeature.DYNAMIC_BASS),
    CHANNEL_SWAP("Left/right swap", GaiaFeature.LR_CHANNEL),
    DUAL_MIC_NOISE_REDUCTION("Dual-mic noise reduction", GaiaFeature.DUAL_MIC_ENC),
    VOICE_PROMPTS("Voice prompts", GaiaFeature.VOICE_PROMPTS);

    val features: Set<Int> = ids.toSet()
}

/** How far OpenDrop supports a capability on the connected device. */
enum class Support {
    /** OpenDrop reads it and changes it. */
    CONTROL,

    /** OpenDrop shows it but sends nothing that changes it (unknown model, or a model we lack data for). */
    READ_ONLY,

    /** The device has it, but OpenDrop has no captured commands for it yet. A device report helps. */
    NEEDS_CAPTURE,
}

/**
 * What OpenDrop can do with each capability the device reports, in
 * [Capability] order. Empty until the feature list is complete.
 */
val GaiaDeviceState.capabilities: Map<Capability, Support>
    get() = if (!featuresKnown) {
        emptyMap()
    } else {
        Capability.entries
            .filter { c -> c.features.any { it in features } }
            .associateWith(::supportFor)
    }

/** Null when the device doesn't have [capability] (or hasn't said yet). */
fun GaiaDeviceState.support(capability: Capability): Support? = capabilities[capability]

private fun GaiaDeviceState.supportFor(capability: Capability): Support = when (capability) {
    Capability.EQ_PRESETS -> if (switchableEqPresets.isNotEmpty()) Support.CONTROL else Support.READ_ONLY
    else -> Support.NEEDS_CAPTURE
}
