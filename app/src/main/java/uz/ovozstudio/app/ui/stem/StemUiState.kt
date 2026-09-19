package uz.ovozstudio.app.ui.stem

import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.dsp.StemSeparator
import uz.ovozstudio.app.media.format.ImportFailure
import uz.ovozstudio.app.util.DecimalText

/**
 * Vokal/cholg'u ajratish xatoliklari.
 *
 * Naqsh `NoiseError` bilan bir xil: ViewModel matn emas, KOD qaytaradi —
 * qaysi tilda ko'rsatishni UI hal qiladi.
 *
 * [NOT_STEREO] va [MONO_CONTENT] — bu usulning emas, **kanal usulining**
 * chegarasi. Ular DSP dan shu holatda keladi: faylda ajratadigan narsa
 * yo'q. Sababni ekranda aytish shart, chunki foydalanuvchi «ajratdim»
 * deb o'ylab, aslida o'zgarmagan fayl olib qolmasligi kerak.
 */
enum class StemError {
    FILE_NOT_FOUND,

    /** Manba bitta kanalli (yoki umuman stereo emas). */
    NOT_STEREO,

    /** Manba stereoda yozilgan, lekin kanallari bir xil — mazmuni mono. */
    MONO_CONTENT,

    /** Amalning o'zi bajarilmadi (o'qish/yozish xatosi). */
    EDIT_FAILED,
}

/**
 * Vokal/cholg'u ajratish ekranining holati.
 *
 * [strength] — satr, son emas: maydon klaviaturadan to'ldiriladi va
 * foydalanuvchi yozayotganda tugallanmagan matn ham vaqtincha yashashi
 * kerak ([DecimalText.parse] ning `fallback` i ishlatiladi).
 *
 * [savedVocalPath] va [savedInstrumentalPath] — natijaning **ikkalasi**.
 * Bittasini ko'rsatib, ikkinchisini yashirish noto'g'ri bo'lardi: ajratish
 * ikkita fayl beradi va foydalanuvchi ikkalasini ham kutmoqda.
 */
data class StemUiState(
    val fileName: String = "",
    val info: WavInfo? = null,
    val mode: StemSeparator.Mode = StemSeparator.Mode.SPLIT,
    val strength: String = STRENGTH_DEFAULT_TEXT,
    val busy: Boolean = false,
    val progress: Float = 0f,
    val savedVocalPath: String? = null,
    val savedInstrumentalPath: String? = null,
    /** O'lchangan natija: yon qism markazga nisbatan qanchalik kuchli. */
    val sideToMidDb: Double = 0.0,
    /** Tizim tanlagichidan kelgan fayl ochilmadi — sabab (kod ko'rinishida). */
    val importFailure: ImportFailure? = null,
    val error: StemError? = null,
) {
    val durationMs: Long get() = info?.durationMs ?: 0L

    /** Manba bitta kanalli: usul qo'llanilmaydi. */
    val notStereo: Boolean get() = info != null && info.channels != 2

    /**
     * Manbada yon qism juda kuchsiz.
     *
     * Bunday yozuvda vokal ham, cholg'u ham bir joyda — ajratish
     * natijasi deyarli sezilmaydi. Bu **o'lchangan** son bo'yicha
     * aytiladi: taxmin emas, balki [StemSeparator.Result.sideToMidDb].
     */
    val nearlyMono: Boolean get() = sideToMidDb <= NEARLY_MONO_DB

    /** Ajratish kuchi faqat [StemSeparator.Mode.SPLIT] da ishlatiladi. */
    val strengthUsed: Boolean get() = mode == StemSeparator.Mode.SPLIT

    /** Kiritilgan qiymatlardan yasalgan sozlama. */
    val settings: StemSeparator.Settings
        get() = StemSeparator.Settings(
            mode = mode,
            strength = DecimalText.parse(
                strength,
                StemSeparator.MIN_STRENGTH,
                StemSeparator.MAX_STRENGTH,
                StemSeparator.DEFAULT_STRENGTH,
            ),
        )

    companion object {
        /** «Kuch» maydonining boshlang'ich ko'rinishi. */
        const val STRENGTH_DEFAULT_TEXT = "1.5"

        /**
         * Shu chegaradan pastda manba amalda mono hisoblanadi.
         *
         * −20 dB — yon qism markazdan o'n barobar kuchsiz degani. Bunday
         * yozuvda cholg'u deyarli eshitilmaydi, ya'ni ajratish natijasi
         * foydalanuvchini ishontirishi mumkin, aslida esa o'zgarish yo'q.
         */
        const val NEARLY_MONO_DB = -20.0
    }
}
