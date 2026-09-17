package uz.ovozstudio.app.media.dsp

import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavFile
import uz.ovozstudio.app.media.WavInfo
import uz.ovozstudio.app.media.WavSampleReader
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import kotlin.math.abs
import kotlin.math.log10

/**
 * Parametrik ekvalayzer.
 *
 * Filtrlash `Float` emas, `Double` da ketadi: 31 polosali kaskad ketma-ket
 * o'tadi va har bir bosqich o'z xatosini qo'shadi. Past chastotali
 * polosalarda (31 Hz) bu farq eshitilarli darajaga chiqadi.
 *
 * Har bir amal YANGI faylga yozadi — manba fayl hech qachon
 * o'zgartirilmaydi. Bu butun ilova bo'ylab bir xil qoida.
 */
object Equalizer {

    /**
     * Bitta polosa: chastota, kuchaytirish va kenglik.
     *
     * Kenglik (`q`) alohida beriladi, chunki u jadvalga bog'liq: oktava
     * oralig'ida 1.41, uchdan bir oktavada 4.32 ([EqBands.q]).
     */
    data class Band(val frequency: Double, val gainDb: Double, val q: Double)

    /**
     * Ekvalayzer sozlamalari.
     *
     * [lowCutHz] — past chastotalarni kesish chastotasi; 0 bo'lsa o'chirilgan.
     */
    data class Settings(val bands: List<Band>, val lowCutHz: Int = 0) {

        /**
         * Sozlama hech narsani o'zgartirmaydimi.
         *
         * Bunday holatda qo'llash ma'nosiz: natija manbaning aynan nusxasi
         * bo'lardi, fayl esa kutubxonada behuda paydo bo'lardi.
         */
        val isFlat: Boolean
            get() = lowCutHz <= 0 && bands.all { it.gainDb == 0.0 }

        /** Sozlamada ishlaydigan filtrlar bormi (fayl chastotasini hisobga olib). */
        fun isAudible(sampleRate: Int): Boolean =
            filters(this, sampleRate).isNotEmpty()
    }

    /**
     * Natija.
     *
     * [headroomDb] — chiqish cho'qqisini kesishdan saqlash uchun butun fayl
     * qancha pasaytirilgani (manfiy son yoki 0). Bu **jimgina** qilinmaydi:
     * ekranda aytiladi, aks holda foydalanuvchi «kuchaytirdim, lekin ovoz
     * balandroq bo'lmadi» deb tushunmay qolardi.
     */
    data class Result(val info: WavInfo, val headroomDb: Double)

    private const val CHUNK_FRAMES = 16_384

    /**
     * Chiqish cho'qqisi uchun chegara.
     *
     * 1.0 emas, undan sal past: 16-bitli faylda 1.0 aynan 32767 ga to'g'ri
     * keladi, ya'ni bitta namuna chetlanishi allaqachon kesilish (clipping)
     * bo'ladi. Kichik zaxira shu chegaradagi yaxlitlashni ham qoplaydi.
     */
    private const val PEAK_LIMIT = 0.999

    /**
     * [source] ni filtrlab, [dest] ga yozadi.
     *
     * Ikki bosqichli ish: birinchi o'tishda filtrlanadi va **haqiqiy** chiqish
     * cho'qqisi o'lchanadi. Agar u chegaradan oshsa, ikkinchi o'tish butun
     * faylni kerakli darajada pasaytirib qayta yozadi. Chastota javobini
     * oldindan hisoblash ham mumkin edi, lekin u faqat eng yomon holatni
     * beradi: 3 dB kuchaytirish uchun butun faylni 3 dB pasaytirish —
     * foydalanuvchi so'ragan narsaga zid. O'lchash esa faqat haqiqatan
     * kerak bo'lganda ish qiladi.
     */
    @Throws(IOException::class)
    fun apply(
        source: File,
        dest: File,
        settings: Settings,
        onProgress: (Float) -> Unit = {},
    ): Result {
        val info = WavFile.readInfo(source)
        if (info.frames <= 0) throw IOException("Fayl bo'sh")

        val cascade = filters(settings, info.sampleRate)
        if (cascade.isEmpty()) throw IOException("Ekvalayzer sozlamasi bo'sh")

        val peak = process(source, dest, cascade, gain = 1.0, onProgress = { onProgress(it * 0.5f) })
        if (peak <= PEAK_LIMIT) {
            // Birinchi o'tish butun ish edi, ya'ni ko'rsatkich shu yerda
            // oxiriga yetadi. Bu qator bo'lmasa u 0.5 da qotib qolardi va
            // ekranda tugallangan ish «yarim yo'lda» ko'rinardi.
            onProgress(1.0f)
            return Result(info, 0.0)
        }

        val gain = PEAK_LIMIT / peak
        process(source, dest, cascade, gain = gain, onProgress = { onProgress(0.5f + it * 0.5f) })
        return Result(info, 20.0 * log10(gain))
    }

