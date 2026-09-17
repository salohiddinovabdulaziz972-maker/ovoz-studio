package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmBytesTest {

    private fun bytes(samples: IntArray, shift: Int): ByteArray {
        val target = ByteArray(samples.size * 2)
        PcmBytes.write(target, samples, 0, samples.size, shift)
        return target
    }

    @Test
    fun `on olti bitli namunalar kichik tartibda yoziladi`() {
        // 0x1234 -> 34 12 (kichik tartib), -1 -> FF FF
        val out = bytes(intArrayOf(0x1234, -1, 0, 1, -2), 0)
        assertEquals(
            "34 12 FF FF 00 00 01 00 FE FF",
            out.joinToString(" ") { "%02X".format(it) },
        )
    }

    @Test
    fun `yigirma tort bit sakkiz bit ongga suriladi`() {
        // 24-bit shkaladagi qiymat 16-bitga tushiriladi: >> 8.
        val source = intArrayOf(8_388_607, -8_388_608, 256, -256)
        val out = bytes(source, 8)
        val shorts = IntArray(4) { i ->
            ((out[i * 2].toInt() and 0xFF) or (out[i * 2 + 1].toInt() shl 8))
        }
        assertEquals(32_767, shorts[0])
        assertEquals(-32_768, shorts[1])
        assertEquals(1, shorts[2])
        assertEquals(-1, shorts[3])
    }

    @Test
    fun `chegaradan chiqqan qiymat qisiladi`() {
        // 32-bitli manba 16-bitga sig'maydi — fayl buzilmasligi kerak.
        val out = bytes(intArrayOf(Int.MAX_VALUE, Int.MIN_VALUE), 16)
        val high = (out[0].toInt() and 0xFF) or (out[1].toInt() shl 8)
        val low = (out[2].toInt() and 0xFF) or (out[3].toInt() shl 8)
        assertEquals(32_767, high)
        assertEquals(-32_768, low)
    }

    @Test
    fun `surish manfiy bolsa rad etiladi`() {
        val error = runCatching { PcmBytes.write(ByteArray(4), intArrayOf(1, 2), 0, 2, -1) }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `manba chegarasidan chiqish rad etiladi`() {
        val error = runCatching { PcmBytes.write(ByteArray(8), intArrayOf(1, 2), 1, 2, 0) }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `kichik bufer rad etiladi`() {
        val error = runCatching { PcmBytes.write(ByteArray(2), intArrayOf(1, 2), 0, 2, 0) }
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `hajm hisobi kanal sonini hisobga oladi`() {
        assertEquals(4, PcmBytes.sizeFor(1, 2))
        assertEquals(400, PcmBytes.sizeFor(100, 2))
        assertEquals(200, PcmBytes.sizeFor(100, 1))
        assertEquals(50, PcmBytes.framesIn(200, 2))
        assertEquals(100, PcmBytes.framesIn(200, 1))
        // Bufer kanal soniga bo'linmasa, oxirgi chala kadr tashlanadi.
        assertEquals(33, PcmBytes.framesIn(201, 3))
        assertEquals(0, PcmBytes.framesIn(100, 0))
    }
}
