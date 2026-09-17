package uz.ovozstudio.app.media.doc

import java.io.StringReader
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXNotSupportedException
import org.xml.sax.helpers.DefaultHandler

/**
 * Belgilangan matndan ajratilgan bo'lak.
 *
 * @property level sarlavha darajasi (1–6), 0 — oddiy paragraf.
 * @property text tozalangan matn; ichida `\n` bo'lishi mumkin (majburiy
 *   satr ko'chirish), lekin bo'sh qator yo'q.
 */
internal data class TextBlock(val level: Int, val text: String)

/**
 * XML ni bo'laklarga ajratish qoidalari. DOCX va XHTML bir xil ishlovdan
 * o'tadi, farq faqat shu sozlamalarda.
 */
internal class MarkupRules(
    /** Matn faqat shu teglar ichida olinadi; `null` — hamma matn olinadi. */
    val textTags: Set<String>?,
    val paragraphTags: Set<String>,
    val breakTags: Set<String>,
    val tabTag: String?,
    /** Teg nomi → sarlavha darajasi (`h1` → 1). */
    val headingTags: Map<String, Int>,
    /** Ichidagi hamma narsa tashlab yuboriladigan teglar. */
    val skipTags: Set<String>,
    /** Uslub tegining nomi (DOCX'da `pStyle`); XHTML'da `null`. */
    val styleTag: String? = null,
    /** Uslub qiymatidan sarlavha darajasini beradi; 0 — sarlavha emas. */
    val styleLevel: ((String) -> Int)? = null,
)

/**
 * XML oqimini o'qib, undan toza matn bo'laklarini yig'adi.
 *
 * Nega SAX, DOM emas: kitob fayli bir necha megabayt bo'ladi va uni
 * butunlay daraxtga aylantirish xotirani behuda yeydi.
 */
internal class BlocksHandler(private val rules: MarkupRules) : DefaultHandler() {

    private val blocks = ArrayList<TextBlock>()
    private val buffer = StringBuilder()

    /** Teg orqali berilgan daraja (XHTML: `<h1>`). */
    private var tagLevel = 0

    /** Uslub orqali berilgan daraja (DOCX: `w:pStyle w:val="Heading1"`). */
    private var styleLevel = 0

