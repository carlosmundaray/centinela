package com.centinela.privacidad.blocker

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class PrivacyLockTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val controller = SensorBlockerController(this)
        val status = controller.getStatus()
        val anyBlocked = status.isCameraBlocked || status.isMicBlocked

        if (anyBlocked) {
            if (status.canBlockCameraProgrammatically) {
                controller.setCameraBlocked(false)
            }
            controller.setMicrophoneBlocked(false)
        } else {
            if (status.canBlockCameraProgrammatically) {
                controller.setCameraBlocked(true)
            }
            controller.setMicrophoneBlocked(true)
        }
        refreshTile()
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val status = SensorBlockerController(this).getStatus()
        val anyBlocked = status.isCameraBlocked || status.isMicBlocked

        tile.state = if (anyBlocked) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = when {
            status.isCameraBlocked && status.isMicBlocked -> "Cám+Mic Bloq"
            status.isCameraBlocked -> "Cámara Bloq"
            status.isMicBlocked -> "Micro Silenc"
            else -> "Bloquear Cám/Mic"
        }
        tile.updateTile()
    }
}
