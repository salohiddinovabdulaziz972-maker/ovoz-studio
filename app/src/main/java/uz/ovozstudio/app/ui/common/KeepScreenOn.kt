package uz.ovozstudio.app.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Eshitish davomida ekran o'chib qolmasin.
 *
 * Nima uchun kerak: audio uzun bo'lishi mumkin (kitob, ma'ruza), foydalanuvchi
 * esa tinglab o'tiradi va ekranga tegmaydi. Ekran o'chsa, ekran o'quvchi
 * bilan boshqarish ham qiyinlashadi — foydalanuvchi qurilmani qayta uyg'otib,
 * ilovaga qaytishi kerak bo'ladi.
 *
 * Faqat [active] rost bo'lganda ushlanadi: tinglash tugagach bayroq olinadi
 * va batareya behuda sarflanmaydi.
 */
@Composable
fun KeepScreenOn(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active, context) {
        val window = context.findActivity()?.window
        if (active) window?.addFlags(FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(FLAG_KEEP_SCREEN_ON) }
    }
}

/**
 * Composable konteksti ichidan `Activity` ni topadi.
 *
 * `LocalContext` har doim ham `Activity` bo'lavermaydi (masalan, mavzu
 * o'ramlari kontekstni o'raydi), shuning uchun o'ramlar ichidan qidiriladi.
 */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** `WindowManager.LayoutParams` dan olingan bayroq; importni qisqartirish uchun alohida. */
private const val FLAG_KEEP_SCREEN_ON = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
