package org.opendrop.protocol

import java.util.Locale

/**
 * Why a connection to the earbuds failed, in words a user can act on.
 * Android's Bluetooth errors are terse ("read failed, socket might closed or
 * timeout, read ret: -1"), so [of] sorts them into a few causes.
 */
enum class LinkError(val advice: String) {
    /** No answer: in the case, out of range, or not connected to the phone. */
    NO_ANSWER(
        "The earbuds didn't answer. Take them out of the case, keep them near the phone, and check that " +
            "they're connected in Android's Bluetooth settings.",
    ),

    /** The control channel is taken or refused, usually by MOONDROP Link. */
    REFUSED(
        "The earbuds refused the control connection. Close the MOONDROP Link app (swipe it away from recent " +
            "apps); only one app can use it at a time.",
    ),

    /** Permission was revoked while OpenDrop was running. */
    PERMISSION("OpenDrop lost the Bluetooth permission. Allow \"Nearby devices\" for OpenDrop in Android's settings."),

    /** Bluetooth turned off or restarted. */
    BLUETOOTH_OFF("Bluetooth turned off. Turn it on and try again."),

    OTHER(
        "Make sure the earbuds are out of the case and connected, and the MOONDROP Link app is closed, then " +
            "try again.",
    ),
    ;

    companion object {
        fun of(message: String?): LinkError {
            val m = message?.lowercase(Locale.ROOT) ?: return OTHER
            return when {
                "permission" in m || "securityexception" in m -> PERMISSION
                "bluetooth is off" in m || "adapter is off" in m || "not enabled" in m -> BLUETOOTH_OFF
                "refused" in m || "service discovery failed" in m || "busy" in m || "in use" in m -> REFUSED
                "timeout" in m || "timed out" in m || "read failed" in m || "host is down" in m ||
                    "socket might closed" in m || "connect failed" in m || "unable to connect" in m -> NO_ANSWER
                else -> OTHER
            }
        }
    }
}
