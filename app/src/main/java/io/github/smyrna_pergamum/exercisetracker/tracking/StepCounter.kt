package io.github.smyrna_pergamum.exercisetracker.tracking

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.room.withTransaction
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.data.StepDay
import io.github.smyrna_pergamum.exercisetracker.data.StepState
import io.github.smyrna_pergamum.exercisetracker.domain.Steps
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Daily steps from the phone's hardware step counter. The sensor keeps counting in the chip while
 * the app is closed, at almost no battery cost; the app only reads it every half hour, just before
 * midnight, after a reboot and while the step card is on screen. Each reading adds the steps since
 * the previous one to the day it was taken.
 */
class StepCounter(private val app: App) {
    private val mutex = Mutex()

    fun hasSensor(): Boolean = sensor() != null

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    private fun sensor(): Sensor? = app.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    /** Adds the steps since the last reading to today. */
    suspend fun record(counter: Long, now: Long = System.currentTimeMillis()) = mutex.withLock {
        app.db.withTransaction {
            val dao = app.db.steps()
            val delta = Steps.delta(dao.state()?.counter, counter)
            if (delta > 0) {
                val today = LocalDate.now().toString()
                val day = dao.day(today)
                dao.upsertDay(StepDay(today, (day?.steps ?: 0) + delta.toInt()))
            }
            dao.setState(StepState(counter = counter, time = now))
        }
    }

    /** One reading of the counter, or null without sensor/permission or when it does not answer. */
    suspend fun readOnce(): Long? {
        val s = sensor() ?: return null
        if (!hasPermission()) return null
        val sm = app.getSystemService(SensorManager::class.java)
        val result = CompletableDeferred<Long>()
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { result.complete(e.values[0].toLong()) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        return try {
            withTimeoutOrNull(5_000) { result.await() }
        } finally {
            sm.unregisterListener(listener)
        }
    }

    /** Live readings while the step card is visible; returns a function that stops them. */
    fun listen(onCounter: (Long) -> Unit): () -> Unit {
        val s = sensor() ?: return {}
        if (!hasPermission()) return {}
        val sm = app.getSystemService(SensorManager::class.java)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) = onCounter(e.values[0].toLong())
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, s, SensorManager.SENSOR_DELAY_NORMAL)
        return { sm.unregisterListener(listener) }
    }

    /** Half-hourly reading plus one shortly before midnight, so steps land on the right day. */
    fun schedule() {
        val am = app.getSystemService(AlarmManager::class.java)
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + AlarmManager.INTERVAL_HALF_HOUR,
            AlarmManager.INTERVAL_HALF_HOUR, pending(REQ_PERIODIC))
        val beforeMidnight = ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(ZonedDateTime.now().zone).minusMinutes(3)
        am.setWindow(AlarmManager.RTC_WAKEUP, beforeMidnight.toInstant().toEpochMilli(), 2 * 60_000L, pending(REQ_MIDNIGHT))
    }

    fun cancel() {
        val am = app.getSystemService(AlarmManager::class.java)
        am.cancel(pending(REQ_PERIODIC))
        am.cancel(pending(REQ_MIDNIGHT))
    }

    private fun pending(req: Int) = PendingIntent.getBroadcast(
        app, req, Intent(app, StepReceiver::class.java).setAction(ACTION_SAMPLE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_SAMPLE = "io.github.smyrna_pergamum.exercisetracker.STEP_SAMPLE"
        private const val REQ_PERIODIC = 501
        private const val REQ_MIDNIGHT = 502
    }
}

/** Takes a reading on schedule. */
open class StepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as App
        val pending = goAsync()
        app.scope.launch {
            try {
                if (!app.settings.flow.first().stepsOn) return@launch
                app.steps.readOnce()?.let { app.steps.record(it) }
                app.steps.schedule()
            } finally {
                pending.finish()
            }
        }
    }
}

/** After a reboot or update: read the restarted counter and set the alarms again. */
class StepBootReceiver : StepReceiver()
