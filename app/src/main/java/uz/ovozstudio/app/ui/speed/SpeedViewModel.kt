package uz.ovozstudio.app.ui.speed

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
import uz.ovozstudio.app.media.RecordingStore
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.dsp.SpeedPitch
import uz.ovozstudio.app.media.format.AndroidAudioImporter
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.media.format.ImportOutcome
import uz.ovozstudio.app.util.SpeedText
import java.io.File

/**
 * Tezlik va ohang xatoliklari.
 *
 * Naqsh `EqError` bilan bir xil: ViewModel matn emas, KOD qaytaradi — qaysi
 * tilda ko'rsatishni UI hal qiladi.
 */
enum class SpeedError {
    FILE_NOT_FOUND,

    /** Amalning o'zi bajarilmadi (o'qish/yozish xatosi). */
    EDIT_FAILED,

    /** Ikkala sozlama ham o'zgarmagan — qo'llash natija bermaydi. */
    NOTHING_TO_APPLY,
}

/**
 * Tezlik va ohang ekranining holati.
 *
 * [speed] va [semitones] — satrlar, sonlar emas: qiymat maydonga qo'lda
 * kiritiladi va foydalanuvchi yozayotganda «1.» yoki «-» kabi tugallanmagan
 * matn ham vaqtincha yashashi kerak.
 */
data class SpeedUiState(
    val fileName: String = "",
    val info: WavInfo? = null,
    val speed: String = "1",
    val semitones: String = "0",
    val busy: Boolean = false,
    val progress: Float = 0f,
    val savedPath: String? = null,
    /** Tizim tanlagichidan kelgan fayl ochilmadi — sabab (kod ko'rinishida). */
    val importFailure: ImportFailure? = null,
    val error: SpeedError? = null,
) {
    val durationMs: Long get() = info?.durationMs ?: 0L

    /** Kiritilgan qiymatlardan yasalgan sozlama. */
    val settings: SpeedPitch.Settings
        get() = SpeedPitch.Settings(
            speed = SpeedText.parseSpeed(speed),
            semitones = SpeedText.parseSemitones(semitones),
        )

    /**
     * Amaldan keyingi uzunlik.
     *
     * Ekranda ko'rsatiladi: «2:13 → 1:06». Busiz foydalanuvchi tezlikni
     * kiritib, natijani eshitmaguncha nima bo'lganini bilmasdi.
     */
    val resultDurationMs: Long
        get() {
            val speed = settings.speed
            if (speed <= 0.0) return durationMs
            return (durationMs / speed).toLong()
        }
}

/**
 * Tezlik va ohang ekrani.
 *
 * Manba fayl hech qachon o'zgartirilmaydi: natija kutubxonaga yangi fayl
 * bo'lib tushadi.
 */
class SpeedViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val importer = AndroidAudioImporter(store)

    private var source: File? = null

    private val _state = MutableStateFlow(SpeedUiState())
    val state: StateFlow<SpeedUiState> = _state.asStateFlow()

    /**
     * Tizim tanlagichidan kelgan faylni ochadi.
     *
     * Import qilingan fayl avval WAV ga ochiladi ([AndroidAudioImporter]):
     * amal faqat PCM ustida ishlaydi. Manba faylning o'zi o'zgartirilmaydi
     * va o'chirilmaydi.
     */
    fun open(uri: Uri) {
        val context = getApplication<Application>()
        _state.update { it.copy(busy = true, error = null, importFailure = null) }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { importer.import(context, uri) }
            when (outcome) {
                is ImportOutcome.Rejected -> _state.update {
                    // Eski fayl haqidagi ma'lumot qolmasligi kerak: aks holda
                    // ekranda yangi xato bilan birga eski faylning nomi
                    // ko'rinib, foydalanuvchi nima ochilganini tushunmasdi.
                    SpeedUiState(busy = false, importFailure = outcome.reason)
                }

                is ImportOutcome.Ready -> {
                    source = outcome.wav
                    applyLoaded(outcome.wav, outcome.displayName)
                }
            }
        }
    }

    /** Ilovaning o'z papkasidagi faylni ochadi. */
    fun load(path: String) {
        val file = File(path)
        if (!file.exists()) {
            _state.update { it.copy(error = SpeedError.FILE_NOT_FOUND) }
            return
        }
        source = file
        applyLoaded(file, file.nameWithoutExtension)
    }

    private fun applyLoaded(wav: File, displayName: String) {
        val info = runCatching { WavFile.readInfo(wav) }.getOrNull()
        if (info == null) {
            _state.update { it.copy(busy = false, error = SpeedError.FILE_NOT_FOUND) }
            return
        }
        val name = displayName.substringBeforeLast('.').ifBlank { wav.nameWithoutExtension }
        _state.update { SpeedUiState(fileName = name, info = info) }
    }

    /** Tezlik maydonini qo'lda o'zgartiradi. */
    fun setSpeed(text: String) = _state.update {
        it.copy(speed = SpeedText.sanitizeSpeed(text), savedPath = null, error = null)
    }

    /** Ohang maydonini qo'lda o'zgartiradi. */
    fun setSemitones(text: String) = _state.update {
        it.copy(semitones = SpeedText.sanitizeSemitones(text), savedPath = null, error = null)
    }

    /** Tezlikni [delta] qadar suradi (± tugmalari uchun). */
    fun nudgeSpeed(delta: Double) = _state.update {
        it.copy(
            speed = SpeedText.nudgeSpeed(it.speed, delta),
            savedPath = null,
            error = null,
        )
    }

    /** Ohangni [delta] yarim ton qadar suradi. */
    fun nudgeSemitones(delta: Double) = _state.update {
        it.copy(
            semitones = SpeedText.nudgeSemitones(it.semitones, delta),
            savedPath = null,
            error = null,
        )
    }

    fun clearError() = _state.update { it.copy(error = null, importFailure = null) }
    fun consumeSaved() = _state.update { it.copy(savedPath = null) }

    /** Amalni bajarib, natijani kutubxonaga yangi fayl sifatida saqlaydi. */
    fun apply() {
        val file = source ?: run {
            _state.update { it.copy(error = SpeedError.FILE_NOT_FOUND) }
            return
        }
        val current = _state.value
        if (current.busy) return

        val settings = current.settings
        // Qo'llashdan oldin aytamiz: aks holda foydalanuvchi tugmani bosadi,
        // kutubxonada hech narsa o'zgarmaydi va sababini bilmaydi.
        if (settings.isIdentity || !settings.isValid()) {
            _state.update { it.copy(error = SpeedError.NOTHING_TO_APPLY) }
            return
        }

        _state.update { it.copy(busy = true, progress = 0f, error = null, savedPath = null) }

        viewModelScope.launch {
            val destination = store.newRecordingFile("${current.fileName}-${suffix(settings)}")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    SpeedPitch.apply(file, destination, settings) { progress ->
                        // Har bir qadamda holatni yangilash ortiqcha: ekran
                        // qayta chizilishdan boshqa narsa o'zgarmaydi.
                        if (progress - _state.value.progress >= PROGRESS_STEP) {
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                }
            }

            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(busy = false, progress = 1f, savedPath = destination.absolutePath)
                    }
                },
                onFailure = {
                    // Yarim yozilgan fayl kutubxonada qolmasligi kerak.
                    destination.delete()
                    _state.update {
                        it.copy(busy = false, progress = 0f, error = SpeedError.EDIT_FAILED)
                    }
                },
            )
        }
    }

    /** Fayl nomiga qo'shiladigan qism: nima o'zgargani nomdan ko'rinsin. */
    private fun suffix(settings: SpeedPitch.Settings): String = when {
        settings.semitones == 0.0 -> "tezlik"
        settings.speed == 1.0 -> "ohang"
        else -> "tezlik-ohang"
    }

    private companion object {
        /** Shu qadamdan kichik o'zgarish ekranga chiqarilmaydi. */
        const val PROGRESS_STEP = 0.02f
    }
}
