package org.opendrop.protocol

import java.io.Closeable

/**
 * A byte stream to a device's GAIA channel, whatever carries it (RFCOMM
 * today, BLE later). Blocking: one thread reads, and [GaiaSession] writes.
 */
interface GaiaLink : Closeable {
    fun connect()

    fun write(data: ByteArray)

    /** Blocks until data arrives. Returns -1 when the connection closes. */
    fun read(buffer: ByteArray): Int
}
