package io.github.smyrna_pergamum.exercisetracker.domain

import io.github.smyrna_pergamum.exercisetracker.data.RestState

/** Wall clock, monotonic clock and boot identity; a fake is used in tests. */
interface Clock {
    fun wall(): Long
    /** Monotonic, counts deep sleep (SystemClock.elapsedRealtime). */
    fun elapsed(): Long
    /** Settings.Global.BOOT_COUNT, or -1 when unknown. */
    fun bootCount(): Int
}

/** Rest timer: the remaining time is always recomputed from the end point, never counted down. */
object RestTimer {
    fun start(totalMs: Long, clock: Clock) =
        RestState(totalMs, clock.elapsed() + totalMs, clock.wall() + totalMs, clock.bootCount())

    /** Remaining milliseconds, 0 when over, or null when the device restarted since (no stale timer). */
    fun remaining(r: RestState, clock: Clock): Long? {
        val boot = clock.bootCount()
        val sameBoot = if (r.bootCount != -1 && boot != -1) r.bootCount == boot else clock.elapsed() >= r.endsAtElapsed - r.totalMs
        if (!sameBoot) return null
        return (r.endsAtElapsed - clock.elapsed()).coerceAtLeast(0)
    }

    fun add(r: RestState, deltaMs: Long, clock: Clock): RestState {
        val now = clock.elapsed()
        val end = (r.endsAtElapsed + deltaMs).coerceAtLeast(now)
        return r.copy(totalMs = (r.totalMs + deltaMs).coerceAtLeast(0), endsAtElapsed = end, endsAtWall = clock.wall() + (end - now))
    }
}

fun formatDuration(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
}
