package uz.ovozstudio.app.media.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.doc.Chapter
import uz.ovozstudio.app.media.format.Mp3Encoder
import uz.ovozstudio.app.media.voice.SpeechListener
import uz.ovozstudio.app.media.voice.SpeechRequest
import uz.ovozstudio.app.media.voice.TextScript
import uz.ovozstudio.app.media.voice.VoiceEngine
import uz.ovozstudio.app.media.voice.VoiceError
import uz.ovozstudio.app.media.voice.VoiceInfo
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bob kodlash sintez bilan bir vaqtda ketishi (konveyer) sinovi.
 *
 * Asosiy oqim `BookBuilderTest` da. Bu yerda faqat konveyer keltirib
 * chiqaradigan xavflar: tartib buzilmasligi, kodlash xatosining bob raqami
 * bilan qaytishi va — eng muhimi — kodlash HAQIQATAN keyingi bobning
 * sintezini kutmasligi.
 */
class BookBuilderPipelineTest {

    /** Soxta sintezator: har bir matn uchun qisqa WAV yozadi. */
    private class Engine(private val onSpeak: (String) -> Unit = {}) : VoiceEngine {

        override fun prepare(onResult: (VoiceError?) -> Unit) = onResult(null)

        override fun isReady(): Boolean = true

        override fun voices(): List<VoiceInfo> = emptyList()

        override fun resolveLanguage(script: TextScript): String = "uz-UZ"

        override fun speak(request: SpeechRequest, listener: SpeechListener) = Unit

        override fun synthesizeToFile(
            request: SpeechRequest,
            output: File,
            onResult: (VoiceError?) -> Unit,
        ) {
            onSpeak(request.text)
            WavWriter(output, 22_050, 1, BitDepth.BIT_16).use { writer ->
                writer.writeIntegers(IntArray(400) { (it * 7) % 20_000 }, 400)
            }
            onResult(null)
        }

        override fun stop() = Unit

        override fun release() = Unit
    }

    private fun tempDir(): File {
        val dir = File.createTempFile("ovozstudio-konveyer", "")
        dir.delete()
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun chapter(title: String, text: String) = Chapter(title, text, 0, text.length)

    private fun chapters(count: Int) =
        (1..count).map { chapter("$it-BOB", "Bu $it-bobning matni.") }

    @Test
    fun `kodlash keyingi bobning sintezi bilan bir vaqtda ketadi`() {
        val secondStarted = CountDownLatch(1)
        val opened = AtomicInteger(0)
        val engine = Engine(onSpeak = { text -> if (text == "2-BOB") secondStarted.countDown() })
        val builder = BookBuilder(
            engine = engine,
            workDir = tempDir(),
            openEncoder = { file, format ->
                // Birinchi bobning kodlovchisi 2-bobning sintezi boshlanmaguncha
                // ochilmaydi. Kodlash sintezni kutib tursa (ketma-ket tartib),
                // bu shart hech qachon bajarilmaydi va kodlash yiqiladi.
                if (opened.incrementAndGet() == 1) {
                    if (!secondStarted.await(10, TimeUnit.SECONDS)) {
                        throw IOException("kodlash keyingi bobning sintezini kutdi")
                    }
                }
                Mp3Encoder(file, format)
            },
        )

        val result = builder.build(BookPlanner.plan(chapters(2)), tempDir(), "Kitob")

        assertTrue(result.toString(), result is BookBuildOutcome.Done)
        assertEquals(2, (result as BookBuildOutcome.Done).book.chapters.size)
    }

    @Test
    fun `boblar tartibi kodlash parallel ketganda ham saqlanadi`() {
        val result = BookBuilder(engine = Engine(), workDir = tempDir())
            .build(BookPlanner.plan(chapters(4)), tempDir(), "Kitob")

        val book = (result as BookBuildOutcome.Done).book
        assertEquals(listOf(0, 1, 2, 3), book.chapters.map { it.index })
        assertEquals(listOf("1-BOB", "2-BOB", "3-BOB", "4-BOB"), book.chapters.map { it.title })
    }

    @Test
    fun `kodlash xatosi bob raqami bilan qaytadi va iz qoldirmaydi`() {
        val opened = AtomicInteger(0)
        val work = tempDir()
        val out = tempDir()
        val builder = BookBuilder(
            engine = Engine(),
            workDir = work,
            openEncoder = { file, format ->
                // Ikkinchi bobning kodlovchisi ochilmaydi (masalan, disk to'ldi).
                if (opened.incrementAndGet() == 2) throw IOException("disk to'ldi")
                Mp3Encoder(file, format)
            },
        )

        val result = builder.build(BookPlanner.plan(chapters(3)), out, "Kitob")

        val failed = result as BookBuildOutcome.Failed
        assertEquals(BookBuildError.ASSEMBLY_FAILED, failed.error)
        assertEquals(1, failed.chapterIndex)
        // Yarim kitob qoldirilmaydi: birinchi bob ham o'chiriladi.
        assertEquals(0, out.listFiles()?.count { it.extension == "mp3" } ?: 0)
        assertEquals("ishchi papkada fayl qoldi", 0, work.listFiles()?.size ?: 0)
    }
}
