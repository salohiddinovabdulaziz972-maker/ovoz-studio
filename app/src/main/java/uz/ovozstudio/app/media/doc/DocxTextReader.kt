package uz.ovozstudio.app.media.doc

import java.io.File
import java.util.zip.ZipFile

/**
 * DOCX (Word) hujjatidan matn ajratadi.
 *
 * DOCX — ZIP arxivi; matn `word/document.xml` ichida. Paragraflar `<w:p>`
 * tegida, matn esa `<w:t>` ichida yotadi — qolgan teglar (uslub, formatlash,
 * jadval chegaralari) matnga aloqasi yo'q.
 *
 * Sarlavhalar Word'da matnning o'zidan emas, **uslubdan** bilinadi:
 * `w:pStyle w:val="Heading1"`. Shuning uchun ular shu yerdan o'qilib,
 * markdown belgisiga aylantiriladi — keyin [ChapterSplitter] ularni oddiy
 * sarlavha deb taniydi.
 */
object DocxTextReader {

    const val DOCUMENT_ENTRY = "word/document.xml"

    /** Bitta XML fayl uchun chegara. Kitob matni odatda bir necha megabayt. */
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    /**
     * Word o'z uslubini tilga qarab nomlaydi: `Heading1`, `Заголовок1`,
     * LibreOffice esa `Heading_20_1` ko'rinishida yozadi.
     *
     * Diqqat: bu yerda `RegexOption.IGNORE_CASE` ishlatiladi, naqsh ichidagi
     * `(?i)` emas. Kotlin bayroq orqali berilganda registrga sezgirlikni
     * Unicode bo'ylab yoqadi, naqsh ichidagi `(?i)` esa faqat lotin
     * harflariga ta'sir qiladi — «Заголовок1» o'shanda topilmay qolardi.
     */
    private val HEADING_STYLE = Regex(
        """^(?:heading|заголовок|sarlavha)[_\s]*([1-6])$""",
        RegexOption.IGNORE_CASE,
    )

    private val RULES = MarkupRules(
        textTags = setOf("t"),
        paragraphTags = setOf("p"),
        breakTags = setOf("br", "cr"),
        tabTag = "tab",
        headingTags = emptyMap(),
        // `instrText` — maydon kodlari («PAGE» kabi), `delText` — o'chirilgan
        // va hujjatda ko'rinmaydigan matn. Ikkalasi ham o'qilmasligi kerak.
        skipTags = setOf("instrtext", "deltext"),
        styleTag = "pstyle",
        styleLevel = { value ->
            // LibreOffice probelni `_20_` qilib yozadi: `Heading_20_1`.
            val normalized = value.replace("_20_", " ")
            HEADING_STYLE.find(normalized)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        },
    )

    fun read(file: File): String = ZipEntries.open(file).use { zip -> read(zip) }

    fun read(zip: ZipFile): String {
        val xml = ZipEntries.read(zip, DOCUMENT_ENTRY, MAX_ENTRY_BYTES)
            ?: throw DocumentFormatException("DOCX ichida $DOCUMENT_ENTRY topilmadi")
        return MarkupBlocks.parse(xml, RULES)
    }
}
