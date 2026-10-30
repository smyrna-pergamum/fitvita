package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Timer
import io.github.smyrna_pergamum.exercisetracker.domain.Group
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.data.SetValues
import io.github.smyrna_pergamum.exercisetracker.data.Settings
import io.github.smyrna_pergamum.exercisetracker.data.Workout
import io.github.smyrna_pergamum.exercisetracker.data.WorkoutItem
import io.github.smyrna_pergamum.exercisetracker.domain.Catalog
import io.github.smyrna_pergamum.exercisetracker.domain.Measure
import io.github.smyrna_pergamum.exercisetracker.domain.RestTimer
import io.github.smyrna_pergamum.exercisetracker.domain.Units
import io.github.smyrna_pergamum.exercisetracker.domain.WeightUnit
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ready-made routines so a first workout needs no setup. */
internal val STARTERS = listOf(
    R.string.starter_full_body to listOf("squat", "knee_push_up", "glute_bridge", "plank", "bird_dog", "jumping_jack"),
    R.string.starter_cardio to listOf("jumping_jack", "high_knees", "jump_squat", "mountain_climber", "side_lunge"),
    R.string.starter_dumbbell to listOf("goblet_squat", "dumbbell_rdl", "bent_over_row", "shoulder_press", "biceps_curl", "triceps_extension"),
    R.string.starter_glutes to listOf("glute_bridge", "donkey_kick", "sumo_squat", "single_leg_bridge", "lunge"),
    R.string.starter_legs to listOf("squat", "lunge", "side_lunge", "jump_squat", "calf_raise", "wall_sit"),
    R.string.starter_core to listOf("crunch", "bicycle_crunch", "russian_twist", "flutter_kick", "plank", "side_plank"),
)

@Composable
fun WorkoutTab(app: App, repo: Repository, settings: Settings, nav: NavHostController) {
    val active by repo.activeWorkout().collectAsState(initial = null)
    val a = active
    if (a == null) StartScreen(repo, nav) else ActiveWorkout(app, repo, settings, nav, a)
}

