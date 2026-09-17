package uz.ovozstudio.app.media.mix

import uz.ovozstudio.app.util.DecimalText

/**
 * Yo'l sozlamalarining **matn** ko'rinishi.
 *
 * Sozlamalar ekranda matn sifatida turadi (maydon yozayotganda qiymat
 * tugallanmagan bo'ladi: «-», «1.»), modelda esa son sifatida. Bu obyekt
 * ikkovining orasidagi yagona ko'prik: ekran ham, saqlash ham shu yerdan
 * o'tadi, ya'ni chegaralar bir joyda turadi.
 *
 * Nega alohida fayl: bu mantiq Android'ga bog'lanmagan, ya'ni sof JVM'da
 * tekshiriladi. Maydon chegaralarini (masalan, `-60` ikki raqamga sig'adimi)
 * qo'lda sinab ko'rish oson emas, testda esa bir qatorda.
 */
object MixTrackText {

    /** Balandlikning bir qadami — ekvalayzer bilan bir xil. */
    const val GAIN_STEP_DB = 0.5

    /** Joylashuv qadami: o'rtadan chetga — o'n qadam. */
    const val PAN_STEP = 0.1

    /** Siljish qadami: 100 ms — quloq bilan seziladigan eng kichik farq. */
    const val OFFSET_STEP_MS = 100.0

    val MIN_GAIN_DB = MixTrack.MIN_GAIN_DB.toDouble()
    val MAX_GAIN_DB = MixTrack.MAX_GAIN_DB.toDouble()

    /** Siljish chegarasi: 10 daqiqa. Undan uzunligi amalda kerak bo'lmaydi. */
    const val MAX_OFFSET_MS = 600_000.0

    /** Raqamlar soni: `-60` dan `12` gacha — ikki butun raqam yetadi. */
    private const val GAIN_INTEGER_DIGITS = 2
    private const val GAIN_FRACTION_DIGITS = 1

    /** Joylashuv: `-1.00` … `1.00`. */
    private const val PAN_INTEGER_DIGITS = 1
    private const val PAN_FRACTION_DIGITS = 2

    /** Siljish: butun millisekund. */
    private const val OFFSET_INTEGER_DIGITS = 6
    private const val OFFSET_FRACTION_DIGITS = 0

    fun sanitizeGain(text: String): String =
        DecimalText.sanitize(text, GAIN_INTEGER_DIGITS, GAIN_FRACTION_DIGITS)

    /** Bo'sh yoki tugallanmagan matn — 0 dB, ya'ni «o'zgartirish yo'q». */
    fun parseGain(text: String): Float =
        DecimalText.parse(text, MIN_GAIN_DB, MAX_GAIN_DB, 0.0).toFloat()

    fun formatGain(gainDb: Float): String =
        DecimalText.format(gainDb.toDouble(), MIN_GAIN_DB, MAX_GAIN_DB, GAIN_FRACTION_DIGITS)

    fun nudgeGain(text: String, deltaDb: Double): String =
        DecimalText.nudge(text, deltaDb, MIN_GAIN_DB, MAX_GAIN_DB, GAIN_FRACTION_DIGITS, 0.0)

    fun sanitizePan(text: String): String =
        DecimalText.sanitize(text, PAN_INTEGER_DIGITS, PAN_FRACTION_DIGITS)

    /** Bo'sh matn — o'rta (0), ya'ni «o'zgartirish yo'q». */
    fun parsePan(text: String): Float =
        DecimalText.parse(text, -1.0, 1.0, 0.0).toFloat()

    fun formatPan(pan: Float): String =
        DecimalText.format(pan.toDouble(), -1.0, 1.0, PAN_FRACTION_DIGITS)

    fun nudgePan(text: String, delta: Double): String =
        DecimalText.nudge(text, delta, -1.0, 1.0, PAN_FRACTION_DIGITS, 0.0)

    fun sanitizeOffset(text: String): String =
        DecimalText.sanitize(text, OFFSET_INTEGER_DIGITS, OFFSET_FRACTION_DIGITS)

    /** Bo'sh matn — nol, ya'ni yo'l boshidan boshlanadi. Manfiy siljish yo'q. */
    fun parseOffset(text: String): Long =
        DecimalText.parse(text, 0.0, MAX_OFFSET_MS, 0.0).toLong()

    fun formatOffset(offsetMs: Long): String =
        DecimalText.format(offsetMs.toDouble(), 0.0, MAX_OFFSET_MS, OFFSET_FRACTION_DIGITS)

    fun nudgeOffset(text: String, deltaMs: Double): String =
        DecimalText.nudge(text, deltaMs, 0.0, MAX_OFFSET_MS, OFFSET_FRACTION_DIGITS, 0.0)

    /** Chiziqli koeffitsientni desibelga o'giradi — «qancha tushirildi». */
    fun gainToDb(gain: Float): Float =
        if (gain <= 0f) 0f else (20.0 * Math.log10(gain.toDouble())).toFloat()
}
