package io.github.smyrna_pergamum.exercisetracker.tracking

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.MainActivity
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Reminder
import io.github.smyrna_pergamum.exercisetracker.data.ReminderType
import io.github.smyrna_pergamum.exercisetracker.domain.Goals
import io.github.smyrna_pergamum.exercisetracker.domain.Quest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Friendly, game-style reminders for water, moving, workouts and yoga. They are local alarms
 * (inexact, so Android may deliver them a little late) and say what is left for today's goals.
 * A reminder whose goal is already reached today stays silent.
 */
object Reminders {
    const val CHANNEL = "reminders"
    const val LAST_HOUR = 21
    const val ACTION_FIRE = "io.github.smyrna_pergamum.exercisetracker.REMIND"
    const val ACTION_WATER = "io.github.smyrna_pergamum.exercisetracker.WATER_PLUS"
    const val EXTRA_TYPE = "type"

    fun defaults(): List<Reminder> = listOf(
        Reminder(ReminderType.WATER, hour = 9, intervalH = 2),
        Reminder(ReminderType.MOVE, hour = 16),
        Reminder(ReminderType.WORKOUT, hour = 18, days = 0b0010101),
        Reminder(ReminderType.YOGA, hour = 21, minute = 30),
    )

    /** Next firing time strictly after [now]. */
    fun next(r: Reminder, now: ZonedDateTime): ZonedDateTime? {
        if (r.days and 0x7F == 0) return null
        for (i in 0..7) {
            val d = now.toLocalDate().plusDays(i.toLong())
            if (r.days and (1 shl (d.dayOfWeek.value - 1)) == 0) continue
            val times = if (r.intervalH > 0) (r.hour..LAST_HOUR step r.intervalH).map { it to 0 } else listOf(r.hour to r.minute)
            times.map { (h, m) -> d.atTime(h, m).atZone(now.zone) }.firstOrNull { it.isAfter(now) }?.let { return it }
        }
        return null
    }

    fun canNotify(context: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun scheduleAll(context: Context, list: List<Reminder>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = ZonedDateTime.now()
        list.forEach { r ->
            val pi = pending(context, r.type)
            am.cancel(pi)
            if (!r.enabled) return@forEach
            val t = next(r, now) ?: return@forEach
            am.setWindow(AlarmManager.RTC_WAKEUP, t.toInstant().toEpochMilli(), 10 * 60_000L, pi)
        }
    }

    fun cancelAll(context: Context) = ReminderType.entries.forEach { context.getSystemService(AlarmManager::class.java).cancel(pending(context, it)) }

    private fun pending(context: Context, type: ReminderType) = PendingIntent.getBroadcast(
        context, 600 + type.ordinal, Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_TYPE, type.name),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Builds and shows the message for [type] from today's numbers. */
    suspend fun show(app: App, type: ReminderType) {
        val repo = app.repo ?: return
        val s = app.settings.flow.first()
        val goals = Goals(s.stepGoal, s.waterGoal, s.goalKg)
        val game = repo.game(kotlinx.coroutines.flow.flowOf(goals)).first()
        val (title, text) = when (type) {
            ReminderType.WATER -> {
                if (Quest.WATER in game.done) return
                app.getString(R.string.rem_water_title) to app.getString(R.string.rem_water_text, game.today.glasses, s.waterGoal)
            }
            ReminderType.MOVE -> {
                if (Quest.STEPS in game.done) return
                app.getString(R.string.rem_move_title) to
                    if (s.stepsOn) app.getString(R.string.rem_move_text, (s.stepGoal - game.today.steps).coerceAtLeast(0).toString())
                    else app.getString(R.string.rem_move_text_nosteps)
            }
            ReminderType.WORKOUT -> {
                if (Quest.MOVE in game.done) return
                app.getString(R.string.rem_workout_title) to
                    if (game.streak > 0) app.getString(R.string.rem_streak_text, game.streak) else app.getString(R.string.rem_workout_text)
            }
            ReminderType.YOGA -> {
                if (Quest.MIND in game.done) return
                app.getString(R.string.rem_yoga_title) to app.getString(R.string.rem_yoga_text)
            }
        }
        notify(app, type, title, text)
    }

    fun notify(context: Context, type: ReminderType, title: String, text: String) {
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (type == ReminderType.WATER) {
            val plus = PendingIntent.getBroadcast(context, 700, Intent(context, ReminderReceiver::class.java).setAction(ACTION_WATER),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(0, context.getString(R.string.rem_water_action), plus)
        }
        runCatching { NotificationManagerCompat.from(context).notify(800 + type.ordinal, b.build()) }
    }
}

open class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val pending = goAsync()
        app.scope.launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_FIRE -> {
                        val type = runCatching { ReminderType.valueOf(intent.getStringExtra(Reminders.EXTRA_TYPE)!!) }.getOrNull()
                        if (type != null) Reminders.show(app, type)
                        Reminders.scheduleAll(app, app.db.health().remindersOnce())
                    }
                    // "+1 glass" straight from the notification.
                    Reminders.ACTION_WATER -> {
                        val glasses = app.repo?.addWater(1) ?: return@launch
                        val goal = app.settings.flow.first().waterGoal
                        Reminders.notify(app, ReminderType.WATER, app.getString(R.string.rem_water_done_title),
                            app.getString(R.string.rem_water_done_text, glasses, goal))
                    }
                    else -> Reminders.scheduleAll(app, app.db.health().remindersOnce()) // boot, time zone, update
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/** Boot, time zone and clock changes, app update: rebuild the alarms. */
class ReminderBootReceiver : ReminderReceiver()
