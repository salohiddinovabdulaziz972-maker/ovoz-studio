package uz.ovozstudio.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Foydalanuvchi faylini himoya qiluvchi tekshiruv sinovlari.
 *
 * Bu tekshiruv noto'g'ri `true` bersa, ilova foydalanuvchining haqiqiy
 * faylini o'chirib yuborishi mumkin — shuning uchun chegaraviy holatlar
 * alohida sinaladi.
 */
class FilePathsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `papka ichidagi fayl ichkarida hisoblanadi`() {
        val work = folder.newFolder("ish")
        val inside = File(work, "manba.wav").apply { writeText("x") }
        assertTrue(FilePaths.isInside(inside, work))
    }

    @Test
    fun `ichma-ich papkadagi fayl ham ichkarida`() {
        val work = folder.newFolder("ish")
        val nested = File(File(work, "tahrir").apply { mkdirs() }, "a.wav").apply { writeText("x") }
        assertTrue(FilePaths.isInside(nested, work))
    }

    @Test
    fun `papkadan tashqaridagi fayl ichkarida emas`() {
        val work = folder.newFolder("ish")
        val outside = folder.newFile("musiqa.mp3")
        assertFalse(FilePaths.isInside(outside, work))
    }

    @Test
    fun `bir xil boshlanadigan qoshni papka ichkarida emas`() {
        val work = folder.newFolder("ish")
        val neighbour = folder.newFolder("ish-boshqa")
        val file = File(neighbour, "a.wav").apply { writeText("x") }
        assertFalse(FilePaths.isInside(file, work))
    }

    @Test
    fun `nuqta-nuqta orqali chiqib ketgan yol ichkarida emas`() {
        val work = folder.newFolder("ish")
        folder.newFile("musiqa.mp3")
        val sneaky = File(work, "../musiqa.mp3")
        assertFalse(FilePaths.isInside(sneaky, work))
    }

    @Test
    fun `papkaning ozi ichkarida hisoblanmaydi`() {
        val work = folder.newFolder("ish")
        assertFalse(FilePaths.isInside(work, work))
    }
}
