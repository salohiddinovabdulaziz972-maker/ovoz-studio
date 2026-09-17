package uz.ovozstudio.app.media.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Loyihani tahrirlash va orqaga qaytarish.
 *
 * Tarix — foydalanuvchining xatosidan himoya: yo'lni o'chirib qo'ysa,
 * orqaga qaytishi kerak. Shuning uchun tekshiruvlar aynan shu ikki savolga
 * javob beradi: holat to'g'ri o'zgaryaptimi va orqaga qaytish uni **aynan**
 * tiklayaptimi.
 */
class MixEditorTest {

    @Test
    fun `qoshilgan yol loyihada qoladi`() {
        val editor = MixEditor()
        assertTrue(editor.add(MixTrack("birinchi")))
        assertTrue(editor.add(MixTrack("ikkinchi")))

        assertEquals(listOf("birinchi", "ikkinchi"), editor.project.tracks.map { it.name })
    }

    @Test
    fun `orqaga qaytarish oldingi holatni tiklaydi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))
        editor.add(MixTrack("ikkinchi"))

        assertTrue(editor.undo())
        assertEquals(listOf("birinchi"), editor.project.tracks.map { it.name })

        assertTrue(editor.undo())
        assertTrue(editor.project.isEmpty)

        // Tarixning boshida orqaga qaytarish yo'q.
        assertFalse(editor.undo())
    }

    @Test
    fun `qaytarib qoyish orqaga qaytarishning teskarisi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))
        editor.undo()

        assertTrue(editor.canRedo)
        assertTrue(editor.redo())
        assertEquals(listOf("birinchi"), editor.project.tracks.map { it.name })
        assertFalse(editor.canRedo)
        assertTrue(editor.canUndo)
    }

    @Test
    fun `yangi ozgarish kelajakni tozalaydi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))
        editor.add(MixTrack("ikkinchi"))
        editor.undo()

        assertTrue(editor.canRedo)
        editor.add(MixTrack("uchinchi"))

        // Orqaga qaytarish chizig'i uzildi: «qaytarib qo'yish» endi
        // bo'lmagan holatga olib kelardi.
        assertFalse(editor.canRedo)
        assertEquals(listOf("birinchi", "uchinchi"), editor.project.tracks.map { it.name })
    }

    @Test
    fun `tarix chegarasi oshib ketmaydi`() {
        val editor = MixEditor(historyLimit = 3)
        repeat(5) { index -> editor.add(MixTrack("yol-$index")) }

        var steps = 0
        while (editor.undo()) steps++
        assertEquals(3, steps)
    }

    @Test
    fun `chegaradan kop yol qoshilmaydi`() {
        val editor = MixEditor()
        repeat(MixProject.MAX_TRACKS) { index -> assertTrue(editor.add(MixTrack("yol-$index"))) }

        assertFalse(editor.add(MixTrack("ortiqcha")))
        assertEquals(MixProject.MAX_TRACKS, editor.project.tracks.size)
    }

    @Test
    fun `muvaffaqiyatsiz amal tarixga yozilmaydi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))
        editor.undo()
        val before = editor.project

        assertFalse(editor.remove(5))
        assertFalse(editor.update(5) { it.copy(gainDb = 3f) })
        assertEquals(before, editor.project)
        assertFalse(editor.canUndo)
    }

    @Test
    fun `ozgarmagan qiymat yozilmaydi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))
        val historyBefore = editor.canUndo

        // Aynan o'sha qiymat qaytarilsa — o'zgarish yo'q, tarix ham
        // o'smasligi kerak: aks holda «orqaga» tugmasi hech narsani
        // o'zgartirmaydigan qadamlar bilan to'lib ketardi.
        assertFalse(editor.update(0) { it })
        assertTrue(historyBefore)
        assertTrue(editor.undo())
        assertTrue(editor.project.isEmpty)
    }

    @Test
    fun `yolni ozgartirish saqlanadi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))

        assertTrue(editor.update(0) { it.copy(gainDb = -6f, pan = 0.5f, muted = true) })
        val track = editor.project.tracks.first()
        assertEquals(-6f, track.gainDb, 0f)
        assertEquals(0.5f, track.pan, 0f)
        assertTrue(track.muted)
    }

    @Test
    fun `umumiy balandlik chegaralanadi`() {
        val editor = MixEditor()
        assertTrue(editor.setMaster(100f))
        assertEquals(MixTrack.MAX_GAIN_DB, editor.project.masterGainDb, 0f)

        assertTrue(editor.setMaster(-100f))
        assertEquals(MixTrack.MIN_GAIN_DB, editor.project.masterGainDb, 0f)

        // Chegaradagi qiymat o'zgarmaydi — tarix ham o'smaydi.
        assertFalse(editor.setMaster(MixTrack.MIN_GAIN_DB))
    }

    @Test
    fun `tiklash tarixni tozalaydi`() {
        val editor = MixEditor()
        editor.add(MixTrack("birinchi"))
        editor.reset(MixProject(tracks = listOf(MixTrack("saqlangandan"))))

        assertFalse(editor.canUndo)
        assertFalse(editor.canRedo)
        assertEquals(listOf("saqlangandan"), editor.project.tracks.map { it.name })
    }

    @Test
    fun `umumiy balandlik chiziqli koeffitsientga ogiriladi`() {
        // −6 dB — yarim amplituda; bu yerda 0.5012 (aniq formula).
        val project = MixProject(masterGainDb = -6f)
        assertEquals(dbToGain(-6f), project.masterGain, 1e-6f)
    }
}
