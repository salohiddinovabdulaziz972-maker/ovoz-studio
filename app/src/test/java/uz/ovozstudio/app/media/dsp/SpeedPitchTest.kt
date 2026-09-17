package uz.ovozstudio.app.media.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Tezlik va ohang — birgalikda.
 *
 * Bu sinov ikkala amalning **birga** to'g'ri ishlashini tekshiradi: ohang
 * qayta namunalash orqali ko'chadi, keyin uzunlik cho'zish orqali tiklanadi.
 * Ikkalasi alohida to'g'ri bo'lib, birgalikda xato bo'lishi mumkin edi —
 * masalan, cho'zish koeffitsienti teskari qo'llanilsa, natija ikki marta
 * tez yoki ikki marta sekin chiqardi.
 */
class SpeedPitchTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = 48_000

    private val edge = 600

    private var counter = 0

    @Test
    fun `yarim tonlar nisbatga togri ogiriladi`() {
        assertEquals(2.0, SpeedPitch.Settings(semitones = 12.0).pitchRatio, 1e-9)
        assertEquals(0.5, SpeedPitch.Settings(semitones = -12.0).pitchRatio, 1e-9)
        // Bir oktava — 12 yarim ton; ettinchisi 2^(7/12).
        assertEquals(1.4983, SpeedPitch.Settings(semitones = 7.0).pitchRatio, 1e-4)
        assertEquals(1.0, SpeedPitch.Settings().pitchRatio, 1e-12)
    }

    @Test
    fun `ozgarmagan sozlama aniqlanadi`() {
        assertTrue(SpeedPitch.Settings().isIdentity)
        assertFalse(SpeedPitch.Settings(speed = 1.01).isIdentity)
        assertFalse(SpeedPitch.Settings(semitones = 0.1).isIdentity)
    }

    @Test
    fun `chegaradan chiqqan sozlama rad etiladi`() {
        assertTrue(SpeedPitch.Settings(speed = 0.5).isValid())
        assertTrue(SpeedPitch.Settings(speed = 2.0).isValid())
        assertTrue(SpeedPitch.Settings(semitones = 12.0).isValid())
        assertTrue(SpeedPitch.Settings(semitones = -12.0).isValid())

        assertFalse(SpeedPitch.Settings(speed = 0.49).isValid())
        assertFalse(SpeedPitch.Settings(speed = 2.01).isValid())
        assertFalse(SpeedPitch.Settings(semitones = 12.1).isValid())
        assertFalse(SpeedPitch.Settings(semitones = -13.0).isValid())
    }

    @Test
    fun `ozgarmagan sozlama qollanmaydi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 12_000)
        try {
            SpeedPitch.apply(source, folder.newFile("natija.wav"), SpeedPitch.Settings())
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Tezlik ham, ohang ham o'zgarmagan", expected.message)
        }
    }

    @Test
    fun `chegaradan chiqqan sozlama bilan ish boshlanmaydi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 12_000)
        try {
            SpeedPitch.apply(source, folder.newFile("natija.wav"), SpeedPitch.Settings(speed = 3.0))
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Tezlik yoki ohang chegaradan chiqqan", expected.message)
        }
    }

    @Test
    fun `tezlik uzunlikni ozgartiradi ohangni emas`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 48_000)

        val fast = apply(source, SpeedPitch.Settings(speed = 2.0))
        assertEquals(24_000L, WavFile.readInfo(fast).frames)
        assertEquals(1000.0, dominantFrequency(fast), 8.0)

        val slow = apply(source, SpeedPitch.Settings(speed = 0.5))
        assertEquals(96_000L, WavFile.readInfo(slow).frames)
        assertEquals(1000.0, dominantFrequency(slow), 8.0)
    }

    @Test
    fun `ohang uzunlikni ozgartirmaydi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 48_000)

        val high = apply(source, SpeedPitch.Settings(semitones = 12.0))
        assertEquals("uzunlik tegilmasligi kerak", 48_000L, WavFile.readInfo(high).frames)
        assertEquals(2000.0, dominantFrequency(high), 15.0)

        val low = apply(source, SpeedPitch.Settings(semitones = -12.0))
        assertEquals(48_000L, WavFile.readInfo(low).frames)
        assertEquals(500.0, dominantFrequency(low), 6.0)
    }

    @Test
    fun `tezlik va ohang birga qollanadi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 48_000)

        // Ikki marta tez va bir oktava yuqori: uzunlik yarmi, chastota ikki
        // barobar. Ikkalasi bir-biriga xalaqit bermasligi kerak.
        val result = apply(source, SpeedPitch.Settings(speed = 2.0, semitones = 12.0))
        assertEquals(24_000L, WavFile.readInfo(result).frames)
        assertEquals(2000.0, dominantFrequency(result), 20.0)
    }

    @Test
    fun `sarlavha saqlanadi`() {
        val file = folder.newFile("stereo.wav")
        val values = FloatArray(24_000 * 2)
        for (i in 0 until 24_000) {
            val value = (0.4 * sin(2.0 * PI * 500.0 * i / 44_100)).toFloat()
            values[i * 2] = value
            values[i * 2 + 1] = value
        }
        WavWriter(file, 44_100, 2, BitDepth.BIT_16).use { it.write(values, 24_000) }

        val info = WavFile.readInfo(apply(file, SpeedPitch.Settings(semitones = 7.0)))
        assertEquals(44_100, info.sampleRate)
        assertEquals(2, info.channels)
        assertEquals(16, info.bitsPerSample)
    }

    @Test
    fun `jarayon korsatkichi ohang bilan ham noldan birgacha yetadi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 24_000)
        val seen = mutableListOf<Float>()

        SpeedPitch.apply(source, folder.newFile("natija.wav"), SpeedPitch.Settings(semitones = 5.0)) {
            seen += it
        }

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

    @Test
    fun `oraliq fayl qolib ketmaydi`() {
        // Qayta namunalash vaqtinchalik fayl orqali ketadi. U o'chirilmasa,
        // har bir amal diskda iz qoldirardi.
        val before = File(System.getProperty("java.io.tmpdir"))
            .listFiles { file -> file.name.startsWith("ovoz-tezlik-") }?.size ?: 0

        val source = sine("manba.wav", frequency = 1000.0, frames = 12_000)
        apply(source, SpeedPitch.Settings(semitones = 3.0))

        val after = File(System.getProperty("java.io.tmpdir"))
            .listFiles { file -> file.name.startsWith("ovoz-tezlik-") }?.size ?: 0
        assertEquals("vaqtinchalik fayl qoldi", before, after)
    }

    // --- yordamchilar ---------------------------------------------------

    private fun apply(source: File, settings: SpeedPitch.Settings): File {
        val dest = folder.newFile("natija-${counter++}.wav")
        SpeedPitch.apply(source, dest, settings)
        return dest
    }

    private fun sine(name: String, frequency: Double, frames: Int): File {
        val file = folder.newFile(name)
        val values = FloatArray(frames)
        for (i in 0 until frames) {
            values[i] = (0.5 * sin(2.0 * PI * frequency * i / rate)).toFloat()
        }
        WavWriter(file, rate, 1, BitDepth.BIT_16).use { it.write(values, frames) }
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
