package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import io.github.smyrna_pergamum.exercisetracker.data.ThemeMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.ActivityType
import io.github.smyrna_pergamum.exercisetracker.domain.Exercise
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** Category colours used across the app; each daily goal has its own colour and gradient. */
object Accent {
    val Steps = Color(0xFFFF8A00)
    val StepsEnd = Color(0xFFFF4E7A)
    val Water = Color(0xFF2F80ED)
    val WaterEnd = Color(0xFF56CCF2)
    val Move = Color(0xFFF2456C)
    val MoveEnd = Color(0xFFFF8A65)
    val Mind = Color(0xFF8E5BFF)
    val MindEnd = Color(0xFFC084FC)
    val Xp = Color(0xFFFFB300)
    val Route = Color(0xFF00B37E)
    val RouteEnd = Color(0xFF00C9A7)
    val Violet = Color(0xFF6C4CF5)
    val Body = Color(0xFF00A3A3)
    val BodyEnd = Color(0xFF3DD6B5)

    fun steps() = Brush.linearGradient(listOf(Steps, StepsEnd))
    fun water() = Brush.linearGradient(listOf(Water, WaterEnd))
    fun move() = Brush.linearGradient(listOf(Move, MoveEnd))
    fun mind() = Brush.linearGradient(listOf(Mind, MindEnd))
    fun route() = Brush.linearGradient(listOf(Route, RouteEnd))
    fun hero() = Brush.linearGradient(listOf(Violet, Color(0xFFB04CF5)))
    fun body() = Brush.linearGradient(listOf(Body, BodyEnd))
}

/** Theme-dependent parts that are not in the Material colour scheme. */
data class Look(val female: Boolean, val hero: Brush)

val LocalLook = staticCompositionLocalOf { Look(false, Accent.hero()) }

private fun scheme(mode: ThemeMode) = when (mode) {
    ThemeMode.BLUE -> lightColorScheme(
        primary = Color(0xFF1E6FE8), onPrimary = Color.White, primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF0B2A66),
        secondary = Color(0xFF00A3C4), onSecondary = Color.White, secondaryContainer = Color(0xFFD3F3FA), onSecondaryContainer = Color(0xFF00363F),
        tertiary = Accent.Route, background = Color(0xFFF2F6FD), surface = Color(0xFFF2F6FD), onSurface = Color(0xFF16202E),
        onSurfaceVariant = Color(0xFF5B6678), surfaceVariant = Color(0xFFE2E9F5), surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White, surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color.White, outlineVariant = Color(0xFFD5DEEC),
    )
    ThemeMode.DARK -> darkColorScheme(
        primary = Color(0xFF7DB2FF), onPrimary = Color(0xFF00285E), primaryContainer = Color(0xFF15386E), onPrimaryContainer = Color(0xFFDCE8FF),
        secondary = Color(0xFF5FD6EE), onSecondary = Color(0xFF00363F), secondaryContainer = Color(0xFF0E4652), onSecondaryContainer = Color(0xFFD3F3FA),
        tertiary = Color(0xFF4FE0B0), background = Color(0xFF0D121B), surface = Color(0xFF0D121B), onSurface = Color(0xFFE3E8F1),
        onSurfaceVariant = Color(0xFFA3ADBF), surfaceVariant = Color(0xFF242D3B), surfaceContainerLowest = Color(0xFF080B11),
        surfaceContainerLow = Color(0xFF151C28), surfaceContainer = Color(0xFF18202D), surfaceContainerHigh = Color(0xFF1B2431),
        surfaceContainerHighest = Color(0xFF1F2836), outlineVariant = Color(0xFF2F3A4B),
    )
    ThemeMode.PINK -> lightColorScheme(
        primary = Color(0xFFE0457B), onPrimary = Color.White, primaryContainer = Color(0xFFFFDCE7), onPrimaryContainer = Color(0xFF5C0A2A),
        secondary = Color(0xFFFF8A65), onSecondary = Color.White, secondaryContainer = Color(0xFFFFE3D9), onSecondaryContainer = Color(0xFF4A1500),
        tertiary = Color(0xFF9C5BD6), background = Color(0xFFFFF4F7), surface = Color(0xFFFFF4F7), onSurface = Color(0xFF2B1720),
        onSurfaceVariant = Color(0xFF7A5D68), surfaceVariant = Color(0xFFF7E1E9), surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color.White, surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
        surfaceContainerHighest = Color.White, outlineVariant = Color(0xFFF0D3DE),
    )
    ThemeMode.BROWN -> darkColorScheme(
        primary = Color(0xFFE0A877), onPrimary = Color(0xFF3B2210), primaryContainer = Color(0xFF5A3A22), onPrimaryContainer = Color(0xFFFFDCC2),
        secondary = Color(0xFFF0B98A), onSecondary = Color(0xFF3B2210), secondaryContainer = Color(0xFF4E3424), onSecondaryContainer = Color(0xFFFFDCC2),
        tertiary = Color(0xFFB8D18A), background = Color(0xFF1C1410), surface = Color(0xFF1C1410), onSurface = Color(0xFFF1E3D8),
        onSurfaceVariant = Color(0xFFCDB8A8), surfaceVariant = Color(0xFF3A2C24), surfaceContainerLowest = Color(0xFF140E0B),
        surfaceContainerLow = Color(0xFF241A15), surfaceContainer = Color(0xFF2A1F19), surfaceContainerHigh = Color(0xFF2F231C),
        surfaceContainerHighest = Color(0xFF34271F), outlineVariant = Color(0xFF4A3A30),
    )
}

