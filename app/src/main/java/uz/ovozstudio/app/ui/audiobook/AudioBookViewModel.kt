package uz.ovozstudio.app.ui.audiobook

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
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
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore
import uz.ovozstudio.app.media.doc.DocumentFormat
import uz.ovozstudio.app.media.doc.DocumentLoader
import uz.ovozstudio.app.media.doc.DocumentTextMissingException
import uz.ovozstudio.app.media.doc.DocumentTooLargeException
import uz.ovozstudio.app.media.doc.ReadingText
import uz.ovozstudio.app.media.doc.TextQuality
import uz.ovozstudio.app.media.pdf.PdfPageTools
import uz.ovozstudio.app.media.pdf.PdfPasswordException
import uz.ovozstudio.app.media.voice.AudioBookExporter
import uz.ovozstudio.app.media.voice.AudioBookOutcome
import uz.ovozstudio.app.media.voice.AudioBookRequest
import uz.ovozstudio.app.media.voice.AudioBookService
import uz.ovozstudio.app.media.voice.DeviceTtsEngine
import uz.ovozstudio.app.media.voice.TtsEngineInfo
import uz.ovozstudio.app.media.voice.VoiceChoice
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.media.voice.VoiceInfo
import uz.ovozstudio.app.media.voice.VoicePrefs
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.ResultFiles
import java.io.File
import java.io.IOException

/** Hujjat ochilmagan yoki eksport bajarilmagan sabab. Matnni ekran tanlaydi. */
enum class AudioBookError {
    UNSUPPORTED,
    BROKEN,
    TOO_LARGE,
    NO_TEXT,
    BROKEN_TEXT,
    PASSWORD,
    VOICE_MISSING,
    FAILED,
    CANCELLED,
}

data class AudioBookUiState(
    val documentName: String = "",
    val paragraphCount: Int = 0,
    val opening: Boolean = false,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val engineReady: Boolean = false,
    val engines: List<TtsEngineInfo> = emptyList(),
    val selectedEngine: String? = null,
    val voices: List<VoiceInfo> = emptyList(),
    val selectedVoiceId: String? = null,
    val hasUzbekVoice: Boolean = true,
    val rate: Float = 1.0f,
    /** Eksport [AudioBookService] ichida ketyapti — bu ekran yopilsa ham davom etadi. */
    val running: Boolean = false,
    val stage: AudioBookExporter.Stage? = null,
    val progress: Float = 0f,
    val result: ResultFile? = null,
    val error: AudioBookError? = null,
) {
    val isOpen: Boolean get() = paragraphCount > 0
}

/**
 * Hujjatni MP3 audio-kitobga aylantirish ekranining holati.
 *
 * Eksportning **o'zi bu yerda ketmaydi** — u [AudioBookService] ichida,
 * ekrandan mustaqil ketadi (ekran yopilsa ham, ilova fonga tushsa ham).
 * Bu ViewModel faqat uchta ish qiladi: hujjatni ochib abzatslarga bo'ladi,
 * ovoz/dvigatel tanlash uchun bitta probasinash dvigatelini boshqaradi,
 * va xizmatning holatini ([AudioBookService.state]) kuzatib ekranga uzatadi.
 */
class AudioBookViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)
    private val prefs = VoicePrefs(application)

    /** Ochilgan hujjatning abzatslari. Holatga kirmaydi: katta bo'lishi mumkin. */
    private var paragraphs: List<String> = emptyList()
    private var baseName = ""

    /** Faqat ovozlar ro'yxatini olish uchun; eksportning o'zi xizmatning o'z dvigatelida ketadi. */
    private var probeEngine: VoiceEngine? = null

    private val _state = MutableStateFlow(AudioBookUiState(rate = prefs.rate))
    val state: StateFlow<AudioBookUiState> = _state.asStateFlow()

    init {
        prepareEngine(prefs.enginePackage)
        viewModelScope.launch {
            AudioBookService.state.collect { service ->
                // Xizmatda [WorkStore] yo'q — u faqat `File` ko'radi, shuning
                // uchun "tayyor" belgisini shu yerda qo'yamiz. Belgi qo'yilmasa
                // [WorkStore.sweep] tugallangan kitobni ham chala deb o'chirib
                // yuborardi: eksport tugagach fayl o'z joyida qoladi, jarayon
                // esa keyin qayta ishga tushadi.
                (service.outcome as? AudioBookOutcome.Done)?.let { done ->
                    store.markOutputReady(done.file)
                }
                _state.update {
                    it.copy(
                        running = service.running,
                        stage = service.stage,
                        progress = service.progress,
                        result = (service.outcome as? AudioBookOutcome.Done)?.let { done ->
                            ResultFile(done.file.absolutePath, done.file.name, "audio/mpeg", done.file.length())
                        } ?: it.result,
                        error = when (service.outcome) {
                            is AudioBookOutcome.Failed -> AudioBookError.FAILED
                            is AudioBookOutcome.Cancelled -> AudioBookError.CANCELLED
                            else -> it.error
                        },
                    )
                }
            }
        }
    }

    fun open(uri: Uri) {
        if (_state.value.opening) return
        viewModelScope.launch {
            _state.update {
                it.copy(opening = true, progressDone = 0, progressTotal = 0, error = null, result = null)
            }
            val context = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) { runCatching { load(context, uri) } }
            val loaded = outcome.getOrNull()
            if (loaded == null) {
                val failure = outcome.exceptionOrNull()
                ErrorLog.error("audiobook.open", "Hujjat ochilmadi", failure)
                _state.update { it.copy(opening = false, error = errorOf(failure)) }
                return@launch
            }
            paragraphs = loaded.paragraphs
            baseName = loaded.name
            _state.update {
                it.copy(
                    opening = false,
                    documentName = loaded.name,
                    paragraphCount = loaded.paragraphs.size,
                    error = null,
                )
            }
        }
    }

    fun setRate(rate: Float) {
        prefs.rate = rate
        _state.update { it.copy(rate = rate) }
    }

    fun selectVoice(id: String?) {
        prefs.voiceId = id
        _state.update { it.copy(selectedVoiceId = id) }
    }

    fun selectEngine(enginePackage: String?) {
        prefs.enginePackage = enginePackage
        prefs.voiceId = null
        prepareEngine(enginePackage)
    }

    /** Eksportni [AudioBookService] ga topshiradi — u ekrandan mustaqil davom etadi. */
    fun start() {
        val current = _state.value
        if (paragraphs.isEmpty() || current.running) return
        if (!current.engineReady) {
            _state.update { it.copy(error = AudioBookError.VOICE_MISSING) }
            return
        }
        val destination = store.newOutputFile(baseName, "-audiokitob", "mp3")
        AudioBookService.start(
            getApplication(),
            AudioBookRequest(
                documentName = baseName,
                paragraphs = paragraphs,
                rate = current.rate,
                enginePackage = current.selectedEngine,
                voiceId = current.selectedVoiceId,
                workDir = store.editsDirectory,
                destination = destination,
            ),
        )
        _state.update { it.copy(error = null, result = null) }
    }

    fun cancel() = AudioBookService.cancel(getApplication())

    /** Tayyor faylni foydalanuvchi tanlagan joyga ([uri]) nusxalaydi. */
    fun saveTo(uri: Uri) {
        val result = _state.value.result ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                ResultFiles.copyTo(getApplication<Application>(), File(result.path), uri)
            }
            if (!ok) ErrorLog.error("audiobook.save", "Faylni tanlangan joyga yozib bo'lmadi")
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    fun clearResult() {
        AudioBookService.clearOutcome()
        _state.update { it.copy(result = null) }
    }

    // --- ovoz dvigateli (faqat ro'yxat uchun) ---

    private fun prepareEngine(enginePackage: String?) {
        probeEngine?.release()
        val fresh = DeviceTtsEngine(getApplication<Application>(), enginePackage)
        probeEngine = fresh
        _state.update {
            it.copy(engineReady = false, selectedEngine = enginePackage, voices = emptyList(), engines = emptyList())
        }
        fresh.prepare { error ->
            if (probeEngine !== fresh) return@prepare
            if (error != null) {
                _state.update { it.copy(engineReady = false) }
                return@prepare
            }
            val all = fresh.voices()
            val saved = prefs.voiceId?.takeIf { id -> all.any { voice -> voice.id == id } }
            _state.update {
                it.copy(
                    engineReady = true,
                    engines = fresh.engines(),
                    voices = VoiceChoice.recommended(all),
                    selectedVoiceId = saved ?: VoiceChoice.automatic(all)?.id,
                    hasUzbekVoice = VoiceChoice.hasUzbek(all),
                )
            }
        }
    }

    // --- hujjatni yuklash (fon oqimida) ---

    private class Loaded(val name: String, val paragraphs: List<String>)

    private class UnsupportedFormatException : IOException("Format qo'llab-quvvatlanmaydi")

    private class BrokenTextException : IOException("Matn o'qib bo'lmaydigan shaklda")

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

            val text = if (format == DocumentFormat.PDF) {
                readPdfText(context, copy)
            } else {
                val document = DocumentLoader.load(
                    file = copy,
                    format = format,
                    fallbackTitle = context.getString(R.string.reader_untitled),
                    prefaceTitle = context.getString(R.string.reader_preface),
                    slideTitle = context.getString(R.string.reader_slide),
                )
                document.text
            }
            if (TextQuality.looksBroken(text)) throw BrokenTextException()

            val paragraphs = ReadingText.paragraphs(text)
            if (paragraphs.isEmpty()) throw DocumentTextMissingException("Hujjatda matn yo'q")
            return Loaded(name, paragraphs)
        } finally {
            runCatching { copy.delete() }
        }
    }

    /** PDF: avval kutubxona bilan ([PdfPageTools]), u matn topmasa — ilovaning o'z o'quvchisi bilan. */
    private fun readPdfText(context: Application, file: File): String {
        val pages = try {
            PdfPageTools.readText(context, file) { done, total ->
                _state.update { it.copy(progressDone = done, progressTotal = total) }
            }
        } catch (error: PdfPasswordException) {
            throw error
        } catch (error: Exception) {
            ErrorLog.error("audiobook.pdf", "PDF kutubxonasi o'qiy olmadi, o'z o'quvchimiz sinaladi", error)
            emptyList()
        }
        if (pages.isNotEmpty()) return pages.joinToString("\n\n") { it.text }

        val document = DocumentLoader.load(
            file = file,
            format = DocumentFormat.PDF,
            fallbackTitle = context.getString(R.string.reader_untitled),
            prefaceTitle = context.getString(R.string.reader_preface),
        )
        return document.text
    }

    private fun errorOf(error: Throwable?): AudioBookError = when (error) {
        is UnsupportedFormatException -> AudioBookError.UNSUPPORTED
        is PdfPasswordException -> AudioBookError.PASSWORD
        is DocumentTooLargeException -> AudioBookError.TOO_LARGE
        is OutOfMemoryError -> AudioBookError.TOO_LARGE
        is BrokenTextException -> AudioBookError.BROKEN_TEXT
        is DocumentTextMissingException -> AudioBookError.NO_TEXT
        else -> AudioBookError.BROKEN
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
        probeEngine?.release()
        probeEngine = null
        store.clearWork()
        // DIQQAT: `AudioBookService` shu yerda TO'XTATILMAYDI — u ekrandan
        // mustaqil davom etishi kerak (ekran yopilsa ham).
    }

    private companion object {
        const val SCOPE = "audiokitob"
        const val DEFAULT_NAME = "hujjat"
        const val COPY_BUFFER = 256 * 1024
    }
}
