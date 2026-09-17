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
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Shovqin tozalash — spektral ayirish.
 *
 * Usul statistik, neyron tarmoq emas: foydalanuvchi **faqat shovqin
 * eshitiladigan** qismni ko'rsatadi, dastur o'sha qismning o'rtacha quvvat
 * spektrini o'lchaydi va uni butun fayldan polosa-ba-polosa ayiradi. Doimiy
 * shovqin (ventilyator, g'uvillash, fon shovqini, mikrofon shovqini) shu
 * usulda yaxshi yo'qoladi; nutq saqlanib qoladi, chunki uning quvvati
 * shovqindan ancha yuqori.
 *
 * Nega shovqin namunasi kerak: shovqin darajasi har bir yozuvda har xil
 * (telefon, xona, mikrofon). Uni «tahminan» olib, keyin ayirish noto'g'ri
 * bo'lardi — ko'p ayirilsa nutq bo'g'ilib qolardi, oz ayirilsa shovqin
 * qolardi. Namunadan o'lchash aniq javob beradi.
 *
 * Ishlash tartibi:
 *
 * 1. **Namuna o'lchovi** — ko'rsatilgan oraliqdagi kadrlar spektri
 *    o'rtachalanadi, har bir kanal uchun alohida.
 * 2. **Qayta ishlash** — fayl 1024 namunalik kadrlarga bo'linadi (qadam 256,
 *    ya'ni 75 % qoplanish), har bir kadrga oyna qo'yiladi, spektr ayiriladi
 *    va natija qayta yig'iladi (overlap-add). Yig'ishda oynaning kvadrati ham
 *    yig'ib boriladi va oxirida bo'linadi — shunda chetlarda ham amplituda
 *    aniq qoladi.
 *
 * Ikkala amal ham manba faylni o'zgartirmaydi: natija yangi faylga yoziladi.
 */
object NoiseReducer {

    /**
     * Kadr uzunligi (namunada).
     *
     * 1024 — chastota va vaqt aniqligi o'rtasidagi murosa: 48 kHz da bu
     * 21 ms, ya'ni nutq tovushlari ajralib turadi, lekin past chastotalarda
     * (100 Hz atrofida) ham yetarli polosa bor.
     */
    const val FRAME = 1024

    /**
     * Kadrlar orasidagi qadam.
     *
     * Kadrning choragi: har bir namuna to'rt marta qayta ishlanadi. Bu
     * «musiqiy shovqin» (musical noise) ni kamaytiradi — kam qoplanishda
     * ayirish qoldiqlari alohida ohanglar bo'lib eshitiladi.
     */
    const val HOP = FRAME / 4

    /** Kuch chegaralari: shundan pastda shovqin sezilarli kamaymaydi. */
    const val MIN_STRENGTH = 1.0
    const val MAX_STRENGTH = 4.0

    /**
     * Standart kuch.
     *
     * Shovqinli polosada qoladigan quvvat taxminan shunday hisoblanadi:
     * quvvat shovqindan `kuch` marta katta bo'lgan polosalar saqlanadi
     * (e^(-kuch) qismi), qolganlari qoldiq chegarasiga tushadi. 2.5 da
     * natija 8–10 dB pasayadi — bu nutqni sezilarli bo'g'masdan shovqinni
     * yo'q qiladi.
     */
    const val DEFAULT_STRENGTH = 2.5

    /**
     * Qoldiq chegaralari (dB).
     *
     * Ayirishdan keyin shovqin polosasida shuncha quvvat qoldiriladi.
     * Bu «musiqiy shovqin» ga qarshi asosiy chora: 0 dB qoldirilsa, ayirish
     * natijasi tasodifiy ohanglar bo'lib eshitiladi.
     */
    const val MIN_FLOOR_DB = -30.0
    const val MAX_FLOOR_DB = -3.0
    const val DEFAULT_FLOOR_DB = -15.0

    /** Kuchaytirishni silliqlash koeffitsienti (0 — o'chirilgan). */
    const val DEFAULT_SMOOTHING = 0.5

    /**
     * Jim oraliqdan namuna olishga urinilganda beriladigan xato matni.
     *
     * Konstanta sifatida shu yerda turadi, chunki UI qatlami sababni aynan
     * shu matn bo'yicha ajratadi. Jim oraliqni «amal bajarilmadi» deb
     * ko'rsatish foydalanuvchiga hech narsa aytmasdi: u boshqa joyni
     * tanlashi kerakligini bilmasdi.
     */
    const val ERROR_QUIET_SAMPLE = "Shovqin namunasi jim"

    /** Polosalar soni: 0…Nyquist. */
    private const val BINS = FRAME / 2 + 1

    /** Bu quvvatdan past namuna «raqamli jimlik» hisoblanadi (≈ −120 dBFS). */
    private const val SILENCE_POWER = 1e-12

    /** Norma shundan kichik bo'lsa bo'lish bajarilmaydi. */
    private const val NORM_EPSILON = 1e-9

    /**
     * Shovqin tozalash sozlamalari.
     *
     * [noiseStartMs], [noiseEndMs] — shovqin namunasi olinadigan oraliq.
     * [strength] — ayirish koeffitsienti: 1.0 shovqinni ozgina bosadi,
     * 4.0 kuchli bosadi. [floorDb] — ayirishdan keyin qoldiriladigan quvvat.
     */
    data class Settings(
        val noiseStartMs: Long,
        val noiseEndMs: Long,
        val strength: Double = DEFAULT_STRENGTH,
        val floorDb: Double = DEFAULT_FLOOR_DB,
        val smoothing: Double = DEFAULT_SMOOTHING,
    ) {

        /**
         * Sozlama to'g'rimi.
         *
         * Oraliq bo'sh bo'lmasligi shart: nol uzunlikdagi oraliqdan shovqin
         * namunasini olib bo'lmaydi, natija esa tasodifiy bo'lardi.
         */
        fun isValid(): Boolean = noiseEndMs > noiseStartMs &&
            strength >= MIN_STRENGTH && strength <= MAX_STRENGTH &&
            floorDb >= MIN_FLOOR_DB && floorDb <= MAX_FLOOR_DB &&
            smoothing in 0.0..0.95
    }

    /**
     * Natija.
     *
     * [noiseDropDb] — namuna olingan oraliqda shovqin qancha desibel
     * pasaygani. Bu **o'lchangan** son (manba va natija bir xil oraliqda
     * o'qib solishtiriladi), taxmin emas.
     */
    data class Result(val info: WavInfo, val noiseDropDb: Double)

    /**
     * [source] dan shovqinni tozalab [dest] ga yozadi.
     *
     * Chastota va bit chuqurligi o'zgarmaydi: amal faqat spektrni
     * o'zgartiradi, vaqt o'qiga tegmaydi.
     */
    @Throws(IOException::class)
    fun apply(
        source: File,
        dest: File,
        settings: Settings,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val info = WavFile.readInfo(source)
        if (info.frames <= 0) throw IOException("Fayl bo'sh")
        if (!settings.isValid()) throw IOException("Sozlama noto'g'ri")

        val depth = BitDepth.of(info.bitsPerSample)
            ?: throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")

        val rate = info.sampleRate
        val channels = info.channels
        val durationMs = info.frames * 1000L / rate

        val startFrame = (settings.noiseStartMs.coerceAtLeast(0) * rate / 1000L)
            .coerceAtMost(info.frames)
        // Chegara fayldan oshsa — fayl oxirigacha olinadi. Bu xato emas:
        // foydalanuvchi butun faylni shovqin deb belgilashi mumkin.
        val endFrame = (settings.noiseEndMs.coerceAtMost(durationMs) * rate / 1000L)
            .coerceAtMost(info.frames)
        if (endFrame <= startFrame) throw IOException("Shovqin namunasi oraliqdan tashqarida")

        val plan = profilePlan(startFrame, endFrame)
        val totalWork = plan.size + processFrameCount(info.frames)

        val noisePower = DoubleArray(channels * BINS)
        WavSampleReader(source).use { reader ->
            measureProfile(reader, plan, noisePower, channels)
        }

        val meanPower = noisePower.sum() / noisePower.size
        // Jim oraliqdan shovqin profili chiqmaydi. Buni jimgina qabul qilish
        // eng yomon variant bo'lardi: natija manbaning nusxasi bo'lardi,
        // foydalanuvchi esa «tozaladim, lekin o'zgarmadi» deb tushunmasdi.
        if (meanPower < SILENCE_POWER) throw IOException(ERROR_QUIET_SAMPLE)

        val completed = intArrayOf(plan.size)
        var reported = -1

        WavSampleReader(source).use { reader ->
            val window = PcmWindow(reader, capacity = FRAME * 2)
            val writer = WavWriter(dest, rate, channels, depth)
            try {
                process(
                    window = window,
                    writer = writer,
                    frames = info.frames,
                    channels = channels,
                    noisePower = noisePower,
                    alpha = settings.strength,
                    beta = 10.0.pow(settings.floorDb / 10.0),
                    smoothing = settings.smoothing,
                    onFrames = { processed ->
                        completed[0] = plan.size + processed
                        // Ko'rsatkich butun foiz o'zgarganda yangilanadi: har
                        // bir kadrda chaqirish ekranni behuda qayta chizdirardi.
                        val percent = (completed[0] * 100 / totalWork).toInt()
                        if (percent != reported) {
                            reported = percent
                            onProgress((percent / 100f).coerceIn(0f, 1f))
                        }
                    },
                )
            } finally {
                writer.close()
            }
        }

        onProgress(1f)

        val before = regionRms(source, startFrame, endFrame)
        val after = regionRms(dest, startFrame, endFrame)
        val drop = if (before > 0.0 && after > 0.0) 20.0 * log10(before / after) else 0.0

        return Result(info, drop)
    }

    /**
     * Profil uchun kadrlarning boshlanish nuqtalari.
     *
     * Kadrlar oraliq **ichida** turadi: chetdagi kadr oraliqdan tashqariga
     * chiqib ketsa, profilga nutq aralashib ketardi va u butun fayldan
     * ayirilardi — ya'ni nutqning o'zi «shovqin» deb o'chirilardi.
     *
     * Oraliq kadrdan qisqa bo'lsa ham bitta kadr olinadi (u oraliqdan sal
     * tashqariga chiqadi): juda qisqa tanlovni rad etish foydalanuvchi uchun
     * foydasiz, 21 ms lik bitta kadr esa o'rtacha shovqin darajasini
     * baribir to'g'ri baholaydi.
     */
    private fun profilePlan(startFrame: Long, endFrame: Long): LongArray {
        val positions = ArrayList<Long>()
        var s = startFrame
        while (s + FRAME <= endFrame) {
            positions.add(s)
            s += HOP
        }
        if (positions.isEmpty()) positions.add(startFrame)
        return positions.toLongArray()
    }

    /** Qayta ishlashda nechta kadr bo'ladi (ko'rsatkich uchun). */
    private fun processFrameCount(frames: Long): Long =
        max((frames + FRAME - HOP + HOP - 1) / HOP, 1)

    /** Namuna oraliqdagi kadrlar spektrini o'rtachalab [noisePower] ga yozadi. */
    private fun measureProfile(
        reader: WavSampleReader,
        plan: LongArray,
        noisePower: DoubleArray,
        channels: Int,
    ) {
        val fft = Fft(FRAME)
        val taper = hannWindow()
        val pcm = PcmWindow(reader, capacity = FRAME * 2)
        val buffer = FloatArray(FRAME * channels)
        val real = DoubleArray(FRAME)
        val imaginary = DoubleArray(FRAME)

        for (position in plan) {
            pcm.ensure(position + FRAME, keepFrom = position)
            pcm.copyFrames(position, FRAME, buffer)

            for (channel in 0 until channels) {
                for (i in 0 until FRAME) {
                    real[i] = buffer[i * channels + channel] * taper[i]
                    imaginary[i] = 0.0
                }
                fft.forward(real, imaginary)
                val base = channel * BINS
                for (k in 0 until BINS) {
                    noisePower[base + k] += real[k] * real[k] + imaginary[k] * imaginary[k]
                }
            }
        }

        val scale = 1.0 / plan.size
        for (i in noisePower.indices) noisePower[i] *= scale
    }

    /**
     * Kadrlarni qayta ishlab [writer] ga yozadi.
     *
     * To'r fayl boshidan `FRAME - HOP` namuna **oldin** boshlanadi. Bu
     * to'ldirish (padding) sun'iy emas, zarur: birinchi namunani to'rt kadr
     * ham qamrab olishi kerak, aks holda oynalar yig'indisi to'liq bo'lmaydi
     * va faylning boshi jimgina pasayib qolardi. Fayldan tashqaridagi
     * namunalar nol deb olinadi ([PcmWindow] shuni qaytaradi).
     */
    private fun process(
        window: PcmWindow,
        writer: WavWriter,
        frames: Long,
        channels: Int,
        noisePower: DoubleArray,
        alpha: Double,
        beta: Double,
        smoothing: Double,
        onFrames: (Int) -> Unit,
    ) {
        val fft = Fft(FRAME)
        val taper = hannWindow()
        val buffer = FloatArray(FRAME * channels)
        val real = DoubleArray(FRAME)
        val imaginary = DoubleArray(FRAME)

        // Har bir kanal uchun yig'indi va oyna kvadratlarining yig'indisi.
        val out = Array(channels) { FloatArray(FRAME) }
        val norm = FloatArray(FRAME)
        val interleaved = FloatArray(HOP * channels)

        // Oldingi kadrning kuchaytirish koeffitsienti: spektrlar orasidagi
        // sakrashni yumshatadi.
        val previousGain = DoubleArray(channels * BINS)

        var steps = 0
        var position = -(FRAME - HOP).toLong()

        while (position < frames) {
            window.ensure(position + FRAME, keepFrom = position)
            window.copyFrames(position, FRAME, buffer)

            for (i in 0 until FRAME) norm[i] += (taper[i] * taper[i]).toFloat()

            for (channel in 0 until channels) {
                for (i in 0 until FRAME) {
                    real[i] = buffer[i * channels + channel] * taper[i]
                    imaginary[i] = 0.0
                }
                fft.forward(real, imaginary)

                val gainBase = channel * BINS
                for (k in 0 until BINS) {
                    val power = real[k] * real[k] + imaginary[k] * imaginary[k]
                    val noise = noisePower[gainBase + k]

                    // Ayirish: signal quvvatidan shovqin quvvati ayiriladi,
                    // qolgani esa qoldiq chegarasidan pastga tushmaydi.
                    val target = max(power - alpha * noise, beta * noise)

                    // Koeffitsient 1 dan oshmaydi: spektral ayirish faqat
                    // bosadi, kuchaytirmaydi. Chegara bo'lmasa, signal shovqin
                    // ostida qolganda kuchsiz polosalar kuchayib, shovqin
                    // yanada balandroq bo'lib eshitilardi.
                    val raw = if (power > SILENCE_POWER) {
                        sqrt(target / power).coerceAtMost(1.0)
                    } else {
                        0.0
                    }

                    val gain = if (smoothing > 0.0) {
                        smoothing * previousGain[gainBase + k] + (1.0 - smoothing) * raw
                    } else {
                        raw
                    }
                    previousGain[gainBase + k] = gain

                    real[k] *= gain
                    imaginary[k] *= gain
                    // Haqiqiy signal spektri simmetrik: ko'zgu polosa ham
                    // xuddi shu koeffitsientni olishi kerak, aks holda natija
                    // haqiqiy bo'lmay qolardi.
                    if (k in 1 until FRAME / 2) {
                        val mirror = FRAME - k
                        real[mirror] *= gain
                        imaginary[mirror] *= gain
                    }
                }

                fft.inverse(real, imaginary)
                val channelOut = out[channel]
                for (i in 0 until FRAME) channelOut[i] += (real[i] * taper[i]).toFloat()
            }

            // [position, position + HOP) namunalar endi tayyor: ularni qamrab
            // oladigan kadrlarning hammasi hisoblandi.
            var flushed = 0
            for (i in 0 until HOP) {
                val frame = position + i
                if (frame >= frames) break
                if (frame < 0) continue
                val divisor = if (norm[i] > NORM_EPSILON) norm[i].toDouble() else 1.0
                for (channel in 0 until channels) {
                    interleaved[i * channels + channel] =
                        (out[channel][i] / divisor).toFloat()
                }
                flushed = i + 1
            }
            if (flushed > 0) writer.write(interleaved, flushed)

            // Yig'indi massivlari qadam qadar chapga suriladi.
            for (channel in 0 until channels) {
                System.arraycopy(out[channel], HOP, out[channel], 0, FRAME - HOP)
                Arrays.fill(out[channel], FRAME - HOP, FRAME, 0f)
            }
            System.arraycopy(norm, HOP, norm, 0, FRAME - HOP)
            Arrays.fill(norm, FRAME - HOP, FRAME, 0f)

            position += HOP
            steps++
            onFrames(steps)
        }
    }

    /** Davriy Xann oynasi: 75 % qoplanishda yig'indisi bir tekis bo'ladi. */
    private fun hannWindow(): DoubleArray = DoubleArray(FRAME) { i ->
        0.5 - 0.5 * cos(2.0 * PI * i / FRAME)
    }

    /** Oraliqdagi o'rtacha kvadratik qiymat (barcha kanallar birga). */
    private fun regionRms(file: File, startFrame: Long, endFrame: Long): Double {
        if (endFrame <= startFrame) return 0.0
        WavSampleReader(file).use { reader ->
            val channels = reader.info.channels
            val buffer = FloatArray(8192 * channels)
            var sum = 0.0
            var count = 0L
            var frame = startFrame
            while (frame < endFrame) {
                val want = minOf(8192L, endFrame - frame).toInt()
                val got = reader.readFrames(frame, want, buffer)
                if (got <= 0) break
                for (i in 0 until got * channels) {
                    val value = buffer[i].toDouble()
                    sum += value * value
                }
                count += got.toLong() * channels
                frame += got
            }
            return if (count > 0) sqrt(sum / count) else 0.0
        }
    }
}
