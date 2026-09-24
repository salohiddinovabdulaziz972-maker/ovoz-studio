package uz.ovozstudio.app.ui.trim

import android.app.Application
import android.net.Uri
import android.os.Build
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
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.AudioPlayer
import uz.ovozstudio.app.media.AudioTrimmer
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WorkStore
import uz.ovozstudio.app.media.format.AndroidAudioEncoders
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.AudioOpener
import uz.ovozstudio.app.media.format.ExportOutcome
import uz.ovozstudio.app.media.format.FormatPreservingExporter
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.media.format.OpenResult
import uz.ovozstudio.app.media.format.StrictFormat
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.MediaSaver
import uz.ovozstudio.app.util.TimeParts
import java.io.File

/**
 * Ekran qaysi amalni bajaradi.
 *
 * Ikkalasi ham bitta tahrirlagichdan foydalanadi, lekin foydalanuvchi ularni
 * ikki xil niyat bilan ochadi — «shu qism kerak» yoki «shu qism keraksiz».
 * Ikki niyat — ikki alohida ekran: har birida faqat bitta amal tugmasi turadi
 * va ekran o'quvchi «Belgilangan qismni o'chirish» deb aniq aytadi.
 *
 * [suffix] — natija fayl nomining oxiriga qo'shiladi (`ovoz-kesilgan.mp3`).
 */
enum class TrimMode(val suffix: String) {
    /** Belgilangan qism qoladi, qolgani olib tashlanadi. */
    CUT("-kesilgan"),

    /** Belgilangan qism o'chadi, qolgan qismlar tutashtiriladi. */
    DELETE("-tahrirlangan"),
}

/**
 * Xatolik turlari.
 *
 * ViewModel matn emas, KOD qaytaradi: matn qaysi tilda ko'rsatilishini UI hal
 * qiladi. Aks holda rus yoki ingliz tilidagi qurilmada ham o'zbekcha xabar
 * chiqib qolardi.
 */
enum class TrimError {
    SELECTION_EMPTY,
    SELECTION_ALL,
    NOTHING_TO_UNDO,

    /** Tahrirlashning o'zi bajarilmadi (o'qish/yozish xatosi). */
    EDIT_FAILED,

    /** Natijani asl formatda yozib bo'lmadi. */
    EXPORT_FAILED,

    /** Tayyor faylni tanlangan joyga nusxalab bo'lmadi. */
    SAVE_FAILED,
}

/** Hozir bajarilayotgan uzoq ish. */
enum class TrimBusy { NONE, OPENING, EDITING, PREPARING, SAVING }

data class TrimUiState(
    val fileName: String = "",
    val formatName: String = "",
    val info: WavInfo? = null,
    val startParts: TimeParts = TimeParts(),
    val endParts: TimeParts = TimeParts(),
    val isPlaying: Boolean = false,
    val playPositionMs: Long = 0L,
    val canUndo: Boolean = false,
    /**
     * Tahrir yoki bekor qilish har bajarilganda bittaga ortadi. Ekran shu
     * raqamning o'zgarishiga qarab «bajarildi» deb e'lon qiladi: ekran
     * o'quvchi foydalanuvchisi natijani ko'rmaydi, faqat eshitadi.
     */
    val revision: Int = 0,
    val busy: TrimBusy = TrimBusy.NONE,
    /**
     * [busy] paytidagi jarayon foizi (0…1). `null` — noma'lum (masalan
     * uzunligini aytmaydigan konteyner): bu holda ekran aniq foizsiz
     * «ishlayapti» ko'rsatishi kerak.
     */
    val progress: Float? = null,
    /** Fayl ochilmagan sababi (ochish tugmasi bosilgandan keyin). */
    val openFailure: ImportFailure? = null,
    val openFailureFormat: String = "",
    val result: ResultFile? = null,
    /** Tayyor fayl saqlangan bo'lsa — uning nomi. */
    val savedName: String? = null,
    /** Saqlangan faylning qurilmadagi manzili — ovozda aytiladi. */
    val savedTo: String? = null,
    val error: TrimError? = null,
) {
    val isOpen: Boolean get() = info != null
    val isBusy: Boolean get() = busy != TrimBusy.NONE
    val durationMs: Long get() = info?.durationMs ?: 0L
}

