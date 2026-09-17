package uz.ovozstudio.app.media.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Furye almashtirishi.
 *
 * Shovqin tozalash butunlay shu sinfga tayanadi: spektrdagi xato to'g'ridan
 * to'g'ri tovushdagi xatoga aylanadi. Shuning uchun bu yerda matematik
 * xossalar tekshiriladi — natija «o'xshash» emas, **aniq** bo'lishi kerak.
 */
class FftTest {

    @Test
    fun `ozgarishsiz qaytadi`() {
        // Eng asosiy xossa: to'g'ri va teskari almashtirish ketma-ket
        // bajarilsa, signal aynan o'z holiga qaytadi.
        val fft = Fft(256)
        val size = fft.size
        val real = DoubleArray(size) { sin(0.37 * it) + 0.4 * cos(0.11 * it) }
        val original = real.copyOf()
        val imaginary = DoubleArray(size)

        fft.forward(real, imaginary)
        fft.inverse(real, imaginary)

        for (i in 0 until size) {
            assertEquals("namuna $i", original[i], real[i], 1e-12)
            assertEquals("mavhum qism $i", 0.0, imaginary[i], 1e-12)
        }
    }

    @Test
    fun `bitta polosali kosinus ikki polosa beradi`() {
        // cos(2*pi*k0*n/N) -> X[k0] = X[N-k0] = N/2, qolgani nol.
        val fft = Fft(128)
        val size = fft.size
        val bin = 9
        val real = DoubleArray(size) { cos(2.0 * PI * bin * it / size) }
        val imaginary = DoubleArray(size)

        fft.forward(real, imaginary)

        val half = size / 2.0
        assertEquals("asosiy polosa", half, sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]), 1e-9)
        assertEquals("ko'zgu polosa", half, sqrt(real[size - bin] * real[size - bin] + imaginary[size - bin] * imaginary[size - bin]), 1e-9)

        for (k in 0 until size) {
            if (k == bin || k == size - bin) continue
            val magnitude = sqrt(real[k] * real[k] + imaginary[k] * imaginary[k])
            assertTrue("polosa $k bo'sh emas: $magnitude", magnitude < 1e-9)
        }
    }

    @Test
    fun `ozgarmas signal faqat nol polosaga tushadi`() {
        val fft = Fft(64)
        val real = DoubleArray(fft.size) { 1.0 }
        val imaginary = DoubleArray(fft.size)

        fft.forward(real, imaginary)

        assertEquals("doimiy qism", 64.0, real[0], 1e-12)
        assertEquals("mavhum qism nol", 0.0, imaginary[0], 1e-12)
        for (k in 1 until fft.size) {
            assertTrue("polosa $k: ${real[k]}", abs(real[k]) < 1e-12)
        }
    }

    @Test
    fun `parseval tengligi bajariladi`() {
        // Energiya saqlanadi: sum|x|^2 = (1/N) * sum|X|^2.
        val fft = Fft(512)
        val size = fft.size
        val real = DoubleArray(size) { sin(0.03 * it) * cos(0.017 * it) }
        val imaginary = DoubleArray(size)

        var timeEnergy = 0.0
        for (i in 0 until size) timeEnergy += real[i] * real[i]

        fft.forward(real, imaginary)

        var spectrumEnergy = 0.0
        for (k in 0 until size) {
            spectrumEnergy += real[k] * real[k] + imaginary[k] * imaginary[k]
        }

        assertEquals(timeEnergy, spectrumEnergy / size, 1e-9)
    }

    @Test
    fun `ikki ning darajasi bolmagan olcham rad etiladi`() {
        try {
            Fft(1000)
            throw AssertionError("xato kutilgan edi")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("1000"))
        }
    }
}
