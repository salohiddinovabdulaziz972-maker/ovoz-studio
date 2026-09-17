package uz.ovozstudio.app.ui.book

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.RecordingStore
import uz.ovozstudio.app.media.book.BookBuildError
import uz.ovozstudio.app.media.book.BookBuildOutcome
import uz.ovozstudio.app.media.book.BookBuilder
import uz.ovozstudio.app.media.book.BookPlanner
import uz.ovozstudio.app.media.doc.Chapter
import uz.ovozstudio.app.media.doc.DocumentFormat
import uz.ovozstudio.app.media.doc.DocumentFormatException
import uz.ovozstudio.app.media.doc.DocumentLoader
import uz.ovozstudio.app.media.doc.DocumentTextMissingException
import uz.ovozstudio.app.media.doc.DocumentTooLargeException
import uz.ovozstudio.app.media.voice.DeviceTtsEngine
import uz.ovozstudio.app.media.voice.ScriptDetector
import uz.ovozstudio.app.media.voice.SpeechRequest
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.util.SpeedText
import java.io.File

/**
 * Audio-kitob ekranining xatolari.
 *
 * Naqsh ilova bo'ylab bir xil: ViewModel matn emas, KOD qaytaradi —
 * qaysi tilda ko'rsatishni ekran hal qiladi.
 */
enum class BookUiError {
    /** Fayl ochilgan, lekin unda o'qiladigan matn yo'q (skaner qilingan PDF). */
    NO_TEXT,

    /** Fayl formati aniqlanmadi yoki qo'llab-quvvatlanmaydi. */
    UNSUPPORTED_FORMAT,

    /** Hujjat o'qish chegarasidan katta. */
    TOO_LARGE,

    /** Fayl buzuq: ichidan kerakli qism topilmadi. */
    BROKEN_DOCUMENT,

    /** Hujjatdan birorta ham bob chiqmadi. */
    EMPTY_DOCUMENT,

    /** Bu kitob uchun mos ovoz topilmadi. */
    VOICE_MISSING,

    /** Sintezator matnni ovozga aylantira olmadi. */
    SPEAK_FAILED,

    /** Faylni yozib bo'lmadi: joy yetmadi. */
    WRITE_FAILED,

    /** Foydalanuvchi to'xtatdi — nosozlik emas, lekin xabar ko'rinadi. */
    CANCELLED,
}

/**
 * Audio-kitob ekranining holati.
 *
 * Tezlik va balandlik — **satrlar**, sonlar emas: qiymat maydonga qo'lda
 * kiritiladi va foydalanuvchi yozayotganda «1.» kabi tugallanmagan matn ham
 * vaqtincha yashashi kerak (ilova bo'ylab yagona qoida).
 */
data class BookUiState(
    val documentName: String = "",
    val formatName: String = "",
    /** Boblar soni va hajmi — foydalanuvchi kitobni yasashdan oldin ko'radi. */
    val chapterCount: Int = 0,
    val charCount: Int = 0,
    /** Bob sarlavhalari (qisqartirilgan ro'yxat) — nima o'qilishini oldindan bilish uchun. */
    val titles: List<String> = emptyList(),
    val ready: Boolean = false,
    /** Aniqlangan til-teg. `null` — mos ovoz yo'q. */
    val languageTag: String? = null,
    val rate: String = "1",
    val semitones: String = "0",
    val busy: Boolean = false,
    val chapterNumber: Int = 0,
    val chapters: Int = 0,
    val done: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    /** Tayyor bob fayllarining nomlari. */
    val output: List<String> = emptyList(),
    val durationMs: Long = 0L,
    val error: BookUiError? = null,
) {
    val rateValue: Float get() = SpeedText.parseSpeed(rate).toFloat()

    /** Semitonlar → sintezator kutadigan balandlik koeffitsienti. */
    val pitchValue: Float
        get() = SpeedText.pitchRatio(SpeedText.parseSemitones(semitones)).toFloat()

    /** Hujjat yaroqli o'qildimi — tugmalar shunga qarab yoqiladi. */
    val hasDocument: Boolean get() = documentName.isNotEmpty() && chapterCount > 0

    companion object {
        /** Ekranda ko'rsatiladigan bob sarlavhalari soni. */
        const val PREVIEW_TITLES = 5
    }
}

