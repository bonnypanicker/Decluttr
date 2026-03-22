package com.archive.decluttr.receiver

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.archive.decluttr.MainActivity

class DecluttrTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        if (tile != null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Decluttr Scan"
            tile.updateTile()
        }
    }

    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        
        // Launch the main activity. We could pass an extra to open the 'Discovery' tab directly
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_DISCOVERY", true)
        }
        
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        
        // Android 14+ requires using startActivityAndCollapse with a PendingIntent
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
