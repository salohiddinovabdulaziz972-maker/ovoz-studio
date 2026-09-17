package uz.ovozstudio.app.ui.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import uz.ovozstudio.app.media.voice.DeviceTtsEngine
import uz.ovozstudio.app.media.voice.ScriptDetector
import uz.ovozstudio.app.media.voice.SpeechChunk
import uz.ovozstudio.app.media.voice.SpeechListener
import uz.ovozstudio.app.media.voice.SpeechRequest
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.media.voice.VoiceError
import uz.ovozstudio.app.util.SpeedText
import kotlin.math.pow

/**
 * Ovoz sinovi xatolari.
 *
 * Naqsh ilova bo'ylab bir xil: ViewModel matn emas, KOD qaytaradi.
 */
enum class VoiceUiError {
    /** Dvigatel ishga tushmadi — qurilmada sintezator yo'q. */
    NOT_AVAILABLE,

    /** O'qishga matn yo'q. */
    EMPTY_TEXT,

    /** O'qish paytida xato. */
    SPEAK_FAILED,
}

/**
 * Ovoz sinovi ekranining holati.
 *
 * [text] — o'qiladigan matn, [rate] va [semitones] — satrlar, sonlar emas:
 * qiymat maydonga qo'lda kiritiladi va foydalanuvchi yozayotganda «1.» yoki
 * «-» kabi tugallanmagan matn ham vaqtincha yashashi kerak.
 */
data class VoiceUiState(
    val text: String = "",
    val rate: String = "1",
    val semitones: String = "0",
    /** Dvigatel tayyormi. */
    val ready: Boolean = false,
    /** Hozir o'qilyaptimi. */
    val speaking: Boolean = false,
    /** Nechanchi bo'lak o'qilyapti (1 dan boshlanadi). */
    val chunkNumber: Int = 0,
    /** Jami nechta bo'lakka bo'lindi. */
    val chunkTotal: Int = 0,
    /** Tanlangan til-teg, masalan `uz-UZ`. */
    val languageTag: String? = null,
    /**
     * Qurilmada mos ovoz topilmadi.
     *
     * Bu xato emas: o'qish joriy ovoz bilan davom etadi. Lekin foydalanuvchi
     * buni bilishi kerak — noto'g'ri talaffuzning sababini bilmaslik undan
     * yomonroq.
     */
    val languageMissing: Boolean = false,
    /** Qurilmadagi ovozlar soni. */
    val voiceCount: Int = 0,
    /** Matn lotin yoki kirill yozuvida. */
    val script: String = "",
    val error: VoiceUiError? = null,
) {
    /** Tezlik — dvigatel kutadigan ko'paytiruvchi. */
    val rateValue: Float get() = SpeedText.parseSpeed(rate).toFloat()

    /** Yarim tonlardan ovoz balandligi ko'paytiruvchisiga. */
    val pitchValue: Float
        get() = 2.0.pow(SpeedText.parseSemitones(semitones) / SEMITONES_PER_OCTAVE).toFloat()

    companion object {
        const val SEMITONES_PER_OCTAVE = 12.0
        const val STEP_RATE = 0.05
        const val STEP_SEMITONES = 1.0
    }
}

/**
 * Ovoz sinovi ekranining mantiqi.
 *
 * Ekranning vazifasi — ovoz dvigatelini **qurilmada** tekshirish: qaysi
 * ovozlar bor, o'zbek ovozi bormi, bo'laklarga bo'lish to'g'ri ishlayaptimi.
 * Audio-kitob ekrani shu dvigatel ustiga quriladi, lekin undan oldin
 * dvigatelning o'zi ishlashiga ishonch hosil qilish kerak.
 */
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: VoiceEngine = DeviceTtsEngine(application)

    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private val listener = object : SpeechListener {
        override fun onStarted(index: Int, total: Int, chunk: SpeechChunk) {
            _state.update {
                it.copy(speaking = true, chunkNumber = index + 1, chunkTotal = total, error = null)
            }
        }

        override fun onFinished() {
            _state.update { it.copy(speaking = false, chunkNumber = 0) }
        }

        override fun onError(error: VoiceError) {
            _state.update { it.copy(speaking = false, error = error.toUi()) }
        }
    }

    init {
        engine.prepare { failure ->
            _state.update {
                it.copy(
                    ready = failure == null,
                    voiceCount = if (failure == null) engine.voices().size else 0,
                    error = failure?.toUi(),
                )
            }
            refreshLanguage()
        }
    }

    fun setText(value: String) {
        _state.update { it.copy(text = value) }
        refreshLanguage()
    }

    fun setRate(value: String) {
        _state.update { it.copy(rate = SpeedText.sanitizeSpeed(value)) }
    }

    fun setSemitones(value: String) {
        _state.update { it.copy(semitones = SpeedText.sanitizeSemitones(value)) }
    }

    fun nudgeRate(delta: Double) {
        _state.update { it.copy(rate = SpeedText.nudgeSpeed(it.rate, delta)) }
    }

    fun nudgeSemitones(delta: Double) {
        _state.update { it.copy(semitones = SpeedText.nudgeSemitones(it.semitones, delta)) }
    }

    fun speak() {
        val current = _state.value
        if (current.text.isBlank()) {
            _state.update { it.copy(error = VoiceUiError.EMPTY_TEXT) }
            return
        }
        _state.update { it.copy(error = null, chunkNumber = 0) }
        engine.speak(
            SpeechRequest(
                text = current.text,
                rate = current.rateValue,
                pitch = current.pitchValue,
                languageTag = current.languageTag,
            ),
            listener,
        )
    }

    fun stop() {
        engine.stop()
        // `chunkTotal` ham nolga tushiriladi: ekran shu maydon bo'yicha
        // «o'qish tugadi» deb e'lon qiladi, foydalanuvchi o'zi to'xtatgan
        // o'qish uchun esa bunday xabar ortiqcha.
        _state.update { it.copy(speaking = false, chunkNumber = 0, chunkTotal = 0) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        // Ovoz sintezatori — tizim xizmati: uni bo'shatmaslik qurilmada
        // ochiq ulanish qoldiradi va batareyani yeydi.
        engine.release()
    }

    /**
     * Matnning yozuviga qarab tilni qayta aniqlaydi.
     *
     * Dvigatel tayyor bo'lmasa hech narsa qilinmaydi: til so'rovini
     * sintezatorga yuborish mumkin emas.
     */
    private fun refreshLanguage() {
        val current = _state.value
        if (!current.ready) return
        val script = ScriptDetector.detect(current.text)
        val tag = if (current.text.isBlank()) null else engine.resolveLanguage(script)
        _state.update {
            it.copy(
                script = script.name,
                languageTag = tag,
                languageMissing = current.text.isNotBlank() && tag == null,
            )
        }
    }
}

private fun VoiceError.toUi(): VoiceUiError = when (this) {
    VoiceError.NOT_AVAILABLE -> VoiceUiError.NOT_AVAILABLE
    VoiceError.EMPTY_TEXT -> VoiceUiError.EMPTY_TEXT
    VoiceError.LANGUAGE_MISSING, VoiceError.SPEAK_FAILED -> VoiceUiError.SPEAK_FAILED
}
