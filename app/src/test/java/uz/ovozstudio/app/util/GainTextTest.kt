package uz.ovozstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Polosa kuchaytirishini qo'lda kiritish qoidalari.
 *
 * Kiritish maydoni — accessibility talabining markazida: ekran o'quvchi
 * bilan sirg'anma tugmani aniq qiymatga qo'yib bo'lmaydi, klaviatura esa
 * aniq son beradi. Shuning uchun maydon **hech qachon xato holatiga
 * tushmasligi** kerak — noto'g'ri belgi shunchaki tashlab yuboriladi.
 */
class GainTextTest {

    @Test
    fun `faqat son va bitta ajratgich qoladi`() {
        assertEquals("3", GainText.sanitize("3"))
        assertEquals("-3.5", GainText.sanitize("-3,5"))
        assertEquals("-3.5", GainText.sanitize("-3.5"))
        assertEquals("", GainText.sanitize("abc"))
        assertEquals("", GainText.sanitize("dB"))
    }

    @Test
    fun `ajratgich faqat bitta boladi`() {
        // Ikkinchi ajratgich ham, undan keyingi raqam ham tashlanadi:
        // «1.2.3» degan son yo'q.
        assertEquals("1.2", GainText.sanitize("1.2.3"))
        assertEquals("1.2", GainText.sanitize("1.2,3"))
        assertEquals("", GainText.sanitize("."))
        assertEquals("", GainText.sanitize(","))
    }

    @Test
    fun `nuqtadan boshlangan son nol bilan toldiriladi`() {
        // «.5» ni ekranda ko'rsatish chalkash: maydonda «0.5» turadi.
        assertEquals("0.5", GainText.sanitize(".5"))
        assertEquals("0.5", GainText.sanitize(",5"))
        assertEquals("-0.5", GainText.sanitize("-.5"))
    }

    @Test
    fun `minus faqat boshida turadi`() {
        assertEquals("-3", GainText.sanitize("-3"))
        // O'rtadagi minus tashlanadi: son musbat qoladi.
        assertEquals("3", GainText.sanitize("3-"))
        assertEquals("", GainText.sanitize("-"))
    }

    @Test
    fun `raqamlar soni chegaralangan`() {
        // Butun qism 2 xona (±12 dB), kasr qism 1 xona (0.5 yetarli).
        assertEquals("12", GainText.sanitize("123"))
        assertEquals("12.9", GainText.sanitize("12.99"))
        assertEquals("-12.9", GainText.sanitize("-12.9999"))
    }

    @Test
    fun `bosh matn nol deb oqiladi`() {
        assertEquals(0.0, GainText.parse(""), 0.0)
        assertEquals(0.0, GainText.parse("abc"), 0.0)
        assertEquals(0.0, GainText.parse("-"), 0.0)
    }

    @Test
    fun `qiymat chegaradan chiqmaydi`() {
        assertEquals(12.0, GainText.parse("20"), 0.0)
        assertEquals(-12.0, GainText.parse("-20"), 0.0)
        assertEquals(GainText.MAX_DB, GainText.parse("999"), 0.0)
        assertEquals(GainText.MIN_DB, GainText.parse("-999"), 0.0)
    }

    @Test
    fun `vergul ham onlik ajratgich sifatida oqiladi`() {
        assertEquals(3.5, GainText.parse("3,5"), 0.0)
        assertEquals(3.5, GainText.parse("3.5"), 0.0)
        assertEquals(-0.5, GainText.parse("-0,5"), 0.0)
    }

    @Test
    fun `maydonga yoziladigan korinish ortib ketgan nollardan toza`() {
        assertEquals("0", GainText.format(0.0))
        assertEquals("-3", GainText.format(-3.0))
        assertEquals("3.5", GainText.format(3.5))
        assertEquals("12", GainText.format(12.0))
        // Yaxlitlash bir xonagacha.
        assertEquals("3.5", GainText.format(3.46))
        assertEquals("3.5", GainText.format(3.54))
        assertEquals("0", GainText.format(0.04))
    }

    @Test
    fun `tugma yarim desibel qadam bilan suradi`() {
        assertEquals("0.5", GainText.nudge("0", GainText.STEP_DB))
        assertEquals("2.5", GainText.nudge("3", -GainText.STEP_DB))
        assertEquals("1", GainText.nudge("0.5", 0.5))
        assertEquals("0.5", GainText.nudge("", 0.5))
    }

    @Test
    fun `tugma chegaradan otib ketmaydi`() {
        assertEquals("12", GainText.nudge("12", 0.5))
        assertEquals("-12", GainText.nudge("-12", -0.5))
        assertEquals("11.5", GainText.nudge("12", -0.5))
    }

    @Test
    fun `format va parse bir-birini takrorlaydi`() {
        // Maydondan o'qilgan son qayta yozilganda o'zgarmasligi kerak —
        // aks holda tugma bosilganda qiymat «sakrab» ketardi.
        var value = GainText.MIN_DB
        while (value <= GainText.MAX_DB) {
            val text = GainText.format(value)
            assertEquals("$value dB", value, GainText.parse(text), 0.0)
            assertEquals("$value dB matni", text, GainText.format(GainText.parse(text)))
            value += GainText.STEP_DB
        }
    }
}
