package uz.ovozstudio.app.media.book

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.doc.Chapter
import uz.ovozstudio.app.media.voice.SpeechListener
import uz.ovozstudio.app.media.voice.SpeechRequest
import uz.ovozstudio.app.media.voice.TextScript
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.media.voice.VoiceError
import uz.ovozstudio.app.media.voice.VoiceInfo

/**
 * Kitob yig'uvchining sinovi.
 *
 * Sintezator — soxta: u haqiqiy WAV yozadi, lekin ovoz chiqarmaydi. Shu
 * sababli butun oqim (tartib, pauza, tozalash, to'xtatish) oddiy JVM
 * sinovida tekshiriladi, qurilma esa faqat tovush sifatiga javob beradi.
 */
class BookBuilderTest {

    /** Soxta sintezator: `frames` kadrli WAV yozadi. */
    private class FakeEngine(
        private val ready: Boolean = true,
        private val sampleRate: Int = 22_050,
        private val frames: Int = 400,
        /** Shu matnda xato qaytaradi — xato yo'lini sinash uchun. */
        private val failOn: String? = null,
        /** Javobdan oldin chaqiriladi: to'xtatishni shu yerda taqlid qilamiz. */
        private val beforeResult: ((String) -> Unit)? = null,
        /** Umuman javob bermaydigan dvigatel. */
        private val silent: Boolean = false,
    ) : VoiceEngine {

        val spoken = ArrayList<String>()
        var stopped = false

        override fun prepare(onResult: (VoiceError?) -> Unit) = onResult(null)

        override fun isReady(): Boolean = ready

        override fun voices(): List<VoiceInfo> = emptyList()

        override fun resolveLanguage(script: TextScript): String = "uz-UZ"

        override fun speak(request: SpeechRequest, listener: SpeechListener) = Unit

        override fun synthesizeToFile(
            request: SpeechRequest,
            output: File,
            onResult: (VoiceError?) -> Unit,
        ) {
            spoken += request.text
            beforeResult?.invoke(request.text)
            if (silent) return
            if (request.text == failOn) {
                onResult(VoiceError.SPEAK_FAILED)
                return
            }
            WavWriter(output, sampleRate, 1, BitDepth.BIT_16).use { writer ->
                writer.writeIntegers(IntArray(frames) { (it * 7) % 20_000 }, frames)
            }
            onResult(null)
        }

        override fun stop() {
            stopped = true
        }

        override fun release() = Unit
    }

