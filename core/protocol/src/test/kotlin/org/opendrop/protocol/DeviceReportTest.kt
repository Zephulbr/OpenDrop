package org.opendrop.protocol

import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceReportTest {
    @Test
    fun listsWhatTheDeviceReported() {
        val state = GaiaDeviceState(
            features = mapOf(0 to 2, 5 to 1, 13 to 1),
            featuresKnown = true,
            variantName = "Space Travel 2",
            firmwareVersion = "1.2.0",
            availableEqPresets = listOf(0, 1, 63),
            eqPresetId = 1,
        )
        val report = DeviceReport.build(state, listOf("App" to "0.1.0"), listOf("12:00:00.000 RX f0 response 0x01 00"))
        listOf(
            "App: 0.1.0",
            "Variant name: Space Travel 2",
            "Known model: Space Travel 2 (bluetrum)",
            "Firmware: 1.2.0",
            "  13: Battery, v1",
            "  EQ presets: read only",
            "  Battery (left, right, case): needs capture",
            "EQ presets available: 0, 1, 63",
            "EQ preset selected: 1",
            "12:00:00.000 RX f0 response 0x01 00",
        ).forEach { assertTrue("missing: $it", it in report) }
    }

    @Test
    fun saysWhenNothingWasReported() {
        val report = DeviceReport.build(GaiaDeviceState(), emptyList(), emptyList())
        assertTrue("Variant name: not reported" in report)
        assertTrue("Known model: no" in report)
        assertTrue("  none reported" in report)
    }
}
