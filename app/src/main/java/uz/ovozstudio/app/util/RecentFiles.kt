package uz.ovozstudio.app.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ilova orqali ochilgan fayllar ro'yxati (oxirgi ochilganlar).
 *
 * Nima uchun kerak: qurilmada fayl papkalari chuqur va nomlari uzun. Ko'zi
 * ojiz foydalanuvchi uchun har safar papkalar ichida qo'lda yurish — eng
 * charchatadigan qism. Shu ro'yxat bir bosishda qaytaradi.
 *
 * Manzil (`content://…`) saqlanmaydi: tizim bergan havola qurilma qayta
 * ishga tushganda yoki fayl ko'chirilganda kuchini yo'qotadi va ochilmay
 * qoladi. Shu sababli faqat **nom va holat** saqlanadi. Fayl qayta
 * ochilganda tizim o'zi havolani beradi.
 *
 * Ro'yxat odatdagi `SharedPreferences` da turadi: ilova keshini tizim
 * tozalasa ham foydalanuvchi ro'yxati yo'qolmaydi.
 */
class RecentFiles(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Ro'yxatdagi yozuv: fayl nomi va oxirgi ochilgan vaqt. */
    data class Entry(val name: String, val openedAtMs: Long)

    /** Oxirgi ochilgan [limit] ta fayl, eng yangisi birinchi. */
    fun list(limit: Int = MAX_ENTRIES): List<Entry> {
        val raw = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString(NAME, "")
                if (name.isEmpty()) return@mapNotNull null
                Entry(name, item.optLong(AT, 0L))
            }
        }.getOrDefault(emptyList()).take(limit)
    }

    /**
     * Fayl ochilganda chaqiriladi.
     *
     * Bir xil nom ikkinchi marta ochilsa yangi yozuv qo'shilmaydi — yozuv
     * yuqoriga ko'chadi va vaqti yangilanadi. Aks holda ro'yxat bir faylning
     * nusxalari bilan to'lib ketardi.
     */
    fun remember(name: String) {
        if (name.isEmpty()) return
        val now = System.currentTimeMillis()
        val updated = buildList {
            add(Entry(name, now))
            list().filterNot { it.name.equals(name, ignoreCase = true) }.forEach { add(it) }
        }.take(MAX_ENTRIES)
        save(updated)
    }

    /** Nom bo'yicha qidiradi (ro'yxat ichida, katta-kichik harf farqsiz). */
    fun find(query: String): List<Entry> {
        val needle = query.trim()
        if (needle.isEmpty()) return emptyList()
        return list().filter { it.name.contains(needle, ignoreCase = true) }
    }

    fun clear() = prefs.edit().remove(KEY_ENTRIES).apply()

    private fun save(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put(NAME, entry.name)
                    .put(AT, entry.openedAtMs),
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "ovoz_oxirgi_fayllar"
        const val KEY_ENTRIES = "yozuvlar"
        const val NAME = "nom"
        const val AT = "vaqt"

        /** Yigirmata yetarli: undan ko'pi ro'yxatni ovoz bilan yurib bo'lmaydigan holga keltiradi. */
        const val MAX_ENTRIES = 20
    }
}
