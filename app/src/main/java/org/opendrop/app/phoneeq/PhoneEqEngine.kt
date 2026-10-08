package org.opendrop.app.phoneeq

import android.media.audiofx.DynamicsProcessing
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import org.opendrop.dsp.EqCurve
import org.opendrop.dsp.bandsFor

/**
 * Applies an [EqCurve] to the phone's output mix with Android's
 * DynamicsProcessing on the global session (0): a multi-band pre-EQ sampled
 * from the curve, the preamp as input gain, and a limiter just under 0 dB as a
 * safety net. Some phones don't allow effects on the global session; then
 * [apply] returns false.
 */
@RequiresApi(Build.VERSION_CODES.P)
class PhoneEqEngine {
    private var effect: DynamicsProcessing? = null

    fun apply(curve: EqCurve): Boolean = try {
        val dp = effect ?: create().also { effect = it }
        val eq = DynamicsProcessing.Eq(true, true, BAND_COUNT)
        bandsFor(curve, BAND_COUNT).forEachIndexed { i, band ->
            eq.setBand(i, DynamicsProcessing.EqBand(true, band.cutoffHz.toFloat(), band.gainDb.toFloat()))
        }
        dp.setPreEqAllChannelsTo(eq)
        dp.setInputGainAllChannelsTo(curve.preamp.toFloat())
        dp.enabled = true
        true
    } catch (e: RuntimeException) {
        // UnsupportedOperationException / IllegalStateException from the audio framework.
        Log.w(TAG, "Couldn't apply the phone EQ", e)
        release()
        false
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
    }

    private fun create(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            true,
            BAND_COUNT,
            false,
            0,
            false,
            0,
            true,
        ).build()
        // Session 0 is the output mix: the only way to reach every app's audio.
        val dp = DynamicsProcessing(0, 0, config)
        dp.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(true, true, 0, LIMITER_ATTACK_MS, LIMITER_RELEASE_MS, 10f, -1f, 0f),
        )
        return dp
    }

    private companion object {
        const val TAG = "PhoneEqEngine"
        const val BAND_COUNT = 64
        const val CHANNELS = 2
        const val LIMITER_ATTACK_MS = 1f
        const val LIMITER_RELEASE_MS = 60f
    }
}
