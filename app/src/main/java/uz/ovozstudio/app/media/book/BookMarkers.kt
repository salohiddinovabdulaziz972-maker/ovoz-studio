package uz.ovozstudio.app.media.book

/** Kitob ichidagi belgi: «shu yerdan falon narsa boshlanadi». */
data class BookMarker(
    val title: String,
    /** Butun audio ichidagi boshlanish kadri. */
    val startFrame: Long,
    val sampleRate: Int,
) {
    /** Boshlanish vaqti millisekundlarda. */
    val startMs: Long
        get() = if (sampleRate <= 0) 0L else startFrame * 1000L / sampleRate
}

/**
 * Bob ichidagi belgilarni yasaydi va ularni CUE fayliga aylantiradi.
 *
 * Nega CUE: MP3 ichiga bob belgilarini yozish (ID3 `CHAP`) hali qilinmagan,
 * CUE esa o'sha ma'noni **bugun** beradi — uni deyarli hamma pleyer o'qiy
 * oladi va u oddiy matn, ya'ni tekshirish oson.
 */
object BookMarkers {

    /**
     * Belgi sarlavhasining chegarasi. Uzun matn pleyer oynasida kesilib
     * ketadi; belbog'ning ma'nosi «qaysi bob, qayerda» — butun paragraf
     * emas.
     */
    const val MAX_TITLE_CHARS = 60

    private const val CUE_FRAMES_PER_SECOND = 75

    /**
     * Birlashtirilgan bob audio'sidan belgilar ro'yxatini yasaydi.
     *
     * Birinchi belgi — faylning boshidan (bob sarlavhasi), keyingilari esa
     * har bir bo'lak boshlanishidan. Ya'ni belgilar soni bo'laklar soniga
     * teng: har bir bo'lak — tinglovchi sakrab o'tishi mumkin bo'lgan mantiqiy
     * bo'lak.
     */
    fun ofChapter(joined: JoinedAudio, plan: BookChapterPlan): List<BookMarker> {
        if (joined.info.sampleRate <= 0) return emptyList()

        return joined.parts.mapIndexed { i, part ->
            val utterance = plan.utterances.getOrNull(i)
            val title = when {
                utterance == null -> fallbackTitle(plan, i)
                utterance.isTitle -> utterance.text.ifBlank { plan.title }
                else -> shorten(utterance.text).ifBlank { fallbackTitle(plan, i) }
            }
            BookMarker(
                title = title,
                startFrame = part.startFrame,
                sampleRate = joined.info.sampleRate,
            )
        }
    }

    /**
     * Belgilarni CUE fayli matniga aylantiradi.
     *
     * Format qat'iy: vaqt `mm:ss:ff` ko'rinishida, oxirgi juftlik — sekundning
     * yetmish beshdan bir ulushi. Pleyerlar shu ko'rinishni kutadi; boshqa
     * ko'rinishda fayl jimgina o'qilmay qolardi.
     */
    fun cueSheet(albumTitle: String, fileName: String, markers: List<BookMarker>): String {
        val out = StringBuilder()
        out.append("REM Ovoz Studio tomonidan yaratilgan\n")
        out.append("TITLE \"").append(escape(albumTitle)).append("\"\n")
        out.append("FILE \"").append(escape(fileName)).append("\" MP3\n")
        for ((i, marker) in markers.withIndex()) {
            out.append("  TRACK ").append(pad2((i + 1).toLong())).append(" AUDIO\n")
            out.append("    TITLE \"").append(escape(marker.title)).append("\"\n")
            out.append("    INDEX 01 ").append(cueTime(marker)).append('\n')
        }
        return out.toString()
    }

    /** `mm:ss:ff` — CUE'ning vaqt ko'rinishi. */
    fun cueTime(marker: BookMarker): String {
        val totalMs = marker.startMs.coerceAtLeast(0)
        val minutes = totalMs / 60_000
        val seconds = totalMs % 60_000 / 1000
        val frames = totalMs % 1000 * CUE_FRAMES_PER_SECOND / 1000
        return "${pad2(minutes)}:${pad2(seconds)}:${pad2(frames)}"
    }

    /**
     * Qo'shtirnoq CUE'da maxsus belgi va uni ekranlash yo'li yo'q —
     * shuning uchun almashtiriladi. Aks holda sarlavha faylni buzib,
     * keyingi qatorlar ham matn ichiga tushib ketardi.
     */
    private fun escape(value: String): String =
        value.replace('"', '\'').replace('\n', ' ').replace('\r', ' ')

    /** Ko'p bo'shliqni bitta qilib, chegaradan uzunini qisqartiradi. */
    private fun shorten(text: String): String {
        val collapsed = text.split(' ', '\n', '\t', '\r')
            .filter { it.isNotEmpty() }
            .joinToString(" ")
        return if (collapsed.length <= MAX_TITLE_CHARS) {
            collapsed
        } else {
            collapsed.take(MAX_TITLE_CHARS).trimEnd() + "…"
        }
    }

    private fun fallbackTitle(plan: BookChapterPlan, index: Int): String =
        plan.title.ifBlank { "${index + 1}" }

    private fun pad2(value: Long): String = value.toString().padStart(2, '0')
}
