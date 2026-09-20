package uz.ovozstudio.app.media.doc

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PDF o'quvchining sinovi.
 *
 * Namunalar **fpdf2** bilan yasalgan (`tools/make-pdf-fixtures.py`) — ya'ni
 * matnni o'zimiz yozmadik. Bu muhim: o'zimiz yozgan PDF'ni o'zimiz o'qisak,
 * ikkala tomon bir xil noto'g'ri tasavvurga ega bo'lishi mumkin va natija
 * «to'g'ri ko'rinadi».
 *
 * Ikkala namuna ataylab kerak — ular PDF'ning ikki xil matn yo'lini
 * qamraydi: oddiy shrift (bayt = belgi) va Type0 + `ToUnicode`.
 */
class PdfTextReaderTest {

    private fun fixture(name: String): File {
        val file = File("app/src/test/fixtures/$name")
        assertTrue("namuna topilmadi: ${file.absolutePath}", file.exists())
        return file
    }

    @Test
    fun `oddiy shriftli pdf oqiladi`() {
        // Matn to'liq solishtiriladi: namuna ma'lum ro'yxatdan yasalgan,
        // shuning uchun «bor» deb tekshirish yetarli emas — ortiqcha yoki
        // takrorlangan qator ham o'tib ketardi.
        val text = PdfTextReader.read(fixture("kitob-lotin.pdf"))

        assertEquals(
            """
            BIRINCHI BOB
            Salim aka qishloqdan shaharga keldi.
            U uzoq yillar davomida shu kunni kutdi.
            IKKINCHI BOB
            Ertalab havo ochiq edi, yo'l esa changli.
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `sahifadagi tartib saqlanadi`() {
        // Matn obyekt tartibida emas, sahifa mazmuni tartibida yig'iladi.
        val text = PdfTextReader.read(fixture("kitob-lotin.pdf"))

        assertTrue("boblar tartibi buzildi", text.indexOf("BIRINCHI") < text.indexOf("IKKINCHI"))
        assertTrue(
            "qatorlar tartibi buzildi",
            text.indexOf("Salim aka") < text.indexOf("U uzoq yillar"),
        )
    }

    @Test
    fun `qatorlar alohida qoladi`() {
        // PDF'da qator tushunchasi yo'q — u koordinatalardan tiklanadi.
        // Yopishib qolsa kitob bitta uzun qator bo'lib o'qilardi.
        val text = PdfTextReader.read(fixture("kitob-lotin.pdf"))
        val lines = text.lines().filter { it.isNotBlank() }

        assertTrue(lines.toString(), lines.contains("BIRINCHI BOB"))
        assertTrue(lines.size >= 5)
    }

    @Test
    fun `tounicode jadvali orqali kirill oqiladi`() {
        // Type0 shriftida bayt kodi ixtiyoriy: matn faqat ToUnicode
        // jadvali orqali ochiladi.
        val text = PdfTextReader.read(fixture("kitob-kirill.pdf"))

        assertEquals(
            """
            BIRINCHI BOB
            Салим ака қишлоқдан шаҳарга келди.
            U "Oʻzbekiston" deb yozdi — bu toʻgʻri.
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `ozbek apostrofi saqlanadi`() {
        val text = PdfTextReader.read(fixture("kitob-kirill.pdf"))

        // U+02BB — o'zbek lotin yozuvining o'ziga xos belgisi. U
        // yo'qolsa matn «Ozbekiston» bo'lib qolardi.
        assertTrue(text, text.contains("Oʻzbekiston"))
        assertTrue(text, text.contains("toʻgʻri"))
    }

    @Test
    fun `pdf bolmagan fayl xato beradi`() {
        val error = assertThrows(DocumentFormatException::class.java) {
            PdfTextReader.read("Bu oddiy matn, PDF emas".toByteArray())
        }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("PDF"))
    }

    @Test
    fun `matnsiz pdf xato beradi`() {
        // Skaner qilingan kitob shunday ko'rinadi: sahifa bor, matn yo'q.
        assertThrows(DocumentFormatException::class.java) {
            PdfTextReader.read(emptyPagePdf().toByteArray(Charsets.ISO_8859_1))
        }
    }

    @Test
    fun `tounicode jadvali yoq shrift ochiq xato beradi`() {
        // Bunday faylda matn «savatcha» bo'lib chiqadi. Jim qolgan xatodan
        // ko'ra ochiq to'xtash yaxshi: foydalanuvchi noto'g'ri kitobni
        // tinglab qolmaydi.
        val bytes = compositeWithoutToUnicode().toByteArray(Charsets.ISO_8859_1)
        val error = assertThrows(DocumentFormatException::class.java) {
            PdfTextReader.read(bytes)
        }
        assertTrue(error.message.orEmpty(), error.message.orEmpty().contains("ToUnicode"))
    }

    @Test
    fun `arxiv oqimi ascii hex bilan ham ochiladi`() {
        // `ASCIIHexDecode` — kam uchraydigan, lekin spetsifikatsiyadagi filtr.
        val bytes = asciiHexPdf().toByteArray(Charsets.ISO_8859_1)
        assertEquals("Salom", PdfTextReader.read(bytes))
    }

    // --- dekompressiya bombasi ---

    @Test
    fun `dekompressiya bombasi xato beradi va ilovani yiqitmaydi`() {
        // Kichik siqilgan oqim, ochilganda chegaradan katta — haqiqiy
        // bombaning shakli. Ilgari chegara yo'q edi: 32 MB'lik PDF o'nlab
        // gigabaytga ochilib, `OutOfMemoryError` berardi (u `Exception`
        // emas, shuning uchun ilova yiqilardi).
        val packed = deflatedZeros((PdfTextReader.MAX_STREAM_BYTES + 1).toInt())
        assertTrue("namuna kichik bo'lishi kerak: ${packed.size}", packed.size < 1_000_000)

        assertThrows(DocumentTooLargeException::class.java) {
            PdfTextReader.read(pdfWithStreams(listOf(packed)))
        }
    }

    @Test
    fun `barcha oqimlar yigindisi chegaradan oshsa xato beradi`() {
        // Har bir oqim kichik (600 bayt), lekin uchtasi birga 1000 baytlik
        // chegaradan katta: minglab kichik oqimdan iborat bomba shunday
        // yig'iladi.
        val stream = deflatedZeros(600)
        val bytes = pdfWithStreams(List(3) { stream })

        assertThrows(DocumentTooLargeException::class.java) {
            PdfTextReader.read(bytes, maxInflatedBytes = 1_000L)
        }
    }

    @Test
    fun `chegara ichidagi siqilgan oqim odatdagidek ochiladi`() {
        // Chegara oddiy hujjatga tegmasligi kerak.
        val content = "BT /F1 12 Tf (Salom) Tj ET"
        val packed = deflate(content.toByteArray(Charsets.ISO_8859_1))

        assertEquals("Salom", PdfTextReader.read(pdfWithStreams(listOf(packed))))
    }

    // --- qo'lda yasalgan eng kichik PDF'lar ---
    // Ular faqat xato yo'llarini sinash uchun: matn to'g'riligi haqida
    // hech qanday da'vo yo'q (u fpdf2 namunalarida tekshiriladi).

    private fun pdf(objects: List<String>, root: Int = 1): String {
        val builder = StringBuilder("%PDF-1.4\n")
        for ((index, body) in objects.withIndex()) {
            builder.append("${index + 1} 0 obj\n$body\nendobj\n")
        }
        builder.append("trailer\n<< /Root $root 0 R >>\n%%EOF\n")
        return builder.toString()
    }

    private fun emptyPagePdf(): String = pdf(
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /Contents 4 0 R >>",
            "<< /Length 0 >>\nstream\n\nendstream",
        )
    )

