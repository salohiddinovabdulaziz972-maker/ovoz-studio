package uz.ovozstudio.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tezlik va ohang maydonlarining kiritish qoidalari.
 *
 * Bu qoidalar sof JVM'da tekshiriladi: ular Android'ga bog'liq emas, lekin
 * xatosi jimgina — noto'g'ri raqamni to'g'ri deb qabul qilib — o'tib ketadi.
 */
class SpeedTextTest {

    @Test
    fun `tezlik matni faqat songa tozalanadi`() {
        assertEquals("1.25", SpeedText.sanitizeSpeed("1.25"))
        assertEquals("1.5", SpeedText.sanitizeSpeed("1,5"))
        assertEquals("0.75", SpeedText.sanitizeSpeed(".75"))
        assertEquals("2", SpeedText.sanitizeSpeed("2"))
        assertEquals("", SpeedText.sanitizeSpeed("abc"))
        assertEquals("", SpeedText.sanitizeSpeed("."))
        // Ortiqcha kasr raqami tashlanadi.
        assertEquals("1.23", SpeedText.sanitizeSpeed("1.234"))
        // Ikkinchi ajratgichning o'zi tashlanadi, raqami esa kasr qismiga
        // qo'shiladi: «1.5.7» — bu «1.57» ni kiritmoqchi bo'lgan qo'l.
        assertEquals("1.57", SpeedText.sanitizeSpeed("1.5.7"))
    }

    @Test
    fun `tezlik butun qismi ikki xonali bolmaydi`() {
        // Tezlik 2.0 dan oshmaydi: «12» deb yozish «1.2» ni nazarda tutgan
        // bo'lishi mumkin, lekin «12» hech qachon to'g'ri tezlik emas.
        assertEquals("1", SpeedText.sanitizeSpeed("12"))
        // Yozilgan ko'rinish o'zgartirilmaydi: «2.0» maydonda shundayligicha
        // qoladi, uni faqat ± tugmasi «2» ga keltiradi.
        assertEquals("2.0", SpeedText.sanitizeSpeed("2.0"))
    }

    @Test
    fun `ohang matni minus bilan kiritiladi`() {
        assertEquals("-12", SpeedText.sanitizeSemitones("-12"))
        assertEquals("12.5", SpeedText.sanitizeSemitones("12.5"))
        // Uch xonali butun qism yo'q: ohang ±12 bilan chegaralangan.
        assertEquals("12", SpeedText.sanitizeSemitones("123"))
        // Minus faqat boshida turadi.
        assertEquals("3", SpeedText.sanitizeSemitones("3-"))
    }

    @Test
    fun `bosh maydon ozgarmagan qiymat beradi`() {
        assertEquals(1.0, SpeedText.parseSpeed(""), 1e-9)
        assertEquals(1.0, SpeedText.parseSpeed("."), 1e-9)
        assertEquals(0.0, SpeedText.parseSemitones(""), 1e-9)
    }

    @Test
    fun `nuqta ham vergul ham ozgarmagan holda oqiladi`() {
        assertEquals(1.5, SpeedText.parseSpeed("1.5"), 1e-9)
        assertEquals(1.5, SpeedText.parseSpeed("1,5"), 1e-9)
        assertEquals(-0.5, SpeedText.parseSemitones("-0,5"), 1e-9)
    }

    @Test
    fun `chegaradan chiqqan qiymat qisiladi`() {
        assertEquals(2.0, SpeedText.parseSpeed("9"), 1e-9)
        assertEquals(0.5, SpeedText.parseSpeed("0.1"), 1e-9)
        assertEquals(12.0, SpeedText.parseSemitones("99"), 1e-9)
        assertEquals(-12.0, SpeedText.parseSemitones("-99"), 1e-9)
    }

