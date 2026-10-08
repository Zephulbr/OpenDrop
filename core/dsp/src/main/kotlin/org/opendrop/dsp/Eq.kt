package org.opendrop.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Lowest and highest frequency OpenDrop's EQ works with, in Hz. */
const val MIN_HZ = 20.0
const val MAX_HZ = 20_000.0

/** Sample rate used to model the filters; the phone's output runs at 48 kHz. */
const val SAMPLE_RATE = 48_000.0

/** Filter types, with the codes AutoEQ's ParametricEQ.txt uses. */
enum class FilterType(val code: String, val label: String, val hasGain: Boolean) {
    PEAK("PK", "Peak", true),
    LOW_SHELF("LSC", "Low shelf", true),
    HIGH_SHELF("HSC", "High shelf", true),
    LOW_PASS("LPQ", "Low pass", false),
    HIGH_PASS("HPQ", "High pass", false),
}

/** One biquad filter. [gain] in dB (ignored for pass filters), [frequency] in Hz. */
data class PeqFilter(
    val type: FilterType = FilterType.PEAK,
    val frequency: Double = 1_000.0,
    val gain: Double = 0.0,
    val q: Double = 0.7,
) {
    /** Magnitude response at [hz], in dB (RBJ Audio EQ Cookbook biquads). */
    fun responseDb(hz: Double, sampleRate: Double = SAMPLE_RATE): Double {
        val c = coefficients(sampleRate)
        val w = 2 * PI * hz / sampleRate
        val (cos1, sin1) = cos(w) to sin(w)
        val (cos2, sin2) = cos(2 * w) to sin(2 * w)
        val numRe = c[0] + c[1] * cos1 + c[2] * cos2
        val numIm = -(c[1] * sin1 + c[2] * sin2)
        val denRe = c[3] + c[4] * cos1 + c[5] * cos2
        val denIm = -(c[4] * sin1 + c[5] * sin2)
        val power = (numRe * numRe + numIm * numIm) / (denRe * denRe + denIm * denIm)
        return 10 * log10(power)
    }

    /** b0, b1, b2, a0, a1, a2. */
    private fun coefficients(fs: Double): DoubleArray {
        val a = 10.0.pow(gain / 40)
        val w0 = 2 * PI * frequency.coerceIn(1.0, fs / 2 - 1) / fs
        val cw = cos(w0)
        val alpha = sin(w0) / (2 * q.coerceAtLeast(0.01))
        val sa = 2 * sqrt(a) * alpha
        return when (type) {
            FilterType.PEAK -> doubleArrayOf(1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a)
            FilterType.LOW_SHELF -> doubleArrayOf(
                a * ((a + 1) - (a - 1) * cw + sa),
                2 * a * ((a - 1) - (a + 1) * cw),
                a * ((a + 1) - (a - 1) * cw - sa),
                (a + 1) + (a - 1) * cw + sa,
                -2 * ((a - 1) + (a + 1) * cw),
                (a + 1) + (a - 1) * cw - sa,
            )
            FilterType.HIGH_SHELF -> doubleArrayOf(
                a * ((a + 1) + (a - 1) * cw + sa),
                -2 * a * ((a - 1) + (a + 1) * cw),
                a * ((a + 1) + (a - 1) * cw - sa),
                (a + 1) - (a - 1) * cw + sa,
                2 * ((a - 1) - (a + 1) * cw),
                (a + 1) - (a - 1) * cw - sa,
            )
            FilterType.LOW_PASS -> doubleArrayOf((1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
            FilterType.HIGH_PASS -> doubleArrayOf((1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha)
        }
    }
}

/** A frequency response the phone EQ can apply: a preamp plus a curve. */
sealed interface EqCurve {
    /** dB, applied before the curve so boosts don't clip. */
    val preamp: Double

    /** The curve's gain at [hz], in dB, without the preamp. */
    fun curveDb(hz: Double): Double

    /** Highest boost of the curve between [MIN_HZ] and [MAX_HZ]. */
    fun peakDb(): Double = logSpaced(MIN_HZ, MAX_HZ, 200).maxOf(::curveDb)

    /** A preamp that keeps the loudest boost at 0 dB. */
    fun safePreamp(): Double = -peakDb().coerceAtLeast(0.0)
}

/** Parametric EQ: the editable kind, and what AutoEQ's ParametricEQ.txt holds. */
data class ParametricEq(override val preamp: Double = 0.0, val filters: List<PeqFilter> = emptyList()) : EqCurve {
    override fun curveDb(hz: Double): Double = filters.sumOf { it.responseDb(hz) }
}

/**
 * Graphic EQ: gains at fixed frequencies (AutoEQ's GraphicEQ.txt), linear in
 * dB between points on a log-frequency axis.
 */
data class GraphicEq(override val preamp: Double = 0.0, val points: List<Pair<Double, Double>>) : EqCurve {
    private val sorted = points.sortedBy { it.first }

    override fun curveDb(hz: Double): Double {
        if (sorted.isEmpty()) return 0.0
        if (hz <= sorted.first().first) return sorted.first().second
        if (hz >= sorted.last().first) return sorted.last().second
        val i = sorted.indexOfFirst { it.first >= hz }
        val (f0, g0) = sorted[i - 1]
        val (f1, g1) = sorted[i]
        val t = (ln(hz) - ln(f0)) / (ln(f1) - ln(f0))
        return g0 + (g1 - g0) * t
    }
}

/** [count] frequencies from [from] to [to], evenly spaced on a log axis. */
fun logSpaced(from: Double, to: Double, count: Int): List<Double> {
    require(count >= 2)
    val a = ln(from)
    val step = (ln(to) - a) / (count - 1)
    return List(count) { exp(a + it * step) }
}

/**
 * One band of a multi-band (graphic) EQ such as Android's DynamicsProcessing:
 * it covers frequencies up to [cutoffHz] (from the previous band's cutoff)
 * and applies [gainDb].
 */
data class Band(val cutoffHz: Double, val gainDb: Double)

/**
 * Samples [curve] into [count] bands whose centers are log-spaced over the
 * audible range. The last band reaches [nyquist].
 */
fun bandsFor(curve: EqCurve, count: Int, nyquist: Double = SAMPLE_RATE / 2): List<Band> {
    val centers = logSpaced(MIN_HZ, MAX_HZ, count)
    return centers.mapIndexed { i, center ->
        val cutoff = if (i == centers.lastIndex) nyquist else sqrt(center * centers[i + 1])
        Band(cutoff, curve.curveDb(center))
    }
}
