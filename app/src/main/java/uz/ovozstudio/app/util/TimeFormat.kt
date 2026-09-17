package uz.ovozstudio.app.util

import java.util.Locale

/**
 * Vaqtni matnga aylantirish va matndan o'qish.
 *
 * Ilovaning asosiy talablaridan biri — kesish nuqtasini slayder bilan emas,
 * klaviatura orqali aniq kiritish. Shu sababli bu yerda `soat:daqiqa:soniya.millisoniya`
 * ko'rinishidagi qat'iy format ishlatiladi.
 */
object TimeFormat {

    private const val MS_PER_SECOND = 1000L
    private const val MS_PER_MINUTE = 60 * MS_PER_SECOND
    private const val MS_PER_HOUR = 60 * MS_PER_MINUTE

    /** 3 723 456 ms -> "01:02:03.456" */
    fun format(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val hours = safe / MS_PER_HOUR
        val minutes = (safe % MS_PER_HOUR) / MS_PER_MINUTE
        val seconds = (safe % MS_PER_MINUTE) / MS_PER_SECOND
        val millis = safe % MS_PER_SECOND
        return String.format(
            Locale.US,
            "%02d:%02d:%02d.%03d",
            hours, minutes, seconds, millis,
        )
    }

    /** "01:02:03.456" -> 3 723 456 ms. Xato bo'lsa `null`. */
    fun parse(text: String): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null

        val dotIndex = trimmed.indexOf('.')
        val mainPart = if (dotIndex >= 0) trimmed.substring(0, dotIndex) else trimmed
        val fractionPart = if (dotIndex >= 0) trimmed.substring(dotIndex + 1) else ""

        val groups = mainPart.split(':')
        if (groups.isEmpty() || groups.size > 3) return null
        if (groups.any { it.isEmpty() || !it.all(Char::isDigit) }) return null
        if (fractionPart.any { !it.isDigit() }) return null

        // Kasr qismi millisoniyaga keltiriladi: ".4" -> 400 ms, ".45" -> 450 ms.
        val millis = when (fractionPart.length) {
            0 -> 0L
            1 -> fractionPart.toLong() * 100
            2 -> fractionPart.toLong() * 10
            3 -> fractionPart.toLong()
            else -> fractionPart.substring(0, 3).toLong()
        }

        // Oxirgi guruh — soniya, undan oldingisi daqiqa, eng oldingisi soat.
        val numbers = groups.map { it.toLong() }
        val seconds = numbers.last()
        val minutes = if (numbers.size >= 2) numbers[numbers.size - 2] else 0L
        val hours = if (numbers.size >= 3) numbers[numbers.size - 3] else 0L

        // Daqiqa va soniya 59 dan oshmasligi kerak, aks holda bu xato kiritish.
        if (seconds > 59 || minutes > 59) return null

        return hours * MS_PER_HOUR + minutes * MS_PER_MINUTE + seconds * MS_PER_SECOND + millis
    }

    /**
     * Ekran o'quvchi uchun ovozli shakl: "3 daqiqa 12 soniya".
     *
     * Birlik nomlari tashqaridan beriladi: bu funksiya Android'ga bog'liq emas
     * (sof JVM'da test qilinadi), tarjima esa resurslardan olinadi. Ilgari bu
     * yerda "h/min/s" qattiq yozilgan edi — o'zbek tilidagi qurilmada ekran
     * o'quvchi inglizcha qisqartmalarni o'qib berardi.
     */
    fun formatSpoken(ms: Long, units: SpokenUnits = SpokenUnits.ENGLISH): String {
        val safe = ms.coerceAtLeast(0)
        val hours = safe / MS_PER_HOUR
        val minutes = (safe % MS_PER_HOUR) / MS_PER_MINUTE
        val seconds = (safe % MS_PER_MINUTE) / MS_PER_SECOND
        val parts = mutableListOf<String>()
        if (hours > 0) parts += "$hours ${units.hours}"
        if (minutes > 0) parts += "$minutes ${units.minutes}"
        parts += "$seconds ${units.seconds}"
        return parts.joinToString(" ")
    }

    /** Ovozli e'lon uchun birlik nomlari (joriy tildan olinadi). */
    data class SpokenUnits(
        val hours: String,
        val minutes: String,
        val seconds: String,
    ) {
        companion object {
            /** Zaxira variant — faqat testlar uchun. */
            val ENGLISH = SpokenUnits("h", "min", "s")
        }
    }
}
