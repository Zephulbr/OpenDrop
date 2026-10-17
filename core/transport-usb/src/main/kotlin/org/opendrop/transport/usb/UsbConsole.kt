package org.opendrop.transport.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import org.opendrop.protocol.UsbProbes
import org.opendrop.protocol.UsbRead
import org.opendrop.protocol.UsbWrite

/**
 * Developer console: sends one [UsbProbes] read, or one of its few reversible
 * writes, to a USB device. Unlike [UsbProbe] these are vendor commands, so
 * the app only calls them behind developer options. It uses control transfers only and claims no interface,
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
        return withConnection(device) { connection -> transfer(connection, read, read.packetBytes()) }
    }

    /**
     * Sends [write] with [value], then its read-back; the result is the
     * read-back reply. Refuses anything not in [UsbProbes.writes].
     */
    fun write(device: UsbDevice, write: UsbWrite, value: Int): Result {
        if (write !in UsbProbes.writes || value !in write.values.indices) return Result.Failed("Not a console write")
        return withConnection(device) { connection ->
            val read = write.readBack
            val packet = write.packet(value)
            val sent = connection.controlTransfer(
                read.requestTypeOut, read.request, read.value, read.index, packet, packet.size, TIMEOUT_MS,
            )
            if (sent < 0) return@withConnection Result.Failed("The device refused the setting (OUT $sent)")
            Thread.sleep(read.replyDelayMs)
            transfer(connection, read, read.packetBytes())
        }
    }

    private fun withConnection(device: UsbDevice, block: (UsbDeviceConnection) -> Result): Result {
        if (!manager.hasPermission(device)) return Result.Failed("No USB permission")
        val connection = manager.openDevice(device) ?: return Result.Failed("Couldn't open the device")
        return try {
            block(connection)
        } finally {
            connection.close()
        }
    }

    /** OUT [packet], wait, IN the reply. */
    private fun transfer(connection: UsbDeviceConnection, read: UsbRead, packet: ByteArray): Result {
        val sent = connection.controlTransfer(
            read.requestTypeOut, read.request, read.value, read.index, packet, packet.size, TIMEOUT_MS,
        )
        if (sent < 0) return Result.Failed("The device refused the request (OUT $sent)")
        Thread.sleep(read.replyDelayMs)
        val buffer = ByteArray(packet.size)
        val n = connection.controlTransfer(
            read.requestTypeIn, read.requestIn, read.value, read.index, buffer, buffer.size, TIMEOUT_MS,
        )
        return if (n < 0) Result.Failed("No reply (IN $n)") else Result.Reply(buffer.copyOf(n))
    }

    private companion object {
        const val TIMEOUT_MS = 500
    }
}
