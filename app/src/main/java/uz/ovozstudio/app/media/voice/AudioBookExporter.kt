package uz.ovozstudio.app.media.voice

import kotlinx.coroutines.suspendCancellableCoroutine
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.format.AudioCodec
import uz.ovozstudio.app.media.format.AudioContainer
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.Mp3Encoder
import uz.ovozstudio.app.media.format.WavPcmReader
import uz.ovozstudio.app.media.merge.AudioMerger
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Hujjatni MP3 audio-kitobga aylantiradi: har bir abzatsni ovozga o'giradi
 * va bittalab, ketma-ket bitta faylga yig'adi.
 *
 * Uch bosqich, [Progress] shu tartibda keladi:
 *  1. **SYNTHESIZING** — har bir bo'lak ([TextChunker] bilan qirqilgan)
 *     alohida WAV fayliga ovozga o'giriladi ([VoiceEngine.synthesizeToFile]).
 *     Bu — eng uzoq bosqich (bitta ovoz chaqiruvi — bitta bo'lak).
 *  2. **JOINING** — bo'laklar tartib bilan bitta WAV ga qo'shiladi
 *     ([AudioMerger]).
 *  3. **ENCODING** — natija MP3 ga kodlanadi ([Mp3Encoder]). Format hujjatga
 *     emas, ovoz dvigateliga bog'liq — shuning uchun natija konteyneri
 *     har doim MP3, "asl formatni saqlash" qoidasi bu yerga tegishli emas:
 *     bunda "asl format" tushunchasining o'zi yo'q, manba matn.
 *
 * **Foiz uch bosqichda ham 0 dan 1 gacha bir marta o'tadi.** Ilgari har
 * bosqich o'z hisobini noldan boshlardi va yarim yo'lда 100 % dan 0 % ga
 * sakrardi. Ko'rish bilan ishlaydigan foydalanuvchi buni chidasa bo'ladi,
 * ko'r foydalanuvchi esa foizni **eshitadi** — u uchun 100 → 0 → 100 → 0 →
 * 100 "ish boshidan ketdi" degani. Shu sababli har bosqich o'z ulushini
 * oladi: sintez 0.00–0.80, qo'shish 0.80–0.90, kodlash 0.90–1.00.
 *
 * [isCancelled] har bir bo'lak orasida tekshiriladi: uzun kitobda bekor
 * qilish darhol emas, keyingi tekshiruv nuqtasida ta'sir qiladi (odatda
 * bir necha soniya ichida).
 *
 * Vaqtinchalik fayllar (bo'laklar, qo'shilgan WAV) [work] papkasida
 * yaratiladi va ish tugagach — muvaffaqiyatli yoki xato bilan — hammasi
 * tozalanadi. Faqat [destination] qoladi.
 */
object AudioBookExporter {

    enum class Stage { SYNTHESIZING, JOINING, ENCODING }

    data class Progress(val stage: Stage, val fraction: Float)

    /**
     * Bosqichlarning umumiy shkaladagi chegarasi. Yig'indi 1.0 bo'lishi shart.
     */
    private const val SYNTHESIS_END = 0.80f
    private const val JOIN_END = 0.90f

