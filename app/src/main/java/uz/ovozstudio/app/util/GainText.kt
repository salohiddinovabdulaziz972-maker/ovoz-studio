package uz.ovozstudio.app.util

import uz.ovozstudio.app.media.dsp.EqBands
import kotlin.math.floor
import kotlin.math.round

/**
 * Ekvalayzer polosasining kuchaytirishini qo'lda kiritish qoidalari.
 *
 * Butun ilova bo'ylab bir qoida: raqam **qo'lda kiritiladi**, sirg'anma
 * tugma bilan emas. Sabab accessibility: ekran o'quvchi bilan sirg'anmani
 * aniq qiymatga qo'yib bo'lmaydi, klaviatura esa aniq son beradi. Shu
 * qoidani buzmaslik uchun kiritish qatlami alohida turga ajratildi va
 * sof JVM'da tekshiriladi.
 *
 * Ajratgich sifatida nuqta ham, vergul ham qabul qilinadi: o'zbek va rus
 * tillarida o'nlik kasr vergul bilan yoziladi, raqamli klaviaturada esa
 * nuqta chiqadi.
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

    /**
     * Kiritilgan matnni tozalaydi: faqat son qoladi.
     *
     * Xato kiritish (harflar, ikkinchi ajratgich, juda ko'p raqam) shunchaki
     * tashlab yuboriladi — maydon hech qachon qizil bo'lib qolmaydi, chunki
     * bu yerda «noto'g'ri qiymat» degan holat yo'q: har qanday tozalangan
     * son to'g'ri. Minus faqat boshida turadi va uni asosan ± tugmalari
     * qo'yadi (raqamli klaviaturada minus yo'q).
     */
    fun sanitize(input: String): String {
        val negative = input.startsWith("-")
        var integerDigits = 0
        var fractionDigits = 0
        var separatorSeen = false
        val digits = StringBuilder()

        for (character in input) {
            when {
                character.isDigit() && !separatorSeen ->
                    if (integerDigits < INTEGER_DIGITS) {
                        digits.append(character)
                        integerDigits++
                    }

                character.isDigit() ->
                    if (fractionDigits < FRACTION_DIGITS) {
                        digits.append(character)
                        fractionDigits++
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

    /** Matnni desibelga aylantiradi. Bo'sh yoki to'liq bo'lmagan matn — 0 dB. */
    fun parse(text: String): Double {
        val value = text.replace(',', '.').toDoubleOrNull() ?: return 0.0
        return value.coerceIn(MIN_DB, MAX_DB)
    }

    /** Qiymatni maydonga yoziladigan ko'rinishga keltiradi: `0`, `-3`, `3.5`. */
    fun format(gainDb: Double): String {
        val clamped = gainDb.coerceIn(MIN_DB, MAX_DB)
        val rounded = round(clamped * 10.0) / 10.0
        return if (rounded == floor(rounded)) rounded.toInt().toString() else rounded.toString()
    }

    /** [deltaDb] qadar suradi va natijani maydon ko'rinishida qaytaradi. */
    fun nudge(text: String, deltaDb: Double): String = format(parse(text) + deltaDb)
}
