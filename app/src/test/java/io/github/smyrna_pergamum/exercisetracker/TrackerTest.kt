package io.github.smyrna_pergamum.exercisetracker

import io.github.smyrna_pergamum.exercisetracker.backup.BackupData
import io.github.smyrna_pergamum.exercisetracker.backup.SetOut
import io.github.smyrna_pergamum.exercisetracker.backup.WorkoutOut
import io.github.smyrna_pergamum.exercisetracker.data.SetValues
import io.github.smyrna_pergamum.exercisetracker.domain.Catalog
import io.github.smyrna_pergamum.exercisetracker.domain.Clock
import io.github.smyrna_pergamum.exercisetracker.domain.Geo
import io.github.smyrna_pergamum.exercisetracker.domain.Pt
import io.github.smyrna_pergamum.exercisetracker.domain.RestTimer
import io.github.smyrna_pergamum.exercisetracker.domain.TrackFilter
import io.github.smyrna_pergamum.exercisetracker.domain.Units
import io.github.smyrna_pergamum.exercisetracker.domain.WeightUnit
import io.github.smyrna_pergamum.exercisetracker.security.AesGcmFieldCipher
import io.github.smyrna_pergamum.exercisetracker.security.BackupCrypto
import io.github.smyrna_pergamum.exercisetracker.security.BackupException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Locale
import javax.crypto.spec.SecretKeySpec

class FakeClock(var wallMs: Long = 1_700_000_000_000, var elapsedMs: Long = 50_000, var boot: Int = 3) : Clock {
    override fun wall() = wallMs
    override fun elapsed() = elapsedMs
    override fun bootCount() = boot
    fun advance(ms: Long) { wallMs += ms; elapsedMs += ms }
}

class GeoTest {
    @Test fun oneDegreeOfLatitude() {
        assertEquals(111_195.0, Geo.distance(40.0, 29.0, 41.0, 29.0), 50.0)
    }

    @Test fun pausesAreNotCountedAsDistance() {
        val a = Pt(41.0, 29.0, t = 0, s = 0)
        val b = Pt(41.001, 29.0, t = 10_000, s = 0)
        val c = Pt(41.01, 29.0, t = 20_000, s = 1) // after a pause, far away
        val d = Pt(41.011, 29.0, t = 30_000, s = 1)
        val len = Geo.length(listOf(a, b, c, d))
        assertEquals(2 * 111.2, len, 1.0)
    }

    @Test fun altitudeNoiseIsNotClimb() {
        val noisy = (0 until 50).map { Pt(41.0, 29.0, alt = 100.0 + (it % 2) * 2.5, t = it * 1000L) }
        assertEquals(0.0, Geo.ascent(noisy), 0.0)
        val hill = listOf(100.0, 103.0, 106.0, 104.0, 112.0).mapIndexed { i, a -> Pt(41.0, 29.0, a, i * 1000L) }
        assertEquals(14.0, Geo.ascent(hill), 0.01) // 100→106 and 104→112
    }

    @Test fun pace() {
        assertEquals(360L, Geo.paceSecPerKm(5000.0, 30 * 60_000L))
        assertEquals("6:00", Geo.formatPace(360))
        assertNull(Geo.paceSecPerKm(10.0, 60_000))
    }

    @Test fun gpxHasSegmentsAndEscapes() {
        val g = Geo.gpx(listOf(Pt(41.0, 29.0, 10.0, 0, 0), Pt(41.1, 29.1, null, 1000, 1)), "Walk <1> & more", "App")
        assertEquals(2, Regex("<trkseg>").findAll(g).count())
        assertTrue(g.contains("Walk &lt;1&gt; &amp; more"))
        assertTrue(g.contains("lat=\"41.0000000\" lon=\"29.0000000\""))
        assertTrue(g.contains("<ele>10.0</ele>"))
    }
}

class TrackFilterTest {
    @Test fun dropsInaccurateJitterAndJumps() {
        val f = TrackFilter()
        assertTrue(f.accept(Pt(41.0, 29.0, t = 0), 5f))
        assertFalse(f.accept(Pt(41.00001, 29.0, t = 2000), 5f)) // ~1 m jitter
        assertFalse(f.accept(Pt(41.0005, 29.0, t = 4000), 60f)) // inaccurate
        assertFalse(f.accept(Pt(41.01, 29.0, t = 6000), 5f)) // 1.1 km in 6 s
        assertTrue(f.accept(Pt(41.0001, 29.0, t = 8000), 5f)) // 11 m in 8 s
    }

