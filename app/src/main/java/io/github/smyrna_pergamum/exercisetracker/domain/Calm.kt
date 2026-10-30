package io.github.smyrna_pergamum.exercisetracker.domain

import io.github.smyrna_pergamum.exercisetracker.R

enum class Breath(val text: Int) { IN(R.string.breath_in), HOLD(R.string.breath_hold), OUT(R.string.breath_out) }

data class Phase(val breath: Breath, val seconds: Int)

/**
 * A calming exercise: either a breathing pattern repeated for some minutes, or a short series of
 * spoken-style prompts shown one after another.
 */
data class Calm(
    val id: String,
    val title: Int,
    val hint: Int,
    val cycle: List<Phase> = emptyList(),
    val prompts: List<Int> = emptyList(),
    val promptSec: Int = 30,
) {
    val breathing get() = cycle.isNotEmpty()
    val cycleSec get() = cycle.sumOf { it.seconds }

    /** Length in seconds; breathing patterns last [minutes], prompt series their own length. */
    fun seconds(minutes: Int) = if (breathing) minutes * 60 else prompts.size * promptSec

    /** Breathing phase and seconds into it at [t] seconds. */
    fun phaseAt(t: Int): Pair<Phase, Int> {
        var r = t % cycleSec
        for (p in cycle) {
            if (r < p.seconds) return p to r
            r -= p.seconds
        }
        return cycle.last() to 0
    }
}

/** A meditation routine: calming exercises in a row, with an optional nature sound behind them. */
data class MeditationRoutine(val title: Int, val hint: Int, val steps: List<Pair<String, Int>>, val sound: Int?)

object Calms {
    val all = listOf(
        Calm("calm", R.string.calm_breath, R.string.calm_breath_hint, cycle = listOf(Phase(Breath.IN, 4), Phase(Breath.OUT, 6))),
        Calm("box", R.string.calm_box, R.string.calm_box_hint,
            cycle = listOf(Phase(Breath.IN, 4), Phase(Breath.HOLD, 4), Phase(Breath.OUT, 4), Phase(Breath.HOLD, 4))),
        Calm("478", R.string.calm_478, R.string.calm_478_hint, cycle = listOf(Phase(Breath.IN, 4), Phase(Breath.HOLD, 7), Phase(Breath.OUT, 8))),
        Calm("body_scan", R.string.calm_body_scan, R.string.calm_body_scan_hint,
            prompts = listOf(R.string.scan_1, R.string.scan_2, R.string.scan_3, R.string.scan_4, R.string.scan_5, R.string.scan_6)),
        Calm("grounding", R.string.calm_grounding, R.string.calm_grounding_hint,
            prompts = listOf(R.string.ground_1, R.string.ground_2, R.string.ground_3, R.string.ground_4, R.string.ground_5)),
        Calm("relax", R.string.calm_relax, R.string.calm_relax_hint,
            prompts = listOf(R.string.relax_1, R.string.relax_2, R.string.relax_3, R.string.relax_4, R.string.relax_5, R.string.relax_6)),
        Calm("gratitude", R.string.calm_gratitude, R.string.calm_gratitude_hint,
            prompts = listOf(R.string.thanks_1, R.string.thanks_2, R.string.thanks_3), promptSec = 40),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String) = byId[id]

    /** Nature sound index in Tracks.nature: 0 birds, 2 water, 3 forest, 6 rain. */
    val routines = listOf(
        MeditationRoutine(R.string.med_morning, R.string.med_morning_hint, listOf("box" to 2, "gratitude" to 0), sound = 0),
        MeditationRoutine(R.string.med_stress, R.string.med_stress_hint, listOf("grounding" to 0, "calm" to 3), sound = 2),
        MeditationRoutine(R.string.med_focus, R.string.med_focus_hint, listOf("box" to 3, "body_scan" to 0), sound = 3),
        MeditationRoutine(R.string.med_sleep, R.string.med_sleep_hint, listOf("478" to 3, "relax" to 0, "body_scan" to 0), sound = 6),
    )
}
