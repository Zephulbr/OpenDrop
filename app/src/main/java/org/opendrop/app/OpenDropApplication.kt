package org.opendrop.app

import android.app.Application
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.opendrop.app.automation.Automation
import org.opendrop.app.device.Connection
import org.opendrop.app.device.ConnectionService
import org.opendrop.app.device.DeviceController
import org.opendrop.app.device.LowBatteryAlert
import org.opendrop.app.device.isActive
import org.opendrop.app.settings.AppSettings
import org.opendrop.app.widget.BatteryWidget

class OpenDropApplication : Application() {
    /** The one connection to the earbuds, shared by every screen and the service. */
    val controller: DeviceController by lazy { DeviceController(this) }

    val settings: AppSettings by lazy { AppSettings(this) }

    override fun onCreate() {
        super.onCreate()
        ConnectionService.createChannel(this)
        LowBatteryAlert.createChannel(this)
        val scope = MainScope()
        // Whenever a connection starts, hold it in a foreground service so it
        // survives the app going to the background. The service stops itself.
        scope.launch {
            controller.state
                .map { it.connection.isActive }
                .distinctUntilChanged()
                .filter { it }
                .collect { ConnectionService.start(this@OpenDropApplication) }
        }
        scope.launch {
            controller.state
                .map { BatteryWidget.WidgetContent.of(it) }
                .distinctUntilChanged()
                .collect { BatteryWidget.update(this@OpenDropApplication, it) }
        }
        val lowBattery = LowBatteryAlert(this, settings)
        scope.launch {
            controller.state
                .map { s -> (s.selected?.name ?: "Earbuds") to s.battery.takeIf { s.connection == Connection.Connected } }
                .distinctUntilChanged()
                .collect { (name, level) -> lowBattery.onLevel(name, level) }
        }
        scope.launch {
            combine(controller.state.map { Automation.State.of(it) }, settings.state.map { it.automation }) { state, on ->
                state.takeIf { on }
            }
                .distinctUntilChanged()
                .collect { state -> state?.let { sendBroadcast(it.toIntent()) } }
        }
    }
}
