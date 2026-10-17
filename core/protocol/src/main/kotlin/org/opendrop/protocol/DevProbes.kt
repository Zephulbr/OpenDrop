package org.opendrop.protocol

import java.util.Locale

/** How the console shows a probe's reply. */
enum class ReplyFormat { U8, BYTES, ASCII, U32, USER_EQ, PAIRS }

/**
 * A fixed developer-console query. Ids and payloads come from MOONDROP Link
 * 2.26.2c (docs/protocol/moondrop-gaia-features.md, **App** evidence): none is
 * a capture, so they're offered only behind developer options, and only for
 * features the device advertises.
 */
data class DevRead(
    val id: String,
    val feature: Int,
    val cmd: Int,
    val label: String,
    val payload: List<Int> = emptyList(),
    val format: ReplyFormat = ReplyFormat.U8,
    /** The reply is device-unique (serial, address): listed only with "show serial numbers". */
    val identifying: Boolean = false,
) {
    fun encode(): ByteArray = Gaia.encode(feature, cmd, ByteArray(payload.size) { payload[it].toByte() })

    /** True for the reply (or error) to this read. */
    fun matches(frame: GaiaFrame): Boolean = frame.vendor == Gaia.VENDOR_V3 &&
        frame.feature == feature && frame.cmd == cmd &&
        (frame.type == GaiaType.RESPONSE || frame.type == GaiaType.ERROR)
}

object DevProbes {
    private fun read(
        id: String,
        feature: Int,
        cmd: Int,
        label: String,
        vararg payload: Int,
        format: ReplyFormat = ReplyFormat.U8,
        identifying: Boolean = false,
    ) = DevRead(id, feature, cmd, label, payload.toList(), format, identifying)

    val reads: List<DevRead> = buildList {
        val f = GaiaFeature
        add(read("core.version", f.CORE, 0x00, "GAIA version"))
        add(read("core.variant", f.CORE, 0x04, "Variant name", format = ReplyFormat.ASCII))
        add(read("core.app_version", f.CORE, 0x05, "Application version", format = ReplyFormat.ASCII))
        add(read("core.serial", f.CORE, 0x03, "Serial number", format = ReplyFormat.ASCII, identifying = true))
        (1..7).forEach { add(read("core.protocol_info.$it", f.CORE, 0x0C, "Protocol info, param $it", it, format = ReplyFormat.U32)) }
        add(read("core.color", f.CORE, 0x12, "Earbud color"))
        add(read("core.language", f.CORE, 0x13, "Earbud language"))
        add(read("core.left_serial", f.CORE, 0x14, "Left earbud serial", format = ReplyFormat.ASCII, identifying = true))
        add(read("core.right_serial", f.CORE, 0x15, "Right earbud serial", format = ReplyFormat.ASCII, identifying = true))
        add(read("core.tws", f.CORE, 0x16, "TWS connection status"))

        add(read("eq.state", f.MUSIC_PROCESSING, 0x00, "EQ state"))
        add(read("eq.presets", f.MUSIC_PROCESSING, 0x01, "Available EQ presets", format = ReplyFormat.BYTES))
        add(read("eq.selected", f.MUSIC_PROCESSING, 0x02, "Selected EQ preset"))
        add(read("eq.user_bands", f.MUSIC_PROCESSING, 0x04, "User EQ band count"))
        add(read("eq.user_0_4", f.MUSIC_PROCESSING, 0x05, "User EQ, bands 0-4", 0, 4, format = ReplyFormat.USER_EQ))
        add(read("eq.user_1_4", f.MUSIC_PROCESSING, 0x05, "User EQ, bands 1-4", 1, 4, format = ReplyFormat.USER_EQ))

        add(read("battery.types", f.BATTERY, 0x00, "Battery types", format = ReplyFormat.BYTES))
        add(read("battery.levels", f.BATTERY, 0x01, "Battery levels, ids 0-3 (payload unconfirmed)", 0, 1, 2, 3, format = ReplyFormat.PAIRS))
        add(read("voice_prompts", f.VOICE_PROMPTS, 0x01, "Voice prompt index/config", format = ReplyFormat.BYTES))
        add(read("dac_gain", f.DAC_GAIN, 0x01, "DAC gain index"))
        add(read("codec.lc3", f.CODEC, 0x01, "LC3"))
        add(read("codec.ldac", f.CODEC, 0x02, "LDAC"))
        add(read("codec.lhdc", f.CODEC, 0x05, "LHDC"))
        add(read("wear_sensor", f.WEAR_SENSOR, 0x01, "Wear sensor"))
        add(read("spatial.enabled", f.SPATIAL_AUDIO, 0x01, "Spatial audio"))
        add(read("spatial.head_tracking", f.SPATIAL_AUDIO, 0x03, "Head tracking"))
        add(read("led", f.LED, 0x01, "LED"))
        add(read("multipoint.enabled", f.MULTIPOINT, 0x01, "Multipoint"))
        add(read("multipoint.timeout", f.MULTIPOINT, 0x03, "Multipoint timeout"))
        add(read("multipoint.current", f.MULTIPOINT, 0x05, "Multipoint current device", format = ReplyFormat.BYTES, identifying = true))
        add(read("multipoint.next", f.MULTIPOINT, 0x06, "Multipoint next device", format = ReplyFormat.BYTES, identifying = true))
        add(read("touch_v2.current", f.TOUCH_V2, 0x01, "Touch v2 current actions", format = ReplyFormat.BYTES))
        add(read("touch_v2.default", f.TOUCH_V2, 0x02, "Touch v2 default actions", format = ReplyFormat.BYTES))
        add(read("audio_resource", f.AUDIO_RESOURCE, 0x01, "Prompt-sound resource"))
        add(read("power.off", f.POWER_TIMEOUT, 0x01, "Auto power-off [enabled, minutes]", format = ReplyFormat.BYTES))
        add(read("power.standby", f.POWER_TIMEOUT, 0x03, "Standby [enabled, minutes]", format = ReplyFormat.BYTES))
        add(read("touch_v3.default", f.TOUCH_V3, 0x01, "Touch v3 default actions", format = ReplyFormat.BYTES))
        add(read("touch_v3.current", f.TOUCH_V3, 0x02, "Touch v3 current actions", format = ReplyFormat.BYTES))
        add(read("dynamic_bass", f.DYNAMIC_BASS, 0x01, "Dynamic bass"))
        add(read("lr_channel", f.LR_CHANNEL, 0x01, "Left/right swap"))
        add(read("touch_v4.knock", f.TOUCH_V4, 0x01, "Touch v4 knock", 0xFF))
        add(read("touch_v4.default", f.TOUCH_V4, 0x03, "Touch v4 default actions", format = ReplyFormat.BYTES))
        add(read("touch_v4.current", f.TOUCH_V4, 0x04, "Touch v4 current actions", format = ReplyFormat.BYTES))
        add(read("anc_v2.mode", f.ANC_V2, 0x03, "ANC v2 mode"))
        add(read("anc_v2.switch", f.ANC_V2, 41, "ANC v2 switch config", format = ReplyFormat.BYTES))
        add(read("anc_v3.mode", f.ANC_V3, 0x03, "ANC v3 mode"))
        add(read("anc_v3.anti_wind", f.ANC_V3, 0x05, "ANC v3 anti-wind"))
        add(read("anc_v3.switch", f.ANC_V3, 41, "ANC v3 switch config", format = ReplyFormat.BYTES))
        add(read("dual_mic_enc", f.DUAL_MIC_ENC, 0x01, "Dual-mic ENC"))
    }

