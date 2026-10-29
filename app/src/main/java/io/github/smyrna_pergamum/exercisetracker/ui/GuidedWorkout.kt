package io.github.smyrna_pergamum.exercisetracker.ui

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.data.SetValues
import io.github.smyrna_pergamum.exercisetracker.data.Settings
import io.github.smyrna_pergamum.exercisetracker.data.Status
import io.github.smyrna_pergamum.exercisetracker.data.WorkoutItem
import io.github.smyrna_pergamum.exercisetracker.domain.Catalog
import io.github.smyrna_pergamum.exercisetracker.domain.Exercise
import io.github.smyrna_pergamum.exercisetracker.domain.Measure
import io.github.smyrna_pergamum.exercisetracker.domain.RestTimer
import io.github.smyrna_pergamum.exercisetracker.domain.Units
import io.github.smyrna_pergamum.exercisetracker.domain.WeightUnit
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Name and exercises behind a preview link: "routine:<id>", "starter:<n>" or "flow:<n>". */
@Composable
private fun resolve(repo: Repository, source: String): Pair<String, List<String>>? {
    val (kind, key) = source.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    return when (kind) {
        "starter" -> STARTERS.getOrNull(key.toIntOrNull() ?: -1)?.let { stringResource(it.first) to it.second }
        "flow" -> FLOWS.getOrNull(key.toIntOrNull() ?: -1)?.let { stringResource(it.first) to it.second }
        "routine" -> {
            val r by produceState<Pair<String, List<String>>?>(null, key) { value = repo.routines().first().firstOrNull { it.id == key }?.let { it.name to it.exerciseIds } }
            r
        }
        else -> null
    }
}

