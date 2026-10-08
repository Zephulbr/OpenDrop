package org.opendrop.app.device

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.opendrop.app.OpenDropApplication

/**
 * Hears a Bluetooth device connect to the phone. Declared in the manifest
 * (ACL_CONNECTED is allowed there), so auto-connect also works when Android
 * has closed the app in the background.
 */
class AclReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
        (context.applicationContext as OpenDropApplication).controller.onAclConnected(intent)
    }
}
