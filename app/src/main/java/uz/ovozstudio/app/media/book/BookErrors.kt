package uz.ovozstudio.app.media.book

/**
 * Audio-kitob yig'ishdagi xato.
 *
 * Sabab — matn emas, tayyor xabar: bu qatlam sof JVM'da ishlaydi va
 * qaysi tilda ko'rsatishni bilmaydi. Xabarni UI qatlami tarjima qiladi,
 * xato turi esa shu yerda qoladi.
 */
class BookAssemblyException(message: String) : Exception(message)