private fun hero(mode: ThemeMode) = when (mode) {
    ThemeMode.BLUE -> Brush.linearGradient(listOf(Color(0xFF1E6FE8), Color(0xFF00B4D8)))
    ThemeMode.DARK -> Brush.linearGradient(listOf(Color(0xFF1B3A7A), Color(0xFF3A1B7A)))
    ThemeMode.PINK -> Brush.linearGradient(listOf(Color(0xFFE0457B), Color(0xFFFF9A76)))
    ThemeMode.BROWN -> Brush.linearGradient(listOf(Color(0xFF7A4A2A), Color(0xFFC8864E)))
}

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.BLUE, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme(mode), shapes = Shapes(
        small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp),
    )) {
        CompositionLocalProvider(LocalLook provides Look(mode.female, hero(mode))) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}

/** A rounded card filled with a gradient; content is white. */
@Composable
fun GradientCard(brush: Brush, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val m = modifier.clip(shape).background(brush).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Column(m.padding(20.dp), content = content)
    }
}

/** Pale background for an exercise picture, tinted by its group. */
@Composable
fun groupTint(g: io.github.smyrna_pergamum.exercisetracker.domain.Group): Color = when (g) {
    io.github.smyrna_pergamum.exercisetracker.domain.Group.UPPER -> Accent.Move
    io.github.smyrna_pergamum.exercisetracker.domain.Group.LEGS -> Accent.Steps
    io.github.smyrna_pergamum.exercisetracker.domain.Group.CORE -> Accent.Water
    io.github.smyrna_pergamum.exercisetracker.domain.Group.CARDIO -> Accent.Route
    io.github.smyrna_pergamum.exercisetracker.domain.Group.YOGA -> Accent.Mind
    io.github.smyrna_pergamum.exercisetracker.domain.Group.PILATES -> Accent.Body
}.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.3f) 0.2f else 0.12f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Bar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        },
        actions = { actions() },
    )
}

/**
 * The exercise picture. Still pictures show the end pose, which tells the movement best; with
 * [animate] it alternates start and end pose.
 */
@Composable
fun ExerciseImage(e: Exercise, modifier: Modifier = Modifier, animate: Boolean = false) {
    var end by remember { mutableStateOf(!animate) }
    if (animate) LaunchedEffect(e.id) { while (true) { delay(1100); end = !end } }
    val female = LocalLook.current.female
    Crossfade(end, animationSpec = tween(350), modifier = modifier, label = "pose") { b ->
        val res = if (b) e.imageB else e.imageA
        Image(
            painterResource(if (female) Art.female[res] ?: res else res),
            contentDescription = stringResource(e.title),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun Confirm(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NumberField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }.take(6)) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
fun Empty(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.fillMaxWidth().sizeIn(minHeight = 52.dp)) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun KeyLostScreen(onReset: () -> Unit) {
    var ask by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.key_lost_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.key_lost_text))
        BigButton(stringResource(R.string.key_lost_reset), { ask = true })
    }
    if (ask) Confirm(stringResource(R.string.key_lost_reset), stringResource(R.string.wipe_text), stringResource(R.string.wipe_confirm), onReset) { ask = false }
}

fun formatDateTime(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))

fun activityLabel(t: ActivityType) = when (t) {
    ActivityType.WALK -> R.string.act_walk
    ActivityType.HIKE -> R.string.act_hike
    ActivityType.JOG -> R.string.act_jog
    ActivityType.RUN -> R.string.act_run
}

fun activityIcon(t: ActivityType) = when (t) {
    ActivityType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    ActivityType.HIKE -> Icons.Filled.Hiking
    ActivityType.JOG -> Icons.AutoMirrored.Filled.DirectionsRun
    ActivityType.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
}

/** "3,42 km" with the locale's decimal sign. */
fun km(m: Double): String = "%.2f km".format(m / 1000)
