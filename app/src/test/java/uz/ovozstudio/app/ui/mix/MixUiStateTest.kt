package uz.ovozstudio.app.ui.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Aralashtirish ekranining hisoblanadigan holati.
 *
 * Ekranda ko'rinadigan ikki raqam shu yerda hisoblanadi: aralashma uzunligi
 * va «Aralashtirish» tugmasi yoqilganmi. Ikkisi ham jim xato qilishi mumkin —
 * o'chirilgan yo'l uzunlikni cho'zib qo'ysa, foydalanuvchi bor bo'lmagan
 * jimlikni kutardi.
 */
class MixUiStateTest {

    private fun track(
        name: String,
        offset: String = "0",
        durationMs: Long = 1_000,
        muted: Boolean = false,
        solo: Boolean = false,
        missing: Boolean = false,
    ) = MixTrackUi(
        name = name,
        file = "$name.wav",
        durationMs = durationMs,
        sampleRate = 48_000,
        channels = 1,
        gainText = "0",
        panText = "0",
        offsetText = offset,
        muted = muted,
        solo = solo,
        missing = missing,
    )

    @Test
    fun `uzunlik eng uzoqqa chozilgan yol boyicha`() {
        val state = MixUiState(
            tracks = listOf(
                track("birinchi", offset = "0", durationMs = 1_000),
                track("ikkinchi", offset = "500", durationMs = 2_000),
            ),
        )
        assertEquals(2_500L, state.totalMs)
    }

    @Test
    fun `ochirilgan yol uzunlikni belgilamaydi`() {
        // Uzoq, lekin jim turadigan yo'l aralashmani cho'zmasligi kerak:
        // natijada o'sha joyda faqat jimlik eshitilardi.
        val state = MixUiState(
            tracks = listOf(
                track("qisqa", durationMs = 1_000),
                track("jim", offset = "5000", durationMs = 5_000, muted = true),
            ),
        )
        assertEquals(1_000L, state.totalMs)
    }

    @Test
    fun `yakka rejim faqat tanlanganini hisoblaydi`() {
        val state = MixUiState(
            tracks = listOf(
                track("uzun", durationMs = 9_000),
                track("yakka", offset = "300", durationMs = 1_000, solo = true),
            ),
        )
        assertEquals(1_300L, state.totalMs)
    }

    @Test
    fun `buzuq siljish maydoni nol deb oqiladi`() {
        // Maydon yozib turgan paytda bo'sh bo'lishi mumkin — uzunlik
        // sakrab ketmasligi kerak.
        val state = MixUiState(tracks = listOf(track("birinchi", offset = "")))
        assertEquals(1_000L, state.totalMs)
    }

    @Test
    fun `yolsiz aralashtirib bolmaydi`() {
        assertFalse(MixUiState().canMix)
    }

    @Test
    fun `manba topilmasa aralashtirib bolmaydi`() {
        // Jim qoldirilgan yo'l aralashmadan tushib qolardi, ya'ni natija
        // foydalanuvchi kutganidan boshqa bo'lardi.
        val state = MixUiState(
            tracks = listOf(track("bor"), track("yoq", missing = true)),
        )
        assertFalse(state.canMix)
    }

    @Test
    fun `ishlayotganda tugma bosilmaydi`() {
        val state = MixUiState(tracks = listOf(track("birinchi")), busy = true)
        assertFalse(state.canMix)
    }

    @Test
    fun `tayyor yol bilan aralashtirish mumkin`() {
        val state = MixUiState(tracks = listOf(track("birinchi")), canUndo = true)
        assertTrue(state.canMix)
        assertTrue(state.canUndo)
    }

    @Test
    fun `kichik tushirish ekranda aytilmaydi`() {
        // To'liq shkaladagi bitta yo'l ham 0.999 ga tushadi: bu 0.01 dB,
        // ya'ni «0 dB tushirildi» degan ma'nosiz xabar berardi.
        assertFalse(MixUiState(appliedGainDb = -0.0087f).limited)
        assertFalse(MixUiState(appliedGainDb = 0f).limited)
        assertTrue(MixUiState(appliedGainDb = -0.1f).limited)
        assertTrue(MixUiState(appliedGainDb = -4.08f).limited)
    }
}
