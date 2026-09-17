package uz.ovozstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Sonlarni joriy tilga mos yozish.
 *
 * Bu kichik qatlamning sababi ekran o'quvchi: u «31.5» ni «o'ttiz bir nuqta
 * besh» deb o'qiydi, «31,5» esa «o'ttiz bir butun besh» bo'lib eshitiladi.
 * O'zbek va rus tillarida o'nlik kasr vergul bilan yoziladi, `toString()`
 * esa har doim nuqta qo'yadi — farq faqat shu yerda tuzatiladi.
 */
class LocalizedNumberTest {

    private val uzbek = Locale.forLanguageTag("uz")
    private val russian = Locale.forLanguageTag("ru")
    private val english = Locale.US

    private val hz = "Gerts"
    private val khz = "kilogerts"

    @Test
    fun `olchov birligi chastotaga qarab tanlanadi`() {
        assertEquals("62 Gerts", LocalizedNumber.frequency(62.0, hz, khz, uzbek))
        assertEquals("500 Gerts", LocalizedNumber.frequency(500.0, hz, khz, uzbek))
        assertEquals("1 kilogerts", LocalizedNumber.frequency(1000.0, hz, khz, uzbek))
        assertEquals("16 kilogerts", LocalizedNumber.frequency(16_000.0, hz, khz, uzbek))
    }

    @Test
    fun `onlik kasr tilga mos yoziladi`() {
        // O'zbek va rus tillarida vergul, ingliz tilida nuqta.
        assertEquals("31,5 Gerts", LocalizedNumber.frequency(31.5, hz, khz, uzbek))
        assertEquals("31,5 Gerts", LocalizedNumber.frequency(31.5, hz, khz, russian))
        assertEquals("31.5 Gerts", LocalizedNumber.frequency(31.5, hz, khz, english))

        assertEquals("12,5 kilogerts", LocalizedNumber.frequency(12_500.0, hz, khz, uzbek))
        assertEquals("12.5 kilogerts", LocalizedNumber.frequency(12_500.0, hz, khz, english))
    }

    @Test
    fun `butun son kasr qismsiz yoziladi`() {
        assertEquals("8 kilogerts", LocalizedNumber.frequency(8000.0, hz, khz, uzbek))
        assertEquals("4 kilogerts", LocalizedNumber.frequency(4000.0, hz, khz, russian))
        // Chegara aynan 1000 Hz da: undan yuqorisi kilogertsda yoziladi.
        assertEquals("1 kilogerts", LocalizedNumber.frequency(1000.0, hz, khz, uzbek))
    }

    @Test
    fun `desibel manfiy belgisi bilan yoziladi`() {
        assertEquals("-1,2 dB", LocalizedNumber.decibels(-1.2, DB, uzbek))
        assertEquals("-1.2 dB", LocalizedNumber.decibels(-1.2, DB, english))
        assertEquals("0 dB", LocalizedNumber.decibels(0.0, DB, uzbek))
    }

    @Test
    fun `mingliklar ajratgichi qoyilmaydi`() {
        // «12 500» emas, «12500»: guruhlash o'zbek tilida bo'sh joy bilan
        // yoziladi va ekran o'quvchi uni ikkita son deb o'qishi mumkin.
        val text = LocalizedNumber.format(12_500.0, uzbek)
        assertEquals("12500", text)
    }

    @Test
    fun `kasr xonalari soni cheklanadi`() {
        assertEquals("12.3", LocalizedNumber.format(12.3456, english, fractionDigits = 1))
        assertEquals("12.35", LocalizedNumber.format(12.3456, english, fractionDigits = 2))
        assertEquals("12", LocalizedNumber.format(12.0, english, fractionDigits = 2))
    }

    private companion object {
        const val DB = "dB"
    }
}
