package uz.ovozstudio.app.media.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    // --- Bo'laklash asoslari ------------------------------------------------

    @Test
    fun `bosh matn bosh royxat beradi`() {
        assertEquals(emptyList<SpeechChunk>(), TextChunker.split(""))
        assertEquals(emptyList<SpeechChunk>(), TextChunker.split("   \n\t  "))
    }

    @Test
    fun `chegaraga sigadigan matn bitta bolak boladi`() {
        val chunks = TextChunker.split("Assalomu alaykum. Bugun havo ochiq.", maxChars = 200)
        assertEquals(1, chunks.size)
        assertEquals("Assalomu alaykum. Bugun havo ochiq.", chunks[0].text)
    }

    @Test
    fun `bolak matni manbadan qirqib olinadi`() {
        // Ofsetlar to'g'ri bo'lsa, qirqib olingan matn bo'lak matniga teng.
        val source = "Birinchi gap. Ikkinchi gap. Uchinchi gap shu yerda tugaydi."
        val chunks = TextChunker.split(source, maxChars = 30)
        for (chunk in chunks) {
            assertEquals(chunk.text, source.substring(chunk.startOffset, chunk.endOffset))
        }
    }

    @Test
    fun `har bir bolak chegaradan oshmaydi`() {
        val source = (1..40).joinToString(" ") { "Bu $it -juda uzun jumla bolib unda yetarlicha soz bor." }
        val chunks = TextChunker.split(source, maxChars = 120)
        assertTrue(chunks.isNotEmpty())
        for (chunk in chunks) {
            assertTrue("bo'lak ${chunk.text.length} belgi", chunk.text.length <= 120)
        }
    }

    @Test
    fun `bolaklar manbani toldiradi va manba yagona`() {
        // Bo'laklar matnni tashlab ketmaydi va uni ikki marta aytmaydi:
        // chegara so'z chegarasida bo'lgani uchun faqat bo'shliq yo'qoladi.
        val source = (1..20).joinToString(". ") { "Gap raqami $it shu yerda" } + "."
        val chunks = TextChunker.split(source, maxChars = 90)
        val joined = chunks.joinToString(" ") { it.text }
        assertEquals(source.replace(Regex("\\s+"), " "), joined.replace(Regex("\\s+"), " "))
    }

    @Test
    fun `ofsetlar osib boradi`() {
        val source = (1..20).joinToString(". ") { "Gap raqami $it shu yerda" } + "."
        val chunks = TextChunker.split(source, maxChars = 90)
        for (i in 1 until chunks.size) {
            assertTrue(chunks[i].startOffset >= chunks[i - 1].endOffset)
        }
    }

    // --- Jumla chegaralari --------------------------------------------------

    @Test
    fun `qisqartmadan keyin jumla bolinmaydi`() {
        val text = "Kitob 2020 y. chiqqan edi. Keyingi gap shu."
        val chunks = TextChunker.split(text, maxChars = 35)
        assertTrue(
            "«y.» dan keyin bo'lindi: ${chunks.map { it.text }}",
            chunks.any { it.text.contains("2020 y. chiqqan") },
        )
    }

    @Test
    fun `kichik harf bilan davom etgan nuqta jumlani tugatmaydi`() {
        val text = "va h.k. keyin davom etadi. Tamom."
        val chunks = TextChunker.split(text, maxChars = 25)
        assertTrue(chunks.any { it.text.contains("h.k. keyin") })
    }

    @Test
    fun `son ichidagi nuqta jumlani bolmaydi`() {
        val text = "Qiymat 3.14 ga teng. Keyingisi 2.71."
        val chunks = TextChunker.split(text, maxChars = 22)
        assertTrue(
            "son ikkiga bo'lindi: ${chunks.map { it.text }}",
            chunks.any { it.text.contains("3.14") },
        )
    }

    @Test
    fun `yopuvchi qoshtirnoq jumla ichida qoladi`() {
        val text = "«U keldi.» dedi u. Keyin ketdi."
        val chunks = TextChunker.split(text, maxChars = 20)
        assertTrue(chunks.any { it.text.startsWith("«U keldi.»") })
    }

    @Test
    fun `qator oxiri jumla oxiri hisoblanadi`() {
        val text = "Birinchi qator\nIkkinchi qator\nUchinchi qator"
        val chunks = TextChunker.split(text, maxChars = 20)
        assertEquals(3, chunks.size)
        assertEquals("Birinchi qator", chunks[0].text)
        assertEquals("Uchinchi qator", chunks[2].text)
    }

    // --- Uzun jumla ichida bo'lish ------------------------------------------

    @Test
    fun `uzun jumla soz chegarasida bolinadi`() {
        val source = (1..80).joinToString(" ") { "soz$it" }
        val chunks = TextChunker.split(source, maxChars = 100)
        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            assertTrue(chunk.text.length <= 100)
            // Har bir bo'lak to'liq so'zlar bilan boshlanadi va tugaydi.
            assertTrue(!chunk.text.startsWith(" "))
            assertTrue(!chunk.text.endsWith(" "))
        }
    }

    @Test
    fun `juda uzun soz qattiq kesiladi`() {
        val source = "a".repeat(250)
        val chunks = TextChunker.split(source, maxChars = 100)
        assertEquals(3, chunks.size)
        assertEquals(source, chunks.joinToString("") { it.text })
    }

    @Test
    fun `uzun son ortasidan kesilmaydi`() {
        // Kesim chegaraga eng yaqin bo'shliqqa tushishi kerak, sondan oldingi
        // bo'shliqqa emas — aks holda raqam ikki bo'lakka bo'linib, sintezator
        // uni ikki marta o'qirdi.
        val digits = "1234567890".repeat(5)
        val source = "a".repeat(80) + " " + digits
        val chunks = TextChunker.split(source, maxChars = 100)
        assertTrue(
            "son bo'lindi: ${chunks.map { it.text.length }}",
            chunks.any { it.text.contains(digits) },
        )
    }

    // --- Chegara qiymatlari -------------------------------------------------

    @Test
    fun `juda kichik chegara kotariladi`() {
        // MIN dan kichik qiymat bilan bo'laklash ma'nosiz bo'lardi: bitta
        // so'z ham sig'maydi. Shu sababli qiymat ko'tariladi va ishlashda
        // davom etadi — jim qolmaydi.
        val chunks = TextChunker.split("Qisqa matn shu yerda.", maxChars = 5)
        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.all { it.text.length <= TextChunker.MIN_MAX_CHARS })
    }

    @Test
    fun `chaqiruvchi bergan chegara ozgartirilmaydi`() {
        // MIN dan katta har qanday qiymat aynan ishlatiladi. Aks holda
        // `maxChars = 30` deb chaqirgan kod jimgina boshqa natija olardi.
        val source = (1..40).joinToString(" ") { "soz$it" }
        val chunks = TextChunker.split(source, maxChars = 30)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.text.length <= 30 })
    }

    @Test
    fun `splitRange ofsetlarni manbaga qaytaradi`() {
        val source = "Bosh. O'rta qism shu yerda. Oxir."
        val from = source.indexOf("O'rta")
        val to = source.indexOf("Oxir")
        val chunks = TextChunker.splitRange(source, from, to, maxChars = 200)
        assertEquals(1, chunks.size)
        assertEquals("O'rta qism shu yerda.", chunks[0].text)
        assertEquals(from, chunks[0].startOffset)
        assertEquals(chunks[0].text, source.substring(chunks[0].startOffset, chunks[0].endOffset))
    }

    @Test
    fun `notogri oraliq bosh royxat beradi`() {
        val source = "Matn shu yerda."
        assertEquals(emptyList<SpeechChunk>(), TextChunker.splitRange(source, 5, 5))
        assertEquals(emptyList<SpeechChunk>(), TextChunker.splitRange(source, 9, 3))
        assertEquals(emptyList<SpeechChunk>(), TextChunker.splitRange(source, -5, -1))
    }

    @Test
    fun `kirill matn ham bolaklanadi`() {
        val source = "Ассалому алайкум. Бугун ҳаво очиқ. Китоб 2020 й. чиққан."
        val chunks = TextChunker.split(source, maxChars = 30)
        assertTrue(chunks.any { it.text.contains("2020 й. чиққан") })
        assertEquals(source.replace(Regex("\\s+"), " "), chunks.joinToString(" ") { it.text }.replace(Regex("\\s+"), " "))
    }
}
