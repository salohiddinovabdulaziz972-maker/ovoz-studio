package uz.ovozstudio.app.media.book

import uz.ovozstudio.app.media.doc.Chapter
import uz.ovozstudio.app.media.voice.TextChunker

/**
 * Bob ichidagi bitta bo'lak — sintezatorga **bir marta** beriladigan matn.
 *
 * Nega bo'laklarga bo'linadi: sintezator bitta chaqiruvda cheklangan uzunlikni
 * qabul qiladi ([android.speech.tts.TextToSpeech.getMaxSpeechInputLength]),
 * chegaradan uzun matnni esa jimgina tashlab ketadi. Kitob bobining o'rtasida
 * jimlik — eng yomon xato turi: foydalanuvchi buni «kitob shu yerda tugadi»
 * deb tushunadi.
 *
 * [startOffset] va [endOffset] — bob **matni** ichidagi belgi ofsetlari
 * (sarlavha ularga kirmaydi). Ular kerak: o'qish to'xtatilganda «qayerda
 * qoldim» degan savolga javob shu ofsetlar orqali beriladi.
 */
data class BookUtterance(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    /** Bu bo'lak bob sarlavhasimi (matn emas, e'lon). */
    val isTitle: Boolean,
)

/** Bitta bobning ovozga aylantirish rejasi. */
data class BookChapterPlan(
    /** Manba ro'yxatidagi o'rni — fayl nomi va belgilar shunga tayanadi. */
    val index: Int,
    val title: String,
    val utterances: List<BookUtterance>,
) {
    /** Ovozga aylanadigan matn hajmi — sarlavha e'loni hisobga kirmaydi. */
    val charCount: Int
        get() = utterances.filterNot { it.isTitle }.sumOf { it.text.length }

    val isEmpty: Boolean get() = utterances.isEmpty()
}

/** Butun kitobning rejasi. */
data class BookPlan(
    val chapters: List<BookChapterPlan>,
    /** Bo'lak uzunligi chegarasi — keyin tekshirish uchun saqlanadi. */
    val maxChars: Int,
) {
    val utteranceCount: Int get() = chapters.sumOf { it.utterances.size }
    val charCount: Int get() = chapters.sumOf { it.charCount }
}

/**
 * Boblarni sintezator bo'laklariga bo'ladi.
 *
 * Reja — sof hisob-kitob: hech narsa o'qilmaydi va yozilmaydi. Shu sababli
 * uni sinab ko'rish uchun sintezator ham, qurilma ham kerak emas.
 */
object BookPlanner {

    /**
     * Bob sarlavhasi ovoz bilan e'lon qilinadimi.
     *
     * Ha: ekran o'quvchi foydalanuvchisi uchun «qaysi bobdaman» degan savol
     * eng muhimi, kitobni esa quloq bilan tinglaydi — sarlavhani eshitmasa,
     * bob chegarasini faqat jimlikdan bilib olardi.
     */
    const val DEFAULT_ANNOUNCE_TITLES = true

    fun plan(
        chapters: List<Chapter>,
        maxChars: Int = TextChunker.DEFAULT_MAX_CHARS,
        announceTitles: Boolean = DEFAULT_ANNOUNCE_TITLES,
    ): BookPlan {
        val plans = chapters.mapIndexed { index, chapter ->
            val utterances = ArrayList<BookUtterance>()

            // Bo'sh bob (matni yo'q, faqat sarlavha) ovozsiz qolib ketmasligi
            // kerak: fayl yaratilmay qolsa, boblar tartibi ham siljib ketardi.
            if (chapter.title.isNotBlank() && (announceTitles || chapter.text.isBlank())) {
                utterances += BookUtterance(
                    text = chapter.title,
                    startOffset = 0,
                    endOffset = 0,
                    isTitle = true,
                )
            }

            if (chapter.text.isNotBlank()) {
                for (chunk in TextChunker.split(chapter.text, maxChars)) {
                    utterances += BookUtterance(
                        text = chunk.text,
                        startOffset = chunk.startOffset,
                        endOffset = chunk.endOffset,
                        isTitle = false,
                    )
                }
            }

            BookChapterPlan(index = index, title = chapter.title, utterances = utterances)
        }
        return BookPlan(chapters = plans, maxChars = maxChars)
    }
}
