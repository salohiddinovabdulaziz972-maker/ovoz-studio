package uz.ovozstudio.app.media.format

/**
 * Import nega bajarilmadi — matn emas, kod. Matnni UI tanlaydi.
 *
 * Alohida faylda turadi (import qiluvchining o'zida emas), chunki sabab
 * kodini ekran holati ham ko'taradi: `StemUiState` uni maydon sifatida
 * saqlaydi, ekran esa matnga o'giradi. Import qiluvchining o'zi Android'ga
 * bog'liq, sabab kodi esa yo'q — shu sababdan ajratilgan.
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
}
