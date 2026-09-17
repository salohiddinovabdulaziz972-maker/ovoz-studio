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
class DocumentFormatException(reason: String) : Exception(reason)
