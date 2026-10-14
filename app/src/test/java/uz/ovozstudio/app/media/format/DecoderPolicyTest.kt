package uz.ovozstudio.app.media.format

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dekoderni tanlash tartibi va oxirida yiqilgan dekoderning natijasini baholash.
 *
 * Bu qoidalar qurilmadagi Samsung xatosidan kelib chiqqan: ishlab chiqaruvchining
 * dekoderi fayl o'rtasida yiqilardi va hamma ovozli amal fayl ochishda to'xtardi.
 */
class DecoderPolicyTest {

    @Test
    fun `platforma dekoderlari ishlab chiqaruvchinikidan oldin sinaladi`() {
        val listed = listOf(
            "c2.sec.mp3.decoder",
            "OMX.SEC.mp3.dec",
            "OMX.google.mp3.decoder",
            "c2.android.mp3.decoder",
        )

        assertEquals(
            listOf(
                "c2.android.mp3.decoder",
                "OMX.google.mp3.decoder",
                "c2.sec.mp3.decoder",
                "OMX.SEC.mp3.dec",
            ),
            DecoderPolicy.order(listed),
        )
    }

    @Test
    fun `faqat ishlab chiqaruvchi dekoderi bo'lsa tartib saqlanadi`() {
        val listed = listOf("c2.qti.wma.decoder", "OMX.qcom.wma.dec")

        assertEquals(listed, DecoderPolicy.order(listed))
    }

    @Test
    fun `takrorlangan nom bir marta olinadi`() {
        val listed = listOf("c2.android.aac.decoder", "c2.sec.aac.decoder", "c2.android.aac.decoder")

        assertEquals(
            listOf("c2.android.aac.decoder", "c2.sec.aac.decoder"),
            DecoderPolicy.order(listed),
        )
    }

    @Test
    fun `bo'sh ro'yxat bo'sh qoladi`() {
        assertEquals(emptyList<String>(), DecoderPolicy.order(emptyList()))
    }

    @Test
    fun `platforma dekoderi faqat nom boshiga qarab aniqlanadi`() {
        assertTrue(DecoderPolicy.isPlatform("c2.android.flac.decoder"))
        assertTrue(DecoderPolicy.isPlatform("OMX.google.vorbis.decoder"))
        assertTrue(DecoderPolicy.isPlatform("C2.ANDROID.AAC.DECODER"))

        assertFalse(DecoderPolicy.isPlatform("c2.sec.aac.decoder"))
        assertFalse(DecoderPolicy.isPlatform("OMX.SEC.aac.dec"))
        // Nom ichida «android» bo'lgani bilan platformaniki emas.
        assertFalse(DecoderPolicy.isPlatform("c2.vendor.android.decoder"))
    }

    @Test
    fun `oxirida yiqilgan dekoder to'liq audio bergan bo'lsa qabul qilinadi`() {
        // 10 soniya, hammasi yozilgan.
        assertTrue(DecoderPolicy.endCrashAcceptable(441_000L, 44_100, 10_000_000L))
    }

    @Test
    fun `bir soniyagacha yetishmasa qabul qilinadi`() {
        // 9,5 soniya yozilgan, 10 soniya aytilgan.
        assertTrue(DecoderPolicy.endCrashAcceptable(418_950L, 44_100, 10_000_000L))
    }

    @Test
    fun `bir soniyadan ko'p yetishmasa qabul qilinmaydi`() {
        // 8 soniya yozilgan, 10 soniya aytilgan: qisqargan audio.
        assertFalse(DecoderPolicy.endCrashAcceptable(352_800L, 44_100, 10_000_000L))
    }

    @Test
    fun `qisqa faylda birinchi bufergayoq yiqilgan dekoder qabul qilinmaydi`() {
        // 2 soniyalik fayl, faqat 1152 kadr (~26 ms) yozilgan.
        assertFalse(DecoderPolicy.endCrashAcceptable(1_152L, 44_100, 2_000_000L))
    }

    @Test
    fun `yarim soniyalik faylda bir soniya yetishmovchilik kechirilmaydi`() {
        // 0,5 soniyalik fayl, faqat ~26 ms yozilgan: butun faylga yaqin yo'qotish.
        assertFalse(DecoderPolicy.endCrashAcceptable(1_152L, 44_100, 500_000L))
        // 0,46 soniya yozilgan: yetishmovchilik 40 ms, davomiylikning o'ninchi ulushidan kam.
        assertTrue(DecoderPolicy.endCrashAcceptable(20_286L, 44_100, 500_000L))
    }

    @Test
    fun `hech narsa yozilmagan bo'lsa qabul qilinmaydi`() {
        assertFalse(DecoderPolicy.endCrashAcceptable(0L, 44_100, 10_000_000L))
        assertFalse(DecoderPolicy.endCrashAcceptable(0L, 44_100, 0L))
    }

    @Test
    fun `davomiylik noma'lum bo'lsa kamida bitta kadr yetarli`() {
        assertTrue(DecoderPolicy.endCrashAcceptable(1_152L, 44_100, 0L))
    }

    @Test
    fun `namuna chastotasi noto'g'ri bo'lsa qabul qilinmaydi`() {
        assertFalse(DecoderPolicy.endCrashAcceptable(1_000L, 0, 10_000_000L))
    }

    @Test
    fun `uzoq faylda ham hisob to'lib ketmaydi`() {
        // 10 soat, 48 kHz: Int bilan hisoblansa to'lib ketardi.
        val frames = 48_000L * 36_000L
        assertTrue(DecoderPolicy.endCrashAcceptable(frames, 48_000, 36_000_000_000L))
    }
}
