package uz.ovozstudio.app.media.book

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.format.WavPcmReader

class WavJoinerTest {

    private fun tempFile(suffix: String): File {
        val file = File.createTempFile("ovozstudio-join", suffix)
        file.deleteOnExit()
        return file
    }

    private fun wav(
        samples: IntArray,
        sampleRate: Int = 8000,
        channels: Int = 1,
        bits: Int = 16,
    ): File {
        val file = tempFile(".wav")
        WavWriter(file, sampleRate, channels, BitDepth.of(bits)!!).use { writer ->
            writer.writeIntegers(samples, samples.size / channels)
        }
        return file
    }

    private fun read(file: File, frames: Int): IntArray {
        val target = IntArray(frames)
        WavPcmReader(file).use { reader ->
            val got = reader.read(target, frames)
            assertEquals("o'qilgan kadrlar soni", frames, got)
        }
        return target
    }

    /**
     * Kadrlarni tanib bo'ladigan qilib to'ldiramiz: `start` dan boshlab.
     *
     * Qiymatlar 16-bit chegarasida qolishi shart — chegaradan oshgani
     * yozishda kesiladi va test «namuna o'zgardi» deb yonli xato berardi.
     */
    private fun ramp(start: Int, frames: Int): IntArray =
        IntArray(frames) { (start + it) * 50 % 32_000 }

    @Test
    fun `bolaklar orasiga pauza qoyiladi`() {
        val first = wav(ramp(0, 100))
        val second = wav(ramp(200, 200))
        val out = tempFile(".wav")

        val joined = WavJoiner.join(listOf(first, second), out, gapMs = 400)

        // 8000 Hz da 400 ms — 3200 kadr.
        assertEquals(0L, joined.parts[0].startFrame)
        assertEquals(100L, joined.parts[0].frames)
        assertEquals(3300L, joined.parts[1].startFrame)
        assertEquals(200L, joined.parts[1].frames)
        assertEquals(3500L, joined.info.frames)
        assertEquals(8000, joined.info.sampleRate)
    }

    @Test
    fun `namunalar ozgarmasdan kochiriladi`() {
        // Butun son ko'rinishida ko'chirishning butun ma'nosi shu: float
        // orqali o'tgan namuna chegarada bir birlikka surilib ketardi.
        val first = ramp(0, 100)
        val second = ramp(200, 200)
        val out = tempFile(".wav")

        WavJoiner.join(listOf(wav(first), wav(second)), out, gapMs = 400)

        val result = read(out, 3500)
        assertArrayEquals(first, result.copyOfRange(0, 100))
        assertArrayEquals(second, result.copyOfRange(3300, 3500))
    }

    @Test
    fun `pauza jimgina boladi`() {
        val out = tempFile(".wav")
        WavJoiner.join(listOf(wav(ramp(0, 10)), wav(ramp(0, 10))), out, gapMs = 400)

        val result = read(out, 3220)
        val gap = result.copyOfRange(10, 3210)
        assertTrue("pauzada ovoz bor", gap.all { it == 0 })
    }

    @Test
    fun `pauza nol bolsa bolaklar yopishadi`() {
        val out = tempFile(".wav")
        val joined = WavJoiner.join(
            listOf(wav(ramp(0, 100)), wav(ramp(0, 50))),
            out,
            gapMs = 0,
        )

        assertEquals(100L, joined.parts[1].startFrame)
        assertEquals(150L, joined.info.frames)
    }

    @Test
    fun `birinchi bolak oldiga pauza qoyilmaydi`() {
        val out = tempFile(".wav")
        val joined = WavJoiner.join(listOf(wav(ramp(0, 100))), out, gapMs = 400)

        assertEquals(0L, joined.parts.single().startFrame)
        assertEquals(100L, joined.info.frames)
    }

    @Test
    fun `kanal soni saqlanadi`() {
        // Stereo bo'lak: kadr — ikki namuna. Kanal soni hisobga olinmasa,
        // pauza ikki barobar uzun bo'lib qolardi.
        val stereo = IntArray(200) { if (it % 2 == 0) 1000 else -1000 }
        val out = tempFile(".wav")
        val joined = WavJoiner.join(
            listOf(wav(stereo, channels = 2), wav(stereo, channels = 2)),
            out,
            gapMs = 500,
        )

        assertEquals(2, joined.info.channels)
        assertEquals(100L, joined.parts[0].frames)
        assertEquals(4100L, joined.parts[1].startFrame)
    }

    @Test
    fun `yigirma tort bit aniqlik saqlanadi`() {
        // 24-bit chegarasidagi qiymatlar: float32 ularni aniq saqlamaydi.
        val extreme = intArrayOf(-8_388_608, 8_388_607, 0, -1, 1, 4_194_303)
        val out = tempFile(".wav")
        val joined = WavJoiner.join(listOf(wav(extreme, bits = 24)), out, gapMs = 0)

        assertEquals(24, joined.info.bitsPerSample)
        assertArrayEquals(extreme, read(out, extreme.size))
    }

    @Test
    fun `format mos kelmasa xato beradi`() {
        val out = tempFile(".wav")

        val error = assertThrows(BookAssemblyException::class.java) {
            WavJoiner.join(
                listOf(wav(ramp(0, 10), sampleRate = 8000), wav(ramp(0, 10), sampleRate = 16000)),
                out,
                gapMs = 0,
            )
        }
        assertTrue(error.message.orEmpty().contains("formati"))
    }

    @Test
    fun `bosh royxat xato beradi`() {
        assertThrows(BookAssemblyException::class.java) {
            WavJoiner.join(emptyList(), tempFile(".wav"))
        }
    }

    @Test
    fun `manfiy pauza xato beradi`() {
        assertThrows(BookAssemblyException::class.java) {
            WavJoiner.join(listOf(wav(ramp(0, 10))), tempFile(".wav"), gapMs = -1)
        }
    }
}
