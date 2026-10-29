package io.github.smyrna_pergamum.exercisetracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.ActivityType
import io.github.smyrna_pergamum.exercisetracker.data.GameState
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.data.Settings
import io.github.smyrna_pergamum.exercisetracker.domain.Quest
import io.github.smyrna_pergamum.exercisetracker.domain.Steps
import io.github.smyrna_pergamum.exercisetracker.tracking.hasLocationPermission
import io.github.smyrna_pergamum.exercisetracker.tracking.isLocationOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

fun formatSteps(n: Long): String = NumberFormat.getIntegerInstance().format(n)


/** The walking buddy: an 8-frame walk cycle on a moving dashed path, like a small GIF. */
@Composable
fun WalkingBuddy(modifier: Modifier = Modifier, fast: Boolean = false) {
    val frames = if (LocalLook.current.female) Art.walkerFemale else Art.walker
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(fast) { while (true) { delay(if (fast) 85 else 120); frame = (frame + 1) % frames.size } }
    val shift by rememberInfiniteTransition(label = "path").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (fast) 600 else 900, easing = LinearEasing), RepeatMode.Restart), label = "shift",
    )
    // In right-to-left languages the buddy walks the other way, with the reading direction.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f }) {
        Canvas(Modifier.fillMaxSize()) {
            val y = size.height * 0.885f
            val dash = 18.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.55f), Offset(0f, y), Offset(size.width, y), strokeWidth = 4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), -shift * 2 * dash))
        }
        Image(painterResource(frames[frame]), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** Home: level and streak, big step counter with the walking buddy, daily quests, water and routes. */
@Composable
fun HomeDashboard(app: App, repo: Repository, settings: Settings, game: GameState?, nav: NavHostController) {
    val scope = rememberCoroutineScope()
    val celebrate = remember { mutableStateOf(false) }
    // Confetti when a quest gets done while the screen is open.
    var seen by remember { mutableStateOf<Set<Quest>?>(null) }
    LaunchedEffect(game?.done) {
        val now = game?.done ?: return@LaunchedEffect
        val before = seen
        if (before != null && (now - before).isNotEmpty()) celebrate.value = true
        seen = now
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header(game) { nav.navigate("settings") }
            StepsHero(app, settings, game)
            Text(stringResource(R.string.daily_quests), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Quests(game, settings, onWater = { scope.launch { repo.addWater(1) } }, onMove = { nav.navigate("workout") }, onMind = { nav.navigate("yoga") })
            WaterCard(game, settings, onAdd = { scope.launch { repo.addWater(it) } })
            BodyCard(repo, settings) { nav.navigate("body") }
            RouteStart(app) { nav.navigate("radio?back=true") }
            Spacer(Modifier.height(8.dp))
        }
        if (celebrate.value) Confetti(Modifier.fillMaxSize()) { celebrate.value = false }
    }
}

@Composable
private fun Header(game: GameState?, onSettings: () -> Unit) {
    val hour = LocalTime.now().hour
    val hello = stringResource(when (hour) { in 5..11 -> R.string.hello_morning; in 12..17 -> R.string.hello_day; else -> R.string.hello_evening })
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(hello, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) }
        }
        if (game != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(Icons.Filled.Star, stringResource(R.string.level_n, game.level.level), Accent.Violet)
            Pill(Icons.Filled.LocalFireDepartment, stringResource(R.string.streak_n, game.streak), Accent.Steps)
            Column(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { game.level.intoLevel.toFloat() / game.level.forNext },
                    color = Accent.Xp, trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                )
                Text(stringResource(R.string.xp_progress, game.level.intoLevel, game.level.forNext), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Pill(icon: ImageVector, text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = 0.15f), contentColor = color) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun StepsHero(app: App, settings: Settings, game: GameState?) {
    val scope = rememberCoroutineScope()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) scope.launch { turnOnSteps(app) } }
    val steps = game?.today?.steps ?: 0
    val on = settings.stepsOn && app.steps.hasPermission() && app.steps.hasSensor()
    var lastChange by remember { mutableStateOf(0L) }
    LaunchedEffect(steps) { lastChange = System.currentTimeMillis() }

    if (on) DisposableEffect(Unit) {
        var lastSave = 0L
        val stop = app.steps.listen { counter ->
            val now = System.currentTimeMillis()
            if (now - lastSave > 3_000) { lastSave = now; scope.launch { app.steps.record(counter) } }
        }
        onDispose { stop() }
    }

    GradientCard(Accent.steps()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WalkingBuddy(Modifier.width(112.dp).height(140.dp), fast = System.currentTimeMillis() - lastChange < 5_000 && steps > 0)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(stringResource(R.string.steps_today), style = MaterialTheme.typography.titleMedium)
                Text(formatSteps(steps), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black,
                    modifier = Modifier.semantics { contentDescription = "${formatSteps(steps)} " })
                Text(stringResource(R.string.of_goal, formatSteps(settings.stepGoal.toLong())), style = MaterialTheme.typography.bodyLarge)
                Text(km(Steps.distanceM(steps, settings.strideCm)), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        LinearProgressIndicator(
            progress = { (steps.toFloat() / settings.stepGoal).coerceAtMost(1f) },
            color = Color.White, trackColor = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(12.dp).clip(RoundedCornerShape(6.dp)),
        )
        when {
            !app.steps.hasSensor() -> Text(stringResource(R.string.steps_no_sensor), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            !on -> TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 29 && !app.steps.hasPermission()) ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                else scope.launch { turnOnSteps(app) }
            }) { Text(stringResource(R.string.steps_turn_on), color = Color.White, fontWeight = FontWeight.Bold) }
            else -> WeekSteps(app, settings.stepGoal.toLong())
        }
    }
}

