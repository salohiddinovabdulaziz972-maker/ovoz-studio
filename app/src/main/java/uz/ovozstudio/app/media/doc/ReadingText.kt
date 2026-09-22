package uz.ovozstudio.app.media.doc

/**
 * Bob matnini o'qish uchun abzatslarga bo'ladi.
 *
 * Ovoz (yoki ekran o'quvchi) matnni abzats bo'yicha o'qiydi: abzats — bitta
 * to'xtovsiz nutq, abzatslar orasida esa nafas oladigan pauza va «keyingi /
 * oldingi abzats» tugmalari uchun nuqta bor. Abzats noto'g'ri kesilsa, ikki
 * xil xato chiqadi: gap o'rtasida pauza (juda mayda kesish) yoki bir
 * nafasda besh sahifa (juda yirik).
 *
 * Xom matn turli ko'rinishda keladi:
 *  - DOCX, EPUB, FB2 — abzatslar allaqachon bo'sh qator bilan ajratilgan;
 *  - TXT va PDF — qator tor ustun kengligida uzilgan, gap qator o'rtasida
 *    davom etadi, so'z esa defis bilan ikkiga bo'linishi mumkin.
 *
 * Qoida ikkalasi uchun bir xil:
 *  1. bo'sh qator — abzats chegarasi;
 *  2. bo'sh qatorsiz keyingi qator: oldingisi gapni tugatgan (`. ! ? …`) va
 *     keyingisi yangi gap boshlagan bo'lsa (bosh harf, raqam, tire, qo'shtirnoq) —
 *     yangi abzats; aks holda qator uzilishi shunchaki joy tanqisligidan bo'lgan
 *     va qatorlar probel bilan birlashadi;
 *  3. qator oxiridagi defis va keyingi qator kichik harf bilan boshlansa —
 *     so'z bo'lingan, defis olib tashlanib qismlar tutashtiriladi; bosh harf
 *     bilan boshlansa — bu haqiqiy defis (`Sharq-G'arb`), u saqlanadi.
 *
 * Bu — evristika. Gap tugagan qatordan keyin bosh harf bilan boshlangan qator
 * abzats ichida ham uchraydi; natijada abzats ikkiga bo'linadi. Buning
 * zarari yo'q: o'qish davom etadi, faqat pauza bir oz ko'proq.
 *
 * Bu — sof matn mantiqi, Android'ga bog'liq emas.
 */
object ReadingText {

    private const val TERMINAL = ".!?\u2026\u00BB\"\u201D)"
    private const val OPENERS = "\"\u00AB\u201C\u2018'\u2014\u2013-\u2022*([\u201E"

    private val BLANK_LINE = Regex("\\n[ \\t\\r]*\\n")
    private val MARKDOWN_HEADING = Regex("^#{1,6}\\s+")
    private val WHITESPACE = Regex("[\\s\\u00A0\\u200B]+")

    fun paragraphs(text: String): List<String> {
        val result = ArrayList<String>()
        for (block in text.split(BLANK_LINE)) {
            val lines = block.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var current = StringBuilder(lines[0])
            for (index in 1 until lines.size) {
                val line = lines[index]
                if (startsNewParagraph(current, line)) {
                    add(result, current.toString())
                    current = StringBuilder(line)
                } else {
                    join(current, line)
                }
            }
            add(result, current.toString())
        }
        return result
    }

    private fun add(result: MutableList<String>, raw: String) {
        // Sarlavha belgisi (`## `) ovozda «panjara» bo'lib eshitilmasligi kerak.
        val cleaned = WHITESPACE.replace(raw.replace(MARKDOWN_HEADING, ""), " ").trim()
        if (cleaned.isNotEmpty()) result.add(cleaned)
    }

    private fun startsNewParagraph(previous: StringBuilder, next: String): Boolean {
        val last = previous[previous.length - 1]
        if (TERMINAL.indexOf(last) < 0) return false
        val first = next[0]
        return first.isUpperCase() || first.isDigit() || OPENERS.indexOf(first) >= 0
    }

    private fun join(current: StringBuilder, next: String) {
        val last = current[current.length - 1]
        val beforeLast = if (current.length >= 2) current[current.length - 2] else ' '
        val hyphenated = last == '-' && beforeLast.isLetter()
        if (hyphenated && next[0].isLowerCase()) {
            // «so'z-» + «lar» — so'z qator oxirida bo'lingan: defis olinadi.
            current.setLength(current.length - 1)
            current.append(next)
        } else if (hyphenated && next[0].isUpperCase()) {
            // «Sharq-» + «G'arb» — bosh harfdan oldingi defis haqiqiy defis.
            current.append(next)
        } else {
            current.append(' ').append(next)
        }
    }
}
