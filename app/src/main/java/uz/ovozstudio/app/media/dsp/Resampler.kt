package uz.ovozstudio.app.media.dsp

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavSampleReader
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Qayta namunalash — uzunlikni o'zgartirib, ohangni ham o'zgartiradi.
 *
 * Amal chastota o'qish tezligini o'zgartirish bilan bir xil: har `ratio`
 * namunani bittaga aylantirsak, fayl `ratio` marta qisqaradi va barcha
 * chastotalar `ratio` marta ko'tariladi. Masalan `ratio = 2` — bir oktava
 * yuqori, uzunlik ikki barobar qisqa.
 *
 * Oddiy «eng yaqin namunani olish» bu yerda yaramaydi: u qo'shni namunalar
 * orasidagi chegarani hisobga olmaydi va natijaga kuchli shovqin qo'shadi.
 * To'g'ri javob — namunalar orasidagi qiymatni **interpolyatsiya** qilish,
 * ya'ni cheksiz uzunlikdagi ideal filtrni (`sinc`) amalda qo'llash.
 *
 * Uzunlikni qisqartirganda (ratio > 1) yana bir muammo bor: kirishda
 * Nyquist yarmidan yuqori bo'lgan hamma narsa past chastotalarga «buralib»
 * tushadi (aliasing). Buni to'sish uchun filtrning kesish chastotasi
 * tushiriladi: `cutoff = min(1, 1/ratio)`. Shu sababli filtr yadrosi
 * uzunlikni qisqartirganda kengayadi.
 *
 * Tezlik uchun yadro jadvalga olinadi: har bir chiqish namunasi uchun
 * `sinc` ni qaytadan hisoblash o'nlab minglab marta ortiqcha ish bo'lardi.
 */
object Resampler {

    /** Jadvaldagi nuqtalar soni — `sinc` davriga yuzlab nuqta to'g'ri keladi. */
    private const val TABLE_POINTS = 8_192

    /** Kesish chastotasi 1 bo'lgandagi yarim yadro uzunligi. */
    private const val HALF_TAPS = 16

    /** Yadro kengayishining yuqori chegarasi — ish hajmi portlamasin. */
    private const val MAX_HALF = 64

    /** Kayzer oynasi parametri: ~-90 dB yon loblar. */
    private const val BETA = 8.6

    /**
     * `besselI0(BETA)` — normallashtiruvchi bo'luvchi. Bir marta hisoblanadi:
     * u `BETA` ga bog'liq, `u` ga emas, ya'ni jadvalning har bir nuqtasida
     * qaytadan hisoblash bekorga ketgan vaqt bo'lardi.
     */
    private val KAISER_NORM = besselI0(BETA)

