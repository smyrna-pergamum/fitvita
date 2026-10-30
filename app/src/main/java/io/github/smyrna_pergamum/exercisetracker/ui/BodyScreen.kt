package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.data.Settings
import io.github.smyrna_pergamum.exercisetracker.domain.BmiBand
import io.github.smyrna_pergamum.exercisetracker.domain.Body
import io.github.smyrna_pergamum.exercisetracker.domain.Units
import io.github.smyrna_pergamum.exercisetracker.domain.WeightUnit
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs

private fun bandColor(b: BmiBand) = when (b) {
    BmiBand.UNDER -> Accent.Water
    BmiBand.NORMAL -> Accent.Route
    BmiBand.OVER -> Accent.Steps
    BmiBand.OBESE -> Accent.Move
}

private fun bandLabel(b: BmiBand) = when (b) {
    BmiBand.UNDER -> R.string.bmi_under
    BmiBand.NORMAL -> R.string.bmi_normal
    BmiBand.OVER -> R.string.bmi_over
    BmiBand.OBESE -> R.string.bmi_obese
}

@Composable
private fun w(kg: Double, unit: WeightUnit) = (Units.format(kg, unit) ?: "") + " " + stringResource(if (unit == WeightUnit.KG) R.string.kg else R.string.lb)

/** Home card: latest weight, BMI and how far the goal is. */
@Composable
fun BodyCard(repo: Repository, settings: Settings, onOpen: () -> Unit) {
    val list by repo.weights().collectAsState(initial = emptyList())
    val now = list.lastOrNull()
    GradientCard(Accent.body(), Modifier.fillMaxWidth(), onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MonitorWeight, null, Modifier.size(32.dp))
            Text(stringResource(R.string.body_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp).weight(1f))
            Icon(Icons.Filled.Add, stringResource(R.string.weigh_in))
        }
        if (now == null) Text(stringResource(R.string.body_empty), modifier = Modifier.padding(top = 8.dp))
        else {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(w(now.kg, settings.unit), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                settings.heightCm?.let { h ->
                    val bmi = Body.bmi(now.kg, h)
                    Text("  BMI %.1f · %s".format(bmi, stringResource(bandLabel(Body.band(bmi)))), modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            settings.goalKg?.let { goal ->
                val left = Body.toGoal(now.kg, goal)
                LinearProgressIndicator(progress = { Body.progress(list.first().kg, now.kg, goal) }, color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp)))
                Text(if (abs(left) < 0.05) stringResource(R.string.goal_reached) else stringResource(R.string.to_goal, w(abs(left), settings.unit)),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Weight log with chart, BMI gauge, height and goal. */
@Composable
fun BodyScreen(app: App, repo: Repository, settings: Settings, nav: NavHostController) {
    val list by repo.weights().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var logging by remember { mutableStateOf(false) }
    var editHeight by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf(false) }
    val unit = settings.unit
    val now = list.lastOrNull()

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(R.string.body_title), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            GradientCard(Accent.body(), Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.current_weight), style = MaterialTheme.typography.titleMedium)
                Text(now?.let { w(it.kg, unit) } ?: "–", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
                if (list.size >= 2) {
                    val diff = list.last().kg - list.first().kg
                    Text(stringResource(R.string.since_start, (if (diff > 0) "+" else "−") + w(abs(diff), unit)))
                }
                Surface(onClick = { logging = true }, shape = CircleShape, color = Color.White, contentColor = Accent.Body,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.weigh_in_xp), fontWeight = FontWeight.Bold) }
                }
            }

            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("BMI", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val h = settings.heightCm
                    if (h == null || now == null) Text(stringResource(R.string.bmi_need_height), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else {
                        val bmi = Body.bmi(now.kg, h)
                        val band = Body.band(bmi)
                        Text("%.1f · %s".format(bmi, stringResource(bandLabel(band))), style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, color = bandColor(band))
                        BmiGauge(bmi)
                        val r = Body.normalRange(h)
                        Text(stringResource(R.string.bmi_range, w(r.start, unit), w(r.endInclusive, unit)), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(io.github.smyrna_pergamum.exercisetracker.data.Sex.FEMALE to R.string.female, io.github.smyrna_pergamum.exercisetracker.data.Sex.MALE to R.string.male).forEach { (sx, label) ->
                            androidx.compose.material3.FilterChip(settings.sex == sx, { scope.launch { app.settings.setSex(sx) } }, label = { Text(stringResource(label)) })
                        }
                    }
                    if (h != null && settings.sex != null) {
                        val female = settings.sex == io.github.smyrna_pergamum.exercisetracker.data.Sex.FEMALE
                        Text(stringResource(R.string.ideal_weight, stringResource(if (female) R.string.female else R.string.male), w(Math.round(Body.idealKg(h, female) * 10) / 10.0, unit)),
                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.bmi_sex_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { editHeight = true }) {
                        Text(h?.let { stringResource(R.string.height_value, it) } ?: stringResource(R.string.set_height))
                    }
                    Text(stringResource(R.string.bmi_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.weight_goal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    val goal = settings.goalKg
                    if (goal != null && now != null) {
                        val left = Body.toGoal(now.kg, goal)
                        Text(w(goal, unit), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Accent.Body)
                        LinearProgressIndicator(progress = { Body.progress(list.first().kg, now.kg, goal) }, color = Accent.Body,
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)))
                        Text(if (abs(left) < 0.05) stringResource(R.string.goal_reached) else stringResource(R.string.to_goal, w(abs(left), unit)))
                    }
                    TextButton(onClick = { editGoal = true }) { Text(stringResource(if (goal == null) R.string.set_goal else R.string.change_goal)) }
                }
            }

            if (list.size >= 2) Card(shape = RoundedCornerShape(28.dp)) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.weight_trend), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    WeightChart(list.takeLast(60).map { it.kg }, settings.goalKg, Modifier.fillMaxWidth().height(180.dp).padding(top = 12.dp))
                }
            }

            if (list.isNotEmpty()) {
                Text(stringResource(R.string.weigh_ins), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val fmt = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                list.asReversed().take(30).forEach { e ->
                    ListItem(
                        headlineContent = { Text(w(e.kg, unit), fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(e.date.format(fmt)) },
                        trailingContent = { IconButton(onClick = { scope.launch { repo.deleteWeight(e.date) } }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
    }

    if (logging) NumberDialog(stringResource(R.string.weigh_in), now?.let { Units.format(it.kg, unit) }.orEmpty(),
        stringResource(if (unit == WeightUnit.KG) R.string.kg else R.string.lb), decimal = true, onDone = { text ->
            (Units.parseLoad(text, unit, now?.kg) as? Units.ParseResult.Ok)?.kg?.takeIf { it in 20.0..400.0 }?.let { kg -> scope.launch { repo.logWeight(kg) } }
        }) { logging = false }
    if (editHeight) NumberDialog(stringResource(R.string.set_height), settings.heightCm?.toString().orEmpty(), "cm", decimal = false, onDone = { text ->
        scope.launch { app.settings.setHeight(text.toIntOrNull()?.takeIf { it in 80..250 }) }
    }) { editHeight = false }
    if (editGoal) NumberDialog(stringResource(R.string.weight_goal), settings.goalKg?.let { Units.format(it, unit) }.orEmpty(),
        stringResource(if (unit == WeightUnit.KG) R.string.kg else R.string.lb), decimal = true, onDone = { text ->
            val kg = (Units.parseLoad(text, unit, settings.goalKg) as? Units.ParseResult.Ok)?.kg?.takeIf { it in 20.0..400.0 }
            scope.launch { app.settings.setGoalKg(kg) }
        }) { editGoal = false }
}

@Composable
private fun NumberDialog(title: String, initial: String, label: String, decimal: Boolean, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { NumberField(text, { text = it }, label, decimal = decimal) },
        confirmButton = { TextButton(onClick = { onDismiss(); onDone(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Four coloured BMI bands from 15 to 40 with a marker at the current value. */
@Composable
private fun BmiGauge(bmi: Double) {
    val marker = MaterialTheme.colorScheme.onSurface
    val label = "BMI %.1f".format(bmi)
    Canvas(Modifier.fillMaxWidth().height(28.dp).semantics { contentDescription = label }) {
        val lo = 15.0
        val hi = 40.0
        fun x(v: Double) = ((v.coerceIn(lo, hi) - lo) / (hi - lo) * size.width).toFloat()
        val h = 12.dp.toPx()
        val y = (size.height - h) / 2
        listOf(lo to 18.5 to Accent.Water, 18.5 to 25.0 to Accent.Route, 25.0 to 30.0 to Accent.Steps, 30.0 to hi to Accent.Move).forEach { (r, c) ->
            drawRoundRect(c, Offset(x(r.first), y), Size(x(r.second) - x(r.first), h), CornerRadius(3.dp.toPx()))
        }
        val mx = x(bmi)
        drawCircle(Color.White, 10.dp.toPx(), Offset(mx, size.height / 2))
        drawCircle(marker, 7.dp.toPx(), Offset(mx, size.height / 2))
    }
}

@Composable
private fun WeightChart(values: List<Double>, goal: Double?, modifier: Modifier) {
    val line = Accent.Body
    val goalColor = Accent.Xp
    Canvas(modifier.semantics { contentDescription = values.joinToString { "%.1f".format(it) } }) {
        val all = values + listOfNotNull(goal)
        val min = all.min() - 1
        val max = all.max() + 1
        fun y(v: Double) = (size.height - (v - min) / (max - min) * size.height).toFloat()
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        goal?.let { drawLine(goalColor, Offset(0f, y(it)), Offset(size.width, y(it)), strokeWidth = 2.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
        values.forEachIndexed { i, v ->
            if (i > 0) drawLine(line, Offset((i - 1) * step, y(values[i - 1])), Offset(i * step, y(v)), strokeWidth = 4.dp.toPx())
            drawCircle(line, 5.dp.toPx(), Offset(i * step, y(v)))
        }
    }
}
