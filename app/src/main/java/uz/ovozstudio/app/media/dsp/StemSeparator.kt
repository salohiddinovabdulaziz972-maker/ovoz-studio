package uz.ovozstudio.app.media.dsp

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavSampleReader
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import java.util.Arrays
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow

/**
 * Vokal va cholg'uni ajratish — kanal (markaz) usuli bilan.
 *
 * **Bu neyron model emas.** Usul stereo miksdagi oddiy haqiqatga tayanadi:
 * vokal odatda **markazda** turadi (ikkala kanalga birdek yozilgan), cholg'u
 * esa ko'proq yonlarga taqsimlanadi. Shundan kelib chiqib:
 *
 * - markaziy qism `M = (L + R) / 2` — vokal shu yerda;
 * - yon qism `S = (L - R) / 2` — cholg'u shu yerda.
 *
 * Ikki rejim bor:
 *
 * - [Mode.REMOVE_VOCALS] — markaz **butunlay** ayiriladi: har bir polosada
 *   `M` olib tashlanadi. Bu matematik jihatdan aniq amal (ayirish), ya'ni
 *   natijada hech qanday «sun'iy tovush» bo'lmaydi. Karaoke usuli deb
 *   ataladigan narsa aynan shu.
 * - [Mode.SPLIT] — har bir polosada markaz qanchalik ustunligi o'lchanadi
 *   (`d`) va markaz `d^kuch` koeffitsienti bilan ayiriladi. Ya'ni ayirish
 *   **qismiy**: markaz butunlay ustun bo'lgan polosa deyarli to'liq
 *   o'chadi, markaz va yon teng bo'lgan polosa esa yarmidan ko'pi
 *   qoladi. Amalda bu keng (stereo) yozilgan cholg'uda sun'iy tovushni
 *   kamaytiradi.
 *
 * Ikkala rejim ham bir xil umumiy qoidaga bo'ysunadi: vokal fayli — `M·m`,
 * cholg'u fayli — `L - M·m` va `R - M·m`. Ya'ni **vokal + cholg'u = manba**,
 * namunagacha aniq. Bu tasodif emas, usulning tuzilishi: ajratish
 * qo'shishga teskari amal bo'lgani uchun hech narsa yo'qolmaydi va hech
 * narsa o'ylab topilmaydi. Tekshiruv ham aynan shu xususiyatga tayanadi.
 *
 * Halol cheklovlar:
 *
 * - **Faqat stereo.** Bitta kanalli faylda yon qism umuman bo'lmaydi
 *   ([ERROR_NOT_STEREO]), kanallari bir xil (mono mazmunli) faylda esa
 *   ayirish nolga olib keladi ([ERROR_MONO_CONTENT]). Ikkisi ham ochiq
 *   xato — «jimgina o'zgarmagan nusxa qaytarish» emas.
 * - **Markazda turgan cholg'u ham o'chadi.** Bas yoki baraban ham
 *   ko'pincha markazda turadi, ya'ni ular vokal bilan bir joyda — ikkala
 *   rejim ham ularni ajratmaydi. Bu usulning emas, **kanal usulining**
 *   chegarasi: markazda turgan narsani faqat markazdan ajratib
 *   bo'lmaydi. O'lchov buni tasdiqlaydi: butunlay chapga surilgan
 *   ohangda `SPLIT` markazning `d^kuch` qisminigina oladi.
 * - Vokal markazda bo'lmasa (keng yozilgan, ko'p aks-sado bilan), natija
 *   shunchalik yaxshi bo'lmaydi. Buni fayl oldindan aytib bera olmaydi:
 *   [Result.sideToMidDb] o'lchanadi va ekran uni ko'rsatadi.
 * - Markazni ayirish natijasi **pastroq** eshitiladi (aniq ayirishda
 *   −6 dB atrofida): bu usulning o'zi shunday, keyin balandlikni
 *   ko'tarish kerak bo'ladi.
 */