/**
 * Hujjatni audio-kitobga aylantiruvchi ekran mantiqi.
 *
 * Ish uch qismga bo'linadi va ular ataylab ajratilgan:
 *  1. **Import** — tizim tanlagichidan kelgan fayl ilova papkasiga
 *     nusxalanadi. Nusxa shart: `content://` havolasi faqat shu seansda
 *     yashaydi, kitob yasash esa undan keyin ham davom etadi.
 *  2. **O'qish** — hujjat boblarga bo'linadi.
 *  3. **Yig'ish** — har bir bo'lak ovozga aylantiriladi va boblar bitta
 *     faylga qo'shiladi ([BookBuilder]).
 *
 * Og'ir qismlar fon oqimida bajariladi. Sintezatorga tegadigan chaqiruvlar
 * esa asosiy oqimdan ketadi — Android'ning TTS xizmati shuni talab qiladi.
 */
class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val engine: VoiceEngine = DeviceTtsEngine(application)

    private val _state = MutableStateFlow(BookUiState())
    val state: StateFlow<BookUiState> = _state.asStateFlow()

    /** O'qilgan hujjat — qayta yig'ish uchun saqlanadi. */
    private var chapters: List<Chapter> = emptyList()

    /** Joriy yig'ish. Ishlamayotgan bo'lsa `null`. */
    private var builder: BookBuilder? = null

    init {
        engine.prepare { failure ->
            _state.update { it.copy(ready = failure == null) }
        }
    }

    /** Tizim tanlagichidan kelgan hujjatni ochadi. */
    fun open(uri: Uri) {
        if (_state.value.busy) return
        val context = getApplication<Application>()
        _state.update { it.copy(busy = true, error = null, output = emptyList()) }

        viewModelScope.launch {
            val imported = withContext(Dispatchers.IO) {
                try {
                    Imported.of(context, uri, store)
                } catch (error: Exception) {
                    Log.w(TAG, "Hujjat ochilmadi", error)
                    error
                }
            }

            if (imported is Imported) applyDocument(imported) else showImportFailure(imported)
        }
    }

    fun setRate(value: String) {
        _state.update { it.copy(rate = SpeedText.sanitizeSpeed(value)) }
    }

    fun setSemitones(value: String) {
        _state.update { it.copy(semitones = SpeedText.sanitizeSemitones(value)) }
    }

    fun nudgeRate(delta: Double) {
        _state.update { it.copy(rate = SpeedText.nudgeSpeed(it.rate, delta)) }
    }

    fun nudgeSemitones(delta: Double) {
        _state.update { it.copy(semitones = SpeedText.nudgeSemitones(it.semitones, delta)) }
    }

    /** Kitobni yig'ishni boshlaydi. */
    fun build() {
        val current = _state.value
        if (current.busy) return
        if (chapters.isEmpty()) {
            _state.update { it.copy(error = BookUiError.EMPTY_DOCUMENT) }
            return
        }
        // Dvigatel tayyor bo'lmasa hech narsa boshlanmaydi: jim qolgan
        // tugma nosozlikka o'xshaydi, sabab esa ayon bo'lishi kerak.
        if (!engine.isReady()) {
            _state.update { it.copy(error = BookUiError.VOICE_MISSING) }
            return
        }

        val plan = BookPlanner.plan(chapters)
        // Nom kengaytmasiz olinadi: fayl ichida «kitob.txt - 01 - ...»
        // ko'rinishi foydalanuvchini chalg'itardi.
        val title = current.documentName.substringBeforeLast('.')
        val voice = SpeechRequest(
            text = "",
            rate = current.rateValue,
            pitch = current.pitchValue,
            languageTag = current.languageTag,
        )
        // Vaqtinchalik bo'laklar keshda yashaydi: ular oraliq mahsulot va
        // kitob yig'ilgach o'chiriladi, foydalanuvchi papkasida esa iz
        // qolmasligi kerak.
        val work = File(getApplication<Application>().cacheDir, "kitob").apply { mkdirs() }

        val active = BookBuilder(engine = engine, workDir = work, voice = voice)
        builder = active
        _state.update {
            it.copy(busy = true, error = null, output = emptyList(), done = 0, percent = 0)
        }

        viewModelScope.launch {
            // Yig'ish fon oqimida: sintezator javobini kutish asosiy oqimda
            // bajarilsa, ekran butun kitob davomida qotib qolardi.
            val outcome = withContext(Dispatchers.IO) {
                active.build(plan, store.directory, title) { progress ->
                    _state.update {
                        it.copy(
                            chapterNumber = progress.chapterIndex + 1,
                            chapters = progress.chapters,
                            done = progress.done,
                            total = progress.total,
                            percent = progress.percent,
                        )
                    }
                }
            }
            builder = null
            applyOutcome(outcome)
        }
    }

    /**
     * Yig'ishni to'xtatadi.
     *
     * Sintezator ham to'xtatiladi: hozir ovoz yozilayotgan bo'lsa, kutish
     * javob kelguncha davom etardi va tugma hech narsa qilmagandek
     * ko'rinardi.
     */
    fun cancel() {
        builder?.cancel()
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /** Natija ko'rilganini tasdiqlaydi. Fayllar o'chirilmaydi. */
    fun consumeOutput() {
        _state.update { it.copy(output = emptyList(), durationMs = 0L) }
    }

    override fun onCleared() {
        super.onCleared()
        builder?.cancel()
        // Ovoz sintezatori — tizim xizmati: bo'shatilmasa qurilmada ochiq
        // ulanish qoladi va batareyani yeydi.
        engine.release()
    }

    private fun applyDocument(imported: Imported) {
        chapters = imported.chapters
        val script = ScriptDetector.detect(imported.text)
        val tag = if (imported.text.isBlank()) null else engine.resolveLanguage(script)

        _state.update {
            it.copy(
                busy = false,
                error = null,
                documentName = imported.name,
                formatName = imported.format.extension.uppercase(),
                chapterCount = imported.chapters.size,
                charCount = imported.chapters.sumOf(Chapter::charCount),
                titles = imported.chapters.take(BookUiState.PREVIEW_TITLES).map(Chapter::title),
                languageTag = tag,
                output = emptyList(),
            )
        }
    }

    /**
     * Hujjat o'qilmadi.
     *
     * Eski hujjat ham unutiladi: aks holda ekranda yangi xato bilan eski
     * kitobning nomi yonma-yon turib, foydalanuvchi nima ochilganini
     * tushunmasdi.
     */
    private fun showImportFailure(cause: Any) {
        chapters = emptyList()
        _state.update {
            it.copy(
                busy = false,
                documentName = "",
                formatName = "",
                chapterCount = 0,
                charCount = 0,
                titles = emptyList(),
                languageTag = null,
                output = emptyList(),
                error = cause.toUiError(),
            )
        }
    }

    private fun applyOutcome(outcome: BookBuildOutcome) {
        when (outcome) {
            is BookBuildOutcome.Done -> _state.update {
                it.copy(
                    busy = false,
                    error = null,
                    done = it.total,
                    percent = 100,
                    output = outcome.book.chapters.map { chapter -> chapter.audio.name },
                    durationMs = outcome.book.durationMs,
                )
            }

            is BookBuildOutcome.Failed -> _state.update {
                it.copy(busy = false, output = emptyList(), error = outcome.error.toUi())
            }
        }
    }

    private companion object {
        const val TAG = "BookViewModel"
    }
}

