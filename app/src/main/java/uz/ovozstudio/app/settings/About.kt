package uz.ovozstudio.app.settings

/**
 * Ilova haqidagi o'zgarmas ma'lumot.
 *
 * Matn resurs emas, **manzil** — u tarjima qilinmaydi va har tilda bir xil
 * bo'lishi kerak. Shu sababli kodda turadi (qolgan matnlar `strings.xml` da).
 */
object About {

    /** Manba kod. Litsenziya GPL-3.0 bo'lgani uchun bu manzil ochiq bo'lishi shart. */
    const val SOURCE_URL = "https://github.com/salohiddinovabdulaziz972-maker/ovoz-studio"

    /** Litsenziya matni — manba kodi bilan bir papkada. */
    const val LICENSE_URL = "$SOURCE_URL/blob/main/LICENSE"

    /** Litsenziya nomi — GPL-3.0 ataylab tanlangan (reklamali yopiq nusxa bo'lmasin). */
    const val LICENSE_NAME = "GPL-3.0"
}
