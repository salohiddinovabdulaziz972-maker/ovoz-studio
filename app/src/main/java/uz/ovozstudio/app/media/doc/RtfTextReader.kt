package uz.ovozstudio.app.media.doc

import java.io.File
import java.nio.charset.Charset

/**
 * RTF hujjatdan matn ajratadi.
 *
 * RTF — 7 bitli matn: `{\rtf1\ansi Salom\par}`. Buyruqlar teskari chiziq bilan
 * boshlanadi, guruhlar `{ }` ichida. Matnni olish uchun to'liq tahlilchi
 * kerak emas, faqat quyidagilarni to'g'ri bajarish kifoya:
 *
 *  - **guruh mazmuni**: shrift, rang, uslub jadvallari, sarlavha/pastki
 *    kolontitul, rasm, izoh — matn emas, ular butunlay tashlanadi. `\*` bilan
 *    boshlangan guruh («e'tiborsiz qoldirilishi mumkin») ham tashlanadi;
 *  - **belgilar**: `\'e9` — kodlash jadvalidagi bitta bayt, jadval esa
 *    `\ansicpg1251` va shriftning `\fcharset204` qiymatidan olinadi (Word rus
 *    va o'zbek kirill matnini shunday yozadi); `\u1178?` — Unicode belgi,
 *    undan keyingi `?` — eski o'quvchilar uchun zaxira belgi, u tashlanadi
 *    (nechta belgi tashlanishini `\uc` aytadi);
 *  - **tuzilma**: `\par` — paragraf, `\line` — qator ko'chirish, `\tab` va
 *    `\cell` — probel.
 *
 * Yangi qator belgilari (`\r`, `\n`) faylning o'zida ma'noga ega emas —
 * RTF ularni e'tiborsiz qoldiradi.
 */
object RtfTextReader {

    private const val DEFAULT_PAGE = 1252

    /** Ichida matn bo'lmagan guruhlar (shrift jadvali alohida ko'riladi). */
    private val SKIPPED = setOf(
        "colortbl", "stylesheet", "info", "pict", "object", "header", "headerl", "headerr",
        "headerf", "footer", "footerl", "footerr", "footerf", "footnote", "listtable",
        "listoverridetable", "themedata", "colorschememapping", "latentstyles", "datastore",
        "revtbl", "rsidtbl", "generator", "xmlnstbl", "template", "private", "filetbl",
        "pgptbl", "protusertbl",
    )

    private val BREAKS = setOf("par", "line", "sect", "page", "row", "column", "pagebb")
    private val SPACES = setOf("tab", "cell", "nestcell")

    private val SYMBOLS = mapOf(
        "emdash" to "\u2014", "endash" to "\u2013", "bullet" to "\u2022",
        "lquote" to "\u2018", "rquote" to "\u2019", "ldblquote" to "\u201C", "rdblquote" to "\u201D",
        "emspace" to " ", "enspace" to " ", "qmspace" to " ",
    )

    /** Shriftning `\fcharset` qiymati → kodlash jadvali (faqat bir baytli jadvallar). */
    private val FONT_PAGES = mapOf(
        0 to 1252, 161 to 1253, 162 to 1254, 163 to 1258, 177 to 1255,
        178 to 1256, 186 to 1257, 204 to 1251, 222 to 874, 238 to 1250,
    )

    private val SPACE = Regex("[\\s\\u00A0\\u200B]+")

    fun read(bytes: ByteArray): String = Parser(String(bytes, Charsets.ISO_8859_1)).run()

    fun read(file: File): String =
        file.inputStream().use { input -> read(PlainTextDecoder.readLimited(input)) }

    /** Guruh holati: `{` bilan nusxalanadi, `}` bilan tiklanadi. */
    private class Group(
        var skip: Boolean = false,
        var uc: Int = 1,
        var font: Int = -1,
        var fontTable: Boolean = false,
    ) {
        fun copy(): Group = Group(skip, uc, font, fontTable)
    }

    private class Parser(private val s: String) {

        private val out = StringBuilder()
        private val stack = ArrayList<Group>()
        private var group = Group()
        private var documentPage = DEFAULT_PAGE
        private val fontPages = HashMap<Int, Int>()
        private val tables = HashMap<Int, String>()
        private var fontDefinition = -1

        /** `\u` dan keyin tashlanishi kerak bo'lgan zaxira belgilar soni. */
        private var skipChars = 0

        /** `{` dan keyingi birinchi buyruq guruh turini belgilaydi. */
        private var expectDestination = false
        private var i = 0

        fun run(): String {
            while (i < s.length) {
                val c = s[i]
                if (c == '{') {
                    stack.add(group.copy())
                    skipChars = 0
                    expectDestination = true
                    i++
                } else if (c == '}') {
                    if (stack.isNotEmpty()) group = stack.removeAt(stack.size - 1)
                    skipChars = 0
                    expectDestination = false
                    i++
                } else if (c == '\\') {
                    escape()
                } else if (c == '\r' || c == '\n') {
                    i++
                } else {
                    plain(c)
                    i++
                }
            }
            return tidy(out.toString())
        }

