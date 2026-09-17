package uz.ovozstudio.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Kontekst zanjiridan `Activity` ni topadi.
 *
 * Compose konteksti har doim ham Activity'ning o'zi emas: mavzu (theme)
 * o'rami, `LocalContext` ni almashtirgan kutubxona — hammasi
 * `ContextWrapper` qo'shadi. Aktivni talab qiladigan amallar (masalan, tilni
 * almashtirgandan keyin ekranni qayta yaratish) uchun zanjir bo'ylab yurish
 * kerak. Topilmasa `null` — chaqiruvchi jim o'tkazib yuboradi, ilova esa
 * ishlashda davom etadi.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
