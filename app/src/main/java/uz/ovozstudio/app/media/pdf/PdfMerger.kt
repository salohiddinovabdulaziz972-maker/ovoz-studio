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

        var expectedFile = false

        // Natija hujjati butun ish davomida ochiq turadi: `importPage` sahifa
        // mazmunini saqlash paytida manbadan o'qiydi, shuning uchun manbalar
        // ham, natija ham bir vaqtda ochiq bo'lishi kerak.
        PDDocument().use { output ->
            try {
                // Barcha manbalar natija yozilgunga qadar OCHIQ turishi shart.
                //
                // Ilgari har bir manba o'z blokining oxirida yopilar edi, natija
                // esa eng oxirida saqlanardi. Kutubxona sahifa mazmunini aynan
                // saqlash paytida manba hujjatdan o'qiydi — yopilgan hujjatdan
                // o'qish esa «COSStream has been closed» xatosini beradi. Bu
                // xato aynan shu ilovada takrorlangan (0.6.0, Samsung SM-A716S).
                // Shuning uchun hujjatlar ro'yxatda saqlanadi va saqlashdan keyin
                // birgalikda yopiladi; xotira rejimi vaqtinchalik fayl bo'lgani
                // uchun ular diskda turadi, xotirani ko'paytirmaydi.
                val opened = ArrayList<PDDocument>(sources.size)
                try {
                    var done = 0
                    for (source in sources) {
                        val document = PdfPageTools.openSource(context, source.file)
                        opened += document
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

                    // Manbalar shu yerda hali ham ochiq — saqlash xavfsiz.
                    output.save(dest)
                    expectedFile = true
                } finally {
                    // Xato bo'lsa ham hamma hujjat yopiladi: ochiq qolgan hujjat
                    // vaqtinchalik faylini ushlab turadi va joy egallaydi.
                    for (document in opened) {
                        runCatching { document.close() }
                    }
                }
            } catch (error: IOException) {
                // Chala natija qoldirilmaydi — lekin butun fayl ham o'chirilmaydi.
                // Istisno saqlashdan KEYIN (tekshiruvda) chiqqan bo'lsa, fayl
                // to'liq yozilgan: uni o'chirish foydalanuvchining ishini yo'q
                // qilardi.
                if (!expectedFile) runCatching { dest.delete() }
                throw error
            }

            // Yozilgan fayl qayta ochiladi: sahifalar soni kutilganidek
            // bo'lishi shart.
            val actual = PdfPageTools.openSource(context, dest).use { it.getNumberOfPages() }
            if (actual != expected) {
                throw IOException("Natija tekshiruvdan o'tmadi: $actual sahifa, $expected kutilgan")
            }
        }
    }
}
