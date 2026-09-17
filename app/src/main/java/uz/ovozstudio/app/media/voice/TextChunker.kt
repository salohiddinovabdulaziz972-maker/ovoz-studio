package uz.ovozstudio.app.media.voice

/**
 * Matnning ovozli o'qish uchun bo'lagi.
 *
 * [startOffset] va [endOffset] — bo'lakning **manba matndagi** chegaralari.
 * Ular shunchaki qulaylik emas: ekran o'quvchi foydalanuvchisi uchun
 * «hozir qaysi qism o'qilmoqda» degan savolga javob beradigan yagona
 * ishonchli manba shu. Matnni qaytadan qidirib topish noto'g'ri ishlaydi:
 * bir xil jumla matnda bir necha marta uchrasa, qaysi biri o'qilayotgani
 * noma'lum bo'lib qoladi.
 */
data class SpeechChunk(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
)

/**
 * Uzun matnni ovoz sintezatoriga bo'laklab berish.
 *
 * Nega bo'laklash kerak:
 *
 * 1. Android'ning `TextToSpeech.speak` i bitta chaqiruvda cheklangan uzunlikni
 *    qabul qiladi (`getMaxSpeechInputLength()`, odatda 4000 belgi). Undan uzun
 *    matn indamay tashlab ketiladi — foydalanuvchi uchun bu «ilova jim qoldi»
 *    degani, sababi esa ko'rinmaydi.
 * 2. To'xtatish va davom ettirish bo'lak chegarasida ishlaydi. Butun bob bitta
 *    chaqiruv bo'lsa, «to'xtat» tugmasi butun bobni boshidan boshlaydi.
 * 3. Ekran o'quvchi qaysi jumla o'qilayotganini bo'lak orqali aytadi.
 *
 * Bo'laklash **jumla** chegarasida qilinadi: sintezator jumla o'rtasidan
 * boshlansa, intonatsiya buziladi va so'zlar boshqa ma'noda eshitiladi.
 *
 * Matn o'zgartirilmaydi — bo'lak matni manbadan qirqib olinadi, hech qanday
 * belgi qo'shilmaydi yoki olib tashlanmaydi. Shu sababli ofsetlar manbaga
 * to'g'ri keladi.
 */
object TextChunker {

    /**
     * Bitta bo'lak uchun standart chegara.
     *
     * 4000 emas, undan past: chegara — texnik limit emas, balki to'xtatish
     * qulayligi. 3000 belgi o'rtacha o'qish tezligida ~2 daqiqa, ya'ni
     * foydalanuvchi kutadigan eng uzun bo'lak.
     */
    const val DEFAULT_MAX_CHARS = 3000

    /**
     * Bundan qisqa chegara ma'nosiz: har bir so'z alohida chaqiruv bo'lib
     * qolardi, sintezator esa har chaqiruv orasida pauza qiladi — o'qish
     * bo'g'ilib eshitilardi.
     *
     * Bu ataylab **past** chegara: u faqat bema'ni qiymatdan saqlaydi,
     * chaqiruvchi bergan qiymatni o'zgartirmaydi. Yuqori chegara qo'yilsa
     * (masalan 100), `maxChars = 30` deb chaqirgan kod jimgina boshqa
     * natija olardi va sababini hech qayerdan bilmasdi.
     */
    const val MIN_MAX_CHARS = 20

    /**
     * Qisqartmalar — nuqtadan keyin jumla **boshlanmaydi**.
     *
     * Ro'yxat ataylab keng: xato bo'lib jumlani erta bo'lish sintaksisni
     * buzadi, bo'lmasa esa zarari yo'q — bo'laklar baribir chegaraga yetganda
     * qo'shilib ketadi. Ya'ni xato bir tomonga og'adi va u xavfsiz tomonga.
     * O'zbek lotin va kirill yozuvidagi eng ko'p uchraydiganlari.
     */
    private val ABBREVIATIONS = setOf(
        // lotin
        "h.k", "hok", "va b", "v.b", "v.h", "sh.k", "b", "t", "y", "s", "q", "r",
        "son", "vv", "k", "m", "n", "km", "kg", "sm", "mm", "ms", "min", "sek",
        "mas", "qarang", "qiyos", "jan", "fev", "apr", "avg", "sen", "okt", "noy", "dek",
        // kirill
        "ҳ.к", "ҳок", "ва б", "в.б", "в.ҳ", "ш.к", "б", "т", "й", "с", "қ", "р",
        "сон", "вв", "к", "м", "н", "км", "кг", "см", "мм", "мс", "мин", "сек",
        "қаранг", "қиёс", "янв", "фев", "апр", "авг", "сен", "окт", "ноя", "дек",
    )