/** Preview of a routine: every exercise with its picture and target, then guided or free start. */
@Composable
fun WorkoutPreview(repo: Repository, nav: NavHostController, source: String) {
    val (name, ids) = resolve(repo, source) ?: run { Bar("", onBack = { nav.popBackStack() }); return }
    val scope = rememberCoroutineScope()
    val list = ids.mapNotNull { Catalog.get(it) }
    val minutes = (list.sumOf { e -> 3 * ((if (e.measure == Measure.TIME) 40 else 40) + 60) } / 60).coerceAtLeast(1)

    Column(Modifier.fillMaxSize()) {
        Bar(name, onBack = { nav.popBackStack() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.preview_summary, list.size, minutes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            list.forEachIndexed { i, e ->
                Card(shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(110.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(groupTint(e.group))) {
                            ExerciseImage(e, Modifier.fillMaxSize(), animate = true)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text("${i + 1}. " + stringResource(e.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(targetLine(e), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BigButton(stringResource(R.string.start_guided), {
                scope.launch { val id = repo.startWorkout(name, ids); nav.navigate("guided/$id") { popUpTo("workout") } }
            })
            OutlinedButton(onClick = { scope.launch { repo.startWorkout(name, ids); nav.navigate("workout") { popUpTo("workout") { inclusive = true } } } },
                modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.start_free)) }
        }
    }
}

@Composable
private fun targetLine(e: Exercise): String = when {
    Catalog.isYoga(e.id) -> "3 × " + stringResource(R.string.seconds_n, 45)
    e.measure == Measure.TIME -> "3 × " + stringResource(R.string.seconds_n, 30)
    else -> "3 × " + stringResource(R.string.reps_n, 10)
}

private data class Step(val item: WorkoutItem, val exercise: Exercise, val order: Int)

/**
 * Guided mode: one exercise at a time with a big picture. Timed exercises count down by
 * themselves; rep exercises are confirmed with one button. A rest countdown runs between sets.
 */
@Composable
fun GuidedWorkout(app: App, repo: Repository, settings: Settings, nav: NavHostController, id: String) {
    val w = repo.workout(id).collectAsState(initial = null).value ?: return
    val logs by repo.logs(id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var skipped by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(200); tick++ } }
    check(tick >= 0)
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 70) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }

    if (w.status == Status.FINISHED) {
        LaunchedEffect(Unit) { nav.navigate("done/$id") { popUpTo("workout") } }
        return
    }
    val done = logs.map { it.itemKey to it.setOrder }.toSet()
    val steps = w.items.flatMap { item -> Catalog.get(item.exerciseId)?.let { e -> (0 until item.sets).map { Step(item, e, it) } }.orEmpty() }
    val open = steps.filter { (it.item.key to it.order) !in done && it.item.key !in skipped }
    val current = open.firstOrNull()
    val restLeft = w.rest?.let { RestTimer.remaining(it, app.clock) }?.takeIf { it > 0 }
    val progress = if (steps.isEmpty()) 0f else 1f - open.size.toFloat() / steps.size

    Column(Modifier.fillMaxSize()) {
        Bar(w.title, onBack = { nav.popBackStack() }) {
            // Music during any workout, not only on routes.
            IconButton(onClick = { nav.navigate("radio?back=true") }) { Icon(Icons.Filled.Radio, stringResource(R.string.radio)) }
            IconButton(onClick = { nav.navigate("workout") { popUpTo("workout") { inclusive = true } } }) {
                Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.list_view))
            }
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(8.dp).clip(RoundedCornerShape(4.dp)))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                current == null -> AllDone(onFinish = { scope.launch { repo.finishWorkout(id, null); nav.navigate("done/$id") { popUpTo("workout") } } })
                restLeft != null -> RestPanel(restLeft, w.rest!!.totalMs, current,
                    onAdd = { scope.launch { repo.setRest(id) { it?.let { r -> RestTimer.add(r, 15_000, app.clock) } } } },
                    onSkip = { scope.launch { repo.setRest(id) { null } } })
                else -> {
                    val itemIndex = w.items.indexOfFirst { it.key == current.item.key } + 1
                    Text(stringResource(R.string.exercise_of, itemIndex, w.items.size) + " · " + stringResource(R.string.set_of, current.order + 1, current.item.sets),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp)).background(groupTint(current.exercise.group))) {
                        ExerciseImage(current.exercise, Modifier.fillMaxSize().padding(8.dp), animate = true)
                    }
                    Text(stringResource(current.exercise.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    val last by produceState(emptyMap<Int, SetValues>(), current.exercise.id) { value = repo.lastValues(current.exercise.id) }
                    val prefill = last[current.order] ?: last.values.lastOrNull() ?: repo.defaultValues(current.exercise.id)
                    val restSec = settings.restSec
                    if (current.exercise.measure == Measure.TIME) {
                        Countdown(key = "${current.item.key}#${current.order}", seconds = prefill.seconds ?: 30, tone = tone) { secs ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { repo.completeSet(id, current.item, current.order, SetValues(seconds = secs), restSec) }
                        }
                    } else {
                        RepsPanel(current, prefill, settings.unit) { v ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { repo.completeSet(id, current.item, current.order, v, restSec) }
                        }
                    }
                    TextButton(onClick = { skipped = skipped + current.item.key }) { Text(stringResource(R.string.skip_exercise)) }
                    Text(stringResource(R.string.health_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun Countdown(key: String, seconds: Int, tone: ToneGenerator?, onDone: (Int) -> Unit) {
    var total by remember(key) { mutableIntStateOf(seconds) }
    var running by remember(key) { mutableStateOf(false) }
    var endAt by remember(key) { mutableLongStateOf(0L) }
    var left by remember(key) { mutableLongStateOf(seconds * 1000L) }
    var lastBeep by remember(key) { mutableIntStateOf(-1) }
    LaunchedEffect(key, running) {
        while (running) {
            left = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            val s = ((left + 999) / 1000).toInt()
            if (s in 1..3 && s != lastBeep) { lastBeep = s; tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120) }
            if (left == 0L) {
                running = false
                tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
                onDone(total)
                break
            }
            delay(100)
        }
    }
    val fill = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 16.dp.toPx()
            val s = Size(size.width - w, size.height - w)
            drawArc(track, 0f, 360f, false, Offset(w / 2, w / 2), s, style = Stroke(w))
            drawArc(fill, -90f, 360f * left / (total * 1000f), false, Offset(w / 2, w / 2), s, style = Stroke(w, cap = StrokeCap.Round))
        }
        Text(formatDuration(left + if (running) 999 else 0), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
    if (!running && left == total * 1000L) Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { total = (total - 5).coerceAtLeast(5); left = total * 1000L }) { Icon(Icons.Filled.Remove, "−5 s") }
        Text(stringResource(R.string.seconds_n, total), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { total = (total + 5).coerceAtMost(600); left = total * 1000L }) { Icon(Icons.Filled.Add, "+5 s") }
    }
    BigButton(stringResource(if (running) R.string.pause else if (left < total * 1000L) R.string.resume else R.string.start), {
        if (running) { running = false } else { endAt = SystemClock.elapsedRealtime() + left; running = true }
    })
    if (!running && left < total * 1000L) TextButton(onClick = { onDone(((total * 1000L - left) / 1000).toInt().coerceAtLeast(1)) }) {
        Text(stringResource(R.string.done_early))
    }
}

@Composable
private fun RepsPanel(step: Step, prefill: SetValues, unit: WeightUnit, onDone: (SetValues) -> Unit) {
    var reps by remember(step.item.key, step.order) { mutableIntStateOf(prefill.reps ?: 10) }
    var load by remember(step.item.key, step.order) { mutableStateOf(Units.format(prefill.loadKg, unit).orEmpty()) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        FilledIconButton(onClick = { reps = (reps - 1).coerceAtLeast(1) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Remove, "−1") }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$reps", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black)
            Text(stringResource(R.string.reps), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledIconButton(onClick = { reps = (reps + 1).coerceAtMost(999) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Filled.Add, "+1") }
    }
    if (step.exercise.weighted) NumberField(load, { load = it }, stringResource(if (unit == WeightUnit.KG) R.string.kg else R.string.lb), Modifier.width(160.dp), decimal = true)
    BigButton(stringResource(R.string.set_done_n, step.order + 1), {
        val kg = (Units.parseLoad(load, unit, prefill.loadKg) as? Units.ParseResult.Ok)?.kg
        onDone(SetValues(reps = reps, loadKg = if (step.exercise.weighted) kg else null))
    })
}

@Composable
private fun RestPanel(leftMs: Long, totalMs: Long, next: Step, onAdd: () -> Unit, onSkip: () -> Unit) {
    GradientCard(Accent.water(), Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.rest_now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(formatDuration(leftMs + 999), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Black,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        LinearProgressIndicator(progress = { 1f - leftMs.toFloat() / totalMs.coerceAtLeast(1) }, color = androidx.compose.ui.graphics.Color.White,
            trackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
        Row(Modifier.padding(top = 8.dp)) {
            TextButton(onClick = onAdd) { Text("+15 s", color = androidx.compose.ui.graphics.Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text(stringResource(R.string.skip), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Bold) }
        }
    }
    Text(stringResource(R.string.next_up), style = MaterialTheme.typography.titleMedium)
    Box(Modifier.fillMaxWidth(0.7f).aspectRatio(4f / 3f).clip(RoundedCornerShape(24.dp)).background(groupTint(next.exercise.group))) {
        ExerciseImage(next.exercise, Modifier.fillMaxSize())
    }
    Text(stringResource(next.exercise.title) + " · " + stringResource(R.string.set_of, next.order + 1, next.item.sets), fontWeight = FontWeight.Bold)
}

@Composable
private fun AllDone(onFinish: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(320.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("🎉", style = MaterialTheme.typography.displayLarge)
            Text(stringResource(R.string.all_done_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.all_done_text), textAlign = TextAlign.Center)
        }
        Confetti(Modifier.fillMaxSize()) {}
    }
    BigButton(stringResource(R.string.finish), onFinish)
}
