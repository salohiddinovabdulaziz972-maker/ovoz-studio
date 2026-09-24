package uz.ovozstudio.app.ui.reader

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.R
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore
import uz.ovozstudio.app.media.doc.Chapter
import uz.ovozstudio.app.media.doc.DocumentFormat
import uz.ovozstudio.app.media.doc.DocumentLoader
import uz.ovozstudio.app.media.doc.DocumentTextMissingException
import uz.ovozstudio.app.media.doc.DocumentTooLargeException
import uz.ovozstudio.app.media.doc.ReadingText
import uz.ovozstudio.app.media.doc.TextQuality
import uz.ovozstudio.app.media.pdf.PdfPageText
import uz.ovozstudio.app.media.pdf.PdfPageTools
import uz.ovozstudio.app.media.pdf.PdfPasswordException
import uz.ovozstudio.app.media.voice.DeviceTtsEngine
import uz.ovozstudio.app.media.voice.KeepAliveService
import uz.ovozstudio.app.media.voice.SpeechChunk
import uz.ovozstudio.app.media.voice.SpeechListener
import uz.ovozstudio.app.media.voice.SpeechRequest
import uz.ovozstudio.app.media.voice.TtsEngineInfo
import uz.ovozstudio.app.media.voice.VoiceChoice
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.media.voice.VoiceError
import uz.ovozstudio.app.media.voice.VoiceInfo
import uz.ovozstudio.app.media.voice.VoicePrefs
import java.io.File
import java.io.IOException

/** O'quvchi ekranining qaysi qismi ko'rinib turibdi. */
enum class ReaderPage { READING, CHAPTERS, SETTINGS }

/** Hujjat ochilmagan yoki o'qish to'xtagan sabab. Matnni ekran tanlaydi. */
enum class ReaderError {
    /** Format qo'llab-quvvatlanmaydi. */
    UNSUPPORTED,

    /** Fayl buzuq yoki o'qib bo'lmadi. */
    BROKEN,

    /** Fayl juda katta. */
    TOO_LARGE,

    /** Ichida o'qiladigan matn yo'q (masalan skaner qilingan PDF). */
    NO_TEXT,

    /** Matn bor, lekin o'qib bo'lmaydigan shaklda (shrift kodlashi noma'lum). */
    BROKEN_TEXT,

    /** PDF parol bilan himoyalangan. */
    PASSWORD,

    /** Ovoz sintezatori tayyor emas. */
    VOICE_MISSING,

    /** O'qish paytida sintezator xato berdi. */
    SPEAK_FAILED,
}

data class ReaderUiState(
    val documentName: String = "",
    val formatName: String = "",
    val chapterTitles: List<String> = emptyList(),
    val chapterIndex: Int = 0,
    /** Joriy bobning abzatslari. */
    val paragraphs: List<String> = emptyList(),
    val paragraphIndex: Int = 0,
    val speaking: Boolean = false,
    val page: ReaderPage = ReaderPage.READING,
    /** Hujjat ochilayotgan bo'lsa `true`. */
    val opening: Boolean = false,
    /** Uzun PDF ochilayotganda: nechanchi sahifa / nechta. */
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val engineReady: Boolean = false,
    val engines: List<TtsEngineInfo> = emptyList(),
    /** Tanlangan dvigatel paketi; `null` — tizimning standarti. */
    val selectedEngine: String? = null,
    val voices: List<VoiceInfo> = emptyList(),
    /** Tanlangan ovoz; `null` — til bo'yicha avtomatik. */
    val selectedVoiceId: String? = null,
    /** Dvigatelda o'zbekcha (yoki Sardor/Madina) ovoz bormi. */
    val hasUzbekVoice: Boolean = true,
    val rate: Float = 1.0f,
    val error: ReaderError? = null,
) {
    val isOpen: Boolean get() = chapterTitles.isNotEmpty()
    val chapterTitle: String get() = chapterTitles.getOrNull(chapterIndex).orEmpty()
}

