package uz.ovozstudio.app.media.voice

/** Matn qaysi yozuvda yozilgan. */
enum class TextScript {
    /** O'zbek lotin yozuvi: «Assalomu alaykum». */
    LATIN,

    /** O'zbek kirill yozuvi: «Ассалому алайкум». */
    CYRILLIC,

    /** Ikkala yozuv ham sezilarli darajada aralash. */
    MIXED,

    /** Umuman harf yo'q: raqam, tinish belgisi yoki bo'sh matn. */
    UNKNOWN,
}

/**
 * Matnning yozuvini aniqlash va shunga mos til variantini tanlash.
 *
 * Nega bu kerak. Ilova o'zbek tilini **ikki yozuvda** qo'llab-quvvatlaydi, va
 * bu ikki yozuv uchun bir xil ovoz mos kelmaydi. Qurilmada o'zbek ovozi
 * bo'lmasa (ko'p qurilmalarda yo'q), tanlov faqat zaxira variantlar orasida
 * bo'ladi:
 *
 * - kirill matnni rus ovozi tushunarli o'qiydi — harflar bir xil;
 * - lotin matnni rus ovozi harflab, tushunarsiz qiladi, turk ovozi esa
 *   yaqin o'qish beradi (ikkala alifbo ham lotin, harflar ko'p joyda bir xil).
 *
 * Ya'ni yozuvni bilmasdan zaxira tilni tanlash mumkin emas. Ilgari bunday
 * qatlam yo'q edi — qurilmada o'zbek ovozi bo'lmasa, matn qaysi yozuvda
 * bo'lishidan qat'i nazar bitta tilga tushib qolardi.
 *
 * Bu fayl Android'ga bog'liq emas: yozuv aniqlash — sof matn mantiqi, ya'ni
 * uni JVM'da sinash mumkin, qurilmasiz.
 */
object ScriptDetector {

    /**
     * Aralash deb hisoblash chegarasi: kamchilik yozuv asosiyning kamida
     * uchdan biri (ya'ni matnning to'rtdan bir qismi) bo'lishi kerak.
     *
     * Nega aynan shunday. O'zbek matnlarida chet so'z deyarli har doim bor:
     * ruscha atama, ilmiy nom, havola. Ularni «aralash» deb hisoblasak,
     * deyarli har qanday kitob aralash bo'lib chiqardi va til tanlash
     * ma'nosiz bo'lardi. Aralash — bu haqiqatan ikki yozuvda yozilgan matn,
     * ya'ni yozuvlarning nisbati taqqoslanadigan holat.
     */
    private const val MIXED_MINOR_RATIO = 3

    /** O'zbek kirill alifbosining o'ziga xos harflari (qo'shimcha isbot). */
    private const val CYRILLIC_SPECIFIC = "ўқғҳ"

    fun detect(text: String): TextScript {
        var latin = 0
        var cyrillic = 0

        for (ch in text) {
            if (isCyrillicLetter(ch)) {
                cyrillic++
            } else if (isLatinLetter(ch)) {
                latin++
            }
        }

        if (latin == 0 && cyrillic == 0) return TextScript.UNKNOWN
        if (cyrillic == 0) return TextScript.LATIN
        if (latin == 0) return TextScript.CYRILLIC

        val major = maxOf(latin, cyrillic)
        val minor = minOf(latin, cyrillic)
        if (minor * MIXED_MINOR_RATIO >= major) return TextScript.MIXED
        return if (latin > cyrillic) TextScript.LATIN else TextScript.CYRILLIC
    }

    /**
     * Shu yozuv uchun til variantlari, afzallik tartibida.
     *
     * Ovoz dvigateli ro'yxatni boshidan boshlab tekshiradi va qurilmada
     * mavjud bo'lgan **birinchisini** oladi. Birinchi o'rin har doim
     * `uz-UZ`: qurilmada o'zbek ovozi bo'lsa, hech qanday zaxira kerak emas.
     *
     * Zaxiralar ataylab shu tartibda:
     * - kirill → `ru-RU` (alifbo bir xil, o'qish tushunarli);
     * - lotin → `tr-TR`, keyin `en-US` (turk alifbosi o'zbek lotiniga eng
     *   yaqin; ingliz ovozi harflarni biladi, lekin talaffuzi uzoq).
     */
    fun languageCandidates(script: TextScript): List<String> = when (script) {
        TextScript.LATIN -> listOf("uz-UZ", "tr-TR", "en-US")
        TextScript.CYRILLIC -> listOf("uz-UZ", "ru-RU")
        // Aralash matnda rus ovozi afzal: kirill qismini to'g'ri o'qiydi,
        // lotin qismini esa hech bo'lmasa harflab aytadi.
        TextScript.MIXED -> listOf("uz-UZ", "ru-RU", "tr-TR")
        TextScript.UNKNOWN -> listOf("uz-UZ")
    }

    /**
     * Matnda o'zbek kirill alifbosiga xos harf bormi.
     *
     * Ba'zi matnlar qisqa bo'ladi («Ўзбекистон»), ba'zilari esa umumiy
     * kirill harflaridan iborat bo'lib, rus tilidan farq qilmaydi. Bu
     * funksiya faqat qo'shimcha isbot uchun: u `uz-UZ` ovozini tanlashda
     * ishonchni oshiradi.
     */
    fun hasUzbekCyrillic(text: String): Boolean =
        text.any { ch -> CYRILLIC_SPECIFIC.contains(ch.lowercaseChar()) }

    private fun isCyrillicLetter(ch: Char): Boolean = ch.code in 0x0400..0x04FF

    private fun isLatinLetter(ch: Char): Boolean =
        (ch in 'a'..'z') || (ch in 'A'..'Z') ||
            // O'zbek lotin yozuvida tutuq belgisi harfdan keyin keladi:
            // o' (U+02BB), g' (U+02BC). Ular mustaqil harf emas, lekin
            // shu yozuvga tegishli — shuning uchun lotin qatoriga
            // qo'shiladi: aks holda faqat tutuq belgisidan iborat matn
            // «harf yo'q» bo'lib qolardi.
            ch.code == 0x02BB || ch.code == 0x02BC
}
