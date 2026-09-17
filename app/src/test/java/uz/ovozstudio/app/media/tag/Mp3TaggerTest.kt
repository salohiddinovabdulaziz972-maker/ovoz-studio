package uz.ovozstudio.app.media.tag

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teg yozish — fayl darajasidagi amallar.
 *
 * Bu yerda sinov mavzusi «teg chiroyli ko'rinadimi» emas, **fayl
 * buzilmadimi**: ovoz baytlari o'z joyida qolganmi, eski teg ustiga
 * qo'shilib ketmaganmi, manba fayl tegilganmi. Ilovaning qoidasi —
 * amal natijasi har doim yangi fayl, manba esa o'zgarmas.
 */
class Mp3TaggerTest {

    private fun dir(): File {
        val dir = File.createTempFile("ovozstudio-teg", "")
        dir.delete()
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    /** Ovoz o'rnidagi baytlar: mazmuni muhim emas, muhimi — aynan qaytishi. */
    private fun audio(size: Int = 500): ByteArray =
        ByteArray(size) { ((it * 7) % 251).toByte() }

    private fun file(name: String, bytes: ByteArray): File =
        File(dir(), name).apply { writeBytes(bytes) }

    private fun tagged(file: File, tags: AudioTags): File {
        val target = File(file.parentFile, "teg-${file.name}")
        Mp3Tagger.write(file, target, tags)
        return target
    }

    @Test
    fun `tegsiz faylga teg qoshiladi va ovoz ozgarmaydi`() {
        val body = audio()
        val source = file("manba.mp3", body)

        val result = tagged(source, AudioTags(title = "Birinchi bob", artist = "Husanboy"))

        val tagSize = Mp3Tagger.id3v2Size(result)
        assertTrue("teg yozilmadi", tagSize > 0)
        assertEquals("ID3", String(result.readBytes(), 0, 3, Charsets.US_ASCII))
        assertArrayEquals(
            "ovoz baytlari o'zgargan",
            body,
            result.readBytes().copyOfRange(tagSize.toInt(), result.readBytes().size),
        )
    }

    @Test
    fun `manba fayl tegilmaydi`() {
        val body = audio()
        val source = file("manba.mp3", body)
        tagged(source, AudioTags(title = "Kitob"))

        assertArrayEquals("manba o'zgardi", body, source.readBytes())
    }

    @Test
    fun `eski teg ustiga qoshilmaydi`() {
        val body = audio()
        val source = file("manba.mp3", body)
        // Birinchi teg: eski nom.
        val first = tagged(source, AudioTags(title = "Eski nom"))
        // Ikkinchi teg: yangi nom. Fayl yangi manba bo'lib qoladi.
        val second = File(first.parentFile, "ikkinchi.mp3")
        Mp3Tagger.write(first, second, AudioTags(title = "Yangi nom"))

        val bytes = second.readBytes()
        assertEquals("faylda ikkita teg bor", 1, countOf(bytes, "ID3".toByteArray(Charsets.US_ASCII)))
        val tagSize = Mp3Tagger.id3v2Size(second)
        assertEquals("teg sarlavhasi ko'chib ketdi", 500, second.length() - tagSize)
        assertTrue("yangi nom yo'q", contains(bytes, "Yangi nom"))
        assertFalse("eski nom qolib ketdi", contains(bytes, "Eski nom"))
    }

    @Test
    fun `eski id3v1 tegi tashlanadi`() {
        val body = audio()
        // ID3v1: 128 bayt, boshida "TAG", ichida eski nom.
        val v1 = ByteArray(128)
        "TAG".toByteArray(Charsets.US_ASCII).copyInto(v1)
        "Eski nom".toByteArray(Charsets.US_ASCII).copyInto(v1, 3)
        val source = file("manba.mp3", body + v1)

        val result = tagged(source, AudioTags(title = "Yangi nom"))

        assertEquals(
            "ID3v1 qoldirildi",
            500,
            result.length() - Mp3Tagger.id3v2Size(result),
        )
        val bytes = result.readBytes()
        assertFalse("eski nom qolib ketdi", contains(bytes, "Eski nom"))
        assertFalse("oxirida TAG qoldi", contains(bytes, "TAG"))
    }

    @Test
    fun `ovoz tugash joyi togri topiladi`() {
        val body = audio(300)
        val tegsiz = file("tegsiz.mp3", body)
        assertEquals(0L, Mp3Tagger.audioStart(tegsiz))
        assertEquals(300L, Mp3Tagger.audioEnd(tegsiz))

        // Tegdan keyin ovoz baytlari aynan o'sha joyda turishi kerak:
        // bitta bayt siljisa ham pleyer faylni ochmaydi.
        val withTag = tagged(file("manba.mp3", body), AudioTags(title = "Kitob"))
        val bytes = withTag.readBytes()
        val start = Mp3Tagger.audioStart(withTag).toInt()
        assertTrue("teg fayl boshida emas", start > 0)
        assertEquals("teg hajmi kutilganidan boshqa", 35, start)
        assertArrayEquals(body, bytes.copyOfRange(start, Mp3Tagger.audioEnd(withTag).toInt()))
    }

    @Test
    fun `teg deb faqat haqiqiy teg tan olinadi`() {
        // Audio oqimida "ID3" degan uch bayt uchrashi mumkin — o'lchami
        // va versiyasi ham teg bo'lishi kerak.
        val fake = file("yolgon.mp3", "ID3salom dunyo".toByteArray(Charsets.US_ASCII))
        assertEquals(0, Mp3Tagger.id3v2Size(fake))

        val short = file("qisqa.mp3", "ID".toByteArray(Charsets.US_ASCII))
        assertEquals(0, Mp3Tagger.id3v2Size(short))
    }

    @Test
    fun `bosh faylga teg yozilmaydi`() {
        val empty = file("bosh.mp3", ByteArray(0))
        assertThrows(TagWriteException::class.java) {
            Mp3Tagger.write(empty, File(empty.parentFile, "natija.mp3"), AudioTags(title = "K"))
        }
    }

    @Test
    fun `juda katta muqova rad etiladi`() {
        val source = file("manba.mp3", audio())
        val huge = ByteArray(Mp3Tagger.MAX_COVER_BYTES + 1)
        assertThrows(TagWriteException::class.java) {
            Mp3Tagger.write(
                source,
                File(source.parentFile, "natija.mp3"),
                AudioTags(title = "K", cover = huge),
            )
        }
    }

    @Test
    fun `bir faylga ozini yozib bolmaydi`() {
        val source = file("manba.mp3", audio())
        assertThrows(IllegalArgumentException::class.java) {
            Mp3Tagger.write(source, source, AudioTags(title = "K"))
        }
        // Va fayl shikastlanmagan.
        assertEquals(500, source.length())
    }

    @Test
    fun `xato bolganda natija fayli yaratilmaydi`() {
        val source = file("manba.mp3", audio())
        val target = File(source.parentFile, "natija.mp3")
        assertThrows(TagWriteException::class.java) {
            Mp3Tagger.write(source, target, AudioTags(cover = ByteArray(Mp3Tagger.MAX_COVER_BYTES + 1)))
        }
        // Yaroqsiz fayl ro'yxatda qolmasligi kerak: muvaffaqiyatsiz urinish
        // hech qanday iz qoldirmaydi.
        assertFalse("yaratilmagan fayl paydo bo'ldi", target.exists())
    }

    @Test
    fun `eski natija ustiga toliq yoziladi`() {
        val source = file("manba.mp3", audio())
        val target = File(source.parentFile, "natija.mp3")
        // O'tgan urinishdan qolgan uzun fayl: ustiga yozilganda oxirida
        // eski baytlar qolib ketmasligi kerak (fayl «uzunroq» bo'lib,
        // pleyer oxirida axlat o'qirdi).
        target.writeBytes(ByteArray(900) { 0x7F })
        Mp3Tagger.write(source, target, AudioTags(title = "Kitob"))

        val tagSize = Mp3Tagger.id3v2Size(target)
        assertEquals("eski baytlar qoldi", 500, target.length() - tagSize)
        assertArrayEquals(audio(), target.readBytes().copyOfRange(tagSize.toInt(), target.readBytes().size))
    }

    @Test
    fun `teg bolmagan faylda ovoz boshidan boshlanadi`() {
        val source = file("manba.mp3", audio(64))
        assertEquals(0L, Mp3Tagger.audioStart(source))
        assertEquals(64L, Mp3Tagger.audioEnd(source))
    }

    /** Baytlar ichida ketma-ketlik necha marta uchraydi. */
    private fun countOf(bytes: ByteArray, target: ByteArray): Int {
        var count = 0
        for (start in 0..bytes.size - target.size) {
            if (target.indices.all { bytes[start + it] == target[it] }) count++
        }
        return count
    }

    /** Satr ASCII yoki UTF-16 ko'rinishida uchraydimi. */
    private fun contains(bytes: ByteArray, needle: String): Boolean =
        countOf(bytes, needle.toByteArray(Charsets.US_ASCII)) > 0 ||
            countOf(bytes, needle.toByteArray(Charsets.UTF_16LE)) > 0
}
