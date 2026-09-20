package uz.ovozstudio.app.settings

import uz.ovozstudio.app.util.AtomicFileWriter
import java.io.File
import java.util.Properties

/**
 * Sozlamalarni faylga saqlaydi va qaytaradi.
 *
 * `java.util.Properties` — aralashma loyihasidagi bilan bir xil tanlov:
 * bir nechta kalit-qiymat uchun baza yoki JSON kutubxonasi ortiqcha bo'lardi.
 *
 * **Yozuv sinxron.** Bu ataylab: tilni almashtirish Activity'ni qayta
 * yaratadi, u esa `attachBaseContext` da faylni **darhol** o'qiydi. Yozuv
 * fonda bo'lsa, poyga chiqardi: qayta ochilgan ekran eski tilni o'qib,
 * foydalanuvchi tanlagan til «o'z-o'zidan qaytib ketgandek» ko'rinardi.
 * Fayl ~100 bayt; yozuv (diskka tushirish bilan) bir necha millisekund
 * oladi. Til almashtirish kam bo'ladigan amal, shuning uchun bu sezilmaydi.
 *
 * Buzuq fayl — buzuq sozlama emas, **zaxira sozlama**: o'qib bo'lmasa
 * standart qiymatlar olinadi. Sozlama fayli uchun xato oynasi ko'rsatish
 * noto'g'ri bo'lardi — foydalanuvchi ilovani ochib ishlatishi kerak.
 */
class AppSettingsStore(private val file: File) {

    fun load(): AppSettings {
        if (!file.exists()) return AppSettings()
        val properties = Properties()
        val loaded = runCatching { file.inputStream().use { properties.load(it) } }.isSuccess
        if (!loaded) return AppSettings()

        val language = properties.getProperty(KEY_LANGUAGE)
            ?.let { value -> AppLanguage.entries.firstOrNull { it.tag == value } }
            ?: AppLanguage.SYSTEM
        val simplified = properties.getProperty(KEY_SIMPLIFIED)?.toBooleanStrictOrNull() ?: false
        return AppSettings(language = language, simplified = simplified)
    }

    fun save(settings: AppSettings) {
        val properties = Properties()
        properties.setProperty(KEY_LANGUAGE, settings.language.tag ?: SYSTEM_VALUE)
        properties.setProperty(KEY_SIMPLIFIED, settings.simplified.toString())
        // Vaqtinchalik fayl + `rename`: yozish yiqilsa (joy yo'q), eski
        // sozlama butun qoladi — «yozib bo'lmasa o'zgarish qo'llanmaydi»
        // qoidasi shu bilan haqiqatan bajariladi.
        AtomicFileWriter.write(file) { output ->
            properties.store(output, "OvozStudio sozlamalari")
        }
    }

    companion object {
        /**
         * Fayl nomi `filesDir` ichida.
         *
         * Ilova papkasining o'zi (`OvozStudio`) ishlatilmaydi: u
         * foydalanuvchiga ko'rinadi va fayl menejerida o'chirilishi mumkin.
         * Sozlama esa ilovaning ichki holati — uni yo'qotish tilni
         * «o'z-o'zidan» qaytarib qo'yardi.
         */
        const val FILE_NAME = "sozlamalar.properties"

        /** `filesDir` berilgan papkadan saqlagich yasaydi. */
        fun inFiles(directory: File): AppSettingsStore = AppSettingsStore(File(directory, FILE_NAME))

        private const val KEY_LANGUAGE = "language"
        private const val KEY_SIMPLIFIED = "simplified"

        /** `SYSTEM` ning baytga yoziladigan ko'rinishi (teg `null`). */
        private const val SYSTEM_VALUE = "system"
    }
}
