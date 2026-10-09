package uz.ovozstudio.app.ui.pdf

import android.app.Application
import android.content.Context
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
import uz.ovozstudio.app.util.RecentFiles
import uz.ovozstudio.app.media.pdf.PdfMergeSource
import uz.ovozstudio.app.media.pdf.PdfMerger
import uz.ovozstudio.app.media.pdf.PdfNoPermissionException
import uz.ovozstudio.app.media.pdf.PdfPageTools
import uz.ovozstudio.app.media.pdf.PdfPasswordException
import uz.ovozstudio.app.ui.common.ResultFile
import uz.ovozstudio.app.util.MediaSaver
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

    /** Tanlangan fayllarni ro'yxatga qo'shadi (tartib: tanlangan tartibda). */
    fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty() || _state.value.isBusy) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            _state.update {
                it.copy(
                    busy = PdfMergeBusy.ADDING,
                    progress = null,
                    addingCurrent = 0,
                    addingTotal = uris.size,
                    error = null,
                    savedName = null,
                    savedTo = null,
                )
            }

            var nextId = (_state.value.items.maxOfOrNull { it.id } ?: 0L) + 1
            val accepted = mutableListOf<PdfMergeItem>()
            val rejected = mutableListOf<PdfMergeFailure>()

            for ((index, uri) in uris.withIndex()) {
                _state.update { it.copy(addingCurrent = index + 1) }
                val name = displayName(context, uri).ifBlank { DEFAULT_NAME }
                val copy = store.newSourceFile("pdf")

                val count = withContext(Dispatchers.IO) {
                    runCatching {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IOException("Fayl ochilmadi")
                        input.use { source -> copy.outputStream().use { sink -> source.copyTo(sink, COPY_BUFFER) } }
                        PdfPageTools.pageCount(context, copy)
                    }
                }

                val pages = count.getOrNull()
                if (pages == null || pages <= 0) {
                    runCatching { copy.delete() }
                    val failure = count.exceptionOrNull()
                    if (failure != null) ErrorLog.error(LOG_TAG_OPEN, "PDF birlashtirishga qo'shilmadi: $name", failure)
                    rejected += PdfMergeFailure(
                        name.substringBeforeLast('.').ifBlank { DEFAULT_NAME },
                        reasonOf(context, failure),
                    )
                } else {
                    accepted += PdfMergeItem(
                        id = nextId++,
                        name = name.substringBeforeLast('.').ifBlank { DEFAULT_NAME },
                        pageCount = pages,
                        source = copy,
                    )
                }
            }

            _state.update {
                it.copy(
                    items = it.items + accepted,
                    busy = PdfMergeBusy.NONE,
                    addingCurrent = 0,
                    addingTotal = 0,
                    failures = rejected,
                    revision = it.revision + 1,
                )
            }

            // Faqat qabul qilingan fayllar eslab qolinadi: ochilmagan fayl
            // ro'yxatda turib, keyin yana xato bersa ro'yxat ishonchsiz
            // bo'lib qolardi.
            accepted.forEach { item -> recent.remember(item.name) }
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

    /** Faylni ro'yxatdan olib tashlaydi va uning nusxasini o'chiradi. */
    fun remove(id: Long) {
        _state.update { current ->
            val item = current.items.firstOrNull { it.id == id } ?: return@update current
            runCatching { item.source.delete() }
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
    fun clearSaved() = _state.update { it.copy(savedName = null, savedTo = null) }

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

    private fun displayName(context: Application, uri: Uri): String {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: ""
            }
        }
        return ""
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
        const val COPY_BUFFER = 128 * 1024
        const val DEFAULT_NAME = "PDF"
        const val MERGED_NAME = "Birlashtirilgan"
        const val MERGE_SUFFIX = "-birlashtirilgan"
        const val PDF_MIME = "application/pdf"
    }
}
