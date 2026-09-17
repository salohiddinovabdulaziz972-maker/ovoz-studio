import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.dsp.EqBandCount
import uz.ovozstudio.app.media.dsp.EqBands
import uz.ovozstudio.app.media.dsp.EqPreset
import uz.ovozstudio.app.media.dsp.Equalizer
import java.io.File

/**
 * `bin/verify-eq.sh` uchun kodsiz drayver.
 *
 * Vazifasi faqat bitta: ilovaning ekvalayzerini chaqirib, natijani faylga
 * yozish va ishlatilgan polosalarni yoniga yozib qo'yish. O'lchov bu yerda
 * yo'q — uni ffmpeg bajaradi. Shu tufayli drayverda ham, ekvalayzerda ham
 * xato bo'lsa, tekshiruv o'z-o'zini oqlamaydi: natijani boshqa kod bazasi
 * o'lchaydi.
 *
 * Ishlatilishi:
 *   EqVerifyKt <manba.wav> <natija.wav> <sozlama> [past-kesish-Hz]
 *
 * [sozlama] uch xil bo'ladi:
 *   `1000:+6`      — 1000 Hz polosasini +6 dB ga ko'tar
 *   `preset:VOICE` — tayyor profil
 *   `flat`         — hech narsa (xato beradi, nazorat uchun)
 *
 * Natija fayli yonida `<natija>.chain` yoziladi: har bir satrda bitta
 * ishlaydigan polosa `chastota:kuchaytirish:kenglik` ko'rinishida. Bu
 * ffmpeg'ning o'z `equalizer` filtri bilan bir xil kaskadni yasash uchun
 * kerak — o'sha kaskadning chiqishi ilovaniki bilan qiyoslanadi.
 */
fun main(args: Array<String>) {
    if (args.size < 3) {
        System.err.println("Ishlatilishi: EqVerifyKt <manba> <natija> <sozlama> [past-kesish]")
        return
    }

    val source = File(args[0])
    val destination = File(args[1])
    val setting = args[2]
    val lowCutHz = args.getOrNull(3)?.toIntOrNull() ?: 0

    val info = WavFile.readInfo(source)
    val count = EqBandCount.TEN
    val centers = EqBands.centers(count, info.sampleRate)
    val gains = gains(centers, count, setting, info.sampleRate)

    val settings = Equalizer.Settings(
        bands = centers.indices.map { index ->
            Equalizer.Band(centers[index], gains[index], EqBands.q(count))
        },
        lowCutHz = lowCutHz,
    )

    val result = Equalizer.apply(source, destination, settings)
    println("headroom_db=${result.headroomDb}")

    // Kaskad fayli faqat haqiqatan ishlaydigan polosalardan yasaladi:
    // nol kuchaytirishli polosani ffmpeg'ga ham berishning ma'nosi yo'q.
    //
    // Oxirida albatta yangi qator bo'lishi kerak: uni o'qiydigan `while
    // read` sikli tugallanmagan qatorni tashlab ketadi, natijada eng yuqori
    // polosa jimgina yo'qolardi — tekshiruv esa «ekvalayzer xato» degan
    // xulosaga kelardi.
    val chain = centers.indices
        .filter { gains[it] != 0.0 }
        .joinToString(separator = "\n", postfix = "\n") {
            "${centers[it]}:${gains[it]}:${EqBands.q(count)}"
        }
    File("$destination.chain").writeText(chain)
}

private fun gains(
    centers: List<Double>,
    count: EqBandCount,
    setting: String,
    sampleRate: Int,
): List<Double> = when {
    setting == "flat" -> List(centers.size) { 0.0 }

    setting.startsWith("preset:") -> EqBands.presetGains(
        count,
        EqPreset.valueOf(setting.removePrefix("preset:")),
        sampleRate,
    )

    else -> {
        val parts = setting.split(":")
        require(parts.size == 2) { "Sozlama ko'rinishi: `1000:+6`" }
        val frequency = parts[0].toDouble()
        val gainDb = parts[1].toDouble()
        require(centers.any { it == frequency }) {
            "$frequency Hz oktava jadvalida yo'q: $centers"
        }
        centers.map { if (it == frequency) gainDb else 0.0 }
    }
}
