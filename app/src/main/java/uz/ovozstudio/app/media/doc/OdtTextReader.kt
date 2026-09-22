package uz.ovozstudio.app.media.doc

import java.io.File
import java.util.zip.ZipFile

/**
 * OpenDocument (ODT, ODS, ODP — LibreOffice va boshqalar) hujjatidan matn ajratadi.
 *
 * ODF — ZIP arxivi; matn `content.xml` ichida. Paragraflar `<text:p>` tegida,
 * sarlavhalar `<text:h>` da; jadval katakchalari va slaydlar ham shu `<text:p>`
 * ni ishlatadi, shuning uchun uch xil fayl bitta qoida bilan o'qiladi.
 *
 * O'qilmaydigan qismlar: izohlar (`annotation`), kuzatilgan tuzatishlar
 * (`tracked-changes` — o'chirilgan matn hujjatda ko'rinmaydi) va **izohnomalar**
 * (`note`). Izohnoma gap o'rtasiga tushib qolardi va ovoz bilan o'qilganda
 * «Salom [pauza] izohnoma matni [pauza] dunyo» bo'lib eshitilardi.
 *
 * `<text:s/>` (ketma-ket probel) va `<text:span>` alohida qayd etilmaydi:
 * birinchisi bo'sh probel, ikkinchisi faqat formatlash.
 */
object OdtTextReader {

    const val CONTENT_ENTRY = "content.xml"

    /** Bitta XML fayl uchun chegara. Kitob matni odatda bir necha megabayt. */
    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    private val RULES = MarkupRules(
        textTags = null,
        paragraphTags = setOf("p"),
        breakTags = setOf("line-break"),
        tabTag = "tab",
        // ODF sarlavha darajasini atribut bilan beradi (`outline-level`), qoidalar
        // esa faqat teg nomiga qaraydi: hammasi bitta daraja. Bob ajratish uchun
        // bu yetarli.
        headingTags = mapOf("h" to 1),
        skipTags = setOf("annotation", "tracked-changes", "note"),
    )

    fun read(file: File): String = ZipEntries.open(file).use { zip -> read(zip) }

    fun read(zip: ZipFile): String {
        val xml = ZipEntries.read(zip, CONTENT_ENTRY, MAX_ENTRY_BYTES)
            ?: throw DocumentFormatException("ODT ichida $CONTENT_ENTRY topilmadi")
        return MarkupBlocks.parse(xml, RULES)
    }
}
