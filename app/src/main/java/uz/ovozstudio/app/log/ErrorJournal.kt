package uz.ovozstudio.app.log

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jurnal yozuvining darajasi. [label] — fayldagi ko'rinishi. */
enum class LogLevel(val label: String) {
    /** Xato emas, lekin muammoni tushunishga yordam beradi (masalan fayl rad etildi). */
    INFO("MA'LUMOT"),

    /** Amal bajarilmadi, ilova ishlashda davom etdi. */
    ERROR("XATO"),

    /** Ilova kutilmaganda to'xtadi. */
    FATAL("TO'XTASH"),
}

/**
 * Xatolar jurnali: oddiy matn fayli.
 *
 * Nega kerak. Ilova internetga chiqmaydi, shuning uchun dasturchi xatoni
 * o'zi ko'ra olmaydi. «Ishlamayapti» degan xabar esa sababni aytmaydi:
 * qaysi amal, qaysi format, qaysi xato. Jurnal shu bo'shliqni to'ldiradi —
 * foydalanuvchi uni ulashsa, sabab bir qarashda ko'rinadi.
 *
 * Qoidalar:
 *  - **jurnal yozish hech qachon ilovani yiqitmaydi**: yozib bo'lmasa (disk
 *    to'la, papka yo'q) jimgina o'tib ketadi;
 *  - hajm cheklangan ([maxBytes]): to'lganda eski fayl `.old` ga ko'chadi,
 *    undan ham eskisi o'chadi. Jurnal cheksiz o'sib, telefon xotirasini
 *    yemaydi;
 *  - **shaxsiy ma'lumot yozilmaydi**: fayl nomlari, `content://` havolalari
 *    va qurilmadagi yo'llar [redact] bilan yashiriladi. Matn mazmuni umuman
 *    jurnalga kirmaydi — faqat xatoning turi va joyi;
 *  - jurnal faqat qurilmada turadi va foydalanuvchi ulashmaguncha hech
 *    qayerga ketmaydi.
 *
 * Bu sinf Android'ga bog'liq emas — sof JVM'da sinaladi.
 */
