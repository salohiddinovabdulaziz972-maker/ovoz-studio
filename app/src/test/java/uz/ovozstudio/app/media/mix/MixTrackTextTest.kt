package uz.ovozstudio.app.media.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Yo'l sozlamalarining matn ko'rinishi.
 *
 * Bu yerda tekshiriladigan narsa — **chegaralar**, chunki ular ekranda
 * ko'rinmaydi va xatosi jimgina o'tib ketadi: maydonga sig'magan son
 * kesilib qolsa, foydalanuvchi kiritgan qiymatdan boshqasini eshitadi.
 *
 * Ikkinchi talab — matn va son orasidagi aylanish **aynan** bo'lishi:
 * sozlama saqlanib, qayta ochilganda o'sha ovoz eshitilishi kerak.
 */
class MixTrackTextTest {

    @Test
    fun `balandlik manfiy oltmishni sigdiradi`() {
        // Maydon ikki butun raqamdan iborat: "-60" sig'ishi shart, aks holda
        // eng past chegara kiritilmasdan qirqilib qolardi.
        assertEquals("-60", MixTrackText.sanitizeGain("-60"))
        assertEquals(MixTrack.MIN_GAIN_DB, MixTrackText.parseGain("-60"), 0f)
    }

    @Test
    fun `balandlik chegaradan oshmaydi`() {
        assertEquals(MixTrack.MAX_GAIN_DB, MixTrackText.parseGain("99"), 0f)
        assertEquals(MixTrack.MIN_GAIN_DB, MixTrackText.parseGain("-99"), 0f)
    }

    @Test
    fun `bosh balandlik ozgartirmaydi`() {
        // Bo'sh maydon — «kuchaytirish yo'q» (0 dB), eng past emas.
        assertEquals(0f, MixTrackText.parseGain(""), 0f)
        assertEquals("0", MixTrackText.formatGain(0f))
    }

    @Test
    fun `balandlik matni orqaga aynan qaytadi`() {
        for (value in listOf(0f, -6f, 3.5f, 12f, -60f)) {
            val text = MixTrackText.formatGain(value)
            assertEquals("matn: $text", value, MixTrackText.parseGain(text), 1e-4f)
        }
    }

    @Test
    fun `qadam yarim desibel`() {
        assertEquals("0.5", MixTrackText.nudgeGain("0", MixTrackText.GAIN_STEP_DB))
        // Chegarada qadam to'xtaydi — chetga chiqmaydi.
        assertEquals("12", MixTrackText.nudgeGain("12", MixTrackText.GAIN_STEP_DB))
        assertEquals("-60", MixTrackText.nudgeGain("-60", -MixTrackText.GAIN_STEP_DB))
    }

    @Test
    fun `joylashuv ikki kasr raqam bilan yoziladi`() {
        assertEquals("0.25", MixTrackText.formatPan(0.25f))
        assertEquals(0.25f, MixTrackText.parsePan("0.25"), 1e-4f)
        // Bo'sh maydon — o'rta, ya'ni «o'zgartirish yo'q».
        assertEquals(0f, MixTrackText.parsePan(""), 0f)
    }

    @Test
    fun `joylashuv chetidan oshmaydi`() {
        assertEquals(1f, MixTrackText.parsePan("2"), 0f)
        assertEquals(-1f, MixTrackText.parsePan("-2"), 0f)
        assertEquals("1", MixTrackText.formatPan(1f))
        assertEquals("-1", MixTrackText.formatPan(-1f))
    }

    @Test
    fun `joylashuv qadami ondan bir`() {
        assertEquals("0.1", MixTrackText.nudgePan("0", MixTrackText.PAN_STEP))
        assertEquals("1", MixTrackText.nudgePan("1", MixTrackText.PAN_STEP))
    }

    @Test
    fun `siljish butun millisekundda`() {
        assertEquals("1500", MixTrackText.sanitizeOffset("1500"))
        assertEquals(1_500L, MixTrackText.parseOffset("1500"))
        assertEquals("1500", MixTrackText.formatOffset(1_500L))
        // Kasr qismi yo'q: siljish kadrlarga yaxlitlanadi.
        assertEquals(1_500L, MixTrackText.parseOffset("1500.7"))
    }

    @Test
    fun `manfiy siljish nolga tushadi`() {
        // Yo'lni aralashma boshidan oldin boshlab bo'lmaydi.
        assertEquals(0L, MixTrackText.parseOffset("-500"))
        assertEquals(0L, MixTrackText.parseOffset(MixTrackText.sanitizeOffset("-500")))
    }

    @Test
    fun `siljish olti raqamdan oshmaydi`() {
        // 10 daqiqadan uzun siljish amalda kerak emas, maydon esa to'lib
        // ketmasligi kerak.
        assertEquals("123456", MixTrackText.sanitizeOffset("1234567"))
        assertEquals(MixTrackText.MAX_OFFSET_MS.toLong(), MixTrackText.parseOffset("9999999"))
    }

    @Test
    fun `siljish qadami yuz millisekund`() {
        assertEquals("100", MixTrackText.nudgeOffset("0", MixTrackText.OFFSET_STEP_MS))
        assertEquals("0", MixTrackText.nudgeOffset("100", -MixTrackText.OFFSET_STEP_MS))
    }

    @Test
    fun `desibel koeffitsientdan qaytariladi`() {
        // Tashqi tekshiruv: aylanish dbToGain (mikserning o'z formulasi)
        // orqali o'tadi, ya'ni ikki tomon bir xil qoidada ekani ko'rinadi.
        assertEquals(0f, MixTrackText.gainToDb(1f), 1e-4f)
        assertEquals(-6f, MixTrackText.gainToDb(dbToGain(-6f)), 1e-3f)
        assertEquals(-20f, MixTrackText.gainToDb(0.1f), 1e-3f)
        // Nol koeffitsient — jimlik; logarifm -cheksizlik berardi.
        assertEquals(0f, MixTrackText.gainToDb(0f), 0f)
    }

    @Test
    fun `desibel tushirilishi qisqa yolni ham qamraydi`() {
        // Kesish himoyasi to'liq shkaladagi bitta yo'lni ham 0.999 ga
        // tushiradi — bu 0.01 dB, ya'ni ekranda aytib bo'lmaydigan qiymat.
        val tiny = MixTrackText.gainToDb(0.999f)
        assertTrue("0.01 dB dan kichik bo'lishi kerak: $tiny", tiny > -0.1f)
    }
}
