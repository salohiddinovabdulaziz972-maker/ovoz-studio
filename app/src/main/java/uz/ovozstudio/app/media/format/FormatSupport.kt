package uz.ovozstudio.app.media.format

/**
 * Nima uchun manba formatini saqlab qolib bo'lmadi.
 *
 * Kodda satr emas, sabab saqlanadi — matn UI qatlamida tarjima qilinadi
 * (`TrimError` bilan bir xil naqsh).
 */
enum class FallbackReason {
    /** Konteyner ochiladi, lekin Android'da unga kodlovchi yo'q. */
    NO_ENCODER,

    /** Android umuman o'qiy olmaydi — faylni import qilib bo'lmaydi. */
    NO_DECODER,

    /** Kodlovchi bor, lekin qurilma versiyasi yetmaydi. */
    API_TOO_OLD,
}

/** Eksport formati bo'yicha qaror. */
sealed interface ExportDecision {

    /** Manba formati saqlanadi — odatiy hol. */
    data class Preserved(val format: AudioFormat) : ExportDecision

    /**
     * Manba formatini qaytarib bo'lmaydi. Tanlovni foydalanuvchi qiladi,
     * lekin [recommended] tayyor turadi — uni yolg'iz qoldirib bo'lmaydi.
     */
    data class Fallback(
        val source: AudioFormat,
        val reason: FallbackReason,
        val recommended: AudioFormat,
        val alternatives: List<AudioFormat>,
    ) : ExportDecision
}

/**
 * Android'ning kodek imkoniyatlari jadvali.
 *
 * Bu yerda faqat platforma haqiqatlari: qaysi konteynerni o'qish mumkin,
 * qaysi biriga yozish mumkin. Siyosat (nima taklif qilinadi) — [resolve].
 *
 * Ilovada qat'iy qoida amal qiladi ([StrictFormat]): natija faqat asl
 * formatda yoziladi. [resolve] qaytaradigan boshqa format takliflari
 * (`recommended`, `alternatives`) ilova tomonidan **ishlatilmaydi** — undan
 * faqat «asl formatga yozib bo'ladimi va bo'lmasa nega» degan javob olinadi.
 *
 * [apiLevel] ataylab parametr: shu tufayli jadvalni oddiy JVM testida
 * tekshirish mumkin, qurilmasiz.
 */
object FormatSupport {

    /** OGG konteyneriga muxing qilish va Opus kodlash API 29 dan boshlanadi. */
    const val OGG_API_LEVEL = 29

    fun canDecode(container: AudioContainer, codec: AudioCodec): Boolean = when (codec) {
        AudioCodec.PCM, AudioCodec.FLAC, AudioCodec.MP3, AudioCodec.AAC, AudioCodec.VORBIS, AudioCodec.OPUS -> true
        // Android'da WMA uchun dekoder yo'q — na MediaCodec, na MediaExtractor.
        AudioCodec.WMA -> false
    }

    /**
     * [sampleRate] ixtiyoriy, lekin muhim: kodek hamma chastotada ham
     * ishlamaydi. Masalan Opus 44.1 kHz ni umuman bilmaydi, AAC esa faqat
     * o'z jadvalidagi 13 ta qiymatni. Chastota berilmasa, faqat konteyner
     * va kodek tekshiriladi.
     */
    fun canEncode(
        container: AudioContainer,
        codec: AudioCodec,
        apiLevel: Int,
        sampleRate: Int? = null,
    ): Boolean {
        val byContainer = when (codec) {
            // O'z kodlovchilarimiz — har qanday versiyada ishlaydi.
            AudioCodec.PCM -> true
            AudioCodec.FLAC -> true
            // jump3r kutubxonasi orqali, sof Kotlin/Java.
            AudioCodec.MP3 -> true
            // MediaCodec + MediaMuxer: MP4 konteyneri barcha versiyalarda bor.
            AudioCodec.AAC -> container != AudioContainer.OGG
            // Android'da Vorbis kodlovchisi umuman yo'q (faqat dekoder).
            AudioCodec.VORBIS -> false
            AudioCodec.OPUS -> apiLevel >= OGG_API_LEVEL && container == AudioContainer.OGG
            AudioCodec.WMA -> false
        }
        if (!byContainer || sampleRate == null) return byContainer
        // Jadvallar `CodecRates` da — u Android'siz, shuning uchun bu
        // obyekt JVM testida ham ishlayveradi.
        return CodecRates.supports(codec, sampleRate)
    }

    /** Faylni umuman import qilib bo'ladimi. */
    fun canImport(format: DetectedFormat): Boolean = canDecode(format.container, format.codec)

