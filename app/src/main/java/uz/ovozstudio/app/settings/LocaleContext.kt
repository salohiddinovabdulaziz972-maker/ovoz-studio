package uz.ovozstudio.app.settings

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import java.util.Locale

/**
 * Tanlangan tilni Android kontekstiga qo'llaydi.
 *
 * Nega `AppCompatDelegate` emas: ilova `appcompat` ni ishlatmaydi (Compose
 * yetarli), ya'ni `setApplicationLocales` yo'q. Buning o'rniga Android'ning
 * o'z mexanizmi ishlatiladi: `attachBaseContext` da konfiguratsiya til bilan
 * o'raladi, resurslar esa shu konfiguratsiya bo'yicha tanlanadi. Usul barcha
 * versiyalarda (minSdk 24 dan) bir xil ishlaydi.
 *
 * Til **Activity** darajasida qo'llanadi: ilova konteksti (`Application`)
 * tizim tilida qoladi. Bu ataylab — aks holda jarayonning umumiy holati
 * o'zgarardi. Ikkalasi bir xil natija beradi, chunki ilova tilni tizim
 * tillar ro'yxatidan aniqlaydi ([followSystem]) va Android'ning o'z
 * tanlashi ham shu ro'yxatdan boradi.
 */
object LocaleContext {

    /**
     * [base] kontekstini [language] tilida qaytaradi.
     *
     * `SYSTEM` tanlansa kontekst o'zgarmaydi, lekin jarayonning standart tili
     * baribir qurilma tiliga qaytariladi: aks holda oldin tanlangan til
     * raqam va sanani formatlashda qolib ketardi (ekranda til o'zgargandek,
     * lekin sonlar boshqacha chiqardi).
     */
    fun apply(base: Context, language: AppLanguage): Context {
        val tag = language.tag
        if (tag == null) {
            Locale.setDefault(deviceLocale())
            return base
        }
        val locales = LocaleList.forLanguageTags(tag)
        Locale.setDefault(locales[0])
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(locales)
        return base.createConfigurationContext(configuration)
    }

    /**
     * Ilova tilini **tizim tilidan** aniqlaydi va qo'llaydi.
     *
     * Ilovada til tanlash sozlamasi yo'q: foydalanuvchi qurilma tilini
     * o'zgartiradi (yoki Android 13+ da «Ilova tili» ni tanlaydi) va ilova
     * shunga ergashadi. Bu yerda ikki qadam:
     *  1. [tagsOf] — kontekstdagi tillar ro'yxati afzallik tartibida;
     *  2. [LanguageMatch.resolve] — ro'yxatdan ilova bilgan **birinchi** til.
     *
     * Nega Android'ning o'z tanlashiga ishonmaymiz: ilovaning asosiy tili
     * o'zbekcha lotin `values/` papkasida turadi va unga alohida `uz` belgisi
     * yo'q. Qurilmada `[uz, ru]` tartibida tillar bo'lsa, tizim mos papkani
     * topolmay, ruscha resursni tanlab qo'yishi mumkin. Aniq hisoblangan
     * til bunday tasodifga yo'l qo'ymaydi: birinchi bilingan til — o'zbekcha.
     *
     * O'zbekcha qurilmada ilova **to'liq o'zbekcha**: lotin yozuvida yoki
     * (qurilma kirillda bo'lsa) kirillda.
     */
    fun followSystem(base: Context): Context = apply(base, LanguageMatch.resolve(tagsOf(base)))

    /**
     * [context] konfiguratsiyasidagi tillar ro'yxati (afzallik tartibida).
     *
     * `Resources.getSystem()` emas, aynan kontekstning o'zi: Android 13+ da
     * foydalanuvchi ilova uchun alohida til tanlagan bo'lsa, u kontekst
     * konfiguratsiyasida turadi, tizim konfiguratsiyasida esa yo'q.
     */
    fun tagsOf(context: Context): List<String> {
        val locales = runCatching { context.resources.configuration.locales }.getOrNull()
        if (locales == null || locales.isEmpty) return deviceTags()
        return locales.toLanguageTags().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Qurilma tili — `Resources.getSystem()` dan olinadi.
     *
     * `Locale.getDefault()` bu yerda yaramaydi: biz uni o'zimiz
     * o'zgartirgan bo'lishimiz mumkin, ya'ni u qurilma tilini emas, oxirgi
     * tanlovni ko'rsatardi.
     */
    fun deviceLocale(): Locale {
        val locales = runCatching { Resources.getSystem().configuration.locales }.getOrNull()
        if (locales == null || locales.isEmpty) return Locale.getDefault()
        return locales[0]
    }

    /** Qurilma tillari ro'yxati — `LanguageMatch.resolve` uchun. */
    fun deviceTags(): List<String> {
        val locales = runCatching { Resources.getSystem().configuration.locales }.getOrNull()
        if (locales == null || locales.isEmpty) return listOf(Locale.getDefault().toLanguageTag())
        return locales.toLanguageTags().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