@Composable
private fun StartScreen(repo: Repository, nav: NavHostController) {
    val routines by repo.routines().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val emptyTitle = stringResource(R.string.workout)
    var deleting by remember { mutableStateOf<String?>(null) }
    val starterNames = STARTERS.map { stringResource(it.first) }
    val flowNames = FLOWS.map { stringResource(it.first) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Timers.swRunning || Timers.cdRunning) { while (Timers.swRunning || Timers.cdRunning) { delay(500); tick++ } }
    check(tick >= 0) // read so running timers update on their cards

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(R.string.tab_workout)) {
            IconButton(onClick = { nav.navigate("radio?back=true") }) { Icon(Icons.Filled.Radio, stringResource(R.string.radio)) }
            IconButton(onClick = { nav.navigate("exercises") }) { Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.library)) }
            SettingsAction(nav)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GradientCard(Accent.move(), Modifier.fillMaxWidth(), onClick = { scope.launch { repo.startWorkout(emptyTitle, emptyList()) } }) {
                Icon(Icons.Filled.FitnessCenter, null, Modifier.size(32.dp))
                Text(stringResource(R.string.start_own), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.start_empty_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientCard(Accent.water(), Modifier.weight(1f), onClick = { nav.navigate("timer/0") }) {
                    Icon(Icons.Filled.Timer, null, Modifier.size(28.dp))
                    Text(stringResource(R.string.stopwatch), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (Timers.swRunning || Timers.stopwatch() > 0) Text(clockText(Timers.stopwatch(), tenths = false))
                }
                GradientCard(Accent.body(), Modifier.weight(1f), onClick = { nav.navigate("timer/1") }) {
                    Icon(Icons.Filled.HourglassBottom, null, Modifier.size(28.dp))
                    Text(stringResource(R.string.countdown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (Timers.cdRunning) Text(clockText(Timers.countdown() + 999, tenths = false))
                }
            }
            if (routines.isNotEmpty()) {
                Text(stringResource(R.string.my_routines), style = MaterialTheme.typography.titleMedium)
                routines.forEach { r ->
                    RoutineCard(r.name, r.exerciseIds, onStart = { nav.navigate("preview/routine:${r.id}") }, onDelete = { deleting = r.id })
                }
            }
            Text(stringResource(R.string.ready_routines), style = MaterialTheme.typography.titleMedium)
            STARTERS.forEachIndexed { i, (_, ids) ->
                RoutineCard(starterNames[i], ids, onStart = { nav.navigate("preview/starter:$i") })
            }
            Text(stringResource(R.string.yoga_pilates), style = MaterialTheme.typography.titleMedium)
            FLOWS.forEachIndexed { i, (_, ids) ->
                RoutineCard(flowNames[i], ids, onStart = { nav.navigate("preview/flow:$i") })
            }
            PoseRow(R.string.group_yoga, Catalog.all.filter { it.group == Group.YOGA }, nav)
            PoseRow(R.string.group_pilates, Catalog.all.filter { it.group == Group.PILATES }, nav)
            Text(stringResource(R.string.routine_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    deleting?.let { id ->
        Confirm(stringResource(R.string.delete_routine), stringResource(R.string.delete_routine_text), stringResource(R.string.delete),
            { scope.launch { repo.deleteRoutine(id) } }) { deleting = null }
    }
}

/** A horizontal strip of exercise pictures; tap one for its steps. */
@Composable
private fun PoseRow(title: Int, list: List<io.github.smyrna_pergamum.exercisetracker.domain.Exercise>, nav: NavHostController) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        list.forEach { e ->
            Card(onClick = { nav.navigate("exercise/${e.id}") }, shape = RoundedCornerShape(20.dp), modifier = Modifier.width(132.dp)) {
                Column(Modifier.padding(6.dp)) {
                    Box(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(16.dp)).background(groupTint(e.group))) {
                        ExerciseImage(e, Modifier.fillMaxSize())
                    }
                    Text(stringResource(e.title), style = MaterialTheme.typography.labelLarge, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(name: String, ids: List<String>, onStart: () -> Unit, onDelete: (() -> Unit)? = null) {
    Card(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    ids.take(5).mapNotNull { Catalog.get(it) }.forEach { e ->
                        Box(Modifier.size(width = 52.dp, height = 39.dp).clip(RoundedCornerShape(10.dp)).background(groupTint(e.group))) {
                            ExerciseImage(e, Modifier.fillMaxSize())
                        }
                    }
                }
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
    }
}

@Composable
private fun ActiveWorkout(app: App, repo: Repository, settings: Settings, nav: NavHostController, w: Workout) {
    val logs by repo.logs(w.id).collectAsState(initial = emptyList())
    val done = logs.associateBy { it.itemKey to it.setOrder }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val fields = remember { mutableStateMapOf<String, Pair<String, String>>() }
    var finish by remember { mutableStateOf(false) }
    var cancel by remember { mutableStateOf(false) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(500); tick++ } }
    check(tick >= 0) // read so time and rest recompose on every tick

    val restLeft = w.rest?.let { RestTimer.remaining(it, app.clock) }
    LaunchedEffect(restLeft == 0L) { if (restLeft == 0L) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(Modifier.fillMaxSize()) {
        Bar(w.title) {
            IconButton(onClick = { nav.navigate("radio?back=true") }) { Icon(Icons.Filled.Radio, stringResource(R.string.radio)) }
            TextButton(onClick = { nav.navigate("guided/${w.id}") }) { Text(stringResource(R.string.guided)) }
            TextButton(onClick = { cancel = true }) { Text(stringResource(R.string.cancel)) }
        }
        Text(
            stringResource(R.string.elapsed, formatDuration(app.clock.wall() - w.startedAt)) + " · " + stringResource(R.string.sets_done, logs.size),
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Box(Modifier.height(4.dp)) }
            if (w.items.isEmpty()) item { Empty(stringResource(R.string.workout_empty)) }
            items(w.items, key = { it.key }) { item ->
                val last by produceState(emptyMap<Int, SetValues>(), item.exerciseId) { value = repo.lastValues(item.exerciseId) }
                ItemCard(
                    item, settings.unit, done.mapValues { repo.values(it.value) }, last, fields,
                    onDone = { order, v -> scope.launch { repo.completeSet(w.id, item, order, v, settings.restSec) } },
                    onUndo = { order -> scope.launch { repo.undoSet(w.id, item.key, order) } },
                    onAddSet = { scope.launch { repo.addSet(w.id, item.key) } },
                    onRemove = { scope.launch { repo.removeItem(w.id, item.key) } },
                    onOpen = { nav.navigate("exercise/${item.exerciseId}") },
                )
            }
            item {
                OutlinedButton(onClick = { nav.navigate("exercises?pick=true") }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_exercise)) }
            }
            item { BigButton(stringResource(R.string.finish), { finish = true }, Modifier.padding(bottom = 16.dp)) }
        }
        if (restLeft != null) RestBar(restLeft, w.rest!!.totalMs,
            onAdd = { scope.launch { repo.setRest(w.id) { it?.let { r -> RestTimer.add(r, 15_000, app.clock) } } } },
            onSkip = { scope.launch { repo.setRest(w.id) { null } } })
    }

    if (cancel) Confirm(stringResource(R.string.cancel_workout), stringResource(R.string.cancel_workout_text), stringResource(R.string.delete),
        { scope.launch { repo.deleteWorkout(w.id) } }) { cancel = false }
    if (finish) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { finish = false },
            title = { Text(stringResource(R.string.finish_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.finish_text, logs.size))
                    OutlinedTextField(name, { name = it.take(60) }, label = { Text(stringResource(R.string.save_as_routine)) }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    finish = false
                    scope.launch { repo.finishWorkout(w.id, name); nav.navigate("done/${w.id}") }
                }) { Text(stringResource(R.string.finish)) }
            },
            dismissButton = { TextButton(onClick = { finish = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun RestBar(leftMs: Long, totalMs: Long, onAdd: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (leftMs > 0) stringResource(R.string.rest, formatDuration(leftMs + 999)) else stringResource(R.string.rest_over),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f),
                )
                if (leftMs > 0) TextButton(onClick = onAdd) { Text("+15 s") }
                TextButton(onClick = onSkip) { Text(stringResource(if (leftMs > 0) R.string.skip else R.string.close)) }
            }
            if (leftMs > 0) LinearProgressIndicator(progress = { 1f - leftMs.toFloat() / totalMs.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ItemCard(
    item: WorkoutItem,
    unit: WeightUnit,
    done: Map<Pair<String, Int>, SetValues?>,
    last: Map<Int, SetValues>,
    fields: MutableMap<String, Pair<String, String>>,
    onDone: (Int, SetValues) -> Unit,
    onUndo: (Int) -> Unit,
    onAddSet: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val e = Catalog.get(item.exerciseId) ?: return
    val time = e.measure == Measure.TIME
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(onClick = onOpen, colors = CardDefaults.cardColors(containerColor = groupTint(e.group))) {
                    ExerciseImage(e, Modifier.size(width = 80.dp, height = 60.dp))
                }
                Text(stringResource(e.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 12.dp))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, stringResource(R.string.remove)) }
            }
            for (order in 0 until item.sets) {
                val key = "${item.key}#$order"
                val performed = done[item.key to order]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("${order + 1}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
                    if (performed != null) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        Text(valuesText(performed, unit), modifier = Modifier.weight(1f).padding(start = 8.dp))
                        IconButton(onClick = { onUndo(order) }) { Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo)) }
                    } else {
                        val prefill = last[order] ?: last.values.lastOrNull()
                        val initial = (if (time) (prefill?.seconds ?: 30) else (prefill?.reps ?: 10)).toString() to
                            (Units.format(prefill?.loadKg, unit).orEmpty())
                        val (main, load) = fields[key] ?: initial
                        NumberField(main, { fields[key] = it to load }, stringResource(if (time) R.string.seconds else R.string.reps), Modifier.weight(1f))
                        if (e.weighted) {
                            Box(Modifier.width(8.dp))
                            NumberField(load, { fields[key] = main to it }, stringResource(if (unit == WeightUnit.KG) R.string.kg else R.string.lb), Modifier.weight(1f), decimal = true)
                        }
                        Box(Modifier.width(8.dp))
                        val n = main.toIntOrNull()
                        val kg = (Units.parseLoad(load, unit, prefill?.loadKg) as? Units.ParseResult.Ok)?.kg
                        FilledIconButton(
                            enabled = n != null && n > 0 && Units.parseLoad(load, unit, prefill?.loadKg) is Units.ParseResult.Ok,
                            onClick = { onDone(order, if (time) SetValues(seconds = n) else SetValues(reps = n, loadKg = if (e.weighted) kg else null)) },
                            modifier = Modifier.size(52.dp),
                        ) { Icon(Icons.Filled.Check, stringResource(R.string.set_done_n, order + 1)) }
                    }
                }
            }
            TextButton(onClick = onAddSet) { Text(stringResource(R.string.add_set)) }
        }
    }
}

