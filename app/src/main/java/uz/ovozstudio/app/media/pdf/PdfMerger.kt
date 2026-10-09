package uz.ovozstudio.app.media.pdf

import android.content.Context
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.IOException

/** Bitta manba fayl va undan olinadigan sahifalar ro'yxati (1 dan boshlanadi). */
class PdfMergeSource(val file: File, val pages: List<Int>)

/**
 * Bir nechta PDF ni bitta faylga birlashtirish.
 *
 * Nima uchun alohida fayl: [PdfPageTools] «bitta manba → bitta natija»
 * ustida qurilgan (kesib olish, o'chirish), birlashtirish esa «ko'p manba →
 * bitta natija». Umumiy qismi — sahifani ko'chirish va bog'liqlikdan ajratish
 * — shu sababli [PdfPageTools] ichidagi `copyPages`/`detach` qayta
 * ishlatiladi, o'z nusxasi yozilmaydi.
 *
 * Qoidalar [PdfPageTools] bilan bir xil:
 *  - manba fayllar o'zgartirilmaydi;
 *  - hujjatlar xotiraga to'liq yuklanmaydi (vaqtinchalik fayl rejimi);
 *  - natija qayta ochib tekshiriladi — buzuq fayl «tayyor» deb berilmaydi.
 */
object PdfMerger {

    @Throws(IOException::class)
    fun merge(
        context: Context,
        sources: List<PdfMergeSource>,
        dest: File,
        onProgress: (Int, Int) -> Unit,
    ) {
        if (sources.isEmpty()) throw IOException("Birlashtirish uchun fayl yo'q")

        val expected = sources.sumOf { it.pages.size }
        if (expected == 0) throw IOException("Natijada birorta ham sahifa qolmaydi")

        try {
            PDDocument().use { output ->
                var done = 0
                for (source in sources) {
                    // Har bir manba navbatma-navbat ochiladi: hammasini bir
                    // yo'la ochish xotirani chaqmoqday ko'paytiradi.
                    PdfPageTools.openSource(context, source.file).use { document ->
                        val total = document.getNumberOfPages()
                        PdfPageTools.requireAssemblyAllowed(document)
                        for (number in source.pages) {
                            if (number < 1 || number > total) {
                                throw IOException("Sahifa hujjatdan tashqarida: $number")
                            }
                            val imported = output.importPage(document.getPage(number - 1))
                            PdfPageTools.detach(imported)
                            onProgress(++done, expected)
                        }
                    }
                }
                output.save(dest)
            }

            // Yozilgan fayl qayta ochiladi: sahifalar soni kutilganidek
            // bo'lishi shart.
            val actual = PdfPageTools.openSource(context, dest).use { it.getNumberOfPages() }
            if (actual != expected) {
                throw IOException("Natija tekshiruvdan o'tmadi: $actual sahifa, $expected kutilgan")
            }
        } catch (error: IOException) {
            runCatching { dest.delete() }
            throw error
        }
    }
}
