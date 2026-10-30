package io.github.smyrna_pergamum.exercisetracker.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player as M3Player
import androidx.media3.datasource.RawResourceDataSource
import androidx.media3.exoplayer.ExoPlayer
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.MainActivity
import io.github.smyrna_pergamum.exercisetracker.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Source { MEDITATION, RADIO }

/** What is playing now; [sleepAt] is the wall time the sleep timer stops playback. */
data class NowPlaying(
    val source: Source,
    val key: String,
    val title: String,
    val subtitle: String = "",
    val playing: Boolean = true,
    val buffering: Boolean = false,
    val error: Boolean = false,
    val sleepAt: Long? = null,
)

/**
 * One audio player for meditation music and radio (only one plays at a time). Built on Media3
 * ExoPlayer, which handles the bundled Opus files as well as MP3/AAC/HLS radio streams. While
 * something plays, [PlaybackService] keeps the app alive with a notification.
 */
class Player(private val app: App) {
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now
    private var exo: ExoPlayer? = null
    private var sleepJob: Job? = null

    private fun player(): ExoPlayer = exo ?: ExoPlayer.Builder(app).build().also { p ->
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        p.setWakeMode(C.WAKE_MODE_NETWORK)
        p.addListener(object : M3Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { _now.value = _now.value?.copy(playing = isPlaying || p.playWhenReady) }
            override fun onPlaybackStateChanged(state: Int) { _now.value = _now.value?.copy(buffering = state == M3Player.STATE_BUFFERING) }
            override fun onPlayerError(error: PlaybackException) { _now.value = _now.value?.copy(error = true, playing = false, buffering = false) }
        })
        exo = p
    }

    /** Plays a bundled track, looping. */
    fun playTrack(t: Track, title: String) {
        val p = player()
        p.setMediaItem(MediaItem.fromUri(RawResourceDataSource.buildRawResourceUri(t.res)))
        p.repeatMode = M3Player.REPEAT_MODE_ONE
        start(NowPlaying(Source.MEDITATION, "track:${t.res}", title, t.artist))
    }

    fun playStation(s: Station) {
        val p = player()
        p.setMediaItem(MediaItem.fromUri(s.url))
        p.repeatMode = M3Player.REPEAT_MODE_OFF
        start(NowPlaying(Source.RADIO, "radio:${s.id}", s.name, s.subtitle))
    }

    private fun start(np: NowPlaying) {
        val p = player()
        p.prepare()
        p.playWhenReady = true
        _now.value = np.copy(sleepAt = _now.value?.sleepAt)
        ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
    }

    fun toggle() {
        val p = exo ?: return
        p.playWhenReady = !p.playWhenReady
        _now.value = _now.value?.copy(playing = p.playWhenReady)
        if (p.playWhenReady && _now.value?.error == true) { _now.value = _now.value?.copy(error = false); p.prepare() }
    }

    /** Stops after [minutes]; null cancels the timer. */
    fun sleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        val at = minutes?.let { System.currentTimeMillis() + it * 60_000L }
        _now.value = _now.value?.copy(sleepAt = at)
        if (minutes != null) sleepJob = app.scope.launch { delay(minutes * 60_000L); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { stop() } }
    }

    fun stop() {
        sleepJob?.cancel()
        exo?.release()
        exo = null
        _now.value = null
        app.stopService(Intent(app, PlaybackService::class.java))
    }
}

/** Keeps playback going with the screen off and offers a stop button in the notification. */
class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as App
        if (intent?.action == ACTION_STOP) {
            app.player.stop()
            return START_NOT_STICKY
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW))
        ServiceCompat.startForeground(this, ID, notification(app.player.now.value),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
        return START_NOT_STICKY
    }

    private fun notification(np: NowPlaying?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(np?.title ?: getString(R.string.app_name))
            .setContentText(np?.subtitle.orEmpty())
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .build()
    }

    companion object {
        const val CHANNEL = "playback"
        const val ID = 21
        const val ACTION_STOP = "io.github.smyrna_pergamum.exercisetracker.STOP_AUDIO"
    }
}

/** A bundled meditation track (tools: compressed to Opus 48 kbps from the user's files). */
data class Track(val res: Int, val artist: String, val seconds: Int)

/** A looping ambient sound with its picture. */
data class Nature(val res: Int, val image: Int, val title: Int) {
    val track get() = Track(res, "", 0)
}

object Tracks {
    val all = listOf(
        Track(R.raw.med_01, "AtlasAudio", 428),
        Track(R.raw.med_02, "AtlasAudio", 490),
        Track(R.raw.med_03, "Leberch", 426),
        Track(R.raw.med_04, "Leberch", 348),
        Track(R.raw.med_05, "Nastelbom", 549),
        Track(R.raw.med_06, "Sound4Stock", 600),
        Track(R.raw.med_07, "Viacheslav Starostin", 546),
        Track(R.raw.med_08, "Verclub Music", 511),
        Track(R.raw.med_09, "Verclub Music", 522),
        Track(R.raw.med_10, "Verclub Music", 511),
        Track(R.raw.med_11, "Viacheslav Starostin", 594),
    )

    /** "Breathing meditation", used behind the breathing exercise. */
    val breathing = all[7]

    /** Ambient nature and place sounds; they loop endlessly. */
    val nature = listOf(
        Nature(R.raw.nat_birds, R.drawable.scene_birds, R.string.nat_birds),
        Nature(R.raw.nat_wind, R.drawable.scene_wind, R.string.nat_wind),
        Nature(R.raw.nat_water, R.drawable.scene_water, R.string.nat_water),
        Nature(R.raw.nat_forest, R.drawable.scene_forest, R.string.nat_forest),
        Nature(R.raw.nat_restaurant, R.drawable.scene_restaurant, R.string.nat_restaurant),
        Nature(R.raw.nat_city, R.drawable.scene_city, R.string.nat_city),
        Nature(R.raw.nat_rain, R.drawable.scene_rain, R.string.nat_rain),
        Nature(R.raw.nat_fire, R.drawable.scene_fire, R.string.nat_fire),
        Nature(R.raw.nat_night, R.drawable.scene_night, R.string.nat_night),
        Nature(R.raw.nat_aquarium, R.drawable.scene_aquarium, R.string.nat_aquarium),
    )
}

/** A radio station from Radio Browser. */
data class Station(val id: String, val name: String, val url: String, val country: String, val tags: String, val codec: String, val bitrate: Int) {
    val subtitle get() = listOf(country, tags.split(',').take(2).joinToString(", "), if (bitrate > 0) "$bitrate kbps" else "").filter { it.isNotBlank() }.joinToString(" · ")
}
