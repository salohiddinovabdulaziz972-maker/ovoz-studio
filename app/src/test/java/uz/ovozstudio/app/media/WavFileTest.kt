package uz.ovozstudio.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WavFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `sarlavha va malumot mos keladi 16 bit mono`() {
        val file = write(rate = 44_100, channels = 1, depth = BitDepth.BIT_16, frames = 1_000)

        val info = WavFile.readInfo(file)
        assertEquals(44_100, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
        assertEquals(1_000L, info.frames)
        assertEquals(WavWriter.HEADER_SIZE + 1_000 * 2L, file.length())
        assertEquals(1_000L * 1000 / 44_100, info.durationMs)
    }

    @Test
    fun `sarlavha va malumot mos keladi 24 bit stereo`() {
        val file = write(rate = 48_000, channels = 2, depth = BitDepth.BIT_24, frames = 500)

        val info = WavFile.readInfo(file)
        assertEquals(48_000, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(24, info.bitsPerSample)
        assertEquals(500L, info.frames)
        assertEquals(WavWriter.HEADER_SIZE + 500 * 6L, file.length())
    }

    @Test
    fun `vaqt kadrga va orqaga togri ogiriladi`() {
        val file = write(rate = 1_000, channels = 1, depth = BitDepth.BIT_16, frames = 1_000)
        val info = WavFile.readInfo(file)

        assertEquals(1_000L, info.durationMs)
        assertEquals(250L, info.msToFrame(250))
        assertEquals(250L, info.frameToMs(250))
    }

    @Test
    fun `massivda yetarli namuna bolmasa sarlavha orttirib yozmaydi`() {
        // Muzlatilgan xato: hisoblagich so'ralgan kadrlarni sanardi, faylga esa
        // kamroq yozilardi — sarlavha fayldagidan ko'proq ma'lumot va'da qilardi.
        val file = File(folder.root, "kam.wav")
        val writer = WavWriter(file, 44_100, 1, BitDepth.BIT_16)
        writer.write(FloatArray(10), count = 100)
        writer.close()

        val info = WavFile.readInfo(file)
        assertEquals(10L, info.frames)
        assertEquals(WavWriter.HEADER_SIZE + 20L, file.length())
    }

    @Test
    fun `chegaradan chiqqan namuna qirqiladi`() {
        val file = File(folder.root, "qirqish.wav")
        val writer = WavWriter(file, 1_000, 1, BitDepth.BIT_16)
        writer.write(floatArrayOf(-5f, 0f, 5f), 3)
        writer.close()

        WavSampleReader(file).use { reader ->
            val out = FloatArray(3)
            assertEquals(3, reader.readFrames(0, 3, out))
            assertEquals(-1f, out[0], 0.001f)
            assertEquals(0f, out[1], 0.001f)
            assertEquals(1f, out[2], 0.001f)
        }
    }

    @Test
    fun `yozilgan namunalar ozgarmasdan oqiladi`() {
        val source = floatArrayOf(-1f, -0.5f, -0.25f, 0f, 0.25f, 0.5f, 1f)
        val file = File(folder.root, "aniqlik.wav")
        WavWriter(file, 1_000, 1, BitDepth.BIT_16).use { it.write(source, source.size) }

        WavSampleReader(file).use { reader ->
            val out = FloatArray(source.size)
            assertEquals(source.size, reader.readFrames(0, source.size, out))
            for (i in source.indices) {
                assertEquals("namuna $i", source[i], out[i], 0.001f)
            }
        }
    }

    @Test
    fun `kichik fayl xato beradi`() {
        val file = File(folder.root, "kichik.wav")
        file.writeBytes(ByteArray(10))

        val failure = runCatching { WavFile.readInfo(file) }.exceptionOrNull()
        assertTrue("Kutilgan xato olindi: $failure", failure != null)
    }

    @Test
    fun `WAV bolmagan fayl xato beradi`() {
        val file = File(folder.root, "boshqa.bin")
        file.writeBytes(ByteArray(64) { 7 })

        val failure = runCatching { WavFile.readInfo(file) }.exceptionOrNull()
        assertTrue("Kutilgan xato olindi: $failure", failure != null)
    }

    private fun write(rate: Int, channels: Int, depth: BitDepth, frames: Int): File {
        val file = File(folder.root, "test-${rate}-${channels}-${depth.bits}-$frames.wav")
        WavWriter(file, rate, channels, depth).use { writer ->
            val samples = FloatArray(frames * channels) { index -> (index % 100) / 100f - 0.5f }
            writer.write(samples, frames)
        }
        return file
    }
}
