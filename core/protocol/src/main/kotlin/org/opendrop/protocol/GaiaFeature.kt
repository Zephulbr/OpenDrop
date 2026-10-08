package org.opendrop.protocol

/**
 * GAIA v3 feature ids under vendor 0x001D, as MOONDROP Link 2.26.1c knows them.
 * 0 to 12 are Qualcomm's; 13 and up are Moondrop additions. See docs/devices.md.
 * A device lists the ones it has in its "supported features" reply.
 */
object GaiaFeature {
    const val CORE = 0
    const val EARBUD = 1
    const val ANC = 2
    const val VOICE_UI = 3
    const val DEBUG = 4
    const val MUSIC_PROCESSING = 5
    const val UPGRADE = 6
    const val HANDSET_SERVICE = 7
    const val AUDIO_CURATION = 8
    const val EARBUD_FIT = 9
    const val VOICE_PROCESSING = 10
    const val GESTURE_CONFIGURATION = 11
    const val STATISTICS = 12
    const val BATTERY = 13
    const val VOICE_PROMPTS = 14
    const val DAC_GAIN = 15
    const val CODEC = 16
    const val WEAR_SENSOR = 17
    const val SPATIAL_AUDIO = 18
    const val LED = 19
    const val MULTIPOINT = 20
    const val BT_ADDRESS = 21
    const val TOUCH_V2 = 22
    const val AUDIO_RESOURCE = 23
    const val POWER_CONTROL = 24
    const val POWER_TIMEOUT = 25
    const val TOUCH_V3 = 26
    const val DYNAMIC_BASS = 27
    const val AUDIO_FILE_STORAGE = 29
    const val LR_CHANNEL = 30
    const val TOUCH_V4 = 31
    const val ANC_V2 = 32
    const val ANC_V3 = 33
    const val FIND_EARBUDS = 34
    const val DUAL_MIC_ENC = 35

    private val names = mapOf(
        CORE to "Core",
        EARBUD to "Earbud",
        ANC to "ANC",
        VOICE_UI to "Voice assistant",
        DEBUG to "Debug",
        MUSIC_PROCESSING to "EQ",
        UPGRADE to "Firmware update",
        HANDSET_SERVICE to "Handset service",
        AUDIO_CURATION to "Audio curation",
        EARBUD_FIT to "Earbud fit",
        VOICE_PROCESSING to "Voice processing",
        GESTURE_CONFIGURATION to "Gestures",
        STATISTICS to "Statistics",
        BATTERY to "Battery",
        VOICE_PROMPTS to "Voice prompts",
        DAC_GAIN to "DAC gain",
        CODEC to "Codec",
        WEAR_SENSOR to "Wear sensor",
        SPATIAL_AUDIO to "Spatial audio",
        LED to "LED",
        MULTIPOINT to "Multipoint",
        BT_ADDRESS to "Bluetooth address",
        TOUCH_V2 to "Touch controls (v2)",
        AUDIO_RESOURCE to "Prompt sounds",
        POWER_CONTROL to "Power control",
        POWER_TIMEOUT to "Auto power-off",
        TOUCH_V3 to "Touch controls (v3)",
        DYNAMIC_BASS to "Dynamic bass",
        AUDIO_FILE_STORAGE to "Audio file storage",
        LR_CHANNEL to "Left/right swap",
        TOUCH_V4 to "Touch controls (v4)",
        ANC_V2 to "ANC (v2)",
        ANC_V3 to "ANC (v3)",
        FIND_EARBUDS to "Find my earbuds",
        DUAL_MIC_ENC to "Dual-mic noise reduction",
    )

    fun name(id: Int): String = names[id] ?: "Feature $id"
}
