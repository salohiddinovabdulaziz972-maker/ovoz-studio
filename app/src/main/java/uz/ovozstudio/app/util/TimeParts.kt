package uz.ovozstudio.app.util

/**
 * Vaqtni qo'lda kiritish uchun to'rt qism: soat, daqiqa, soniya, millisoniya.
 *
 * Nega bitta matn maydoni emas: `KeyboardType.Number` klaviaturasida `:` yo'q,
 * bitta maydonda esa ekran o'quvchi foydalanuvchisi kursorni kerakli raqamga
 * qo'yish uchun ko'p harakat qiladi. To'rtta alohida maydon TalkBack bilan
 * har biri o'z nomi bilan o'qiladi va Tab bilan ketma-ket o'tiladi.
 *
 * Millisoniya maydoni 0…999 oralig'ida — bu yerda «5» besh millisoniya degani,
 * «500» emas. Noaniqlikka yo'l qo'ymaslik uchun shunday tanlangan.
 *
 * Bu tur `util` paketida: tekshirish qoidalari mantiqning bir qismi, UI'niki
 * emas — shuning uchun ular Android'siz, sof JVM'da test qilinadi.
 */
data class TimeParts(
    val hours: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val millis: String = "",
) {

    val isEmpty: Boolean
        get() = hours.isEmpty() && minutes.isEmpty() && seconds.isEmpty() && millis.isEmpty()

    /** Millisoniyaga o'girish. Noto'g'ri kiritishda `null`. */
    fun toMillisOrNull(): Long? {
        if (isEmpty) return null
        val h = hours.toLongOrNull() ?: if (hours.isEmpty()) 0L else return null
        val m = minutes.toLongOrNull() ?: if (minutes.isEmpty()) 0L else return null
        val s = seconds.toLongOrNull() ?: if (seconds.isEmpty()) 0L else return null
        val ms = millis.toLongOrNull() ?: if (millis.isEmpty()) 0L else return null
        if (m > 59 || s > 59 || ms > 999) return null
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    companion object {
        /** Millisoniyadan maydonlarni to'ldirish. */
        fun fromMillis(totalMs: Long): TimeParts {
            val safe = totalMs.coerceAtLeast(0)
            return TimeParts(
                hours = (safe / 3_600_000L).toString(),
                minutes = ((safe % 3_600_000L) / 60_000L).toString(),
                seconds = ((safe % 60_000L) / 1_000L).toString(),
                millis = (safe % 1_000L).toString(),
            )
        }
    }
}
