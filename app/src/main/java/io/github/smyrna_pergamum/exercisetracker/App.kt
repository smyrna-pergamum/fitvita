package io.github.smyrna_pergamum.exercisetracker

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings as SysSettings
import io.github.smyrna_pergamum.exercisetracker.backup.Backup
import io.github.smyrna_pergamum.exercisetracker.data.AppDatabase
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.data.SettingsStore
import io.github.smyrna_pergamum.exercisetracker.domain.Clock
import io.github.smyrna_pergamum.exercisetracker.security.FieldKeys
import io.github.smyrna_pergamum.exercisetracker.security.KeyLostException
import io.github.smyrna_pergamum.exercisetracker.tracking.Reminders
import io.github.smyrna_pergamum.exercisetracker.tracking.StepCounter
import io.github.smyrna_pergamum.exercisetracker.tracking.Tracker
import io.github.smyrna_pergamum.exercisetracker.tracking.TrackingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import kotlin.system.exitProcess

class AndroidClock(private val context: Context) : Clock {
    override fun wall() = System.currentTimeMillis()
    override fun elapsed() = SystemClock.elapsedRealtime()
    override fun bootCount() = SysSettings.Global.getInt(context.contentResolver, SysSettings.Global.BOOT_COUNT, -1)
}

/** Dependency container. Nothing is sent over the network at start; map tiles load only on map screens. */
class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var db: AppDatabase
    lateinit var settings: SettingsStore
    lateinit var clock: Clock
    lateinit var tracker: Tracker
    lateinit var steps: StepCounter
    val player by lazy { io.github.smyrna_pergamum.exercisetracker.audio.Player(this) }
    val radio by lazy { io.github.smyrna_pergamum.exercisetracker.audio.RadioBrowser() }

    /** Null when the device key is lost and stored data can no longer be decrypted. */
    var repo: Repository? = null
        private set
    var backup: Backup? = null
        private set

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.create(this)
        settings = SettingsStore(this)
        clock = AndroidClock(this)
        tracker = Tracker(this)
        steps = StepCounter(this)
        Configuration.getInstance().apply {
            // OSM tile policy: identify the app; keep the tile cache in app-private cache storage.
            userAgentValue = "FitVita/${BuildConfig.VERSION_NAME} (${BuildConfig.APPLICATION_ID})"
            osmdroidBasePath = cacheDir.resolve("osmdroid")
            osmdroidTileCache = cacheDir.resolve("osmdroid/tiles")
        }
        try {
            val cipher = FieldKeys.open(this)
            repo = Repository(db, cipher, clock)
            backup = Backup(db, cipher)
        } catch (e: KeyLostException) {
            repo = null
        }
        scope.launch {
            repo?.closeOpenActivities()
            if (settings.flow.first().stepsOn) steps.schedule()
            Reminders.channel(this@App)
            Reminders.scheduleAll(this@App, db.health().remindersOnce())
        }
    }

    /** Deletes the database, settings, keys and caches, then restarts the app. */
    fun wipeAndRestart() {
        scope.launch {
            stopService(Intent(this@App, TrackingService::class.java))
            steps.cancel()
            kotlinx.coroutines.withContext(Dispatchers.Main) { player.stop() }
            Reminders.cancelAll(this@App)
            runCatching { settings.clear() }
            db.close()
            deleteDatabase(AppDatabase.NAME)
            FieldKeys.destroy(this@App)
            cacheDir.deleteRecursively()
            startActivity(Intent(this@App, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            exitProcess(0)
        }
    }
}
