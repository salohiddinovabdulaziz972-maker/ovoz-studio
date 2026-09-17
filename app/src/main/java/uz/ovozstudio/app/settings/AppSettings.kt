package uz.ovozstudio.app.settings

/**
 * Ilovaning saqlanadigan sozlamalari.
 *
 * Hammasi **bitta** kichik faylda saqlanadi (`AppSettingsStore`): sozlama
 * bir nechta bo'lib qolsa ham, ular bir odamning qarori — alohida fayllarga
 * bo'lish ularni bir-biridan mustaqil qilib qo'yardi va tiklash paytida
 * chalkashlik berardi.
 *
 * Qiymatlar ataylab **odam o'qiy oladigan** ko'rinishda: til — BCP-47 tegi
 * (`"uz-Cyrl"`), mantiqiy sozlama — `true`/`false`. Fayl buzuq bo'lsa ham
 * foydalanuvchi nima yozilganini ko'ra oladi.
 */
data class AppSettings(
    /** Til tanlovi. `SYSTEM` — qurilma tilini ishlatish. */
    val language: AppLanguage = AppLanguage.SYSTEM,
    /**
     * Soddalashtirilgan rejim: ekranda faqat asosiy amallar qoladi.
     *
     * Ekran o'quvchi uchun bu ko'proq narsa emas, **kamroq narsa**: har bir
     * tugma — alohida fokus nuqtasi, ya'ni kerak bo'lmagan tugmalar orasidan
     * o'tish vaqti. Rejim ikkinchi darajali amallarni yashiradi, ularni
     * yo'q qilmaydi — kerak bo'lganda ochib olinadi.
     */
    val simplified: Boolean = false,
)
