package org.opendrop.app

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.opendrop.app.device.ConnectionService
import org.opendrop.app.device.DeviceController
import org.opendrop.app.device.isActive

class OpenDropApplication : Application() {
    /** The one connection to the earbuds, shared by every screen and the service. */
    val controller: DeviceController by lazy { DeviceController(this) }

    override fun onCreate() {
        super.onCreate()
        ConnectionService.createChannel(this)
        // Whenever a connection starts, hold it in a foreground service so it
        // survives the app going to the background. The service stops itself.
        MainScope().launch {
            controller.state
                .map { it.connection.isActive }
                .distinctUntilChanged()
                .filter { it }
                .collect { ConnectionService.start(this@OpenDropApplication) }
        }
    }
}
