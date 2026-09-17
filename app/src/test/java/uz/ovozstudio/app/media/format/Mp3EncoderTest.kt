package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import java.io.File

/**
 * MP3 kodlovchisining tuzilish sinovlari.
 *
 * Bu yerda oqim **dekodlanmaydi**: sinov faqat MPEG kadrlar tuzilishini
 * tekshiradi (sinxronizatsiya, bit tezligi, kadr o'lchami). Ovozning
 * to'g'riligi esa mustaqil dekoder bilan — `bin/verify-mp3.sh` (ffmpeg)
 * orqali tekshiriladi. Sabab `docs/PROGRESS.md` da yozilgan: o'z-o'zini
 * tekshirish "to'g'ri ko'rinadi" da to'xtaydi.
 */
class Mp3EncoderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val mp3 = AudioFormat(AudioContainer.MP3, AudioCodec.MP3, 44_100, 2, 16, bitrate = 192_000)

    private fun writeWav(name: String, channels: Int, depth: BitDepth, frames: Int): File {
        val file = File(folder.root, name)
        val samples = IntArray(frames * channels)
        val amp = (1 shl (depth.bits - 1)) - 1
        for (t in 0 until frames) {
            val v = (Math.sin(2.0 * Math.PI * 440 * t / 44_100.0) * amp / 3).toInt()
            for (c in 0 until channels) samples[t * channels + c] = if (c == 1) v / 3 else v
        }
        WavWriter(file, 44_100, channels, depth).use { it.writeIntegers(samples, frames) }
        return file
    }

    /** Kodlangan faylni kadrlarga bo'lib, har birining o'lchamini qaytaradi. */
    private fun frameSizes(file: File): List<Int> {
        val bytes = file.readBytes()
        val sizes = ArrayList<Int>()
        var offset = 0
        while (offset + 4 <= bytes.size) {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            // Sinxronizatsiya so'zi: 11 bit bir, keyin MPEG-1 (11) va
            // Layer III (01) kelishi shart.
            if (b0 != 0xFF || b1 != 0xFB) break
            val bitrateIndex = (bytes[offset + 2].toInt() and 0xF0) shr 4
            val rateIndex = (bytes[offset + 2].toInt() and 0x0C) shr 2
            val padding = (bytes[offset + 2].toInt() and 0x02) shr 1
            // Jadval kbit/s da, kadr o'lchami esa bit/s ni talab qiladi.
            val bitrate = MPEG1_LAYER3_BITRATES.getOrNull(bitrateIndex) ?: break
            val rate = MPEG1_RATES.getOrNull(rateIndex) ?: break
            val size = 144 * bitrate * 1000 / rate + padding
            if (size <= 4) break
            sizes.add(size)
            offset += size
        }
        return sizes
    }

    @Test
    fun `oqim mpeg kadrlardan tuzilgan va oxirigacha ochiladi`() {
        val source = writeWav("manba.wav", 2, BitDepth.BIT_16, 44_100)
        val destination = File(folder.root, "natija.mp3")

        val outcome = FormatPreservingExporter(34).export(source, mp3, destination)
        outcome as ExportOutcome.Done
        assertEquals(44_100L, outcome.frames)

        val sizes = frameSizes(destination)
        val head = destination.readBytes().take(8).joinToString(" ") { "%02X".format(it) }
        // 1 sekund uchun 44100/1152 ≈ 38 kadr; Xing/Info kadri ham shu yerda.
        assertTrue("kadrlar topilmadi: ${sizes.size} ta, hajm=${destination.length()}, bosh=$head",
            sizes.size >= 38)
        // 192 kbps, 44100 Hz: kadr 626 yoki 627 bayt bo'lishi shart.
        assertTrue("kutilmagan kadr o'lchami: ${sizes.take(4)}",
            sizes.all { it == 626 || it == 627 })
    }

    @Test
    fun `bit tezligi fayl hajmida korinadi`() {
        val source = writeWav("manba.wav", 2, BitDepth.BIT_16, 44_100)

        val slow = File(folder.root, "sekin.mp3")
        val fast = File(folder.root, "tez.mp3")
        FormatPreservingExporter(34).export(source, mp3.copy(bitrate = 96_000), slow)
        FormatPreservingExporter(34).export(source, mp3.copy(bitrate = 320_000), fast)

        assertTrue("320 kbps 96 kbps dan katta bo'lishi kerak", fast.length() > slow.length() * 2)
    }

    @Test
    fun `yigirma tort bitli manba shkalasi uzatiladi`() {
        // Bu — topilgan xatoning qo'riqchisi. Kodlovchi namunalarni
        // `format.bitDepth` bo'yicha shkalalaydi; yo'qotishli konteynerda
        // bu maydon `null` bo'lib qolsa, 24-bit manba 16-bit deb hisoblanib,
        // ovoz butunlay buzilardi.
        val source = writeWav("chuqur.wav", 1, BitDepth.BIT_24, 20_000)
        val destination = File(folder.root, "chuqur.mp3")

        val mono = mp3.copy(channels = 1, bitDepth = null)
        val outcome = FormatPreservingExporter(34).export(source, mono, destination) as ExportOutcome.Done

        assertEquals(24, outcome.format.bitDepth)
        val sizes = frameSizes(destination)
        val head = destination.readBytes().take(8).joinToString(" ") { "%02X".format(it) }
        assertTrue("kadrlar topilmadi: ${sizes.size} ta, hajm=${destination.length()}, bosh=$head",
            sizes.isNotEmpty())
    }

    @Test
    fun `uch kanalli manba rad etiladi`() {
        val file = File(folder.root, "kop.mp3")
        val bad = mp3.copy(channels = 3)
        val error = runCatching { Mp3Encoder(file, bad) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue("fayl yaratilmasligi kerak", !file.exists())
    }

    @Test
    fun `yoqotishsiz kodek rad etiladi`() {
        val file = File(folder.root, "flac.mp3")
        val bad = mp3.copy(codec = AudioCodec.FLAC)
        val error = runCatching { Mp3Encoder(file, bad) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `finish ikki marta chaqirilsa ham xato bermaydi`() {
        val file = File(folder.root, "ikki.mp3")
        val encoder = Mp3Encoder(file, mp3.copy(channels = 1))
        encoder.write(IntArray(1_152), 1_152)
        encoder.finish()
        val afterFirst = file.length()
        encoder.finish()
        assertEquals(afterFirst, file.length())
        // Yopilgandan keyin yozish — dastur xatosi, jimgina o'tib ketmasin.
        assertTrue(runCatching { encoder.write(IntArray(1_152), 1_152) }.exceptionOrNull() is IllegalStateException)
    }

    private companion object {
        /** MPEG-1 Layer III bit tezliklari (kbit/s), indeks bo'yicha. */
        val MPEG1_LAYER3_BITRATES = intArrayOf(
            0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320,
        )

        /** MPEG-1 namuna chastotalari, indeks bo'yicha. */
        val MPEG1_RATES = intArrayOf(44_100, 48_000, 32_000)
    }
}
