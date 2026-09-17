package uz.ovozstudio.app.media.mix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ko'p yo'lli aralashtirish.
 *
 * Tekshiruvlar **analitik** javobga qarab yozilgan: har bir holatda kutilgan
 * son qo'lda hisoblanadi (masalan, mono yo'l o'rtada −3 dB, ya'ni 0.7071).
 * Kirish signallari ham o'zimiz yasagan manbadan keladi, ya'ni test o'z
 * natijasini o'zi bilan solishtirib qo'ymaydi — son tashqaridan, matematikadan
 * olinadi.
 */
class AudioMixerTest {

    // ---------- Asosiy qoidalar ----------

    @Test
    fun `bitta stereo yol ozgarmaydi`() {
        val source = TestSource(frames = 100, channels = 2, sampleRate = 48_000) { frame, channel ->
            if (channel == 0) 0.5f else -0.25f + frame * 1e-6f
        }
        val samples = render(track(source, MixTrack("a")))

        for (i in 0 until 100) {
            assertEquals(0.5f, samples[i * 2], 1e-6f)
            assertEquals(-0.25f + i * 1e-6f, samples[i * 2 + 1], 1e-6f)
        }
    }

    @Test
    fun `natija har doim stereo`() {
        val mono = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        val capture = Capture()
        val result = AudioMixer.render(listOf(track(mono, MixTrack("a"))), 1f, capture.sink)

        assertEquals(10L, result.frames)
        assertEquals(20, capture.samples.size)
    }

    @Test
    fun `balandlik desibelda qollanadi`() {
        val source = TestSource(frames = 10, channels = 2, sampleRate = 48_000) { _, _ -> 0.5f }
        val samples = render(track(source, MixTrack("a", gainDb = -6f)))

        // −6 dB — bu yarim amplituda (aniqrog'i 0.5012), 0.25 emas.
        assertEquals(0.5f * dbToGain(-6f), samples[0], 1e-6f)
        assertEquals(0.2506f, samples[0], 0.001f)
    }

    @Test
    fun `umumiy balandlik ham qollanadi`() {
        val source = TestSource(frames = 4, channels = 2, sampleRate = 48_000) { _, _ -> 1f }
        val capture = Capture()
        AudioMixer.render(listOf(track(source, MixTrack("a"))), masterGain = 0.5f, capture.sink)

        assertEquals(0.5f, capture.samples[0], 1e-6f)
    }

    // ---------- Panorama ----------

