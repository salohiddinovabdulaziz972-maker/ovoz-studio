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

    /**
     * Bayt buferi. Har chaqiruvda yangisini yaratish o'rniga qayta ishlatiladi:
     * uzun faylda chaqiruvlar soni o'n minglab, har biri esa yuzlab kilobayt
     * ajratardi — axlat yig'uvchi shuni tozalab ulgurmasdan tezlik tushardi.
     */
    private var scratch = ByteArray(0)

    /** [startFrame] dan boshlab [frameCount] ta kadrni [out] ga yozadi.
     *  Qaytaradi: haqiqatda o'qilgan kadrlar soni. */
    @Throws(IOException::class)
    fun readFrames(startFrame: Long, frameCount: Int, out: FloatArray): Int {
        val available = (info.frames - startFrame).coerceAtLeast(0)
        val toRead = minOf(frameCount.toLong(), available).toInt()
        if (toRead <= 0) return 0

        val byteCount = toRead * bytesPerFrame
        if (scratch.size < byteCount) scratch = ByteArray(byteCount)
        val bytes = scratch
        raf.seek(info.dataOffset + startFrame * bytesPerFrame)
        raf.readFully(bytes, 0, byteCount)

        // Chuqurlik sikldan tashqarida tanlanadi: har bir namuna uchun qayta
        // tekshirish yuz millionlab ortiqcha shartga aylanardi.
        val samples = toRead * info.channels
        when (bytesPerSample) {
            2 -> {
                var byteOffset = 0
                for (index in 0 until samples) {
                    val value = (bytes[byteOffset].toInt() and 0xFF) or
                        ((bytes[byteOffset + 1].toInt() and 0xFF) shl 8)
                    out[index] = value.toShort() / 32_768f
                    byteOffset += 2
                }
            }
            3 -> {
                var byteOffset = 0
                for (index in 0 until samples) {
                    val raw = (bytes[byteOffset].toInt() and 0xFF) or
                        ((bytes[byteOffset + 1].toInt() and 0xFF) shl 8) or
                        ((bytes[byteOffset + 2].toInt() and 0xFF) shl 16)
                    // 24-bitdan 32-bitga belgini saqlab kengaytirish.
                    val signed = if (raw and 0x800000 != 0) raw or -0x1000000 else raw
                    out[index] = signed / 8_388_608f
                    byteOffset += 3
                }
            }
            else -> throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")
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

                    // Silliqlash yo'q bo'lsa (odatiy hol) har bir kadr bo'ylab
                    // yurish ortiqcha: namunalar o'zgarmasdan yoziladi.
                    if (fadeInFrames > 0 || fadeOutFrames > 0) {
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
     * Ko'p nuqtali o'chirish: [cuts] da ko'rsatilgan bo'laklar chiqarib tashlanadi,
     * qolgan qismlar ketma-ket qo'shiladi. Bo'sh bo'laklar e'tiborsiz qoldiriladi,
     * ustma-ust tushganlari birlashtiriladi.
     */
    @Throws(IOException::class)
    fun deleteRanges(
        source: File,
        dest: File,
        cuts: List<Cut>,
        fades: Fades = Fades(),
        joinFadeMs: Long = 0,
        onProgress: (Float) -> Unit = {},
    ): WavInfo {
        WavSampleReader(source).use { reader ->
            val info = reader.info
            val totalFrames = info.frames
            if (totalFrames <= 0) throw IOException("Fayl bo'sh")

            val keep = keptRanges(info, cuts)
            if (keep.isEmpty()) throw IOException("Butun fayl o'chirilishini oldini olish")

            val keptFrames = keep.sumOf { (it.last - it.first).toLong() }
            val fadeInFrames = info.msToFrame(fades.fadeInMs).coerceIn(0, keptFrames)
            val fadeOutFrames = info.msToFrame(fades.fadeOutMs).coerceIn(0, keptFrames - fadeInFrames)

            val writer = WavWriter(dest, info.sampleRate, info.channels, bitDepthOf(info.bitsPerSample))
            try {
                val buffer = FloatArray(CHUNK_FRAMES * info.channels)
                val joinFrames = info.msToFrame(joinFadeMs).coerceAtLeast(0)
                var written = 0L
                for ((segmentIndex, segment) in keep.withIndex()) {
                    // Qism o'chirilganda ikki chekka yonma-yon tushadi va to'lqin bir
                    // zumda boshqa qiymatga sakraydi — quloqqa «chiqillash» bo'lib
                    // eshitiladi. Tutashuv joyida ikki tomondan qisqa silliqlash shuni
                    // yo'qotadi. Faylning boshi va oxiri bunga kirmaydi.
                    val length = segment.last - segment.first
                    val joinIn = if (segmentIndex > 0) minOf(joinFrames, length / 2) else 0L
                    val joinOut = if (segmentIndex < keep.size - 1) minOf(joinFrames, length / 2) else 0L

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
                            val inSegment = frame - segment.first + i
                            if (joinIn > 0 && inSegment < joinIn) {
                                gain *= inSegment.toFloat() / joinIn
                            }
                            if (joinOut > 0 && inSegment >= length - joinOut) {
                                gain *= (length - inSegment).toFloat() / joinOut
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

    /**
     * [cuts] o'chirilgandan keyin qoladigan bo'laklar (kadrlarda, yarim ochiq).
     *
     * Butun hisob-kitob — `deleteRanges` ham, foydalanuvchiga aniq xato
     * ko'rsatish ham — shu bitta funksiyaga tayanadi. Ilgari bu mantiq
     * `deleteRanges` ichida yopiq edi va ViewModel «butun fayl o'chirilmoqda»
     * holatini oldindan ayta olmasdi: xato faqat ish boshlangandan keyin,
     * umumiy «tahrirlab bo'lmadi» ko'rinishida chiqardi.
     */
    fun keptRanges(info: WavInfo, cuts: List<Cut>): List<LongRange> {
        val totalFrames = info.frames
        if (totalFrames <= 0) return emptyList()

        // Barcha oraliqlar kadrlarda va yarim ochiq: `[boshlanish, tugash)`.
        val removed = cuts
            .map { info.msToFrame(it.startMs)..info.msToFrame(it.endMs) }
            .map { it.first.coerceIn(0, totalFrames)..it.last.coerceIn(0, totalFrames) }
            .filter { it.last > it.first }
            .sortedBy { it.first }

        val keep = mutableListOf<LongRange>()
        var cursor = 0L
        for (cut in removed) {
            if (cut.first > cursor) keep += cursor..cut.first
            cursor = maxOf(cursor, cut.last)
        }
        if (cursor < totalFrames) keep += cursor..totalFrames
        return keep
    }

    /** [cuts] butun faylni qamrab oladimi — ya'ni o'chirishdan keyin hech narsa qolmaydimi. */
    fun coversWholeFile(info: WavInfo, cuts: List<Cut>): Boolean =
        info.frames > 0 && keptRanges(info, cuts).isEmpty()

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
