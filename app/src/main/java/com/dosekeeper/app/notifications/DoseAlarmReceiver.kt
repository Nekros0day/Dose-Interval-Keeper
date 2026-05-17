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
                val touched = repository.recordDose(ids)
                touched.forEach { DoseNotificationManager.showCountdown(context, it) }
                DoseNotificationManager.scheduleAll(context, repository)
                DoseNotificationManager.cancelDueNotification(context, ids)
            }

            DoseNotificationManager.ACTION_SHOW_DUE -> {
                val ids = intent.getStringExtra(DoseNotificationManager.EXTRA_ITEM_IDS)
                    ?.split(",")
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                val items = repository.state.value.items.filter { it.id in ids }
                if (items.isNotEmpty()) {
                    DoseNotificationManager.showDue(context, items)
                }
            }

            DoseNotificationManager.ACTION_SHOW_SAFE -> {
                val id = intent.getStringExtra(DoseNotificationManager.EXTRA_ITEM_IDS).orEmpty()
                val item = repository.state.value.items.firstOrNull { it.id == id }
                if (item != null) DoseNotificationManager.showSafeAgain(context, item)
            }
        }
    }
}
