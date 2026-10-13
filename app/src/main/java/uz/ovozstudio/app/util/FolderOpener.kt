package uz.ovozstudio.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast
import uz.ovozstudio.app.log.ErrorLog

/**
 * «Ovoz Studio» papkasini tizim fayl boshqaruvchisida ochadi.
 *
 * Nima uchun kerak. Natija saqlangandan keyin foydalanuvchiga joy nomi
 * aytiladi («Music/Ovoz Studio/8-bob.mp3»), lekin nomni eshitish bilan uni
 * topish oson emas: ko'ruvchi foydalanuvchi bildirishnomadagi yozuvni bosib
 * bir zumda papkaga tushadi, eshitish bilan esa qo'lda papkalar ichida
 * yurishga to'g'ri kelardi. Shu tugma ikkalasiga ham bir xil imkon beradi.
 *
 * Nima uchun `DocumentsContract` manzili, `file://` emas. Android 7 dan
 * boshlab `file://` havolasi boshqa ilovaga berilsa `FileUriExposedException`
 * beriladi. Hujjat manzili esa ochiq provayder manzili: u fayl tizimiga
 * to'g'ridan-to'g'ri yo'l ochmaydi, faqat papkani ko'rsatadi.
 *
 * Zaxira yo'li yo'q. Ilgari MediaStore'dagi papka identifikatori orqali
 * ikkinchi urinish ham bor edi, lekin u ishonchsiz (ustun nomi qurilmaga
 * qarab farq qiladi) va natijada papka o'rniga butun «Musiqa» ro'yxati
 * ochilardi. Shuning uchun faqat hujjat manzili sinaladi; u ishlamasa
 * `false` qaytadi va ekran buni ovozda aytadi.
 *
 * Papkalar `MediaSaver` yozadigan joylar bilan bir xil bo'lishi shart:
 * audio — `Music/Ovoz Studio`, hujjat — `Documents/Ovoz Studio`. Mos kelmasa
 * tugma foydalanuvchini bo'sh papkaga olib borardi.
 */
object FolderOpener {

    private const val TAG = "papka.ochish"
    private const val FOLDER = "Ovoz Studio"
    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /** Ilova papkasining qurilmadagi nomi — `MediaSaver` bilan bir xil. */
    private fun relativePath(isAudio: Boolean): String =
        if (isAudio) "${Environment.DIRECTORY_MUSIC}/$FOLDER"
        else "${Environment.DIRECTORY_DOCUMENTS}/$FOLDER"

    /**
     * Papkani ochadi. `true` — biror fayl boshqaruvchisi ochildi.
     *
     * Chaqiruvchi `startActivity` istisnosini o'zi ushlamaydi: bu yerda
     * hammasi `runCatching` ichida, sabab esa jurnalga tushadi.
     *
     * Ochilmaganda jim qolish mumkin emas: `failureText` shu hol uchun
     * tayyorlangan matn (ekranning o'z tilida), u ovozda aytiladi. Sabab
     * esa jurnalga yoziladi — foydalanuvchi ko'rmaydigan tafsilot shu yerda
     * qoladi, ko'radigan qismi esa faqat bitta qisqa gap.
     */
    fun open(context: Context, isAudio: Boolean, failureText: String): Boolean {
        val path = relativePath(isAudio)

        if (openDocument(context, path)) return true

        ErrorLog.error(TAG, "Papka ochilmadi: $path")
        Toast.makeText(context, failureText, Toast.LENGTH_SHORT).show()
        return false
    }

    /**
     * `content://com.android.externalstorage.documents/document/primary%3AMusic%2FOvoz%20Studio`
     * — zamonaviy fayl boshqaruvchilari shu manzilni tushunadi.
     *
     * Manzilni `DocumentsContract.buildDocumentUri` yasaydi: u hujjat
     * identifikatorini («primary:Music/Ovoz Studio») o'zi to'g'ri kodlaydi.
     * Oldingi versiyada bo'sh joy qo'lda `%20` ga almashtirilib, keyin
     * `appendPath` uni YANA kodlardi (`%2520`) — fayl boshqaruvchisi
     * «Ovoz%20Studio» degan mavjud bo'lmagan papkani qidirardi.
     */
    private fun openDocument(context: Context, path: String): Boolean = runCatching {
        val document = DocumentsContract.buildDocumentUri(STORAGE_AUTHORITY, "primary:$path")
        launchDirectory(context, document)
    }.getOrElse { error ->
        ErrorLog.info(TAG, "Hujjat manzili ishlamadi ($path): ${error.message}")
        false
    }

    /** Papkani ko'rsatadigan har qanday ilovani ochadi. */
    private fun launchDirectory(context: Context, uri: Uri): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    }.getOrElse { error ->
        ErrorLog.info(TAG, "Papka ilovasi javob bermadi: ${error.message}")
        false
    }
}
