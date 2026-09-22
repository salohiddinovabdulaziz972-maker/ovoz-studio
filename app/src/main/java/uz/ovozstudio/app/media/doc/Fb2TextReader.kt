package uz.ovozstudio.app.media.doc

import java.io.File

/**
 * FictionBook (FB2) — kitob formati, ayniqsa rus va o'zbek kutubxonalarida
 * keng tarqalgan — dan matn ajratadi.
 *
 * FB2 — oddiy XML. Kodlash XML sarlavhasida yoziladi (`windows-1251` ko'p
 * uchraydi), uni o'qish SAX'ning o'z ishi.
 *
 * Qoidalar:
 *  - matn `<p>` (paragraf), `<v>` (she'r misrasi), `<subtitle>` va
 *    `<text-author>` ichida;
 *  - `<title>` ichidagi paragraflar sarlavha bo'ladi;
 *  - `<description>` (kitob haqida ma'lumot), `<binary>` (base64 rasmlar) va
 *    `<stylesheet>` — o'qilmaydi. Rasmning base64 matnini ovoz bilan o'qib
 *    berish — foydalanuvchi uchun eng yomon natija.
 */
object Fb2TextReader {

    /** FB2 fayl chegarasi: ichida rasmlar ham bo'ladi, ammo kitob baribir shundan kichik. */
    const val MAX_BYTES = 64L * 1024 * 1024

    private val RULES = MarkupRules(
        textTags = null,
        paragraphTags = setOf("p", "v", "subtitle", "text-author"),
        breakTags = emptySet(),
        tabTag = null,
        headingTags = mapOf("title" to 1),
        skipTags = setOf("binary", "description", "stylesheet"),
    )

    fun read(file: File): String {
        val bytes = file.inputStream().use { PlainTextDecoder.readLimited(it, MAX_BYTES) }
        return read(bytes)
    }

    fun read(bytes: ByteArray): String = MarkupBlocks.parse(bytes, RULES)
}