object StemSeparator {

    /**
     * Kadr uzunligi (namunada).
     *
     * Shovqin tozalashdagidek 1024: 48 kHz da bu 21 ms, ya'ni spektr
     * polosalari cholg'uni ajratish uchun yetarli darajada aniq.
     */
    const val FRAME = 1024

    /** Kadrlar orasidagi qadam: 75 % qoplanish, chetlarda shitirlash bo'lmasin. */
    const val HOP = FRAME / 4

    /**
     * Ajratish kuchi chegaralari.
     *
     * [Mode.SPLIT] da polosa vokal deb hisoblanishi uchun markaz qanchalik
     * ustun bo'lishi kerakligini belgilaydi: 1.0 — markaz oddiy ko'pchilik
     * bo'lsa yetarli, 4.0 — polosa deyarli butunlay markaziy bo'lishi shart
     * (cholg'u ko'proq saqlanadi, vokal kamroq o'chadi).
     */
    const val MIN_STRENGTH = 0.5
    const val MAX_STRENGTH = 4.0

    /**
     * Standart kuch.
     *
     * 1.5 — o'rtacha: markazda turgan vokal o'chadi, lekin bir tomonga
     * surilib ketgan cholg'u tegilmeydi. Pastroq qiymatda cholg'uning
     * markaziy qismi ham «vokal» bo'lib ketadi.
     */
    const val DEFAULT_STRENGTH = 1.5

    /** Bitta kanalli manba uchun xato matni (UI sababni shu matn bilan ajratadi). */
    const val ERROR_NOT_STEREO = "Manba stereo emas"

    /**
     * Kanallari bir xil (ya'ni mazmuni mono) stereo fayl uchun xato matni.
     *
     * Bunday faylda `L - R` nolga teng: ajratadigan narsa yo'q. Natija
     * chiqarish ham mumkin edi (vokal — manbaning o'zi, cholg'u — jimlik),
     * lekin bu foydalanuvchini chalg'itardi: u «ajratdim» deb o'ylardi.
     */
    const val ERROR_MONO_CONTENT = "Manba kanallari bir xil (mono)"

    /** Polosalar soni: 0…Nyquist. */
    private const val BINS = FRAME / 2 + 1

    /** Bu quvvatdan past signal «raqamli jimlik» hisoblanadi (≈ −120 dBFS). */
    private const val SILENCE_POWER = 1e-12

    /**
     * Ajratish usuli.
     *
     * [REMOVE_VOCALS] — markaz butunlay ayiriladi (aniq, sun'iy tovushsiz).
     * [SPLIT] — faqat markazga xos polosalar ayiriladi.
     */
    enum class Mode { REMOVE_VOCALS, SPLIT }

    /** Ajratish sozlamalari. */
    data class Settings(
        val mode: Mode = Mode.SPLIT,
        val strength: Double = DEFAULT_STRENGTH,
    ) {
        /** Sozlama to'g'rimi. Kuch faqat [Mode.SPLIT] da ma'noga ega. */
        fun isValid(): Boolean = strength >= MIN_STRENGTH && strength <= MAX_STRENGTH
    }

    /**
     * Natija.
     *
     * [sideToMidDb] — manbaning yon qismi markazga nisbatan qanchalik kuchli
     * ekani (desibelda), **o'lchangan** son. −∞ ga yaqin qiymat «manba deyarli
     * mono» degani: ajratish mumkin, lekin natija amalda sezilmaydi. Buni
     * taxmin qilib bo'lmaydi — faqat o'lchash mumkin.
     */
    data class Result(val info: WavInfo, val sideToMidDb: Double)