class ErrorJournal(
    private val directory: File,
    private val header: String,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val lock = Any()
    private val current = File(directory, CURRENT_NAME)
    private val previous = File(directory, PREVIOUS_NAME)

    /** Yozuv qo'shadi. Xato bo'lsa — jimgina o'tadi (qarang: sinf hujjati). */
    fun append(level: LogLevel, tag: String, message: String, error: Throwable? = null) {
        val entry = format(level, tag, message, error)
        synchronized(lock) {
            try {
                directory.mkdirs()
                rotateIfNeeded(entry.length)
                if (!current.exists() || current.length() == 0L) {
                    current.appendText(header + "\n\n", Charsets.UTF_8)
                }
                current.appendText(entry, Charsets.UTF_8)
            } catch (ignored: IOException) {
                // Jurnal yozilmasa ham ilova ishlashi shart.
            } catch (ignored: SecurityException) {
                // Xuddi shu.
            }
        }
    }

    /** Butun jurnal: avval eski qism, keyin joriysi. Bo'sh bo'lsa — bo'sh matn. */
    fun readAll(): String = synchronized(lock) {
        try {
            val parts = ArrayList<String>()
            if (previous.exists()) parts.add(previous.readText(Charsets.UTF_8))
            if (current.exists()) parts.add(current.readText(Charsets.UTF_8))
            parts.joinToString("\n")
        } catch (ignored: IOException) {
            ""
        }
    }

    /**
     * Jurnalning oxirgi qismi (ko'pi bilan [maxChars] belgi), yozuv chegarasidan
     * boshlanadi: yarim uzilgan qator bilan boshlanmaydi.
     *
     * Ekranda butun jurnalni ko'rsatib bo'lmaydi: yuzlab kilobayt matnni
     * ekran o'quvchi bilan o'qishning hech ma'nosi yo'q. Butun jurnal —
     * ulashish uchun.
     */
    fun readTail(maxChars: Int): String {
        val all = readAll()
        if (all.length <= maxChars) return all
        val cut = all.length - maxChars
        val lineStart = all.indexOf('\n', cut)
        return if (lineStart < 0) all.substring(cut) else all.substring(lineStart + 1)
    }

    /** Yozuvlar soni (sarlavha hisobga olinmaydi). */
    fun entryCount(): Int = readAll().lineSequence().count { ENTRY_START.containsMatchIn(it) }

    /** Jurnalning diskdagi hajmi (bayt). */
    fun sizeBytes(): Long = synchronized(lock) {
        (if (current.exists()) current.length() else 0L) +
            (if (previous.exists()) previous.length() else 0L)
    }

    /** Jurnalni tozalaydi. */
    fun clear() {
        synchronized(lock) {
            current.delete()
            previous.delete()
        }
    }

    /** Butun jurnalni [target] fayliga yozadi (ulashish uchun). `false` — yozib bo'lmadi. */
    fun exportTo(target: File): Boolean = try {
        target.parentFile?.mkdirs()
        val text = readAll().ifEmpty { header + "\n" }
        target.writeText(text, Charsets.UTF_8)
        true
    } catch (ignored: IOException) {
        false
    }

    private fun rotateIfNeeded(incoming: Int) {
        if (!current.exists()) return
        if (current.length() + incoming <= maxBytes) return
        previous.delete()
        // Ko'chirib bo'lmasa, joriy fayl o'chadi: cheksiz o'sishdan yaxshiroq.
        if (!current.renameTo(previous)) current.delete()
    }

    private fun format(level: LogLevel, tag: String, message: String, error: Throwable?): String {
        val out = StringBuilder()
        out.append(stamp()).append(" [").append(level.label).append("] ")
            .append(tag).append(" — ").append(redact(message)).append('\n')

        var cause: Throwable? = error
        var depth = 0
        while (cause != null && depth < MAX_CAUSES) {
            out.append("    ")
            if (depth > 0) out.append("Sabab: ")
            out.append(cause.javaClass.name)
            val text = cause.message
            if (!text.isNullOrBlank()) out.append(": ").append(redact(text))
            out.append('\n')

            val frames = cause.stackTrace
            val shown = minOf(frames.size, MAX_FRAMES)
            for (index in 0 until shown) {
                out.append("        at ").append(frames[index].toString()).append('\n')
            }
            if (frames.size > shown) {
                out.append("        … yana ").append(frames.size - shown).append(" qator\n")
            }
            cause = cause.cause
            depth++
        }
        return out.toString()
    }

    /** `SimpleDateFormat` bir oqimli — shuning uchun har safar yangisi yasaladi. */
    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(clock()))

    companion object {
        /** Jurnal hajmi: joriy fayl shundan oshsa, eskisiga ko'chadi. Ikki fayl — eng ko'pi ikki barobar. */
        const val DEFAULT_MAX_BYTES = 256L * 1024

        const val CURRENT_NAME = "xatolar.log"
        const val PREVIOUS_NAME = "xatolar.old.log"

        /** Bitta xatoning stek qatorlari: to'liq stek ham foydasiz, ham katta. */
        private const val MAX_FRAMES = 20

        /** «Sabab» zanjirining chuqurligi. */
        private const val MAX_CAUSES = 4

        private val ENTRY_START = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[")
        private val CONTENT_URI = Regex("content://\\S+")
        private val DEVICE_PATH = Regex("/(?:storage|sdcard|data|mnt|Android)/\\S+")

        /**
         * Shaxsiy ma'lumotni yashiradi. Xato xabarlarida ko'pincha fayl yo'li
         * yoki `content://…/Ovoz yozuvi.mp3` kabi havola bo'ladi — ularda
         * foydalanuvchining fayl nomlari turadi.
         */
        fun redact(text: String): String =
            text.replace(CONTENT_URI, "content://…").replace(DEVICE_PATH, "<yo'l>")
    }
}