    /** Features no console read may touch: debug, upgrade, BT address, power control, file storage, fit test. */
    val NEVER_READ: Set<Int> = with(GaiaFeature) {
        setOf(ANC, DEBUG, UPGRADE, EARBUD_FIT, BT_ADDRESS, POWER_CONTROL, AUDIO_FILE_STORAGE)
    }

    private val byId = reads.associateBy { it.id }

    fun find(id: String): DevRead? = byId[id]

    /** Reads for the features a device advertised; identifying ones only if [showIdentifying]. */
    fun readsFor(features: Set<Int>, showIdentifying: Boolean): List<DevRead> =
        reads.filter { it.feature in features && (showIdentifying || !it.identifying) }

    /** The console's text for a reply payload. [reveal] false masks identifying replies. */
    fun describe(read: DevRead, payload: ByteArray, reveal: Boolean): String {
        if (read.identifying && !reveal) return "[hidden: ${payload.size} bytes; turn on \"show serial numbers\"]"
        if (payload.isEmpty()) return "(empty)"
        val hex = payload.toHex()
        val decoded = when (read.format) {
            ReplyFormat.U8 -> if (payload.size == 1) (payload[0].toInt() and 0xFF).toString() else null
            ReplyFormat.BYTES -> null
            ReplyFormat.ASCII -> "\"${String(payload, Charsets.US_ASCII).filter { it in ' '..'~' }}\""
            ReplyFormat.U32 -> if (payload.size >= 4) {
                payload.takeLast(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }.toString()
            } else {
                null
            }
            ReplyFormat.USER_EQ -> UserEq.parse(payload)?.let(::describeUserEq) ?: "malformed user EQ"
            ReplyFormat.PAIRS -> if (payload.size % 2 == 0) {
                payload.toList().chunked(2).joinToString { (id, v) -> "id ${id.toInt() and 0xFF}: ${v.toInt() and 0xFF}" }
            } else {
                null
            }
        }
        return if (decoded == null) hex else "$decoded  ($hex)"
    }

    private fun describeUserEq(c: UserEqConfig): String {
        val bands = c.bands.joinToString("; ") {
            String.format(Locale.ROOT, "%d Hz Q %.3f type %d %+.1f dB", it.frequencyHz, it.q, it.filterType, it.gainDb)
        }
        return "start ${c.startBand}, end ${c.endBand}, ${c.bands.size} bands" + if (bands.isEmpty()) "" else ": $bands"
    }
}
