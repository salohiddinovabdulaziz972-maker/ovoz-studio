package uz.ovozstudio.app.media.book

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.format.AudioCodec
import uz.ovozstudio.app.media.format.AudioContainer
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.Mp3Encoder

class ChapterAssemblerTest {

    private fun tempFile(suffix: String): File {
        val file = File.createTempFile("ovozstudio-bob", suffix)
        file.deleteOnExit()
        return file
    }

    private fun wav(frames: Int, sampleRate: Int, offset: Int = 0): File {
        val file = tempFile(".wav")
        WavWriter(file, sampleRate, 1, BitDepth.BIT_16).use { writer ->
            val samples = IntArray(frames) { ((offset + it) * 50 % 32_000) }
            writer.writeIntegers(samples, frames)
        }
        return file
    }

    private val plan = BookChapterPlan(
        index = 0,
        title = "1-BOB",
        utterances = listOf(
            BookUtterance("1-BOB", 0, 0, isTitle = true),
            BookUtterance("Salim aka haqida", 0, 16, isTitle = false),
        ),
    )

    @Test
    fun `bolaklar bitta mp3 faylga yigiladi`() {
        val parts = listOf(wav(frames = 2205, sampleRate = 22_050), wav(2205, 22_050, 10_000))
        val audio = tempFile(".mp3")

        val chapter = ChapterAssembler.assemble(
            parts = parts,
            joinTarget = tempFile(".wav"),
            audioTarget = audio,
            target = AudioTarget.MP3,
            plan = plan,
        )

        assertEquals(audio, chapter.audio)
        assertTrue("fayl juda kichik", audio.length() > 200)
        // MP3 kadr sinxronizatsiyasi: 11 ta bir bit.
        val head = audio.readBytes().take(2)
        assertEquals(0xFF, head[0].toInt() and 0xFF)
        assertEquals(0xE0, head[1].toInt() and 0xE0)
    }

    @Test
    fun `belgilar pauzani hisobga oladi`() {
        // Belgilar bo'laklar soni bo'yicha emas, qo'shilgan fayldagi kadr
        // bo'yicha hisoblanadi — aks holda ular asta-sekin siljib ketardi.
        val parts = listOf(wav(frames = 2205, sampleRate = 22_050), wav(2205, 22_050))
        val chapter = ChapterAssembler.assemble(
            parts = parts,
            joinTarget = tempFile(".wav"),
            audioTarget = tempFile(".mp3"),
            target = AudioTarget.MP3,
            plan = plan,
            gapMs = 400,
        )

        assertEquals(2, chapter.markers.size)
        // 22050 Hz da 400 ms — 8820 kadr.
        assertEquals(2205L + 8820L, chapter.markers[1].startFrame)
        assertEquals("Salim aka haqida", chapter.markers[1].title)
    }

    @Test
    fun `davomiylik qoshilgan fayldan olinadi`() {
        val chapter = ChapterAssembler.assemble(
            parts = listOf(wav(frames = 2205, sampleRate = 22_050), wav(2205, 22_050)),
            joinTarget = tempFile(".wav"),
            audioTarget = tempFile(".mp3"),
            target = AudioTarget.MP3,
            plan = plan,
            gapMs = 400,
        )

        // 2205 + 8820 + 2205 = 13230 kadr; 22050 Hz da bu 600 ms.
        assertEquals(600L, chapter.durationMs)
    }

    @Test
    fun `oraliq wav fayl ochiriladi`() {
        // Oraliq fayl telefon xotirasida qolib ketmasligi kerak: kitob
        // yuzlab bo'lakdan iborat bo'ladi.
        val join = tempFile(".wav")
        ChapterAssembler.assemble(
            parts = listOf(wav(frames = 2205, sampleRate = 22_050)),
            joinTarget = join,
            audioTarget = tempFile(".mp3"),
            target = AudioTarget.MP3,
            plan = plan,
        )

        assertFalse("oraliq fayl qoldi", join.exists())
    }

    @Test
    fun `oraliq fayl xatolikda ham ochiriladi`() {
        val join = tempFile(".wav")
        val bad = tempFile(".mp3")
        bad.delete()
        bad.mkdirs() // papka: fayl sifatida ochib bo'lmaydi

        assertThrows(Exception::class.java) {
            ChapterAssembler.assemble(
                parts = listOf(wav(frames = 2205, sampleRate = 22_050)),
                joinTarget = join,
                audioTarget = bad,
                target = AudioTarget.MP3,
                plan = plan,
            )
        }
        assertFalse("xatolikdan keyin oraliq fayl qoldi", join.exists())
    }

