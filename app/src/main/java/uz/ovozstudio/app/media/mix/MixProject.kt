package uz.ovozstudio.app.media.mix

/** Aralashma loyihasi: yo'llar ro'yxati va umumiy balandlik. */
data class MixProject(
    val tracks: List<MixTrack> = emptyList(),
    /** Umumiy balandlik, desibelda — butun aralashmaga qo'llanadi. */
    val masterGainDb: Float = 0f,
) {

    val masterGain: Float get() = dbToGain(masterGainDb)

    val isEmpty: Boolean get() = tracks.isEmpty()

    companion object {
        /**
         * Yo'llar sonining chegarasi. Telefonda sakkizdan ko'p yo'lni bir
         * ekranda ko'rsatib bo'lmaydi, xotira ham cheklangan — va bu
         * chegaradan keyin foydalanuvchi baribir qaysi yo'lni eshitayotganini
         * yo'qotadi.
         */
        const val MAX_TRACKS = 8
    }
}

/**
 * Loyihani tahrirlash — **orqaga qaytarish** bilan.
 *
 * Har bir o'zgarish tarixga yoziladi, ya'ni foydalanuvchi xato qilib
 * o'chirib qo'ysa, orqaga qaytishi mumkin. Tarix **butun loyihani**
 * saqlaydi (yo'llar ro'yxati kichik — sakkiztagacha sozlama), shuning uchun
 * «qaysi maydon o'zgargan» degan savol tug'ilmaydi va orqaga qaytarish
 * har doim aniq bir holatga qaytaradi.
 *
 * Bu sinf Android'ga bog'lanmagan: butun mantiq sof JVM'da tekshiriladi,
 * ekranda esa faqat tugmalar qoladi.
 */
class MixEditor(initial: MixProject = MixProject(), private val historyLimit: Int = 30) {

    var project: MixProject = initial
        private set

    private val past = ArrayDeque<MixProject>()
    private val future = ArrayDeque<MixProject>()

    val canUndo: Boolean get() = past.isNotEmpty()

    val canRedo: Boolean get() = future.isNotEmpty()

    /** Yo'l qo'shadi. Chegaradan oshsa `false` — loyiha o'zgarmaydi. */
    fun add(track: MixTrack): Boolean {
        if (project.tracks.size >= MixProject.MAX_TRACKS) return false
        apply(project.copy(tracks = project.tracks + track))
        return true
    }

    /** [index] dagi yo'lni olib tashlaydi. */
    fun remove(index: Int): Boolean {
        if (index !in project.tracks.indices) return false
        apply(project.copy(tracks = project.tracks.filterIndexed { i, _ -> i != index }))
        return true
    }

    /** [index] dagi yo'lni o'zgartiradi; natija eskisiga teng bo'lsa yozilmaydi. */
    fun update(index: Int, transform: (MixTrack) -> MixTrack): Boolean {
        val current = project.tracks.getOrNull(index) ?: return false
        val changed = transform(current)
        if (changed == current) return false
        val tracks = project.tracks.toMutableList()
        tracks[index] = changed
        apply(project.copy(tracks = tracks))
        return true
    }

    /** Umumiy balandlik. */
    fun setMaster(gainDb: Float): Boolean {
        val clamped = gainDb.coerceIn(MixTrack.MIN_GAIN_DB, MixTrack.MAX_GAIN_DB)
        if (clamped == project.masterGainDb) return false
        apply(project.copy(masterGainDb = clamped))
        return true
    }

    /** Butun loyihani almashtiradi (masalan, saqlangan fayldan tiklash). */
    fun reset(next: MixProject) {
        project = next
        past.clear()
        future.clear()
    }

    fun undo(): Boolean {
        val previous = past.removeLastOrNull() ?: return false
        future.addFirst(project)
        project = previous
        return true
    }

    fun redo(): Boolean {
        val next = future.removeFirstOrNull() ?: return false
        past.addLast(project)
        project = next
        return true
    }

    private fun apply(next: MixProject) {
        past.addLast(project)
        // Tarix chegarasi: eng eskisi tushib qoladi. Cheksiz o'sish uzoq
        // ishlashda xotirani sekin-asta yeb qo'yardi.
        while (past.size > historyLimit) past.removeFirst()
        future.clear()
        project = next
    }
}
