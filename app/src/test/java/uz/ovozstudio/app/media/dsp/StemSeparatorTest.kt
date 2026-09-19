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
 * Vokal/cholg'u ajratishning tuzilish xossalari.
 *
 * Bu testlar **usulning matematikasini** tekshiradi: ajratish qo'shishga
 * teskari amal bo'lgani uchun vokal + cholg'u aynan manbani berishi shart,
 * kanallar soni va uzunlik o'zgarmasligi shart, imkonsiz manbalar esa ochiq
 * xato berishi shart.
 *
 * Bu yerda o'lchanmaydigan narsa — **ajratish sifati** (vokal haqiqatan
 * cholg'udan ajraldimi). Uni bu test o'zi yasagan signalda o'zi o'lchasa,
 * o'z-o'zini tekshirish bo'lardi; shuning uchun u alohida, tashqi
 * o'lchovda tekshiriladi: `bin/verify-stem.sh` (Python o'qiydi, ffmpeg
 * solishtiradi).
 */
class StemSeparatorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = 8_000

    // --- yordamchi ---

    /** Stereo fayl yozadi: har bir kadr uchun (chap, o'ng) qiymat beriladi. */
    private fun write(
        name: String,
        frames: Int,
        sample: (Int) -> Pair<Float, Float>,
    ): File {
        val file = File(folder.root, name)
        val interleaved = FloatArray(frames * 2)
        for (i in 0 until frames) {
            val (left, right) = sample(i)
            interleaved[i * 2] = left
            interleaved[i * 2 + 1] = right
        }
        WavWriter(file, rate, 2, BitDepth.BIT_16).use { it.write(interleaved, frames) }
        return file
    }

    /** Markazda [centerHz], yonda [sideHz] turgan signal. */
    private fun mix(frames: Int, centerHz: Double, sideHz: Double, amplitude: Double = 0.4): File =
        write("manba.wav", frames) { i ->
            val center = amplitude * sin(2.0 * PI * centerHz * i / rate)
            val side = amplitude * sin(2.0 * PI * sideHz * i / rate)
            (center + side).toFloat() to (center - side).toFloat()
        }

    private fun samples(file: File): List<Pair<Float, Float>> {
        val info = WavFile.readInfo(file)
        val buffer = FloatArray(info.frames.toInt() * info.channels)
        WavSampleReader(file).use { it.readFrames(0, info.frames.toInt(), buffer) }
        return List(info.frames.toInt()) { i -> buffer[i * 2] to buffer[i * 2 + 1] }
    }

    private fun separate(source: File, settings: StemSeparator.Settings): Pair<File, File> {
        val vocal = File(folder.root, "vokal.wav")
        val instrumental = File(folder.root, "cholgu.wav")
        StemSeparator.apply(source, vocal, instrumental, settings)
        return vocal to instrumental
    }

    // --- asosiy xossa: hech narsa yo'qolmaydi ---

    @Test
    fun `vokal va cholgu yigindisi manbani beradi`() {
        // Butun usul shu xossaga tayanadi: ajratish — spektrda qo'shishga
        // teskari amal. Agar bu buzilsa, ilova «ajratdi» deb aslida
        // ma'lumot yo'qotgan yoki o'ylab topgan bo'lardi.
        val source = mix(frames = 4_000, centerHz = 440.0, sideHz = 1_500.0)
        val (vocal, instrumental) = separate(source, StemSeparator.Settings(StemSeparator.Mode.SPLIT))

        val original = samples(source)
        val a = samples(vocal)
        val b = samples(instrumental)

        assertEquals(original.size, a.size)
        var worst = 0.0
        for (i in original.indices) {
            val sumLeft = (a[i].first + b[i].first).toDouble()
            val sumRight = (a[i].second + b[i].second).toDouble()
            worst = maxOf(
                worst,
                abs(sumLeft - original[i].first.toDouble()),
                abs(sumRight - original[i].second.toDouble()),
            )
        }
        // 16-bit fayl: bitta razryad 1/32768 ≈ 0.00003. Chegara undan
        // kattaroq, lekin «eshitilmaydigan» darajada kichik.
        assertTrue("eng katta farq $worst", worst < 1e-3)
    }

    @Test
    fun `aniq ayirish rejimida ham yigindi manbani beradi`() {
        val source = mix(frames = 4_000, centerHz = 300.0, sideHz = 900.0)
        val (vocal, instrumental) =
            separate(source, StemSeparator.Settings(StemSeparator.Mode.REMOVE_VOCALS))

        val original = samples(source)
        val a = samples(vocal)
        val b = samples(instrumental)
        var worst = 0.0
        for (i in original.indices) {
            worst = maxOf(
                worst,
                abs((a[i].first + b[i].first - original[i].first).toDouble()),
                abs((a[i].second + b[i].second - original[i].second).toDouble()),
            )
        }
        assertTrue("eng katta farq $worst", worst < 1e-3)
    }

    @Test
    fun `aniq ayirish markazni butunlay yoq qiladi`() {
        // Faqat markaziy signal (yon qism yo'q): cholg'u faylida jimlik
        // qolishi kerak. Bu — karaoke usulining ta'rifi.
        val frames = 2_000
        // Kanallari bir xil fayl ajratishga yaroqsiz, shuning uchun kichik
        // yon qism qo'shamiz: usul markazni baribir butunlay olib tashlashi
        // kerak, qolgan yon qismgina qoladi.
        val withNoise = write("markaz-shovqin.wav", frames) { i ->
            val center = 0.5 * sin(2.0 * PI * 500.0 * i / rate)
            val side = 1e-4 * sin(2.0 * PI * 700.0 * i / rate)
            (center + side).toFloat() to (center - side).toFloat()
        }

        val (_, instrumental) =
            separate(withNoise, StemSeparator.Settings(StemSeparator.Mode.REMOVE_VOCALS))
        val left = samples(instrumental).map { it.first }
        val loudest = left.maxOf { abs(it) }
        assertTrue("cholg'u faylida qoldiq juda katta: $loudest", loudest < 1e-3)
    }

    // --- shakl saqlanadi ---

    @Test
    fun `ikkala fayl manbaning shaklini saqlaydi`() {
        val source = mix(frames = 3_000, centerHz = 440.0, sideHz = 1_200.0)
        val (vocal, instrumental) = separate(source, StemSeparator.Settings())

        for (file in listOf(vocal, instrumental)) {
            val info = WavFile.readInfo(file)
            assertEquals("chastota ${file.name}", rate, info.sampleRate)
            assertEquals("kanallar ${file.name}", 2, info.channels)
            assertEquals("bit chuqurligi ${file.name}", 16, info.bitsPerSample)
            assertEquals("uzunlik ${file.name}", 3_000L, info.frames)
        }
    }

    @Test
    fun `vokal fayli markazlashgan`() {
        // Vokal — markazdan olinadi, ya'ni u bitta signal: ikkala kanalda
        // bir xil bo'lishi kerak. Aks holda foydalanuvchi «vokal» faylini
        // tinglaganda uni bir tomondan eshitardi.
        val source = mix(frames = 2_000, centerHz = 400.0, sideHz = 1_000.0)
        val (vocal, _) = separate(source, StemSeparator.Settings())
        for ((left, right) in samples(vocal)) {
            assertEquals("kanallar bir xil emas", left, right, 1e-4f)
        }
    }

    // --- imkonsiz manbalar: ochiq xato ---

    @Test
    fun `bitta kanalli fayl rad etiladi`() {
        val file = File(folder.root, "mono.wav")
        val frames = 500
        WavWriter(file, rate, 1, BitDepth.BIT_16).use {
            it.write(FloatArray(frames) { 0.1f }, frames)
        }

        val error = runCatching {
            StemSeparator.apply(
                file,
                File(folder.root, "v.wav"),
                File(folder.root, "c.wav"),
                StemSeparator.Settings(),
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(StemSeparator.ERROR_NOT_STEREO, error?.message)
    }

    @Test
    fun `kanallari bir xil fayl rad etiladi`() {
        // Bunday faylda yon qism nolga teng: ajratadigan narsa yo'q.
        // Jimgina nusxa qaytarish eng yomon variant bo'lardi.
        val source = write("bir-xil.wav", 1_000) { i ->
            val value = (0.3 * sin(2.0 * PI * 440.0 * i / rate)).toFloat()
            value to value
        }

        val error = runCatching {
            StemSeparator.apply(
                source,
                File(folder.root, "v.wav"),
                File(folder.root, "c.wav"),
                StemSeparator.Settings(),
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(StemSeparator.ERROR_MONO_CONTENT, error?.message)
    }

    @Test
    fun `bosh fayl rad etiladi`() {
        val file = File(folder.root, "bosh.wav")
        WavWriter(file, rate, 2, BitDepth.BIT_16).use { }

        val error = runCatching {
            StemSeparator.apply(
                file,
                File(folder.root, "v.wav"),
                File(folder.root, "c.wav"),
                StemSeparator.Settings(),
            )
        }.exceptionOrNull()

        assertTrue(error is IOException)
    }

    @Test
    fun `notogri sozlama rad etiladi`() {
        val source = mix(frames = 1_000, centerHz = 440.0, sideHz = 900.0)
        val error = runCatching {
            separate(source, StemSeparator.Settings(strength = 99.0))
        }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    // --- o'lchangan ko'rsatkichlar ---

    @Test
    fun `yon va markaz nisbati olchanadi`() {
        // Kuchli yon qismli faylda nisbat musbat, kuchsizida manfiy bo'lishi
        // kerak: ekran shu songa qarab «manba deyarli mono» deb ogohlantiradi.
        val wide = mix(frames = 2_000, centerHz = 400.0, sideHz = 1_000.0, amplitude = 0.3)
        val result = StemSeparator.apply(
            wide,
            File(folder.root, "v1.wav"),
            File(folder.root, "c1.wav"),
            StemSeparator.Settings(),
        )
        // Markaz va yon bir xil amplitudada: nisbat 0 dB atrofida.
        assertEquals(0.0, result.sideToMidDb, 0.1)
    }

    @Test
    fun `ko'rsatkich oxirigacha yetadi`() {
        val source = mix(frames = 5_000, centerHz = 440.0, sideHz = 1_000.0)
        val seen = ArrayList<Float>()
        StemSeparator.apply(
            source,
            File(folder.root, "v.wav"),
            File(folder.root, "c.wav"),
            StemSeparator.Settings(),
        ) { seen.add(it) }

        assertTrue("ko'rsatkich bo'sh", seen.isNotEmpty())
        assertEquals(1f, seen.last(), 1e-6f)
        // Ko'rsatkich faqat o'sib boradi.
        for (i in 1 until seen.size) assertTrue(seen[i] >= seen[i - 1])
    }

    @Test
    fun `manba ozgartirilmaydi`() {
        val source = mix(frames = 2_000, centerHz = 440.0, sideHz = 900.0)
        val before = source.readBytes()
        separate(source, StemSeparator.Settings())
        assertTrue("manba fayl o'zgardi", before.contentEquals(source.readBytes()))
    }

    // --- sozlama chegaralari ---

    @Test
    fun `kuch chegaralari tekshiriladi`() {
        assertTrue(StemSeparator.Settings(strength = StemSeparator.MIN_STRENGTH).isValid())
        assertTrue(StemSeparator.Settings(strength = StemSeparator.MAX_STRENGTH).isValid())
        assertTrue(!StemSeparator.Settings(strength = StemSeparator.MIN_STRENGTH - 0.01).isValid())
        assertTrue(!StemSeparator.Settings(strength = StemSeparator.MAX_STRENGTH + 0.01).isValid())
    }

    @Test
    fun `standart sozlama yaroqli`() {
        assertTrue(StemSeparator.Settings().isValid())
    }
}
