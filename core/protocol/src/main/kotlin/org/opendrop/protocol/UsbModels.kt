package org.opendrop.protocol

import java.util.Locale

/**
 * USB control protocol families in MOONDROP Link (docs/devices.md). None is
 * decoded yet, so OpenDrop only reads USB devices (roadmap M5).
 */
enum class UsbFamily(val label: String) {
    COMTRUE("Comtrue"),
    JIELI("Jieli"),
    JIELI_USB("Jieli USB"),
    JIU("Jiu"),
    SPV("SPV"),
    SYNAPTICS("Synaptics"),
}

enum class UsbKind { DAC, DSP_CABLE, GAMING }

/** A USB model from Link's catalogue. [name] is the catalogue's `model` string. */
data class MoondropUsbModel(val name: String, val kind: UsbKind, val family: UsbFamily, val eqBands: Int)

/** USB models in Link's catalogue (2026-10-08). "MOONDROP CDSP" shares "CDSP"'s key, so it's listed once. */
object MoondropUsbModels {
    private fun model(name: String, kind: UsbKind, family: UsbFamily, eqBands: Int) =
        MoondropUsbModel(name, kind, family, eqBands)

    val all: List<MoondropUsbModel> = listOf(
        model("DISCDREAM", UsbKind.DSP_CABLE, UsbFamily.COMTRUE, 5),
        model("MOONDROP DAWN 3.5", UsbKind.DAC, UsbFamily.COMTRUE, 5),
        model("MOONDROP DAWN 4.4", UsbKind.DAC, UsbFamily.COMTRUE, 5),
        model("MOONDROP DAWN PRO", UsbKind.DAC, UsbFamily.COMTRUE, 5),
        model("MOONDROP Moonriver2 Ti", UsbKind.DAC, UsbFamily.COMTRUE, 5),
        model("MOONDROP Nicebuds DSP", UsbKind.DSP_CABLE, UsbFamily.JIELI, 10),
        model("Echo-BP", UsbKind.DAC, UsbFamily.JIELI_USB, 32),
        model("MOONDROP Gaming Maestro", UsbKind.GAMING, UsbFamily.JIELI_USB, 10),
        model("MOONDROP GM01 Pro", UsbKind.GAMING, UsbFamily.JIELI_USB, 10),
        model("CDSP", UsbKind.DSP_CABLE, UsbFamily.JIU, 5),
        model("CHU2 DSP", UsbKind.DSP_CABLE, UsbFamily.JIU, 5),
        model("MOONDROP JIU", UsbKind.DSP_CABLE, UsbFamily.JIU, 5),
        model("Moondrop Old Fashioned", UsbKind.DSP_CABLE, UsbFamily.JIU, 5),
        model("DA-016 BLUE ROSE", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("DAWN PRO2", UsbKind.DAC, UsbFamily.SPV, 8),
        model("ddHiFi DSP IEM - Memory", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("Deco Audio System", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("E.S.combo", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("FreeDSP Mini", UsbKind.DAC, UsbFamily.SPV, 8),
        model("FreeDSP Pro", UsbKind.DAC, UsbFamily.SPV, 8),
        model("INN Deco75-DH Audio", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("Moondrop DHA15", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("MOONDROP Marigold", UsbKind.DSP_CABLE, UsbFamily.SPV, 8),
        model("MOONDROP Position", UsbKind.GAMING, UsbFamily.SPV, 8),
        model("MOONDROP Rays", UsbKind.GAMING, UsbFamily.SPV, 8),
        model("MOONDROP X AG Rays", UsbKind.GAMING, UsbFamily.SPV, 8),
        model("MOONRIVER 3", UsbKind.DAC, UsbFamily.SPV, 8),
        model("DUSK-SP", UsbKind.DSP_CABLE, UsbFamily.SYNAPTICS, 9),
        model("ECHO-B", UsbKind.DAC, UsbFamily.SYNAPTICS, 9),
        model("FreeDSP", UsbKind.DAC, UsbFamily.SYNAPTICS, 9),
        model("MAY", UsbKind.DSP_CABLE, UsbFamily.SYNAPTICS, 9),
        model("MOONDROP Click", UsbKind.DAC, UsbFamily.SYNAPTICS, 5),
        model("Starlight", UsbKind.DSP_CABLE, UsbFamily.SYNAPTICS, 9),
    )

    private val byKey = all.associateBy { key(it.name) }

    /**
     * The model a USB product string names, or null. USB product strings
     * aren't known for most models yet, so this compares letters and digits
     * only and ignores "Moondrop" ("Moondrop Dawn Pro 2" finds "DAWN PRO2").
     * A guess until a device report confirms the string.
     */
    fun find(product: String?): MoondropUsbModel? {
        val k = product?.let(::key)?.takeIf { it.isNotEmpty() } ?: return null
        return byKey[k]
    }

    /** True if the USB strings say Moondrop, even for a model not in the list. */
    fun looksLikeMoondrop(manufacturer: String?, product: String?): Boolean =
        find(product) != null || listOfNotNull(manufacturer, product).any { "moondrop" in it.lowercase(Locale.ROOT) }

    private fun key(name: String) =
        name.lowercase(Locale.ROOT).replace("moondrop", "").filter { it.isLetterOrDigit() }
}

/** What Android tells us about one USB interface. */
data class UsbInterfaceInfo(
    val id: Int,
    val alternate: Int,
    val interfaceClass: Int,
    val subclass: Int,
    val protocol: Int,
    /** E.g. "IN 0x81 interrupt 64". */
    val endpoints: List<String>,
)

/** Everything OpenDrop reads from a USB device, all with standard read-only requests. */
data class UsbDeviceInfo(
    val vendorId: Int,
    val productId: Int,
    val manufacturer: String?,
    val product: String?,
    val version: String?,
    val interfaces: List<UsbInterfaceInfo>,
    /** Device and configuration descriptors as Android cached them; null without permission. */
    val rawDescriptors: ByteArray? = null,
    /** HID report descriptor per HID interface id; empty without permission. */
    val hidReportDescriptors: Map<Int, ByteArray> = emptyMap(),
) {
    val model: MoondropUsbModel? get() = MoondropUsbModels.find(product)

    val id: String get() = String.format(Locale.ROOT, "%04x:%04x", vendorId, productId)

    // ByteArray fields: compare by content so StateFlow sees real changes only.
    override fun equals(other: Any?): Boolean = other is UsbDeviceInfo &&
        vendorId == other.vendorId && productId == other.productId && manufacturer == other.manufacturer &&
        product == other.product && version == other.version && interfaces == other.interfaces &&
        rawDescriptors.contentEquals(other.rawDescriptors) &&
        hidReportDescriptors.keys == other.hidReportDescriptors.keys &&
        hidReportDescriptors.all { (k, v) -> v.contentEquals(other.hidReportDescriptors[k]) }

    override fun hashCode(): Int = id.hashCode() * 31 + (product?.hashCode() ?: 0)
}

/** Plain-text USB report for owners to share, like [DeviceReport] for Bluetooth. No serial number. */
object UsbReport {
    fun build(info: UsbDeviceInfo, context: List<Pair<String, String>>): String = buildString {
        appendLine("OpenDrop USB device report")
        appendLine()
        context.forEach { (key, value) -> appendLine("$key: $value") }
        appendLine("USB id: ${info.id}")
        appendLine("Manufacturer: ${info.manufacturer ?: "not reported"}")
        appendLine("Product: ${info.product ?: "not reported"}")
        appendLine("Version: ${info.version ?: "not reported"}")
        appendLine("Known model: ${info.model?.let { "${it.name} (${it.family.label})" } ?: "no"}")
        appendLine()
        appendLine("Interfaces (id/alt: class subclass protocol):")
        info.interfaces.forEach { i ->
            appendLine("  ${i.id}/${i.alternate}: ${className(i.interfaceClass)} ${i.subclass} ${i.protocol}")
            i.endpoints.forEach { appendLine("    $it") }
        }
        appendLine()
        appendLine("Raw descriptors:")
        appendLine(info.rawDescriptors?.let(::hexDump) ?: "  not read (no USB permission)")
        info.hidReportDescriptors.toSortedMap().forEach { (iface, bytes) ->
            appendLine()
            appendLine("HID report descriptor, interface $iface (${bytes.size} bytes):")
            appendLine(hexDump(bytes))
        }
    }

    fun className(c: Int): String = when (c) {
        0x01 -> "audio"
        0x03 -> "HID"
        0x08 -> "mass storage"
        0x0a -> "CDC data"
        0x02 -> "CDC"
        0xfe -> "app-specific"
        0xff -> "vendor"
        else -> String.format(Locale.ROOT, "0x%02x", c)
    }

    /** 16 bytes per line, space separated, indented. */
    fun hexDump(bytes: ByteArray): String = bytes.toList().chunked(16).joinToString("\n") { line ->
        "  " + line.joinToString(" ") { String.format(Locale.ROOT, "%02x", it.toInt() and 0xff) }
    }
}
