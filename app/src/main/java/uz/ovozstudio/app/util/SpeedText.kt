package uz.ovozstudio.app.util

import uz.ovozstudio.app.media.dsp.SpeedPitch

/**
 * Tezlik va ohangni qo'lda kiritish qoidalari.
 *
 * Kiritishning umumiy qoidalari [DecimalText] da — ular ekvalayzer
 * maydonlarida ham bir xil. Bu yerda faqat shu amalga xos chegaralar:
 * tezlik 0.5–2.0 (undan narisi eshitilmaydigan darajada buziladi), ohang
 * ±12 yarim ton (bir oktava) va ularning qadamlari.
 */
object SpeedText {

    const val MIN_SPEED: Double = SpeedPitch.MIN_SPEED
    const val MAX_SPEED: Double = SpeedPitch.MAX_SPEED

    const val MIN_SEMITONES: Double = SpeedPitch.MIN_SEMITONES
    const val MAX_SEMITONES: Double = SpeedPitch.MAX_SEMITONES

    /** Bir bosishda o'zgaradigan qadam: 5% — quloq sezadigan eng kichik farq. */
    const val STEP_SPEED = 0.05

    /** Ohang qadami — yarim ton, musiqadagi eng kichik qadam. */
    const val STEP_SEMITONES = 1.0

    /** Bo'sh maydon — «o'zgartirish yo'q». */
    private const val FALLBACK_SPEED = 1.0
    private const val FALLBACK_SEMITONES = 0.0

    /** Butun qismdagi raqamlar: tezlik 2.0 dan oshmaydi, ohang ±12. */
    private const val SPEED_INTEGER_DIGITS = 1
    private const val SEMITONE_INTEGER_DIGITS = 2

    /** Kasr qismidagi raqamlar: tezlikda yuzdan birlik, ohangda o'ndan birlik. */
    private const val SPEED_FRACTION_DIGITS = 2
    private const val SEMITONE_FRACTION_DIGITS = 1

    fun sanitizeSpeed(input: String): String =
        DecimalText.sanitize(input, SPEED_INTEGER_DIGITS, SPEED_FRACTION_DIGITS)

    fun sanitizeSemitones(input: String): String =
        DecimalText.sanitize(input, SEMITONE_INTEGER_DIGITS, SEMITONE_FRACTION_DIGITS)

    fun parseSpeed(text: String): Double =
        DecimalText.parse(text, MIN_SPEED, MAX_SPEED, FALLBACK_SPEED)

    fun parseSemitones(text: String): Double =
        DecimalText.parse(text, MIN_SEMITONES, MAX_SEMITONES, FALLBACK_SEMITONES)

    fun formatSpeed(speed: Double): String =
        DecimalText.format(speed, MIN_SPEED, MAX_SPEED, SPEED_FRACTION_DIGITS)

    fun formatSemitones(semitones: Double): String =
        DecimalText.format(semitones, MIN_SEMITONES, MAX_SEMITONES, SEMITONE_FRACTION_DIGITS)

    fun nudgeSpeed(text: String, delta: Double): String =
        DecimalText.nudge(text, delta, MIN_SPEED, MAX_SPEED, SPEED_FRACTION_DIGITS, FALLBACK_SPEED)

    fun nudgeSemitones(text: String, delta: Double): String = DecimalText.nudge(
        text,
        delta,
        MIN_SEMITONES,
        MAX_SEMITONES,
        SEMITONE_FRACTION_DIGITS,
        FALLBACK_SEMITONES,
    )
}
