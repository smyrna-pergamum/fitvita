package io.github.smyrna_pergamum.exercisetracker.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.MainActivity
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Activity
import io.github.smyrna_pergamum.exercisetracker.data.ActivityType
import io.github.smyrna_pergamum.exercisetracker.data.Status
import io.github.smyrna_pergamum.exercisetracker.data.TrackPoint
import io.github.smyrna_pergamum.exercisetracker.domain.Geo
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import io.github.smyrna_pergamum.exercisetracker.domain.Steps
import io.github.smyrna_pergamum.exercisetracker.domain.TrackFilter
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the live screen shows while an activity is recorded. */
data class Live(
    val activityId: String,
    val type: ActivityType,
    val startedAt: Long,
    /** Active time before the current running stretch. */
    val activeBeforeMs: Long = 0,
    /** elapsedRealtime when the current stretch started, null while paused. */
    val runningSince: Long? = SystemClock.elapsedRealtime(),
    val segment: Int = 0,
    val points: List<Pt> = emptyList(),
    val distanceM: Double = 0.0,
    /** Last fix, also while paused, so the map can show where you are. */
    val here: Pt? = null,
    val accuracyM: Float? = null,
    /** Steps while running (not paused); null until the step sensor has answered. */
    val steps: Int? = null,
    val stepCounter: Long? = null,
) {
    val paused get() = runningSince == null
    fun activeMs(now: Long = SystemClock.elapsedRealtime()) = activeBeforeMs + (runningSince?.let { now - it } ?: 0)
}

/**
 * Records one outdoor activity at a time. GPS comes from the platform LocationManager (no Google
 * services). Points are written to the database as they arrive, so a crash loses nothing.
 */
class Tracker(private val app: App) {
    private val _live = MutableStateFlow<Live?>(null)
    val live: StateFlow<Live?> = _live
    private val filter = TrackFilter()
    private var seq = 0
    /** Point inserts run one after another; finish() waits for the last one. */
    private var lastInsert: Job? = null

    fun start(type: ActivityType) {
        if (_live.value != null) return
        val a = Activity(type = type, status = Status.ACTIVE, startedAt = System.currentTimeMillis())
        _live.value = Live(a.id, type, a.startedAt)
        seq = 0
        filter.reset()
        app.scope.launch { app.db.activities().insert(a) }
        ContextCompat.startForegroundService(app, Intent(app, TrackingService::class.java))
    }

    fun pause() {
        val l = _live.value ?: return
        if (l.paused) return
        _live.value = l.copy(activeBeforeMs = l.activeMs(), runningSince = null, segment = l.segment + 1)
    }

    fun resume() {
        val l = _live.value ?: return
        if (!l.paused) return
        filter.reset()
        _live.value = l.copy(runningSince = SystemClock.elapsedRealtime())
    }

    /** Stops recording and stores the route; returns the activity id, or null if it was empty. */
    suspend fun finish(): String? {
        val l = _live.value ?: return null
        val active = l.activeMs()
        app.stopService(Intent(app, TrackingService::class.java))
        // Saving must finish even if the screen that asked for it goes away.
        return withContext(NonCancellable) {
            lastInsert?.join()
            app.repo?.finishActivity(l.activityId, active, _live.value?.steps ?: l.steps)
            _live.value = null
            l.activityId.takeIf { app.db.activities().get(it) != null }
        }
    }

    suspend fun discard() {
        val l = _live.value ?: return
        _live.value = null
        app.stopService(Intent(app, TrackingService::class.java))
        app.db.activities().delete(l.activityId)
    }

    internal fun onStepCounter(counter: Long) {
        val l = _live.value ?: return
        val d = Steps.delta(l.stepCounter, counter).toInt()
        _live.value = l.copy(stepCounter = counter, steps = (l.steps ?: 0) + if (l.paused) 0 else d)
    }

    private var lastGps = 0L

    /**
     * A new position. While satellite GPS is delivering, fixes from other providers only move the
     * "you are here" dot; otherwise good fused/network fixes are recorded too.
     */
    internal fun onFix(loc: Location, recordable: Boolean = true) {
        val l = _live.value ?: return
        val now = SystemClock.elapsedRealtime()
        val gps = loc.provider == LocationManager.GPS_PROVIDER
        if (gps) lastGps = now
        val p = Pt(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else null, loc.time, l.segment)
        val acc = if (loc.hasAccuracy()) loc.accuracy else null
        val record = recordable && (gps || now - lastGps > 10_000)
        if (l.paused || !record || !filter.accept(p, acc)) {
            _live.value = l.copy(here = p, accuracyM = acc)
            return
        }
        val prev = l.points.lastOrNull()
        val step = if (prev != null && prev.s == p.s) Geo.distance(prev.lat, prev.lon, p.lat, p.lon) else 0.0
        _live.value = l.copy(points = l.points + p, distanceM = l.distanceM + step, here = p, accuracyM = acc)
        val point = TrackPoint(l.activityId, seq++, p.lat, p.lon, p.alt, p.t, p.s)
        val prevJob = lastInsert
        lastInsert = app.scope.launch { prevJob?.join(); app.db.activities().insertPoint(point) }
    }
}

/** Foreground service that keeps GPS running while the screen is off (no background permission needed). */
class TrackingService : Service(), LocationListener {
    private val app get() = application as App
    private var lastNotify = 0L
    private var stopSteps: () -> Unit = {}

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW))
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this, NOTIFICATION, notification(getString(R.string.tracking_running)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        if (!hasLocationPermission()) {
            stopSelf()
            return START_NOT_STICKY
        }
        val lm = getSystemService(LocationManager::class.java)
        lm.removeUpdates(this)
        // Satellite GPS first; the platform "fused" and network providers fill in where GPS is slow to
        // start or weak (indoors, between buildings). No Google services are involved.
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= 31) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { it in lm.allProviders && lm.isProviderEnabled(it) }.getOrDefault(false) }
        providers.forEach { p ->
            runCatching { lm.requestLocationUpdates(p, 2_000L, 0f, this, Looper.getMainLooper()) }
        }
        // Show where we are straight away from the last known position.
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 5 * 60_000 }
            .maxByOrNull { it.time }
            ?.let { app.tracker.onFix(it, recordable = false) }
        stopSteps()
        stopSteps = app.steps.listen { app.tracker.onStepCounter(it) }
        return START_NOT_STICKY
    }

    override fun onLocationChanged(location: Location) {
        app.tracker.onFix(location)
        val l = app.tracker.live.value ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastNotify > 5_000) {
            lastNotify = now
            val text = "%.2f km · %s".format(l.distanceM / 1000, formatDuration(l.activeMs(now)))
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification(text))
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit

    override fun onDestroy() {
        getSystemService(LocationManager::class.java).removeUpdates(this)
        stopSteps()
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.tracking_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        const val CHANNEL = "tracking"
        const val NOTIFICATION = 11
    }
}

/** Precise or approximate location; approximate works but gives a rough route. */
fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

fun Context.hasPreciseLocation() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Whether the phone's location switch is on. */
fun Context.isLocationOn(): Boolean {
    val lm = getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
    else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}
