package uz.ovozstudio.app.media

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * WAV faylni namuna (sample) aniqligida o'qiydigan o'quvchi.
 *
 * Kesish, bo'lish va silliqlash (fade) shu o'quvchi orqali float ko'rinishida
 * bajariladi — shunda 16 va 24-bit fayllar uchun bitta kod yo'li ishlaydi.
 */
class WavSampleReader(file: File) : Closeable {

    val info: WavInfo = WavFile.readInfo(file)

    private val raf = RandomAccessFile(file, "r")
    private val bytesPerSample = info.bitsPerSample / 8
    private val bytesPerFrame = info.bytesPerFrame

    /** [startFrame] dan boshlab [frameCount] ta kadrni [out] ga yozadi.
     *  Qaytaradi: haqiqatda o'qilgan kadrlar soni. */
    @Throws(IOException::class)
    fun readFrames(startFrame: Long, frameCount: Int, out: FloatArray): Int {
        val available = (info.frames - startFrame).coerceAtLeast(0)
        val toRead = minOf(frameCount.toLong(), available).toInt()
        if (toRead <= 0) return 0

        val byteCount = toRead * bytesPerFrame
        val bytes = ByteArray(byteCount)
        raf.seek(info.dataOffset + startFrame * bytesPerFrame)
        raf.readFully(bytes)

        var byteOffset = 0
        var sampleIndex = 0
        while (sampleIndex < toRead * info.channels) {
            out[sampleIndex] = when (bytesPerSample) {
                2 -> {
                    val value = (bytes[byteOffset].toInt() and 0xFF) or
                        ((bytes[byteOffset + 1].toInt() and 0xFF) shl 8)
                    value.toShort() / 32_768f
                }
                3 -> {
                    val raw = (bytes[byteOffset].toInt() and 0xFF) or
                        ((bytes[byteOffset + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[byteOffset + 2].toInt() and 0xFF) shl 16)
                    // 24-bitdan 32-bitga belgini saqlab kengaytirish.
                    val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
                    signed / 8_388_608f
                }
                else -> throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")
            }
            byteOffset += bytesPerSample
            sampleIndex++
        }
        return toRead
    }

    override fun close() = raf.close()
}

/**
 * Kesish, bo'lish va silliqlash amallari.
 *
 * Har bir amal YANGI faylga yozadi — manba fayl hech qachon o'zgartirilmaydi.
 * Shu tufayli «orqaga qaytarish» oddiy: oldingi faylga qaytish kifoya.
 */
object AudioTrimmer {

    /** Silliqlash uzunligi (millisoniya). 0 — silliqlash yo'q. */
    data class Fades(val fadeInMs: Long = 0, val fadeOutMs: Long = 0)

    /**
     * O'chiriladigan bo'lak: `[startMs, endMs)` — yarim ochiq oraliq.
     *
     * Ataylab `LongRange` emas: `LongRange` da oxirgi element kiradimi yoki
     * yo'qmi — ko'rinmaydi, va aynan shu noaniqlik bir kadr yo'qolishiga olib
     * kelgan edi. Bu yerda kelishuv `copyRange(startMs, endMs)` bilan bir xil.
     */
    data class Cut(val startMs: Long, val endMs: Long)

    private const val CHUNK_FRAMES = 16_384

    /**
     * [source] faylning [startMs] … [endMs] oralig'ini [dest] ga yozadi.
     * Oraliq fayl chegarasidan chiqsa, avtomatik qisqartiriladi.
     */
    @Throws(IOException::class)
    fun copyRange(
        source: File,
        dest: File,
        startMs: Long,
        endMs: Long,
        fades: Fades = Fades(),
        onProgress: (Float) -> Unit = {},
    ): WavInfo {
        WavSampleReader(source).use { reader ->
            val info = reader.info
            val startFrame = info.msToFrame(startMs).coerceIn(0, info.frames)
            val endFrame = info.msToFrame(endMs).coerceIn(startFrame, info.frames)
            val totalFrames = endFrame - startFrame
            if (totalFrames <= 0) throw IOException("Tanlangan oraliq bo'sh")

            val fadeInFrames = info.msToFrame(fades.fadeInMs).coerceIn(0, totalFrames)
            val fadeOutFrames = info.msToFrame(fades.fadeOutMs).coerceIn(0, totalFrames - fadeInFrames)

            val writer = WavWriter(dest, info.sampleRate, info.channels, bitDepthOf(info.bitsPerSample))
            try {
                val buffer = FloatArray(CHUNK_FRAMES * info.channels)
                var frame = startFrame
                while (frame < endFrame) {
                    val want = minOf(CHUNK_FRAMES.toLong(), endFrame - frame).toInt()
                    val got = reader.readFrames(frame, want, buffer)
                    if (got <= 0) break

                    val positionInSelection = frame - startFrame
                    for (i in 0 until got) {
                        val offset = positionInSelection + i
                        var gain = 1f
                        if (fadeInFrames > 0 && offset < fadeInFrames) {
                            gain *= offset.toFloat() / fadeInFrames
                        }
                        if (fadeOutFrames > 0 && offset >= totalFrames - fadeOutFrames) {
                            gain *= (totalFrames - offset).toFloat() / fadeOutFrames
                        }
                        if (gain != 1f) {
                            for (channel in 0 until info.channels) {
                                val index = i * info.channels + channel
                                buffer[index] *= gain
                            }
                        }
                    }
                    writer.write(buffer, got)
                    frame += got
                    onProgress((frame - startFrame).toFloat() / totalFrames)
                }
            } finally {
                writer.close()
            }
            return WavFile.readInfo(dest)
        }
    }

