package uz.ovozstudio.app.media.merge

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavSampleReader
import uz.ovozstudio.app.media.WavWriter
import uz.ovozstudio.app.media.dsp.Resampler
import uz.ovozstudio.app.media.format.WavPcmReader
import java.io.File
import java.io.IOException

/**
 * Bir necha WAV faylni ketma-ket bitta faylga qo'shadi.
 *
 * Manbalar bir xil parametrda bo'lishi shart emas — ikki qurilmadan olingan
 * yozuvlar odatda boshqa-boshqa chastotada bo'ladi. Shuning uchun natija
 * parametrlari [targetOf] bilan tanlanadi, farq qiladigan manbalar esa
 * moslashtiriladi:
 *  - chastota boshqacha bo'lsa — qayta namunalanadi ([Resampler]), ovoz
 *    balandligi va uzunligi o'zgarmaydi;
 *  - kanal soni boshqacha bo'lsa — mono fayl stereoga ko'chiriladi.
 *
 * Hamma manba bir xil parametrda bo'lsa, namunalar **butun son** ko'rinishida,
 * bayt-bayt o'zgarmasdan ko'chiriladi. Float orqali o'tkazish 16 bitda ba'zi
 * namunalarni bir birlikka siljitardi; birlashtirishda buning hech kerakligi
 * yo'q.
 *
 * Manba fayllar hech qachon o'zgartirilmaydi.
 */
object AudioMerger {

    /** Birlashgan fayl parametrlari. */
    data class Target(val sampleRate: Int, val channels: Int, val bitDepth: BitDepth)

    private const val CHUNK_FRAMES = 65_536
    private const val EXACT_CHUNK_FRAMES = 65_536

    /**
     * Natija parametrlarini manbalardan tanlaydi.
     *
     *  - chastota — **birinchi** fayldan: natija o'sha faylga o'xshab eshitiladi;
     *  - kanallar — eng ko'pi: mono stereoga ko'chiriladi, stereo esa mono
     *    bo'lib ezilib ketmaydi;
     *  - chuqurlik — birorta manba 24 bitli bo'lsa 24, aks holda 16.
     */
    fun targetOf(infos: List<WavInfo>): Target {
        require(infos.isNotEmpty()) { "Birlashtirish uchun kamida bitta fayl kerak" }
        val depth = if (infos.any { it.bitsPerSample == 24 }) BitDepth.BIT_24 else BitDepth.BIT_16
        return Target(
            sampleRate = infos.first().sampleRate,
            channels = infos.maxOf { it.channels },
            bitDepth = depth,
        )
    }

    /**
     * [sources] ni berilgan tartibda [dest] ga qo'shadi.
     *
     * @param work vaqtinchalik fayllar uchun papka (qayta namunalash uchun).
     *   Ish tugagach bu papkada iz qolmaydi.
     * @param onProgress 0…1 oralig'ida; taxminiy, chunki qayta namunalangan
     *   fayl uzunligi yaxlitlanadi.
     */
    @Throws(IOException::class)
    fun merge(
        sources: List<File>,
        dest: File,
        work: File,
        onProgress: (Float) -> Unit = {},
    ): WavInfo {
        if (sources.isEmpty()) throw IOException("Birlashtirish uchun fayl yo'q")

        val infos = sources.map { WavFile.readInfo(it) }
        for (info in infos) {
            if (info.bitsPerSample != 16 && info.bitsPerSample != 24) {
                throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")
            }
        }
        val target = targetOf(infos)

        var expected = 0L
        for (info in infos) expected += info.frames * target.sampleRate / info.sampleRate
        val totalFrames = maxOf(1L, expected)

        val writer = WavWriter(dest, target.sampleRate, target.channels, target.bitDepth)
        var done = 0L
        try {
            for (index in sources.indices) {
                val source = sources[index]
                val info = infos[index]
                val resample = info.sampleRate != target.sampleRate

                var converted: File? = null
                try {
                    val input: File
                    if (resample) {
                        val temp = File(work, "qayta-namunalangan-$index.wav")
                        // Koeffitsient — eski chastota / yangi chastota: shunda
                        // namunalar soni yangi chastotaga mos keladi, ohang esa
                        // o'zgarmaydi. Fayl sarlavhasidagi chastota bu yerda
                        // ahamiyatsiz: quyida namunalar `target` chastotasida
                        // o'qiladi.
                        Resampler.resample(source, temp, info.sampleRate.toDouble() / target.sampleRate)
                        converted = temp
                        input = temp
                    } else {
                        input = source
                    }

                    val exact = !resample &&
                        info.channels == target.channels &&
                        info.bitsPerSample == target.bitDepth.bits

                    val before = done
                    done += if (exact) {
                        copyExact(writer, input, target.channels) { frames ->
                            onProgress(((before + frames).toFloat() / totalFrames).coerceIn(0f, 1f))
                        }
                    } else {
                        copyConverted(writer, input, target.channels) { frames ->
                            onProgress(((before + frames).toFloat() / totalFrames).coerceIn(0f, 1f))
                        }
                    }
                } finally {
                    converted?.delete()
                }
            }
        } finally {
            writer.close()
        }
        return WavFile.readInfo(dest)
    }

    /** Namunalarni butun son sifatida, o'zgarishsiz ko'chiradi. Qaytaradi: kadrlar soni. */
    private fun copyExact(
        writer: WavWriter,
        file: File,
        channels: Int,
        onFrames: (Long) -> Unit,
    ): Long {
        WavPcmReader(file).use { reader ->
            val buffer = IntArray(EXACT_CHUNK_FRAMES * channels)
            var total = 0L
            while (true) {
                val got = reader.read(buffer, EXACT_CHUNK_FRAMES)
                if (got <= 0) break
                writer.writeIntegers(buffer, got)
                total += got
                onFrames(total)
            }
            return total
        }
    }

    /**
     * Namunalarni float orqali o'tkazadi va kanal sonini moslaydi.
     * Qaytaradi: yozilgan kadrlar soni.
     */
    private fun copyConverted(
        writer: WavWriter,
        file: File,
        outChannels: Int,
        onFrames: (Long) -> Unit,
    ): Long {
        WavSampleReader(file).use { reader ->
            val inChannels = reader.info.channels
            val frames = reader.info.frames
            val input = FloatArray(CHUNK_FRAMES * inChannels)
            val mapped = if (inChannels == outChannels) input else FloatArray(CHUNK_FRAMES * outChannels)
            var position = 0L
            while (position < frames) {
                val want = minOf(CHUNK_FRAMES.toLong(), frames - position).toInt()
                val got = reader.readFrames(position, want, input)
                if (got <= 0) break
                if (inChannels != outChannels) remap(input, inChannels, mapped, outChannels, got)
                writer.write(mapped, got)
                position += got
                onFrames(position)
            }
            return position
        }
    }

    /**
     * Kanallarni moslaydi: chiqish kanali kirishdagi shu raqamli kanaldan,
     * kirish kamroq bo'lsa — oxirgisidan olinadi. Mono fayl shu tariqa
     * ikkala kanalga ham bir xil ko'chadi.
     */
    internal fun remap(input: FloatArray, inChannels: Int, output: FloatArray, outChannels: Int, frames: Int) {
        for (frame in 0 until frames) {
            for (channel in 0 until outChannels) {
                val from = minOf(channel, inChannels - 1)
                output[frame * outChannels + channel] = input[frame * inChannels + from]
            }
        }
    }
}
