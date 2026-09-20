package uz.ovozstudio.app.media

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * Yozib olish yadrosi.
 *
 * Ovoz har doim float ko'rinishida o'qiladi (`ENCODING_PCM_FLOAT`) — bu 24-bit
 * fayl yozish imkonini beradi, holbuki `AudioRecord` to'g'ridan-to'g'ri 24-bit
 * faqat API 31+ qurilmalarda ishlaydi.
 *
 * Yozish [File] ga, ilovaning ichki papkasiga bo'ladi — tashqi xotira ruxsati kerak emas.
 */
class AudioRecorderEngine {

    /** Bitta yozuv natijasi. */
    data class Result(
        val file: File,
        val info: WavInfo,
        val markersMs: List<Long>,
    )

    /** Yozish jarayonidagi holat — UI shu orqali yangilanadi. */
    interface Listener {
        fun onProgress(elapsedMs: Long, level: Float)
        fun onError(message: String)
    }

    private var record: AudioRecord? = null
    private var writer: WavWriter? = null
    private var thread: Thread? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    // Belgi UI oqimidan qo'shiladi, `stop()` esa boshqa oqimdan o'qiydi:
    // oddiy ro'yxatda bu ConcurrentModificationException berardi.
    private val markers = CopyOnWriteArrayList<Long>()

    /** Yozuvchi oqim o'qiydigan maydon — shuning uchun @Volatile. */
    @Volatile
    private var framesWritten = 0L

    private var sampleRate = RecorderConfig.DEFAULT_SAMPLE_RATE
    private var currentFile: File? = null

    val isRunning: Boolean get() = running.get()
    val isPaused: Boolean get() = paused.get()

    /**
     * Yozishni boshlaydi. Xato bo'lsa `null` emas, istisno tashlaydi —
     * chaqiruvchi tomon uni ushlab, foydalanuvchiga ko'rsatadi.
     */
    @Synchronized
    @SuppressLint("MissingPermission")
    fun start(config: RecorderConfig, dest: File, listener: Listener) {
        check(!running.get()) { "Yozish allaqachon ketmoqda" }

        sampleRate = config.sampleRate
        val channelMask =
            if (config.channels == 2) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) {
            throw IllegalStateException("Bu qurilma ${sampleRate} Hz float yozishni qo'llab-quvvatlamaydi")
        }
        val bufferBytes = max(minBuffer, sampleRate * config.channels * 4 / 2)

