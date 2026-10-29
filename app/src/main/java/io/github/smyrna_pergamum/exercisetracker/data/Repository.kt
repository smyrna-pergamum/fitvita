package io.github.smyrna_pergamum.exercisetracker.data

import androidx.room.withTransaction
import io.github.smyrna_pergamum.exercisetracker.domain.Catalog
import io.github.smyrna_pergamum.exercisetracker.domain.Clock
import io.github.smyrna_pergamum.exercisetracker.domain.Geo
import io.github.smyrna_pergamum.exercisetracker.domain.Measure
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import io.github.smyrna_pergamum.exercisetracker.domain.RestTimer
import io.github.smyrna_pergamum.exercisetracker.security.FieldCipher
import io.github.smyrna_pergamum.exercisetracker.domain.DayFacts
import io.github.smyrna_pergamum.exercisetracker.domain.Game
import io.github.smyrna_pergamum.exercisetracker.domain.Goals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json

class Repository(val db: AppDatabase, private val cipher: FieldCipher, private val clock: Clock) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ptList = ListSerializer(Pt.serializer())

    // ---------- workouts ----------

    fun routines() = db.workouts().routines()
    fun activeWorkout() = db.workouts().activeFlow()
    fun workout(id: String) = db.workouts().workout(id)
    fun logs(id: String) = db.workouts().logs(id)
    fun finishedWorkouts() = db.workouts().finished()
    fun setCounts() = db.workouts().setCounts()

    /** Only one workout is open at a time; an open one is simply returned. */
    suspend fun startWorkout(title: String, exerciseIds: List<String>): String = db.withTransaction {
        db.workouts().active()?.let { return@withTransaction it.id }
        val w = Workout(title = title, items = exerciseIds.map { WorkoutItem(exerciseId = it) }, startedAt = clock.wall(), status = Status.ACTIVE)
        db.workouts().insert(w)
        w.id
    }

    suspend fun addExercise(exerciseId: String, emptyTitle: String): String {
        val id = startWorkout(emptyTitle, emptyList())
        edit(id) { it.copy(items = it.items + WorkoutItem(exerciseId = exerciseId)) }
        return id
    }

    suspend fun addSet(id: String, key: String) = edit(id) { w -> w.copy(items = w.items.map { if (it.key == key) it.copy(sets = it.sets + 1) else it }) }

    suspend fun removeItem(id: String, key: String) = edit(id) { w -> w.copy(items = w.items.filter { it.key != key }) }

    private suspend fun edit(id: String, f: (Workout) -> Workout) = db.withTransaction {
        val w = db.workouts().get(id) ?: return@withTransaction
        db.workouts().update(f(w))
    }

    /** Stores a set atomically and starts the rest timer. Returns false for a repeated tap. */
    suspend fun completeSet(id: String, item: WorkoutItem, order: Int, values: SetValues, restSec: Int): Boolean = db.withTransaction {
        val w = db.workouts().get(id) ?: return@withTransaction false
        val row = SetLog(workoutId = id, itemKey = item.key, exerciseId = item.exerciseId, setOrder = order,
            valuesEnc = cipher.encrypt(json.encodeToString(SetValues.serializer(), values)), completedAt = clock.wall())
        val stored = db.workouts().insertLog(row) != -1L
        if (stored) db.workouts().update(w.copy(rest = if (restSec > 0) RestTimer.start(restSec * 1000L, clock) else null))
        stored
    }

    suspend fun undoSet(id: String, key: String, order: Int) = db.workouts().deleteLog(id, key, order)

    suspend fun setRest(id: String, f: (RestState?) -> RestState?) = edit(id) { it.copy(rest = f(it.rest)) }

    fun values(l: SetLog): SetValues? = runCatching { json.decodeFromString(SetValues.serializer(), cipher.decrypt(l.valuesEnc)) }.getOrNull()

    /** Last values per set number, for pre-filling. */
    suspend fun lastValues(exerciseId: String): Map<Int, SetValues> {
        val recent = db.workouts().recentFor(exerciseId)
        val lastWorkout = recent.firstOrNull()?.workoutId ?: return emptyMap()
        return recent.filter { it.workoutId == lastWorkout }.mapNotNull { l -> values(l)?.let { l.setOrder to it } }.toMap()
    }

    suspend fun finishWorkout(id: String, saveAsRoutine: String?) = db.withTransaction {
        val w = db.workouts().get(id) ?: return@withTransaction
        val end = clock.wall()
        db.workouts().update(w.copy(status = Status.FINISHED, endedAt = end, durationMs = end - w.startedAt, rest = null))
        if (!saveAsRoutine.isNullOrBlank() && w.items.isNotEmpty()) {
            db.workouts().upsertRoutine(Routine(name = saveAsRoutine.trim().take(60), exerciseIds = w.items.map { it.exerciseId }))
        }
    }

    suspend fun deleteWorkout(id: String) = db.workouts().delete(id)
    suspend fun deleteRoutine(id: String) = db.workouts().deleteRoutine(id)

    fun defaultValues(exerciseId: String): SetValues =
        when {
            Catalog.isYoga(exerciseId) -> SetValues(seconds = 45)
            Catalog.get(exerciseId)?.measure == Measure.TIME -> SetValues(seconds = 30)
            else -> SetValues(reps = 10)
        }

    // ---------- activities ----------

    fun finishedActivities() = db.activities().finished()
    fun activity(id: String) = db.activities().activity(id)

    suspend fun route(a: Activity): List<Pt> = when {
        a.routeEnc != null -> runCatching { json.decodeFromString(ptList, cipher.decrypt(a.routeEnc)) }.getOrDefault(emptyList())
        else -> db.activities().points(a.id).map { Pt(it.lat, it.lon, it.alt, it.time, it.segment) }
    }

    /**
     * Ends recording: the raw points are folded into one encrypted route and deleted, and the
     * totals are recomputed from the kept points. An empty recording is discarded.
     */
    suspend fun finishActivity(id: String, durationMs: Long, steps: Int? = null) = db.withTransaction {
        val a = db.activities().get(id) ?: return@withTransaction
        val pts = db.activities().points(id).map { Pt(it.lat, it.lon, it.alt, it.time, it.segment) }
        if (pts.size < 2) {
            db.activities().delete(id)
            return@withTransaction
        }
        db.activities().update(
            a.copy(
                status = Status.FINISHED, endedAt = pts.last().t, durationMs = durationMs,
                distanceM = Geo.length(pts), ascentM = Geo.ascent(pts), routeEnc = cipher.encrypt(json.encodeToString(ptList, pts)), steps = steps,
            )
        )
        db.activities().deletePoints(id)
    }

    /** After a crash or forced stop, recordings left open are closed with the points they have. */
    suspend fun closeOpenActivities() {
        db.activities().active().forEach { a ->
            val pts = db.activities().points(a.id)
            val active = pts.groupBy { it.segment }.values.sumOf { s -> s.last().time - s.first().time }
            finishActivity(a.id, active)
        }
    }

    suspend fun deleteActivity(id: String) = db.activities().delete(id)

    // ---------- steps ----------

    /** Daily step totals from [from] (ISO date) on. */
    fun stepDays(from: String) = db.steps().since(from)

    // ---------- water & breathing ----------

    fun water(date: LocalDate) = db.health().water(date.toString())

    suspend fun addWater(delta: Int, date: LocalDate = LocalDate.now()): Int = db.withTransaction {
        val now = db.health().waterOnce(date.toString())?.glasses ?: 0
        val next = (now + delta).coerceIn(0, 40)
        db.health().setWater(WaterDay(date.toString(), next))
        next
    }

    suspend fun addMindful(startedAt: Long, durationSec: Int) = db.health().addMindful(Mindful(startedAt = startedAt, durationSec = durationSec))

    // ---------- body weight ----------

    data class Weight(val date: LocalDate, val kg: Double)

    fun weights(): Flow<List<Weight>> = db.health().weights().map { list ->
        list.mapNotNull { w -> runCatching { Weight(LocalDate.parse(w.date), cipher.decrypt(w.kgEnc).toDouble()) }.getOrNull() }
    }

    /** One value per day; logging again the same day replaces it. */
    suspend fun logWeight(kg: Double, date: LocalDate = LocalDate.now()) =
        db.health().setWeight(WeightEntry(date.toString(), cipher.encrypt(kg.toString())))

    suspend fun deleteWeight(date: LocalDate) = db.health().deleteWeight(date.toString())

    // ---------- game ----------

    /** Everything the reward screens need, recomputed whenever a record changes. */
    fun game(goals: Flow<Goals>): Flow<GameState> {
        val zone = ZoneId.systemDefault()
        fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val sources = combine(
            db.steps().since("0000"), db.health().allWater(), db.workouts().finished(), db.workouts().setCounts(), db.activities().finished(),
        ) { steps, water, workouts, counts, activities -> Sources(steps, water, workouts, counts, activities) }
        return combine(sources, db.health().mindful(), goals, weights()) { src, mindful, g, weights ->
            val sets = src.counts.associate { it.workoutId to it.n }
            val facts = HashMap<LocalDate, DayFacts>()
            fun upd(d: LocalDate, f: (DayFacts) -> DayFacts) { facts[d] = f(facts[d] ?: DayFacts(d)) }
            src.steps.forEach { s -> runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { d -> upd(d) { it.copy(steps = s.steps.toLong()) } } }
            src.water.forEach { w -> runCatching { LocalDate.parse(w.date) }.getOrNull()?.let { d -> upd(d) { it.copy(glasses = w.glasses) } } }
            src.workouts.forEach { w ->
                val d = day(w.startedAt)
                val yoga = w.items.isNotEmpty() && w.items.all { Catalog.isYoga(it.exerciseId) }
                val hour = Instant.ofEpochMilli(w.startedAt).atZone(zone).hour
                upd(d) {
                    if (yoga) it.copy(mindSessions = it.mindSessions + 1, sets = it.sets + (sets[w.id] ?: 0))
                    else it.copy(workouts = it.workouts + 1, sets = it.sets + (sets[w.id] ?: 0),
                        earliestWorkoutHour = minOf(it.earliestWorkoutHour ?: 24, hour))
                }
            }
            src.activities.forEach { a ->
                upd(day(a.startedAt)) { it.copy(routeKm = it.routeKm + a.distanceM / 1000, routeMinutes = it.routeMinutes + (a.durationMs / 60_000).toInt()) }
            }
            mindful.forEach { m -> upd(day(m.startedAt)) { it.copy(mindSessions = it.mindSessions + 1) } }
            weights.forEach { w -> upd(w.date) { it.copy(weighed = true) } }
            val latest = weights.lastOrNull()?.kg
            val goalReached = g.weightKg != null && latest != null && weights.size >= 2 &&
                Game.reached(weights.first().kg, latest, g.weightKg)
            val today = LocalDate.now(zone)
            val todayFacts = facts[today] ?: DayFacts(today)
            val total = facts.values.sumOf { Game.xp(it, g) }
            val level = Game.level(total)
            GameState(
                today = todayFacts, goals = g, done = Game.done(todayFacts, g), xpToday = Game.xp(todayFacts, g), xpTotal = total,
                level = level, streak = Game.streak(facts, g, today),
                badges = Game.badges(facts, g, src.activities.maxOfOrNull { it.distanceM / 1000 } ?: 0.0,
                    src.activities.sumOf { it.distanceM } / 1000, level.level, weights.size, goalReached),
            )
        }
    }

    private data class Sources(val steps: List<StepDay>, val water: List<WaterDay>, val workouts: List<Workout>, val counts: List<SetCount>, val activities: List<Activity>)
}

data class GameState(
    val today: DayFacts,
    val goals: Goals,
    val done: Set<io.github.smyrna_pergamum.exercisetracker.domain.Quest>,
    val xpToday: Int,
    val xpTotal: Int,
    val level: io.github.smyrna_pergamum.exercisetracker.domain.Level,
    val streak: Int,
    val badges: List<io.github.smyrna_pergamum.exercisetracker.domain.BadgeState>,
)
