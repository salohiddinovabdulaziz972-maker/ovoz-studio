package uz.ovozstudio.app.media.format

/**
 * ADTS sarlavhasi — xom AAC kadrlarini o'z-o'zini ochib beruvchi oqimga
 * aylantiradi (`.aac` fayllari shunday tuzilgan).
 *
 * Nega kerak: `MediaCodec` AAC kodlovchisi faqat **xom** kadrlarni beradi,
 * ularda chastota ham, kanal soni ham yo'q — ular konteyner ichida
 * saqlanadi. `.m4a` uchun buni `MediaMuxer` bajaradi, lekin `.aac` uchun
 * muxer yo'q: har bir kadr oldiga 7 baytlik ADTS sarlavhasi qo'lda
 * yozilishi kerak, aks holda faylni hech bir pleyer ocholmaydi.
 *
 * Sof arifmetika — Android'ga bog'liq emas, shuning uchun JVM'da
 * tekshiriladi. Bu ataylab: xato sarlavha faylni "jimgina buzadi",
 * qurilmada buni payqash qiyin.
 */
object AdtsHeader {

    /** Sarlavha uzunligi, nazorat summasi (CRC) ishlatilmaganda. */
    const val LENGTH = 7

    /** AAC Low Complexity — eng keng tarqalgan profil. */
    const val PROFILE_AAC_LC = 1

    /**
     * Namuna chastotasi indeksi. Jadval [CodecRates] da — ikki joyda ikki
     * xil bo'lsa, sarlavha noto'g'ri indeks yozib, fayl jimgina buzilardi.
     *
     * Ro'yxatda yo'q chastota — dastur xatosi, shuning uchun `-1` emas,
     * istisno.
     */
    fun samplingIndex(sampleRate: Int): Int {
        val index = CodecRates.aacIndex(sampleRate)
        require(index >= 0) { "AAC bu chastotani bilmaydi: $sampleRate Hz" }
        return index
    }

    /** Shu chastotani AAC umuman qo'llab-quvvatlaydimi. */
    fun supports(sampleRate: Int): Boolean = CodecRates.supports(AudioCodec.AAC, sampleRate)

    /**
     * [target] ning [offset] dan boshlab 7 baytini to'ldiradi.
     *
     * [payloadLength] — sarlavhadan keyingi xom AAC kadr uzunligi; umumiy
     * `frame_length` maydoniga sarlavhaning o'zi ham qo'shiladi.
     */
    fun write(
        target: ByteArray,
        offset: Int,
        payloadLength: Int,
        sampleRate: Int,
        channels: Int,
        profile: Int = PROFILE_AAC_LC,
    ): Int {
        require(offset >= 0 && offset + LENGTH <= target.size) {
            "Bufer yetarli emas: $offset + $LENGTH > ${target.size}"
        }
        require(channels in 1..7) { "AAC kanal konfiguratsiyasi 1..7: $channels" }
        require(profile in 0..3) { "AAC profili 2 bit: $profile" }

        val frameLength = payloadLength + LENGTH
        require(frameLength in 0..0x1FFF) { "ADTS kadri 13 bitga sig'maydi: $frameLength" }

        val rateIndex = samplingIndex(sampleRate)

        target[offset] = 0xFF.toByte()
        // 1111 (sinxronizatsiya oxiri) 0 (MPEG-4) 00 (qatlam) 1 (CRC yo'q)
        target[offset + 1] = 0xF1.toByte()
        target[offset + 2] =
            ((profile shl 6) or (rateIndex shl 2) or (channels shr 2)).toByte()
        target[offset + 3] = (((channels and 0x03) shl 6) or (frameLength shr 11)).toByte()
        target[offset + 4] = ((frameLength shr 3) and 0xFF).toByte()
        target[offset + 5] = (((frameLength and 0x07) shl 5) or 0x1F).toByte()
        // To'liqlik ko'rsatkichi 0x7FF (o'zgaruvchan bit tezligi) + 1 blok.
        target[offset + 6] = 0xFC.toByte()
        return LENGTH
    }

    /**
     * AAC konfiguratsiya baytlaridan (`MediaCodec` `csd-0`) kanal sonini
     * o'qiydi. Kodlovchi qaytargan haqiqiy qiymat biz so'raganimizdan
     * farq qilishi mumkin — sarlavha fayl ichidagi haqiqatga mos bo'lsin.
     *
     * AudioSpecificConfig: 5 bit obyekt turi, 4 bit chastota indeksi,
     * 4 bit kanal konfiguratsiyasi.
     */
    fun channelCountFromCsd(csd: ByteArray?): Int? {
        if (csd == null || csd.size < 2) return null
        val value = ((csd[0].toInt() and 0xFF) shl 8) or (csd[1].toInt() and 0xFF)
        val channels = (value shr 3) and 0x0F
        return if (channels in 1..7) channels else null
    }

}
