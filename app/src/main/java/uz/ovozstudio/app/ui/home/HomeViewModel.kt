package uz.ovozstudio.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import uz.ovozstudio.app.media.Recording
import uz.ovozstudio.app.media.RecordingStore

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    init {
        refresh()
    }

    /** Fayl ro'yxatini qayta o'qiydi — yozib olish yoki tahrirlashdan qaytganda chaqiriladi. */
    fun refresh() {
        _recordings.value = store.list()
    }

    fun delete(recording: Recording) {
        store.delete(recording.file)
        refresh()
    }
}