    @Test
    fun `qiymat maydon korinishiga ogiriladi`() {
        assertEquals("1", SpeedText.formatSpeed(1.0))
        assertEquals("1.5", SpeedText.formatSpeed(1.5))
        assertEquals("0.5", SpeedText.formatSpeed(0.5))
        assertEquals("2", SpeedText.formatSpeed(5.0))
        assertEquals("0", SpeedText.formatSemitones(0.0))
        assertEquals("-12", SpeedText.formatSemitones(-12.0))
        assertEquals("7.5", SpeedText.formatSemitones(7.5))
    }

    @Test
    fun `sirgish qadam bilan ketadi`() {
        assertEquals("1.05", SpeedText.nudgeSpeed("1", SpeedText.STEP_SPEED))
        assertEquals("1.95", SpeedText.nudgeSpeed("2", -SpeedText.STEP_SPEED))
        assertEquals("12", SpeedText.nudgeSemitones("11", SpeedText.STEP_SEMITONES))
        assertEquals("-12", SpeedText.nudgeSemitones("-11", -SpeedText.STEP_SEMITONES))
    }

    @Test
    fun `sirgish chegaradan otib ketmaydi`() {
        assertEquals("2", SpeedText.nudgeSpeed("2", SpeedText.STEP_SPEED))
        assertEquals("0.5", SpeedText.nudgeSpeed("0.5", -SpeedText.STEP_SPEED))
        assertEquals("12", SpeedText.nudgeSemitones("12", SpeedText.STEP_SEMITONES))
        assertEquals("-12", SpeedText.nudgeSemitones("-12", -SpeedText.STEP_SEMITONES))
    }

    @Test
    fun `bosh maydonni surish ozgarmagan qiymatdan boshlanadi`() {
        // Bo'sh maydonda «-» bosilsa, natija 0.5 emas, 1.0 dan boshlanadi:
        // aks holda tugma tezlikni eng past darajaga tushirib qo'yardi.
        assertEquals("0.95", SpeedText.nudgeSpeed("", -SpeedText.STEP_SPEED))
        assertEquals("1", SpeedText.nudgeSemitones("", SpeedText.STEP_SEMITONES))
    }

    @Test
    fun `yarim tonlar kopaytiruvchiga togri ogiriladi`() {
        // O'n ikki yarim ton — bir oktava, ya'ni ikki baravar baland.
        assertEquals(1.0, SpeedText.pitchRatio(0.0), 1e-12)
        assertEquals(2.0, SpeedText.pitchRatio(12.0), 1e-12)
        assertEquals(0.5, SpeedText.pitchRatio(-12.0), 1e-12)

        // Teng temperatsiyalangan kvinta: 2^(7/12). Qiymat tashqaridan
        // ma'lum — shuning uchun bu yerda son bilan qotiriladi, «o'zini
        // o'zi tekshirish» bo'lib qolmasin.
        assertEquals(1.4983070768766815, SpeedText.pitchRatio(7.0), 1e-12)

        // Kvarta pastga: 2^(-5/12).
        assertEquals(0.7491535384383408, SpeedText.pitchRatio(-5.0), 1e-12)
    }

    @Test
    fun `chegaradagi yarim tonlar oktavaga togri keladi`() {
        // Ilova beradigan eng katta va eng kichik qiymat: ±12.
        assertEquals(2.0, SpeedText.pitchRatio(SpeedText.MAX_SEMITONES), 1e-12)
        assertEquals(0.5, SpeedText.pitchRatio(SpeedText.MIN_SEMITONES), 1e-12)
        assertEquals(12.0, SpeedText.SEMITONES_PER_OCTAVE, 1e-12)
    }

    @Test
    fun `qadam chegaralari dvigatel chegaralariga mos`() {
        assertEquals(0.5, SpeedText.MIN_SPEED, 1e-9)
        assertEquals(2.0, SpeedText.MAX_SPEED, 1e-9)
        assertEquals(-12.0, SpeedText.MIN_SEMITONES, 1e-9)
        assertEquals(12.0, SpeedText.MAX_SEMITONES, 1e-9)
    }
}