/**
 * Hujjatni o'qish ekranining holati.
 *
 * Matn ikki yo'l bilan «o'qiladi», foydalanuvchi ikkalasini tanlaydi:
 *  - **ekran o'quvchi** (TalkBack va boshqalar): matn ekranda abzatslar
 *    bo'yicha turadi, tizim o'quvchisi uni o'zi o'qiydi;
 *  - **ilovaning ovozi**: tugma bosilganda qurilmaning ovoz sintezatori
 *    matnni abzatsma-abzats o'qiydi. Sintezator va ovoz tanlanadi —
 *    shu yerda Microsoft Sardor va Madina ham (agar ularni beradigan
 *    dvigatel o'rnatilgan bo'lsa).
 *
 * Hujjat bobga bo'linadi (PDF da bob — sahifa). Ovoz bobni abzatsma-abzats
 * o'qiydi va bob tugagach keyingisiga o'tadi.
 */
class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)
    private val prefs = VoicePrefs(application)

    /** Ochilgan hujjatning boblari. Matn holatga kirmaydi: katta bo'lishi mumkin. */
    private var chapters: List<Chapter> = emptyList()

    private var engine: VoiceEngine? = null

    private val _state = MutableStateFlow(ReaderUiState(rate = prefs.rate))
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    init {
        prepareEngine(prefs.enginePackage)

        // Ekran o'chganda ham jonli o'qish davom etishi uchun: qaysi yo'l bilan
        // to'xtagan/bo'lganidan qat'i nazar (pauza, hujjat tugashi, xato) —
        // `speaking` holatining o'zi kuzatiladi, shuning uchun bitta joy
        // hammasini qamrab oladi, har bir to'xtash nuqtasini alohida eslab
        // yurish shart emas.
        viewModelScope.launch {
            state.map { it.speaking }.distinctUntilChanged().collect { speaking ->
                val context = getApplication<Application>()
                if (speaking) {
                    KeepAliveService.start(context, state.value.documentName)
                } else {
                    KeepAliveService.stop(context)
                }
            }
        }
    }

    // --- hujjat ---

    /** Tanlangan hujjatni ochadi. */
    fun open(uri: Uri) {
        if (_state.value.opening) return
        viewModelScope.launch {
            stopSpeaking()
            _state.update {
                it.copy(opening = true, error = null, progressDone = 0, progressTotal = 0)
            }
            val context = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) {
                runCatching { load(context, uri) }
            }

            val loaded = outcome.getOrNull()
            if (loaded == null || loaded.isEmpty()) {
                val failure = outcome.exceptionOrNull()
                val error = errorOf(failure)
                ErrorLog.error("reader.open", "Hujjat ochilmadi: $error", failure)
                _state.update { it.copy(opening = false, error = error) }
                return@launch
            }

            chapters = loaded.chapters
            // Birinchi bob abzatslarga fon oqimida bo'linadi: sarlavhasiz katta
            // TXT butun hujjat bitta bob bo'ladi va uni asosiy oqimda bo'lish
            // ekranni sezilarli qotirardi.
            val first = withContext(Dispatchers.Default) { paragraphsOf(0) }
            _state.update {
                it.copy(
                    opening = false,
                    documentName = loaded.name,
                    formatName = loaded.formatName,
                    chapterTitles = loaded.chapters.map { chapter -> chapter.title },
                    chapterIndex = 0,
                    paragraphs = first,
                    paragraphIndex = 0,
                    page = ReaderPage.READING,
                    error = null,
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun setPage(page: ReaderPage) = _state.update { it.copy(page = page) }

    // --- harakat ---

    /** Bobga o'tadi. Ovoz yoqilgan bo'lsa, shu bobning boshidan o'qishda davom etadi. */
    fun goToChapter(index: Int) {
        if (index !in chapters.indices) return
        val wasSpeaking = _state.value.speaking
        stopSpeaking()
        _state.update {
            it.copy(
                chapterIndex = index,
                paragraphs = paragraphsOf(index),
                paragraphIndex = 0,
                page = ReaderPage.READING,
            )
        }
        if (wasSpeaking) speakCurrent()
    }

    fun nextChapter() = goToChapter(_state.value.chapterIndex + 1)

    fun previousChapter() = goToChapter(_state.value.chapterIndex - 1)

    /** Abzatsga o'tadi. Ovoz yoqilgan bo'lsa, shu abzatsdan o'qishda davom etadi. */
    fun goToParagraph(index: Int) {
        val current = _state.value
        if (index !in current.paragraphs.indices) return
        val wasSpeaking = current.speaking
        stopSpeaking()
        _state.update { it.copy(paragraphIndex = index) }
        if (wasSpeaking) speakCurrent()
    }

    fun nextParagraph() {
        val current = _state.value
        if (current.paragraphIndex + 1 < current.paragraphs.size) {
            goToParagraph(current.paragraphIndex + 1)
        } else if (current.chapterIndex + 1 < chapters.size) {
            goToChapter(current.chapterIndex + 1)
        }
    }

    fun previousParagraph() {
        val current = _state.value
        if (current.paragraphIndex > 0) {
            goToParagraph(current.paragraphIndex - 1)
        } else if (current.chapterIndex > 0) {
            // Oldingi bobning oxirgi abzatsiga.
            val previous = current.chapterIndex - 1
            val wasSpeaking = current.speaking
            stopSpeaking()
            val paragraphs = paragraphsOf(previous)
            _state.update {
                it.copy(
                    chapterIndex = previous,
                    paragraphs = paragraphs,
                    paragraphIndex = (paragraphs.size - 1).coerceAtLeast(0),
                )
            }
            if (wasSpeaking) speakCurrent()
        }
    }

    // --- ovoz ---

    /** O'qishni boshlaydi (joriy abzatsdan). */
    fun play() {
        val current = _state.value
        if (current.paragraphs.isEmpty()) return
        if (!current.engineReady) {
            _state.update { it.copy(error = ReaderError.VOICE_MISSING) }
            return
        }
        _state.update { it.copy(error = null) }
        speakCurrent()
    }

    /** [index] abzatsdan o'qishni boshlaydi (abzatsga bosilganda). */
    fun playFrom(index: Int) {
        val current = _state.value
        if (index !in current.paragraphs.indices) return
        stopSpeaking()
        _state.update { it.copy(paragraphIndex = index) }
        play()
    }

    /** O'qishni to'xtatadi; joriy abzats o'sha holicha qoladi. */
    fun pause() {
        stopSpeaking()
    }

    fun setRate(rate: Float) {
        prefs.rate = rate
        _state.update { it.copy(rate = rate) }
    }

    /** Ovozni tanlaydi ([id] `null` — avtomatik). */
    fun selectVoice(id: String?) {
        prefs.voiceId = id
        engine?.setVoice(id ?: VoiceChoice.automatic(allVoices())?.id)
        _state.update { it.copy(selectedVoiceId = id) }
        // O'qilayotgan bo'lsa, yangi ovoz bilan shu abzatsdan davom etadi.
        if (_state.value.speaking) {
            stopSpeaking()
            speakCurrent()
        }
    }

    /** Ovoz dvigatelini tanlaydi ([enginePackage] `null` — tizimning standarti). */
    fun selectEngine(enginePackage: String?) {
        prefs.enginePackage = enginePackage
        prefs.voiceId = null
        stopSpeaking()
        prepareEngine(enginePackage)
    }

    // --- ichki ---

    private fun prepareEngine(enginePackage: String?) {
        engine?.release()
        val fresh = DeviceTtsEngine(getApplication<Application>(), enginePackage)
        engine = fresh
        _state.update {
            it.copy(engineReady = false, selectedEngine = enginePackage, voices = emptyList(), engines = emptyList())
        }
        fresh.prepare { error ->
            // Dvigatel almashgan bo'lsa, eskisining javobi e'tiborga olinmaydi.
            if (engine !== fresh) return@prepare
            if (error != null) {
                ErrorLog.error("reader.tts", "Ovoz dvigateli ishga tushmadi: $error")
                _state.update { it.copy(engineReady = false) }
                return@prepare
            }
            val all = fresh.voices()
            // Saqlangan ovoz hali mavjud bo'lsa — shu; aks holda Sardor/Madina, bo'lmasa — avtomatik.
            val saved = prefs.voiceId?.takeIf { id -> all.any { voice -> voice.id == id } }
            fresh.setVoice(saved ?: VoiceChoice.automatic(all)?.id)
            _state.update {
                it.copy(
                    engineReady = true,
                    engines = fresh.engines(),
                    voices = VoiceChoice.recommended(all),
                    selectedVoiceId = saved,
                    hasUzbekVoice = VoiceChoice.hasUzbek(all),
                )
            }
        }
    }

    private fun allVoices(): List<VoiceInfo> = engine?.voices() ?: emptyList()

    private fun speakCurrent() {
        val tts = engine ?: return
        val current = _state.value
        val text = current.paragraphs.getOrNull(current.paragraphIndex) ?: return
        _state.update { it.copy(speaking = true, error = null) }
        tts.speak(SpeechRequest(text = text, rate = current.rate), listener)
    }

    private fun stopSpeaking() {
        engine?.stop()
        if (_state.value.speaking) _state.update { it.copy(speaking = false) }
    }

    /** Abzats tugadi: keyingisiga, bob tugasa — keyingi bobning birinchi abzatsiga. */
    private fun advance() {
        val current = _state.value
        if (!current.speaking) return

        val nextParagraph = current.paragraphIndex + 1
        if (nextParagraph < current.paragraphs.size) {
            _state.update { it.copy(paragraphIndex = nextParagraph) }
            speakCurrent()
            return
        }

        // Bob tugadi. Bo'sh boblar (matni yo'q) o'tkazib yuboriladi.
        var chapter = current.chapterIndex + 1
        while (chapter < chapters.size) {
            val paragraphs = paragraphsOf(chapter)
            if (paragraphs.isNotEmpty()) {
                _state.update {
                    it.copy(chapterIndex = chapter, paragraphs = paragraphs, paragraphIndex = 0)
                }
                speakCurrent()
                return
            }
            chapter++
        }
        // Hujjat oxiri.
        _state.update { it.copy(speaking = false) }
    }

    private val listener = object : SpeechListener {
        override fun onStarted(index: Int, total: Int, chunk: SpeechChunk) {
            // Bo'lak tugmasi kerak emas: abzats butun holda o'qiladi.
        }

        override fun onFinished() {
            advance()
        }

        override fun onError(error: VoiceError) {
            ErrorLog.error("reader.tts", "O'qish to'xtadi: $error")
            _state.update {
                it.copy(
                    speaking = false,
                    error = if (error == VoiceError.NOT_AVAILABLE) ReaderError.VOICE_MISSING else ReaderError.SPEAK_FAILED,
                )
            }
        }
    }

    private fun paragraphsOf(index: Int): List<String> {
        val chapter = chapters.getOrNull(index) ?: return emptyList()
        return ReadingText.paragraphs(chapter.text)
    }

    private fun errorOf(error: Throwable?): ReaderError = when (error) {
        is UnsupportedFormatException -> ReaderError.UNSUPPORTED
        is PdfPasswordException -> ReaderError.PASSWORD
        is DocumentTooLargeException -> ReaderError.TOO_LARGE
        is OutOfMemoryError -> ReaderError.TOO_LARGE
        is BrokenTextException -> ReaderError.BROKEN_TEXT
        is DocumentTextMissingException -> ReaderError.NO_TEXT
        else -> ReaderError.BROKEN
    }

    // --- hujjatni yuklash (fon oqimida) ---

    /** Yuklangan hujjat: boblar va ko'rsatiladigan ma'lumot. */
    private class Loaded(val name: String, val formatName: String, val chapters: List<Chapter>) {
        fun isEmpty(): Boolean = chapters.isEmpty()
    }

    private class UnsupportedFormatException : IOException("Format qo'llab-quvvatlanmaydi")

    private class BrokenTextException : IOException("Matn o'qib bo'lmaydigan shaklda")

    /** Havoladagi hujjatni nusxalaydi, formatini aniqlaydi va boblarga bo'ladi. Fon oqimida. */
    private fun load(context: Application, uri: Uri): Loaded {
        val displayName = displayName(context, uri)
        val extension = displayName.substringAfterLast('.', "").ifEmpty { "bin" }
        val copy = store.newSourceFile(extension)

        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Fayl ochilmadi")
        input.use { source -> copy.outputStream().use { sink -> source.copyTo(sink, COPY_BUFFER) } }

        try {
            val format = DocumentLoader.formatOf(copy)
                ?: DocumentLoader.zipKindOf(copy)
                ?: (if (DocumentLoader.looksLikeText(copy)) DocumentFormat.TXT else null)
                ?: throw UnsupportedFormatException()

            val name = displayName.substringBeforeLast('.').ifBlank { displayName }
            val formatName = format.extension.uppercase()

            val loadedChapters = if (format == DocumentFormat.PDF) {
                loadPdf(context, copy)
            } else {
                val document = DocumentLoader.load(
                    file = copy,
                    format = format,
                    fallbackTitle = context.getString(R.string.reader_untitled),
                    prefaceTitle = context.getString(R.string.reader_preface),
                    slideTitle = context.getString(R.string.reader_slide),
                )
                if (TextQuality.looksBroken(document.text)) throw BrokenTextException()
                document.chapters
            }
            if (loadedChapters.isEmpty()) throw DocumentTextMissingException("Hujjatda matn yo'q")
            return Loaded(name, formatName, loadedChapters)
        } finally {
            // Matn xotiraga o'qib bo'lindi, manba nusxasi endi kerak emas.
            runCatching { copy.delete() }
        }
    }

    /**
     * PDF: har bir sahifa — bob. Avval PDF kutubxonasi (deyarli har qanday
     * PDF ni o'qiydi), u matn topmasa yoki xato bersa — ilovaning o'z
     * o'quvchisi (oddiy PDF larda ishlaydi).
     */
    private fun loadPdf(context: Application, file: File): List<Chapter> {
        val pageTitle = { number: Int -> context.getString(R.string.reader_page_title, number) }

        val pages: List<PdfPageText> = try {
            PdfPageTools.readText(context, file) { done, total ->
                _state.update { it.copy(progressDone = done, progressTotal = total) }
            }
        } catch (error: PdfPasswordException) {
            throw error
        } catch (error: Exception) {
            ErrorLog.error("reader.pdf", "PDF kutubxonasi o'qiy olmadi, o'z o'quvchimiz sinaladi", error)
            emptyList()
        }

        if (pages.isNotEmpty()) {
            val joined = pages.joinToString("\n\n") { it.text }
            if (TextQuality.looksBroken(joined)) throw BrokenTextException()

            var offset = 0
            val result = ArrayList<Chapter>(pages.size)
            for (page in pages) {
                val end = offset + page.text.length
                result.add(Chapter(title = pageTitle(page.number), text = page.text, startOffset = offset, endOffset = end))
                offset = end
            }
            return result
        }

        // Zaxira yo'l: ilovaning o'z PDF o'quvchisi. Matni yo'q PDF (skaner)
        // bu yerda `DocumentTextMissingException` beradi.
        val document = DocumentLoader.load(
            file = file,
            format = DocumentFormat.PDF,
            fallbackTitle = context.getString(R.string.reader_untitled),
            prefaceTitle = context.getString(R.string.reader_preface),
        )
        if (TextQuality.looksBroken(document.text)) throw BrokenTextException()
        return document.chapters
    }

    private fun displayName(context: Application, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return fromProvider?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
    }

    override fun onCleared() {
        super.onCleared()
        engine?.release()
        engine = null
        store.clearWork()
        // Xavfsizlik uchun: `viewModelScope` shu yerda tugaydi, shuning uchun
        // yuqoridagi kuzatuvchi (agar `speaking = true` bo'lsa ham) endi hech
        // qachon ishlamaydi. Aks holda bildirishnoma va xizmat abadiy
        // ishlab qolib ketardi — ekran butunlay yopilganda (masalan bosh
        // ekranga qaytilganda) bu yerda aniq to'xtatiladi.
        KeepAliveService.stop(getApplication<Application>())
    }

    private companion object {
        const val SCOPE = "oqish"
        const val DEFAULT_NAME = "hujjat"
        const val COPY_BUFFER = 256 * 1024
    }
}
