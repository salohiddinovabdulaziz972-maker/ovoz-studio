package uz.ovozstudio.app.ui.record

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.media.AudioRecorderEngine
import uz.ovozstudio.app.media.RecorderConfig
import uz.ovozstudio.app.media.RecordingService
import uz.ovozstudio.app.media.RecordingStore

data class RecordUiState(
    val config: RecorderConfig = RecorderConfig(),
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val level: Float = 0f,
    val markerCount: Int = 0,
    /** Yozuv saqlangandan keyin to'ldiriladi — UI shu faylni ochishni taklif qiladi. */
    val savedPath: String? = null,
    val savedDurationMs: Long = 0L,
    val errorMessage: String? = null,
    /**
     * Fon xizmatini yoqib bo'lmadi — yozuv davom etadi, lekin ekran o'chsa
     * tizim uni to'xtatishi mumkin. Xato emas, ogohlantirish.
     */
    val backgroundWarning: Boolean = false,
) {
    /** Sozlamalarni faqat yozuv ketmayotganda o'zgartirish mumkin. */
    val configEditable: Boolean get() = !isRecording
}

/**
 * Yozib olish ekranining holati.
 *
 * Yadro (AudioRecorderEngine) o'z oqimida ishlaydi va har ~40 ms da daraja
 * haqida xabar beradi. Bu xabarlar asosiy oqimga ko'chiriladi — Compose
 * holatni faqat asosiy oqimda o'zgartirishi kerak.
 */
class RecordViewModel(application: Application) :
    AndroidViewModel(application),
    AudioRecorderEngine.Listener {

    private val store = RecordingStore(application)
    private val engine = AudioRecorderEngine()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(RecordUiState())
    val state: StateFlow<RecordUiState> = _state.asStateFlow()

    fun updateConfig(config: RecorderConfig) {
        _state.update { if (it.isRecording) it else it.copy(config = config) }
    }

    fun startRecording() {
        val current = _state.value
        if (current.isRecording) return
        val config = current.config
        val destination = store.newRecordingFile()
        try {
            engine.start(config, destination, this)
            _state.update {
                it.copy(
                    isRecording = true,
                    isPaused = false,
                    elapsedMs = 0L,
                    level = 0f,
                    markerCount = 0,
                    savedPath = null,
                    errorMessage = null,
                    backgroundWarning = !startBackgroundService(),
                )
            }
        } catch (error: Exception) {
            engineError(error.message ?: error.javaClass.simpleName)
        }
    }

    fun pauseRecording() {
        if (!_state.value.isRecording || _state.value.isPaused) return
        engine.pause()
        _state.update { it.copy(isPaused = true, level = 0f) }
    }

    fun resumeRecording() {
        if (!_state.value.isRecording || !_state.value.isPaused) return
        engine.resume()
        _state.update { it.copy(isPaused = false) }
    }

    fun addMarker(): Long? {
        if (!_state.value.isRecording || _state.value.isPaused) return null
        val at = engine.addMarker()
        _state.update { it.copy(markerCount = it.markerCount + 1) }
        return at
    }

    /** Yozishni to'xtatadi. Fayl yozilishi tugagach holat yangilanadi. */
    fun stopRecording() {
        if (!_state.value.isRecording) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { engine.stop() }
            // Fon xizmati fayl to'liq yozilgandan KEYIN o'chiriladi: aks holda
            // jarayon sarlavha yozilishidan oldin o'ldirilishi mumkin edi.
            stopBackgroundService()
            _state.update { current ->
                if (result == null) {
                    current.copy(
                        isRecording = false,
                        isPaused = false,
                        level = 0f,
                        errorMessage = current.errorMessage ?: NO_DATA_MESSAGE,
                    )
                } else {
                    current.copy(
                        isRecording = false,
                        isPaused = false,
                        level = 0f,
                        savedPath = result.file.absolutePath,
                        savedDurationMs = result.info.durationMs,
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun consumeSaved() {
        _state.update { it.copy(savedPath = null) }
    }

    fun dismissBackgroundWarning() {
        _state.update { it.copy(backgroundWarning = false) }
    }

    // --- AudioRecorderEngine.Listener (ovoz oqimidan chaqiriladi) ---

    override fun onProgress(elapsedMs: Long, level: Float) {
        mainHandler.post {
            _state.update { it.copy(elapsedMs = elapsedMs, level = level) }
        }
    }

    override fun onError(message: String) {
        mainHandler.post { engineError(message) }
    }

    private fun engineError(message: String) {
        // Yadro o'zi to'xtab qoldi — fon xizmati ham keraksiz bo'lib qoldi,
        // aks holda bildirishnoma «yozib olinmoqda» deb turib olardi.
        stopBackgroundService()
        _state.update {
            it.copy(
                isRecording = false,
                isPaused = false,
                level = 0f,
                errorMessage = message,
            )
        }
        // Yozuvchi oqim xato bilan tugagan bo'lsa ham mikrofon va fayl hali
        // ochiq, yadro esa «ishlayapti» deb turibdi. Bo'shatilmasa: mikrofon
        // band qoladi, keyingi «Yozish» «allaqachon ketmoqda» deb rad etiladi
        // va xatodan oldin yozilgan qism sarlavhasiz qoladi.
        if (engine.isRunning) salvageAfterError()
    }

    /** Xatodan keyin yadroni bo'shatadi va yozilgan qismni saqlaydi. */
    private fun salvageAfterError() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { engine.stop() }.getOrNull() }
            if (result != null) {
                _state.update {
                    it.copy(
                        savedPath = result.file.absolutePath,
                        savedDurationMs = result.info.durationMs,
                    )
                }
            }
        }
    }

    /**
     * Fon xizmatini yoqadi. `false` — yoqilmadi.
     *
     * Xatoni yutmaymiz, lekin yozuvni ham to'xtatmaymiz: yozish ishlayapti,
     * shunchaki ekran o'chganda tizim uni to'xtatishi mumkin. Bu holat
     * foydalanuvchiga ogohlantirish sifatida ko'rsatiladi.
     */
    private fun startBackgroundService(): Boolean = runCatching {
        RecordingService.start(getApplication())
    }.isSuccess

    private fun stopBackgroundService() {
        runCatching { RecordingService.stop(getApplication()) }
    }

    override fun onCleared() {
        super.onCleared()
        stopBackgroundService()
        if (engine.isRunning) {
            engine.stop()
        }
    }

    private companion object {
        const val NO_DATA_MESSAGE = "Yozuv saqlanmadi — ma'lumot yozilmagan"
    }
}
