package com.dosekeeper.app.notifications

import android.annotation.SuppressLint
import android.Manifest
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
import com.dosekeeper.app.data.DoseKind
import com.dosekeeper.app.data.DoseRepository
import com.dosekeeper.app.scheduling.SupplementScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.absoluteValue

object DoseNotificationManager {
    const val ACTION_CONFIRM = "com.dosekeeper.app.ACTION_CONFIRM"
    const val ACTION_SHOW_DUE = "com.dosekeeper.app.ACTION_SHOW_DUE"
    const val ACTION_SHOW_SAFE = "com.dosekeeper.app.ACTION_SHOW_SAFE"
    const val EXTRA_ITEM_IDS = "item_ids"

    private const val DUE_CHANNEL = "dose_due"
    private const val COUNTDOWN_CHANNEL = "dose_countdown"
    private const val DUE_BASE_ID = 40_000
    private const val COUNTDOWN_BASE_ID = 80_000
    private const val SAFE_BASE_ID = 120_000

    fun ensureChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val due = NotificationChannel(
            DUE_CHANNEL,
            "Dose and supplement reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders for safe intervals and supplement plan windows."
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }

        val countdown = NotificationChannel(
            COUNTDOWN_CHANNEL,
            "Lock-screen countdowns",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Ongoing countdowns until the next safe interval."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        notificationManager.createNotificationChannels(listOf(due, countdown))
    }

    fun scheduleAll(context: Context, repository: DoseRepository) {
        ensureChannels(context)
        val state = repository.state.value
        state.activeItems.forEach { item ->
            val nextSafeAt = item.nextSafeAtMillis()
            if (nextSafeAt != null && nextSafeAt > System.currentTimeMillis()) {
                scheduleAlarm(context, ACTION_SHOW_SAFE, nextSafeAt, listOf(item.id), safeRequestCode(item.id))
                showCountdown(context, item)
            }
        }

        val plan = SupplementScheduler.buildPlan(
            items = state.activeItems,
            templates = repository.templates(),
            rules = repository.conflicts(),
        )

        plan.forEachIndexed { index, group ->
            val triggerAt = nextPlanOccurrenceMillis(group.minuteOfDay, group.entries.map { it.item })
            scheduleAlarm(context, ACTION_SHOW_DUE, triggerAt, group.itemIds, groupRequestCode(index))
        }
    }

    fun showCountdown(context: Context, item: DoseItem) {
        if (!canNotify(context)) return
        val nextSafeAt = item.nextSafeAtMillis() ?: return
        if (nextSafeAt <= System.currentTimeMillis()) return

        ensureChannels(context)
        val publicVersion = NotificationCompat.Builder(context, COUNTDOWN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle("Dose timer")
            .setContentText("Countdown running")
            .setWhen(nextSafeAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setSilent(true)
            .build()

        val notification = NotificationCompat.Builder(context, COUNTDOWN_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle("${item.name} safe interval")
            .setContentText("Next safe window in ${timeUntil(nextSafeAt)}")
            .setContentIntent(openAppIntent(context))
            .setWhen(nextSafeAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()

        notify(context, countdownId(item.id), notification)
    }

    fun showDue(context: Context, items: List<DoseItem>) {
        if (!canNotify(context)) return
        ensureChannels(context)

        val ids = items.map { it.id }
        val title = if (items.size == 1) {
            "Time for ${items.first().name}"
        } else {
            "Time for ${items.size} supplements"
        }
        val body = items.joinToString(", ") { it.name }

        val notification = NotificationCompat.Builder(context, DUE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_stat_dose,
                "Confirm taken",
                confirmIntent(context, ids),
            )
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        notify(context, dueId(ids), notification)
    }

    fun showSafeAgain(context: Context, item: DoseItem) {
        if (!canNotify(context)) return
        ensureChannels(context)
        NotificationManagerCompat.from(context).cancel(countdownId(item.id))

        val label = when (item.kind) {
            DoseKind.Medication -> "${item.name} interval complete"
            DoseKind.Supplement -> "${item.name} is ready again"
        }

        val notification = NotificationCompat.Builder(context, DUE_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dose)
            .setContentTitle(label)
            .setContentText("Timing aid only. Follow the label or clinician instructions.")
            .setContentIntent(openAppIntent(context))
            .addAction(
                R.drawable.ic_stat_dose,
                "Confirm taken",
                confirmIntent(context, listOf(item.id)),
            )
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        notify(context, safeId(item.id), notification)
    }

    fun cancelDueNotification(context: Context, ids: List<String>) {
        NotificationManagerCompat.from(context).cancel(dueId(ids))
        ids.forEach { NotificationManagerCompat.from(context).cancel(safeId(it)) }
    }

    private fun scheduleAlarm(
        context: Context,
        action: String,
        triggerAtMillis: Long,
        itemIds: List<String>,
        requestCode: Int,
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, DoseAlarmReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_ITEM_IDS, itemIds.joinToString(","))
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun nextPlanOccurrenceMillis(minuteOfDay: Int, items: List<DoseItem>): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val alreadyTakenToday = items.all { (it.lastDoseAtMillis ?: 0L) >= startOfToday }
        var target = now.toLocalDate().atStartOfDay(zone).plusMinutes(minuteOfDay.toLong())
        if (!target.isAfter(now) || alreadyTakenToday) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
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

    private fun timeUntil(targetMillis: Long): String {
        val minutes = ((targetMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L).coerceAtLeast(1L)
        val hours = minutes / 60
        val remaining = minutes % 60
        return if (hours > 0) "${hours}h ${remaining}m" else "${remaining}m"
    }

    private fun countdownId(itemId: String): Int = COUNTDOWN_BASE_ID + itemId.hashCode().absoluteValue % 30_000
    private fun safeId(itemId: String): Int = SAFE_BASE_ID + itemId.hashCode().absoluteValue % 30_000
    private fun dueId(ids: List<String>): Int = DUE_BASE_ID + ids.sorted().joinToString("|").hashCode().absoluteValue % 30_000
    private fun safeRequestCode(itemId: String): Int = 200_000 + itemId.hashCode().absoluteValue % 30_000
    private fun groupRequestCode(index: Int): Int = 300_000 + index
    private fun confirmRequestCode(ids: List<String>): Int = 400_000 + ids.sorted().joinToString("|").hashCode().absoluteValue % 30_000
}
