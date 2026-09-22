package uz.ovozstudio.app.log

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Ilovadagi yagona jurnal nuqtasi.
 *
 * Har qanday joydan `ErrorLog.error("teg", "nima bo'ldi", xato)` deb
 * chaqiriladi — kontekst kerak emas. Ilova ishga tushganda [install]
 * jurnalni ochadi; undan oldin (yoki sinovlarda) chaqiruvlar hech narsa
 * qilmaydi va ilovani yiqitmaydi.
 *
 * Har bir yozuv ikki joyga tushadi: ilovaning jurnal fayliga (foydalanuvchi
 * uni ko'radi va ulashadi) va Logcat'ga (dasturchi kompyuterga ulanganda).
 *
 * Teg — qisqa joy nomi: `trim.edit`, `pdf.save`, `reader.load`. Xabarga fayl
 * nomi yoki matn mazmuni yozilmaydi (qarang: [ErrorJournal]).
 */
object ErrorLog {

    private const val LOGCAT_TAG = "OvozStudio"
    private const val DIRECTORY = "jurnal"

    @Volatile
    private var journal: ErrorJournal? = null

    /** Jurnalni ochadi va kutilmagan to'xtashlarni ushlaydigan ishlovchini qo'yadi. */
    fun install(context: Context) {
        val app = context.applicationContext
        journal = ErrorJournal(File(app.filesDir, DIRECTORY), header(app))

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            Thread.UncaughtExceptionHandler { thread, error ->
                // Avval yozamiz, keyin tizimning odatiy ishlovchisiga beramiz:
                // ilova baribir to'xtaydi, lekin sababi jurnalda qoladi.
                try {
                    fatal("crash", "Ilova kutilmaganda to'xtadi (oqim: ${thread.name})", error)
                } catch (ignored: Throwable) {
                    // Jurnal ishlamasa ham odatiy ishlovchi chaqirilishi shart.
                }
                previous?.uncaughtException(thread, error)
            },
        )
    }

    /** Ochiq jurnal; [install] chaqirilmagan bo'lsa `null`. */
    fun journal(): ErrorJournal? = journal

    fun info(tag: String, message: String) {
        Log.i(LOGCAT_TAG, "$tag: $message")
        journal?.append(LogLevel.INFO, tag, message)
    }

    fun error(tag: String, message: String, error: Throwable? = null) {
        Log.e(LOGCAT_TAG, "$tag: $message", error)
        journal?.append(LogLevel.ERROR, tag, message, error)
    }

    fun fatal(tag: String, message: String, error: Throwable? = null) {
        Log.e(LOGCAT_TAG, "$tag: $message", error)
        journal?.append(LogLevel.FATAL, tag, message, error)
    }

    /** Jurnal boshidagi ma'lumot: xatoni qaysi qurilmada ko'rilgani. */
    private fun header(context: Context): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (ignored: Exception) {
            null
        }
        return buildString {
            append("Ovoz Studio — xatolar jurnali\n")
            append("Versiya: ").append(version ?: "noma'lum").append('\n')
            append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Qurilma: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Jurnal faqat shu qurilmada saqlanadi va o'zi hech qayerga yuborilmaydi.")
        }
    }
}
