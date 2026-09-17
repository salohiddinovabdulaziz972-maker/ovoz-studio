package uz.ovozstudio.app.media.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ikkilik (radix-2) kompleks Furye almashtirishi.
 *
 * Spektr bilan ishlaydigan amallar uchun kerak: shovqin tozalash chastota
 * sohasida ishlaydi, u yerda esa har bir kadr uchun to'g'ri va teskari
 * almashtirish bajariladi.
 *
 * O'lcham 2 ning darajasi bo'lishi shart — bu shart emas, balki algoritmning
 * o'zi: ikkilik bo'lmagan o'lcham uchun boshqa usul (Bluesteyn) kerak bo'lardi.
 *
 * Burilish koeffitsientlari (`cosTable`, `sinTable`) va bit teskari
 * almashtirish jadvali bir marta, yaratishda hisoblanadi: o'n daqiqalik fayl
 * uchun almashtirish yuz minglab marta chaqiriladi, har safar trigonometriya
 * hisoblash esa telefon uchun ortiqcha yuk.
 *
 * Hisob `Double` da ketadi. Spektr qiymatlari keyin kuchga ko'tariladi va
 * ayiriladi: `Float` da bu yerda aniqlik yetmay qolardi, ayniqsa shovqin
 * darajasi signaldan ancha past bo'lganda.
 *
 * Bu sinf `internal`: u ilovaning tashqi interfeysi emas, DSP dvigatelining
 * ichki qismi.
 */
internal class Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) {
            "O'lcham 2 ning darajasi bo'lishi kerak: $size"
        }
    }

    private val cosTable = DoubleArray(size / 2)
    private val sinTable = DoubleArray(size / 2)
    private val reversed = IntArray(size)

    init {
        for (i in 0 until size / 2) {
            val angle = 2.0 * PI * i / size
            cosTable[i] = cos(angle)
            sinTable[i] = sin(angle)
        }
        val bits = Integer.numberOfTrailingZeros(size)
        for (i in 0 until size) {
            reversed[i] = Integer.reverse(i) ushr (Int.SIZE_BITS - bits)
        }
    }

    /** To'g'ri almashtirish: `x[n] -> X[k]`, belgisi `e^(-i·2πkn/N)`. */
    fun forward(real: DoubleArray, imaginary: DoubleArray) = transform(real, imaginary, inverse = false)

    /**
     * Teskari almashtirish: `X[k] -> x[n]`.
     *
     * Natija `size` ga bo'linadi — shunda `inverse(forward(x)) == x` bo'ladi.
     */
    fun inverse(real: DoubleArray, imaginary: DoubleArray) = transform(real, imaginary, inverse = true)

    private fun transform(real: DoubleArray, imaginary: DoubleArray, inverse: Boolean) {
        require(real.size >= size && imaginary.size >= size) {
            "Massiv o'lchami yetarli emas: ${real.size} < $size"
        }

        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var swap = real[i]; real[i] = real[j]; real[j] = swap
                swap = imaginary[i]; imaginary[i] = imaginary[j]; imaginary[j] = swap
            }
        }

        var length = 2
        while (length <= size) {
            val half = length / 2
            val step = size / length
            var base = 0
            while (base < size) {
                var k = 0
                for (j in base until base + half) {
                    val l = j + half
                    val wr = cosTable[k]
                    // Teskari almashtirishda burilish teskari yo'nalishda.
                    val wi = if (inverse) sinTable[k] else -sinTable[k]

                    val tr = real[l] * wr - imaginary[l] * wi
                    val ti = real[l] * wi + imaginary[l] * wr

                    real[l] = real[j] - tr
                    imaginary[l] = imaginary[j] - ti
                    real[j] += tr
                    imaginary[j] += ti

                    k += step
                }
                base += length
            }
            length = length shl 1
        }

        if (inverse) {
            val scale = 1.0 / size
            for (i in 0 until size) {
                real[i] *= scale
                imaginary[i] *= scale
            }
        }
    }
}
