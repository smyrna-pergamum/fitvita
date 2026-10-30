package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.audio.Source
import io.github.smyrna_pergamum.exercisetracker.audio.Station
import io.github.smyrna_pergamum.exercisetracker.audio.Tracks
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import java.util.Locale

private val TAGS = listOf("pop", "rock", "dance", "chillout", "lofi", "jazz", "classical", "news")

/** Internet radio for walks and runs: search by name, genre and country (Radio Browser). */
@Composable
fun RadioScreen(app: App, nav: NavHostController, back: Boolean = false) {
    val now by app.player.now.collectAsState()
    // The per-app language has no region, so the device's region decides "my country".
    val myCountry = (android.content.res.Resources.getSystem().configuration.locales[0].country.takeIf { it.length == 2 }
        ?: mapOf("tr" to "TR", "de" to "DE", "es" to "ES", "fr" to "FR", "ru" to "RU")[Locale.getDefault().language])
    var query by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    var local by rememberSaveable { mutableStateOf(myCountry != null) }
    var submitted by rememberSaveable { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<Station>?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(submitted, tag, local) {
        stations = null
        failed = false
        stations = try {
            val cc = if (local) myCountry else null
            if (submitted.isBlank() && tag == null) app.radio.top(cc) else app.radio.search(submitted, tag, cc)
        } catch (e: Exception) {
            failed = true
            emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (back) Bar(stringResource(R.string.radio), onBack = { nav.popBackStack() })
        else Bar(stringResource(R.string.radio)) { SettingsAction(nav) }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                query, { query = it.take(60) }, Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.radio_search)) }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submitted = query }),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (myCountry != null) FilterChip(local, { local = true }, label = { Text(Locale("", myCountry).displayCountry) })
                FilterChip(!local, { local = false }, label = { Text(stringResource(R.string.radio_world)) })
                FilterChip(tag == null, { tag = null }, label = { Text(stringResource(R.string.radio_all)) })
                TAGS.forEach { t -> FilterChip(tag == t, { tag = if (tag == t) null else t }, label = { Text(t) }) }
            }
        }
        Box(Modifier.weight(1f)) {
            val list = stations
            when {
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                failed -> Empty(stringResource(R.string.radio_error))
                list.isEmpty() -> Empty(stringResource(R.string.radio_empty))
                else -> LazyColumn {
                    items(list, key = { it.id }) { s ->
                        val playing = now?.key == "radio:${s.id}"
                        ListItem(
                            headlineContent = { Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (playing) FontWeight.Bold else null) },
                            supportingContent = { Text(s.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Accent.Route.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    Icon(if (playing) Icons.Filled.GraphicEq else Icons.Filled.Radio, null, tint = Accent.Route)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = if (playing) Accent.Route.copy(alpha = 0.08f) else Color.Transparent),
                            modifier = Modifier.clickable { if (playing) app.player.toggle() else app.player.playStation(s, list) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
        Text(stringResource(R.string.radio_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
    }
}

/** Meditation music list with a sleep timer, for the Inner peace tab. */
@Composable
fun MeditationMusic(app: App) {
    val now by app.player.now.collectAsState()
    val nameOf = @Composable { i: Int -> stringResource(R.string.track_n, i + 1) }
    val names = Tracks.all.indices.map { nameOf(it) }
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null, tint = Accent.Mind)
                Text(stringResource(R.string.meditation_music), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp))
            }
            if (now?.source == Source.MEDITATION) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.sleep_timer), Modifier.align(Alignment.CenterVertically))
                    listOf(null, 10, 20, 30, 60).forEach { m ->
                        val active = if (m == null) now?.sleepAt == null else now?.sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000 + 1).toInt() in (m - 1)..m } == true
                        FilterChip(active, { app.player.sleepTimer(m) }, label = { Text(if (m == null) "∞" else stringResource(R.string.minutes_n, m)) })
                    }
                }
            }
            Tracks.all.forEachIndexed { i, t ->
                val playing = now?.key == "track:${t.res}"
                ListItem(
                    headlineContent = { Text(names[i], fontWeight = if (playing) FontWeight.Bold else null) },
                    supportingContent = { Text("${t.artist} · ${formatDuration(t.seconds * 1000L)}") },
                    leadingContent = {
                        Box(Modifier.size(40.dp).clip(CircleShape).background(Accent.Mind.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(if (playing && now?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Accent.Mind)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { if (playing) app.player.toggle() else app.player.playTrack(t, names[i], Tracks.all.zip(names)) },
                )
            }
        }
    }
}

/** Shown above the tab bar while music or radio plays: title, play/pause and stop. */
@Composable
fun MiniPlayer(app: App) {
    val np = app.player.now.collectAsState().value ?: return
    val color = if (np.source == Source.RADIO) Accent.Route else Accent.Mind
    Surface(color = color, contentColor = Color.White, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (np.source == Source.RADIO) Icons.Filled.Radio else Icons.Filled.MusicNote, null)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(np.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when { np.error -> stringResource(R.string.radio_play_error); np.buffering -> stringResource(R.string.buffering); else -> np.subtitle },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (np.buffering) CircularProgressIndicator(Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
            IconButton(onClick = { app.player.toggle() }) {
                Icon(if (np.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (np.playing) R.string.pause else R.string.resume))
            }
            if (np.hasNext) IconButton(onClick = { app.player.next() }) { Icon(Icons.Filled.SkipNext, stringResource(R.string.next)) }
            IconButton(onClick = { app.player.stop() }) { Icon(Icons.Filled.Close, stringResource(R.string.stop)) }
        }
    }
}

/** Endless ambient sounds (birds, rain, fire …) with pictures; one plays at a time. */
@Composable
fun NatureSounds(app: App) {
    val now by app.player.now.collectAsState()
    val names = Tracks.nature.map { stringResource(it.title) }
    Card(shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Forest, null, tint = Accent.Route)
                Text(stringResource(R.string.nature_sounds), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp).weight(1f))
                Text(stringResource(R.string.loop_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Tracks.nature.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { n ->
                        val i = Tracks.nature.indexOf(n)
                        val playing = now?.key == "track:${n.res}"
                        Box(Modifier.weight(1f).aspectRatio(1.6f).clip(RoundedCornerShape(20.dp))
                            .clickable { if (playing) app.player.toggle() else app.player.playTrack(n.track, names[i], Tracks.nature.map { it.track }.zip(names)) }) {
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(n.image), null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                            Row(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (playing && now?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(names[i], color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
                            }
                        }
                    }
                }
            }
            if (now?.source == Source.MEDITATION) SleepTimerRow(app)
        }
    }
}

@Composable
private fun SleepTimerRow(app: App) {
    val now by app.player.now.collectAsState()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sleep_timer), Modifier.align(Alignment.CenterVertically))
        listOf(null, 10, 20, 30, 60).forEach { m ->
            val active = if (m == null) now?.sleepAt == null else now?.sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000 + 1).toInt() in (m - 1)..m } == true
            FilterChip(active, { app.player.sleepTimer(m) }, label = { Text(if (m == null) "∞" else stringResource(R.string.minutes_n, m)) })
        }
    }
}
