package uz.ovozstudio.app.settings

import java.util.Locale

/**
 * Ilova tilining tanlovi.
 *
 * `SYSTEM` — «qurilma tilini ishlat»: Android o'zi mos resurs papkasini
 * tanlaydi. Qolganlari — foydalanuvchi qo'lda tanlagan til; u qurilma tilidan
 * ustun turadi (ekran o'quvchi foydalanuvchisi uchun bu muhim: qurilma tili
 * ruscha bo'lib, ilova o'zbekcha o'qilishi kerak bo'lishi mumkin).
 *
 * [tag] — BCP-47 tegi. `null` — tizim tanlovi, ya'ni til majburlanmaydi.
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    UZ_LATN("uz"),
    UZ_CYRL("uz-Cyrl"),
    RU("ru"),
    EN("en"),
    ;

    /** Foydalanuvchi tilni qo'lda tanlaganmi (tizim tanlovi emasmi). */
    val chosen: Boolean get() = tag != null
}

/**
 * Tizim tilidan ilova tilini aniqlash.
 *
 * Nega kerak: ekranda «Tizim tili» tanlangan bo'lsa, foydalanuvchi qaysi til
 * ochilishini bilishi kerak — ayniqsa qurilma tilida ilova tarjimasi
 * bo'lmasa. Bunda Android `values/` papkasini oladi, ya'ni ilova **o'zbekcha**
 * ochiladi; bu ilovaning ataylab tanlangan zaxirasi (README, «Nega o'zbekcha»).
 */
object LanguageMatch {

    /** Qo'llab-quvvatlanmagan til uchun javob: ilovaning zaxira tili. */
    val DEFAULT: AppLanguage = AppLanguage.UZ_LATN

    /**
     * Qurilma tillari ro'yxatidan mos tilni tanlaydi.
     *
     * Ro'yxat **tartibi muhim**: Android tillarni afzallik bo'yicha beradi,
     * ya'ni birinchi mos kelgani to'g'ri javob. Mos keladigan til topilmasa —
     * [DEFAULT] (ilova o'zbekcha ochiladi).
     */
    fun resolve(deviceTags: List<String>): AppLanguage {
        for (raw in deviceTags) {
            val tag = raw.trim().lowercase(Locale.ROOT).replace('_', '-')
            if (tag.isEmpty()) continue
            val parts = tag.split('-')
            when (parts.first()) {
                // O'zbekcha ikki yozuvda: lotin va kirill. Kirillni faqat
                // yozuv qismidan ajratish mumkin ("uz-Cyrl-UZ").
                "uz" -> return if (parts.any { it == "cyrl" }) AppLanguage.UZ_CYRL else AppLanguage.UZ_LATN
                "ru" -> return AppLanguage.RU
                "en" -> return AppLanguage.EN
            }
        }
        return DEFAULT
    }
}
