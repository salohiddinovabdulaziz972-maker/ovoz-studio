package uz.ovozstudio.app.ui.book

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.R
import uz.ovozstudio.app.media.AudioPlayer
import uz.ovozstudio.app.media.RecordingStore
import uz.ovozstudio.app.media.book.BookBuildError
import uz.ovozstudio.app.media.book.BookBuildOutcome
import uz.ovozstudio.app.media.book.BookBuilder
import uz.ovozstudio.app.media.book.BookPlaybackStore
import uz.ovozstudio.app.media.book.BookPlanner
import uz.ovozstudio.app.media.book.BookPlaylist
import uz.ovozstudio.app.media.book.BuiltBook
import uz.ovozstudio.app.media.book.PlaylistChapter
import uz.ovozstudio.app.media.book.SleepTimer
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

    /**
     * Yasalgan bob fayli topilmadi.
     *
     * Faqat qurilmaning o'zi tozalaganda bo'ladi (papka qo'lda o'chirildi
     * yoki tizim joy bo'shatdi) — kitob qaytadan yasalishi kerak.
     */
    FILE_MISSING,
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

    // --- tinglash ---
    /** Kitob yasalgan va tinglash mumkinmi. */
    val playable: Boolean = false,
    val playing: Boolean = false,
    val chapterIndex: Int = 0,
    /** Joriy bob ichidagi joy. */
    val chapterPositionMs: Long = 0L,
    val chapterDurationMs: Long = 0L,
    /** Kitobning umumiy vaqti va qolgani. */
    val bookDurationMs: Long = 0L,
    val bookRemainingMs: Long = 0L,
    /** Joriy bo'lak (belgi) sarlavhasi; belgi bo'lmasa — bo'sh. */
    val section: String = "",
    /** Uxlash taymeri: qolgan soniya va «bob oxirigacha» rejimi. */
    val timerActive: Boolean = false,
    val timerRemainingSec: Long = 0L,
    val timerAtChapterEnd: Boolean = false,
    /** Qoldirilgan joy: bob raqami (0 dan) va vaqt. */
    val resumeChapter: Int = 0,
    val resumePositionMs: Long = 0L,
    /** Oxirgi bob ham o'qib bo'lindi — ekran bir marta e'lon qiladi. */
    val bookFinished: Boolean = false,
) {
    val rateValue: Float get() = SpeedText.parseSpeed(rate).toFloat()

    /** Semitonlar → sintezator kutadigan balandlik koeffitsienti. */
    val pitchValue: Float
        get() = SpeedText.pitchRatio(SpeedText.parseSemitones(semitones)).toFloat()

    /** Hujjat yaroqli o'qildimi — tugmalar shunga qarab yoqiladi. */
    val hasDocument: Boolean get() = documentName.isNotEmpty() && chapterCount > 0

    /** Qoldirilgan joy boshidan uzoqda — «davom etish» taklif qilinadi. */
    val hasResume: Boolean get() = resumeChapter > 0 || resumePositionMs > RESUME_MIN_MS

    companion object {
        /** Ekranda ko'rsatiladigan bob sarlavhalari soni. */
        const val PREVIEW_TITLES = 5

        /**
         * «Davom etish» taklif qilinadigan eng kichik joy.
         *
         * Bir necha soniyalik tasodifiy to'xtash (qo'ng'iroq, xato
         * bosish) uchun «davom etish» tugmasini ko'rsatish ortiqcha.
         */
        const val RESUME_MIN_MS = 20_000L

        /** Orqaga/oldinga sakrash qadami. */
        const val SKIP_MS = 15_000L

        /** Uxlash taymeri uchun tayyor daqiqalar. */
        val TIMER_MINUTES = listOf(15, 30, 60)
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

    // --- tinglash ---
    // Pleyer asosiy oqimda yaratiladi: `MediaPlayer` shu oqimga bog'lanadi
    // va boshqa oqimdan boshqarilsa, chaqiruvlar navbatsiz bajarilardi.
    private val player = AudioPlayer()
    private val sleep = SleepTimer()

    /** Qoldirilgan joy alohida faylda: jarayon o'lsa ham qoladi. */
    private val progress = BookPlaybackStore(File(application.filesDir, PROGRESS_FILE))

    private var playlist: BookPlaylist? = null

    /** Kitob nomi — qoldirilgan joy shu nom bilan yoziladi. */
    private var albumTitle: String = ""

    private var ticker: Job? = null

    /** Oxirgi tanlangan taymer vaqti (soniya) — «bob oxirigacha» uchun. */
    private var timerTotalSeconds: Long = 0

    init {
        engine.prepare { failure ->
            _state.update { it.copy(ready = failure == null) }
        }
        player.onFinished = { onChapterFinished() }
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
        val title = albumTitleOf(current.documentName)
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

    // --- tinglash ---

    /**
     * Joriy bobni tanlab, ko'rsatilgan joydan qo'yadi.
     *
     * Kitob boblar bo'ylab ketma-ket o'qiladi: bob tugaganda keyingisi
     * o'zi boshlanadi, oxirgi bobdan keyin to'xtaydi.
     */
    fun playChapter(index: Int, positionMs: Long = 0L) {
        val list = playlist ?: return
        val chapter = list.select(index) ?: return
        if (!chapter.file.exists()) {
            pause()
            _state.update { it.copy(error = BookUiError.FILE_MISSING) }
            return
        }
        if (_state.value.busy) return

        player.play(chapter.file, positionMs)
        saveProgress(index, positionMs)
        _state.update {
            it.copy(
                playing = true,
                error = null,
                bookFinished = false,
                chapterIndex = index,
                chapterDurationMs = chapter.durationMs,
            )
        }
        startTicker()
        publishPosition()
    }

    /** Qoldirilgan joydan davom ettiradi. */
    fun resume() {
        playChapter(_state.value.resumeChapter, _state.value.resumePositionMs)
    }

    /** Kitobni birinchi bobdan boshlaydi. */
    fun restart() {
        playChapter(0, 0L)
        // «Boshidan» bosilgach qoldirilgan joy yo'q: aks holda tugma
        // yana «Davom etish» bo'lib qolardi.
        _state.update { it.copy(resumeChapter = 0, resumePositionMs = 0L) }
    }

    /** Ijroni to'xtatib turadi (joy eslab qolinadi). */
    fun pause() {
        ticker?.cancel()
        ticker = null
        player.stop()
        val position = _state.value.chapterPositionMs
        saveProgress(_state.value.chapterIndex, position)
        // Qoldirilgan joy holatda ham yangilanadi: tugma yorlig'i («Davom
        // etish» yoki «Tinglashni boshlash») haqiqiy holatga qarab tanlanadi.
        _state.update {
            it.copy(
                playing = false,
                resumeChapter = it.chapterIndex,
                resumePositionMs = position,
            )
        }
    }

    /** Ijro/tanaffus tugmasi. */
    fun togglePlay() {
        if (_state.value.playing) pause() else playChapter(_state.value.chapterIndex, _state.value.chapterPositionMs)
    }

    fun nextChapter() {
        val list = playlist ?: return
        if (list.advance() == null) return
        playChapter(list.currentIndex, 0L)
    }

    fun previousChapter() {
        val list = playlist ?: return
        if (list.rewind() == null) return
        playChapter(list.currentIndex, 0L)
    }

    /**
     * Joriy bob ichida orqaga/oldinga suradi.
     *
     * Bob chegarasidan chiqmaydi: keyingi bobga o'tish alohida tugma —
     * tasodifiy surish kitobni boshqa bobga tashlab qo'ymasligi kerak.
     */
    fun skip(deltaMs: Long) {
        val list = playlist ?: return
        if (!_state.value.playing) return
        val duration = list.current?.durationMs ?: return
        val target = (_state.value.chapterPositionMs + deltaMs).coerceIn(0L, (duration - 1).coerceAtLeast(0L))
        player.seekTo(target)
        publishPosition(target)
    }

    /**
     * Keyingi yoki oldingi bo'limga (belgi bo'ylab) sakraydi.
     *
     * Keyingi belgi bo'lmasa hech narsa qilinmaydi: bob oxirida turgan
     * tinglovchi tugmani bosganda kitob boshidan boshlanib ketishi —
     * yo'qotishdan ham yomonroq. Orqaga esa belgi bo'lmasa boshidan.
     */
    fun jumpToSection(next: Boolean) {
        val list = playlist ?: return
        if (!_state.value.playing) return
        val position = _state.value.chapterPositionMs
        val target = if (next) {
            list.nextMarker(position)?.startMs ?: return
        } else {
            list.previousMarker(position)?.startMs ?: 0L
        }
        player.seekTo(target)
        publishPosition(target)
    }

    /**
     * Uxlash taymerini ishga tushiradi.
     *
     * Joriy rejim («bob oxirigacha») saqlanadi: foydalanuvchi rejimni
     * tanlab, keyin vaqtni o'zgartirsa, tanlovi yo'qolmasligi kerak.
     */
    fun startTimer(minutes: Int) {
        timerTotalSeconds = minutes * 60L
        sleep.start(timerTotalSeconds, sleep.stopAtChapterEnd)
        publishTimer()
    }

    /** «Bob oxirigacha» rejimini almashtiradi (ishlab turgan taymer uchun). */
    fun toggleTimerAtChapterEnd() {
        if (!sleep.isRunning) return
        // Vaqt qaytadan boshlanmaydi: qolgan soniya saqlanadi.
        sleep.start(sleep.remainingSeconds, !sleep.stopAtChapterEnd)
        publishTimer()
    }

    fun cancelTimer() {
        sleep.cancel()
        timerTotalSeconds = 0
        publishTimer()
    }

    /** Kitob tugagani e'lon qilinganini tasdiqlaydi. */
    fun consumeFinished() {
        _state.update { it.copy(bookFinished = false) }
    }

    /**
     * Har chorak soniyada holatni yangilaydi.
     *
     * Nega taymer ham shu yerda: uxlash taymeri faqat **ijro paytida**
     * sanaydi. Alohida soat qo'yilsa, tanaffusda ham vaqt o'tib ketardi va
     * foydalanuvchi kutganidan erta to'xtardi.
     */
    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var elapsedMs = 0L
            var sinceSaveMs = 0L
            while (true) {
                delay(TICK_MS)
                elapsedMs += TICK_MS
                sinceSaveMs += TICK_MS
                if (elapsedMs >= 1000) {
                    val seconds = elapsedMs / 1000
                    elapsedMs -= seconds * 1000
                    sleep.elapse(seconds)
                }
                publishPosition()
                if (sleep.shouldStop(atChapterEnd = false)) {
                    // Vaqt tugadi va «bob oxirigacha» rejimi o'chiq: darhol
                    // to'xtaydi. Taymer o'chiriladi — aks holda davom
                    // ettirilganda bir zumda yana to'xtardi.
                    pause()
                    cancelTimer()
                    return@launch
                }
                if (sinceSaveMs >= SAVE_EVERY_MS) {
                    sinceSaveMs = 0L
                    saveProgress(_state.value.chapterIndex, _state.value.chapterPositionMs)
                }
            }
        }
    }

    /** Bob tugaganda: keyingisiga o'tadi yoki to'xtaydi. */
    private fun onChapterFinished() {
        val list = playlist ?: return
        if (sleep.shouldStop(atChapterEnd = true)) {
            pause()
            cancelTimer()
            return
        }
        if (list.advance() == null) {
            // Kitob tugadi: qoldirilgan joy endi kerak emas va pleyer
            // boshiga qaytadi. Aks holda «Tinglash» tugmasi oxirgi bobning
            // oxirini qo'yib, kitob darhol yana tugagandek ko'rinardi.
            pause()
            saveProgress(0, 0L)
            list.select(0)
            _state.update {
                it.copy(
                    bookFinished = true,
                    chapterIndex = 0,
                    chapterPositionMs = 0L,
                    resumeChapter = 0,
                    resumePositionMs = 0L,
                    bookRemainingMs = list.remainingMs(0),
                    section = "",
                )
            }
            return
        }
        playChapter(list.currentIndex, 0L)
    }

    private fun publishPosition(position: Long = player.positionMs()) {
        val list = playlist ?: return
        val chapter = list.current ?: return
        val clamped = position.coerceIn(0L, chapter.durationMs.coerceAtLeast(0L))
        _state.update {
            it.copy(
                chapterPositionMs = clamped,
                chapterDurationMs = chapter.durationMs,
                bookDurationMs = list.totalDurationMs,
                bookRemainingMs = list.remainingMs(clamped),
                section = list.markerAt(clamped)?.title.orEmpty(),
            )
        }
    }

    private fun publishTimer() {
        _state.update {
            it.copy(
                timerActive = sleep.isRunning,
                timerRemainingSec = sleep.remainingSeconds,
                timerAtChapterEnd = sleep.stopAtChapterEnd,
            )
        }
    }

    private fun saveProgress(chapterIndex: Int, positionMs: Long) {
        val title = albumTitle
        if (title.isBlank()) return
        progress.save(title, chapterIndex, positionMs, System.currentTimeMillis())
    }

    /** Yasalgan kitobni tinglash uchun tayyorlaydi. */
    private fun openForPlayback(book: BuiltBook, album: String) {
        // Qayta yig'ish eski ijroni davom ettirmaydi: fayllar joyida
        // almashgan bo'lishi mumkin, eski taymer esa endi ma'nosiz.
        ticker?.cancel()
        ticker = null
        player.stop()
        sleep.cancel()
        timerTotalSeconds = 0

        playlist = BookPlaylist(
            book.chapters.map { chapter ->
                PlaylistChapter(
                    title = chapter.title,
                    file = chapter.audio,
                    durationMs = chapter.durationMs,
                    markers = chapter.markers,
                )
            }
        )
        albumTitle = album
        val saved = progress.load(album)
        val list = playlist
        val chapters = list?.size ?: 0
        val chapterIndex = (saved?.chapterIndex ?: 0).coerceIn(0, (chapters - 1).coerceAtLeast(0))
        // Ro'yxat qoldirilgan bobga suriladi: aks holda ekranda «3-bob»
        // yozilib, uzunlik va qolgan vaqt birinchi bobdan hisoblanardi.
        list?.select(chapterIndex)
        val position = saved?.positionMs ?: 0L
        _state.update {
            it.copy(
                playable = chapters > 0,
                playing = false,
                chapterIndex = chapterIndex,
                chapterPositionMs = position,
                chapterDurationMs = list?.current?.durationMs ?: 0L,
                bookDurationMs = list?.totalDurationMs ?: 0L,
                bookRemainingMs = list?.remainingMs(position) ?: 0L,
                resumeChapter = chapterIndex,
                resumePositionMs = position,
                section = "",
                timerActive = false,
                timerRemainingSec = 0L,
                timerAtChapterEnd = false,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        builder?.cancel()
        saveProgress(_state.value.chapterIndex, _state.value.chapterPositionMs)
        player.release()
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
            is BookBuildOutcome.Done -> {
                openForPlayback(outcome.book, albumTitleOf(_state.value.documentName))
                _state.update {
                    it.copy(
                        busy = false,
                        error = null,
                        done = it.total,
                        percent = 100,
                        output = outcome.book.chapters.map { chapter -> chapter.audio.name },
                        durationMs = outcome.book.durationMs,
                    )
                }
            }

            is BookBuildOutcome.Failed -> _state.update {
                it.copy(busy = false, output = emptyList(), error = outcome.error.toUi())
            }
        }
    }

    /**
     * Kitob nomi — kengaytmasiz.
     *
     * Bob fayllarining nomlari ham shu nomdan yasaladi, shuning uchun
     * qoldirilgan joy ham aynan shu kalit bilan yoziladi.
     */
    private fun albumTitleOf(documentName: String): String = documentName.substringBeforeLast('.')

    private companion object {
        const val TAG = "BookViewModel"

        /** Holat yangilanishi orasidagi qadam: soniyasiga to'rt marta. */
        const val TICK_MS = 250L

        /**
         * Qoldirilgan joyni saqlash qadami.
         *
         * Har chorak soniyada yozish ortiqcha: fayl har o'zgarishda to'liq
         * qayta yoziladi va flesh xotirani bekorga yeydi. O'ttiz soniya —
         * kutilmagan yopilishda yo'qoladigan eng ko'p joy.
         */
        const val SAVE_EVERY_MS = 30_000L

        /** Qoldirilgan joy fayli — ilovaning ichki papkasida. */
        const val PROGRESS_FILE = "kitob-jarayon.properties"
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
