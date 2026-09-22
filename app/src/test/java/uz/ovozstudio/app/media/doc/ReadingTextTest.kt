package uz.ovozstudio.app.media.doc

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Matnni o'qish abzatslariga bo'lish.
 *
 * Har bir test aniq bitta qoidani ushlaydi: bo'sh qator, gap tugashi,
 * qator uzilishi, defisli so'z, tire bilan boshlanadigan dialog.
 */
class ReadingTextTest {

    @Test
    fun `bosh qator abzats chegarasi`() {
        assertEquals(listOf("Birinchi.", "Ikkinchi."), ReadingText.paragraphs("Birinchi.\n\nIkkinchi."))
    }

    @Test
    fun `gap tugagan qatordan keyingi bosh harfli qator yangi abzats`() {
        assertEquals(
            listOf("Birinchi jumla.", "Ikkinchi jumla."),
            ReadingText.paragraphs("Birinchi jumla.\nIkkinchi jumla."),
        )
    }

    @Test
    fun `gap tugamagan qator keyingisi bilan birlashadi`() {
        assertEquals(
            listOf("Bu gap qator oxirida uzilgan va davom etadi."),
            ReadingText.paragraphs("Bu gap qator oxirida\nuzilgan va davom etadi."),
        )
    }

    @Test
    fun `nuqtadan keyin kichik harf bolsa qisqartma deb birlashadi`() {
        assertEquals(listOf("Dr. smith keldi."), ReadingText.paragraphs("Dr.\nsmith keldi."))
    }

    @Test
    fun `defis bilan bolingan sozning qismlari tutashadi`() {
        assertEquals(listOf("so'zlar bilan"), ReadingText.paragraphs("so'z-\nlar bilan"))
    }

    @Test
    fun `defisdan keyin bosh harf kelsa defis saqlanadi`() {
        assertEquals(listOf("Sharq-G'arb"), ReadingText.paragraphs("Sharq-\nG'arb"))
    }

    @Test
    fun `tire bilan boshlangan dialog qatorlari alohida`() {
        assertEquals(
            listOf("\u2014 Salom, \u2014 dedi u.", "\u2014 Xayr."),
            ReadingText.paragraphs("\u2014 Salom, \u2014 dedi u.\n\u2014 Xayr."),
        )
    }

    @Test
    fun `markdown sarlavha belgisi olib tashlanadi`() {
        assertEquals(listOf("Kichik sarlavha", "Matn."), ReadingText.paragraphs("## Kichik sarlavha\n\nMatn."))
    }

    @Test
    fun `ortiqcha probel va CRLF tozalanadi`() {
        assertEquals(listOf("Bir ikki", "Uch."), ReadingText.paragraphs("Bir  ikki\r\n\r\nUch."))
    }

    @Test
    fun `bosh matn abzats bermaydi`() {
        assertEquals(emptyList<String>(), ReadingText.paragraphs(""))
        assertEquals(emptyList<String>(), ReadingText.paragraphs("  \n\n \n"))
    }
}
