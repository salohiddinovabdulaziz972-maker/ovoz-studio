package uz.ovozstudio.app.media.format

/**
 * Kodeklarning namuna chastotalari jadvali.
 *
 * Alohida, Android'siz sinf — ataylab: bu ma'lumot `FormatSupport` ga ham,
 * `MediaCodecEncoder` ga ham kerak, lekin `FormatSupport` **sof JVM testida**
 * tekshiriladi. Agar u `MediaCodecEncoder` ga murojaat qilsa, test
 * `android.media` sinflarini topolmay yiqilardi.
 *
 * Bu yerda faqat raqamlar, kodlovchi haqida hech narsa yo'q.
 */
object CodecRates {

    /**
     * Opus kirish chastotalari. Ro'yxat tor: Opus spetsifikatsiyasi faqat
     * shularni biladi — 44.1 kHz unga kirmaydi. Bunday manba uchun
     * `FormatSupport` chekinishni taklif qiladi; bu kodlovchining yiqilishi
     * emas, tushunarli javob bo'lishi kerak.
     */
    val OPUS = intArrayOf(8_000, 12_000, 16_000, 24_000, 48_000)

    /** AAC chastotalari — ADTS sarlavhasidagi 4 bitli indeks jadvali bilan bir xil. */
    val AAC = intArrayOf(
        96_000, 88_200, 64_000, 48_000, 44_100, 32_000, 24_000,
        22_050, 16_000, 12_000, 11_025, 8_000, 7_350,
    )

    /** Shu kodek shu chastotada ishlay oladimi. */
    fun supports(codec: AudioCodec, sampleRate: Int): Boolean = when (codec) {
        AudioCodec.AAC -> AAC.contains(sampleRate)
        AudioCodec.OPUS -> OPUS.contains(sampleRate)
        else -> true
    }

    /** ADTS sarlavhasidagi indeks. Ro'yxatda yo'q bo'lsa `-1`. */
    fun aacIndex(sampleRate: Int): Int = AAC.indexOf(sampleRate)
}
