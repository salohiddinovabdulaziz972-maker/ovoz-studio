package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatSupportTest {

    private fun fmt(
        container: AudioContainer,
        codec: AudioCodec,
        rate: Int = 44_100,
        channels: Int = 2,
        depth: Int? = null,
        bitrate: Int? = null,
    ) = AudioFormat(container, codec, rate, channels, depth, bitrate)

    @Test
    fun `wma ochilmaydi qolganlari ochiladi`() {
        assertFalse(FormatSupport.canDecode(AudioContainer.WMA, AudioCodec.WMA))
        assertTrue(FormatSupport.canDecode(AudioContainer.WAV, AudioCodec.PCM))
        assertTrue(FormatSupport.canDecode(AudioContainer.FLAC, AudioCodec.FLAC))
        assertTrue(FormatSupport.canDecode(AudioContainer.MP3, AudioCodec.MP3))
        assertTrue(FormatSupport.canDecode(AudioContainer.M4A, AudioCodec.AAC))
        assertTrue(FormatSupport.canDecode(AudioContainer.OGG, AudioCodec.VORBIS))
        assertTrue(FormatSupport.canDecode(AudioContainer.OGG, AudioCodec.OPUS))
    }

    @Test
    fun `oz kodlovchilarimiz har qanday versiyada ishlaydi`() {
        for (api in listOf(24, 26, 29, 34)) {
            assertTrue(FormatSupport.canEncode(AudioContainer.WAV, AudioCodec.PCM, api))
            assertTrue(FormatSupport.canEncode(AudioContainer.FLAC, AudioCodec.FLAC, api))
            assertTrue(FormatSupport.canEncode(AudioContainer.MP3, AudioCodec.MP3, api))
        }
    }

    @Test
    fun `vorbis va wma hech qachon kodlanmaydi`() {
        for (api in listOf(24, 29, 34)) {
            assertFalse(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.VORBIS, api))
            assertFalse(FormatSupport.canEncode(AudioContainer.WMA, AudioCodec.WMA, api))
        }
    }

    @Test
    fun `opus faqat api 29 dan boshlab kodlanadi`() {
        assertFalse(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.OPUS, 28))
        assertTrue(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.OPUS, 29))
    }

    @Test
    fun `aac mp4 da kodlanadi ogg da yoq`() {
        assertTrue(FormatSupport.canEncode(AudioContainer.M4A, AudioCodec.AAC, 24))
        assertTrue(FormatSupport.canEncode(AudioContainer.AAC, AudioCodec.AAC, 24))
        assertFalse(FormatSupport.canEncode(AudioContainer.OGG, AudioCodec.AAC, 34))
    }

    @Test
    fun `manba formati saqlanadi mp3 uchun`() {
        val source = fmt(AudioContainer.MP3, AudioCodec.MP3, bitrate = 192_000)
        val decision = FormatSupport.resolve(source, apiLevel = 24)
        assertEquals(ExportDecision.Preserved(source), decision)
    }

    @Test
    fun `manba formati saqlanadi flac uchun`() {
        val source = fmt(AudioContainer.FLAC, AudioCodec.FLAC, rate = 96_000, channels = 1, depth = 24)
        assertEquals(ExportDecision.Preserved(source), FormatSupport.resolve(source, apiLevel = 26))
    }

    @Test
    fun `ogg vorbis uchun chekinish taklif qilinadi`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.VORBIS, rate = 48_000, channels = 1)
        val decision = FormatSupport.resolve(source, apiLevel = 34)

        assertTrue("qaror chekinish bo'lishi kerak", decision is ExportDecision.Fallback)
        decision as ExportDecision.Fallback
        assertEquals(FallbackReason.NO_ENCODER, decision.reason)
        assertEquals(AudioContainer.FLAC, decision.recommended.container)
        // Namuna parametrlari o'zgarmasdan o'tadi.
        assertEquals(48_000, decision.recommended.sampleRate)
        assertEquals(1, decision.recommended.channels)
        assertTrue(decision.alternatives.any { it.container == AudioContainer.WAV })
        assertTrue(decision.alternatives.any { it.container == AudioContainer.M4A })
    }

    @Test
    fun `eski qurilmada opus uchun sabab boshqa`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.OPUS)
        val decision = FormatSupport.resolve(source, apiLevel = 26) as ExportDecision.Fallback
        assertEquals(FallbackReason.API_TOO_OLD, decision.reason)
    }

    @Test
    fun `wma uchun ochish sababi korsatiladi`() {
        val source = fmt(AudioContainer.WMA, AudioCodec.WMA)
        val decision = FormatSupport.resolve(source, apiLevel = 34) as ExportDecision.Fallback
        assertEquals(FallbackReason.NO_DECODER, decision.reason)
    }

    @Test
    fun `ochiq tanlov chekinishni bekor qiladi`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.VORBIS)
        val chosen = fmt(AudioContainer.M4A, AudioCodec.AAC, bitrate = 128_000)
        assertEquals(ExportDecision.Preserved(chosen), FormatSupport.resolve(source, 34, override = chosen))
    }

    @Test
    fun `yoqotishsiz manbaning bit chuqurligi saqlanadi`() {
        val source = fmt(AudioContainer.OGG, AudioCodec.VORBIS, depth = null)
        val decision = FormatSupport.resolve(source, 34) as ExportDecision.Fallback
        assertEquals(16, decision.recommended.bitDepth)
    }

    @Test
    fun `bit tezligi standarti kanal soniga qarab`() {
        assertEquals(128_000, FormatSupport.defaultBitrate(AudioCodec.AAC, 2))
        assertEquals(96_000, FormatSupport.defaultBitrate(AudioCodec.AAC, 1))
        assertEquals(0, FormatSupport.defaultBitrate(AudioCodec.FLAC, 2))
    }

    @Test
    fun `fayl nomi konteyner kengaytmasini oladi`() {
        assertEquals("ovoz.flac", fmt(AudioContainer.FLAC, AudioCodec.FLAC).fileName("ovoz"))
        assertEquals("ovoz.m4a", fmt(AudioContainer.M4A, AudioCodec.AAC).fileName("ovoz"))
    }

    @Test
    fun `import qobiliyati dekodlashga tayanadi`() {
        assertTrue(FormatSupport.canImport(DetectedFormat(AudioContainer.FLAC, AudioCodec.FLAC)))
        assertFalse(FormatSupport.canImport(DetectedFormat(AudioContainer.WMA, AudioCodec.WMA)))
    }
}
