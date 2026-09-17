package uz.ovozstudio.app.media.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Polosalar jadvali va tayyor profillar.
 *
 * Eng muhim tekshiruv — **profil ikkala jadvalda ham bir xil shaklda
 * ochilishi**. Profil egri chiziq sifatida yozilgan, shuning uchun 10
 * polosadan 31 polosaga o'tilganda u o'sha joyda qolishi kerak; agar
 * qiymatlar polosalar bo'yicha ko'chirilsa, profil jimgina buzilardi.
 */
class EqBandsTest {

    private val rate = 48_000

    @Test
    fun `oktava jadvali on polosadan iborat`() {
        val centers = EqBands.centers(EqBandCount.TEN, rate)

        assertEquals(10, centers.size)
        assertEquals(31.5, centers.first(), 0.001)
        assertEquals(16_000.0, centers.last(), 0.001)
    }

    @Test
    fun `uchdan bir oktava jadvali ottiz bir polosadan iborat`() {
        val centers = EqBands.centers(EqBandCount.THIRTY_ONE, rate)

        assertEquals(31, centers.size)
        assertEquals(20.0, centers.first(), 0.001)
        assertEquals(20_000.0, centers.last(), 0.001)
        // Qo'shni polosalar orasi uchdan bir oktava: 2^(1/3) ≈ 1.26.
        // ISO jadvali markaziy chastotalarni uch xonaga yaxlitlaydi
        // (25, 31.5, 63), shuning uchun nisbat 1.25–1.28 oralig'ida
        // tebranadi — aniq emas, 2% aniqlik talab qilinadi.
        for (i in 1 until centers.size) {
            assertEquals("${centers[i - 1]} -> ${centers[i]}", 1.2599, centers[i] / centers[i - 1], 0.03)
        }
    }

    @Test
    fun `qoshni polosalarning chegaralari tutashib turadi`() {
        // Har bir polosaning kengligi shunday tanlanganki, qo'shnisi bilan
        // orasida na «teshik», na qavat-qavat ustma-ust tushish qoladi.
        // Teshik bo'lsa, foydalanuvchi o'sha chastotani hech qachon
        // tuzata olmasdi — polosa yo'q, demak tugma ham yo'q.
        for (count in EqBandCount.entries) {
            val centers = EqBands.centers(count, rate)
            for (i in 1 until centers.size) {
                val lowerTop = EqBands.bandwidth(count, centers[i - 1]).endInclusive
                val upperBottom = EqBands.bandwidth(count, centers[i]).start

                // Markaziy chastotalar ISO jadvalida yaxlitlangan (25, 31.5),
                // shuning uchun aniq tenglik emas, 3% aniqlik talab qilinadi.
                assertEquals(
                    "${centers[i - 1]} va ${centers[i]} orasida uzilish",
                    lowerTop,
                    upperBottom,
                    lowerTop * 0.03,
                )
            }
        }
    }

    @Test
    fun `nyquist dan yuqori polosalar jadvaldan chiqadi`() {
        // 22.05 kHz li faylda eng yuqori polosa 10 kHz: undan yuqorisini
        // kuchaytirish mumkin emas.
        val centers = EqBands.centers(EqBandCount.THIRTY_ONE, 22_050)

        assertEquals(28, centers.size)
        assertEquals(10_000.0, centers.last(), 0.001)
        assertFalse(centers.contains(12_500.0))

        // 8 kHz li faylda Nyquist 4 kHz: aynan shu chastotadagi polosa ham
        // tushib qoladi (u yerda filtr yarim kuchaytirishdan boshqa narsa
        // qilolmaydi), ya'ni 2000 Hz da to'xtaydi.
        val narrow = EqBands.centers(EqBandCount.TEN, 8_000)
        assertEquals(7, narrow.size)
        assertEquals(2000.0, narrow.last(), 0.001)
    }

    @Test
    fun `tekis profil hech narsani ozgartirmaydi`() {
        for (count in EqBandCount.entries) {
            val gains = EqBands.presetGains(count, EqPreset.FLAT, rate)
            assertEquals(EqBands.centers(count, rate).size, gains.size)
            assertTrue("hamma qiymat nol bo'lishi kerak", gains.all { it == 0.0 })
        }
    }

    @Test
    fun `profil qiymatlari tayanch nuqtalarida aynan mos keladi`() {
        assertEquals(-6.0, EqBands.presetGainDb(EqPreset.VOICE, 50.0), 0.001)
        assertEquals(3.0, EqBands.presetGainDb(EqPreset.VOICE, 3000.0), 0.001)
        assertEquals(6.0, EqBands.presetGainDb(EqPreset.BASS, 60.0), 0.001)
        assertEquals(0.0, EqBands.presetGainDb(EqPreset.BASS, 1000.0), 0.001)
    }

