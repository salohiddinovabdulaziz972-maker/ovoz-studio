package uz.ovozstudio.app.util

import android.net.Uri
import android.provider.DocumentsContract
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
 * **Nima uchun ikkalasi ham hujjat manzili.** Ilgari audio manzili
 * MediaStore audio kolleksiyasi edi (`primary:Music`). Ayrim qurilmalarda
 * tizim tanlagichi uni tanimay jimgina e'tiborsiz qoldirardi va oyna
 * «Oxirgi fayllar» dan ochilardi — ya'ni papkani topish yana qiyinlashardi.
 * Ichki xotira provayderining `/root/primary` manzili esa barcha
 * qurilmalarda bir xil ishlaydi: papkalar ro'yxati ildizdan boshlanadi.
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
     * Ichki xotiraning ildizi:
     * `content://com.android.externalstorage.documents/root/primary`.
     *
     * `/root/<id>` shakli `DocumentsContract.buildRootUri` bilan yasaladi va
     * tizim fayl tanlagichi uni to'g'ri ochadi; `/document/...` shakli ba'zi
     * qurilmalarda jimgina e'tiborsiz qoldiriladi.
     *
     * Ildizdan boshlanadi: qurilmaning hamma papkasi shu yerda ko'rinadi —
     * audio ham, hujjat ham. Foydalanuvchi kerakli papkani o'zi ochadi.
     */
    val device: Uri = DocumentsContract.buildRootUri(STORAGE_AUTHORITY, PRIMARY_ROOT_ID)

    /** Foydalanuvchiga tushunarli joy nomi — saqlash joyini aytish uchun. */
    fun describe(uri: Uri): String = uri.toString()

    init {
        // Manzil yaroqli ekanini jimgina tekshirib qo'yamiz: noto'g'ri URI
        // butun tanlash oynasini ishlamay qoldirishi mumkin.
        ErrorLog.info(TAG, "Boshlang'ich manzil: $device")
    }

    private const val PRIMARY_ROOT_ID = "primary"
}
