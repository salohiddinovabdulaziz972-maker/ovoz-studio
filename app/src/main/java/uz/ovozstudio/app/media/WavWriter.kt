package uz.ovozstudio.app.media

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/**
 * PCM WAV faylini yozadi.
 *
 * Kirish har doim float (−1.0 … 1.0) — shunda 16 va 24-bit chiqishni bir xil
 * kod bilan berish mumkin, va yozib olish paytida aniqlik yo'qolmaydi.
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
        val buffer = ByteArray(sampleCount * bytesPerSample)
        var offset = 0
        for (i in 0 until sampleCount) {
            val clamped = samples[i].coerceIn(-1f, 1f)
            when (bitDepth) {
                BitDepth.BIT_16 -> {
                    val value = (clamped * SHORT_MAX).roundToInt()
                    buffer[offset++] = (value and 0xFF).toByte()
                    buffer[offset++] = ((value shr 8) and 0xFF).toByte()
                }
                BitDepth.BIT_24 -> {
                    val value = (clamped * INT24_MAX).roundToInt()
                    buffer[offset++] = (value and 0xFF).toByte()
                    buffer[offset++] = ((value shr 8) and 0xFF).toByte()
                    buffer[offset++] = ((value shr 16) and 0xFF).toByte()
                }
            }
        }
        out.write(buffer)
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
        val buffer = ByteArray(sampleCount * bytesPerSample)
        var offset = 0
        val min = -(1 shl (bitDepth.bits - 1))
        val max = (1 shl (bitDepth.bits - 1)) - 1
        for (i in 0 until sampleCount) {
            val value = samples[i].coerceIn(min, max)
            for (byte in 0 until bytesPerSample) {
                buffer[offset++] = ((value shr (8 * byte)) and 0xFF).toByte()
            }
        }
        out.write(buffer)
        framesWritten += frames
    }

    override fun close() {
        if (closed) return
        closed = true
        out.flush()
        out.close()
        patchHeader()
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

    /** Fayl yopilgandan keyin RIFF va data o'lchamlarini haqiqiy qiymatga keltiradi. */
    private fun patchHeader() {
        val dataSize = framesWritten * bytesPerFrame
        if (dataSize > Int.MAX_VALUE) return // 4 GB dan katta WAV qo'llab-quvvatlanmaydi
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(intLe(HEADER_SIZE - 8 + dataSize.toInt()))
            raf.seek(40)
            raf.write(intLe(dataSize.toInt()))
        }
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
        private const val BUFFER_BYTES = 64 * 1024
        private const val SHORT_MAX = 32_767f
        private const val INT24_MAX = 8_388_607f
    }
}
