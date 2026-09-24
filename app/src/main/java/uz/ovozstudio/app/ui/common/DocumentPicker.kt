package uz.ovozstudio.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import uz.ovozstudio.app.util.DeviceFolders

/**
 * Fayl tanlash oynasi qurilmaning **kerakli papkasidan** ochiladi.
 *
 * `ActivityResultContracts.OpenDocument` ni o'zgartirmasdan ishlatib
 * bo'lmaydi: u niyatni o'zi quradi va boshlang'ich joy qo'shishga yo'l
 * qoldirmaydi. Shuning uchun standart kontraktning ustiga yupqa qatlam
 * yozildi — u qurilgan niyatga `EXTRA_INITIAL_URI` qo'shadi, qolgan hamma
 * narsa (natijani o'qish, ruxsat bayroqlari) o'sha-o'sha qoladi.
 *
 * Papka topilmasa niyat o'zgarishsiz qoladi: oyna odatdagi joyidan
 * ochiladi. Bu xato emas, shuning uchun foydalanuvchiga xabar berilmaydi.
 */
object DocumentPicker {

    /** Audio faylni bitta tanlash; oyna audio papkasidan ochiladi. */
    object OpenAudio : ActivityResultContract<Array<String>, Uri?>() {
        private val base = ActivityResultContracts.OpenDocument()

        override fun createIntent(context: Context, input: Array<String>): Intent =
            base.createIntent(context, input).putExtra(DocumentsContract.EXTRA_INITIAL_URI, DeviceFolders.audio)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            base.parseResult(resultCode, intent)
    }

    /** Hujjatni bitta tanlash; oyna `Documents` papkasidan ochiladi. */
    object OpenDocument : ActivityResultContract<Array<String>, Uri?>() {
        private val base = ActivityResultContracts.OpenDocument()

        override fun createIntent(context: Context, input: Array<String>): Intent =
            base.createIntent(context, input)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DeviceFolders.documents)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            base.parseResult(resultCode, intent)
    }

    /** Bir nechta hujjatni tanlash (birlashtirish ekrani). */
    object OpenDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        private val base = ActivityResultContracts.OpenMultipleDocuments()

        override fun createIntent(context: Context, input: Array<String>): Intent =
            base.createIntent(context, input)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DeviceFolders.documents)

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
            base.parseResult(resultCode, intent)
    }
}
