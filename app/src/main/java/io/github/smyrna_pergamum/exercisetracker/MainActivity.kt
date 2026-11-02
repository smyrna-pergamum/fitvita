package io.github.smyrna_pergamum.exercisetracker

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import io.github.smyrna_pergamum.exercisetracker.data.Settings
import io.github.smyrna_pergamum.exercisetracker.data.ThemeMode
import io.github.smyrna_pergamum.exercisetracker.ui.AppNav
import io.github.smyrna_pergamum.exercisetracker.ui.AppTheme
import io.github.smyrna_pergamum.exercisetracker.ui.KeyLostScreen
import io.github.smyrna_pergamum.exercisetracker.ui.ScreenFade

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent {
            // Starting from the last known settings lets a recreated activity (e.g. after a language
            // change) draw its first frame at once instead of showing the bare window.
            val settings by app.settings.flow.collectAsState(initial = lastSettings)
            SideEffect { settings?.let { lastSettings = it } }
            val theme = settings?.theme ?: ThemeMode.BLUE
            // Status bar icons follow the app theme (light icons on the dark theme).
            SideEffect {
                val style = if (theme.dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(theme) {
                val bg = MaterialTheme.colorScheme.background
                SideEffect { window.setBackgroundDrawable(ColorDrawable(bg.toArgb())) }
                LaunchedEffect(Unit) { ScreenFade.alpha.animateTo(1f, tween(250)) }
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = ScreenFade.alpha.value }) {
                    val repo = app.repo
                    when {
                        repo == null -> KeyLostScreen(onReset = app::wipeAndRestart)
                        settings != null -> AppNav(app, repo, settings!!)
                    }
                }
            }
        }
    }

    private companion object {
        var lastSettings: Settings? = null
    }
}
