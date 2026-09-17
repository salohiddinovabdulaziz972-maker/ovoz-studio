package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodecRatesTest {

    @Test
    fun `aac jadvali ADTS tartibida`() {
        // Indekslar ADTS sarlavhasiga yoziladi, shuning uchun tartib
        // spetsifikatsiyadagi bilan bir xil bo'lishi shart.
        assertEquals(0, CodecRates.aacIndex(96_000))
        assertEquals(3, CodecRates.aacIndex(48_000))
        assertEquals(4, CodecRates.aacIndex(44_100))
        assertEquals(11, CodecRates.aacIndex(8_000))
        assertEquals(-1, CodecRates.aacIndex(47_000))
        assertEquals(13, CodecRates.AAC.size)
    }

    @Test
    fun `opus faqat beshta chastotani biladi`() {
        for (rate in intArrayOf(8_000, 12_000, 16_000, 24_000, 48_000)) {
            assertTrue("$rate", CodecRates.supports(AudioCodec.OPUS, rate))
        }
        // Eng ko'p uchraydigan chastota — aynan shu ishlamaydi.
        assertFalse(CodecRates.supports(AudioCodec.OPUS, 44_100))
        assertFalse(CodecRates.supports(AudioCodec.OPUS, 96_000))
    }

    @Test
    fun `oz kodlovchilarimiz har qanday chastotada ishlaydi`() {
        assertTrue(CodecRates.supports(AudioCodec.PCM, 47_000))
        assertTrue(CodecRates.supports(AudioCodec.FLAC, 47_000))
        assertTrue(CodecRates.supports(AudioCodec.MP3, 47_000))
    }

    @Test
    fun `ADTS sarlavhasi bilan bir xil jadval`() {
        // Ikki joyda ikki xil jadval bo'lsa, sarlavha noto'g'ri indeks
        // yozib, fayl jimgina buzilardi.
        for (rate in CodecRates.AAC) {
            assertEquals(rate, AdtsHeader.samplingIndex(rate).let { CodecRates.AAC[it] })
            assertTrue(AdtsHeader.supports(rate))
        }
    }
}
