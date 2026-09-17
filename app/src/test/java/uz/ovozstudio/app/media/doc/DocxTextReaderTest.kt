package uz.ovozstudio.app.media.doc

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocxTextReaderTest {

    /** Arxivni shu yerda yig'amiz: DOCX — oddiy ZIP, boshqa hech narsa emas. */
    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("ovozstudio-test", ".docx")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }

    private fun document(vararg body: String): File = zipOf(
        "word/document.xml" to
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
               <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                 <w:body>
                   ${body.joinToString("\n")}
                 </w:body>
               </w:document>""",
        "[Content_Types].xml" to """<?xml version="1.0"?><Types/>""",
    )

    private fun heading(level: Int, text: String): String =
        """<w:p><w:pPr><w:pStyle w:val="Heading$level"/></w:pPr><w:r><w:t>$text</w:t></w:r></w:p>"""

    private fun paragraph(text: String): String = """<w:p><w:r><w:t>$text</w:t></w:r></w:p>"""

    @Test
    fun `paragraf va sarlavhalar ajratiladi`() {
        val file = document(
            heading(1, "1-BOB"),
            paragraph("Salim aka haqida"),
            heading(2, "Kichik sarlavha"),
            paragraph("Yana matn"),
        )

        val text = DocxTextReader.read(file)

        assertEquals(
            "# 1-BOB\n\nSalim aka haqida\n\n## Kichik sarlavha\n\nYana matn",
            text,
        )
    }

    @Test
    fun `bitta paragraf ichidagi bolaklar birlashadi`() {
        val file = document(
            """<w:p><w:r><w:t>Salim</w:t></w:r><w:r><w:t xml:space="preserve"> aka</w:t></w:r><w:r><w:t xml:space="preserve"> haqida</w:t></w:r></w:p>""",
        )

        assertEquals("Salim aka haqida", DocxTextReader.read(file))
    }

    @Test
    fun `ochirilgan matn va maydon kodlari oqilmaydi`() {
        // `delText` — kuzatilgan tuzatishda o'chirilgan, hujjatda
        // ko'rinmaydigan matn. Uni ovoz bilan o'qib berish xato bo'lardi.
        val file = document(
            """<w:p><w:r><w:t>Ko'rinadigan matn</w:t></w:r>""" +
                """<w:del w:id="1"><w:r><w:delText>o'chirilgan matn</w:delText></w:r></w:del>""" +
                """<w:r><w:instrText> PAGE </w:instrText></w:r></w:p>""",
        )

        val text = DocxTextReader.read(file)

        assertTrue(text.contains("Ko'rinadigan matn"))
        assertFalse(text.contains("o'chirilgan matn"))
        assertFalse(text.contains("PAGE"))
    }

    @Test
    fun `tab probelga br satrga aylanadi`() {
        val file = document(
            """<w:p><w:r><w:t>Salim</w:t><w:tab/><w:t>aka</w:t><w:br/><w:t>ikkinchi qator</w:t></w:r></w:p>""",
        )

        assertEquals("Salim aka\nikkinchi qator", DocxTextReader.read(file))
    }

    @Test
    fun `ruscha va libreoffice uslub nomlari ham taniladi`() {
        val rus = document(
            """<w:p><w:pPr><w:pStyle w:val="Заголовок1"/></w:pPr><w:r><w:t>ГЛАВА</w:t></w:r></w:p>""",
        )
        val libre = document(
            """<w:p><w:pPr><w:pStyle w:val="Heading_20_2"/></w:pPr><w:r><w:t>Sarlavha</w:t></w:r></w:p>""",
        )

        assertEquals("# ГЛАВА", DocxTextReader.read(rus))
        assertEquals("## Sarlavha", DocxTextReader.read(libre))
    }

    @Test
    fun `oddiy uslub sarlavha hisoblanmaydi`() {
        val file = document(
            """<w:p><w:pPr><w:pStyle w:val="Normal"/></w:pPr><w:r><w:t>Oddiy matn</w:t></w:r></w:p>""",
        )

        assertEquals("Oddiy matn", DocxTextReader.read(file))
    }

    @Test
    fun `docx sarlavhalari boblarga aylanadi`() {
        // Butun zanjir: DOCX → matn → boblar. Sarlavha uslubi yo'qolsa,
        // kitob bitta katta bob bo'lib qolardi.
        val file = document(
            heading(1, "1-BOB"),
            paragraph("Salim aka haqida"),
            heading(1, "2-BOB"),
            paragraph("Yangi kun"),
        )

        val chapters = ChapterSplitter.split(
            text = DocxTextReader.read(file),
            fallbackTitle = "Kitob",
            prefaceTitle = "Muqaddima",
        )

        assertEquals(listOf("1-BOB", "2-BOB"), chapters.map { it.title })
        assertEquals(listOf("Salim aka haqida", "Yangi kun"), chapters.map { it.text })
    }

    @Test
    fun `document xml yoq bolsa xato beradi`() {
        val file = zipOf("word/styles.xml" to "<styles/>")

        assertThrows(DocumentFormatException::class.java) { DocxTextReader.read(file) }
    }

    @Test
    fun `zip bolmagan fayl xato beradi`() {
        val file = File.createTempFile("ovozstudio-buzuq", ".docx")
        file.deleteOnExit()
        file.writeText("bu DOCX emas, oddiy matn")

        assertThrows(DocumentFormatException::class.java) { DocxTextReader.read(file) }
    }
}
