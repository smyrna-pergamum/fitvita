package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.domain.Catalog
import io.github.smyrna_pergamum.exercisetracker.domain.Group
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val FLOWS = listOf(
    R.string.flow_morning to listOf("yoga_mountain", "yoga_tree", "yoga_warrior", "yoga_triangle", "yoga_downward_dog"),
    R.string.flow_evening to listOf("yoga_child", "yoga_cobra", "yoga_forward_bend", "yoga_bridge", "yoga_savasana"),
    R.string.flow_pilates to listOf("pilates_hundred", "pilates_roll_up", "pilates_single_leg", "pilates_leg_circle", "pilates_swimming", "pilates_teaser"),
)

/** Yoga flows, single poses and a guided breathing exercise. */
@Composable
fun YogaScreen(app: io.github.smyrna_pergamum.exercisetracker.App, repo: Repository, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val poses = Catalog.all.filter { it.group == Group.YOGA }
    val pilates = Catalog.all.filter { it.group == Group.PILATES }
    val flowNames = FLOWS.map { stringResource(it.first) }
    LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.yoga_pilates), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SettingsAction(nav)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            GradientCard(Accent.mind()) {
                Text(stringResource(R.string.breath_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.breath_hint))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5).forEach { m ->
                        Surface(onClick = { nav.navigate("breathe/$m") }, shape = CircleShape, color = Color.White, contentColor = Accent.Mind) {
                            Text(stringResource(R.string.minutes_n, m), Modifier.padding(horizontal = 18.dp, vertical = 10.dp), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { NatureSounds(app) }
        item(span = { GridItemSpan(maxLineSpan) }) { MeditationMusic(app) }
        FLOWS.forEachIndexed { i, (_, ids) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(onClick = { nav.navigate("preview/flow:$i") }, shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(flowNames[i], style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.flow_hint, ids.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            ids.mapNotNull { Catalog.get(it) }.forEach { e ->
                                Box(Modifier.weight(1f).aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).background(groupTint(e.group))) {
                                    ExerciseImage(e, Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.poses), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(poses, key = { it.id }) { e ->
            Card(onClick = { nav.navigate("exercise/${e.id}") }, shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp)).background(groupTint(e.group))) {
                        ExerciseImage(e, Modifier.fillMaxSize())
                    }
                    Text(stringResource(e.title), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(stringResource(R.string.group_pilates), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        items(pilates, key = { it.id }) { e ->
            Card(onClick = { nav.navigate("exercise/${e.id}") }, shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(8.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp)).background(groupTint(e.group))) {
                        ExerciseImage(e, Modifier.fillMaxSize())
                    }
                    Text(stringResource(e.title), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
        }
    }
}

/** Guided breathing: 4 s in, 6 s out, with a growing and shrinking circle. Counts as a mind session. */
@Composable
fun BreatheScreen(app: io.github.smyrna_pergamum.exercisetracker.App, repo: Repository, nav: NavHostController, minutes: Int) {
    val total = minutes * 60
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(0.55f) }
    var inhale by remember { mutableStateOf(true) }
    var left by remember { mutableIntStateOf(total) }
    var finished by remember { mutableStateOf(false) }
    val started = remember { System.currentTimeMillis() }
    // Soft music behind the exercise; whatever played before is replaced while breathing.
    var music by rememberSaveable { mutableStateOf(true) }
    val trackName = stringResource(R.string.breath_title)
    androidx.compose.runtime.DisposableEffect(music) {
        if (music) app.player.playTrack(io.github.smyrna_pergamum.exercisetracker.audio.Tracks.breathing, trackName)
        onDispose { if (app.player.now.value?.key == "track:${io.github.smyrna_pergamum.exercisetracker.audio.Tracks.breathing.res}") app.player.stop() }
    }

    LaunchedEffect(Unit) {
        while (!finished) {
            inhale = true
            scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing))
            inhale = false
            scale.animateTo(0.55f, tween(6000, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(Unit) {
        while (left > 0) { delay(1000); left-- }
        finished = true
        repo.addMindful(started, total)
    }
    fun stop() {
        val done = ((System.currentTimeMillis() - started) / 1000).toInt()
        if (!finished && done >= 30) scope.launch { repo.addMindful(started, done) }
        nav.popBackStack()
    }

    Box(Modifier.fillMaxSize().background(Accent.mind()), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(260.dp).scale(scale.value).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.size(170.dp).scale(scale.value).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)))
                Text(
                    stringResource(if (finished) R.string.breath_done else if (inhale) R.string.breath_in else R.string.breath_out),
                    color = Accent.Mind, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Text(formatDuration(left * 1000L), color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            if (finished) Text(stringResource(R.string.breath_xp), color = Color.White, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.with_music), color = Color.White)
                androidx.compose.material3.Switch(music, { music = it }, modifier = Modifier.padding(start = 8.dp))
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
