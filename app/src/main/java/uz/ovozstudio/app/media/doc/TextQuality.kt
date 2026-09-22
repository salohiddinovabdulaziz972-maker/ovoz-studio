package uz.ovozstudio.app.media.doc

/**
 * Chiqarilgan matn o'qishga yaroqlimi.
 *
 * PDF dan matn olish har doim ham ma'noli natija bermaydi: eski o'zbek
 * kitoblari maxsus (standart bo'lmagan) shrift bilan terilgan, shriftda esa
 * «harf → Unicode» jadvali yo'q. Kutubxona bunday sahifadan matn o'rniga
 * almashtirish belgilari (`\uFFFD`), shaxsiy foydalanish oralig'idagi
 * belgilar yoki boshqaruv kodlari chiqaradi. Ovoz shuni «o'qib» bersa,
 * foydalanuvchi bir necha daqiqa tushunarsiz shovqin eshitadi va sababini
 * bilmaydi. Shuning uchun bunday matn oldindan aniqlanib, aniq xabar
 * beriladi.
 *
 * Bu — evristika: harf va raqamlar orasida yaroqsiz belgilar ulushi
 * [BROKEN_PERCENT] dan oshsa, matn «buzuq» hisoblanadi. Noto'g'ri harf
 * (jadval xato, lekin belgi «to'g'ri» ko'rinadi) bunday usul bilan
 * aniqlanmaydi — buning uchun tekshirishning yo'li yo'q.
 *
 * Sof matn mantiqi, Android'ga bog'liq emas.
 */
object TextQuality {

    /** Yaroqsiz belgilar ulushi (foiz): shundan ko'pi — matn buzuq. */
    const val BROKEN_PERCENT = 20

    /** Bundan kam belgiga qarab hukm chiqarilmaydi: qisqa matnda bir-ikki belgi foizni buzadi. */
    private const val MIN_SAMPLE = 50

    /** Katta matnning boshidan olingan namuna yetarli: hamma belgini sanash shart emas. */
    private const val SAMPLE_CHARS = 20_000

    fun looksBroken(text: String): Boolean {
        val end = minOf(text.length, SAMPLE_CHARS)
        var good = 0
        var bad = 0
        for (index in 0 until end) {
            val ch = text[index]
            when {
                ch.isLetterOrDigit() -> good++
                isInvalid(ch) -> bad++
            }
        }
        val total = good + bad
        if (total < MIN_SAMPLE) return false
        return bad * 100 >= total * BROKEN_PERCENT
    }

    /** Matnda bo'lmasligi kerak belgi: almashtirish belgisi, shaxsiy oraliq, boshqaruv kodi. */
    private fun isInvalid(ch: Char): Boolean =
        ch == '\uFFFD' ||
            ch in '\uE000'..'\uF8FF' ||
            (ch.isISOControl() && ch != '\n' && ch != '\r' && ch != '\t')
}
