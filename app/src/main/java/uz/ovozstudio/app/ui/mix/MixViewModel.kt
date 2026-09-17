package uz.ovozstudio.app.ui.mix

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.Recording
import uz.ovozstudio.app.media.RecordingStore
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.mix.AudioMixer
import uz.ovozstudio.app.media.mix.MixEditor
import uz.ovozstudio.app.media.mix.MixError
import uz.ovozstudio.app.media.mix.MixProjectStore
import uz.ovozstudio.app.media.mix.MixTrackText
import uz.ovozstudio.app.media.mix.MixTrack
import uz.ovozstudio.app.media.mix.WavMixSource
import java.io.File

/**
 * Ko'p yo'lli aralashtirish ekranining holati.
 *
 * Uch qoida:
 *
 *   1. **Loyiha avtomatik saqlanadi.** Har bir o'zgarishdan keyin — yo'l
 *      qo'shilsa, sozlama tahrirlansa, orqaga qaytarilsa. Sakkiz yo'lning
 *      balandligi, joylashuvi va siljishi — bu daqiqalab mehnat; ilova
 *      yopilib qolganda uni yo'qotish eng qimmat xato bo'lardi. Shuning
 *      uchun «saqlash» tugmasi yo'q — saqlash doimiy.
 *   2. **Manbalar WAV.** Mikser faylni ikki marta o'qiydi (avval cho'qqini
 *      o'lchaydi, keyin yozadi), ya'ni manba qayta o'qiladigan fayl bo'lishi
 *      shart. Boshqa format avval konvertor orqali WAV ga o'tkaziladi — u
 *      shu ilovaning o'zida.
 *   3. **Chastotalar teng bo'lishi kerak.** Har xil chastotali yo'lni jimgina
 *      qayta namunalash sifatsiz bo'lardi va buni faqat quloq bilan payqash
 *      mumkin — shuning uchun bu ochiq xato ([MixUiError.SAMPLE_RATE_MISMATCH]).
 */
class MixViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)

    /**
     * Loyiha fayli ilovaning **ichki** papkasida saqlanadi.
     *
     * Bu foydalanuvchi fayli emas — ilovaning holati, shuning uchun
     * `OvozStudio` papkasiga tushmaydi: u yerdagi har bir fayl fayl
     * menejerida ko'rinadi va uni o'chirib yuborish mumkin.
     */
    private val projectStore = MixProjectStore(File(application.filesDir, PROJECT_FILE))

    private val editor = MixEditor()

    private val _state = MutableStateFlow(MixUiState())
    val state: StateFlow<MixUiState> = _state.asStateFlow()

    /** Qo'shish uchun yozuvlar — ilova papkasidagi WAV fayllar. */
    private val _files = MutableStateFlow<List<Recording>>(emptyList())
    val files: StateFlow<List<Recording>> = _files.asStateFlow()

    /** Fayl nomi bo'yicha ma'lumot: uzunlik, chastota, kanal soni. */
    private var recordings: Map<String, Recording> = emptyMap()

    /**
     * Saqlash navbatini tartibga soladi.
     *
     * Saqlash fon oqimida ketadi (har bosishda asosiy oqimni to'xtatmaslik
     * uchun). Loyiha navbat ichida o'qiladi, ya'ni navbatning oxirgi
     * egasi eng yangi holatni yozadi: sekin kelgan eski nusxa yangisini
     * bosib ketmaydi.
     */
    private val saveLock = Mutex()

    init {
        restore()
    }

    /** Saqlangan loyihani tiklaydi. */
    private fun restore() {
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { projectStore.load() }
            editor.reset(project)
            refreshFiles()
        }
    }

    /**
     * Ro'yxatni qayta o'qiydi.
     *
     * Ekran har ochilganda chaqiriladi: shu orada yangi yozuv paydo bo'lgan
     * yoki fayl o'chirilgan bo'lishi mumkin — ikkinchisi yo'llarni «topilmadi»
     * holatiga o'tkazadi.
     */
    fun refreshFiles() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { store.list() }
            recordings = list.associateBy { it.file.name }
            _files.value = list
            rebuild()
        }
    }

    /** Ro'yxatdagi faylni loyihaga qo'shadi. */
    fun add(recording: Recording) {
        val name = recording.file.name
        if (editor.project.tracks.any { it.file == name }) {
            // Bir fayl ikki marta qo'shilsa, ekranda bir xil ikki qator
            // paydo bo'lardi va foydalanuvchi ularni ajrata olmasdi.
            fail(MixUiError.ALREADY_ADDED)
            return
        }
        if (!editor.add(MixTrack(name = recording.title, file = name))) {
            fail(MixUiError.TOO_MANY)
            return
        }
        rebuild()
        persist()
    }

    /** Yo'lni loyihadan olib tashlaydi. */
    fun remove(index: Int) {
        if (editor.remove(index)) {
            rebuild()
            persist()
        }
    }

    fun setGain(index: Int, text: String) {
        val clean = MixTrackText.sanitizeGain(text)
        edit(index, ui = { it.copy(gainText = clean) }, model = { it.copy(gainDb = MixTrackText.parseGain(clean)) })
    }

    fun nudgeGain(index: Int, delta: Double) {
        val text = _state.value.tracks.getOrNull(index)?.gainText ?: return
        setGain(index, MixTrackText.nudgeGain(text, delta))
    }

    fun setPan(index: Int, text: String) {
        val clean = MixTrackText.sanitizePan(text)
        edit(index, ui = { it.copy(panText = clean) }, model = { it.copy(pan = MixTrackText.parsePan(clean)) })
    }

    fun nudgePan(index: Int, delta: Double) {
        val text = _state.value.tracks.getOrNull(index)?.panText ?: return
        setPan(index, MixTrackText.nudgePan(text, delta))
    }

    fun setOffset(index: Int, text: String) {
        val clean = MixTrackText.sanitizeOffset(text)
        edit(index, ui = { it.copy(offsetText = clean) }, model = { it.copy(offsetMs = MixTrackText.parseOffset(clean)) })
    }

    fun nudgeOffset(index: Int, delta: Double) {
        val text = _state.value.tracks.getOrNull(index)?.offsetText ?: return
        setOffset(index, MixTrackText.nudgeOffset(text, delta))
    }

    fun toggleMute(index: Int) {
        edit(index, ui = { it.copy(muted = !it.muted) }, model = { it.copy(muted = !it.muted) })
    }

    fun toggleSolo(index: Int) {
        edit(index, ui = { it.copy(solo = !it.solo) }, model = { it.copy(solo = !it.solo) })
    }

    fun setMaster(text: String) {
        val clean = MixTrackText.sanitizeGain(text)
        editor.setMaster(MixTrackText.parseGain(clean))
        _state.update {
            it.copy(
                masterText = clean,
                canUndo = editor.canUndo,
                canRedo = editor.canRedo,
                outputPath = null,
                appliedGainDb = 0f,
            )
        }
        persist()
    }

    fun nudgeMaster(delta: Double) {
        setMaster(MixTrackText.nudgeGain(_state.value.masterText, delta))
    }

    fun undo() {
        if (editor.undo()) {
            rebuild()
            persist()
        }
    }

    fun redo() {
        if (editor.redo()) {
            rebuild()
            persist()
        }
    }

    /** Xato ko'rsatilganini tasdiqlaydi. */
    fun clearError() {
        _state.update { it.copy(error = null, errorDetail = "") }
    }

    /** Natija ko'rsatilganini tasdiqlaydi. Faylning o'zi o'chirilmaydi. */
    fun consumeOutput() {
        _state.update { it.copy(outputPath = null, appliedGainDb = 0f) }
    }

    /** Yo'llarni bitta faylga aralashtiradi. */
    fun mix() {
        val current = _state.value
        if (current.busy) return

        val inputs = buildInputs() ?: return
        val masterGain = editor.project.masterGain

        _state.update {
            it.copy(busy = true, error = null, errorDetail = "", outputPath = null, appliedGainDb = 0f)
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { render(inputs, masterGain) }
            _state.update { state ->
                when (result) {
                    is Mix.Done -> state.copy(
                        busy = false,
                        outputPath = result.path,
                        outputMs = result.durationMs,
                        appliedGainDb = result.appliedGainDb,
                    )

                    is Mix.Failed -> state.copy(busy = false, error = result.error)
                }
            }
            // Yangi fayl papkada paydo bo'ldi — ro'yxatda ham ko'rinsin.
            if (result is Mix.Done) refreshFiles()
        }
    }

    /**
     * Yo'llarni mikser uchun tayyorlaydi; xato bo'lsa uni ko'rsatadi va `null`.
     */
    private fun buildInputs(): List<AudioMixer.Input>? {
        val project = editor.project
        if (project.isEmpty) {
            fail(MixUiError.EMPTY)
            return null
        }

        val inputs = ArrayList<AudioMixer.Input>(project.tracks.size)
        for (track in project.tracks) {
            val recording = recordings[track.file]
            if (recording == null) {
                fail(MixUiError.FILE_MISSING, track.file)
                return null
            }
            inputs += AudioMixer.Input(WavMixSource(recording.file), track)
        }

        val failure = AudioMixer.validate(inputs)
        if (failure != null) {
            // Mikser manbalarni ochib qo'ydi — xato bo'lsa ular yopilishi kerak.
            inputs.forEach { runCatching { it.source.close() } }
            fail(map(failure), rateDetail(inputs, failure))
            return null
        }
        return inputs
    }

    private sealed interface Mix {
        data class Done(val path: String, val durationMs: Long, val appliedGainDb: Float) : Mix

        data class Failed(val error: MixUiError) : Mix
    }

    /**
     * Aralashmani yozadi. Faqat fon oqimida chaqiriladi.
     *
     * Chuqurlik manbalarning eng kattasi bo'yicha olinadi: 24-bitli yo'l
     * 16-bitga tushirilsa, foydalanuvchi buni so'ramagan holda aniqlikni
     * yo'qotardi.
     */
    private fun render(inputs: List<AudioMixer.Input>, masterGain: Float): Mix {
        val destination = store.newOutputFile("aralashma", "wav")
        return try {
            val rate = AudioMixer.outputSampleRate(inputs)
            val depth = outputDepth()
            WavWriter(destination, rate, AudioMixer.OUTPUT_CHANNELS, depth).use { writer ->
                val result = AudioMixer.render(inputs, masterGain) { samples, frames ->
                    writer.write(samples, frames)
                }
                Mix.Done(
                    path = destination.absolutePath,
                    durationMs = if (rate <= 0) 0L else result.frames * 1000L / rate,
                    appliedGainDb = MixTrackText.gainToDb(result.appliedGain),
                )
            }
        } catch (error: Exception) {
            // Sabab logga, tushunarli xabar ekranga: aralashtirish o'rtasida
            // yiqilib qolish eng yomon natija bo'lardi.
            Log.w(TAG, "Aralashma yozilmadi", error)
            destination.delete()
            Mix.Failed(MixUiError.WRITE_FAILED)
        } finally {
            inputs.forEach { runCatching { it.source.close() } }
        }
    }

    /** Manbalar orasidagi eng katta bit chuqurligi — loyihadagi fayllar bo'yicha. */
    private fun outputDepth(): BitDepth {
        val depths = editor.project.tracks.mapNotNull { track ->
            val recording = recordings[track.file] ?: return@mapNotNull null
            BitDepth.of(recording.info.bitsPerSample)
        }
        return if (depths.any { it == BitDepth.BIT_24 }) BitDepth.BIT_24 else BitDepth.BIT_16
    }

    /**
     * Yo'lning sozlamasini o'zgartiradi: model (tarix bilan) va ekran matni.
     *
     * Natija fayli bekor qilinadi: u endi joriy sozlamalarga mos kelmaydi va
     * eski natijani ko'rsatib turish foydalanuvchini chalg'itardi.
     */
    private fun edit(index: Int, ui: (MixTrackUi) -> MixTrackUi, model: (MixTrack) -> MixTrack) {
        editor.update(index) { model(it) }
        _state.update { current ->
            val tracks = current.tracks.toMutableList()
            val track = tracks.getOrNull(index) ?: return@update current
            tracks[index] = ui(track)
            current.copy(
                tracks = tracks,
                canUndo = editor.canUndo,
                canRedo = editor.canRedo,
                outputPath = null,
                appliedGainDb = 0f,
            )
        }
        persist()
    }

    /** Modeldan ekran holatini qayta yasaydi (orqaga qaytarish, qo'shish, o'chirish). */
    private fun rebuild() {
        _state.update { current ->
            current.copy(
                tracks = editor.project.tracks.map { track -> uiTrack(track) },
                masterText = MixTrackText.formatGain(editor.project.masterGainDb),
                canUndo = editor.canUndo,
                canRedo = editor.canRedo,
                outputPath = null,
                appliedGainDb = 0f,
            )
        }
    }

    private fun uiTrack(track: MixTrack): MixTrackUi {
        val recording = recordings[track.file]
        return MixTrackUi(
            name = track.name,
            file = track.file,
            durationMs = recording?.durationMs ?: 0L,
            sampleRate = recording?.info?.sampleRate ?: 0,
            channels = recording?.info?.channels ?: 0,
            gainText = MixTrackText.formatGain(track.gainDb),
            panText = MixTrackText.formatPan(track.pan),
            offsetText = MixTrackText.formatOffset(track.offsetMs),
            muted = track.muted,
            solo = track.solo,
            missing = recording == null,
        )
    }

    private fun persist() {
        viewModelScope.launch(Dispatchers.IO) {
            saveLock.withLock {
                runCatching { projectStore.save(editor.project) }
                    .onFailure { Log.w(TAG, "Loyiha saqlanmadi", it) }
            }
        }
    }

    private fun fail(error: MixUiError, detail: String = "") {
        _state.update { it.copy(error = error, errorDetail = detail) }
    }

    private fun map(error: MixError): MixUiError = when (error) {
        MixError.EMPTY -> MixUiError.EMPTY
        MixError.SAMPLE_RATE_MISMATCH -> MixUiError.SAMPLE_RATE_MISMATCH
        MixError.UNSUPPORTED_CHANNELS -> MixUiError.UNSUPPORTED_CHANNELS
        MixError.NO_AUDIBLE_TRACK -> MixUiError.NO_AUDIBLE_TRACK
    }

    /** Chastotalar har xil bo'lsa — qaysilari ekanini aytadi (tuzatish uchun kerak). */
    private fun rateDetail(inputs: List<AudioMixer.Input>, failure: MixError): String =
        if (failure != MixError.SAMPLE_RATE_MISMATCH) {
            ""
        } else {
            inputs.map { it.source.sampleRate }.distinct().sorted().joinToString(", ")
        }

    private companion object {
        const val TAG = "MixViewModel"
        const val PROJECT_FILE = "aralashma-loyiha.properties"
    }
}
