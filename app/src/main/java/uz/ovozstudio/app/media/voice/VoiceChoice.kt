package uz.ovozstudio.app.media.voice

/**
 * Ovozlar ro'yxatidan o'zbek tiliga mosini ajratadi.
 *
 * Qurilmada yuzlab ovoz bo'lishi mumkin (har bir til uchun bir nechta).
 * Ularni foydalanuvchiga to'liq ko'rsatish ekran o'quvchi bilan yurishni
 * azobga aylantiradi, shuning uchun o'zbekcha ovozlar ajratib beriladi;
 * o'zbekcha ovoz umuman bo'lmasa — ro'yxat qisqartirilgan holda hammasi.
 *
 * **Microsoft Sardor va Madina** — o'zbek tilidagi ikki ovoz. Ular bulutli
 * (Azure) ovozlar bo'lib, Android qurilmada faqat ularni sintezator sifatida
 * ochib beradigan dvigatel dasturi orqali ko'rinadi. Shunday dvigatel
 * tanlangan bo'lsa, ular ro'yxatning boshida turadi va ovoz tanlanmagan
 * paytda avtomatik ishlatiladi.
 *
 * Bu — sof mantiq, Android'ga bog'liq emas.
 */
object VoiceChoice {

    /** Tavsiya bo'lmasa, ro'yxatga ko'pi bilan shuncha ovoz chiqariladi. */
    const val MAX_LISTED = 40

    /**
     * Ovoz nomida «Sardor» yoki «Madina» bormi. Nom dvigatelga qarab har xil
     * yoziladi (`uz-UZ-SardorNeural`, `Microsoft Madina Online (Natural)`),
     * shuning uchun katta-kichik harf ahamiyatsiz, faqat ismning o'zi izlanadi.
     */
    fun isMicrosoftUzbek(name: String): Boolean =
        name.contains("sardor", ignoreCase = true) || name.contains("madina", ignoreCase = true)

    /** Til-teg o'zbek tiliminmi (`uz`, `uz-UZ`, `uz-Cyrl-UZ`). */
    fun isUzbek(localeTag: String): Boolean = localeTag.startsWith("uz", ignoreCase = true)

    /**
     * Ovoz tanlanmagan paytda ishlatiladigan ovoz: Sardor yoki Madina bo'lsa —
     * shulardan biri, bo'lmasa `null` (til bo'yicha tanlash davom etadi).
     */
    fun automatic(voices: List<VoiceInfo>): VoiceInfo? =
        voices.filter { isMicrosoftUzbek(it.name) }.sortedBy { it.name.lowercase() }.firstOrNull()

    /**
     * Foydalanuvchiga ko'rsatiladigan ovozlar: avval Sardor/Madina, keyin
     * boshqa o'zbekcha ovozlar. O'zbekcha ovoz yo'q bo'lsa — hamma ovoz
     * (til va nom bo'yicha tartiblangan, [MAX_LISTED] tagacha).
     */
    fun recommended(voices: List<VoiceInfo>): List<VoiceInfo> {
        val uzbek = voices.filter { isMicrosoftUzbek(it.name) || isUzbek(it.localeTag) }
        val pool = if (uzbek.isNotEmpty()) uzbek else voices
        return pool
            .sortedWith(
                compareBy<VoiceInfo>(
                    { !isMicrosoftUzbek(it.name) },
                    { it.localeTag },
                    { it.name },
                ),
            )
            .take(MAX_LISTED)
    }

    /** Ro'yxatda o'zbekcha ovoz (Sardor/Madina ham) bormi — ekran ogohlantirish beradimi. */
    fun hasUzbek(voices: List<VoiceInfo>): Boolean =
        voices.any { isMicrosoftUzbek(it.name) || isUzbek(it.localeTag) }
}
