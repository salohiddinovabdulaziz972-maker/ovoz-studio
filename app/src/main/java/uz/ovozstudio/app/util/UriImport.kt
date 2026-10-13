package uz.ovozstudio.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.WorkStore
import java.io.File
import java.io.FileOutputStream

/**
 * Tizim tanlagichidan kelgan `content://` faylni ilovaning ish papkasiga
 * nusxalaydi (PDF ekranlari uchun; audio uchun [uz.ovozstudio.app.media.format.AudioOpener]).
 *
 * Nusxa ilovaniki — uni o'chirish xavfsiz. Foydalanuvchining asl fayli
 * o'z joyida qoladi va o'zgarmaydi.
 *
 * Faqat fon oqimidan chaqiriladi.
 */
object UriImport {

    private const val TAG = "fayl.nusxa"
    private const val BUFFER_BYTES = 64 * 1024

    /** Nusxalangan fayl va foydalanuvchi ko'radigan asl nomi. */
    class Copied(val file: File, val displayName: String)

    /**
     * Havoladagi faylni ish papkasiga nusxalaydi. O'qib bo'lmasa — `null`
     * (sabab jurnalga yoziladi, fayl nomisiz).
     */
    fun copy(context: Context, store: WorkStore, uri: Uri, fallbackExtension: String): Copied? {
        val name = displayName(context, uri)
        val extension = name.substringAfterLast('.', "").ifEmpty { fallbackExtension }
        val target = store.newSourceFile(extension)
        return try {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                ErrorLog.error(TAG, "Tizim fayli ochilmadi (oqim yo'q)", null)
                target.delete()
                null
            } else {
                input.use { source ->
                    FileOutputStream(target).use { sink -> source.copyTo(sink, BUFFER_BYTES) }
                }
                Copied(target, name)
            }
        } catch (error: Exception) {
            ErrorLog.error(TAG, "Faylni nusxalab bo'lmadi", error)
            target.delete()
            null
        }
    }

    /** Foydalanuvchi ko'radigan nom. Provayder nom bermasa — havola oxiridagi qism, u ham bo'lmasa — umumiy nom. */
    fun displayName(context: Context, uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()

        return fromProvider?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "yuklangan"
    }
}
