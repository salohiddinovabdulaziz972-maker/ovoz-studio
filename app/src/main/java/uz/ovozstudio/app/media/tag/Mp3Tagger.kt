package uz.ovozstudio.app.media.tag

import java.io.File
import java.io.RandomAccessFile

/** Teg yozib bo'lmadi (fayl o'qilmayapti, joy yetmayapti, muqova juda katta). */
class TagWriteException(reason: String, cause: Throwable? = null) : Exception(reason, cause)

/**
 * MP3 faylga ID3 teg yozadi.
 *
 * Ilovadagi qat'iy qoida — **manba fayl joyida o'zgartirilmaydi**: har bir
 * amal yangi fayl yozadi. Teg ham shunday: `source` o'qiladi, natija
 * `target` ga tushadi. Xato bo'lsa asl fayl shikastlanmagan holda qoladi.
 *
 * Eski teg **almashtiriladi**, ustiga qo'shilmaydi. Aks holda faylda ikkita
 * ID3 sarlavhasi bo'lardi: o'quvchilar birinchisini oladi va yangi nom
 * ekranda umuman ko'rinmasdi.
 */
object Mp3Tagger {

    /** ID3v2 sarlavhasi: "ID3" (3) + versiya (2) + o'lcham (4) + bayroqlar. */
    private const val ID3V2_HEADER_SIZE = 10

    /** ID3v2.4 dagi qo'shimcha (footer) bloki. */
    private const val ID3V2_FOOTER_SIZE = 10

    /** ID3v1 — fayl oxiridagi qat'iy 128 bayt. */
    const val ID3V1_SIZE = 128L

    /**
     * Muqovaning eng katta hajmi.
     *
     * Telefon kamerasi 5–10 MB rasm beradi; bunday muqova butun kitob
     * faylidan og'ir bo'lib qolardi va pleyerda bir zumda ochilmasdi.
     * 2 MB — bosma sifatli muqova uchun yetarli.
     */
    const val MAX_COVER_BYTES = 2 * 1024 * 1024

    private const val COPY_BUFFER = 64 * 1024

    /** Fayl boshidagi ID3v2 tegining hajmi; teg bo'lmasa 0. */
    fun id3v2Size(file: File): Long {
        val header = ByteArray(ID3V2_HEADER_SIZE)
        val read = try {
            RandomAccessFile(file, "r").use { it.read(header) }
        } catch (error: Exception) {
            throw TagWriteException("Faylni o'qib bo'lmadi", error)
        }
        if (read < ID3V2_HEADER_SIZE) return 0
        if (header[0] != 'I'.code.toByte() ||
            header[1] != 'D'.code.toByte() ||
            header[2] != '3'.code.toByte()
        ) {
            return 0
        }
        // Versiya va reviziya ham tekshiriladi: audio oqimidagi tasodifiy
        // "ID3" ketma-ketligi teg deb qabul qilinmasligi kerak.
        if (header[3].toInt() !in 2..4 || header[4].toInt() == 0xFF) return 0

        // O'lchamning eng katta biti 1 bo'lsa — bu synchsafe emas, demak teg
        // emas (buzuq fayl).
        if (header[6].toInt() and 0x80 != 0) return 0

        var total = ID3V2_HEADER_SIZE.toLong() + Id3v2Tag.readSynchsafe(header, 6)
        // Bayroqlarning eng kichik biti — qo'shimcha blok borligini bildiradi.
        if (header[5].toInt() and 0x10 != 0) total += ID3V2_FOOTER_SIZE
        return total
    }

    /** Ovozli oqim qayerdan boshlanadi (tegdan keyin). */
    fun audioStart(file: File): Long = id3v2Size(file)

    /**
     * Ovozli oqim qayerda tugaydi.
     *
     * Oxirida ID3v1 tegi bo'lsa (128 bayt, oxirida o'sha tegning eski
     * nomi turadi), u tashlab ketiladi — aks holda yangi teg bilan birga
     * eski nom ham faylda qolib, pleyerlar uni o'qib yuborardi.
     */
    fun audioEnd(file: File): Long {
        val size = file.length()
        if (size < ID3V1_SIZE) return size
        val tail = ByteArray(3)
        try {
            RandomAccessFile(file, "r").use {
                it.seek(size - ID3V1_SIZE)
                it.readFully(tail)
            }
        } catch (error: Exception) {
            throw TagWriteException("Fayl oxirini o'qib bo'lmadi", error)
        }
        val isV1 = tail[0] == 'T'.code.toByte() &&
            tail[1] == 'A'.code.toByte() &&
            tail[2] == 'G'.code.toByte()
        return if (isV1) size - ID3V1_SIZE else size
    }

    /**
     * Teg yozilgan yangi fayl yasaydi.
     *
     * [source] o'zgartirilmaydi va [target] bilan bir xil bo'lmasligi kerak.
     */
    fun write(source: File, target: File, tags: AudioTags) {
        require(source.absolutePath != target.absolutePath) {
            "Manba va natija bir fayl bo'lmasligi kerak"
        }
        val cover = tags.cover
        if (cover != null && cover.size > MAX_COVER_BYTES) {
            throw TagWriteException("Muqova juda katta: ${cover.size / 1024} KB")
        }

        val start = audioStart(source)
        val end = audioEnd(source)
        if (end <= start) throw TagWriteException("Faylda ovoz yo'q")

        val tag = Id3v2Tag.build(tags)
        target.parentFile?.mkdirs()

        try {
            RandomAccessFile(source, "r").use { input ->
                target.outputStream().buffered(COPY_BUFFER).use { output ->
                    output.write(tag)
                    input.seek(start)
                    var remaining = end - start
                    val buffer = ByteArray(COPY_BUFFER)
                    while (remaining > 0) {
                        val want = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, want)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                    if (remaining > 0) throw TagWriteException("Fayl to'liq o'qilmadi")
                }
            }
        } catch (error: TagWriteException) {
            target.delete()
            throw error
        } catch (error: Exception) {
            target.delete()
            throw TagWriteException("Teg yozilmadi", error)
        }
    }
}
