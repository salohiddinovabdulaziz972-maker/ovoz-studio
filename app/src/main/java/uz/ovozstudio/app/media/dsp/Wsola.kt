package uz.ovozstudio.app.media.dsp

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavSampleReader
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * WSOLA — tezlikni o'zgartirish, ohangni tegmasdan.
 *
 * Oddiy tezlashtirish (har ikkinchi namunani olish) ohangni ham ko'taradi:
 * «chipmunk» effekti. WSOLA boshqacha ishlaydi — u tovushni bo'laklarga
 * bo'lib, ularni **ustma-ust qo'yadi** (overlap-add). Bo'laklar orasidagi
 * masofa o'zgaradi, tovushning o'zi esa o'zgarmaydi, shuning uchun ohang
 * joyida qoladi.
 *
 * Bo'laklarni shunchaki ustma-ust qo'yishning o'zi yetmaydi: ular fazasi
 * mos kelmasa, chegaralarda «qarsillash» eshitiladi. Shuning uchun har bir
 * yangi bo'lak shunchaki kerakli joydan olinmaydi, balki atrofdagi
 * nomzodlar orasidan oldingi bo'lakning **tabiiy davomiga** eng o'xshashi
 * tanlanadi (`bestShift`). Bu — WSOLA ning o'zi: Waveform Similarity
 * Overlap-Add, ya'ni «to'lqin shakli o'xshashligi bo'yicha ustma-ust
 * qo'yish».
 *
 * Ishlatiladigan belgilar:
 *   - [n] — tahlil oynasi uzunligi (~30 ms);
 *   - [synthesisHop] — chiqishda qo'shni bo'laklar orasidagi masofa;
 *   - [analysisHop] — kirishdan olinadigan qadam: `synthesisHop * speed`.
 *
 * Tezlik 1.0 bo'lganda qadamlar teng bo'ladi, ya'ni algoritm o'z-o'zidan
 * aynan tiklashga aylanadi — bu tekshiruvda alohida sinaladi.
 */
object Wsola {

    /**
     * Tahlil oynasi uzunligi — taxminan 30 millisoniya.
     *
     * Qisqaroq oyna tez o'zgaradigan tovushni yaxshi kuzatadi, lekin past
     * chastotalarni ushlab tura olmaydi; uzuni esa aksincha. 30 ms —
     * nutq va musiqa uchun keng qabul qilingan murosa.
     */
    internal fun frameSize(sampleRate: Int): Int = (sampleRate * 30 / 1000).coerceIn(256, 4096)

    /** Korrelyatsiya izlanadigan oraliq — oynaning choragi. */
    internal fun searchRadius(frameSize: Int): Int = frameSize / 8

    /**
     * Og'irlik shu chegaradan kichik bo'lsa, bo'linish o'tkazib yuboriladi.
     *
     * Sabab: Xann oynasining chetlari nolga intiladi. Ularga bo'lish
     * cheksiz kattalashuv berardi — bir necha namunadagi shovqin butun
     * bo'lakni bosib ketardi. Shu chegara oynaning eng chekkasidagi
     * ~0.3 ms ni jimgina nolga aylantiradi.
     */
    private const val MIN_WEIGHT = 1e-3

    /** Korrelyatsiyada shu quvvatdan past nomzodlar e'tiborsiz qoldiriladi. */
    private const val ENERGY_FLOOR = 1e-9

