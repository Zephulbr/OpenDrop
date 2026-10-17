package org.opendrop.protocol

/** What a HID report descriptor declares; see docs/protocol/usb-dac.md. */
data class HidSummary(val reportIds: Set<Int>, val usagePages: Set<Int>)

object UsbHid {
    /**
     * Report IDs (global item 0x85) and usage pages (global item 0x05) in a
     * HID report descriptor. Walks items by their size bits, so a data byte
     * that happens to equal a tag isn't mistaken for one.
     */
    fun summarize(descriptor: ByteArray): HidSummary {
        val reportIds = sortedSetOf<Int>()
        val usagePages = sortedSetOf<Int>()
        var i = 0
        while (i < descriptor.size) {
            val prefix = descriptor[i].toInt() and 0xFF
            if (prefix == LONG_ITEM) { // prefix, data size, long tag, data
                if (i + 1 >= descriptor.size) break
                i += 3 + (descriptor[i + 1].toInt() and 0xFF)
                continue
            }
            val size = when (prefix and 0x03) { 3 -> 4; else -> prefix and 0x03 }
            if (i + size >= descriptor.size) break
            var value = 0
            for (b in size downTo 1) value = (value shl 8) or (descriptor[i + b].toInt() and 0xFF)
            when (prefix and 0xFC) {
                TAG_REPORT_ID -> if (size > 0) reportIds += value
                TAG_USAGE_PAGE -> if (size > 0) usagePages += value
            }
            i += 1 + size
        }
        return HidSummary(reportIds, usagePages)
    }

    private const val LONG_ITEM = 0xFE
    private const val TAG_REPORT_ID = 0x84
    private const val TAG_USAGE_PAGE = 0x04
}

/** Why OpenDrop thinks a USB device belongs to a family. */
enum class UsbEvidence(val label: String) {
    CATALOGUE("catalogue name"),
    USB_ID("Synaptics USB id"),
    CAPE("CAPE string"),
    SPV_REPORT("HID report 0x4b"),
    JIELI_PAGE("HID usage page 0xff02"),
}

data class UsbIdentity(val family: UsbFamily?, val evidence: List<UsbEvidence>)

/**
 * Picks a USB family from what [UsbDeviceInfo] already holds: no vendor
 * command is sent. The catalogue name wins; the other signals are what Link
 * 2.26.2c checks (App evidence, no capture yet) and only fill in when the
 * product string isn't in the catalogue.
 */
object UsbIdentify {
    const val SYNAPTICS_VENDOR_ID = 0x35D8
    val SYNAPTICS_PRODUCT_IDS = 0x1496..0x149C

    /** USB string descriptor index Link reads for the "CAPE" id. */
    const val CAPE_STRING_INDEX = 41

    private const val SPV_REPORT_ID = 0x4B
    private const val JIELI_USAGE_PAGE = 0xFF02

    fun of(info: UsbDeviceInfo): UsbIdentity {
        val hid = info.hidSummary
        val evidence = buildList {
            if (info.model != null) add(UsbEvidence.CATALOGUE)
            if (info.vendorId == SYNAPTICS_VENDOR_ID && info.productId in SYNAPTICS_PRODUCT_IDS) add(UsbEvidence.USB_ID)
            if (info.capeMatch == true) add(UsbEvidence.CAPE)
            if (SPV_REPORT_ID in hid.reportIds) add(UsbEvidence.SPV_REPORT)
            if (JIELI_USAGE_PAGE in hid.usagePages) add(UsbEvidence.JIELI_PAGE)
        }
        val family = info.model?.family ?: when {
            UsbEvidence.USB_ID in evidence || UsbEvidence.CAPE in evidence -> UsbFamily.SYNAPTICS
            UsbEvidence.SPV_REPORT in evidence -> UsbFamily.SPV
            UsbEvidence.JIELI_PAGE in evidence -> UsbFamily.JIELI_USB
            else -> null
        }
        return UsbIdentity(family, evidence)
    }

    /** Whether to read string [CAPE_STRING_INDEX]: skip devices the catalogue puts in another family. */
    fun shouldReadCape(info: UsbDeviceInfo): Boolean = info.model.let { it == null || it.family == UsbFamily.SYNAPTICS }

    /** True if a raw string descriptor (`bLength`, `3`, UTF-16LE) contains "CAPE". */
    fun isCapeString(raw: ByteArray, length: Int): Boolean {
        val n = minOf(length, raw.size, raw.firstOrNull()?.toInt()?.and(0xFF) ?: 0)
        if (n < 4 || raw[1].toInt() != STRING_DESCRIPTOR) return false
        val text = String(raw, 2, (n - 2) and 1.inv(), Charsets.UTF_16LE)
        return text.contains("CAPE", ignoreCase = true)
    }

    private const val STRING_DESCRIPTOR = 3
}
