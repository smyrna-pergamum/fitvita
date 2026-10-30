package io.github.smyrna_pergamum.exercisetracker.domain

/** WHO adult BMI bands. Shown as general information only. */
enum class BmiBand { UNDER, NORMAL, OVER, OBESE }

object Body {
    fun bmi(kg: Double, heightCm: Int): Double {
        val m = heightCm / 100.0
        return kg / (m * m)
    }

    fun band(bmi: Double): BmiBand = when {
        bmi < 18.5 -> BmiBand.UNDER
        bmi < 25.0 -> BmiBand.NORMAL
        bmi < 30.0 -> BmiBand.OVER
        else -> BmiBand.OBESE
    }

    /** Weight range for a normal BMI (18.5–24.9) at this height, in kg. */
    fun normalRange(heightCm: Int): ClosedFloatingPointRange<Double> {
        val m = heightCm / 100.0
        return 18.5 * m * m..24.9 * m * m
    }

    /** Devine formula, a common reference for "ideal" weight by sex and height. */
    fun idealKg(heightCm: Int, female: Boolean): Double = (if (female) 45.5 else 50.0) + 0.9055 * (heightCm - 152.4)

    /** Signed difference to the goal: negative means "lose", positive "gain". */
    fun toGoal(nowKg: Double, goalKg: Double) = goalKg - nowKg

    /** 0..1 progress from the first logged weight towards the goal. */
    fun progress(startKg: Double, nowKg: Double, goalKg: Double): Float {
        val total = goalKg - startKg
        if (kotlin.math.abs(total) < 0.05) return 1f
        return ((nowKg - startKg) / total).toFloat().coerceIn(0f, 1f)
    }
}
