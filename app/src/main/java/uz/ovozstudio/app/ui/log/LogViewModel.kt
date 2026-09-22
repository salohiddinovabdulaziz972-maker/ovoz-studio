package uz.ovozstudio.app.ui.log

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
import uz.ovozstudio.app.util.ResultFiles
import java.io.File

/** Jurnal ekranidagi xabarlar. Matnni ekran tanlaydi (tilga qarab). */
enum class LogNotice {
    CLEARED,
    SAVED,
    SAVE_FAILED,
    SHARE_FAILED,
}

data class LogUiState(
    val loaded: Boolean = false,
    val entryCount: Int = 0,
    val sizeBytes: Long = 0L,
    /** Jurnalning oxirgi qismi: ekranda ko'rsatish uchun. Butun jurnal — ulashishda. */
    val tail: String = "",
    val notice: LogNotice? = null,
)

/**
 * Xatolar jurnali ekrani.
 *
 * Jurnal — ilova ichida yozilgan matn fayli ([ErrorLog]). Bu ekran uni
 * ko'rsatadi va foydalanuvchiga uch yo'l beradi: ulashish (masalan
 * dasturchiga yuborish), qurilmaga saqlash va tozalash. Ilovaning o'zi
 * jurnalni hech qayerga yubormaydi.
 */
class LogViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WorkStore(application, SCOPE)

    private val _state = MutableStateFlow(LogUiState())
    val state: StateFlow<LogUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** Jurnalni qayta o'qiydi. */
    fun refresh() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) {
                val journal = ErrorLog.journal()
                if (journal == null) {
                    LogUiState(loaded = true)
                } else {
                    LogUiState(
                        loaded = true,
                        entryCount = journal.entryCount(),
                        sizeBytes = journal.sizeBytes(),
                        tail = journal.readTail(TAIL_CHARS),
                    )
                }
            }
            _state.update { snapshot.copy(notice = it.notice) }
        }
    }

    fun clear() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ErrorLog.journal()?.clear() }
            _state.update {
                it.copy(entryCount = 0, sizeBytes = 0L, tail = "", loaded = true, notice = LogNotice.CLEARED)
            }
        }
    }

    /**
     * Butun jurnalni ulashish uchun faylga yozadi. `null` — yozib bo'lmadi.
     * Fayl kichik (eng ko'pi bilan yarim megabayt), shuning uchun asosiy
     * oqimda yoziladi: ulashish oynasi darhol ochilishi kerak.
     */
    fun exportForShare(): File? {
        val journal = ErrorLog.journal() ?: return null
        val target = store.newOutputFile(SHARE_NAME, "", "txt")
        return if (journal.exportTo(target)) target else null
    }

    /** Butun jurnalni foydalanuvchi tanlagan joyga ([uri]) yozadi. */
    fun saveTo(uri: Uri) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val journal = ErrorLog.journal()
                val temp = store.newOutputFile(SHARE_NAME, "", "txt")
                journal != null && journal.exportTo(temp) &&
                    ResultFiles.copyTo(getApplication<Application>(), temp, uri)
            }
            _state.update {
                it.copy(notice = if (ok) LogNotice.SAVED else LogNotice.SAVE_FAILED)
            }
        }
    }

    fun shareFailed() = _state.update { it.copy(notice = LogNotice.SHARE_FAILED) }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    override fun onCleared() {
        super.onCleared()
        store.clearWork()
    }

    companion object {
        private const val SCOPE = "jurnal"
        private const val SHARE_NAME = "ovoz-studio-jurnal"

        /** Ekranda ko'rsatiladigan oxirgi qism: ekran o'quvchi bilan o'qishga ma'qul hajm. */
        private const val TAIL_CHARS = 6_000

        /** «Saqlash» oynasida taklif qilinadigan nom. */
        const val SAVE_NAME = "ovoz-studio-jurnal.txt"
    }
}
