package uz.ovozstudio.app.media.format

import android.content.Context
import android.net.Uri
import uz.ovozstudio.app.media.WorkStore
import java.io.File

/** [AudioOpener] natijasi. */
sealed interface OpenResult {

    /**
     * Fayl tahrirlashga tayyor.
     *
     * @property source yuklangan asl faylning nusxasi (o'zgartirilmaydi).
     * @property wav tahrirlash uchun ochilgan yo'qotishsiz PCM. WAV manba
     *   uchun bu — o'sha nusxaning o'zi.
     * @property origin natija yoziladigan format: konteyner va kodek manbadan,
     *   qolgani ochilgan fayldan.
     */
    data class Opened(
        val source: File,
        val wav: File,
        val origin: AudioFormat,
        val displayName: String,
        val frames: Long,
    ) : OpenResult {
        val durationMs: Long
            get() = if (origin.sampleRate <= 0) 0L else frames * 1000L / origin.sampleRate
    }

    /**
     * Fayl ochilmadi. [formatName] faqat format bilan bog'liq sabablarda
     * to'ldiriladi; [displayName] — qaysi fayl rad etilgani (bir nechta fayl
     * tanlanganda foydalanuvchi shuni bilishi kerak).
     */
    data class Refused(
        val reason: ImportFailure,
        val formatName: String = "",
        val displayName: String = "",
    ) : OpenResult
}

/**
 * Faylni tahrirlash uchun ochadi va **qat'iy format qoidasini** tekshiradi.
 *
 * Tahrirlash ekranlarining hammasi (kesish, o'chirish, birlashtirish) shu
 * sinfdan o'tadi, shuning uchun qoida bitta joyda turadi: natija aynan
 * yuklangan formatda qaytarilishi mumkin bo'lmagan fayl ochilmaydi.
 *
 * Faqat fon oqimidan chaqiriladi: ichida fayl nusxalash va dekodlash bor.
 */
class AudioOpener(store: WorkStore, private val apiLevel: Int) {

    private val importer = AndroidAudioImporter(store)

    fun open(context: Context, uri: Uri): OpenResult =
        when (val outcome = importer.import(context, uri)) {
            is ImportOutcome.Rejected ->
                OpenResult.Refused(outcome.reason, displayName = importer.displayName(context, uri))
            is ImportOutcome.Ready -> check(outcome)
        }

    private fun check(ready: ImportOutcome.Ready): OpenResult {
        val rate = ready.format.sampleRate
        val durationMs = if (rate <= 0) 0L else ready.frames * 1000L / rate
        val origin = StrictFormat.originOf(
            detected = ready.detected,
            decoded = ready.format,
            sourceBytes = ready.source.length(),
            durationMs = durationMs,
        )

        val blocker = StrictFormat.blocker(origin, apiLevel)
        if (blocker != null) {
            // Rad etilgan fayl diskda qolib ketmasligi kerak.
            discard(ready)
            val reason = when (blocker) {
                FallbackReason.NO_ENCODER -> ImportFailure.CANNOT_WRITE_FORMAT
                FallbackReason.API_TOO_OLD -> ImportFailure.OLD_ANDROID
                FallbackReason.NO_DECODER -> ImportFailure.NO_DECODER
            }
            return OpenResult.Refused(reason, StrictFormat.label(origin), ready.displayName)
        }

        return OpenResult.Opened(
            source = ready.source,
            wav = ready.wav,
            origin = origin,
            displayName = ready.displayName,
            frames = ready.frames,
        )
    }

    /** Havoladagi faylning foydalanuvchi ko'radigan nomi. */
    fun displayName(context: Context, uri: Uri): String = importer.displayName(context, uri)

    /** Ochilgan fayllarni tashlaydi. Ikkalasi bir fayl bo'lsa (WAV) — bir marta. */
    fun discard(source: File, wav: File) {
        runCatching { source.delete() }
        if (wav.absolutePath != source.absolutePath) runCatching { wav.delete() }
    }

    private fun discard(ready: ImportOutcome.Ready) = discard(ready.source, ready.wav)
}
