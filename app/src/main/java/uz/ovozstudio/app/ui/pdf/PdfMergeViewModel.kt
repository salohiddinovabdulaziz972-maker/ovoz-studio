package uz.ovozstudio.app.ui.pdf

import android.app.Application
import android.content.Context
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
import uz.ovozstudio.app.R
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore
import uz.ovozstudio.app.util.RecentFiles
import uz.ovozstudio.app.media.pdf.PdfMergeSource
import uz.ovozstudio.app.media.pdf.PdfMerger
import uz.ovozstudio.app.media.pdf.PdfNoPermissionException
import uz.ovozstudio.app.media.pdf.PdfPageTools
import uz.ovozstudio.app.media.pdf.PdfPasswordException
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.MediaSaver
import uz.ovozstudio.app.util.UriImport
import java.io.File
import java.io.IOException

/** Ro'yxatdagi bitta PDF: ochilgan va birlashtirishga tayyor. */
data class PdfMergeItem(
    val id: Long,
    val name: String,
    val pageCount: Int,
    val source: File,
)

/** Ro'yxatga qo'shilmagan fayl va sababi. */
data class PdfMergeFailure(
    val fileName: String,
    val text: String,
)

enum class PdfMergeError {
    /** Birlashtirish uchun kamida ikkita fayl kerak. */
    NEED_TWO,

    /** Birlashtirishning o'zi bajarilmadi (o'qish/yozish xatosi, joy yetmadi). */
    MERGE_FAILED,

    /** Tayyor faylni tanlangan joyga nusxalab bo'lmadi. */
    SAVE_FAILED,
}

/** Hozir bajarilayotgan uzoq ish. */
enum class PdfMergeBusy { NONE, ADDING, MERGING, SAVING }

data class PdfMergeUiState(
    val items: List<PdfMergeItem> = emptyList(),
    val busy: PdfMergeBusy = PdfMergeBusy.NONE,
    /** Fayl qo'shilayotganda: nechanchi fayl / nechta. */
    val addingCurrent: Int = 0,
    val addingTotal: Int = 0,
    /** Uzoq ish foizi (0…1); `null` — noma'lum. */
    /** Birlashtirish jarayoni: `(ko'chirilgan, jami)` sahifa. */
    val progress: Pair<Int, Int>? = null,
    val failures: List<PdfMergeFailure> = emptyList(),
    val result: ResultFile? = null,
    val savedName: String? = null,
    /** Saqlangan faylning qurilmadagi manzili — ovozda aytiladi. */
    val savedTo: String? = null,
    /** Saqlangan faylning yo'li — papkani ochish tugmasi uchun. */
    val savedPath: String? = null,
    val error: PdfMergeError? = null,
    /**
     * Har bir qo'shish urinishida oshadi.
     *
     * Ekran e'lonni shu songa bog'laydi: fayl rad etilib ro'yxat o'zgarmasa,
     * son o'zgarmaydi va e'lon umuman bo'lmay qolardi.
     */
    val revision: Int = 0,
    /** Oxirgi qo'shilgan PDF lar nomi (eng yangisi birinchi). */
    val recentFiles: List<String> = emptyList(),
    /** Oxirgi fayllar ichidan qidiruv so'zi. */
    val recentQuery: String = "",
) {
    val isBusy: Boolean get() = busy != PdfMergeBusy.NONE

    /** Natijadagi sahifalar soni. */
    val totalPages: Int get() = items.sumOf { it.pageCount }

    /** Birlashtirish jarayoni 0..1 ko'rinishida — ekran shu bilan chiziq chizadi. */
    val mergeProgress: Float?
        get() = progress?.let { (done, total) -> if (total > 0) done.toFloat() / total else null }

    /** Fayllar yuklanayotgandagi jarayon: `(ochilgan, jami)`. */
    val addingProgress: Float?
        get() = if (addingTotal > 0) addingCurrent.toFloat() / addingTotal else null
}

/**
 * Bir nechta PDF ni bitta faylga birlashtirish ekranining holati.
 *
 * Tartib: fayllar tanlanadi → har biri darhol ochiladi (sahifalar soni
 * o'qiladi) → tartib belgilanadi (yuqoriga / pastga) → birlashtiriladi →
 * saqlanadi yoki ulashiladi. Og'ir ish (PDF ochish, yozish) fon oqimida.
 *
 * Fayl qo'shilganda darhol ochiladi: xato bo'lsa foydalanuvchi buni
 * birlashtirish paytida emas, shu zahoti bilib oladi va ro'yxatdan
 * qaysi fayl tushib qolganini ko'radi.
 */
class PdfMergeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)

    /** Oxirgi ochilgan PDF lar — papkalar ichida yurish o'rniga bir bosish. */
    private val recent = RecentFiles(application)

    private val _state = MutableStateFlow(PdfMergeUiState())
    val state: StateFlow<PdfMergeUiState> = _state.asStateFlow()

    /**
     * Tanlangan PDF ni ro'yxatga qo'shadi.
     *
     * Fayl — qurilmadagi haqiqiy fayl ([File]), nusxa ko'chirilmaydi. Ilgari
     * fayl `WorkStore` ga ko'chirilar edi va ekran yopilganda o'sha nusxa
     * o'chib ketardi — birlashtirish o'rtasida manba yo'qolsa natija buzuq
     * chiqardi (ekranda xato bo'lib ko'rinardi). Endi fayl o'z joyida turadi
     * va uni hech kim o'chirmaydi; foydalanuvchi tanlagan fayli shu yerda
     * qoladi va boshqa ilovada ham ochiladi. Papkadagi nom o'zgarmaydi,
     * natija esa alohida fayl bo'lib saqlanadi.
     */
    fun addFile(file: File) = addWith { UriImport.Copied(file, file.name) }

    /**
     * Tizim tanlagichidan kelgan PDF ni qo'shadi (Android 11 dan boshlab
     * boshqa ilovalar yaratgan PDF larni faqat shu yo'l bilan ko'rish mumkin).
     * Fayl ish papkasiga nusxalanadi; asl fayl o'zgarmaydi.
     */
    fun addUri(uri: Uri) {
        val context = getApplication<Application>()
        addWith { UriImport.copy(context, store, uri, "pdf") }
    }

    private fun addWith(prepare: () -> UriImport.Copied?) {
        if (_state.value.isBusy) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update {
                it.copy(
                    busy = PdfMergeBusy.ADDING,
                    progress = null,
                    addingCurrent = 1,
                    addingTotal = 1,
                    error = null,
                    savedName = null,
                    savedTo = null,
                    savedPath = null,
                )
            }

            val prepared = withContext(Dispatchers.IO) { runCatching(prepare).getOrNull() }
            val file = prepared?.file
            val name = (prepared?.displayName ?: "").ifBlank { DEFAULT_NAME }
            val pages = withContext(Dispatchers.IO) {
                if (file == null) {
                    Result.failure<Int>(IOException("Fayl nusxalanmadi"))
                } else {
                    runCatching { PdfPageTools.pageCount(context, file) }
                }
            }

            val count = pages.getOrNull()
            val state = if (file == null || count == null || count <= 0) {
                val failure = pages.exceptionOrNull()
                if (failure != null) ErrorLog.error(LOG_TAG_OPEN, "PDF birlashtirishga qo'shilmadi", failure)
                // Nusxa ilovaniki bo'lsa — tashlanadi; haqiqiy fayl joyida qoladi.
                if (file != null) store.deleteIfOwned(file)
                _state.value.copy(
                    busy = PdfMergeBusy.NONE,
                    addingCurrent = 0,
                    addingTotal = 0,
                    failures = listOf(
                        PdfMergeFailure(
                            name.substringBeforeLast('.').ifBlank { DEFAULT_NAME },
                            reasonOf(context, failure),
                        ),
                    ),
                )
            } else {
                val item = PdfMergeItem(
                    id = (_state.value.items.maxOfOrNull { it.id } ?: 0L) + 1,
                    name = name.substringBeforeLast('.').ifBlank { DEFAULT_NAME },
                    pageCount = count,
                    source = file,
                )
                recent.remember(item.name)
                _state.value.copy(
                    items = _state.value.items + item,
                    busy = PdfMergeBusy.NONE,
                    addingCurrent = 0,
                    addingTotal = 0,
                    failures = emptyList(),
                    revision = _state.value.revision + 1,
                )
            }
            _state.value = state
            refreshRecent()
        }
    }

    /** Oxirgi qo'shilgan PDF lar ro'yxatini holatga yuklaydi. */
    fun refreshRecent() {
        _state.update { it.copy(recentFiles = recent.list().map { entry -> entry.name }) }
    }

    /** Ro'yxat ichidan nom bo'yicha filtrlaydi (bo'sh so'z — butun ro'yxat). */
    fun searchRecent(query: String) {
        val needle = query.trim()
        val matches = if (needle.isEmpty()) recent.list() else recent.find(needle)
        _state.update {
            it.copy(recentQuery = query, recentFiles = matches.map { entry -> entry.name })
        }
    }

    /** Ro'yxatni tozalaydi — fayllar o'zi o'chirilmaydi, faqat yodda qolgani. */
    fun clearRecent() {
        recent.clear()
        _state.update { it.copy(recentFiles = emptyList(), recentQuery = "") }
    }

    /** Faylni ro'yxatda yuqoriga yoki pastga suradi. */
    fun move(id: Long, delta: Int) {
        _state.update { current ->
            val list = current.items.toMutableList()
            val from = list.indexOfFirst { it.id == id }
            if (from < 0) return@update current
            val to = (from + delta).coerceIn(0, list.lastIndex)
            if (to == from) return@update current
            list.add(to, list.removeAt(from))
            current.copy(items = list, revision = current.revision + 1)
        }
    }

    /**
     * Faylni ro'yxatdan olib tashlaydi. Tizim tanlagichidan kelgan nusxa
     * (ilovaniki) o'chadi; qurilmadagi haqiqiy fayl esa joyida qoladi —
     * oldingi versiyada u ham o'chib ketardi.
     */
    fun remove(id: Long) {
        _state.update { current ->
            val item = current.items.firstOrNull { it.id == id } ?: return@update current
            store.deleteIfOwned(item.source)
            current.copy(
                items = current.items.filterNot { it.id == id },
                revision = current.revision + 1,
            )
        }
    }

    /** Ro'yxatdagi fayllarni bitta PDF ga birlashtiradi. */
    fun merge() {
        val current = _state.value
        if (current.isBusy) return
        if (current.items.size < 2) {
            _state.update { it.copy(error = PdfMergeError.NEED_TWO) }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = PdfMergeBusy.MERGING,
                    progress = null,
                    error = null,
                    result = null,
                    savedName = null,
                    savedTo = null,
                )
            }
            val context = getApplication<Application>()
            val base = MERGED_NAME
            val output = store.newOutputFile(base, MERGE_SUFFIX, "pdf")
            val sources = current.items.map { PdfMergeSource(it.source, (1..it.pageCount).toList()) }

            val outcome = withContext(Dispatchers.IO) {
                var lastPercent = -1
                val onProgress = { done: Int, total: Int ->
                    val fraction = if (total > 0) done.toFloat() / total else 0f
                    val percent = (fraction * 100).toInt()
                    if (percent != lastPercent) {
                        lastPercent = percent
                        _state.update { it.copy(progress = done to total) }
                    }
                }
                runCatching { PdfMerger.merge(context, sources, output, onProgress) }
            }

            val failure = outcome.exceptionOrNull()
            if (failure != null || !output.exists()) {
                runCatching { output.parentFile?.deleteRecursively() }
                ErrorLog.error(LOG_TAG_MERGE, "PDF birlashtirilmadi", failure)
                _state.update { it.copy(busy = PdfMergeBusy.NONE, progress = null, error = PdfMergeError.MERGE_FAILED) }
                return@launch
            }

            store.markOutputReady(output)
            _state.update {
                it.copy(
                    busy = PdfMergeBusy.NONE,
                    progress = null,
                    result = ResultFile(
                        path = output.absolutePath,
                        name = output.name,
                        mimeType = PDF_MIME,
                        sizeBytes = output.length(),
                    ),
                    error = null,
                )
            }
        }
    }

    /** Tayyor faylni qurilmadagi «Ovoz Studio» papkasiga saqlaydi. */
    fun save() {
        val current = _state.value
        val result = current.result ?: return
        if (current.isBusy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = PdfMergeBusy.SAVING, error = null) }
            val context = getApplication<Application>()
            val outcome = withContext(Dispatchers.IO) {
                MediaSaver.save(context, File(result.path), result.name, PDF_MIME, isAudio = false)
            }
            // Sabab `MediaSaver` ichida jurnalga yoziladi — bu yerda
            // takrorlanmaydi, aks holda bitta xato ikki marta tushardi.
            _state.update {
                when (outcome) {
                    is MediaSaver.Result.Saved -> it.copy(
                        busy = PdfMergeBusy.NONE,
                        savedName = result.name,
                        savedTo = outcome.display,
                        savedPath = outcome.display,
                        error = null,
                    )
                    is MediaSaver.Result.Failed -> it.copy(
                        busy = PdfMergeBusy.NONE,
                        savedName = null,
                        savedTo = null,
                        error = PdfMergeError.SAVE_FAILED,
                    )
                }
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
    fun clearFailures() = _state.update { it.copy(failures = emptyList()) }
    fun clearSaved() = _state.update { it.copy(savedName = null, savedTo = null, savedPath = null) }

    /**
     * Faylni ochish muvaffaqiyatsizligi sababini tayyor matnga o'giradi.
     *
     * Matn shu yerda yasaladi: ro'yxat fon oqimida yig'iladi, u yerda
     * Compose chaqirib bo'lmaydi. Fayl nomi xabarning boshida turadi.
     */
    private fun reasonOf(context: Context, failure: Throwable?): String = when (failure) {
        is PdfPasswordException -> context.getString(R.string.pdf_error_password)
        is PdfNoPermissionException -> context.getString(R.string.pdf_error_protected)
        is OutOfMemoryError -> context.getString(R.string.pdf_error_too_large)
        else -> context.getString(R.string.pdf_error_failed)
    }

    override fun onCleared() {
        // Ekran yopilganda manba nusxalari qolib ketmasligi kerak.
        // Natijalar qoldiriladi: ulashilgan fayl keyinroq ham o'qilishi mumkin.
        store.clearWork()
    }

    private companion object {
        const val SCOPE = "pdf.merge"
        const val LOG_TAG_OPEN = "pdf.merge.open"
        const val LOG_TAG_MERGE = "pdf.merge.apply"
        const val DEFAULT_NAME = "PDF"
        const val MERGED_NAME = "Birlashtirilgan"
        const val MERGE_SUFFIX = "-birlashtirilgan"
        const val PDF_MIME = "application/pdf"
    }
}
