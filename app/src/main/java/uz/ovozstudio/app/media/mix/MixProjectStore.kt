package uz.ovozstudio.app.media.mix

import uz.ovozstudio.app.util.AtomicFileWriter
import java.io.File
import java.util.Properties

/**
 * Loyihani faylga saqlaydi va qaytaradi.
 *
 * Saqlash — `java.util.Properties`: bu kichik sozlamalar to'plami, baza yoki
 * JSON kutubxonasi ortiqcha bo'lardi, `Properties` esa o'zi qochirish
 * (escaping) va bo'shliqli qiymatlarni to'g'ri o'qiydi.
 *
 * Yo'llar **tartibi bilan** saqlanadi (`0.track`, `1.track` …): aralashmada
 * tartib ma'noga ega, ya'ni uni yo'qotib bo'lmaydi.
 *
 * Buzuq fayl ilovani yiqitmaydi: o'qib bo'lmagan yo'l tashlab ketiladi va
 * qolgani ishlayveradi. Loyiha — bu sozlamalar to'plami, u uchun
 * foydalanuvchini xato oynasi bilan kutib olish noto'g'ri bo'lardi.
 */
class MixProjectStore(private val file: File) {

    fun save(project: MixProject) {
        val properties = Properties()
        properties.setProperty(KEY_MASTER, project.masterGainDb.toString())
        properties.setProperty(KEY_COUNT, project.tracks.size.toString())
        project.tracks.forEachIndexed { index, track ->
            properties.setProperty("$index.$KEY_NAME", track.name)
            properties.setProperty("$index.$KEY_FILE", track.file)
            properties.setProperty("$index.$KEY_GAIN", track.gainDb.toString())
            properties.setProperty("$index.$KEY_PAN", track.pan.toString())
            properties.setProperty("$index.$KEY_OFFSET", track.offsetMs.toString())
            properties.setProperty("$index.$KEY_MUTED", track.muted.toString())
            properties.setProperty("$index.$KEY_SOLO", track.solo.toString())
        }
        // Har o'zgarishda darhol saqlanadi, ya'ni yozish paytida uzilish
        // ehtimoli kichik emas. Vaqtinchalik fayl + `rename`: uzilsa yoki
        // disk to'lsa, avvalgi loyiha butun qoladi (oddiy `outputStream()`
        // faylni avval bo'shatardi va butun loyiha yo'qolardi).
        AtomicFileWriter.write(file) { output ->
            properties.store(output, "OvozStudio aralashma loyihasi")
        }
    }

    /** Fayl bo'lmasa yoki o'qilmasa — bo'sh loyiha. */
    fun load(): MixProject {
        if (!file.exists()) return MixProject()
        val properties = Properties()
        val loaded = runCatching { file.inputStream().use { properties.load(it) } }.isSuccess
        if (!loaded) return MixProject()

        // `tracks=2000000000` yozilgan buzuq fayl millionlab bo'sh qidiruv
        // bilan ochilishni osib qo'yardi. Haqiqiy loyihada MAX_TRACKS tadan
        // ortiq yo'l bo'lmaydi; zaxira chegara bo'shliqlar uchun.
        val count = (properties.getProperty(KEY_COUNT)?.toIntOrNull() ?: return MixProject())
            .coerceIn(0, MAX_SCANNED_INDEXES)
        val tracks = (0 until count).mapNotNull { index -> readTrack(properties, index) }
        val master = properties.getProperty(KEY_MASTER)?.toFloatOrNull() ?: 0f
        return MixProject(
            tracks = tracks.take(MixProject.MAX_TRACKS),
            masterGainDb = master.coerceIn(MixTrack.MIN_GAIN_DB, MixTrack.MAX_GAIN_DB),
        )
    }

    /**
     * Bitta yo'lni o'qiydi; nomi bo'lmasa — `null`.
     *
     * Sonlar buzuq bo'lsa ham yo'l tashlanmaydi: nolga tushadi. Sabab —
     * yo'lning o'zi (nomi) muhimroq, uni yo'qotish foydalanuvchi uchun
     * sozlamaning bir sonidan ko'ra qimmatroq.
     */
    private fun readTrack(properties: Properties, index: Int): MixTrack? {
        val name = properties.getProperty("$index.$KEY_NAME")?.takeIf { it.isNotBlank() } ?: return null
        return MixTrack(
            name = name,
            file = properties.getProperty("$index.$KEY_FILE").orEmpty(),
            gainDb = (properties.getProperty("$index.$KEY_GAIN")?.toFloatOrNull() ?: 0f)
                .coerceIn(MixTrack.MIN_GAIN_DB, MixTrack.MAX_GAIN_DB),
            pan = (properties.getProperty("$index.$KEY_PAN")?.toFloatOrNull() ?: 0f).coerceIn(-1f, 1f),
            offsetMs = (properties.getProperty("$index.$KEY_OFFSET")?.toLongOrNull() ?: 0L)
                .coerceAtLeast(0L),
            muted = properties.getProperty("$index.$KEY_MUTED")?.toBooleanStrictOrNull() ?: false,
            solo = properties.getProperty("$index.$KEY_SOLO")?.toBooleanStrictOrNull() ?: false,
        )
    }

    private companion object {
        /** `tracks=` qiymati shundan katta bo'lsa, shuncha indeksgina ko'riladi. */
        const val MAX_SCANNED_INDEXES = 256
        const val KEY_MASTER = "master"
        const val KEY_COUNT = "tracks"
        const val KEY_NAME = "name"
        const val KEY_FILE = "file"
        const val KEY_GAIN = "gain"
        const val KEY_PAN = "pan"
        const val KEY_OFFSET = "offset"
        const val KEY_MUTED = "muted"
        const val KEY_SOLO = "solo"
    }
}
