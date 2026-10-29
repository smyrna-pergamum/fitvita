package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.domain.Catalog
import io.github.smyrna_pergamum.exercisetracker.domain.Exercise
import io.github.smyrna_pergamum.exercisetracker.domain.Group
import io.github.smyrna_pergamum.exercisetracker.domain.Measure
import kotlinx.coroutines.launch

@Composable
fun SettingsAction(nav: NavHostController) {
    IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
}

/** Picture grid with one row of group filters. In [pick] mode a tap adds the exercise to the workout. */
@Composable
fun ExercisesScreen(repo: Repository, nav: NavHostController, pick: Boolean) {
    var group by rememberSaveable { mutableStateOf<Group?>(null) }
    val scope = rememberCoroutineScope()
    val emptyTitle = stringResource(R.string.workout)
    val list = Catalog.all.filter { group == null || it.group == group }

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(if (pick) R.string.add_exercise else R.string.library), onBack = { nav.popBackStack() })
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(group == null, { group = null }, label = { Text(stringResource(R.string.all)) })
            Group.entries.forEach { g -> FilterChip(group == g, { group = g }, label = { Text(stringResource(g.label)) }) }
        }
        LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { e ->
                ExerciseCard(e) {
                    if (pick) scope.launch { repo.addExercise(e.id, emptyTitle); nav.popBackStack() }
                    else nav.navigate("exercise/${e.id}")
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(e: Exercise, onClick: () -> Unit) {
    Card(onClick = onClick) {
        Column(Modifier.padding(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(18.dp)).background(groupTint(e.group))) {
                ExerciseImage(e, Modifier.fillMaxSize())
            }
            Text(stringResource(e.title), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
fun ExerciseDetail(repo: Repository, nav: NavHostController, id: String) {
    val e = Catalog.get(id) ?: return
    val scope = rememberCoroutineScope()
    val emptyTitle = stringResource(R.string.workout)
    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(e.title), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(28.dp)).background(groupTint(e.group))) {
                ExerciseImage(e, Modifier.fillMaxSize().padding(8.dp), animate = true)
            }
            Text(
                stringResource(e.group.label) + " · " + stringResource(if (e.measure == Measure.TIME) R.string.by_time else R.string.by_reps) +
                    if (e.weighted) " · " + stringResource(R.string.with_dumbbells) else "",
                color = MaterialTheme.colorScheme.primary,
            )
            stringResource(e.steps).split('\n').forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodyLarge) }
            BigButton(stringResource(R.string.add_to_workout), {
                scope.launch { repo.addExercise(e.id, emptyTitle); nav.navigate("workout") { popUpTo("workout") } }
            }, Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.health_short), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
