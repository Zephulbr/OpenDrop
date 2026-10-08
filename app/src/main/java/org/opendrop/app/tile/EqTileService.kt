package org.opendrop.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.opendrop.app.MainActivity
import org.opendrop.app.OpenDropApplication
import org.opendrop.app.R
import org.opendrop.app.device.Connection
import org.opendrop.app.device.UiState
import org.opendrop.app.device.isActive

/**
 * Quick Settings tile: shows the EQ preset and switches to the next one on
 * tap. While disconnected, a tap connects to the remembered earbuds (or opens
 * the app if there are none).
 */
class EqTileService : TileService() {
    private val controller get() = (application as OpenDropApplication).controller
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        scope?.cancel()
        scope = MainScope().also { s ->
            s.launch {
                controller.state.map { TileContent.of(it) }.distinctUntilChanged().collect { show(it) }
            }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val state = controller.state.value
        when {
            state.connection == Connection.Connected -> if (!controller.nextEq()) openApp()
            state.connection.isActive -> Unit // connecting; the tile updates when it's done
            !controller.connectRemembered() -> openApp()
        }
    }

    private fun show(content: TileContent) {
        val tile = qsTile ?: return
        tile.state = content.state
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_opendrop)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.label = getString(R.string.tile_eq)
            tile.subtitle = content.subtitle
        } else {
            tile.label = "${getString(R.string.tile_eq)} · ${content.subtitle}"
        }
        tile.contentDescription = "${getString(R.string.tile_eq)}, ${content.subtitle}"
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private data class TileContent(val state: Int, val subtitle: String) {
        companion object {
            fun of(s: UiState): TileContent = when (s.connection) {
                Connection.Connected -> {
                    val preset = s.device.namedEqPreset
                    when {
                        !s.device.featuresKnown || s.device.variantName == null ->
                            TileContent(Tile.STATE_INACTIVE, "Connecting…")
                        s.device.switchableEqPresets.isEmpty() -> TileContent(Tile.STATE_UNAVAILABLE, "Not supported")
                        preset != null -> TileContent(Tile.STATE_ACTIVE, preset.label)
                        s.device.eqPresetId == null -> TileContent(Tile.STATE_ACTIVE, "–")
                        else -> TileContent(Tile.STATE_ACTIVE, "Custom")
                    }
                }
                Connection.Connecting, Connection.Reconnecting -> TileContent(Tile.STATE_INACTIVE, "Connecting…")
                else -> TileContent(
                    Tile.STATE_INACTIVE,
                    if (s.autoConnect != null) "Tap to connect" else "Not connected",
                )
            }
        }
    }
}