    /**
     * [source] ni ikkita faylga ajratadi: [vocalFile] — markaziy qism,
     * [instrumentalFile] — qolgani.
     *
     * Ikkala fayl ham **har doim** yoziladi (manba o'zgarmaydi). Chastota,
     * kanal soni va bit chuqurligi manbadagidek qoladi.
     */
    @Throws(IOException::class)
    fun apply(
        source: File,
        vocalFile: File,
        instrumentalFile: File,
        settings: Settings,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val info = WavFile.readInfo(source)
        if (info.frames <= 0) throw IOException("Fayl bo'sh")
        if (info.channels != 2) throw IOException(ERROR_NOT_STEREO)
        if (!settings.isValid()) throw IOException("Sozlama noto'g'ri")

        val depth = BitDepth.of(info.bitsPerSample)
            ?: throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")

        val rate = info.sampleRate
        val power = measurePower(source)
        // Markaz umuman bo'lmasa (kanallar bir xil) ajratadigan narsa yo'q.
        // Bu fayl oxirigacha o'qilgandan keyin aniqlanadi, shuning uchun
        // tekshiruv aynan shu yerda — yozishdan oldin.
        if (power.side <= SILENCE_POWER) throw IOException(ERROR_MONO_CONTENT)

        val steps = frameSteps(info.frames)
        var reported = -1

        WavSampleReader(source).use { reader ->
            val window = PcmWindow(reader, capacity = FRAME * 2)
            val vocalWriter = WavWriter(vocalFile, rate, 2, depth)
            val instrumentalWriter = WavWriter(instrumentalFile, rate, 2, depth)
            try {
                process(
                    window = window,
                    vocalWriter = vocalWriter,
                    instrumentalWriter = instrumentalWriter,
                    frames = info.frames,
                    mode = settings.mode,
                    gamma = settings.strength,
                    onFrames = { processed ->
                        val percent = (processed * 100 / steps).toInt()
                        if (percent != reported) {
                            reported = percent
                            onProgress((percent / 100f).coerceIn(0f, 1f))
                        }
                    },
                )
            } finally {
                vocalWriter.close()
                instrumentalWriter.close()
            }
        }

        onProgress(1f)
        return Result(info, 10.0 * log10(power.side / power.mid))
    }

    /** Butun fayl bo'ylab markaz va yon qism quvvati. */
    private class Power(val mid: Double, val side: Double)

    /**
     * Butun fayl bo'ylab markaz va yon qism quvvatini o'lchaydi.
     *
     * O'lchov vaqt sohasida (barcha namunalar bo'yicha) bajariladi: bu
     * spektrdan ancha arzon va shu maqsad uchun yetarli — savol «faylda
     * umuman yon qism bormi» va «u qanchalik kuchli», polosalar taqsimoti
     * emas.
     */
    private fun measurePower(source: File): Power {
        WavSampleReader(source).use { reader ->
            val info = reader.info
            val buffer = FloatArray(8192 * info.channels)
            var mid = 0.0
            var side = 0.0
            var position = 0L
            while (position < info.frames) {
                val want = minOf(8192L, info.frames - position).toInt()
                val got = reader.readFrames(position, want, buffer)
                if (got <= 0) break
                for (i in 0 until got) {
                    val l = buffer[i * 2].toDouble()
                    val r = buffer[i * 2 + 1].toDouble()
                    val m = (l + r) / 2.0
                    val s = (l - r) / 2.0
                    mid += m * m
                    side += s * s
                }
                position += got
            }
            return Power(mid, side)
        }
    }

    /** Qayta ishlashda nechta qadam bo'ladi (ko'rsatkich uchun). */
    private fun frameSteps(frames: Long): Long {
        val padding = (FRAME - HOP).toLong()
        return maxOf((frames + padding + HOP - 1) / HOP, 1L)
    }

