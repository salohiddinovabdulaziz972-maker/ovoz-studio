package uz.ovozstudio.app.media.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectorTest {

    @Test
    fun `ozbek lotin matni lotin deb aniqlanadi`() {
        assertEquals(
            TextScript.LATIN,
            ScriptDetector.detect("Assalomu alaykum, bugun havo juda ochiq."),
        )
    }

    @Test
    fun `ozbek kirill matni kirill deb aniqlanadi`() {
        assertEquals(
            TextScript.CYRILLIC,
            ScriptDetector.detect("Ассалому алайкум, бугун ҳаво жуда очиқ."),
        )
    }

    @Test
    fun `rus tilidagi matn ham kirill`() {
        assertEquals(TextScript.CYRILLIC, ScriptDetector.detect("Здравствуйте, как дела?"))
    }

    @Test
    fun `harfsiz matn noma lum`() {
        assertEquals(TextScript.UNKNOWN, ScriptDetector.detect(""))
        assertEquals(TextScript.UNKNOWN, ScriptDetector.detect("   \n  "))
        assertEquals(TextScript.UNKNOWN, ScriptDetector.detect("123 456 — 78!"))
        assertEquals(TextScript.UNKNOWN, ScriptDetector.detect("... ?!…"))
    }

    @Test
    fun `bitta chet harf matnni aralash qilmaydi`() {
        // Sarlavhada bitta ruscha atama uchrasa, butun kitob aralash
        // hisoblanmasligi kerak: aks holda til tanlash ma'nosiz bo'lardi.
        val text = "O'zbekiston Respublikasi. Bu yerda «область» so'zi bor. Yana o'zbekcha davom etadi."
        assertEquals(TextScript.LATIN, ScriptDetector.detect(text))
    }

    @Test
    fun `teng aralash matn aralash deb aniqlanadi`() {
        assertEquals(
            TextScript.MIXED,
            ScriptDetector.detect("Ассалому алайкум, Assalomu alaykum do'stlar."),
        )
    }

    @Test
    fun `kamchilik yozuv chegaradan oshsa aralash`() {
        // 10 lotin + 4 kirill: kamchilik asosiyning uchdan biridan ko'p.
        assertEquals(TextScript.MIXED, ScriptDetector.detect("abcdefghij абвг"))
        // 10 lotin + 3 kirill: uchdan birga yetmaydi — asosiy yozuv qoladi.
        assertEquals(TextScript.LATIN, ScriptDetector.detect("abcdefghij абв"))
        // Kamchilik tomonda bo'lganda ham qoida simmetrik ishlaydi.
        assertEquals(TextScript.CYRILLIC, ScriptDetector.detect("абвгдежзий abc"))
    }

    @Test
    fun `ozbek kirill harflari alohida aniqlanadi`() {
        assertTrue(ScriptDetector.hasUzbekCyrillic("Ўзбекистон"))
        assertTrue(ScriptDetector.hasUzbekCyrillic("ғалати"))
        assertFalse(ScriptDetector.hasUzbekCyrillic("Здравствуйте"))
        assertFalse(ScriptDetector.hasUzbekCyrillic("Assalomu alaykum"))
    }

    // --- Til variantlari ----------------------------------------------------

    @Test
    fun `birinchi variant har doim ozbek`() {
        for (script in TextScript.entries) {
            assertEquals(
                "yozuv: $script",
                "uz-UZ",
                ScriptDetector.languageCandidates(script).first(),
            )
        }
    }

    @Test
    fun `lotin uchun zaxira turk tili`() {
        assertEquals(
            listOf("uz-UZ", "tr-TR", "en-US"),
            ScriptDetector.languageCandidates(TextScript.LATIN),
        )
    }

    @Test
    fun `kirill uchun zaxira rus tili`() {
        assertEquals(
            listOf("uz-UZ", "ru-RU"),
            ScriptDetector.languageCandidates(TextScript.CYRILLIC),
        )
    }

    @Test
    fun `til teglari standart korinishda`() {
        // Til teglari `Locale.forLanguageTag` ga uzatiladi: `uz-UZ` emas,
        // `uz_UZ` yozilsa u tilni tanimaydi va jim qoladi.
        val tags = TextScript.entries.flatMap { ScriptDetector.languageCandidates(it) }
        for (tag in tags) {
            assertTrue("teg: $tag", Regex("^[a-z]{2}-[A-Z]{2}$").matches(tag))
        }
    }
}
