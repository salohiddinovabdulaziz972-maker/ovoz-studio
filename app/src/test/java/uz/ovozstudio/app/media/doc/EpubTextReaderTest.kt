package uz.ovozstudio.app.media.doc

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubTextReaderTest {

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("ovozstudio-test", ".epub")
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

    private val container = """<?xml version="1.0"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>"""

    private fun opf(vararg spineIds: String): String {
        val items = spineIds.joinToString("\n") {
            """<item id="$it" href="$it.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val refs = spineIds.joinToString("\n") { """<itemref idref="$it"/>""" }
        return """<?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <manifest>$items</manifest>
              <spine>$refs</spine>
            </package>"""
    }

    private fun xhtml(title: String, body: String): String = """<?xml version="1.0"?>
        <html xmlns="http://www.w3.org/1999/xhtml">
          <head><title>$title</title><style>p { color: red }</style></head>
          <body>$body</body>
        </html>"""

    @Test
    fun `spine tartibida oqiladi va sarlavha h1 dan olinadi`() {
        val file = zipOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf("bob1", "bob2"),
            "OEBPS/bob1.xhtml" to xhtml("Bir", "<h1>1-BOB</h1><p>Salim aka haqida</p>"),
            "OEBPS/bob2.xhtml" to xhtml("Ikki", "<h1>2-BOB</h1><p>Yangi kun</p>"),
        )

        val chapters = ChapterSplitter.split(
            text = EpubTextReader.read(file),
            fallbackTitle = "Kitob",
            prefaceTitle = "Muqaddima",
        )

        assertEquals(listOf("1-BOB", "2-BOB"), chapters.map { it.title })
        assertEquals(listOf("Salim aka haqida", "Yangi kun"), chapters.map { it.text })
    }

    @Test
    fun `spine tartibi manifest tartibidan ustun`() {
        // Manifest alifbo tartibida, spine esa kitob tartibida bo'ladi —
        // noto'g'ri tanlansa, boblar aralashib ketardi.
        val file = zipOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="a" href="a.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="b.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="b"/><itemref idref="a"/></spine>
                </package>""",
            "OEBPS/a.xhtml" to xhtml("A", "<p>Birinchi yozilgan fayl</p>"),
            "OEBPS/b.xhtml" to xhtml("B", "<p>Ikkinchi yozilgan fayl</p>"),
        )

        val text = EpubTextReader.read(file)

        assertTrue(text.indexOf("Ikkinchi yozilgan fayl") < text.indexOf("Birinchi yozilgan fayl"))
    }

    @Test
    fun `script va style matnga tushmaydi`() {
        val file = zipOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf("bob1"),
            "OEBPS/bob1.xhtml" to xhtml(
                "Bir",
                """<h1>Bob</h1><script>var x = "skript matni";</script><p>Asosiy matn</p>""",
            ),
        )

        val text = EpubTextReader.read(file)

        assertTrue(text.contains("Asosiy matn"))
        assertFalse(text.contains("skript matni"))
        assertFalse(text.contains("color: red"))
    }

    @Test
    fun `html teglari va atributlari matnga tushmaydi`() {
        val file = zipOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf("bob1"),
            "OEBPS/bob1.xhtml" to xhtml(
                "Bir",
                """<p class="oddiy"><a href="https://example.com">havola matni</a> davomi</p>""",
            ),
        )

        val text = EpubTextReader.read(file)

        assertTrue(text.contains("havola matni davomi"))
        assertFalse(text.contains("class"))
        assertFalse(text.contains("https://example.com"))
    }

    @Test
    fun `paket fayli boshqa papkada bolsa yol togri topiladi`() {
        val file = zipOf(
            "META-INF/container.xml" to
                """<container><rootfiles>
                   <rootfile full-path="kitob/matn/paket.opf"/>
                   </rootfiles></container>""",
            "kitob/matn/paket.opf" to """<?xml version="1.0"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
                  <manifest>
                    <item id="a" href="../boblar/bob1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="b" href="bob%202.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="a"/><itemref idref="b"/></spine>
                </package>""",
            "kitob/boblar/bob1.xhtml" to xhtml("Bir", "<h1>Birinchi</h1>"),
            "kitob/matn/bob 2.xhtml" to xhtml("Ikki", "<h1>Ikkinchi</h1>"),
        )

        val text = EpubTextReader.read(file)

        assertTrue(text.contains("Birinchi"))
        assertTrue(text.contains("Ikkinchi"))
    }

    @Test
    fun `matn topilmasa xato beradi`() {
        val file = zipOf(
            "META-INF/container.xml" to container,
            "OEBPS/content.opf" to opf("bob1"),
            "OEBPS/bob1.xhtml" to xhtml("Bir", ""),
        )

        assertThrows(DocumentFormatException::class.java) { EpubTextReader.read(file) }
    }

    @Test
    fun `konteyner yoq bolsa xato beradi`() {
        val file = zipOf("OEBPS/content.opf" to opf("bob1"))

        assertThrows(DocumentFormatException::class.java) { EpubTextReader.read(file) }
    }

    @Test
    fun `paket fayli yolida togri hal qilinadi`() {
        assertEquals("kitob/boblar/1.xhtml", resolvePath("kitob/matn", "../boblar/1.xhtml"))
        assertEquals("kitob/matn/1.xhtml", resolvePath("kitob/matn", "./1.xhtml"))
        assertEquals("kitob/matn/1.xhtml", resolvePath("kitob/matn", "1.xhtml"))
        assertEquals("1.xhtml", resolvePath("", "1.xhtml"))
        // Arxiv ildizidan yuqoriga chiqib bo'lmaydi.
        assertEquals("1.xhtml", resolvePath("", "../../1.xhtml"))
        // Bo'lak belgisi yo'lni o'zgartirmaydi.
        assertEquals("kitob/1.xhtml", resolvePath("kitob", "1.xhtml#bob-2"))
    }

    @Test
    fun `foiz bilan yozilgan belgilar ochiladi`() {
        assertEquals("bob 2.xhtml", percentDecode("bob%202.xhtml"))
        assertEquals("kitob/Bob 2.xhtml", percentDecode("kitob/Bob%202.xhtml"))
        // O'n oltilik juftlik bo'lmasa — tegilmaydi.
        assertEquals("%ZZ", percentDecode("%ZZ"))
        assertEquals("100%", percentDecode("100%"))
        // Plyus — yo'lda oddiy belgi, probelga aylanmasligi kerak.
        assertEquals("1+2.xhtml", percentDecode("1+2.xhtml"))
        assertEquals("1+2.xhtml", percentDecode("1%2B2.xhtml"))
        // Ko'p baytli belgi (o'zbekcha so'z) to'liq yig'iladi.
        assertEquals("soʻz", percentDecode("so%CA%BBz"))
    }
}
