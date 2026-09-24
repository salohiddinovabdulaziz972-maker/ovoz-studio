package uz.ovozstudio.app.media.doc

import uz.ovozstudio.app.log.ErrorLog
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Hujjat formati.
 *
 * [extension] — ilova o'zi yozadigan kengaytma; qolgan qiymatlar joinatni
 * fayl nomidan tanish uchun ishlatiladi.
 */
enum class DocumentFormat(val extension: String) {
    TXT("txt"),
    DOCX("docx"),
    EPUB("epub"),
    PDF("pdf"),
    RTF("rtf"),

    /** OpenDocument: matn (odt), jadval (ods) va taqdimot (odp) — uchalasi bir xil o'qiladi. */
    ODT("odt"),
    FB2("fb2"),
    HTML("html"),
    PPTX("pptx"),
    ;

    companion object {
        /** Oddiy matn sifatida o'qiladigan kengaytmalar. */
        private val TEXT_LIKE = setOf(
            "md", "markdown", "text", "log", "csv", "tsv", "json", "srt", "ini", "cfg", "yaml", "yml", "tex",
        )

        /** Kengaytmadan tanish. Katta-kichik harf va nuqta ahamiyatsiz. */
        fun ofExtension(name: String): DocumentFormat? {
            val tail = name.substringAfterLast('.', "").lowercase()
            val exact = entries.firstOrNull { it.extension == tail }
            if (exact != null) return exact
            return when {
                // Markdown va shunga o'xshash — matn; kitoblar shu ko'rinishda ham tarqaladi.
                tail in TEXT_LIKE -> TXT
                tail == "htm" || tail == "xhtml" -> HTML
                tail == "ods" || tail == "odp" -> ODT
                else -> null
            }
        }
    }
}

/**
 * O'qilgan hujjat: matn va undan ajratilgan boblar.
 *
 * [encoding] faqat matn fayllari uchun ma'noga ega — DOCX, EPUB va PDF
 * ichida jadval o'z sarlavhasida yozilgan, taxmin qilinmaydi.
 */
data class LoadedDocument(
    val format: DocumentFormat,
    /** Boblarga bo'linmagan, lekin tozalangan matn — qidiruv va hisobot uchun. */
    val text: String,
    val encoding: TextEncoding?,
    val chapters: List<Chapter>,
)

/**
 * Faylni formatiga qarab o'qib, boblarga bo'ladi.
 *
 * Nega bitta kirish nuqtasi: ekran fayl tanlagichdan kelgan nom bilangina
 * ishlaydi va formatni o'zi aniqlamasligi kerak — aks holda har bir yangi
 * format butun UI zanjirini o'zgartirishni talab qilardi.
 *
 * Aniqlash tartibi: avval **kengaytma**, keyin **imzo** (fayl boshidagi
 * baytlar). Imzo kerak, chunki fayl nomi ko'pincha yolg'on bo'ladi:
 * Telegram'dan kelgan DOCX `file.bin` bo'lishi mumkin.
 */
object DocumentLoader {

    /** Imzoni o'qish uchun yetarli baytlar soni. */
    private const val SNIFF_BYTES = 512

    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val RTF_MAGIC = "{\\rtf".toByteArray(Charsets.US_ASCII)

    /** Formatni aniqlaydi: kengaytma, keyin fayl imzosi, keyin matn. */
    fun formatOf(file: File): DocumentFormat? {
        val header = readHeader(file) ?: return null
        return formatOf(file.name, header)
    }

    /**
     * @param header fayl boshidagi baytlar (imzo uchun). `null` bo'lsa
     *   faqat kengaytmaga qarab qaror qilinadi.
     */
    fun formatOf(name: String, header: ByteArray?): DocumentFormat? {
        // Ikki imzo shunchalik aniqki, ular kengaytmadan ustun turadi: Word
        // hujjati ko'pincha RTF bo'lib, `.doc` nomi bilan saqlanadi, PDF esa
        // `.txt` nomi bilan yuboriladi. Bunday faylni kengaytma bo'yicha
        // o'qish savatcha matn beradi.
        if (header != null) {
            if (startsWith(header, PDF_MAGIC)) return DocumentFormat.PDF
            if (startsWith(header, RTF_MAGIC)) return DocumentFormat.RTF
        }
        val byName = DocumentFormat.ofExtension(name)
        if (byName != null) return byName
        if (header == null) return null
        if (startsWith(header, ZIP_MAGIC)) {
            val kind = zipKind(header)
            // EPUB ham, ODT ham bir xil ZIP: imzo kesimida ajralmasa,
            // arxiv ro'yxatidan aniqlanadi. Aks holda `.bin` nomi bilan
            // kelgan kitob «noma'lum format» bo'lib qaytardi.
            if (kind != null) return kind
            return zipKindOf(File(name))
        }
        // Kengaytmasi noma'lum bo'lib, ichi ZIP ham, PDF/RTF ham emas.
        // Kitoblar ko'pincha shunday tarqaladi (`.bin`, kengaytmasiz), va
        // imzo bo'yicha matnga o'xshasa — shuni tan olish kerak.
        return if (looksLikeTextBytes(header)) DocumentFormat.TXT else null
    }