    /**
     * Kadrlarni qayta ishlab ikkala faylga yozadi.
     *
     * Har bir kadrda uchta spektr yasaladi: markaz `M`, chap `L` va o'ng `R`.
     * Vokal fayli — `M·m`, cholg'u fayli — `L - M·m` (va o'ng kanal uchun
     * `R - M·m`). Ikkalasi bir xil oynadan va bir xil normadan o'tgani uchun
     * ularning yig'indisi aynan manbani beradi.
     *
     * Kadr fayl boshidan `FRAME - HOP` namuna oldin boshlanadi: birinchi
     * namunani ham to'rt kadr qamrab olishi kerak, aks holda oynalar
     * yig'indisi to'liq bo'lmay, faylning boshi jimgina pasayib qolardi.
     */
    private fun process(
        window: PcmWindow,
        vocalWriter: WavWriter,
        instrumentalWriter: WavWriter,
        frames: Long,
        mode: Mode,
        gamma: Double,
        onFrames: (Int) -> Unit,
    ) {
        val fft = Fft(FRAME)
        val taper = hannWindow()
        val buffer = FloatArray(FRAME * 2)

        val leftReal = DoubleArray(FRAME)
        val leftImaginary = DoubleArray(FRAME)
        val rightReal = DoubleArray(FRAME)
        val rightImaginary = DoubleArray(FRAME)

        // Markaz spektri (vokal fayli) va har bir polosaning koeffitsienti.
        val centerReal = DoubleArray(FRAME)
        val centerImaginary = DoubleArray(FRAME)
        val mask = DoubleArray(FRAME)

        // Yig'indi massivlari: vokal (bitta kanal — markaz baribir bitta
        // signal), chap va o'ng cholg'u.
        val centerOut = FloatArray(FRAME)
        val leftOut = FloatArray(FRAME)
        val rightOut = FloatArray(FRAME)
        val norm = FloatArray(FRAME)

        val vocalInterleaved = FloatArray(HOP * 2)
        val instrumentalInterleaved = FloatArray(HOP * 2)

        var steps = 0
        var position = -(FRAME - HOP).toLong()

        while (position < frames) {
            window.ensure(position + FRAME, keepFrom = position)
            window.copyFrames(position, FRAME, buffer)

            for (i in 0 until FRAME) {
                norm[i] += (taper[i] * taper[i]).toFloat()
                leftReal[i] = buffer[i * 2] * taper[i]
                leftImaginary[i] = 0.0
                rightReal[i] = buffer[i * 2 + 1] * taper[i]
                rightImaginary[i] = 0.0
            }
            fft.forward(leftReal, leftImaginary)
            fft.forward(rightReal, rightImaginary)

            // Koeffitsientlar avval hisoblanadi, keyin qo'llanadi. Sabab:
            // ko'zgu polosa (`FRAME - k`) ham **o'sha** koeffitsientni
            // olishi kerak — haqiqiy signal spektri simmetrik, aks holda
            // teskari almashtirish natijasi haqiqiy bo'lmay qolardi.
            for (k in 0 until BINS) {
                mask[k] = maskFor(
                    mode = mode,
                    gamma = gamma,
                    leftReal = leftReal[k],
                    leftImaginary = leftImaginary[k],
                    rightReal = rightReal[k],
                    rightImaginary = rightImaginary[k],
                )
            }
            for (k in BINS until FRAME) {
                mask[k] = mask[FRAME - k]
            }

            // Vokal — markaz (`M·m`), cholg'u — manbadan markazni ayirish
            // (`L - M·m`, `R - M·m`). Ayirish shu yerda, spektrda bajariladi:
            // natijada ikkala faylning yig'indisi aynan manbani beradi.
            for (k in 0 until FRAME) {
                val gain = mask[k]
                val midReal = (leftReal[k] + rightReal[k]) * 0.5 * gain
                val midImaginary = (leftImaginary[k] + rightImaginary[k]) * 0.5 * gain
                centerReal[k] = midReal
                centerImaginary[k] = midImaginary
                leftReal[k] -= midReal
                leftImaginary[k] -= midImaginary
                rightReal[k] -= midReal
                rightImaginary[k] -= midImaginary
            }

            fft.inverse(centerReal, centerImaginary)
            fft.inverse(leftReal, leftImaginary)
            fft.inverse(rightReal, rightImaginary)

            // Uchala natija ham bir xil oynadan o'tadi va bir xil normaga
            // bo'linadi — shuning uchun ularning yig'indisi manbaga teng
            // bo'lib qolaveradi.
            for (i in 0 until FRAME) {
                val gain = taper[i]
                centerOut[i] += (centerReal[i] * gain).toFloat()
                leftOut[i] += (leftReal[i] * gain).toFloat()
                rightOut[i] += (rightReal[i] * gain).toFloat()
            }

            var flushed = 0
            for (i in 0 until HOP) {
                val frame = position + i
                if (frame >= frames) break
                if (frame < 0) continue
                val divisor = if (norm[i] > SILENCE_POWER) norm[i].toDouble() else 1.0
                val center = (centerOut[i] / divisor).toFloat()
                vocalInterleaved[i * 2] = center
                vocalInterleaved[i * 2 + 1] = center
                instrumentalInterleaved[i * 2] = (leftOut[i] / divisor).toFloat()
                instrumentalInterleaved[i * 2 + 1] = (rightOut[i] / divisor).toFloat()
                flushed = i + 1
            }
            if (flushed > 0) {
                vocalWriter.write(vocalInterleaved, flushed)
                instrumentalWriter.write(instrumentalInterleaved, flushed)
            }

            for (i in 0 until FRAME - HOP) {
                centerOut[i] = centerOut[i + HOP]
                leftOut[i] = leftOut[i + HOP]
                rightOut[i] = rightOut[i + HOP]
                norm[i] = norm[i + HOP]
            }
            Arrays.fill(centerOut, FRAME - HOP, FRAME, 0f)
            Arrays.fill(leftOut, FRAME - HOP, FRAME, 0f)
            Arrays.fill(rightOut, FRAME - HOP, FRAME, 0f)
            Arrays.fill(norm, FRAME - HOP, FRAME, 0f)

            position += HOP
            steps++
            onFrames(steps)
        }
    }

