package uz.ovozstudio.app.ui.common

/**
 * Fayl tanlash oynasiga beriladigan turlar.
 *
 * Ro'yxat tor bo'lsa, fayl oynada **umuman ko'rinmay qoladi** — va buni
 * foydalanuvchi tushunmaydi: u papkani ochadi-yu, ichida fayl yo'qdek
 * tuyuladi. Ba'zi ilovalar turni noto'g'ri bildiradi (`.ogg` ni
 * `application/ogg` deb beradi), ba'zilari esa butunlay boshqa tur qo'yadi.
 *
 * Shuning uchun qoida: tanlash oynasiga **hamma fayl** ko'rsatiladi
 * ([ALL]), yaroqsizini esa ilovaning o'zi tushunarli xabar bilan rad etadi.
 * Bu xavfsiz: ilova faqat foydalanuvchi tanlagan faylni ochadi, tanlash esa
 * o'zi ruxsat talab qilmaydi.
 */
object FileTypes {

    /** Hamma fayl: papkadagi hech narsa ko'zdan yashirilmaydi. */
    val ALL: Array<String> = arrayOf("*/*")

    /** Audio. Tanlash oynasida toraytirilmaydi — sabab yuqorida. */
    val AUDIO: Array<String> = ALL

    val PDF: Array<String> = ALL

    /** Hujjatlar: format turi noaniq bo'lgani uchun hamma fayl. */
    val DOCUMENTS: Array<String> = ALL
}
