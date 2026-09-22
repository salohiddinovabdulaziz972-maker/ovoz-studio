package uz.ovozstudio.app.media.pdf

/**
 * Sahifalar ro'yxatini o'qiydi: `1-3, 7, 10-12`.
 *
 * Nega bitta matn maydoni. Sahifalar ko'pincha bitta oraliq bo'ladi
 * («5–12»), lekin ba'zan bir nechta («1–3 va 7»). Ikkalasi ham bir xil
 * yoziladi, ekran o'quvchi foydalanuvchisi esa bitta maydonni to'ldiradi.
 *
 * Qoidalar:
 *  - sahifa raqami 1 dan boshlanadi (hujjatdagi chop etilgan raqam emas,
 *    faylning tartib raqami);
 *  - ajratgich: vergul, nuqta-vergul yoki probel;
 *  - chiziqcha atrofidagi probel ahamiyatsiz, uzun chiziqcha (`–`, `—`)
 *    ham chiziqcha deb o'qiladi: telefon klaviaturasi ko'pincha shuni qo'yadi;
 *  - takrorlangan sahifa bir marta olinadi, natija tartiblanadi.
 *
 * Bu — sof matn mantiqi, Android'ga bog'liq emas.
 */
object PageRange {

    /** Ro'yxat nega o'qilmadi. Matnni ekran tanlaydi. */
    enum class Problem {
        /** Hech narsa kiritilmagan. */
        EMPTY,

        /** Yozuv tushunarsiz: harf yoki ortiqcha belgi. */
        SYNTAX,

        /** Sahifa hujjatda yo'q: 0 yoki oxirgi sahifadan katta. */
        OUT_OF_RANGE,

        /** Oraliq teskari yozilgan: `5-3`. */
        REVERSED,
    }

    sealed interface Parsed {
        /** Sahifalar: 1 dan boshlanadi, takrorsiz, o'sish tartibida. */
        data class Pages(val pages: List<Int>) : Parsed

        /** [token] — muammoli bo'lak (bo'sh ro'yxatda bo'sh). */
        data class Invalid(val problem: Problem, val token: String = "") : Parsed
    }

    private val DASHES = Regex("[\\u2010-\\u2015\\u2212]")
    private val AROUND_DASH = Regex("\\s*-\\s*")
    private val SEPARATORS = Regex("[,;\\s]+")

    /** Bir sahifa yoki oraliq. To'qqiz raqam — `Int` dan oshib ketmaydi. */
    private val TOKEN = Regex("(\\d{1,9})(?:-(\\d{1,9}))?")

    fun parse(text: String, pageCount: Int): Parsed {
        val cleaned = text.replace(DASHES, "-").replace(AROUND_DASH, "-").trim()
        val tokens = cleaned.split(SEPARATORS).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return Parsed.Invalid(Problem.EMPTY)

        val pages = sortedSetOf<Int>()
        for (token in tokens) {
            val match = TOKEN.matchEntire(token) ?: return Parsed.Invalid(Problem.SYNTAX, token)
            val start = match.groupValues[1].toInt()
            val endText = match.groupValues[2]
            val end = if (endText.isEmpty()) start else endText.toInt()

            if (end < start) return Parsed.Invalid(Problem.REVERSED, token)
            // Chegara sikldan OLDIN tekshiriladi: «1-999999999» bilan
            // yuz millionlab elementli ro'yxat yig'ilmasin.
            if (start < 1 || end > pageCount) return Parsed.Invalid(Problem.OUT_OF_RANGE, token)
            for (page in start..end) pages.add(page)
        }
        return Parsed.Pages(pages.toList())
    }

    /** [pages] dan tashqaridagi sahifalar (1…[pageCount]), o'sish tartibida. */
    fun complement(pages: List<Int>, pageCount: Int): List<Int> {
        val skip = pages.toHashSet()
        val rest = ArrayList<Int>()
        for (page in 1..pageCount) {
            if (page !in skip) rest.add(page)
        }
        return rest
    }

    /** Sahifalarni qisqa yozuvga aylantiradi: `[1,2,3,7]` → `1-3, 7`. */
    fun format(pages: List<Int>): String {
        if (pages.isEmpty()) return ""
        val out = StringBuilder()
        var start = pages[0]
        var previous = start
        for (index in 1 until pages.size) {
            val page = pages[index]
            if (page == previous + 1) {
                previous = page
            } else {
                appendRange(out, start, previous)
                start = page
                previous = page
            }
        }
        appendRange(out, start, previous)
        return out.toString()
    }

    private fun appendRange(out: StringBuilder, start: Int, end: Int) {
        if (out.isNotEmpty()) out.append(", ")
        out.append(start)
        if (end != start) out.append('-').append(end)
    }
}
