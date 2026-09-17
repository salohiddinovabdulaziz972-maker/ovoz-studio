package uz.ovozstudio.app.media.doc

/**
 * Kitobning bir bo'lagi — odatda bob.
 *
 * @property title sarlavha. Sarlavha topilmasa — chaqiruvchi bergan zaxira nom.
 * @property text bobning matni: **sarlavhasiz**, chetlari kesilgan. Aynan shu
 *   matn ovozga aylantiriladi.
 * @property startOffset manbadagi boshlanishi — sarlavha qatorini ham o'z
 *   ichiga oladi, ya'ni `text` undan keyin boshlanadi.
 * @property endOffset manbadagi tugashi (ochiq chegara: `endOffset` kirmaydi).
 */
data class Chapter(
    val title: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
) {
    val charCount: Int get() = text.length
}

/**
 * Oddiy matnni boblarga ajratadi.
 *
 * Bu — **evristika**, hujjat formati emas. DOCX va EPUB o'z sarlavhalarini
 * aniq belgilaydi (uslub nomi, `<h1>` tegi) va ular [ChapterSplitter] ga
 * tayyor belgi bilan keladi; bu yerdagi qoidalar esa faqat matnga qarab
 * ishlaydi, chunki TXT'da boshqa hech narsa yo'q.
 *
 * Shu sababli qoidalar **ehtiyotkor**: sarlavha deb noto'g'ri tan olingan
 * oddiy qator kitobni o'rtasidan ikkiga bo'ladi va natija foydalanuvchiga
 * g'alati ko'rinadi. Tan olmaslik esa faqat bitta katta bob beradi — bu
 * yomonroq emas, tushunarliroq.
 *
 * Qoidalar (shu tartibda):
 *  1. Markdown sarlavhasi — `#`, `##`, `###` … (DOCX/EPUB shu ko'rinishda beradi).
 *  2. Son + kalit so'z: `1-BOB`, `II bob`, `ГЛАВА 3`, `Chapter 2`, `1-qism`.
 *     Kalit so'zsiz yalang'och son sarlavha hisoblanmaydi.
 *  3. Butunlay katta harflarda yozilgan qisqa qator — faqat ikki tomoni bo'sh
 *     qator bilan o'ralgan bo'lsa. `BIRINCHI BOB` shu qoida bilan topiladi.
 */
object ChapterSplitter {

    /**
     * Sarlavha qatorining eng katta uzunligi. Undan uzun qator — matn.
     * Markdown sarlavhasiga bu chegara qo'llanmaydi: u ochiq belgi.
     */
    const val MAX_TITLE_CHARS = 60

    /**
     * Sarlavhadan oldingi matn shu uzunlikdan qisqa bo'lsa tashlab yuboriladi.
     * Odatda u muqova, muallif ismi va nashr ma'lumotlari bo'ladi — ularni
     * ovozga aylantirish kitobxon uchun foydasiz, lekin kitobning birinchi
     * sahifasi bo'lib qolishi ham mumkin, shuning uchun chegaradan uzun
     * bo'lsa saqlanadi.
     */
    const val DEFAULT_MIN_PREFACE_CHARS = 400

    private const val MIN_CAPS_LETTERS = 3
    private const val CAPS_NUMERATOR = 9
    private const val CAPS_DENOMINATOR = 10

    /** Son: arab raqami yoki rim raqami. */
    private const val NUM = """\d{1,3}|[IVXLCDM]{1,7}"""

    /** Kalit so'z. Apostrofning hamma ko'rinishi: ' ‘ ’ ʻ ʼ ` */
    private const val KEY =
        """bob|bo['‘’ʻʼ`]?lim|qism|боб|бўлим|қисм|булим|глава|часть|раздел|chapter|part"""

    /**
     * Kalit so'zdan keyin harf kelmasligi shart. `\b` bu yerda yaramaydi:
     * Java'ning `\b` bayrog'i faqat ASCII harflarni so'z deb biladi, kirill
     * harfi esa uning uchun so'z emas — «боблар» so'zi «боб» deb topilib
     * qolardi.
     */
    private const val KEY_END = """(?!\p{L})"""

    private val NUMBER_FIRST = Regex(
        """^\s*[(\[]?\s*(?:$NUM)\s*[.\-–—):]?\s*(?:$KEY)$KEY_END""",
        RegexOption.IGNORE_CASE,
    )

    private val KEY_FIRST = Regex(
        """^\s*(?:$KEY)\s+(?:$NUM)$KEY_END""",
        RegexOption.IGNORE_CASE,
    )

    private val MARKDOWN = Regex("""^(#{1,6})\s+(.+?)\s*#*\z""")

    /** Katta harfli sarlavhada uchraydigan tinish belgilari va raqamlar. */
    private const val CAPS_ALLOWED = " \t-–—:;,.!?()[]{}\"'«»…&/\\"

