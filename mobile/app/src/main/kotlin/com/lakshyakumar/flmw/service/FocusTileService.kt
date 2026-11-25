package com.lakshyakumar.flmw.service

import android.net.VpnService
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lakshyakumar.flmw.FlmwApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings tile allowing one-tap 25m focus sessions from the notification shade.
 */
class FocusTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        val repo = (application as FlmwApplication).repository
        val active = repo.focusState.value.isEffectivelyActive()
        scope.launch {
            if (active) {
                repo.stopFocus()
                FlmwVpnService.stop(applicationContext)
            } else {
                repo.startFocus(25)
                if (VpnService.prepare(applicationContext) == null) {
                    FlmwVpnService.start(applicationContext)
                }
            }
            syncTile()
        }
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val repo = (application as FlmwApplication).repository
        val active = repo.focusState.value.isEffectivelyActive()
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (active) "Focus ON" else "flmw Focus"
        tile.updateTile()
    }
}
