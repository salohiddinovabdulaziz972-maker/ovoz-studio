package uz.ovozstudio.app.media.format

/**
 * Audio konteyner turlari.
 *
 * [WMA] alohida ajratilgan: Android'da uning uchun dekoder umuman yo'q,
 * shuning uchun uni na import, na eksport qilish mumkin. Ro'yxatda qolishi
 * foydalanuvchiga sababni aytish uchun kerak — "jimgina qo'llab-quvvatlanmaydi"
 * deyishdan ko'ra aniq javob berish yaxshi.
 */
enum class AudioContainer(
    val extension: String,
    val displayName: String,
) {
    WAV("wav", "WAV"),
    FLAC("flac", "FLAC"),
    MP3("mp3", "MP3"),
    M4A("m4a", "M4A"),
    AAC("aac", "AAC"),
    OGG("ogg", "OGG"),
    OPUS("opus", "OPUS"),
    WMA("wma", "WMA"),
    ;

    companion object {
        /** Kengaytma bo'yicha topadi; noma'lum bo'lsa `null`. */
        fun fromExtension(extension: String?): AudioContainer? {
            val clean = extension?.trim()?.lowercase()?.removePrefix(".") ?: return null
            if (clean.isEmpty()) return null
            return entries.firstOrNull { it.extension == clean }
        }
    }
}

/**
 * Konteyner ichidagi kodek. Konteynerning o'zi kodekni aniqlamaydi:
 * OGG ichida Vorbis ham, Opus ham bo'lishi mumkin, M4A ichida AAC ham,
 * ALAC ham. Qayta kodlashda aynan shu kodek kerak bo'ladi.
 */
enum class AudioCodec(val displayName: String, val lossless: Boolean) {
    PCM("PCM", true),
    FLAC("FLAC", true),
    MP3("MP3", false),
    AAC("AAC", false),
    VORBIS("Vorbis", false),
    OPUS("Opus", false),
    WMA("WMA", false),
}

/**
 * Faylning to'liq audio formati: konteyner, kodek va namuna parametrlari.
 *
 * Bu qiymat import paytida o'qiladi va loyiha bilan birga saqlanadi.
 * Tahrirlash tugagach natija AYNAN shu formatda qaytariladi — foydalanuvchi
 * talabi: "qanday format yuklasa, shunday format qaytarilsin". Boshqa
 * formatga o'tish faqat ochiq konvertatsiya amali bo'lganda bo'ladi.
 *
 * [bitDepth] yo'qotishsiz konteynerlarda (WAV, FLAC) sarlavhaga yoziladigan
 * haqiqiy chuqurlik. Yo'qotishli konteynerlarda (MP3, AAC, Opus) esa u
 * **kodlovchiga kelayotgan PCM'ning shkalasi** degan ma'noni bildiradi —
 * bunday konteynerda bit chuqurligi saqlanmaydi, lekin kodlovchi namunalarni
 * to'g'ri talqin qilishi uchun uni bilishi shart: 24-bit manbani 16-bit deb
 * hisoblash ovozni butunlay buzadi.
 *
 * [bitrate] faqat yo'qotishli formatlarda bo'ladi.
 * Ikkalasi ham `null` bo'lishi mumkin — u holda eksport standart qiymatni
 * ishlatadi.
 */
data class AudioFormat(
    val container: AudioContainer,
    val codec: AudioCodec,
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int? = null,
    val bitrate: Int? = null,
) {

    /** Eksport natijasi uchun fayl nomi: `ovoz.mp3` kabi. */
    fun fileName(base: String): String = "$base.${container.extension}"

    /**
     * Shu formatni qayta kodlashda namuna parametrlari saqlanib qoladimi.
     * Yo'qotishli manbaning bit tezligini bilmasak ham, chastota va kanal
     * soni har doim saqlanadi — bu eshitiladigan farq beradi.
     */
    fun withBitrate(fallback: Int): AudioFormat =
        if (bitrate != null || codec.lossless) this else copy(bitrate = fallback)
}
