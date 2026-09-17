package uz.ovozstudio.app.media.doc

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Fayl qaysi jadvalda yozilgani. Bu — taxmin natijasi, shuning uchun u
 * foydalanuvchiga ham ko'rsatiladi: matn «savatcha» bo'lib chiqsa, sabab
 * shu yerda ko'rinadi.
 */
enum class TextEncoding(val charsetName: String) {
    UTF_8("UTF-8"),
    UTF_16_LE("UTF-16LE"),
    UTF_16_BE("UTF-16BE"),
    WINDOWS_1251("windows-1251"),
    LATIN_1("ISO-8859-1"),
}

data class DecodedText(
    val text: String,
    /** Matn `\n` ga keltirilgan: `\r\n` va yakka `\r` ham `\n` bo'lgan. */
    val encoding: TextEncoding,
    /** Fayl o'zi bayt tartibini aytganmi (BOM bor). */
    val bomFound: Boolean,
)

class DocumentTooLargeException(limitBytes: Long) :
    Exception("Hujjat juda katta: $limitBytes baytdan oshdi")

/**
 * Matn faylini o'qish: jadvalni aniqlash va satr oxirlarini bir xillashtirish.
 *
 * Tartib — ishonchlidan taxminiyga:
 *  1. BOM. Fayl o'zi aytgan bo'lsa, bahslashishning hojati yo'q.
 *  2. BOM'siz UTF-16. Nollarning joylashuviga qarab aniqlanadi: lotin
 *     matnda har ikkinchi bayt nol bo'ladi.
 *  3. Qattiq UTF-8: birorta bayt mos kelmasa — bu UTF-8 emas.
 *  4. windows-1251: o'zbek va rus kirill matni uchun asosiy nomzod.
 *  5. ISO-8859-1: hech qachon xato bermaydi, lekin oxirgi chora.
 *
 * Kirill uchun aynan 1251 tanlangani muhim: 8859-1 ni birinchi qo'yish
 * kirill faylni har doim «muvaffaqiyatli» o'qib, savatcha berardi.
 */
object PlainTextDecoder {

    /** O'qiladigan eng katta hajm: undan kattasi — xato, jim qotish emas. */
    const val DEFAULT_MAX_BYTES = 32L * 1024 * 1024

    private const val SNIFF_BYTES = 64

    fun decode(bytes: ByteArray): DecodedText {
        if (bytes.isEmpty()) return DecodedText("", TextEncoding.UTF_8, bomFound = false)

        findBom(bytes)?.let { bom ->
            val text = lenient(bytes, bom.skip, bytes.size, bom.encoding)
            return DecodedText(normalize(text), bom.encoding, bomFound = true)
        }

        guessUtf16WithoutBom(bytes)?.let { encoding ->
            return DecodedText(normalize(lenient(bytes, 0, bytes.size, encoding)), encoding, false)
        }

        strictUtf8(bytes)?.let {
            return DecodedText(normalize(it), TextEncoding.UTF_8, bomFound = false)
        }

        if (Charset.isSupported(TextEncoding.WINDOWS_1251.charsetName)) {
            val text = lenient(bytes, 0, bytes.size, TextEncoding.WINDOWS_1251)
            return DecodedText(normalize(text), TextEncoding.WINDOWS_1251, bomFound = false)
        }

        val text = lenient(bytes, 0, bytes.size, TextEncoding.LATIN_1)
        return DecodedText(normalize(text), TextEncoding.LATIN_1, bomFound = false)
    }

    fun decode(input: InputStream, maxBytes: Long = DEFAULT_MAX_BYTES): DecodedText =
        decode(readLimited(input, maxBytes))

    /**
     * Oqimni chegaragacha o'qiydi. Chegaradan oshsa — [DocumentTooLargeException].
     * Jim qirqib tashlash yomonroq bo'lardi: foydalanuvchi kitobning yarmini
     * olib, sababini bilmasdi.
     */
    fun readLimited(input: InputStream, maxBytes: Long = DEFAULT_MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size().toLong() + read > maxBytes) throw DocumentTooLargeException(maxBytes)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private data class Bom(val encoding: TextEncoding, val skip: Int)

    private fun findBom(bytes: ByteArray): Bom? = when {
        bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte() -> Bom(TextEncoding.UTF_8, 3)

        bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
            Bom(TextEncoding.UTF_16_LE, 2)

        bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
            Bom(TextEncoding.UTF_16_BE, 2)

        else -> null
    }

    /**
     * BOM'siz UTF-16 ni topadi.
     *
     * Lotin matn UTF-16 da yozilsa, har juftlikning bitta bayti nol bo'ladi:
     * kichik bayt oldinda (LE) — ikkinchisi, katta bayt oldinda (BE) —
     * birinchisi. Kirill matnda bu ishlamaydi (ikkala bayt ham nolga teng
     * emas), lekin kirill UTF-16 si deyarli har doim BOM bilan keladi.
     */
    private fun guessUtf16WithoutBom(bytes: ByteArray): TextEncoding? {
        val pairs = minOf(bytes.size, SNIFF_BYTES) / 2
        if (pairs < 4) return null

        var leHits = 0
        var beHits = 0
        for (i in 0 until pairs) {
            if (bytes[i * 2 + 1] == 0.toByte()) leHits++
            if (bytes[i * 2] == 0.toByte()) beHits++
        }

        if (beHits * 10 >= pairs * 9) return TextEncoding.UTF_16_BE
        if (leHits * 10 >= pairs * 9) return TextEncoding.UTF_16_LE
        return null
    }

    private fun strictUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /**
     * Xato bergan baytni «?» bilan almashtiradi. Bu — ataylab: bitta buzuq
     * bayt butun kitobni o'qib bo'lmaydigan holga keltirmasligi kerak.
     */
    private fun lenient(bytes: ByteArray, from: Int, to: Int, encoding: TextEncoding): String =
        charset(encoding).newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
            .decode(ByteBuffer.wrap(bytes, from, to - from))
            .toString()

    private fun charset(encoding: TextEncoding): Charset = try {
        Charset.forName(encoding.charsetName)
    } catch (_: IllegalArgumentException) {
        // Qurilmada bu jadval yo'q. `IllegalCharsetNameException` ham,
        // `UnsupportedCharsetException` ham shu yerga tushadi.
        StandardCharsets.ISO_8859_1
    }

    private fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        // Noto'g'ri aniqlangan UTF-16 dan qolgan nollar matnni ko'rinmas
        // qilib qo'yadi va sintezator ularni ovoz bilan o'qib beradi.
        .replace("\u0000", "")
}
