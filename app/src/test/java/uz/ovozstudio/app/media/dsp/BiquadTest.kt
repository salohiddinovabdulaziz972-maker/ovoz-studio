package uz.ovozstudio.app.media.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Filtr koeffitsientlari.
 *
 * Bu yerda tekshiriladigan narsa — filtrning **chastota javobi**: qo'ng'iroq
 * filtr o'z chastotasida aynan kerakli kuchaytirishni berishi, undan
 * uzoqda esa hech narsa qilmasligi. Bu xato bo'lsa, ekvalayzer «ishlaydi»,
 * lekin noto'g'ri joyni tuzatadi — bunday xatoni quloq bilan sezish qiyin,
 * hisob bilan esa oson.
 */
class BiquadTest {

    private val rate = 48_000

    @Test
    fun `qongiroq filtr oz chastotasida kerakli kuchaytirishni beradi`() {
        for (gain in listOf(-12.0, -6.0, 3.0, 6.0, 12.0)) {
            val filter = Biquad.peaking(rate, 1000.0, gain, EqBands.q(EqBandCount.TEN))
            assertEquals("$gain dB", gain, filter.gainDbAt(1000.0, rate), 0.01)
        }
    }

    @Test
    fun `qongiroq filtr chastotadan uzoqda tegilmaydi`() {
        val filter = Biquad.peaking(rate, 1000.0, 12.0, EqBands.q(EqBandCount.TEN))

        assertEquals("20 Hz", 0.0, filter.gainDbAt(20.0, rate), 0.5)
        assertEquals("18 kHz", 0.0, filter.gainDbAt(18_000.0, rate), 0.5)
    }

    @Test
    fun `uchdan bir oktava polosasi tor`() {
        // Tor polosa qo'shnisiga kamroq ta'sir qiladi: bir oktava narida
        // kuchaytirish deyarli nolga tushishi kerak.
        val narrow = Biquad.peaking(rate, 1000.0, 12.0, EqBands.q(EqBandCount.THIRTY_ONE))
        val wide = Biquad.peaking(rate, 1000.0, 12.0, EqBands.q(EqBandCount.TEN))

        val narrowNeighbour = narrow.gainDbAt(2000.0, rate)
        val wideNeighbour = wide.gainDbAt(2000.0, rate)

        assertTrue("tor polosa qo'shnisiga kamroq tegadi", narrowNeighbour < wideNeighbour)
        assertTrue("bir oktava narida tor polosa deyarli nol", narrowNeighbour < 0.5)
    }

    @Test
    fun `nol kuchaytirish filtr qurmaydi`() {
        assertTrue(Biquad.peaking(rate, 1000.0, 0.0, 1.41).isIdentity)
        assertTrue(Biquad.identity().isIdentity)
    }

    @Test
    fun `barqarorlik hamma polosalarda saqlanadi`() {
        // Nominal qiymatlardan tashqariga chiqib ketadigan holat: eng katta
        // kuchaytirish, eng tor polosa va eng past chastota. Filtr barqaror
        // bo'lmasa chiqish cheksiz o'sib, fayl shovqinga aylanardi.
        for (count in EqBandCount.entries) {
            for (center in EqBands.centers(count, rate)) {
                for (gain in listOf(-12.0, 12.0)) {
                    val filter = Biquad.peaking(rate, center, gain, EqBands.q(count))
                    assertTrue("$center Hz, $gain dB barqaror emas", filter.stable)
                }
            }
        }
    }

    @Test
    fun `nyquist chegarasidagi chastota filtr qurmaydi`() {
        // Namuna olish chastotasining yarmidan yuqorini kuchaytirish mumkin
        // emas: 8 kHz li faylda 20 kHz degan tushunchaning o'zi yo'q.
        assertTrue(Biquad.peaking(8_000, 20_000.0, 6.0, 1.41).isIdentity)
        assertTrue(Biquad.peaking(8_000, 4_000.0, 6.0, 1.41).isIdentity)
        assertTrue(!Biquad.peaking(8_000, 3_900.0, 6.0, 1.41).isIdentity)
    }

    @Test
    fun `kesish filtri oz chastotasida uch desibel pasaytiradi`() {
        // Buterworth kesish filtrining klassik belgisi: kesish chastotasida
        // javob aynan -3 dB. Bu qiymat xato bo'lsa, filtr boshqa chastotada
        // kesayotgan bo'lardi.
        val filter = Biquad.highPass(rate, 120.0)

        assertEquals(-3.0103, filter.gainDbAt(120.0, rate), 0.05)
        assertTrue("pastda kuchli pasaytiradi", filter.gainDbAt(30.0, rate) < -20.0)
        assertEquals("o'tish zonasida tegmaydi", 0.0, filter.gainDbAt(2_000.0, rate), 0.05)
    }
}