    @Test
    fun `sintezatorning past chastotasi ham kodlanadi`() {
        // Aynan shu holat eksportni yiqitardi: 128 kbit/s MPEG-2.5 uchun
        // ruxsat etilmagan, LAME esa bunday so'rovda umuman ishga
        // tushmaydi. Bit tezligi chastotaga moslashtirilishi kerak.
        val chapter = ChapterAssembler.assemble(
            parts = listOf(wav(frames = 800, sampleRate = 8_000)),
            joinTarget = tempFile(".wav"),
            audioTarget = tempFile(".mp3"),
            target = AudioTarget.MP3,
            plan = plan,
            gapMs = 0,
        )

        assertTrue("fayl bo'sh", chapter.audio.length() > 100)
    }

    @Test
    fun `bit tezligi sintezator chastotasiga moslashadi`() {
        // O'lchangan haqiqat (native libmp3lame, ffprobe): 8 kHz da 128 kbit/s
        // so'ralsa ham LAME 64 kbit/s yozadi — ya'ni u jimgina tushiradi.
        // Biz bunga tayanmaymiz: qiymatni o'zimiz hisoblaymiz, natija esa
        // kodlovchining bag'rikengligiga bog'liq bo'lib qolmaydi.
        val info8k = WavInfo(sampleRate = 8_000, channels = 1, bitsPerSample = 16, dataOffset = 44, dataSize = 0)
        val info22k = WavInfo(sampleRate = 22_050, channels = 1, bitsPerSample = 16, dataOffset = 44, dataSize = 0)
        val info48k = WavInfo(sampleRate = 48_000, channels = 1, bitsPerSample = 16, dataOffset = 44, dataSize = 0)

        val high = AudioTarget(AudioContainer.MP3, AudioCodec.MP3, 320_000)

        // 8 kHz — MPEG-2.5: 128 kbit/s chegaradan katta, 64 ga tushadi.
        assertEquals(64_000, AudioTarget.MP3.formatFor(info8k).bitrate)
        // 22.05 kHz — MPEG-2: 128 chegaraga sig'adi, o'zgarmaydi...
        assertEquals(128_000, AudioTarget.MP3.formatFor(info22k).bitrate)
        // ...320 esa sig'maydi, 160 ga tushadi.
        assertEquals(160_000, high.formatFor(info22k).bitrate)
        // 48 kHz — MPEG-1: to'liq zinapoya.
        assertEquals(128_000, AudioTarget.MP3.formatFor(info48k).bitrate)
        assertEquals(320_000, high.formatFor(info48k).bitrate)
    }

    @Test
    fun `qollab bolmaydigan chastota kodlovchini yiqitadi`() {
        // MP3 jadvalidan tashqaridagi chastota LAME'ni umuman ishga
        // tushirmaydi (o'lchandi: 47 kHz → kod -1). Shu sababli
        // `CodecRates.supports` MP3 uchun jadvalni tekshiradi.
        assertThrows(IllegalStateException::class.java) {
            Mp3Encoder(
                tempFile(".mp3"),
                AudioFormat(
                    container = AudioContainer.MP3,
                    codec = AudioCodec.MP3,
                    sampleRate = 47_000,
                    channels = 1,
                    bitDepth = 16,
                    bitrate = 128_000,
                ),
            )
        }
    }

    @Test
    fun `format sintezator parametrlaridan olinadi`() {
        val chapter = ChapterAssembler.assemble(
            parts = listOf(wav(frames = 800, sampleRate = 44_100)),
            joinTarget = tempFile(".wav"),
            audioTarget = tempFile(".mp3"),
            target = AudioTarget.MP3,
            plan = plan,
            gapMs = 0,
        )

        assertTrue(chapter.audio.length() > 100)
        val head = chapter.audio.readBytes().take(2)
        assertEquals(0xFF, head[0].toInt() and 0xFF)
    }

    @Test
    fun `bolaklar bosh bolsa xato beradi`() {
        assertThrows(BookAssemblyException::class.java) {
            ChapterAssembler.assemble(
                parts = emptyList(),
                joinTarget = tempFile(".wav"),
                audioTarget = tempFile(".mp3"),
                target = AudioTarget.MP3,
                plan = plan,
            )
        }
    }
}
