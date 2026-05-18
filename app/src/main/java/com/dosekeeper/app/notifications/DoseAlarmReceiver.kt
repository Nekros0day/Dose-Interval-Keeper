package com.dosekeeper.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dosekeeper.app.data.DoseRepository

class DoseAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repository = DoseRepository(context)
        when (intent.action) {
            DoseNotificationManager.ACTION_CONFIRM -> {
                val ids = intent.getStringExtra(DoseNotificationManager.EXTRA_ITEM_IDS)
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                repository.recordDose(ids)
                DoseNotificationManager.scheduleAll(context, repository)
                DoseNotificationManager.cancelDueNotification(context, ids)
            }

            DoseNotificationManager.ACTION_SHOW_DUE -> {
                val ids = intent.getStringExtra(DoseNotificationManager.EXTRA_ITEM_IDS)
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                val triggerAt = intent.getLongExtra(DoseNotificationManager.EXTRA_TRIGGER_AT, System.currentTimeMillis())
                DoseNotificationManager.showDueIfCurrent(context, repository, ids, triggerAt)
            }
        }
    }
}
