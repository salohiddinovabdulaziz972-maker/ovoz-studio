package uz.ovozstudio.app.media.tag

import java.io.File
import java.io.RandomAccessFile

/**
 * Fayldagi mavjud ID3v2 tegini o'qiydi.
 *
 * Nega o'quvchi kerak: yozuvchi tegni **butunlay** almashtiradi. Agar
 * foydalanuvchi faqat muqova qo'shmoqchi bo'lib, nom maydonini bo'sh
 * qoldirsa, o'quvchisiz eski nom yo'q bo'lardi — bu jimgina ma'lumot
 * yo'qotish, eng yomon turdagi xato. Shuning uchun ekran maydonlarni
 * mavjud teg bilan to'ldiradi, foydalanuvchi esa uni **tahrirlaydi**.
 *
 * 2.3 va 2.4 versiyalari o'qiladi (2.4 da kadr o'lchami synchsafe, 2.3 da
 * oddiy — farq shu). 2.2 uchraydi-yu, lekin juda eski: kadr nomlari uch
 * harfli, tuzilishi boshqa. U «teg yo'q» deb qaytariladi — bu yolg'on emas,
 * chunki ilova baribir faqat o'zi tushunadigan tegni ko'rsata oladi.
 *
 * O'qish faqat **teg** baytlariga tegadi: butun MP3 fayl xotiraga
 * ko'tarilmaydi (100 MB li kitobda bu telefonni bo'g'ardi). Muqova ham
 * teg ichida — o'lchami sarlavhadan ma'lum.
 */
object Id3v2Reader {

    private const val HEADER_SIZE = 10
    private const val FRAME_HEADER_SIZE = 10

    /**
     * Teg uchun o'qiladigan eng katta hajm.
     *
     * Sarlavhadagi o'lcham — fayldan kelgan son, ya'ni unga cheksiz
     * ishonib bo'lmaydi: buzilgan fayl «500 MB teg» deb ko'rsatishi va
     * ilovani yiqitishi mumkin.
     */
    private const val MAX_TAG_BYTES = 32 * 1024 * 1024

    /** Matn tugatuvchisi: nol bayt. */
    private const val NUL = '\u0000'

    private const val ENCODING_LATIN1 = 0
    private const val ENCODING_UTF16_BOM = 1
    private const val ENCODING_UTF16_BE = 2
    private const val ENCODING_UTF8 = 3

    /**
     * Fayldagi tegni o'qiydi.
     *
     * Teg yo'q, buzilgan yoki versiya tushunarsiz bo'lsa — bo'sh [AudioTags]
     * qaytaradi. Bu «faylda ma'lumot yo'q» degani emas, «o'qib bo'lmadi»
     * degani; ekran ikkisini bir xil ko'rsatadi va bunda yomon narsa yo'q:
     * bo'sh maydonlar yangi teg yozishni taklif qiladi.
     */
    fun read(file: File): AudioTags {
        val tag = readTag(file) ?: return AudioTags()
        return parse(tag)
    }

    /**
     * Baytlar ko'rinishidagi tegni tahlil qiladi.
     *
     * Sinovlar shu yo'lni ishlatadi: teg baytlari qo'lda yasaladi va o'quvchi
     * aynan o'sha tuzilmani tushunishi tekshiriladi.
     */
    fun parse(tag: ByteArray): AudioTags {
        if (tag.size < HEADER_SIZE) return AudioTags()
        if (tag[0] != 'I'.code.toByte() || tag[1] != 'D'.code.toByte() || tag[2] != '3'.code.toByte()) {
            return AudioTags()
        }
        val version = tag[3].toInt()
        // 2.2 da kadr nomi uch harfli va butun tuzilishi boshqa — uni
        // «o'qib bo'lmadi» deb qoldiramiz.
        if (version !in 3..4) return AudioTags()

        val flags = tag[5].toInt()
        val size = Id3v2Tag.readSynchsafe(tag, 6)
        // Sarlavha o'zi ham hisobga olinadi: baytlar `size + HEADER_SIZE` ta.
        val end = minOf(HEADER_SIZE + size, tag.size)
        if (end <= HEADER_SIZE) return AudioTags()

        var body = tag.copyOfRange(HEADER_SIZE, end)
        // Sinxronlashdan chiqarish: oqimda «FF 00» juftligi «FF» ni bildiradi.
        // Buni qilmasak, muqova baytlari bir baytga siljib, rasm buziladi.
        if (flags and 0x80 != 0) body = deUnsynchronise(body)

        val frames = startOfFrames(body, version, flags)
        return readFrames(body, frames, version)
    }

