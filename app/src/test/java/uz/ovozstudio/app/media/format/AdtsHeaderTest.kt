package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdtsHeaderTest {

    private fun header(payload: Int, rate: Int, channels: Int): ByteArray {
        val out = ByteArray(AdtsHeader.LENGTH)
        AdtsHeader.write(out, 0, payload, rate, channels)
        return out
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }

    @Test
    fun `qiriq tort bir kilogerts stereo sarlavhasi bayt-bayt togri`() {
        // 44.1 kHz, stereo, AAC LC, 100 baytlik kadr.
        val expected = byteArrayOf(
            0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(),
            0x0D, 0x7F, 0xFC.toByte(),
        )
        assertEquals(hex(expected), hex(header(100, 44_100, 2)))
    }

    @Test
    fun `kadr uzunligi maydoniga sarlavhaning ozi ham qoshiladi`() {
        // frame_length = payload + 7. 13 bitli maydon 4-5-6-baytlarga
        // bo'lib yoziladi; xato bo'lsa pleyer kadrni topa olmaydi.
        val payload = 0x1234
        val h = header(payload, 44_100, 1)
        val length = ((h[3].toInt() and 0x03) shl 11) or
            ((h[4].toInt() and 0xFF) shl 3) or
            ((h[5].toInt() and 0xE0) shr 5)
        assertEquals(payload + AdtsHeader.LENGTH, length)
    }

    @Test
    fun `kanal soni ikki baytga bolinib yoziladi`() {
        // 3 kanal: yuqori bit 3-baytda, pastki 2 bit 4-baytda.
        val h = header(10, 48_000, 3)
        val channels = ((h[2].toInt() and 0x01) shl 2) or ((h[3].toInt() and 0xC0) shr 6)
        assertEquals(3, channels)
    }

    @Test
    fun `har bir qollab-quvvatlanadigan chastota oz indeksini oladi`() {
        val cases = mapOf(
            96_000 to 0, 48_000 to 3, 44_100 to 4, 32_000 to 5, 16_000 to 8, 8_000 to 11,
        )
        for ((rate, index) in cases) {
            val h = header(10, rate, 1)
            // Chastota indeksi 4 bit: 3-baytning 5..2-bitlari.
            val read = (h[2].toInt() and 0x3C) shr 2
            assertEquals("$rate Hz", index, read)
        }
    }

    @Test
    fun `nomalum chastota rad etiladi`() {
        val out = ByteArray(AdtsHeader.LENGTH)
        val error = runCatching { AdtsHeader.write(out, 0, 10, 47_000, 1) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `juda katta kadr rad etiladi`() {
        val out = ByteArray(AdtsHeader.LENGTH)
        // 13 bitli maydon 8191 gacha; sarlavha bilan birga 8191 dan oshmasligi kerak.
        val error = runCatching { AdtsHeader.write(out, 0, 8_200, 44_100, 1) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `kichik bufer rad etiladi`() {
        val error = runCatching { AdtsHeader.write(ByteArray(4), 0, 10, 44_100, 1) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `kanal soni kodlovchi konfiguratsiyasidan oqiladi`() {
        // AudioSpecificConfig: 5 bit obyekt (2 = LC), 4 bit indeks (4 = 44.1k),
        // 4 bit kanal (2) -> 00010 0100 0010 000
        val csd = byteArrayOf(0x12, 0x10)
        assertEquals(2, AdtsHeader.channelCountFromCsd(csd))
        assertNull(AdtsHeader.channelCountFromCsd(null))
        assertNull(AdtsHeader.channelCountFromCsd(byteArrayOf(0x12)))
        assertNull("kanal 0 bo'lsa konfiguratsiya o'qilmaydi", AdtsHeader.channelCountFromCsd(byteArrayOf(0x00, 0x00)))
    }

    @Test
    fun `qollab-quvvatlash toldirilgan`() {
        assertTrue(AdtsHeader.supports(44_100))
        assertTrue(AdtsHeader.supports(48_000))
        assertTrue(AdtsHeader.supports(96_000))
        assertFalse(AdtsHeader.supports(47_000))
        assertEquals(AdtsHeader.LENGTH, 7)
    }
}
