package io.github.smyrna_pergamum.exercisetracker.domain

import java.time.LocalDate

/** What happened on one day, collected from steps, water, workouts, routes and breathing. */
data class DayFacts(
    val date: LocalDate,
    val steps: Long = 0,
    val glasses: Int = 0,
    /** Finished workouts that are not yoga-only. */
    val workouts: Int = 0,
    val sets: Int = 0,
    /** Yoga workouts plus breathing sessions. */
    val mindSessions: Int = 0,
    val routeKm: Double = 0.0,
    val routeMinutes: Int = 0,
    val earliestWorkoutHour: Int? = null,
    val weighed: Boolean = false,
)

data class Goals(val steps: Int, val water: Int, val weightKg: Double? = null)

enum class Quest { STEPS, WATER, MOVE, MIND }

data class Level(val level: Int, val intoLevel: Int, val forNext: Int)

enum class Badge { FIRST_STEPS, STEPS_10K, STEPS_20K, FIRST_WORKOUT, WORKOUTS_10, WORKOUTS_50, FIRST_YOGA, YOGA_10,
    FIRST_ROUTE, ROUTE_5K, TOTAL_42K, HYDRATED, HYDRATED_7, STREAK_3, STREAK_7, STREAK_30, PERFECT_DAY, EARLY_BIRD, LEVEL_5, LEVEL_10,
    FIRST_WEIGH, WEIGH_30, WEIGHT_GOAL }

data class BadgeState(val badge: Badge, val unlocked: Boolean, val progress: Int, val target: Int)

/**
 * Points, levels, streaks and badges. Everything is derived from the stored records, so it stays
 * right after a restore and cannot drift. Rules (shown in the app):
 * - 1 XP per 100 steps (up to 200 a day), 5 XP per glass up to the goal, 2 XP per set,
 *   10 XP per km on a route, 20 XP per yoga or breathing session, 10 XP for a weigh-in;
 * - +25 XP for each daily goal reached, +50 when all four are reached;
 * - a streak day is a day with at least two daily goals reached.
 */
object Game {
    const val STREAK_GOALS = 2
    const val MOVE_MINUTES = 20

    fun done(f: DayFacts, g: Goals): Set<Quest> = buildSet {
        if (f.steps >= g.steps) add(Quest.STEPS)
        if (f.glasses >= g.water) add(Quest.WATER)
        if (f.workouts > 0 || f.routeMinutes >= MOVE_MINUTES) add(Quest.MOVE)
        if (f.mindSessions > 0) add(Quest.MIND)
    }

    fun xp(f: DayFacts, g: Goals): Int {
        val q = done(f, g).size
        return (f.steps / 100).toInt().coerceAtMost(200) +
            f.glasses.coerceAtMost(g.water) * 5 +
            f.sets * 2 +
            (f.routeKm * 10).toInt() +
            f.mindSessions * 20 +
            (if (f.weighed) 10 else 0) +
            q * 25 + if (q == Quest.entries.size) 50 else 0
    }

    /** Level n needs 100 + 50·(n−1) XP to reach level n+1. */
    fun level(totalXp: Int): Level {
        var level = 1
        var left = totalXp
        while (left >= need(level)) { left -= need(level); level++ }
        return Level(level, left, need(level))
    }

    private fun need(level: Int) = 100 + 50 * (level - 1)

    /** Streak days up to today; an unfinished today does not break it. */
    fun streak(days: Map<LocalDate, DayFacts>, g: Goals, today: LocalDate): Int {
        fun ok(d: LocalDate) = days[d]?.let { done(it, g).size >= STREAK_GOALS } == true
        var d = if (ok(today)) today else today.minusDays(1)
        var n = 0
        while (ok(d)) { n++; d = d.minusDays(1) }
        return n
    }

    fun longestStreak(days: Map<LocalDate, DayFacts>, g: Goals): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.keys.sorted()) {
            val ok = done(days.getValue(d), g).size >= STREAK_GOALS
            run = if (ok && prev != null && prev.plusDays(1) == d && run > 0) run + 1 else if (ok) 1 else 0
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** [longestRouteKm] is the longest single route. */
    /** Goal reached when the weight moved from the start to (or past) the goal in the intended direction. */
    fun reached(startKg: Double, nowKg: Double, goalKg: Double): Boolean =
        if (goalKg <= startKg) nowKg <= goalKg else nowKg >= goalKg

    fun badges(days: Map<LocalDate, DayFacts>, g: Goals, longestRouteKm: Double, totalKm: Double, level: Int,
               weighIns: Int = 0, weightGoal: Boolean = false): List<BadgeState> {
        val all = days.values
        val maxSteps = all.maxOfOrNull { it.steps } ?: 0
        val workouts = all.sumOf { it.workouts }
        val mind = all.sumOf { it.mindSessions }
        val routes = all.count { it.routeKm > 0 }
        val waterDays = all.count { it.glasses >= g.water }
        val bestWaterRun = longestRun(days) { it.glasses >= g.water }
        val longest = longestStreak(days, g)
        val perfect = all.any { done(it, g).size == Quest.entries.size }
        val early = all.any { (it.earliestWorkoutHour ?: 24) < 8 }
        fun b(badge: Badge, value: Number, target: Number) =
            BadgeState(badge, value.toDouble() >= target.toDouble(), value.toInt().coerceAtMost(target.toInt()), target.toInt())
        return listOf(
            b(Badge.FIRST_STEPS, maxSteps, 1_000),
            b(Badge.STEPS_10K, maxSteps, 10_000),
            b(Badge.STEPS_20K, maxSteps, 20_000),
            b(Badge.FIRST_WORKOUT, workouts, 1),
            b(Badge.WORKOUTS_10, workouts, 10),
            b(Badge.WORKOUTS_50, workouts, 50),
            b(Badge.FIRST_YOGA, mind, 1),
            b(Badge.YOGA_10, mind, 10),
            b(Badge.FIRST_ROUTE, routes, 1),
            b(Badge.ROUTE_5K, longestRouteKm, 5),
            b(Badge.TOTAL_42K, totalKm, 42),
            b(Badge.HYDRATED, waterDays, 1),
            b(Badge.HYDRATED_7, bestWaterRun, 7),
            b(Badge.STREAK_3, longest, 3),
            b(Badge.STREAK_7, longest, 7),
            b(Badge.STREAK_30, longest, 30),
            b(Badge.PERFECT_DAY, if (perfect) 1 else 0, 1),
            b(Badge.EARLY_BIRD, if (early) 1 else 0, 1),
            b(Badge.LEVEL_5, level, 5),
            b(Badge.LEVEL_10, level, 10),
            b(Badge.FIRST_WEIGH, weighIns, 1),
            b(Badge.WEIGH_30, weighIns, 30),
            b(Badge.WEIGHT_GOAL, if (weightGoal) 1 else 0, 1),
        )
    }

    private fun longestRun(days: Map<LocalDate, DayFacts>, ok: (DayFacts) -> Boolean): Int {
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in days.keys.sorted()) {
            val good = ok(days.getValue(d))
            run = if (good && prev?.plusDays(1) == d) run + 1 else if (good) 1 else 0
            best = maxOf(best, run)
            prev = d
        }
        return best
    }
}
