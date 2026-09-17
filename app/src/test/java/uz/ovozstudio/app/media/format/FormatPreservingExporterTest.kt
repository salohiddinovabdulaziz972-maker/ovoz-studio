package uz.ovozstudio.app.media.format

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import java.io.File

class FormatPreservingExporterTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val api = 34

    private fun writeWav(name: String, rate: Int, channels: Int, depth: BitDepth, values: IntArray): File {
        val file = File(folder.root, name)
        WavWriter(file, rate, channels, depth).use { writer ->
            writer.writeIntegers(values, values.size / channels)
        }
        return file
    }

    private fun readAll(file: File): IntArray {
        WavPcmReader(file).use { reader ->
            val collected = ArrayList<Int>()
            val buffer = IntArray(1024 * reader.format.channels)
            while (true) {
                val frames = reader.read(buffer, 1024)
                if (frames <= 0) break
                for (i in 0 until frames * reader.format.channels) collected.add(buffer[i])
            }
            return collected.toIntArray()
        }
    }

    /** 4096 kadrlik bo'lakdan uzunroq — oqim bir necha marta aylanadi. */
    private fun ramp(count: Int, limit: Int): IntArray =
        IntArray(count) { i -> ((i * 37) % (2 * limit)) - limit }

    private val wavFormat = AudioFormat(AudioContainer.WAV, AudioCodec.PCM, 44_100, 2, 16)

    @Test
    fun `wav dan wav ga namunalar aynan oz holida otadi`() {
        val values = ramp(20_000 * 2, 30_000)
        val source = writeWav("manba.wav", 44_100, 2, BitDepth.BIT_16, values)
        val destination = File(folder.root, "natija.wav")

        val outcome = FormatPreservingExporter(api).export(source, wavFormat, destination)

        assertTrue(outcome is ExportOutcome.Done)
        outcome as ExportOutcome.Done
        assertEquals(AudioContainer.WAV, outcome.format.container)
        assertEquals(20_000L, outcome.frames)
        assertArrayEquals(values, readAll(destination))
    }

    @Test
    fun `yigirma tort bitli wav ham aniq saqlanadi`() {
        // 24-bit chegarasiga yaqin qiymatlar: float orqali o'tkazilganda
        // aynan shular surilib ketardi.
        val values = intArrayOf(8_388_607, -8_388_608, 8_388_606, -8_388_607, 1, -1, 0, 4_194_303)
        val source = writeWav("chuqur.wav", 48_000, 1, BitDepth.BIT_24, values)
        val destination = File(folder.root, "chuqur-natija.wav")

        val format = AudioFormat(AudioContainer.WAV, AudioCodec.PCM, 48_000, 1, 24)
        FormatPreservingExporter(api).export(source, format, destination)

        assertArrayEquals(values, readAll(destination))
    }

    @Test
    fun `flac manba flac bolib qaytadi`() {
        val values = ramp(9_000 * 2, 20_000)
        val source = writeWav("manba.wav", 44_100, 2, BitDepth.BIT_16, values)
        val destination = File(folder.root, "natija.flac")

        val flacFormat = AudioFormat(AudioContainer.FLAC, AudioCodec.FLAC, 44_100, 2, 16)
        val outcome = FormatPreservingExporter(api).export(source, flacFormat, destination)

        outcome as ExportOutcome.Done
        assertEquals(AudioContainer.FLAC, outcome.format.container)
        assertEquals(9_000L, outcome.frames)
        assertEquals("fLaC", String(destination.readBytes(), 0, 4, Charsets.US_ASCII))
        assertTrue("fayl bo'sh bo'lmasligi kerak", destination.length() > 42)
    }

    @Test
    fun `manba faylga tegilmaydi`() {
        val values = ramp(5_000, 10_000)
        val source = writeWav("manba.wav", 44_100, 1, BitDepth.BIT_16, values)
        val before = source.readBytes()

        val mono = AudioFormat(AudioContainer.WAV, AudioCodec.PCM, 44_100, 1, 16)
        FormatPreservingExporter(api).export(source, mono, File(folder.root, "nusxa.wav"))

        assertArrayEquals(before, source.readBytes())
    }

    @Test
    fun `vorbis manba uchun fayl yaratilmaydi`() {
        val values = ramp(1_000, 5_000)
        val source = writeWav("manba.wav", 44_100, 1, BitDepth.BIT_16, values)
        val destination = File(folder.root, "natija.ogg")

        val vorbis = AudioFormat(AudioContainer.OGG, AudioCodec.VORBIS, 44_100, 1)
        val outcome = FormatPreservingExporter(api).export(source, vorbis, destination)

        assertTrue(outcome is ExportOutcome.Unsupported)
        assertEquals(FallbackReason.NO_ENCODER, (outcome as ExportOutcome.Unsupported).reason)
        assertTrue("fayl yaratilmasligi kerak", !destination.exists())
    }

    @Test
    fun `ochiq tanlangan format chekinishni bekor qiladi`() {
        val values = ramp(3_000, 8_000)
        val source = writeWav("manba.wav", 44_100, 1, BitDepth.BIT_16, values)
        val destination = File(folder.root, "tanlangan.flac")

        val vorbis = AudioFormat(AudioContainer.OGG, AudioCodec.VORBIS, 44_100, 1)
        val chosen = AudioFormat(AudioContainer.FLAC, AudioCodec.FLAC, 44_100, 1, 16)
        val outcome = FormatPreservingExporter(api).export(source, vorbis, destination, override = chosen)

        outcome as ExportOutcome.Done
        assertEquals(AudioContainer.FLAC, outcome.format.container)
        assertEquals(3_000L, outcome.frames)
    }

    @Test
    fun `namuna parametrlari tahrirlangan fayldan olinadi`() {
        // Manba formati 44.1 kHz stereo deb e'lon qilingan, lekin tahrirlangan
        // fayl 48 kHz mono — sarlavha fayl ichidagi haqiqatga mos bo'lishi kerak.
        val values = ramp(2_000, 6_000)
        val source = writeWav("manba.wav", 48_000, 1, BitDepth.BIT_16, values)
        val destination = File(folder.root, "natija.flac")

        val declared = AudioFormat(AudioContainer.FLAC, AudioCodec.FLAC, 44_100, 2, 16)
        val outcome = FormatPreservingExporter(api).export(source, declared, destination) as ExportOutcome.Done

        assertEquals(48_000, outcome.format.sampleRate)
        assertEquals(1, outcome.format.channels)
        assertEquals(AudioContainer.FLAC, outcome.format.container)
    }

    @Test
    fun `qoshimcha kodlovchi ishlatiladi`() {
        val values = ramp(1_500, 4_000)
        val source = writeWav("manba.wav", 44_100, 1, BitDepth.BIT_16, values)
        val destination = File(folder.root, "soxta.bin")

        var asked: AudioFormat? = null
        val exporter = FormatPreservingExporter(api) { target, file ->
            asked = target
            object : AudioEncoder {
                override val format = target
                var frames = 0L
                override fun write(samples: IntArray, frames: Int) {
                    this.frames += frames
                }
                override fun finish() = file.writeBytes(byteArrayOf(1, 2, 3))
                override fun close() = Unit
            }
        }

        val m4a = AudioFormat(AudioContainer.M4A, AudioCodec.AAC, 44_100, 1, bitrate = 128_000)
        val outcome = exporter.export(source, m4a, destination) as ExportOutcome.Done

        assertEquals(AudioContainer.M4A, asked?.container)
        assertEquals(1_500L, outcome.frames)
        assertEquals(3L, destination.length())
    }

    @Test
    fun `oqish tugagach nol qaytaradi`() {
        val source = writeWav("kichik.wav", 44_100, 1, BitDepth.BIT_16, intArrayOf(1, 2, 3, 4))
        WavPcmReader(source).use { reader ->
            val buffer = IntArray(16)
            assertEquals(4, reader.read(buffer, 16))
            assertEquals(0, reader.read(buffer, 16))
            assertEquals(4L, reader.frames)
        }
    }
}
