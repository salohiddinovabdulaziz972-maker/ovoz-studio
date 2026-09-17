package uz.ovozstudio.app.media.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
 * Ekvalayzerning o'zi: faylni filtrlab, yangi fayl yozadi.
 *
 * Bu yerda tekshiriladigan narsa — **eshitiladigan natija**: kuchaytirilgan
 * polosa o'sha chastotadagi tovushni haqiqatan ko'taradimi, kesilgani
 * pasaytiradimi, boshqa chastotalar tegilmaydimi. Filtr koeffitsientlarining
 * to'g'riligi [BiquadTest] da; bu yerda ularning faylga ta'siri.
 */
class EqualizerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = 48_000

    /** Filtr o'rnashib ulgurishi uchun o'lchovdan tashlab yuboriladigan qism. */
    private val warmupFrames = 4_800

    private var counter = 0

    @Test
    fun `kuchaytirilgan polosa shu chastotadagi tovushni kotaradi`() {
        val source = sine("manba.wav", frequency = 1000.0, amplitude = 0.5f)
        val sourceRms = rms(samples(source), warmupFrames)

        val (result, _) = apply(source, gains(1000.0 to 6.0))

        val ratio = rms(samples(result), warmupFrames) / sourceRms
        assertEquals("+6 dB ikki barobar", 1.9953, ratio, 0.02)
    }

    @Test
    fun `pasaytirilgan polosa tovushni tushiradi`() {
        val source = sine("manba.wav", frequency = 1000.0, amplitude = 0.5f)
        val sourceRms = rms(samples(source), warmupFrames)

        val (result, _) = apply(source, gains(1000.0 to -12.0))

        val ratio = rms(samples(result), warmupFrames) / sourceRms
        assertEquals("-12 dB to'rt barobar", 0.2512, ratio, 0.01)
    }

    @Test
    fun `boshqa chastotadagi tovush tegilmaydi`() {
        // 100 Hz li tovushga 8 kHz polosasining hech qanday aloqasi yo'q:
        // u o'zgarmasligi kerak.
        val source = sine("manba.wav", frequency = 100.0, amplitude = 0.5f)
        val sourceRms = rms(samples(source), warmupFrames)

        val (result, _) = apply(source, gains(8000.0 to 12.0))

        val ratio = rms(samples(result), warmupFrames) / sourceRms
        assertEquals(1.0, ratio, 0.01)
    }

    @Test
    fun `kesish filtri past chastotani olib tashlaydi`() {
        val low = sine("past.wav", frequency = 30.0, amplitude = 0.5f)
        val lowRms = rms(samples(low), warmupFrames)
        val (filtered, _) = apply(low, gains(lowCutHz = 120))
        val lowRatio = rms(samples(filtered), warmupFrames) / lowRms

        // 120 Hz dagi ikkinchi tartibli Buterworth filtri 30 Hz ni ~24 dB
        // pasaytiradi.
        assertTrue("30 Hz o'tib ketdi: $lowRatio", lowRatio < 0.1)

        // O'sha filtr 1 kHz ga tegmasligi kerak.
        val mid = sine("orta.wav", frequency = 1000.0, amplitude = 0.5f)
        val midRms = rms(samples(mid), warmupFrames)
        val (untouched, _) = apply(mid, gains(lowCutHz = 120))
        val midRatio = rms(samples(untouched), warmupFrames) / midRms

        assertEquals("1 kHz pasaytirildi", 1.0, midRatio, 0.01)
    }

    @Test
    fun `kuchaytirish kesishga olib kelganda butun fayl pasaytiriladi`() {
        // Manba allaqachon baland (0.9), ustiga +12 dB — chiqish 3.5 ga
        // chiqib ketardi. Kesish (clipping) o'rniga butun fayl
        // pasaytiriladi, va bu **aytiladi**: jimgina qilinsa,
        // foydalanuvchi «kuchaytirdim, lekin balandroq bo'lmadi» deb
        // tushunmay qolardi.
        val source = sine("baland.wav", frequency = 1000.0, amplitude = 0.9f)
        val sourceRms = rms(samples(source), warmupFrames)

        val (result, info) = apply(source, gains(1000.0 to 12.0))

        assertTrue("pasaytirish haqida aytilmagan: ${info.headroomDb}", info.headroomDb < -8.0)

        val output = samples(result)
        val outputPeak = peak(output)
        assertTrue("chiqish chegaradan oshgan: $outputPeak", outputPeak <= 1.0)
        assertTrue("chiqish juda jim: $outputPeak", outputPeak > 0.9)

        // Pasaytirish butun faylga bir xil tegadi: cho'qqi 0.9 edi, endi
        // 0.999 — ya'ni hamma narsa 0.999/0.9 marta ko'tarilgan.
        val outputRms = rms(output, warmupFrames)
        assertEquals("notekis pasaytirilgan", sourceRms * (0.999 / 0.9), outputRms, sourceRms * 0.02)
    }

    @Test
    fun `kesish kerak bolmasa daraja ozgarmaydi`() {
        val source = sine("manba.wav", frequency = 1000.0, amplitude = 0.3f)

        val (_, info) = apply(source, gains(1000.0 to 6.0))

        assertEquals(0.0, info.headroomDb, 0.0)
    }

    @Test
    fun `natija manba parametrlarini saqlaydi`() {
        val source = sine("manba.wav", frequency = 440.0, amplitude = 0.4f, channels = 2)
        val before = WavFile.readInfo(source)

        val (_, outcome) = apply(source, gains(1000.0 to 6.0))

        assertEquals(before.sampleRate, outcome.info.sampleRate)
        assertEquals(before.channels, outcome.info.channels)
        assertEquals(before.bitsPerSample, outcome.info.bitsPerSample)
        assertEquals(before.frames, outcome.info.frames)
    }

    @Test
    fun `manba uzunligi ozgarmaydi`() {
        val source = sine("manba.wav", frequency = 1000.0, frames = 12_345)

        val (_, outcome) = apply(source, gains(1000.0 to 6.0))

        assertEquals(12_345L, outcome.info.frames)
    }

    @Test
    fun `ikkala kanal bir xil filtrlanadi`() {
        // Kanallar manbada bir xil; agar filtr holati kanallar bo'yicha
        // to'g'ri ajratilmagan bo'lsa, ular ajralib ketardi — birinchi
        // kanalning «dumi» ikkinchisiga o'tib ketardi.
        val source = sine("stereo.wav", frequency = 1000.0, amplitude = 0.5f, channels = 2)

        val (result, _) = apply(source, gains(1000.0 to 9.0))

        val values = samples(result)
        for (frame in 0 until values.size / 2) {
            assertEquals(
                "kadr $frame da kanallar ajralgan",
                values[frame * 2].toDouble(),
                values[frame * 2 + 1].toDouble(),
                0.0,
            )
        }
    }

    @Test
    fun `manba fayl ozgartirmaydi`() {
        val source = sine("manba.wav", frequency = 1000.0, amplitude = 0.5f)
        val before = source.readBytes()

        apply(source, gains(1000.0 to 6.0))

        assertTrue("manba fayl o'zgarib ketgan", before.contentEquals(source.readBytes()))
    }

    @Test
    fun `tekis sozlama xato beradi`() {
        // Hamma polosa nol — qo'llashdan natija manbaning nusxasi bo'lardi.
        // Bunday faylni kutubxonaga qo'shish foydalanuvchini chalg'itardi.
        val source = sine("manba.wav", frequency = 1000.0)

        try {
            apply(source, gains())
            fail("bo'sh sozlama qabul qilindi")
        } catch (expected: IOException) {
            assertEquals("Ekvalayzer sozlamasi bo'sh", expected.message)
        }
    }

    @Test
    fun `bosh fayl xato beradi`() {
        val empty = File(folder.root, "bosh.wav")
        WavWriter(empty, rate, 1, BitDepth.BIT_16).use { }

        try {
            apply(empty, gains(1000.0 to 6.0))
            fail("bo'sh fayl qabul qilindi")
        } catch (expected: IOException) {
            assertEquals("Fayl bo'sh", expected.message)
        }
    }

    @Test
    fun `nol polosalar kaskadga kirmaydi`() {
        // Kaskad faqat haqiqatan ishlaydigan filtrlardan yig'iladi: 10
        // polosadan bittasi kuchaytirilgan bo'lsa, hisob ham bitta filtr
        // narida bo'ladi.
        val bands = EqBands.centers(EqBandCount.TEN, rate)
            .mapIndexed { index, center ->
                Equalizer.Band(center, if (index == 5) 3.0 else 0.0, EqBands.q(EqBandCount.TEN))
            }

        val flat = Equalizer.Settings(bands.map { it.copy(gainDb = 0.0) })
        assertTrue(Equalizer.filters(flat, rate).isEmpty())
        assertTrue(!flat.isAudible(rate))

        val one = Equalizer.Settings(bands)
        assertEquals(1, Equalizer.filters(one, rate).size)
        assertTrue(one.isAudible(rate))

        // Kesish filtri ham kaskadga qo'shiladi.
        assertEquals(2, Equalizer.filters(one.copy(lowCutHz = 80), rate).size)
    }

    @Test
    fun `jarayon korsatkichi noldan birgacha yetadi`() {
        // Ko'rsatkich ekranda chiziq bo'lib ko'rinadi; u oxiriga yetmasa,
        // ish tugagan bo'lsa ham «davom etmoqda» degan taassurot qolardi.
        val source = sine("manba.wav", frequency = 1000.0, amplitude = 0.5f)
        val noClip = mutableListOf<Float>()
        Equalizer.apply(source, newFile(), gains(1000.0 to 6.0)) { noClip.add(it) }

        assertProgressReachesEnd(noClip)

        // Kesish bo'lgan yo'l (ikki o'tish) ham oxiriga yetadi.
        val loudest = sine("juda-baland.wav", frequency = 1000.0, amplitude = 0.9f)
        val clip = mutableListOf<Float>()
        Equalizer.apply(loudest, newFile(), gains(1000.0 to 12.0)) { clip.add(it) }

        assertProgressReachesEnd(clip)
    }

    private fun assertProgressReachesEnd(progress: List<Float>) {
        assertTrue("ko'rsatkich umuman chaqirilmagan", progress.isNotEmpty())
        assertEquals("ko'rsatkich oxiriga yetmagan", 1.0, progress.last().toDouble(), 0.0001)
        for (i in 1 until progress.size) {
            assertTrue("ko'rsatkich orqaga qaytdi", progress[i] >= progress[i - 1])
        }
    }

    /** Bitta yoki bir nechta polosani kuchaytiruvchi sozlama. */
    private fun gains(
        vararg boosts: Pair<Double, Double>,
        lowCutHz: Int = 0,
    ): Equalizer.Settings = Equalizer.Settings(
        bands = EqBands.centers(EqBandCount.TEN, rate).map { center ->
            Equalizer.Band(
                frequency = center,
                gainDb = boosts.firstOrNull { it.first == center }?.second ?: 0.0,
                q = EqBands.q(EqBandCount.TEN),
            )
        },
        lowCutHz = lowCutHz,
    )

    private fun apply(source: File, settings: Equalizer.Settings): Pair<File, Equalizer.Result> {
        val destination = newFile()
        return destination to Equalizer.apply(source, destination, settings)
    }

    private fun newFile(): File = File(folder.root, "natija-${counter++}.wav")

    private fun sine(
        name: String,
        frequency: Double,
        frames: Int = rate,
        amplitude: Float = 0.5f,
        channels: Int = 1,
        sampleRate: Int = rate,
    ): File {
        val file = File(folder.root, name)
        val buffer = FloatArray(frames * channels)
        for (frame in 0 until frames) {
            val value = (amplitude * sin(2.0 * PI * frequency * frame / sampleRate)).toFloat()
            for (channel in 0 until channels) buffer[frame * channels + channel] = value
        }
        WavWriter(file, sampleRate, channels, BitDepth.BIT_16).use { writer ->
            writer.write(buffer, frames)
        }
        return file
    }

    private fun samples(file: File): FloatArray {
        val info = WavFile.readInfo(file)
        val values = FloatArray(info.frames.toInt() * info.channels)
        WavSampleReader(file).use { reader -> reader.readFrames(0, info.frames.toInt(), values) }
        return values
    }

    /** [fromFrame] dan oxirigacha bo'lgan o'rtacha kvadratik qiymat. */
    private fun rms(values: FloatArray, fromFrame: Int): Double {
        var sum = 0.0
        for (index in fromFrame until values.size) sum += values[index].toDouble() * values[index]
        val count = values.size - fromFrame
        return if (count <= 0) 0.0 else sqrt(sum / count)
    }

    private fun peak(values: FloatArray): Double {
        var best = 0.0
        for (value in values) {
            val magnitude = abs(value.toDouble())
            if (magnitude > best) best = magnitude
        }
        return best
    }
}
