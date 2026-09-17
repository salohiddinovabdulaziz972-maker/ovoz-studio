package uz.ovozstudio.app.ui.tag

import android.app.Application
import android.net.Uri
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
import uz.ovozstudio.app.media.format.AudioContainer
import uz.ovozstudio.app.media.format.AudioFormatDetector
import uz.ovozstudio.app.media.tag.AudioTags
import uz.ovozstudio.app.media.tag.Id3v2Reader
import uz.ovozstudio.app.media.tag.Mp3Tagger
import uz.ovozstudio.app.media.tag.TagDraft
import uz.ovozstudio.app.media.tag.TagError
import uz.ovozstudio.app.media.tag.TagWriteException
import uz.ovozstudio.app.media.tag.supportsId3Tags
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

/** Teg ekranidagi xatolar. Matn emas, kod — matnni UI joriy tilda yozadi. */
enum class TagUiError {
    /** Fayl tanlanmagan. */
    NO_FILE,

    /** Bu formatda ID3 tegi yo'q. */
    UNSUPPORTED_FORMAT,

    /** Birorta maydon to'ldirilmagan. */
    EMPTY_TAGS,

    /** Muqova rasmi juda katta. */
    COVER_TOO_LARGE,

    /** Yozib bo'lmadi. */
    WRITE_FAILED,

    /** Fayl topilmadi (o'chirilgan yoki ko'chirilgan). */
    FILE_NOT_FOUND,
}

/** Tahrirlanadigan maydonlar. Ekran shu nom bo'yicha yorliq topadi. */
enum class TagField {
    TITLE,
    ARTIST,
    ALBUM,
    YEAR,
    GENRE,
    TRACK,
}

data class TagUiState(
    val sourcePath: String? = null,
    val sourceName: String = "",
    /** Fayl formati nomi (MP3, WAV…) — ekranda ko'rinadi. */
    val sourceFormat: String = "",
    /** Fayl formati ID3 tegsiz (masalan WAV) — yozish mumkin emas. */
    val supported: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
    /** Foydalanuvchi shu sessiyada tanlagan rasmning nomi; bo'sh bo'lsa — nom ma'lum emas. */
    val coverName: String = "",
    val coverSize: Long = 0L,
    val busy: Boolean = false,
    val outputPath: String? = null,
    val error: TagUiError? = null,
) {
    val hasCover: Boolean get() = coverSize > 0

    /**
     * Ulashish uchun fayl: yangi saqlangan natija bo'lsa — u, aks holda
     * manba. Ya'ni faylni teg yozmasdan ham ulashish mumkin.
     */
    val sharePath: String? get() = outputPath ?: sourcePath

    val canWrite: Boolean get() = sourcePath != null && supported && !busy
}

/**
 * ID3 teg muharriri.
 *
 * Ikki qoida:
 *
 *   1. **Manba fayl o'zgarmaydi.** Teg yangi faylga yoziladi — naqsh ilova
 *      bo'ylab bir xil (kesish, konvertatsiya ham shunday). Telefonda
 *      «bekor qilish» tugmasi yo'q, shuning uchun asl nusxa joyida qolishi
 *      kerak.
 *   2. **Maydonlar mavjud teg bilan to'ldiriladi.** Yozuvchi tegni
 *      butunlay almashtiradi: bo'sh maydon — yo'q maydon. Agar ekran
 *      fayldagi tegni o'qimasa, foydalanuvchi faqat muqova qo'shmoqchi
 *      bo'lib, nomni jimgina o'chirib qo'yardi.
 */
class TagViewModel(application: Application) : AndroidViewModel(application) {

    private val store = RecordingStore(application)

    private val _state = MutableStateFlow(TagUiState())
    val state: StateFlow<TagUiState> = _state.asStateFlow()

    /**
     * Ilova papkasidagi MP3 fayllar.
     *
     * Ro'yxat ekranda ko'rinadi, chunki tizim tanlagichi bu papkani
     * ko'rmaydi: konvertor va audio-kitob fayllari shu yerda tug'iladi va
     * foydalanuvchi ularni boshqa yo'l bilan topa olmasdi.
     */
    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    init {
        refreshFiles()
    }

    /** Ro'yxatni qayta o'qiydi: yangi fayl paydo bo'lgan bo'lishi mumkin. */
    fun refreshFiles() {
        _files.value = store.listMp3()
    }

    /**
     * Muqova baytlari holatda emas, shu yerda saqlanadi.
     *
     * Sabab texnik: `ByteArray` ma'lumot sinfida bo'lsa, har bir yangi
     * holat uni «boshqa» deb hisoblab, Compose butun ekranni qayta
     * chizardi. Ekranga esa faqat o'lcham va nom kerak.
     */
    private var cover: ByteArray? = null
    private var coverMime = "image/jpeg"

