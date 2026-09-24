package uz.ovozstudio.app.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import uz.ovozstudio.app.util.DeviceFolders

/**
 * Fayl tanlash oynasi qurilmaning **ichki xotirasidan** ochiladi.
 *
 * `ActivityResultContracts.OpenDocument` ni o'zgartirmasdan ishlatib
 * bo'lmaydi: u niyatni o'zi quradi va boshlang'ich joy qo'shishga yo'l
 * qoldirmaydi. Shuning uchun standart kontraktning ustiga yupqa qatlam
 * yozildi — u qurilgan niyatga `EXTRA_INITIAL_URI` qo'shadi, qolgan hamma
 * narsa (natijani o'qish, ruxsat bayroqlari) o'sha-o'sha qoladi.
 *
 * Boshlang'ich joy — ichki xotiraning ildizi, ya'ni qurilmaning hamma
 * papkasi bir ekranda ko'rinadi. Ilgari audio manzili MediaStore audio
 * kolleksiyasiga ishora qilardi; ayrim qurilmalarda tanlagich uni tanimay,
 * oyna «Oxirgi fayllar» dan ochilardi va papkani topish qiyinlashardi.
 *
 * Papka topilmasa niyat o'zgarishsiz qoladi: oyna odatdagi joyidan
 * ochiladi. Bu xato emas, shuning uchun foydalanuvchiga xabar berilmaydi.
 */
object DocumentPicker {

    /** Audio faylni bitta tanlash. */
    object OpenAudio : ActivityResultContract<Array<String>, Uri?>() {
        private val base = ActivityResultContracts.OpenDocument()

        override fun createIntent(context: Context, input: Array<String>): Intent =
            base.createIntent(context, input)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DeviceFolders.device)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            base.parseResult(resultCode, intent)
    }

    /** Hujjat yoki audio: istalgan turdagi faylni bitta tanlash. */
    object OpenAny : ActivityResultContract<Array<String>, Uri?>() {
        private val base = ActivityResultContracts.OpenDocument()

        override fun createIntent(context: Context, input: Array<String>): Intent =
            base.createIntent(context, input)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DeviceFolders.device)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            base.parseResult(resultCode, intent)
    }

    /** Bir nechta hujjatni tanlash (birlashtirish ekrani). */
    object OpenDocuments : ActivityResultContract<Array<String>, List<Uri>>() {
        private val base = ActivityResultContracts.OpenMultipleDocuments()

        override fun createIntent(context: Context, input: Array<String>): Intent =
            base.createIntent(context, input)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI, DeviceFolders.device)

        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
            base.parseResult(resultCode, intent)
    }

    /**
     * Yangi hujjat yaratish (birlashtirish natijasi PDF bo'lsa).
     *
     * Manzil berilmaydi: fayl nomini va joyini foydalanuvchi o'zi tanlaydi,
     * bu ekranda bu — kutilgan xatti-harakat.
     */
    object CreatePdf : ActivityResultContract<String, Uri?>() {
        private val base = ActivityResultContracts.CreateDocument("application/pdf")

        override fun createIntent(context: Context, input: String): Intent =
            base.createIntent(context, input)

        override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
            base.parseResult(resultCode, intent)
    }
}