    /**
     * Sozlamadan filtrlar kaskadini yig'adi.
     *
     * Nol kuchaytirishli polosalar tushib qoladi ([Biquad.isIdentity]):
     * ish hajmi faqat haqiqatan ishlaydigan filtrlardan hisoblanadi.
     */
    internal fun filters(settings: Settings, sampleRate: Int): List<Biquad> = buildList {
        if (settings.lowCutHz > 0) {
            val highPass = Biquad.highPass(sampleRate, settings.lowCutHz.toDouble())
            if (!highPass.isIdentity) add(highPass)
        }
        for (band in settings.bands) {
            val peaking = Biquad.peaking(sampleRate, band.frequency, band.gainDb, band.q)
            if (!peaking.isIdentity) add(peaking)
        }
    }

    /**
     * Kaskadni [source] dan o'tkazib [dest] ga yozadi va chiqish cho'qqisini
     * qaytaradi. [gain] — yozishdan oldingi umumiy koeffitsient.
     *
     * Filtr holati ([state]) har bir chaqiruvda noldan boshlanadi — ikkinchi
     * o'tish birinchisining davomi emas, boshidan boshlanadi.
     */
    @Throws(IOException::class)
    private fun process(
        source: File,
        dest: File,
        cascade: List<Biquad>,
        gain: Double,
        onProgress: (Float) -> Unit,
    ): Double {
        WavSampleReader(source).use { reader ->
            val info = reader.info
            val channels = info.channels
            val depth = BitDepth.of(info.bitsPerSample)
                ?: throw IOException("Qo'llab-quvvatlanmaydigan bit chuqurligi: ${info.bitsPerSample}")

            // Har bir filtr va har bir kanal uchun ikkita holat o'zgaruvchisi.
            val state = DoubleArray(cascade.size * channels * 2)
            val buffer = FloatArray(CHUNK_FRAMES * channels)
            val writer = WavWriter(dest, info.sampleRate, channels, depth)
            var peak = 0.0
            try {
                var frame = 0L
                while (true) {
                    val got = reader.readFrames(frame, CHUNK_FRAMES, buffer)
                    if (got <= 0) break

                    for (i in 0 until got) {
                        for (channel in 0 until channels) {
                            val index = i * channels + channel
                            var value = buffer[index].toDouble()

                            for (f in cascade.indices) {
                                val filter = cascade[f]
                                val slot = (f * channels + channel) * 2
                                // To'g'ridan-to'g'ri II shakl: bitta ko'paytirish
                                // va bitta qo'shish kamroq, holat esa ikkita
                                // o'zgaruvchida saqlanadi.
                                val out = filter.b0 * value + state[slot]
                                state[slot] = filter.b1 * value - filter.a1 * out + state[slot + 1]
                                state[slot + 1] = filter.b2 * value - filter.a2 * out
                                value = out
                            }

                            value *= gain
                            val magnitude = abs(value)
                            if (magnitude > peak) peak = magnitude
                            buffer[index] = value.toFloat()
                        }
                    }

                    writer.write(buffer, got)
                    frame += got
                    onProgress(frame.toFloat() / info.frames)
                }
            } finally {
                writer.close()
            }
            return peak
        }
    }
}
