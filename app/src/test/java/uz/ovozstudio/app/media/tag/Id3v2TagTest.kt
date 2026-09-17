package uz.ovozstudio.app.media.tag

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ID3 tegining bayt darajasidagi tuzilishi.
 *
 * Bu sinovlar **formatning o'ziga** qaratilgan: kadr o'lchami qanday
 * yozilishi, matn kodlashi, BOM. Xato shu yerda bo'lsa, fayl pleyerda
 * ochilmaydi yoki nom «????» bo'lib chiqadi — lekin ilovaning o'zi buni
 * sezmaydi. Shuning uchun tekshiruv baytlar bo'yicha, ko'z bilan emas.
 */
class Id3v2TagTest {

    private fun ascii(bytes: ByteArray, offset: Int, length: Int) =
        String(bytes, offset, length, Charsets.US_ASCII)

    @Test
    fun `synchsafe olcham har baytda yetti bit ishlatadi`() {
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), Id3v2Tag.synchsafe(0))
        assertArrayEquals(byteArrayOf(0, 0, 0, 127), Id3v2Tag.synchsafe(127))
        // 128 — sakkizinchi bit emas, keyingi baytga o'tadi: 1 * 128.
        assertArrayEquals(byteArrayOf(0, 0, 1, 0), Id3v2Tag.synchsafe(128))
        assertArrayEquals(byteArrayOf(0, 0, 2, 1), Id3v2Tag.synchsafe(257))
        // Eng katta qiymat: to'rt baytning hammasi to'la.
        assertArrayEquals(
            byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F),
            Id3v2Tag.synchsafe(Id3v2Tag.MAX_FRAME_SIZE),
        )
    }

    @Test
    fun `olcham ortib ketmaydi`() {
        // Chegaradan katta qiymat eng kattasiga qisiladi — buzuq teg
        // yozgandan ko'ra, to'ldirilgan teg yaxshiroq.
        assertArrayEquals(
            byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F),
            Id3v2Tag.synchsafe(Int.MAX_VALUE),
        )
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), Id3v2Tag.synchsafe(-5))
    }

    @Test
    fun `synchsafe ozini qaytaradi`() {
        for (value in listOf(0, 1, 127, 128, 16_384, 2_097_151, Id3v2Tag.MAX_FRAME_SIZE)) {
            assertEquals(value, Id3v2Tag.readSynchsafe(Id3v2Tag.synchsafe(value)))
        }
    }

    @Test
    fun `sarlavha versiya 2 3 ekanini bildiradi`() {
        val tag = Id3v2Tag.build(AudioTags(title = "Kitob"))

        assertEquals("ID3", ascii(tag, 0, 3))
        assertEquals(3, tag[3].toInt()) // versiya
        assertEquals(0, tag[4].toInt()) // reviziya
        assertEquals(0, tag[5].toInt()) // bayroqlar

        // O'lcham — sarlavhadan keyingi hamma bayt.
        val size = Id3v2Tag.readSynchsafe(tag, 6)
        assertEquals(tag.size - 10, size)
    }

    @Test
    fun `kadr olchami oddiy son sinchsafe emas`() {
        val payload = ByteArray(300) { 'a'.code.toByte() }
        val frame = Id3v2Tag.frame("TIT2", payload)

        assertEquals("TIT2", ascii(frame, 0, 4))
        // 300 = 0x0000012C — katta uchli son. Synchsafe bo'lganda
        // 0x00000228 chiqardi va o'quvchi kadrni tops olmasdi.
        assertEquals(0x00, frame[4].toInt())
        assertEquals(0x00, frame[5].toInt())
        assertEquals(0x01, frame[6].toInt())
        assertEquals(0x2C, frame[7].toInt())
        assertEquals(0, frame[8].toInt())
        assertEquals(0, frame[9].toInt())
        assertEquals(10 + 300, frame.size)
    }

    @Test
    fun `matn utf 16 va bom bilan yoziladi`() {
        val payload = Id3v2Tag.textPayload("Kitob")

        assertEquals(1, payload[0].toInt()) // kodlash: UTF-16
        assertEquals(0xFF, payload[1].toInt() and 0xFF) // BOM
        assertEquals(0xFE, payload[2].toInt() and 0xFF)
        // Oxirida nol: matn tugaganini bildiradi.
        assertEquals(0, payload[payload.size - 2].toInt())
        assertEquals(0, payload[payload.size - 1].toInt())
        assertEquals("Kitob", decode(payload))
    }

    @Test
    fun `kirill matni ham togri saqlanadi`() {
        // ISO-8859-1 da kirill «?????» bo'lib qolardi — o'zbek kitoblari
        // ikki yozuvda ham chiqadi, shuning uchun bu asosiy talab.
        val payload = Id3v2Tag.textPayload("Салим ака келди")
        assertEquals("Салим ака келди", decode(payload))
    }

    @Test
    fun `bosh maydonlar umuman yozilmaydi`() {
        val tag = Id3v2Tag.build(AudioTags(title = "Kitob"))

        assertTrue("nom yo'q", contains(tag, "TIT2"))
        assertFalse("bo'sh ijrochi yozildi", contains(tag, "TPE1"))
        assertFalse("bo'sh albom yozildi", contains(tag, "TALB"))
        assertFalse("bo'sh muqova yozildi", contains(tag, "APIC"))
    }

    @Test
    fun `bosh teg faqat sarlavhadan iborat`() {
        // Bu «eski tegni o'chir» degani: yozuvchi bo'sh tegni fayl
        // boshiga qo'yadi va eski ma'lumot qolmaydi.
        val tag = Id3v2Tag.build(AudioTags())
        assertEquals(10, tag.size)
        assertEquals(0, Id3v2Tag.readSynchsafe(tag, 6))
    }

    @Test
    fun `tartib raqami jami bilan va jami siz yoziladi`() {
        assertTrue(contains(Id3v2Tag.build(AudioTags(track = 3, trackTotal = 12)), "3/12"))
        assertTrue(contains(Id3v2Tag.build(AudioTags(track = 3)), "3"))
        // 3/0 ko'rinishi pleyerda «3/0» bo'lib chiqardi.
        val yolgiz = Id3v2Tag.build(AudioTags(track = 3, trackTotal = 3))
        assertFalse("jami raqamdan katta bo'lmasa qo'shilmaydi", contains(yolgiz, "3/3"))
    }

    @Test
    fun `raqamsiz tegda tartib kadri yoq`() {
        assertFalse(contains(Id3v2Tag.build(AudioTags(title = "Kitob")), "TRCK"))
    }

    @Test
    fun `muqova mime va rasmdan iborat`() {
        val image = byteArrayOf(1, 2, 3, 4, 5)
        val payload = Id3v2Tag.picturePayload(image, "image/jpeg")

        assertEquals(1, payload[0].toInt())
        val mime = String(payload, 1, 10, Charsets.US_ASCII)
        assertEquals("image/jpeg", mime)
        assertEquals(0, payload[11].toInt()) // MIME tugadi
        assertEquals(3, payload[12].toInt()) // old tomondagi rasm
        // Bo'sh izoh: kodlash bayti + nol.
        assertEquals(0xFF, payload[13].toInt() and 0xFF)
        assertEquals(0xFE, payload[14].toInt() and 0xFF)
        assertEquals(0, payload[15].toInt())
        assertEquals(0, payload[16].toInt())
        assertArrayEquals(image, payload.copyOfRange(17, payload.size))
    }

    @Test
    fun `bosh rasm yozilmaydi`() {
        assertFalse(contains(Id3v2Tag.build(AudioTags(title = "K", cover = ByteArray(0))), "APIC"))
    }

    @Test
    fun `ortiqcha bosh joylar tashlanadi`() {
        // Maydonlar atrofidagi bo'shliq pleyerda ko'rinmaydi, lekin
        // qidiruvda xalaqit beradi. Teg bayt darajasida aynan bir xil
        // bo'lishi kerak — farq qolsa, sinov buni sezmay o'tmasin.
        assertArrayEquals(
            Id3v2Tag.build(AudioTags(title = "Kitob")),
            Id3v2Tag.build(AudioTags(title = "  Kitob  ")),
        )
    }

    /** Matn kadri mazmunidan satrni qaytaradi. */
    private fun decode(payload: ByteArray): String =
        String(payload, 3, payload.size - 5, Charsets.UTF_16LE)

    /** Baytlar ichida shunday ketma-ketlik bormi (kadr nomi yoki qiymat). */
    private fun contains(bytes: ByteArray, needle: String): Boolean {
        val targets = listOf(
            needle.toByteArray(Charsets.US_ASCII),
            needle.toByteArray(Charsets.UTF_16LE),
        )
        return targets.any { target ->
            if (target.isEmpty() || target.size > bytes.size) return@any false
            (0..bytes.size - target.size).any { start ->
                target.indices.all { bytes[start + it] == target[it] }
            }
        }
    }
}