    @Test fun newSegmentAlwaysStartsFresh() {
        val f = TrackFilter()
        f.accept(Pt(41.0, 29.0, t = 0, s = 0), 5f)
        assertTrue(f.accept(Pt(41.02, 29.0, t = 1000, s = 1), 5f))
    }
}

class RestTimerTest {
    @Test fun recomputedFromMonotonicClock() {
        val c = FakeClock()
        val r = RestTimer.start(60_000, c)
        c.advance(45_000)
        assertEquals(15_000L, RestTimer.remaining(r, c))
        c.wallMs -= 3_600_000 // clock change does not matter
        c.advance(20_000)
        assertEquals(0L, RestTimer.remaining(r, c))
    }

    @Test fun rebootDropsTimer() {
        val c = FakeClock()
        val r = RestTimer.start(60_000, c)
        c.boot = 4; c.elapsedMs = 1_000
        assertNull(RestTimer.remaining(r, c))
    }

    @Test fun addTime() {
        val c = FakeClock()
        val r = RestTimer.add(RestTimer.start(30_000, c), 15_000, c)
        assertEquals(45_000L, RestTimer.remaining(r, c))
    }
}

class UnitsTest {
    private val en = Locale.ENGLISH

    @Test fun emptyLoadIsNotZero() {
        assertEquals(Units.ParseResult.Ok(null), Units.parseLoad("", WeightUnit.KG, null, en))
        assertEquals(Units.ParseResult.Ok(0.0), Units.parseLoad("0", WeightUnit.KG, null, en))
    }

    @Test fun unchangedInputKeepsStoredValue() {
        var stored = 61.3
        repeat(500) {
            stored = (Units.parseLoad(Units.format(stored, WeightUnit.LB, en)!!, WeightUnit.LB, stored, en) as Units.ParseResult.Ok).kg!!
        }
        assertEquals(61.3, stored, 0.0)
    }
}

class CatalogTest {
    @Test fun everyExerciseIsComplete() {
        assertTrue(Catalog.all.size >= 20)
        assertEquals(Catalog.all.size, Catalog.all.map { it.id }.toSet().size)
        Catalog.all.forEach { assertTrue(it.imageA != 0 && it.imageB != 0 && it.title != 0 && it.steps != 0) }
    }
}

class SecurityTest {
    private val cipher = AesGcmFieldCipher(SecretKeySpec(ByteArray(32) { it.toByte() }, "AES"))

    @Test fun fieldRoundTrip() {
        val a = cipher.encrypt("41.0,29.0")
        assertTrue(a != cipher.encrypt("41.0,29.0"))
        assertEquals("41.0,29.0", cipher.decrypt(a))
    }

    @Test fun backupRoundTripAndWrongPassword() {
        val f = BackupCrypto.encrypt("{}".toByteArray(), "password1".toCharArray(), 20_000)
        assertArrayEquals("{}".toByteArray(), BackupCrypto.decrypt(f, "password1".toCharArray()))
        try { BackupCrypto.decrypt(f, "password2".toCharArray()); fail() } catch (e: BackupException) { assertEquals("wrong_password", e.reason) }
    }

    @Test fun backupValidationRejectsBrokenReferences() {
        val b = io.github.smyrna_pergamum.exercisetracker.backup.Backup
        val ok = BackupData(exportedAt = 0, workouts = listOf(WorkoutOut("w", "t", emptyList(), 0, 1, 1)),
            sets = listOf(SetOut("s", "w", "k", "squat", 0, SetValues(reps = 10), 1)))
        b.validate(ok)
        try { b.validate(ok.copy(sets = listOf(SetOut("s", "missing", "k", "squat", 0, SetValues(reps = 1), 1)))); fail() } catch (e: BackupException) { }
        try { b.validate(ok.copy(version = 99)); fail() } catch (e: BackupException) { assertEquals("newer_version", e.reason) }
    }

}

class StepsTest {
    @Test fun firstReadingOnlySetsBase() {
        assertEquals(0L, io.github.smyrna_pergamum.exercisetracker.domain.Steps.delta(null, 12_345))
    }

