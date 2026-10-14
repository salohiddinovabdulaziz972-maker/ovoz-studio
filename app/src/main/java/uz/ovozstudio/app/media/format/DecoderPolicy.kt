package uz.ovozstudio.app.media.format

/**
 * Dekoderni tanlash va oxirida yiqilgan dekoderning natijasini baholash qoidalari.
 *
 * Nega alohida: `MediaCodec` Android'ga bog'liq, bu qoidalar esa yo'q — shuning
 * uchun ular JVM'da, qurilmasiz sinaladi.
 *
 * ## Tanlash tartibi
 *
 * `MediaCodec.createDecoderByType(mime)` ro'yxatdagi BIRINCHI mos dekoderni
 * beradi. Samsung kabi qurilmalarda bu ishlab chiqaruvchining o'z dekoderi
 * (`c2.sec…`, `OMX.SEC…`) bo'lishi mumkin, u esa ba'zi fayllarda
 * `IllegalStateException` bilan yiqiladi. Samsung SM-A716S (Android 13) da
 * jurnalda «Dekoder fayl o'rtasida to'xtadi», so'ng «NO_DECODER» yozilardi va
 * ovozli amallarning hammasi fayl ochishda to'xtardi.
 *
 * Android'ning o'z (Google) dasturiy dekoderlari — `c2.android…`, `OMX.google…` —
 * barcha qurilmada bir xil ishlaydi va audio uchun yetarlicha tez. Shuning uchun
 * ular birinchi sinaladi, ishlab chiqaruvchining dekoderlari esa zaxira bo'ladi.
 */
object DecoderPolicy {

    private const val CODEC2_PREFIX = "c2.android."
    private const val OMX_PREFIX = "OMX.google."

    /**
     * Yiqilgan dekoder faylning oxirini bermagan bo'lsa ham, qabul qilinadigan
     * eng katta yetishmovchilik (mikrosekund). Buyog'i — qisqargan audio.
     * Qisqa faylda bu chegara faylning o'ziga nisbatan ham cheklanadi
     * ([endCrashAcceptable]).
     */
    const val MAX_MISSING_US = 1_000_000L

    /** Android'ning o'z (Google) dasturiy dekoderimi — barcha qurilmada bir xil. */
    fun isPlatform(name: String): Boolean =
        name.startsWith(CODEC2_PREFIX, ignoreCase = true) ||
            name.startsWith(OMX_PREFIX, ignoreCase = true)

    /**
     * [listed] — qurilma ro'yxatidagi tartibda berilgan dekoder nomlari.
     *
     * Natija: avval `c2.android…`, keyin `OMX.google…`, keyin qolganlari
     * (qurilma bergan tartibda). Takrorlar olib tashlanadi.
     */
    fun order(listed: List<String>): List<String> {
        val unique = listed.distinct()
        val codec2 = unique.filter { it.startsWith(CODEC2_PREFIX, ignoreCase = true) }
        val omx = unique.filter { it.startsWith(OMX_PREFIX, ignoreCase = true) }
        val vendor = unique.filter { !isPlatform(it) }
        return codec2 + omx + vendor
    }

    /**
     * Kirish tugagandan keyin dekoder yiqilganda: yozilgan qism to'liq audiomi?
     *
     * Eski qoida «kirish tugagan bo'lsa, hammasi joyida» edi. Qisqa faylda bu
     * noto'g'ri: kirish bir zumda tugaydi, dekoder esa birinchi bufergayoq
     * yiqilishi mumkin — natijada fayl jimgina qisqargan holda «tayyor» bo'lardi.
     * Shuning uchun yozilgan davomiylik aytilgan davomiylikdan [MAX_MISSING_US]
     * dan ko'p kam bo'lmasa, qism qabul qilinadi. Qisqa faylda esa yetishmovchilik
     * davomiylikning o'ninchi ulushidan oshmasligi kerak: yarim soniyalik faylda
     * bir soniya «yetishmovchilik» butun faylga teng bo'lib qolardi.
     *
     * Davomiylik noma'lum bo'lsa ([durationUs] <= 0) tekshirib bo'lmaydi:
     * kamida bitta kadr yozilgan bo'lsa, qabul qilinadi.
     */
    fun endCrashAcceptable(frames: Long, sampleRate: Int, durationUs: Long): Boolean {
        if (frames <= 0L || sampleRate <= 0) return false
        if (durationUs <= 0L) return true
        val decodedUs = frames * 1_000_000L / sampleRate
        val allowedMissingUs = minOf(MAX_MISSING_US, durationUs / 10)
        return decodedUs >= durationUs - allowedMissingUs
    }
}
