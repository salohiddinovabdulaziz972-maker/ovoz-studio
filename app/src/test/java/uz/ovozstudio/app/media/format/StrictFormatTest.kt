package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Qat'iy format qoidasi: natija aynan yuklangan formatda yoziladi, yozib
 * bo'lmasa fayl ish boshlanmasdan oldin rad etiladi.
 */
class StrictFormatTest {

    private fun fmt(
        container: AudioContainer,
        codec: AudioCodec,
        rate: Int = 44_100,
    ) = AudioFormat(container, codec, rate, 2, 16, null)

    @Test
    fun `format nomi konteyner va kodekdan yasaladi`() {
        assertEquals("MP3", StrictFormat.label(AudioContainer.MP3, AudioCodec.MP3))
        assertEquals("WAV", StrictFormat.label(AudioContainer.WAV, AudioCodec.PCM))
        assertEquals("FLAC", StrictFormat.label(AudioContainer.FLAC, AudioCodec.FLAC))
        assertEquals("M4A (AAC)", StrictFormat.label(AudioContainer.M4A, AudioCodec.AAC))
        assertEquals("AAC", StrictFormat.label(AudioContainer.AAC, AudioCodec.AAC))
        assertEquals("OGG (Vorbis)", StrictFormat.label(AudioContainer.OGG, AudioCodec.VORBIS))
        assertEquals("OGG (Opus)", StrictFormat.label(AudioContainer.OGG, AudioCodec.OPUS))
    }

    @Test
    fun `fayl turi konteynerga mos`() {
        assertEquals("audio/mpeg", StrictFormat.mimeType(AudioContainer.MP3))
        assertEquals("audio/mp4", StrictFormat.mimeType(AudioContainer.M4A))
        assertEquals("audio/wav", StrictFormat.mimeType(AudioContainer.WAV))
        assertEquals("audio/flac", StrictFormat.mimeType(AudioContainer.FLAC))
        assertEquals("audio/ogg", StrictFormat.mimeType(AudioContainer.OGG))
        assertEquals("audio/ogg", StrictFormat.mimeType(AudioContainer.OPUS))
        assertEquals("audio/aac", StrictFormat.mimeType(AudioContainer.AAC))
    }

    @Test
    fun `yozib boladigan formatlar rad etilmaydi`() {
        assertNull(StrictFormat.blocker(fmt(AudioContainer.WAV, AudioCodec.PCM), 24))
        assertNull(StrictFormat.blocker(fmt(AudioContainer.FLAC, AudioCodec.FLAC), 24))
        assertNull(StrictFormat.blocker(fmt(AudioContainer.MP3, AudioCodec.MP3), 24))
        assertNull(StrictFormat.blocker(fmt(AudioContainer.M4A, AudioCodec.AAC), 24))
        assertNull(StrictFormat.blocker(fmt(AudioContainer.AAC, AudioCodec.AAC), 24))
    }

    @Test
    fun `vorbis yozib bolmaydi`() {
        assertEquals(
            FallbackReason.NO_ENCODER,
            StrictFormat.blocker(fmt(AudioContainer.OGG, AudioCodec.VORBIS), 34),
        )
    }

    @Test
    fun `wma ochilmaydi`() {
        assertEquals(
            FallbackReason.NO_DECODER,
            StrictFormat.blocker(fmt(AudioContainer.WMA, AudioCodec.WMA), 34),
        )
    }

    @Test
    fun `opus faqat api 29 dan va 48 kilogertsda yoziladi`() {
        val opus48 = fmt(AudioContainer.OGG, AudioCodec.OPUS, rate = 48_000)
        assertEquals(FallbackReason.API_TOO_OLD, StrictFormat.blocker(opus48, 28))
        assertNull(StrictFormat.blocker(opus48, 29))

        val opus44 = fmt(AudioContainer.OGG, AudioCodec.OPUS, rate = 44_100)
        assertEquals(FallbackReason.NO_ENCODER, StrictFormat.blocker(opus44, 34))
    }

    @Test
    fun `bit tezligi fayl hajmi va uzunligidan taxmin qilinadi`() {
        // 1 000 000 bayt / 100 soniya = 80 kbit/s.
        assertEquals(80_000, StrictFormat.estimateBitrate(AudioCodec.MP3, 44_100, 1_000_000, 100_000))
        // 1 600 000 bayt / 100 soniya = 128 kbit/s.
        assertEquals(128_000, StrictFormat.estimateBitrate(AudioCodec.AAC, 44_100, 1_600_000, 100_000))
    }

    @Test
    fun `bit tezligi kodek oraligiga keltiriladi`() {
        assertEquals(32_000, StrictFormat.estimateBitrate(AudioCodec.AAC, 44_100, 1_000, 100_000))
        assertEquals(256_000, StrictFormat.estimateBitrate(AudioCodec.AAC, 44_100, 100_000_000, 100_000))
    }

    @Test
    fun `mp3 bit tezligi chastotaga mos zinapoyaga tushadi`() {
        // 8 kHz da MP3 uchun eng ko'pi 64 kbit/s.
        assertEquals(64_000, StrictFormat.estimateBitrate(AudioCodec.MP3, 8_000, 4_000_000, 100_000))
    }

    @Test
    fun `yoqotishsiz format va noma'lum uzunlik uchun bit tezligi yoq`() {
        assertNull(StrictFormat.estimateBitrate(AudioCodec.FLAC, 44_100, 1_000_000, 100_000))
        assertNull(StrictFormat.estimateBitrate(AudioCodec.PCM, 44_100, 1_000_000, 100_000))
        assertNull(StrictFormat.estimateBitrate(AudioCodec.MP3, 44_100, 0, 100_000))
        assertNull(StrictFormat.estimateBitrate(AudioCodec.MP3, 44_100, 1_000_000, 0))
    }

    @Test
    fun `asl format konteyner va kodekni manbadan parametrlarni WAV dan oladi`() {
        val origin = StrictFormat.originOf(
            detected = DetectedFormat(AudioContainer.MP3, AudioCodec.MP3),
            decoded = AudioFormat(AudioContainer.WAV, AudioCodec.PCM, 44_100, 2, 16),
            sourceBytes = 1_000_000,
            durationMs = 100_000,
        )
        assertEquals(AudioContainer.MP3, origin.container)
        assertEquals(AudioCodec.MP3, origin.codec)
        assertEquals(44_100, origin.sampleRate)
        assertEquals(2, origin.channels)
        assertEquals(16, origin.bitDepth)
        assertEquals(80_000, origin.bitrate)
    }
}
