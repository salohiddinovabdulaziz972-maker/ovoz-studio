package uz.ovozstudio.app.media.doc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O'qib bo'lmaydigan («savatcha») matnni aniqlash.
 */
class TextQualityTest {

    private val normal = "Salim aka qishloqdan keldi va uzoq gapirdi. ".repeat(10)

    @Test
    fun `oddiy matn yaroqli`() {
        assertFalse(TextQuality.looksBroken(normal))
    }

    @Test
    fun `kirill va o'zbek harflari yaroqli`() {
        val text = "Ўзбекистон — гўзал юрт. Ғоя ва қалб. O‘zbekiston, g‘oya. ".repeat(10)
        assertFalse(TextQuality.looksBroken(text))
    }

    @Test
    fun `almashtirish belgilari ko'p bo'lsa matn buzuq`() {
        val text = "\uFFFD\uFFFD\uFFFD abc \uFFFD\uFFFD ".repeat(30)
        assertTrue(TextQuality.looksBroken(text))
    }

    @Test
    fun `shaxsiy oraliq belgilar ko'p bo'lsa matn buzuq`() {
        val text = "\uE001\uE002\uE003 ab \uE004\uE005 ".repeat(30)
        assertTrue(TextQuality.looksBroken(text))
    }

    @Test
    fun `bir necha yaroqsiz belgi matnni buzmaydi`() {
        val text = normal + "\uFFFD\uFFFD"
        assertFalse(TextQuality.looksBroken(text))
    }

    @Test
    fun `qisqa matn haqida hukm chiqarilmaydi`() {
        assertFalse(TextQuality.looksBroken("\uFFFD\uFFFD\uFFFD"))
        assertFalse(TextQuality.looksBroken(""))
    }

    @Test
    fun `qator ko'chirish va tab yaroqsiz emas`() {
        val text = ("So'z\tso'z\nyangi qator\r\n").repeat(20)
        assertFalse(TextQuality.looksBroken(text))
    }

    @Test
    fun `boshqaruv kodlari ko'p bo'lsa matn buzuq`() {
        val text = "\u0001\u0002\u0003 xy \u0004\u0005 ".repeat(30)
        assertTrue(TextQuality.looksBroken(text))
    }
}
