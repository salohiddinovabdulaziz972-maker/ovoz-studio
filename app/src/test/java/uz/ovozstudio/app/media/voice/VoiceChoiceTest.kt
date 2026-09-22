package uz.ovozstudio.app.media.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * O'zbekcha ovozlarni ajratish, ayniqsa Microsoft Sardor va Madina.
 */
class VoiceChoiceTest {

    private fun voice(name: String, tag: String) = VoiceInfo(id = name, localeTag = tag, name = name, quality = 300)

    @Test
    fun `sardor va madina nomdan taniladi`() {
        assertTrue(VoiceChoice.isMicrosoftUzbek("uz-UZ-SardorNeural"))
        assertTrue(VoiceChoice.isMicrosoftUzbek("Microsoft Madina Online (Natural) - Uzbek (Uzbekistan)"))
        assertTrue(VoiceChoice.isMicrosoftUzbek("MADINA"))
        assertFalse(VoiceChoice.isMicrosoftUzbek("uz-uz-x-uzf-local"))
        assertFalse(VoiceChoice.isMicrosoftUzbek("en-US-Jenny"))
    }

    @Test
    fun `ozbek til tegi taniladi`() {
        assertTrue(VoiceChoice.isUzbek("uz"))
        assertTrue(VoiceChoice.isUzbek("uz-UZ"))
        assertTrue(VoiceChoice.isUzbek("uz-Cyrl-UZ"))
        assertFalse(VoiceChoice.isUzbek("ru-RU"))
        assertFalse(VoiceChoice.isUzbek(""))
    }

    @Test
    fun `avtomatik ovoz faqat sardor yoki madina boladi`() {
        val voices = listOf(
            voice("en-US-Jenny", "en-US"),
            voice("uz-uz-x-local", "uz-UZ"),
            voice("uz-UZ-SardorNeural", "uz-UZ"),
        )
        assertEquals("uz-UZ-SardorNeural", VoiceChoice.automatic(voices)?.id)
    }

    @Test
    fun `sardor yoki madina yoq bolsa avtomatik ovoz yoq`() {
        val voices = listOf(voice("en-US-Jenny", "en-US"), voice("uz-uz-x-local", "uz-UZ"))
        assertNull(VoiceChoice.automatic(voices))
        assertNull(VoiceChoice.automatic(emptyList()))
    }

    @Test
    fun `tavsiya ozbekcha ovozlarni sardor va madina boshida beradi`() {
        val voices = listOf(
            voice("ru-RU-Svetlana", "ru-RU"),
            voice("uz-uz-x-local", "uz-UZ"),
            voice("uz-UZ-SardorNeural", "uz-UZ"),
            voice("en-US-Jenny", "en-US"),
        )
        assertEquals(
            listOf("uz-UZ-SardorNeural", "uz-uz-x-local"),
            VoiceChoice.recommended(voices).map { it.id },
        )
    }

    @Test
    fun `ozbekcha ovoz bolmasa hamma ovoz tartiblanib beriladi`() {
        val voices = listOf(
            voice("ru-RU-Svetlana", "ru-RU"),
            voice("en-US-Jenny", "en-US"),
        )
        assertEquals(
            listOf("en-US-Jenny", "ru-RU-Svetlana"),
            VoiceChoice.recommended(voices).map { it.id },
        )
        assertFalse(VoiceChoice.hasUzbek(voices))
    }

    @Test
    fun `ro'yxat chegaradan oshmaydi`() {
        val voices = (1..100).map { voice("ovoz-$it", "en-US") }
        assertEquals(VoiceChoice.MAX_LISTED, VoiceChoice.recommended(voices).size)
    }

    @Test
    fun `hasUzbek ozbekcha ovozni topadi`() {
        assertTrue(VoiceChoice.hasUzbek(listOf(voice("x", "uz-UZ"))))
        assertTrue(VoiceChoice.hasUzbek(listOf(voice("Madina", "en-US"))))
        assertFalse(VoiceChoice.hasUzbek(emptyList()))
    }
}