/**
 * Kesish va o'chirish ekranlarining holati.
 *
 * Tartib: fayl tanlanadi → ochiladi (qat'iy format qoidasi shu yerda
 * tekshiriladi) → tahrirlanadi → natija **asl formatda** tayyorlanadi →
 * foydalanuvchi uni saqlaydi yoki ulashadi.
 *
 * Muhim qoida: hech bir amal mavjud faylni joyida o'zgartirmaydi. Har bir
 * tahrir YANGI fayl yozadi, tarix esa shu fayllar ro'yxatidan iborat.
 * «Bekor qilish» — shunchaki tarixda bir qadam orqaga ketish, ya'ni hech
 * qachon ma'lumot yo'qolmaydi. Asl fayl esa umuman tegilmaydi.
 */
class TrimViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)
    private val opener = AudioOpener(store, Build.VERSION.SDK_INT)
    private val exporter = FormatPreservingExporter(Build.VERSION.SDK_INT, AndroidAudioEncoders::open)
    private val player = AudioPlayer()

    /** Tahrirlash zanjiri: har bir element — to'liq WAV fayl. */
    private val history = mutableListOf<File>()
    private var historyIndex = -1

    /** Yuklangan faylning nusxasi va uning formati (natija shu formatda yoziladi). */
    private var sourceFile: File? = null
    private var origin: AudioFormat? = null
    private var baseName = ""

    private var playTicker: Job? = null

    private val _state = MutableStateFlow(TrimUiState())
    val state: StateFlow<TrimUiState> = _state.asStateFlow()

    private val currentFile: File?
        get() = history.getOrNull(historyIndex)

    /** Tanlangan faylni ochadi. Eski ish (bo'lsa) yangi fayl muvaffaqiyatli ochilgandan keyin tashlanadi. */
    fun open(uri: Uri) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            stopPlayback()
            _state.update {
                it.copy(
                    busy = TrimBusy.OPENING,
                    progress = null,
                    openFailure = null,
                    openFailureFormat = "",
                    error = null,
                    savedName = null,
                )
            }
            val outcome = withContext(Dispatchers.IO) {
                var lastPercent = -1
                runCatching {
                    opener.open(getApplication<Application>(), uri) { fraction ->
                        // Har foizda bir marta yangilanadi, har bir dekodlangan
                        // bo'lakda emas — aks holda holat oqimi soniyasiga
                        // yuzlab marta chiqardi.
                        val percent = (fraction * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.update { it.copy(progress = fraction) }
                        }
                    }
                }
            }
            when (val opened = outcome.getOrNull()) {
                is OpenResult.Opened -> startSession(opened)
                is OpenResult.Refused -> {
                    ErrorLog.info("audio.open", "Fayl ochilmadi: ${opened.reason}, format: ${opened.formatName}")
                    _state.update {
                        it.copy(
                            busy = TrimBusy.NONE,
                            progress = null,
                            openFailure = opened.reason,
                            openFailureFormat = opened.formatName,
                        )
                    }
                }
                null -> {
                    ErrorLog.error("audio.open", "Fayl ochishda kutilmagan xato", outcome.exceptionOrNull())
                    _state.update {
                        it.copy(
                            busy = TrimBusy.NONE,
                            progress = null,
                            openFailure = ImportFailure.READ_FAILED,
                            openFailureFormat = "",
                        )
                    }
                }
            }
        }
    }

    fun setStart(parts: TimeParts) = _state.update { it.copy(startParts = parts) }
    fun setEnd(parts: TimeParts) = _state.update { it.copy(endParts = parts) }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearOpenFailure() = _state.update { it.copy(openFailure = null, openFailureFormat = "") }
    fun clearSaved() = _state.update { it.copy(savedName = null, savedTo = null) }

    /** Tanlangan oraliqning boshlanishi (ms). Kiritilmagan bo'lsa — 0. */
    fun selectionStartMs(state: TrimUiState = _state.value): Long =
        state.startParts.toMillisOrNull() ?: 0L

    /** Tanlangan oraliqning tugashi (ms). Kiritilmagan bo'lsa — fayl oxiri. */
    fun selectionEndMs(state: TrimUiState = _state.value): Long =
        state.endParts.toMillisOrNull() ?: state.durationMs

    fun playSelection() {
        val file = currentFile ?: return
        val current = _state.value
        val start = selectionStartMs(current).coerceIn(0, current.durationMs)
        val end = selectionEndMs(current).coerceIn(start, current.durationMs)
        if (end <= start) {
            _state.update { it.copy(error = TrimError.SELECTION_EMPTY) }
            return
        }
        player.onFinished = { stopTicker() }
        _state.update { it.copy(isPlaying = true, playPositionMs = start, error = null) }
        player.play(file, start, end)
        startTicker(start)
    }

    fun stopPlayback() {
        player.stop()
        stopTicker()
        _state.update { it.copy(isPlaying = false) }
    }

    /** Tanlangan oraliqni saqlaydi: oraliqdan tashqari hamma narsa olib tashlanadi. */
    fun cutSelection() {
        val source = currentFile ?: return
        val current = _state.value
        val start = selectionStartMs(current)
        val end = selectionEndMs(current)
        if (end <= start) {
            _state.update { it.copy(error = TrimError.SELECTION_EMPTY) }
            return
        }
        // Yangi faylning chetlari to'lqin o'rtasidan boshlanadi: bir zumlik
        // silliqlash «chiqillash»ning oldini oladi. Faylning haqiqiy boshi va
        // oxiriga tegilmaydi.
        val fades = AudioTrimmer.Fades(
            fadeInMs = if (start > 0L) EDGE_FADE_MS else 0L,
            fadeOutMs = if (end < current.durationMs) EDGE_FADE_MS else 0L,
        )
        runEdit(TAG_CUT) { destination, onProgress ->
            AudioTrimmer.copyRange(source, destination, start, end, fades, onProgress = onProgress)
        }
    }

    /** Tanlangan oraliqni o'chiradi, qolgan qismlar bitishtiriladi. */
    fun deleteSelection() {
        val source = currentFile ?: return
        val current = _state.value
        val start = selectionStartMs(current)
        val end = selectionEndMs(current)
        // Bo'sh yoki teskari oraliq — bu boshqa xato: foydalanuvchi butun faylni
        // o'chirmoqchi emas, shunchaki raqamlarni xato kiritgan.
        if (end <= start) {
            _state.update { it.copy(error = TrimError.SELECTION_EMPTY) }
            return
        }
        if (start == 0L && end >= current.durationMs) {
            _state.update { it.copy(error = TrimError.SELECTION_ALL) }
            return
        }
        runEdit(TAG_DELETE) { destination, onProgress ->
            AudioTrimmer.deleteRanges(
                source,
                destination,
                listOf(AudioTrimmer.Cut(start, end)),
                joinFadeMs = JOIN_FADE_MS,
                onProgress = onProgress,
            )
        }
    }

    fun undo() {
        if (_state.value.isBusy) return
        if (historyIndex <= 0) {
            _state.update { it.copy(error = TrimError.NOTHING_TO_UNDO) }
            return
        }
        stopPlayback()
        historyIndex--
        _state.update {
            it.copy(result = null, savedName = null, error = null, revision = it.revision + 1)
        }
        refreshFromCurrent()
    }

    /**
     * Natijani **asl formatda** yozadi.
     *
     * Ichkarida tahrirlash yo'qotishsiz PCM ustida ketgan; bu yerda u faylning
     * yuklangan konteyneri va kodekiga qayta kodlanadi. Boshqa formatga
     * o'tish yo'q: [AudioOpener] shunday format bilan ishlab bo'lmaydigan
     * faylni ish boshlanmasdan oldin rad etgan.
     */
    fun prepareResult(mode: TrimMode) {
        val edited = currentFile ?: return
        val format = origin ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            stopPlayback()
            _state.update { it.copy(busy = TrimBusy.PREPARING, progress = null, error = null, savedName = null) }
            val destination = store.newOutputFile(baseName, mode.suffix, format.container.extension)
            val outcome = withContext(Dispatchers.IO) {
                var lastPercent = -1
                runCatching {
                    exporter.export(edited, format, destination) { fraction ->
                        val percent = (fraction * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.update { it.copy(progress = fraction) }
                        }
                    }
                }
            }
            val done = outcome.getOrNull()
            if (done is ExportOutcome.Done && destination.exists()) {
                store.markOutputReady(destination)
                _state.update {
                    it.copy(
                        busy = TrimBusy.NONE,
                        progress = null,
                        result = ResultFile(
                            path = destination.absolutePath,
                            name = destination.name,
                            mimeType = StrictFormat.mimeType(format.container),
                            sizeBytes = destination.length(),
                        ),
                    )
                }
            } else {
                // Yarim yozilgan fayl qolib ketmasligi kerak.
                runCatching { destination.parentFile?.deleteRecursively() }
                ErrorLog.error(
                    "audio.export",
                    "Natijani asl formatda yozib bo'lmadi: ${StrictFormat.label(format)}, natija: ${done?.javaClass?.simpleName}",
                    outcome.exceptionOrNull(),
                )
                _state.update { it.copy(busy = TrimBusy.NONE, progress = null, error = TrimError.EXPORT_FAILED) }
            }
        }
    }

    /**
     * Tayyor faylni qurilmaning umumiy papkasiga **bitta bosishda** yozadi.
     *
     * Alohida «Fayl sifatida saqlash» oynasi yo'q: u har saqlashda joy tanlash
     * va nom tasdiqlashni talab qilardi. Audio fayllar `Music`, hujjatlar
     * `Documents` papkasiga tushadi.
     */
    fun save() {
        val result = _state.value.result ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = TrimBusy.SAVING, error = null) }
            val outcome = withContext(Dispatchers.IO) {
                MediaSaver.save(
                    getApplication(),
                    File(result.path),
                    result.name,
                    result.mimeType,
                    isAudio = true,
                )
            }
            // Xato jurnalga `MediaSaver` ichida yoziladi — bu yerda
            // takrorlanmaydi, aks holda bitta xato ikki marta tushardi.
            _state.update {
                when (outcome) {
                    is MediaSaver.Result.Saved ->
                        it.copy(busy = TrimBusy.NONE, savedName = result.name, savedTo = outcome.display, error = null)
                    is MediaSaver.Result.Failed ->
                        it.copy(busy = TrimBusy.NONE, savedName = null, savedTo = null, error = TrimError.SAVE_FAILED)
                }
            }
        }
    }

    fun releasePlayer() {
        player.release()
        stopTicker()
    }

    // --- yordamchi ---

    private fun startSession(opened: OpenResult.Opened) {
        // Oldingi ish tugadi: uning fayllari endi keraksiz.
        val previous = history.toList() + listOfNotNull(sourceFile)

        sourceFile = opened.source
        origin = opened.origin
        baseName = opened.displayName.substringBeforeLast('.').ifBlank { DEFAULT_NAME }
        history.clear()
        history += opened.wav
        historyIndex = 0

        for (file in previous) runCatching { file.delete() }

        _state.update {
            it.copy(
                busy = TrimBusy.NONE,
                progress = null,
                formatName = StrictFormat.label(opened.origin),
                openFailure = null,
                openFailureFormat = "",
                result = null,
                savedName = null,
                error = null,
                revision = 0,
            )
        }
        refreshFromCurrent()
    }

    private fun runEdit(tag: String, operation: (File, (Float) -> Unit) -> WavInfo) {
        if (currentFile == null || _state.value.isBusy) return
        viewModelScope.launch {
            stopPlayback()
            _state.update { it.copy(busy = TrimBusy.EDITING, progress = null, error = null, savedName = null) }
            val destination = store.newEditFile(tag)
            val result = withContext(Dispatchers.IO) {
                var lastPercent = -1
                runCatching {
                    operation(destination) { fraction ->
                        val percent = (fraction * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.update { it.copy(progress = fraction) }
                        }
                    }
                }
            }
            result.fold(
                onSuccess = {
                    // Yangi amaldan keyin «oldinga» tarixi yo'qoladi va tayyor
                    // natija eskirdi: u endi oldingi tahrirga tegishli.
                    while (history.size > historyIndex + 1) {
                        val dropped = history.removeAt(history.size - 1)
                        runCatching { dropped.delete() }
                    }
                    history += destination
                    historyIndex = history.size - 1
                    _state.update {
                        it.copy(busy = TrimBusy.NONE, progress = null, result = null, revision = it.revision + 1)
                    }
                    refreshFromCurrent()
                },
                onFailure = { error ->
                    ErrorLog.error("audio.edit", "Tahrirlash bajarilmadi ($tag)", error)
                    runCatching { destination.delete() }
                    _state.update { it.copy(busy = TrimBusy.NONE, progress = null, error = TrimError.EDIT_FAILED) }
                },
            )
        }
    }

    private fun refreshFromCurrent() {
        val file = currentFile ?: return
        val info = runCatching { WavFile.readInfo(file) }.getOrNull()
        if (info == null) {
            // Fayl o'qilmadi: ekran `info` bo'yicha chiziladi, ya'ni barcha
            // boshqaruv tugmalari yo'qolib, fayl ochilmagandek ko'rinardi —
            // sabab esa aytilmasdi. Xatoni jurnalga yozamiz va ko'rsatamiz.
            ErrorLog.error("kesish.ochish", "Tahrir natijasini o'qib bo'lmadi: ${file.name}")
            _state.update {
                it.copy(
                    busy = TrimBusy.NONE,
                    progress = null,
                    info = null,
                    error = TrimError.EDIT_FAILED,
                )
            }
            return
        }
        _state.update {
            it.copy(
                fileName = baseName,
                info = info,
                // Har bir amaldan keyin tanlov butun faylni qamrab oladi:
                // eski raqam yangi fayl uzunligiga to'g'ri kelmasligi mumkin.
                startParts = TimeParts.fromMillis(0),
                endParts = TimeParts.fromMillis(info.durationMs),
                canUndo = historyIndex > 0,
            )
        }
    }

    private fun startTicker(fromMs: Long) {
        stopTicker()
        playTicker = viewModelScope.launch {
            var last = fromMs
            while (true) {
                delay(POSITION_TICK_MS)
                player.stopIfPastEnd()
                val position = player.positionMs()
                if (!player.isPlaying) {
                    _state.update { it.copy(isPlaying = false, playPositionMs = position) }
                    break
                }
                if (position != last) {
                    last = position
                    _state.update { it.copy(playPositionMs = position) }
                }
            }
        }
    }

    private fun stopTicker() {
        playTicker?.cancel()
        playTicker = null
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
        // Ish fayllari o'chadi. Tayyor natijalar qoladi: ulashilgan fayl
        // boshqa ilovada keyinroq o'qilishi mumkin (`WorkStore.clearWork`).
        store.clearWork()
    }

    private companion object {
        const val SCOPE = "audio"
        const val TAG_CUT = "kesish"
        const val TAG_DELETE = "ochirish"
        const val DEFAULT_NAME = "audio"
        const val POSITION_TICK_MS = 100L

        /** Kesilgan joyning chetidagi silliqlash (ms): quloq eshitmaydi, «chiqillash»ni yo'qotadi. */
        const val EDGE_FADE_MS = 5L

        /** O'chirilgan qism o'rnidagi tutashuv silliqlashi (ms). */
        const val JOIN_FADE_MS = 5L
    }
}
