package io.github.smyrna_pergamum.exercisetracker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.smyrna_pergamum.exercisetracker.ui.AppNav
import io.github.smyrna_pergamum.exercisetracker.ui.AppTheme
import io.github.smyrna_pergamum.exercisetracker.ui.KeyLostScreen

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        setContent {
            val settings by app.settings.flow.collectAsState(initial = null)
            val theme = settings?.theme ?: io.github.smyrna_pergamum.exercisetracker.data.ThemeMode.BLUE
            // Status bar icons follow the app theme (light icons on the dark theme).
            androidx.compose.runtime.SideEffect {
                val style = if (theme.dark) androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else androidx.activity.SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AppTheme(theme) {
                val repo = app.repo
                when {
                    repo == null -> KeyLostScreen(onReset = app::wipeAndRestart)
                    settings != null -> AppNav(app, repo, settings!!)
                }
            }
        }
    }
}
