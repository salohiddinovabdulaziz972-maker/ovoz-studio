package uz.ovozstudio.app.media.tag

import java.io.ByteArrayOutputStream

/**
 * ID3v2.3 tegini yig'uvchi.
 *
 * Ataylab **2.3** versiyasi, 2.4 emas: 2.3 ni deyarli har qanday pleyer,
 * jumladan eski Android qurilmalari ham o'qiydi. 2.4 dagi matn kodlash
 * qoidalari kengroq, lekin uni hamma ham tushunmaydi — kitob fayli esa
 * telefonda, mashinada va oddiy pleyerda bir xil ko'rinishi kerak.
 *
 * Matn **UTF-16** (BOM bilan) yoziladi. Sabab: o'zbek kitoblarida kirill
 * ham, lotin ham bor, ISO-8859-1 esa kirillni umuman saqlay olmaydi va
 * nom «?????» bo'lib chiqardi.
 *
 * Bu sinf hech narsani o'qimaydi va faylga tegmaydi — faqat bayt yasaydi.
 * Shu sababli u sof JVM sinovida ham, ffprobe bilan mustaqil tekshiruvda
 * ham bir xil ishlaydi (qarang: `bin/verify-tag.sh`).
 */
object Id3v2Tag {

    /** Teg sarlavhasi: "ID3" + versiya 2.3 + bayroqlarsiz. */
    private const val HEADER_SIZE = 10

    /** Bitta kadr sarlavhasi: nom (4) + o'lcham (4) + bayroqlar (2). */
    private const val FRAME_HEADER_SIZE = 10

    /** Matn UTF-16, BOM bilan. */
    private const val ENCODING_UTF16 = 1

    /** Muqova — old tomondagi rasm (ID3 spetsifikatsiyasi, 3-raqam). */
    private const val PICTURE_FRONT_COVER = 3

    /**
     * Kadr o'lchamining eng katta qiymati.
     *
     * 2.3 da o'lcham oddiy 32-bitli son, lekin spetsifikatsiya uni
     * 28 bitdan oshirmaslikni aytadi — kattaroq tegni ba'zi o'quvchilar
     * rad etadi.
     */
    const val MAX_FRAME_SIZE = 0x0FFFFFFF

    /**
     * Tegni yasaydi: sarlavha + kadrlar.
     *
     * Bo'sh maydonlar tushirib qoldiriladi. Hamma maydon bo'sh bo'lsa ham
     * sarlavha qaytariladi — bu «eski tegni o'chir» degani (yozuvchi uni
     * fayl boshiga qo'yadi, ya'ni eski teg qolmaydi).
     */
    fun build(tags: AudioTags): ByteArray {
        val frames = ByteArrayOutputStream()

        addText(frames, "TIT2", tags.title)
        addText(frames, "TPE1", tags.artist)
        addText(frames, "TALB", tags.album)
        addText(frames, "TYER", tags.year)
        addText(frames, "TCON", tags.genre)
        tags.trackText?.let { addText(frames, "TRCK", it) }
        tags.cover?.let { addPicture(frames, it, tags.coverMime) }

        val body = frames.toByteArray()

        val out = ByteArrayOutputStream(HEADER_SIZE + body.size)
        out.write(ID3.toByteArray(Charsets.US_ASCII))
        out.write(3) // versiya 2.3
        out.write(0) // reviziya
        out.write(0) // bayroqlar: sinxronlash ham, qo'shimcha ham yo'q
        out.write(synchsafe(body.size), 0, 4)
        out.write(body, 0, body.size)
        return out.toByteArray()
    }

    /**
     * Synchsafe o'lcham: har baytning faqat 7 biti ishlatiladi.
     *
     * Nega shunday: teg o'lchami audio oqimidan ajratilishi kerak, oqimda
     * esa har qanday bayt uchrashi mumkin. 7 bitli kodlash «11111111» ni
     * umuman ishlatmaydi va shu bilan kadr sinxronizatsiyasi bilan
     * chalkashmaydi.
     */
    fun synchsafe(size: Int): ByteArray {
        val safe = size.coerceIn(0, MAX_FRAME_SIZE)
        return byteArrayOf(
            ((safe shr 21) and 0x7F).toByte(),
            ((safe shr 14) and 0x7F).toByte(),
            ((safe shr 7) and 0x7F).toByte(),
            (safe and 0x7F).toByte(),
        )
    }

    /** Synchsafe o'lchamni o'qiydi (fayldan kelgan 4 bayt). */
    fun readSynchsafe(bytes: ByteArray, offset: Int = 0): Int =
        ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)

    /**
     * Bitta kadr: nom + o'lcham + bayroqlar + mazmun.
     *
     * 2.3 da kadr o'lchami **oddiy** 32-bitli son, synchsafe emas — 2.4 dan
     * farqi shu. Adashtirilsa, o'quvchi kadrni topa olmaydi.
     */
    fun frame(id: String, payload: ByteArray): ByteArray {
        require(id.length == 4) { "Kadr nomi to'rt belgidan iborat bo'lishi kerak: $id" }
        require(payload.size <= MAX_FRAME_SIZE) { "Kadr juda katta: ${payload.size}" }

        val out = ByteArrayOutputStream(FRAME_HEADER_SIZE + payload.size)
        out.write(id.toByteArray(Charsets.US_ASCII))
        out.write(int32(payload.size), 0, 4)
        out.write(0) // bayroqlar
        out.write(0)
        out.write(payload, 0, payload.size)
        return out.toByteArray()
    }

    /** Matn kadri mazmuni: kodlash bayti + BOM'li UTF-16 + nol. */
    fun textPayload(value: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ENCODING_UTF16)
        out.write(0xFF) // BOM: little-endian
        out.write(0xFE)
        out.write(value.toByteArray(Charsets.UTF_16LE))
        out.write(0) // matn oxiri
        out.write(0)
        return out.toByteArray()
    }

    /**
     * Muqova kadri (APIC).
     *
     * Tuzilishi: kodlash + MIME + nol + rasm turi + izoh + rasm. Izoh
     * bo'sh, lekin u ham kodlash bayti va nol bilan yozilishi shart —
     * tushirib qoldirilsa, o'quvchi rasm baytlarini izoh deb o'qib,
     * muqovani buzib ko'rsatadi.
     */
    fun picturePayload(image: ByteArray, mime: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ENCODING_UTF16)
        out.write(mime.toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write(PICTURE_FRONT_COVER)
        out.write(0xFF) // bo'sh izoh: UTF-16, keyin nol
        out.write(0xFE)
        out.write(0)
        out.write(0)
        out.write(image, 0, image.size)
        return out.toByteArray()
    }

    private fun addText(out: ByteArrayOutputStream, id: String, value: String) {
        if (value.isBlank()) return
        val payload = textPayload(value.trim())
        out.write(frame(id, payload), 0, FRAME_HEADER_SIZE + payload.size)
    }

    private fun addPicture(out: ByteArrayOutputStream, image: ByteArray, mime: String) {
        if (image.isEmpty()) return
        val payload = picturePayload(image, mime)
        out.write(frame("APIC", payload), 0, FRAME_HEADER_SIZE + payload.size)
    }

    /** Katta uchli (big-endian) 32-bitli son — 2.3 dagi kadr o'lchami. */
    private fun int32(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private const val ID3 = "ID3"
}