    /**
     * [source] ni [ratio] marta qayta namunalab [dest] ga yozadi.
     *
     * [ratio] — **chastota koeffitsienti**: 2.0 bir oktava yuqori (uzunlik
     * ikki barobar qisqa), 0.5 bir oktava past.
     */
    @Throws(IOException::class)
    fun resample(
        source: File,
        dest: File,
        ratio: Double,
        onProgress: (Float) -> Unit = {},
    ): WavInfo {
        require(ratio > 0.0) { "Koeffitsient musbat bo'lishi kerak" }

        WavSampleReader(source).use { reader ->
            val info = reader.info
            if (info.frames <= 0) throw IOException("Fayl bo'sh")
            val depth = BitDepth.of(info.bitsPerSample)
                ?: throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")

            val channels = info.channels
            val total = info.frames
            val step = ratio

            val cutoff = minOf(1.0, 1.0 / step)
            val half = ceil(HALF_TAPS / cutoff).toInt().coerceIn(4, MAX_HALF)
            val taps = FloatArray(2 * half)
            val kernel = kernelTable(half, cutoff)

            val outFrames = (total.toDouble() / step).roundToLong().coerceAtLeast(1)

            val window = PcmWindow(reader, capacity = 4 * half + 64)
            val out = FloatArray(channels)
            val writer = WavWriter(dest, info.sampleRate, channels, depth)
            // Ko'rsatkich faqat foiz o'zgarganda beriladi. Har bir namuna
            // uchun chaqirilsa, bir sekundlik tovushda o'n minglab marta
            // chaqirilardi — ko'rsatkichni chizadigan tomon uchun bu
            // foydasiz yuk, chunki ekranda baribir 100 dan ko'p qadam
            // ko'rinmaydi.
            var reported = -1
            try {
                for (j in 0 until outFrames) {
                    val position = j * step
                    val base = floor(position).toLong()
                    val fraction = position - base
                    // Yadro [base - half + 1, base + half] oralig'ini ko'radi;
                    // shundan pastdagisi endi kerak emas.
                    window.ensure(base + half + 1, keepFrom = base - half + 1)

                    var sum = 0.0
                    for (m in taps.indices) {
                        val offset = m - half + 1
                        val value = kernelAt(kernel, half, (offset - fraction))
                        taps[m] = value.toFloat()
                        sum += value
                    }

                    for (channel in 0 until channels) {
                        var acc = 0.0
                        for (m in taps.indices) {
                            val frame = base + m - half + 1
                            // Fayl chekkasida yadro tashqariga chiqadi. Nol
                            // bilan to'ldirish o'sha joyda jimgina pasayish
                            // berardi; chekka namunasini takrorlash esa
                            // tovushni saqlaydi.
                            val clamped = frame.coerceIn(0, total - 1)
                            acc += taps[m] * window.value(clamped, channel)
                        }
                        out[channel] = if (sum > 0.0) (acc / sum).toFloat() else 0f
                    }

                    writer.write(out, 1)
                    val percent = ((j + 1) * 100 / outFrames).toInt()
                    if (percent != reported) {
                        reported = percent
                        onProgress(percent / 100f)
                    }
                }
            } finally {
                writer.close()
            }
            return WavFile.readInfo(dest)
        }
    }

    /**
     * Yadro jadvali: `u ∈ [0, half]` oralig'ida `cutoff · sinc(cutoff·u) · w(u)`.
     *
     * Jadval bir marta — butun fayl uchun — quriladi, chunki `cutoff` va
     * `half` qadam o'zgarmaguncha o'zgarmaydi.
     */
    private fun kernelTable(half: Int, cutoff: Double): FloatArray {
        val table = FloatArray(TABLE_POINTS + 1)
        val scale = TABLE_POINTS.toDouble() / half
        for (i in 0..TABLE_POINTS) {
            val u = i / scale
            table[i] = (cutoff * sinc(cutoff * u) * kaiser(u / half)).toFloat()
        }
        return table
    }

    /** Jadvaldan chiziqli interpolyatsiya bilan o'qiydi. */
    private fun kernelAt(table: FloatArray, half: Int, u: Double): Double {
        val scaled = abs(u) / half * TABLE_POINTS
        if (scaled >= TABLE_POINTS) return 0.0
        val index = floor(scaled).toInt()
        val fraction = scaled - index
        val a = table[index]
        val b = table[index + 1]
        return a + (b - a) * fraction
    }

    private fun sinc(x: Double): Double =
        if (abs(x) < 1e-9) 1.0 else sin(PI * x) / (PI * x)

    /**
     * Kayzer oynasi. Chetlanishni boshqarish uchun [BETA] bilan birga
     * ishlatiladi: katta qiymat yon loblarni pasaytiradi, lekin asosiy
     * lobni kengaytiradi.
     */
    private fun kaiser(t: Double): Double {
        val x = 1.0 - t * t
        if (x <= 0.0) return 0.0
        return besselI0(BETA * sqrt(x)) / KAISER_NORM
    }

    /**
     * Nolinchi tartibli modifikatsiyalangan Bessel funksiyasi — qator
     * yig'indisi orqali. [BETA] = 8.6 uchun yigirma had yetarli aniqlik
     * beradi (keyingi hadlar mashina aniqligidan past).
     */
    private fun besselI0(x: Double): Double {
        var sum = 1.0
        var term = 1.0
        val quarter = x * x / 4.0
        for (k in 1..24) {
            term *= quarter / (k * k)
            sum += term
        }
        return sum
    }
}
