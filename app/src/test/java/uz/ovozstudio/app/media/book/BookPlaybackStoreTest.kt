package uz.ovozstudio.app.media.book

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qoldirilgan joyni eslab qolish.
 *
 * Asosiy talab — **yozuv hech qachon tinglashga xalaqit bermasligi**:
 * fayl buzuq, papka yo'q, nom g'alati — har qanday holatda pleyer ishlaydi,
 * eng yomoni boshidan boshlanadi. Shuning uchun bu yerdagi sinovlarning
 * ko'pi xato yo'llari haqida.
 */
class BookPlaybackStoreTest {

    private fun tempFile(name: String = "kitob-jarayon.properties"): File {
        val dir = File.createTempFile("ovozstudio-jarayon", "")
        dir.delete()
        dir.mkdirs()
        dir.deleteOnExit()
        return File(dir, name)
    }

    @Test
    fun `saqlangan joy qaytariladi`() {
        val store = BookPlaybackStore(tempFile())
        store.save("Mening kitobim", chapterIndex = 3, positionMs = 45_000, nowMs = 1_000)

        val progress = store.load("Mening kitobim")
        assertNotNull(progress)
        assertEquals(3, progress!!.chapterIndex)
        assertEquals(45_000, progress.positionMs)
    }

    @Test
    fun `yozuv qayta yozilganda eskisi qolmaydi`() {
        val store = BookPlaybackStore(tempFile())
        store.save("Kitob", 1, 10_000, 1_000)
        store.save("Kitob", 7, 20_000, 2_000)

        val progress = store.load("Kitob")!!
        assertEquals(7, progress.chapterIndex)
        assertEquals(20_000, progress.positionMs)
    }

    @Test
    fun `kitoblar bir-birini bosmaydi`() {
        val store = BookPlaybackStore(tempFile())
        store.save("Birinchi kitob", 1, 1_000, 1)
        store.save("Ikkinchi kitob", 2, 2_000, 2)

        assertEquals(1, store.load("Birinchi kitob")!!.chapterIndex)
        assertEquals(2, store.load("Ikkinchi kitob")!!.chapterIndex)
    }

    @Test
    fun `g'alati nomlar togri saqlanadi`() {
        // Kitob nomi fayl nomidan keladi: bo'shliq, ikki nuqta, tenglik
        // belgisi va kirill harflari bo'lishi mumkin.
        val store = BookPlaybackStore(tempFile())
        val nom = "Китоб: 2-qism = \"yangi\" (2026)"
        store.save(nom, 5, 6_000, 3)

        val progress = store.load(nom)
        assertNotNull("kalit ekranlanmagan", progress)
        assertEquals(5, progress!!.chapterIndex)
    }

    @Test
    fun `yozuv ochiriladi`() {
        val store = BookPlaybackStore(tempFile())
        store.save("Kitob", 1, 1_000, 1)
        store.clear("Kitob")

        assertNull(store.load("Kitob"))
        // Boshqa kitobga tegilmaydi.
        store.save("Boshqa", 2, 2_000, 2)
        store.clear("Kitob")
        assertNotNull(store.load("Boshqa"))
    }

    @Test
    fun `buzuq fayl boshidan boshlashga olib keladi`() {
        val file = tempFile()
        // Buzuq `\u` ketma-ketligi: `Properties.load` shunday faylni
        // o'qishdan bosh tortadi (bu — eng ko'p uchraydigan buzilish:
        // yozish paytida uzilib qolgan fayl).
        file.writeText("kitob=\\uZZZZ\n")
        val store = BookPlaybackStore(file)

        assertNull(store.load("Kitob"))
        // Va yozish baribir ishlaydi: buzilgan fayl ustiga yangisi yoziladi.
        store.save("Kitob", 4, 5_000, 6)
        assertEquals(4, BookPlaybackStore(file).load("Kitob")!!.chapterIndex)
    }

    @Test
    fun `buzuq qator otkazib yuboriladi`() {
        val file = tempFile()
        file.writeText("Kitob=12|34|56\nBuzuq=salom\n")
        val store = BookPlaybackStore(file)

        assertEquals(12, store.load("Kitob")!!.chapterIndex)
        assertNull("buzuq qator o'qilmasligi kerak", store.load("Buzuq"))
    }

    @Test
    fun `manfiy qiymat qabul qilinmaydi`() {
        val file = tempFile()
        file.writeText("Kitob=-1|500|1\n")
        assertNull(BookPlaybackStore(file).load("Kitob"))
    }

    @Test
    fun `papka mavjud bolmasa ham ishlaydi`() {
        // Birinchi ishga tushirishda papka hali yaratilmagan bo'ladi.
        val dir = File.createTempFile("ovozstudio-yangi", "")
        dir.delete()
        val store = BookPlaybackStore(File(dir, "ichki/kilob/jarayon.properties"))

        assertNull("papka yo'q — bu xato emas", store.load("Kitob"))
        store.save("Kitob", 2, 3_000, 4)
        assertEquals(2, store.load("Kitob")!!.chapterIndex)
    }

    @Test
    fun `eski yozuvlar chegaradan keyin tashlanadi`() {
        val file = tempFile()
        val store = BookPlaybackStore(file)
        // 60 kitob — chegaradan ko'p; eng eskisi birinchi bo'lib ketadi.
        for (i in 0 until 60) store.save("Kitob $i", i, 0, nowMs = i.toLong())

        assertNull("eng eski yozuv qolib ketdi", store.load("Kitob 0"))
        assertNotNull("eng yangi yozuv yo'q", store.load("Kitob 59"))
        assertTrue("fayl cheksiz o'sdi", file.length() < 20_000)
    }

    @Test
    fun `doska nomi bilan yozilmaydi`() {
        // Fayl yo'li o'rniga bo'sh nom kelishi mumkin (hujjat nomi
        // aniqlanmadi). Bunday yozuv boshqa kitoblarni bosib ketmasligi kerak.
        val store = BookPlaybackStore(tempFile())
        store.save("", 3, 3_000, 3)
        store.save("Kitob", 5, 5_000, 5)

        assertNull(store.load(""))
        assertEquals(5, store.load("Kitob")!!.chapterIndex)
    }
}
