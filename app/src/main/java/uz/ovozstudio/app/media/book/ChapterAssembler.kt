package uz.ovozstudio.app.media.book

import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.format.AudioCodec
import uz.ovozstudio.app.media.format.AudioContainer
import uz.ovozstudio.app.media.format.AudioEncoder
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.CodecRates
import uz.ovozstudio.app.media.format.Mp3Encoder
import uz.ovozstudio.app.media.format.WavPcmReader
import java.io.File

/**
 * Eksport formati — namuna parametrlarisiz.
 *
 * Namuna chastotasi va kanal soni sintezator bergan fayldan olinadi:
 * ularni oldindan aytib bo'lmaydi (qurilmaga qarab 22050 yoki 16000 Hz
 * bo'ladi). Nol qiymatlarni to'ldirib yuboradigan `AudioFormat` yasashdan
 * ko'ra, niyatni shu yerda ochiq yozgan ma'qul.
 */
data class AudioTarget(
    val container: AudioContainer,
    val codec: AudioCodec,
    val bitrate: Int? = null,
) {
    fun formatFor(info: WavInfo): AudioFormat = AudioFormat(
        container = container,
        codec = codec,
        sampleRate = info.sampleRate,
        channels = info.channels,
        // Yo'qotishli kodekda bu — kodlovchiga kelayotgan PCM'ning shkalasi;
        // noto'g'ri berilsa, ovoz butunlay buziladi.
        bitDepth = info.bitsPerSample,
        // Sintezator chastotasi oldindan noma'lum: 8 kHz beradigan dvigatel
        // ham bor. MP3'da bit tezligining chegarasi chastotaga bog'liq,
        // shuning uchun qiymat o'zimiz hisoblanadi — kodlovchining o'zi
        // tushirishiga tayanmaymiz.
        bitrate = bitrate?.let { requested ->
            when (codec) {
                AudioCodec.MP3 -> CodecRates.mp3Bitrate(info.sampleRate, requested)
                else -> requested
            }
        },
    )

    companion object {
        /**
         * 128 kbit/s — nutq uchun yetarli: MP3'da bu odatdagi «audio-kitob»
         * sifat darajasi, fayl esa telefonda ham, uzatishda ham yengil qoladi.
         */
        const val DEFAULT_BITRATE = 128_000

        val MP3 = AudioTarget(AudioContainer.MP3, AudioCodec.MP3, DEFAULT_BITRATE)
    }
}

/** Tayyor bob fayli. */
data class AssembledChapter(
    val audio: File,
    val markers: List<BookMarker>,
    val durationMs: Long,
)

/**
 * Bob bo'laklarini bitta audio faylga yig'adi.
 *
 * Uch qadam: qo'shish → kodlash → belgilar. Har biri alohida tekshirilgan,
 * bu yerda faqat tartib muhim: belgilar **qo'shilgan** fayldagi kadr
 * ofsetlaridan olinadi, ya'ni pauzalar hisobga olinadi. Bo'laklar soni
 * bo'yicha hisoblansa, belgilar asta-sekin haqiqiy joyidan siljib ketardi.
 */
object ChapterAssembler {

    private const val READ_FRAMES = 4096

    /**
     * [parts] — bitta bobning sintezlangan bo'laklari (tartibi muhim).
     *
     * [joinTarget] — oraliq WAV: ish tugagach o'chiriladi. Xatolik bo'lsa ham
     * o'chiriladi — sabab tahlil uchun emas, foydalanuvchi xotirasida joy
     * qolmasligi uchun muhimroq: xato matni allaqachon qaytarilgan bo'ladi.
     */
    @Throws(BookAssemblyException::class)
    fun assemble(
        parts: List<File>,
        joinTarget: File,
        audioTarget: File,
        target: AudioTarget,
        plan: BookChapterPlan,
        gapMs: Int = WavJoiner.DEFAULT_GAP_MS,
        openEncoder: (File, AudioFormat) -> AudioEncoder = { file, format ->
            Mp3Encoder(file, format)
        },
    ): AssembledChapter {
        val joined = WavJoiner.join(parts, joinTarget, gapMs)
        try {
            openEncoder(audioTarget, target.formatFor(joined.info)).use { encoder ->
                WavPcmReader(joinTarget).use { reader ->
                    val buffer = IntArray(READ_FRAMES * joined.info.channels)
                    while (true) {
                        val got = reader.read(buffer, READ_FRAMES)
                        if (got <= 0) break
                        encoder.write(buffer, got)
                    }
                }
                encoder.finish()
            }
        } finally {
            joinTarget.delete()
        }

        return AssembledChapter(
            audio = audioTarget,
            markers = BookMarkers.ofChapter(joined, plan),
            durationMs = joined.info.durationMs,
        )
    }
}
