package uz.ovozstudio.app.media.book

/**
 * Uyqu taymeri holati.
 *
 * `EXPIRED` — vaqt tugadi, lekin o'qish hali to'xtamagan bo'lishi mumkin:
 * «bob oxirigacha» rejimida to'xtash bob chegarasida bo'ladi.
 */
enum class SleepState { IDLE, RUNNING, PAUSED, EXPIRED }

/**
 * Uyqu taymeri: belgilangan vaqtdan keyin kitobni to'xtatadi.
 *
 * Vaqt **tashqaridan** beriladi ([elapse]): taymer devor soatiga qaramaydi.
 * Bu ataylab shunday — aks holda uni sinash uchun haqiqiy daqiqalab kutish
 * kerak bo'lardi, telefonda esa taymer fon rejimida to'xtab qolishi mumkin.
 * Haqiqiy soniyalarni UI qatlami beradi (har soniyada bir marta).
 *
 * Nega kerak: kitobni tinglash — ko'pincha uxlashdan oldingi mashg'ulot.
 * Taymersiz telefon ertalabgacha o'qib, quvvatni tugatardi.
 */
class SleepTimer {

    var state: SleepState = SleepState.IDLE
        private set

    /** Qolgan soniyalar; taymer ishlamasa `0`. */
    var remainingSeconds: Long = 0
        private set

    /**
     * «Bob oxirigacha» rejimi yoqilganmi.
     *
     * Yoqilgan bo'lsa, vaqt tugaganda o'qish darhol to'xtamaydi — joriy bob
     * tugashini kutadi. Gap o'rtasida o'chib qolish tinglovchini eng
     * g'ashini keltiradigan narsa, ayniqsa uxlab qolgan bo'lsa: ertalab
     * «qayerda qoldim» degan savolga javob topib bo'lmaydi.
     */
    var stopAtChapterEnd: Boolean = false
        private set

    val isRunning: Boolean get() = state == SleepState.RUNNING

    /** Vaqt tugadimi (rejimdan qat'i nazar). */
    val isExpired: Boolean get() = state == SleepState.EXPIRED

    /** Taymer boshidan ishga tushiriladi; eski taymer almashtiriladi. */
    fun start(totalSeconds: Long, stopAtChapterEnd: Boolean = false) {
        this.stopAtChapterEnd = stopAtChapterEnd
        if (totalSeconds <= 0) {
            remainingSeconds = 0
            state = SleepState.EXPIRED
            return
        }
        remainingSeconds = totalSeconds
        state = SleepState.RUNNING
    }

    /**
     * Taymerni to'xtatib turadi (masalan, ilova fonda qolganda).
     *
     * Faqat ishlab turgan taymer to'xtatiladi: `EXPIRED` va `IDLE` holatda
     * qolgan vaqt yo'q, ularni «pauza»ga o'tkazish vaqtni qayta tiklab
     * qo'yardi.
     */
    fun pause() {
        if (state == SleepState.RUNNING) state = SleepState.PAUSED
    }

    /** Pauzadan keyin davom ettiradi; tugagan taymer qayta tiklanmaydi. */
    fun resume() {
        if (state == SleepState.PAUSED) state = SleepState.RUNNING
    }

    /** Taymerni o'chiradi va vaqtni nolga qaytaradi. */
    fun cancel() {
        remainingSeconds = 0
        state = SleepState.IDLE
    }

    /**
     * Vaqt o'tishini bildiradi. Taymer tugasa `true` qaytaradi.
     *
     * Faqat ishlab turgan taymer vaqtni sanaydi: pauzada yoki allaqachon
     * tugagan taymerga [elapse] chaqirilsa, natija o'zgarmaydi.
     */
    fun elapse(seconds: Long): Boolean {
        if (state != SleepState.RUNNING || seconds <= 0) return isExpired
        remainingSeconds -= seconds
        if (remainingSeconds <= 0) {
            remainingSeconds = 0
            state = SleepState.EXPIRED
        }
        return isExpired
    }

    /**
     * Shu paytda o'qishni to'xtatish kerakmi.
     *
     * [atChapterEnd] — joriy bob tugadimi. «Bob oxirigacha» rejimi
     * o'chirilgan bo'lsa, chegara ahamiyatsiz.
     */
    fun shouldStop(atChapterEnd: Boolean): Boolean =
        isExpired && (!stopAtChapterEnd || atChapterEnd)
}
