package io.github.smyrna_pergamum.exercisetracker.ui

import android.Manifest
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.ActivityType
import io.github.smyrna_pergamum.exercisetracker.data.Repository
import io.github.smyrna_pergamum.exercisetracker.domain.Geo
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import io.github.smyrna_pergamum.exercisetracker.domain.formatDuration
import io.github.smyrna_pergamum.exercisetracker.tracking.hasLocationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ActivityTab(app: App, repo: Repository, settings: io.github.smyrna_pergamum.exercisetracker.data.Settings, game: io.github.smyrna_pergamum.exercisetracker.data.GameState?, nav: NavHostController) {
    val live by app.tracker.live.collectAsState()
    // This scope outlives the live screen, so navigation after saving is not cancelled.
    val scope = rememberCoroutineScope()
    var tooShort by remember { mutableStateOf(false) }
    if (live == null) HomeDashboard(app, repo, settings, game, nav)
    else LiveActivity(app, onRadio = { nav.navigate("radio?back=true") }, onFinish = {
        scope.launch {
            val id = app.tracker.finish()
            if (id != null) nav.navigate("activity/$id") else tooShort = true
        }
    })
    if (tooShort) Confirm(stringResource(R.string.finish_activity), stringResource(R.string.too_short), stringResource(R.string.ok), {}) { tooShort = false }
}

@Composable
private fun LiveActivity(app: App, onRadio: () -> Unit, onFinish: () -> Unit) {
    val live by app.tracker.live.collectAsState()
    val l = live ?: return
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    check(tick >= 0)
    var finish by remember { mutableStateOf(false) }
    val active = l.activeMs(SystemClock.elapsedRealtime())

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(activityLabel(l.type))) {
            IconButton(onClick = onRadio) { Icon(Icons.Filled.Radio, stringResource(R.string.radio)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            RouteMap(l.points, l.here, follow = true, modifier = Modifier.fillMaxSize().clipToBounds())
            if (l.here == null) Card(Modifier.align(Alignment.TopCenter).padding(12.dp)) {
                Text(stringResource(R.string.waiting_gps), Modifier.padding(12.dp))
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatDuration(active), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row {
                Stat(km(l.distanceM), stringResource(R.string.distance), Modifier.weight(1f))
                Stat(Geo.formatPace(Geo.paceSecPerKm(l.distanceM, active)) + " /km", stringResource(R.string.pace), Modifier.weight(1f))
                l.steps?.let { Stat(formatSteps(it.toLong()), stringResource(R.string.steps_unit), Modifier.weight(1f)) }
            }
            if (l.paused) Text(stringResource(R.string.paused), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { if (l.paused) app.tracker.resume() else app.tracker.pause() }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(if (l.paused) R.string.resume else R.string.pause))
                }
                BigButton(stringResource(R.string.finish), { finish = true }, Modifier.weight(1f))
            }
        }
    }
    if (finish) Confirm(stringResource(R.string.finish_activity), stringResource(R.string.finish_activity_text), stringResource(R.string.finish), onFinish) { finish = false }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Finished activity: route on the map, numbers, and share / save as picture / GPX. */
@Composable
fun ActivityDetail(app: App, repo: Repository, nav: NavHostController, id: String) {
    val context = LocalContext.current
    val a by repo.activity(id).collectAsState(initial = null)
    val act = a
    val route by produceState(emptyList<Pt>(), act?.routeEnc) { value = act?.let { repo.route(it) }.orEmpty() }
    val scope = rememberCoroutineScope()
    val handle = remember { MapHandle() }
    var delete by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val saved = stringResource(R.string.saved)
    val failed = stringResource(R.string.err_io)

    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val x = act ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val bmp = ShareImage.render(context, x, route, handle.snapshot())
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; saved }.getOrDefault(failed)
            }
        }
    }
    val saveGpx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        val x = act ?: return@rememberLauncherForActivityResult
        uri ?: return@rememberLauncherForActivityResult
        val gpx = Geo.gpx(route, context.getString(activityLabel(x.type)) + " " + formatDateTime(x.startedAt), context.getString(R.string.app_name))
        scope.launch {
            message = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)!!.use { it.write(gpx.toByteArray()) }; saved }.getOrDefault(failed)
            }
        }
    }

    if (act == null) { Bar("", onBack = { nav.popBackStack() }); return }
    val fileBase = "activity-" + java.text.SimpleDateFormat("yyyy-MM-dd-HHmm", java.util.Locale.ROOT).format(java.util.Date(act.startedAt))

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(activityLabel(act.type)), onBack = { if (!nav.popBackStack("history", false)) nav.navigate("history") }) {
            IconButton(onClick = { ShareImage.share(context, ShareImage.render(context, act, route, handle.snapshot())) }) {
                Icon(Icons.Filled.Share, stringResource(R.string.share))
            }
            IconButton(onClick = { delete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
        }
        // The map stays outside the scrolling area: a native map view inside a scroll container
        // would draw over the content below it.
        Box(Modifier.fillMaxWidth().height(300.dp).clipToBounds()) {
            RouteMap(route, null, follow = false, modifier = Modifier.fillMaxSize(), handle = handle)
        }
        Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatDateTime(act.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row {
                    Stat(km(act.distanceM), stringResource(R.string.distance), Modifier.weight(1f))
                    Stat(formatDuration(act.durationMs), stringResource(R.string.duration), Modifier.weight(1f))
                }
                Row {
                    Stat(Geo.formatPace(Geo.paceSecPerKm(act.distanceM, act.durationMs)) + " /km", stringResource(R.string.pace), Modifier.weight(1f))
                    val kmh = if (act.durationMs > 0) act.distanceM / 1000 / (act.durationMs / 3_600_000.0) else 0.0
                    Stat("%.1f km/h".format(kmh), stringResource(R.string.speed), Modifier.weight(1f))
                }
                Row {
                    Stat("%.0f m".format(act.ascentM), stringResource(R.string.ascent), Modifier.weight(1f))
                    act.steps?.let { Stat(formatSteps(it.toLong()), stringResource(R.string.steps_unit), Modifier.weight(1f)) }
                }
                BigButton(stringResource(R.string.share_image), { ShareImage.share(context, ShareImage.render(context, act, route, handle.snapshot())) })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { saveImage.launch("$fileBase.png") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.save_image)) }
                    OutlinedButton(onClick = { saveGpx.launch("$fileBase.gpx") }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_gpx)) }
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Text(stringResource(R.string.map_credit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (delete) Confirm(stringResource(R.string.delete_activity), stringResource(R.string.delete_activity_text), stringResource(R.string.delete), {
        scope.launch { repo.deleteActivity(id); nav.popBackStack() }
    }) { delete = false }
}
