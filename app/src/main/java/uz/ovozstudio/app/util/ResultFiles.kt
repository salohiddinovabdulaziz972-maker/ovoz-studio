package uz.ovozstudio.app.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Tayyor faylni foydalanuvchi tanlagan joyga nusxalash.
 *
 * Ilova natijani o'z papkasida saqlamaydi: «Saqlash» tizimning «Fayl sifatida
 * saqlash» oynasini ochadi, foydalanuvchi joyni o'zi tanlaydi (ruxsat kerak
 * emas), bu funksiya esa tayyor faylni shu joyga ko'chiradi.
 */
object ResultFiles {

    /**
     * [source] ni [destination] ga nusxalaydi. `false` — nusxalanmadi (joy
     * tugagan, fayl provayderi rad etdi, tanlangan joy o'chirilgan).
     *
     * Ichida fayl o'qish-yozish bor: faqat fon oqimidan chaqiriladi.
     */
    fun copyTo(context: Context, source: File, destination: Uri): Boolean = try {
        // «wt» — mavjud faylning ustiga yozganda eski mazmun qolib ketmasin.
        val output = context.contentResolver.openOutputStream(destination, "wt")
        if (output == null) {
            false
        } else {
            output.use { sink ->
                source.inputStream().use { input -> input.copyTo(sink) }
            }
            true
        }
    } catch (error: Exception) {
        false
    }
}
