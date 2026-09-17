package uz.ovozstudio.app.media.tag

import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mavjud tegni o'qish.
 *
 * Nega bu sinovlar muhim: yozuvchi tegni butunlay almashtiradi. O'quvchi
 * noto'g'ri ishlasa, foydalanuvchi faqat muqovani qo'shmoqchi bo'lib,
 * ochilgan maydonlar bo'sh qoladi va eski nom jimgina o'chib ketadi.
 *
 * Sinovlar **turli kodlashlarni** qamraydi: fayl bizning ilova bilan emas,
 * boshqa dastur bilan yozilgan bo'lishi mumkin — u ISO-8859-1, UTF-8 yoki
 * BOM'siz UTF-16 ni tanlashi mumkin.
 */
class Id3v2ReaderTest {

    @Test
    fun `ozimiz yozgan teg ozimizga qaytadi`() {
        val cover = ByteArray(64) { (it * 3).toByte() }
        val tags = AudioTags(
            title = "Ўткан кунлар",
            artist = "Абдулла Қодирий",
            album = "Kitob",
            year = "2025",
            genre = "Audiobook",
            track = 3,
            trackTotal = 12,
            cover = cover,
        )

        val read = Id3v2Reader.parse(Id3v2Tag.build(tags))

        assertEquals(tags.title, read.title)
        assertEquals(tags.artist, read.artist)
        assertEquals(tags.album, read.album)
        assertEquals(tags.year, read.year)
        assertEquals(tags.genre, read.genre)
        assertEquals(3, read.track)
        assertEquals(12, read.trackTotal)
        assertArrayEquals(cover, read.cover)
    }

    @Test
    fun `lotin matni ham buzilmaydi`() {
        val tags = AudioTags(title = "Salom, dunyo", artist = "Husanboy")
        val read = Id3v2Reader.parse(Id3v2Tag.build(tags))
        assertEquals("Salom, dunyo", read.title)
        assertEquals("Husanboy", read.artist)
    }

    @Test
    fun `latin1 kodlashi oqiladi`() {
        // Boshqa dasturlar ko'pincha eng sodda kodlashni tanlaydi: bitta
        // bayt — bitta belgi.
        val bytes = latin1Frame("TIT2", "Kadr nomi")
        val read = Id3v2Reader.parse(tag(version = 3, frames = bytes))
        assertEquals("Kadr nomi", read.title)
    }

    @Test
    fun `utf8 kodlashi oqiladi`() {
        val payload = byteArrayOf(3) + "Китоб".toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val read = Id3v2Reader.parse(tag(version = 3, frames = Id3v2Tag.frame("TIT2", payload)))
        assertEquals("Китоб", read.title)
    }

    @Test
    fun `bomsiz utf16 kodlashi oqiladi`() {
        val payload = byteArrayOf(2) + "Kitob".toByteArray(Charsets.UTF_16BE) + byteArrayOf(0, 0)
        val read = Id3v2Reader.parse(tag(version = 3, frames = Id3v2Tag.frame("TIT2", payload)))
        assertEquals("Kitob", read.title)
    }

    @Test
    fun `bomli utf16 ikki tartibda ham oqiladi`() {
        val le = byteArrayOf(1, 0xFF.toByte(), 0xFE.toByte()) +
            "Kitob".toByteArray(Charsets.UTF_16LE) + byteArrayOf(0, 0)
        val be = byteArrayOf(1, 0xFE.toByte(), 0xFF.toByte()) +
            "Kitob".toByteArray(Charsets.UTF_16BE) + byteArrayOf(0, 0)
        assertEquals("Kitob", Id3v2Reader.parse(tag(3, Id3v2Tag.frame("TIT2", le))).title)
        assertEquals("Kitob", Id3v2Reader.parse(tag(3, Id3v2Tag.frame("TIT2", be))).title)
    }

    @Test
    fun `v24 dagi synchsafe kadr olchami oqiladi`() {
        // 2.4 da kadr o'lchami ham synchsafe. Farqi faqat uzun matnda
        // ko'rinadi: 127 baytdan katta o'lchamda baytlar suriladi.
        val long = "K".repeat(200)
        val read = Id3v2Reader.parse(tag(version = 4, frames = frame24("TIT2", text(long))))
        assertEquals(long, read.title)
    }

    @Test
    fun `v24 da olcham korsatkichi tashlanadi`() {
        // «O'lcham ko'rsatkichi» bayrog'i mazmun boshiga to'rt bayt qo'shadi.
        // Ular tashlanmasa, nom boshida axlat ko'rinardi.
        val inner = text("Kitob")
        val payload = ByteArray(4) + inner
        val frame = frame24("TIT2", payload, flags = 0x01)
        assertEquals("Kitob", Id3v2Reader.parse(tag(4, frame)).title)
    }