    @Test
    fun `mono ortada ikki kanalga teng va 3 dB pasayib tushadi`() {
        val source = TestSource(frames = 4, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        val samples = render(track(source, MixTrack("a")))

        val expected = Math.cos(Math.PI / 4).toFloat()
        assertEquals(0.7071f, expected, 1e-4f)
        assertEquals(expected, samples[0], 1e-6f)
        assertEquals(expected, samples[1], 1e-6f)
    }

    @Test
    fun `mono chapga surilsa ong kanal jim qoladi`() {
        // Amplitude 0.5: cho'qqi chegaradan past, ya'ni kesish himoyasi
        // aralashmaydi va test faqat panorama qoidasini o'lchaydi.
        val source = TestSource(frames = 4, channels = 1, sampleRate = 48_000) { _, _ -> 0.5f }
        val samples = render(track(source, MixTrack("a", pan = -1f)))

        assertEquals(0.5f, samples[0], 1e-6f)
        assertEquals(0f, samples[1], 1e-6f)
    }

    @Test
    fun `stereo balans qarama qarshi kanalni pasaytiradi`() {
        val source = TestSource(frames = 4, channels = 2, sampleRate = 48_000) { _, _ -> 0.5f }
        val samples = render(track(source, MixTrack("a", pan = 0.5f)))

        // O'ngga surildi: chap kanal ikki barobar pasayadi, o'ng tegilmaydi.
        assertEquals(0.25f, samples[0], 1e-6f)
        assertEquals(0.5f, samples[1], 1e-6f)
    }

    @Test
    fun `stereo ortada ozgarmaydi`() {
        val source = TestSource(frames = 4, channels = 2, sampleRate = 48_000) { _, _ -> 0.8f }
        val samples = render(track(source, MixTrack("a", pan = 0f)))

        // Balans qoidasi o'rtada 1.0 beradi — doimiy quvvat qoidasi bu yerda
        // har bir stereo yo'lni jimgina −3 dB ga tushirib qo'yardi.
        assertEquals(0.8f, samples[0], 1e-6f)
        assertEquals(0.8f, samples[1], 1e-6f)
    }

    // ---------- Vaqt ----------

    @Test
    fun `siljish yolni kech boshlaydi`() {
        val source = TestSource(frames = 100, channels = 1, sampleRate = 1000) { _, _ -> 1f }
        val samples = render(track(source, MixTrack("a", offsetMs = 50)))

        // 1000 Hz da 50 ms — 50 kadr.
        for (i in 0 until 50) {
            assertEquals(0f, samples[i * 2], 1e-9f)
        }
        assertTrue(samples[50 * 2] > 0.7f)
    }

    @Test
    fun `siljish bolak chegarasidan otsa ham togri`() {
        // 8192 kadrlik bo'lakdan kattaroq: siljish ikkinchi bo'lakka tushadi.
        val source = TestSource(frames = 20_000, channels = 1, sampleRate = 1000) { _, _ -> 1f }
        val samples = render(track(source, MixTrack("a", offsetMs = 9_000)))

        assertEquals(29_000, samples.size / 2)
        assertEquals(0f, samples[(9_000 - 1) * 2], 1e-9f)
        // Mono yo'l o'rtada — har bir kanalda −3 dB.
        assertEquals(0.7071f, samples[9_000 * 2], 1e-4f)
        assertEquals(0.7071f, samples[28_999 * 2], 1e-4f)
    }

    @Test
    fun `uzunlik eng uzoqqa chozilgan yol boyicha`() {
        val short = TestSource(frames = 1_000, channels = 1, sampleRate = 1000) { _, _ -> 1f }
        val long = TestSource(frames = 2_000, channels = 1, sampleRate = 1000) { _, _ -> 1f }

        val inputs = listOf(
            track(short, MixTrack("a")),
            track(long, MixTrack("b", offsetMs = 500)),
        )
        assertEquals(2_500L, AudioMixer.lengthFrames(inputs))
        assertEquals(2_500, render(*inputs.toTypedArray()).size / 2)
    }

    // ---------- Yoqish va o'chirish ----------

    @Test
    fun `ochirilgan yol eshitilmaydi`() {
        val silent = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        val samples = render(track(silent, MixTrack("a", muted = true, gainDb = 0f)))

        // Uzunlik hisobga olinmaydi ham: o'chirilgan yo'l aralashmani cho'zmaydi.
        assertEquals(0, samples.size)
    }

    @Test
    fun `yakka rejim faqat tanlangan yolni qoldiradi`() {
        val first = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        val second = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 0.25f }

        val samples = render(
            track(first, MixTrack("a")),
            track(second, MixTrack("b", solo = true)),
        )

        // Faqat «b» eshitiladi, o'rtada −3 dB.
        assertEquals(0.25f * 0.7071f, samples[0], 1e-4f)
    }

    @Test
    fun `ochirilgan yol yakka rejimni ham boy beradi`() {
        val first = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        val samples = render(track(first, MixTrack("a", solo = true, muted = true)))

        assertEquals(0, samples.size)
    }

    // ---------- Kesish himoyasi ----------

    @Test
    fun `yigindi chegaradan oshsa butun fayl bir xil tushiriladi`() {
        val a = TestSource(frames = 100, channels = 2, sampleRate = 48_000) { _, _ -> 0.8f }
        val b = TestSource(frames = 100, channels = 2, sampleRate = 48_000) { _, _ -> 0.8f }

        val capture = Capture()
        val result = AudioMixer.render(
            listOf(track(a, MixTrack("a")), track(b, MixTrack("b"))),
            1f,
            capture.sink,
        )

        // Yig'indi 1.6 — chegara 0.999. Cho'qqi o'lchangan, koeffitsient aniq.
        assertEquals(1.6f, result.peakBefore, 1e-4f)
        assertEquals(AudioMixer.LIMIT_PEAK / 1.6f, result.appliedGain, 1e-6f)
        assertTrue(result.limited)

        val samples = capture.samples
        var peak = 0f
        for (value in samples) peak = maxOf(peak, kotlin.math.abs(value))
        assertEquals(AudioMixer.LIMIT_PEAK, peak, 1e-5f)

        // Shakl saqlanadi: barcha namunalar bir xil koeffitsientga tushgan,
        // ya'ni ular orasidagi nisbat o'zgarmagan.
        for (value in samples) assertEquals(AudioMixer.LIMIT_PEAK, value, 1e-5f)
    }

    @Test
    fun `chegaradan oshmagan yigindi tushirilmaydi`() {
        val a = TestSource(frames = 100, channels = 2, sampleRate = 48_000) { _, _ -> 0.2f }
        val b = TestSource(frames = 100, channels = 2, sampleRate = 48_000) { _, _ -> 0.3f }

        val capture = Capture()
        val result = AudioMixer.render(
            listOf(track(a, MixTrack("a")), track(b, MixTrack("b"))),
            1f,
            capture.sink,
        )

        assertEquals(1f, result.appliedGain, 1e-6f)
        assertEquals(0.5f, capture.samples[0], 1e-6f)
    }