    /** Kengaytirilgan sarlavhadan keyingi birinchi kadr joyi. */
    private fun startOfFrames(body: ByteArray, version: Int, flags: Int): Int {
        if (flags and 0x40 == 0) return 0
        if (body.size < 4) return 0
        val extended = if (version >= 4) {
            // 2.4 da o'lcham synchsafe va o'zini ham qo'shib hisoblaydi.
            Id3v2Tag.readSynchsafe(body, 0)
        } else {
            // 2.3 da o'lcham oddiy va o'zini qo'shmaydi.
            4 + int32(body, 0)
        }
        return extended.coerceIn(0, body.size)
    }

    private fun readFrames(body: ByteArray, from: Int, version: Int): AudioTags {
        var title = ""
        var artist = ""
        var album = ""
        var year = ""
        var genre = ""
        var trackText = ""
        var cover: ByteArray? = null
        var coverMime = "image/jpeg"

        var offset = from
        while (offset + FRAME_HEADER_SIZE <= body.size) {
            val id = String(body, offset, 4, Charsets.ISO_8859_1)
            // To'ldiruvchi (padding) nol baytlardan iborat — kadrlar tugadi.
            if (id[0] == NUL) break
            if (!id.all { it.isLetterOrDigit() }) break

            val size = if (version >= 4) {
                Id3v2Tag.readSynchsafe(body, offset + 4)
            } else {
                int32(body, offset + 4)
            }
            if (size <= 0) break

            val start = offset + FRAME_HEADER_SIZE
            // O'lcham fayldan kelgan son. Tekshiruv ayirish bilan: qo'shishda
            // katta son «aylanib» manfiy bo'lib qolardi va chegara
            // o'tkazib yuborilardi — buzuq fayl esa ilovani yiqitardi.
            if (size > body.size - start) break
            val end = start + size

            val frameFlags = body[offset + 8].toInt() to body[offset + 9].toInt()
            if (isUnreadable(frameFlags, version)) {
                offset = end
                continue
            }

            val payload = framePayload(body, start, end, frameFlags, version)
            when (id) {
                "TIT2" -> title = decodeText(payload)
                "TPE1" -> artist = decodeText(payload)
                "TALB" -> album = decodeText(payload)
                // 2.4 da yil TDOR/TDRC da ham bo'lishi mumkin, lekin TYER
                // hali ham eng ko'p uchraydigani — uni birinchi o'qiymiz.
                "TYER", "TDRC" -> if (year.isBlank()) year = decodeText(payload).take(4)
                "TCON" -> genre = decodeText(payload)
                "TRCK" -> trackText = decodeText(payload)
                "APIC" -> decodePicture(payload)?.let { (image, mime) ->
                    // Bir nechta rasm bo'lsa — birinchisi qoladi.
                    if (cover == null) {
                        cover = image
                        coverMime = mime
                    }
                }
            }
            offset = end
        }

        val numbers = parseTrack(trackText)
        return AudioTags(
            title = title,
            artist = artist,
            album = album,
            year = year,
            genre = genre,
            track = numbers.first,
            trackTotal = numbers.second,
            cover = cover,
            coverMime = coverMime,
        )
    }

    /**
     * Kadr o'qiladimi.
     *
     * Siqilgan va shifrlangan kadrlar ochilmaydi — ularni «o'qib bo'lmadi»
     * deb tashlab ketish kerak, taxmin qilib noto'g'ri matn ko'rsatishdan
     * ko'ra. Bayroqlar versiyalar bo'yicha boshqa joyda turadi: 2.3 da
     * birinchi baytda, 2.4 da ikkinchisida.
     */
    private fun isUnreadable(frameFlags: Pair<Int, Int>, version: Int): Boolean {
        val (first, second) = frameFlags
        return if (version >= 4) {
            second and 0x0C != 0 // siqish (0x08) yoki shifrlash (0x04)
        } else {
            first and 0xC0 != 0 // siqish (0x80) yoki shifrlash (0x40)
        }
    }

    /**
     * Kadr mazmuni.
     *
     * 2.4 ning ikki bayrog'i mazmunni o'zgartiradi: «o'lcham ko'rsatkichi»
     * mazmun boshiga 4 bayt qo'shadi (u matn emas — tashlanmasa, nom
     * boshidagi to'rt belgi axlat bo'lardi), «sinxronlash» esa baytlarni
     * qaytadan ochishni talab qiladi.
     */
    private fun framePayload(
        body: ByteArray,
        start: Int,
        end: Int,
        frameFlags: Pair<Int, Int>,
        version: Int,
    ): ByteArray {
        var payload = body.copyOfRange(start, end)
        if (version >= 4) {
            val second = frameFlags.second
            if (second and 0x01 != 0 && payload.size > 4) payload = payload.copyOfRange(4, payload.size)
            if (second and 0x02 != 0) payload = deUnsynchronise(payload)
        }
        return payload
    }

