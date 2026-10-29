package io.github.smyrna_pergamum.exercisetracker.domain

/** Pure step arithmetic, kept apart from the sensor so it can be tested. */
object Steps {
    /** Readings further apart than this are not trusted to be one walk (sensor glitches). */
    const val MAX_DELTA = 60_000L

    /**
     * Steps between two readings of the hardware counter. The counter restarts from zero after a
     * reboot, so a smaller value means "steps since boot". The first reading only sets the base.
     */
    fun delta(previous: Long?, current: Long): Long = when {
        previous == null -> 0
        current >= previous -> current - previous
        else -> current
    }.coerceIn(0, MAX_DELTA)

    /** Distance walked, from the number of steps and the step length in centimetres. */
    fun distanceM(steps: Long, strideCm: Int): Double = steps * strideCm / 100.0
}
