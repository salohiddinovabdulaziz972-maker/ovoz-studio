package uz.ovozstudio.app.media.voice

/**
 * Ovoz dvigateli xatolari.
 *
 * Naqsh ilova bo'ylab bir xil: dvigatel matn emas, **kod** qaytaradi —
 * qaysi tilda ko'rsatishni UI hal qiladi. Aks holda rus tilidagi qurilmada
 * o'zbekcha xato chiqardi.
 */
enum class VoiceError {
    /** Qurilmada ovoz sintezatori yo'q yoki ishga tushmadi. */
    NOT_AVAILABLE,

    /** Hech bo'lmasa zaxira til ham topilmadi. */
    LANGUAGE_MISSING,

    /** O'qish paytida xato (sintezatorning o'zi xabar berdi). */
    SPEAK_FAILED,

    /** O'qish uchun matn yo'q. */
    EMPTY_TEXT,
}

/** Qurilmadagi mavjud ovoz. */
data class VoiceInfo(
    /** Tizim identifikatori — keyin shu ovozni tanlash uchun. */
    val id: String,
    /** Til-teg, masalan `uz-UZ`. */
    val localeTag: String,
    /** Qurilma bergan nom (ba'zan bo'sh bo'ladi). */
    val name: String,
    /** Sifat darajasi: qurilma aytgan baho. */
    val quality: Int,
)

/**
 * O'qish uchun sozlash.
 *
 * [text] — **o'qiladigan matn**, [SpeechChunk] emas: bo'laklash dvigatelning
 * ichida bajariladi, chunki bo'lak chegarasi sintezatorning texnik chegarasi
 * (`getMaxSpeechInputLength`), ya'ni u haqda faqat dvigatel biladi.
 */
data class SpeechRequest(
    val text: String,
    /** 1.0 — normal tezlik. Ilova 0.5–2.0 ni beradi. */
    val rate: Float = 1.0f,
    /** 1.0 — normal ovoz balandligi. */
    val pitch: Float = 1.0f,
    /** Til-teg; berilmasa matnning yozuvidan aniqlanadi. */
    val languageTag: String? = null,
)

/**
 * O'qish jarayoni haqidagi xabarlar.
 *
 * [onStarted] har bir bo'lak boshida chaqiriladi va qaysi bo'lak
 * o'qilayotganini aytadi — ekran o'quvchi foydalanuvchisi uchun bu
 * «qayerda qoldim» degan savolga javob.
 */
interface SpeechListener {
    fun onStarted(index: Int, total: Int, chunk: SpeechChunk)
    fun onFinished()
    fun onError(error: VoiceError)
}

/**
 * Ovoz dvigateli abstraksiyasi.
 *
 * Nega interfeys kerak: bugun o'qish qurilmaning o'z sintezatori bilan
 * bajariladi, lekin o'zbek ovozi hamma qurilmada yo'q — ba'zilarida umuman
 * yo'q, ba'zilarida sifati past. Keyingi qadam shu interfeys ortiga bulutli
 * (AI) ovozni ulash. Ulanish nuqtasi bitta bo'lmasa, almashtirish butun
 * ilovani qayta yozishni talab qilardi.
 *
 * Barcha amallar **asosiy oqimdan** chaqiriladi. Dvigatel ichida
 * sintezatorning o'zi boshqa oqimda javob bersa ham, tashqariga xabar
 * asosiy oqimda uzatiladi: UI holatini faqat asosiy oqimdan o'zgartirish
 * mumkin.
 */
interface VoiceEngine {

    /**
     * Dvigatelni ishga tayyorlaydi. Xato bo'lsa `null` emas, sabab keladi.
     *
     * Tayyorlash **asinxron**: Android'ning sintezatori xizmatga ulanadi va
     * javob keyin keladi. Shu sababli natija callback bilan qaytariladi —
     * kutish uchun oqimni to'xtatish (bloklash) UI'ni qotib qolishga olib
     * kelardi.
     */
    fun prepare(onResult: (VoiceError?) -> Unit)

    /** Dvigatel tayyormi. */
    fun isReady(): Boolean

    /** Qurilmadagi mavjud ovozlar ro'yxati. */
    fun voices(): List<VoiceInfo>

    /**
     * Shu yozuv uchun ishlatiladigan til-teg, afzallik tartibida birinchisi.
     *
     * `null` — hech bo'lmasa zaxira til ham topilmadi. Bu holda o'qish
     * to'xtatilmaydi: dvigatelning joriy tili qoladi. Lekin UI buni
     * foydalanuvchiga aytishi kerak — «o'zbek ovozi yo'q, rus ovozi bilan
     * o'qiladi» degan xabar jim qolgan noto'g'ri talaffuzdan yaxshiroq.
     */
    fun resolveLanguage(script: TextScript): String?

    /** Matnni o'qishni boshlaydi (yoki davom ettiradi). */
    fun speak(request: SpeechRequest, listener: SpeechListener)

    /** O'qishni to'xtatadi. Keyingi [speak] boshidan boshlanadi. */
    fun stop()

    /** Resurslarni bo'shatadi. Shundan keyin dvigatel ishlamaydi. */
    fun release()
}
