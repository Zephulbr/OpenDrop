package org.opendrop.transport.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import java.util.Locale
import org.opendrop.protocol.UsbDeviceInfo
import org.opendrop.protocol.UsbIdentify
import org.opendrop.protocol.UsbInterfaceInfo

/**
 * Reads what a USB device describes about itself, using only standard
 * read-only requests. It never claims an interface, so the phone's audio
 * driver keeps the device, and it sends nothing vendor-specific: the USB
 * control protocols are known only from Link's code, not a capture (roadmap M5).
 */
class UsbProbe(private val manager: UsbManager) {
    /** What Android reports without permission: ids, names and interfaces. */
    fun describe(device: UsbDevice): UsbDeviceInfo = UsbDeviceInfo(
        vendorId = device.vendorId,
        productId = device.productId,
        manufacturer = runCatching { device.manufacturerName }.getOrNull(),
        product = runCatching { device.productName }.getOrNull(),
        version = runCatching { device.version }.getOrNull(),
        interfaces = (0 until device.interfaceCount).map { i ->
            val iface = device.getInterface(i)
            UsbInterfaceInfo(
                id = iface.id,
                alternate = iface.alternateSetting,
                interfaceClass = iface.interfaceClass,
                subclass = iface.interfaceSubclass,
                protocol = iface.interfaceProtocol,
                endpoints = (0 until iface.endpointCount).map { e ->
                    val ep = iface.getEndpoint(e)
                    val direction = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                    val type = when (ep.type) {
                        UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "control"
                        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isochronous"
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                        else -> "interrupt"
                    }
                    String.format(Locale.ROOT, "%s 0x%02x %s %d", direction, ep.address, type, ep.maxPacketSize)
                },
            )
        },
    )

    /**
     * [describe], plus the raw descriptors, each HID interface's report
     * descriptor (a standard GET_DESCRIPTOR request; the phone's own HID driver
     * reads the same) and whether string 41 is Synaptics' "CAPE" id. Needs USB
     * permission; without it returns [describe].
     */
    fun read(device: UsbDevice): UsbDeviceInfo {
        val basic = describe(device)
        if (!manager.hasPermission(device)) return basic
        val connection = manager.openDevice(device) ?: return basic
        return try {
            val raw = connection.rawDescriptors
            val hid = basic.interfaces
                .filter { it.interfaceClass == UsbConstants.USB_CLASS_HID }
                .map { it.id }
                .distinct()
                .mapNotNull { id ->
                    val buffer = ByteArray(MAX_REPORT_DESCRIPTOR)
                    val n = connection.controlTransfer(
                        REQUEST_TYPE_STANDARD_INTERFACE_IN,
                        REQUEST_GET_DESCRIPTOR,
                        HID_REPORT_DESCRIPTOR shl 8,
                        id,
                        buffer,
                        buffer.size,
                        TIMEOUT_MS,
                    )
                    if (n > 0) id to buffer.copyOf(n) else null
                }
                .toMap()
            // Standard GET_DESCRIPTOR(string); a device without string 41 just stalls it.
            val capeMatch = if (UsbIdentify.shouldReadCape(basic)) {
                val buffer = ByteArray(MAX_STRING_DESCRIPTOR)
                val n = connection.controlTransfer(
                    REQUEST_TYPE_STANDARD_DEVICE_IN,
                    REQUEST_GET_DESCRIPTOR,
                    (STRING_DESCRIPTOR shl 8) or UsbIdentify.CAPE_STRING_INDEX,
                    LANGUAGE_EN_US,
                    buffer,
                    buffer.size,
                    TIMEOUT_MS,
                )
                UsbIdentify.isCapeString(buffer, n)
            } else {
                null
            }
            basic.copy(rawDescriptors = raw, hidReportDescriptors = hid, capeMatch = capeMatch)
        } finally {
            connection.close()
        }
    }

    private companion object {
        /** Device-to-host, standard, recipient interface. */
        const val REQUEST_TYPE_STANDARD_INTERFACE_IN = 0x81

        /** Device-to-host, standard, recipient device. */
        const val REQUEST_TYPE_STANDARD_DEVICE_IN = 0x80
        const val REQUEST_GET_DESCRIPTOR = 0x06
        const val STRING_DESCRIPTOR = 0x03
        const val HID_REPORT_DESCRIPTOR = 0x22
        const val LANGUAGE_EN_US = 0x0409
        const val MAX_STRING_DESCRIPTOR = 255

        /** HID report descriptors are usually <1 KB; 16 KB covers exotic ones. */
        const val MAX_REPORT_DESCRIPTOR = 16_384
        const val TIMEOUT_MS = 500
    }
}
