package uz.ovozstudio.app.media.format

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import uz.ovozstudio.app.log.ErrorLog
import uz.ovozstudio.app.media.BitDepth
import uz.ovozstudio.app.media.WavWriter
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Siqilgan faylni (MP3, M4A, FLAC, OGG…) WAV ga dekodlaydi.
 *
 * Nega kerak: tahrirlash ichkarida yo'qotishsiz PCM ustida ketadi, lekin
 * foydalanuvchi istalgan formatni yuklashi mumkin. Import paytida fayl bir
 * marta WAV ga ochiladi, ish tugagach esa [FormatPreservingExporter] uni
 * **manba formatiga qaytaradi** — foydalanuvchi talab qilgan aynan shu.
 *
 * Dekodlash `MediaExtractor` + `MediaCodec` orqali: Android'ning o'zi
 * beradigan yagona yo'l. Qurilmada sinaladi, JVM'da emas.
 */
class AndroidAudioDecoder {

    sealed interface Result {
        /** [wav] — ochilgan fayl; [format] — undagi namuna parametrlari. */
        data class Done(val wav: File, val format: AudioFormat, val frames: Long) : Result

        /** Bu faylni ochib bo'lmadi. Fayl yaratilmaydi. */
        data class Failed(val reason: FallbackReason) : Result
    }

