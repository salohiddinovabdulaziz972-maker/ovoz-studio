package uz.ovozstudio.app.media.dsp

import kotlin.math.log2
import kotlin.math.pow

/** Polosalar soni. Ikkitasi — amalda ishlatiladigan barcha variant. */
enum class EqBandCount(val count: Int) {
    /** Oktava oralig'i — 10 polosa. Keng, tez sozlanadi. */
    TEN(10),

    /** Uchdan bir oktava — 31 polosa. Aniq, tor muammolarni tuzatadi. */
    THIRTY_ONE(31),
}

/**
 * Tayyor ovoz profillari.
 *
 * Profil — bu **chastota bo'yicha egri chiziq**, polosalar ro'yxati emas.
 * Shu tufayli bir xil profil 10 polosada ham, 31 polosada ham to'g'ri
 * ochiladi: egri chiziq har bir polosaning o'z chastotasida hisoblanadi.
 * Aks holda 10 polosa uchun yozilgan qiymatlar 31 polosaga hech qanday
 * ma'no bermasdan ko'chirilardi.
 */
enum class EqPreset {
    /** Hamma polosa 0 dB — hech narsa o'zgarmaydi. */
    FLAT,

    /** Nutq va podkast: gumburlash kesiladi, ravshanlik ko'tariladi. */
    VOICE,

    /** Kuchli bas. */
    BASS,

    /** Yorqin yuqori chastotalar. */
    TREBLE,

    /** Baland ovozda tinglash uchun V shaklidagi egri chiziq. */
    LOUDNESS,
}

/**
 * Ekvalayzer polosalari: chastotalar jadvali, kenglik va profil egri
 * chiziqlari.
 *
 * Bu qatlam ataylab Android'siz: chastotalar jadvali, interpolyatsiya va
 * qurilish qoidalari sof JVM testlarida tekshiriladi.
 */
object EqBands {

    /** Oktava oralig'idagi polosalar (ISO standarti). */
    private val OCTAVE_CENTERS = listOf(
        31.5, 63.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0,
    )

    /** Uchdan bir oktava — 31 polosa. */
    private val THIRD_OCTAVE_CENTERS = listOf(
        20.0, 25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0,
        200.0, 250.0, 315.0, 400.0, 500.0, 630.0, 800.0, 1000.0, 1250.0, 1600.0,
        2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0,
        20000.0,
    )

    /**
     * Polosa kengligi (Q).
     *
     * Oktava oraliqidagi grafik ekvalayzer uchun 1.41 — qo'shni polosalarning
     * egri chiziqlari taxminan bir-biriga tegib turadigan qiymat. Uchdan bir
     * oktava uchun 4.32. Boshqa qiymatda yoki polosalar orasida chuqur
     * o'ralgan «cho'qqilar», yoki hamma joyni qoplaydigan keng doiralar
     * hosil bo'lardi.
     */
    private const val OCTAVE_Q = 1.41
    private const val THIRD_OCTAVE_Q = 4.32

    /** Ikkala jadvaldagi eng tor va eng keng oraliq: `sqrt(2)` ning darajalari. */
    private const val THIRD_OCTAVE_RATIO = 1.12246 // 2^(1/6)

    /** Shakl chegarasi: dB. Foydalanuvchi bundan tashqariga chiqa olmaydi. */
    const val MIN_GAIN_DB = -12.0
    const val MAX_GAIN_DB = 12.0

    /**
     * Fayl uchun mos polosa chastotalari.
     *
     * [sampleRate] bo'yicha filtrlanadi: Nyquist chegarasidan (namuna olish
     * chastotasining yarmi) yuqori polosa o'sha faylda hech narsa
     * qilolmaydi. 8 kHz li faylda 20 kHz ni ko'rsatib qo'yish — foydalanuvchini
     * aldash bo'lardi: u «kuchaytirdim» deb o'ylaydi, natijada hech narsa
     * o'zgarmaydi.
     */
    fun centers(count: EqBandCount, sampleRate: Int): List<Double> {
        val all = when (count) {
            EqBandCount.TEN -> OCTAVE_CENTERS
            EqBandCount.THIRTY_ONE -> THIRD_OCTAVE_CENTERS
        }
        val limit = if (sampleRate > 0) sampleRate / 2.0 else Double.MAX_VALUE
        return all.filter { it < limit }
    }

    /** [count] polosali jadvalning kengligi (Q). */
    fun q(count: EqBandCount): Double = when (count) {
        EqBandCount.TEN -> OCTAVE_Q
        EqBandCount.THIRTY_ONE -> THIRD_OCTAVE_Q
    }

