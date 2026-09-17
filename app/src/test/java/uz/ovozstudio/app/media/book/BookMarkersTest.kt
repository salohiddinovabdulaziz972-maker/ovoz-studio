package uz.ovozstudio.app.media.book

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.ovozstudio.app.media.WavInfo

class BookMarkersTest {

    private fun joined(starts: List<Long>, sampleRate: Int = 8000): JoinedAudio = JoinedAudio(
        file = File("bob.wav"),
        info = WavInfo(
            sampleRate = sampleRate,
            channels = 1,
            bitsPerSample = 16,
            dataOffset = 44,
            dataSize = 0,
        ),
        parts = starts.mapIndexed { i, start ->
            JoinedPart(file = File("bolak$i.wav"), startFrame = start, frames = 1)
        },
    )

    private fun plan(vararg utterances: BookUtterance): BookChapterPlan =
        BookChapterPlan(index = 0, title = "1-BOB", utterances = utterances.toList())

    private fun title(text: String) = BookUtterance(text, 0, 0, isTitle = true)
    private fun body(text: String) = BookUtterance(text, 0, text.length, isTitle = false)

    @Test
    fun `har bir bolak oz belgisini oladi`() {
        val markers = BookMarkers.ofChapter(
            joined(listOf(0L, 4000L)),
            plan(title("1-BOB"), body("Salim aka haqida")),
        )

        assertEquals(2, markers.size)
        assertEquals("1-BOB", markers[0].title)
        assertEquals(0L, markers[0].startFrame)
        assertEquals("Salim aka haqida", markers[1].title)
        assertEquals(4000L, markers[1].startFrame)
    }

    @Test
    fun `uzun bolak qisqartiriladi`() {
        val long = "so'z ".repeat(40).trim()
        val markers = BookMarkers.ofChapter(joined(listOf(0L)), plan(body(long)))

        val title = markers.single().title
        assertTrue(
            "sarlavha chegaradan uzun: ${title.length}",
            title.length <= BookMarkers.MAX_TITLE_CHARS + 1,
        )
        assertTrue("qisqartirilgani ko'rinishi kerak", title.endsWith("…"))
    }

    @Test
    fun `qator uzilishlari bitta probelga aylanadi`() {
        val markers = BookMarkers.ofChapter(
            joined(listOf(0L)),
            plan(body("birinchi qator\n\nikkinchi\tqator")),
        )

        assertEquals("birinchi qator ikkinchi qator", markers.single().title)
    }

    @Test
    fun `sarlavhali bolak bob nomini oladi`() {
        val markers = BookMarkers.ofChapter(
            joined(listOf(0L, 100L)),
            plan(title("   "), body("Matn")),
        )

        // Sarlavha bo'sh bo'lsa ham belgi nomsiz qolmasligi kerak.
        assertEquals("1-BOB", markers[0].title)
    }

    @Test
    fun `kadr vaqti millisekundga ogiriladi`() {
        assertEquals(0L, BookMarker("a", 0, 8000).startMs)
        assertEquals(500L, BookMarker("a", 4000, 8000).startMs)
        assertEquals(1000L, BookMarker("a", 48000, 48000).startMs)
    }

    @Test
    fun `notogr namuna chastotasi belgi yasamaydi`() {
        val markers = BookMarkers.ofChapter(joined(listOf(0L), sampleRate = 0), plan(body("Matn")))

        assertTrue(markers.isEmpty())
    }

    @Test
    fun `cue vaqti yetmish besh kadrda yoziladi`() {
        // CUE sekundning 1/75 ulushida hisoblaydi: 500 ms — 37 kadr.
        assertEquals("00:00:00", BookMarkers.cueTime(BookMarker("a", 0, 8000)))
        assertEquals("00:01:00", BookMarkers.cueTime(BookMarker("a", 8000, 8000)))
        assertEquals("00:01:37", BookMarkers.cueTime(BookMarker("a", 12000, 8000)))
        assertEquals("01:01:37", BookMarkers.cueTime(BookMarker("a", 492000, 8000)))
    }

    @Test
    fun `manfiy vaqt nolga tenglashadi`() {
        assertEquals("00:00:00", BookMarkers.cueTime(BookMarker("a", -100, 8000)))
    }

    @Test
    fun `cue fayli pleyerlar kutgan korinishda boladi`() {
        val cue = BookMarkers.cueSheet(
            albumTitle = "Salim aka",
            fileName = "salim-aka.mp3",
            markers = listOf(
                BookMarker("1-BOB", 0, 8000),
                BookMarker("2-BOB", 8000, 8000),
            ),
        )

        assertTrue(cue.startsWith("REM Ovoz Studio"))
        assertTrue(cue.contains("TITLE \"Salim aka\""))
        assertTrue(cue.contains("FILE \"salim-aka.mp3\" MP3"))
        assertTrue(cue.contains("  TRACK 01 AUDIO"))
        assertTrue(cue.contains("    TITLE \"1-BOB\""))
        assertTrue(cue.contains("    INDEX 01 00:00:00"))
        assertTrue(cue.contains("  TRACK 02 AUDIO"))
        assertTrue(cue.contains("    INDEX 01 00:01:00"))
    }

    @Test
    fun `cue sarlavhasidagi qoshtirnoq almashtiriladi`() {
        // Ekranlash yo'li yo'q: qo'shtirnoq qolsa, fayl buziladi.
        val cue = BookMarkers.cueSheet(
            albumTitle = "Salim \"aka\"",
            fileName = "kitob.mp3",
            markers = listOf(BookMarker("1-\"BOB\"", 0, 8000)),
        )

        assertTrue(cue.contains("TITLE \"Salim 'aka'\""))
        assertTrue(cue.contains("TITLE \"1-'BOB'\""))
        // Faqat uchta qatorning ochib-yopuvchi qo'shtirnoqlari qoladi:
        // sarlavha ichidagilari almashtirilgan.
        assertEquals(6, cue.count { it == '"' })
    }
}
