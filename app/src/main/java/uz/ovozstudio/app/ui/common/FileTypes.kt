package uz.ovozstudio.app.ui.common

/**
 * Fayl tanlash oynasiga beriladigan turlar.
 *
 * Ro'yxat tor bo'lsa, fayl oynada ko'rinmay qoladi: ba'zi ilovalar turni
 * noto'g'ri bildiradi. Shuning uchun audio uchun ikki tur, hujjatlar uchun
 * hamma fayl ko'rsatiladi — yaroqsiz faylni ilovaning o'zi tushunarli xabar
 * bilan rad etadi.
 */
object FileTypes {

    /** Audio. Ba'zi ilovalar `.ogg` ni `application/ogg` deb beradi. */
    val AUDIO: Array<String> = arrayOf("audio/*", "application/ogg")

    val PDF: Array<String> = arrayOf("application/pdf")

    /** Hujjatlar: format turi noaniq bo'lgani uchun hamma fayl. */
    val DOCUMENTS: Array<String> = arrayOf("*/*")
}
