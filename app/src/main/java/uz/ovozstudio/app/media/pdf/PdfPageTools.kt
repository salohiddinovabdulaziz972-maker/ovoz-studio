package uz.ovozstudio.app.media.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.IOException

/** PDF ochish uchun parol kerak. Ilova parol so'ramaydi: bunday fayl ochilmaydi. */
class PdfPasswordException : IOException("PDF parol bilan himoyalangan")

/** Fayl muallifi sahifalarni ajratib olishni taqiqlagan (PDF ruxsat bayrog'i). */
class PdfNoPermissionException : IOException("PDF himoyalangan: sahifalarni ajratishga ruxsat yo'q")

/** Bitta sahifaning matni. [number] — 1 dan boshlanadigan tartib raqami. */
class PdfPageText(val number: Int, val text: String)

/**
 * PDF sahifalari bilan ishlash: sahifalarni kesib olish, o'chirish, matnini o'qish.
 *
 * Bu yerda PDF'ni o'zimiz tahlil qilmaymiz — buning uchun sinalgan kutubxona
 * (`pdfbox-android`, Apache-2.0) ishlatiladi. Sabab: PDF'lar juda xilma-xil
 * (obyekt oqimlari, shifrlash, buzuq havolalar, murakkab shriftlar), o'zimiz
 * yozgan tahlilchi bunday fayllarning bir qismini o'qiy olmasdi.
 *
 * Qoidalar:
 *  - manba fayl hech qachon o'zgartirilmaydi, natija har doim yangi fayl;
 *  - hujjat xotiraga to'liq yuklanmaydi: vaqtinchalik fayl rejimi (yuzlab
 *    megabaytlik skaner kitoblari ham ochiladi);
 *  - natija «yangi hujjatga ko'chirish» yo'li bilan yasaladi, ya'ni o'chirilgan
 *    sahifalarning mazmuni faylda **qolmaydi**;
 *  - yozilgan fayl qayta ochib tekshiriladi.
 *
 * Barcha funksiyalar og'ir ish qiladi — faqat fon oqimidan chaqiriladi.
 */
object PdfPageTools {

    private var initialized = false

    /** PDFBox resurslarini (shrift jadvallari) bir marta yuklaydi. */
    @Synchronized
    private fun prepare(context: Context) {
        if (initialized) return
        PDFBoxResourceLoader.init(context.applicationContext)
        initialized = true
    }

    /**
     * Faylni ochadi. Vaqtinchalik fayl rejimi: hujjat mazmuni xotirada emas,
     * diskda turadi.
     */
    @Throws(IOException::class)
    private fun open(context: Context, file: File): PDDocument {
        prepare(context)
        try {
            return PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
        } catch (error: InvalidPasswordException) {
            throw PdfPasswordException()
        }
    }

    /** Sahifalar soni. */
    @Throws(IOException::class)
    fun pageCount(context: Context, file: File): Int =
        open(context, file).use { document -> document.getNumberOfPages() }

    /** [pages] (1 dan boshlanadi) dan yangi PDF yasaydi: faqat shu sahifalar qoladi. */
    @Throws(IOException::class)
    fun extract(context: Context, source: File, pages: List<Int>, dest: File) {
        copyPages(context, source, dest) { pages }
    }

    /** [pages] (1 dan boshlanadi) ni olib tashlab, qolganidan yangi PDF yasaydi. */
    @Throws(IOException::class)
    fun delete(context: Context, source: File, pages: List<Int>, dest: File) {
        copyPages(context, source, dest) { total -> PageRange.complement(pages, total) }
    }

