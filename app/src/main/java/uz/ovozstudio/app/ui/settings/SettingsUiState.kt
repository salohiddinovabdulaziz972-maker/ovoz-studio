package uz.ovozstudio.app.ui.settings

import uz.ovozstudio.app.settings.AppLanguage

/**
 * Sozlamalar ekranining holati.
 *
 * Android'ga bog'liq emas: shu sababli hisoblanadigan qismi (qaysi til
 * amalda ishlayapti) JVM'da tekshiriladi — bu yerda jim xato qilish oson,
 * chunki ekranda «Tizim tili» tanlangan bo'lsa ham foydalanuvchi **aniq**
 * bir tilni eshitadi.
 */
data class SettingsUiState(
    /** Foydalanuvchi tanlovi: `SYSTEM` yoki aniq til. */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /** Tizim tanlovida qurilma tilidan aniqlangan til. */
    val systemLanguage: AppLanguage = AppLanguage.UZ_LATN,
    /** Soddalashtirilgan rejim yoniqmi. */
    val simplified: Boolean = false,
    /** Ilova versiyasi (paketdan olinadi). Bo'sh bo'lsa ko'rsatilmaydi. */
    val version: String = "",
    val error: SettingsError? = null,
) {
    /**
     * Amalda ishlatilayotgan til.
     *
     * «Tizim tili» tanlanganda ham ekranda aynan qaysi til ochilishini
     * ko'rsatish kerak: qurilma tili ro'yxatdagi tillardan biri bo'lmasa,
     * ilova o'zbekcha ochiladi — foydalanuvchi buni oldindan bilsin.
     */
    val activeLanguage: AppLanguage get() = if (language.chosen) language else systemLanguage
}

/** Sozlamalar ekranida ko'rsatiladigan xatolar. */
enum class SettingsError {
    /** Sozlama faylga yozilmadi (joy yo'q, ruxsat yo'q) — o'zgarish qo'llanmadi. */
    SAVE_FAILED,

    /** Havolani ochib bo'lmadi (brauzer yo'q). */
    LINK_FAILED,
}
