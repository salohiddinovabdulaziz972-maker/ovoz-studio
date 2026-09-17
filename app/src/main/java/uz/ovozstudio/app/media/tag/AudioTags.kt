package uz.ovozstudio.app.media.tag

/**
 * Faylga yoziladigan ma'lumot: nom, ijrochi, albom, muqova va hokazo.
 *
 * Bo'sh maydon yozilmaydi — «nom: (bo'sh)» degan teg pleyerda eski nomni
 * o'chirib, ro'yxatda fayl nomini ko'rsatishga majbur qilardi. Ya'ni bo'sh
 * maydon «o'zgartirmayman» emas, «tozalayman» degani bo'lardi.
 *
 * `data class` emas: muqova — baytlar massivi, ular uchun `equals` baribir
 * kerak emas, lekin avtomatik qurilgan taqqoslash noto'g'ri ishlardi.
 */
class AudioTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: String = "",
    val genre: String = "",
    /** Tartib raqami; 0 — yozilmaydi. */
    val track: Int = 0,
    /** Jami boblar soni; 0 — faqat raqam yoziladi. */
    val trackTotal: Int = 0,
    /** Muqova rasmi (JPEG/PNG baytlari); `null` — muqova yo'q. */
    val cover: ByteArray? = null,
    /** Muqova turi: `image/jpeg` yoki `image/png`. */
    val coverMime: String = "image/jpeg",
) {

    /** Birorta maydon to'ldirilmaganmi — teg yozishning ma'nosi yo'q. */
    val isEmpty: Boolean
        get() = title.isBlank() && artist.isBlank() && album.isBlank() &&
            year.isBlank() && genre.isBlank() && track <= 0 && cover == null

    /**
     * Tartib raqami matni: `3` yoki `3/12`.
     *
     * Jami soni faqat raqamdan katta bo'lsa qo'shiladi: `3/0` ko'rinishi
     * pleyerda «3/0» bo'lib chiqardi.
     */
    val trackText: String?
        get() = when {
            track <= 0 -> null
            trackTotal > track -> "$track/$trackTotal"
            else -> track.toString()
        }
}
