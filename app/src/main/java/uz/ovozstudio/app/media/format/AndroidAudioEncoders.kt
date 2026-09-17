package uz.ovozstudio.app.media.format

import java.io.File

/**
 * Tizim kodlovchilarini (AAC, Opus) `FormatPreservingExporter` ga ulaydi.
 *
 * WAV, FLAC va MP3 o'zimizniki — ular eksport qiluvchining ichida yaratiladi.
 * AAC va Opus esa faqat `MediaCodec` orqali mavjud, shuning uchun ular shu
 * zavod orqali keladi: bu ajratish tufayli eksport qiluvchi sinf sof JVM
 * sharoitida ham tekshirilaveradi ([FormatPreservingExporter] ning
 * konstruktori `extraEncoder`siz quriladi).
 */
object AndroidAudioEncoders {

    /**
     * Kodlovchi yaratadi; bu format bu qurilmada yozilmasa `null`.
     *
     * Hech qanday istisno tashqariga chiqmaydi: kodlovchi sozlanmasligi —
     * kutilgan holat (qurilmada Opus kodlovchisi umuman bo'lmasligi mumkin),
     * u yiqilish emas, "chekinish taklif qilish" degani.
     *
     * Chala fayl qoldirilmaydi: `MediaCodecEncoder` muxerni qurilish paytida
     * ochadi, ya'ni kodlovchi ishga tushmasa ham fayl paydo bo'lib qolardi.
     */
    fun open(target: AudioFormat, destination: File): AudioEncoder? {
        if (target.codec != AudioCodec.AAC && target.codec != AudioCodec.OPUS) return null

        val encoder = runCatching { MediaCodecEncoder(destination, target) }.getOrNull()
        if (encoder == null) destination.delete()
        return encoder
    }
}
