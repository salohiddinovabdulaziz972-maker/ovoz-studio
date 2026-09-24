package uz.ovozstudio.app.media.format

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WorkStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Import natijasi.
 *
 * [Ready] da ikkita fayl bor va ularning vazifasi boshqa-boshqa:
 * [Ready.source] — yuklangan asl faylning nusxasi (u saqlanadi, chunki SAF
 * bergan ruxsat jarayon qayta ishga tushgach yo'qolishi mumkin);
 * [Ready.wav] — tahrirlash uchun ochilgan yo'qotishsiz PCM.
 * Eksportda kerak bo'ladigan yagona narsa — [Ready.detected] va [Ready.format].
 */
sealed interface ImportOutcome {

    data class Ready(
        val source: File,
        val wav: File,
        val detected: DetectedFormat,
        val format: AudioFormat,
        val frames: Long,
        val displayName: String,
    ) : ImportOutcome

    data class Rejected(val reason: ImportFailure) : ImportOutcome
}

/**
 * Tayyor faylni (yoki tizim tanlagichidan kelgan `content://` havolani)
 * ilovaga oladi va tahrirlash uchun WAV tayyorlaydi.
 *
 * Ikki yo'l bor va ular ataylab ajratilgan:
 *  - WAV manba hech narsaga aylanmaydi — o'sha faylning o'zi ochiladi, ya'ni
 *    24-bitli yozuv 24-bit bo'lib qoladi (qayta kodlash yo'qotish bermasdan
 *    turib aniqlikni yo'qotardi);
 *  - qolgan hamma format dekodlanadi ([AndroidAudioDecoder]) va vaqtinchalik
 *    WAV ga yoziladi. Ish tugagach natija [FormatPreservingExporter] orqali
 *    **manbaning o'z formatiga** qaytariladi — foydalanuvchi yuklagan format
 *    o'zgarmasdan qaytishi kerak.
 */
class AndroidAudioImporter(private val store: WorkStore) {

    /**
     * Tizim tanlagichidan kelgan havolani nusxalab, [open] ga uzatadi.
     *
     * Nusxa majburiy: `OpenDocument` bergan ruxsat doimiy emas, jarayon
     * qayta ishga tushsa havola o'qilmas bo'lib qolardi.
     */
    fun import(context: Context, uri: Uri, onProgress: (Float) -> Unit = {}): ImportOutcome {
        val displayName = displayName(context, uri)
        val extension = displayName.substringAfterLast('.', "").ifEmpty { "audio" }
        val copy = store.newSourceFile(extension)

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(copy).use { output -> input.copyTo(output, 64 * 1024) }
            } ?: run {
                copy.delete()
                return ImportOutcome.Rejected(ImportFailure.READ_FAILED)
            }
        } catch (error: IOException) {
            copy.delete()
            return ImportOutcome.Rejected(ImportFailure.READ_FAILED)
        } catch (error: SecurityException) {
            copy.delete()
            return ImportOutcome.Rejected(ImportFailure.READ_FAILED)
        }

        val outcome = open(copy, displayName, onProgress)
        // Nusxa faqat ochilgan fayl uchun kerak. Rad etilgan fayl papkada
        // qolib ketsa, har bir xato urinish diskda iz qoldirardi.
        if (outcome is ImportOutcome.Rejected) copy.delete()
        return outcome
    }

    /**
     * Allaqachon diskda turgan faylni ochadi. Fayl o'zgartirilmaydi.
     */
    fun open(source: File, displayName: String = source.name, onProgress: (Float) -> Unit = {}): ImportOutcome {
        val detected = try {
            AudioFormatDetector.detect(source)
        } catch (error: IOException) {
            return ImportOutcome.Rejected(ImportFailure.READ_FAILED)
        } ?: return ImportOutcome.Rejected(ImportFailure.UNKNOWN_FORMAT)

        if (!FormatSupport.canImport(detected)) {
            return ImportOutcome.Rejected(ImportFailure.NO_DECODER)
        }

        if (detected.container == AudioContainer.WAV && detected.codec == AudioCodec.PCM) {
            return openWav(source, detected, displayName)
        }

        return decodeToWav(source, detected, displayName, onProgress)
    }

    /** WAV manba: hech qanday qayta kodlash yo'q. */
    private fun openWav(source: File, detected: DetectedFormat, displayName: String): ImportOutcome {
        val info = try {
            WavFile.readInfo(source)
        } catch (error: IOException) {
            return ImportOutcome.Rejected(ImportFailure.READ_FAILED)
        } catch (error: IllegalArgumentException) {
            return ImportOutcome.Rejected(ImportFailure.UNKNOWN_FORMAT)
        }

        if (info.frames <= 0) return ImportOutcome.Rejected(ImportFailure.EMPTY)

        // Tahrirlagich faqat 16 va 24 bitni biladi. 8 va 32 bitli fayl bu yerda
        // aniq sabab bilan rad etiladi: aks holda xato tahrirlash paytida,
        // umumiy «bajarib bo'lmadi» ko'rinishida chiqardi.
        if (info.bitsPerSample != 16 && info.bitsPerSample != 24) {
            return ImportOutcome.Rejected(ImportFailure.UNSUPPORTED_DEPTH)
        }

        return ImportOutcome.Ready(
            source = source,
            wav = source,
            detected = detected,
            format = AudioFormat(
                container = AudioContainer.WAV,
                codec = AudioCodec.PCM,
                sampleRate = info.sampleRate,
                channels = info.channels,
                bitDepth = info.bitsPerSample,
            ),
            frames = info.frames,
            displayName = displayName,
        )
    }

    /** Siqilgan manba: WAV ga ochiladi, asl nusxa joyida qoladi. */
    private fun decodeToWav(
        source: File,
        detected: DetectedFormat,
        displayName: String,
        onProgress: (Float) -> Unit,
    ): ImportOutcome {
        // Nom `manba-mp3-20260917-…` ko'rinishida bo'ladi: xato bo'lsa
        // qaysi fayldan chiqqanini fayl nomidan ham ko'rish mumkin.
        val target = store.newEditFile("ochilgan-${detected.container.extension}")

        return when (val result = AndroidAudioDecoder().decode(source, target, onProgress)) {
            is AndroidAudioDecoder.Result.Failed -> {
                target.delete()
                ImportOutcome.Rejected(ImportFailure.NO_DECODER)
            }

            is AndroidAudioDecoder.Result.Done -> {
                if (result.frames <= 0) {
                    target.delete()
                    return ImportOutcome.Rejected(ImportFailure.EMPTY)
                }
                ImportOutcome.Ready(
                    source = source,
                    wav = result.wav,
                    detected = detected,
                    format = result.format,
                    frames = result.frames,
                    displayName = displayName,
                )
            }
        }
    }

    /**
     * Faylning foydalanuvchi ko'radigan nomi. Provayder nom bermasa — havola
     * oxiridagi qism, u ham bo'lmasa — umumiy nom.
     */
    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "yuklangan"
    }
}
