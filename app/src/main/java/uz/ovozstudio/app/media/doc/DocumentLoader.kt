package uz.ovozstudio.app.media.doc

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
    ;

    companion object {
        /** Kengaytmadan tanish. Katta-kichik harf va nuqta ahamiyatsiz. */
        fun ofExtension(name: String): DocumentFormat? {
            val tail = name.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extension == tail }
                // Markdown — matn; kitoblar shu ko'rinishda ham tarqaladi.
                ?: if (tail == "md" || tail == "markdown" || tail == "text") TXT else null
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
        val byName = DocumentFormat.ofExtension(name)
        if (byName != null) return byName
        if (header == null) return null
        if (startsWith(header, PDF_MAGIC)) return DocumentFormat.PDF
        if (startsWith(header, ZIP_MAGIC)) return zipKind(header)
        return null
    }

    /**
     * Faylni o'qib boblarga bo'ladi.
     *
     * @param fallbackTitle sarlavhalar topilmasa butun hujjatga beriladigan nom.
     * @param prefaceTitle sarlavhadan oldingi uzun matnning nomi.
     * @throws DocumentFormatException fayl buzuq yoki ichida kerakli qism yo'q.
     * @throws DocumentTooLargeException fayl o'qish chegarasidan katta.
     */
    fun load(
        file: File,
        format: DocumentFormat,
        fallbackTitle: String,
        prefaceTitle: String,
        minPrefaceChars: Int = ChapterSplitter.DEFAULT_MIN_PREFACE_CHARS,
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
                zip.getEntry(EpubTextReader.CONTAINER_ENTRY) != null -> DocumentFormat.EPUB
                else -> null
            }
        }
    } catch (error: Exception) {
        null
    }

    private fun readHeader(file: File): ByteArray? = try {
        FileInputStream(file).use { input -> readUpTo(input, SNIFF_BYTES) }
    } catch (error: Exception) {
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
