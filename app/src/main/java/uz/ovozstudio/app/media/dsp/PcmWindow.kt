package uz.ovozstudio.app.media.dsp

import uz.ovozstudio.app.media.WavSampleReader

/**
 * Siltanuvchi oyna: fayldan kadrlarni oldinga qarab yuklab boradi.
 *
 * Vaqt bilan ishlaydigan amallar (cho'zish, qayta namunalash) fayl bo'ylab
 * oldinga qarab harakat qiladi, lekin bir vaqtning o'zida bir necha joyga
 * murojaat qiladi: joriy nuqta atrofidagi oyna, oldingi segmentning davomi,
 * filtr yadrosining chetlari. Har bir murojaat uchun alohida o'qish juda
 * sekin bo'lardi (har bir kadr uchun `seek`), butun faylni xotiraga olish
 * esa mumkin emas — o'n daqiqalik stereo fayl yuz megabaytdan oshadi.
 *
 * Shuning uchun oraliq yo'l: cheklangan hajmdagi oyna saqlanadi va u faqat
 * kerak bo'lganda oldinga suriladi. Surilganda hamon kerak bo'lgan qismi
 * ko'chiriladi, qolgani tashlanadi.
 *
 * Oyna faqat **oldinga** suriladi: orqaga qaytib murojaat qilinsa, u yerda
 * eski ma'lumot bo'lmaydi. Buni chaqiruvchi tomon ta'minlaydi — barcha
 * amallarda pozitsiya monoton o'sadi.
 */
internal class PcmWindow(
    private val reader: WavSampleReader,
    private val capacity: Int,
) {

    val channels: Int = reader.info.channels

    /** Fayldagi kadrlar soni — chegaradan chiqmaslik uchun. */
    val frames: Long = reader.info.frames

    /** Oynadagi namunalar: `[kadr][kanal]`. */
    val samples = FloatArray(capacity * channels)

    /** `samples[0]` qaysi kadrga to'g'ri keladi. */
    var first = 0L
        private set

    /** Oynada nechta kadr bor. */
    var count = 0
        private set

    private val scratch = FloatArray(capacity * channels)

    /**
     * Oyna suriladigan eng kichik qadam — yarim hajm.
     *
     * Har surilishda fayldan shuncha kadr o'qiladi. Bir kadrlik qadam
     * «har bir namunada bitta o'qish» degani bo'lardi: o'n daqiqalik fayl
     * uchun millionlab murojaat, ya'ni telefon uchun yaroqsiz tezlik.
     */
    private val step = maxOf(1L, capacity.toLong() / 2)

    /**
     * [endExclusive] kadrgacha bo'lgan ma'lumot oynada bo'lishini
     * ta'minlaydi; [keepFrom] dan pastdagisi endi kerak emas deb hisoblanadi.
     *
     * `keepFrom` — chaqiruvchining shartnomasi: u hozir eng past qaysi
     * kadrga murojaat qilishi mumkin. Oyna **faqat** shu chegaragacha
     * tashlab, undan oldinga suriladi.
     *
     * Nega bu parametr kerak. Oyna eng kami kerakli joygacha surilsa (bir
     * kadr), har bir namunada fayldan bir kadr o'qilardi: sekundiga o'n
     * minglab murojaat. Katta qadam bilan surish uchun esa qaysi ma'lumot
     * hali kerakligini bilish shart — usiz oyna kerakli tarixni tashlab
     * yuborardi. Shuning uchun chegarani chaqiruvchi aytadi.
     */
    fun ensure(endExclusive: Long, keepFrom: Long = endExclusive) {
        val wanted = minOf(endExclusive, frames)
        if (wanted <= first + count) return

        // Oyna shu chegaradan pastga tushmasligi kerak: undan yuqorisi
        // sig'may qolardi.
        val lowest = wanted - capacity
        var newFirst = first
        if (lowest > first) {
            // Katta qadamlar bilan suriladi, lekin kerakli ma'lumot
            // qoladigan darajada.
            val stepped = first + (lowest - first + step - 1) / step * step
            newFirst = if (stepped <= maxOf(lowest, keepFrom)) stepped else lowest
        }
        // O'qilmagan kadrlarni tashlab bo'lmaydi — o'sha yerda teshik
        // qolardi va chaqiruvchi jimgina nol o'qirdi.
        newFirst = minOf(newFirst, first + count)

        val keep = (first + count - newFirst).coerceIn(0L, count.toLong()).toInt()
        if (newFirst != first && keep > 0) {
            System.arraycopy(
                samples,
                ((newFirst - first) * channels).toInt(),
                samples,
                0,
                keep * channels,
            )
        }
        first = newFirst
        count = keep

        val want = minOf((capacity - count).toLong(), frames - (first + count)).toInt()
        if (want <= 0) return
        val got = reader.readFrames(first + count, want, scratch)
        if (got > 0) {
            System.arraycopy(scratch, 0, samples, count * channels, got * channels)
            count += got
        }
    }

    /** [frame] hozir oynada bormi. */
    fun loaded(frame: Long): Boolean = frame >= first && frame < first + count

    /** [frame] ning `samples` massividagi boshlanish indeksi (kanal 0 uchun). */
    fun offset(frame: Long): Int = ((frame - first) * channels).toInt()

    /** Kanal bo'yicha namuna; fayl chegarasidan tashqarida — nol. */
    fun value(frame: Long, channel: Int): Float =
        if (loaded(frame)) samples[offset(frame) + channel] else 0f

    /** Ketma-ket kadrlarni [out] ga ko'chiradi; chegaradan tashqarisi nol. */
    fun copyFrames(startFrame: Long, frames_: Int, out: FloatArray) {
        for (i in 0 until frames_) {
            val frame = startFrame + i
            val base = i * channels
            if (loaded(frame)) {
                val from = offset(frame)
                for (channel in 0 until channels) out[base + channel] = samples[from + channel]
            } else {
                for (channel in 0 until channels) out[base + channel] = 0f
            }
        }
    }
}
