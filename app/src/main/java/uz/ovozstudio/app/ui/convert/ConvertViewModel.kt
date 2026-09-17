package uz.ovozstudio.app.ui.convert

import android.app.Application
import android.net.Uri
import android.os.Build
import android.util.Log
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
import uz.ovozstudio.app.media.format.AndroidAudioEncoders
import uz.ovozstudio.app.media.format.AndroidAudioImporter
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.DetectedFormat
import uz.ovozstudio.app.media.format.ExportDecision
import uz.ovozstudio.app.media.format.ExportOutcome
import uz.ovozstudio.app.media.format.FallbackReason
import uz.ovozstudio.app.media.format.FormatPreservingExporter
import uz.ovozstudio.app.media.format.FormatSupport
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.media.format.ImportOutcome
import java.io.File

/**
 * Konvertor ekranidagi xatoliklar.
 *
 * Naqsh `TrimError` bilan bir xil: ViewModel matn emas, KOD qaytaradi,
 * matnni qaysi tilda ko'rsatishni UI hal qiladi.
 */
enum class ConvertError {
    /** Fayl ochildi, lekin bu formatga yozib bo'lmadi (kodek rad etdi). */
    ENCODE_FAILED,

    /** Natija faylini yozib bo'lmadi: joy yetmadi yoki fayl ochilmadi. */
    WRITE_FAILED,

    /** Fayl topilmadi — masalan, tizim uni tozalab tashlagan. */
    FILE_NOT_FOUND,
}

/**
 * Konvertor ekranining holati.
 *
 * Ish ikkiga bo'linadi va bu ataylab: avval fayl **ochiladi**
 * ([AndroidAudioImporter]), keyin **boshqa formatda yoziladi**
 * ([FormatPreservingExporter]). Ikkalasiga ham bitta qoida amal qiladi —
 * manba fayl hech qachon o'zgartirilmaydi, natija har doim yangi fayl.
 */
data class ConvertUiState(
    val sourceName: String = "",
    /** Ochilgan WAV ning namuna parametrlari: chastota, kanal, bit chuqurligi. */
    val sourceFormat: AudioFormat? = null,
    /** Manba fayldan o'qilgan konteyner va kodek. */
    val sourceDetected: DetectedFormat? = null,
    val durationMs: Long = 0L,
    /** Tahrirlash uchun ochilgan fayl — konvertatsiya shundan boshlanadi. */
    val workingWav: File? = null,
    /**
     * Manba formatini o'zgartirmasdan qaytarish mumkinmi.
     *
     * Ha bo'lsa, tanlov shu formatda turadi: foydalanuvchi hech narsani
     * o'zgartirmasa ham natija tanish formatda chiqadi.
     */
    val preserving: Boolean = false,
    /**
     * Manba formatini saqlab bo'lmasa — sababi. Ekranda ochiq aytiladi:
     * "jimgina boshqa formatda saqlandi" — eng yomon xatti-harakat.
     */
    val fallbackReason: FallbackReason? = null,
    val target: AudioFormat? = null,
    val targets: List<AudioFormat> = emptyList(),
    val busy: Boolean = false,
    /** Yozilgan faylning to'liq yo'li — keyingi qadam shu faylni oladi. */
    val outputPath: String? = null,
    /** Haqiqatda yozilgan format: kodlovchi so'ralganidan farq qilishi mumkin. */
    val outputFormat: AudioFormat? = null,
    val importFailure: ImportFailure? = null,
    val error: ConvertError? = null,
)

/**
 * Format konvertori.
 *
 * Asosiy qoida — foydalanuvchi qanday format yuklasa, natija ham shu
 * formatda bo'ladi. Shuning uchun tanlov dastlab manba formatida turadi;
 * boshqa formatga o'tish faqat foydalanuvchi uni ochiq tanlaganda bo'ladi —
 * o'shanda bu "konvertatsiya" va u kutilgan amal.
 */
class ConvertViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)
    private val importer = AndroidAudioImporter(store)
    private val apiLevel: Int = Build.VERSION.SDK_INT

    private val _state = MutableStateFlow(ConvertUiState())
    val state: StateFlow<ConvertUiState> = _state.asStateFlow()

    /** Tizim tanlagichidan kelgan faylni ochadi. */
    fun open(uri: Uri) {
        val context = getApplication<Application>()
        startImport { importer.import(context, uri) }
    }

    /** Ilovaning o'z papkasidagi faylni ochadi (yozuv yoki saqlangan tahrir). */
    fun openPath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            _state.update { it.copy(error = ConvertError.FILE_NOT_FOUND) }
            return
        }
        startImport { importer.open(file) }
    }

    /**
     * Importni ishga tushiradi va natijani holatga qo'llaydi.
     *
     * [work] faqat `Dispatchers.IO` oqimida chaqiriladi: dekodlash ham,
     * nusxalash ham asosiy oqimda bajarilsa, ekran muzlab qolardi.
     */
    private fun startImport(work: () -> ImportOutcome) {
        _state.update {
            it.copy(busy = true, error = null, importFailure = null, outputPath = null, outputFormat = null)
        }
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { work() }
            applyImport(outcome)
        }
    }

    private fun applyImport(outcome: ImportOutcome) {
        when (outcome) {
            is ImportOutcome.Rejected -> _state.update {
                it.copy(
                    busy = false,
                    importFailure = outcome.reason,
                    // Manba haqidagi eski ma'lumot qolmasligi kerak: aks holda
                    // ekranda yangi xato bilan birga eski faylning nomi
                    // ko'rinib, foydalanuvchi nima ochilganini tushunmasdi.
                    sourceName = "",
                    sourceFormat = null,
                    sourceDetected = null,
                    workingWav = null,
                    target = null,
                    targets = emptyList(),
                )
            }

            is ImportOutcome.Ready -> {
                // Manba formati ikki manbadan yig'iladi: konteyner va kodek
                // fayl sarlavhasidan, namuna parametrlari ochilgan WAV dan.
                // Ikkalasi birga kerak — MP3 ning ichida bit chuqurligi
                // saqlanmaydi, uni faqat dekoder aytadi.
                val origin = AudioFormat(
                    container = outcome.detected.container,
                    codec = outcome.detected.codec,
                    sampleRate = outcome.format.sampleRate,
                    channels = outcome.format.channels,
                    bitDepth = outcome.format.bitDepth,
                )
                val decision = FormatSupport.resolve(origin, apiLevel)

                _state.update {
                    it.copy(
                        busy = false,
                        importFailure = null,
                        sourceName = outcome.displayName,
                        sourceDetected = outcome.detected,
                        sourceFormat = outcome.format,
                        durationMs = duration(outcome.frames, outcome.format.sampleRate),
                        workingWav = outcome.wav,
                        preserving = decision is ExportDecision.Preserved,
                        fallbackReason = (decision as? ExportDecision.Fallback)?.reason,
                        // Standart tanlov ro'yxatdagi nusxa bilan almashtiriladi,
                        // aks holda tanlov qatorida hech biri belgilanmasdi.
                        target = FormatSupport.defaultTarget(origin, apiLevel),
                        targets = FormatSupport.convertOptions(origin, apiLevel),
                        error = null,
                        outputPath = null,
                        outputFormat = null,
                    )
                }
            }
        }
    }

    /** Foydalanuvchi tanlagan maqsad format. */
    fun selectTarget(target: AudioFormat) {
        _state.update { it.copy(target = target, outputPath = null, outputFormat = null) }
    }

    /** Xato ko'rsatilganini tasdiqlaydi — ekrandan olib tashlanadi. */
    fun clearError() {
        _state.update { it.copy(error = null, importFailure = null) }
    }

    /** Natija ko'rsatilganini tasdiqlaydi. Faylning o'zi o'chirilmaydi. */
    fun consumeOutput() {
        _state.update { it.copy(outputPath = null, outputFormat = null) }
    }

    fun convert() {
        val current = _state.value
        val wav = current.workingWav
        val target = current.target
        val detected = current.sourceDetected
        val sourceFormat = current.sourceFormat
        if (wav == null || target == null || detected == null || sourceFormat == null) return
        if (current.busy) return

        _state.update { it.copy(busy = true, error = null, outputPath = null, outputFormat = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                run(wav, detected, sourceFormat, target, current.sourceName)
            }
            _state.update {
                when (result) {
                    is Conversion.Done -> it.copy(
                        busy = false,
                        error = null,
                        outputPath = result.path,
                        outputFormat = result.format,
                    )

                    is Conversion.Failed -> it.copy(
                        busy = false,
                        error = result.error,
                        outputPath = null,
                        outputFormat = null,
                    )
                }
            }
        }
    }

    private sealed interface Conversion {
        data class Done(val path: String, val format: AudioFormat) : Conversion

        data class Failed(val error: ConvertError) : Conversion
    }

    /**
     * Konvertatsiyaning o'zi. Faqat fon oqimida chaqiriladi.
     *
     * [override] sifatida tanlangan format beriladi: shunda eksport qiluvchi
     * hech qanday "chekinish" qilmaydi — foydalanuvchi nima tanlagan bo'lsa,
     * fayl shunday yoziladi.
     */
    private fun run(
        wav: File,
        detected: DetectedFormat,
        sourceFormat: AudioFormat,
        target: AudioFormat,
        sourceName: String,
    ): Conversion {
        // Nom manbadan olinadi: "qoshiq.mp3" -> "qoshiq.flac".
        val base = sourceName.substringBeforeLast('.').ifBlank { "ovoz" }
        val destination = store.newOutputFile(base, target.container.extension)

        // Manba formati: konteyner va kodek fayldan, namuna parametrlari
        // ochilgan WAV dan. `override` berilganda bu qiymat qarorga ta'sir
        // qilmaydi, lekin aynan shu yerda turgani to'g'ri — u manbani
        // tasvirlaydi, maqsadni emas.
        val origin = AudioFormat(
            container = detected.container,
            codec = detected.codec,
            sampleRate = sourceFormat.sampleRate,
            channels = sourceFormat.channels,
            bitDepth = sourceFormat.bitDepth,
        )

        return try {
            val exporter = FormatPreservingExporter(apiLevel, AndroidAudioEncoders::open)
            when (val outcome = exporter.export(wav, origin, destination, override = target)) {
                is ExportOutcome.Done -> Conversion.Done(
                    path = outcome.destination.absolutePath,
                    format = outcome.format,
                )

                is ExportOutcome.Unsupported -> {
                    destination.delete()
                    Conversion.Failed(ConvertError.ENCODE_FAILED)
                }
            }
        } catch (error: Exception) {
            // Yiqilish emas, xabar: foydalanuvchi uchun "ilova yopildi" eng
            // yomon natija. Sabab logga tushadi, ekranga esa tushunarli kod.
            Log.w(TAG, "Konvertatsiya bajarilmadi: ${target.container}", error)
            destination.delete()
            Conversion.Failed(ConvertError.WRITE_FAILED)
        }
    }

    private fun duration(frames: Long, sampleRate: Int): Long =
        if (sampleRate <= 0) 0L else frames * 1000L / sampleRate

    private companion object {
        const val TAG = "ConvertViewModel"
    }
}