@Composable
fun valuesText(v: SetValues, unit: WeightUnit): String {
    val main = v.seconds?.let { formatDuration(it * 1000L) } ?: v.reps?.let { stringResource(R.string.reps_n, it) } ?: "–"
    val load = Units.format(v.loadKg, unit)?.let { " × $it ${stringResource(if (unit == WeightUnit.KG) R.string.kg else R.string.lb)}" } ?: ""
    return main + load
}

/** Summary of a finished workout. */
@Composable
fun WorkoutDetail(repo: Repository, settings: Settings, nav: NavHostController, id: String) {
    val w by repo.workout(id).collectAsState(initial = null)
    val logs by repo.logs(id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var delete by remember { mutableStateOf(false) }
    val workout = w ?: return
    Column(Modifier.fillMaxSize()) {
        Bar(workout.title, onBack = { if (nav.previousBackStackEntry?.destination?.route == "history") nav.popBackStack() else goHome(nav) }) {
            IconButton(onClick = { delete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatDateTime(workout.startedAt) + " · " + formatDuration(workout.durationMs ?: 0), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.sets_done, logs.size), style = MaterialTheme.typography.titleMedium)
            workout.items.forEach { item ->
                val e = Catalog.get(item.exerciseId) ?: return@forEach
                val mine = logs.filter { it.itemKey == item.key }.sortedBy { it.setOrder }
                if (mine.isEmpty()) return@forEach
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExerciseImage(e, Modifier.size(width = 72.dp, height = 54.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(e.title), style = MaterialTheme.typography.titleMedium)
                            mine.forEach { l -> repo.values(l)?.let { Text("${l.setOrder + 1}. " + valuesText(it, settings.unit)) } }
                        }
                    }
                }
            }
            if (nav.previousBackStackEntry?.destination?.route != "history") BigButton(stringResource(R.string.back_home), { goHome(nav) }, Modifier.padding(top = 8.dp))
        }
    }
    if (delete) Confirm(stringResource(R.string.delete_workout), stringResource(R.string.delete_workout_text), stringResource(R.string.delete),
        { scope.launch { repo.deleteWorkout(id); nav.popBackStack() } }) { delete = false }
}

/** Back to the Activity home with the tab bar, dropping finished workout screens from the stack. */
fun goHome(nav: NavHostController) {
    nav.navigate("activity") {
        popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}
