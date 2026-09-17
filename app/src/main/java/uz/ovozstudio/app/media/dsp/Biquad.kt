package uz.ovozstudio.app.media.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ikkinchi tartibli rekursiv filtr (biquad).
 *
 * Koeffitsientlar RBJ "Audio EQ Cookbook" formulalari bo'yicha hisoblanadi —
 * bu audio dasturlarida standart hisoblanadi, ya'ni natija boshqa
 * ekvalayzerlar bilan solishtirsa bo'ladi.
 *
 * `a0` ga bo'lingan holda saqlanadi, shuning uchun ishlatishda faqat
 * `b0, b1, b2, a1, a2` kerak bo'ladi.
 *
 * Filtr `Double` da hisoblanadi. Sabab: 31 polosali kaskad ketma-ket
 * o'tadi va har bir bosqichda xatolik to'planadi; `Float` da esa past
 * chastotali polosalarda (31 Hz) sezilarli darajada eshitilardi.
 */
class Biquad private constructor(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {

    /**
     * Filtr barqarormi — qutblar birlik aylana ichidami.
     *
     * Barqarorlik sharti biquad uchun shu ikki tengsizlikka aylanadi.
     * Hisob-kitob xato bo'lsa (masalan, chastota noto'g'ri berilsa), filtr
     * chiqishi cheksiz o'sib ketardi va fayl shovqinga aylanardi.
     */
    val stable: Boolean get() = abs(a2) < 1.0 && abs(a1) < 1.0 + a2

    /**
     * Filtr hech narsani o'zgartirmaydimi.
     *
     * Kaskadni yig'ishda bunday filtrlar tashlab yuboriladi: 31 polosali
     * ekvalayzerda odatda ularning ko'pi 0 dB da turadi va ularni baribir
     * hisoblash ishni bekorga bir necha barobar oshiradi.
     */
    val isIdentity: Boolean
        get() = b0 == 1.0 && b1 == 0.0 && b2 == 0.0 && a1 == 0.0 && a2 == 0.0

    /** Filtrning [frequency] chastotadagi kuchaytirishi (marta, dB emas). */
    fun magnitudeAt(frequency: Double, sampleRate: Int): Double {
        if (sampleRate <= 0) return 1.0
        val w = 2.0 * PI * frequency / sampleRate
        val cos1 = cos(w)
        val sin1 = sin(w)
        val cos2 = cos(2.0 * w)
        val sin2 = sin(2.0 * w)

        val numeratorReal = b0 + b1 * cos1 + b2 * cos2
        val numeratorImaginary = -(b1 * sin1 + b2 * sin2)
        val denominatorReal = 1.0 + a1 * cos1 + a2 * cos2
        val denominatorImaginary = -(a1 * sin1 + a2 * sin2)

        val numerator = sqrt(numeratorReal * numeratorReal + numeratorImaginary * numeratorImaginary)
        val denominator = sqrt(denominatorReal * denominatorReal + denominatorImaginary * denominatorImaginary)
        return if (denominator == 0.0) 1.0 else numerator / denominator
    }

    /** Filtrning [frequency] chastotadagi kuchaytirishi desibellarda. */
    fun gainDbAt(frequency: Double, sampleRate: Int): Double {
        val magnitude = magnitudeAt(frequency, sampleRate)
        return if (magnitude <= 0.0) Double.NEGATIVE_INFINITY else 20.0 * log10(magnitude)
    }

    companion object {

        /** Tovushda eshitilmaydigan, lekin hisob-kitobni buzadigan eng past chegara. */
        private const val MIN_FREQUENCY = 10.0

        /** Buterworth ko'rsatkichi: eng tekis o'tish zonasi (kesish filtrlarida). */
        const val BUTTERWORTH_Q = 0.7071

        /**
         * Qo'ng'iroq (peaking) filtr — ekvalayzer polosasining o'zi.
         *
         * [gainDb] = 0 bo'lsa filtr o'zgartirilmaydi (natija — hech narsa
         * qilmaydigan filtr). Bu shart emas, lekin foydali: har bir polosa
         * uchun ish hajmi hisoblanadi va nol polosalar tashlab yuboriladi.
         */
        fun peaking(sampleRate: Int, frequency: Double, gainDb: Double, q: Double): Biquad {
            if (gainDb == 0.0) return identity()
            if (!canBuild(sampleRate, frequency) || q <= 0.0) return identity()

            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * frequency / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            return normalised(
                b0 = 1.0 + alpha * a,
                b1 = -2.0 * cosW0,
                b2 = 1.0 - alpha * a,
                a0 = 1.0 + alpha / a,
                a1 = -2.0 * cosW0,
                a2 = 1.0 - alpha / a,
            )
        }

        /**
         * Past chastotalarni kesuvchi filtr.
         *
         * Ovoz yozuvida bu eng ko'p ishlatiladigan tuzatish: mikrofon
         * ushlaganda, stol tebranishida va shamol oqimida paydo bo'ladigan
         * past gumburlashni olib tashlaydi — ovoz esa tegilmaydi.
         */
        fun highPass(sampleRate: Int, frequency: Double, q: Double = BUTTERWORTH_Q): Biquad {
            if (!canBuild(sampleRate, frequency) || q <= 0.0) return identity()

            val w0 = 2.0 * PI * frequency / sampleRate
            val alpha = sin(w0) / (2.0 * q)
            val cosW0 = cos(w0)

            return normalised(
                b0 = (1.0 + cosW0) / 2.0,
                b1 = -(1.0 + cosW0),
                b2 = (1.0 + cosW0) / 2.0,
                a0 = 1.0 + alpha,
                a1 = -2.0 * cosW0,
                a2 = 1.0 - alpha,
            )
        }

        /** Hech narsani o'zgartirmaydigan filtr: chiqish = kirish. */
        fun identity(): Biquad = Biquad(1.0, 0.0, 0.0, 0.0, 0.0)

        /**
         * Filtrni shu fayl uchun qurish mumkinmi.
         *
         * Chastota Nyquist chegarasidan yuqori bo'lsa (namuna olish chastotasi
         * yarmi), filtr ma'nosini yo'qotadi: 8 kHz li faylda 20 kHz ni
         * kuchaytirish umuman mumkin emas. Bunday polosa jimgina o'tkazib
         * yuboriladi — «kuchaytirdim, lekin hech narsa o'zgarmadi» degan
         * holat bo'lmasligi uchun polosa ro'yxatidan ham chiqariladi
         * ([EqBands.centers] ga qarang).
         */
        private fun canBuild(sampleRate: Int, frequency: Double): Boolean =
            sampleRate > 0 && frequency >= MIN_FREQUENCY && frequency < sampleRate / 2.0

        private fun normalised(
            b0: Double,
            b1: Double,
            b2: Double,
            a0: Double,
            a1: Double,
            a2: Double,
        ): Biquad = Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
    }
}