    /** Matn kadri: kodlash bayti + matn; oxiridagi nol tashlanadi. */
    private fun decodeText(payload: ByteArray): String {
        if (payload.size < 2) return ""
        val encoding = payload[0].toInt()
        val text = when (encoding) {
            ENCODING_LATIN1 -> String(payload, 1, payload.size - 1, Charsets.ISO_8859_1)
            // BOM'li UTF-16: Java o'zi BOM'ni o'qib, tartibni aniqlaydi.
            ENCODING_UTF16_BOM -> String(payload, 1, payload.size - 1, Charsets.UTF_16)
            ENCODING_UTF16_BE -> String(payload, 1, payload.size - 1, Charsets.UTF_16BE)
            ENCODING_UTF8 -> String(payload, 1, payload.size - 1, Charsets.UTF_8)
            else -> return ""
        }
        return text.trimEnd(NUL).trim()
    }

    /** Muqova kadri: kodlash, MIME, rasm turi, izoh, rasm. */
    private fun decodePicture(payload: ByteArray): Pair<ByteArray, String>? {
        if (payload.size < 4) return null
        val encoding = payload[0].toInt()

        val mimeEnd = payload.indexOf(0, 1)
        if (mimeEnd < 0) return null
        val mime = String(payload, 1, mimeEnd - 1, Charsets.ISO_8859_1)

        // MIME'dan keyin rasm turi (bir bayt), keyin izoh.
        var offset = mimeEnd + 1
        if (offset >= payload.size) return null
        offset++

        offset = skipDescription(payload, offset, encoding)
        if (offset >= payload.size) return null

        return payload.copyOfRange(offset, payload.size) to mime.ifBlank { "image/jpeg" }
    }

    /**
     * Izohni tashlab o'tadi.
     *
     * UTF-16 da tugatuvchi nol **ikki** bayt, boshqa kodlashlarda bitta.
     * Bitta deb hisoblansa, rasm baytlarining yarmi izoh deb o'qilib,
     * muqova yaroqsiz bo'lardi.
     */
    private fun skipDescription(payload: ByteArray, from: Int, encoding: Int): Int {
        if (encoding == ENCODING_LATIN1 || encoding == ENCODING_UTF8) {
            val end = payload.indexOf(0, from)
            return if (end < 0) payload.size else end + 1
        }
        var index = from
        while (index + 1 < payload.size) {
            if (payload[index] == 0.toByte() && payload[index + 1] == 0.toByte()) return index + 2
            index += 2
        }
        return payload.size
    }

    /** Sinxronlashdan chiqaradi: har «FF 00» juftligidan nol tashlanadi. */
    private fun deUnsynchronise(body: ByteArray): ByteArray {
        val out = ByteArray(body.size)
        var written = 0
        var index = 0
        while (index < body.size) {
            out[written++] = body[index]
            if (body[index] == 0xFF.toByte() && index + 1 < body.size && body[index + 1] == 0.toByte()) {
                index++ // keyingi nol tashlanadi
            }
            index++
        }
        return out.copyOf(written)
    }

    /** Fayldan faqat teg baytlarini o'qiydi (audio tegmaydi). */
    private fun readTag(file: File): ByteArray? {
        if (!file.exists() || file.length() < HEADER_SIZE) return null
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(HEADER_SIZE)
                raf.readFully(header)
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() ||
                    header[2] != '3'.code.toByte()
                ) {
                    return@use null
                }
                val size = Id3v2Tag.readSynchsafe(header, 6).coerceAtMost(MAX_TAG_BYTES)
                val full = ByteArray(HEADER_SIZE + size)
                header.copyInto(full)
                raf.readFully(full, HEADER_SIZE, size)
                full
            }
        }.getOrNull()
    }

    private fun parseTrack(value: String): Pair<Int, Int> {
        val parts = value.trim().split('/')
        return parts.getOrNull(0).toPositiveInt() to parts.getOrNull(1).toPositiveInt()
    }

    private fun String?.toPositiveInt(): Int =
        this?.trim()?.takeWhile(Char::isDigit)?.take(4)?.toIntOrNull() ?: 0

    /** `needle` baytning birinchi uchrash joyi; topilmasa `-1`. */
    private fun ByteArray.indexOf(needle: Int, from: Int): Int {
        for (index in from until size) {
            if (this[index].toInt() == needle) return index
        }
        return -1
    }

    /** Katta uchli (big-endian) 32-bitli son — 2.3 dagi kadr o'lchami. */
    private fun int32(bytes: ByteArray, offset: Int): Int =
        if (offset + 4 > bytes.size) {
            0
        } else {
            ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
        }
}
