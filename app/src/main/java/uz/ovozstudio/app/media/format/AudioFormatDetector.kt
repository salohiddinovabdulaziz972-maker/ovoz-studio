package uz.ovozstudio.app.media.format

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Aniqlangan konteyner va kodek juftligi. */
data class DetectedFormat(
    val container: AudioContainer,
    val codec: AudioCodec,
)

/**
 * Faylning formatini sarlavha baytlari bo'yicha aniqlaydi.
 *
 * Nega kengaytmaga ishonmaymiz: foydalanuvchi fayl nomini o'zgartirgan
 * bo'lishi mumkin (`ovoz.mp3` aslida FLAC bo'lishi mumkin), va noto'g'ri
 * aniqlangan format eksportda noto'g'ri kodek tanlashga olib keladi.
 * Shuning uchun qaror faylning o'z tarkibiga tayanadi.
 *
 * Namuna chastotasi va kanal soni bu yerda aniqlanmaydi — ular uchun
 * dekoder kerak. Bu sinf faqat konteyner va kodekni aytadi.
 */
object AudioFormatDetector {

    /** Sarlavhadan o'qiladigan baytlar soni — barcha imzolar shunga sig'adi. */
    private const val HEADER_BYTES = 64

    private val RIFF = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
    private val WAVE = byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte())
    private val FLAC_MAGIC = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
    private val OGG_MAGIC = byteArrayOf('O'.code.toByte(), 'g'.code.toByte(), 'g'.code.toByte(), 'S'.code.toByte())
    private val FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    private val ID3 = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())

    /** ASF (WMA) konteynerining GUID'i — birinchi 16 bayt. */
    private val ASF_GUID = byteArrayOf(
        0x30, 0x26, 0xB2.toByte(), 0x75, 0x8E.toByte(), 0x66, 0xCF.toByte(), 0x11,
        0xA6.toByte(), 0xD9.toByte(), 0x00, 0xAA.toByte(), 0x00, 0x62, 0xCE.toByte(), 0x6C,
    )

    private val OPUS_HEAD = "OpusHead".toByteArray(Charsets.US_ASCII)
    private val VORBIS_HEAD = byteArrayOf(0x01) + "vorbis".toByteArray(Charsets.US_ASCII)

    /** Faylni o'qib formatini aniqlaydi; tanilmasa `null`. */
    @Throws(IOException::class)
    fun detect(file: File): DetectedFormat? = detect(readHeader(file))

    /** Faylning boshidan [HEADER_BYTES] bayt o'qiydi (fayl qisqa bo'lsa — borini). */
    @Throws(IOException::class)
    fun readHeader(file: File): ByteArray {
        RandomAccessFile(file, "r").use { raf ->
            val size = minOf(raf.length(), HEADER_BYTES.toLong()).toInt()
            val buf = ByteArray(size)
            raf.readFully(buf)
            return buf
        }
    }

    /**
     * Sarlavha baytlari bo'yicha formatni aniqlaydi.
     * Tanilmasa yoki qo'llab-quvvatlanmasa `null` qaytaradi.
     */
    fun detect(header: ByteArray): DetectedFormat? {
        // Eng qisqa imzo — MPEG kadr sinxronizatsiyasi (2 bayt). Qolgan
        // shoxlar o'z chegarasini o'zi tekshiradi: `startsWith` yetmagan
        // baytda `false` qaytaradi.
        if (header.size < 2) return null

        if (header.startsWith(RIFF, 0)) {
            if (!header.startsWith(WAVE, 8)) return null // RIFF, lekin audio emas (masalan AVI)
            return if (isPcmWav(header)) DetectedFormat(AudioContainer.WAV, AudioCodec.PCM) else null
        }

        if (header.startsWith(FLAC_MAGIC, 0)) {
            return DetectedFormat(AudioContainer.FLAC, AudioCodec.FLAC)
        }

        if (header.startsWith(ASF_GUID, 0)) {
            return DetectedFormat(AudioContainer.WMA, AudioCodec.WMA)
        }

        if (header.startsWith(OGG_MAGIC, 0)) {
            // OGG — konteyner; kodek birinchi paketning sarlavhasida yozilgan.
            return when {
                header.indexOf(OPUS_HEAD) >= 0 -> DetectedFormat(AudioContainer.OGG, AudioCodec.OPUS)
                header.indexOf(VORBIS_HEAD) >= 0 -> DetectedFormat(AudioContainer.OGG, AudioCodec.VORBIS)
                else -> null
            }
        }

        if (header.startsWith(FTYP, 4) && header.size >= 12) {
            val brand = String(header, 8, 4, Charsets.US_ASCII)
            if (brand in MP4_AUDIO_BRANDS) return DetectedFormat(AudioContainer.M4A, AudioCodec.AAC)
            return null
        }

        if (header.startsWith(ID3, 0)) {
            // ID3v2 teg deyarli har doim MP3 faylni boshlaydi.
            return DetectedFormat(AudioContainer.MP3, AudioCodec.MP3)
        }

        return detectMpegAudioFrame(header)
    }

    /**
     * MPEG audio kadrlari va ADTS AAC bir xil 12 bitli sinxronizatsiyadan
     * (`0xFFF`) boshlanadi. Farqi keyingi "layer" bitlarida: ADTS'da ular
     * `00`, MPEG audio'da hech qachon `00` bo'lmaydi (u zahira qiymat).
     */
    private fun detectMpegAudioFrame(header: ByteArray): DetectedFormat? {
        if (header.size < 2) return null
        val b0 = header[0].toInt() and 0xFF
        val b1 = header[1].toInt() and 0xFF
        if (b0 != 0xFF || (b1 and 0xE0) != 0xE0) return null

        val layer = (b1 shr 1) and 0x03
        val version = (b1 shr 3) and 0x03
        if (version == 0x01) return null // zahira qiymat — bu audio kadr emas

        return when {
            (b1 and 0xF6) == 0xF0 -> DetectedFormat(AudioContainer.AAC, AudioCodec.AAC)
            layer != 0 -> DetectedFormat(AudioContainer.MP3, AudioCodec.MP3)
            else -> null
        }
    }

    /**
     * WAV ichidagi kodlash turi. Standart joylashuvda `fmt ` bloki 12 dan
     * boshlanadi va kodlash turi 20-baytda turadi. Boshqa joylashuv uchragan
     * bo'lsa PCM deb qabul qilamiz — haqiqiy tekshiruvni o'quvchi bajaradi.
     */
    private fun isPcmWav(header: ByteArray): Boolean {
        if (header.size < 22) return true
        val fmt = String(header, 12, 4, Charsets.US_ASCII)
        if (fmt != "fmt ") return true
        val tag = (header[20].toInt() and 0xFF) or ((header[21].toInt() and 0xFF) shl 8)
        return tag == 1 || tag == 0xFFFE
    }

    private fun ByteArray.startsWith(prefix: ByteArray, offset: Int): Boolean {
        if (offset + prefix.size > size) return false
        for (i in prefix.indices) {
            if (this[offset + i] != prefix[i]) return false
        }
        return true
    }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.isEmpty() || needle.size > size) return -1
        outer@ for (start in 0..(size - needle.size)) {
            for (i in needle.indices) {
                if (this[start + i] != needle[i]) continue@outer
            }
            return start
        }
        return -1
    }

    /**
     * MP4 konteynerining brendlari. Ularning barchasi M4A deb qaraladi:
     * ALAC (Apple lossless) ni faqat qutilar ichidan topish mumkin, 64
     * baytlik sarlavha bunga yetmaydi — uni dekoder aniqlaydi.
     */
    private val MP4_AUDIO_BRANDS = setOf("M4A ", "M4B ", "M4P ", "mp42", "isom", "iso2", "qt  ")
}
