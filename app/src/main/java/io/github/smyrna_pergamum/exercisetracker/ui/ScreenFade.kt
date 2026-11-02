package io.github.smyrna_pergamum.exercisetracker.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.delay

/**
 * Opacity of the whole screen. Changing the language recreates the activity; fading out first and
 * back in on the new activity (MainActivity) turns that jump into a soft transition.
 */
object ScreenFade {
    val alpha = Animatable(1f)

    suspend fun switchLanguage(locales: LocaleListCompat) {
        if (locales.toLanguageTags() == AppCompatDelegate.getApplicationLocales().toLanguageTags()) return
        alpha.animateTo(0f, tween(180))
        AppCompatDelegate.setApplicationLocales(locales)
        // Normally the activity is recreated and fades itself back in; this only runs if it was not.
        delay(800)
        alpha.animateTo(1f, tween(250))
    }
}
