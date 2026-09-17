package uz.ovozstudio.app.media.format

/**
 * Butun sonli namunalarni 16-bitli PCM baytlariga o'girish.
 *
 * `MediaCodec` ham, uning muxeri ham faqat 16-bitli PCM bilan ishlaydi:
 * AAC ham, Opus ham bundan aniqroq kirishni saqlay olmaydi. Manba 24-bit
 * bo'lsa, namunalar shu yerda o'ngga suriladi.
 *
 * Alohida sinf — ataylab: `MediaCodec` ni JVM'da ishga tushirib bo'lmaydi,
 * o'girish mantiqini esa ajratib olib **haqiqiy sinovdan** o'tkazish mumkin.
 * Surish xatosi (masalan `>> 8` o'rniga `>> 4`) ovozni jimgina buzadi —
 * bunday xatoni qurilmada payqash qiyin.
 */
internal object PcmBytes {

    const val BYTES_PER_SAMPLE = 2

    /** Necha kadr shu buferga sig'adi. */
    fun framesIn(byteCapacity: Int, channels: Int): Int =
        if (channels <= 0) 0 else byteCapacity / (channels * BYTES_PER_SAMPLE)

    /** [frames] kadr uchun kerakli bayt hajmi. */
    fun sizeFor(frames: Int, channels: Int): Int = frames * channels * BYTES_PER_SAMPLE

    /**
     * [samples] ning [from] dan boshlab [count] tasini [target] ga kichik
     * tartibda (little-endian) yozadi.
     *
     * [shift] — o'ngga surish: manba bit chuqurligi 16 dan qancha katta
     * bo'lsa, shuncha. 16-bit uchun `0`.
     *
     * Qiymatlar 16-bit chegarasiga qisiladi — buzuq manba butun faylni
     * buzmasligi kerak.
     */
    fun write(target: ByteArray, samples: IntArray, from: Int, count: Int, shift: Int): Int {
        require(shift >= 0) { "Surish manfiy bo'lishi mumkin emas: $shift" }
        require(from >= 0 && count >= 0 && from + count <= samples.size) {
            "Chegaradan chiqish: $from + $count > ${samples.size}"
        }
        require(target.size >= count * BYTES_PER_SAMPLE) {
            "Bufer yetarli emas: ${target.size} < ${count * BYTES_PER_SAMPLE}"
        }
        var offset = 0
        for (i in 0 until count) {
            val value = (samples[from + i] shr shift).coerceIn(SHORT_MIN, SHORT_MAX)
            target[offset++] = (value and 0xFF).toByte()
            target[offset++] = ((value shr 8) and 0xFF).toByte()
        }
        return offset
    }

    private const val SHORT_MIN = -32_768
    private const val SHORT_MAX = 32_767
}