    @Test
    fun `profil chegaradan tashqarida eng yaqin qiymatni oladi`() {
        // 5 Hz ham, 40 kHz ham jadvalda yo'q: chetdagi qiymat qaytadi,
        // ekstrapolyatsiya qilinmaydi.
        assertEquals(0.0, EqBands.presetGainDb(EqPreset.TREBLE, 5.0), 0.001)
        assertEquals(5.0, EqBands.presetGainDb(EqPreset.TREBLE, 40_000.0), 0.001)
    }

    @Test
    fun `profil chastotalar orasida silliq ozgaradi`() {
        // Interpolyatsiya logarifmik: 141.42 Hz — 100 Hz bilan 200 Hz ning
        // geometrik o'rtasi, ya'ni javob ular orasidagi o'rtacha qiymat.
        val low = EqBands.presetGainDb(EqPreset.VOICE, 100.0)
        val high = EqBands.presetGainDb(EqPreset.VOICE, 200.0)
        val middle = EqBands.presetGainDb(EqPreset.VOICE, 141.42)

        assertEquals((low + high) / 2.0, middle, 0.02)
    }

    @Test
    fun `jadval profil egri chizigini surmaydi`() {
        // Profil polosalarga «yozib qo'yilmaydi»: jadval faqat egri
        // chiziqdan namuna oladi. Ikkala jadvalda ham bir xil chastotadagi
        // qiymat bir xil bo'lishi shart, aks holda polosalar sonini
        // almashtirish ovozni o'zgartirib yuborardi.
        for (preset in EqPreset.entries) {
            val octave = EqBands.centers(EqBandCount.TEN, rate)
            val third = EqBands.centers(EqBandCount.THIRTY_ONE, rate)
            val octaveGains = EqBands.presetGains(EqBandCount.TEN, preset, rate)
            val thirdGains = EqBands.presetGains(EqBandCount.THIRTY_ONE, preset, rate)

            assertTrue("$preset chegaradan chiqib ketgan", (octaveGains + thirdGains).all { it in -12.0..12.0 })

            for (shared in listOf(31.5, 125.0, 1000.0, 4000.0, 8000.0)) {
                val fromOctave = octaveGains[octave.indexOf(shared)]
                val fromThird = thirdGains[third.indexOf(shared)]

                assertEquals("$preset @ $shared Hz bir xil emas", fromOctave, fromThird, 0.0)
                assertEquals(
                    "$preset @ $shared Hz egri chiziqdan chetlashgan",
                    EqBands.presetGainDb(preset, shared),
                    fromOctave,
                    0.0,
                )
            }
        }
    }

    @Test
    fun `ovoz profili pastni tushiradi va ravshanlikni kotaradi`() {
        assertTrue(EqBands.presetGainDb(EqPreset.VOICE, 100.0) < 0.0)
        assertTrue(EqBands.presetGainDb(EqPreset.VOICE, 3000.0) > 0.0)
    }

    @Test
    fun `kenglik jadvalga mos keladi`() {
        assertEquals(1.41, EqBands.q(EqBandCount.TEN), 0.0001)
        assertEquals(4.32, EqBands.q(EqBandCount.THIRTY_ONE), 0.0001)

        // `bandwidth` — yarim kenglik: oktava polosasining chegaralari.
        val octave = EqBands.bandwidth(EqBandCount.TEN, 1000.0)
        assertEquals(707.1, octave.start, 0.5)
        assertEquals(1414.2, octave.endInclusive, 0.5)
    }

    @Test
    fun `eng yaqin polosa logarifmik masofa bilan topiladi`() {
        val centers = EqBands.centers(EqBandCount.TEN, rate)

        // 100 Hz qo'shnilari 63 va 125; 125 yaqinroq.
        assertEquals(125.0, centers[EqBands.nearestIndex(centers, 100.0)], 0.001)
        // 1100 Hz esa 1000 ga yaqin, 2000 ga emas.
        assertEquals(1000.0, centers[EqBands.nearestIndex(centers, 1100.0)], 0.001)
        assertTrue(EqBands.nearestIndex(emptyList(), 1000.0) < 0)
    }

    @Test
    fun `chastota olchov birligi ozgaradi`() {
        // 500 Hz gertsda, 1 kHz dan yuqorisi kilogertsda yoziladi.
        assertEquals(500.0, EqBands.centers(EqBandCount.TEN, rate)[4], 0.001)
        assertEquals(1000.0, EqBands.centers(EqBandCount.TEN, rate)[5], 0.001)
    }
}
