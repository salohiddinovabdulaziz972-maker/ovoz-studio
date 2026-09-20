package uz.ovozstudio.app.media.doc

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/**
 * EPUB kitobidan matn ajratadi.
 *
 * EPUB — ZIP arxivi, lekin DOCX'dan farqli ravishda matn bitta faylda emas:
 * kitob boblarga bo'lingan ko'p XHTML fayldan iborat. Qaysi fayl qaysi
 * tartibda o'qilishini paket fayli (`content.opf`) aytadi:
 *
 *  1. `META-INF/container.xml` — paket fayli qayerda yotganini aytadi.
 *  2. Paket fayli — `<manifest>` (id → fayl yo'li) va `<spine>` (o'qish
 *     tartibi).
 *  3. Har bir XHTML fayl — matn shu yerdan olinadi, `<h1>`–`<h6>` esa
 *     sarlavha bo'lib qoladi.
 *
 * **Ma'lum cheklov:** bob sarlavhalari faqat XHTML ichidagi `<h1>`–`<h6>`
 * teglaridan olinadi. Ba'zi kitoblarda sarlavha faqat mundarija faylida
 * (NCX yoki nav) bo'ladi va u holda kitob bitta katta bob bo'lib chiqadi.
 * Bu — yo'qotish emas, balki bo'linmagan kitob; mundarijani o'qish keyingi
 * qadam sifatida qo'shiladi.
 */
object EpubTextReader {

    const val CONTAINER_ENTRY = "META-INF/container.xml"

    /** Paket fayllari kichik: bir necha megabaytdan kattasi shubhali. */
    const val MAX_META_BYTES = 4L * 1024 * 1024

    const val MAX_ENTRY_BYTES = 64L * 1024 * 1024

    /**
     * Barcha o'qilgan hujjatlarning yig'indi hajmi chegarasi.
     *
     * Har bir fayl [MAX_ENTRY_BYTES] dan kichik bo'lsa ham, 2000 ta fayl
     * ([MAX_DOCUMENTS]) yuzlab gigabayt beradi — zip-bomba shunday
     * yig'iladi. Oddiy kitobning barcha XHTML fayllari birgalikda bir necha
     * megabayt.
     */
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024

    /**
     * O'qiladigan hujjatlar soni chegarasi. Minglab fayldan iborat arxiv
     * oddiy kitob emas — bu cheksiz kutishdan saqlaydi.
     */
    const val MAX_DOCUMENTS = 2000

    private val XHTML_RULES = MarkupRules(
        textTags = null,
        paragraphTags = setOf("p", "div", "li", "section", "article", "blockquote"),
        breakTags = setOf("br"),
        tabTag = null,
        headingTags = mapOf(
            "h1" to 1, "h2" to 2, "h3" to 3, "h4" to 4, "h5" to 5, "h6" to 6,
        ),
        // `head` ichida kitob nomi va uslub havolalari bor — ular matn emas.
        skipTags = setOf("script", "style", "head"),
    )

    fun read(file: File): String = ZipEntries.open(file).use { zip -> read(zip) }

    fun read(zip: ZipFile, maxTotalBytes: Long = MAX_TOTAL_BYTES): String {
        val container = ZipEntries.read(zip, CONTAINER_ENTRY, MAX_META_BYTES)
            ?: throw DocumentFormatException("EPUB ichida $CONTAINER_ENTRY topilmadi")

        val opfPath = MarkupBlocks.attributes(container, "rootfile", "full-path").firstOrNull()
            ?: throw DocumentFormatException("EPUB konteynerida paket fayli ko'rsatilmagan")

        val opf = ZipEntries.read(zip, opfPath, MAX_META_BYTES)
            ?: throw DocumentFormatException("EPUB ichida $opfPath topilmadi")

        val packageHandler = PackageHandler()
        MarkupBlocks.scan(opf, packageHandler)

        val base = opfPath.substringBeforeLast('/', "")
        val out = StringBuilder()
        var documents = 0
        var totalBytes = 0L

        for (id in packageHandler.spine) {
            if (documents >= MAX_DOCUMENTS) break
            val href = packageHandler.items[id] ?: continue
            val bytes = ZipEntries.read(zip, resolvePath(base, href), MAX_ENTRY_BYTES) ?: continue
            documents++
            totalBytes += bytes.size
            if (totalBytes > maxTotalBytes) throw DocumentTooLargeException(maxTotalBytes)

            val text = MarkupBlocks.parse(bytes, XHTML_RULES)
            if (text.isBlank()) continue
            if (out.isNotEmpty()) out.append("\n\n")
            out.append(text)
        }

        if (out.isEmpty()) {
            throw DocumentFormatException("EPUB ichida o'qiladigan matn topilmadi")
        }
        return out.toString()
    }
}

/** Paket faylidan kerakli ikkitasini yig'adi: fayllar jadvali va tartib. */
internal class PackageHandler : DefaultHandler() {

    /** `id` → arxiv ichidagi yo'l. */
    val items = LinkedHashMap<String, String>()

    /** O'qish tartibi — `manifest` dagi `id` lar. */
    val spine = ArrayList<String>()

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes?,
    ) {
        when (tagName(localName, qName)) {
            "item" -> {
                val id = attributes.attr("id") ?: return
                val href = attributes.attr("href") ?: return
                items[id] = href
            }
            "itemref" -> attributes.attr("idref")?.let { spine += it }
        }
    }
}

/**
 * Paket faylidan nisbiy yo'lni arxiv ichidagi to'liq yo'lga aylantiradi.
 * `../` ham, `%20` ko'rinishidagi belgilar ham uchraydi.
 */
internal fun resolvePath(base: String, href: String): String {
    val segments = ArrayList<String>()
    if (base.isNotEmpty()) segments += base.split('/').filter { it.isNotEmpty() }

    for (segment in percentDecode(href.substringBefore('#')).split('/')) {
        when (segment) {
            "", "." -> Unit
            // Arxiv ildizidan yuqoriga chiqib bo'lmaydi: ortiqcha `..` shunchaki
            // tashlab yuboriladi — aks holda yo'l arxivdan tashqariga chiqib
            // ketardi.
            ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
            else -> segments += segment
        }
    }
    return segments.joinToString("/")
}

/** `%20` → probel. `+` ga tegilmaydi: yo'lda u oddiy plyus belgisi. */
internal fun percentDecode(value: String): String {
    if ('%' !in value) return value

    val out = StringBuilder()
    val pending = ByteArrayOutputStream()
    var i = 0
    while (i < value.length) {
        val c = value[i]
        if (c == '%' && i + 2 < value.length) {
            val code = value.substring(i + 1, i + 3).toIntOrNull(16)
            if (code != null) {
                pending.write(code)
                i += 3
                continue
            }
        }
        if (pending.size() > 0) {
            out.append(String(pending.toByteArray(), Charsets.UTF_8))
            pending.reset()
        }
        out.append(c)
        i++
    }
    if (pending.size() > 0) out.append(String(pending.toByteArray(), Charsets.UTF_8))
    return out.toString()
}
