package uz.ovozstudio.app.media.book

import uz.ovozstudio.app.media.format.AudioEncoder
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.Mp3Encoder
import uz.ovozstudio.app.media.voice.SpeechRequest
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.media.voice.VoiceError
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Kitob yig'ishdagi xatoliklar.
 *
 * Naqsh ilova bo'ylab bir xil: qatlam matn emas, **kod** qaytaradi —
 * qaysi tilda ko'rsatishni UI hal qiladi.
 */
enum class BookBuildError {
    /** Sintezator tayyor emas yoki qurilmada umuman yo'q. */
    NOT_READY,

    /** Hujjatdan birorta ham o'qiladigan matn chiqmadi. */
    EMPTY_DOCUMENT,

    /** Sintezator matnni faylga yozib bera olmadi. */
    SPEAK_FAILED,

    /** Bo'laklarni qo'shib yoki kodlab bo'lmadi (masalan, chastotalar har xil). */
    ASSEMBLY_FAILED,

    /** Natija faylini yozib bo'lmadi: joy yetmadi yoki papka yopiq. */
    WRITE_FAILED,

    /** Foydalanuvchi to'xtatdi. */
    CANCELLED,
}

/**
 * Yig'ilish jarayoni haqidagi xabar.
 *
 * [done] va [total] — **bo'laklar** (jumlalar) bo'yicha, boblar emas: uzun
 * kitobda bob bir necha daqiqa davom etadi va bob bo'yicha hisoblangan
 * ko'rsatkich qimirlamay turgandek ko'rinardi.
 */
data class BookProgress(
    val chapterIndex: Int,
    val chapterTitle: String,
    val chapters: Int,
    val done: Int,
    val total: Int,
) {
    val percent: Int get() = if (total <= 0) 0 else (done * 100 / total).coerceIn(0, 100)
}

/** Tayyor bob: audio fayl, unga tegishli belgilar va (iloji bo'lsa) CUE varaq. */
data class BuiltChapter(
    val index: Int,
    val title: String,
    val audio: File,
    /** Belgilar varaqasi yozilmagan bo'lsa `null` — audio baribir tayyor. */
    val cue: File?,
    val durationMs: Long,
    val markers: List<BookMarker>,
)

/** Yig'ilgan kitob. */
data class BuiltBook(
    val chapters: List<BuiltChapter>,
    val durationMs: Long,
    /** O'qilgan bo'laklar soni — xato bo'lmagani shu bilan tasdiqlanadi. */
    val utteranceCount: Int,
)

/**
 * Kitobni yig'ish natijasi.
 *
 * Xato yiqilish emas, qaytariladigan qiymat: bitta bobning buzilishi butun
 * kitobni yo'q qilmasligi kerak — foydalanuvchi qolgan boblar bilan
 * ishlashda davom etadi.
 */
sealed interface BookBuildOutcome {
    data class Done(val book: BuiltBook) : BookBuildOutcome

    data class Failed(val error: BookBuildError, val chapterIndex: Int = -1) : BookBuildOutcome
}

/**
 * Kitobni yig'uvchi: reja → bo'laklar → boblar → fayllar.
 *
 * Nega alohida sinf, ViewModel emas: yig'ish mantiqi qurilmaga bog'liq emas
 * va uni faqat haqiqiy sintezator bilan sinash mumkin bo'lsa, sinov
 * sekin va ishonchsiz bo'lardi. Bu yerda dvigatel — interfeys, shuning uchun
 * butun oqim (tartib, tozalash, to'xtatish) oddiy JVM sinovida
 * tekshiriladi, qurilma esa faqat tovush sifatiga javob beradi.
 *
 * Ish **ketma-ket**: sintezator bitta, parallel chaqiruv uni siqib
 * qo'yardi. Shu sababli [synthesizeToFile] javobi kelguncha shu oqim
 * kutadi, boshqa oqim esa bloklanmaydi — Android'ning sintezatori javobni
 * asosiy oqimda beradi.
 */
