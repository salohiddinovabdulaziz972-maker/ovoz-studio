package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AudioFormatDetectorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun header(vararg parts: ByteArray): ByteArray =
        parts.fold(ByteArray(0)) { acc, part -> acc + part }

    private fun ascii(text: String) = text.toByteArray(Charsets.US_ASCII)

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun `riff wav pcm aniqlanadi`() {
        val h = header(ascii("RIFF"), bytes(0, 0, 0, 0), ascii("WAVE"), ascii("fmt "), bytes(16, 0, 0, 0), bytes(1, 0))
        assertEquals(DetectedFormat(AudioContainer.WAV, AudioCodec.PCM), AudioFormatDetector.detect(h))
    }

    @Test
    fun `riff lekin avi rad etiladi`() {
        val h = header(ascii("RIFF"), bytes(0, 0, 0, 0), ascii("AVI "))
        assertNull(AudioFormatDetector.detect(h))
    }

    @Test
    fun `pcm bolmagan wav rad etiladi`() {
        // Kodlash turi 0x0002 (ADPCM) — o'quvchi buni qabul qilmaydi.
        val h = header(ascii("RIFF"), bytes(0, 0, 0, 0), ascii("WAVE"), ascii("fmt "), bytes(16, 0, 0, 0), bytes(2, 0))
        assertNull(AudioFormatDetector.detect(h))
    }

    @Test
    fun `flac aniqlanadi`() {
        val h = header(ascii("fLaC"), ByteArray(40))
        assertEquals(DetectedFormat(AudioContainer.FLAC, AudioCodec.FLAC), AudioFormatDetector.detect(h))
    }

    @Test
    fun `ogg ichidagi opus aniqlanadi`() {
        val h = header(ascii("OggS"), ByteArray(22), bytes(1, 0), ascii("OpusHead"), ByteArray(16))
        assertEquals(DetectedFormat(AudioContainer.OGG, AudioCodec.OPUS), AudioFormatDetector.detect(h))
    }

    @Test
    fun `ogg ichidagi vorbis aniqlanadi`() {
        val h = header(ascii("OggS"), ByteArray(22), bytes(1, 0), bytes(0x01), ascii("vorbis"), ByteArray(16))
        assertEquals(DetectedFormat(AudioContainer.OGG, AudioCodec.VORBIS), AudioFormatDetector.detect(h))
    }

    @Test
    fun `ogg lekin kodeksiz rad etiladi`() {
        val h = header(ascii("OggS"), ByteArray(56))
        assertNull(AudioFormatDetector.detect(h))
    }

    @Test
    fun `wma guid aniqlanadi`() {
        val h = header(bytes(0x30, 0x26, 0xB2, 0x75, 0x8E, 0x66, 0xCF, 0x11, 0xA6, 0xD9, 0x00, 0xAA, 0x00, 0x62, 0xCE, 0x6C), ByteArray(8))
        assertEquals(DetectedFormat(AudioContainer.WMA, AudioCodec.WMA), AudioFormatDetector.detect(h))
    }

    @Test
    fun `m4a aniqlanadi`() {
        val h = header(bytes(0, 0, 0, 0x20), ascii("ftyp"), ascii("M4A "), ByteArray(24))
        assertEquals(DetectedFormat(AudioContainer.M4A, AudioCodec.AAC), AudioFormatDetector.detect(h))
    }

    @Test
    fun `id3 teg mp3 deb aniqlanadi`() {
        val h = header(ascii("ID3"), bytes(4, 0, 0), ByteArray(48))
        assertEquals(DetectedFormat(AudioContainer.MP3, AudioCodec.MP3), AudioFormatDetector.detect(h))
    }

    @Test
    fun `mpeg1 layer3 kadri mp3 aniqlanadi`() {
        // 0xFF 0xFB — MPEG1 Layer III, himoyasiz.
        assertEquals(DetectedFormat(AudioContainer.MP3, AudioCodec.MP3), AudioFormatDetector.detect(bytes(0xFF, 0xFB, 0x90, 0x00)))
    }

    @Test
    fun `crcli mpeg kadr ham mp3 aniqlanadi`() {
        assertEquals(DetectedFormat(AudioContainer.MP3, AudioCodec.MP3), AudioFormatDetector.detect(bytes(0xFF, 0xFA, 0x90, 0x00)))
    }

    @Test
    fun `adts aac mp3 emas deb aniqlanadi`() {
        // 0xFF 0xF1 — layer bitlari 00, ya'ni ADTS AAC.
        assertEquals(DetectedFormat(AudioContainer.AAC, AudioCodec.AAC), AudioFormatDetector.detect(bytes(0xFF, 0xF1, 0x50, 0x80)))
    }

    @Test
    fun `zahira versiya rad etiladi`() {
        // Versiya bitlari 01 — MPEG'da bu zahira qiymat, kadr boshi emas.
        assertNull(AudioFormatDetector.detect(bytes(0xFF, 0xEB, 0x90, 0x00)))
    }

    @Test
    fun `tasodifiy baytlar rad etiladi`() {
        assertNull(AudioFormatDetector.detect(bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B)))
    }

    @Test
    fun `bir baytli fayl rad etiladi`() {
        assertNull(AudioFormatDetector.detect(bytes(0xFF)))
    }

    @Test
    fun `ikki baytlik mpeg sinxronizatsiyasi taniladi`() {
        // MPEG kadr boshi uchun ikki bayt yetarli — bu eng qisqa imzo.
        assertEquals(DetectedFormat(AudioContainer.MP3, AudioCodec.MP3), AudioFormatDetector.detect(bytes(0xFF, 0xFB)))
    }

    @Test
    fun `fayldan sarlavha oqiladi`() {
        val file = File(folder.root, "sinov.flac")
        file.writeBytes(header(ascii("fLaC"), ByteArray(100)))
        assertEquals(DetectedFormat(AudioContainer.FLAC, AudioCodec.FLAC), AudioFormatDetector.detect(file))
    }

    @Test
    fun `fayldan oqish qisqa faylda ham ishlaydi`() {
        val file = File(folder.root, "kichik.wav")
        file.writeBytes(ascii("RIFF"))
        // Sarlavha uchun bayt yetmaydi — shunchaki yiqilmasligi kerak.
        assertNull(AudioFormatDetector.detect(file))
    }

    @Test
    fun `kengaytma boyicha topish`() {
        assertEquals(AudioContainer.MP3, AudioContainer.fromExtension(".MP3"))
        assertEquals(AudioContainer.M4A, AudioContainer.fromExtension("m4a"))
        assertEquals(AudioContainer.OPUS, AudioContainer.fromExtension(" opus "))
        assertNull(AudioContainer.fromExtension("txt"))
        assertNull(AudioContainer.fromExtension(null))
    }
}
