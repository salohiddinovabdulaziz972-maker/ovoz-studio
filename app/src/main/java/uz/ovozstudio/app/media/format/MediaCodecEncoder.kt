package uz.ovozstudio.app.media.format

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * `MediaCodec` ustidagi kodlovchi: M4A/AAC va OGG/Opus.
 *
 * Bu — tizim kodeki, shuning uchun uni JVM'da ishga tushirib bo'lmaydi:
 * sinov qurilmada o'tkaziladi. Sinovdan o'tadigan qismi ataylab ajratilgan —
 * [AdtsHeader] va [PcmBytes] sof arifmetika, ular JVM'da tekshiriladi.
 *
 * Uch xil chiqish yo'li bor va ular tubdan farq qiladi:
 *
 * - **M4A** — `MediaMuxer` konteynerni o'zi yozadi, biz faqat xom AAC
 *   kadrlarini beramiz.
 * - **AAC** — muxer yo'q: `.aac` fayli — bu ketma-ket ADTS sarlavhali xom
 *   kadrlar. Sarlavhani [AdtsHeader] yozadi.
 * - **OGG/Opus** — yana `MediaMuxer`, lekin OGG konteynerida (API 29+).
 *
 * Kodlovchiga kirish har doim 16-bitli PCM ([PcmBytes] surib beradi).
 */
