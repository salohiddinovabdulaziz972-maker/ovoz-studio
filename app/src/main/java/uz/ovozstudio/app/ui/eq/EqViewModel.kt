package uz.ovozstudio.app.ui.eq

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
import uz.ovozstudio.app.media.dsp.EqBandCount
import uz.ovozstudio.app.media.dsp.EqBands
import uz.ovozstudio.app.media.dsp.EqPreset
import uz.ovozstudio.app.media.dsp.Equalizer
import uz.ovozstudio.app.media.format.AndroidAudioImporter
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.media.format.ImportOutcome
import uz.ovozstudio.app.util.GainText
import java.io.File

/**
 * Ekvalayzer xatoliklari.
 *
 * Naqsh `TrimError` va `ConvertError` bilan bir xil: ViewModel matn emas,
 * KOD qaytaradi — qaysi tilda ko'rsatishni UI hal qiladi.
 */
enum class EqError {
    FILE_NOT_FOUND,

    /** Filtrlashning o'zi bajarilmadi (o'qish/yozish xatosi). */
    EDIT_FAILED,

    /** Natijani kutubxonaga yozib bo'lmadi. */
    SAVE_FAILED,

    /** Hamma polosa 0 dB — qo'llash natija bermaydi. */
    NOTHING_TO_APPLY,
}

/**
 * Ekvalayzer ekranining holati.
 *
 * [gains] — satrlar ro'yxati, sonlar emas: qiymat maydonga qo'lda
 * kiritiladi va foydalanuvchi yozayotganda «3.» yoki «-» kabi tugallanmagan
 * matn ham vaqtincha yashashi kerak. Sonlar ko'rinishida saqlash uni har
 * bosishda qayta yozib, kursor sakrab ketishiga olib kelardi.
 */
data class EqUiState(
    val fileName: String = "",
    val info: WavInfo? = null,
    val bandCount: EqBandCount = EqBandCount.TEN,
    val preset: EqPreset = EqPreset.FLAT,
    /** Har bir polosaning chastotasi (Hz) — fayl chastotasiga qarab kesilgan. */
    val centers: List<Double> = emptyList(),
    /** [centers] bilan bir xil uzunlikdagi kuchaytirishlar (dB, satr). */
    val gains: List<String> = emptyList(),
    /**
     * Foydalanuvchi polosalarni profildan keyin qo'lda o'zgartirganmi.
     *
     * Polosalar soni almashganda kerak: o'zgartirilmagan bo'lsa qiymatlar
     * profildan qaytadan hisoblanadi (aniq natija), o'zgartirilgan bo'lsa
     * eng yaqin polosadan ko'chiriladi (ish yo'qolmasin).
     */
    val edited: Boolean = false,
    /** Past chastotalarni kesish chegarasi (Hz); 0 — o'chirilgan. */
    val lowCutHz: Int = 0,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val savedPath: String? = null,
    /** Chiqish cho'qqisini kesishdan saqlash uchun pasaytirilgan daraja (dB). */
    val headroomDb: Double = 0.0,
    /** Tizim tanlagichidan kelgan fayl ochilmadi — sabab (kod ko'rinishida). */
    val importFailure: ImportFailure? = null,
    val error: EqError? = null,
) {
    val durationMs: Long get() = info?.durationMs ?: 0L
}

/**
 * Parametrik ekvalayzer ekrani.
 *
 * Manba fayl hech qachon o'zgartirilmaydi: natija kutubxonaga yangi fayl
 * bo'lib tushadi. Bu ilovaning umumiy qoidasi — tahrir tarixi fayllar
 * ro'yxatidan iborat.
 */
class EqViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val importer = AndroidAudioImporter(store)

    private var source: File? = null

    private val _state = MutableStateFlow(EqUiState())
    val state: StateFlow<EqUiState> = _state.asStateFlow()

    /**
     * Tizim tanlagichidan kelgan faylni ochadi.
     *
     * Import qilingan fayl avval WAV ga ochiladi ([AndroidAudioImporter]):
     * ekvalayzer faqat PCM ustida ishlaydi. Manba faylning o'zi
     * o'zgartirilmaydi va o'chirilmaydi.
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
                    EqUiState(busy = false, importFailure = outcome.reason)
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
            _state.update { it.copy(error = EqError.FILE_NOT_FOUND) }
            return
        }
        source = file
        applyLoaded(file, file.nameWithoutExtension)
    }

    private fun applyLoaded(wav: File, displayName: String) {
        val info = runCatching { WavFile.readInfo(wav) }.getOrNull()
        if (info == null) {
            _state.update { it.copy(busy = false, error = EqError.FILE_NOT_FOUND) }
            return
        }
        val name = displayName.substringBeforeLast('.').ifBlank { wav.nameWithoutExtension }
        _state.update {
            EqUiState(
                fileName = name,
                info = info,
                bandCount = EqBandCount.TEN,
                preset = EqPreset.FLAT,
                centers = EqBands.centers(EqBandCount.TEN, info.sampleRate),
                gains = EqBands.presetGains(EqBandCount.TEN, EqPreset.FLAT, info.sampleRate)
                    .map(GainText::format),
            )
        }
    }

    /** Polosalar sonini almashtiradi; qiymatlar yo'qolmaydi. */
    fun setBandCount(count: EqBandCount) {
        val current = _state.value
        val sampleRate = current.info?.sampleRate ?: return
        if (count == current.bandCount) return

        val centers = EqBands.centers(count, sampleRate)
        val gains = if (!current.edited) {
            // Profil egri chizig'i — uzluksiz funksiya, shuning uchun uni
            // yangi jadvalda qaytadan hisoblash eng aniq yo'l.
            EqBands.presetGains(count, current.preset, sampleRate).map(GainText::format)
        } else {
            centers.map { center ->
                val nearest = EqBands.nearestIndex(current.centers, center)
                current.gains.getOrNull(nearest) ?: "0"
            }
        }
        _state.update {
            it.copy(
                bandCount = count,
                centers = centers,
                gains = gains,
                savedPath = null,
                error = null,
            )
        }
    }

    /** Tayyor profilni qo'llaydi — qo'lda kiritilgan qiymatlar almashtiriladi. */
    fun setPreset(preset: EqPreset) {
        val current = _state.value
        val sampleRate = current.info?.sampleRate ?: return
        _state.update {
            it.copy(
                preset = preset,
                gains = EqBands.presetGains(current.bandCount, preset, sampleRate)
                    .map(GainText::format),
                edited = false,
                savedPath = null,
                error = null,
            )
        }
    }

    /** Bitta polosaning kuchaytirishini qo'lda o'zgartiradi. */
    fun setGain(index: Int, text: String) {
        val current = _state.value
        if (index !in current.centers.indices) return
        val sanitized = GainText.sanitize(text)
        _state.update {
            it.copy(
                gains = it.gains.toMutableList().also { list -> list[index] = sanitized },
                edited = true,
                savedPath = null,
                error = null,
            )
        }
    }

    /** Polosani [deltaDb] qadar suradi (± tugmalari uchun). */
    fun nudgeGain(index: Int, deltaDb: Double) {
        val current = _state.value
        val text = current.gains.getOrNull(index) ?: return
        setGain(index, GainText.nudge(text, deltaDb))
    }

    /** Past chastotalarni kesish chegarasi. */
    fun setLowCut(hertz: Int) = _state.update {
        it.copy(lowCutHz = hertz, savedPath = null, error = null)
    }

    fun clearError() = _state.update { it.copy(error = null, importFailure = null) }
    fun consumeSaved() = _state.update { it.copy(savedPath = null, headroomDb = 0.0) }

    /** Filtrlab, natijani kutubxonaga yangi fayl sifatida saqlaydi. */
    fun apply() {
        val file = source ?: run {
            _state.update { it.copy(error = EqError.FILE_NOT_FOUND) }
            return
        }
        val current = _state.value
        if (current.busy) return

        val settings = settingsOf(current)
        val sampleRate = current.info?.sampleRate ?: 0
        // Qo'llashdan oldin aytamiz: aks holda foydalanuvchi tugmani bosadi,
        // kutubxonada hech narsa o'zgarmaydi va sababini bilmaydi.
        if (settings.isFlat || !settings.isAudible(sampleRate)) {
            _state.update { it.copy(error = EqError.NOTHING_TO_APPLY) }
            return
        }

        _state.update { it.copy(busy = true, progress = 0f, error = null, savedPath = null) }

        viewModelScope.launch {
            val destination = store.newRecordingFile("${current.fileName}-ekvalayzer")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Equalizer.apply(file, destination, settings) { progress ->
                        // Har bir bo'lakda holatni yangilash ortiqcha: ekran
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
                            headroomDb = outcome.headroomDb,
                        )
                    }
                },
                onFailure = {
                    // Yarim yozilgan fayl kutubxonada qolmasligi kerak.
                    destination.delete()
                    _state.update { it.copy(busy = false, progress = 0f, error = EqError.EDIT_FAILED) }
                },
            )
        }
    }

    private fun settingsOf(state: EqUiState): Equalizer.Settings {
        val q = EqBands.q(state.bandCount)
        val bands = state.centers.indices.map { index ->
            Equalizer.Band(
                frequency = state.centers[index],
                gainDb = GainText.parse(state.gains.getOrNull(index).orEmpty()),
                q = q,
            )
        }
        return Equalizer.Settings(bands = bands, lowCutHz = state.lowCutHz)
    }

    private companion object {
        /** Shu qadamdan kichik o'zgarish ekranga chiqarilmaydi. */
        const val PROGRESS_STEP = 0.02f
    }
}
