package com.dosekeeper.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosekeeper.app.data.DoseRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> DoseNotificationManager.scheduleAll(context, DoseRepository(context))
        }
    }
}
