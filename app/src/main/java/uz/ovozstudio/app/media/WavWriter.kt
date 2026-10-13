package uz.ovozstudio.app.media

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import uz.ovozstudio.app.log.ErrorLog
import kotlin.math.roundToInt

/**
 * PCM WAV faylini yozadi.
 *
 * Kirish har doim float (−1.0 … 1.0) — shunda 16 va 24-bit chiqishni bir xil
 * kod bilan berish mumkin, va yozib olish paytida aniqlik yo'qolmaydi.
 *
 * Masshtab o'qish tomoni bilan AYNAN bir xil: butun son `2^(n−1)` ga bo'linib
 * floatga o'tadi, qaytishda shunga ko'paytiriladi va chegaraga qisiladi.
 * Ilgari yozuvchi `2^(n−1) − 1` ga ko'paytirardi (o'qish esa `2^(n−1)` ga
 * bo'lardi): har bir kesish yoki o'chirish bosqichi namunalarni bir birlik
 * pasaytirib, ularni asldan farqlab qo'yardi. Endi tegilmagan namunalar
 * float orqali o'tgach ham bayt-bayt o'zgarmaydi.
 *
 * WAV sarlavhasidagi o'lchamlar fayl yopilganda to'ldiriladi: yozish paytida
 * umumiy hajm hali noma'lum.
 */
class WavWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channels: Int,
    private val bitDepth: BitDepth,
) : Closeable {

    private val bytesPerSample = bitDepth.bits / 8
    private val bytesPerFrame = bytesPerSample * channels
    private val out = BufferedOutputStream(FileOutputStream(file), BUFFER_BYTES)

    /**
     * Bayt buferi: har `write` chaqiruvida yangisini yaratish o'rniga qayta
     * ishlatiladi (chaqiruvlar soni o'n minglab, har biri o'nlab kilobayt).
     */
    private var scratch = ByteArray(0)

    private var framesWritten = 0L
    private var closed = false

    init {
        writeHeader(dataSize = 0)
    }

    /** Nechta kadr (frame — barcha kanallar uchun bitta namuna nuqtasi) yozilgan. */
    val frames: Long get() = framesWritten

    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else framesWritten * 1000L / sampleRate

    /**
     * [samples] massivining birinchi [count] ta float qiymatini WAV faylga yozadi.
     * Massiv uzunligi `count * channels` bo'lishi kerak.
     */
    fun write(samples: FloatArray, count: Int) {
        check(!closed) { "WavWriter allaqachon yopilgan" }
        require(count >= 0) { "count manfiy bo'lishi mumkin emas" }
        // Massivda so'ralganicha namuna bo'lmasa, faqat mavjudini yozamiz.
        // Hisoblagich ham haqiqatda yozilgan kadrlarni sanaydi — aks holda
        // sarlavha fayldagidan ko'proq ma'lumot va'da qilib, fayl buzilardi.
        val frames = minOf(count, samples.size / channels)
        val sampleCount = frames * channels
        val byteCount = sampleCount * bytesPerSample
        if (scratch.size < byteCount) scratch = ByteArray(byteCount)
        val buffer = scratch
        var offset = 0
        // Chuqurlik sikldan tashqarida tanlanadi: har namuna uchun qayta
        // tekshirish yuz millionlab ortiqcha shartga aylanardi.
        when (bitDepth) {
            BitDepth.BIT_16 -> {
                for (i in 0 until sampleCount) {
                    val value = (samples[i].coerceIn(-1f, 1f) * SHORT_SCALE).roundToInt()
                        .coerceIn(-SHORT_LIMIT, SHORT_LIMIT - 1)
                    buffer[offset++] = (value and 0xFF).toByte()
                    buffer[offset++] = ((value shr 8) and 0xFF).toByte()
                }
            }
            BitDepth.BIT_24 -> {
                for (i in 0 until sampleCount) {
                    val value = (samples[i].coerceIn(-1f, 1f) * INT24_SCALE).roundToInt()
                        .coerceIn(-INT24_LIMIT, INT24_LIMIT - 1)
                    buffer[offset++] = (value and 0xFF).toByte()
                    buffer[offset++] = ((value shr 8) and 0xFF).toByte()
                    buffer[offset++] = ((value shr 16) and 0xFF).toByte()
                }
            }
        }
        out.write(buffer, 0, byteCount)
        framesWritten += frames
    }

    /**
     * Butun sonli namunalarni to'g'ridan-to'g'ri yozadi — float orqali
     * o'tkazmasdan.
     *
     * Formatni saqlab qolish uchun bu shart: float32 faqat 24 bitgacha
     * bo'lgan butun sonlarni aniq saqlaydi, 24-bit tovush esa ±2^23
     * chegarasida yuradi. Namuna float orqali o'tib qaytsa, chegaradagi
     * qiymatlar bir birlikka surilib ketardi. Butun sonni butun son
     * sifatida uzatganda natija manba bilan bayt-bayt bir xil bo'ladi.
     */
    fun writeIntegers(samples: IntArray, count: Int) {
        check(!closed) { "WavWriter allaqachon yopilgan" }
        require(count >= 0) { "count manfiy bo'lishi mumkin emas" }
        val frames = minOf(count, samples.size / channels)
        val sampleCount = frames * channels
        val byteCount = sampleCount * bytesPerSample
        if (scratch.size < byteCount) scratch = ByteArray(byteCount)
        val buffer = scratch
        var offset = 0
        val min = -(1 shl (bitDepth.bits - 1))
        val max = (1 shl (bitDepth.bits - 1)) - 1
        if (bytesPerSample == 2) {
            for (i in 0 until sampleCount) {
                val value = samples[i].coerceIn(min, max)
                buffer[offset++] = (value and 0xFF).toByte()
                buffer[offset++] = ((value shr 8) and 0xFF).toByte()
            }
        } else {
            for (i in 0 until sampleCount) {
                val value = samples[i].coerceIn(min, max)
                buffer[offset++] = (value and 0xFF).toByte()
                buffer[offset++] = ((value shr 8) and 0xFF).toByte()
                buffer[offset++] = ((value shr 16) and 0xFF).toByte()
            }
        }
        out.write(buffer, 0, byteCount)
        framesWritten += frames
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            // `close()` buferni o'zi yuvadi va oqimni xato bo'lganda ham
            // yopadi. Alohida `flush()` xato bersa (disk to'lgan), `close()`
            // chaqirilmay qolar va fayl deskriptori oqib ketardi.
            out.close()
        } finally {
            // Sarlavha yopish xato bergan taqdirda ham to'g'rilanadi: yozilgan
            // qism eshitilishi kerak. Sarlavha kattaroq bo'lib qolsa,
            // `WavFile` uni haqiqiy fayl hajmigacha qisqartirib o'qiydi.
            if (!patchHeader()) {
                // 4 GB dan katta: sarlavhaga sig'madi, fayl ochilmaydi.
                // Jimgina qoldirmasdan o'chiramiz va sababini yozamiz.
                ErrorLog.error(
                    "wav.yozish",
                    "Natija 4 GB dan oshdi, sarlavhani yozib bo'lmadi",
                )
                runCatching { file.delete() }
            }
        }
    }

    private fun writeHeader(dataSize: Int) {
        val header = ByteArray(HEADER_SIZE)
        var offset = 0
        offset = header.putAscii(offset, "RIFF")
        offset = header.putIntLe(offset, HEADER_SIZE - 8 + dataSize)
        offset = header.putAscii(offset, "WAVE")
        offset = header.putAscii(offset, "fmt ")
        offset = header.putIntLe(offset, 16)                       // fmt chunk hajmi
        offset = header.putShortLe(offset, 1)                      // audio format: PCM
        offset = header.putShortLe(offset, channels)
        offset = header.putIntLe(offset, sampleRate)
        offset = header.putIntLe(offset, sampleRate * bytesPerFrame) // byte rate
        offset = header.putShortLe(offset, bytesPerFrame)            // block align
        offset = header.putShortLe(offset, bitDepth.bits)
        offset = header.putAscii(offset, "data")
        offset = header.putIntLe(offset, dataSize)
        out.write(header)
    }

    /**
     * Fayl yopilgandan keyin RIFF va data o'lchamlarini haqiqiy qiymatga keltiradi.
     *
     * `false` — hajm 4 GB dan oshdi, sarlavhaga sig'maydi. Bunday fayl
     * nol o'lchamli sarlavha bilan qolib, ochilmaydi; chaqiruvchi uni
     * saqlamasligi kerak.
     *
     * 2 GB dan katta hajm `toInt()` da manfiy ko'rinadi, lekin bayt shakli
     * ishorasiz son bilan bir xil — sarlavhaga aynan shu yoziladi.
     */
    private fun patchHeader(): Boolean {
        val dataSize = framesWritten * bytesPerFrame
        if (dataSize > MAX_DATA_BYTES) return false
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intLe(HEADER_SIZE - 8 + dataSize.toInt()))
            raf.seek(40)
            raf.write(intLe(dataSize.toInt()))
        }
        return true
    }

    private fun intLe(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun ByteArray.putAscii(start: Int, text: String): Int {
        var offset = start
        for (char in text) this[offset++] = char.code.toByte()
        return offset
    }

    private fun ByteArray.putIntLe(start: Int, value: Int): Int {
        this[start] = (value and 0xFF).toByte()
        this[start + 1] = ((value shr 8) and 0xFF).toByte()
        this[start + 2] = ((value shr 16) and 0xFF).toByte()
        this[start + 3] = ((value shr 24) and 0xFF).toByte()
        return start + 4
    }

    private fun ByteArray.putShortLe(start: Int, value: Int): Int {
        this[start] = (value and 0xFF).toByte()
        this[start + 1] = ((value shr 8) and 0xFF).toByte()
        return start + 2
    }

    companion object {
        /** Kanonik 44 baytli PCM WAV sarlavhasi. */
        const val HEADER_SIZE = 44
        private const val BUFFER_BYTES = 256 * 1024
        private const val SHORT_SCALE = 32_768f
        private const val SHORT_LIMIT = 32_768
        private const val INT24_SCALE = 8_388_608f
        private const val INT24_LIMIT = 8_388_608

        /**
         * Sarlavhaga sig'adigan eng katta ma'lumot hajmi: RIFF o'lchami
         * `HEADER_SIZE - 8 + data` ishorasiz 32 bitga (4 GB) sig'ishi kerak.
         * Ilgari chegara `Int.MAX_VALUE` (2 GB) edi: uzun audio kitob (3,3
         * soatdan oshiq, 44,1 kHz stereo) dekodlanganda fayl o'chirilardi.
         */
        private const val MAX_DATA_BYTES = 0xFFFF_FFFFL - (HEADER_SIZE - 8)
    }
}
