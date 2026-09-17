package uz.ovozstudio.app.media.mix

/**
 * Aralashmadagi bitta yo'lning sozlamalari.
 *
 * Ovozning o'zi bu yerda emas: [MixTrack] — bu «qanday eshitilsin» degan
 * qaror. Faqat [file] manbani nomlaydi; uni ochish va o'qish
 * [AudioMixer.Input] ning ishi.
 */
data class MixTrack(
    /** Foydalanuvchi ko'radigan nom — odatda fayl nomi. */
    val name: String,
    /**
     * Manba faylning **nomi** (to'liq yo'l emas).
     *
     * To'liq yo'l saqlanmaydi: ilovaning papkasi qurilmada va yangilanishdan
     * keyin o'zgarishi mumkin, nom esa qoladi. Fayl ilova papkasidan shu nom
     * bo'yicha topiladi.
     */
    val file: String = "",
    /** Balandlik, desibelda. 0 dB — manba o'zgartirilmaydi. */
    val gainDb: Float = 0f,
    /** Chap/o'ng joylashuv: −1 butunlay chap, 0 o'rta, +1 butunlay o'ng. */
    val pan: Float = 0f,
    /** Aralashma boshidan siljish, millisekundda. */
    val offsetMs: Long = 0L,
    /** O'chirilgan yo'l. Sozlamalari saqlanadi, ovozi eshitilmaydi. */
    val muted: Boolean = false,
    /** Faqat shu yo'lni eshitish (yakka). Boshqa yo'llar jim turadi. */
    val solo: Boolean = false,
) {

    /** Balandlik chiziqli koeffitsientda. */
    val gain: Float get() = dbToGain(gainDb)

    /** Yo'l eshitiladimi: yakka rejim yoqilgan bo'lsa faqat yakkalanganlar. */
    fun audible(anySolo: Boolean): Boolean = !muted && (!anySolo || solo)

    companion object {
        /** Balandlik chegarasi. +12 dB kuchaytirish — amalda yetarli. */
        const val MIN_GAIN_DB = -60f
        const val MAX_GAIN_DB = 12f
    }
}

/** Desibelni chiziqli koeffitsientga o'giradi: `10^(dB/20)`. */
fun dbToGain(db: Float): Float = Math.pow(10.0, db / 20.0).toFloat()

/**
 * Yo'lning chap va o'ng kanal koeffitsientlari.
 *
 * Ikki xil qoida, va bu **ataylab** shunday: mononi joylashtirish — bu
 * haqiqiy panorama, stereoni joylashtirish — bu balans.
 *
 *   * **Mono** — doimiy quvvat qoidasi: o'rtada har bir kanal −3 dB
 *     (0.707), chetda bir kanal 0 dB. Ya'ni tovush balandligi joylashuvni
 *     o'zgartirganda o'zgarmaydi va chetga surilgan yo'l manbadan balandroq
 *     bo'lib qolmaydi. Bu — mikser pultlaridagi standart.
 *   * **Stereo** — balans: o'rtada koeffitsientlar 1.0 (manba
 *     o'zgartirilmaydi), chetga surilsa qarama-qarshi kanal jimgina
 *     pasayadi. Bu yerda −3 dB qoidasi noto'g'ri bo'lardi: u har bir stereo
 *     yo'lni o'rtada ham jimgina pasaytirardi.
 */
fun channelGains(channels: Int, pan: Float): Pair<Float, Float> {
    val p = pan.coerceIn(-1f, 1f)
    return if (channels <= 1) {
        val angle = (p + 1f) * (Math.PI.toFloat() / 4f)
        Math.cos(angle.toDouble()).toFloat() to Math.sin(angle.toDouble()).toFloat()
    } else {
        val left = if (p > 0f) 1f - p else 1f
        val right = if (p < 0f) 1f + p else 1f
        left to right
    }
}
