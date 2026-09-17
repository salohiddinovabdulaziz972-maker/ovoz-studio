package uz.ovozstudio.app.media.tag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.format.AudioContainer

/**
 * Foydalanuvchi kiritgan maydonlardan tegga o'tish.
 *
 * Bu yerda tekshiriladigan narsa — **kiritishga chidamlilik**: odam
 * qo'lda yozadi, xato yozadi, ortiqcha bo'sh joy qoldiradi. Yaroqsiz
 * kiritish ilovani yiqitmasligi va jimgina o'tib ketmasligi kerak —
 * birinchi holat foydalanuvchini yo'qotadi, ikkinchisi ishonchni.
 */
class TagDraftTest {

    @Test
    fun `bosh maydonlar tegga tushmaydi`() {
        val tags = TagDraft(title = "  Kitob  ", artist = "   ").toTags()
        assertEquals("Kitob", tags.title)
        assertEquals("", tags.artist)
    }

    @Test
    fun `tartib raqami va jami oqiladi`() {
        val tags = TagDraft(title = "K", track = "3/12").toTags()
        assertEquals(3, tags.track)
        assertEquals(12, tags.trackTotal)
        assertEquals("3/12", tags.trackText)
    }

    @Test
    fun `faqat raqam kiritilsa jami nol boladi`() {
        val tags = TagDraft(title = "K", track = "7").toTags()
        assertEquals(7, tags.track)
        assertEquals(0, tags.trackTotal)
        assertEquals("7", tags.trackText)
    }

    @Test
    fun `yaroqsiz tartib raqami jimgina nolga aylanadi`() {
        // Foydalanuvchi harf yozsa ham ilova yiqilmaydi — maydon shunchaki
        // bo'sh qoladi. Xato xabari bu yerda ortiqcha: u yozishni
        // to'xtatmaydi, faqat tartib raqami tegda ko'rinmaydi.
        assertEquals(0, TagDraft(title = "K", track = "abc").toTags().track)
        assertEquals(0, TagDraft(title = "K", track = "/").toTags().track)
        assertEquals(0, TagDraft(title = "K", track = "-5").toTags().track)
        assertEquals(0, TagDraft(title = "K", track = "").toTags().track)
    }

    @Test
    fun `ortiqcha raqamlar kesiladi`() {
        // «3/12/2026» — ikkinchi qism o'qiladi, qolgani e'tiborsiz.
        val tags = TagDraft(title = "K", track = "3/12/2026").toTags()
        assertEquals(3, tags.track)
        assertEquals(12, tags.trackTotal)
        // Uzun raqam xotirani talab qilmaydi: to'rt xona yetarli.
        assertEquals(2026, TagDraft(title = "K", track = "20260917").toTags().track)
    }

    @Test
    fun `yil tort xonagacha qisqartiriladi`() {
        assertEquals("2026", TagDraft(title = "K", year = "2026").toTags().year)
        assertEquals("2026", TagDraft(title = "K", year = "2026-09-17").toTags().year)
        assertEquals("", TagDraft(title = "K", year = "   ").toTags().year)
    }

    @Test
    fun `bosh teg xato deb belgilanadi`() {
        // Fayl tanlangan, lekin bitta ham maydon to'ldirilmagan: yozadigan
        // narsa yo'q. Jimgina «saqlandi» deyish — yolg'on.
        val error = TagDraft().validationError(fileSelected = true)
        assertEquals(TagError.EMPTY_TAGS, error)
    }

    @Test
    fun `faqat tartib raqami kiritilgani yetarli`() {
        // Nom bo'sh bo'lsa ham, tartib raqami — teg. Foydalanuvchi faqat
        // shuni tuzatmoqchi bo'lishi mumkin.
        assertNull(TagDraft(track = "4").validationError(fileSelected = true))
    }

    @Test
    fun `fayl tanlanmagan bolsa xato`() {
        assertEquals(
            TagError.NO_FILE,
            TagDraft(title = "Kitob").validationError(fileSelected = false),
        )
    }

    @Test
    fun `katta muqova yozishdan oldin rad etiladi`() {
        val draft = TagDraft(
            title = "Kitob",
            cover = ByteArray(Mp3Tagger.MAX_COVER_BYTES + 1),
        )
        assertEquals(TagError.COVER_TOO_LARGE, draft.validationError(fileSelected = true))
        // Chegaradagi rasm o'tadi — chegara «dan katta», «dan kichik» emas.
        val atLimit = TagDraft(title = "Kitob", cover = ByteArray(Mp3Tagger.MAX_COVER_BYTES))
        assertNull(atLimit.validationError(fileSelected = true))
    }

    @Test
    fun `teg faqat mp3 da yoziladi`() {
        assertTrue(supportsId3Tags(AudioContainer.MP3))
        // MP4/M4A o'z atomlarini, FLAC va OGG Vorbis izohini ishlatadi:
        // ularga ID3 yozilsa pleyer tegni ovoz deb o'qib, boshidan
        // shovqin chiqarardi.
        for (other in AudioContainer.entries - AudioContainer.MP3) {
            assertFalse("${other.displayName} da ID3 yozilmasligi kerak", supportsId3Tags(other))
        }
    }

    @Test
    fun `muqova va mime teg ichiga otadi`() {
        val cover = byteArrayOf(1, 2, 3)
        val tags = TagDraft(title = "K", cover = cover, coverMime = "image/png").toTags()
        assertTrue(tags.cover!!.contentEquals(cover))
        assertEquals("image/png", tags.coverMime)
        assertFalse(tags.isEmpty)
    }
}
