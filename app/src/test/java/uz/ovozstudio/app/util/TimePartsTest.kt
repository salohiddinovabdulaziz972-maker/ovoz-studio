package uz.ovozstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimePartsTest {

    @Test
    fun `bosh toplam vaqt bermaydi`() {
        assertTrue(TimeParts().isEmpty)
        assertNull(TimeParts().toMillisOrNull())
    }

    @Test
    fun `bosh maydonlar nol deb hisoblanadi`() {
        assertEquals(5_000L, TimeParts(seconds = "5").toMillisOrNull())
        assertEquals(300_000L, TimeParts(minutes = "5").toMillisOrNull())
        assertEquals(3_600_000L, TimeParts(hours = "1").toMillisOrNull())
        assertEquals(250L, TimeParts(millis = "250").toMillisOrNull())
    }

    @Test
    fun `qismlar qoshiladi`() {
        val parts = TimeParts(hours = "1", minutes = "2", seconds = "3", millis = "456")
        assertEquals(3_723_456L, parts.toMillisOrNull())
    }

    @Test
    fun `chegaradan oshgan qiymat xato`() {
        assertNull(TimeParts(minutes = "60").toMillisOrNull())
        assertNull(TimeParts(seconds = "60").toMillisOrNull())
        assertNull(TimeParts(millis = "1000").toMillisOrNull())
    }

    @Test
    fun `raqam boimagan kiritish xato`() {
        assertNull(TimeParts(seconds = "abc").toMillisOrNull())
        assertNull(TimeParts(minutes = "1a").toMillisOrNull())
        assertNull(TimeParts(millis = " ").toMillisOrNull())
    }

    @Test
    fun `chegaradagi qiymatlar togri`() {
        assertEquals(59_000L, TimeParts(seconds = "59").toMillisOrNull())
        assertEquals(3_599_000L, TimeParts(minutes = "59", seconds = "59").toMillisOrNull())
        assertEquals(999L, TimeParts(millis = "999").toMillisOrNull())
    }

    @Test
    fun `fromMillis va toMillisOrNull bir birini qaytaradi`() {
        val values = listOf(0L, 1L, 999L, 1_000L, 61_000L, 3_723_456L, 86_399_999L)
        for (value in values) {
            assertEquals(value, TimeParts.fromMillis(value).toMillisOrNull())
        }
    }

    @Test
    fun `fromMillis manfiy vaqtni nolga keltiradi`() {
        assertEquals(0L, TimeParts.fromMillis(-500).toMillisOrNull())
    }

    @Test
    fun `fromMillis toplamni bosh deb hisoblamaydi`() {
        // Nol ham to'ldirilgan toplam: aks holda tanlov «kiritilmagan» bo'lib
        // ko'rinardi va kesish butun faylni qamrab olardi.
        assertFalse(TimeParts.fromMillis(0).isEmpty)
    }
}
