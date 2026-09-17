package uz.ovozstudio.app.media.book

import java.io.File

/**
 * Kitobdagi bitta bob — pleyer ko'radigan ko'rinish.
 *
 * [file] — yasalgan MP3, [durationMs] — uning uzunligi, [markers] — shu bob
 * ichidagi belgilar (bo'lak sarlavhalari, vaqt bob boshidan hisoblanadi).
 */
data class PlaylistChapter(
    val title: String,
    val file: File,
    val durationMs: Long,
    val markers: List<BookMarker> = emptyList(),
)

/**
 * Kitobni boblar bo'ylab o'qish tartibi.
 *
 * Bu qatlam ataylab **tovushsiz**: qaysi bob keyin keladi, belgi bo'ylab
 * qanday sakraladi, umumiy vaqt qanday hisoblanadi — hammasi sof mantiq va
 * shu sababli oddiy JVM sinovida tekshiriladi. Telefonda qoladigan qism
 * faqat bitta: faylni ochib, ko'rsatilgan joydan qo'yish.
 *
 * Nega alohida sinf: pleyerning eng ko'p uchraydigan xatosi — kitobning
 * chekkasida noto'g'ri o'tish (oxirgi bobdan keyin birinchi bobga qaytib
 * ketish yoki «keyingi» tugmasi jim qolishi). Buni qo'lda, 300 bobli
 * kitobda hamisha sinab bo'lmaydi.
 */
class BookPlaylist(val chapters: List<PlaylistChapter>) {

    private var index: Int = 0

    val size: Int get() = chapters.size

    val isEmpty: Boolean get() = chapters.isEmpty()

    val currentIndex: Int get() = index

    val current: PlaylistChapter? get() = chapters.getOrNull(index)

    /** Oxirgi bobdami — «keyingi» tugmasi o'chiriladi. */
    val isLast: Boolean get() = index >= chapters.size - 1

    /** Birinchi bobdami. */
    val isFirst: Boolean get() = index <= 0

    /** Butun kitobning uzunligi. */
    val totalDurationMs: Long get() = chapters.sumOf { it.durationMs }

    /**
     * Bobni tanlaydi. Mavjud bo'lmagan raqam e'tiborsiz qoldiriladi —
     * tugma bosilganda holat buzilib qolmasligi kerak.
     */
    fun select(position: Int): PlaylistChapter? {
        if (position !in chapters.indices) return current
        index = position
        return current
    }

    /**
     * Keyingi bobga o'tadi. Oxirgi bobdan keyin `null` qaytaradi va
     * **o'rnida qoladi** — kitob tugadi, qaytadan boshlanmaydi.
     */
    fun advance(): PlaylistChapter? {
        if (isLast) return null
        index += 1
        return current
    }

    /** Oldingi bobga o'tadi. Birinchi bobda `null`. */
    fun rewind(): PlaylistChapter? {
        if (isFirst) return null
        index -= 1
        return current
    }

    /** Shu bobgacha o'tgan vaqt — «kitobda qayerdaman» hisobi uchun. */
    fun elapsedBefore(position: Int): Long {
        if (position <= 0) return 0
        return chapters.take(position.coerceAtMost(chapters.size)).sumOf { it.durationMs }
    }

    /** Joriy bob ichidagi joydan kitob bo'ylab umumiy vaqt. */
    fun bookPositionMs(positionInChapterMs: Long): Long =
        elapsedBefore(index) + positionInChapterMs.coerceAtLeast(0)

    /** Kitob oxirigacha qolgan vaqt. */
    fun remainingMs(positionInChapterMs: Long): Long =
        (totalDurationMs - bookPositionMs(positionInChapterMs)).coerceAtLeast(0)

    /**
     * Joriy bob ichida keyingi belgi. Yo'q bo'lsa `null`.
     *
     * [positionInChapterMs] ga juda yaqin belgi tashlanadi ([EPSILON_MS]):
     * aks holda tugma bosilganda hech narsa o'zgarmagandek ko'rinardi.
     */
    fun nextMarker(positionInChapterMs: Long): BookMarker? =
        current?.markers?.firstOrNull { it.startMs > positionInChapterMs + EPSILON_MS }

    /** Joriy bob ichida oldingi belgi. Yo'q bo'lsa `null`. */
    fun previousMarker(positionInChapterMs: Long): BookMarker? =
        current?.markers?.lastOrNull { it.startMs < positionInChapterMs - EPSILON_MS }

    /**
     * Joriy joy qaysi bo'lak (belgi) ichida — ekranda «hozir nima
     * o'qilyapti» ni ko'rsatish uchun.
     */
    fun markerAt(positionInChapterMs: Long): BookMarker? =
        current?.markers?.lastOrNull { it.startMs <= positionInChapterMs }

    companion object {
        /**
         * Bir lahzali chidam: tugma va pleyer pozitsiyasi orasidagi
         * millisoniyalar farqi belgini «shu yerdaman» deb hisoblamasligi
         * kerak.
         */
        const val EPSILON_MS = 500L
    }
}
