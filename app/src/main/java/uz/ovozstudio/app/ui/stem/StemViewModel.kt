package uz.ovozstudio.app.ui.stem

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
import uz.ovozstudio.app.media.dsp.StemSeparator
import uz.ovozstudio.app.media.format.AndroidAudioImporter
import uz.ovozstudio.app.media.format.ImportOutcome
import uz.ovozstudio.app.util.DecimalText
import java.io.File

/**
 * Vokal/cholg'u ajratish ekrani.
 *
 * Manba fayl hech qachon o'zgartirilmaydi: natija kutubxonaga **ikkita
 * yangi fayl** bo'lib tushadi. Ikkalasi birga manbani beradi (vokal +
 * cholg'u = manba), ya'ni foydalanuvchi hech narsa yo'qotmaydi — bu
 * usulning tuzilish xossasi, va aynan shu xossa tekshiriladi.
 */
class StemViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val importer = AndroidAudioImporter(store)

    private var source: File? = null

    private val _state = MutableStateFlow(StemUiState())
    val state: StateFlow<StemUiState> = _state.asStateFlow()

    /** Tizim tanlagichidan kelgan faylni ochadi. */
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
                    StemUiState(busy = false, importFailure = outcome.reason)
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
            _state.update { it.copy(error = StemError.FILE_NOT_FOUND) }
            return
        }
        source = file
        applyLoaded(file, file.nameWithoutExtension)
    }

    private fun applyLoaded(wav: File, displayName: String) {
        val info = runCatching { WavFile.readInfo(wav) }.getOrNull()
        if (info == null) {
            _state.update { it.copy(busy = false, error = StemError.FILE_NOT_FOUND) }
            return
        }
        val name = displayName.substringBeforeLast('.').ifBlank { wav.nameWithoutExtension }
        _state.update { StemUiState(fileName = name, info = info) }
    }

    /** Ajratish rejimini tanlaydi. */
    fun setMode(mode: StemSeparator.Mode) = _state.update {
        it.copy(mode = mode, savedVocalPath = null, savedInstrumentalPath = null, error = null)
    }

    /** Kuch maydonini qo'lda o'zgartiradi. */
    fun setStrength(text: String) = _state.update {
        it.copy(
            strength = DecimalText.sanitize(text, integerDigits = 1, fractionDigits = 1),
            savedVocalPath = null,
            savedInstrumentalPath = null,
            error = null,
        )
    }

    /** Kuchni [delta] qadar suradi (± tugmalari uchun). */
    fun nudgeStrength(delta: Double) = _state.update {
        it.copy(
            strength = DecimalText.nudge(
                text = it.strength,
                delta = delta,
                min = StemSeparator.MIN_STRENGTH,
                max = StemSeparator.MAX_STRENGTH,
                fractionDigits = 1,
                fallback = StemSeparator.DEFAULT_STRENGTH,
            ),
            savedVocalPath = null,
            savedInstrumentalPath = null,
            error = null,
        )
    }

    fun clearError() = _state.update { it.copy(error = null, importFailure = null) }

    fun consumeSaved() = _state.update {
        it.copy(savedVocalPath = null, savedInstrumentalPath = null)
    }

    /**
     * Manbani ikkita faylga ajratadi.
     *
     * Ikkala fayl bir joyda yoziladi: biri tayyor bo'lib, ikkinchisi
     * yozilmay qolsa, natija yarim bo'lardi — foydalanuvchi «ajratdim»
     * deb bitta fayl olardi. Shuning uchun xato bo'lsa **ikkalasi ham**
     * o'chiriladi va sabab ekranga chiqadi.
     */
    fun apply() {
        val file = source ?: run {
            _state.update { it.copy(error = StemError.FILE_NOT_FOUND) }
            return
        }
        val current = _state.value
        if (current.busy) return

        val settings = current.settings
        _state.update {
            it.copy(
                busy = true,
                progress = 0f,
                error = null,
                savedVocalPath = null,
                savedInstrumentalPath = null,
            )
        }

        viewModelScope.launch {
            val vocalFile = store.newRecordingFile("${current.fileName}-vokal")
            val instrumentalFile = store.newRecordingFile("${current.fileName}-cholgu")
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    StemSeparator.apply(file, vocalFile, instrumentalFile, settings) { progress ->
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
                            savedVocalPath = vocalFile.absolutePath,
                            savedInstrumentalPath = instrumentalFile.absolutePath,
                            sideToMidDb = outcome.sideToMidDb,
                        )
                    }
                },
                onFailure = { failure ->
                    // Yarim qolgan natija kutubxonada ko'rinmasligi kerak.
                    vocalFile.delete()
                    instrumentalFile.delete()
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
     * «Manba stereo emas» va «kanallari bir xil» ni faqat DSP aniqlay oladi —
     * buning uchun faylni o'qish kerak. Shu sababdan bu yerda istisno matni
     * bo'yicha ajratiladi, matn esa [StemSeparator] da konstanta bo'lib
     * turadi (ikki joyda ikki xil yozilib, jimgina ajralib ketmasligi uchun).
     */
    private fun errorOf(failure: Throwable): StemError = when (failure.message) {
        StemSeparator.ERROR_NOT_STEREO -> StemError.NOT_STEREO
        StemSeparator.ERROR_MONO_CONTENT -> StemError.MONO_CONTENT
        else -> StemError.EDIT_FAILED
    }

    private companion object {
        /** Shu qadamdan kichik o'zgarish ekranga chiqarilmaydi. */
        const val PROGRESS_STEP = 0.02f
    }
}