    /**
     * Polosa uchun vokal koeffitsienti: 0 — polosa butunlay cholg'uniki,
     * 1 — butunlay markaziy.
     *
     * [Mode.REMOVE_VOCALS] da har doim 1: markaz qanday bo'lishidan qat'i
     * nazar butunlay ayiriladi (aniq, sun'iy tovushsiz).
     *
     * [Mode.SPLIT] da markazning ustunligi o'lchanadi:
     * `d = |M|² / (|M|² + |S|²)` va koeffitsient `d^gamma`. Jimlikda
     * koeffitsient 0 — jimlikni «vokal» deb atash ma'nosiz bo'lardi.
     */
    private fun maskFor(
        mode: Mode,
        gamma: Double,
        leftReal: Double,
        leftImaginary: Double,
        rightReal: Double,
        rightImaginary: Double,
    ): Double {
        if (mode == Mode.REMOVE_VOCALS) return 1.0
        val midReal = (leftReal + rightReal) / 2.0
        val midImaginary = (leftImaginary + rightImaginary) / 2.0
        val sideReal = (leftReal - rightReal) / 2.0
        val sideImaginary = (leftImaginary - rightImaginary) / 2.0
        val mid = midReal * midReal + midImaginary * midImaginary
        val side = sideReal * sideReal + sideImaginary * sideImaginary
        if (mid + side < SILENCE_POWER) return 0.0
        return (mid / (mid + side)).pow(gamma)
    }

    /** Davriy Xann oynasi: 75 % qoplanishda yig'indisi bir tekis bo'ladi. */
    private fun hannWindow(): DoubleArray = DoubleArray(FRAME) { i ->
        0.5 - 0.5 * cos(2.0 * PI * i / FRAME)
    }
}
