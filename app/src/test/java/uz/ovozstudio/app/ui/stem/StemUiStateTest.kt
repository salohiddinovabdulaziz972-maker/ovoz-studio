package uz.ovozstudio.app.ui.stem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.dsp.StemSeparator

/**
 * Ajratish ekrani holatining hisob-kitobi.
 *
 * Ekranning o'zi (Compose) kompilyatsiya bilan tekshiriladi; bu yerda esa
 * **qarorlar** tekshiriladi: qaysi rejimda kuch ishlatiladi, qachon manba
 * «deyarli mono» hisoblanadi, kiritilgan matn qanday sozlamaga aylanadi.
 */
class StemUiStateTest {

    private fun stereo(channels: Int = 2) = WavInfo(
        sampleRate = 48_000,
        channels = channels,
        bitsPerSample = 16,
        dataOffset = 44,
        dataSize = 48_000L * channels * 2,
    )

    @Test
    fun `standart rejim ajratish`() {
        // Standart — qismiy ajratish: u keng yozilgan cholg'uni saqlaydi,
        // ya'ni birinchi urinishda natija yumshoqroq bo'ladi.
        assertEquals(StemSeparator.Mode.SPLIT, StemUiState().mode)
    }

    @Test
    fun `standart matn songa aylanadi`() {
        val settings = StemUiState().settings
        assertEquals(StemSeparator.DEFAULT_STRENGTH, settings.strength, 1e-9)
        assertEquals(StemSeparator.Mode.SPLIT, settings.mode)
    }

    @Test
    fun `kuch faqat ajratish rejimida ishlatiladi`() {
        assertTrue(StemUiState(mode = StemSeparator.Mode.SPLIT).strengthUsed)
        assertFalse(StemUiState(mode = StemSeparator.Mode.REMOVE_VOCALS).strengthUsed)
    }

    @Test
    fun `aniq ayirishda kuch sozlamaga tasir qilmaydi`() {
        // Rejim markazni butunlay oladi: kiritilgan son natijaga ta'sir
        // qilmaydi. Ekran buni maydonni o'chirib aytadi, shuning uchun
        // sozlama ham baribir yaroqli bo'lishi kerak (DSP uni rad etmaydi).
        val settings = StemUiState(
            mode = StemSeparator.Mode.REMOVE_VOCALS,
            strength = "4.0",
        ).settings
        assertEquals(StemSeparator.Mode.REMOVE_VOCALS, settings.mode)
        assertTrue(settings.isValid())
    }

    @Test
    fun `chegaradan katta son chegaraga qisiladi`() {
        val settings = StemUiState(strength = "9.5").settings
        assertEquals(StemSeparator.MAX_STRENGTH, settings.strength, 1e-9)
        assertTrue(settings.isValid())
    }

    @Test
    fun `bosh matn standart kuchni beradi`() {
        val settings = StemUiState(strength = "").settings
        assertEquals(StemSeparator.DEFAULT_STRENGTH, settings.strength, 1e-9)
    }

    @Test
    fun `nuqta ham vergul ham ozlashtiriladi`() {
        assertEquals(2.5, StemUiState(strength = "2,5").settings.strength, 1e-9)
        assertEquals(2.5, StemUiState(strength = "2.5").settings.strength, 1e-9)
    }

    @Test
    fun `bitta kanalli manba belgilanadi`() {
        assertTrue(StemUiState(info = stereo(channels = 1)).notStereo)
        assertFalse(StemUiState(info = stereo()).notStereo)
        // Fayl hali tanlanmagan: «stereo emas» deb aytishga asos yo'q.
        assertFalse(StemUiState().notStereo)
    }

    @Test
    fun `kuchsiz yon qism deyarli mono deb belgilanadi`() {
        val threshold = StemUiState.NEARLY_MONO_DB
        assertTrue(StemUiState(sideToMidDb = threshold).nearlyMono)
        assertTrue(StemUiState(sideToMidDb = threshold - 0.1).nearlyMono)
        assertTrue(StemUiState(sideToMidDb = -120.0).nearlyMono)
        assertFalse(StemUiState(sideToMidDb = threshold + 0.1).nearlyMono)
        // Kuchli yon qism (keng stereo) — ogohlantirish yo'q.
        assertFalse(StemUiState(sideToMidDb = 3.0).nearlyMono)
    }

    @Test
    fun `natija boshlanishida ogohlantirish yonmaydi`() {
        // `sideToMidDb` nol — hali o'lchanmagan qiymat, «yaxshi natija»
        // emas. Shunga qaramay ogohlantirish faqat o'lchovdan keyin
        // ko'rinadi: natija bloki o'sha paytda chiqadi.
        assertFalse(StemUiState().nearlyMono)
    }

    @Test
    fun `uzunlik fayldan olinadi`() {
        val info = stereo()
        assertEquals(info.durationMs, StemUiState(info = info).durationMs)
        assertEquals(0L, StemUiState().durationMs)
    }
}
