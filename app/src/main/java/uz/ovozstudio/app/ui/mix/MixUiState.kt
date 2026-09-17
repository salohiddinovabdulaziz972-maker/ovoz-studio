package uz.ovozstudio.app.ui.mix

import uz.ovozstudio.app.media.mix.MixTrackText

/**
 * Shu darajadan kichik tushirish ekranda aytilmaydi (0.1 dB).
 *
 * Bitta to'liq shkaladagi yo'l ham 0.999 chegarasidan oshadi, ya'ni tushirish
 * 0.01 dB bo'lishi mumkin — uni «0 dB tushirildi» deb aytish chalg'itardi.
 */
const val LIMIT_NOTICE_DB = -0.1f

/** Aralashma ekranidagi xatolar. Matn emas, kod — matnni UI joriy tilda yozadi. */
enum class MixUiError {
    /** Birorta yo'l qo'shilmagan. */
    EMPTY,

    /** Yo'llar chegarasidan oshib ketdi. */
    TOO_MANY,

    /** Bu fayl loyihada allaqachon bor. */
    ALREADY_ADDED,

    /** Manba fayl topilmadi. */
    FILE_MISSING,

    /** Yo'llarning chastotasi har xil. */
    SAMPLE_RATE_MISMATCH,

    /** Qo'llanmaydigan kanal soni. */
    UNSUPPORTED_CHANNELS,

    /** Eshitiladigan yo'l yo'q. */
    NO_AUDIBLE_TRACK,

    /** Natijani yozib bo'lmadi. */
    WRITE_FAILED,
}

/**
 * Ekrandagi bitta yo'l.
 *
 * Sozlamalar **matn** ko'rinishida saqlanadi, son ko'rinishida emas: maydon
 * yozib turgan paytda qiymat tugallanmagan bo'ladi («-» yoki «1.»), va uni
 * har bosishda songa aylantirib, keyin qayta yozib chiqish foydalanuvchining
 * qo'l ostidagi kursorni sakratib yuborardi.
 */
data class MixTrackUi(
    val name: String,
    /** Manba faylning nomi — loyihada saqlanadigan kalit. */
    val file: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val gainText: String,
    val panText: String,
    val offsetText: String,
    val muted: Boolean,
    val solo: Boolean,
    /** Manba fayl papkada yo'q (o'chirilgan yoki ko'chirilgan). */
    val missing: Boolean,
)

data class MixUiState(
    val tracks: List<MixTrackUi> = emptyList(),
    val masterText: String = "0",
    val busy: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val error: MixUiError? = null,
    /** Xatoni tushuntiradigan qo'shimcha ma'lumot (masalan, chastotalar ro'yxati). */
    val errorDetail: String = "",
    /** Yozilgan natija faylining yo'li. */
    val outputPath: String? = null,
    val outputMs: Long = 0L,
    /**
     * Kesish himoyasi qo'llangan koeffitsient, desibelda (0 — tushirilmadi).
     *
     * Ekranda ko'rsatiladi: foydalanuvchi «kuchaytirdim, lekin balandroq
     * bo'lmadi» deb tushunmay qolmasligi kerak.
     */
    val appliedGainDb: Float = 0f,
) {

    /**
     * Aralashmaning uzunligi: eng uzoqqa cho'zilgan **eshitiladigan** yo'l
     * bo'yicha. O'chirilgan yo'l uzunlikni belgilamaydi — u eshitilmaydi.
     */
    val totalMs: Long
        get() {
            val anySolo = tracks.any { it.solo }
            return tracks
                .filter { !it.muted && (!anySolo || it.solo) }
                .maxOfOrNull { MixTrackText.parseOffset(it.offsetText) + it.durationMs }
                ?: 0L
        }

    /**
     * Aralashtirish mumkinmi.
     *
     * Manbasi topilmagan yo'l bo'lsa — yo'q: u jimgina aralashmadan tushib
     * qolardi, ya'ni natija foydalanuvchi kutganidan boshqa bo'lardi.
     */
    val canMix: Boolean get() = tracks.isNotEmpty() && !busy && tracks.none { it.missing }

    /** Aralashma cho'qqi chegarasidan oshib, sezilarli darajada tushirildimi. */
    val limited: Boolean get() = appliedGainDb <= LIMIT_NOTICE_DB
}
