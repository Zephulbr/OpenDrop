package org.opendrop.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkErrorTest {
    @Test
    fun sortsAndroidBluetoothErrors() {
        assertEquals(LinkError.NO_ANSWER, LinkError.of("read failed, socket might closed or timeout, read ret: -1"))
        assertEquals(LinkError.NO_ANSWER, LinkError.of("Connection timed out"))
        assertEquals(LinkError.REFUSED, LinkError.of("Service discovery failed"))
        assertEquals(LinkError.REFUSED, LinkError.of("Connection refused"))
        assertEquals(LinkError.PERMISSION, LinkError.of("java.lang.SecurityException: Need BLUETOOTH_CONNECT permission"))
        assertEquals(LinkError.BLUETOOTH_OFF, LinkError.of("Bluetooth is off"))
        assertEquals(LinkError.OTHER, LinkError.of("IOException"))
        assertEquals(LinkError.OTHER, LinkError.of(null))
    }
}
