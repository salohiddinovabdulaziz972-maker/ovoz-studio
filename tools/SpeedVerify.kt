import uz.ovozstudio.app.media.dsp.SpeedPitch
import java.io.File

/**
 * `bin/verify-speed.sh` uchun kodsiz drayver.
 *
 * Vazifasi faqat bitta: ilovaning tezlik/ohang amalini chaqirib, natijani
 * faylga yozish. O'lchov bu yerda **yo'q** — davomiylikni ham, chastotani
 * ham tashqi vosita (python3) WAV faylning o'zidan o'lchaydi. Shu tufayli
 * drayverda ham, DSP'da ham bir xil xato bo'lsa, tekshiruv o'z-o'zini
 * oqlamaydi.
 *
 * Ishlatilishi:
 *   SpeedVerifyKt <manba.wav> <natija.wav> <tezlik> <yarim-ton>
 *
 * `tezlik` 2.0 bo'lsa fayl ikki marta tez eshitiladi (uzunlik ikki barobar
 * qisqa), `yarim-ton` 12 bo'lsa ohang bir oktava ko'tariladi (uzunlik
 * tegmaydi).
 */
fun main(args: Array<String>) {
    if (args.size < 4) {
        System.err.println("Ishlatilishi: SpeedVerifyKt <manba> <natija> <tezlik> <yarim-ton>")
        return
    }

    val settings = SpeedPitch.Settings(
        speed = args[2].toDouble(),
        semitones = args[3].toDouble(),
    )
    val result = SpeedPitch.apply(File(args[0]), File(args[1]), settings)
    println("frames=${result.info.frames}")
    println("rate=${result.info.sampleRate}")
}
