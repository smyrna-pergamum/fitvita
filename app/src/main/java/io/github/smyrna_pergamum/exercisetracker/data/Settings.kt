package io.github.smyrna_pergamum.exercisetracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.smyrna_pergamum.exercisetracker.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Four looks: colour scheme plus the character shown in pictures (male or female). */
enum class ThemeMode(val dark: Boolean, val female: Boolean) {
    BLUE(false, false), DARK(true, false), PINK(false, true), BROWN(true, true)
}

/** For BMI context (ideal weight); null until chosen. */
enum class Sex { FEMALE, MALE }

data class Settings(
    val unit: WeightUnit,
    val restSec: Int,
    val stepsOn: Boolean = false,
    val stepGoal: Int = 8000,
    /** Step length used to turn steps into distance. */
    val strideCm: Int = 70,
    val theme: ThemeMode = ThemeMode.BLUE,
    val waterGoal: Int = 8,
    val glassMl: Int = 250,
    /** For BMI; null until the user enters it. */
    val heightCm: Int? = null,
    val goalKg: Double? = null,
    val sex: Sex? = null,
)

private val Context.store by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private val unitKey = stringPreferencesKey("unit")
    private val restKey = intPreferencesKey("rest")
    private val stepsKey = booleanPreferencesKey("steps_on")
    private val goalKey = intPreferencesKey("step_goal")
    private val strideKey = intPreferencesKey("stride_cm")
    private val themeKey = stringPreferencesKey("theme")
    private val waterGoalKey = intPreferencesKey("water_goal")
    private val glassKey = intPreferencesKey("glass_ml")
    private val heightKey = intPreferencesKey("height_cm")
    private val goalKgKey = androidx.datastore.preferences.core.doublePreferencesKey("goal_kg")
    private val sexKey = stringPreferencesKey("sex")

    val flow: Flow<Settings> = context.store.data.map { p ->
        Settings(
            unit = p[unitKey]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG,
            restSec = p[restKey] ?: 60,
            stepsOn = p[stepsKey] ?: false,
            stepGoal = p[goalKey] ?: 8000,
            strideCm = p[strideKey] ?: 70,
            theme = p[themeKey]?.let { if (it == "LIGHT") ThemeMode.BROWN else runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.BLUE,
            waterGoal = p[waterGoalKey] ?: 8,
            glassMl = p[glassKey] ?: 250,
            heightCm = p[heightKey],
            goalKg = p[goalKgKey],
            sex = p[sexKey]?.let { runCatching { Sex.valueOf(it) }.getOrNull() },
        )
    }

    suspend fun setUnit(u: WeightUnit) = context.store.edit { it[unitKey] = u.name }
    suspend fun setRest(sec: Int) = context.store.edit { it[restKey] = sec.coerceIn(0, 600) }
    suspend fun setStepsOn(on: Boolean) = context.store.edit { it[stepsKey] = on }
    suspend fun setStepGoal(steps: Int) = context.store.edit { it[goalKey] = steps.coerceIn(1000, 50_000) }
    suspend fun setStride(cm: Int) = context.store.edit { it[strideKey] = cm.coerceIn(30, 150) }
    suspend fun setTheme(t: ThemeMode) = context.store.edit { it[themeKey] = t.name }
    suspend fun setWaterGoal(glasses: Int) = context.store.edit { it[waterGoalKey] = glasses.coerceIn(1, 20) }
    suspend fun setGlassMl(ml: Int) = context.store.edit { it[glassKey] = ml.coerceIn(100, 1000) }
    suspend fun setHeight(cm: Int?) = context.store.edit { if (cm == null) it.remove(heightKey) else it[heightKey] = cm.coerceIn(80, 250) }
    suspend fun setGoalKg(kg: Double?) = context.store.edit { if (kg == null) it.remove(goalKgKey) else it[goalKgKey] = kg.coerceIn(20.0, 400.0) }
    suspend fun setSex(s: Sex?) = context.store.edit { if (s == null) it.remove(sexKey) else it[sexKey] = s.name }
    suspend fun clear() = context.store.edit { it.clear() }
}