    /**
     * Har bir sahifaning matni, sahifa tartibida. Matni yo'q sahifa (skaner
     * qilingan rasm) ro'yxatga kirmaydi.
     *
     * @param onProgress `(tayyor, jami)` — uzun kitobda ekran «ishlayapti»
     *   deb turishi uchun.
     */
    @Throws(IOException::class)
    fun readText(
        context: Context,
        file: File,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<PdfPageText> {
        open(context, file).use { document ->
            val total = document.getNumberOfPages()
            val stripper = PDFTextStripper()
            val result = ArrayList<PdfPageText>()
            for (number in 1..total) {
                stripper.setStartPage(number)
                stripper.setEndPage(number)
                val text = stripper.getText(document).trim()
                if (text.isNotEmpty()) result.add(PdfPageText(number, text))
                onProgress(number, total)
            }
            return result
        }
    }

    /**
     * Tanlangan sahifalarni yangi hujjatga ko'chiradi.
     *
     * [select] hujjatdagi sahifalar sonini oladi va **saqlanadigan** sahifalarni
     * qaytaradi. Kesib olish ham, o'chirish ham shu bitta yo'ldan o'tadi —
     * o'chirish «qolganini kesib olish» bo'ladi.
     */
    @Throws(IOException::class)
    private fun copyPages(
        context: Context,
        source: File,
        dest: File,
        select: (Int) -> List<Int>,
    ) {
        var expected = 0
        open(context, source).use { document ->
            val total = document.getNumberOfPages()
            val keep = select(total)
            if (keep.isEmpty()) throw IOException("Natijada birorta ham sahifa qolmaydi")
            for (number in keep) {
                if (number < 1 || number > total) throw IOException("Sahifa hujjatdan tashqarida: $number")
            }
            requireAssemblyAllowed(document)

            // Manba hujjat natija yozilguncha ochiq turishi shart: sahifa
            // mazmuni saqlash paytida shu yerdan o'qiladi.
            PDDocument().use { output ->
                for (number in keep) {
                    val imported = output.importPage(document.getPage(number - 1))
                    detach(imported)
                }
                output.save(dest)
            }
            expected = keep.size
        }

        // Yozilgan fayl qayta ochiladi: buzuq natijani foydalanuvchiga
        // «tayyor» deb berib bo'lmaydi.
        val actual = open(context, dest).use { document -> document.getNumberOfPages() }
        if (actual != expected) {
            throw IOException("Natija tekshiruvdan o'tmadi: $actual sahifa, $expected kutilgan")
        }
    }

    /** Muallif sahifalarni ajratishni taqiqlagan bo'lsa — hurmat qilinadi. */
    private fun requireAssemblyAllowed(document: PDDocument) {
        if (!document.isEncrypted()) return
        val permission = document.getCurrentAccessPermission()
        if (!permission.isOwnerPermission() && !permission.canAssembleDocument()) {
            throw PdfNoPermissionException()
        }
    }

    private val NAME_B = COSName.getPDFName("B")
    private val NAME_P = COSName.getPDFName("P")
    private val NAME_A = COSName.getPDFName("A")
    private val NAME_S = COSName.getPDFName("S")
    private val NAME_DEST = COSName.getPDFName("Dest")
    private val NAME_ANNOTS = COSName.getPDFName("Annots")

    /**
     * Ko'chirilgan sahifani eski hujjat bilan bog'liqlikdan ajratadi.
     *
     * Havolalar (annotatsiya) boshqa sahifalarga ishora qiladi va kutubxona
     * ularni **birga** yozib yuboradi — natijada «o'chirilgan» sahifalar
     * fayl ichida ko'rinmas holda qolib ketardi (hajm ortadi, mazmun sizib
     * chiqadi). Shuning uchun boshqa sahifaga olib boradigan havola va
     * ko'rsatkichlar olib tashlanadi; tashqi manzilga (URL) havolalar qoladi.
     */
    private fun detach(page: PDPage) {
        val dictionary: COSDictionary = page.getCOSObject()
        // Maqola boncuklari (`/B`) zanjir bo'ylab boshqa sahifalarga bog'langan.
        dictionary.removeItem(NAME_B)

        val annotations = dictionary.getDictionaryObject(NAME_ANNOTS)
        if (annotations !is COSArray) return
        for (index in 0 until annotations.size()) {
            val annotation = annotations.getObject(index)
            if (annotation !is COSDictionary) continue
            annotation.removeItem(NAME_P)
            annotation.removeItem(NAME_DEST)
            val action = annotation.getDictionaryObject(NAME_A)
            if (action is COSDictionary && action.getNameAsString(NAME_S) == "GoTo") {
                annotation.removeItem(NAME_A)
            }
        }
    }
}
