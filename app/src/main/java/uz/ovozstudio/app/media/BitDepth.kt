package uz.ovozstudio.app.media

/**
 * Bit chuqurligi. WAV faylga faqat 16 va 24 bit yoziladi: 8 va 32 bitli
 * fayllar ilovada ochilmaydi (import paytida aniq xabar bilan rad etiladi).
 *
 * Ichkarida namunalar har doim float ko'rinishida yuradi va faylga yozishda
 * kerakli chuqurlikka o'giriladi, shuning uchun ikkala chuqurlik uchun bitta
 * kod yo'li ishlaydi.
 */
enum class BitDepth(val bits: Int) {
    BIT_16(16),
    BIT_24(24);

    companion object {
        /**
         * Bit chuqurligini raqam bo'yicha topadi; noma'lum qiymat uchun `null`.
         *
         * `null` qaytariladi, xato tashlanmaydi: chaqiruvchi qaysi xatoni
         * ko'rsatishni o'zi hal qiladi (masalan fayl o'qishda bu — `IOException`).
         */
        fun of(bits: Int): BitDepth? = entries.firstOrNull { it.bits == bits }
    }
}
