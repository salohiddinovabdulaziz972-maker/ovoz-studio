import uz.ovozstudio.app.media.dsp.StemSeparator
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * `bin/verify-stem.sh` uchun kodsiz drayver.
 *
 * Vazifasi faqat amalni chaqirish va **ilovaning o'zi aytgan** natijani
 * chop etish. O'lchov bu yerda yo'q: na rekonstruksiya xatosi, na
 * chastotalar nisbati shu faylda hisoblanmaydi — ularni tashqi vosita
 * (python3) WAV fayllarning o'zidan o'lchaydi. Shu tufayli ilova xato
 * natija bersa yoki xatoni yashirsa, bu darhol ko'rinadi.
 *
 * Xato ham **chiqish natijasi** sifatida chop etiladi (`error=...`),
 * jarayon esa 0 bilan tugaydi: skript xato matnini o'qiy olishi kerak,
 * «yiqildi» degan kabardan esa matn ko'rinmaydi.
 *
 * Ishlatilishi:
 *   StemVerifyKt <manba.wav> <vokal.wav> <cholg'u.wav> <rejim> <kuch>
 *
 * `rejim` — `split` yoki `remove`; `kuch` — ajratish kuchi (0.5…4.0).
 */
fun main(args: Array<String>) {
    if (args.size < 5) {
        System.err.println(
            "Ishlatilishi: StemVerifyKt <manba> <vokal> <cholg'u> <split|remove> <kuch>",
        )
        return
    }

    val mode = when (args[3]) {
        "split" -> StemSeparator.Mode.SPLIT
        "remove" -> StemSeparator.Mode.REMOVE_VOCALS
        else -> {
            System.err.println("Noma'lum rejim: ${args[3]}")
            return
        }
    }
    val settings = StemSeparator.Settings(mode = mode, strength = args[4].toDouble())

    val result = try {
        StemSeparator.apply(File(args[0]), File(args[1]), File(args[2]), settings)
    } catch (error: IOException) {
        // Xato matni — tekshiriladigan natijaning bir qismi.
        println("error=${error.message}")
        return
    }

    println("error=")
    println("frames=${result.info.frames}")
    println("rate=${result.info.sampleRate}")
    println("channels=${result.info.channels}")
    println("bits=${result.info.bitsPerSample}")
    // Nuqta har doim nuqta bo'lib qolishi kerak: skript bu sonni awk bilan
    // o'qiydi, vergulli o'nlik kasr esa uni buzardi.
    println("sideToMidDb=" + String.format(Locale.US, "%.6f", result.sideToMidDb))
}
