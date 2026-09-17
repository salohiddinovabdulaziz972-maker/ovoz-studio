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

    /**
     * MP3 chastotalari — MPEG-1 (32/44.1/48 kHz), MPEG-2 (16–24 kHz) va
     * MPEG-2.5 (8–12 kHz) jadvallari birlashtirilgani.
     *
     * Ro'yxat torligi amalda muhim: ovoz sintezatori 8 yoki 16 kHz beradi,
     * ya'ni «istalgan chastota ishlaydi» degan taxmin aynan kitob yozishda
     * buziladi.
     */
    val MP3 = intArrayOf(
        8_000, 11_025, 12_000, 16_000, 22_050, 24_000, 32_000, 44_100, 48_000,
    )

    /**
     * MP3 bit tezliklari zinapoyasi (kbit/s). LAME faqat shu qiymatlarni
     * qabul qiladi — oradagi sonni bersa, kodlovchi umuman ishga tushmaydi.
     */
    private val MP3_BITRATES = intArrayOf(
        8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 192, 224, 256, 320,
    )

    /** Shu kodek shu chastotada ishlay oladimi. */
    fun supports(codec: AudioCodec, sampleRate: Int): Boolean = when (codec) {
        AudioCodec.AAC -> AAC.contains(sampleRate)
        AudioCodec.OPUS -> OPUS.contains(sampleRate)
        AudioCodec.MP3 -> MP3.contains(sampleRate)
        else -> true
    }

    /**
     * Shu chastotada ruxsat etilgan eng katta bit tezligi (bit/s).
     *
     * Chegara MPEG versiyasidan kelib chiqadi, versiya esa chastotadan:
     * 8–12 kHz — MPEG-2.5 (64 kbit/s gacha), 16–24 kHz — MPEG-2 (160 kbit/s
     * gacha), yuqorisi — MPEG-1 (320 kbit/s gacha).
     */
    fun mp3MaxBitrate(sampleRate: Int): Int = when {
        sampleRate <= 12_000 -> 64_000
        sampleRate <= 24_000 -> 160_000
        else -> 320_000
    }

    /**
     * So'ralgan bit tezligini shu chastotaga moslashtiradi.
     *
     * Kerak bo'lsa **pastga** tushadi. O'lchov (native libmp3lame, ffprobe):
     * 8 kHz da 128 kbit/s so'ralsa, LAME jimgina 64 kbit/s yozadi — ya'ni
     * chegaradan kattasi yiqilishga olib kelmaydi, kodlovchining o'zi
     * tushiradi. Shunga tayanmaslik uchun qiymatni o'zimiz hisoblaymiz:
     * natija kodlovchining bag'rikengligiga bog'liq bo'lib qolmasin.
     * Zinapoyadagi eng yaqin qiymat olinadi — oradagi sonni ham LAME
     * yaxlitlaydi, lekin qaysi tomonga — aytilgan emas.
     */
    fun mp3Bitrate(sampleRate: Int, preferred: Int): Int {
        val maxKbps = mp3MaxBitrate(sampleRate) / 1000
        val asked = preferred / 1000
        val fitted = MP3_BITRATES.lastOrNull { it <= asked && it <= maxKbps }
            ?: MP3_BITRATES.first()
        return fitted * 1000
    }

    /** ADTS sarlavhasidagi indeks. Ro'yxatda yo'q bo'lsa `-1`. */
    fun aacIndex(sampleRate: Int): Int = AAC.indexOf(sampleRate)
}