    /** Profil egri chizig'ining [frequency] chastotadagi qiymati (dB). */
    fun presetGainDb(preset: EqPreset, frequency: Double): Double {
        val curve = CURVES.getValue(preset)
        if (curve.isEmpty()) return 0.0

        val first = curve.first()
        if (frequency <= first.frequency) return first.gainDb
        val last = curve.last()
        if (frequency >= last.frequency) return last.gainDb

        for (i in 1 until curve.size) {
            val high = curve[i]
            if (frequency > high.frequency) continue
            val low = curve[i - 1]
            // Interpolyatsiya logarifmik chastota bo'yicha: quloq ham
            // chastotani chiziqli emas, nisbat bo'yicha eshitadi. Chiziqli
            // interpolyatsiya 100 Hz bilan 200 Hz orasidagi masofani 10 kHz
            // bilan 10.1 kHz orasidagi masofaga teng ko'rardi.
            val span = log2(high.frequency / low.frequency)
            val position = if (span == 0.0) 0.0 else log2(frequency / low.frequency) / span
            return low.gainDb + (high.gainDb - low.gainDb) * position
        }
        return last.gainDb
    }

    /** [count] polosali jadval uchun profil egri chizig'i (dB). */
    fun presetGains(count: EqBandCount, preset: EqPreset, sampleRate: Int): List<Double> =
        centers(count, sampleRate).map { presetGainDb(preset, it) }

    /**
     * Polosani kenglikka moslashtirish: jadvalda bo'lmagan chastota uchun
     * eng yaqin polosani oladi.
     *
     * Ekranda polosalar soni almashganda kerak: foydalanuvchi qo'lda
     * o'zgartirgan qiymatlar yangi jadvalda o'z joyini topishi kerak, aks
     * holda ular jimgina yo'qolardi.
     */
    fun nearestIndex(centers: List<Double>, frequency: Double): Int {
        if (centers.isEmpty()) return -1
        var best = 0
        var bestDistance = Double.MAX_VALUE
        for (i in centers.indices) {
            // Masofa ham logarifmik: 100 Hz 200 Hz ga 1000 Hz 1100 Hz dan
            // yaqinroq.
            val distance = kotlin.math.abs(log2(centers[i] / frequency))
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /** Polosaning pastki va yuqori chegarasi — faqat hujjat va test uchun. */
    fun bandwidth(count: EqBandCount, frequency: Double): ClosedFloatingPointRange<Double> {
        val half = when (count) {
            EqBandCount.TEN -> 2.0.pow(0.5)
            EqBandCount.THIRTY_ONE -> THIRD_OCTAVE_RATIO
        }
        return (frequency / half)..(frequency * half)
    }

    /** Egri chiziqning tayanch nuqtasi. */
    private data class Point(val frequency: Double, val gainDb: Double)

    private fun point(frequency: Double, gainDb: Double) = Point(frequency, gainDb)

    /**
     * Profillarning tayanch nuqtalari.
     *
     * Qiymatlar ataylab yumshoq (±6 dB atrofida): maqsad — ovozni
     * tanishroq qilish, uni butunlay o'zgartirish emas. Kuchliroq
     * tuzatish kerak bo'lsa, foydalanuvchi polosalarni qo'lda suradi.
     */
    private val CURVES: Map<EqPreset, List<Point>> = mapOf(
        EqPreset.FLAT to emptyList(),

        // Nutq: 200 Hz gacha bo'lgan «gumburlash» pasaytiriladi, 2–5 kHz
        // oralig'i (undoshlarning aniqligi) ko'tariladi, 12 kHz dan yuqorisi
        // kesiladi — u yerda foydali ma'lumot yo'q, faqat shivirlash bor.
        EqPreset.VOICE to listOf(
            point(50.0, -6.0), point(100.0, -3.0), point(200.0, -1.5), point(400.0, 0.0),
            point(800.0, 1.0), point(1600.0, 2.0), point(3000.0, 3.0), point(5000.0, 1.5),
            point(8000.0, 0.0), point(12000.0, -2.0), point(20000.0, -6.0),
        ),

        EqPreset.BASS to listOf(
            point(20.0, 6.0), point(60.0, 6.0), point(120.0, 4.5), point(250.0, 2.0),
            point(500.0, 0.5), point(1000.0, 0.0), point(20000.0, 0.0),
        ),

        EqPreset.TREBLE to listOf(
            point(20.0, 0.0), point(1000.0, 0.0), point(3000.0, 2.0), point(6000.0, 4.0),
            point(12000.0, 6.0), point(20000.0, 5.0),
        ),

        EqPreset.LOUDNESS to listOf(
            point(20.0, 5.0), point(60.0, 4.0), point(200.0, 0.0), point(1000.0, -3.0),
            point(3000.0, -1.0), point(8000.0, 1.5), point(16000.0, 3.5), point(20000.0, 4.0),
        ),
    )
}