    /**
     * Matnni boblarga bo'ladi.
     *
     * @param text hujjatning butun matni (satr oxirlari `\n` ga keltirilgan).
     * @param fallbackTitle sarlavhalar umuman topilmasa ishlatiladigan nom.
     * @param prefaceTitle sarlavhadan oldingi uzun matnga beriladigan nom
     *   (masalan «Muqaddima») — tarjima qilingan matn chaqiruvchidan keladi,
     *   shuning uchun bu qatlamda resurs ishlatilmaydi.
     * @param minPrefaceChars sarlavhadan oldingi matnning eng kichik uzunligi.
     */
    fun split(
        text: String,
        fallbackTitle: String,
        prefaceTitle: String,
        minPrefaceChars: Int = DEFAULT_MIN_PREFACE_CHARS,
    ): List<Chapter> {
        if (text.isBlank()) return emptyList()

        val lines = linesOf(text)
        val headings = ArrayList<Found>()
        for (i in lines.indices) {
            val heading = headingOf(lines, i) ?: continue
            headings += Found(i, heading)
        }

        // Sarlavha yo'q — butun hujjat bitta bob.
        if (headings.isEmpty()) return listOf(whole(text, fallbackTitle))

        val chapters = ArrayList<Chapter>()
        var pending: String? = null

        val firstLineStart = lines[headings[0].line].start
        if (firstLineStart > 0) {
            val preface = text.substring(0, firstLineStart).trim()
            if (preface.length >= minPrefaceChars) {
                val start = text.indexOfFirst { !it.isWhitespace() }
                chapters += Chapter(prefaceTitle, preface, start, firstLineStart)
            }
        }

        for ((n, found) in headings.withIndex()) {
            val line = lines[found.line]
            val bodyEnd =
                if (n + 1 < headings.size) lines[headings[n + 1].line].start else text.length
            val body = text.substring(line.end, bodyEnd).trim()

            val title = if (pending == null) found.title else "$pending — ${found.title}"
            pending = null

            if (body.isEmpty()) {
                // Sarlavha ostida matn yo'q. Odatda bu bo'lim nomi
                // («BIRINCHI QISM»), ya'ni keyingi bobning ustki nomi —
                // shuning uchun uni keyingi sarlavhaga qo'shib qo'yamiz.
                pending = title
                continue
            }
            chapters += Chapter(title, body, line.start, bodyEnd)
        }

        // Har bir sarlavha ostida matn bo'sh chiqdi: demak ular sarlavha
        // emas, tasodifan qoidaga tushgan qatorlar edi. Butun matnni
        // bo'lmasdan qaytaramiz — o'rtasidan kesilgan kitobdan ko'ra
        // bo'linmagan kitob yaxshi.
        if (chapters.isEmpty()) return listOf(whole(text, fallbackTitle))

        return chapters
    }

    private fun whole(text: String, title: String): Chapter {
        val start = text.indexOfFirst { !it.isWhitespace() }
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        return Chapter(title, text.substring(start, end), start, end)
    }

    private fun headingOf(lines: List<Line>, index: Int): String? {
        val trimmed = lines[index].text.trim()
        if (trimmed.isEmpty()) return null

        MARKDOWN.find(trimmed)?.let { return it.groupValues[2].trim() }

        if (trimmed.length > MAX_TITLE_CHARS) return null

        if (NUMBER_FIRST.containsMatchIn(trimmed) || KEY_FIRST.containsMatchIn(trimmed)) {
            return trimmed
        }

        if (isSeparated(lines, index) && capsTitle(trimmed) != null) return trimmed

        return null
    }

    /** Qator ikki tomonidan bo'sh qator bilan o'ralganmi (matn boshi/oxiri ham hisobga olinadi). */
    private fun isSeparated(lines: List<Line>, index: Int): Boolean {
        val before = index == 0 || lines[index - 1].text.isBlank()
        val after = index == lines.size - 1 || lines[index + 1].text.isBlank()
        return before && after
    }

    /**
     * Qator butunlay katta harflarda yozilganmi. Faqat harflar sanaladi:
     * tinish belgilari, raqamlar va apostroflar hisobga olinmaydi — aks
     * holda «BIRINCHI BOB» o'zbekcha apostroflar tufayli o'tmay qolardi.
     */
    private fun capsTitle(trimmed: String): String? {
        var letters = 0
        var upper = 0
        for (c in trimmed) {
            when {
                c == 'ʻ' || c == 'ʼ' || c == '‘' || c == '’' || c == '`' || c == '\'' -> Unit
                c.isLetter() -> {
                    letters++
                    if (c.isUpperCase()) upper++
                }
                c.isDigit() || c in CAPS_ALLOWED -> Unit
                else -> return null
            }
        }
        if (letters < MIN_CAPS_LETTERS) return null
        if (upper * CAPS_DENOMINATOR < letters * CAPS_NUMERATOR) return null
        return trimmed
    }

    private fun linesOf(text: String): List<Line> {
        val out = ArrayList<Line>()
        var start = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '\n') {
                out += Line(start, i, text.substring(start, i))
                start = i + 1
            }
            i++
        }
        out += Line(start, text.length, text.substring(start))
        return out
    }

    private data class Line(val start: Int, val end: Int, val text: String)

    private data class Found(val line: Int, val title: String)
}
