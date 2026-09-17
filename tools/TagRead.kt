import uz.ovozstudio.app.media.tag.Id3v2Reader
import java.io.File

/**
 * Teg o'quvchi uchun yordamchi: fayldagi tegni o'qib, ekranga chiqaradi.
 *
 * Ilovaning bir qismi emas — faqat `bin/verify-tag.sh` ishlatadi. Maqsad
 * ikki tomonlama:
 *
 *   1. **Boshqa dastur** yozgan tegni o'qiy olamizmi (ffmpeg yozgan fayl);
 *   2. o'zimiz yozgan tegni o'qib, qiymatlar faylga **haqiqatan** tushganini
 *      ko'ramiz — yozuvchi «yozdim» deyishi dalil emas.
 *
 * Chiqish formati oddiy `kalit=qiymat` qatorlari: uni Python tomonda
 * taqqoslash oson.
 */
fun main(args: Array<String>) {
    val tags = Id3v2Reader.read(File(args[0]))
    println("nom=${tags.title}")
    println("ijrochi=${tags.artist}")
    println("albom=${tags.album}")
    println("yil=${tags.year}")
    println("janr=${tags.genre}")
    println("raqam=${tags.trackText.orEmpty()}")
    println("muqova=${tags.cover?.size ?: 0}")
}
