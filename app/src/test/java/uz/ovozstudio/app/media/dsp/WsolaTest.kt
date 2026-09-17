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

/**
 * WSOLA — tezlikni o'zgartirish, ohangni tegmasdan.
 *
 * Asosiy xossa ikkita va ular bir-biriga zid emas, balki birga tekshiriladi:
 * uzunlik tezlikka teskari o'zgaradi, **chastota esa o'zgarmaydi**. Oddiy
 * tezlashtirish (har ikkinchi namunani olish) ikkalasini ham birga
 * o'zgartirardi — shuning uchun «chipmunk» effekti chiqardi.
 *
 * Eng qat'iy tekshiruv — tezlik 1.0: algoritm o'z-o'zidan aynan tiklashga
 * aylanishi kerak. U shovqinda sinaladi, sinusda emas: sinus davriy, ya'ni
 * unga bir davr siljigan bo'lak ham «aynan mos» bo'lib ko'rinadi va tanlov
 * tasodifga bog'liq bo'lib qolardi.
 */
class WsolaTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = 48_000

    /** Chekka effektlari (oyna boshi va oxiri) tashlab yuboriladigan qism. */
    private val edge = 4_000

    private var counter = 0

    @Test
    fun `bir marta tezlashtirish tovushni aynan tiklaydi`() {
        val source = noise("manba.wav", frames = 24_000)
        val result = stretch(source, speed = 1.0)

        val before = samples(source)
        val after = samples(result)

        assertEquals("uzunlik o'zgarmaydi", before.size, after.size)

        var worst = 0f
        for (i in edge until before.size - edge) {
            worst = maxOf(worst, abs(before[i] - after[i]))
        }
        // 16-bit faylning bir qadami 1/32768; shundan kattaroq farq
        // allaqachon eshitiladigan xato bo'lardi.
        assertTrue("eng katta farq $worst", worst <= 2f / 32_768f)
    }

    @Test
    fun `tezlik uzunlikni teskari ozgartiradi`() {
        val source = noise("manba.wav", frames = 48_000)

        assertEquals(24_000L, WavFile.readInfo(stretch(source, 2.0)).frames)
        assertEquals(96_000L, WavFile.readInfo(stretch(source, 0.5)).frames)
        assertEquals(48_000L, WavFile.readInfo(stretch(source, 1.0)).frames)
    }

    @Test
    fun `tezlashtirish ohangni ozgartirmaydi`() {
        val source = sine("ohang.wav", frequency = 1000.0, frames = 96_000)

        val fast = stretch(source, speed = 2.0)
        val slow = stretch(source, speed = 0.5)

        // Tezlashtirish — bu «har ikkinchi namunani olish» emas: aks holda
        // chastota 2000 Hz bo'lib qolardi.
        assertEquals(1000.0, dominantFrequency(fast), 5.0)
        assertEquals(1000.0, dominantFrequency(slow), 5.0)
    }

    @Test
    fun `past chastota ham saqlanadi`() {
        // Past chastotalar uchun oyna ichida davrlar kam — bu algoritmning
        // eng nozik joyi.
        val source = sine("past.wav", frequency = 120.0, frames = 96_000)

        assertEquals(120.0, dominantFrequency(stretch(source, 1.5)), 3.0)
    }

    @Test
    fun `kanallar bir-biriga aralashmaydi`() {
        val source = stereo("kanallar.wav", left = 400.0, right = 3000.0, frames = 48_000)
        val result = stretch(source, speed = 1.5)

        val values = samples(result)
        val channels = WavFile.readInfo(result).channels
        assertEquals(2, channels)

        val left = FloatArray(values.size / 2) { values[it * 2] }
        val right = FloatArray(values.size / 2) { values[it * 2 + 1] }

        assertEquals(400.0, dominantFrequency(left, rate), 5.0)
        assertEquals(3000.0, dominantFrequency(right, rate), 15.0)
    }

    @Test
    fun `sarlavha ozgarmaydi`() {
        val source = stereoHz("stereo.wav", frequency = 500.0, frames = 24_000, sampleRate = 44_100)
        val info = WavFile.readInfo(stretch(source, speed = 2.0))

        assertEquals(44_100, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(16, info.bitsPerSample)
    }

    @Test
    fun `juda qisqa fayl ham ishlanadi`() {
        // Oynadan (30 ms) qisqa fayl: tahlil oynasi fayldan katta bo'lib
        // qoladi. Bunday holat ilovada bir sekundlik yozuvlarda uchraydi.
        val source = sine("qisqa.wav", frequency = 440.0, frames = 1_000)

        val info = WavFile.readInfo(stretch(source, speed = 2.0))
        assertEquals(500L, info.frames)
    }

    @Test
    fun `bosh fayl xato beradi`() {
        val empty = folder.newFile("bosh.wav")
        WavWriter(empty, rate, 1, BitDepth.BIT_16).use { }

        try {
            Wsola.stretch(empty, folder.newFile("natija.wav"), 2.0)
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Fayl bo'sh", expected.message)
        }
    }

    @Test
    fun `jarayon korsatkichi noldan birgacha yetadi`() {
        val source = noise("manba.wav", frames = 24_000)
        val seen = mutableListOf<Float>()

        Wsola.stretch(source, folder.newFile("natija.wav"), 1.7) { seen += it }

        assertTrue("ko'rsatkich umuman chaqirilmadi", seen.isNotEmpty())
        assertEquals("oxirgi qiymat", 1.0f, seen.last(), 0.0001f)
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

    private fun stretch(source: File, speed: Double): File {
        val dest = folder.newFile("natija-${counter++}.wav")
        Wsola.stretch(source, dest, speed)
        return dest
    }

    /**
     * Belgilangan urug'li soxta tasodifiy shovqin.
     *
     * Takrorlanadigan (davriy) emas: korrelyatsiya eng katta qiymatga faqat
     * bitta joyda erishadi, shuning uchun tezlik 1.0 dagi tiklash
     * tasodifga bog'liq bo'lmaydi.
     */
    private fun noise(name: String, frames: Int, channels: Int = 1): File {
        var seed = 12_345L
        val values = FloatArray(frames * channels)
        for (i in values.indices) {
            seed = (seed * 1_103_515_245L + 12_345L) and 0x7FFFFFFF
            values[i] = ((seed % 20_001) - 10_000) / 20_000f
        }
        return wav(name, values, channels)
    }

    private fun sine(name: String, frequency: Double, frames: Int, channels: Int = 1): File {
        val values = FloatArray(frames * channels)
        for (i in 0 until frames) {
            val value = (0.5 * sin(2.0 * PI * frequency * i / rate)).toFloat()
            for (channel in 0 until channels) values[i * channels + channel] = value
        }
        return wav(name, values, channels)
    }

    private fun stereo(name: String, left: Double, right: Double, frames: Int): File {
        val values = FloatArray(frames * 2)
        for (i in 0 until frames) {
            values[i * 2] = (0.4 * sin(2.0 * PI * left * i / rate)).toFloat()
            values[i * 2 + 1] = (0.4 * sin(2.0 * PI * right * i / rate)).toFloat()
        }
        return wav(name, values, 2)
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

    private fun wav(name: String, values: FloatArray, channels: Int = 1, sampleRate: Int = rate): File {
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

    /** Fayldagi asosiy chastota (Gerts) — nolni kesib o'tishlar orqali. */
    private fun dominantFrequency(file: File): Double =
        dominantFrequency(samples(file), WavFile.readInfo(file).sampleRate)

    /**
     * Nolni kesib o'tishlar orqali chastota.
     *
     * Gisterezis ishlatiladi: shovqinli nol atrofidagi mayda tebranishlar
     * qo'shimcha o'tishlar bo'lib sanalmasligi kerak — ular chastotani
     * sun'iy ravishda ko'tarib yuborardi.
     */
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
