package io.github.smyrna_pergamum.exercisetracker.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One route point: [s] is the segment number; a pause starts a new segment. */
@Serializable
data class Pt(val lat: Double, val lon: Double, val alt: Double? = null, val t: Long, val s: Int = 0)

object Geo {
    private const val R = 6_371_000.0

    fun distance(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Distance along the route; gaps between segments (pauses) are not counted. */
    fun length(pts: List<Pt>): Double =
        pts.zipWithNext().sumOf { (a, b) -> if (a.s == b.s) distance(a.lat, a.lon, b.lat, b.lon) else 0.0 }

    /**
     * Total climb with a dead band, so GPS altitude noise of a few metres is not summed up as
     * ascent. Only rises larger than [threshold] from the last low point count.
     */
    fun ascent(pts: List<Pt>, threshold: Double = 4.0): Double {
        var total = 0.0
        var ref: Double? = null
        var seg = -1
        for (p in pts) {
            val a = p.alt ?: continue
            if (p.s != seg) { seg = p.s; ref = a; continue }
            val r = ref ?: a
            when {
                a - r >= threshold -> { total += a - r; ref = a }
                a < r -> ref = a
            }
        }
        return total
    }

    /** Seconds per kilometre, or null when too short to be meaningful. */
    fun paceSecPerKm(distanceM: Double, durationMs: Long): Long? =
        if (distanceM < 50 || durationMs <= 0) null else (durationMs / 1000.0 / (distanceM / 1000.0)).toLong()

    fun formatPace(secPerKm: Long?): String = secPerKm?.let { "%d:%02d".format(it / 60, it % 60) } ?: "–"

    /** GPX 1.1 track, one <trkseg> per segment. */
    fun gpx(pts: List<Pt>, name: String, creator: String): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        append("""<gpx version="1.1" creator="${esc(creator)}" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
        append("<trk><name>").append(esc(name)).append("</name>\n")
        pts.groupBy { it.s }.toSortedMap().values.forEach { seg ->
            append("<trkseg>\n")
            seg.forEach { p ->
                append("""<trkpt lat="${"%.7f".format(java.util.Locale.ROOT, p.lat)}" lon="${"%.7f".format(java.util.Locale.ROOT, p.lon)}">""")
                p.alt?.let { append("<ele>").append("%.1f".format(java.util.Locale.ROOT, it)).append("</ele>") }
                append("<time>").append(Instant.ofEpochMilli(p.t)).append("</time></trkpt>\n")
            }
            append("</trkseg>\n")
        }
        append("</trk>\n</gpx>\n")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * Decides which GPS fixes are kept while recording: inaccurate fixes and sub-metre jitter while
 * standing still are dropped, and so are jumps no walker or runner could make.
 */
class TrackFilter(private val maxAccuracyM: Float = 25f, private val minStepM: Double = 3.0, private val maxSpeedMs: Double = 12.0) {
    private var last: Pt? = null

    fun reset() { last = null }

    fun accept(p: Pt, accuracyM: Float?): Boolean {
        if (accuracyM != null && accuracyM > maxAccuracyM) return false
        val l = last
        if (l == null || l.s != p.s) { last = p; return true }
        val d = Geo.distance(l.lat, l.lon, p.lat, p.lon)
        if (d < minStepM) return false
        val dt = (p.t - l.t) / 1000.0
        if (dt > 0 && d / dt > maxSpeedMs) return false
        if (abs(dt) < 0.001) return false
        last = p
        return true
    }
}
