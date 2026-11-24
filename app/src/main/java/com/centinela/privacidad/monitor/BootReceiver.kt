package com.centinela.privacidad.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.centinela.privacidad.CentinelaApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? CentinelaApp ?: return
        val prefs = app.repository.preferences.value
        if (prefs.autoStartOnBoot && prefs.wasMonitoringEnabled) {
            SensorMonitorService.start(context)
        }
    }
}
