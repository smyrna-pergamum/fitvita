package io.github.smyrna_pergamum.exercisetracker.backup

import androidx.room.withTransaction
import io.github.smyrna_pergamum.exercisetracker.data.Activity
import io.github.smyrna_pergamum.exercisetracker.data.ActivityType
import io.github.smyrna_pergamum.exercisetracker.data.AppDatabase
import io.github.smyrna_pergamum.exercisetracker.data.Routine
import io.github.smyrna_pergamum.exercisetracker.data.SetLog
import io.github.smyrna_pergamum.exercisetracker.data.SetValues
import io.github.smyrna_pergamum.exercisetracker.data.Status
import io.github.smyrna_pergamum.exercisetracker.data.Mindful
import io.github.smyrna_pergamum.exercisetracker.data.Reminder
import io.github.smyrna_pergamum.exercisetracker.data.StepDay
import io.github.smyrna_pergamum.exercisetracker.data.WaterDay
import io.github.smyrna_pergamum.exercisetracker.data.Workout
import io.github.smyrna_pergamum.exercisetracker.data.WorkoutItem
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import io.github.smyrna_pergamum.exercisetracker.security.BackupCrypto
import io.github.smyrna_pergamum.exercisetracker.security.BackupException
import io.github.smyrna_pergamum.exercisetracker.security.FieldCipher
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class BackupData(
    val schema: String = SCHEMA,
    val version: Int = VERSION,
    val exportedAt: Long,
    val routines: List<Routine> = emptyList(),
    val workouts: List<WorkoutOut> = emptyList(),
    val sets: List<SetOut> = emptyList(),
    val activities: List<ActivityOut> = emptyList(),
    val stepDays: List<StepDay> = emptyList(),
    val water: List<WaterDay> = emptyList(),
    val mindful: List<Mindful> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val weights: List<WeightOut> = emptyList(),
) {
    companion object {
        const val SCHEMA = "exercisetracker.data"
        const val VERSION = 1
    }
}

@Serializable
data class WeightOut(val date: String, val kg: Double)

@Serializable
data class WorkoutOut(val id: String, val title: String, val items: List<WorkoutItem>, val startedAt: Long, val endedAt: Long?, val durationMs: Long?)

@Serializable
data class SetOut(val id: String, val workoutId: String, val itemKey: String, val exerciseId: String, val setOrder: Int, val values: SetValues, val completedAt: Long)

@Serializable
data class ActivityOut(val id: String, val type: ActivityType, val startedAt: Long, val endedAt: Long?, val durationMs: Long, val distanceM: Double, val ascentM: Double, val route: List<Pt>, val steps: Int? = null)

/**
 * Password-protected full backup: plain values inside an encrypted container, so it can be
 * restored on another phone whose device key is different.
 */
