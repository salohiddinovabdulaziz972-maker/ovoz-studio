package uz.ovozstudio.app.media

/** Bit chuqurligi. 24-bit telefonda har doim ham qo'llab-quvvatlanmaydi, shuning uchun
 *  yozish doim float ko'rinishida bo'ladi va faylga yozishda kerakli chuqurlikka o'giriladi. */
enum class BitDepth(val bits: Int) {
    BIT_16(16),
    BIT_24(24),
}

/** Yozib olish sozlamalari — hujjatning 2-V bo'limidagi talablar. */
data class RecorderConfig(
    val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    val bitDepth: BitDepth = BitDepth.BIT_16,
    val stereo: Boolean = false,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = false,
) {
    val channels: Int get() = if (stereo) 2 else 1

    companion object {
        const val DEFAULT_SAMPLE_RATE = 48_000
        val SAMPLE_RATES = intArrayOf(44_100, 48_000, 96_000)
    }
}
