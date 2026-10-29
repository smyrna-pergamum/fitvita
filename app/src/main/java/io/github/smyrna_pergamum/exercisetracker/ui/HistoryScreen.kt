package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

private data class Row(val time: Long, val icon: ImageVector, val title: String, val detail: String, val route: String)

/** Workouts and activities in one list, newest first, with this week's totals on top. */
@Composable
fun HistoryScreen(repo: Repository, nav: NavHostController) {
    val workouts by repo.finishedWorkouts().collectAsState(initial = emptyList())
    val activities by repo.finishedActivities().collectAsState(initial = emptyList())
    val counts by repo.setCounts().collectAsState(initial = emptyList())
    val sets = counts.associate { it.workoutId to it.n }
    val setsLabel = stringResource(R.string.sets_count)

    val rows = workouts.map { w ->
        Row(w.startedAt, Icons.Filled.FitnessCenter, w.title, formatDateTime(w.startedAt) + " · " + formatDuration(w.durationMs ?: 0) + " · " + setsLabel.format(sets[w.id] ?: 0), "done/${w.id}")
    } + activities.map { a ->
        Row(a.startedAt, activityIcon(a.type), stringResource(activityLabel(a.type)), formatDateTime(a.startedAt) + " · " + km(a.distanceM) + " · " + formatDuration(a.durationMs), "activity/${a.id}")
    }
    val weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val weekWorkouts = workouts.count { it.startedAt >= weekStart }
    val weekKm = activities.filter { it.startedAt >= weekStart }.sumOf { it.distanceM }
    val mondayIso = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
    val stepDays by remember(mondayIso) { repo.stepDays(mondayIso) }.collectAsState(initial = emptyList())
    val weekSteps = stepDays.sumOf { it.steps.toLong() }

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(R.string.tab_history), onBack = { nav.popBackStack() })
        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.this_week), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.week_summary, weekWorkouts, km(weekKm)), style = MaterialTheme.typography.titleLarge)
                if (weekSteps > 0) Text(stringResource(R.string.week_steps, formatSteps(weekSteps)), style = MaterialTheme.typography.bodyLarge)
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            if (rows.isEmpty()) item { Empty(stringResource(R.string.no_history)) }
            items(rows.sortedByDescending { it.time }, key = { it.route }) { r ->
                ListItem(
                    headlineContent = { Text(r.title) },
                    supportingContent = { Text(r.detail) },
                    leadingContent = { Icon(r.icon, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { nav.navigate(r.route) },
                )
                HorizontalDivider()
            }
        }
    }
}
