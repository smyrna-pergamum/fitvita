package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import io.github.smyrna_pergamum.exercisetracker.audio.Tracks
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.domain.Breath
import io.github.smyrna_pergamum.exercisetracker.domain.Calm
import io.github.smyrna_pergamum.exercisetracker.domain.Calms
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Yoga and pilates flows, shown on the Workout tab. */
internal val FLOWS = listOf(
    R.string.flow_morning to listOf("yoga_mountain", "yoga_tree", "yoga_warrior", "yoga_triangle", "yoga_downward_dog"),
    R.string.flow_evening to listOf("yoga_child", "yoga_cobra", "yoga_forward_bend", "yoga_bridge", "yoga_savasana"),
    R.string.flow_pilates to listOf("pilates_hundred", "pilates_roll_up", "pilates_single_leg", "pilates_leg_circle", "pilates_swimming", "pilates_teaser"),
    R.string.flow_pilates_core to listOf("pilates_hundred", "pilates_double_leg", "pilates_scissors", "pilates_rolling", "pilates_teaser"),
    R.string.flow_pilates_body to listOf("pilates_roll_up", "pilates_spine_stretch", "pilates_shoulder_bridge", "pilates_leg_pull", "pilates_swimming", "pilates_leg_circle"),
)

/** Inner peace: meditation routines, breathing and calming exercises, nature sounds and music. */
@Composable
fun YogaScreen(app: App, nav: NavHostController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.inner_peace), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SettingsAction(nav)
        }
        Text(stringResource(R.string.meditation_routines), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Calms.routines.forEachIndexed { i, r ->
            val sound = r.sound?.let { Tracks.nature[it] }
            val total = r.steps.sumOf { (id, m) -> Calms.get(id)?.seconds(m) ?: 0 }
            val steps = r.steps.mapNotNull { Calms.get(it.first) }.map { stringResource(it.title) }.joinToString(" → ")
            Card(onClick = { nav.navigate("calm/routine:$i") }, shape = RoundedCornerShape(28.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (sound != null) Image(painterResource(sound.image), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 96.dp, height = 72.dp).clip(RoundedCornerShape(18.dp)))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(stringResource(r.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(r.hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(steps + " · " + stringResource(R.string.minutes_n, (total + 59) / 60),
                            style = MaterialTheme.typography.labelMedium, color = Accent.Mind, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Text(stringResource(R.string.calm_exercises), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Calms.all.forEachIndexed { i, c -> CalmCard(c, if (i % 2 == 0) Accent.mind() else Accent.water(), nav) }
        NatureSounds(app)
        MeditationMusic(app)
    }
}

@Composable
private fun CalmCard(c: Calm, brush: androidx.compose.ui.graphics.Brush, nav: NavHostController) {
    GradientCard(brush) {
        Text(stringResource(c.title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(c.hint))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (c.breathing) listOf(1, 3, 5).forEach { m -> Pill(stringResource(R.string.minutes_n, m)) { nav.navigate("calm/ex:${c.id}:$m") } }
            else Pill(stringResource(R.string.start_n_min, (c.seconds(0) + 59) / 60)) { nav.navigate("calm/ex:${c.id}:0") }
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = Color.White, contentColor = Accent.Mind) {
        Text(text, Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
    }
}

private data class Segment(val calm: Calm, val seconds: Int)

/**
 * A guided calming session: one exercise ("ex:<id>:<minutes>") or a meditation routine
 * ("routine:<n>"). Breathing patterns animate a circle, prompt series show one prompt at a time.
 * Counts as a mind session.
 */
@Composable
fun CalmSession(app: App, repo: Repository, nav: NavHostController, spec: String) {
    val parts = spec.split(':')
    val routine = if (parts[0] == "routine") Calms.routines.getOrNull(parts.getOrNull(1)?.toIntOrNull() ?: -1) else null
    val segments = remember(spec) {
        if (routine != null) routine.steps.mapNotNull { (id, m) -> Calms.get(id)?.let { Segment(it, it.seconds(m)) } }
        else listOfNotNull(Calms.get(parts.getOrNull(1).orEmpty())?.let { c -> Segment(c, c.seconds(parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 30) ?: 3)) })
    }
    if (segments.isEmpty()) {
        LaunchedEffect(Unit) { nav.popBackStack() }
        return
    }
    val total = segments.sumOf { it.seconds }
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(0.55f) }
    var elapsed by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    val started = rememberSaveable { System.currentTimeMillis() }

    // Where we are: segment index and seconds into it.
    var seg = 0
    var into = elapsed.coerceAtMost(total - 1)
    while (seg < segments.lastIndex && into >= segments[seg].seconds) { into -= segments[seg].seconds; seg++ }
    val current = segments[seg]

    // Soft sound behind the session: the routine's nature sound, else the breathing music.
    var music by rememberSaveable { mutableStateOf(true) }
    val nature = routine?.sound?.let { Tracks.nature[it] }
    val track = nature?.track ?: Tracks.breathing
    val trackName = stringResource(nature?.title ?: R.string.breath_title)
    DisposableEffect(music) {
        if (music) app.player.playTrack(track, trackName)
        onDispose { if (app.player.now.value?.key == "track:${track.res}") app.player.stop() }
    }

    LaunchedEffect(Unit) {
        while (elapsed < total) { delay(1000); elapsed++ }
        if (!finished) { finished = true; repo.addMindful(started, total) }
    }
    // The circle follows the breathing phase, or pulses slowly during prompts.
    val phase = if (current.calm.breathing && !finished) current.calm.phaseAt(into) else null
    val phaseStart = phase?.let { into - it.second }
    LaunchedEffect(seg, phaseStart, finished) {
        when {
            finished -> scale.animateTo(0.8f, tween(1500))
            phase == null -> while (true) {
                scale.animateTo(0.9f, tween(5000, easing = FastOutSlowInEasing))
                scale.animateTo(0.65f, tween(5000, easing = FastOutSlowInEasing))
            }
            phase.first.breath == Breath.IN -> scale.animateTo(1f, tween((phase.first.seconds - phase.second) * 1000, easing = FastOutSlowInEasing))
            phase.first.breath == Breath.OUT -> scale.animateTo(0.55f, tween((phase.first.seconds - phase.second) * 1000, easing = FastOutSlowInEasing))
            else -> {}
        }
    }
    fun stop() {
        val done = ((System.currentTimeMillis() - started) / 1000).toInt()
        if (!finished && done >= 30) scope.launch { repo.addMindful(started, done) }
        nav.popBackStack()
    }

    Box(Modifier.fillMaxSize().background(Accent.mind()), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(
                (if (segments.size > 1) "${seg + 1}/${segments.size} · " else "") + stringResource(current.calm.title),
                color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(240.dp).scale(scale.value).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.size(160.dp).scale(scale.value).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)))
                val label = when {
                    finished -> stringResource(R.string.breath_done)
                    phase != null -> stringResource(phase.first.breath.text) + "\n" + (phase.first.seconds - phase.second)
                    else -> null
                }
                if (label != null) Text(label, color = Accent.Mind, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            if (!current.calm.breathing && !finished) {
                val prompt = current.calm.prompts[(into / current.calm.promptSec).coerceAtMost(current.calm.prompts.lastIndex)]
                Text(stringResource(prompt), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    modifier = Modifier.heightIn(min = 96.dp).semantics { liveRegion = LiveRegionMode.Polite })
            }
            Text(formatDuration((total - elapsed).coerceAtLeast(0) * 1000L), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            if (finished) Text(stringResource(R.string.breath_xp), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.with_music), color = Color.White)
                Switch(music, { music = it }, modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { if (finished) nav.popBackStack() else stop() }) {
                Text(stringResource(if (finished) R.string.close else R.string.finish), color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            if (finished) TextButton(onClick = { goHome(nav) }) {
                Text(stringResource(R.string.back_home), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (finished) Confetti(Modifier.fillMaxSize()) {}
    }
}