class BookBuilder(
    private val engine: VoiceEngine,
    private val workDir: File,
    /**
     * Ovoz sozlamalari namunasi: `text` har bir bo'lak uchun almashtiriladi,
     * qolgan maydonlar (tezlik, ohang, til) o'zgarmaydi.
     */
    private val voice: SpeechRequest = SpeechRequest(text = ""),
    /**
     * Bitta bo'lak eng ko'p shu qadar kutuladi. Dvigatel javob bermay
     * qolsa, kitob yig'ish abadiy osilib qolmasligi kerak: uch ming
     * bo'lakli kitobda bitta jim qolgan javob butun ishni to'xtatardi.
     */
    private val utteranceTimeoutMs: Long = DEFAULT_UTTERANCE_TIMEOUT_MS,
    /** Kodlovchi ochilishi — sinovda almashtiriladi. */
    private val openEncoder: (File, AudioFormat) -> AudioEncoder = { file, format ->
        Mp3Encoder(file, format)
    },
) {

    @Volatile
    private var cancelled = false

    /**
     * Yig'ishni to'xtatadi.
     *
     * Faqat bayroq qo'yish yetmaydi: bo'lak hozir sintez qilinayotgan
     * bo'lsa, kutish javob kelguncha davom etardi. Shuning uchun dvigatel
     * ham to'xtatiladi — u kutayotgan javobni darhol qaytaradi.
     */
    fun cancel() {
        cancelled = true
        engine.stop()
    }

    /**
     * Rejani fayllarga aylantiradi.
     *
     * Har bir bob alohida fayl bo'ladi: butun kitobni bitta faylga yig'ish
     * mumkin emas — uni telefonda ochib bo'lmaydi va xato bo'lsa hammasi
     * yo'qoladi.
     *
     * @param outputDir tayyor boblar yoziladigan papka.
     * @param albumTitle kitob nomi: fayl nomi va CUE sarlavhasi shundan.
     */
    fun build(
        plan: BookPlan,
        outputDir: File,
        albumTitle: String,
        target: AudioTarget = AudioTarget.MP3,
        gapMs: Int = WavJoiner.DEFAULT_GAP_MS,
        onProgress: (BookProgress) -> Unit = {},
    ): BookBuildOutcome {
        if (plan.chapters.isEmpty() || plan.utteranceCount == 0) {
            return BookBuildOutcome.Failed(BookBuildError.EMPTY_DOCUMENT)
        }
        if (!engine.isReady()) return BookBuildOutcome.Failed(BookBuildError.NOT_READY)

        workDir.mkdirs()
        outputDir.mkdirs()

        val created = ArrayList<File>()
        val built = ArrayList<BuiltChapter>()
        var done = 0
        val total = plan.utteranceCount
        var failure: BookBuildOutcome.Failed? = null

        try {
            chapters@ for (chapter in plan.chapters) {
                if (cancelled) {
                    failure = BookBuildOutcome.Failed(BookBuildError.CANCELLED, chapter.index)
                    break@chapters
                }

                val parts = ArrayList<File>()
                for ((n, utterance) in chapter.utterances.withIndex()) {
                    if (cancelled) {
                        failure = BookBuildOutcome.Failed(BookBuildError.CANCELLED, chapter.index)
                        break@chapters
                    }
                    val part = File(workDir, "bob-${chapter.index}-$n.wav")
                    created += part
                    val error = synthesize(utterance.text, part)
                    // To'xtatish sintez paytida kelgan bo'lsa, dvigatel xatoni
                    // «javob kelmadi» deb qaytaradi — sabab shu yerda aniq.
                    if (cancelled) {
                        failure = BookBuildOutcome.Failed(BookBuildError.CANCELLED, chapter.index)
                        break@chapters
                    }
                    if (error != null) {
                        failure = BookBuildOutcome.Failed(BookBuildError.SPEAK_FAILED, chapter.index)
                        break@chapters
                    }
                    parts += part
                    done++
                    onProgress(
                        BookProgress(chapter.index, chapter.title, plan.chapters.size, done, total)
                    )
                }

                val audio = File(outputDir, chapterFileName(albumTitle, chapter.index, chapter.title, target))
                val assembled = assemble(chapter, parts, audio, target, gapMs)
                if (assembled == null) {
                    failure = BookBuildOutcome.Failed(BookBuildError.ASSEMBLY_FAILED, chapter.index)
                    break@chapters
                }

                built += BuiltChapter(
                    index = chapter.index,
                    title = chapter.title,
                    audio = assembled.audio,
                    cue = writeCue(assembled.audio, albumTitle, assembled.markers),
                    durationMs = assembled.durationMs,
                    markers = assembled.markers,
                )
            }
        } catch (error: Exception) {
            // Yiqilish emas, xabar: chaqiruvchiga kod qaytadi.
            failure = BookBuildOutcome.Failed(BookBuildError.WRITE_FAILED)
        } finally {
            // Bo'laklar vaqtinchalik: ular bobga qo'shildi. Xato bo'lgan
            // taqdirda ham o'chiriladi — telefon xotirasida yuzlab fayl
            // qolib ketmasligi kerak.
            created.forEach { it.delete() }
        }

        if (failure != null) {
            // Yarim kitob qoldirilmaydi: tayyor boblar ham o'chiriladi.
            // Sabab — natija birdan yig'iladi; papkadagi yarim to'plam
            // foydalanuvchini chalg'itadi (u qaysi biri to'liq ekanini
            // bilmaydi), qayta yig'ish esa baribir hammasini qaytadan
            // yozadi.
            built.forEach {
                it.audio.delete()
                it.cue?.delete()
            }
            return failure
        }

        return BookBuildOutcome.Done(
            BuiltBook(
                chapters = built,
                durationMs = built.sumOf { it.durationMs },
                utteranceCount = done,
            )
        )
    }

    /**
     * Bitta bo'lakni faylga sintez qiladi va javobni kutadi.
     *
     * `null` — muvaffaqiyat. Kutish cho'zilib ketsa ham xato qaytadi:
     * qurilmada ovoz o'rnatilmagan bo'lsa, sintezator ba'zan javob
     * bermaydi, ilova esa buni kutib qotib qolmasligi kerak.
     */
    private fun synthesize(text: String, output: File): VoiceError? {
        val latch = CountDownLatch(1)
        var result: VoiceError? = null
        val callback: (VoiceError?) -> Unit = { error ->
            result = error
            latch.countDown()
        }
        try {
            engine.synthesizeToFile(voice.copy(text = text), output, callback)
        } catch (error: Exception) {
            output.delete()
            return VoiceError.SPEAK_FAILED
        }
        val finished = try {
            latch.await(utteranceTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (!finished) {
            // Dvigatel jim qoldi: uni bo'shatib qo'yamiz, aks holda keyingi
            // bo'lak ham "band" javobini olardi.
            engine.stop()
            output.delete()
            return VoiceError.SPEAK_FAILED
        }
        if (result != null) output.delete()
        return result
    }

    private fun assemble(
        chapter: BookChapterPlan,
        parts: List<File>,
        audio: File,
        target: AudioTarget,
        gapMs: Int,
    ): AssembledChapter? = try {
        ChapterAssembler.assemble(
            parts = parts,
            joinTarget = File(workDir, "bob-${chapter.index}.wav"),
            audioTarget = audio,
            target = target,
            plan = chapter,
            gapMs = gapMs,
            openEncoder = openEncoder,
        )
    } catch (error: Exception) {
        audio.delete()
        null
    }

    /**
     * Belgilar varaqasini yozadi. Yozilmasa — bu xato emas: audio tayyor,
     * CUE esa qurilmalarning bir qismigina o'qiydigan qo'shimcha.
     */
    private fun writeCue(audio: File, albumTitle: String, markers: List<BookMarker>): File? {
        if (markers.isEmpty()) return null
        val cue = File(audio.parentFile, audio.nameWithoutExtension + ".cue")
        return try {
            cue.writeText(BookMarkers.cueSheet(albumTitle, audio.name, markers))
            cue
        } catch (error: Exception) {
            null
        }
    }

    companion object {
        /** Bitta bo'lak uchun kutish chegarasi: 5 daqiqa. */
        const val DEFAULT_UTTERANCE_TIMEOUT_MS = 5 * 60 * 1000L

        /** Fayl nomidagi nomning eng katta uzunligi. */
        const val MAX_NAME_CHARS = 40

        /**
         * Bob faylining nomi: `Kitob - 01 - Bob nomi.mp3`.
         *
         * Nom ichida tartib raqami bor va u **ikki xonali**: fayl
         * menejerida boblar alifbo bo'yicha ham to'g'ri tartibda turadi.
         */
        fun chapterFileName(
            albumTitle: String,
            index: Int,
            title: String,
            target: AudioTarget = AudioTarget.MP3,
        ): String {
            val base = sanitize(albumTitle).ifBlank { "kitob" }
            val part = sanitize(title).ifBlank { "bob" }
            return "$base - ${"%02d".format(index + 1)} - $part.${target.container.extension}"
        }

        /**
         * Nomni fayl tizimi qabul qiladigan ko'rinishga keltiradi.
         *
         * Harflar (kirill ham), raqamlar, apostrof, chiziqcha va probel
         * saqlanadi — o'zbek kitoblari shunday nomlanadi va nom
         * o'qilishi kerak. Qolgani `_` ga aylanadi: yo'l belgisi nomga
         * tushsa fayl umuman yaratilmasdi yoki boshqa papkaga ketardi.
         */
        fun sanitize(name: String): String {
            val builder = StringBuilder()
            for (c in name.trim()) {
                when {
                    c.isLetterOrDigit() || c == '\'' || c == 'ʻ' || c == 'ʼ' || c == '-' ->
                        builder.append(c)

                    c == ' ' -> if (builder.isNotEmpty() && builder.last() != ' ') builder.append(' ')
                    else -> if (builder.isNotEmpty() && builder.last() != '_') builder.append('_')
                }
            }
            return builder.toString().trim(' ', '_').take(MAX_NAME_CHARS).trim(' ', '_')
        }
    }
}