/**
 * Import natijasi: nom, format va boblar birga.
 *
 * Ular birga qaytariladi, chunki ekranga yarim ma'lumot kerak emas:
 * «nom bor, boblar yo'q» holati foydalanuvchi uchun yolg'on ko'rinish.
 */
private class Imported(
    val name: String,
    val format: DocumentFormat,
    val text: String,
    val chapters: List<Chapter>,
) {
    companion object {
        /**
         * Faylni ilova papkasiga nusxalab, boblarga bo'ladi.
         *
         * Faqat fon oqimidan chaqiriladi: ichida fayl o'qish va arxiv
         * ochish bor.
         */
        fun of(context: Context, uri: Uri, store: RecordingStore): Imported {
            val name = displayName(context, uri)
            // Nusxa shart: `content://` havolasi faqat shu seansda yashaydi,
            // kitob yasash esa undan keyin ham davom etadi.
            val target = store.newSourceFile(name.substringAfterLast('.', ""))

            val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (!copied) throw DocumentFormatException("Fayl ochilmadi")

            // Kengaytma yolg'on bo'lishi mumkin (Telegram'dan kelgan fayllar
            // ko'pincha `file.bin`), shuning uchun zaxira yo'l — imzo.
            val format = DocumentLoader.formatOf(target)
                ?: DocumentLoader.zipKindOf(target)
                ?: throw DocumentFormatException("Format aniqlanmadi")

            val loaded = DocumentLoader.load(
                file = target,
                format = format,
                fallbackTitle = context.getString(R.string.book_chapter_fallback),
                prefaceTitle = context.getString(R.string.book_preface_title),
            )
            if (loaded.chapters.isEmpty() || loaded.text.isBlank()) {
                throw DocumentTextMissingException("Hujjatda o'qiladigan matn yo'q")
            }
            return Imported(name, format, loaded.text, loaded.chapters)
        }

        /** Tanlagich bergan ko'rinadigan nom. Topilmasa — zaxira nom. */
        private fun displayName(context: Context, uri: Uri): String {
            val fallback = uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "hujjat" }
            return try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index < 0 || !cursor.moveToFirst()) fallback
                    else cursor.getString(index)?.takeIf { it.isNotBlank() } ?: fallback
                } ?: fallback
            } catch (error: Exception) {
                Log.w("BookViewModel", "Fayl nomini o'qib bo'lmadi", error)
                fallback
            }
        }
    }
}

