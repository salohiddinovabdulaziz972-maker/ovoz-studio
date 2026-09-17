package uz.ovozstudio.app.media.doc

/**
 * Hujjat o'qishda yuzaga keladigan xatolar. Har biri ekranda alohida
 * xabarga aylanadi: «fayl juda katta» va «fayl buzuq» — foydalanuvchi
 * uchun ikki xil muammo va ikki xil yechim.
 */

/** Fayl o'qish chegarasidan katta. */
class DocumentTooLargeException(limitBytes: Long) :
    Exception("Hujjat juda katta: $limitBytes baytdan oshdi")

/** Fayl tuzilishi kutilganidek emas (buzuq arxiv, kerakli fayl yo'q). */
open class DocumentFormatException(reason: String) : Exception(reason)

/**
 * Fayl to'g'ri o'qildi, lekin ichida o'qiladigan matn yo'q.
 *
 * Alohida tur, chunki foydalanuvchi uchun bu **boshqa muammo**: format
 * qo'llab-quvvatlanmaydi degan xabar «boshqa formatda saqlang» deb
 * yo'naltirsa, bu — «kitob skaner qilingan rasm, matn qatlami yo'q» va
 * yechim boshqa (OCR). Eng ko'p uchraydigan joyi — skaner qilingan PDF.
 */
class DocumentTextMissingException(reason: String) : DocumentFormatException(reason)