class MediaCodecEncoder(
    private val file: File,
    override val format: AudioFormat,
) : AudioEncoder {

    init {
        require(format.codec == AudioCodec.AAC || format.codec == AudioCodec.OPUS) {
            "MediaCodec kodlovchisi faqat AAC va Opus uchun: ${format.codec}"
        }
        require(format.channels in 1..8) { "Kanal soni 1..8: ${format.channels}" }
        require(CodecRates.supports(format.codec, format.sampleRate)) {
            "${format.codec} bu chastotani bilmaydi: ${format.sampleRate} Hz"
        }
        if (format.container == AudioContainer.OGG) {
            check(Build.VERSION.SDK_INT >= OGG_API_LEVEL) {
                "OGG konteyneri API $OGG_API_LEVEL dan boshlab yoziladi (qurilma: ${Build.VERSION.SDK_INT})"
            }
        }
    }

    private val mime: String = when (format.codec) {
        AudioCodec.AAC -> MediaFormat.MIMETYPE_AUDIO_AAC
        else -> MediaFormat.MIMETYPE_AUDIO_OPUS
    }

    /** Muxer faqat konteynerli chiqishda kerak; `.aac` uni ishlatmaydi. */
    private val muxer: MediaMuxer? =
        if (format.container == AudioContainer.AAC) null
        else MediaMuxer(
            file.absolutePath,
            if (format.container == AudioContainer.OGG) MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG
            else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        )

    private val adtsOut: BufferedOutputStream? =
        if (muxer == null) BufferedOutputStream(FileOutputStream(file)) else null

    private val codec: MediaCodec = MediaCodec.createEncoderByType(mime).also { encoder ->
        val description = MediaFormat.createAudioFormat(mime, format.sampleRate, format.channels)
        description.setInteger(MediaFormat.KEY_BIT_RATE, format.bitrate ?: defaultBitrate(format.codec))
        description.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, MAX_INPUT_BYTES)
        if (format.codec == AudioCodec.AAC) {
            description.setInteger(
                MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC,
            )
        }
        encoder.configure(description, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
    }

    private val bufferInfo = MediaCodec.BufferInfo()

    /** Manba bit chuqurligidan 16-bitga tushirish uchun surish. */
    private val shift = ((format.bitDepth ?: DEFAULT_BIT_DEPTH) - DEFAULT_BIT_DEPTH).coerceAtLeast(0)

    /** Kirish buferining bir bo'lagi uchun qayta ishlatiladigan massiv. */
    private val staging = ByteArray(MAX_INPUT_BYTES)

    private var trackIndex = -1
    private var muxerStarted = false
    private var framesWritten = 0L
    private var finished = false

    /**
     * Kodlovchi qaytargan haqiqiy kanal soni. U so'raganimizdan farq
     * qilishi mumkin, ADTS sarlavhasi esa fayl ichidagi haqiqatga mos
     * bo'lishi kerak.
     */
    private var actualChannels = format.channels

    @Throws(IOException::class)
    override fun write(samples: IntArray, frames: Int) {
        check(!finished) { "Kodlovchi allaqachon yopilgan" }
        require(frames >= 0 && frames * format.channels <= samples.size) {
            "Massiv juda kichik: $frames kadr kerak, massivda ${samples.size / format.channels} bor"
        }
        if (frames == 0) return

        var offset = 0
        var stalls = 0
        while (offset < frames) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) {
                // Kodlovchi hali bufer bo'shatmadi: chiqishini olib, qayta
                // urinamiz. Cheksiz kutmaslik uchun urinishlar sanaladi.
                drain(endOfStream = false)
                stalls++
                if (stalls > MAX_STALLS) throw IOException("Kodlovchi kirish buferini bermayapti")
                continue
            }
            stalls = 0
            val buffer = codec.getInputBuffer(index)
                ?: throw IOException("Kodlovchi kirish buferini bermadi")
            buffer.clear()
            val capacityFrames = PcmBytes.framesIn(buffer.capacity(), format.channels)
            val take = minOf(frames - offset, capacityFrames)
            if (take <= 0) throw IOException("Kirish buferi juda kichik: ${buffer.capacity()} bayt")

            val bytes = PcmBytes.sizeFor(take, format.channels)
            PcmBytes.write(staging, samples, offset * format.channels, take * format.channels, shift)
            buffer.put(staging, 0, bytes)
            codec.queueInputBuffer(
                index, 0, bytes,
                framesWritten * MICROS_PER_SECOND / format.sampleRate, 0,
            )
            framesWritten += take
            offset += take
            drain(endOfStream = false)
        }
    }

    override fun finish() {
        if (finished) return
        finished = true
        try {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                codec.queueInputBuffer(
                    index, 0, 0,
                    framesWritten * MICROS_PER_SECOND / format.sampleRate,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                )
            }
            drain(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { adtsOut?.flush() }
            runCatching { adtsOut?.close() }
        }
    }

    override fun close() = finish()

    /**
     * Kodlovchi chiqishini bo'shatadi. Har yozishdan keyin ham chaqiriladi:
     * aks holda ichki navbat to'lib, `dequeueInputBuffer` abadiy `-1`
     * qaytarardi.
     */
    @Throws(IOException::class)
    private fun drain(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }

                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readOutputFormat(codec.outputFormat)

                index >= 0 -> {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && bufferInfo.size > 0) {
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        emit(buffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                }

                else -> return
            }
            if (endOfStream && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
        }
    }

    private fun readOutputFormat(outputFormat: MediaFormat) {
        // Kanal soni chiqish formatida ham, konfiguratsiya baytlarida ham
        // bo'ladi; ikkinchisi ishonchliroq, chunki ADTS sarlavhasi aynan
        // shundan quriladi.
        val channels = AdtsHeader.channelCountFromCsd(outputFormat.getByteBuffer("csd-0")?.let {
            ByteArray(it.remaining()).also { target -> it.duplicate().get(target) }
        }) ?: outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        actualChannels = channels

        val active = muxer ?: return
        if (muxerStarted) return
        trackIndex = active.addTrack(outputFormat)
        active.start()
        muxerStarted = true
    }

    @Throws(IOException::class)
    private fun emit(buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        val active = muxer
        if (active != null) {
            if (!muxerStarted) throw IOException("Muxer ishga tushmagan holda ma'lumot keldi")
            active.writeSampleData(trackIndex, buffer, info)
            return
        }
        // `.aac`: xom kadr oldiga ADTS sarlavhasi qo'yiladi, aks holda
        // faylni hech bir pleyer ochmaydi.
        val out = adtsOut ?: throw IOException("Chiqish oqimi yo'q")
        val header = ByteArray(AdtsHeader.LENGTH)
        AdtsHeader.write(header, 0, info.size, format.sampleRate, actualChannels)
        out.write(header)
        val payload = ByteArray(info.size)
        buffer.get(payload)
        out.write(payload)
    }

    companion object {
        const val OGG_API_LEVEL = 29

        private fun defaultBitrate(codec: AudioCodec): Int =
            if (codec == AudioCodec.OPUS) 96_000 else 128_000

        private const val TIMEOUT_US = 10_000L
        private const val DEFAULT_BIT_DEPTH = 16
        private const val MAX_INPUT_BYTES = 64 * 1024
        private const val MICROS_PER_SECOND = 1_000_000L

        /** Nechta bo'sh urinishdan keyin kodlovchi buzuq deb hisoblanadi. */
        private const val MAX_STALLS = 200
    }
}