    /**
     * [destination] ga WAV yozadi. Manba fayl o'zgartirilmaydi.
     *
     * Bekor qilish yo'q: import qisqa jarayon va fayl yangi nom bilan
     * yaratiladi — xato bo'lsa shunchaki qoldirilmaydi.
     */
    fun decode(source: File, destination: File): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.absolutePath)
        } catch (error: Exception) {
            // Fayl ochilmadi: konteyner buzuq yoki Android uni umuman
            // bilmaydi (WMA shunday). Faqat `IOException` emas, hamma istisno
            // ushlanadi: `MediaExtractor` yaroqsiz faylda `IllegalArgumentException`
            // ham tashlaydi, u esa ilovani yiqitmasligi kerak.
            extractor.release()
            ErrorLog.error("audio.decode", "MediaExtractor faylni ocha olmadi", error)
            return Result.Failed(FallbackReason.NO_DECODER)
        }

        var codec: MediaCodec? = null
        try {
            val track = selectAudioTrack(extractor) ?: return Result.Failed(FallbackReason.NO_DECODER)
            extractor.selectTrack(track.index)

            val input = extractor.getTrackFormat(track.index)
            val mime = input.getString(MediaFormat.KEY_MIME) ?: return Result.Failed(FallbackReason.NO_DECODER)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(input, null, null, 0)
            codec.start()

            return pump(extractor, codec, destination)
        } catch (error: Exception) {
            // `MediaCodec` yaroqsiz yoki qo'llab-quvvatlanmagan oqimda
            // `CodecException`, `IllegalStateException` ham tashlaydi.
            destination.delete()
            ErrorLog.error("audio.decode", "Dekodlash bajarilmadi", error)
            return Result.Failed(FallbackReason.NO_DECODER)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private class Track(val index: Int)

    private fun selectAudioTrack(extractor: MediaExtractor): Track? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return Track(i)
        }
        return null
    }

    /**
     * Dekoder chiqishini WAV ga quyadi.
     *
     * Bit chuqurligi dekoderning o'zi aytadi: `KEY_PCM_ENCODING` 16-bit yoki
     * suzuvchi nuqta bo'lishi mumkin. Suzuvchi nuqta bersa — 24-bit yozamiz,
     * aks holda aniqlik behuda yo'qolardi (masalan 24-bitli FLAC manba).
     */
    @Throws(IOException::class)
    private fun pump(extractor: MediaExtractor, codec: MediaCodec, destination: File): Result {
        val info = MediaCodec.BufferInfo()
        var writer: WavWriter? = null
        var format: AudioFormat? = null
        var channels = 0
        var frames = 0L
        var inputDone = false
        var outputDone = false
        var floatOutput = false
        var stalls = 0

        try {
            while (!outputDone) {
                // Har bir aylanish nimadir berganini kuzatamiz. Buferlar
                // almashmay turgan aylanishlar cheksiz davom etishi mumkin:
                // buzuq faylda MediaCodec oxirini e'lon qilmasa, tsikl hech
                // qachon tugamaydi va import "abadiy ishlayapti" bo'lib qoladi.
                var progressed = false

                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        progressed = true
                        val buffer = codec.getInputBuffer(index)
                            ?: throw IOException("Dekoder kirish buferini bermadi")
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        progressed = true
                        val output = codec.outputFormat
                        floatOutput = isFloat(output)
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        val rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val depth = if (floatOutput) BitDepth.BIT_24 else BitDepth.BIT_16
                        writer?.close()
                        writer = WavWriter(destination, rate, channels, depth)
                        format = AudioFormat(AudioContainer.WAV, AudioCodec.PCM, rate, channels, depth.bits)
                    }

                    else -> if (index >= 0) {
                        progressed = true
                        val buffer = codec.getOutputBuffer(index)
                        if (buffer != null && info.size > 0) {
                            val sink = writer
                                ?: throw IOException("Dekoder formatni e'lon qilmadi")
                            frames += writeBuffer(sink, buffer, info, floatOutput, channels)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }

                stalls = if (progressed) 0 else stalls + 1
                if (stalls > MAX_STALLS) throw IOException("Dekoder $MAX_STALLS urinishdan keyin ham javob bermadi")
            }
        } finally {
            runCatching { writer?.close() }
        }

        val actual = format ?: run {
            destination.delete()
            return Result.Failed(FallbackReason.NO_DECODER)
        }
        return Result.Done(destination, actual, frames)
    }

    private fun isFloat(output: MediaFormat): Boolean {
        val encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            output.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else {
            MediaCodecInfoEncoding.PCM_16BIT
        }
        return encoding == MediaCodecInfoEncoding.PCM_FLOAT
    }

    /**
     * Dekoder chiqishini namunalarga aylantirib, WAV ga yozadi va yozilgan
     * kadrlar sonini qaytaradi.
     *
     * [channels] — dekoder e'lon qilgan kanal soni; u [sink] bilan bir xil
     * bo'lishi shart, chunki ikkalasi ham bitta formatdan olingan.
     */
    private fun writeBuffer(
        sink: WavWriter,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        floatOutput: Boolean,
        channels: Int,
    ): Long {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        buffer.order(ByteOrder.nativeOrder())

        if (floatOutput) {
            val count = info.size / 4
            val samples = FloatArray(count)
            buffer.asFloatBuffer().get(samples)
            sink.write(samples, count)
            return (count / channels).toLong()
        }

        val count = info.size / 2
        // Bufer bir yo'la o'qiladi (xotiradan xotiraga ko'chirish): har bir
        // namunani alohida `get(i)` bilan olish uzun faylda sezilarli sekin.
        val shorts = ShortArray(count)
        buffer.asShortBuffer().get(shorts)
        val samples = IntArray(count)
        for (i in 0 until count) samples[i] = shorts[i].toInt()
        // 16-bitli namunalar WavWriter ning butun sonli yo'lidan o'tadi:
        // float orqali aylantirish 24-bitda aniqlikni yo'qotardi.
        sink.writeIntegers(samples, count)
        return (count / channels).toLong()
    }

    private companion object {
        const val TIMEOUT_US = 10_000L

        /**
         * Nechta bo'sh aylanishdan keyin dekoder buzuq deb hisoblanadi.
         *
         * Har biri 10 ms kutadi, ya'ni 200 ta — taxminan ikki soniya uzluksiz
         * jimjitlik. Sog'lom dekoder bunday qilmaydi, buzuq fayl esa aynan
         * shunday qiladi.
         */
        const val MAX_STALLS = 200
    }

    /** `MediaFormat` konstantalari Android'ning turli versiyalarida nomlanadi. */
    private object MediaCodecInfoEncoding {
        const val PCM_16BIT = 2
        const val PCM_FLOAT = 4
    }
}
