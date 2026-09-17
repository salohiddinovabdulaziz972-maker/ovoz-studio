package uz.ovozstudio.app.media.book

import java.io.File
import java.util.Properties

/** Tinglash qoldirilgan joy. */
data class PlaybackProgress(
    val chapterIndex: Int,
    val positionMs: Long,
    /** Oxirgi marta qachon yangilangani — eski yozuvlarni saralash uchun. */
    val updatedAtMs: Long = 0L,
)

/**
 * Qaysi kitob qayerda qolganini eslab qoladi.
 *
 * Nega kerak: kitob bir necha soat davom etadi va u bir kunda tugamaydi.
 * Har safar birinchi bobdan boshlanadigan pleyer amalda ishlatilmaydi —
 * tinglovchi o'zi qo'lda qidiradi.
 *
 * Saqlash **faylda** (`java.util.Properties`), bazada emas: bu bitta kichik
 * jadval, uni o'qish uchun butun baza ochish ortiqcha. Fayl buzilsa yoki
 * o'chib qolsa, pleyer shunchaki boshidan boshlanadi — yo'qoladigan narsa
 * kichik, shuning uchun bu yerda hech qanday xato yuqoriga uzatilmaydi.
 *
 * Kitob nomi kalit bo'lib xizmat qiladi: `Properties` kalitdagi bo'shliq va
 * tenglik belgisini o'zi ekranlaydi, ya'ni «Mening kitobim: 2-qism» kabi
 * nomlar ham to'qnashmaydi.
 */
class BookPlaybackStore(private val file: File) {

    /** Kitobning qoldirilgan joyini yozadi. */
    fun save(book: String, chapterIndex: Int, positionMs: Long, nowMs: Long = 0L) {
        if (book.isBlank()) return
        val values = read()
        values[book] = encode(PlaybackProgress(chapterIndex, positionMs, nowMs))
        write(trim(values))
    }

    /** Qoldirilgan joyni o'qiydi. Yozuv yo'q yoki buzuq bo'lsa — `null`. */
    fun load(book: String): PlaybackProgress? = read()[book]?.let(::decode)

    /** Bitta kitobning yozuvini o'chiradi (kitob oxirigacha tinglanganda). */
    fun clear(book: String) {
        val values = read()
        if (values.remove(book) == null) return
        write(values)
    }

    /**
     * Eng ko'p saqlanadigan kitoblar soni.
     *
     * Har tinglangan kitob bitta qator qoldiradi; chegara qo'yilmasa fayl
     * yillar davomida o'sib borardi, foydasi esa nolga intiladi.
     */
    private fun trim(values: MutableMap<String, String>): MutableMap<String, String> {
        if (values.size <= MAX_BOOKS) return values
        val keep = values.entries
            .sortedByDescending { decode(it.value)?.updatedAtMs ?: 0L }
            .take(MAX_BOOKS)
        return keep.associateTo(LinkedHashMap()) { it.key to it.value }
    }

    private fun read(): MutableMap<String, String> {
        val properties = Properties()
        if (!file.exists()) return mutableMapOf()
        return try {
            file.inputStream().use(properties::load)
            properties.stringPropertyNames().associateWithTo(LinkedHashMap()) { properties.getProperty(it) }
        } catch (error: Exception) {
            // Buzuq fayl tinglashni to'xtatmaydi: eng yomoni — boshidan
            // boshlanadi, xato ko'rsatish esa foydasiz.
            mutableMapOf()
        }
    }

    private fun write(values: Map<String, String>) {
        try {
            file.parentFile?.mkdirs()
            val properties = Properties()
            values.forEach { (key, value) -> properties.setProperty(key, value) }
            // Vaqtinchalik faylga yozib, keyin o'rniga qo'yiladi: yozish
            // paytida uzilib qolsa eski yozuv butun qoladi.
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { properties.store(it, null) }
            if (!temp.renameTo(file)) {
                file.outputStream().use { properties.store(it, null) }
                temp.delete()
            }
        } catch (error: Exception) {
            // Joy yo'q yoki papka ochilmadi — tinglash baribir davom etadi,
            // faqat qoldirilgan joy eslab qolinmaydi.
        }
    }

    private fun encode(progress: PlaybackProgress): String =
        "${progress.chapterIndex}$SEPARATOR${progress.positionMs}$SEPARATOR${progress.updatedAtMs}"

    private fun decode(value: String): PlaybackProgress? {
        val parts = value.split(SEPARATOR)
        if (parts.size != 3) return null
        val chapter = parts[0].toIntOrNull() ?: return null
        val position = parts[1].toLongOrNull() ?: return null
        val updated = parts[2].toLongOrNull() ?: return null
        if (chapter < 0 || position < 0) return null
        return PlaybackProgress(chapter, position, updated)
    }

    private companion object {
        const val SEPARATOR = '|'
        const val MAX_BOOKS = 50
    }
}
