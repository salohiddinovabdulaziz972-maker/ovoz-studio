package uz.ovozstudio.app.ui.merge

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore
import uz.ovozstudio.app.media.format.AndroidAudioEncoders
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.AudioOpener
import uz.ovozstudio.app.media.format.ExportOutcome
import uz.ovozstudio.app.media.format.FormatPreservingExporter
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.media.format.OpenResult
import uz.ovozstudio.app.media.format.StrictFormat
import uz.ovozstudio.app.media.merge.AudioMerger
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.ResultFiles
import java.io.File

/** Ro'yxatdagi bitta fayl: ochilgan va birlashtirishga tayyor. */
data class MergeItem(
    val id: Long,
    val name: String,
    /** Konteyner va kodek — manbadan; parametrlar — ochilgan fayldan. */
    val format: AudioFormat,
    val durationMs: Long,
    val source: File,
    val wav: File,
)

/**
 * Ro'yxatga qo'shilmagan fayl.
 *
 * [reason] `null` bo'lsa, fayl ochildi, lekin ro'yxatdagi formatga mos emas:
 * [formatName] — uning formati, [listFormatName] — ro'yxatniki.
 */
data class MergeFailure(
    val fileName: String,
    val reason: ImportFailure? = null,
    val formatName: String = "",
    val listFormatName: String = "",
)

enum class MergeError {
    /** Birlashtirish uchun kamida ikkita fayl kerak. */
    NEED_TWO,

    /** Birlashtirishning o'zi bajarilmadi (o'qish/yozish xatosi, joy yetmadi). */
    MERGE_FAILED,

    /** Natijani asl formatda yozib bo'lmadi. */
    EXPORT_FAILED,

    /** Tayyor faylni tanlangan joyga nusxalab bo'lmadi. */
    SAVE_FAILED,
}

/** Hozir bajarilayotgan uzoq ish. */
enum class MergeBusy { NONE, ADDING, MERGING, SAVING }

data class MergeUiState(
    val items: List<MergeItem> = emptyList(),
    val busy: MergeBusy = MergeBusy.NONE,
    /** Fayl qo'shilayotganda: nechanchi fayl / nechta. */
    val addingCurrent: Int = 0,
    val addingTotal: Int = 0,
    /** Birlashtirish jarayoni, 0…1. */
    val progress: Float = 0f,
    val failures: List<MergeFailure> = emptyList(),
    val result: ResultFile? = null,
    val savedName: String? = null,
    val error: MergeError? = null,
) {
    val isBusy: Boolean get() = busy != MergeBusy.NONE

    /** Ro'yxat formati (birinchi fayl bo'yicha); ro'yxat bo'sh bo'lsa bo'sh matn. */
    val formatName: String
        get() = items.firstOrNull()?.let { StrictFormat.label(it.format) } ?: ""

    val totalDurationMs: Long get() = items.sumOf { it.durationMs }
}

/**
 * Audiolarni birlashtirish ekranining holati.
 *
 * **Format qoidasi.** Natija yuklangan formatda qaytadi. Bir nechta fayl
 * bo'lganda «yuklangan format» faqat hammasi bir xil bo'lsa aniq: MP3 bilan
 * M4A ni birlashtirsak, natijani qaysi biriga o'xshatish kerak? Ikkalasiga
 * ham to'g'ri javob yo'q, shuning uchun boshqa formatdagi fayl **qo'shilmaydi**
 * va sababi aytiladi. Bir xil formatdagi, lekin chastotasi yoki kanal soni
 * boshqa fayllar esa qo'shiladi ([AudioMerger] ularni moslashtiradi).
 *
 * Har bir fayl qo'shilganda darhol ochiladi (dekodlanadi): xato bo'lsa
 * foydalanuvchi buni birlashtirish paytida emas, shu zahoti bilib oladi.
 */
class MergeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)
    private val opener = AudioOpener(store, Build.VERSION.SDK_INT)
    private val exporter = FormatPreservingExporter(Build.VERSION.SDK_INT, AndroidAudioEncoders::open)

    private var nextId = 1L

    private val _state = MutableStateFlow(MergeUiState())
    val state: StateFlow<MergeUiState> = _state.asStateFlow()

    /** Tanlangan fayllarni ketma-ket ochib, ro'yxat oxiriga qo'shadi. */
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = MergeBusy.ADDING,
                    addingCurrent = 0,
                    addingTotal = uris.size,
                    failures = emptyList(),
                    error = null,
                    result = null,
                    savedName = null,
                )
            }
            for ((index, uri) in uris.withIndex()) {
                _state.update { it.copy(addingCurrent = index + 1) }
                val context = getApplication<Application>()
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { opener.open(context, uri) }
                }
                when (val opened = outcome.getOrNull()) {
                    is OpenResult.Opened -> accept(opened)
                    is OpenResult.Refused -> {
                        ErrorLog.info("merge.add", "Fayl qo'shilmadi: ${opened.reason}, format: ${opened.formatName}")
                        addFailure(
                            MergeFailure(
                                fileName = opened.displayName,
                                reason = opened.reason,
                                formatName = opened.formatName,
                            ),
                        )
                    }
                    null -> {
                        ErrorLog.error("merge.add", "Fayl ochishda kutilmagan xato", outcome.exceptionOrNull())
                        addFailure(
                            MergeFailure(
                                fileName = opener.displayName(context, uri),
                                reason = ImportFailure.READ_FAILED,
                            ),
                        )
                    }
                }
            }
            _state.update { it.copy(busy = MergeBusy.NONE) }
        }
    }

    /** Faylni [delta] qadamga ko'chiradi: -1 — yuqoriga, +1 — pastga. */
    fun move(id: Long, delta: Int) {
        if (_state.value.isBusy) return
        _state.update { current ->
            val from = current.items.indexOfFirst { it.id == id }
            val to = from + delta
            if (from < 0 || to < 0 || to >= current.items.size) {
                current
            } else {
                val list = current.items.toMutableList()
                val moved = list[from]
                list[from] = list[to]
                list[to] = moved
                current.copy(items = list, result = null, savedName = null)
            }
        }
    }

    fun remove(id: Long) {
        if (_state.value.isBusy) return
        val item = _state.value.items.firstOrNull { it.id == id } ?: return
        opener.discard(item.source, item.wav)
        _state.update {
            it.copy(items = it.items.filter { other -> other.id != id }, result = null, savedName = null)
        }
    }

    /**
     * Ro'yxatdagi fayllarni tartib bilan birlashtiradi va natijani **asl
     * formatda** yozadi.
     */
    fun merge() {
        val current = _state.value
        if (current.isBusy) return
        if (current.items.size < 2) {
            _state.update { it.copy(error = MergeError.NEED_TWO) }
            return
        }

        val items = current.items
        // Yo'qotishli formatda bit tezligi eng yuqori manbaniki olinadi:
        // aks holda sifatli fayl past tezlik tufayli buzilardi.
        val bitrate = items.mapNotNull { it.format.bitrate }.maxOrNull()
        val format = items.first().format.copy(bitrate = bitrate)

        viewModelScope.launch {
            _state.update {
                it.copy(busy = MergeBusy.MERGING, progress = 0f, error = null, result = null, savedName = null)
            }
            val merged = store.newEditFile(TAG_MERGED)
            val output = store.newOutputFile(OUTPUT_NAME, "", format.container.extension)

            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    var lastPercent = -1
                    AudioMerger.merge(items.map { it.wav }, merged, store.editsDirectory) { progress ->
                        // Ekran har bir foizda bir marta yangilanadi, har bir bo'lakda emas.
                        val percent = (progress * 100).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                    exporter.export(merged, format, output)
                }
            }

            runCatching { merged.delete() }

            val exported = outcome.getOrNull()
            if (exported is ExportOutcome.Done && output.exists()) {
                _state.update {
                    it.copy(
                        busy = MergeBusy.NONE,
                        result = ResultFile(
                            path = output.absolutePath,
                            name = output.name,
                            mimeType = StrictFormat.mimeType(format.container),
                            sizeBytes = output.length(),
                        ),
                    )
                }
            } else {
                runCatching { output.parentFile?.deleteRecursively() }
                ErrorLog.error(
                    "merge.run",
                    "Birlashtirib bo'lmadi: ${items.size} fayl, format: ${StrictFormat.label(format)}, " +
                        "natija: ${exported?.javaClass?.simpleName}",
                    outcome.exceptionOrNull(),
                )
                // Birlashtirish o'tdi-yu, asl formatda yozib bo'lmadi — bu boshqa xato.
                _state.update {
                    it.copy(
                        busy = MergeBusy.NONE,
                        error = if (outcome.isSuccess) MergeError.EXPORT_FAILED else MergeError.MERGE_FAILED,
                    )
                }
            }
        }
    }

    /** Tayyor faylni foydalanuvchi tanlagan joyga ([uri]) nusxalaydi. */
    fun saveTo(uri: Uri) {
        val result = _state.value.result ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = MergeBusy.SAVING, error = null) }
            val ok = withContext(Dispatchers.IO) {
                ResultFiles.copyTo(getApplication<Application>(), File(result.path), uri)
            }
            if (!ok) ErrorLog.error("merge.save", "Faylni tanlangan joyga yozib bo'lmadi")
            _state.update {
                it.copy(
                    busy = MergeBusy.NONE,
                    savedName = if (ok) result.name else null,
                    error = if (ok) null else MergeError.SAVE_FAILED,
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearFailures() = _state.update { it.copy(failures = emptyList()) }
    fun clearSaved() = _state.update { it.copy(savedName = null) }

    // --- yordamchi ---

    /** Ochilgan faylni ro'yxatga qo'shadi — agar formati ro'yxatdagilarga mos bo'lsa. */
    private fun accept(opened: OpenResult.Opened) {
        val first = _state.value.items.firstOrNull()
        if (first != null &&
            (first.format.container != opened.origin.container || first.format.codec != opened.origin.codec)
        ) {
            opener.discard(opened.source, opened.wav)
            addFailure(
                MergeFailure(
                    fileName = opened.displayName,
                    reason = null,
                    formatName = StrictFormat.label(opened.origin),
                    listFormatName = StrictFormat.label(first.format),
                ),
            )
            return
        }

        val item = MergeItem(
            id = nextId++,
            name = opened.displayName,
            format = opened.origin,
            durationMs = opened.durationMs,
            source = opened.source,
            wav = opened.wav,
        )
        _state.update { it.copy(items = it.items + item, result = null, savedName = null) }
    }

    private fun addFailure(failure: MergeFailure) {
        _state.update { it.copy(failures = it.failures + failure) }
    }

    override fun onCleared() {
        super.onCleared()
        // Ish fayllari o'chadi; tayyor natijalar qoladi (ulashilgan fayl keyinroq o'qilishi mumkin).
        store.clearWork()
    }

    private companion object {
        const val SCOPE = "birlashtirish"
        const val TAG_MERGED = "birlashgan"
        const val OUTPUT_NAME = "birlashtirilgan"
    }
}
