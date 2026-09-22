package uz.ovozstudio.app.media.merge

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.format.WavPcmReader
import java.io.File
import java.io.IOException
import kotlin.math.abs

/**
 * Audiolarni birlashtirish.
 *
 * Asosiy talab: natija manbalarning aynan ketma-ketligi bo'lishi kerak —
 * bitta ham kadr yo'qolmaydi, ortmaydi va joyi almashmaydi. Parametrlari
 * farq qiladigan manbalar (chastota, kanal, bit chuqurligi) alohida holat.
 */
class AudioMergerTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `bir xil parametrli fayllar bayt-bayt ketma-ket qoshiladi`() {
        val a = writeInts("a.wav", 8_000, 1, BitDepth.BIT_16, IntArray(1_000) { it - 500 })
        val b = writeInts("b.wav", 8_000, 1, BitDepth.BIT_16, IntArray(700) { 20_000 + it })
        val dest = File(folder.root, "natija.wav")

        val info = AudioMerger.merge(listOf(a, b), dest, folder.newFolder("ish"))

        assertEquals(1_700L, info.frames)
        assertEquals(8_000, info.sampleRate)
        assertEquals(1, info.channels)
        val expected = IntArray(1_700) { if (it < 1_000) it - 500 else 20_000 + (it - 1_000) }
        assertArrayEquals(expected, readInts(dest))
    }

    @Test
    fun `tartib saqlanadi`() {
        val a = writeInts("a.wav", 8_000, 1, BitDepth.BIT_16, IntArray(10) { 1 })
        val b = writeInts("b.wav", 8_000, 1, BitDepth.BIT_16, IntArray(10) { 2 })
        val dest = File(folder.root, "natija.wav")

        AudioMerger.merge(listOf(b, a), dest, folder.newFolder("ish"))

        val result = readInts(dest)
        assertEquals(2, result[0])
        assertEquals(2, result[9])
        assertEquals(1, result[10])
        assertEquals(1, result[19])
    }

    @Test
    fun `manba fayllar ozgarmaydi`() {
        val a = writeInts("a.wav", 8_000, 1, BitDepth.BIT_16, IntArray(100) { it })
        val b = writeInts("b.wav", 8_000, 1, BitDepth.BIT_16, IntArray(100) { it })
        val sizeA = a.length()
        val bytesA = a.readBytes()

        AudioMerger.merge(listOf(a, b), File(folder.root, "natija.wav"), folder.newFolder("ish"))

        assertEquals(sizeA, a.length())
        assertArrayEquals(bytesA, a.readBytes())
    }

    @Test
    fun `mono fayl stereo bilan qoshilganda ikkala kanalga kochadi`() {
        val mono = writeInts("m.wav", 8_000, 1, BitDepth.BIT_16, intArrayOf(100, 200, 300, 400))
        val stereo = writeInts("s.wav", 8_000, 2, BitDepth.BIT_16, intArrayOf(1, 2, 3, 4))
        val dest = File(folder.root, "natija.wav")

        val info = AudioMerger.merge(listOf(mono, stereo), dest, folder.newFolder("ish"))

        assertEquals(2, info.channels)
        assertEquals(6L, info.frames)
        assertClose(intArrayOf(100, 100, 200, 200, 300, 300, 400, 400, 1, 2, 3, 4), readInts(dest), 1)
    }

    @Test
    fun `chastotasi boshqa fayl birinchi fayl chastotasiga keltiriladi`() {
        val slow = writeInts("a.wav", 8_000, 1, BitDepth.BIT_16, IntArray(8_000) { 16_384 })
        val fast = writeInts("b.wav", 16_000, 1, BitDepth.BIT_16, IntArray(16_000) { 16_384 })
        val dest = File(folder.root, "natija.wav")
        val work = folder.newFolder("ish")

        val info = AudioMerger.merge(listOf(slow, fast), dest, work)

        // Birinchi fayl 8 kHz: ikkinchisi (bir soniya) ham sakkiz ming kadr bo'ladi.
        assertEquals(8_000, info.sampleRate)
        assertEquals(16_000L, info.frames)
        val result = readInts(dest)
        // Ikkinchi qismning o'rtasida doimiy signal joyida turibdi: ovoz balandligi o'zgarmagan.
        for (index in listOf(8_500, 12_000, 15_500)) {
            assertTrue("kadr $index: ${result[index]}", abs(result[index] - 16_384) <= 2)
        }
        assertEquals("vaqtinchalik fayl qolmasligi kerak", 0, work.listFiles()?.size ?: 0)
    }

    @Test
    fun `24 bitli manba bo'lsa natija 24 bit bo'ladi`() {
        val low = writeInts("a.wav", 8_000, 1, BitDepth.BIT_16, IntArray(4) { 16_384 })
        val high = writeInts("b.wav", 8_000, 1, BitDepth.BIT_24, IntArray(4) { 4_194_304 })
        val dest = File(folder.root, "natija.wav")

        val info = AudioMerger.merge(listOf(low, high), dest, folder.newFolder("ish"))

        assertEquals(24, info.bitsPerSample)
        assertEquals(8L, info.frames)
        // 16 bitli qism 24 bitga shkalalanadi: 0.5 to'liq shkalaning yarmi bo'lib qoladi.
        assertClose(IntArray(8) { 4_194_304 }, readInts(dest), 1)
    }

    @Test
    fun `targetOf birinchi chastotani eng kop kanalni va kata chuqurlikni oladi`() {
        val target = AudioMerger.targetOf(
            listOf(
                WavInfo(44_100, 1, 16, 44, 0),
                WavInfo(48_000, 2, 24, 44, 0),
            ),
        )
        assertEquals(44_100, target.sampleRate)
        assertEquals(2, target.channels)
        assertEquals(BitDepth.BIT_24, target.bitDepth)
    }

    @Test
    fun `jarayon oxirida bir bo'ladi`() {
        val a = writeInts("a.wav", 8_000, 1, BitDepth.BIT_16, IntArray(1_000) { it })
        val b = writeInts("b.wav", 8_000, 1, BitDepth.BIT_16, IntArray(700) { it })
        var last = 0f

        AudioMerger.merge(
            listOf(a, b),
            File(folder.root, "natija.wav"),
            folder.newFolder("ish"),
        ) { last = it }

        assertEquals(1f, last, 0.0001f)
    }

    @Test
    fun `fayl berilmasa xato beradi`() {
        assertThrows(IOException::class.java) {
            AudioMerger.merge(emptyList(), File(folder.root, "natija.wav"), folder.newFolder("ish"))
        }
    }

    @Test
    fun `remap mono kirishni ikki kanalga kochiradi`() {
        val output = FloatArray(6)
        AudioMerger.remap(floatArrayOf(1f, 2f, 3f), 1, output, 2, 3)
        assertArrayEquals(floatArrayOf(1f, 1f, 2f, 2f, 3f, 3f), output, 0f)
    }

    // --- yordamchi ---

    /** Berilgan butun sonli namunalar bilan WAV yozadi (ko'p kanal bo'lsa — aralash tartibda). */
    private fun writeInts(
        name: String,
        rate: Int,
        channels: Int,
        depth: BitDepth,
        values: IntArray,
    ): File {
        val file = File(folder.root, name)
        WavWriter(file, rate, channels, depth).use { writer ->
            writer.writeIntegers(values, values.size / channels)
        }
        return file
    }

    /** WAV dagi barcha namunalarni butun son sifatida o'qiydi. */
    private fun readInts(file: File): IntArray {
        WavPcmReader(file).use { reader ->
            val channels = reader.info.channels
            val out = IntArray(reader.info.frames.toInt() * channels)
            val chunk = IntArray(4_096 * channels)
            var frames = 0
            while (true) {
                val got = reader.read(chunk, 4_096)
                if (got <= 0) break
                System.arraycopy(chunk, 0, out, frames * channels, got * channels)
                frames += got
            }
            return out
        }
    }

    private fun assertClose(expected: IntArray, actual: IntArray, tolerance: Int) {
        assertEquals("namunalar soni", expected.size, actual.size)
        for (index in expected.indices) {
            assertTrue(
                "namuna $index: kutilgan ${expected[index]}, chiqdi ${actual[index]}",
                abs(expected[index] - actual[index]) <= tolerance,
            )
        }
    }
}
