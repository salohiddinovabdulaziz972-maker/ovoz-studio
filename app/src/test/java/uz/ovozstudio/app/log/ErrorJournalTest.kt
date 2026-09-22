package uz.ovozstudio.app.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Xatolar jurnali.
 *
 * Asosiy qoida: jurnal xatoni yozadi, lekin hech qachon o'zi xatoga sabab
 * bo'lmaydi va shaxsiy ma'lumotni saqlamaydi.
 */
class ErrorJournalTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun journal(maxBytes: Long = ErrorJournal.DEFAULT_MAX_BYTES): ErrorJournal =
        ErrorJournal(File(folder.root, "jurnal"), "SARLAVHA", maxBytes)

    @Test
    fun `yozuv vaqt daraja teg va xabar bilan yoziladi`() {
        val journal = journal()

        journal.append(LogLevel.ERROR, "trim.edit", "Tahrirlash bajarilmadi")

        val text = journal.readAll()
        assertTrue(text, Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[XATO\\] trim\\.edit — Tahrirlash bajarilmadi").containsMatchIn(text))
    }

    @Test
    fun `sarlavha faqat bir marta yoziladi`() {
        val journal = journal()

        journal.append(LogLevel.ERROR, "a", "birinchi")
        journal.append(LogLevel.ERROR, "b", "ikkinchi")

        val text = journal.readAll()
        assertEquals(1, Regex("SARLAVHA").findAll(text).count())
        assertEquals(2, journal.entryCount())
    }

    @Test
    fun `istisno turi xabari va sabablari yoziladi`() {
        val journal = journal()
        val error = RuntimeException("tashqi", IllegalStateException("ichki"))

        journal.append(LogLevel.ERROR, "x", "xato", error)

        val text = journal.readAll()
        assertTrue(text, text.contains("java.lang.RuntimeException: tashqi"))
        assertTrue(text, text.contains("Sabab: java.lang.IllegalStateException: ichki"))
    }

    @Test
    fun `uzun stek qisqartiriladi`() {
        val journal = journal()
        val error = RuntimeException("chuqur")
        error.stackTrace = Array(50) { StackTraceElement("Sinf", "usul$it", "Fayl.kt", it) }

        journal.append(LogLevel.ERROR, "x", "xato", error)

        val text = journal.readAll()
        assertTrue(text, text.contains("usul19"))
        assertFalse(text, text.contains("usul20"))
        assertTrue(text, text.contains("… yana 30 qator"))
    }

    @Test
    fun `fayl nomi va yol yashiriladi`() {
        val journal = journal()

        journal.append(
            LogLevel.ERROR,
            "x",
            "ochilmadi: content://media/external/audio/12/Maxfiy ovoz.mp3 va /storage/emulated/0/Music/shaxsiy.mp3",
        )

        val text = journal.readAll()
        assertFalse(text, text.contains("Maxfiy"))
        assertFalse(text, text.contains("shaxsiy"))
        assertTrue(text, text.contains("content://…"))
        assertTrue(text, text.contains("<yo'l>"))
    }

    @Test
    fun `hajm to'lganda eski qism alohida faylga ko'chadi`() {
        val journal = journal(maxBytes = 600)
        val directory = File(folder.root, "jurnal")

        for (index in 1..12) {
            journal.append(LogLevel.INFO, "sinov", "yozuv raqami $index uzun matn uzun matn uzun matn")
        }

        assertTrue(File(directory, ErrorJournal.PREVIOUS_NAME).exists())
        // Eng oxirgi yozuv doim saqlanadi, jami hajm chegaradan ancha oshmaydi.
        assertTrue(journal.readAll().contains("yozuv raqami 12"))
        assertTrue(journal.sizeBytes() < 600 * 3)
    }

    @Test
    fun `tozalash hamma narsani ochiradi`() {
        val journal = journal()
        journal.append(LogLevel.ERROR, "a", "xato")

        journal.clear()

        assertEquals("", journal.readAll())
        assertEquals(0, journal.entryCount())
        assertEquals(0L, journal.sizeBytes())
    }

    @Test
    fun `readTail yozuv chegarasidan boshlanadi`() {
        val journal = journal()
        for (index in 1..30) {
            journal.append(LogLevel.INFO, "sinov", "yozuv $index")
        }

        val tail = journal.readTail(300)

        assertTrue(tail.length <= 300)
        assertTrue(tail, Regex("^\\d{4}-\\d{2}-\\d{2} ").containsMatchIn(tail))
        assertTrue(tail, tail.contains("yozuv 30"))
    }

    @Test
    fun `eksport butun jurnalni faylga yozadi`() {
        val journal = journal()
        journal.append(LogLevel.ERROR, "a", "birinchi xato")
        val target = File(folder.root, "chiqish/jurnal.txt")

        val ok = journal.exportTo(target)

        assertTrue(ok)
        assertTrue(target.readText().contains("birinchi xato"))
    }

    @Test
    fun `bo'sh jurnal eksportida ham sarlavha bor`() {
        val journal = journal()
        val target = File(folder.root, "bosh.txt")

        assertTrue(journal.exportTo(target))

        assertTrue(target.readText().contains("SARLAVHA"))
    }

    @Test
    fun `yozib bo'lmasa ilova yiqilmaydi`() {
        // Jurnal papkasi o'rnida oddiy fayl turibdi: papka yaratib bo'lmaydi.
        val blocker = File(folder.root, "jurnal")
        blocker.writeText("men papka emasman")
        val journal = ErrorJournal(blocker, "SARLAVHA")

        journal.append(LogLevel.ERROR, "a", "xato")

        assertEquals("", journal.readAll())
    }
}
