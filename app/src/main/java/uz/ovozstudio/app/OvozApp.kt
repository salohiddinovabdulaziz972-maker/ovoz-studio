package uz.ovozstudio.app

import android.app.Application
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore

/**
 * Ilova jarayoni.
 *
 * Ikki ish faqat shu yerda, jarayon boshlanganda bajariladi:
 *  - xatolar jurnali ochiladi — birorta ekran yaratilishidan oldin, shunda
 *    ilovaning eng boshidagi xato ham jurnalga tushadi;
 *  - o'tgan ishlarning vaqtinchalik fayllari tozalanadi. Bu ekran ochilganda
 *    emas, jarayon boshida bajariladi: ochiq ekranning faylini o'chirib
 *    yuborish xavfi yo'q, chunki hali hech bir ekran yo'q.
 */
class OvozApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ErrorLog.install(this)
        // Ish fayllari to'liq tozalanadi, tayyor natijalar bir kun saqlanadi.
        try {
            WorkStore.sweep(this)
        } catch (error: Exception) {
            ErrorLog.error("app.sweep", "Vaqtinchalik fayllar tozalanmadi", error)
        }
    }
}