        val audioRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build(),
            )
            .setBufferSizeInBytes(bufferBytes)
            .build()

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("Mikrofon ochilmadi")
        }

        val activeWriter = try {
            WavWriter(dest, sampleRate, config.channels, config.bitDepth)
        } catch (error: Exception) {
            // Fayl ochilmadi (joy yo'q, papka yopiq). Mikrofon bo'shatilmasa,
            // u keyingi urinishgacha band bo'lib qolardi.
            audioRecord.release()
            runCatching { dest.delete() }
            throw error
        }
        record = audioRecord
        writer = activeWriter
        currentFile = dest
        markers.clear()
        framesWritten = 0
        paused.set(false)

        // Effektlarni ulash ham, yozishni boshlash ham xato berishi mumkin:
        // ikkalasi bitta himoya ostida. Xato bo'lsa ochilgan resurslar shu
        // yerda bo'shatiladi — aks holda `running` abadiy `true` bo'lib
        // qolardi va keyingi urinish «yozish allaqachon ketmoqda» xatosi
        // bilan yopilardi.
        try {
            applyAudioEffects(audioRecord.audioSessionId, config)
            audioRecord.startRecording()
        } catch (error: Exception) {
            releaseAfterFailedStart()
            throw error
        }

        running.set(true)

        val readBuffer = FloatArray(FRAMES_PER_READ * config.channels)
        val channels = config.channels

        thread = Thread({
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            var failedReads = 0
            try {
                while (running.get()) {
                    val read = audioRecord.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (read < 0) {
                        // Manfiy qiymat — XATO KODI, «hozircha ma'lumot yo'q»
                        // emas. Uni e'tiborsiz qoldirsak, sikl to'xtovsiz
                        // aylanib protsessorni to'liq band qiladi (mikrofonni
                        // boshqa ilova olganda, quloqchin uzilganda) —
                        // foydalanuvchi esa yozuv ketmayotganini bilmaydi.
                        // `running` yolg'on bo'lsa, bu oddiy to'xtatish.
                        failedReads++
                        if (running.get() &&
                            (read == AudioRecord.ERROR_DEAD_OBJECT || failedReads >= MAX_READ_FAILURES)
                        ) {
                            throw IllegalStateException("Mikrofondan ovoz kelmayapti (kod $read)")
                        }
                        continue
                    }
                    failedReads = 0
                    if (read == 0) continue
                    if (paused.get()) continue // pauza paytida yozilmaydi

                    var peak = 0f
                    for (i in 0 until read) {
                        val value = abs(readBuffer[i])
                        if (value > peak) peak = value
                    }
                    // Yozuvchi oqim faqat shu yerda ishlatiladi: to'xtatish
                    // tartibi (join -> close) shu tufayli xavfsiz.
                    activeWriter.write(readBuffer, read / channels)
                    framesWritten += read / channels
                    listener.onProgress(framesWritten * 1000L / sampleRate, peak)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Yozish jarayonida xatolik", error)
                // Mikrofon darhol bo'shatiladi: oqim tugadi, lekin `AudioRecord`
                // hali yozib turibdi (tizim mikrofon belgisi yonib qoladi).
                // Yadro holatini esa `stop()` tozalaydi — uni tinglovchi chaqiradi.
                runCatching { audioRecord.stop() }
                listener.onError(error.message ?: "Yozishda xatolik")
            }
        }, "ovoz-recorder").apply { start() }
    }

    fun pause() {
        if (running.get()) paused.set(true)
    }

    fun resume() {
        if (running.get()) paused.set(false)
    }

    /** Belgining hozirgi vaqtini qaytaradi (millisoniya). */
    fun addMarker(): Long {
        val at = framesWritten * 1000L / sampleRate
        markers += at
        return at
    }

    /**
     * Yozishni to'xtatadi va tayyor faylni qaytaradi.
     *
     * Yozuvchi oqim xato bilan o'zi tugagan bo'lsa ham, mikrofon va fayl
     * ochiq qoladi va `running` `true` turadi — shuning uchun tinglovchi
     * xatodan keyin ham shu metodni chaqirishi shart. Aks holda keyingi
     * yozish «allaqachon ketmoqda» deb rad etilardi.
     */
    @Synchronized
    fun stop(): Result? {
        if (!running.get()) return null
        running.set(false)
        paused.set(false)

        record?.let { recorder ->
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (error: IllegalStateException) {
                Log.w(TAG, "AudioRecord.stop() xatolik", error)
            }
        }

        thread?.join(STOP_TIMEOUT_MS)
        if (thread?.isAlive == true) {
            // Oqim hali ham bloklangan o'qishda: faylni yopishga majburmiz,
            // aks holda yozilgan hamma narsa yo'qoladi. Vaziyat jurnalga tushadi.
            Log.w(TAG, "Yozuvchi oqim $STOP_TIMEOUT_MS ms ichida to'xtamadi")
        }
        thread = null

        // Fayl yopilganda joy yetmay qolishi mumkin (disk to'lgan). Bu xato
        // mikrofonni bo'shatishga xalaqit bermasligi kerak: aks holda u band
        // qolardi, «to'xtatish» tugmasi esa ilovani yiqitardi. Yozilgan qism
        // baribir o'qiladi: `WavFile` sarlavha noto'g'ri bo'lsa, uzunlikni
        // fayl hajmidan oladi.
        try {
            writer?.close()
        } catch (error: IOException) {
            Log.w(TAG, "Yozuv faylini yopib bo'lmadi (joy yetmadimi?)", error)
        }
        writer = null

        runCatching { record?.release() }
        record = null
        releaseAudioEffects()

        val file = currentFile ?: return null
        currentFile = null
        val info = runCatching { WavFile.readInfo(file) }.getOrNull() ?: return null
        return Result(file, info, markers.toList())
    }

    /** [start] yarim yo'lda yiqilganda ochilgan resurslarni yig'ishtirib tashlaydi. */
    private fun releaseAfterFailedStart() {
        runCatching { record?.release() }
        runCatching { writer?.close() }
        runCatching { currentFile?.delete() }
        record = null
        writer = null
        currentFile = null
        running.set(false)
        paused.set(false)
        releaseAudioEffects()
    }

    private fun applyAudioEffects(sessionId: Int, config: RecorderConfig) {
        if (config.noiseSuppression && NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
        }
        if (config.echoCancellation && AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
        }
    }

    private fun releaseAudioEffects() {
        runCatching { noiseSuppressor?.release() }
        runCatching { echoCanceler?.release() }
        noiseSuppressor = null
        echoCanceler = null
    }

    private companion object {
        const val TAG = "AudioRecorderEngine"
        const val FRAMES_PER_READ = 2048

        /** Ketma-ket shuncha xato kodidan keyin yozish to'xtatiladi va xato aytiladi. */
        const val MAX_READ_FAILURES = 50
        const val STOP_TIMEOUT_MS = 3_000L
    }
}
