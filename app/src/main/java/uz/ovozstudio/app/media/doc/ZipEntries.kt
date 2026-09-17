package uz.ovozstudio.app.media.doc

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Arxiv ichidan kerakli faylni chegaralangan hajmda o'qish.
 *
 * DOCX ham, EPUB ham — oddiy ZIP arxivi. Ikkalasi ham foydalanuvchidan
 * keladi, ya'ni ichida nima borligi oldindan noma'lum. Shu sababli hajm
 * ikki marta tekshiriladi: arxiv aytgan hajm bo'yicha ham (katta faylni
 * umuman o'qimaslik uchun), o'qish paytida ham (yolg'on hajm e'lon qilgan
 * «zip-bomba» uchun).
 */
internal object ZipEntries {

    fun read(zip: ZipFile, name: String, maxBytes: Long): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        if (entry.size > maxBytes) throw DocumentTooLargeException(maxBytes)
        return zip.getInputStream(entry).use { PlainTextDecoder.readLimited(it, maxBytes) }
    }

    /**
     * Arxivni ochadi. ZIP bo'lmagan fayl ham shu yerga keladi — fayl
     * kengaytmasi `.docx` bo'lishi uning DOCX ekanini bildirmaydi.
     */
    fun open(file: File): ZipFile = try {
        ZipFile(file)
    } catch (e: IOException) {
        throw DocumentFormatException("Arxiv ochilmadi: ${e.message}")
    }
}