    /** Bosqich ichidagi 0…1 ulushni umumiy shkalaga o'giradi. */
    private fun overall(stage: Stage, fraction: Float, from: Float, to: Float): Float =
        (from + (to - from) * fraction.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** [isCancelled] `true` qaytarganda tashlanadi. */
    class Cancelled : IOException("Bekor qilindi")

    /** Sof matn uchun bitta ovoz chaqiruvi ishlamadi. */
    class SynthesisFailedException(val error: VoiceError) : IOException("Sintez bajarilmadi: $error")

    /**
     * @param paragraphs o'qiladigan matn, abzatslarga bo'lingan (masalan
     *   [uz.ovozstudio.app.media.doc.ReadingText.paragraphs]).
     * @param work vaqtinchalik fayllar uchun bo'sh papka.
     */
    @Throws(IOException::class)
    suspend fun export(
        engine: VoiceEngine,
        paragraphs: List<String>,
        rate: Float,
        work: File,
        destination: File,
        isCancelled: () -> Boolean,
        onProgress: (Progress) -> Unit,
    ) {
        val chunkFiles = ArrayList<File>()
        try {
            val texts = ArrayList<String>()
            for (paragraph in paragraphs) {
                for (chunk in TextChunker.split(paragraph, maxChars = engine.maxSynthChars())) {
                    texts.add(chunk.text)
                }
            }
            if (texts.isEmpty()) throw IOException("O'qiladigan matn yo'q")

            for ((index, text) in texts.withIndex()) {
                if (isCancelled()) throw Cancelled()
                val chunkFile = File(work, "bolak-$index.wav")
                val result = synthesizeOne(engine, SpeechRequest(text = text, rate = rate), chunkFile)
                if (result is SynthesisResult.Failed) throw SynthesisFailedException(result.error)
                chunkFiles.add(chunkFile)
                onProgress(
                    Progress(
                        Stage.SYNTHESIZING,
                        overall(Stage.SYNTHESIZING, (index + 1).toFloat() / texts.size, 0f, SYNTHESIS_END),
                    ),
                )
            }

            if (isCancelled()) throw Cancelled()
            val joined = File(work, "qoshilgan.wav")
            AudioMerger.merge(chunkFiles, joined, work) { fraction ->
                onProgress(Progress(Stage.JOINING, overall(Stage.JOINING, fraction, SYNTHESIS_END, JOIN_END)))
            }

            try {
                if (isCancelled()) throw Cancelled()
                encodeToMp3(joined, destination, isCancelled) { fraction ->
                    onProgress(Progress(Stage.ENCODING, overall(Stage.ENCODING, fraction, JOIN_END, 1f)))
                }
            } finally {
                joined.delete()
            }
        } finally {
            // Chala qolgan bo'laklar (xato yoki bekor qilishda) qolib ketmaydi.
            for (file in chunkFiles) runCatching { file.delete() }
        }
    }

    /** Bitta bo'lakni faylga ovozga o'giradi — callback'ni suspend shaklga o'girib. */
    private suspend fun synthesizeOne(engine: VoiceEngine, request: SpeechRequest, destination: File): SynthesisResult =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { engine.stop() }
            engine.synthesizeToFile(request, destination) { result ->
                if (continuation.isActive) continuation.resume(result)
            }
        }

    /** Qo'shilgan WAV'ni MP3 ga kodlaydi. */
    @Throws(IOException::class)
    private fun encodeToMp3(source: File, destination: File, isCancelled: () -> Boolean, onProgress: (Float) -> Unit) {
        val info = WavFile.readInfo(source)
        val format = AudioFormat(
            container = AudioContainer.MP3,
            codec = AudioCodec.MP3,
            sampleRate = info.sampleRate,
            channels = info.channels,
            bitDepth = 16,
            // Ovoz uchun 64 kbit/s yetarli va sifatli: musiqadan farqli,
            // baland bitreyt faylni faqat kattalashtiradi, quloq farqni
            // ajratmaydi. Uzoq kitobda bu fayl hajmini sezilarli kamaytiradi.
            bitrate = SPEECH_BITRATE_BPS,
        )
        Mp3Encoder(destination, format).use { encoder ->
            WavPcmReader(source).use { reader ->
                val buffer = IntArray(CHUNK_FRAMES * reader.format.channels)
                val total = reader.info.frames.coerceAtLeast(1L)
                var done = 0L
                while (true) {
                    if (isCancelled()) throw Cancelled()
                    val frames = reader.read(buffer, CHUNK_FRAMES)
                    if (frames <= 0) break
                    encoder.write(buffer, frames)
                    done += frames
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            encoder.finish()
        }
    }

    private const val CHUNK_FRAMES = 65_536
    private const val SPEECH_BITRATE_BPS = 64_000
}
