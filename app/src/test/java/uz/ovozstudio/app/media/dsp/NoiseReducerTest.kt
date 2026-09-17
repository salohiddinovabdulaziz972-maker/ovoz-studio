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
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shovqin tozalash — spektral ayirish.
 *
 * Bu yerda ikkita qarama-qarshi talab birga tekshiriladi: **shovqin
 * yo'qolishi** va **foydali tovush saqlanishi**. Faqat birinchisini
 * tekshirish oson bo'lardi — hammasini nolga aylantirish ham shovqinni
 * «yo'q qiladi» — shuning uchun har bir sinovda ohang amplitudasi ham
 * o'lchanadi.
 *
 * Sinov signallari sun'iy va aniq: sinus + oq shovqin. Bu shovqin tozalash
 * uchun eng qiyin holat emas, balki eng **o'lchovli** holati: ohangning
 * amplitudasi ham, shovqin darajasi ham oldindan ma'lum.
 */
class NoiseReducerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = 48_000

    /** Faylning birinchi qismi — faqat shovqin; shundan namuna olinadi. */
    private val noiseFrames = 21_600 // 450 ms

    private val toneFrequency = 1000.0

    private var counter = 0

    @Test
    fun `faqat shovqindan iborat fayl sezilarli pasayadi`() {
        val source = noiseFile("shovqin.wav", frames = 72_000)

        val result = NoiseReducer.apply(source, dest(), settings(0, 400))

        // Nazariy hisob: quvvati shovqindan `kuch` marta katta bo'lgan
        // polosalar saqlanadi, qolganlari qoldiq chegarasiga tushadi.
        // Standart sozlamada bu 8–10 dB atrofida.
        assertTrue(
            "kutilgan 6 dB dan kam: ${result.noiseDropDb}",
            result.noiseDropDb >= 6.0,
        )
        assertTrue(
            "kutilgan 20 dB dan ko'p: ${result.noiseDropDb}",
            result.noiseDropDb <= 20.0,
        )
    }

    @Test
    fun `ohang saqlanib qoladi`() {
        val source = toneWithNoise("ohang.wav", frames = 96_000)
        val dest = dest()
        NoiseReducer.apply(source, dest, settings(0, 400))

        val before = samples(source)
        val after = samples(dest)

        // Ohang faqat shovqin tugaganidan keyin boshlanadi.
        val from = noiseFrames + 4800
        val count = 48_000

        val original = toneAmplitude(before, from, count, toneFrequency)
        val processed = toneAmplitude(after, from, count, toneFrequency)

        // 0.5 dB — eshitilmaydigan daraja. Spektral ayirish ohang polosasida
        // koeffitsientni 1 ga juda yaqin qoldiradi: ohang quvvati shovqindan
        // millionlab marta katta.
        val ratioDb = 20.0 * log10(processed / original)
        assertTrue(
            "ohang o'zgardi: $ratioDb dB (oldin $original, keyin $processed)",
            abs(ratioDb) <= 0.5,
        )
    }

    @Test
    fun `kuch oshganda chuqurroq tozalanadi`() {
        val source = noiseFile("shovqin.wav", frames = 72_000)

        val soft = NoiseReducer.apply(source, dest(), settings(0, 400, strength = 2.0, floorDb = -15.0))
        val strong = NoiseReducer.apply(source, dest(), settings(0, 400, strength = 4.0, floorDb = -30.0))

        assertTrue(
            "kuchli sozlama kamroq tozaladi: ${strong.noiseDropDb} <= ${soft.noiseDropDb}",
            strong.noiseDropDb > soft.noiseDropDb + 2.0,
        )
    }

    @Test
    fun `sarlavha va uzunlik ozgarmaydi`() {
        val source = toneWithNoise("manba.wav", frames = 48_000, sampleRate = 44_100)
        val dest = dest()
        NoiseReducer.apply(source, dest, settings(0, 300))

        val info = WavFile.readInfo(dest)
        assertEquals(48_000L, info.frames)
        assertEquals(44_100, info.sampleRate)
        assertEquals(1, info.channels)
        assertEquals(16, info.bitsPerSample)
    }

    @Test
    fun `kanallar bir-biriga aralashmaydi`() {
        val source = stereo("kanallar.wav", frames = 96_000)
        val dest = dest()
        NoiseReducer.apply(source, dest, settings(0, 400))

        val values = samples(dest)
        val left = FloatArray(values.size / 2) { values[it * 2] }
        val right = FloatArray(values.size / 2) { values[it * 2 + 1] }

        val from = noiseFrames + 4800
        val count = 24_000

        // Har bir kanalda faqat o'z ohangi qolishi kerak: chapda 1000 Hz,
        // o'ngda 3000 Hz. Kanallar aralashsa, ikkinchi ohang ham birinchi
        // kanalda paydo bo'lardi.
        assertTrue("chap kanalda 1000 Hz yo'qoldi", toneAmplitude(left, from, count, 1000.0) > 0.3)
        assertTrue("o'ng kanalda 3000 Hz yo'qoldi", toneAmplitude(right, from, count, 3000.0) > 0.3)
        assertTrue("chap kanalga 3000 Hz o'tib ketdi", toneAmplitude(left, from, count, 3000.0) < 0.01)
    }

    @Test
    fun `chiqish choqqisi kirishdan oshmaydi`() {
        // Spektral ayirish faqat bosadi. Agar biror polosada koeffitsient
        // 1 dan oshsa, shovqin yanada balandroq bo'lib qolardi — bu eng ko'p
        // uchraydigan xato.
        val source = toneWithNoise("manba.wav", frames = 48_000)
        val dest = dest()
        NoiseReducer.apply(source, dest, settings(0, 400))

        val before = samples(source)
        val after = samples(dest)

        var inputPeak = 0f
        var outputPeak = 0f
        for (i in before.indices) {
            inputPeak = maxOf(inputPeak, abs(before[i]))
            outputPeak = maxOf(outputPeak, abs(after[i]))
        }

        assertTrue(
            "chiqish cho'qqisi oshdi: $outputPeak > $inputPeak",
            outputPeak <= inputPeak + 1e-3f,
        )
    }

    @Test
    fun `jim namunadan profil chiqmaydi`() {
        // Birinchi yarim sekund — raqamli jimlik, keyin shovqin. Bunday
        // tanlov xato: ayirish uchun hech narsa yo'q, natija esa manbaning
        // nusxasi bo'lardi.
        val source = folder.newFile("jim.wav")
        write(source, FloatArray(48_000), channels = 1) { frame -> frame >= 24_000 }

        try {
            NoiseReducer.apply(source, dest(), settings(0, 300))
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Shovqin namunasi jim", expected.message)
        }
    }

    @Test
    fun `bosh oraliq rad etiladi`() {
        val source = noiseFile("shovqin.wav", frames = 48_000)

        try {
            NoiseReducer.apply(source, dest(), settings(300, 300))
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Sozlama noto'g'ri", expected.message)
        }
    }

    @Test
    fun `fayldan tashqari oraliq faylga moslanadi`() {
        // Foydalanuvchi butun faylni shovqin deb belgilashi mumkin: oxiri
        // fayldan oshib ketsa bu xato emas — fayl oxirigacha olinadi.
        val source = noiseFile("shovqin.wav", frames = 48_000)

        val result = NoiseReducer.apply(source, dest(), settings(0, 60_000))

        assertTrue("tozalanmadi: ${result.noiseDropDb}", result.noiseDropDb >= 6.0)
    }

    @Test
    fun `jarayon korsatkichi noldan birgacha yetadi`() {
        val source = noiseFile("shovqin.wav", frames = 48_000)
        val seen = mutableListOf<Float>()

        NoiseReducer.apply(source, dest(), settings(0, 400)) { seen += it }

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

    @Test
    fun `bosh fayl xato beradi`() {
        val empty = folder.newFile("bosh.wav")
        WavWriter(empty, rate, 1, BitDepth.BIT_16).use { }

        try {
            NoiseReducer.apply(empty, dest(), settings(0, 100))
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IOException) {
            assertEquals("Fayl bo'sh", expected.message)
        }
    }

    // --- yordamchilar ---------------------------------------------------

    private fun dest(): File = folder.newFile("natija-${counter++}.wav")

    private fun settings(
        startMs: Long,
        endMs: Long,
        strength: Double = NoiseReducer.DEFAULT_STRENGTH,
        floorDb: Double = NoiseReducer.DEFAULT_FLOOR_DB,
    ) = NoiseReducer.Settings(
        noiseStartMs = startMs,
        noiseEndMs = endMs,
        strength = strength,
        floorDb = floorDb,
    )

    /** Belgilangan urug'li shovqin: tekis spektr, RMS ≈ 0.029. */
    private fun noises(frames: Int, channels: Int): FloatArray {
        var seed = 987_654_321L
        val values = FloatArray(frames * channels)
        for (i in values.indices) {
            seed = (seed * 1_103_515_245L + 12_345L) and 0x7FFFFFFF
            values[i] = ((seed % 20_001) - 10_000) / 200_000f
        }
        return values
    }

    private fun noiseFile(name: String, frames: Int, sampleRate: Int = rate): File =
        write(folder.newFile(name), noises(frames, 1), channels = 1, sampleRate = sampleRate)

    /** Birinchi [noiseFrames] namuna — shovqin, keyin ohang + shovqin. */
    private fun toneWithNoise(name: String, frames: Int, sampleRate: Int = rate): File {
        val values = noises(frames, 1)
        for (i in noiseFrames until frames) {
            values[i] += (0.5 * sin(2.0 * PI * toneFrequency * i / sampleRate)).toFloat()
        }
        return write(folder.newFile(name), values, channels = 1, sampleRate = sampleRate)
    }

    /** Chapda 1000 Hz, o'ngda 3000 Hz — kanallar aralashmasligini ko'rish uchun. */
    private fun stereo(name: String, frames: Int): File {
        val values = noises(frames, 2)
        for (i in noiseFrames until frames) {
            values[i * 2] += (0.5 * sin(2.0 * PI * 1000.0 * i / rate)).toFloat()
            values[i * 2 + 1] += (0.5 * sin(2.0 * PI * 3000.0 * i / rate)).toFloat()
        }
        return write(folder.newFile(name), values, channels = 2)
    }

    /** [keep] rost qaytargan kadrlar yoziladi, qolgani jimlik bo'lib qoladi. */
    private fun write(
        file: File,
        values: FloatArray,
        channels: Int,
        sampleRate: Int = rate,
        keep: (Int) -> Boolean = { true },
    ): File {
        val masked = FloatArray(values.size)
        for (frame in 0 until values.size / channels) {
            if (!keep(frame)) continue
            for (channel in 0 until channels) {
                masked[frame * channels + channel] = values[frame * channels + channel]
            }
        }
        WavWriter(file, sampleRate, channels, BitDepth.BIT_16).use { writer ->
            writer.write(masked, values.size / channels)
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

    /**
     * Bitta chastotaning amplitudasi (bitta polosali DFT).
     *
     * [count] davrga karrali bo'lishi kerak, aks holda sizib chiqish
     * (leakage) o'lchovni buzadi.
     */
    private fun toneAmplitude(values: FloatArray, from: Int, count: Int, frequency: Double): Double {
        var re = 0.0
        var im = 0.0
        for (i in 0 until count) {
            val angle = 2.0 * PI * frequency * (from + i) / rate
            re += values[from + i] * cos(angle)
            im += values[from + i] * sin(angle)
        }
        return 2.0 * sqrt(re * re + im * im) / count
    }
}