    /**
     * Matnni bo'laklarga bo'ladi. Matn bo'sh bo'lsa — bo'sh ro'yxat.
     *
     * @param maxChars bitta bo'lakdagi eng ko'p belgi. [MIN_MAX_CHARS] dan
     *   kichik qiymat shu chegaraga ko'tariladi: aks holda bitta so'z ham
     *   sig'may, bo'laklash ma'nosiz bo'lardi.
     */
    fun split(text: String, maxChars: Int = DEFAULT_MAX_CHARS): List<SpeechChunk> {
        val limit = maxChars.coerceAtLeast(MIN_MAX_CHARS)
        if (text.isBlank()) return emptyList()

        val chunks = mutableListOf<SpeechChunk>()
        var currentStart = -1
        var currentEnd = -1

        fun flush() {
            if (currentStart >= 0 && currentEnd > currentStart) {
                chunks += SpeechChunk(text.substring(currentStart, currentEnd), currentStart, currentEnd)
            }
            currentStart = -1
            currentEnd = -1
        }

        for (sentence in sentences(text)) {
            if (sentence.end - sentence.start > limit) {
                // Bitta jumla ham chegaradan uzun (uzun ro'yxat, tinish
                // belgisisiz yozilgan nutq). Uni so'z chegarasida bo'lamiz.
                flush()
                chunks += splitLongSpan(text, sentence.start, sentence.end, limit)
                continue
            }
            // Qo'shilgan uzunlik manba bo'yicha o'lchanadi: oradagi bo'shliq
            // ham o'sha oraliqqa kiradi, ya'ni hisob aniq.
            if (currentStart >= 0 && sentence.end - currentStart > limit) flush()
            if (currentStart < 0) currentStart = sentence.start
            currentEnd = sentence.end
        }
        flush()

        return chunks
    }

    /** Matnning ixtiyoriy bir qismini bo'laklarga bo'lish (bob, paragraf). */
    fun splitRange(text: String, start: Int, end: Int, maxChars: Int = DEFAULT_MAX_CHARS): List<SpeechChunk> {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        if (to == from) return emptyList()
        return split(text.substring(from, to), maxChars).map {
            SpeechChunk(it.text, it.startOffset + from, it.endOffset + from)
        }
    }

    private class Span(val start: Int, val end: Int)

    /**
     * Matndagi jumlalarning chegaralarini topadi.
     *
     * Jumla oxiri — `.`, `!`, `?`, `…` yoki qator oxiri. Nuqta har doim ham
     * jumla oxiri emas, shuning uchun uchta himoya bor: son ichidagi nuqta
     * (`3.14`, `1.000`), qisqartma (`2020 y.`), va kichik harf bilan davom
     * etgan nuqta (`va h.k. keyin`).
     */
    private fun sentences(text: String): List<Span> {
        val result = mutableListOf<Span>()
        val n = text.length
        var i = 0
        var start = -1

        while (i < n) {
            val c = text[i]

            if (start < 0) {
                if (c.isWhitespace()) {
                    i++
                    continue
                }
                start = i
            }

            if (c == '\n') {
                val end = trimEnd(text, start, i)
                if (end > start) result += Span(start, end)
                start = -1
                i++
                continue
            }

            if (isTerminator(c) && isSentenceEnd(text, i)) {
                // Yopuvchi qo'shtirnoq va qavs jumla ichida qoladi:
                // «U keldi.» — bu yerda qo'shtirnoq jumlaning bir qismi.
                var j = i + 1
                while (j < n && (text[j] == '»' || text[j] == '"' || text[j] == ')' ||
                        text[j] == '“' || text[j] == '”' || text[j] == '’')
                ) {
                    j++
                }
                val end = trimEnd(text, start, j)
                if (end > start) result += Span(start, end)
                start = -1
                i = j
                continue
            }

            i++
        }

        if (start >= 0) {
            val end = trimEnd(text, start, n)
            if (end > start) result += Span(start, end)
        }
        return result
    }

