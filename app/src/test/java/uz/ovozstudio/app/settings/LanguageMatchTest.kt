package uz.ovozstudio.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qurilma tilidan ilova tilini aniqlash.
 *
 * Bu funksiya ekranda ko'rinmaydi, lekin xatosi jimgina o'tadi: qurilma
 * o'zbekcha (kirill) bo'lib, ilova lotincha ochilsa, foydalanuvchi
 * tushunmaydigan yozuvni o'qishga majbur bo'lardi. Shuning uchun har bir
 * til — alohida holat.
 */
class LanguageMatchTest {

    @Test
    fun `ozbek lotin yozuvi topiladi`() {
        assertEquals(AppLanguage.UZ_LATN, LanguageMatch.resolve(listOf("uz-UZ")))
        assertEquals(AppLanguage.UZ_LATN, LanguageMatch.resolve(listOf("uz")))
    }

    @Test
    fun `ozbek kirill yozuvi topiladi`() {
        // Yozuvni faqat teg ichidagi "cyrl" qismidan ajratish mumkin:
        // o'zbekcha ikki yozuvda yoziladi, tillari bir xil.
        assertEquals(AppLanguage.UZ_CYRL, LanguageMatch.resolve(listOf("uz-Cyrl-UZ")))
        assertEquals(AppLanguage.UZ_CYRL, LanguageMatch.resolve(listOf("uz-CYRL")))
    }

    @Test
    fun `qurilma ozbekchada bolsa ozbekcha tanlanadi`() {
        assertEquals(AppLanguage.UZ_LATN, LanguageMatch.resolve(listOf("uz-UZ")))
        assertEquals(AppLanguage.RU, LanguageMatch.resolve(listOf("ru-RU")))
        assertEquals(AppLanguage.EN, LanguageMatch.resolve(listOf("en-GB")))
    }

    @Test
    fun `tillar tartibi saqlanadi`() {
        // Android tillarni afzallik bo'yicha beradi: birinchi mos kelgani
        // to'g'ri javob, qolganlariga qaralmaydi. Ya'ni tartib almashtirilsa,
        // javob ham almashadi.
        assertEquals(AppLanguage.RU, LanguageMatch.resolve(listOf("de-DE", "ru-RU")))
        assertEquals(AppLanguage.EN, LanguageMatch.resolve(listOf("en-US", "ru-RU")))
        assertEquals(AppLanguage.RU, LanguageMatch.resolve(listOf("ru-RU", "en-US")))
    }

    @Test
    fun `qollab quvvatlanmagan til ozbekchaga tushadi`() {
        // Ilovaning zaxira tili — o'zbekcha (`values/`), shuning uchun
        // nemischa qurilmada ham ilova o'zbekcha ochiladi.
        assertEquals(LanguageMatch.DEFAULT, LanguageMatch.resolve(listOf("de-DE", "fr-FR")))
        assertEquals(AppLanguage.UZ_LATN, LanguageMatch.resolve(listOf("de-DE")))
    }

    @Test
    fun `bosh royxat standart tilni beradi`() {
        assertEquals(LanguageMatch.DEFAULT, LanguageMatch.resolve(emptyList()))
        assertEquals(LanguageMatch.DEFAULT, LanguageMatch.resolve(listOf("", "  ")))
    }

    @Test
    fun `pastki chiziqli teg ham oqiladi`() {
        // Android ba'zi joylarda tegil "+" o'rniga pastki chiziq bilan
        // beradi ("uz_Cyrl_UZ") — ajratish shunga ham chidashi kerak.
        assertEquals(AppLanguage.UZ_CYRL, LanguageMatch.resolve(listOf("uz_Cyrl_UZ")))
    }

    @Test
    fun `faqat tizim tanlovigina tanlanmagan hisoblanadi`() {
        assertFalse(AppLanguage.SYSTEM.chosen)
        assertTrue(AppLanguage.UZ_LATN.chosen)
        assertTrue(AppLanguage.UZ_CYRL.chosen)
        assertTrue(AppLanguage.RU.chosen)
        assertTrue(AppLanguage.EN.chosen)
    }

    @Test
    fun `har bir tilning tegi boshqacha`() {
        // Teg takrorlansa, faylga yozilgan qiymat ikki tilga bir xil
        // o'qilardi — ya'ni tanlov jimgina boshqa tilga o'tib ketardi.
        val tags = AppLanguage.entries.mapNotNull { it.tag }
        assertEquals(tags.size, tags.toSet().size)
    }
}
