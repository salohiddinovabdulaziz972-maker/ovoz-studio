package uz.ovozstudio.app.media.mix

import uz.ovozstudio.app.media.WavSampleReader
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Aralashmaga kiradigan ovoz manbasi.
 *
 * Manba **qayta o'qiladigan** bo'lishi shart: mikser faylni ikki marta
 * o'qiydi — birinchi o'tishda cho'qqi o'lchanadi, ikkinchisida yoziladi.
 * Sabab: yig'indi 1.0 dan oshib ketishi mumkin va buni oldindan bilish
 * kerak, aks holda fayl shiqillagan (klipplangan) holda chiqardi.
 * Shu tufayli manba — fayl, oqim (stream) emas.
 */
interface MixSource : Closeable {

    val sampleRate: Int

    /** Kanallar soni: 1 yoki 2. */
    val channels: Int

    val frames: Long

    /**
     * [startFrame] dan boshlab [frameCount] ta kadrni [out] ga yozadi
     * (kanallar ketma-ket: L R L R …). Qaytaradi: haqiqatda o'qilgan kadr.
     */
    @Throws(IOException::class)
    fun read(startFrame: Long, frameCount: Int, out: FloatArray): Int
}

/** WAV faylni manba sifatida beradi. */
class WavMixSource(file: File) : MixSource {

    private val reader = WavSampleReader(file)

    override val sampleRate: Int get() = reader.info.sampleRate
    override val channels: Int get() = reader.info.channels
    override val frames: Long get() = reader.info.frames

    override fun read(startFrame: Long, frameCount: Int, out: FloatArray): Int =
        reader.readFrames(startFrame, frameCount, out)

    override fun close() = reader.close()
}
