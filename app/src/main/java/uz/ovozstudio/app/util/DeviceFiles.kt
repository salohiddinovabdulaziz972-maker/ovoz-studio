package uz.ovozstudio.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import uz.ovozstudio.app.log.ErrorLog
import java.io.File
import java.util.Locale

/**
 * Qurilmaning o'z papkalarini o'qish.
 *
 * Nima uchun kerak. Ilgari fayl tizim tanlagichi orqali olinardi va uning
 * nusxasi ilovaning vaqtinchalik papkasiga ko'chirilar edi. Nusxa vaqtinchalik
 * bo'lgani uchun keyin yo'q bo'lib qolardi va foydalanuvchi «fayl topilmadi»
 * degan xabarni ko'rardi. Bu yerdagi yo'l boshqa: fayl ko'chirilmaydi, uning
 * qurilmadagi haqiqiy yo'li ishlatiladi. Shunda fayl o'z joyida qoladi, boshqa
 * ilovada ham ochiladi va qurilma qayta yoqilgandan keyin ham yo'qolmaydi.
 *
 * Nima uchun faqat ichki xotira. SD karta va USB xotira alohida ruxsat yo'lini
 * talab qiladi (`MANAGE_EXTERNAL_STORAGE`). Ular hozircha ochilmagan — bu
 * alohida qaror, ilova ularsiz ham to'liq ishlaydi.
 */
object DeviceFiles {

    private const val TAG = "papka.fayllar"

    /** Ilova o'z natijalarini yozadigan papka nomi — `MediaSaver` bilan bir xil. */
    private const val FOLDER = "Ovoz Studio"

    /** Android 13 dan boshlab eski `READ_EXTERNAL_STORAGE` o'rniga yangi ruxsatlar. */
    private const val PERMISSION: String = Manifest.permission.READ_EXTERNAL_STORAGE

    /**
     * O'qish ruxsati berilganmi.
     *
     * `isAudio` bo'yicha farq qiladi, chunki Android 13 dan boshlab ruxsatlar
     * tur bo'yicha ajratilgan: `READ_MEDIA_AUDIO` faqat audio uchun, PDF uchun
     * esa alohida o'qish ruxsati yo'q. PDF so'rovini so'rash foydalanuvchini
     * keraksiz oynaga tiqardi va foyda bermasdi.
     *
     * Android 13 va undan yuqorida tanlash oynasi ruxsatsiz ham ishlaydi:
     * `Music` va `Documents` — ommaviy papkalar, ulardagi fayllar MediaStore
     * orqali ochiq. Quyi versiyalarda esa eski `READ_EXTERNAL_STORAGE` kerak.
     */
    fun hasAccess(context: Context, isAudio: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(context, PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** So'raladigan ruxsat; Android 13 dan yuqorida so'raladigan narsa yo'q. */
    fun neededPermission(isAudio: Boolean): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) null else PERMISSION

    /** Tanlash oynasi ochiladigan dastlabki papka. */
    fun root(isAudio: Boolean): File = File(
        Environment.getExternalStorageDirectory(),
        if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOCUMENTS,
    )

    /**
     * Foydalanuvchiga ko'rsatiladigan joy nomi.
     *
     * To'liq texnik yo'l (`/storage/emulated/0/Music`) eshitish uchun juda uzun
     * va tushunarsiz. Shuning uchun ildiz qismi olib tashlanadi: «Music/Ovoz
     * Studio». Ildizning o'zi ko'rsatilsa — «Ichki xotira».
     */
    fun displayPath(file: File): String {
        val root = Environment.getExternalStorageDirectory()
        val full = file.absolutePath
        val relative = when {
            full == root.absolutePath -> ""
            full.startsWith(root.absolutePath + "/") -> full.removePrefix(root.absolutePath + "/")
            else -> return full
        }
        return relative.ifEmpty { ROOT_LABEL }
    }

    /**
     * Papka ichidagi yozuvlar: papkalar birinchi, keyin fayllar, alifbo
     * tartibida. Har bir papka uchun eng yuqorida «yuqoriga» qatori qo'yiladi.
     */
    fun list(context: Context, folder: File): List<Entry> {
        val children = folder.listFiles()
        if (children == null) {
            // `listFiles()` `null` qaytaradi — ruxsat yo'q yoki papka o'chirilgan.
            // Sababni jurnalga yozamiz: ekranda bo'sh ro'yxat ko'rinadi, aks
            // holda u «papka bo'sh» degan noto'g'ri taassurot qoldirardi.
            ErrorLog.info(TAG, "Papka o'qilmadi: ${folder.absolutePath}")
            return emptyList()
        }
        val dirs = children.filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
        val files = children
            .filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".nomedia") }
            .sortedBy { it.name.lowercase(Locale.ROOT) }

        val root = Environment.getExternalStorageDirectory()
        val entries = mutableListOf<Entry>()
        if (folder.absolutePath != root.absolutePath) {
            entries += Entry(folder, isUp = true)
        }
        entries += dirs.map { Entry(it, isUp = false) }
        entries += files.map { Entry(it, isUp = false) }
        return entries
    }

    /**
     * Hajmni odam o'qiy oladigan ko'rinishga keltiradi: «1,2 MB».
     *
     * O'lchov birligi va o'nlik ajratgichi joriy tildan olinadi — o'zbek tilida
     * ham, rus tilida ham to'g'ri chiqadi.
     */
    fun readableSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB")
        var value = bytes.toDouble() / 1024
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (value >= 100) {
            String.format(Locale.getDefault(), "%.0f %s", value, units[unit])
        } else {
            String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
        }
    }

    /** Ichki xotira ildizi ko'rsatilganda chiqadigan nom. */
    private const val ROOT_LABEL = "Ichki xotira"

    /** Bir qator: papka, fayl yoki «yuqoriga». */
    class Entry(val file: File, val isUp: Boolean) {
        val name: String get() = file.name
        val isDirectory: Boolean get() = file.isDirectory
        val length: Long get() = file.length()

        override fun toString(): String = name
    }
}
