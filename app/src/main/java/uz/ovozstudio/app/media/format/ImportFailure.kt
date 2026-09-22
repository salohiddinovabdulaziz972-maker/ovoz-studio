package uz.ovozstudio.app.media.format

/**
 * Import nega bajarilmadi — matn emas, kod. Matnni UI tanlaydi.
 *
 * Alohida faylda turadi (import qiluvchining o'zida emas), chunki sabab
 * kodini ekran holati ham ko'taradi: kesish va birlashtirish ekranlari uni
 * maydon sifatida saqlaydi, ekran esa matnga o'giradi. Import qiluvchining
 * o'zi Android'ga bog'liq, sabab kodi esa yo'q — shu sababdan ajratilgan.
 */
enum class ImportFailure {
    /** Fayl audio emas yoki formati umuman noma'lum. */
    UNKNOWN_FORMAT,

    /** Format tanildi, lekin bu qurilmada ochadigan dekoder yo'q (WMA). */
    NO_DECODER,

    /** Fayl o'qilmadi: nusxalash yoki dekodlash paytida xato. */
    READ_FAILED,

    /** Fayl ochildi, lekin ichida birorta ham namuna yo'q. */
    EMPTY,

    /** WAV fayl 16 yoki 24 bitli emas (8 va 32 bitli WAV tahrirlanmaydi). */
    UNSUPPORTED_DEPTH,

    /**
     * Fayl ochiladi, lekin Android unga **yoza olmaydi** (masalan OGG Vorbis).
     *
     * Ilova natijani aynan yuklangan formatda qaytarishi shart, boshqa
     * formatga jimgina o'tmaydi. Shuning uchun bunday fayl ish boshlanmasdan
     * oldin rad etiladi — foydalanuvchi soatlab ishlab, oxirida «saqlab
     * bo'lmadi» degan xabarni ko'rmasligi kerak.
     */
    CANNOT_WRITE_FORMAT,

    /** Formatga yozish mumkin, lekin qurilma Android'i eski (Opus — API 29 dan). */
    OLD_ANDROID,
}
