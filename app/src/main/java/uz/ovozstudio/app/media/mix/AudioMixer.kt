package uz.ovozstudio.app.media.mix

import kotlin.math.abs

/** Aralashmani boshlashdan oldin topiladigan muammolar. */
enum class MixError {
    /** Birorta yo'l qo'shilmagan. */
    EMPTY,

    /** Yo'llarning chastotasi har xil — ularni bir varaqda aralashtirib bo'lmaydi. */
    SAMPLE_RATE_MISMATCH,

    /** Yo'lda 1 yoki 2 dan boshqa kanal bor. */
    UNSUPPORTED_CHANNELS,

    /** Hamma yo'l o'chirilgan (yoki yakka rejim hech kimni tanlamagan). */
    NO_AUDIBLE_TRACK,
}

/**
 * Ko'p yo'lli aralashtirish.
 *
 * Natija **har doim stereo**: panorama (chap/o'ng joylashuv) faqat ikki
 * kanalda ma'noga ega, mono chiqishda u jimgina yo'qolardi.
 *
 * Ikki o'tishli ish: avval cho'qqi o'lchanadi, keyin yoziladi. Sabab —
 * qo'shilgan yo'llarning yig'indisi 1.0 dan oshib ketadi (ikki yo'l
 * 0.8 dan qo'shilsa — 1.6). Bunda har bir namuna o'z chegarasiga urilib
 * buzilish (limiter) o'rniga butun fayl **bitta** koeffitsientga
 * tushiriladi: tovush o'z shaklini saqlaydi, faqat balandroq bo'lmaydi.
 * Bu — ekvalayzerdagi kesish himoyasi bilan bir xil qoida
 * ([LIMIT_PEAK]).
 *
 * Chastotalar teng bo'lishi shart. Har xil chastotali yo'lni jimgina
 * qayta namunalab bo'lmaydi: bu sifatsiz interpolyatsiya bo'lardi va
 * foydalanuvchi buni faqat quloq bilan, keyin payqardi. Shuning uchun
 * bunday holat [MixError.SAMPLE_RATE_MISMATCH] bilan ochiq aytiladi.
 */
object AudioMixer {

    /** Natijadagi kanallar soni. */
    const val OUTPUT_CHANNELS = 2

    /** Kesish himoyasi chegarasi. */
    const val LIMIT_PEAK = 0.999f

    /**
     * Bir marta o'qiladigan kadrlar soni. Xotira [OUTPUT_CHANNELS] × shu son
     * × 4 bayt ≈ 64 KB — butun faylni xotiraga ko'tarmaslik uchun bo'lak-bo'lak
     * ishlanadi (bir soatlik aralashma ham shu bilan ishlaydi).
     */
    private const val CHUNK_FRAMES = 8_192

    /** Aralashmaga kiradigan bitta yo'l: manba va uning sozlamalari. */
    data class Input(val source: MixSource, val track: MixTrack) {

        /** Siljish kadrlarda; millisekund yaxlitlanadi (kesish emas). */
        val offsetFrames: Long
            get() = (track.offsetMs.coerceAtLeast(0) * source.sampleRate + 500) / 1000

        val endFrame: Long get() = offsetFrames + source.frames
    }

    /** Natija: uzunlik, tushirishdan oldingi cho'qqi va qo'llangan koeffitsient. */
    data class Result(val frames: Long, val peakBefore: Float, val appliedGain: Float) {
        val limited: Boolean get() = appliedGain < 1f
    }

    /** Natijani bo'lak-bo'lak oladigan tomon (fayl yozuvchi yoki test). */
    fun interface Sink {
        /** [frames] ta kadr; [samples] uzunligi `frames * 2` (L R L R …). */
        fun write(samples: FloatArray, frames: Int)
    }

    /** Yo'llar aralashtirishga yaroqlimi. */
    fun validate(inputs: List<Input>): MixError? {
        if (inputs.isEmpty()) return MixError.EMPTY
        if (inputs.any { it.source.channels !in 1..2 }) return MixError.UNSUPPORTED_CHANNELS
        if (inputs.map { it.source.sampleRate }.distinct().size > 1) {
            return MixError.SAMPLE_RATE_MISMATCH
        }
        val anySolo = inputs.any { it.track.solo }
        if (inputs.none { it.track.audible(anySolo) }) return MixError.NO_AUDIBLE_TRACK
        return null
    }

    /** Natijaning chastotasi — birinchi yo'lniki (tekshiruvdan keyin hammasi teng). */
    fun outputSampleRate(inputs: List<Input>): Int = inputs.firstOrNull()?.source?.sampleRate ?: 0

