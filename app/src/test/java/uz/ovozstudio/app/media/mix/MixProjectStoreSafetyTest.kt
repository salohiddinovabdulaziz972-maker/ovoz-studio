package uz.ovozstudio.app.media.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Loyiha faylining xavfsizligi: yozish uzilishi va buzuq son.
 *
 * Oddiy saqlash-qaytarish `MixProjectStoreTest` da.
 */
class MixProjectStoreSafetyTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test(timeout = 10_000)
    fun `ulkan tracks qiymati ochilishni osib qoymaydi`() {
        // `tracks=2000000000` yozilgan buzuq fayl millionlab bo'sh qidiruv
        // bilan ochilishni osib qo'yardi.
        val file = File(folder.root, "loyiha.properties")
        file.writeText("tracks=2000000000\n0.name=Ovoz\n0.file=ovoz.wav\n")

        val project = MixProjectStore(file).load()

        assertEquals(listOf("Ovoz"), project.tracks.map { it.name })
    }

    @Test
    fun `saqlashdan keyin vaqtinchalik fayl qolmaydi`() {
        val file = File(folder.root, "loyiha.properties")
        val store = MixProjectStore(file)

        store.save(MixProject())
        store.save(MixProject(masterGainDb = 3f))

        assertEquals(listOf("loyiha.properties"), folder.root.list()!!.toList())
        assertEquals(3f, store.load().masterGainDb, 0.001f)
    }

    @Test
    fun `saqlab bolmasa vaqtinchalik fayl qolmaydi`() {
        // Maqsad joyda bo'sh bo'lmagan papka turibdi: almashtirish
        // o'xshamaydi va xato beradi. Yarim yozilgan fayl qolmasligi kerak.
        val target = folder.newFolder("loyiha.properties")
        File(target, "ichida.txt").writeText("x")

        assertThrows(IOException::class.java) {
            MixProjectStore(target).save(MixProject())
        }

        assertEquals(listOf("loyiha.properties"), folder.root.list()!!.toList())
    }
}
