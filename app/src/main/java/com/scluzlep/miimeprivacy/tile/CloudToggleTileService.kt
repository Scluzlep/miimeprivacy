package com.scluzlep.miimeprivacy.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.scluzlep.miimeprivacy.R
import com.scluzlep.miimeprivacy.bridge.XposedServiceManager

/**
 * HyperOS Quick Settings Tile for toggling global IME cloud-input mode (`allowCloudInput`).
 */
class CloudToggleTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        XposedServiceManager.init(applicationContext)
        syncTileState()
    }

    override fun onClick() {
        super.onClick()
        XposedServiceManager.init(applicationContext)
        XposedServiceManager.updateConfig { current ->
            current.copy(allowCloudInput = !current.allowCloudInput)
        }
        syncTileState()
    }

    private fun syncTileState() {
        val tile = qsTile ?: return
        val allowCloud = XposedServiceManager.config.value.allowCloudInput
        tile.state = if (allowCloud) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_cloud_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (allowCloud) R.string.tile_cloud_on else R.string.tile_cloud_off
            )
        }
        tile.updateTile()
    }
}
