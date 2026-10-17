package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbIdentityTest {
    private fun bytes(vararg b: Int) = b.map { it.toByte() }.toByteArray()

    private fun info(
        vendorId: Int = 0x2FC6,
        productId: Int = 0xF06A,
        product: String? = "USB Audio",
        capeMatch: Boolean? = null,
        hid: ByteArray? = null,
    ) = UsbDeviceInfo(
        vendorId, productId, null, product, null, emptyList(),
        hidReportDescriptors = hid?.let { mapOf(3 to it) } ?: emptyMap(),
        capeMatch = capeMatch,
    )

    @Test
    fun hidFindsReportIdsAndUsagePages() {
        // Usage Page (0xFF02), Usage (1), Collection, Report ID (0x4B), End Collection.
        val d = bytes(0x06, 0x02, 0xFF, 0x09, 0x01, 0xA1, 0x01, 0x85, 0x4B, 0xC0)
        assertEquals(HidSummary(setOf(0x4B), setOf(0xFF02)), UsbHid.summarize(d))
    }

    @Test
    fun hidDoesNotMistakeDataForATag() {
        // Usage (0x85) and Usage (0x0585, 2 bytes) carry 0x85 / 0x05 only as data.
        assertEquals(HidSummary(emptySet(), emptySet()), UsbHid.summarize(bytes(0x09, 0x85, 0x0A, 0x85, 0x05)))
    }

    @Test
    fun hidSkipsLongItemsAndStopsOnTruncation() {
        assertEquals(setOf(1), UsbHid.summarize(bytes(0xFE, 0x02, 0x01, 0xAA, 0xBB, 0x85, 0x01)).reportIds)
        assertEquals(HidSummary(emptySet(), emptySet()), UsbHid.summarize(bytes(0x06, 0x02))) // cut-off usage page
        assertEquals(HidSummary(emptySet(), emptySet()), UsbHid.summarize(ByteArray(0)))
    }

    @Test
    fun synapticsByVendorAndProductId() {
        val identity = UsbIdentify.of(info(0x35D8, 0x1496))
        assertEquals(UsbFamily.SYNAPTICS, identity.family)
        assertEquals(listOf(UsbEvidence.USB_ID), identity.evidence)
        assertNull(UsbIdentify.of(info(0x35D8, 0x1495)).family)
    }

    @Test
    fun synapticsByCapeString() {
        assertEquals(UsbFamily.SYNAPTICS, UsbIdentify.of(info(capeMatch = true)).family)
        assertNull(UsbIdentify.of(info(capeMatch = false)).family)
    }

    @Test
    fun spvAndJieliByHidDescriptor() {
        assertEquals(UsbFamily.SPV, UsbIdentify.of(info(hid = bytes(0x85, 0x4B))).family)
        assertEquals(UsbFamily.JIELI_USB, UsbIdentify.of(info(hid = bytes(0x06, 0x02, 0xFF))).family)
    }

    @Test
    fun catalogueWinsOverOtherSignals() {
        val identity = UsbIdentify.of(info(product = "Dawn Pro 2", capeMatch = true))
        assertEquals(UsbFamily.SPV, identity.family)
        assertEquals(listOf(UsbEvidence.CATALOGUE, UsbEvidence.CAPE), identity.evidence)
    }

    @Test
    fun unknownDeviceStaysUnknown() {
        assertEquals(UsbIdentity(null, emptyList()), UsbIdentify.of(info()))
    }

    @Test
    fun capeReadOnlyWhenTheCatalogueAllowsIt() {
        assertTrue(UsbIdentify.shouldReadCape(info()))
        assertTrue(UsbIdentify.shouldReadCape(info(product = "ECHO-B")))
        assertFalse(UsbIdentify.shouldReadCape(info(product = "Dawn Pro 2")))
    }

    @Test
    fun capeStringDescriptor() {
        fun descriptor(text: String): ByteArray {
            val utf16 = text.toByteArray(Charsets.UTF_16LE)
            return byteArrayOf((utf16.size + 2).toByte(), 3) + utf16
        }
        val cape = descriptor("CAPE-Freeman3")
        assertTrue(UsbIdentify.isCapeString(cape, cape.size))
        assertTrue(UsbIdentify.isCapeString(cape + ByteArray(100), cape.size + 100)) // bLength bounds it
        assertFalse(UsbIdentify.isCapeString(descriptor("USB Audio"), 20))
        assertFalse(UsbIdentify.isCapeString(cape, -1)) // stalled request
        assertFalse(UsbIdentify.isCapeString(cape.copyOf().also { it[1] = 2 }, cape.size)) // not a string descriptor
    }
}