    /**
     * [source] ni [speed] marta tezlashtiradi (yoki sekinlashtiradi) va
     * [dest] ga yozadi. Ohang o'zgarmaydi.
     *
     * [speed] — **tezlik**: 2.0 bo'lsa fayl ikki marta qisqa eshitiladi.
     * Ya'ni chiqish uzunligi `kirish / speed`.
     */
    @Throws(IOException::class)
    fun stretch(
        source: File,
        dest: File,
        speed: Double,
        onProgress: (Float) -> Unit = {},
    ): WavInfo {
        require(speed > 0.0) { "Tezlik musbat bo'lishi kerak" }

        WavSampleReader(source).use { reader ->
            val info = reader.info
            if (info.frames <= 0) throw IOException("Fayl bo'sh")
            val depth = BitDepth.of(info.bitsPerSample)
                ?: throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")

            val channels = info.channels
            val total = info.frames

            val n = frameSize(info.sampleRate)
            val synthesisHop = n / 2
            val overlap = n - synthesisHop
            val analysisHop = (synthesisHop * speed).roundToInt().coerceAtLeast(1)
            val radius = searchRadius(n)

            val outFrames = (total.toDouble() / speed).roundToLong().coerceAtLeast(1)

            val hann = FloatArray(n) { i -> (0.5 - 0.5 * cos(2.0 * PI * i / n)).toFloat() }

            // Oyna yetadigan hajm: tahlil bo'lagi ([n]), orqaga izlash
            // ([radius]), oldinga izlash ([radius]) va oldingi segmentning
            // davomi uchun qadam ([analysisHop]).
            val capacity = 2 * n + 2 * radius + analysisHop + 16
            val window = PcmWindow(reader, capacity)

            val pendingFrames = n + 8
            val pending = FloatArray(pendingFrames * channels)
            val weights = FloatArray(pendingFrames)
            val chunk = FloatArray(n * channels)
            val reference = FloatArray(overlap * channels)

            var base = 0L        // pending[0] qaysi chiqish kadriga to'g'ri keladi
            var placed = 0L      // qo'yilgan oynalarning oxiri
            var produced = 0L    // faylga yozilgan kadrlar
            var previous = 0L    // oldingi bo'lak qaysi kirish kadridan boshlangan
            var reported = -1    // oxirgi xabar qilingan foiz

            val writer = WavWriter(dest, info.sampleRate, channels, depth)
            try {
                var k = 0L
                while (k * synthesisHop < outFrames) {
                    val offset = k * synthesisHop
                    val target = k * analysisHop

                    val start = if (k == 0L) {
                        0L
                    } else {
                        // Shu yerdan keyin eng past murojaat: oldingi bo'lakning
                        // davomi va izlash oynasining chap cheti.
                        window.ensure(
                            target + n + radius,
                            keepFrom = minOf(target - radius, previous + synthesisHop),
                        )
                        window.copyFrames(previous + synthesisHop, overlap, reference)
                        target + bestShift(window, target, radius, total, reference, overlap, channels)
                    }

                    window.ensure(start + n, keepFrom = start)
                    val shift = (offset - base).toInt()
                    for (i in 0 until n) {
                        val slot = shift + i
                        if (slot >= pendingFrames) break
                        val weight = hann[i]
                        val frame = start + i
                        val from = window.offset(frame)
                        val into = slot * channels
                        if (window.loaded(frame)) {
                            for (channel in 0 until channels) {
                                pending[into + channel] += window.samples[from + channel] * weight
                            }
                        }
                        weights[slot] += weight
                    }
                    placed = maxOf(placed, offset + n)

                    // Shu oynadan keyin `offset + synthesisHop` gacha bo'lgan
                    // chiqish allaqachon to'liq: keyingi oynalar faqat undan
                    // keyin boshlanadi.
                    produced += emit(pending, weights, chunk, channels, writer,
                        (offset + synthesisHop - base).toInt(), outFrames - produced)

                    val done = (offset + synthesisHop - base).toInt()
                    val rest = ((placed - base) - done).toInt().coerceAtLeast(0)
                    if (rest > 0) {
                        System.arraycopy(pending, done * channels, pending, 0, rest * channels)
                        System.arraycopy(weights, done, weights, 0, rest)
                    }
                    // Qolgan qismini tozalash shart: keyingi oyna o'sha
                    // joylarga **qo'shiladi**, ya'ni eski qiymatlar u yerda
                    // qolsa, ular yangi tovushga qo'shilib ketardi. Bu xato
                    // jimgina — faqat uchinchi oynadan boshlab eshitilardi.
                    java.util.Arrays.fill(pending, rest * channels, pending.size, 0f)
                    java.util.Arrays.fill(weights, rest, weights.size, 0f)
                    base += done

                    previous = start
                    k++
                    // Ko'rsatkich yozilgan hajm bo'yicha: hisoblangan qadam
                    // emas. Aks holda u oxirida 1 dan oshib ketardi. Va faqat
                    // foiz o'zgarganda — qolgani ko'rsatkichni chizadigan
                    // tomon uchun bekorga yuk.
                    val percent = (produced * 100 / outFrames).toInt().coerceIn(0, 100)
                    if (percent != reported) {
                        reported = percent
                        onProgress(percent / 100f)
                    }
                }

                // Oxirgi oynaning qolgan qismi. Yuqoridagi sikl shartiga
                // ko'ra chiqish to'liq qoplangan bo'lishi kerak, lekin
                // yaxlitlash hisobiga bir necha kadr yetmay qolishi mumkin —
                // o'shanda oxiriga jimlik qo'shiladi.
                produced += emit(pending, weights, chunk, channels, writer,
                    ((placed - base).toInt()).coerceAtLeast(0), outFrames - produced)
                while (produced < outFrames) {
                    val want = minOf(chunk.size.toLong() / channels, outFrames - produced).toInt()
                    java.util.Arrays.fill(chunk, 0, want * channels, 0f)
                    writer.write(chunk, want)
                    produced += want
                }
            } finally {
                writer.close()
            }
            onProgress(1.0f)
            return WavFile.readInfo(dest)
        }
    }

