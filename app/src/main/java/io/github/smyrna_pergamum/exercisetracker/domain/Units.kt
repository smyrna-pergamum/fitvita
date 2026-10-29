package io.github.smyrna_pergamum.exercisetracker.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

enum class WeightUnit { KG, LB }

/**
 * FR-09 / AT-06: loads are stored in kilograms only. Pounds exist on screen; conversion happens at
 * display and input time, and an unchanged input keeps the stored value so no rounding piles up.
 */
object Units {
    const val KG_PER_LB = 0.45359237

    fun toDisplay(kg: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) kg else kg / KG_PER_LB

    fun toKg(value: Double, unit: WeightUnit): Double = if (unit == WeightUnit.KG) value else value * KG_PER_LB

    /** Two decimals at most, trailing zeros dropped: 20 → "20", 44.0925 → "44.09". */
    fun format(kg: Double?, unit: WeightUnit, locale: Locale = Locale.getDefault()): String? {
        kg ?: return null
        val v = BigDecimal(toDisplay(kg, unit)).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = v.toPlainString()
        return if (locale.language == "tr") plain.replace('.', ',') else plain
    }

    /**
     * Parses a load field. Empty means "no external load" (null), which is not 0 kg (FR-02).
     * If the text is what [format] shows for [originalKg], the original stored value is returned.
     */
    fun parseLoad(text: String, unit: WeightUnit, originalKg: Double?, locale: Locale = Locale.getDefault()): ParseResult {
        val t = text.trim()
        if (t.isEmpty()) return ParseResult.Ok(null)
        if (originalKg != null && t == format(originalKg, unit, locale)) return ParseResult.Ok(originalKg)
        val v = t.replace(',', '.').toDoubleOrNull() ?: return ParseResult.Invalid
        if (v < 0 || v > 2000 || v.isNaN()) return ParseResult.Invalid
        return ParseResult.Ok(toKg(v, unit))
    }

    sealed interface ParseResult {
        data class Ok(val kg: Double?) : ParseResult
        data object Invalid : ParseResult
    }

    fun formatDuration(sec: Int): String = if (sec >= 3600) "%d:%02d:%02d".format(sec / 3600, sec / 60 % 60, sec % 60) else "%d:%02d".format(sec / 60, sec % 60)
}