    @Test fun normalIncrease() {
        assertEquals(250L, io.github.smyrna_pergamum.exercisetracker.domain.Steps.delta(1_000, 1_250))
    }

    /** After a reboot the hardware counter starts again at zero. */
    @Test fun rebootCountsStepsSinceBoot() {
        assertEquals(40L, io.github.smyrna_pergamum.exercisetracker.domain.Steps.delta(98_000, 40))
    }

    @Test fun implausibleJumpIsCapped() {
        assertEquals(io.github.smyrna_pergamum.exercisetracker.domain.Steps.MAX_DELTA,
            io.github.smyrna_pergamum.exercisetracker.domain.Steps.delta(0, 5_000_000))
    }

    @Test fun distanceFromStride() {
        assertEquals(4_368.0, io.github.smyrna_pergamum.exercisetracker.domain.Steps.distanceM(6_240, 70), 0.001)
    }
}

class GameTest {
    private val g = io.github.smyrna_pergamum.exercisetracker.domain.Goals(steps = 8000, water = 8)
    private val Game = io.github.smyrna_pergamum.exercisetracker.domain.Game
    private fun day(d: String, steps: Long = 0, glasses: Int = 0, workouts: Int = 0, mind: Int = 0, routeMin: Int = 0, sets: Int = 0, km: Double = 0.0) =
        io.github.smyrna_pergamum.exercisetracker.domain.DayFacts(java.time.LocalDate.parse(d), steps, glasses, workouts, sets, mind, km, routeMin)

    @Test fun questsAndXp() {
        val f = day("2026-10-29", steps = 8_500, glasses = 8, workouts = 1, sets = 6, mind = 1)
        assertEquals(4, Game.done(f, g).size)
        // 85 + 40 + 12 + 0 + 20 + 4*25 + 50
        assertEquals(307, Game.xp(f, g))
    }

    @Test fun twentyMinutesOutsideCountsAsMove() {
        assertTrue(io.github.smyrna_pergamum.exercisetracker.domain.Quest.MOVE in Game.done(day("2026-10-29", routeMin = 20), g))
        assertFalse(io.github.smyrna_pergamum.exercisetracker.domain.Quest.MOVE in Game.done(day("2026-10-29", routeMin = 19), g))
    }

    @Test fun stepXpIsCapped() {
        assertEquals(200 + 25, Game.xp(day("2026-10-29", steps = 50_000), g))
    }

    @Test fun levels() {
        assertEquals(1, Game.level(0).level)
        assertEquals(2, Game.level(100).level)
        assertEquals(0, Game.level(100).intoLevel)
        assertEquals(150, Game.level(100).forNext)
        assertEquals(3, Game.level(250).level)
    }

    @Test fun streakIgnoresUnfinishedToday() {
        val two = { d: String -> day(d, steps = 9_000, glasses = 8) }
        val days = listOf(two("2026-10-26"), two("2026-10-27"), two("2026-10-28"), day("2026-10-29", glasses = 2)).associateBy { it.date }
        assertEquals(3, Game.streak(days, g, java.time.LocalDate.parse("2026-10-29")))
        val broken = days - java.time.LocalDate.parse("2026-10-27")
        assertEquals(1, Game.streak(broken, g, java.time.LocalDate.parse("2026-10-29")))
    }

    @Test fun badges() {
        val days = listOf(day("2026-10-28", steps = 10_500, glasses = 8), day("2026-10-29", workouts = 1, glasses = 8)).associateBy { it.date }
        val b = Game.badges(days, g, longestRouteKm = 5.2, totalKm = 5.2, level = 2).associateBy { it.badge }
        assertTrue(b.getValue(io.github.smyrna_pergamum.exercisetracker.domain.Badge.STEPS_10K).unlocked)
        assertTrue(b.getValue(io.github.smyrna_pergamum.exercisetracker.domain.Badge.ROUTE_5K).unlocked)
        assertTrue(b.getValue(io.github.smyrna_pergamum.exercisetracker.domain.Badge.FIRST_WORKOUT).unlocked)
        assertFalse(b.getValue(io.github.smyrna_pergamum.exercisetracker.domain.Badge.HYDRATED_7).unlocked)
        assertEquals(2, b.getValue(io.github.smyrna_pergamum.exercisetracker.domain.Badge.HYDRATED_7).progress)
    }
}

