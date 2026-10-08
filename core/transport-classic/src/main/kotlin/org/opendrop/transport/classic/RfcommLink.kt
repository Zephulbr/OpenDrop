package org.opendrop.transport.classic

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.UUID
import org.opendrop.protocol.GaiaLink

/** Blocking RFCOMM (Serial Port Profile) connection to the earbuds' GAIA channel. */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT first
class RfcommLink(private val device: BluetoothDevice) : GaiaLink {
    private var socket: BluetoothSocket? = null

    /** Connects via the SPP service record; falls back to channel 1 (verified on Space Travel). */
    override fun connect() {
        socket = try {
            open(device.createRfcommSocketToServiceRecord(SPP_UUID))
        } catch (e: IOException) {
            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            open(method.invoke(device, FALLBACK_CHANNEL) as BluetoothSocket)
        }
    }

    private fun open(candidate: BluetoothSocket): BluetoothSocket {
        try {
            candidate.connect()
            return candidate
        } catch (e: IOException) {
            runCatching { candidate.close() }
            throw e
        }
    }

    override fun write(data: ByteArray) {
        val out = checkNotNull(socket) { "not connected" }.outputStream
        out.write(data)
        out.flush()
    }

    override fun read(buffer: ByteArray): Int = checkNotNull(socket) { "not connected" }.inputStream.read(buffer)

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val FALLBACK_CHANNEL = 1
    }
}