    private fun compositeWithoutToUnicode(): String = pdf(
        listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /Contents 4 0 R " +
                "/Resources << /Font << /F1 5 0 R >> >> >>",
            "<< /Length 26 >>\nstream\nBT /F1 12 Tf (matn) Tj ET\nendstream",
            "<< /Type /Font /Subtype /Type0 /BaseFont /DejaVu >>",
        )
    )

    private fun asciiHexPdf(): String {
        // `(Salom)` — 53 61 6C 6F 6D.
        val content = "BT /F1 12 Tf (Salom) Tj ET"
        return pdf(
            listOf(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /Contents 4 0 R " +
                    "/Resources << /Font << /F1 5 0 R >> >> >>",
                "<< /Filter /ASCIIHexDecode /Length ${content.length * 2 + 1} >>\n" +
                    "stream\n${content.toByteArray().joinToString("") { "%02X".format(it) }}>\nendstream",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
            )
        )
    }

    /** [data] ni Flate bilan siqadi. */
    private fun deflate(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out).use { it.write(data) }
        return out.toByteArray()
    }

    /** [size] bayt nolni siqadi: natija ~1000 marta kichik bo'ladi. */
    private fun deflatedZeros(size: Int): ByteArray {
        val out = ByteArrayOutputStream()
        DeflaterOutputStream(out, Deflater(Deflater.BEST_COMPRESSION)).use { stream ->
            val chunk = ByteArray(64 * 1024)
            var left = size
            while (left > 0) {
                val count = minOf(left, chunk.size)
                stream.write(chunk, 0, count)
                left -= count
            }
        }
        return out.toByteArray()
    }

    /**
     * Har bir sahifasi bittadan Flate oqimli PDF: 1 — katalog, 2 — sahifalar
     * daraxti, keyin (sahifa, oqim) juftlari, oxirida shrift.
     */
    private fun pdfWithStreams(streams: List<ByteArray>): ByteArray {
        val pageCount = streams.size
        val fontNumber = 2 + pageCount * 2 + 1
        val kids = (0 until pageCount).joinToString(" ") { "${3 + it * 2} 0 R" }

        val objects = ArrayList<String>()
        objects += "<< /Type /Catalog /Pages 2 0 R >>"
        objects += "<< /Type /Pages /Kids [$kids] /Count $pageCount >>"
        for ((i, packed) in streams.withIndex()) {
            val streamNumber = 4 + i * 2
            objects += "<< /Type /Page /Parent 2 0 R /Contents $streamNumber 0 R " +
                "/Resources << /Font << /F1 $fontNumber 0 R >> >> >>"
            objects += "<< /Filter /FlateDecode /Length ${packed.size} >>\nstream\n" +
                String(packed, Charsets.ISO_8859_1) + "\nendstream"
        }
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        return pdf(objects).toByteArray(Charsets.ISO_8859_1)
    }
}