    private var textDepth = 0
    private var skipDepth = 0
    private var capture = false

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes?,
    ) {
        val name = tagName(localName, qName)

        if (rules.skipTags.contains(name)) {
            skipDepth++
            return
        }

        if (rules.styleTag != null && name == rules.styleTag) {
            val value = attributes.attr("val")
            if (value != null) styleLevel = rules.styleLevel?.invoke(value) ?: 0
            return
        }

        val heading = rules.headingTags[name]
        if (heading != null) {
            tagLevel = heading
            return
        }

        if (rules.breakTags.contains(name)) {
            buffer.append('\n')
            return
        }
        if (rules.tabTag != null && name == rules.tabTag) {
            buffer.append(' ')
            return
        }

        // Matn teglari ko'rsatilmagan bo'lsa, hamma matn olinadi (XHTML).
        if (rules.textTags == null) {
            capture = true
            return
        }
        if (rules.textTags.contains(name)) {
            textDepth++
            capture = true
        }
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        if (skipDepth > 0 || !capture) return
        buffer.appendRange(ch, start, start + length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        val name = tagName(localName, qName)

        if (rules.skipTags.contains(name)) {
            if (skipDepth > 0) skipDepth--
            return
        }
        if (rules.headingTags.containsKey(name)) {
            flush()
            tagLevel = 0
            return
        }
        if (rules.paragraphTags.contains(name)) {
            flush()
            styleLevel = 0
            return
        }
        if (rules.textTags != null && rules.textTags.contains(name)) {
            textDepth--
            if (textDepth <= 0) {
                textDepth = 0
                capture = false
            }
        }
    }

    /**
     * Bo'laklarni bitta matnga yig'adi, sarlavhalarni markdown belgisi bilan
     * chiqaradi. Shu tufayli [ChapterSplitter] DOCX va EPUB sarlavhasini ham,
     * oddiy TXT matnini ham bir xil ko'rinishda o'qiydi.
     */
    fun assemble(): String {
        flush()
        val out = StringBuilder()
        for (block in blocks) {
            if (block.text.isEmpty()) continue
            if (out.isNotEmpty()) out.append("\n\n")
            if (block.level > 0) {
                out.append("#".repeat(block.level.coerceIn(1, 6))).append(' ')
            }
            out.append(block.text)
        }
        return out.toString()
    }

    private fun flush() {
        val level = if (tagLevel > 0) tagLevel else styleLevel
        val text = clean(buffer)
        buffer.setLength(0)
        if (text.isNotEmpty()) blocks += TextBlock(level, text)
    }

    private companion object {
        /** Ko'rinmas bo'shliqlar: uzilmaydigan va nol kenglikdagi probel. */
        val SPACE = Regex("[\\s\\u00A0\\u200B]+")

        /**
         * Satr ichidagi bo'shliqlar bitta probelga keltiriladi, bo'sh qatorlar
         * tashlanadi — XML'ni chiroyli qilib yozish uchun qo'yilgan yangi
         * qatorlar matnga tushib qolmasligi kerak.
         */
        fun clean(source: StringBuilder): String = source.toString()
            .split('\n')
            .map { SPACE.replace(it, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}

/** Kerakli tegning bitta atributini yig'adi (konteyner, paket fayli uchun). */
internal class AttributeHandler(private val tag: String, private val attribute: String) :
    DefaultHandler() {

    val values = ArrayList<String>()

    override fun startElement(
        uri: String?,
        localName: String?,
        qName: String?,
        attributes: Attributes?,
    ) {
        if (tagName(localName, qName) != tag) return
        attributes.attr(attribute)?.let { values += it }
    }
}

/**
 * XML matnini o'qish. Tashqi havolalar (XXE) to'siladi: hujjat fayli
 * foydalanuvchidan keladi, ya'ni unga ishonib bo'lmaydi — u qurilmadagi
 * boshqa faylni o'qishga yoki tarmoqqa chiqishga urinishi mumkin.
 */
internal object MarkupBlocks {

    private val HARDENING = listOf(
        "http://apache.org/xml/features/disallow-doctype-decl" to true,
        "http://xml.org/sax/features/external-general-entities" to false,
        "http://xml.org/sax/features/external-parameter-entities" to false,
        "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
    )

    fun parse(xml: ByteArray, rules: MarkupRules): String {
        val handler = BlocksHandler(rules)
        scan(xml, handler)
        return handler.assemble()
    }

    fun attributes(xml: ByteArray, tag: String, attribute: String): List<String> {
        val handler = AttributeHandler(tag, attribute)
        scan(xml, handler)
        return handler.values
    }

    /** Berilgan ishlovchini oqim bo'ylab yuritadi. */
    fun scan(xml: ByteArray, handler: DefaultHandler) {
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        for ((feature, value) in HARDENING) {
            try {
                factory.setFeature(feature, value)
            } catch (_: SAXNotRecognizedException) {
                // Parser bu bayroqni bilmaydi. Pastdagi EntityResolver
                // baribir tashqi havolani ochmaydi.
            } catch (_: SAXNotSupportedException) {
                // Bayroq tanilgan, lekin bu holatda qo'llanmaydi.
            }
        }

        val parser = factory.newSAXParser()
        // Har qanday tashqi havolaga bo'sh javob: DTD yuklanmaydi, tarmoqqa
        // chiqilmaydi. Bu — asosiy himoya, bayroqlar esa ustiga qo'shimcha.
        parser.xmlReader.entityResolver = EntityResolver { _, _ -> InputSource(StringReader("")) }

        parser.parse(xml.inputStream(), handler)
    }
}

/** Teg nomi: prefiks olib tashlanadi va kichik harflarga o'giriladi. */
internal fun tagName(localName: String?, qName: String?): String {
    val name = if (!qName.isNullOrEmpty()) qName else localName.orEmpty()
    return name.substringAfterLast(':').lowercase()
}

/**
 * Atributni prefiksidan qat'i nazar topadi: SAX prefiks bilan ishlaganda
 * (`w:val`) oddiy `getValue("val")` hech narsa topmasdi.
 */
internal fun Attributes?.attr(name: String): String? {
    if (this == null) return null
    for (i in 0 until length) {
        if (getQName(i).substringAfterLast(':').lowercase() == name) return getValue(i)
    }
    return null
}
