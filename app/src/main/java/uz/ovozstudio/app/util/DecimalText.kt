package uz.ovozstudio.app.util

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round

/**
 * O'nlik sonni qo'lda kiritish qoidalari — butun ilova uchun bitta.
 *
 * Qoida: raqam **qo'lda kiritiladi**, sirg'anma tugma bilan emas. Sabab
 * accessibility: ekran o'quvchi bilan sirg'anmani aniq qiymatga qo'yib
 * bo'lmaydi, klaviatura esa aniq son beradi. Bu qoida ikki joyda —
 * ekvalayzer kuchaytirishida va tezlik/ohangda — ishlaydi, shuning uchun
 * qoidalar bir joyda turadi va chegaralar chaqiruvchidan keladi.
 *
 * Ajratgich sifatida nuqta ham, vergul ham qabul qilinadi: o'zbek va rus
 * tillarida o'nlik kasr vergul bilan yoziladi, raqamli klaviaturada esa
 * nuqta chiqadi.
 */
object DecimalText {

    /**
     * Kiritilgan matnni tozalaydi: faqat son qoladi.
     *
     * [integerDigits] va [fractionDigits] — butun va kasr qismdagi raqamlar
     * soni. Ortiqchasi shunchaki tashlab yuboriladi.
     *
     * Xato kiritish (harflar, ikkinchi ajratgich, juda ko'p raqam) jimgina
     * yo'qoladi — maydon hech qachon qizil bo'lib qolmaydi, chunki bu yerda
     * «noto'g'ri qiymat» degan holat yo'q: har qanday tozalangan son
     * to'g'ri. Minus faqat boshida turadi va uni asosan ± tugmalari qo'yadi
     * (raqamli klaviaturada minus yo'q).
     */
    fun sanitize(input: String, integerDigits: Int, fractionDigits: Int): String {
        val negative = input.startsWith("-")
        var integerCount = 0
        var fractionCount = 0
        var separatorSeen = false
        val digits = StringBuilder()

        for (character in input) {
            when {
                character.isDigit() && !separatorSeen ->
                    if (integerCount < integerDigits) {
                        digits.append(character)
                        integerCount++
                    }

                character.isDigit() ->
                    if (fractionCount < fractionDigits) {
                        digits.append(character)
                        fractionCount++
                    }

                (character == ',' || character == '.') && !separatorSeen -> {
                    separatorSeen = true
                    digits.append('.')
                }
            }
        }

        // Faqat nuqta qolgan bo'lsa ("." yoki ",") — bu hali son emas.
        if (digits.isEmpty() || digits.toString() == ".") return ""
        // ".5" emas, "0.5": ko'rinish ham, o'qilishi ham bir xil bo'lsin.
        val body = if (digits.startsWith(".")) "0$digits" else digits.toString()
        return if (negative) "-$body" else body
    }

    /**
     * Matnni songa aylantiradi va [min]..[max] oralig'iga qisadi.
     *
     * [fallback] — maydon bo'sh yoki tugallanmagan bo'lgandagi qiymat.
     * U chaqiruvchidan keladi va ataylab **majburiy**: bu «o'zgartirish
     * yo'q» degan ma'no, va u har bir sozlamada boshqacha (kuchaytirishda
     * 0 dB, tezlikda 1.0). Umumiy qiymat tanlansa, bo'sh maydon tezlikni
     * eng past darajaga tushirib qo'yardi.
     */
    fun parse(text: String, min: Double, max: Double, fallback: Double): Double {
        val value = text.replace(',', '.').toDoubleOrNull() ?: return fallback.coerceIn(min, max)
        return value.coerceIn(min, max)
    }

    /** Qiymatni maydonga yoziladigan ko'rinishga keltiradi: `1`, `1.5`, `-3.25`. */
    fun format(value: Double, min: Double, max: Double, fractionDigits: Int): String {
        val clamped = value.coerceIn(min, max)
        val scale = 10.0.pow(fractionDigits)
        val rounded = round(clamped * scale) / scale
        return if (rounded == floor(rounded)) rounded.toInt().toString() else rounded.toString()
    }

    /** [text] ni [delta] qadar suradi va natijani maydon ko'rinishida qaytaradi. */
    fun nudge(
        text: String,
        delta: Double,
        min: Double,
        max: Double,
        fractionDigits: Int,
        fallback: Double,
    ): String = format(parse(text, min, max, fallback) + delta, min, max, fractionDigits)
}