    /** Aralashmaning uzunligi, kadrlarda: eng uzoqqa cho'zilgan yo'l bo'yicha. */
    fun lengthFrames(inputs: List<Input>): Long {
        val anySolo = inputs.any { it.track.solo }
        return inputs.filter { it.track.audible(anySolo) }.maxOfOrNull { it.endFrame } ?: 0L
    }

    /** Faqat cho'qqini o'lchaydi (yozmasdan). */
    fun measurePeak(inputs: List<Input>, masterGain: Float = 1f): Float =
        run(inputs, masterGain, scale = 1f, sink = null).peakBefore

    /**
     * Aralashmani [sink] ga yozadi.
     *
     * Cho'qqi chegaradan oshsa, butun natija bitta koeffitsientga tushiriladi
     * va bu [Result.appliedGain] da qaytadi — ekran buni foydalanuvchiga
     * aytadi, chunki u kutgan balandlikdan farq qilishi mumkin.
     */
    fun render(inputs: List<Input>, masterGain: Float = 1f, sink: Sink): Result {
        val first = run(inputs, masterGain, scale = 1f, sink = null)
        val applied = if (first.peakBefore > LIMIT_PEAK) LIMIT_PEAK / first.peakBefore else 1f
        run(inputs, masterGain, scale = applied, sink = sink)
        return first.copy(appliedGain = applied)
    }

    /**
     * Bitta o'tish: bo'lak-bo'lak o'qib, yig'ib chiqadi.
     *
     * [sink] `null` bo'lsa faqat o'lchaydi (birinchi o'tish). [scale] ikkinchi
     * o'tishda qo'llanadi.
     */
    private fun run(inputs: List<Input>, masterGain: Float, scale: Float, sink: Sink?): Result {
        val length = lengthFrames(inputs)
        val anySolo = inputs.any { it.track.solo }
        val active = inputs.filter { it.track.audible(anySolo) }

        // Har bir yo'l uchun koeffitsientlar va bufer bir marta tayyorlanadi:
        // har bo'lakda qaytadan hisoblash bekorga ketgan vaqt bo'lardi.
        // Ro'yxat indeks bo'yicha yuritiladi, xarita emas: bitta manba ikki
        // marta qo'shilsa, xarita kalitlari to'qnashib, ikkinchisi yo'qolardi.
        val plans = active.map { input ->
            val (left, right) = channelGains(input.source.channels, input.track.pan)
            val gain = input.track.gain * masterGain * scale
            Plan(
                input = input,
                leftGain = left * gain,
                rightGain = right * gain,
                buffer = FloatArray(CHUNK_FRAMES * input.source.channels),
            )
        }

        val out = FloatArray(CHUNK_FRAMES * OUTPUT_CHANNELS)
        var peak = 0f
        var frame = 0L

        while (frame < length) {
            val count = minOf(CHUNK_FRAMES.toLong(), length - frame).toInt()
            val samples = count * OUTPUT_CHANNELS
            java.util.Arrays.fill(out, 0, samples, 0f)

            for (plan in plans) {
                val input = plan.input
                val from = maxOf(frame, input.offsetFrames)
                val to = minOf(frame + count, input.endFrame)
                if (to <= from) continue

                val requested = (to - from).toInt()
                // Manba faylning oxirida so'ralganichadan kam qaytarishi mumkin.
                val read = input.source.read(from - input.offsetFrames, requested, plan.buffer)
                if (read <= 0) continue

                val base = (from - frame).toInt() * OUTPUT_CHANNELS
                if (input.source.channels == 1) {
                    for (i in 0 until read) {
                        val value = plan.buffer[i]
                        out[base + i * 2] += value * plan.leftGain
                        out[base + i * 2 + 1] += value * plan.rightGain
                    }
                } else {
                    for (i in 0 until read) {
                        out[base + i * 2] += plan.buffer[i * 2] * plan.leftGain
                        out[base + i * 2 + 1] += plan.buffer[i * 2 + 1] * plan.rightGain
                    }
                }
            }

            for (i in 0 until samples) {
                val magnitude = abs(out[i])
                if (magnitude > peak) peak = magnitude
            }
            sink?.write(out, count)
            frame += count
        }

        return Result(frames = length, peakBefore = peak, appliedGain = 1f)
    }

    /** Bitta yo'l uchun oldindan hisoblangan koeffitsientlar va bufer. */
    private class Plan(
        val input: Input,
        val leftGain: Float,
        val rightGain: Float,
        val buffer: FloatArray,
    )
}
