package uz.ovozstudio.app.media.doc

import java.io.File

/**
 * HTML sahifadan o'qiladigan matnni ajratadi.
 *
 * EPUB ichidagi XHTML qat'iy XML bo'lgani uchun uni SAX o'qiydi
 * ([MarkupBlocks]). Oddiy HTML esa XML emas: yopilmagan `<p>`, `<br>`,
 * `&nbsp;` — SAX ularda to'xtaydi. Shuning uchun bu yerda **toqatli**
 * o'quvchi: teglar matndan ajratib olinadi, tuzilma esa teg turiga qarab
 * tiklanadi.
 *
 * Qoidalar:
 *  - `<script>`, `<style>`, `<head>`, izohlar — matn emas, tashlanadi;
 *  - `<h1>`–`<h6>` markdown sarlavhasiga (`#`) aylanadi — [ChapterSplitter]
 *    uni bob deb taniydi;
 *  - `<p>`, `<div>`, `<li>`, `<tr>` va shunga o'xshash blok teglar —
 *    paragraf chegarasi, `<br>` — qator ko'chirish;
 *  - `&amp;`, `&nbsp;`, `&#1178;` kabi belgilar oddiy harfga aylanadi.
 *
 * Fayl jadvali ([PlainTextDecoder]) boshqa matn fayllari bilan bir xil
 * aniqlanadi.
 */
object HtmlTextReader {

    private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)

    private val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val HIDDEN = Regex("<(script|style|head|noscript|template|svg)\\b[^>]*>.*?</\\1\\s*>", OPTIONS)
    private val HEADING = Regex("<h([1-6])\\b[^>]*>(.*?)</h\\1\\s*>", OPTIONS)
    private val BREAK = Regex("<br\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val BLOCK = Regex(
        "</?(?:p|div|li|tr|ul|ol|dl|dd|dt|section|article|blockquote|pre|table|header|footer|" +
            "main|nav|aside|figure|figcaption|form|hr|h[1-6])\\b[^>]*>",
        RegexOption.IGNORE_CASE,
    )
    private val CELL_END = Regex("</t[dh]\\s*>", RegexOption.IGNORE_CASE)
    private val TAG = Regex("<[^>]*>")
    private val ENTITY = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,31});")
    private val SPACE = Regex("[\\s\\u00A0\\u200B]+")

    /** Eng ko'p uchraydigan nomli belgilar. Noma'lum nom joyida qoladi. */
    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
        "nbsp" to "\u00A0", "laquo" to "\u00AB", "raquo" to "\u00BB",
        "ldquo" to "\u201C", "rdquo" to "\u201D", "lsquo" to "\u2018", "rsquo" to "\u2019",
        "ndash" to "\u2013", "mdash" to "\u2014", "hellip" to "\u2026",
        "copy" to "\u00A9", "reg" to "\u00AE", "times" to "\u00D7", "middot" to "\u00B7",
        "bull" to "\u2022", "euro" to "\u20AC", "deg" to "\u00B0", "para" to "\u00B6", "sect" to "\u00A7",
    )

    fun read(bytes: ByteArray): String = convert(PlainTextDecoder.decode(bytes).text)

    fun read(file: File): String =
        file.inputStream().use { input -> convert(PlainTextDecoder.decode(input).text) }

    /** HTML matnini oddiy matnga aylantiradi. Fayl o'qishdan alohida — sinash oson. */
    fun convert(html: String): String {
        var text = COMMENTS.replace(html, "")
        text = HIDDEN.replace(text, "")
        text = HEADING.replace(text) { match ->
            val inner = SPACE.replace(TAG.replace(match.groupValues[2], ""), " ").trim()
            if (inner.isEmpty()) {
                "\n\n"
            } else {
                "\n\n" + "#".repeat(match.groupValues[1].toInt()) + " " + inner + "\n\n"
            }
        }
        text = BREAK.replace(text, "\n")
        text = BLOCK.replace(text, "\n\n")
        text = CELL_END.replace(text, " ")
        text = TAG.replace(text, "")
        text = decodeEntities(text)

        return text.split('\n')
            .map { SPACE.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }

    private fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        val decoded: String? = when {
            body.startsWith("#x") || body.startsWith("#X") -> codePoint(body.substring(2).toIntOrNull(16))
            body.startsWith("#") -> codePoint(body.substring(1).toIntOrNull())
            else -> NAMED[body]
        }
        decoded ?: match.value
    }

    private fun codePoint(value: Int?): String? {
        if (value == null || value <= 0 || value > 0x10FFFF) return null
        // Surrogat oralig'i — haqiqiy belgi emas.
        if (value in 0xD800..0xDFFF) return null
        return String(Character.toChars(value))
    }
}
