package org.opendrop.app.device

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import java.util.Locale

/**
 * What Android itself reports about a USB audio output. Android picks the
 * format it sends; public APIs list what's possible, not what's playing.
 */
data class UsbAudioOutput(
    /** Hz; empty means Android didn't list any (it accepts what the device does). */
    val sampleRates: List<Int>,
    /** Bit depths Android can send, e.g. "16-bit", "32-bit float". */
    val encodings: List<String>,
    val channelCounts: List<Int>,
    /** Whether a player can ask for bit-perfect output; null before Android 14. */
    val bitPerfect: Boolean?,
)

object UsbAudioOutputs {
    private val USB_TYPES = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)

    /**
     * The USB output for the device whose USB product string is [product];
     * the only USB output if names don't match (Android may prefix them).
     */
    fun find(audio: AudioManager?, product: String?): UsbAudioOutput? {
        val outputs = audio?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.filter { it.type in USB_TYPES }.orEmpty()
        val device = outputs.firstOrNull { d ->
            product != null && d.productName?.toString()?.contains(product, ignoreCase = true) == true
        } ?: outputs.singleOrNull() ?: return null
        val mixer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching { audio?.getSupportedMixerAttributes(device) }.getOrNull()
        } else {
            null
        }
        val bitPerfect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mixer?.any { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        } else {
            null
        }
        val rates = (device.sampleRates.toList() + mixer.orEmpty().map { it.format.sampleRate })
            .filter { it > 0 }.distinct().sorted()
        val encodings = (device.encodings.toList() + mixer.orEmpty().map { it.format.encoding })
            .distinct().sortedBy(::bits).mapNotNull(::label)
        return UsbAudioOutput(
            sampleRates = rates,
            encodings = encodings,
            channelCounts = device.channelCounts.toList().distinct().sorted(),
            bitPerfect = bitPerfect,
        )
    }

    /** "44.1, 48, 96 kHz". */
    fun rates(hz: List<Int>): String = hz.joinToString(", ") { rate ->
        if (rate % 1000 == 0) (rate / 1000).toString() else String.format(Locale.ROOT, "%.1f", rate / 1000.0)
    } + " kHz"

    @SuppressLint("InlinedApi") // compile-time constants; older Androids just never report them
    private fun label(encoding: Int): String? = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> "8-bit"
        AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
        AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
        AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
        else -> null
    }

    @SuppressLint("InlinedApi")
    private fun bits(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 8
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT -> 32
        AudioFormat.ENCODING_PCM_FLOAT -> 33
        else -> 99
    }
}