    /** Ilovaning o'z papkasidagi faylni ochadi. */
    fun openPath(path: String) {
        val file = File(path)
        if (!file.exists()) {
            _state.update { it.copy(error = TagUiError.FILE_NOT_FOUND) }
            return
        }
        load(file)
    }

    /**
     * Tizim tanlagichidan kelgan faylni oladi.
     *
     * Fayl nusxasi ilovaning papkasiga ko'chiriladi: teg butun fayl
     * baytlari bo'yicha yoziladi, oqim (stream) bilan bu mumkin emas.
     */
    fun open(uri: Uri) {
        val context = getApplication<Application>()
        _state.update { it.copy(busy = true, error = null, outputPath = null) }

        viewModelScope.launch {
            val copy = withContext(Dispatchers.IO) {
                val name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
                val extension = name.substringAfterLast('.', "").ifBlank { "mp3" }
                val target = store.newSourceFile(extension)
                val ok = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    } != null
                }.getOrDefault(false)

                if (ok) target else {
                    runCatching { target.delete() }
                    null
                }
            }

            if (copy == null) {
                _state.update { it.copy(busy = false, error = TagUiError.FILE_NOT_FOUND) }
                return@launch
            }
            load(copy)
        }
    }

    /** Maydonni o'zgartiradi. */
    fun setField(field: TagField, value: String) {
        _state.update { current ->
            when (field) {
                TagField.TITLE -> current.copy(title = value)
                TagField.ARTIST -> current.copy(artist = value)
                TagField.ALBUM -> current.copy(album = value)
                TagField.YEAR -> current.copy(year = value)
                TagField.GENRE -> current.copy(genre = value)
                TagField.TRACK -> current.copy(track = value)
            }
        }
    }

    /**
     * Muqova rasmini oladi.
     *
     * O'qish **chegara bilan**: rasm to'liq xotiraga ko'tarilmaydi, chunki
     * foydalanuvchi 40 megapikselli suratni tanlashi mumkin va uni butunlay
     * o'qish ilovani yiqitardi. Chegaradan oshsa — o'qish to'xtaydi va
     * tushunarli xabar chiqadi.
     */
    fun setCover(uri: Uri) {
        val context = getApplication<Application>()
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val name = uri.lastPathSegment.orEmpty().substringAfterLast('/')
                val mime = context.contentResolver.getType(uri)?.let(::normalizeMime)
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        readCapped(input, Mp3Tagger.MAX_COVER_BYTES)
                    }
                }.getOrNull()
                name to (bytes to mime)
            }

            val (name, loaded) = result
            val bytes = loaded.first
            if (bytes == null) {
                _state.update { it.copy(busy = false, error = TagUiError.COVER_TOO_LARGE) }
                return@launch
            }

            cover = bytes
            // MIME noma'lum bo'lsa JPEG qoladi: APIC ichida u yozilishi shart,
            // bo'sh qoldirilsa ba'zi pleyerlar rasmni ko'rsatmaydi.
            coverMime = loaded.second ?: "image/jpeg"
            _state.update {
                it.copy(
                    busy = false,
                    coverName = name.ifBlank { "rasm" },
                    coverSize = bytes.size.toLong(),
                    outputPath = null,
                )
            }
        }
    }

    /** Muqovani olib tashlaydi. */
    fun clearCover() {
        cover = null
        coverMime = "image/jpeg"
        _state.update { it.copy(coverName = "", coverSize = 0L, outputPath = null) }
    }

    /** Xato ko'rsatilganini tasdiqlaydi. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Natija ko'rsatilganini tasdiqlaydi. Faylning o'zi o'chirilmaydi. */
    fun consumeOutput() {
        _state.update { it.copy(outputPath = null) }
    }

    /** Tegni yangi faylga yozadi. */
    fun save() {
        val current = _state.value
        if (current.busy) return

        val path = current.sourcePath
        if (path == null) {
            _state.update { it.copy(error = TagUiError.NO_FILE) }
            return
        }
        if (!current.supported) {
            _state.update { it.copy(error = TagUiError.UNSUPPORTED_FORMAT) }
            return
        }

        val source = File(path)
        if (!source.exists()) {
            _state.update { it.copy(error = TagUiError.FILE_NOT_FOUND) }
            return
        }

        val draft = draftOf(current)
        // Tekshiruv yozishdan OLDIN: foydalanuvchi kutib, keyin «yozilmadi»
        // degan xabarni ko'rmasin. Sabab ko'p hollarda maydonning o'zida.
        val failure = draft.validationError(fileSelected = true)
        if (failure != null) {
            _state.update { it.copy(error = uiError(failure)) }
            return
        }

        _state.update { it.copy(busy = true, error = null, outputPath = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { write(source, draft) }
            _state.update {
                when (result) {
                    is Write.Done -> it.copy(busy = false, error = null, outputPath = result.path)
                    is Write.Failed -> it.copy(busy = false, error = result.error)
                }
            }
            // Yangi fayl papkada paydo bo'ldi — ro'yxatda ko'rinsin.
            if (result is Write.Done) refreshFiles()
        }
    }

    private sealed interface Write {
        data class Done(val path: String) : Write

        data class Failed(val error: TagUiError) : Write
    }

    /**
     * Tegni faylga yozadi. Faqat fon oqimida chaqiriladi.
     *
     * Yangi fayl manba bilan bir papkada, o'sha nom bilan yasaladi —
     * pleyerlarda ham, fayl menejerida ham tanish ko'rinadi.
     */
    private fun write(source: File, draft: TagDraft): Write {
        val base = source.name.substringBeforeLast('.').ifBlank { "ovoz" }
        val destination = store.newOutputFile(base, "mp3")
        return try {
            Mp3Tagger.write(source, destination, draft.toTags())
            Write.Done(destination.absolutePath)
        } catch (error: TagWriteException) {
            // Sabab logga, tushunarli xabar ekranga: «ilova yopildi» eng
            // yomon natija, shuning uchun bu yerda hech narsa otilmaydi.
            Log.w(TAG, "Teg yozilmadi: ${source.name}", error)
            destination.delete()
            Write.Failed(TagUiError.WRITE_FAILED)
        } catch (error: Exception) {
            Log.w(TAG, "Kutilmagan xato: ${source.name}", error)
            destination.delete()
            Write.Failed(TagUiError.WRITE_FAILED)
        }
    }

    /** Faylni ochadi: format aniqlanadi, mavjud teg o'qiladi. */
    private fun load(file: File) {
        _state.update { it.copy(busy = true, error = null, outputPath = null) }

        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val container = runCatching { AudioFormatDetector.detect(file) }
                    .getOrNull()
                    ?.container
                val readable = container != null && supportsId3Tags(container)
                Loaded(
                    container = container,
                    tags = if (readable) runCatching { Id3v2Reader.read(file) }.getOrNull() else null,
                    readable = readable,
                )
            }

            val tags = loaded.tags ?: AudioTags()
            cover = tags.cover
            coverMime = tags.coverMime

            _state.update {
                it.copy(
                    busy = false,
                    sourcePath = file.absolutePath,
                    sourceName = file.name,
                    sourceFormat = loaded.container?.displayName.orEmpty(),
                    supported = loaded.readable,
                    title = tags.title,
                    artist = tags.artist,
                    album = tags.album,
                    year = tags.year,
                    genre = tags.genre,
                    track = tags.trackText.orEmpty(),
                    // Fayldagi muqovaning nomi yo'q — u teg ichida yashaydi.
                    coverName = "",
                    coverSize = tags.cover?.size?.toLong() ?: 0L,
                    // Format noma'lum yoki ID3 ni qo'llamaydi: sabab darhol
                    // aytiladi, foydalanuvchi «Saqlash» ni bosib kutmasin.
                    error = if (loaded.container != null && !loaded.readable) {
                        TagUiError.UNSUPPORTED_FORMAT
                    } else {
                        null
                    },
                )
            }
        }
    }

    private class Loaded(
        val container: AudioContainer?,
        val tags: AudioTags?,
        val readable: Boolean,
    )

    private fun draftOf(state: TagUiState): TagDraft = TagDraft(
        title = state.title,
        artist = state.artist,
        album = state.album,
        year = state.year,
        genre = state.genre,
        track = state.track,
        cover = cover,
        coverMime = coverMime,
    )

    private fun uiError(error: TagError): TagUiError = when (error) {
        TagError.NO_FILE -> TagUiError.NO_FILE
        TagError.UNSUPPORTED_FORMAT -> TagUiError.UNSUPPORTED_FORMAT
        TagError.EMPTY_TAGS -> TagUiError.EMPTY_TAGS
        TagError.COVER_TOO_LARGE -> TagUiError.COVER_TOO_LARGE
        TagError.WRITE_FAILED -> TagUiError.WRITE_FAILED
    }

    /** MIME nomini tekislaydi: `image/jpg` noto'g'ri yozuv, APIC esa aniq nom kutadi. */
    private fun normalizeMime(mime: String): String? {
        val clean = mime.trim().lowercase()
        if (!clean.startsWith("image/")) return null
        return if (clean == "image/jpg") "image/jpeg" else clean
    }

    /**
     * Oqimdan ko'pi bilan [limit] bayt o'qiydi; oshib ketsa `null`.
     *
     * `readBytes()` ishlatilmaydi: u butun rasmni xotiraga ko'taradi va
     * chegara tekshiruvi undan keyin ma'nosiz bo'lardi.
     */
    private fun readCapped(input: InputStream, limit: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read <= 0) break
            if (out.size() + read > limit) return null
            out.write(chunk, 0, read)
        }
        return out.toByteArray()
    }

    private companion object {
        const val TAG = "TagViewModel"
    }
}
