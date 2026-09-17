package uz.ovozstudio.app.media.tag

import uz.ovozstudio.app.media.format.AudioContainer

/**
 * Bu konteynerda ID3 teg yozish mumkinmi.
 *
 * Faqat MP3: MP4/M4A o'z atomlarini (`©nam`, `covr`), FLAC va OGG esa
 * Vorbis izohini ishlatadi — boshqa tuzilma, alohida ish. Noto'g'ri
 * formatga ID3 yozish faylni buzardi: pleyer tegni ovoz deb o'qib,
 * boshidan shovqin chiqarardi.
 *
 * Shuning uchun tekshiruv kengaytma bo'yicha emas, **fayl tarkibi**
 * bo'yicha (`AudioFormatDetector`) — `ovoz.mp3` deb nomlangan WAV ham
 * rad etilishi kerak.
 */
fun supportsId3Tags(container: AudioContainer): Boolean = container == AudioContainer.MP3

/**
 * Teg maydonlaridagi xatolar.
 *
 * Naqsh ilova bo'ylab bir xil: mantiq matn emas, **kod** qaytaradi —
 * qaysi tilda yozishni ekran hal qiladi.
 */
enum class TagError {
    /** Fayl tanlanmagan. */
    NO_FILE,

    /** Bu formatda ID3 tegi yo'q (hozircha faqat MP3 qo'llab-quvvatlanadi). */
    UNSUPPORTED_FORMAT,

    /** Birorta maydon to'ldirilmagan — yozadigan narsa yo'q. */
    EMPTY_TAGS,

    /** Muqova rasmi juda katta. */
    COVER_TOO_LARGE,

    /** Yozib bo'lmadi (joy yo'q, fayl o'qilmayapti). */
    WRITE_FAILED,
}

/**
 * Foydalanuvchi kiritgan teg maydonlari.
 *
 * Maydonlar **satr** ko'rinishida saqlanadi: foydalanuvchi yozayotganda
 * «3/1» kabi tugallanmagan matn ham vaqtincha yashashi kerak (ilova bo'ylab
 * yagona qoida — qiymat qo'lda kiritiladi). Songa o'girish faqat yozish
 * paytida bo'ladi.
 */
class TagDraft(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    /** Tartib raqami — «3» yoki «3/12». */
    val track: String = "",
    val cover: ByteArray? = null,
    val coverMime: String = "image/jpeg",
) {

    /** Yozishga tayyor teg. */
    fun toTags(): AudioTags {
        val numbers = parseTrack(track)
        return AudioTags(
            title = title.trim(),
            artist = artist.trim(),
            album = album.trim(),
            year = year.trim().take(4),
            genre = genre.trim(),
            track = numbers.first,
            trackTotal = numbers.second,
            cover = cover,
            coverMime = coverMime,
        )
    }

    /**
     * Yozishdan oldingi tekshiruv. Xato bo'lmasa `null`.
     *
     * Muqova hajmi shu yerda ham tekshiriladi: xatoni fayl yozish
     * boshlanishidan **oldin** aytish kerak — foydalanuvchi kutib, keyin
     * «yozilmadi» degan xabarni ko'rmasin.
     */
    fun validationError(
        fileSelected: Boolean,
        coverLimit: Int = Mp3Tagger.MAX_COVER_BYTES,
    ): TagError? = when {
        !fileSelected -> TagError.NO_FILE
        cover != null && cover.size > coverLimit -> TagError.COVER_TOO_LARGE
        toTags().isEmpty -> TagError.EMPTY_TAGS
        else -> null
    }

    /** Tartib raqamini o'qish: «3/12» → 3 va 12. Noto'g'ri matn — 0. */
    private fun parseTrack(value: String): Pair<Int, Int> {
        val parts = value.trim().split('/')
        val number = parts.getOrNull(0).toPositiveInt()
        val total = parts.getOrNull(1).toPositiveInt()
        return number to total
    }

    private fun String?.toPositiveInt(): Int =
        this?.trim()?.takeWhile(Char::isDigit)?.take(4)?.toIntOrNull() ?: 0
}
