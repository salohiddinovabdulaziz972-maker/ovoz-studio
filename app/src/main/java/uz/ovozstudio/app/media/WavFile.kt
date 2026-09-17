package uz.ovozstudio.app.media

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** WAV faylning o'qilgan sarlavhasi. */
data class WavInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataSize: Long,
) {
    val bytesPerFrame: Int get() = channels * bitsPerSample / 8

    val frames: Long get() = if (bytesPerFrame == 0) 0L else dataSize / bytesPerFrame

    val durationMs: Long
        get() = if (sampleRate <= 0) 0L else frames * 1000L / sampleRate

    fun msToFrame(ms: Long): Long = ms.coerceAtLeast(0) * sampleRate / 1000L

    fun frameToMs(frame: Long): Long = if (sampleRate <= 0) 0L else frame * 1000L / sampleRate
}

object WavFile {

    private const val RIFF = "RIFF"
    private const val WAVE = "WAVE"
    private const val FMT = "fmt "
    private const val DATA = "data"

    /**
     * WAV sarlavhasini o'qib, ma'lumot blokining joyini va formatini qaytaradi.
     * Chunk'lar ketma-ket o'qiladi — ba'zi dasturlar `fmt ` va `data` orasiga
     * o'z bloklarini qo'shadi, shuning uchun qat'iy siljishlarga tayanmaymiz.
     */
    @Throws(IOException::class)
    fun readInfo(file: File): WavInfo {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 44) throw IOException("Fayl juda kichik: ${file.name}")

            val riff = ByteArray(4)
            raf.readFully(riff)
            if (String(riff, Charsets.US_ASCII) != RIFF) {
                throw IOException("RIFF sarlavhasi topilmadi: ${file.name}")
            }
            raf.skipBytes(4) // umumiy hajm — ishlatilmaydi
            val wave = ByteArray(4)
            raf.readFully(wave)
            if (String(wave, Charsets.US_ASCII) != WAVE) {
                throw IOException("WAVE formati emas: ${file.name}")
            }

            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            var dataOffset = -1L
            var dataSize = 0L

            val header = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                raf.readFully(header)
                val chunkId = String(header, 0, 4, Charsets.US_ASCII)
                val chunkSize = header.readIntLe(4).toLong() and 0xFFFFFFFFL
                val chunkStart = raf.filePointer

                when (chunkId) {
                    FMT -> {
                        if (chunkSize < 16) throw IOException("fmt bloki buzuq")
                        val fmt = ByteArray(16)
                        raf.readFully(fmt)
                        val audioFormat = fmt.readShortLe(0)
                        if (audioFormat != 1 && audioFormat != 0xFFFE) {
                            throw IOException("Faqat PCM WAV qo'llab-quvvatlanadi (format=$audioFormat)")
                        }
                        channels = fmt.readShortLe(2)
                        sampleRate = fmt.readIntLe(4)
                        bitsPerSample = fmt.readShortLe(14)
                    }
                    DATA -> {
                        dataOffset = chunkStart
                        // Sarlavhadagi hajm noto'g'ri bo'lishi mumkin — faylning
                        // haqiqiy uzunligidan kattasini tanlaymiz.
                        val available = raf.length() - chunkStart
                        dataSize = if (chunkSize <= 0 || chunkSize > available) available else chunkSize
                    }
                }

                // Chunk hajmi toq bo'lsa, keyingisi juft chegaradan boshlanadi.
                val advance = chunkSize + (chunkSize and 1L)
                val next = chunkStart + advance
                if (next <= chunkStart || next > raf.length()) break
                raf.seek(next)
            }

            if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0) {
                throw IOException("WAV formati o'qilmadi: ${file.name}")
            }
            if (dataOffset < 0) throw IOException("data bloki topilmadi: ${file.name}")

            return WavInfo(
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = bitsPerSample,
                dataOffset = dataOffset,
                dataSize = dataSize,
            )
        }
    }

    private fun ByteArray.readIntLe(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.readShortLe(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}
