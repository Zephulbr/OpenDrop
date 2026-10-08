package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesTest {
    private fun state(variant: String?, vararg features: Int) = GaiaDeviceState(
        features = features.associateWith { 1 },
        featuresKnown = true,
        variantName = variant,
    )

    @Test
    fun spaceTravelControlsEqOnly() {
        // core, earbud, voice UI, EQ, upgrade: as captured
        val state = state("Moondrop Space Travel", 0, 1, 3, 5, 6)
        assertEquals(mapOf(Capability.EQ_PRESETS to Support.CONTROL), state.capabilities)
    }

    @Test
    fun eqIsReadOnlyWithoutPresetNames() {
        val known = state("Space Travel 2", GaiaFeature.MUSIC_PROCESSING)
        assertEquals(Support.READ_ONLY, known.support(Capability.EQ_PRESETS))
        val unknown = state("Some Other Buds", GaiaFeature.MUSIC_PROCESSING)
        assertEquals(Support.READ_ONLY, unknown.support(Capability.EQ_PRESETS))
    }

    @Test
    fun moondropFeaturesNeedACapture() {
        val state = state("Space Travel 2 Ultra", GaiaFeature.BATTERY, GaiaFeature.ANC_V3, GaiaFeature.TOUCH_V4)
        assertEquals(
            mapOf(
                Capability.BATTERY to Support.NEEDS_CAPTURE,
                Capability.NOISE_CONTROL to Support.NEEDS_CAPTURE,
                Capability.TOUCH_CONTROLS to Support.NEEDS_CAPTURE,
            ),
            state.capabilities,
        )
        assertNull(state.support(Capability.EQ_PRESETS))
    }

    @Test
    fun anyAncVersionCounts() {
        listOf(GaiaFeature.ANC, GaiaFeature.ANC_V2, GaiaFeature.ANC_V3).forEach {
            assertEquals(Support.NEEDS_CAPTURE, state("Space Force", it).support(Capability.NOISE_CONTROL))
        }
    }

    @Test
    fun emptyUntilFeaturesAreKnown() {
        val partial = GaiaDeviceState(features = mapOf(GaiaFeature.MUSIC_PROCESSING to 1), featuresKnown = false)
        assertTrue(partial.capabilities.isEmpty())
    }

    @Test
    fun everyCapabilityMapsToAKnownFeature() {
        Capability.entries.forEach { c ->
            assertTrue(c.name, c.features.isNotEmpty())
            c.features.forEach { assertTrue("${c.name}: $it", GaiaFeature.name(it) != "Feature $it") }
        }
    }
}