    @Test
    fun `kadrlar orasidagi toldiruvchi xalaqit bermaydi`() {
        val frames = Id3v2Tag.frame("TIT2", text("Kitob")) + ByteArray(50)
        assertEquals("Kitob", Id3v2Reader.parse(tag(3, frames)).title)
    }

    @Test
    fun `teg bolmasa bosh teg qaytadi`() {
        assertTrue(Id3v2Reader.parse(ByteArray(0)).isEmpty)
        assertTrue(Id3v2Reader.parse("salom dunyo".toByteArray()).isEmpty)
        // 2.2 versiyasi: kadr nomi uch harfli, tuzilishi boshqa.
        assertTrue(Id3v2Reader.parse(tag(version = 2, frames = ByteArray(0))).isEmpty)
    }

    @Test
    fun `buzilgan kadr tahlilni toxtatadi`() {
        // O'lcham fayldan katta: o'quvchi chegaradan chiqib ketmasligi
        // kerak (aks holda istisno va ilova yopilardi).
        val broken = ByteArrayOutputStream().apply {
            write("TIT2".toByteArray(Charsets.US_ASCII))
            write(byteArrayOf(0x7F, 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
            write(0); write(0)
            write("qisqa".toByteArray(Charsets.US_ASCII))
        }.toByteArray()

        assertTrue(Id3v2Reader.parse(tag(3, broken)).isEmpty)
    }

    @Test
    fun `siqilgan kadr tashlab ketiladi`() {
        // Siqilgan matnni ochib bo'lmaydi — taxmin qilib noto'g'ri nom
        // ko'rsatishdan ko'ra bo'sh qoldirish yaxshi.
        val frame = Id3v2Tag.frame("TIT2", text("Kitob")).copyOf()
        frame[8] = 0x80.toByte() // 2.3: siqilgan
        assertEquals("", Id3v2Reader.parse(tag(3, frame)).title)
    }

    @Test
    fun `muqova va mime ajratib olinadi`() {
        val image = ByteArray(32) { 0x42 }
        val payload = ByteArrayOutputStream().apply {
            write(0) // kodlash
            write("image/png".toByteArray(Charsets.US_ASCII))
            write(0) // MIME tugadi
            write(3) // old muqova
            write(0) // izoh: latin1, bitta nol
            write(image)
        }.toByteArray()

        val read = Id3v2Reader.parse(tag(3, Id3v2Tag.frame("APIC", payload)))
        assertArrayEquals(image, read.cover)
        assertEquals("image/png", read.coverMime)
    }

    @Test
    fun `muqovaning oz baytigina olinadi`() {
        // APIC o'lchami rasmning o'zidan katta bo'lsa ham, ortiqcha bayt
        // rasmga qo'shilib ketmasligi kerak: aks holda saqlangan rasm
        // ochilmaydi.
        val image = ByteArray(16) { 1 }
        val payload = ByteArrayOutputStream().apply {
            write(0)
            write("image/jpeg".toByteArray(Charsets.US_ASCII))
            write(0)
            write(3)
            write(0)
            write(image)
        }.toByteArray()

        val read = Id3v2Reader.parse(tag(3, Id3v2Tag.frame("APIC", payload)))
        assertEquals(16, read.cover?.size)
    }

    @Test
    fun `fayldan faqat teg oqiladi`() {
        val tags = AudioTags(title = "Kitob", artist = "Husanboy")
        val file = File.createTempFile("ovozstudio-oqish", ".mp3")
        file.deleteOnExit()
        file.writeBytes(Id3v2Tag.build(tags) + ByteArray(4096) { 0x55 })

        val read = Id3v2Reader.read(file)
        assertEquals("Kitob", read.title)
        assertEquals("Husanboy", read.artist)
        // Audio teg ichiga tushib qolmasligi kerak: muqova yo'q edi.
        assertNull(read.cover)
    }

    /** Teg sarlavhasi + kadrlardan iborat baytlar. */
    private fun tag(version: Int, frames: ByteArray, flags: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("ID3".toByteArray(Charsets.US_ASCII))
        out.write(version)
        out.write(0) // reviziya
        out.write(flags)
        out.write(Id3v2Tag.synchsafe(frames.size), 0, 4)
        out.write(frames)
        return out.toByteArray()
    }

    /** 2.4 kadri: o'lcham synchsafe, bayroqlar berilishi mumkin. */
    private fun frame24(id: String, payload: ByteArray, flags: Int = 0): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(Id3v2Tag.synchsafe(payload.size), 0, 4)
        out.write(0)
        out.write(flags)
        out.write(payload)
        return out.toByteArray()
    }

    /** ISO-8859-1 matn kadri — eng sodda kodlash. */
    private fun latin1Frame(id: String, value: String): ByteArray {
        val payload = byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)
        return Id3v2Tag.frame(id, payload)
    }

    private fun text(value: String): ByteArray = Id3v2Tag.textPayload(value)
}
