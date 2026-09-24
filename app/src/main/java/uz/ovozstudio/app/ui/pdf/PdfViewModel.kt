package uz.ovozstudio.app.ui.pdf

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
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore
import uz.ovozstudio.app.media.pdf.PageRange
import uz.ovozstudio.app.media.pdf.PdfNoPermissionException
import uz.ovozstudio.app.media.pdf.PdfPageTools
import uz.ovozstudio.app.media.pdf.PdfPasswordException
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.ResultFiles
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
    val error: PdfError? = null,
    /** Sahifalar ro'yxatidagi muammoli bo'lak (xabarda ko'rsatiladi). */
    val errorToken: String = "",
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

    private var sourceFile: File? = null
    private var baseName = ""

    private val _state = MutableStateFlow(PdfUiState())
    val state: StateFlow<PdfUiState> = _state.asStateFlow()

    /** Tanlangan PDF ni ochadi. Eski fayl (bo'lsa) yangisi ochilgandan keyin tashlanadi. */
    fun open(uri: Uri) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update {
                it.copy(busy = PdfBusy.OPENING, error = null, errorToken = "", savedName = null)
            }
            val context = getApplication<Application>()
            val copy = store.newSourceFile("pdf")
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    // Nusxa majburiy: tizim bergan havola doimiy emas, PDF kutubxonasi
                    // esa fayl yo'li bilan ishlaydi.
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Fayl ochilmadi")
                    input.use { source -> copy.outputStream().use { sink -> source.copyTo(sink, COPY_BUFFER) } }
                    PdfPageTools.pageCount(context, copy)
                }
            }

            val count = outcome.getOrNull()
            if (count == null || count <= 0) {
                runCatching { copy.delete() }
                val failure = outcome.exceptionOrNull()
                if (failure != null) ErrorLog.error("pdf.open", "PDF ochilmadi", failure)
                _state.update {
                    it.copy(busy = PdfBusy.NONE, error = errorOf(failure))
                }
                return@launch
            }

            val previous = sourceFile
            sourceFile = copy
            if (previous != null) runCatching { previous.delete() }
            baseName = displayName(context, uri).substringBeforeLast('.').ifBlank { DEFAULT_NAME }
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
        }
    }

    fun setPages(text: String) = _state.update { it.copy(pagesText = text) }

    fun clearError() = _state.update { it.copy(error = null, errorToken = "") }
    fun clearSaved() = _state.update { it.copy(savedName = null) }

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

    /** Tayyor faylni foydalanuvchi tanlagan joyga ([uri]) nusxalaydi. */
    fun saveTo(uri: Uri) {
        val result = _state.value.result ?: return
        if (_state.value.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = PdfBusy.SAVING, error = null, errorToken = "") }
            val ok = withContext(Dispatchers.IO) {
                ResultFiles.copyTo(getApplication<Application>(), File(result.path), uri)
            }
            if (!ok) ErrorLog.error("pdf.save", "PDF ni tanlangan joyga yozib bo'lmadi")
            _state.update {
                it.copy(
                    busy = PdfBusy.NONE,
                    savedName = if (ok) result.name else null,
                    error = if (ok) null else PdfError.SAVE_FAILED,
                )
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
        // Manba nusxasi o'chadi; tayyor natijalar qoladi (ulashilgan fayl keyinroq o'qilishi mumkin).
        store.clearWork()
    }

    private companion object {
        const val SCOPE = "pdf"
        const val DEFAULT_NAME = "hujjat"
        const val PDF_MIME = "application/pdf"
        const val COPY_BUFFER = 256 * 1024
    }
}
