package org.opendrop.transport.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import org.opendrop.protocol.UsbProbes
import org.opendrop.protocol.UsbRead

/**
 * Developer console: sends one [UsbProbes] read to a USB device. Unlike
 * [UsbProbe] this is a vendor command, so the app only calls it behind
 * developer options. It uses control transfers only and claims no interface,
 * so the phone's audio driver keeps the device. Blocking; call it off the main
 * thread.
 */
class UsbConsole(private val manager: UsbManager) {
    sealed interface Result {
        data class Reply(val bytes: ByteArray) : Result
        data class Failed(val message: String) : Result
    }

    fun query(device: UsbDevice, read: UsbRead): Result {
        if (read !in UsbProbes.reads) return Result.Failed("Not a console read")
        if (!manager.hasPermission(device)) return Result.Failed("No USB permission")
        val connection = manager.openDevice(device) ?: return Result.Failed("Couldn't open the device")
        return try {
            val packet = read.packetBytes()
            val sent = connection.controlTransfer(
                read.requestTypeOut, read.request, read.value, read.index, packet, packet.size, TIMEOUT_MS,
            )
            if (sent < 0) return Result.Failed("The device refused the request (OUT $sent)")
            Thread.sleep(read.replyDelayMs)
            val buffer = ByteArray(packet.size)
            val n = connection.controlTransfer(
                read.requestTypeIn, read.request, read.value, read.index, buffer, buffer.size, TIMEOUT_MS,
            )
            if (n < 0) Result.Failed("No reply (IN $n)") else Result.Reply(buffer.copyOf(n))
        } finally {
            connection.close()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 500
    }
}
