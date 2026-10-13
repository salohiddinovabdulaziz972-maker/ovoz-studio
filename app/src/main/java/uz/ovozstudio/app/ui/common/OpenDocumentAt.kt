package uz.ovozstudio.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Tizim fayl tanlash oynasi — **to'g'ridan-to'g'ri kerakli papkadan** ochiladi:
 * audio uchun `Music`, PDF uchun `Documents`.
 *
 * Oddiy `ActivityResultContracts.OpenDocument` oynani oxirgi ochilgan joydan
 * boshlaydi, ya'ni foydalanuvchi har safar papkalar ichida yo'l qidirishi
 * kerak (ko'zi ojiz foydalanuvchi uchun bu bir necha qo'shimcha bosish va
 * ekranni o'qish). Bu shartnoma oynaga boshlang'ich joyni aytadi
 * (`EXTRA_INITIAL_URI`, Android 8 dan boshlab). Boshlang'ich joy — «iloji
 * boricha» ko'rsatma: Samsung kabi ba'zi qurilmalardagi tanlagich uni
 * e'tiborsiz qoldirishi mumkin; u holda oyna odatdagidek ochiladi va fayl
 * baribir tanlanadi.
 *
 * Fayl turi cheklanmaydi (`*` / `*`): ba'zi qurilmalarda audio yoki PDF fayl
 * noto'g'ri MIME turi bilan beriladi va tur bo'yicha filtr uni yashirib
 * qo'yardi.
 */
class OpenDocumentAt(private val isAudio: Boolean) :
    ActivityResultContract<Array<String>, Uri?>() {

    override fun createIntent(context: Context, input: Array<String>): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, startFolder(isAudio))
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null

    companion object {
        private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /**
         * Ichki xotiradagi `Music` (audio) yoki `Documents` (PDF) papkasining
         * hujjat manzili. `DocumentsContract.buildDocumentUri` identifikatorni
         * o'zi to'g'ri kodlaydi.
         */
        fun startFolder(isAudio: Boolean): Uri = DocumentsContract.buildDocumentUri(
            STORAGE_AUTHORITY,
            "primary:" + if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DOCUMENTS,
        )
    }
}
