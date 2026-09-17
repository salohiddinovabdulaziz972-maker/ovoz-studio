package uz.ovozstudio.app.media.book

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kitobni boblar bo'ylab o'qish tartibi.
 *
 * Sinovlar chekkalarga qaratilgan: kitobning boshi va oxiri, bo'sh ro'yxat,
 * belgi yo'qligi. Aynan shu joylarda pleyer noto'g'ri o'tib ketadi.
 */
class BookPlaylistTest {

    private fun chapter(title: String, seconds: Long, markers: List<BookMarker> = emptyList()) =
        PlaylistChapter(title, File("$title.mp3"), seconds * 1000, markers)

    private fun marker(title: String, startMs: Long) =
        BookMarker(title = title, startFrame = startMs, sampleRate = 1000)

    private val kitob = BookPlaylist(
        listOf(
            chapter("1-BOB", 60),
            chapter("2-BOB", 120),
            chapter("3-BOB", 30),
        )
    )

    @Test
    fun `tartib boshidan boshlanadi`() {
        assertEquals(0, kitob.currentIndex)
        assertEquals("1-BOB", kitob.current?.title)
        assertTrue(kitob.isFirst)
        assertFalse(kitob.isLast)
        assertEquals(210_000, kitob.totalDurationMs)
    }

    @Test
    fun `keyingi va oldingi bobga otadi`() {
        assertEquals("2-BOB", kitob.advance()?.title)
        assertEquals("3-BOB", kitob.advance()?.title)
        // Oxirgi bobdan keyin o'rnida qoladi: kitob qaytadan boshlanmaydi.
        assertNull(kitob.advance())
        assertEquals("3-BOB", kitob.current?.title)

        assertEquals("2-BOB", kitob.rewind()?.title)
        assertEquals("1-BOB", kitob.rewind()?.title)
        assertNull(kitob.rewind())
        assertEquals("1-BOB", kitob.current?.title)
    }

    @Test
    fun `bobni tanlash chegaradan tashqarida holatni buzmaydi`() {
        assertSame(kitob.current, kitob.select(99))
        assertSame(kitob.current, kitob.select(-1))
        assertEquals("3-BOB", kitob.select(2)?.title)
    }

    @Test
    fun `kitob boylab vaqt togri hisoblanadi`() {
        // M: 0-60, 2: 60-180, 3: 180-210 soniya.
        assertEquals(0, kitob.bookPositionMs(0))
        assertEquals(65_000, kitob.bookPositionMs(65_000))

        kitob.select(1)
        assertEquals(70_000, kitob.bookPositionMs(10_000))

        kitob.select(2)
        assertEquals(180_000, kitob.bookPositionMs(0))
        assertEquals(30_000, kitob.remainingMs(0))
        assertEquals(5_000, kitob.remainingMs(25_000))
        // Kitobdan tashqariga chiqib ketmaydi.
        assertEquals(0, kitob.remainingMs(999_000))
    }

    @Test
    fun `belgi boylab oldinga va orqaga sakraladi`() {
        val bob = PlaylistChapter(
            title = "1-BOB",
            file = File("1-BOB.mp3"),
            durationMs = 60_000,
            markers = listOf(marker("Kirish", 0), marker("Asosiy", 20_000), marker("Xulosa", 45_000)),
        )
        val list = BookPlaylist(listOf(bob))

        // Boshidagi belgi «keyingi» hisoblanmaydi: u yerda turibmiz.
        assertEquals("Asosiy", list.nextMarker(0)?.title)
        assertEquals("Asosiy", list.nextMarker(5_000)?.title)
        assertEquals("Xulosa", list.nextMarker(25_000)?.title)
        assertNull("oxirgi belgidan keyin hech narsa yo'q", list.nextMarker(50_000))

        assertNull("birinchi belgidan oldin hech narsa yo'q", list.previousMarker(0))
        assertEquals("Kirish", list.previousMarker(5_000)?.title)
        assertEquals("Asosiy", list.previousMarker(44_000)?.title)
    }

    @Test
    fun `juda yaqin belgi otkazib yuboriladi`() {
        // Pleyer pozitsiyasi tugma bosilgandan keyin bir oz surilgan bo'ladi:
        // belgi «shu yerdaman» deb hisoblansa, tugma ishlamagandek ko'rinardi.
        val bob = PlaylistChapter(
            title = "1-BOB",
            file = File("1-BOB.mp3"),
            durationMs = 60_000,
            markers = listOf(marker("Kirish", 20_000), marker("Asosiy", 40_000)),
        )
        val list = BookPlaylist(listOf(bob))

        assertEquals("Asosiy", list.nextMarker(20_000)?.title)
        assertEquals("Asosiy", list.nextMarker(20_000 + BookPlaylist.EPSILON_MS / 2)?.title)
        // Oldinga sakragach orqaga qaytish ham ishlaydi.
        assertEquals("Kirish", list.previousMarker(21_000)?.title)
    }

    @Test
    fun `belgisi yoq bobda sakrash jimgina otkaziladi`() {
        val list = BookPlaylist(listOf(chapter("1-BOB", 60)))

        assertNull(list.nextMarker(10_000))
        assertNull(list.previousMarker(10_000))
        assertNull(list.markerAt(10_000))
    }

    @Test
    fun `hozirgi bolak topiladi`() {
        val bob = PlaylistChapter(
            title = "1-BOB",
            file = File("1-BOB.mp3"),
            durationMs = 60_000,
            markers = listOf(marker("Kirish", 0), marker("Asosiy", 20_000)),
        )
        val list = BookPlaylist(listOf(bob))

        assertEquals("Kirish", list.markerAt(0)?.title)
        assertEquals("Kirish", list.markerAt(19_000)?.title)
        assertEquals("Asosiy", list.markerAt(20_000)?.title)
        assertEquals("Asosiy", list.markerAt(59_000)?.title)
    }

    @Test
    fun `bosh kitob xato bermaydi`() {
        val empty = BookPlaylist(emptyList())

        assertTrue(empty.isEmpty)
        assertNull(empty.current)
        assertNull(empty.advance())
        assertNull(empty.rewind())
        assertEquals(0, empty.totalDurationMs)
        assertEquals(0, empty.remainingMs(0))
        assertTrue("birinchi ham, oxirgi ham", empty.isFirst && empty.isLast)
    }

    @Test
    fun `bitta bobli kitob chegarada togri ishlaydi`() {
        val one = BookPlaylist(listOf(chapter("Yagona", 10)))

        assertNull(one.advance())
        assertNull(one.rewind())
        assertEquals("Yagona", one.current?.title)
        assertEquals(10_000, one.remainingMs(0))
    }
}
