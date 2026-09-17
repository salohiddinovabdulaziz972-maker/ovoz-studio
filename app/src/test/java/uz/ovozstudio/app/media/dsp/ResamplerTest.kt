package uz.ovozstudio.app.media.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavSampleReader
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Qayta namunalash — ohangni ko'taradi, uzunlikni qisqartiradi.
 *
 * Bu amal [Wsola] ning teskari tomoni: u yerda uzunlik o'zgarib, chastota
 * joyida qoladi; bu yerda ikkalasi birga o'zgaradi.
 *
 * Tekshiriladigan uch narsa: koeffitsient uzunlikka qanday ta'sir qiladi,
 * chastota qanday ko'chadi va **qisqartirishda** yuqori chastotalar
 * buralib tushmasligi (aliasing).
 */
class ResamplerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = 48_000

    private val edge = 600

    private var counter = 0

    @Test
    fun `bir marta qayta namunalash faylni ozgartirmaydi`() {
        // Koeffitsient 1 bo'lganda interpolyatsiya butun sonli nuqtalarga
        // tushadi: yadro aynan bitta namuna qoldiradi, qolganlari nol.
        val source = sine("manba.wav", frequency = 1000.0, frames = 12_000)
        val result = resample(source, 1.0)

        val before = samples(source)
        val after = samples(result)

        assertEquals(before.size, after.size)
        var worst = 0f
        for (i in edge until before.size - edge) {
            worst = maxOf(worst, abs(before[i] - after[i]))
        }
        assertTrue("eng katta farq $worst", worst <= 2f / 32_768f)
    }

    @Test
    fun `uzunlik koeffitsientga teskari ozgaradi`() {
        val source = sine("manba.wav", frequency = 500.0, frames = 24_000)

        assertEquals(12_000L, WavFile.readInfo(resample(source, 2.0)).frames)
        assertEquals(48_000L, WavFile.readInfo(resample(source, 0.5)).frames)
        assertEquals(24_000L, WavFile.readInfo(resample(source, 1.0)).frames)
    }

    @Test
    fun `chastota koeffitsientga kotariladi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 48_000)

        // Bir oktava yuqori.
        assertEquals(2000.0, dominantFrequency(resample(source, 2.0)), 10.0)
        // Bir oktava past.
        assertEquals(500.0, dominantFrequency(resample(source, 0.5)), 5.0)
        // Yarim oktava: 2^(7/12) = 1.4983.
        assertEquals(1498.3, dominantFrequency(resample(source, 1.4983)), 10.0)
    }

    @Test
    fun `qisqartirishda yuqori chastota buralib tushmaydi`() {
        // 20 kHz — kirishda bor (Nyquist 24 kHz), lekin koeffitsient 2
        // bo'lganda chiqishning Nyquisti 12 kHz ga tushadi. Filtrsiz u
        // 4 kHz ga buralib tushardi va eshitiladigan «hushtak» bo'lardi.
        val high = sine("yuqori.wav", frequency = 20_000.0, frames = 48_000)
        val low = sine("past.wav", frequency = 1_000.0, frames = 48_000)

        val highRms = rms(samples(resample(high, 2.0)), edge)
        val lowRms = rms(samples(resample(low, 2.0)), edge)

        assertTrue(
            "20 kHz bostirilmadi: $highRms (1 kHz esa $lowRms)",
            highRms < lowRms * 0.2,
        )
        // 1 kHz esa deyarli o'zgarmasdan o'tishi kerak.
        assertTrue("1 kHz yo'qolib qoldi: $lowRms", lowRms > 0.3)
    }

    @Test
    fun `kanallar saqlanadi`() {
        val source = stereo("stereo.wav", left = 500.0, right = 4000.0, frames = 24_000)
        val result = resample(source, 2.0)

        val values = samples(result)
        val left = FloatArray(values.size / 2) { values[it * 2] }
        val right = FloatArray(values.size / 2) { values[it * 2 + 1] }

        assertEquals(1000.0, dominantFrequency(left, rate), 10.0)
        assertEquals(8000.0, dominantFrequency(right, rate), 60.0)
    }

    @Test
    fun `sarlavha saqlanadi`() {
        val source = stereoHz("stereo.wav", frequency = 500.0, frames = 12_000, sampleRate = 44_100)
        val info = WavFile.readInfo(resample(source, 1.5))

        assertEquals(44_100, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(16, info.bitsPerSample)
    }

    @Test
    fun `bosh fayl xato beradi`() {
        val empty = folder.newFile("bosh.wav")
        WavWriter(empty, rate, 1, BitDepth.BIT_16).use { }

        try {
            Resampler.resample(empty, folder.newFile("natija.wav"), 2.0)
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Fayl bo'sh", expected.message)
        }
    }

    @Test
    fun `jarayon korsatkichi noldan birgacha yetadi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 12_000)
        val seen = mutableListOf<Float>()

        Resampler.resample(source, folder.newFile("natija.wav"), 1.5) { seen += it }

        assertTrue("ko'rsatkich umuman chaqirilmadi", seen.isNotEmpty())
        assertEquals(1.0f, seen.last(), 0.0001f)
        // Xato matni ataylab shu yerda quriladi: `assertTrue` ning xabar
        // argumenti har doim hisoblanadi, ya'ni uni yuqoridagi ko'rinishda
        // yozsak, har bir qadamda butun ro'yxat satrga aylanardi.
        for (i in 1 until seen.size) {
            if (seen[i] < seen[i - 1]) {
                throw AssertionError("ko'rsatkich orqaga qaytdi: ${seen[i - 1]} dan ${seen[i]} ga")
            }
        }
    }

    // --- yordamchilar ---------------------------------------------------

    private fun resample(source: File, ratio: Double): File {
        val dest = folder.newFile("natija-${counter++}.wav")
        Resampler.resample(source, dest, ratio)
        return dest
    }

    private fun sine(name: String, frequency: Double, frames: Int, channels: Int = 1): File {
        val values = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val value = (0.5 * sin(2.0 * PI * frequency * i / rate)).toFloat()
            for (channel in 0 until channels) values[i * channels + channel] = value
        }
        return wav(name, values, channels, rate)
    }

    private fun stereo(name: String, left: Double, right: Double, frames: Int): File {
        val values = FloatArray(frames * 2)
        for (i in 0 until frames) {
            values[i * 2] = (0.4 * sin(2.0 * PI * left * i / rate)).toFloat()
            values[i * 2 + 1] = (0.4 * sin(2.0 * PI * right * i / rate)).toFloat()
        }
        return wav(name, values, 2, rate)
    }

    private fun stereoHz(name: String, frequency: Double, frames: Int, sampleRate: Int): File {
        val values = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val value = (0.4 * sin(2.0 * PI * frequency * i / sampleRate)).toFloat()
            values[i * 2] = value
            values[i * 2 + 1] = value
        }
        return wav(name, values, 2, sampleRate)
    }

    private fun wav(name: String, values: FloatArray, channels: Int, sampleRate: Int): File {
        val file = folder.newFile(name)
        WavWriter(file, sampleRate, channels, BitDepth.BIT_16).use { writer ->
            writer.write(values, values.size / channels)
        }
        return file
    }

    private fun samples(file: File): FloatArray {
        WavSampleReader(file).use { reader ->
            val info = reader.info
            val out = FloatArray(info.frames.toInt() * info.channels)
            reader.readFrames(0, info.frames.toInt(), out)
            return out
        }
    }

    private fun rms(values: FloatArray, from: Int): Double {
        var sum = 0.0
        var count = 0
        for (i in from until values.size - from) {
            sum += values[i].toDouble() * values[i]
            count++
        }
        return if (count == 0) 0.0 else sqrt(sum / count)
    }

    private fun dominantFrequency(file: File): Double =
        dominantFrequency(samples(file), WavFile.readInfo(file).sampleRate)

    /** Nolni kesib o'tishlar orqali chastota (gisterezis bilan). */
    private fun dominantFrequency(values: FloatArray, sampleRate: Int): Double {
        var peak = 0f
        for (i in edge until values.size - edge) peak = maxOf(peak, abs(values[i]))
        val threshold = peak * 0.4f

        var below = false
        var first = -1L
        var last = -1L
        var crossings = 0L
        for (i in edge until values.size - edge) {
            val value = values[i]
            if (value < -threshold) below = true
            if (below && value > threshold) {
                below = false
                if (first < 0) first = i.toLong() else { crossings++; last = i.toLong() }
            }
        }
        if (crossings <= 0) return 0.0
        return crossings * sampleRate.toDouble() / (last - first)
    }
}
