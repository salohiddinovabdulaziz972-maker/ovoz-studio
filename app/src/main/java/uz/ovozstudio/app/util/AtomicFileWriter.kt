package uz.ovozstudio.app.util

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Faylni «yo'q yoki to'liq» qoidasi bilan yozadi.
 *
 * Nega kerak. `file.outputStream()` faylni **darrov bo'shatadi**, keyin
 * yozadi. Yozish paytida jarayon o'ldirilsa, telefon o'chib qolsa yoki disk
 * to'lsa, foydalanuvchining eski ma'lumoti (aralashma loyihasi, sozlamalar)
 * allaqachon yo'qolgan, yangisi esa yarim bo'ladi.
 *
 * Bu yerda esa yangi ma'lumot avval **vaqtinchalik faylga** yoziladi, diskka
 * haqiqatan tushirilgach, `rename` bilan eski fayl o'rniga qo'yiladi. Linux'da
 * (Android ham) `rename` bo'linmas amal: kim qaramasin, eski yoki yangi to'liq
 * fayl ko'rinadi — yarim fayl emas. Xato bo'lsa eski fayl tegilmay qoladi.
 *
 * Yozish bir vaqtda faqat bittasi ketadi (`@Synchronized`): vaqtinchalik fayl
 * nomi maqsad nomidan olinadi, ikki oqim bir faylni ustma-ust yozmasligi
 * kerak. Fayllar kichik (sozlamalar), shuning uchun navbat sezilmaydi.
 */
object AtomicFileWriter {

    /**
     * [block] ga berilgan oqimga yozilganini [target] o'rniga qo'yadi.
     *
     * [block] istisno tashlasa yoki almashtirish o'xshamasa, [target]
     * o'zgarmaydi, vaqtinchalik fayl esa o'chiriladi.
     */
    @Synchronized
    @Throws(IOException::class)
    fun write(target: File, block: (OutputStream) -> Unit) {
        val directory = target.absoluteFile.parentFile
        directory?.mkdirs()
        val temp = File(directory, "${target.name}.tmp")

        var done = false
        try {
            FileOutputStream(temp).use { stream ->
                block(stream)
                stream.flush()
                // Ma'lumot tizim keshida emas, diskda bo'lishi shart: aks holda
                // `rename` keyin elektr o'chsa, yangi fayl bo'sh chiqishi mumkin.
                stream.fd.sync()
            }
            if (!temp.renameTo(target)) {
                throw IOException("Faylni almashtirib bo'lmadi: ${target.name}")
            }
            done = true
        } finally {
            if (!done) temp.delete()
        }
    }
}
