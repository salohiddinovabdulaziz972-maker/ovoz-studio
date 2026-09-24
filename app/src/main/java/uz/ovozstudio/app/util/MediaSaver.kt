package uz.ovozstudio.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import uz.ovozstudio.app.log.ErrorLog
import java.io.File

/**
 * Natijani qurilmaning umumiy papkasiga — bitta bosishda — saqlaydi.
 *
 * Nima uchun MediaStore, «Fayl sifatida saqlash» oynasi emas: o'sha oynada
 * foydalanuvchi avval joy tanlaydi, keyin nomni tasdiqlaydi — ko'zi ojiz
 * foydalanuvchi uchun bu har saqlashda ikki-uch bosish va ekranni o'qish
 * degani. MediaStore esa yozishni to'g'ridan-to'g'ri audio yoki hujjatlar
 * papkasiga bajaradi: bitta tugma, natija tayyor.
 *
 * Ruxsat. O'z qo'li bilan yaratilgan yozuvni keyin o'qib-o'chirish uchun
 * Android 10+ da hech qanday ruxsat kerak emas. Android 9 va undan pastda
 * esa to'g'ridan-to'g'ri yozish uchun `WRITE_EXTERNAL_STORAGE` bo'lishi
 * kerak — u manifestda `maxSdkVersion="28"` bilan e'lon qilingan va
 * chaqiruvchi tomon uni ishga tushirishdan oldin so'raydi.
 */
object MediaSaver {

    private const val TAG = "fayl.saqlash"

    /** Saqlash natijasi: muvaffaqiyat yoki jimgina yutilmagan sabab. */
    sealed interface Result {
        /** Fayl saqlandi; [display] — foydalanuvchiga aytiladigan manzil. */
        data class Saved(val uri: Uri, val display: String) : Result

        /** Saqlanmadi. [reason] allaqachon jurnalga yozilgan. */
        data class Failed(val reason: String) : Result
    }

    /**
     * [source] ni audio ([Music]) yoki hujjatlar ([Documents]) papkasiga
     * [suggestedName] nomi bilan yozadi.
     *
     * Bir xil nomli fayl bo'lsa MediaStore nomni o'zi «(1)» qo'shib
     * ajratadi — mavjud fayl ustidan yozilmaydi, ya'ni foydalanuvchi
     * natijasini yo'qotmaydi. Qaysi yo'l tanlanganini [isAudio] belgilaydi.
     */
    fun save(
        context: Context,
        source: File,
        suggestedName: String,
        mimeType: String,
        isAudio: Boolean,
    ): Result {
        val collection = if (isAudio) audioCollection() else documentCollection()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, suggestedName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // `IS_PENDING`: tayyor bo'lmagan fayl boshqa ilovalarga
                // ko'rinmaydi. Yazish o'rtasida to'xtab qolsa ham yarim
                // fayl pleyerda paydo bo'lmaydi.
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    if (isAudio) "${Environment.DIRECTORY_MUSIC}/Ovoz Studio"
                    else "${Environment.DIRECTORY_DOCUMENTS}/Ovoz Studio",
                )
            }
        }

        val uri = try {
            context.contentResolver.insert(collection, values)
        } catch (error: Exception) {
            ErrorLog.error(TAG, "Yozuv ochilmadi: $suggestedName", error)
            null
        }
        if (uri == null) {
            // `insert` odatda istisno beradi; `null` — kam uchraydigan, lekin
            // haqiqiy holat (provayder rad etdi). Sababni yozmasak, fayl
            // shunchaki «saqlanmadi» bo'lib qolardi.
            ErrorLog.error(TAG, "Yozuv ochilmadi (provayder bo'sh URI qaytardi): $suggestedName")
            return Result.Failed("insert-null")
        }

        val ok = try {
            context.contentResolver.openOutputStream(uri, "w")?.use { sink ->
                source.inputStream().use { input -> input.copyTo(sink) }
            } != null
        } catch (error: Exception) {
            ErrorLog.error(TAG, "Fayl nusxalanmadi: $suggestedName", error)
            false
        }

        if (!ok) {
            // Yarim yozilgan yozuv qolmasligi kerak: bo'sh fayl pleyerda
            // ochilmaydi va foydalanuvchini chalg'itadi.
            runCatching { context.contentResolver.delete(uri, null, null) }
            return Result.Failed("copy")
        }

        val display = locationOf(context, uri) ?: suggestedName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            runCatching { context.contentResolver.update(uri, done, null, null) }
                .onFailure { ErrorLog.error(TAG, "Fayl «tayyor» deb belgilanmadi: $suggestedName", it) }
        }
        ErrorLog.info(TAG, "Saqlandi: $display")
        return Result.Saved(uri, display)
    }

    /**
     * MediaStore yozuvidagi ko'rinadigan joy — foydalanuvchiga aytish uchun.
     * Ba'zi provayderlar bu ustunni bermaydi, o'shanda `null`.
     */
    private fun locationOf(context: Context, uri: Uri): String? = runCatching {
        val columns = arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
        context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val path = columns.indices
                .mapNotNull { index -> if (cursor.isNull(index)) null else cursor.getString(index) }
                .joinToString("/")
            path.ifEmpty { null }
        }
    }.onFailure { ErrorLog.error(TAG, "Saqlangan joyni o'qib bo'lmadi: $uri", it) }.getOrNull()

    /**
     * Audio uchun to'plam. API 29+ da `MediaStore.Audio`; pastda esa
     * `EXTERNAL_CONTENT_URI` — o'sha jadval, lekin `RELATIVE_PATH` yo'q
     * (shuning uchun `DATA` ustuni bilan yoziladi).
     */
    private fun audioCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private fun documentCollection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Files.getContentUri("external")

    /** Android 9 va pastda yozish uchun ruxsat kerakmi. */
    val needsLegacyPermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
}
