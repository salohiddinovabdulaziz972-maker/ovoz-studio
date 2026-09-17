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

    fun canEncode(container: AudioContainer, codec: AudioCodec, apiLevel: Int): Boolean = when (codec) {
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

        if (canEncode(source.container, source.codec, apiLevel)) {
            return ExportDecision.Preserved(source)
        }

        val reason = when {
            !canDecode(source.container, source.codec) -> FallbackReason.NO_DECODER
            source.codec == AudioCodec.OPUS -> FallbackReason.API_TOO_OLD
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
