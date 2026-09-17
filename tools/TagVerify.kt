import uz.ovozstudio.app.media.tag.AudioTags
import uz.ovozstudio.app.media.tag.Mp3Tagger
import java.io.File

/**
 * Teg yozuvchi uchun yordamchi: berilgan MP3 faylga teg yozadi.
 *
 * Bu fayl ilovaning bir qismi emas — faqat `bin/verify-tag.sh` ishlatadi.
 * Maqsad: yozgan tegimizni **mustaqil o'quvchi** (ffprobe) bilan
 * tekshirish. Sinov faqat o'z baytlarimizni ko'rsa, «to'g'ri ko'rinadi»da
 * to'xtaydi; ffprobe esa boshqa kod bazasi va u bizning taxminlarimizni
 * takrorlamaydi.
 *
 * Argumentlar: <manba.mp3> <natija.mp3> <muqova.jpg|-> <nom> <ijrochi>
 *              <albom> <yil> <janr> <raqam> <jami>
 */
fun main(args: Array<String>) {
    val source = File(args[0])
    val target = File(args[1])
    val coverFile = args[2]
    val track = args[8].toIntOrNull() ?: 0
    val total = args[9].toIntOrNull() ?: 0

    val cover = if (coverFile == "-") null else coverFile.let { path ->
        File(path).takeIf { it.exists() }?.readBytes()
    }

    val tags = AudioTags(
        title = args[3],
        artist = args[4],
        album = args[5],
        year = args[6],
        genre = args[7],
        track = track,
        trackTotal = total,
        cover = cover,
        coverMime = "image/jpeg",
    )

    target.delete()
    Mp3Tagger.write(source, target, tags)

    // Natijani shu yerda ham tekshiramiz: ffprobe topa olmagan teg
    // «umuman yozilmagan» bo'lishi mumkin, o'lcham esa buni ajratadi.
    println("teg=${Mp3Tagger.id3v2Size(target)} ovoz=${Mp3Tagger.audioEnd(target) - Mp3Tagger.id3v2Size(target)}")
}
