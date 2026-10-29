package io.github.smyrna_pergamum.exercisetracker.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.data.Reminder
import io.github.smyrna_pergamum.exercisetracker.data.ReminderType
import io.github.smyrna_pergamum.exercisetracker.tracking.Reminders
import kotlinx.coroutines.launch

private data class ReminderLook(val icon: ImageVector, val color: Color, val title: Int)

private fun look(t: ReminderType) = when (t) {
    ReminderType.WATER -> ReminderLook(Icons.Filled.WaterDrop, Accent.Water, R.string.rem_water)
    ReminderType.MOVE -> ReminderLook(Icons.AutoMirrored.Filled.DirectionsWalk, Accent.Steps, R.string.rem_move)
    ReminderType.WORKOUT -> ReminderLook(Icons.Filled.FitnessCenter, Accent.Move, R.string.rem_workout)
    ReminderType.YOGA -> ReminderLook(Icons.Filled.SelfImprovement, Accent.Mind, R.string.rem_yoga)
}

/** One switch per reminder kind; tapping the row changes its time (and interval for water). */
@Composable
fun ReminderSettings(app: App) {
    val stored by app.db.health().reminders().collectAsState(initial = emptyList())
    val list = Reminders.defaults().map { d -> stored.firstOrNull { it.type == d.type } ?: d }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<Reminder?>(null) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun save(r: Reminder) = scope.launch {
        app.db.health().setReminder(r)
        Reminders.scheduleAll(app, listOf(r))
    }

    Text(stringResource(R.string.reminders_note), style = MaterialTheme.typography.bodySmall)
    list.forEach { r ->
        val l = look(r.type)
        Row(Modifier.fillMaxWidth().clickable { editing = r }.sizeIn(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(l.color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(l.icon, null, tint = l.color)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(stringResource(l.title), fontWeight = FontWeight.Bold)
                Text(
                    if (r.intervalH > 0) stringResource(R.string.rem_every, "%02d:00".format(r.hour), "%02d:00".format(Reminders.LAST_HOUR), r.intervalH)
                    else "%02d:%02d".format(r.hour, r.minute),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(r.enabled, { on ->
                if (on && Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                save(r.copy(enabled = on))
            })
        }
    }
    editing?.let { r -> TimeDialog(r, onSave = { save(it.copy(enabled = true)) }) { editing = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(r: Reminder, onSave: (Reminder) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(r.hour, r.minute, is24Hour = true)
    var interval by remember { mutableStateOf(r.intervalH) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(look(r.type).title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (r.type == ReminderType.WATER) {
                    Text(stringResource(R.string.rem_interval))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { h -> FilterChip(interval == h, { interval = h }, label = { Text(stringResource(R.string.hours_n, h)) }) }
                    }
                    Text(stringResource(R.string.rem_from))
                }
                TimePicker(state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onSave(r.copy(hour = state.hour, minute = if (r.type == ReminderType.WATER) 0 else state.minute, intervalH = if (r.type == ReminderType.WATER) interval else 0))
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