/** Xatoni ekran kodiga aylantiradi. */
private fun Any?.toUiError(): BookUiError = when (this) {
    is DocumentTooLargeException -> BookUiError.TOO_LARGE
    // Matnsiz hujjat alohida ajratiladi: bu «formatni almashtiring» emas,
    // «kitob rasm bo'lib skaner qilingan» — yechim boshqa.
    is DocumentTextMissingException -> BookUiError.NO_TEXT
    // Aniqlanmagan format ham, buzuq arxiv ham bir xil ko'rinadi:
    // foydalanuvchi uchun ikkalasi ham «bu fayl kitobga yaramaydi».
    is DocumentFormatException -> BookUiError.UNSUPPORTED_FORMAT
    else -> BookUiError.BROKEN_DOCUMENT
}

private fun BookBuildError.toUi(): BookUiError = when (this) {
    BookBuildError.NOT_READY -> BookUiError.SPEAK_FAILED
    BookBuildError.EMPTY_DOCUMENT -> BookUiError.EMPTY_DOCUMENT
    BookBuildError.SPEAK_FAILED -> BookUiError.SPEAK_FAILED
    BookBuildError.ASSEMBLY_FAILED, BookBuildError.WRITE_FAILED -> BookUiError.WRITE_FAILED
    BookBuildError.CANCELLED -> BookUiError.CANCELLED
}
