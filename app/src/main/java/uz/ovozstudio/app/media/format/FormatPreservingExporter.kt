package uz.ovozstudio.app.media.format

import java.io.File
import java.io.IOException

/** Eksport natijasi. */
sealed interface ExportOutcome {

    /**
     * Fayl yozildi. [format] — haqiqatda yozilgan format: u
     * [ExportDecision.Preserved.format] bilan bir xil bo'lishi kerak, lekin
     * manba va tahrirlangan audio parametrlari mos kelmasa farq qilishi
     * mumkin — shuning uchun haqiqiy qiymat qaytariladi.
     */
    data class Done(
        val destination: File,
        val format: AudioFormat,
        val frames: Long,
    ) : ExportOutcome

    /**
     * Bu formatga yozib bo'lmaydi. Fayl yaratilmadi. Foydalanuvchi tanlov
     * qilishi kerak — [ExportDecision.Fallback] uni tayyor holda beradi.
     */
    data class Unsupported(
        val source: AudioFormat,
        val reason: FallbackReason,
    ) : ExportOutcome
}

/**
 * Tahrirlangan WAV faylni manba formatida qaytarib beradi.
 *
 * Foydalanuvchi qanday formatda yuklagan bo'lsa, natija ham shu formatda
 * bo'ladi: tahrirlash ichkarida yo'qotishsiz PCM ustida ketadi, bu sinf esa
 * natijani manbaning konteyneriga va kodekiga qayta kodlaydi.
 *
 * Namuna chastotasi, kanal soni va bit chuqurligi **tahrirlangan fayldan**
 * olinadi, konteyner va kodek esa **manbadan**. Bu ataylab shunday: agar
 * tahrirlash jarayonida namuna parametrlari o'zgargan bo'lsa, sarlavha
 * ichidagi haqiqatga mos kelishi kerak — aks holda fayl o'zini noto'g'ri
 * e'lon qilardi.
 *
 * @param apiLevel qurilma API darajasi. Parametr sifatida beriladi, chunki
 *   OGG/Opus imkoniyati shunga bog'liq va shu tufayli sinfni oddiy JVM
 *   testida tekshirish mumkin.
 * @param extraEncoder qo'shimcha kodlovchi zavodi. Android kodeklari
 *   (MP3, M4A, Opus) MediaCodec'ga tayanadi va shu yerda ulanadi — bu sinf
 *   ularga bevosita bog'lanmaydi.
 */
class FormatPreservingExporter(
    private val apiLevel: Int,
    private val extraEncoder: ((AudioFormat, File) -> AudioEncoder?)? = null,
) {

    /**
     * [edited] — tahrirlash natijasidagi WAV; [sourceFormat] — import
     * qilingan faylning formati; [destination] — yoziladigan yangi fayl.
     *
     * Manba faylning o'zi bu yerda ishtirok etmaydi va hech qachon
     * o'zgartirilmaydi: natija har doim yangi faylga yoziladi.
     */
    @Throws(IOException::class)
    fun export(
        edited: File,
        sourceFormat: AudioFormat,
        destination: File,
        override: AudioFormat? = null,
    ): ExportOutcome {
        val target = when (val decision = FormatSupport.resolve(sourceFormat, apiLevel, override)) {
            is ExportDecision.Fallback ->
                return ExportOutcome.Unsupported(sourceFormat, decision.reason)
            is ExportDecision.Preserved -> decision.format
        }

        WavPcmReader(edited).use { reader ->
            // Namuna parametrlari tahrirlangan fayldan olinadi — fayl ichidagi
            // sarlavha bilan bir xil bo'lishi uchun. Bu yo'qotishli
            // kodeklarga ham tegishli: `bitDepth` u yerda konteyner
            // sarlavhasi emas, kodlovchiga kelayotgan PCM'ning shkalasi
            // (24-bit manba 24-bit bo'lib kelishi kerak, aks holda kodlovchi
            // uni 16-bit deb hisoblab, ovozni butunlay buzardi).
            val actual = target.copy(
                sampleRate = reader.format.sampleRate,
                channels = reader.format.channels,
                bitDepth = reader.format.bitDepth,
            )

            val encoder = openEncoder(actual, destination)
                ?: return ExportOutcome.Unsupported(sourceFormat, FallbackReason.NO_ENCODER)

            encoder.use { sink ->
                val frames = pump(reader, sink)
                sink.finish()
                return ExportOutcome.Done(destination, actual, frames)
            }
        }
    }

    private fun openEncoder(target: AudioFormat, destination: File): AudioEncoder? = when {
        target.container == AudioContainer.WAV && target.codec == AudioCodec.PCM ->
            WavPcmEncoder(destination, target)

        target.container == AudioContainer.FLAC && target.codec == AudioCodec.FLAC ->
            FlacEncoder(
                file = destination,
                sampleRate = target.sampleRate,
                channels = target.channels,
                bitsPerSample = target.bitDepth ?: DEFAULT_BIT_DEPTH,
            )

        target.container == AudioContainer.MP3 && target.codec == AudioCodec.MP3 ->
            Mp3Encoder(destination, target)

        else -> extraEncoder?.invoke(target, destination)
    }

    /** Namunalarni oqim bilan o'tkazadi — butun fayl xotiraga yuklanmaydi. */
    @Throws(IOException::class)
    private fun pump(reader: WavPcmReader, sink: AudioEncoder): Long {
        val buffer = IntArray(CHUNK_FRAMES * reader.format.channels)
        var total = 0L
        while (true) {
            val frames = reader.read(buffer, CHUNK_FRAMES)
            if (frames <= 0) break
            sink.write(buffer, frames)
            total += frames
        }
        return total
    }

    private companion object {
        const val CHUNK_FRAMES = 4096
        const val DEFAULT_BIT_DEPTH = 16
    }
}