    /**
     * Fayl tanlagichdan kelgan nom bo'yicha aniqlaydi: avval kengaytma,
     * keyin **ichidagi baytlar**.
     *
     * Bu [formatOf] ning qulay ko'rinishi — kengaytmasi yolg'on bo'lgan
     * fayl (Telegram'dan `.bin` bo'lib kelgan DOCX, `.txt` nomli PDF)
     * shu yerda to'g'ri o'qiladi.
     */
    fun formatOf(file: File, fallbackName: String): DocumentFormat? {
        val header = readHeader(file) ?: return null
        return formatOf(fallbackName, header) ?: run {
            val byName = DocumentFormat.ofExtension(file.name)
            if (byName != null) byName
            else if (looksLikeTextBytes(header)) DocumentFormat.TXT else null
        }
    }

    /**
     * Faylni o'qib boblarga bo'ladi.
     *
     * @param fallbackTitle sarlavhalar topilmasa butun hujjatga beriladigan nom.
     * @param prefaceTitle sarlavhadan oldingi uzun matnning nomi.
     * @param slideTitle taqdimot slaydlariga beriladigan nom («Slayd»).
     * @throws DocumentFormatException fayl buzuq yoki ichida kerakli qism yo'q.
     * @throws DocumentTooLargeException fayl o'qish chegarasidan katta.
     */
    fun load(
        file: File,
        format: DocumentFormat,
        fallbackTitle: String,
        prefaceTitle: String,
        minPrefaceChars: Int = ChapterSplitter.DEFAULT_MIN_PREFACE_CHARS,
        slideTitle: String = "Slide",
    ): LoadedDocument {
        val encoding: TextEncoding?
        val text = when (format) {
            DocumentFormat.TXT -> {
                val decoded = FileInputStream(file).use { PlainTextDecoder.decode(it) }
                encoding = decoded.encoding
                decoded.text
            }

            DocumentFormat.DOCX -> {
                encoding = null
                DocxTextReader.read(file)
            }

            DocumentFormat.EPUB -> {
                encoding = null
                EpubTextReader.read(file)
            }

            DocumentFormat.PDF -> {
                encoding = null
                PdfTextReader.read(file)
            }

            DocumentFormat.RTF -> {
                encoding = null
                RtfTextReader.read(file)
            }

            DocumentFormat.ODT -> {
                encoding = null
                OdtTextReader.read(file)
            }

            DocumentFormat.FB2 -> {
                encoding = null
                Fb2TextReader.read(file)
            }

            DocumentFormat.HTML -> {
                encoding = null
                HtmlTextReader.read(file)
            }

            DocumentFormat.PPTX -> {
                encoding = null
                PptxTextReader.read(file, slideTitle)
            }
        }
        val chapters = ChapterSplitter.split(
            text = text,
            fallbackTitle = fallbackTitle,
            prefaceTitle = prefaceTitle,
            minPrefaceChars = minPrefaceChars,
        )
        return LoadedDocument(format, text, encoding, chapters)
    }