    private fun tempDir(): File {
        val dir = File.createTempFile("ovozstudio-kitob", "")
        dir.delete()
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun chapter(title: String, text: String) = Chapter(title, text, 0, text.length)

    private fun plan(vararg chapters: Chapter) = BookPlanner.plan(chapters.toList())

    private val boblar = arrayOf(
        chapter("1-BOB", "Salim aka qishloqdan keldi."),
        chapter("2-BOB", "Ertalab havo ochiq edi."),
    )

    private fun builder(engine: VoiceEngine, work: File, timeout: Long = 5_000) =
        BookBuilder(engine = engine, workDir = work, utteranceTimeoutMs = timeout)

    @Test
    fun `har bir bob alohida faylga yigiladi`() {
        val engine = FakeEngine()
        val out = tempDir()
        val result = builder(engine, tempDir()).build(plan(*boblar), out, "Mening kitobim")

        val book = (result as BookBuildOutcome.Done).book
        assertEquals(2, book.chapters.size)
        assertEquals(4, book.utteranceCount)
        assertTrue("davomiylik hisoblanmadi", book.durationMs > 0)

        for (bob in book.chapters) {
            assertTrue("fayl yozilmadi: ${bob.audio.name}", bob.audio.length() > 100)
            assertEquals(out, bob.audio.parentFile)
        }
        // Nom tartib raqamini saqlaydi: fayl menejerida boblar aralashmasin.
        assertTrue(book.chapters[0].audio.name.startsWith("Mening kitobim - 01 - 1-BOB"))
        assertTrue(book.chapters[1].audio.name.startsWith("Mening kitobim - 02 - 2-BOB"))
        assertTrue(book.chapters[0].audio.name.endsWith(".mp3"))
    }

    @Test
    fun `boblar mp3 sifatida yoziladi`() {
        val out = tempDir()
        val result = builder(FakeEngine(), tempDir()).build(plan(*boblar), out, "Kitob")
        val book = (result as BookBuildOutcome.Done).book

        // Kadr sinxronizatsiyasi: 11 ta bir bit — fayl haqiqatan MP3.
        val head = book.chapters[0].audio.readBytes().take(2)
        assertEquals(0xFF, head[0].toInt() and 0xFF)
        assertEquals(0xE0, head[1].toInt() and 0xE0)
    }

    @Test
    fun `belgilar varaqasi yoziladi`() {
        val out = tempDir()
        val result = builder(FakeEngine(), tempDir()).build(plan(*boblar), out, "Kitob")
        val book = (result as BookBuildOutcome.Done).book

        val cue = book.chapters[0].cue
        assertNotNull("CUE yozilmadi", cue)
        val text = cue!!.readText()
        assertTrue(text, text.contains("TRACK 01 AUDIO"))
        assertTrue(text, text.contains("INDEX 01"))
        assertTrue(text, text.contains("1-BOB"))
        // CUE audio faylning nomini ko'rsatadi — pleyer shu nom bilan qidiradi.
        assertTrue(text, text.contains(book.chapters[0].audio.name))
    }

    @Test
    fun `vaqtinchalik bolaklar ochiriladi`() {
        val work = tempDir()
        builder(FakeEngine(), work).build(plan(*boblar), tempDir(), "Kitob")

        assertEquals("ishchi papkada fayl qoldi", 0, work.listFiles()?.size ?: 0)
    }

    @Test
    fun `bosh reja xato beradi`() {
        val result = builder(FakeEngine(), tempDir()).build(BookPlan(emptyList(), 400), tempDir(), "Kitob")
        assertEquals(BookBuildError.EMPTY_DOCUMENT, (result as BookBuildOutcome.Failed).error)
    }

    @Test
    fun `tayyor bolmagan dvigatel xato beradi`() {
        val engine = FakeEngine(ready = false)
        val result = builder(engine, tempDir()).build(plan(*boblar), tempDir(), "Kitob")

        assertEquals(BookBuildError.NOT_READY, (result as BookBuildOutcome.Failed).error)
        assertTrue("dvigatel ishga tushirilmadi", engine.spoken.isEmpty())
    }

    @Test
    fun `sintezator xatosi bob raqami bilan qaytadi`() {
        // Ikkinchi bobning bo'lagi yiqiladi: xato qaysi bobda ekani
        // ko'rsatilishi kerak — aks holda uch yuz bobli kitobda
        // foydalanuvchi qayerdan boshlashni bilmasdi.
        val engine = FakeEngine(failOn = "Ertalab havo ochiq edi.")
        val out = tempDir()
        val result = builder(engine, tempDir()).build(plan(*boblar), out, "Kitob")

        val failed = result as BookBuildOutcome.Failed
        assertEquals(BookBuildError.SPEAK_FAILED, failed.error)
        assertEquals(1, failed.chapterIndex)
        // Birinchi bob yozilgan bo'lsa ham qoldirilmaydi: kitob butun
        // bo'lmasa, uning bir qismi foydalanuvchini chalg'itardi.
        assertEquals(0, out.listFiles()?.count { it.extension == "mp3" } ?: 0)
    }

    @Test
    fun `javob kelmasa kutish tugaydi`() {
        // Jim qolgan dvigatel butun kitobni osib qo'ymasligi kerak.
        val engine = FakeEngine(silent = true)
        val result = builder(engine, tempDir(), timeout = 100)
            .build(plan(*boblar), tempDir(), "Kitob")

        assertEquals(BookBuildError.SPEAK_FAILED, (result as BookBuildOutcome.Failed).error)
        assertTrue("dvigatel bo'shatilmadi", engine.stopped)
    }

    @Test
    fun `toxtatish yarim yo'lda ishlaydi`() {
        lateinit var builder: BookBuilder
        var count = 0
        val engine = FakeEngine(
            beforeResult = {
                // Ikkinchi bo'lakda foydalanuvchi «to'xtat» bosdi.
                if (++count == 2) builder.cancel()
            }
        )
        val work = tempDir()
        val out = tempDir()
        builder = BookBuilder(engine = engine, workDir = work)

        val result = builder.build(plan(*boblar), out, "Kitob")

        assertEquals(BookBuildError.CANCELLED, (result as BookBuildOutcome.Failed).error)
        assertTrue("dvigatel to'xtatilmadi", engine.stopped)
        assertEquals("ishchi papka tozalanmadi", 0, work.listFiles()?.size ?: 0)
        assertFalse("yarim bob qoldirildi", out.listFiles()?.any { it.extension == "mp3" } ?: false)
    }

    @Test
    fun `toxtatilgandan keyin qayta yigiladi`() {
        // To'xtatish faqat shu yig'ishga tegishli: yangi yig'ish toza
        // boshlanadi, aks holda foydalanuvchi «qayta urinish» tugmasini
        // bosganda hech narsa bo'lmasdi.
        val engine = FakeEngine()
        val interrupted = builder(engine, tempDir())
        interrupted.cancel()
        assertTrue(interrupted.build(plan(*boblar), tempDir(), "Kitob") is BookBuildOutcome.Failed)

        val fresh = builder(FakeEngine(), tempDir())
        assertTrue(fresh.build(plan(*boblar), tempDir(), "Kitob") is BookBuildOutcome.Done)
    }

    @Test
    fun `fon vazifasi matnlari sintezga uzatiladi`() {
        val engine = FakeEngine()
        builder(engine, tempDir()).build(plan(*boblar), tempDir(), "Kitob")

        // Har bir bob: sarlavha (e'lon qilinadi) + matn.
        assertEquals(
            listOf("1-BOB", "Salim aka qishloqdan keldi.", "2-BOB", "Ertalab havo ochiq edi."),
            engine.spoken,
        )
    }

    @Test
    fun `jarayon xabari boblarni sanaydi`() {
        val steps = ArrayList<BookProgress>()
        val plan = plan(*boblar)
        builder(FakeEngine(), tempDir()).build(plan, tempDir(), "Kitob") { steps += it }

        assertEquals(4, steps.size)
        assertEquals(4, steps.last().done)
        assertEquals(4, steps.last().total)
        assertEquals(2, steps.last().chapters)
        assertEquals(100, steps.last().percent)
        assertEquals(25, steps.first().percent)
    }

    @Test
    fun `fayl nomi fayl tizimiga mos keladi`() {
        assertEquals(
            "Kitob - 01 - 1-BOB.mp3",
            BookBuilder.chapterFileName("Kitob", 0, "1-BOB"),
        )
        // Yo'l belgilari nomga tushmasligi kerak: aks holda fayl umuman
        // yaratilmasdi yoki boshqa papkaga tushib qolardi.
        val nom = BookBuilder.chapterFileName("Kitob", 4, "Kirish/Chiqish: 2-qism")
        assertFalse(nom, nom.contains('/'))
        assertTrue(nom, nom.startsWith("Kitob - 05 - Kirish"))
    }

    @Test
    fun `kirill sarlavhasi saqlanadi`() {
        val nom = BookBuilder.chapterFileName("Китоб", 0, "БИРИНЧИ БОБ")
        assertEquals("Китоб - 01 - БИРИНЧИ БОБ.mp3", nom)
    }

    @Test
    fun `uzun nom qisqartiriladi`() {
        val uzun = "a".repeat(200)
        val nom = BookBuilder.chapterFileName(uzun, 0, uzun)
        assertTrue("nom juda uzun: ${nom.length}", nom.length < 120)
    }
}
