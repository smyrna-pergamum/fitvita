package io.github.smyrna_pergamum.exercisetracker.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

/** A saved list of exercises to start a workout from. */
@Serializable
@Entity(tableName = "routine")
data class Routine(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val exerciseIds: List<String>,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One exercise inside a workout; [key] keeps two entries of the same exercise apart. */
@Serializable
data class WorkoutItem(val key: String = newId(), val exerciseId: String, val sets: Int = 3)

enum class Status { ACTIVE, FINISHED }

/** Rest timer as an end point on one boot's monotonic clock (survives screen-off and restarts of the app). */
@Serializable
data class RestState(val totalMs: Long, val endsAtElapsed: Long, val endsAtWall: Long, val bootCount: Int)

@Entity(tableName = "workout", indices = [Index("status"), Index("startedAt")])
data class Workout(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val items: List<WorkoutItem>,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long? = null,
    val status: Status,
    val rest: RestState? = null,
)

/**
 * A completed set. The values are encrypted on the device; the unique index makes a double tap a
 * no-op.
 */
@Entity(
    tableName = "set_log",
    foreignKeys = [ForeignKey(entity = Workout::class, parentColumns = ["id"], childColumns = ["workoutId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["workoutId", "itemKey", "setOrder"], unique = true), Index(value = ["exerciseId", "completedAt"])],
)
data class SetLog(
    @PrimaryKey val id: String = newId(),
    val workoutId: String,
    val itemKey: String,
    val exerciseId: String,
    val setOrder: Int,
    val valuesEnc: String,
    val completedAt: Long,
)

/** Reps or seconds, and an optional load in kg. An empty load is not the same as 0 kg. */
@Serializable
data class SetValues(val reps: Int? = null, val seconds: Int? = null, val loadKg: Double? = null)

enum class ActivityType { WALK, HIKE, JOG, RUN }

/** An outdoor activity recorded with GPS. The finished route is stored encrypted in [routeEnc]. */
@Entity(tableName = "activity", indices = [Index("status"), Index("startedAt")])
data class Activity(
    @PrimaryKey val id: String = newId(),
    val type: ActivityType,
    val status: Status,
    val startedAt: Long,
    val endedAt: Long? = null,
    val durationMs: Long = 0,
    val distanceM: Double = 0.0,
    val ascentM: Double = 0.0,
    val routeEnc: String? = null,
    /** Steps from the phone's step sensor during the activity; null without a sensor or permission. */
    val steps: Int? = null,
)

/** Raw GPS fix while an activity is being recorded; folded into [Activity.routeEnc] when it ends. */
@Entity(
    tableName = "track_point",
    primaryKeys = ["activityId", "seq"],
    foreignKeys = [ForeignKey(entity = Activity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE)],
)
data class TrackPoint(
    val activityId: String,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val alt: Double?,
    val time: Long,
    /** A new segment starts after every pause, so the route is not drawn across the gap. */
    val segment: Int,
)

/** Steps counted on one local day ("2026-10-29"). */
@Serializable
@Entity(tableName = "step_day")
data class StepDay(@PrimaryKey val date: String, val steps: Int)

/**
 * Last reading of the hardware step counter, which counts up from zero since the last boot.
 * Each new reading adds the difference to the current day.
 */
@Entity(tableName = "step_state")
data class StepState(@PrimaryKey val id: Int = 0, val counter: Long, val time: Long)

/** Glasses of water drunk on one local day. */
@Serializable
@Entity(tableName = "water_day")
data class WaterDay(@PrimaryKey val date: String, val glasses: Int)

/** A finished breathing session. */
@Serializable
@Entity(tableName = "mindful", indices = [Index("startedAt")])
data class Mindful(@PrimaryKey val id: String = newId(), val startedAt: Long, val durationSec: Int)

enum class ReminderType { WATER, MOVE, WORKOUT, YOGA }

/**
 * A local reminder. [days] is a bitmask, Monday = bit 0. With [intervalH] > 0 it repeats every
 * that many hours from [hour] until 21:00 (water); otherwise it fires once at [hour]:[minute].
 */
@Serializable
@Entity(tableName = "reminder")
data class Reminder(
    @PrimaryKey val type: ReminderType,
    val hour: Int,
    val minute: Int = 0,
    val days: Int = 0x7F,
    val intervalH: Int = 0,
    val enabled: Boolean = false,
)

/** Body weight on one local day; the value is encrypted like other health data. */
@Entity(tableName = "weight")
data class WeightEntry(@PrimaryKey val date: String, val kgEnc: String)
