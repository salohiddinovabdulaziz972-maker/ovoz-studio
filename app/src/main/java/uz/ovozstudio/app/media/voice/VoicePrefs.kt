package uz.ovozstudio.app.media.voice

import android.content.Context

/**
 * O'qish sozlamalari: qaysi dvigatel, qaysi ovoz, qanday tezlik.
 *
 * Bular foydalanuvchi bir marta tanlab, keyin unutadigan narsalar: har safar
 * hujjat ochilganda Sardor yoki Madinani qayta izlash — ekran o'quvchi bilan
 * ishlovchi uchun ortiqcha mehnat.
 *
 * Bo'sh qiymat («tanlanmagan») `null` bilan ifodalanadi.
 */
class VoicePrefs(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Tanlangan ovoz dvigatelining paket nomi; `null` — tizim dvigateli. */
    var enginePackage: String?
        get() = prefs.getString(KEY_ENGINE, null)
        set(value) {
            prefs.edit().putString(KEY_ENGINE, value).apply()
        }

    /** Tanlangan ovozning identifikatori; `null` — avtomatik. */
    var voiceId: String?
        get() = prefs.getString(KEY_VOICE, null)
        set(value) {
            prefs.edit().putString(KEY_VOICE, value).apply()
        }

    /** O'qish tezligi (1.0 — odatiy). */
    var rate: Float
        get() = prefs.getFloat(KEY_RATE, DEFAULT_RATE)
        set(value) {
            prefs.edit().putFloat(KEY_RATE, value).apply()
        }

    private companion object {
        const val FILE_NAME = "ovoz_sozlamalari"
        const val KEY_ENGINE = "dvigatel"
        const val KEY_VOICE = "ovoz"
        const val KEY_RATE = "tezlik"
        const val DEFAULT_RATE = 1.0f
    }
}
