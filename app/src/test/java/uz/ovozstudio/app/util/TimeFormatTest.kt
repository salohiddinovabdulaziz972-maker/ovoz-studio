package uz.ovozstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `format chiqaradi soat daqiqa soniya millisoniya`() {
        assertEquals("00:00:00.000", TimeFormat.format(0))
        assertEquals("00:00:00.005", TimeFormat.format(5))
        assertEquals("00:00:01.000", TimeFormat.format(1_000))
        assertEquals("00:01:00.000", TimeFormat.format(60_000))
        assertEquals("01:02:03.456", TimeFormat.format(3_723_456))
    }

    @Test
    fun `format manfiy vaqtni nolga keltiradi`() {
        assertEquals("00:00:00.000", TimeFormat.format(-1))
        assertEquals("00:00:00.000", TimeFormat.format(Long.MIN_VALUE))
    }

    @Test
    fun `format va parse bir birini qaytaradi`() {
        val values = listOf(0L, 1L, 999L, 1_000L, 59_999L, 60_000L, 3_723_456L, 86_399_999L)
        for (value in values) {
            assertEquals(value, TimeFormat.parse(TimeFormat.format(value)))
        }
    }

    @Test
    fun `parse qisqa shakllarni ham tushunadi`() {
        assertEquals(5_000L, TimeFormat.parse("5"))
        assertEquals(65_000L, TimeFormat.parse("1:05"))
        assertEquals(3_723_456L, TimeFormat.parse("1:02:03.456"))
        assertEquals(400L, TimeFormat.parse("0.4"))
        assertEquals(450L, TimeFormat.parse("0.45"))
        // Kasr qismi uchta raqamdan uzun bo'lsa, qolgani tashlanadi.
        assertEquals(123L, TimeFormat.parse("0.1239"))
    }

    @Test
    fun `parse notogri kiritishda null qaytaradi`() {
        val invalid = listOf(
            "", "   ", "abc", "1:2:3:4", "1::2", ":", ".",
            "1:60", "0:60", "60:00", // daqiqa va soniya 59 dan oshmasligi kerak
            "-5", "1:-5", "1.2.3", "1 2",
        )
        for (text in invalid) {
            assertNull("«$text» noto'g'ri deb topilishi kerak edi", TimeFormat.parse(text))
        }
    }

    @Test
    fun `parse atrofidagi bosh joylarni tashlaydi`() {
        assertEquals(65_000L, TimeFormat.parse("  1:05  "))
    }

    @Test
    fun `formatSpoken birliklarni tashqaridan oladi`() {
        val uz = TimeFormat.SpokenUnits("soat", "daqiqa", "soniya")
        assertEquals("3 daqiqa 12 soniya", TimeFormat.formatSpoken(192_000, uz))
        assertEquals("0 soniya", TimeFormat.formatSpoken(0, uz))
        assertEquals("1 soat 5 soniya", TimeFormat.formatSpoken(3_605_000, uz))
    }

    @Test
    fun `formatSpoken nolga teng qismlarni tashlab ketadi`() {
        val uz = TimeFormat.SpokenUnits("soat", "daqiqa", "soniya")
        assertEquals("5 soniya", TimeFormat.formatSpoken(5_000, uz))
        assertEquals("2 daqiqa 0 soniya", TimeFormat.formatSpoken(120_000, uz))
    }

    @Test
    fun `formatSpoken manfiy vaqtni nolga keltiradi`() {
        assertEquals("0 s", TimeFormat.formatSpoken(-5_000))
    }
}