private suspend fun turnOnSteps(app: App) {
    app.settings.setStepsOn(true)
    app.steps.schedule()
    app.steps.readOnce()?.let { app.steps.record(it) }
}

@Composable
private fun WeekSteps(app: App, goal: Long) {
    val today = LocalDate.now()
    val days by remember(today) { app.repo!!.stepDays(today.minusDays(6).toString()) }.collectAsState(initial = emptyList())
    val byDate = days.associate { it.date to it.steps.toLong() }
    val week = (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to (byDate[it.toString()] ?: 0L) }
    val max = maxOf(goal, week.maxOf { it.second }).coerceAtLeast(1)
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().height(70.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { (d, n) ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.weight(1f).fillMaxWidth().semantics { contentDescription = "${d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}: ${formatSteps(n)}" }) {
                    val h = size.height * n / max
                    drawRoundRect(Color.White.copy(alpha = 0.25f), Offset.Zero, size, CornerRadius(5.dp.toPx()))
                    drawRoundRect(Color.White.copy(alpha = if (n >= goal) 1f else 0.75f), Offset(0f, size.height - h), Size(size.width, h), CornerRadius(5.dp.toPx()))
                }
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Quests(game: GameState?, settings: Settings, onWater: () -> Unit, onMove: () -> Unit, onMind: () -> Unit) {
    val g = game ?: return
    val t = g.today
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestTile(Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(R.string.quest_steps), "${formatSteps(t.steps)} / ${formatSteps(settings.stepGoal.toLong())}",
                Quest.STEPS in g.done, Accent.steps(), Accent.Steps, Modifier.weight(1f)) {}
            QuestTile(Icons.Filled.WaterDrop, stringResource(R.string.quest_water), "${t.glasses} / ${settings.waterGoal}",
                Quest.WATER in g.done, Accent.water(), Accent.Water, Modifier.weight(1f), onWater)
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuestTile(Icons.Filled.FitnessCenter, stringResource(R.string.quest_move), stringResource(R.string.quest_move_hint),
                Quest.MOVE in g.done, Accent.move(), Accent.Move, Modifier.weight(1f), onMove)
            QuestTile(Icons.Filled.SelfImprovement, stringResource(R.string.quest_mind), stringResource(R.string.quest_mind_hint),
                Quest.MIND in g.done, Accent.mind(), Accent.Mind, Modifier.weight(1f), onMind)
        }
        Text(stringResource(R.string.xp_today, g.xpToday), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuestTile(icon: ImageVector, title: String, progress: String, done: Boolean, brush: Brush, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val stateText = stringResource(if (done) R.string.quest_done else R.string.quest_open)
    if (done) {
        GradientCard(brush, modifier.fillMaxHeight().semantics { contentDescription = "$title, $progress, $stateText" }, onClick) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(28.dp))
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(24.dp))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            Text(progress, style = MaterialTheme.typography.bodySmall)
            Text("+25 XP ✓", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    } else {
        Card(onClick = onClick, modifier = modifier.fillMaxHeight().semantics { contentDescription = "$title, $progress, $stateText" }, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(progress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("+25 XP", style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WaterCard(game: GameState?, settings: Settings, onAdd: (Int) -> Unit) {
    val glasses = game?.today?.glasses ?: 0
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.water_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.water_amount, glasses * settings.glassMl, settings.waterGoal * settings.glassMl),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onAdd(-1) }, enabled = glasses > 0) { Icon(Icons.Filled.Remove, stringResource(R.string.water_minus)) }
                FilledIconButton(onClick = { onAdd(1) }, modifier = Modifier.size(56.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent.Water, contentColor = Color.White)) {
                    Icon(Icons.Filled.Add, stringResource(R.string.water_plus), Modifier.size(32.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(maxOf(settings.waterGoal, glasses)) { i ->
                    Icon(if (i < glasses) Icons.Filled.WaterDrop else Icons.Outlined.WaterDrop, null, tint = Accent.Water,
                        modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun RouteStart(app: App, onRadio: () -> Unit) {
    val context = LocalContext.current
    var type by rememberSaveable { mutableStateOf(ActivityType.WALK) }
    var denied by remember { mutableStateOf(false) }
    var locationOff by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when {
            !context.hasLocationPermission() -> denied = true
            !context.isLocationOn() -> { denied = false; locationOff = true }
            else -> { denied = false; app.tracker.start(type) }
        }
    }
    if (locationOff) Confirm(stringResource(R.string.location_off_title), stringResource(R.string.location_off_text), stringResource(R.string.open_settings),
        { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { locationOff = false }
    GradientCard(Accent.route()) {
        Text(stringResource(R.string.record_route), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.route_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Surface(onClick = onRadio, shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Radio, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.radio), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityType.entries.forEach { t ->
                val sel = t == type
                Surface(onClick = { type = t }, shape = RoundedCornerShape(16.dp), color = if (sel) Color.White else Color.White.copy(alpha = 0.2f),
                    contentColor = if (sel) Accent.Route else Color.White, modifier = Modifier.weight(1f)) {
                    Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(activityIcon(t), null)
                        Text(stringResource(activityLabel(t)), style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Surface(onClick = {
            if (context.hasLocationPermission()) { if (context.isLocationOn()) app.tracker.start(type) else locationOff = true }
            else ask.launch(buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray())
        }, shape = CircleShape, color = Color.White, contentColor = Accent.Route, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(stringResource(R.string.start), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
        if (denied) Text(stringResource(R.string.location_denied), modifier = Modifier.padding(top = 8.dp))
    }
}