    /**
     * Arxiv ichidagi belgilangan fayl bo'yicha DOCX va EPUB ni ajratadi.
     *
     * Kengaytma yolg'on bo'lgan holat uchun: ikkalasi ham ZIP, farq esa
     * ichidagi fayllarda. Imzo boshidagi baytlardan o'qiladi — arxivning
     * butun ro'yxatini ochish faqat shu ish uchun ortiqcha.
     */
    private fun zipKind(header: ByteArray): DocumentFormat? {
        val marker = String(header, Charsets.ISO_8859_1)
        return when {
            marker.contains("word/") || marker.contains("word\\") -> DocumentFormat.DOCX
            marker.contains("ppt/") || marker.contains("ppt\\") -> DocumentFormat.PPTX
            // ODF va EPUB ikkalasida ham `mimetype` birinchi turadi; ODF'ning
            // `META-INF/` i esa arxiv boshida ham uchrashi mumkin, shuning uchun
            // ODF tekshiruvi EPUB'dan oldin.
            marker.contains("opendocument") -> DocumentFormat.ODT
            marker.contains("META-INF/") -> DocumentFormat.EPUB
            else -> null
        }
    }

    /**
     * ZIP bo'lsa ichidagi fayllar ro'yxatidan aniqlaydi.
     *
     * Boshidagi baytlar yetarli bo'lmaganda ishlatiladi (fayl ro'yxati
     * arxiv oxirida ham turishi mumkin). Xato yiqitmaydi: aniqlanmasa —
     * `null`, chaqiruvchi buni tushunarli xabarga aylantiradi.
     */
    fun zipKindOf(file: File): DocumentFormat? = try {
        ZipFile(file).use { zip ->
            when {
                zip.getEntry(DocxTextReader.DOCUMENT_ENTRY) != null -> DocumentFormat.DOCX
                zip.getEntry(PptxTextReader.PRESENTATION_ENTRY) != null -> DocumentFormat.PPTX
                zip.getEntry(EpubTextReader.CONTAINER_ENTRY) != null -> DocumentFormat.EPUB
                // ODF: ildizda `content.xml` bilan birga `mimetype` turadi.
                zip.getEntry(OdtTextReader.CONTENT_ENTRY) != null && zip.getEntry("mimetype") != null ->
                    DocumentFormat.ODT
                else -> null
            }
        }
    } catch (error: Exception) {
        // Arxiv ochilmadi — tashqi qator xulosani keyin o'zi chiqaradi,
        // lekin sabab shu yerda yo'qolib qolmasin.
        ErrorLog.error("hujjat.sniff", "Arxiv turini aniqlab bo'lmadi: ${file.name}", error)
        null
    }

    /**
     * Fayl oddiy matnga o'xshaydimi.
     *
     * Kengaytmasi noma'lum fayl (`.log`, `.srt`, kengaytmasiz) ko'pincha shunchaki
     * matn. Boshidagi baytlarga qaraladi: matnda boshqaruv belgilari (yozuv
     * mashinkasidagi tab, qator ko'chirish bundan mustasno) deyarli bo'lmaydi,
     * ikkilik faylda esa ular ko'p. UTF-16 fayl nol baytlarga boy, shuning
     * uchun BOM bilan alohida tekshiriladi.
     */
    fun looksLikeText(file: File): Boolean {
        val header = readHeader(file) ?: return false
        return looksLikeTextBytes(header)
    }

    /** [looksLikeText] ning o'zagi — sarlavha allaqachon o'qilgan holat uchun. */
    private fun looksLikeTextBytes(header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        if (header.size >= 2) {
            val first = header[0].toInt() and 0xFF
            val second = header[1].toInt() and 0xFF
            if ((first == 0xFF && second == 0xFE) || (first == 0xFE && second == 0xFF)) return true
        }
        var suspicious = 0
        for (byte in header) {
            val value = byte.toInt() and 0xFF
            val control = value < 32 && value != 9 && value != 10 && value != 13 && value != 12
            if (control) suspicious++
        }
        // 2% dan ko'p boshqaruv belgisi — bu matn emas.
        return suspicious * 50 <= header.size
    }

    private fun readHeader(file: File): ByteArray? = try {
        FileInputStream(file).use { input -> readUpTo(input, SNIFF_BYTES) }
    } catch (error: Exception) {
        ErrorLog.error("hujjat.header", "Fayl boshini o'qib bo'lmadi: ${file.name}", error)
        null
    }

    private fun readUpTo(input: InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val got = input.read(buffer, read, limit - read)
            if (got <= 0) break
            read += got
        }
        return if (read == limit) buffer else buffer.copyOf(read)
    }

    private fun startsWith(bytes: ByteArray, prefix: ByteArray): Boolean {
        if (bytes.size < prefix.size) return false
        for (i in prefix.indices) if (bytes[i] != prefix[i]) return false
        return true
    }
}
