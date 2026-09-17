import uz.ovozstudio.app.media.dsp.NoiseReducer
import java.io.File
import java.util.Locale

/**
 * `bin/verify-noise.sh` uchun kodsiz drayver.
 *
 * Vazifasi ikkita: ilovaning shovqin tozalash amalini chaqirish va uning
 * **o'zi e'lon qilgan** natijani chop etish. O'lchov bu yerda yo'q:
 * shovqin darajasini ham, ohang amplitudasini ham tashqi vosita (python3)
 * WAV fayllarning o'zidan o'lchaydi. Shu tufayli ilova «tozaladim» deb
 * xato aytgan bo'lsa, bu darhol ko'rinadi.
 *
 * Ishlatilishi:
 *   NoiseVerifyKt <manba.wav> <natija.wav> <boshlanishMs> <oxirMs> <kuch> <qoldiqDb> [silliqlash]
 *
 * `boshlanishMs`/`oxirMs` — shovqin namunasi olinadigan oraliq, `kuch` —
 * ayirish koeffitsienti (1.0…4.0), `qoldiqDb` — qoldiriladigan quvvat,
 * `silliqlash` — kadrlar orasidagi koeffitsient silliqlash darajasi.
 *
 * Silliqlash alohida argument: nazariy model uni hisobga olmaydi (model
 * «har bir kadrda koeffitsient shu kadrning quvvatidan hisoblanadi» deb
 * oladi), shuning uchun modelga qiyoslash `silliqlash = 0` da o'tkaziladi,
 * silliqlashning o'zi esa alohida o'lchanadi.
 */
fun main(args: Array<String>) {
    if (args.size < 6) {
        System.err.println(
            "Ishlatilishi: NoiseVerifyKt <manba> <natija> <boshlanishMs> <oxirMs> <kuch> <qoldiqDb> [silliqlash]",
        )
        return
    }

    val settings = NoiseReducer.Settings(
        noiseStartMs = args[2].toLong(),
        noiseEndMs = args[3].toLong(),
        strength = args[4].toDouble(),
        floorDb = args[5].toDouble(),
        smoothing = if (args.size > 6) args[6].toDouble() else NoiseReducer.DEFAULT_SMOOTHING,
    )
    val result = NoiseReducer.apply(File(args[0]), File(args[1]), settings)

    println("frames=${result.info.frames}")
    println("rate=${result.info.sampleRate}")
    println("channels=${result.info.channels}")
    // Nuqta har doim nuqta bo'lib qolishi kerak: skript bu sonni awk bilan
    // o'qiydi, vergulli o'nlik kasr esa uni buzardi.
    println("drop=" + String.format(Locale.US, "%.6f", result.noiseDropDb))
}
