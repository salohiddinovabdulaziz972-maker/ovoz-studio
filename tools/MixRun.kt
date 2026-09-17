import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.mix.AudioMixer
import uz.ovozstudio.app.media.mix.MixTrack
import uz.ovozstudio.app.media.mix.WavMixSource
import java.io.File
import kotlin.system.exitProcess

/**
 * Aralashmani faylga yozadigan probe — mustaqil tekshiruv uchun.
 *
 * Bu alohida dastur, chunki `bin/verify-mix.sh` ilovaning **butun** ekran
 * qatlamini ko'tarmasligi kerak: faqat mikser va uning bog'liqliklari
 * yig'iladi. Probe hech narsani o'lchamaydi va tekshirmaydi — u shunchaki
 * ishni bajaradi va natijani faylga yozadi. O'lchovni ffmpeg va python
 * bajaradi (ilovadan tashqarida).
 *
 * Ishlatish:
 *
 *     java MixRunKt --out natija.wav [--master 1.0] \
 *         --track fayl.wav:balandlik_db:panorama:siljish_ms [...]
 */
fun main(args: Array<String>) {
    var out: File? = null
    var master = 1f
    val inputs = mutableListOf<AudioMixer.Input>()
    val files = mutableListOf<File>()

    var index = 0
    while (index < args.size) {
        val key = args[index]
        when (key) {
            "--out" -> out = File(args[++index])
            "--master" -> master = args[++index].toFloat()
            "--track" -> {
                val parts = args[++index].split(":")
                val file = File(parts[0])
                val gainDb = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                val pan = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                val offsetMs = parts.getOrNull(3)?.toLongOrNull() ?: 0L
                // Nomlar bilan: `MixTrack` maydonlari orasiga yangisi
                // qo'shilsa, pozitsiya bo'yicha chaqiruv jimgina siljib
                // ketardi (bir marta shunday bo'ldi).
                inputs += AudioMixer.Input(
                    WavMixSource(file),
                    MixTrack(name = file.name, file = file.name, gainDb = gainDb, pan = pan, offsetMs = offsetMs),
                )
                files += file
            }
            else -> {
                System.err.println("Noma'lum argument: $key")
                exitProcess(2)
            }
        }
        index++
    }

    val destination = out ?: run {
        System.err.println("--out berilmagan")
        exitProcess(2)
    }

    val problem = AudioMixer.validate(inputs)
    if (problem != null) {
        System.err.println("XATO: $problem")
        exitProcess(3)
    }

    inputs.forEach { println("yol=${it.track.name}") }

    val rate = AudioMixer.outputSampleRate(inputs)
    // Bit chuqurligi manbalarning eng chuquri bo'yicha: 24-bit manbani
    // 16-bitga tushirish aralashmaning o'zida yo'qotish bo'lardi.
    val depth = files
        .mapNotNull { runCatching { BitDepth.of(WavFile.readInfo(it).bitsPerSample) }.getOrNull() }
        .maxByOrNull { it.bits }
        ?: BitDepth.BIT_16

    try {
        WavWriter(destination, rate, AudioMixer.OUTPUT_CHANNELS, depth).use { writer ->
            val result = AudioMixer.render(
                inputs = inputs,
                masterGain = master,
                sink = AudioMixer.Sink { samples, frames -> writer.write(samples, frames) },
            )
            println("kadr=${result.frames}")
            println("choqqi=${result.peakBefore}")
            println("koeffitsient=${result.appliedGain}")
            println("chastota=$rate")
            println("kanal=${AudioMixer.OUTPUT_CHANNELS}")
            println("chuqurlik=${depth.bits}")
        }
    } finally {
        inputs.forEach { runCatching { it.source.close() } }
    }
}