    private fun isTerminator(c: Char): Boolean = c == '.' || c == '!' || c == '?' || c == '…'

    private fun isSentenceEnd(text: String, index: Int): Boolean {
        val c = text[index]
        if (c != '.') return true

        // 1. Son ichidagi nuqta: 3.14, 1.000, 12.05.2026
        val before = text.getOrNull(index - 1)
        val after = text.getOrNull(index + 1)
        if (before != null && after != null && before.isDigit() && after.isDigit()) return false

        // 2. Nuqtadan keyingi bo'shliqdan keyingi belgi kichik harf bo'lsa,
        //    gap davom etmoqda: «va h.k. keyin».
        val next = nextNonSpace(text, index + 1)
        if (next != null && (next.isLetter() && next.isLowerCase())) return false

        // 3. Qisqartma: «2020 y.», «5 km.», «2-§.»
        val token = tokenBefore(text, index)
        if (token.isNotEmpty() && ABBREVIATIONS.contains(token.lowercase())) return false

        return true
    }

    private fun nextNonSpace(text: String, from: Int): Char? {
        var i = from
        while (i < text.length) {
            if (!text[i].isWhitespace() && text[i] != '»' && text[i] != '"' && text[i] != ')') return text[i]
            i++
        }
        return null
    }

    /**
     * Nuqtadan oldingi so'z: harflar va qisqartma ichidagi nuqtalar.
     *
     * «va h.k.» uchun `h.k` qaytariladi — ya'ni ichki nuqta so'zning bir
     * qismi hisoblanadi, oxirgisi esa ajratuvchi. Shu sababli ro'yxatdagi
     * qisqartmalar ham nuqtali yozilgan.
     */
    private fun tokenBefore(text: String, index: Int): String {
        var i = index - 1
        while (i >= 0) {
            val c = text[i]
            if (c.isLetter()) {
                i--
                continue
            }
            if (c == '.' && i > 0 && text[i - 1].isLetter()) {
                i--
                continue
            }
            break
        }
        return text.substring(i + 1, index)
    }

    /** [from, to) dan oxiridagi bo'shliqni tashlab, haqiqiy oxirni qaytaradi. */
    private fun trimEnd(text: String, from: Int, to: Int): Int {
        var e = to
        while (e > from && text[e - 1].isWhitespace()) e--
        return e
    }

    /**
     * Chegaradan uzun bitta jumlani so'z chegaralarida bo'laklarga bo'ladi.
     *
     * Son ichidan kesish alohida taqiqlanadi: «12 500» ni o'rtasidan kessak,
     * sintezator «o'n ikki» va «besh yuz» deb ikki marta o'qiydi.
     */
    private fun splitLongSpan(text: String, start: Int, end: Int, limit: Int): List<SpeechChunk> {
        val result = mutableListOf<SpeechChunk>()
        var from = start

        while (from < end) {
            val hardEnd = minOf(from + limit, end)
            val cut = if (hardEnd >= end) end else safeCut(text, from, hardEnd)
            val tightEnd = trimEnd(text, from, cut)
            if (tightEnd > from) {
                result += SpeechChunk(text.substring(from, tightEnd), from, tightEnd)
            }
            from = cut
            while (from < end && text[from].isWhitespace()) from++
        }

        return result
    }

    /**
     * [from, hardEnd] oralig'ida eng yaqin xavfsiz kesim nuqtasini topadi.
     *
     * Qaytarilgan qiymat har doim `from` dan katta — aks holda bo'laklash
     * to'xtab qolardi.
     */
    private fun safeCut(text: String, from: Int, hardEnd: Int): Int {
        var c = hardEnd
        while (c > from + 1) {
            val atSpace = text[c - 1].isWhitespace() || (c < text.length && text[c].isWhitespace())
            val insideNumber = c < text.length && text[c - 1].isDigit() && text[c].isDigit()
            if (atSpace && !insideNumber) return c
            c--
        }
        // Bo'shliq umuman yo'q (bitta juda uzun so'z yoki uzun son).
        // Hech bo'lmasa raqamlar orasidan kesmaymiz.
        var d = hardEnd
        while (d > from + 1 && d < text.length && text[d - 1].isDigit() && text[d].isDigit()) d--
        return d
    }
}