class Backup(private val db: AppDatabase, private val cipher: FieldCipher, private val now: () -> Long = System::currentTimeMillis) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val pts = ListSerializer(Pt.serializer())

    suspend fun collect(): BackupData {
        val workouts = db.workouts().all().filter { it.status == Status.FINISHED }
        val ids = workouts.map { it.id }.toSet()
        return BackupData(
            exportedAt = now(),
            routines = db.workouts().allRoutines(),
            workouts = workouts.map { WorkoutOut(it.id, it.title, it.items, it.startedAt, it.endedAt, it.durationMs) },
            sets = db.workouts().allLogs().filter { it.workoutId in ids }.mapNotNull { l ->
                runCatching {
                    SetOut(l.id, l.workoutId, l.itemKey, l.exerciseId, l.setOrder, json.decodeFromString(SetValues.serializer(), cipher.decrypt(l.valuesEnc)), l.completedAt)
                }.getOrNull()
            },
            activities = db.activities().all().filter { it.status == Status.FINISHED && it.routeEnc != null }.mapNotNull { a ->
                runCatching {
                    ActivityOut(a.id, a.type, a.startedAt, a.endedAt, a.durationMs, a.distanceM, a.ascentM, json.decodeFromString(pts, cipher.decrypt(a.routeEnc!!)), a.steps)
                }.getOrNull()
            },
            stepDays = db.steps().all(),
            water = db.health().allWaterOnce(),
            mindful = db.health().allMindful(),
            reminders = db.health().remindersOnce(),
            weights = db.health().allWeights().mapNotNull { w -> runCatching { WeightOut(w.date, cipher.decrypt(w.kgEnc).toDouble()) }.getOrNull() },
        )
    }

    suspend fun export(password: CharArray, iterations: Int = BackupCrypto.DEFAULT_ITERATIONS): ByteArray =
        BackupCrypto.encrypt(json.encodeToString(BackupData.serializer(), collect()).toByteArray(), password, iterations)

    /** Decrypts and checks the file without touching the database. */
    fun read(file: ByteArray, password: CharArray): BackupData {
        if (file.size > MAX_BYTES) throw BackupException("too_large")
        val data = try {
            json.decodeFromString(BackupData.serializer(), BackupCrypto.decrypt(file, password).toString(Charsets.UTF_8))
        } catch (e: BackupException) {
            throw e
        } catch (e: Exception) {
            throw BackupException("bad_format")
        }
        validate(data)
        return data
    }

    /** Replaces all records in one transaction; any failure leaves the current data as it was. */
    suspend fun restore(d: BackupData) {
        validate(d)
        db.withTransaction {
            val conn = db.openHelper.writableDatabase
            listOf("set_log", "workout", "routine", "track_point", "activity", "step_day", "water_day", "mindful", "reminder", "weight").forEach { conn.execSQL("DELETE FROM $it") }
            d.routines.forEach { db.workouts().upsertRoutine(it) }
            d.workouts.forEach { db.workouts().insert(Workout(it.id, it.title, it.items, it.startedAt, it.endedAt, it.durationMs, Status.FINISHED)) }
            d.sets.forEach {
                db.workouts().insertLog(SetLog(it.id, it.workoutId, it.itemKey, it.exerciseId, it.setOrder,
                    cipher.encrypt(json.encodeToString(SetValues.serializer(), it.values)), it.completedAt))
            }
            d.activities.forEach {
                db.activities().insert(Activity(it.id, it.type, Status.FINISHED, it.startedAt, it.endedAt, it.durationMs, it.distanceM, it.ascentM,
                    cipher.encrypt(json.encodeToString(pts, it.route)), it.steps))
            }
            d.stepDays.forEach { db.steps().upsertDay(it) }
            d.water.forEach { db.health().setWater(it) }
            d.mindful.forEach { db.health().addMindful(it) }
            d.reminders.forEach { db.health().setReminder(it) }
            d.weights.forEach { db.health().setWeight(io.github.smyrna_pergamum.exercisetracker.data.WeightEntry(it.date, cipher.encrypt(it.kg.toString()))) }
        }
    }

    companion object {
        const val MAX_BYTES = 64 * 1024 * 1024
        const val MAX_RECORDS = 500_000

        /** Schema, size and reference checks; a broken file is rejected as a whole. */
        fun validate(d: BackupData) {
            if (d.schema != BackupData.SCHEMA) throw BackupException("bad_format")
            if (d.version > BackupData.VERSION) throw BackupException("newer_version")
            if (listOf(d.routines.size, d.workouts.size, d.sets.size, d.activities.size).any { it > MAX_RECORDS }) throw BackupException("too_large")
            val ids = d.workouts.map { it.id }.toSet()
            val ok = ids.size == d.workouts.size &&
                d.sets.all { it.workoutId in ids } &&
                d.sets.map { it.id }.toSet().size == d.sets.size &&
                d.sets.distinctBy { Triple(it.workoutId, it.itemKey, it.setOrder) }.size == d.sets.size &&
                d.activities.map { it.id }.toSet().size == d.activities.size &&
                d.weights.all { it.kg in 20.0..400.0 && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess } &&
                d.stepDays.all { it.steps in 0..500_000 && runCatching { java.time.LocalDate.parse(it.date) }.isSuccess } &&
                d.activities.all { a -> a.route.all { it.lat in -90.0..90.0 && it.lon in -180.0..180.0 } }
            if (!ok) throw BackupException("bad_format")
        }
    }
}
