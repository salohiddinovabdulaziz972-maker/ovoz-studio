package uz.ovozstudio.app.media.format

/**
 * «Qanday formatda yuklansa, shunday formatda qaytarilsin» qoidasining
 * qat'iy shakli.
 *
 * Bu yerda **hech qanday chekinish yo'q**: natija aynan manbaning konteyneri
 * va kodekida yoziladi. Agar Android shu formatga yoza olmasa (OGG Vorbis,
 * WMA, eski Android'da Opus), fayl ish boshlanmasdan oldin rad etiladi va
 * sababi aytiladi. Boshqa formatga «taklif» ham, «zaxira» ham yo'q.
 *
 * Sinf Android'ga bog'liq emas — sof JVM'da sinaladi.
 */
object StrictFormat {

    /** Bit tezligi taxminining chegaralari (bit/s): odatiy va ishonchli oraliq. */
    private const val MP3_MIN = 32_000L
    private const val MP3_MAX = 320_000L
    private const val AAC_MIN = 32_000L
    private const val AAC_MAX = 256_000L
    private const val OPUS_MIN = 24_000L
    private const val OPUS_MAX = 256_000L

    /** Format nomi foydalanuvchi uchun: `MP3`, `M4A (AAC)`, `OGG (Vorbis)`. */
    fun label(container: AudioContainer, codec: AudioCodec): String = when {
        container == AudioContainer.WAV -> container.displayName
        container.displayName.equals(codec.displayName, ignoreCase = true) -> container.displayName
        else -> "${container.displayName} (${codec.displayName})"
    }

    fun label(format: AudioFormat): String = label(format.container, format.codec)

    /**
     * Fayl turi (MIME): «saqlash» oynasi va ulashish shunga qarab ilovani
     * tanlaydi. Umumiy audio turi emas, aniq tur — qabul qiluvchi to'g'ri
     * pleyerni ochsin.
     */
    fun mimeType(container: AudioContainer): String = when (container) {
        AudioContainer.WAV -> "audio/wav"
        AudioContainer.FLAC -> "audio/flac"
        AudioContainer.MP3 -> "audio/mpeg"
        AudioContainer.M4A -> "audio/mp4"
        AudioContainer.AAC -> "audio/aac"
        AudioContainer.OGG, AudioContainer.OPUS -> "audio/ogg"
        AudioContainer.WMA -> "audio/x-ms-wma"
    }

    /**
     * Manbaning «asl formati».
     *
     * Konteyner va kodek fayl sarlavhasidan olinadi ([detected]), chastota,
     * kanal soni va bit chuqurligi — ochilgan WAV dan ([decoded]), bit
     * tezligi esa fayl hajmi va uzunligidan taxmin qilinadi. Bit tezligi
     * kerak: uni bilmasak, 64 kbit/s lik ovoz yozuvi qayta kodlanganda
     * bir necha baravar kattaroq faylga aylanardi.
     */
    fun originOf(
        detected: DetectedFormat,
        decoded: AudioFormat,
        sourceBytes: Long,
        durationMs: Long,
    ): AudioFormat {
        val base = AudioFormat(
            container = detected.container,
            codec = detected.codec,
            sampleRate = decoded.sampleRate,
            channels = decoded.channels,
            bitDepth = decoded.bitDepth,
        )
        return base.copy(
            bitrate = estimateBitrate(detected.codec, decoded.sampleRate, sourceBytes, durationMs),
        )
    }

    /**
     * O'rtacha bit tezligi (bit/s): fayl hajmi / uzunlik.
     *
     * Yo'qotishsiz kodeklar (WAV, FLAC) uchun `null` — ularda bit tezligi
     * tushunchasi yo'q. Natija kodekning qabul qiladigan oralig'iga
     * keltiriladi; MP3 uchun esa shu chastotada ruxsat etilgan zinapoyaga.
     */
    fun estimateBitrate(codec: AudioCodec, sampleRate: Int, sourceBytes: Long, durationMs: Long): Int? {
        if (sourceBytes <= 0L || durationMs <= 0L) return null
        val average = sourceBytes * 8_000L / durationMs
        return when (codec) {
            AudioCodec.MP3 -> CodecRates.mp3Bitrate(sampleRate, average.coerceIn(MP3_MIN, MP3_MAX).toInt())
            AudioCodec.AAC -> average.coerceIn(AAC_MIN, AAC_MAX).toInt()
            AudioCodec.OPUS -> average.coerceIn(OPUS_MIN, OPUS_MAX).toInt()
            else -> null
        }
    }

    /**
     * Aynan shu formatda yozib bo'lmasa — sababi, bo'lsa `null`.
     *
     * [apiLevel] parametr sifatida beriladi (Opus API 29 dan): shu tufayli
     * qoida qurilmasiz sinaladi.
     */
    fun blocker(origin: AudioFormat, apiLevel: Int): FallbackReason? =
        when (val decision = FormatSupport.resolve(origin, apiLevel)) {
            is ExportDecision.Preserved -> null
            is ExportDecision.Fallback -> decision.reason
        }
}
