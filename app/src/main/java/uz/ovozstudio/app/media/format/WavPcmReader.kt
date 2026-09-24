package uz.ovozstudio.app.media.format

import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream

/**
 * WAV faylni bo'laklab o'qiydi va namunalarni butun son ko'rinishida beradi.
 *
 * Qiymat konvensiyasi — faylning bit chuqurligida, ishorali, ya'ni
 * `16 bit` uchun −32768…32767. Bitta istisno: WAV'da 8-bit PCM ishorasiz
 * saqlanadi (0…255, markazi 128), shuning uchun u o'qishda 128 ga
 * suriladi — shunda qolgan barcha chuqurliklar bilan bir xil bo'ladi.
 *
 * Butun fayl xotiraga sig'masligi mumkin, shuning uchun o'qish oqim
 * bilan ketadi.
 */
class WavPcmReader(file: File) : Closeable {

    val info: WavInfo = WavFile.readInfo(file)

    val format = AudioFormat(
        container = AudioContainer.WAV,
        codec = AudioCodec.PCM,
        sampleRate = info.sampleRate,
        channels = info.channels,
        bitDepth = info.bitsPerSample,
    )

    private val input: InputStream = BufferedInputStream(FileInputStream(file), BUFFER_BYTES)
    private val bytesPerSample = info.bitsPerSample / 8
    private val bytesPerFrame = info.bytesPerFrame
    private val byteBuffer = ByteArray(READ_FRAMES * bytesPerFrame)

    private var remainingBytes = info.dataSize
    private var framesRead = 0L

    init {
        require(bytesPerSample in 1..4) { "Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}" }
        skipFully(input, info.dataOffset)
    }

    /** Shu paytgacha o'qilgan kadrlar soni. */
    val frames: Long get() = framesRead

    /**
     * [target] massivga ko'pi bilan [frames] kadr o'qiydi.
     * Qaytaradi: haqiqatda o'qilgan kadrlar soni; fayl tugagan bo'lsa 0.
     */
    @Throws(IOException::class)
    fun read(target: IntArray, frames: Int): Int {
        if (remainingBytes <= 0 || frames <= 0) return 0

        // [READ_FRAMES] chegarasi majburiy: o'qish buferi shunga mo'ljallangan,
        // undan kattaroq so'rov buferdan tashqariga chiqib ketardi.
        val byArray = target.size / info.channels
        val byFile = remainingBytes / bytesPerFrame
        val count = minOf(frames.toLong(), byArray.toLong(), byFile.toLong(), READ_FRAMES.toLong()).toInt()
        if (count <= 0) return 0

        val byteCount = count * bytesPerFrame
        var filled = 0
        while (filled < byteCount) {
            val got = input.read(byteBuffer, filled, byteCount - filled)
            if (got < 0) break
            filled += got
        }
        if (filled < byteCount) {
            // Fayl sarlavhada va'da qilganidan qisqa — bor qismini beramiz.
            remainingBytes = 0
            return decode(byteBuffer, filled, target)
        }

        remainingBytes -= byteCount
        return decode(byteBuffer, byteCount, target)
    }

    override fun close() {
        input.close()
    }

    /** Kichik-endian baytlarni ishorali butun sonlarga aylantiradi. */
    private fun decode(buffer: ByteArray, byteCount: Int, target: IntArray): Int {
        val count = byteCount / bytesPerFrame
        var offset = 0
        var out = 0
        for (frame in 0 until count) {
            for (channel in 0 until info.channels) {
                var raw = 0
                for (byte in 0 until bytesPerSample) {
                    raw = raw or ((buffer[offset++].toInt() and 0xFF) shl (8 * byte))
                }
                target[out++] = if (info.bitsPerSample == 8) {
                    raw - 128 // WAV'da 8 bit ishorasiz saqlanadi
                } else {
                    val shift = 32 - info.bitsPerSample
                    (raw shl shift) shr shift // yuqori bitlarni belgi bilan to'ldiramiz
                }
            }
        }
        framesRead += count
        return count
    }

    /**
     * `InputStream.skip` bir marta chaqirilganda kamroq bayt tashlab ketishi
     * mumkin (ayniqsa fayl oqimlarida) — shuning uchun to'liq tashlanmaguncha
     * takrorlaymiz. `skipNBytes` ishlatilmaydi: u Android'ning eski
     * versiyalarida yo'q.
     */
    private tailrec fun skipFully(stream: InputStream, count: Long) {
        if (count <= 0) return
        val skipped = stream.skip(count)
        if (skipped > 0) return skipFully(stream, count - skipped)
        if (stream.read() < 0) return
        skipFully(stream, count - 1)
    }

    private companion object {
        const val READ_FRAMES = 4096
        const val BUFFER_BYTES = 256 * 1024
    }
}
