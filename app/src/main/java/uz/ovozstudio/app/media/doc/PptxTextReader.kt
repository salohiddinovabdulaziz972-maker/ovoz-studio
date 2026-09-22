package uz.ovozstudio.app.media.doc

import java.io.File
import java.util.zip.ZipFile

/**
 * PowerPoint (PPTX) taqdimotidan matn ajratadi.
 *
 * PPTX — ZIP arxivi; har bir slayd alohida `ppt/slides/slideN.xml` faylida.
 * Matn `<a:t>` ichida, paragraflar `<a:p>` da. Har slayd o'z sarlavhasi bilan
 * beriladi («Slayd 3»), shunda o'quvchi slaydlar bo'yicha yura oladi.
 *
 * Slaydlar fayl nomidagi raqam bo'yicha tartiblanadi. Taqdimotda slaydlar
 * qayta joylashtirilgan bo'lsa, tartib `presentation.xml` da turadi va u
 * fayl nomlaridan farq qilishi mumkin — bu holda o'qish tartibi aniq
 * ko'rsatkichdan bir oz farq qiladi. Matn yo'qolmaydi.
 */
object PptxTextReader {

    const val PRESENTATION_ENTRY = "ppt/presentation.xml"

    /** Bitta slayd uchun chegara. */
    const val MAX_ENTRY_BYTES = 16L * 1024 * 1024

    /** Hamma slaydlar uchun umumiy chegara («zip-bomba»dan himoya). */
    private const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    private val SLIDE = Regex("ppt/slides/slide(\\d+)\\.xml")

    private val RULES = MarkupRules(
        textTags = setOf("t"),
        paragraphTags = setOf("p"),
        breakTags = setOf("br"),
        tabTag = null,
        headingTags = emptyMap(),
        skipTags = emptySet(),
    )

    /** [slideTitle] — «Slayd»: chaqiruvchi tarjima qilingan so'zni beradi. */
    fun read(file: File, slideTitle: String = "Slide"): String =
        ZipEntries.open(file).use { zip -> read(zip, slideTitle) }

    fun read(zip: ZipFile, slideTitle: String = "Slide"): String {
        val slides = ArrayList<Pair<Int, String>>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val name = entries.nextElement().name
            val match = SLIDE.matchEntire(name) ?: continue
            val number = match.groupValues[1].toIntOrNull() ?: continue
            slides.add(number to name)
        }
        if (slides.isEmpty()) {
            throw DocumentFormatException("PPTX ichida slaydlar topilmadi")
        }
        slides.sortBy { it.first }

        val out = StringBuilder()
        var total = 0L
        for ((number, name) in slides) {
            val bytes = ZipEntries.read(zip, name, MAX_ENTRY_BYTES) ?: continue
            total += bytes.size
            if (total > MAX_TOTAL_BYTES) throw DocumentTooLargeException(MAX_TOTAL_BYTES)

            val text = MarkupBlocks.parse(bytes, RULES)
            if (text.isBlank()) continue
            if (out.isNotEmpty()) out.append("\n\n")
            out.append("# ").append(slideTitle).append(' ').append(number).append("\n\n").append(text)
        }
        if (out.isEmpty()) {
            throw DocumentTextMissingException("Taqdimotda o'qiladigan matn yo'q")
        }
        return out.toString()
    }
}
