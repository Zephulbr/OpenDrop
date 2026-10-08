package org.opendrop.dsp

import java.util.Locale

/**
 * Reads and writes AutoEQ's text formats (https://github.com/jaakkopasanen/AutoEq):
 *
 * ParametricEQ.txt
 * ```
 * Preamp: -6.2 dB
 * Filter 1: ON LSC Fc 105 Hz Gain 6.5 dB Q 0.70
 * Filter 2: ON PK Fc 158 Hz Gain -2.6 dB Q 0.43
 * ```
 * GraphicEQ.txt (also Wavelet's format)
 * ```
 * GraphicEQ: 20 -6.2; 21 -6.3; 22 -6.3; ...
 * ```
 */
object AutoEq {
    private val preampRegex = Regex("""^\s*Preamp:\s*([-+]?\d+(?:\.\d+)?)\s*dB""", RegexOption.IGNORE_CASE)
    private val filterRegex = Regex(
        """^\s*Filter\s*\d*:\s*(ON|OFF)\s+([A-Z]+)\s+Fc\s+([\d.]+)\s*Hz(?:\s+Gain\s+([-+]?[\d.]+)\s*dB)?(?:\s+Q\s+([\d.]+))?""",
        RegexOption.IGNORE_CASE,
    )
    private val graphicRegex = Regex("""^\s*GraphicEQ:\s*(.*)$""", RegexOption.IGNORE_CASE)

    /** Parses either format. Null if the text holds neither filters nor graphic points. */
    fun parse(text: String): EqCurve? {
        var preamp: Double? = null
        val filters = mutableListOf<PeqFilter>()
        var graphic: List<Pair<Double, Double>>? = null
        for (line in text.lineSequence()) {
            preampRegex.find(line)?.let { preamp = it.groupValues[1].toDouble() }
            filterRegex.find(line)?.let { m -> parseFilter(m)?.let(filters::add) }
            graphicRegex.find(line)?.let { m -> graphic = parseGraphic(m.groupValues[1]) }
        }
        val points = graphic
        return when {
            filters.isNotEmpty() -> ParametricEq(preamp ?: 0.0, filters).let { eq ->
                if (preamp == null) eq.copy(preamp = eq.safePreamp()) else eq
            }
            !points.isNullOrEmpty() -> GraphicEq(preamp ?: 0.0, points).let { eq ->
                if (preamp == null) eq.copy(preamp = eq.safePreamp()) else eq
            }
            else -> null
        }
    }

    private fun parseFilter(m: MatchResult): PeqFilter? {
        if (!m.groupValues[1].equals("ON", ignoreCase = true)) return null
        val type = when (m.groupValues[2].uppercase(Locale.ROOT)) {
            "PK", "PEQ", "PEAK" -> FilterType.PEAK
            "LS", "LSC", "LSQ" -> FilterType.LOW_SHELF
            "HS", "HSC", "HSQ" -> FilterType.HIGH_SHELF
            "LP", "LPQ" -> FilterType.LOW_PASS
            "HP", "HPQ" -> FilterType.HIGH_PASS
            else -> return null
        }
        val frequency = m.groupValues[3].toDoubleOrNull()?.takeIf { it > 0 } ?: return null
        val gain = m.groupValues[4].toDoubleOrNull() ?: 0.0
        // AutoEQ's default for shelves without Q is 0.7; pass filters 0.707.
        val q = m.groupValues[5].toDoubleOrNull()?.takeIf { it > 0 } ?: 0.707
        return PeqFilter(type, frequency, if (type.hasGain) gain else 0.0, q)
    }

    private fun parseGraphic(body: String): List<Pair<Double, Double>> =
        body.split(';').mapNotNull { part ->
            val bits = part.trim().split(Regex("\\s+"))
            if (bits.size < 2) return@mapNotNull null
            val hz = bits[0].toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val db = bits[1].toDoubleOrNull() ?: return@mapNotNull null
            hz to db
        }

    /** Writes [eq] in the format it came from. */
    fun write(eq: EqCurve): String = when (eq) {
        is ParametricEq -> buildString {
            appendLine("Preamp: ${fmt(eq.preamp)} dB")
            eq.filters.forEachIndexed { i, f ->
                append("Filter ${i + 1}: ON ${f.type.code} Fc ${fmt(f.frequency, 0)} Hz")
                if (f.type.hasGain) append(" Gain ${fmt(f.gain)} dB")
                appendLine(" Q ${fmt(f.q, 2)}")
            }
        }
        is GraphicEq -> buildString {
            appendLine("Preamp: ${fmt(eq.preamp)} dB")
            append("GraphicEQ: ")
            appendLine(eq.points.joinToString("; ") { (hz, db) -> "${fmt(hz, 0)} ${fmt(db)}" })
        }
    }

    private fun fmt(value: Double, decimals: Int = 1): String = String.format(Locale.ROOT, "%.${decimals}f", value)
}

/** Built-in starting points. Broad, gentle curves; not measurements of any headphone. */
enum class EqPresetCurve(val label: String, val eq: ParametricEq) {
    FLAT("Flat", ParametricEq()),
    BASS("Bass boost", ParametricEq(-6.0, listOf(PeqFilter(FilterType.LOW_SHELF, 105.0, 6.0, 0.7)))),
    WARM(
        "Warm",
        ParametricEq(
            -3.0,
            listOf(PeqFilter(FilterType.LOW_SHELF, 150.0, 3.0, 0.7), PeqFilter(FilterType.HIGH_SHELF, 6_000.0, -2.0, 0.7)),
        ),
    ),
    V_SHAPE(
        "V-shape",
        ParametricEq(
            -5.0,
            listOf(
                PeqFilter(FilterType.LOW_SHELF, 105.0, 5.0, 0.7),
                PeqFilter(FilterType.PEAK, 1_000.0, -2.0, 0.6),
                PeqFilter(FilterType.HIGH_SHELF, 8_000.0, 4.0, 0.7),
            ),
        ),
    ),
    VOCAL(
        "Vocal",
        ParametricEq(
            -3.0,
            listOf(PeqFilter(FilterType.LOW_SHELF, 120.0, -2.0, 0.7), PeqFilter(FilterType.PEAK, 2_500.0, 3.0, 1.0)),
        ),
    ),
    TREBLE(
        "Treble boost",
        ParametricEq(-4.0, listOf(PeqFilter(FilterType.HIGH_SHELF, 6_000.0, 4.0, 0.7))),
    ),
}
