package uz.ovozstudio.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Sozlamalar fayli bilan ishlash.
 *
 * Fayl — ilovaning yagona doimiy holati, ya'ni bu yerdagi xato jimgina
 * o'tadi: foydalanuvchi tilni tanlaydi, ilova qayta ochiladi va eski til
 * qaytadi. Shuning uchun har bir o'qish holati — buzuq fayl, bo'sh fayl,
 * notanish teg — alohida tekshiriladi.
 */
class AppSettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun store(file: File = File(folder.root, "loyiha.properties")) =
        AppSettingsStore(file)

    @Test
    fun `saqlangan sozlama oz holicha qaytadi`() {
        val file = File(folder.root, "sozlamalar.properties")
        val settings = AppSettings(language = AppLanguage.UZ_CYRL, simplified = true)

        store(file).save(settings)

        assertEquals(settings, store(file).load())
    }

    @Test
    fun `tizim tili ham saqlanadi`() {
        // `SYSTEM` ning tegi `null` — faylga yoziladigan qiymat alohida
        // bo'lmasa, u o'qishda «notanish teg» bo'lib, standart holatga
        // tushib qolardi. Bu yerda aynan shu yo'l tekshiriladi.
        val file = File(folder.root, "s.properties")
        store(file).save(AppSettings(language = AppLanguage.SYSTEM, simplified = false))

        assertEquals(AppLanguage.SYSTEM, store(file).load().language)
    }

    @Test
    fun `fayl yoq bolsa standart sozlama olinadi`() {
        val file = File(folder.root, "yoq.properties")
        assertFalse(file.exists())

        val settings = store(file).load()

        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertFalse(settings.simplified)
    }

    @Test
    fun `buzuq fayl ilovani yiqitmaydi`() {
        // `Properties.load` buzuq `\u` ketma-ketligida istisno tashlaydi.
        // Istisno ushlanmasa, ilova har ochilganda yiqilardi — fayl esa
        // foydalanuvchi qo'lida emas, ya'ni o'zi tuzata olmaydi.
        val file = File(folder.root, "buzuq.properties")
        file.writeText("kalit=\\u00zz\n")

        val settings = store(file).load()

        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertFalse(settings.simplified)
    }

    @Test
    fun `bosh fayl standart sozlama beradi`() {
        val file = File(folder.root, "bosh.properties")
        file.writeText("")

        assertEquals(AppSettings(), store(file).load())
    }

    @Test
    fun `notanish til tegi tizim tiliga tushadi`() {
        // Eski versiyada mavjud bo'lgan, keyin olib tashlangan til: tanlov
        // jimgina saqlanib qolmasligi kerak.
        val file = File(folder.root, "eski.properties")
        file.writeText("language=de\nsimplified=true\n")

        val settings = store(file).load()

        assertEquals(AppLanguage.SYSTEM, settings.language)
        assertTrue(settings.simplified)
    }

    @Test
    fun `yetishmayotgan kalitlar standart qiymat oladi`() {
        val file = File(folder.root, "chala.properties")
        file.writeText("language=ru\n")

        val settings = store(file).load()

        assertEquals(AppLanguage.RU, settings.language)
        assertFalse(settings.simplified)
    }

    @Test
    fun `notogri mantiqiy qiymat yolgonga aylanadi`() {
        val file = File(folder.root, "mantiq.properties")
        file.writeText("language=en\nsimplified=ha\n")

        assertFalse(store(file).load().simplified)
    }

    @Test
    fun `in files faylni oz nomi bilan yasaydi`() {
        // Nom tashqaridan ko'rinadi: `filesDir` ichida boshqa fayl bilan
        // to'qnashmasligi va qayta o'qishda o'sha fayl topilishi kerak.
        val saved = AppSettingsStore.inFiles(folder.root)
        saved.save(AppSettings(language = AppLanguage.EN, simplified = true))

        val file = File(folder.root, AppSettingsStore.FILE_NAME)
        assertTrue(file.exists())
        assertEquals(AppLanguage.EN, AppSettingsStore.inFiles(folder.root).load().language)
    }

    @Test
    fun `yetishmayotgan papka yasab beriladi`() {
        // Birinchi ishga tushirishda `filesDir` bo'lmasligi mumkin.
        val nested = File(folder.root, "a/b/c")
        val file = File(nested, "s.properties")

        store(file).save(AppSettings(language = AppLanguage.RU, simplified = false))

        assertEquals(AppLanguage.RU, store(file).load().language)
    }
}
