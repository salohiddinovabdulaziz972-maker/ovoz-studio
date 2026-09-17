package uz.ovozstudio.app.ui.noise

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
import uz.ovozstudio.app.media.dsp.NoiseReducer
import uz.ovozstudio.app.media.format.AndroidAudioImporter
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.media.format.ImportOutcome
import uz.ovozstudio.app.util.DecimalText
import uz.ovozstudio.app.util.TimeParts
import java.io.File

/**
 * Shovqin tozalash xatoliklari.
 *
 * Naqsh `EqError` va `SpeedError` bilan bir xil: ViewModel matn emas, KOD
 * qaytaradi — qaysi tilda ko'rsatishni UI hal qiladi.
 */
enum class NoiseError {
    FILE_NOT_FOUND,

    /** Amalning o'zi bajarilmadi (o'qish/yozish xatosi). */
    EDIT_FAILED,

    /** Namuna oraliq noto'g'ri: oxiri boshidan keyin bo'lishi shart. */
    SAMPLE_RANGE,

    /** Tanlangan oraliq jim — shovqin profilini olish uchun asos yo'q. */
    SAMPLE_QUIET,
}

/**
 * Shovqin tozalash ekranining holati.
 *
 * [noiseStart] va [noiseEnd] — satrlar to'plami ([TimeParts]), sonlar emas:
 * vaqt maydonlarga qo'lda kiritiladi va foydalanuvchi yozayotganda tugallanmagan
 * matn ham vaqtincha yashashi kerak.
 *
 * [strength] va [floorDb] ham satr: maydon bo'sh qolishi mumkin, o'shanda
 * standart qiymat ishlatiladi ([DecimalText.parse] ning `fallback` i).
 */
data class NoiseUiState(
    val fileName: String = "",
    val info: WavInfo? = null,
    val noiseStart: TimeParts = TimeParts(),
    val noiseEnd: TimeParts = TimeParts(),
    val strength: String = STRENGTH_DEFAULT_TEXT,
    val floorDb: String = FLOOR_DEFAULT_TEXT,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val savedPath: String? = null,
    /** Saqlangan faylda shovqin qancha pasaygani — o'lchangan son. */
    val savedDropDb: Double = 0.0,
    /** Tizim tanlagichidan kelgan fayl ochilmadi — sabab (kod ko'rinishida). */
    val importFailure: ImportFailure? = null,
    val error: NoiseError? = null,
) {
    val durationMs: Long get() = info?.durationMs ?: 0L

    /** Namunaning boshlanishi. Bo'sh maydon — nol. */
    val startMs: Long get() = noiseStart.toMillisOrNull() ?: 0L

    /** Namunaning oxiri. Bo'sh maydon — nol. */
    val endMs: Long get() = noiseEnd.toMillisOrNull() ?: 0L

    /** Oraliq to'g'rimi. Maydonlardagi jingalak matn ham shu yerda ko'rinadi. */
    val rangeValid: Boolean get() = endMs > startMs

    /** Kiritilgan qiymatlardan yasalgan sozlama. */
    val settings: NoiseReducer.Settings
        get() = NoiseReducer.Settings(
            noiseStartMs = startMs,
            noiseEndMs = endMs,
            strength = DecimalText.parse(
                strength,
                NoiseReducer.MIN_STRENGTH,
                NoiseReducer.MAX_STRENGTH,
                NoiseReducer.DEFAULT_STRENGTH,
            ),
            floorDb = DecimalText.parse(
                floorDb,
                NoiseReducer.MIN_FLOOR_DB,
                NoiseReducer.MAX_FLOOR_DB,
                NoiseReducer.DEFAULT_FLOOR_DB,
            ),
        )

    companion object {
        /** Maydonlarning boshlang'ich ko'rinishi — «kuch» va «qoldiq». */
        const val STRENGTH_DEFAULT_TEXT = "2.5"
        const val FLOOR_DEFAULT_TEXT = "-15"
    }
}

/**
 * Shovqin tozalash ekrani.
 *
 * Manba fayl hech qachon o'zgartirilmaydi: natija kutubxonaga yangi fayl
 * bo'lib tushadi.
 */
class NoiseViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val importer = AndroidAudioImporter(store)

    private var source: File? = null

    private val _state = MutableStateFlow(NoiseUiState())
    val state: StateFlow<NoiseUiState> = _state.asStateFlow()

    /**
     * Tizim tanlagichidan kelgan faylni ochadi.
     *
     * Import qilingan fayl avval WAV ga ochiladi ([AndroidAudioImporter]):
     * amal faqat PCM ustida ishlaydi. Manba faylning o'zi o'zgartirilmaydi.
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
                    NoiseUiState(busy = false, importFailure = outcome.reason)
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
            _state.update { it.copy(error = NoiseError.FILE_NOT_FOUND) }
            return
        }
        source = file
        applyLoaded(file, file.nameWithoutExtension)
    }

    private fun applyLoaded(wav: File, displayName: String) {
        val info = runCatching { WavFile.readInfo(wav) }.getOrNull()
        if (info == null) {
            _state.update { it.copy(busy = false, error = NoiseError.FILE_NOT_FOUND) }
            return
        }
        val name = displayName.substringBeforeLast('.').ifBlank { wav.nameWithoutExtension }

        // Namunaning boshlang'ich oraliqi — faylning birinchi yarim sekundi.
        // Eng ko'p uchraydigan holat shu: yozuv boshida hali hech narsa
        // aytilmagan bo'ladi va faqat fon shovqini eshitiladi. Fayl undan
        // qisqa bo'lsa — fayl oxiri.
        val sampleEndMs = minOf(DEFAULT_SAMPLE_MS, info.durationMs)
        _state.update {
            NoiseUiState(
                fileName = name,
                info = info,
                noiseStart = TimeParts.fromMillis(0),
                noiseEnd = TimeParts.fromMillis(sampleEndMs),
            )
        }
    }

    /** Namunaning boshlanishini qo'lda o'zgartiradi. */
    fun setNoiseStart(parts: TimeParts) = _state.update {
        it.copy(noiseStart = parts, savedPath = null, error = null)
    }

    /** Namunaning oxirini qo'lda o'zgartiradi. */
    fun setNoiseEnd(parts: TimeParts) = _state.update {
        it.copy(noiseEnd = parts, savedPath = null, error = null)
    }

    /** Kuch maydonini qo'lda o'zgartiradi. */
    fun setStrength(text: String) = _state.update {
        it.copy(
            strength = DecimalText.sanitize(text, integerDigits = 1, fractionDigits = 1),
            savedPath = null,
            error = null,
        )
    }

    /** Qoldiq maydonini qo'lda o'zgartiradi. */
    fun setFloorDb(text: String) = _state.update {
        it.copy(
            floorDb = DecimalText.sanitize(text, integerDigits = 2, fractionDigits = 0),
            savedPath = null,
            error = null,
        )
    }

    /** Kuchni [delta] qadar suradi (± tugmalari uchun). */
    fun nudgeStrength(delta: Double) = _state.update {
        it.copy(
            strength = DecimalText.nudge(
                text = it.strength,
                delta = delta,
                min = NoiseReducer.MIN_STRENGTH,
                max = NoiseReducer.MAX_STRENGTH,
                fractionDigits = 1,
                fallback = NoiseReducer.DEFAULT_STRENGTH,
            ),
            savedPath = null,
            error = null,
        )
    }

    /** Qoldiqni [delta] dB qadar suradi. */
    fun nudgeFloorDb(delta: Double) = _state.update {
        it.copy(
            floorDb = DecimalText.nudge(
                text = it.floorDb,
                delta = delta,
                min = NoiseReducer.MIN_FLOOR_DB,
                max = NoiseReducer.MAX_FLOOR_DB,
                fractionDigits = 0,
                fallback = NoiseReducer.DEFAULT_FLOOR_DB,
            ),
            savedPath = null,
            error = null,
        )
    }

    fun clearError() = _state.update { it.copy(error = null, importFailure = null) }
    fun consumeSaved() = _state.update { it.copy(savedPath = null) }

    /** Amalni bajarib, natijani kutubxonaga yangi fayl sifatida saqlaydi. */
    fun apply() {
        val file = source ?: run {
            _state.update { it.copy(error = NoiseError.FILE_NOT_FOUND) }
            return
        }
        val current = _state.value
        if (current.busy) return

        // Qo'llashdan oldin aytamiz: aks holda foydalanuvchi tugmani bosadi,
        // kutubxonada hech narsa o'zgarmaydi va sababini bilmaydi.
        if (!current.rangeValid) {
            _state.update { it.copy(error = NoiseError.SAMPLE_RANGE) }
            return
        }

        val settings = current.settings
        _state.update { it.copy(busy = true, progress = 0f, error = null, savedPath = null) }

        viewModelScope.launch {
            val destination = store.newRecordingFile("${current.fileName}-tozalangan")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    NoiseReducer.apply(file, destination, settings) { progress ->
                        // Har bir kadrda holatni yangilash ortiqcha: ekran
                        // qayta chizilishdan boshqa narsa o'zgarmaydi.
                        if (progress - _state.value.progress >= PROGRESS_STEP) {
                            _state.update { it.copy(progress = progress) }
                        }
                    }
                }
            }

            result.fold(
                onSuccess = { outcome ->
                    _state.update {
                        it.copy(
                            busy = false,
                            progress = 1f,
                            savedPath = destination.absolutePath,
                            savedDropDb = outcome.noiseDropDb,
                        )
                    }
                },
                onFailure = { failure ->
                    // Yarim yozilgan fayl kutubxonada qolmasligi kerak.
                    destination.delete()
                    _state.update {
                        it.copy(busy = false, progress = 0f, error = errorOf(failure))
                    }
                },
            )
        }
    }

    /**
     * Istisnoni sabab kodiga aylantiradi.
     *
     * «Jim namuna» ni faqat DSP aniqlay oladi — buning uchun faylni o'qish
     * kerak. Shu sababdan bu yerda istisno matni bo'yicha ajratiladi, matn
     * esa [NoiseReducer] da konstanta bo'lib turadi (ikki joyda ikki xil
     * yozilib, jimgina ajralib ketmasligi uchun).
     */
    private fun errorOf(failure: Throwable): NoiseError = when (failure.message) {
        NoiseReducer.ERROR_QUIET_SAMPLE -> NoiseError.SAMPLE_QUIET
        else -> NoiseError.EDIT_FAILED
    }

    private companion object {
        /** Shu qadamdan kichik o'zgarish ekranga chiqarilmaydi. */
        const val PROGRESS_STEP = 0.02f

        /** Namunaning boshlang'ich uzunligi — yarim sekund. */
        const val DEFAULT_SAMPLE_MS = 500L
    }
}