    /**
     * Eksport formatini hal qiladi.
     *
     * Asosiy qoida: manba formatiga qayta kodlaymiz — foydalanuvchi qanday
     * formatda yuklasa, shunday formatda oladi. Faqat texnik imkoni
     * bo'lmaganda chekinamiz va buni ochiq aytamiz.
     *
     * [override] — foydalanuvchi konvertor ekranida ochiq tanlagan format;
     * u bo'lsa, hech qanday chekinish yo'q.
     */
    fun resolve(source: AudioFormat, apiLevel: Int, override: AudioFormat? = null): ExportDecision {
        if (override != null) return ExportDecision.Preserved(override)

        if (canEncode(source.container, source.codec, apiLevel, source.sampleRate)) {
            return ExportDecision.Preserved(source)
        }

        val reason = when {
            !canDecode(source.container, source.codec) -> FallbackReason.NO_DECODER
            // Opus uchun eski versiya — alohida holat: kodlovchi bor, lekin
            // qurilma yetmaydi. Chastota mos kelmasa bu yerga tushmaydi:
            // u kodlovchining o'zi yo'qligi bilan bir xil.
            source.codec == AudioCodec.OPUS && apiLevel < OGG_API_LEVEL -> FallbackReason.API_TOO_OLD
            else -> FallbackReason.NO_ENCODER
        }

        // Yo'qotishsiz varianti eng yaqin: dekodlangan audio aynan saqlanadi.
        val recommended = AudioFormat(
            container = AudioContainer.FLAC,
            codec = AudioCodec.FLAC,
            sampleRate = source.sampleRate,
            channels = source.channels,
            bitDepth = source.bitDepth ?: DEFAULT_BIT_DEPTH,
        )

        val alternatives = buildList {
            add(
                AudioFormat(
                    container = AudioContainer.WAV,
                    codec = AudioCodec.PCM,
                    sampleRate = source.sampleRate,
                    channels = source.channels,
                    bitDepth = source.bitDepth ?: DEFAULT_BIT_DEPTH,
                )
            )
            if (canEncode(AudioContainer.M4A, AudioCodec.AAC, apiLevel)) {
                add(
                    AudioFormat(
                        container = AudioContainer.M4A,
                        codec = AudioCodec.AAC,
                        sampleRate = source.sampleRate,
                        channels = source.channels,
                        bitrate = defaultBitrate(AudioCodec.AAC, source.channels),
                    )
                )
            }
        }

        return ExportDecision.Fallback(source, reason, recommended, alternatives)
    }

    /**
     * Konvertor ekranida taklif qilinadigan maqsad formatlar.
     *
     * Ro'yxat manbaning namuna parametrlaridan kelib chiqib tuziladi, shuning
     * uchun unda "tanladingiz, lekin yozib bo'lmadi" holati bo'lmaydi: Opus
     * 44.1 kHz ni umuman bilmaydi, AAC esa o'z jadvalidan tashqari
     * chastotalarni — bunday formatlar ro'yxatga kirmaydi.
     *
     * Chastota va kanal soni barcha variantlarda bir xil: konvertatsiya
     * formatni almashtiradi, sifatni emas. Uni o'zgartirish kerak bo'lsa,
     * bu alohida amal bo'ladi.
     */
    fun convertOptions(source: AudioFormat, apiLevel: Int): List<AudioFormat> = buildList {
        val rate = source.sampleRate
        val channels = source.channels
        val depth = source.bitDepth ?: DEFAULT_BIT_DEPTH

        add(AudioFormat(AudioContainer.WAV, AudioCodec.PCM, rate, channels, depth))
        // Yo'qotishsiz varianti ikkinchi turadi: u har doim mavjud va
        // sifatni saqlaydi, ya'ni xavfsiz tanlov.
        add(AudioFormat(AudioContainer.FLAC, AudioCodec.FLAC, rate, channels, depth))
        add(lossy(AudioContainer.MP3, AudioCodec.MP3, rate, channels, depth))

        for (container in listOf(AudioContainer.M4A, AudioContainer.AAC)) {
            if (canEncode(container, AudioCodec.AAC, apiLevel, rate)) {
                add(lossy(container, AudioCodec.AAC, rate, channels, depth))
            }
        }

        if (canEncode(AudioContainer.OGG, AudioCodec.OPUS, apiLevel, rate)) {
            add(lossy(AudioContainer.OGG, AudioCodec.OPUS, rate, channels, depth))
        }
    }

    /**
     * Ekran dastlab tanlab turadigan format.
     *
     * [resolve] taklif qilgan format **ro'yxatdagi nusxasi** bilan
     * almashtiriladi. Sabab: manba faylda bit tezligi ko'rsatilmagan bo'lishi
     * mumkin (MP3 uchun u sarlavhada har doim ham aniq emas), ro'yxatdagi
     * variantda esa aniq qiymat turadi. Ikkalasi bir xil bo'lmasa, tanlov
     * qatorida hech biri belgilanmay qolardi — foydalanuvchi esa "tanlov
     * yo'qolgan" holatini ko'rardi.
     */
    fun defaultTarget(source: AudioFormat, apiLevel: Int): AudioFormat {
        val preferred = when (val decision = resolve(source, apiLevel)) {
            is ExportDecision.Preserved -> decision.format
            is ExportDecision.Fallback -> decision.recommended
        }
        return convertOptions(source, apiLevel)
            .firstOrNull { it.container == preferred.container && it.codec == preferred.codec }
            ?: preferred
    }

    private fun lossy(
        container: AudioContainer,
        codec: AudioCodec,
        sampleRate: Int,
        channels: Int,
        bitDepth: Int,
    ): AudioFormat = AudioFormat(
        container = container,
        codec = codec,
        sampleRate = sampleRate,
        channels = channels,
        bitDepth = bitDepth,
        bitrate = defaultBitrate(codec, channels),
    )

    /** Yo'qotishli kodlash uchun standart bit tezligi (bit/s). */
    fun defaultBitrate(codec: AudioCodec, channels: Int): Int {
        val perChannel = when (codec) {
            AudioCodec.AAC -> 128_000
            AudioCodec.MP3 -> 192_000
            AudioCodec.OPUS -> 96_000
            else -> 0
        }
        return if (channels <= 1) perChannel * 3 / 4 else perChannel
    }

    private const val DEFAULT_BIT_DEPTH = 16
}
