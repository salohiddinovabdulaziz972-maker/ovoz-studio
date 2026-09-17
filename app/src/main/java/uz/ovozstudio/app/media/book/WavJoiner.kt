package uz.ovozstudio.app.media.book

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.format.WavPcmReader
import java.io.File

/** Birlashtirilgan fayldagi bitta manba: qayerdan boshlanadi va qancha davom etadi. */
data class JoinedPart(
    val file: File,
    /** Butun fayl ichidagi boshlanish kadri. */
    val startFrame: Long,
    val frames: Long,
) {
    fun endFrame(): Long = startFrame + frames
}

/** Birlashtirish natijasi. */
data class JoinedAudio(
    val file: File,
    /** Haqiqatda yozilgan fayl sarlavhasidan o'qilgan ma'lumot. */
    val info: WavInfo,
    val parts: List<JoinedPart>,
)

/**
 * Bir nechta WAV bo'lakni bitta faylga qo'shadi.
 *
 * Bo'laklar orasiga jimlik qo'yiladi: sintezator har bir bo'lakni alohida
 * fayl qilib beradi va ular orasida hech qanday pauza yo'q. Pauzasiz
 * qo'shilsa, oxirgi so'z keyingi bo'lakning birinchi so'ziga yopishib,
 * «bir nafasda» o'qilgandek eshitilardi.
 *
 * Namunalar **butun son ko'rinishida** ko'chiriladi ([WavWriter.writeIntegers]):
 * float orqali o'tkazilsa, 24-bit chegarasidagi qiymatlar bir birlikka surilib
 * ketardi.
 */
object WavJoiner {

    /**
     * Bo'laklar orasidagi jimlik. 400 ms — gapiruvdagi tabiiy pauzaga yaqin:
     * undan qisqasi shoshilinch, uzuni esa «fayl buzilganmi?» degan tuyg'u
     * beradi.
     */
    const val DEFAULT_GAP_MS = 400

    private const val READ_FRAMES = 4096

    /**
     * [parts] dagi barcha WAV'larni [output] ga ketma-ket yozadi.
     *
     * Format bir xil bo'lishi shart: hamma bo'lakni bitta sintezator, bitta
     * ovoz beradi. Boshqa format kelsa, uni jimgina qayta hisoblash
     * (resampling) noto'g'ri natija berardi — shuning uchun ochiq xato
     * qaytariladi.
     */
    @Throws(BookAssemblyException::class)
    fun join(parts: List<File>, output: File, gapMs: Int = DEFAULT_GAP_MS): JoinedAudio {
        if (parts.isEmpty()) throw BookAssemblyException("Birlashtirish uchun fayl berilmagan")
        if (gapMs < 0) throw BookAssemblyException("Pauza manfiy bo'lishi mumkin emas: $gapMs")

        val first = WavFile.readInfo(parts.first())
        val depth = BitDepth.of(first.bitsPerSample)
            ?: throw BookAssemblyException(
                "Bit chuqurligi qo'llab-quvvatlanmaydi: ${first.bitsPerSample}",
            )

        val joined = ArrayList<JoinedPart>(parts.size)
        val silenceFrames = first.sampleRate.toLong() * gapMs / 1000L
        val silence = IntArray((silenceFrames * first.channels).toInt())

        WavWriter(output, first.sampleRate, first.channels, depth).use { writer ->
            val buffer = IntArray(READ_FRAMES * first.channels)

            for ((i, file) in parts.withIndex()) {
                WavPcmReader(file).use { reader ->
                    requireSameFormat(first, reader.info, file)

                    // Pauza bo'laklar orasiga qo'yiladi — faylning boshiga emas:
                    // boshidagi jimlik hech narsa demaydi, oxiridagisi esa
                    // keyingi fayl bilan qo'shilib, ikki barobar uzun pauza
                    // bo'lib qolardi.
                    if (i > 0 && silence.isNotEmpty()) {
                        writer.writeIntegers(silence, silenceFrames.toInt())
                    }

                    val start = writer.frames
                    while (true) {
                        val got = reader.read(buffer, READ_FRAMES)
                        if (got <= 0) break
                        writer.writeIntegers(buffer, got)
                    }
                    joined += JoinedPart(file = file, startFrame = start, frames = writer.frames - start)
                }
            }
        }

        return JoinedAudio(file = output, info = WavFile.readInfo(output), parts = joined)
    }

    private fun requireSameFormat(expected: WavInfo, actual: WavInfo, file: File) {
        if (actual.sampleRate == expected.sampleRate &&
            actual.channels == expected.channels &&
            actual.bitsPerSample == expected.bitsPerSample
        ) {
            return
        }
        throw BookAssemblyException(
            "${file.name} formati boshqalarnikiga mos emas: " +
                "${actual.sampleRate} Hz / ${actual.channels} kanal / ${actual.bitsPerSample} bit, " +
                "kutilgani ${expected.sampleRate} Hz / ${expected.channels} kanal / ${expected.bitsPerSample} bit",
        )
    }
}