    @Test
    fun `jim aralashmada koeffitsient buzilmaydi`() {
        val source = TestSource(frames = 10, channels = 2, sampleRate = 48_000) { _, _ -> 0f }
        val result = AudioMixer.render(listOf(track(source, MixTrack("a"))), 1f) { _, _ -> }

        assertEquals(0f, result.peakBefore, 1e-9f)
        assertEquals(1f, result.appliedGain, 1e-6f)
    }

    @Test
    fun `toliq shkaladagi bitta yol ham ozgina tushiriladi`() {
        // Kutilmagan tuyuladi, lekin to'g'ri: namuna aynan 1.0 bo'lsa, u
        // chegarada turadi va eng kichik qo'shilish ham uni buzardi. Butun
        // fayl 0.999 ga tushiriladi — bu −0.009 dB, quloq ilg'amaydi.
        val source = TestSource(frames = 4, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        val capture = Capture()
        val result = AudioMixer.render(
            listOf(track(source, MixTrack("a", pan = -1f))),
            1f,
            capture.sink,
        )

        assertTrue(result.limited)
        assertEquals(AudioMixer.LIMIT_PEAK, capture.samples[0], 1e-6f)
    }

    @Test
    fun `choqqi oldindan olchanadi`() {
        val source = TestSource(frames = 10, channels = 2, sampleRate = 48_000) { _, _ -> -0.9f }
        val peak = AudioMixer.measurePeak(listOf(track(source, MixTrack("a"))))

        // Manfiy cho'qqi ham hisobga olinadi: modul olinadi.
        assertEquals(0.9f, peak, 1e-6f)
    }

    // ---------- Tekshiruv (validate) ----------

    @Test
    fun `bosh royxat xato beradi`() {
        assertEquals(MixError.EMPTY, AudioMixer.validate(emptyList()))
    }

    @Test
    fun `har xil chastota qabul qilinmaydi`() {
        val first = TestSource(frames = 10, channels = 1, sampleRate = 44_100) { _, _ -> 1f }
        val second = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }

        assertEquals(
            MixError.SAMPLE_RATE_MISMATCH,
            AudioMixer.validate(listOf(track(first, MixTrack("a")), track(second, MixTrack("b")))),
        )
    }

    @Test
    fun `uch kanalli yol qabul qilinmaydi`() {
        val source = TestSource(frames = 10, channels = 3, sampleRate = 48_000) { _, _ -> 1f }
        assertEquals(MixError.UNSUPPORTED_CHANNELS, AudioMixer.validate(listOf(track(source, MixTrack("a")))))
    }

    @Test
    fun `hammasi ochirilgan bolsa xato`() {
        val source = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        assertEquals(
            MixError.NO_AUDIBLE_TRACK,
            AudioMixer.validate(listOf(track(source, MixTrack("a", muted = true)))),
        )
    }

    @Test
    fun `togri yol uchun xato yoq`() {
        val source = TestSource(frames = 10, channels = 1, sampleRate = 48_000) { _, _ -> 1f }
        assertNull(AudioMixer.validate(listOf(track(source, MixTrack("a")))))

        // Yakka rejim ham xato emas: yo'l eshitiladi.
        assertNull(AudioMixer.validate(listOf(track(source, MixTrack("a", solo = true)))))
    }

    // ---------- Yordamchilar ----------

    private fun track(source: MixSource, settings: MixTrack) = AudioMixer.Input(source, settings)

    private fun render(vararg inputs: AudioMixer.Input): FloatArray {
        val capture = Capture()
        AudioMixer.render(inputs.toList(), 1f, capture.sink)
        return capture.samples
    }

    /** Natijani yig'ib oladigan soxta yozuvchi. */
    private class Capture {
        private val values = ArrayList<Float>(1024)
        val sink = AudioMixer.Sink { samples, frames ->
            for (i in 0 until frames * AudioMixer.OUTPUT_CHANNELS) values.add(samples[i])
        }

        val samples: FloatArray get() = FloatArray(values.size) { values[it] }
    }

    /** Soxta manba: namunalar formula bo'yicha yasaladi. */
    private class TestSource(
        override val frames: Long,
        override val channels: Int,
        override val sampleRate: Int,
        private val generator: (Long, Int) -> Float,
    ) : MixSource {

        override fun read(startFrame: Long, frameCount: Int, out: FloatArray): Int {
            val available = (frames - startFrame).coerceAtLeast(0)
            val count = minOf(frameCount.toLong(), available).toInt()
            if (count <= 0) return 0
            for (i in 0 until count) {
                for (channel in 0 until channels) {
                    out[i * channels + channel] = generator(startFrame + i, channel)
                }
            }
            return count
        }

        override fun close() = Unit
    }
}
