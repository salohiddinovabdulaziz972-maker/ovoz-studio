package uz.ovozstudio.app.media.doc

import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PlainTextDecoderTest {

    private fun withBom(bom: IntArray, text: String, charset: Charset): ByteArray =
        ByteArray(bom.size) { bom[it].toByte() } + text.toByteArray(charset)

    private val utf8Bom = intArrayOf(0xEF, 0xBB, 0xBF)
    private val utf16LeBom = intArrayOf(0xFF, 0xFE)
    private val utf16BeBom = intArrayOf(0xFE, 0xFF)

    @Test
    fun `bosh massiv bosh matn beradi`() {
        val decoded = PlainTextDecoder.decode(ByteArray(0))

        assertEquals("", decoded.text)
        assertFalse(decoded.bomFound)
    }

    @Test
    fun `utf8 bom olib tashlanadi`() {
        val raw = withBom(utf8Bom, "Salom, dunyo!", StandardCharsets.UTF_8)
        val decoded = PlainTextDecoder.decode(raw)

        assertEquals("Salom, dunyo!", decoded.text)
        assertEquals(TextEncoding.UTF_8, decoded.encoding)
        assertTrue(decoded.bomFound)
    }

    @Test
    fun `bomsiz utf8 ozbekcha matn ozgarmaydi`() {
        // O'zbek lotin apostroflari (ʻ ʼ) — ko'p baytli belgilar. Ular
        // yo'qolsa, matn «Salom, dunyo» bo'lib qolardi.
        val text = "Gʻayrat shundoq: oʻzbekcha soʻzlar — toʻgʻri."

        val decoded = PlainTextDecoder.decode(text.toByteArray(StandardCharsets.UTF_8))

        assertEquals(text, decoded.text)
        assertEquals(TextEncoding.UTF_8, decoded.encoding)
        assertFalse(decoded.bomFound)
    }

    @Test
    fun `utf16le bom bilan oqiladi`() {
        val raw = withBom(utf16LeBom, "Salom", StandardCharsets.UTF_16LE)
        val decoded = PlainTextDecoder.decode(raw)

        assertEquals("Salom", decoded.text)
        assertEquals(TextEncoding.UTF_16_LE, decoded.encoding)
        assertTrue(decoded.bomFound)
    }

    @Test
    fun `utf16be bom bilan oqiladi`() {
        val raw = withBom(utf16BeBom, "Salom", StandardCharsets.UTF_16BE)
        val decoded = PlainTextDecoder.decode(raw)

        assertEquals("Salom", decoded.text)
        assertEquals(TextEncoding.UTF_16_BE, decoded.encoding)
        assertTrue(decoded.bomFound)
    }

    @Test
    fun `bomsiz utf16 nollar boyicha topiladi`() {
        // Har ikkinchi bayt nol: lotin matn UTF-16 da aynan shunday ko'rinadi.
        val raw = "Hello world".toByteArray(StandardCharsets.UTF_16LE)
        val decoded = PlainTextDecoder.decode(raw)

        assertEquals("Hello world", decoded.text)
        assertEquals(TextEncoding.UTF_16_LE, decoded.encoding)
        assertFalse(decoded.bomFound)
    }

    @Test
    fun `kirill matni windows-1251 da oqiladi`() {
        val cyrillic = Charset.forName("windows-1251")
        val text = "Салом, дўстлар! Бу китоб."
        val raw = text.toByteArray(cyrillic)

        // Bu baytlar UTF-8 emas — qattiq tekshiruv shuni aniqlashi kerak.
        val decoded = PlainTextDecoder.decode(raw)

        assertEquals(text, decoded.text)
        assertEquals(TextEncoding.WINDOWS_1251, decoded.encoding)
    }

    @Test
    fun `crlf va yakka cr bitta n ga keltiriladi`() {
        val raw = "birinchi\r\nikkinchi\r\nuchinchi\rto'rtinchi".toByteArray(StandardCharsets.UTF_8)

        val decoded = PlainTextDecoder.decode(raw)

        assertEquals("birinchi\nikkinchi\nuchinchi\nto'rtinchi", decoded.text)
        assertFalse(decoded.text.contains('\r'))
    }

    @Test
    fun `nol belgilari olib tashlanadi`() {
        // Noto'g'ri aniqlangan UTF-16 dan qolgan nollar sintezator tomonidan
        // ovoz bilan o'qib yuborilardi.
        val raw = "a\u0000b\u0000c".toByteArray(StandardCharsets.UTF_8)

        assertEquals("abc", PlainTextDecoder.decode(raw).text)
    }

    @Test
    fun `readLimited oddiy oqimni toliq oqiydi`() {
        val text = "Qisqa matn."
        val decoded = PlainTextDecoder.decode(
            ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)),
        )

        assertEquals(text, decoded.text)
    }

    @Test
    fun `readLimited chegaradan oshsa xato beradi`() {
        val stream = ByteArrayInputStream(ByteArray(100) { 'a'.code.toByte() })

        assertThrows(DocumentTooLargeException::class.java) {
            PlainTextDecoder.readLimited(stream, maxBytes = 10)
        }
    }

    @Test
    fun `readLimited aynan chegaradagi hajmni oqiy oladi`() {
        val stream = ByteArrayInputStream(ByteArray(10) { 'a'.code.toByte() })

        assertEquals(10, PlainTextDecoder.readLimited(stream, maxBytes = 10).size)
    }
}
