package org.opendrop.protocol

/**
 * GAIA v3 feature ids under vendor 0x001D, as MOONDROP Link 2.26.2c knows them.
 * 0 to 12 are Qualcomm's; 13 and up are Moondrop additions; there is no 28.
 * See docs/protocol/moondrop-gaia-features.md.
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

    /** Packet-log keys, the same as tools/gaia.py's FEATURES. */
    private val keys = mapOf(
        CORE to "core",
        EARBUD to "earbud",
        ANC to "anc",
        VOICE_UI to "voice_ui",
        DEBUG to "debug",
        MUSIC_PROCESSING to "music_processing",
        UPGRADE to "upgrade",
        HANDSET_SERVICE to "handset_service",
        AUDIO_CURATION to "audio_curation",
        EARBUD_FIT to "earbud_fit",
        VOICE_PROCESSING to "voice_processing",
        GESTURE_CONFIGURATION to "gesture_configuration",
        STATISTICS to "statistics",
        BATTERY to "battery",
        VOICE_PROMPTS to "voice_prompts",
        DAC_GAIN to "dac_gain",
        CODEC to "codec",
        WEAR_SENSOR to "wear_sensor",
        SPATIAL_AUDIO to "spatial_audio",
        LED to "led",
        MULTIPOINT to "multipoint",
        BT_ADDRESS to "bt_address",
        TOUCH_V2 to "touch_v2",
        AUDIO_RESOURCE to "audio_resource",
        POWER_CONTROL to "power_control",
        POWER_TIMEOUT to "power_timeout",
        TOUCH_V3 to "touch_v3",
        DYNAMIC_BASS to "dynamic_bass",
        AUDIO_FILE_STORAGE to "audio_file_storage",
        LR_CHANNEL to "lr_channel",
        TOUCH_V4 to "touch_v4",
        ANC_V2 to "anc_v2",
        ANC_V3 to "anc_v3",
        FIND_EARBUDS to "find_earbuds",
        DUAL_MIC_ENC to "dual_mic_enc",
    )

    fun name(id: Int): String = names[id] ?: "Feature $id"

    fun key(id: Int): String = keys[id] ?: "feature_$id"
}
