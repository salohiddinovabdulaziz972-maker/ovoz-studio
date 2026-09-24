package uz.ovozstudio.app.media.voice

import java.io.File

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
 * Qurilmada o'rnatilgan ovoz dvigateli (masalan «Google», «Samsung» yoki
 * Microsoft ovozlarini beradigan uchinchi tomon dasturi).
 *
 * Ovozlar dvigatelga tegishli: Sardor va Madina kabi ovozlar faqat ularni
 * bergan dvigatel tanlanganda ko'rinadi.
 */
data class TtsEngineInfo(
    /** Dastur paketining nomi — dvigatelni tanlash uchun. */
    val packageName: String,
    /** Foydalanuvchiga ko'rsatiladigan nom. */
    val label: String,
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
 * [VoiceEngine.synthesizeToFile] natijasi.
 *
 * Nega alohida enum emas, balki shu ikkita holat: xato kodi allaqachon
 * [VoiceError] da bor, uni takrorlash shart emas.
 */
sealed interface SynthesisResult {
    /** Fayl muvaffaqiyatli yozildi. */
    data object Done : SynthesisResult

    data class Failed(val error: VoiceError) : SynthesisResult
}

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
 * yo'q, ba'zilarida sifati past. Ovozni keyinroq boshqa manbaga (masalan
 * bulutli) almashtirish kerak bo'lsa, ulanish nuqtasi shu interfeys bo'ladi:
 * ilovaning qolgan qismi o'zgarmaydi.
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

    /**
     * Matnni **jonli o'qimasdan**, [destination] fayliga (WAV) yozadi.
     *
     * Audio-kitob yasashda ishlatiladi: har bir bo'lak alohida faylga
     * yoziladi, keyin ular ketma-ket qo'shiladi
     * ([uz.ovozstudio.app.media.merge.AudioMerger]).
     *
     * [request.text] uzunligi dvigatelning bir chaqiruvdagi chegarasidan
     * oshmasligi kerak — [speak] dan farqli, bu yerda bo'laklash
     * chaqiruvchining zimmasida ([TextChunker]): natija alohida fayllarga
     * yozilgani uchun bo'laklarni kim ketma-ket chaqirishini bilib turishi
     * kerak — bu ma'lumot faqat chaqiruvchida bor (u umumiy kitob bo'ylab
     * jarayon foizini ham shundan hisoblaydi).
     *
     * Bir vaqtning o'zida faqat bitta chaqiruv faol bo'lishi mumkin: yangisi
     * eskisini bekor qiladi (natija fayli tugallanmagan holda qoladi).
     */
    fun synthesizeToFile(request: SpeechRequest, destination: File, onResult: (SynthesisResult) -> Unit)

    /**
     * Bitta chaqiruvga sig'adigan eng ko'p belgi — bo'laklash shu chegara
     * bo'yicha qilinadi.
     *
     * Nega dvigateldan so'raladi. Ilgari chaqiruvchi [TextChunker] ning
     * qat'iy `DEFAULT_MAX_CHARS` ini ishlatardi, [speak] esa haqiqiy
     * chegarani — `TextToSpeech.getMaxSpeechInputLength()` ni — hisobga
     * olardi. Ikkalasi mos kelmasa, chegaradan uzun bo'lak dvigatelga
     * berilardi va u matnni **jimgina tashlab yuborardi**: kitobning ba'zi
     * bo'laklari ovozsiz chiqar, hech qanday xato ham ko'rinmasdi.
     * `getMaxSpeechInputLength()` hamma dvigatellarda bir xil emas, shuning
     * uchun birlamchi manba — dvigatelning o'zi.
     */
    fun maxSynthChars(): Int

    /** Qurilmada o'rnatilgan ovoz dvigatellari (tanlangan dvigatel ham ro'yxatda). */
    fun engines(): List<TtsEngineInfo>

    /**
     * Aniq ovozni tanlaydi ([VoiceInfo.id]); `null` — ovozni til bo'yicha
     * avtomatik tanlash.
     *
     * Tanlangan ovoz keyingi [speak] dan kuchga kiradi. Ovoz topilmasa
     * (dvigatel almashgan, ovoz o'chirilgan) o'qish til bo'yicha davom etadi.
     */
    fun setVoice(id: String?)

    /** O'qishni (jonli va faylga yozishni) to'xtatadi. Keyingi [speak] boshidan boshlanadi. */
    fun stop()

    /** Resurslarni bo'shatadi. Shundan keyin dvigatel ishlamaydi. */
    fun release()
}
