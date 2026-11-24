package com.scluzlep.miimeprivacy.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.scluzlep.miimeprivacy.R
import com.scluzlep.miimeprivacy.bridge.XposedServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * HyperOS Quick Settings Tile for toggling global IME cloud-input mode (`allowCloudInput`).
 */
class CloudToggleTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var configObserverJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        XposedServiceManager.init(this)
        syncTileState(XposedServiceManager.config.value.allowCloudInput)

        configObserverJob?.cancel()
        configObserverJob = serviceScope.launch {
            XposedServiceManager.config.collect { cfg ->
                syncTileState(cfg.allowCloudInput)
            }
        }
    }

    override fun onStopListening() {
        configObserverJob?.cancel()
        configObserverJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        XposedServiceManager.init(this)
        val updated = XposedServiceManager.updateConfig { current ->
            current.copy(allowCloudInput = !current.allowCloudInput)
        }
        syncTileState(updated.allowCloudInput)
    }

    override fun onDestroy() {
        configObserverJob?.cancel()
        configObserverJob = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun syncTileState(allowCloud: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (allowCloud) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_cloud_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (allowCloud) SUBTITLE_CLOUD_ON else SUBTITLE_LOCAL_ONLY
        }
        tile.updateTile()
    }

    companion object {
        internal const val SUBTITLE_CLOUD_ON = "云输入"
        internal const val SUBTITLE_LOCAL_ONLY = "纯本地"
    }
}
