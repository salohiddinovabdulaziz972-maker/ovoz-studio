package uz.ovozstudio.app.media.doc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSplitterTest {

    private fun split(text: String): List<Chapter> = ChapterSplitter.split(
        text = text,
        fallbackTitle = "Kitob",
        prefaceTitle = "Muqaddima",
    )

    private fun titles(text: String): List<String> = split(text).map { it.title }

    @Test
    fun `bosh matn bosh royxat qaytaradi`() {
        assertTrue(split("").isEmpty())
        assertTrue(split("   \n\n\t\n").isEmpty())
    }

    @Test
    fun `sarlavhasiz matn bitta bob boladi`() {
        val chapters = split("Bu shunchaki matn.\nIkkinchi qatori ham bor.")

        assertEquals(1, chapters.size)
        assertEquals("Kitob", chapters[0].title)
        assertTrue(chapters[0].text.startsWith("Bu shunchaki matn."))
    }

    @Test
    fun `son va kalit soz bilan bob ochiladi`() {
        val text = "1-BOB\nSalim aka haqida\n\n2-BOB\nYangi kun\n"

        assertEquals(listOf("1-BOB", "2-BOB"), titles(text))
    }

    @Test
    fun `qism va bolim ham sarlavha hisoblanadi`() {
        assertEquals(listOf("1-qism"), titles("1-qism\nBirinchi qism matni."))
        assertEquals(listOf("2-bo'lim"), titles("2-bo'lim\nIkkinchi bo'lim matni."))
    }

    @Test
    fun `rim raqami bilan bob ochiladi`() {
        assertEquals(listOf("II bob"), titles("II bob\nSalim aka haqida."))
    }

    @Test
    fun `kirill va ingliz sarlavhalari topiladi`() {
        assertEquals(listOf("ГЛАВА 1"), titles("ГЛАВА 1\nСалом, дунё."))
        assertEquals(listOf("Chapter 3"), titles("Chapter 3\nThe story begins."))
    }

    @Test
    fun `kalit soz matn ichida sarlavha hisoblanmaydi`() {
        // Qator raqam bilan boshlanadi va «bob» so'zi bor, lekin «bobda» —
        // boshqa so'z. Aynan shu holat `\b` emas, harf tekshiruvi bilan
        // ushlanadi.
        val chapters = split("1 bobda aytilganidek hamma narsa bor edi.")

        assertEquals(1, chapters.size)
        assertEquals("Kitob", chapters[0].title)
    }

    @Test
    fun `kalit sozsiz son sarlavha hisoblanmaydi`() {
        assertEquals(1, titles("3 ta kitob sotib olindi.").size)
    }

    @Test
    fun `yakka katta harfli qator sarlavha boladi`() {
        val text = "BIRINCHI BOB\n\nSalim aka haqida\n\nIKKINCHI BOB\n\nYangi kun"

        assertEquals(listOf("BIRINCHI BOB", "IKKINCHI BOB"), titles(text))
    }

    @Test
    fun `ozbekcha apostrof bilan katta harfli sarlavha topiladi`() {
        // ʻ — o'zbek lotin alifbosining harfi, lekin katta harfi yo'q.
        // U harf deb sanalsa, «TOʻRTINCHI QISM» hech qachon o'tmasdi.
        assertEquals(listOf("TOʻRTINCHI QISM"), titles("TOʻRTINCHI QISM\n\nMatn shu yerda."))
    }

    @Test
    fun `matn ichidagi katta harfli qator sarlavha hisoblanmaydi`() {
        // Ikki tomoni bo'sh qator bilan o'ralmagan — demak bu matn.
        val chapters = split("oldingi qator\nBIRINCHI BOB\nkeyingi qator")

        assertEquals(1, chapters.size)
        assertEquals("Kitob", chapters[0].title)
    }

    @Test
    fun `markdown sarlavhasi bob ochadi`() {
        val text = "# Birinchi\nMatn bir.\n\n# Ikkinchi\nMatn ikki."

        assertEquals(listOf("Birinchi", "Ikkinchi"), titles(text))
        assertEquals("Matn bir.", split(text)[0].text)
    }

    @Test
    fun `markdown sarlavhasiga uzunlik chegarasi qollanmaydi`() {
        // Markdown — ochiq belgi, taxmin emas: uzun bo'lsa ham ishonamiz.
        val long = "x".repeat(70)

        assertEquals(listOf(long), titles("# $long\nMatn."))
    }

    @Test
    fun `uzun qator sarlavha hisoblanmaydi`() {
        val long = "1-BOB " + "a".repeat(60)

        assertEquals(listOf("Kitob"), titles("$long\nMatn."))
    }

    @Test
    fun `sarlavha ostida matn bolmasa keyingi sarlavhaga qoshiladi`() {
        // «BIRINCHI QISM» — bo'lim nomi, bobning o'zi emas. U keyingi
        // sarlavhaning ustiga qo'shiladi, alohida bo'sh bob bo'lib qolmaydi.
        val chapters = split("BIRINCHI QISM\n\n1-BOB\nSalim aka haqida")

        assertEquals(listOf("BIRINCHI QISM — 1-BOB"), titles("BIRINCHI QISM\n\n1-BOB\nSalim aka haqida"))
        assertEquals("Salim aka haqida", chapters[0].text)
    }

    @Test
    fun `oxirgi sarlavha ostida matn bolmasa tashlab yuboriladi`() {
        val text = "1-BOB\nA matni\n\n2-BOB\nB matni\n\n3-BOB\n"

        assertEquals(listOf("1-BOB", "2-BOB"), titles(text))
    }

    @Test
    fun `sarlavhadan oldingi uzun matn muqaddima boladi`() {
        val body = "m".repeat(500)
        val chapters = split("$body\n\n1-BOB\nAsosiy matn.")

        assertEquals(listOf("Muqaddima", "1-BOB"), chapters.map { it.title })
        assertEquals(body, chapters[0].text)
    }

    @Test
    fun `sarlavhadan oldingi qisqa matn tashlab yuboriladi`() {
        // Muqova va nashr ma'lumotlari ovozga aylantirilmaydi.
        val chapters = split("Qisqa muqova\n\n1-BOB\nAsosiy matn.")

        assertEquals(listOf("1-BOB"), chapters.map { it.title })
    }

    @Test
    fun `hamma sarlavha bosh chiqsa butun matn bitta bob boladi`() {
        // Sarlavhalar topildi, lekin ostida matn yo'q: demak ular sarlavha
        // emas edi. O'rtasidan kesilgan kitobdan ko'ra bo'linmagani yaxshi.
        val chapters = split("1-BOB\n\n2-BOB\n")

        assertEquals(1, chapters.size)
        assertEquals("Kitob", chapters[0].title)
        assertEquals("1-BOB\n\n2-BOB", chapters[0].text)
    }

    @Test
    fun `bob matni manba ichida qoladi`() {
        // Offsets — manbaga ishora. Ular noto'g'ri bo'lsa, ekranda
        // «shu yerga o'tish» boshqa joyni ko'rsatardi.
        val text = "Muqova\n\n1-BOB\nBirinchi matn.\n\n2-BOB\nIkkinchi matn.\n"
        val chapters = split(text)

        assertFalse(chapters.isEmpty())
        for (chapter in chapters) {
            val region = text.substring(chapter.startOffset, chapter.endOffset)
            assertTrue(
                "«${chapter.title}» matni manba oralig'ida yo'q",
                region.contains(chapter.text),
            )
        }
    }

    @Test
    fun `bob matni sarlavhani oz ichiga olmaydi`() {
        val chapters = split("1-BOB\nSalim aka haqida")

        assertFalse(chapters[0].text.contains("1-BOB"))
        assertEquals("Salim aka haqida", chapters[0].text)
    }

    @Test
    fun `sarlavha darajasi matnni ozgartirmaydi`() {
        val chapters = split("## Ikkinchi daraja\nMatn shu yerda.")

        assertEquals("Ikkinchi daraja", chapters[0].title)
        assertEquals("Matn shu yerda.", chapters[0].text)
    }
}
