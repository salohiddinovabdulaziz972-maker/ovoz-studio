package uz.ovozstudio.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Kesish amallari.
 *
 * Namuna chastotasi ataylab 1000 Hz: shunda bitta kadr — aynan bir millisoniya
 * va testlardagi barcha sonlar butun chiqadi.
 */
class AudioTrimmerTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `copyRange faqat tanlangan oraliqni yozadi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "oraliq.wav")

        val info = AudioTrimmer.copyRange(source, dest, startMs = 200, endMs = 700)

        assertEquals(500L, info.frames)
        assertEquals(500L, info.durationMs)
        assertFrames(dest, expected = 200..699)
    }

    @Test
    fun `copyRange oraliqni fayl chegarasiga qisqartiradi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "chegara.wav")

        val info = AudioTrimmer.copyRange(source, dest, startMs = 900, endMs = 5_000)

        assertEquals(100L, info.frames)
        assertFrames(dest, expected = 900..999)
    }

    @Test
    fun `copyRange bosh oraliqda xato beradi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "bosh.wav")

        val failure = runCatching {
            AudioTrimmer.copyRange(source, dest, startMs = 500, endMs = 500)
        }.exceptionOrNull()

        assertTrue("Kutilgan xato olindi: $failure", failure != null)
    }

    @Test
    fun `copyRange manba faylni ozgartirmaydi`() {
        val source = ramp(frames = 1_000)
        val before = source.readBytes()

        AudioTrimmer.copyRange(source, File(folder.root, "natija.wav"), 100, 500)

        assertTrue("Manba fayl o'zgarmasligi kerak", before.contentEquals(source.readBytes()))
    }

    @Test
    fun `deleteRanges ortadagi bolakni olib tashlab qolganini birlashtiradi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "ochirish.wav")

        val info = AudioTrimmer.deleteRanges(source, dest, listOf(AudioTrimmer.Cut(200, 700)))

        assertEquals(500L, info.frames)
        assertFrames(dest, expected = (0..199) + (700..999))
    }

    @Test
    fun `deleteRanges bir nechta bolakni ozaro kesishsa ham togri ishlaydi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "kop.wav")

        val info = AudioTrimmer.deleteRanges(source, dest, listOf(AudioTrimmer.Cut(100, 300), AudioTrimmer.Cut(200, 400)))

        assertEquals(700L, info.frames)
        assertFrames(dest, expected = (0..99) + (400..999))
    }

    @Test
    fun `deleteRanges tartibsiz berilgan oraliqlarni ozi tartiblaydi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "tartibsiz.wav")

        val info = AudioTrimmer.deleteRanges(source, dest, listOf(AudioTrimmer.Cut(700, 900), AudioTrimmer.Cut(100, 300)))

        assertEquals(600L, info.frames)
        assertFrames(dest, expected = (0..99) + (300..699) + (900..999))
    }

    @Test
    fun `deleteRanges butun faylni ochirishga yol qoymaydi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "hammasi.wav")

        val failure = runCatching {
            AudioTrimmer.deleteRanges(source, dest, listOf(AudioTrimmer.Cut(0, 1_000)))
        }.exceptionOrNull()

        assertTrue("Kutilgan xato olindi: $failure", failure != null)
        assertTrue("Bo'sh fayl qolmasligi kerak", !dest.exists() || dest.length() == 0L)
    }

    @Test
    fun `fadeIn boshidagi namunani bostirib boradi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "fade-in.wav")

        AudioTrimmer.copyRange(
            source, dest, startMs = 0, endMs = 1_000,
            fades = AudioTrimmer.Fades(fadeInMs = 100),
        )

        WavSampleReader(dest).use { reader ->
            val out = FloatArray(1_000)
            reader.readFrames(0, 1_000, out)
            assertEquals("birinchi namuna", 0f, out[0], 0.001f)
            assertEquals("fade oxirida to'liq ovoz", out[100], 1f / 1_000f * 100, 0.01f)
            assertEquals("fadedan keyin o'zgarmaydi", out[500], 0.5f, 0.01f)
        }
    }

    @Test
    fun `split faylni ikki qismga boladi va davomiylikni yoqotmaydi`() {
        val source = ramp(frames = 1_000)
        val first = File(folder.root, "birinchi.wav")
        val second = File(folder.root, "ikkinchi.wav")

        val (firstInfo, secondInfo) = AudioTrimmer.split(source, first, second, atMs = 400)

        assertEquals(400L, firstInfo.frames)
        assertEquals(600L, secondInfo.frames)
        assertEquals(1_000L, firstInfo.frames + secondInfo.frames)
        assertFrames(first, expected = 0..399)
        assertFrames(second, expected = 400..999)
    }

    @Test
    fun `keptRanges ustma-ust tushgan oraliqlarni birlashtiradi`() {
        val info = WavFile.readInfo(ramp(frames = 1_000))

        val keep = AudioTrimmer.keptRanges(
            info,
            listOf(AudioTrimmer.Cut(100, 300), AudioTrimmer.Cut(200, 400)),
        )

        assertEquals(listOf(0L to 100L, 400L to 1_000L), keep.map { it.first to it.last })
    }

    @Test
    fun `keptRanges fayl chegarasidan chiqqan oraliqni qisqartiradi`() {
        val info = WavFile.readInfo(ramp(frames = 1_000))

        val keep = AudioTrimmer.keptRanges(info, listOf(AudioTrimmer.Cut(-500, 200)))

        assertEquals(listOf(200L to 1_000L), keep.map { it.first to it.last })
    }

    @Test
    fun `keptRanges bosh oraliqni etiborsiz qoldiradi`() {
        val info = WavFile.readInfo(ramp(frames = 1_000))

        val keep = AudioTrimmer.keptRanges(info, listOf(AudioTrimmer.Cut(400, 400)))

        assertEquals(listOf(0L to 1_000L), keep.map { it.first to it.last })
    }

    @Test
    fun `coversWholeFile butun fayl qamrab olinganini aniqlaydi`() {
        val info = WavFile.readInfo(ramp(frames = 1_000))

        assertTrue(
            "bitta oraliq butun faylni qamrab oladi",
            AudioTrimmer.coversWholeFile(info, listOf(AudioTrimmer.Cut(0, 1_000))),
        )
        assertTrue(
            "ikki qo'shni oraliq birgalikda butun faylni qamrab oladi",
            AudioTrimmer.coversWholeFile(
                info,
                listOf(AudioTrimmer.Cut(0, 500), AudioTrimmer.Cut(500, 1_000)),
            ),
        )
    }

    @Test
    fun `coversWholeFile oraliq orasida bolak qolsa false qaytaradi`() {
        val info = WavFile.readInfo(ramp(frames = 1_000))

        val covered = AudioTrimmer.coversWholeFile(
            info,
            listOf(AudioTrimmer.Cut(0, 400), AudioTrimmer.Cut(600, 1_000)),
        )

        assertTrue("400–599 oralig'i saqlanib qoladi", !covered)
    }

    @Test
    fun `deleteRanges tutashuv joyini silliqlaydi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "silliq.wav")

        // 200…700 o'chadi: qoladi 0…200 va 700…1000, tutashuv 200-kadrda.
        // Silliqlash 10 ms = 10 kadr.
        val info = AudioTrimmer.deleteRanges(
            source,
            dest,
            listOf(AudioTrimmer.Cut(200, 700)),
            joinFadeMs = 10,
        )

        assertEquals(500L, info.frames)
        WavSampleReader(dest).use { reader ->
            val out = FloatArray(500)
            reader.readFrames(0, 500, out)

            // Faylning boshi va oxiri silliqlanmaydi.
            assertEquals(0.0f, out[0], 0.001f)
            assertEquals(0.999f, out[499], 0.001f)
            // Birinchi qismning oxiri pasayadi: 189 gacha o'zgarmaydi.
            assertEquals(0.189f, out[189], 0.001f)
            assertEquals(0.190f, out[190], 0.001f)
            assertEquals(0.195f * 0.5f, out[195], 0.001f)
            assertEquals(0.199f * 0.1f, out[199], 0.001f)
            // Ikkinchi qism noldan ko'tariladi (manba indeksi 700 + n).
            assertEquals(0.0f, out[200], 0.001f)
            assertEquals(0.705f * 0.5f, out[205], 0.001f)
            assertEquals(0.710f, out[210], 0.001f)
        }
    }

    @Test
    fun `joinFadeMs berilmasa tutashuv o'zgarmaydi`() {
        val source = ramp(frames = 1_000)
        val dest = File(folder.root, "oddiy.wav")

        AudioTrimmer.deleteRanges(source, dest, listOf(AudioTrimmer.Cut(200, 700)))

        WavSampleReader(dest).use { reader ->
            val out = FloatArray(500)
            reader.readFrames(0, 500, out)
            assertEquals(0.199f, out[199], 0.001f)
            assertEquals(0.700f, out[200], 0.001f)
        }
    }

    // --- yordamchi ---

    /** Har bir kadri o'z indeksiga teng bo'lgan fayl: solishtirish oson. */
    private fun ramp(frames: Int): File {
        val file = File(folder.root, "manba-$frames.wav")
        WavWriter(file, 1_000, 1, BitDepth.BIT_16).use { writer ->
            writer.write(FloatArray(frames) { it / 1_000f }, frames)
        }
        return file
    }

    /**
     * Natija faylidagi kadrlar manba fayldagi qaysi indekslarga mos kelishini
     * tekshiradi: har bir kadrning qiymati o'sha indeksning 1000 ga nisbati.
     */
    private fun assertFrames(file: File, expected: Iterable<Int>) {
        val indexes = expected.toList()
        val info = WavFile.readInfo(file)
        assertEquals("kadrlar soni ${file.name}", indexes.size.toLong(), info.frames)

        WavSampleReader(file).use { reader ->
            val out = FloatArray(info.frames.toInt())
            reader.readFrames(0, out.size, out)

            indexes.forEachIndexed { offset, index ->
                assertEquals(
                    "fayl=${file.name} kadr=$offset manba indeksi=$index",
                    index / 1_000f,
                    out[offset],
                    0.001f,
                )
            }
        }
    }
}
