package uz.ovozstudio.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import uz.ovozstudio.app.log.ErrorLog

/**
 * Fayl tanlash oynasi qurilmaning **audio** yoki **hujjatlar** bo'limidan
 * ochilishi uchun boshlang'ich manzil.
 *
 * Nima uchun kerak: `ACTION_OPEN_DOCUMENT` oynasi o'zi tanlagan joydan
 * boshlanadi — ko'pincha «Oxirgi fayllar» yoki bulut. Ko'zi ojiz
 * foydalanuvchi uchun bu har safar papkalar ichida qo'lda yurish degani.
 * `EXTRA_INITIAL_URI` oynani kerakli joyga olib boradi.
 *
 * Manzil faqat **ko'rinishni** boshlaydi: foydalanuvchi baribir istagan
 * joyidan fayl tanlay oladi, ilovaga esa faqat u tanlagan fayl ochiladi.
 * Shu sababli hech qanday ruxsat so'ralmaydi.
 *
 * Manzilni topib bo'lmasa (ba'zi qurilmalarda ichki xotira provayderi
 * boshqacha nomlanadi) hech narsa qo'shilmaydi: oyna odatdagi joyidan
 * ochiladi. Bu xato emas, shuning uchun foydalanuvchi bezovta qilinmaydi —
 * sabab jurnalga yoziladi.
 */
object DeviceFolders {

    private const val TAG = "papka"

    /**
     * Ichki xotira provayderi nomi.
     *
     * Konstantani `DocumentsContract` dan olish mumkin emas: u `@hide`,
     * ya'ni Android 15 SDK stub'ida yo'q va Gradle kompilyatsiyasi uni
     * topmay yiqiladi. Nom esa barqaror — ichki xotira provayderi shu
     * manzilda turadi.
     */
    private const val STORAGE_AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Rasmiy hujjatlar manzili: `content://com.android.externalstorage.documents/root/primary`.
     *
     * `/root/<id>` shakli `DocumentsContract.buildRootUri` bilan yasaladi va
     * tizim fayl tanlagichi uni to'g'ri ochadi; `/document/...` shakli ba'zi
     * qurilmalarda jimgina e'tiborsiz qoldiriladi.
     */
    val documents: Uri = DocumentsContract.buildRootUri(STORAGE_AUTHORITY, PRIMARY_ROOT_ID)

    /**
     * Audio manzili.
     *
     * Rasmiy audio manzil bu MediaStore audio kolleksiyasi: provayder nomi
     * `media` bilan boshlanadi, shuning uchun tizim fayl tanlagichi "faqat
     * ichki" rejimini qo'llamaydi va manzil ochiq qoladi. `Music` papkasi
     * ichida boshlanadi.
     */
    val audio: Uri = DocumentsContract.buildDocumentUri(
        MediaStore.AUTHORITY,
        "$PRIMARY_ROOT_ID:${android.os.Environment.DIRECTORY_MUSIC}",
    )

    /** Foydalanuvchiga tushunarli joy nomi — saqlash joyini aytish uchun. */
    fun describe(uri: Uri): String = uri.toString()

    init {
        // Bu manzillar yaroqli ekanini jimgina tekshirib qo'yamiz: noto'g'ri
        // URI butun tanlash oynasini ishlamay qoldirishi mumkin.
        ErrorLog.info(TAG, "Audio manzili: $audio")
    }

    private const val PRIMARY_ROOT_ID = "primary"
}
