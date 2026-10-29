package io.github.smyrna_pergamum.exercisetracker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.smyrna_pergamum.exercisetracker.App
import io.github.smyrna_pergamum.exercisetracker.R
import io.github.smyrna_pergamum.exercisetracker.tracking.hasLocationPermission
import io.github.smyrna_pergamum.exercisetracker.tracking.hasPreciseLocation
import io.github.smyrna_pergamum.exercisetracker.tracking.isLocationOn
import kotlinx.coroutines.launch

private enum class Ask { NONE, LOCATION_OFF, PRECISE }

/**
 * At app start: asks for location (plus notifications and step counting) when missing, then checks
 * that the phone's location switch is on and that precise location is allowed, offering the
 * matching system screen. Asked once per app start; Android itself stops asking after two refusals.
 */
@Composable
fun StartupPermissions(app: App) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asked by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(Ask.NONE) }

    fun checkServices() {
        dialog = when {
            !context.hasLocationPermission() -> Ask.NONE
            !context.isLocationOn() -> Ask.LOCATION_OFF
            !context.hasPreciseLocation() -> Ask.PRECISE
            else -> Ask.NONE
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACTIVITY_RECOGNITION] == true && app.steps.hasSensor()) {
            scope.launch { app.settings.setStepsOn(true); app.steps.schedule() }
        }
        checkServices()
    }

    LaunchedEffect(Unit) {
        if (asked) return@LaunchedEffect
        asked = true
        val missing = buildList {
            if (!context.hasLocationPermission()) { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION) }
            if (Build.VERSION.SDK_INT >= 33 && !io.github.smyrna_pergamum.exercisetracker.tracking.Reminders.canNotify(context)) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 29 && !app.steps.hasPermission() && app.steps.hasSensor()) add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray()) else checkServices()
    }

    when (dialog) {
        Ask.LOCATION_OFF -> AlertDialog(
            onDismissRequest = { dialog = Ask.NONE },
            title = { Text(stringResource(R.string.location_off_title)) },
            text = { Text(stringResource(R.string.location_off_text)) },
            confirmButton = {
                TextButton(onClick = { dialog = Ask.NONE; context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = Ask.NONE }) { Text(stringResource(R.string.later)) } },
        )
        Ask.PRECISE -> AlertDialog(
            onDismissRequest = { dialog = Ask.NONE },
            title = { Text(stringResource(R.string.precise_title)) },
            text = { Text(stringResource(R.string.precise_text)) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = Ask.NONE
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }) { Text(stringResource(R.string.open_settings)) }
            },
            dismissButton = { TextButton(onClick = { dialog = Ask.NONE }) { Text(stringResource(R.string.later)) } },
        )
        Ask.NONE -> {}
    }
}

/** Same check right before a route starts; returns true when recording can begin. */
fun locationReady(context: android.content.Context): Boolean = context.hasLocationPermission() && context.isLocationOn()
