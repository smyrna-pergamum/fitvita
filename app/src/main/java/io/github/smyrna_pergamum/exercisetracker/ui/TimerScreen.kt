package io.github.smyrna_pergamum.exercisetracker.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.R
import kotlinx.coroutines.delay

/** Stopwatch and countdown state, kept for the whole app process so leaving the screen keeps them running. */
object Timers {
    var swRunning by mutableStateOf(false)
    var swStart by mutableLongStateOf(0L)
    var swBase by mutableLongStateOf(0L)
    val laps = mutableStateListOf<Long>()

    var cdTotal by mutableLongStateOf(60_000L)
    var cdPaused by mutableLongStateOf(60_000L)
    var cdEnd by mutableLongStateOf(0L)
    var cdRunning by mutableStateOf(false)

    private fun now() = SystemClock.elapsedRealtime()
    fun stopwatch() = swBase + if (swRunning) now() - swStart else 0
    fun countdown() = if (cdRunning) (cdEnd - now()).coerceAtLeast(0) else cdPaused

    fun swToggle() {
        if (swRunning) swBase += now() - swStart else swStart = now()
        swRunning = !swRunning
    }
    fun swReset() { swRunning = false; swBase = 0; laps.clear() }
    fun lap() { laps.add(0, stopwatch()) }

    fun cdToggle() {
        if (cdRunning) { cdPaused = countdown(); cdRunning = false }
        else if (cdPaused > 0) { cdEnd = now() + cdPaused; cdRunning = true }
    }
    fun cdSet(ms: Long) { cdRunning = false; cdTotal = ms.coerceIn(5_000, 99 * 60_000L); cdPaused = cdTotal }
    fun cdAdd(ms: Long) {
        if (cdRunning) cdEnd = (cdEnd + ms).coerceAtLeast(now() + 1_000) else cdPaused = (cdPaused + ms).coerceIn(5_000, 99 * 60_000L)
        cdTotal = maxOf(cdTotal, countdown())
    }
    fun cdReset() { cdRunning = false; cdPaused = cdTotal }
}

/** "m:ss.t", with hours when needed. */
fun clockText(ms: Long, tenths: Boolean = true): String {
    val s = ms / 1000
    val base = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    return if (tenths) base + "." + (ms / 100 % 10) else base
}

/** Stopwatch (tab 0) and countdown timer (tab 1). */
@Composable
fun TimerScreen(nav: NavHostController, startTab: Int) {
    var tab by rememberSaveable { mutableIntStateOf(startTab.coerceIn(0, 1)) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(50); tick++ } }
    // The tick is passed down so the clocks recompose on every tick.
    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(R.string.timers), onBack = { nav.popBackStack() })
        PrimaryTabRow(tab) {
            Tab(tab == 0, { tab = 0 }, text = { Text(stringResource(R.string.stopwatch)) })
            Tab(tab == 1, { tab = 1 }, text = { Text(stringResource(R.string.countdown)) })
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (tab == 0) StopwatchTab(tick) else CountdownTab(tick)
        }
    }
}

@Composable
private fun StopwatchTab(tick: Long) {
    check(tick >= 0)
    val t = Timers.stopwatch()
    Text(clockText(t), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 48.dp, bottom = 24.dp))
    BigButton(stringResource(if (Timers.swRunning) R.string.pause else if (t > 0) R.string.resume else R.string.start), { Timers.swToggle() })
    if (Timers.swRunning) OutlinedButton(onClick = { Timers.lap() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.lap)) }
    else if (t > 0) OutlinedButton(onClick = { Timers.swReset() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset)) }
    val laps = Timers.laps
    laps.forEachIndexed { i, total ->
        val split = total - (laps.getOrNull(i + 1) ?: 0L)
        Row(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.lap_n, laps.size - i), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("+" + clockText(split), Modifier.weight(1f), fontFamily = FontFamily.Monospace)
            Text(clockText(total), fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun CountdownTab(tick: Long) {
    check(tick >= 0)
    val left = Timers.countdown()
    val haptic = LocalHapticFeedback.current
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    LaunchedEffect(Timers.cdRunning && left == 0L) {
        if (Timers.cdRunning && left == 0L) {
            Timers.cdRunning = false
            Timers.cdPaused = 0
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            repeat(3) { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300); delay(500) }
        }
    }
    Box(Modifier.size(260.dp).padding(top = 16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(progress = { left.toFloat() / Timers.cdTotal.coerceAtLeast(1) }, modifier = Modifier.fillMaxSize(),
            strokeWidth = 14.dp, color = Accent.Move, trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Text(if (left == 0L) stringResource(R.string.time_up) else clockText(left + 999, tenths = false),
            style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(30_000L, 60_000L, 120_000L, 180_000L, 300_000L, 600_000L).forEach { ms ->
            FilterChip(Timers.cdTotal == ms, { Timers.cdSet(ms) },
                label = { Text(if (ms < 60_000) stringResource(R.string.seconds_n, (ms / 1000).toInt()) else stringResource(R.string.minutes_n, (ms / 60_000).toInt())) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { Timers.cdAdd(-10_000) }) { Text("−10 s") }
        OutlinedButton(onClick = { Timers.cdAdd(10_000) }) { Text("+10 s") }
        OutlinedButton(onClick = { Timers.cdAdd(60_000) }) { Text("+1 min") }
    }
    BigButton(stringResource(if (Timers.cdRunning) R.string.pause else if (left in 1 until Timers.cdTotal) R.string.resume else R.string.start),
        { if (left == 0L) Timers.cdReset(); Timers.cdToggle() })
    if (!Timers.cdRunning && left != Timers.cdTotal) OutlinedButton(onClick = { Timers.cdReset() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset)) }
}
