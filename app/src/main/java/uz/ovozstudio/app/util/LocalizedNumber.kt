package uz.ovozstudio.app.util

import java.text.NumberFormat
import java.util.Locale

/**
 * Sonlarni joriy tilga mos ko'rinishda yozadi.
 *
 * Nega alohida tur: `toString()` har doim nuqta qo'yadi, o'zbek va rus
 * tillarida esa o'nlik kasr vergul bilan yoziladi. Ekran o'quvchi
 * «31.5» ni «o'ttiz bir nuqta besh» deb o'qiydi — «31,5» esa to'g'ri
 * o'qiladi. Bu farq faqat shu yerda ko'rinadi, shuning uchun qoida bitta
 * joyda saqlanadi.
 */
object LocalizedNumber {

    /** Chastota yorlig'i: `62 Gerts`, `12,5 kilogerts`. */
    fun frequency(
        frequency: Double,
        hzUnit: String,
        khzUnit: String,
        locale: Locale,
    ): String {
        val (value, unit) = if (frequency >= 1000.0) {
            frequency / 1000.0 to khzUnit
        } else {
            frequency to hzUnit
        }
        return "${format(value, locale)} $unit"
    }

    /** Desibel qiymati: `-1,2 dB`. */
    fun decibels(value: Double, unit: String, locale: Locale): String =
        "${format(value, locale)} $unit"

    /** Sonni ko'pi bilan [fractionDigits] xona bilan yozadi. */
    fun format(value: Double, locale: Locale, fractionDigits: Int = 1): String {
        val format = NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = fractionDigits
            minimumFractionDigits = 0
            isGroupingUsed = false
        }
        return format.format(value)
    }
}
