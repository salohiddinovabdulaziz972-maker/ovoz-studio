package uz.ovozstudio.app.ui.pdf

import android.app.Application
import android.net.Uri
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
import uz.ovozstudio.app.media.pdf.PageRange
import uz.ovozstudio.app.media.pdf.PdfNoPermissionException
import uz.ovozstudio.app.media.pdf.PdfPageTools
import uz.ovozstudio.app.media.pdf.PdfPasswordException
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.MediaSaver
import uz.ovozstudio.app.util.RecentFiles
import uz.ovozstudio.app.util.UriImport
import java.io.File
import java.io.IOException

/**
 * Ekran qaysi amalni bajaradi.
 *
 * Audiodagi kabi: ikki niyat — ikki ekran. «Kesib olish» — tanlangan
 * sahifalardan yangi PDF; «o'chirish» — tanlangan sahifalarsiz yangi PDF.
 * Asl fayl ikkala holatda ham o'zgarmaydi.
 */
enum class PdfMode(val suffix: String) {
    CUT("-ajratilgan"),
    DELETE("-tahrirlangan"),
}

enum class PdfError {
    /** Sahifalar kiritilmagan. */
    RANGE_EMPTY,

    /** Yozuv tushunarsiz (harf, ortiqcha belgi). */
    RANGE_SYNTAX,

    /** Sahifa hujjatda yo'q. */
    RANGE_OUT_OF_RANGE,

    /** Oraliq teskari yozilgan (`5-3`). */
    RANGE_REVERSED,

    /** Butun hujjatni o'chirib bo'lmaydi. */
    DELETE_ALL,

    /** PDF ochish uchun parol talab qiladi. */
    PASSWORD,

    /** Muallif sahifalarni ajratishni taqiqlagan. */
    PROTECTED,

    /** Fayl juda katta: qurilma xotirasi yetmadi. */
    TOO_LARGE,

    /** Fayl ochilmadi yoki natijani yozib bo'lmadi (buzuq PDF, joy yetmadi). */
    FAILED,

    /** Tayyor faylni tanlangan joyga nusxalab bo'lmadi. */
    SAVE_FAILED,
}

enum class PdfBusy { NONE, OPENING, WORKING, SAVING }

data class PdfUiState(
    val fileName: String = "",
    /** 0 — fayl hali ochilmagan. */
    val pageCount: Int = 0,
    val pagesText: String = "",
    val busy: PdfBusy = PdfBusy.NONE,
    /** Natijadagi sahifalar soni. */
    val resultPages: Int = 0,
    /** [busy] paytidagi jarayon foizi (0…1); `null` — noma'lum yoki hali boshlanmagan. */
    val progress: Float? = null,
    val result: ResultFile? = null,
    val savedName: String? = null,
    /** Saqlangan faylning qurilmadagi manzili — ovozda aytiladi. */
    val savedTo: String? = null,
    /** Saqlangan faylning yo'li — papkani ochish tugmasi uchun. */
    val savedPath: String? = null,
    val error: PdfError? = null,
    /** Sahifalar ro'yxatidagi muammoli bo'lak (xabarda ko'rsatiladi). */
    val errorToken: String = "",
    /** Ilovada ilgari ochilgan fayllar nomi (eng yangisi birinchi). */
    val recentFiles: List<String> = emptyList(),
    /** Shu ro'yxat ustidagi qidiruv so'zi. */
    val recentQuery: String = "",
) {
    val isOpen: Boolean get() = pageCount > 0
    val isBusy: Boolean get() = busy != PdfBusy.NONE
}

/**
 * PDF sahifalarini kesib olish va o'chirish ekranining holati.
 *
 * Tartib: PDF tanlanadi → nusxalanadi va sahifalar soni o'qiladi →
 * foydalanuvchi sahifalarni yozadi (`3, 5-8`) → yangi PDF yasaladi →
 * saqlanadi yoki ulashiladi. Og'ir ish (PDF ochish, yozish) fon oqimida.
 */