    /**
     * Ko'p nuqtali o'chirish: [ranges] da ko'rsatilgan bo'laklar chiqarib tashlanadi,
     * qolgan qismlar ketma-ket qo'shiladi. Bo'sh bo'laklar e'tiborsiz qoldiriladi.
     */
    @Throws(IOException::class)
    fun deleteRanges(
        source: File,
        dest: File,
        cuts: List<Cut>,
        fades: Fades = Fades(),
        onProgress: (Float) -> Unit = {},
    ): WavInfo {
        WavSampleReader(source).use { reader ->
            val info = reader.info
            val totalFrames = info.frames
            if (totalFrames <= 0) throw IOException("Fayl bo'sh")

            // Barcha oraliqlar kadrlarda va yarim ochiq: `[boshlanish, tugash)`.
            val removed = cuts
                .map { info.msToFrame(it.startMs)..info.msToFrame(it.endMs) }
                .map { it.first.coerceIn(0, totalFrames)..it.last.coerceIn(0, totalFrames) }
                .filter { it.last > it.first }
                .sortedBy { it.first }

            // Qoladigan bo'laklar — o'sha kelishuvda.
            val keep = mutableListOf<LongRange>()
            var cursor = 0L
            for (cut in removed) {
                if (cut.first > cursor) keep += cursor..cut.first
                cursor = maxOf(cursor, cut.last)
            }
            if (cursor < totalFrames) keep += cursor..totalFrames
            if (keep.isEmpty()) throw IOException("Butun fayl o'chirilishini oldini olish")

            val keptFrames = keep.sumOf { (it.last - it.first).toLong() }
            val fadeInFrames = info.msToFrame(fades.fadeInMs).coerceIn(0, keptFrames)
            val fadeOutFrames = info.msToFrame(fades.fadeOutMs).coerceIn(0, keptFrames - fadeInFrames)

            val writer = WavWriter(dest, info.sampleRate, info.channels, bitDepthOf(info.bitsPerSample))
            try {
                val buffer = FloatArray(CHUNK_FRAMES * info.channels)
                var written = 0L
                for (segment in keep) {
                    var frame = segment.first
                    while (frame < segment.last) {
                        val want = minOf(CHUNK_FRAMES.toLong(), segment.last - frame).toInt()
                        val got = reader.readFrames(frame, want, buffer)
                        if (got <= 0) break

                        for (i in 0 until got) {
                            val offset = written + i
                            var gain = 1f
                            if (fadeInFrames > 0 && offset < fadeInFrames) {
                                gain *= offset.toFloat() / fadeInFrames
                            }
                            if (fadeOutFrames > 0 && offset >= keptFrames - fadeOutFrames) {
                                gain *= (keptFrames - offset).toFloat() / fadeOutFrames
                            }
                            if (gain != 1f) {
                                for (channel in 0 until info.channels) {
                                    val index = i * info.channels + channel
                                    buffer[index] *= gain
                                }
                            }
                        }
                        writer.write(buffer, got)
                        written += got
                        frame += got
                        onProgress(written.toFloat() / keptFrames)
                    }
                }
            } finally {
                writer.close()
            }
            return WavFile.readInfo(dest)
        }
    }

    /** Faylni [atMs] nuqtasidan ikki qismga bo'ladi. */
    @Throws(IOException::class)
    fun split(source: File, destFirst: File, destSecond: File, atMs: Long): Pair<WavInfo, WavInfo> {
        val info = WavFile.readInfo(source)
        val first = copyRange(source, destFirst, 0, atMs)
        val second = copyRange(source, destSecond, atMs, info.durationMs)
        return first to second
    }

    /** 8 dan boshqa bit chuqurliklari uchun xato beradi — WavWriter faqat 16/24 ni biladi. */
    private fun bitDepthOf(bits: Int): BitDepth = when (bits) {
        16 -> BitDepth.BIT_16
        24 -> BitDepth.BIT_24
        else -> throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: $bits")
    }
}