        /** Teskari chiziq: buyruq so'zi yoki buyruq belgisi. */
        private fun escape() {
            if (i + 1 >= s.length) {
                i++
                return
            }
            val next = s[i + 1]
            if (isLetter(next)) {
                word()
                return
            }

            i += 2
            when (next) {
                '\\', '{', '}' -> {
                    literal(next)
                    expectDestination = false
                }
                '\'' -> {
                    hexByte()
                    expectDestination = false
                }
                // `\*` — e'tiborsiz qoldiriladigan guruh: shu guruh tashlanadi.
                '*' -> if (expectDestination) group.skip = true
                '~' -> {
                    literal(' ')
                    expectDestination = false
                }
                '_' -> {
                    literal('-')
                    expectDestination = false
                }
                '\r', '\n' -> {
                    literal('\n')
                    expectDestination = false
                }
                else -> expectDestination = false
            }
        }

        /** `\so'z`, ixtiyoriy son va bitta probel-ajratgich. */
        private fun word() {
            var end = i + 1
            while (end < s.length && isLetter(s[end])) end++
            val name = s.substring(i + 1, end)

            var next = end
            var number: Int? = null
            if (next < s.length && (s[next] == '-' || isDigit(s[next]))) {
                var digitsEnd = next
                if (s[digitsEnd] == '-') digitsEnd++
                while (digitsEnd < s.length && isDigit(s[digitsEnd])) digitsEnd++
                number = s.substring(next, digitsEnd).toIntOrNull()
                next = digitsEnd
            }
            // Buyruqdan keyingi bitta probel — ajratgich, matn emas.
            if (next < s.length && s[next] == ' ') next++
            i = next

            command(name, number)
        }

        private fun command(name: String, number: Int?) {
            if (expectDestination) {
                if (name == "fonttbl") {
                    group.skip = true
                    group.fontTable = true
                } else if (name in SKIPPED) {
                    group.skip = true
                }
            }
            expectDestination = false

            when (name) {
                "ansicpg" -> if (number != null && number > 0) documentPage = number
                "uc" -> group.uc = number ?: 1
                "u" -> if (number != null) {
                    if (!group.skip) {
                        // Ishorali 16 bit: 32767 dan katta kodlar manfiy yoziladi.
                        val code = if (number < 0) number + 65536 else number
                        out.append(code.toChar())
                    }
                    skipChars = group.uc
                }
                "f" -> if (number != null) {
                    if (group.fontTable) fontDefinition = number else group.font = number
                }
                "fcharset" -> if (group.fontTable && fontDefinition >= 0 && number != null) {
                    fontPages[fontDefinition] = FONT_PAGES[number] ?: documentPage
                }
                else -> if (!group.skip) {
                    if (name in BREAKS) {
                        out.append('\n')
                    } else if (name in SPACES) {
                        out.append(' ')
                    } else {
                        val symbol = SYMBOLS[name]
                        if (symbol != null) out.append(symbol)
                    }
                }
            }
        }

        /** `\'e9` — ikki o'n oltilik raqam. */
        private fun hexByte() {
            if (i + 1 >= s.length) return
            val value = s.substring(i, i + 2).toIntOrNull(16) ?: return
            i += 2
            if (skipChars > 0) {
                skipChars--
            } else if (!group.skip) {
                byteChar(value)
            }
        }

        /** Oddiy belgi. 127 dan katta bo'lsa, u kodlash jadvalidagi bayt. */
        private fun plain(c: Char) {
            expectDestination = false
            if (skipChars > 0) {
                skipChars--
                return
            }
            if (group.skip) return
            if (c.code < 128) out.append(c) else byteChar(c.code)
        }

        private fun literal(c: Char) {
            if (skipChars > 0) {
                skipChars--
            } else if (!group.skip) {
                out.append(c)
            }
        }

        private fun byteChar(value: Int) {
            val page = fontPages[group.font] ?: documentPage
            val table = tables.getOrPut(page) { tableFor(page) }
            out.append(table[value and 0xFF])
        }

        /** Bo'sh qatorlarni tashlab, paragraflarni bo'sh qator bilan ajratadi. */
        private fun tidy(text: String): String = text.split('\n')
            .map { SPACE.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")

        private fun isLetter(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

        private fun isDigit(c: Char): Boolean = c in '0'..'9'

        /**
         * 256 ta bayt uchun belgi jadvali. Jadval topilmasa yoki bir baytli
         * bo'lmasa (masalan UTF-8) — ISO-8859-1.
         */
        private fun tableFor(page: Int): String {
            val all = ByteArray(256) { it.toByte() }
            val name = when (page) {
                866 -> "IBM866"
                65001 -> "UTF-8"
                else -> "windows-$page"
            }
            val decoded = try {
                String(all, Charset.forName(name))
            } catch (error: Exception) {
                null
            }
            return if (decoded != null && decoded.length == 256) decoded else String(all, Charsets.ISO_8859_1)
        }
    }
}