class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)
    private val recent = RecentFiles(application)

    private var sourceFile: File? = null
    private var baseName = ""

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    /**
     * Tanlangan PDF ni ochadi.
     *
     * Fayl — qurilmadagi haqiqiy fayl ([File]), nusxa ko'chirilmaydi. Ilgari
     * fayl `WorkStore` ga ko'chirilar va ekran yopilganda o'chib ketardi;
     * endi fayl o'z joyida turadi, ishlov berilmaydi (manba hech qachon
     * o'zgartirilmaydi) va natija alohida fayl bo'lib saqlanadi.
     */
    fun open(file: File) = openWith { UriImport.Copied(file, file.name) }

    /**
     * Tizim tanlagichidan kelgan PDF ni ochadi (Android 11 dan boshlab boshqa
     * ilovalar yaratgan PDF larni faqat shu yo'l bilan ko'rish mumkin). Fayl
     * ish papkasiga nusxalanadi; asl fayl o'zgarmaydi.
     */
    fun open(uri: Uri) {
        val context = getApplication<Application>()
        openWith { UriImport.copy(context, store, uri, "pdf") }
    }

    private fun openWith(prepare: () -> UriImport.Copied?) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = PdfBusy.OPENING,
                    error = null,
                    errorToken = "",
                    savedName = null,
                    savedTo = null,
                    savedPath = null,
                )
            }
            val context = getApplication<Application>()
            val prepared = withContext(Dispatchers.IO) { runCatching(prepare).getOrNull() }
            if (prepared == null) {
                _state.update { it.copy(busy = PdfBusy.NONE, error = PdfError.FAILED) }
                return@launch
            }
            val file = prepared.file
            val outcome = withContext(Dispatchers.IO) {
                runCatching { PdfPageTools.pageCount(context, file) }
            }

            val count = outcome.getOrNull()
            if (count == null || count <= 0) {
                val failure = outcome.exceptionOrNull()
                if (failure != null) ErrorLog.error("pdf.open", "PDF ochilmadi", failure)
                // Nusxa ilovaniki bo'lsa — tashlanadi; haqiqiy fayl joyida qoladi.
                store.deleteIfOwned(file)
                _state.update {
                    it.copy(busy = PdfBusy.NONE, error = errorOf(failure))
                }
                return@launch
            }

            val previous = sourceFile
            sourceFile = file
            if (previous != null && previous != file) store.deleteIfOwned(previous)
            baseName = prepared.displayName.substringBeforeLast('.').ifBlank { DEFAULT_NAME }
            _state.update {
                it.copy(
                    busy = PdfBusy.NONE,
                    fileName = baseName,
                    pageCount = count,
                    pagesText = "",
                    result = null,
                    resultPages = 0,
                    savedName = null,
                    error = null,
                    errorToken = "",
                )
            }
            // Muvaffaqiyatli ochilgandan keyingina yozamiz: yarim yo'lda
            // uzilgan urinish ro'yxatga tushib qolsa, foydalanuvchi
            // ochib bo'lmaydigan fayl nomini ko'rib qolardi.
            recent.remember(baseName)
            refreshRecent()
        }
    }

    fun refreshRecent() {
        _state.update { it.copy(recentFiles = recent.list().map { entry -> entry.name }) }
    }

    fun searchRecent(query: String) {
        val needle = query.trim()
        val matches = if (needle.isEmpty()) recent.list() else recent.find(needle)
        _state.update {
            it.copy(recentQuery = query, recentFiles = matches.map { entry -> entry.name })
        }
    }

    fun clearRecent() {
        recent.clear()
        _state.update { it.copy(recentFiles = emptyList(), recentQuery = "") }
    }

    fun setPages(text: String) = _state.update { it.copy(pagesText = text) }

    fun clearError() = _state.update { it.copy(error = null, errorToken = "") }
    fun clearSaved() = _state.update { it.copy(savedName = null, savedTo = null, savedPath = null) }

    /** Tanlangan sahifalar bo'yicha yangi PDF yasaydi. */
    fun apply(mode: PdfMode) {
        val current = _state.value
        val source = sourceFile ?: return
        if (current.isBusy || !current.isOpen) return

        val selected = when (val parsed = PageRange.parse(current.pagesText, current.pageCount)) {
            is PageRange.Parsed.Pages -> parsed.pages
            is PageRange.Parsed.Invalid -> {
                _state.update {
                    it.copy(error = rangeError(parsed.problem), errorToken = parsed.token)
                }
                return
            }
        }
        // Butun hujjatni o'chirish — bo'sh fayl: buni oldindan aytamiz,
        // aks holda foydalanuvchi umumiy «bo'lmadi» xatosini olardi.
        if (mode == PdfMode.DELETE && selected.size >= current.pageCount) {
            _state.update { it.copy(error = PdfError.DELETE_ALL, errorToken = "") }
            return
        }

        val resultPages = if (mode == PdfMode.CUT) selected.size else current.pageCount - selected.size

        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = PdfBusy.WORKING,
                    progress = null,
                    error = null,
                    errorToken = "",
                    result = null,
                    savedName = null,
                )
            }
            val context = getApplication<Application>()
            val output = store.newOutputFile(baseName, mode.suffix, "pdf")
            val outcome = withContext(Dispatchers.IO) {
                var lastPercent = -1
                val onProgress = { done: Int, total: Int ->
                    val fraction = if (total > 0) done.toFloat() / total else 0f
                    val percent = (fraction * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.update { it.copy(progress = fraction) }
                    }
                }
                runCatching {
                    if (mode == PdfMode.CUT) {
                        PdfPageTools.extract(context, source, selected, output, onProgress)
                    } else {
                        PdfPageTools.delete(context, source, selected, output, onProgress)
                    }
                }
            }

            if (outcome.isSuccess && output.exists()) {
                store.markOutputReady(output)
                _state.update {
                    it.copy(
                        busy = PdfBusy.NONE,
                        progress = null,
                        resultPages = resultPages,
                        result = ResultFile(
                            path = output.absolutePath,
                            name = output.name,
                            mimeType = PDF_MIME,
                            sizeBytes = output.length(),
                        ),
                    )
                }
            } else {
                runCatching { output.parentFile?.deleteRecursively() }
                val failure = outcome.exceptionOrNull()
                ErrorLog.error("pdf.${mode.name.lowercase()}", "PDF sahifalarini yasab bo'lmadi", failure)
                _state.update { it.copy(busy = PdfBusy.NONE, progress = null, error = errorOf(failure)) }
            }
        }
    }

    /**
     * Natijani qurilmaning umumiy papkasiga **bitta bosishda** yozadi.
     * Alohida «Fayl sifatida saqlash» oynasi yo'q.
     */
    fun save() {
        val result = _state.value.result ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = PdfBusy.SAVING, error = null, errorToken = "") }
            val outcome = withContext(Dispatchers.IO) {
                MediaSaver.save(
                    getApplication(),
                    File(result.path),
                    result.name,
                    PDF_MIME,
                    isAudio = false,
                )
            }
            // Sabab `MediaSaver` ichida jurnalga yoziladi — bu yerda
            // takrorlanmaydi, aks holda bitta xato ikki marta tushardi.
            _state.update {
                when (outcome) {
                    is MediaSaver.Result.Saved -> it.copy(
                        busy = PdfBusy.NONE,
                        savedName = result.name,
                        savedTo = outcome.display,
                        savedPath = outcome.display,
                        error = null,
                    )
                    is MediaSaver.Result.Failed -> it.copy(
                        busy = PdfBusy.NONE,
                        savedName = null,
                        savedTo = null,
                        savedPath = null,
                        error = PdfError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    // --- yordamchi ---

    private fun rangeError(problem: PageRange.Problem): PdfError = when (problem) {
        PageRange.Problem.EMPTY -> PdfError.RANGE_EMPTY
        PageRange.Problem.SYNTAX -> PdfError.RANGE_SYNTAX
        PageRange.Problem.OUT_OF_RANGE -> PdfError.RANGE_OUT_OF_RANGE
        PageRange.Problem.REVERSED -> PdfError.RANGE_REVERSED
    }

    /** Xato turini foydalanuvchiga tushunarli sababga aylantiradi. */
    private fun errorOf(error: Throwable?): PdfError = when (error) {
        is PdfPasswordException -> PdfError.PASSWORD
        is PdfNoPermissionException -> PdfError.PROTECTED
        is OutOfMemoryError -> PdfError.TOO_LARGE
        else -> PdfError.FAILED
    }

    override fun onCleared() {
        super.onCleared()
        // Tizim tanlagichidan kelgan nusxalar o'chadi. Qurilmadagi haqiqiy
        // fayl bu papkada emas, shuning uchun unga tegilmaydi. Tayyor
        // natijalar qoladi — ularni `WorkStore.sweep` eskirgach tozalaydi.
        store.clearWork()
    }

    private companion object {
        const val SCOPE = "pdf"
        const val DEFAULT_NAME = "hujjat"
        const val PDF_MIME = "application/pdf"
    }
}
