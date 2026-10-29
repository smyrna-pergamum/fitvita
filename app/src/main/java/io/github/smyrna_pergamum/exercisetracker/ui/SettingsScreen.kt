package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavHostController
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.BuildConfig
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.backup.Backup
import io.github.smyrna_pergamum.exercisetracker.backup.BackupData
import io.github.smyrna_pergamum.exercisetracker.data.Settings
import io.github.smyrna_pergamum.exercisetracker.data.ThemeMode
import io.github.smyrna_pergamum.exercisetracker.domain.WeightUnit
import io.github.smyrna_pergamum.exercisetracker.security.BackupException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
}

/** UI languages; names are written in their own language. */
val LANGUAGES = listOf("tr" to "Türkçe", "en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "ru" to "Русский", "ar" to "العربية")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(app: App, settings: Settings, nav: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf<String?>(null) } // "export" or "restore"
    var exportPw by remember { mutableStateOf<CharArray?>(null) }
    var restoreBytes by remember { mutableStateOf<ByteArray?>(null) }
    var restoreData by remember { mutableStateOf<BackupData?>(null) }
    var wipe by remember { mutableStateOf(false) }
    val err = mapOf(
        "wrong_password" to stringResource(R.string.err_wrong_password),
        "newer_version" to stringResource(R.string.err_newer),
        "too_large" to stringResource(R.string.err_too_large),
        "bad_format" to stringResource(R.string.err_bad_file),
        "io" to stringResource(R.string.err_io),
    )
    val saved = stringResource(R.string.saved)
    val restored = stringResource(R.string.restored)
    val appLocales = AppCompatDelegate.getApplicationLocales()
    val lang = if (appLocales.isEmpty) Locale.getDefault().language else appLocales[0]!!.language

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = exportPw.also { exportPw = null }
        if (uri == null || pw == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                val bytes = withContext(Dispatchers.Default) { app.backup!!.export(pw) }
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) } }
                saved
            } catch (e: Exception) { err["io"] } finally { pw.fill('\u0000'); busy = false }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }.getOrNull()
            }
            when {
                bytes == null -> message = err["io"]
                bytes.size > Backup.MAX_BYTES -> message = err["too_large"]
                else -> { restoreBytes = bytes; password = "restore" }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Bar(stringResource(R.string.settings), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            androidx.compose.material3.Card(onClick = { nav.navigate("history") }, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.History, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
            if (busy) Text(stringResource(R.string.working))

            Section(stringResource(R.string.theme))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThemeMode.BLUE to R.string.theme_blue, ThemeMode.DARK to R.string.theme_dark, ThemeMode.PINK to R.string.theme_pink,
                    ThemeMode.LIGHT to R.string.theme_light).forEach { (m, label) ->
                    FilterChip(settings.theme == m, { scope.launch { app.settings.setTheme(m) } }, label = { Text(stringResource(label)) })
                }
            }
            Section(stringResource(R.string.language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(appLocales.isEmpty, { AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()) },
                    label = { Text(stringResource(R.string.lang_system)) })
                LANGUAGES.forEach { (tag, label) ->
                    FilterChip(!appLocales.isEmpty && lang == tag, { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }, label = { Text(label) })
                }
            }

            Section(stringResource(R.string.reminders))
            ReminderSettings(app)

            Section(stringResource(R.string.water_goal))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6, 8, 10, 12).forEach { n ->
                    FilterChip(settings.waterGoal == n, { scope.launch { app.settings.setWaterGoal(n) } }, label = { Text(stringResource(R.string.glasses_n, n)) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(200, 250, 300, 500).forEach { ml ->
                    FilterChip(settings.glassMl == ml, { scope.launch { app.settings.setGlassMl(ml) } }, label = { Text("$ml ml") })
                }
            }
            Section(stringResource(R.string.weight_unit))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.unit == WeightUnit.KG, { scope.launch { app.settings.setUnit(WeightUnit.KG) } }, label = { Text("kg") })
                FilterChip(settings.unit == WeightUnit.LB, { scope.launch { app.settings.setUnit(WeightUnit.LB) } }, label = { Text("lb") })
            }
            Section(stringResource(R.string.rest_time))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 30, 60, 90, 120).forEach { s ->
                    FilterChip(settings.restSec == s, { scope.launch { app.settings.setRest(s) } }, label = { Text(if (s == 0) stringResource(R.string.off) else "$s s") })
                }
            }

            Section(stringResource(R.string.daily_goal))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5000, 8000, 10_000, 12_000, 15_000).forEach { g ->
                    FilterChip(settings.stepGoal == g, { scope.launch { app.settings.setStepGoal(g) } }, label = { Text(formatSteps(g.toLong())) })
                }
            }
            Section(stringResource(R.string.stride))
            Text(stringResource(R.string.stride_note), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(55, 60, 65, 70, 75, 80, 85, 90).forEach { cm ->
                    FilterChip(settings.strideCm == cm, { scope.launch { app.settings.setStride(cm) } }, label = { Text("$cm cm") })
                }
            }
            if (settings.stepsOn) OutlinedButton(onClick = { scope.launch { app.settings.setStepsOn(false); app.steps.cancel() } }) {
                Text(stringResource(R.string.steps_turn_off))
            }

            Section(stringResource(R.string.backup))
            Text(stringResource(R.string.backup_note), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !busy, onClick = { password = "export" }) { Text(stringResource(R.string.backup_create)) }
                OutlinedButton(enabled = !busy, onClick = { open.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.backup_restore)) }
            }

            Section(stringResource(R.string.about))
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.health_text), style = MaterialTheme.typography.bodySmall)

            Section(stringResource(R.string.delete_data))
            Text(stringResource(R.string.wipe_text), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { wipe = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.wipe_confirm))
            }
            Text("v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        }
    }

    password?.let { mode ->
        PasswordDialog(twice = mode == "export", onDone = { pw ->
            password = null
            if (mode == "export") { exportPw = pw; create.launch("exercise-tracker.backup") }
            else {
                val bytes = restoreBytes ?: return@PasswordDialog
                busy = true
                scope.launch {
                    try {
                        restoreData = withContext(Dispatchers.Default) { app.backup!!.read(bytes, pw) }
                    } catch (e: BackupException) {
                        message = err[e.reason] ?: err["bad_format"]
                    } finally { pw.fill('\u0000'); busy = false }
                }
            }
        }) { password = null; restoreBytes = null }
    }
    restoreData?.let { d ->
        Confirm(stringResource(R.string.backup_restore),
            stringResource(R.string.restore_confirm, d.workouts.size, d.activities.size, formatDateTime(d.exportedAt)),
            stringResource(R.string.restore_go), {
                scope.launch { message = try { app.backup!!.restore(d); restored } catch (e: Exception) { err["bad_format"] } }
            }) { restoreData = null; restoreBytes = null }
    }
    if (wipe) Confirm(stringResource(R.string.delete_data), stringResource(R.string.wipe_text), stringResource(R.string.wipe_confirm), app::wipeAndRestart) { wipe = false }
}

@Composable
private fun PasswordDialog(twice: Boolean, onDone: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (twice) Text(stringResource(R.string.password_note), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(a, { a = it }, label = { Text(stringResource(R.string.password)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (twice) OutlinedTextField(b, { b = it }, label = { Text(stringResource(R.string.password_again)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = {
            TextButton(enabled = a.length >= 8 && (!twice || a == b), onClick = { val pw = a.toCharArray(); a = ""; b = ""; onDone(pw) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