    /**
     * Oynani normallashtirib, faylga yozadi. Qaytaradi: yozilgan kadrlar.
     *
     * Normallashtirish shart: Xann oynalari chetlarda noldan boshlanadi,
     * ya'ni eng boshdagi va eng oxirgi bo'laklar to'liq qoplanmagan. Ularni
     * og'irlik yig'indisiga bo'lish aynan tiklashni beradi — aks holda fayl
     * boshida va oxirida ovoz jimgina pasayib qolardi.
     */
    private fun emit(
        pending: FloatArray,
        weights: FloatArray,
        chunk: FloatArray,
        channels: Int,
        writer: WavWriter,
        frames: Int,
        limit: Long,
    ): Long {
        val count = minOf(frames.toLong(), limit, chunk.size.toLong() / channels).toInt()
        if (count <= 0) return 0
        for (i in 0 until count) {
            val weight = weights[i]
            val into = i * channels
            if (weight > MIN_WEIGHT) {
                for (channel in 0 until channels) {
                    chunk[into + channel] = pending[into + channel] / weight
                }
            } else {
                for (channel in 0 until channels) chunk[into + channel] = 0f
            }
        }
        writer.write(chunk, count)
        return count.toLong()
    }

    /**
     * [target] atrofidan oldingi bo'lakning davomiga eng o'xshash siljishni
     * topadi. Qaytaradi: eng yaxshi siljish ([target] ga nisbatan).
     *
     * Mezon — normallashtirilgan o'zaro korrelyatsiya. Normallashtirish
     * shart: usiz balandroq bo'lak har doim «o'xshashroq» chiqardi va tanlov
     * shunchaki eng baland joyga tushib qolardi.
     */
    private fun bestShift(
        window: PcmWindow,
        target: Long,
        radius: Int,
        total: Long,
        reference: FloatArray,
        overlap: Int,
        channels: Int,
    ): Long {
        val low = maxOf(-radius.toLong(), -target)
        val high = minOf(radius.toLong(), total - overlap - target)
        if (high < low) return 0L

        var bestScore = Double.NEGATIVE_INFINITY
        var best = 0L
        var shift = low
        while (shift <= high) {
            var dot = 0.0
            var energy = 0.0
            for (i in 0 until overlap) {
                val from = window.offset(target + shift + i)
                val into = i * channels
                for (channel in 0 until channels) {
                    val candidate = window.samples[from + channel].toDouble()
                    dot += reference[into + channel] * candidate
                    energy += candidate * candidate
                }
            }
            // Jim bo'lakda korrelyatsiya ma'nosiz — uni eng yaxshi deb
            // hisoblash tasodifiy siljishga olib kelardi.
            if (energy > ENERGY_FLOOR) {
                val score = dot / sqrt(energy)
                if (score > bestScore) {
                    bestScore = score
                    best = shift
                }
            }
            shift++
        }
        return best
    }
}