class ReminderTimeTest {
    private val zone = java.time.ZoneId.of("Europe/Istanbul")

    @Test fun waterEveryTwoHoursUntilNine() {
        val r = io.github.smyrna_pergamum.exercisetracker.data.Reminder(io.github.smyrna_pergamum.exercisetracker.data.ReminderType.WATER, hour = 9, intervalH = 2, enabled = true)
        val at = { h: Int, m: Int -> java.time.ZonedDateTime.of(2026, 10, 29, h, m, 0, 0, zone) }
        assertEquals(11, io.github.smyrna_pergamum.exercisetracker.tracking.Reminders.next(r, at(9, 30))!!.hour)
        assertEquals(21, io.github.smyrna_pergamum.exercisetracker.tracking.Reminders.next(r, at(20, 0))!!.hour)
        val next = io.github.smyrna_pergamum.exercisetracker.tracking.Reminders.next(r, at(21, 5))!!
        assertEquals(30, next.dayOfMonth); assertEquals(9, next.hour)
    }
}

class BodyTest {
    private val Body = io.github.smyrna_pergamum.exercisetracker.domain.Body

    @Test fun bmiAndBands() {
        assertEquals(22.86, Body.bmi(70.0, 175), 0.01)
        assertEquals(io.github.smyrna_pergamum.exercisetracker.domain.BmiBand.NORMAL, Body.band(22.9))
        assertEquals(io.github.smyrna_pergamum.exercisetracker.domain.BmiBand.UNDER, Body.band(18.4))
        assertEquals(io.github.smyrna_pergamum.exercisetracker.domain.BmiBand.OVER, Body.band(25.0))
        assertEquals(io.github.smyrna_pergamum.exercisetracker.domain.BmiBand.OBESE, Body.band(30.0))
        val r = Body.normalRange(175)
        assertEquals(56.66, r.start, 0.01); assertEquals(76.26, r.endInclusive, 0.01)
    }

    @Test fun goalProgressBothDirections() {
        assertEquals(0.5f, Body.progress(90.0, 85.0, 80.0), 0.001f)
        assertEquals(0.5f, Body.progress(50.0, 52.5, 55.0), 0.001f)
        assertEquals(1f, Body.progress(90.0, 78.0, 80.0), 0.001f)
        assertTrue(io.github.smyrna_pergamum.exercisetracker.domain.Game.reached(90.0, 79.9, 80.0))
        assertFalse(io.github.smyrna_pergamum.exercisetracker.domain.Game.reached(90.0, 81.0, 80.0))
        assertTrue(io.github.smyrna_pergamum.exercisetracker.domain.Game.reached(50.0, 55.2, 55.0))
    }

    @Test fun weighInGivesXp() {
        val g = io.github.smyrna_pergamum.exercisetracker.domain.Goals(8000, 8)
        val d = io.github.smyrna_pergamum.exercisetracker.domain.DayFacts(java.time.LocalDate.parse("2026-10-29"), weighed = true)
        assertEquals(10, io.github.smyrna_pergamum.exercisetracker.domain.Game.xp(d, g))
    }
}

class RadioParseTest {
    @Test fun keepsHttpsStationsOnly() {
        val body = """[
          {"stationuuid":"a","name":"Good FM","url_resolved":"https://good.example/stream","country":"Türkiye","tags":"pop,rock,90s","codec":"MP3","bitrate":128},
          {"stationuuid":"b","name":"Plain FM","url_resolved":"http://plain.example/stream","country":"Türkiye","tags":"","codec":"MP3","bitrate":64},
          {"stationuuid":"c","name":"Dup FM","url_resolved":"https://good.example/stream","country":"","tags":"","codec":"","bitrate":0},
          {"stationuuid":"d","name":"","url_resolved":"https://x.example/","country":"","tags":"","codec":"","bitrate":0, "extra": 1}
        ]"""
        val s = io.github.smyrna_pergamum.exercisetracker.audio.RadioBrowser.parse(body)
        assertEquals(listOf("a"), s.map { it.id })
        assertEquals("Türkiye · pop, rock · 128 kbps", s[0].subtitle)
    }
}
