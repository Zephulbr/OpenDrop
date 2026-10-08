package org.opendrop.dsp

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EqTest {
    private fun near(expected: Double, actual: Double, tolerance: Double = 0.05) =
        assertTrue("expected $expected, got $actual", abs(expected - actual) <= tolerance)

    @Test
    fun peakHitsItsGainAtTheCenter() {
        val f = PeqFilter(FilterType.PEAK, 1_000.0, 6.0, 1.0)
        near(6.0, f.responseDb(1_000.0))
        near(0.0, f.responseDb(20.0), 0.1)
        near(0.0, f.responseDb(18_000.0), 0.2)
    }

    @Test
    fun shelvesReachTheirGainOnTheirSide() {
        near(6.0, PeqFilter(FilterType.LOW_SHELF, 105.0, 6.0, 0.7).responseDb(20.0), 0.2)
        near(0.0, PeqFilter(FilterType.LOW_SHELF, 105.0, 6.0, 0.7).responseDb(5_000.0), 0.1)
        near(-4.0, PeqFilter(FilterType.HIGH_SHELF, 6_000.0, -4.0, 0.7).responseDb(19_000.0), 0.3)
        near(3.0, PeqFilter(FilterType.LOW_SHELF, 200.0, 6.0, 0.7).responseDb(200.0), 0.1) // half gain at fc
    }

    @Test
    fun passFiltersAreMinus3dBAtCutoff() {
        near(-3.01, PeqFilter(FilterType.LOW_PASS, 5_000.0, 0.0, 0.7071).responseDb(5_000.0))
        near(-3.01, PeqFilter(FilterType.HIGH_PASS, 50.0, 0.0, 0.7071).responseDb(50.0))
        assertTrue(PeqFilter(FilterType.HIGH_PASS, 50.0, 0.0, 0.7071).responseDb(20.0) < -10)
    }

    @Test
    fun parametricSumsFilters() {
        val eq = ParametricEq(0.0, listOf(PeqFilter(FilterType.PEAK, 1_000.0, 3.0, 1.0), PeqFilter(FilterType.PEAK, 1_000.0, 3.0, 1.0)))
        near(6.0, eq.curveDb(1_000.0))
        near(-6.0, eq.safePreamp(), 0.1)
    }

    @Test
    fun safePreampNeverBoosts() {
        val cut = ParametricEq(0.0, listOf(PeqFilter(FilterType.PEAK, 1_000.0, -6.0, 1.0)))
        assertEquals(0.0, cut.safePreamp(), 0.0)
    }

    @Test
    fun graphicInterpolatesOnLogAxis() {
        val eq = GraphicEq(0.0, listOf(100.0 to 0.0, 1_000.0 to 10.0))
        near(5.0, eq.curveDb(316.2), 0.01)
        assertEquals(0.0, eq.curveDb(20.0), 0.0)
        assertEquals(10.0, eq.curveDb(15_000.0), 0.0)
    }

    @Test
    fun bandsCoverTheRangeInOrder() {
        val bands = bandsFor(EqPresetCurve.BASS.eq, 64)
        assertEquals(64, bands.size)
        assertTrue(bands.zipWithNext().all { (a, b) -> a.cutoffHz < b.cutoffHz })
        assertEquals(24_000.0, bands.last().cutoffHz, 0.0)
        near(6.0, bands.first().gainDb, 0.2) // 20 Hz sits under the shelf
        near(0.0, bands[50].gainDb, 0.1)
    }

    @Test
    fun parsesAutoEqParametric() {
        val text = """
            Preamp: -6.2 dB
            Filter 1: ON LSC Fc 105 Hz Gain 6.5 dB Q 0.70
            Filter 2: ON PK Fc 158 Hz Gain -2.6 dB Q 0.43
            Filter 3: OFF PK Fc 3000 Hz Gain 2.0 dB Q 1.00
            Filter 4: ON HSC Fc 10000 Hz Gain -3.0 dB Q 0.70
        """.trimIndent()
        val eq = AutoEq.parse(text) as ParametricEq
        assertEquals(-6.2, eq.preamp, 0.0)
        assertEquals(
            listOf(
                PeqFilter(FilterType.LOW_SHELF, 105.0, 6.5, 0.70),
                PeqFilter(FilterType.PEAK, 158.0, -2.6, 0.43),
                PeqFilter(FilterType.HIGH_SHELF, 10_000.0, -3.0, 0.70),
            ),
            eq.filters,
        )
    }

    @Test
    fun missingPreampIsMadeSafe() {
        val eq = AutoEq.parse("Filter 1: ON PK Fc 1000 Hz Gain 4.0 dB Q 1.0")!!
        near(-4.0, eq.preamp, 0.1)
    }

    @Test
    fun parsesAutoEqGraphic() {
        val eq = AutoEq.parse("GraphicEQ: 20 -6.2; 21 -6.3; 10000 2.5") as GraphicEq
        assertEquals(listOf(20.0 to -6.2, 21.0 to -6.3, 10_000.0 to 2.5), eq.points)
        near(-2.5, eq.preamp, 0.01)
    }

    @Test
    fun rejectsOtherText() {
        assertNull(AutoEq.parse("hello"))
        assertNull(AutoEq.parse(""))
    }

    @Test
    fun writeThenParseRoundTrips() {
        EqPresetCurve.entries.forEach { preset ->
            assertEquals(preset.label, preset.eq.filters.isEmpty(), AutoEq.parse(AutoEq.write(preset.eq)) == null)
            if (preset.eq.filters.isNotEmpty()) assertEquals(preset.label, preset.eq, AutoEq.parse(AutoEq.write(preset.eq)))
        }
        val graphic = GraphicEq(-1.5, listOf(20.0 to -1.0, 1_000.0 to 0.5))
        assertEquals(graphic, AutoEq.parse(AutoEq.write(graphic)))
    }

    @Test
    fun presetsDontClip() {
        EqPresetCurve.entries.forEach { assertTrue(it.label, it.eq.preamp + it.eq.peakDb() <= 0.3) }
    }
}
