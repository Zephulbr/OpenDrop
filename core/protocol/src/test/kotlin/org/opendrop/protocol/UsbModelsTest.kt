package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbModelsTest {
    @Test
    fun findsModelsByLooseProductString() {
        assertEquals("DAWN PRO2", MoondropUsbModels.find("Moondrop Dawn Pro 2")?.name)
        assertEquals("MOONRIVER 3", MoondropUsbModels.find("MOONDROP Moonriver3")?.name)
        assertEquals(UsbFamily.SPV, MoondropUsbModels.find("FreeDSP Pro")?.family)
        assertEquals(UsbFamily.SYNAPTICS, MoondropUsbModels.find("FreeDSP")?.family)
        assertNull(MoondropUsbModels.find("USB Audio"))
        assertNull(MoondropUsbModels.find("Moondrop"))
        assertNull(MoondropUsbModels.find(null))
    }

    @Test
    fun everyCatalogueNameFindsItself() {
        MoondropUsbModels.all.forEach { assertEquals(it, MoondropUsbModels.find(it.name)) }
        assertEquals(33, MoondropUsbModels.all.size)
    }

    @Test
    fun recognisesMoondropByStrings() {
        assertTrue(MoondropUsbModels.looksLikeMoondrop("MOONDROP", "Some New DAC"))
        assertTrue(MoondropUsbModels.looksLikeMoondrop(null, "ECHO-B"))
        assertFalse(MoondropUsbModels.looksLikeMoondrop("Generic", "USB Audio"))
    }

    @Test
    fun reportListsDescriptors() {
        val info = UsbDeviceInfo(
            vendorId = 0x2fc6,
            productId = 0xf06a,
            manufacturer = "MOONDROP",
            product = "Dawn Pro 2",
            version = "1.00",
            interfaces = listOf(UsbInterfaceInfo(3, 0, 0x03, 0, 0, listOf("IN 0x83 interrupt 64"))),
            rawDescriptors = ByteArray(18) { it.toByte() },
            hidReportDescriptors = mapOf(3 to byteArrayOf(0x06, 0x00, 0xff.toByte())),
        )
        val report = UsbReport.build(info, listOf("App" to "0.2.0"))
        listOf(
            "App: 0.2.0",
            "USB id: 2fc6:f06a",
            "Known model: DAWN PRO2 (SPV)",
            "  3/0: HID 0 0",
            "    IN 0x83 interrupt 64",
            "  00 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f",
            "  10 11",
            "HID report descriptor, interface 3 (3 bytes):",
            "  06 00 ff",
        ).forEach { assertTrue("missing: $it", it in report) }
    }

    @Test
    fun infoEqualityComparesBytes() {
        val a = UsbDeviceInfo(1, 2, null, null, null, emptyList(), byteArrayOf(1), mapOf(0 to byteArrayOf(2)))
        assertEquals(a, a.copy(rawDescriptors = byteArrayOf(1), hidReportDescriptors = mapOf(0 to byteArrayOf(2))))
        assertFalse(a == a.copy(rawDescriptors = byteArrayOf(9)))
    }
}
