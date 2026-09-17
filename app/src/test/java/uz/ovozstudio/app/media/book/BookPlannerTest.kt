package uz.ovozstudio.app.media.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.doc.Chapter

class BookPlannerTest {

    private fun chapter(title: String, text: String, start: Int = 0): Chapter =
        Chapter(title = title, text = text, startOffset = start, endOffset = start + text.length)

    @Test
    fun `bob sarlavhasi ovoz bilan elon qilinadi`() {
        val plan = BookPlanner.plan(listOf(chapter("1-BOB", "Salim aka haqida")))

        val utterances = plan.chapters.single().utterances
        assertEquals(2, utterances.size)
        assertTrue(utterances.first().isTitle)
        assertEquals("1-BOB", utterances.first().text)
        assertFalse(utterances.last().isTitle)
        assertEquals("Salim aka haqida", utterances.last().text)
    }

    @Test
    fun `sarlavha eloni ochirilsa boyicha faqat matn qoladi`() {
        val plan = BookPlanner.plan(
            listOf(chapter("1-BOB", "Salim aka haqida")),
            announceTitles = false,
        )

        val utterances = plan.chapters.single().utterances
        assertEquals(1, utterances.size)
        assertFalse(utterances.single().isTitle)
    }

    @Test
    fun `matni yoq bob sarlavhasi bilan qoladi`() {
        // Sarlavhadan boshqa matni yo'q bob: e'lon o'chirilgan bo'lsa ham
        // ovozsiz qolmasligi kerak — aks holda boblar tartibi siljib ketardi.
        val plan = BookPlanner.plan(
            listOf(chapter("2-BOB", "   "), chapter("3-BOB", "Matn")),
            announceTitles = false,
        )

        val first = plan.chapters.first()
        assertEquals(1, first.utterances.size)
        assertEquals("2-BOB", first.utterances.single().text)
        assertTrue(first.utterances.single().isTitle)
    }

    @Test
    fun `uzun bob sintezator chegarasidan oshmaydigan bolaklarga bolinadi`() {
        val text = (1..200).joinToString(" ") { "so$it" }
        val plan = BookPlanner.plan(listOf(chapter("1-BOB", text)), maxChars = 100)

        val body = plan.chapters.single().utterances.filterNot { it.isTitle }
        assertTrue("bo'laklar kamida ikkita bo'lishi kerak", body.size >= 2)
        for (utterance in body) {
            assertTrue(
                "bo'lak ${utterance.text.length} belgi — chegaradan uzun",
                utterance.text.length <= 100,
            )
        }
    }

    @Test
    fun `bolak ofsetlari manba matniga togri keladi`() {
        // Ofsetlar «qayerda qoldim» degan savolga javob beradi: ular
        // siljib ketsa, o'qishni davom ettirish noto'g'ri joydan boshlanardi.
        val text = (1..200).joinToString(" ") { "so$it" }
        val plan = BookPlanner.plan(listOf(chapter("1-BOB", text)), maxChars = 100)

        val body = plan.chapters.single().utterances.filterNot { it.isTitle }
        for (utterance in body) {
            assertEquals(
                utterance.text,
                text.substring(utterance.startOffset, utterance.endOffset),
            )
        }
    }

    @Test
    fun `boblar tartibi va indeksi saqlanadi`() {
        val plan = BookPlanner.plan(
            listOf(
                chapter("1-BOB", "Bir"),
                chapter("2-BOB", "Ikki"),
                chapter("3-BOB", "Uch"),
            ),
        )

        assertEquals(listOf(0, 1, 2), plan.chapters.map { it.index })
        assertEquals(listOf("1-BOB", "2-BOB", "3-BOB"), plan.chapters.map { it.title })
    }

    @Test
    fun `belgi hajmi sarlavha elonini hisobga olmaydi`() {
        val plan = BookPlanner.plan(listOf(chapter("1-BOB", "Salim aka haqida")))

        assertEquals("Salim aka haqida".length, plan.chapters.single().charCount)
        assertEquals(2, plan.utteranceCount)
    }

    @Test
    fun `bolaklar soni kitob boyicha jamlanadi`() {
        val plan = BookPlanner.plan(
            listOf(chapter("1-BOB", "Bir"), chapter("2-BOB", "Ikki")),
        )

        assertEquals(4, plan.utteranceCount)
        assertEquals("Bir".length + "Ikki".length, plan.charCount)
    }
}
