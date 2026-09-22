package uz.ovozstudio.app.media.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sahifalar ro'yxatini o'qish.
 *
 * Bu matn foydalanuvchi qo'lda yozadigan yagona joy, shuning uchun har bir
 * xato turi alohida: «tushunarsiz yozuv» va «bunday sahifa yo'q» — ikki xil
 * yordam talab qiladi.
 */
class PageRangeTest {

    private fun pages(text: String, count: Int = 20): List<Int> {
        val parsed = PageRange.parse(text, count)
        return (parsed as PageRange.Parsed.Pages).pages
    }

    private fun invalid(text: String, count: Int = 20): PageRange.Parsed.Invalid =
        PageRange.parse(text, count) as PageRange.Parsed.Invalid

    @Test
    fun `bitta sahifa va oraliq birga o'qiladi`() {
        assertEquals(listOf(1, 2, 3, 7, 10, 11, 12), pages("1-3, 7, 10-12"))
    }

    @Test
    fun `bitta sahifa`() {
        assertEquals(listOf(5), pages(" 5 "))
    }

    @Test
    fun `takror va tartibsiz sahifalar tartiblanadi`() {
        assertEquals(listOf(1, 2, 3), pages("3,1,2,2"))
        assertEquals(listOf(1, 2, 3, 4), pages("1-3, 2-4"))
    }

    @Test
    fun `ajratgich probel yoki nuqta-vergul bolishi mumkin`() {
        assertEquals(listOf(1, 4, 6), pages("1 4 6"))
        assertEquals(listOf(1, 4, 6), pages("1;4;6"))
    }

    @Test
    fun `chiziqcha atrofidagi probel va uzun chiziqcha qabul qilinadi`() {
        assertEquals(listOf(2, 3, 4), pages("2 - 4"))
        assertEquals(listOf(2, 3, 4), pages("2\u20134"))
        assertEquals(listOf(2, 3, 4), pages("2\u2014 4"))
    }

    @Test
    fun `bosh yozuv EMPTY beradi`() {
        assertEquals(PageRange.Problem.EMPTY, invalid("").problem)
        assertEquals(PageRange.Problem.EMPTY, invalid("  ,  ; ").problem)
    }

    @Test
    fun `harf yoki ortiqcha belgi SYNTAX beradi`() {
        assertEquals(PageRange.Parsed.Invalid(PageRange.Problem.SYNTAX, "a"), invalid("1, a"))
        assertEquals(PageRange.Parsed.Invalid(PageRange.Problem.SYNTAX, "1-2-3"), invalid("1-2-3"))
    }

    @Test
    fun `hujjatda yoq sahifa OUT_OF_RANGE beradi`() {
        assertEquals(PageRange.Parsed.Invalid(PageRange.Problem.OUT_OF_RANGE, "0"), invalid("0", 5))
        assertEquals(PageRange.Parsed.Invalid(PageRange.Problem.OUT_OF_RANGE, "6"), invalid("6", 5))
        assertEquals(PageRange.Parsed.Invalid(PageRange.Problem.OUT_OF_RANGE, "1-9"), invalid("1-9", 5))
    }

    @Test
    fun `juda katta oraliq royxat yigmasdan rad etiladi`() {
        assertEquals(
            PageRange.Parsed.Invalid(PageRange.Problem.OUT_OF_RANGE, "1-999999999"),
            invalid("1-999999999", 100),
        )
    }

    @Test
    fun `teskari oraliq REVERSED beradi`() {
        assertEquals(PageRange.Parsed.Invalid(PageRange.Problem.REVERSED, "4-2"), invalid("4-2", 5))
    }

    @Test
    fun `complement tanlanmagan sahifalarni beradi`() {
        assertEquals(listOf(1, 4, 5), PageRange.complement(listOf(2, 3), 5))
        assertEquals(emptyList<Int>(), PageRange.complement(listOf(1, 2, 3), 3))
        assertEquals(listOf(1, 2, 3), PageRange.complement(emptyList(), 3))
    }

    @Test
    fun `format oraliqlarni qisqartiradi`() {
        assertEquals("1-3, 7, 10-12", PageRange.format(listOf(1, 2, 3, 7, 10, 11, 12)))
        assertEquals("5", PageRange.format(listOf(5)))
        assertEquals("", PageRange.format(emptyList()))
    }
}
