package uz.ovozstudio.app.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Faylni boshqa ilovalarga ulashish.
 *
 * Android 7 dan boshlab ilovalar bir-biriga `file://` yo'l bera olmaydi:
 * faqat `content://` havola, u esa `FileProvider` orqali beriladi (qarang:
 * `AndroidManifest.xml` va `res/xml/file_paths.xml`). Boshqa yo'l bilan
 * ulashish `FileUriExposedException` bilan yiqilardi.
 *
 * Fayl boshqa ilovaga **o'qish uchun** beriladi va ruxsat faqat shu
 * chaqiruv uchun amal qiladi (`FLAG_GRANT_READ_URI_PERMISSION`) — doimiy
 * ruxsat berilmaydi.
 */
object Sharing {

    /**
     * Ulashish oynasini ochadi.
     *
     * `false` — ulashish boshlanmadi: fayl yo'q, yo'l `file_paths.xml` da
     * ko'rsatilmagan yoki qurilmada mos ilova topilmadi. Bu holatda ekran
     * tushunarli xabar ko'rsatadi; jimgina hech narsa qilmaslik — eng
     * yomon javob, foydalanuvchi tugmani bosganini biladi.
     */
    fun share(context: Context, file: File, mime: String, chooserTitle: String): Boolean {
        if (!file.exists()) return false
        return runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(send, chooserTitle)
            // Ekran (Activity) dan ochilganda yangi vazifa kerak emas, lekin
            // ilova kontekstidan chaqirilsa bayroqsiz `startActivity`
            // ishlamaydi. Shuning uchun u faqat shu holatda qo'shiladi.
            if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
            true
        }.getOrDefault(false)
    }

    // MP3 fayl uchun tur. Umumiy «audio» turidan aniqroq: qabul qiluvchi
    // ilovalar shunga qarab filtrlaydi va to'g'ri pleyerni tanlaydi.
    const val MP3_MIME = "audio/mpeg"

    /** WAV fayl uchun tur — aralashtirish natijasi shu formatda chiqadi. */
    const val WAV_MIME = "audio/wav"
}
