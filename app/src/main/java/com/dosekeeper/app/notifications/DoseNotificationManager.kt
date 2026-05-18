package com.dosekeeper.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dosekeeper.app.MainActivity
import com.dosekeeper.app.R
import com.dosekeeper.app.data.DoseItem
import com.dosekeeper.app.data.DoseRepository
import com.dosekeeper.app.scheduling.SupplementScheduler
import kotlin.math.absoluteValue

object DoseNotificationManager {
    const val ACTION_CONFIRM = "com.dosekeeper.app.ACTION_CONFIRM"
    const val ACTION_SHOW_DUE = "com.dosekeeper.app.ACTION_SHOW_DUE"
    const val EXTRA_ITEM_IDS = "item_ids"
    const val EXTRA_TRIGGER_AT = "trigger_at"

    private const val DUE_CHANNEL = "dose_due"
    private const val DUE_BASE_ID = 40_000

    fun ensureChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val due = NotificationChannel(
            DUE_CHANNEL,
            "Dose and supplement reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Compact reminders when something is due."
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        notificationManager.createNotificationChannel(due)
    }

    fun scheduleAll(context: Context, repository: DoseRepository) {
        ensureChannels(context)
        val state = repository.state.value
        val plan = SupplementScheduler.buildPlan(
            items = state.activeItems,
            templates = repository.templates(),
            rules = repository.interactions(),
            quietStartMinute = state.quietStartMinute,
            quietEndMinute = state.quietEndMinute,
        )

        plan.forEach { group ->
            if (group.scheduledAtMillis > System.currentTimeMillis()) {
                scheduleAlarm(
                    context = context,
                    triggerAtMillis = group.scheduledAtMillis,
                    itemIds = group.itemIds,
                    requestCode = groupRequestCode(group.itemIds, group.scheduledAtMillis),
                )
            }
        }
    }

    fun showDueIfCurrent(context: Context, repository: DoseRepository, itemIds: List<String>, triggerAtMillis: Long) {
        val idSet = itemIds.toSet()
        val state = repository.state.value
        val plan = SupplementScheduler.buildPlan(
            items = state.activeItems,
            templates = repository.templates(),
            rules = repository.interactions(),
            nowMillis = (triggerAtMillis - 60_000L).coerceAtLeast(0L),
            quietStartMinute = state.quietStartMinute,
            quietEndMinute = state.quietEndMinute,
        )
        val group = plan.firstOrNull {
            it.itemIds.toSet() == idSet && kotlin.math.abs(it.scheduledAtMillis - triggerAtMillis) <= 60_000L
        }
        scheduleAll(context, repository)
        if (group == null) return

        showDue(context, group.entries.map { it.item }, group.notes)
    }

    fun showDue(context: Context, items: List<DoseItem>, notes: List<String> = emptyList()) {
        if (!canNotify(context)) return
        ensureChannels(context)

        val ids = items.map { it.id }
        val title = if (items.size == 1) {
            "Time for ${items.first().name}"
        } else {
            "Time for ${items.first().name} + ${items.size - 1} more"
        }
        val body = buildString {
            append(items.joinToString(", ") { it.name })
            if (notes.isNotEmpty()) append(". ${notes.first()}")
        }

        val notification = NotificationCompat.Builder(context, DUE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_stat_dose,
                "Taken",
                confirmIntent(context, ids),
            )
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        notify(context, dueId(ids), notification)
    }

    fun cancelDueNotification(context: Context, ids: List<String>) {
        NotificationManagerCompat.from(context).cancel(dueId(ids))
    }

    private fun scheduleAlarm(
        context: Context,
        triggerAtMillis: Long,
        itemIds: List<String>,
        requestCode: Int,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(ACTION_SHOW_DUE)
            .putExtra(EXTRA_ITEM_IDS, itemIds.joinToString(","))
            .putExtra(EXTRA_TRIGGER_AT, triggerAtMillis)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun confirmIntent(context: Context, ids: List<String>): PendingIntent {
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(ACTION_CONFIRM)
            .putExtra(EXTRA_ITEM_IDS, ids.joinToString(","))
        return PendingIntent.getBroadcast(
            context,
            confirmRequestCode(ids),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            9,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, notificationId: Int, notification: Notification) {
        if (canNotify(context)) {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    private fun dueId(ids: List<String>): Int = DUE_BASE_ID + ids.sorted().joinToString("|").hashCode().absoluteValue % 30_000
    private fun groupRequestCode(ids: List<String>, triggerAtMillis: Long): Int =
        300_000 + "${ids.sorted().joinToString("|")}:$triggerAtMillis".hashCode().absoluteValue % 30_000

    private fun confirmRequestCode(ids: List<String>): Int =
        400_000 + ids.sorted().joinToString("|").hashCode().absoluteValue % 30_000
}
