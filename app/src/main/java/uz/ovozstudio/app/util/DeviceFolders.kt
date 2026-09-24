package uz.ovozstudio.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import uz.ovozstudio.app.log.ErrorLog

/**
 * Fayl tanlash oynasini qurilmaning **audio** yoki **hujjatlar** papkasida
 * ochadi.
 *
 * Nima uchun kerak: `OpenDocument` oynasi o'zi tanlagan joydan boshlanadi —
 * ko'pincha «Oxirgi fayllar» yoki bulut. Ko'zi ojiz foydalanuvchi uchun bu
 * har safar papkalar ichida qo'lda yurish degani. `EXTRA_INITIAL_URI` esa
 * oynani to'g'ridan-to'g'ri kerakli papkaga olib boradi.
 *
 * Papkani topib bo'lmasa (ba'zi qurilmalarda `ExternalStorageProvider`
 * boshqacha nomlanadi) shunchaki URI berilmaydi: oyna odatdagi joyidan
 * ochiladi. Bu xato emas — shuning uchun foydalanuvchiga xabar berilmaydi,
 * faqat jurnalga yoziladi.
 */
object DeviceFolders {

    private const val TAG = "papka"

    /** Audio fayllar uchun boshlang'ich joy (MediaStore audio papkasi). */
    fun audio(context: Context): Uri? = runCatching {
        initialUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
    }.onFailure { ErrorLog.error(TAG, "Audio papkasi topilmadi", it) }.getOrNull()

    /** Hujjatlar uchun boshlang'ich joy (`Documents` papkasi). */
    fun documents(context: Context): Uri? = runCatching {
        val authority = DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY
        val root = DocumentsContract.buildRootUri(authority, DOCUMENTS_ROOT_ID)
        // Ildiz topilmasa zaxira: butun xotira ildizi.
        if (context.contentResolver.query(root, null, null, null, null)?.use { it.count > 0 } == true) {
            DocumentsContract.buildDocumentUri(authority, "$DOCUMENTS_ROOT_ID:Documents")
        } else {
            DocumentsContract.buildRootUri(authority, PRIMARY_ROOT_ID)
        }
    }.onFailure { ErrorLog.error(TAG, "Hujjatlar papkasi topilmadi", it) }.getOrNull()

    /** Intent ichidagi `EXTRA_INITIAL_URI` — tanlash oynasi shu joydan ochiladi. */
    private fun initialUri(uri: Uri): Uri = uri

    /**
     * `ActivityResultContracts` yaratgan xom niyatga boshlang'ich joyni
     * qo'shadi. `Intent.ACTION_OPEN_DOCUMENT` ning rasmiy qo'shimchasi shu,
     * shuning uchun tanlash oynasi o'zgarishsiz qoladi.
     */
    fun withInitial(context: Context, intent: Intent, folder: Uri?): Intent {
        if (folder == null) return intent
        return intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, folder)
    }

    /**
     * Tizim fayl boshqaruvchisini shu papkada ochishga urinadi.
     * Ochilmadi — `false`.
     */
    fun openInFileManager(context: Context, folder: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folder, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (error: ActivityNotFoundException) {
            ErrorLog.info(TAG, "Fayl boshqaruvchisi yo'q: ${folder}")
            false
        }
    }

    private const val DOCUMENTS_ROOT_ID = "com.android.externalstorage.documents"
    private const val PRIMARY_ROOT_ID = "primary"

    init {
        // Logcat'da bu sinf ishlatilganini ko'rish uchun (bo'sh init — ataylab).
        Log.isLoggable(TAG, Log.DEBUG)
    }
}
