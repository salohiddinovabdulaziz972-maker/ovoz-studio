package uz.ovozstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * «Yo'q yoki to'liq» yozuv: yozish uzilsa, eski fayl butun qoladi.
 *
 * Oddiy `outputStream()` faylni avval bo'shatardi — bu sinovlarning asosiy
 * tekshiruvi aynan shu farq.
 */
class AtomicFileWriterTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `yangi fayl yoziladi`() {
        val file = File(folder.root, "yangi.txt")

        AtomicFileWriter.write(file) { it.write("salom".toByteArray()) }

        assertEquals("salom", file.readText())
    }

    @Test
    fun `mavjud fayl almashtiriladi`() {
        val file = File(folder.root, "sozlama.txt")
        file.writeText("eski")

        AtomicFileWriter.write(file) { it.write("yangi".toByteArray()) }

        assertEquals("yangi", file.readText())
    }

    @Test
    fun `yozish yiqilsa eski fayl butun qoladi`() {
        val file = File(folder.root, "eski.txt")
        file.writeText("eski ma'lumot")

        assertThrows(IOException::class.java) {
            AtomicFileWriter.write(file) { output ->
                output.write("yangi, lekin yarim".toByteArray())
                throw IOException("joy yetmadi")
            }
        }

        assertEquals("eski ma'lumot", file.readText())
    }

    @Test
    fun `yozish yiqilsa vaqtinchalik fayl qolmaydi`() {
        val file = File(folder.root, "eski.txt")
        file.writeText("eski")

        assertThrows(IOException::class.java) {
            AtomicFileWriter.write(file) { throw IOException("joy yetmadi") }
        }

        assertEquals(listOf("eski.txt"), folder.root.list()!!.toList())
    }

    @Test
    fun `yozish muvaffaqiyatli bolsa vaqtinchalik fayl qolmaydi`() {
        val file = File(folder.root, "sozlama.txt")

        AtomicFileWriter.write(file) { it.write(1) }

        assertEquals(listOf("sozlama.txt"), folder.root.list()!!.toList())
    }

    @Test
    fun `yoq papka yaratiladi`() {
        val file = File(folder.root, "ichki/chuqur/fayl.txt")

        AtomicFileWriter.write(file) { it.write(1) }

        assertEquals(1L, file.length())
    }
}
