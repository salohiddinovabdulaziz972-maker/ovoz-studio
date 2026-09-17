package uz.ovozstudio.app.media.format

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import java.io.Closeable
import java.io.File

/**
 * PCM namunalarini faylga kodlovchi umumiy shartnoma.
 *
 * Namunalar butun son ko'rinishida beriladi va ular faylning bit
 * chuqurligida, ishorali bo'ladi (`16 bit` uchun −32768…32767). Float orqali
 * o'tkazish ataylab qilinmaydi: 24-bit tovushda u chegaradagi qiymatlarni
 * bir birlikka surib yuborardi, formatni saqlab qolish talabi esa aynan
 * aniqlikni talab qiladi.
 */
interface AudioEncoder : Closeable {

    /** Yozilayotgan faylning formati. */
    val format: AudioFormat

    /**
     * [samples] massivining birinchi `frames * kanal soni` ta namunasini
     * yozadi. Massiv qisqaroq bo'lsa — bor qismi yoziladi.
     */
    fun write(samples: IntArray, frames: Int)

    /** Sarlavhalarni yakunlaydi va faylni yopadi. Belgilangan bayt hajmi
     *  faqat shundan keyin to'g'ri bo'ladi. */
    fun finish()
}

/**
 * PCM WAV yozadi. Formatni saqlashda eng ishonchli yo'l: hech qanday
 * qayta kodlash yo'q, namunalar bayt-bayt o'z holida qoladi.
 */
class WavPcmEncoder(
    file: File,
    override val format: AudioFormat,
) : AudioEncoder {

    private val writer = WavWriter(
        file = file,
        sampleRate = format.sampleRate,
        channels = format.channels,
        bitDepth = bitDepthOf(format.bitDepth),
    )

    override fun write(samples: IntArray, frames: Int) = writer.writeIntegers(samples, frames)

    override fun finish() = writer.close()

    override fun close() = writer.close()

    private companion object {
        /** WAV'da faqat 16 va 24 bit bor; noma'lum qiymat 16 deb olinadi. */
        fun bitDepthOf(bits: Int?): BitDepth = when (bits) {
            24 -> BitDepth.BIT_24
            else -> BitDepth.BIT_16
        }
    }
}
