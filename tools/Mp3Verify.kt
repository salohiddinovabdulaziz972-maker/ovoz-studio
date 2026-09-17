import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.format.AudioCodec
import uz.ovozstudio.app.media.format.AudioContainer
import uz.ovozstudio.app.media.format.AudioFormat
import uz.ovozstudio.app.media.format.ExportOutcome
import uz.ovozstudio.app.media.format.FormatPreservingExporter
import java.io.File

/**
 * MP3 tekshiruvi uchun yordamchi: sinov WAV'ini yaratadi va uni
 * `FormatPreservingExporter` orqali MP3 qilib kodlaydi.
 *
 * Bu fayl ilovaning bir qismi emas — faqat `bin/verify-mp3.sh` ishlatadi.
 * Maqsad: kodlovchini **mustaqil dekoder** (ffmpeg) bilan tekshirish.
 * Sinovning o'zi JVM ichida bo'lsa, u o'z-o'zini tekshirishga aylanadi va
 * "to'g'ri ko'rinadi" da to'xtaydi.
 *
 * Argumentlar: <wav> <mp3> <kadrlar> <kanal> <bit chuqurligi> <kbps|0> [chastota]
 * `kbps = 0` — o'zgaruvchan bit tezligi (VBR).
 * Chastota berilmasa — 44100. Kitob esa sintezator bergan chastotada
 * yoziladi, u ko'pincha past bo'ladi: shuning uchun bu yerda u sozlanadi.
 */
fun main(args: Array<String>) {
    val wav = File(args[0])
    val mp3 = File(args[1])
    val frames = args[2].toInt()
    val channels = args[3].toInt()
    val depth = args[4].toInt()
    val kbps = args[5].toInt()
    val rate = args.getOrNull(6)?.toIntOrNull() ?: 44_100
    wav.delete()
    mp3.delete()

    val samples = IntArray(frames * channels)
    val amplitude = (1 shl (depth - 1)) - 1
    for (t in 0 until frames) {
        val value = (Math.sin(2.0 * Math.PI * 440 * t / rate.toDouble()) * amplitude / 3).toInt()
        for (c in 0 until channels) samples[t * channels + c] = if (c == 1) value / 3 else value
    }
    WavWriter(wav, rate, channels, if (depth == 24) BitDepth.BIT_24 else BitDepth.BIT_16)
        .use { it.writeIntegers(samples, frames) }

    val source = AudioFormat(
        container = AudioContainer.MP3,
        codec = AudioCodec.MP3,
        sampleRate = rate,
        channels = channels,
        bitDepth = depth,
        bitrate = if (kbps > 0) kbps * 1000 else null,
    )
    when (val outcome = FormatPreservingExporter(apiLevel = 34).export(wav, source, mp3)) {
        is ExportOutcome.Done -> println("OK ${outcome.frames} kadr ${outcome.format}")
        is ExportOutcome.Unsupported -> {
            System.err.println("QO'LLAB-QUVVATLANMAYDI: ${outcome.reason}")
            kotlin.system.exitProcess(1)
        }
    }
}
