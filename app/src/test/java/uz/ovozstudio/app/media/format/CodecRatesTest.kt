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
    fun `pcm va flac har qanday chastotada ishlaydi`() {
        assertTrue(CodecRates.supports(AudioCodec.PCM, 47_000))
        assertTrue(CodecRates.supports(AudioCodec.FLAC, 47_000))
    }

    @Test
    fun `mp3 faqat oz jadvalidagi chastotalarda ishlaydi`() {
        for (rate in CodecRates.MP3) {
            assertTrue("$rate", CodecRates.supports(AudioCodec.MP3, rate))
        }
        // Sintezator beradigan past chastotalar jadvalda bor...
        assertTrue(CodecRates.supports(AudioCodec.MP3, 8_000))
        assertTrue(CodecRates.supports(AudioCodec.MP3, 16_000))
        // ...jadvaldan tashqarisi esa yo'q: LAME uni qabul qilmaydi.
        assertFalse(CodecRates.supports(AudioCodec.MP3, 47_000))
        assertFalse(CodecRates.supports(AudioCodec.MP3, 96_000))
    }

    @Test
    fun `mp3 bit tezligi chastota chegarasidan oshmaydi`() {
        // 8–12 kHz — MPEG-2.5: chegarasi 64 kbit/s.
        assertEquals(64_000, CodecRates.mp3Bitrate(8_000, 128_000))
        assertEquals(64_000, CodecRates.mp3Bitrate(12_000, 320_000))
        // 16–24 kHz — MPEG-2: chegarasi 160.
        assertEquals(160_000, CodecRates.mp3Bitrate(22_050, 320_000))
        assertEquals(128_000, CodecRates.mp3Bitrate(22_050, 128_000))
        // MPEG-1: to'liq zinapoya.
        assertEquals(128_000, CodecRates.mp3Bitrate(44_100, 128_000))
        assertEquals(320_000, CodecRates.mp3Bitrate(48_000, 500_000))
    }

    @Test
    fun `mp3 bit tezligi zinapoyadagi qiymatga tushadi`() {
        // LAME oradagi sonni bilmaydi — 100 kbit/s so'ralsa, 96 bo'ladi.
        assertEquals(96_000, CodecRates.mp3Bitrate(44_100, 100_000))
        // Judayam kichik so'rov ham zinapoyaning eng pastidan pastga tushmaydi.
        assertEquals(8_000, CodecRates.mp3Bitrate(44_100, 1_000))
    }

    @Test
    fun `mp3 bit tezligi chegaralari chastotaga qarab ozgaradi`() {
        assertEquals(64_000, CodecRates.mp3MaxBitrate(8_000))
        assertEquals(160_000, CodecRates.mp3MaxBitrate(16_000))
        assertEquals(320_000, CodecRates.mp3MaxBitrate(44_100))
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
