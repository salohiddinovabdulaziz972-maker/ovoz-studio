package uz.ovozstudio.app.media.doc

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentLoaderTest {

    private fun tempFile(name: String, content: String): File {
        val dir = File.createTempFile("ovozstudio-hujjat", "")
        dir.delete()
        dir.mkdirs()
        dir.deleteOnExit()
        val file = File(dir, name)
        file.writeText(content)
        file.deleteOnExit()
        return file
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `kengaytmadan format aniqlanadi`() {
        assertEquals(DocumentFormat.TXT, DocumentFormat.ofExtension("kitob.txt"))
        assertEquals(DocumentFormat.DOCX, DocumentFormat.ofExtension("KITOB.DOCX"))
        assertEquals(DocumentFormat.EPUB, DocumentFormat.ofExtension("kitob.epub"))
        assertEquals(DocumentFormat.PDF, DocumentFormat.ofExtension("kitob.pdf"))
        // Markdown — matn fayli: kitoblar shu ko'rinishda ham tarqaladi.
        assertEquals(DocumentFormat.TXT, DocumentFormat.ofExtension("kitob.md"))
        assertNull(DocumentFormat.ofExtension("ovoz.mp3"))
        assertNull(DocumentFormat.ofExtension("kengaytmasiz"))
    }

    @Test
    fun `fayl nomi yolg'on bolsa imzo oqiladi`() {
        // Telegram'dan kelgan fayllar ko'pincha `file.bin` bo'ladi.
        val pdf = bytes(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31)
        assertEquals(DocumentFormat.PDF, DocumentLoader.formatOf("file.bin", pdf))

        val docx = bytes(0x50, 0x4B, 0x03, 0x04) + "word/document.xml".toByteArray()
        assertEquals(DocumentFormat.DOCX, DocumentLoader.formatOf("file.bin", docx))

        val epub = bytes(0x50, 0x4B, 0x03, 0x04) + "META-INF/container.xml".toByteArray()
        assertEquals(DocumentFormat.EPUB, DocumentLoader.formatOf("file.bin", epub))

        // Imzo ham, kengaytma ham yo'q: aniqlanmaydi — bu xato emas,
        // chaqiruvchi tushunarli xabar beradi.
        assertNull(DocumentLoader.formatOf("file.bin", "Salom".toByteArray()))
    }

    @Test
    fun `matn fayli boblarga bolinadi`() {
        val file = tempFile(
            "kitob.txt",
            """
            1-BOB

            Salim aka qishloqdan keldi.

            2-BOB

            Ertalab havo ochiq edi.
            """.trimIndent(),
        )

        val document = DocumentLoader.load(file, DocumentFormat.TXT, "Kitob", "Muqaddima")

        assertEquals(DocumentFormat.TXT, document.format)
        assertEquals(TextEncoding.UTF_8, document.encoding)
        assertEquals(2, document.chapters.size)
        assertEquals("1-BOB", document.chapters[0].title)
        assertTrue(document.chapters[1].text.contains("Ertalab havo ochiq edi."))
    }

    @Test
    fun `sarlavhasiz matn bitta bob boladi`() {
        val file = tempFile("kitob.txt", "Shunchaki matn. Unda sarlavha yo'q.")

        val document = DocumentLoader.load(file, DocumentFormat.TXT, "Kitob", "Muqaddima")

        assertEquals(1, document.chapters.size)
        assertEquals("Kitob", document.chapters[0].title)
    }

    @Test
    fun `kirill matni togri jadvalda oqiladi`() {
        // UTF-8 bo'lmagan fayl: jadval aniqlanishi va matn «savatcha»
        // bo'lib qolmasligi kerak.
        val dir = File.createTempFile("ovozstudio-kirill", "")
        dir.delete()
        dir.mkdirs()
        val file = File(dir, "kitob.txt")
        file.writeBytes("БИРИНЧИ БОБ\nСалим ака келди.".toByteArray(java.nio.charset.Charset.forName("windows-1251")))
        file.deleteOnExit()

        val document = DocumentLoader.load(file, DocumentFormat.TXT, "Китоб", "Муқаддима")

        assertEquals(TextEncoding.WINDOWS_1251, document.encoding)
        assertTrue(document.text, document.text.contains("Салим ака келди."))
    }

    @Test
    fun `pdf hujjat ham boblarga bolinadi`() {
        val file = pdfFixture("kitob-kirill.pdf")

        val document = DocumentLoader.load(file, DocumentFormat.PDF, "Kitob", "Muqaddima")

        assertNull("PDF'da jadval taxmin qilinmaydi", document.encoding)
        assertNotNull(document.chapters.firstOrNull())
        assertTrue(document.text, document.text.contains("Салим ака"))
        // Fayl nomidan kengaytma orqali ham aniqlanadi.
        assertEquals(DocumentFormat.PDF, DocumentLoader.formatOf(file))
    }

    @Test
    fun `buzuq docx tushunarli xato beradi`() {
        val file = tempFile("buzuq.docx", "bu docx emas")
        assertThrows(DocumentFormatException::class.java) {
            DocumentLoader.load(file, DocumentFormat.DOCX, "Kitob", "Muqaddima")
        }
    }

    @Test
    fun `docx arxivi ichidan aniqlanadi`() {
        // Kengaytma noto'g'ri bo'lsa ham ichidagi fayl to'g'ri aytadi.
        val dir = File.createTempFile("ovozstudio-docx", "")
        dir.delete()
        dir.mkdirs()
        val file = File(dir, "kitob.zip")
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("word/document.xml"))
            zip.write("<w:document/>".toByteArray())
            zip.closeEntry()
        }
        file.deleteOnExit()

        assertEquals(DocumentFormat.DOCX, DocumentLoader.zipKindOf(file))
    }
}
