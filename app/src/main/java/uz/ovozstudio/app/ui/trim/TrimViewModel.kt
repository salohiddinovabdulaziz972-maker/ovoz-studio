package uz.ovozstudio.app.ui.trim

import android.app.Application
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
import uz.ovozstudio.app.media.AudioPlayer
import uz.ovozstudio.app.media.AudioTrimmer
import uz.ovozstudio.app.media.RecordingStore
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.util.TimeParts
import java.io.File

/**
 * Xatolik turlari.
 *
 * ViewModel matn emas, KOD qaytaradi: matn qaysi tilda ko'rsatilishini UI hal
 * qiladi. Aks holda rus yoki ingliz tilidagi qurilmada ham o'zbekcha xabar
 * chiqib qolardi.
 */
/**
 * Yangi fade uzunligi (ms) uchun boshlang'ich qiymat.
 *
 * Fayl darajasida turibdi, `TrimViewModel` ning ichida emas: `TrimUiState`
 * ham shu qiymatni ishlatadi, sinf ichidagi `private` esa undan ko'rinmaydi.
 */
private const val DEFAULT_FADE_MS = "30"

enum class TrimError {
    FILE_NOT_FOUND,
    SELECTION_EMPTY,
    SELECTION_ALL,
    NOTHING_TO_UNDO,
    /** Ro'yxat bo'sh — o'chirish uchun hech narsa qo'shilmagan. */
    CUTS_EMPTY,
    /** Ro'yxatdagi bo'laklar butun faylni qamrab olgan. */
    CUTS_ALL,
    /** Bo'lish nuqtasi fayl chegarasida yoki kiritilmagan. */
    SPLIT_POINT_INVALID,
    /** Tahrirlashning o'zi bajarilmadi (o'qish/yozish xatosi). */
    EDIT_FAILED,
    SAVE_FAILED,
}

data class TrimUiState(
    val fileName: String = "",
    val info: WavInfo? = null,
    val startParts: TimeParts = TimeParts(),
    val endParts: TimeParts = TimeParts(),
    val splitParts: TimeParts = TimeParts(),
    /**
     * Ko'p nuqtali o'chirish ro'yxati.
     *
     * Bo'laklar fayl vaqtida saqlanadi, ya'ni ro'yxat tahrirlar orasida
     * o'zgarmaydi: foydalanuvchi ularni bir to'plam qilib yig'ib, keyin bir
     * marta qo'llaydi. Har bir tahrir ularni tozalaydi.
     */
    val cuts: List<AudioTrimmer.Cut> = emptyList(),
    val fadeIn: Boolean = false,
    val fadeOut: Boolean = false,
    val fadeMs: String = DEFAULT_FADE_MS,
    val isPlaying: Boolean = false,
    val playPositionMs: Long = 0L,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: Boolean = false,
    val savedPath: String? = null,
    /** Bo'lishdan keyingi ikkinchi qism — kutubxonaga tushgan yangi fayl. */
    val splitSecondPath: String? = null,
    val error: TrimError? = null,
) {
    val durationMs: Long get() = info?.durationMs ?: 0L
}

/**
 * Kesish ekranining holati.
 *
 * Muhim qoida: hech bir amal mavjud faylni joyida o'zgartirmaydi. Har bir
 * kesish YANGI fayl yozadi, tarix esa shu fayllar ro'yxatidan iborat.
 * «Orqaga qaytarish» — shunchaki tarixda bir qadam orqaga ketish, ya'ni
 * hech qachon ma'lumot yo'qolmaydi.
 */
class TrimViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val player = AudioPlayer()

    /** Tahrirlash zanjiri: har bir element — to'liq WAV fayl. */
    private val history = mutableListOf<File>()
    private var historyIndex = -1

    private var playTicker: Job? = null

    private val _state = MutableStateFlow(TrimUiState())
    val state: StateFlow<TrimUiState> = _state.asStateFlow()

    private val currentFile: File?
        get() = history.getOrNull(historyIndex)

    fun load(path: String) {
        stopPlayback()
        val file = File(path)
        if (!file.exists()) {
            _state.update { it.copy(error = TrimError.FILE_NOT_FOUND) }
            return
        }
        history.clear()
        history += file
        historyIndex = 0
        refreshFromCurrent()
    }

    fun setStart(parts: TimeParts) = _state.update { it.copy(startParts = parts) }
    fun setEnd(parts: TimeParts) = _state.update { it.copy(endParts = parts) }
    fun setSplitPoint(parts: TimeParts) = _state.update { it.copy(splitParts = parts) }
    fun setFadeIn(enabled: Boolean) = _state.update { it.copy(fadeIn = enabled) }
    fun setFadeOut(enabled: Boolean) = _state.update { it.copy(fadeOut = enabled) }
    fun setFadeMs(value: String) = _state.update {
        it.copy(fadeMs = value.filter(Char::isDigit).take(4))
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun consumeSaved() = _state.update { it.copy(savedPath = null) }
    fun consumeSplit() = _state.update { it.copy(splitSecondPath = null) }

    /** Tanlangan oraliqning boshlanishi (ms). Butun fayl kiritilmagan bo'lsa — 0. */
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

    /** Tanlangan oraliqni saqlaydi: oraliqdan tashqari hamma narsa o'chadi. */
    fun applyTrim() {
        val source = currentFile ?: return
        val current = _state.value
        val start = selectionStartMs(current)
        val end = selectionEndMs(current)
        if (end <= start) {
            _state.update { it.copy(error = TrimError.SELECTION_EMPTY) }
            return
        }
        runEdit(TAG_TRIM) { destination ->
            AudioTrimmer.copyRange(source, destination, start, end, fadesOf(current))
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
        runEdit(TAG_DELETE) { destination ->
            AudioTrimmer.deleteRanges(
                source,
                destination,
                listOf(AudioTrimmer.Cut(start, end)),
                fadesOf(current),
            )
        }
    }

    /**
     * Joriy tanlovni o'chirish ro'yxatiga qo'shadi.
     *
     * Hech narsa o'chirilmaydi — faqat ro'yxat to'ladi. Shu sababli bu amal
     * tarixga ham tushmaydi: bekor qilish uchun «ro'yxatdan olib tashlash» bor.
     */
    fun addCut() {
        val current = _state.value
        val start = selectionStartMs(current)
        val end = selectionEndMs(current)
        if (end <= start) {
            _state.update { it.copy(error = TrimError.SELECTION_EMPTY) }
            return
        }
        _state.update {
            it.copy(cuts = it.cuts + AudioTrimmer.Cut(start, end), error = null)
        }
    }

    fun removeCut(index: Int) = _state.update {
        if (index in it.cuts.indices) it.copy(cuts = it.cuts.filterIndexed { i, _ -> i != index })
        else it
    }

    fun clearCuts() = _state.update { it.copy(cuts = emptyList(), error = null) }

    /** Ro'yxatdagi barcha bo'laklarni bir marta o'chiradi. */
    fun applyCuts() {
        val source = currentFile ?: return
        val current = _state.value
        val info = current.info
        if (current.cuts.isEmpty()) {
            _state.update { it.copy(error = TrimError.CUTS_EMPTY) }
            return
        }
        if (info == null) {
            _state.update { it.copy(error = TrimError.FILE_NOT_FOUND) }
            return
        }
        // Butun fayl o'chirilishini oldindan aytamiz: aks holda foydalanuvchi
        // umumiy «tahrirlab bo'lmadi» xatosini olardi va sababini bilmasdi.
        if (AudioTrimmer.coversWholeFile(info, current.cuts)) {
            _state.update { it.copy(error = TrimError.CUTS_ALL) }
            return
        }
        val cuts = current.cuts
        runEdit(tag = TAG_DELETE, onSuccess = { clearCuts() }) { destination ->
            AudioTrimmer.deleteRanges(source, destination, cuts, fadesOf(current))
        }
    }

    /**
     * Faylni bo'lish nuqtasidan ikki qismga ajratadi.
     *
     * Birinchi qism tahrirlash zanjirida qoladi (ya'ni uni yana tahrirlab,
     * «Saqlash» bilan yakunlash mumkin), ikkinchisi esa darhol kutubxonaga —
     * asosiy yozuvlar papkasiga — tushadi. Shu sababli bo'lish hech qachon
     * ma'lumot yo'qotmaydi: ikkala qism ham fayl ko'rinishida mavjud.
     */
    fun applySplit() {
        val source = currentFile ?: return
        val current = _state.value
        val at = current.splitParts.toMillisOrNull()
        if (at == null || at <= 0L || at >= current.durationMs) {
            _state.update { it.copy(error = TrimError.SPLIT_POINT_INVALID) }
            return
        }
        viewModelScope.launch {
            stopPlayback()
            _state.update { it.copy(busy = true, error = null) }
            val first = store.newEditFile(TAG_SPLIT)
            val second = store.newRecordingFile("${source.nameWithoutExtension}-2")
            val result = withContext(Dispatchers.IO) {
                runCatching { AudioTrimmer.split(source, first, second, at) }
            }
            result.fold(
                onSuccess = {
                    while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
                    history += first
                    historyIndex = history.size - 1
                    _state.update {
                        it.copy(busy = false, splitSecondPath = second.absolutePath)
                    }
                    refreshFromCurrent()
                },
                onFailure = {
                    // Yarim yozilgan fayllar qolib ketmasligi kerak.
                    first.delete()
                    second.delete()
                    _state.update { it.copy(busy = false, error = TrimError.EDIT_FAILED) }
                },
            )
        }
    }

    fun undo() {
        if (historyIndex <= 0) {
            _state.update { it.copy(error = TrimError.NOTHING_TO_UNDO) }
            return
        }
        historyIndex--
        refreshFromCurrent()
    }

    fun redo() {
        if (historyIndex >= history.size - 1) return
        historyIndex++
        refreshFromCurrent()
    }

    /** Joriy natijani asosiy papkaga saqlaydi. */
    fun save() {
        val source = currentFile ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val destination = store.newRecordingFile()
            val ok = withContext(Dispatchers.IO) {
                runCatching { source.copyTo(destination, overwrite = true) }.isSuccess
            }
            if (ok) {
                // Oraliq fayllar o'chiriladi, shuning uchun tarix ham yangilanadi:
                // aks holda «orqaga qaytarish» o'chirilgan faylga olib borardi.
                // Saqlangan fayl — yangi zanjirning boshi.
                history.clear()
                history += destination
                historyIndex = 0
                store.clearEdits(keep = null)
            }
            _state.update {
                it.copy(
                    busy = false,
                    savedPath = if (ok) destination.absolutePath else null,
                    error = if (ok) null else TrimError.SAVE_FAILED,
                )
            }
            if (ok) refreshFromCurrent()
        }
    }

    fun releasePlayer() {
        player.release()
        stopTicker()
    }

    // --- yordamchi ---

    private fun runEdit(
        tag: String,
        onSuccess: () -> Unit = {},
        operation: (File) -> WavInfo,
    ) {
        if (currentFile == null) return
        viewModelScope.launch {
            stopPlayback()
            _state.update { it.copy(busy = true, error = null) }
            val destination = store.newEditFile(tag)
            val result = withContext(Dispatchers.IO) {
                runCatching { operation(destination) }
            }
            result.fold(
                onSuccess = {
                    // Yangi amaldan keyin «oldinga» tarixi yo'qoladi.
                    while (history.size > historyIndex + 1) history.removeAt(history.size - 1)
                    history += destination
                    historyIndex = history.size - 1
                    _state.update { it.copy(busy = false) }
                    onSuccess()
                    refreshFromCurrent()
                },
                onFailure = { error ->
                    destination.delete()
                    _state.update {
                        it.copy(busy = false, error = TrimError.EDIT_FAILED)
                    }
                },
            )
        }
    }

    private fun refreshFromCurrent() {
        val file = currentFile ?: return
        val info = runCatching { WavFile.readInfo(file) }.getOrNull()
        _state.update {
            it.copy(
                fileName = file.nameWithoutExtension,
                info = info,
                // Har bir amaldan keyin tanlov butun faylni qamrab oladi,
                // bo'lish nuqtasi esa bo'shatiladi: eski raqam yangi fayl
                // uzunligiga to'g'ri kelmasligi mumkin.
                startParts = TimeParts.fromMillis(0),
                endParts = TimeParts.fromMillis(info?.durationMs ?: 0L),
                splitParts = TimeParts(),
                canUndo = historyIndex > 0,
                canRedo = historyIndex < history.size - 1,
            )
        }
    }

    private fun fadesOf(state: TrimUiState): AudioTrimmer.Fades {
        val length = state.fadeMs.toLongOrNull() ?: 0L
        return AudioTrimmer.Fades(
            fadeInMs = if (state.fadeIn) length else 0L,
            fadeOutMs = if (state.fadeOut) length else 0L,
        )
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
    }

    private companion object {
        const val TAG_TRIM = "kesish"
        const val TAG_DELETE = "ochirish"
        const val TAG_SPLIT = "bolish"
        const val POSITION_TICK_MS = 100L
    }
}
