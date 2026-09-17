package uz.ovozstudio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.ovozstudio.app.media.Recording
import uz.ovozstudio.app.media.RecordingStore
import uz.ovozstudio.app.settings.AppSettingsStore

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val settingsStore = AppSettingsStore.inFiles(application.filesDir)

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    /**
     * Soddalashtirilgan rejim yoniqmi.
     *
     * Sozlama bosh ekranda ekranning qurilishida kerak, shuning uchun u shu
     * yerda o'qiladi: sozlamalar ekranidan qaytganda esa [refresh] qayta
     * o'qiydi (foydalanuvchi rejimni o'sha yerda o'zgartirgan bo'ladi).
     */
    private val _simplified = MutableStateFlow(false)
    val simplified: StateFlow<Boolean> = _simplified.asStateFlow()

    init {
        refresh()
    }

    /** Fayl ro'yxatini va sozlamani qayta o'qiydi — boshqa ekrandan qaytganda chaqiriladi. */
    fun refresh() {
        _recordings.value = store.list()
        _simplified.value = runCatching { settingsStore.load().simplified }.getOrDefault(false)
    }

    fun delete(recording: Recording) {
        store.delete(recording.file)
        refresh()
    }
}
