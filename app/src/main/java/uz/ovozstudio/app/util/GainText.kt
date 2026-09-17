package uz.ovozstudio.app.util

import uz.ovozstudio.app.media.dsp.EqBands

/**
 * Ekvalayzer polosasining kuchaytirishini qo'lda kiritish qoidalari.
 *
 * Kiritishning umumiy qoidalari [DecimalText] da turadi — ular tezlik va
 * ohang maydonlarida ham bir xil. Bu yerda faqat kuchaytirishga xos
 * chegaralar qoladi.
 */
object GainText {

    const val MIN_DB: Double = EqBands.MIN_GAIN_DB
    const val MAX_DB: Double = EqBands.MAX_GAIN_DB

    /** Bir bosishda o'zgaradigan qadam: ±0.5 dB — quloq sezadigan eng kichik farq. */
    const val STEP_DB = 0.5

    /** Butun qismdagi raqamlar soni: ±12 dan katta qiymat kiritib bo'lmaydi. */
    private const val INTEGER_DIGITS = 2

    /** Kasr qismidagi raqamlar soni: 0.5 yetarli, undan aniqrog'i eshitilmaydi. */
    private const val FRACTION_DIGITS = 1

    /** Bo'sh maydon — «kuchaytirish yo'q». */
    private const val FALLBACK_DB = 0.0

    fun sanitize(input: String): String = DecimalText.sanitize(input, INTEGER_DIGITS, FRACTION_DIGITS)

    /** Matnni desibelga aylantiradi. Bo'sh yoki to'liq bo'lmagan matn — 0 dB. */
    fun parse(text: String): Double = DecimalText.parse(text, MIN_DB, MAX_DB, FALLBACK_DB)

    /** Qiymatni maydonga yoziladigan ko'rinishga keltiradi: `0`, `-3`, `3.5`. */
    fun format(gainDb: Double): String = DecimalText.format(gainDb, MIN_DB, MAX_DB, FRACTION_DIGITS)

    /** [deltaDb] qadar suradi va natijani maydon ko'rinishida qaytaradi. */
    fun nudge(text: String, deltaDb: Double): String =
        DecimalText.nudge(text, deltaDb, MIN_DB, MAX_DB, FRACTION_DIGITS, FALLBACK_DB)
}
