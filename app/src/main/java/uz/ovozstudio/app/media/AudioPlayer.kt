package uz.ovozstudio.app.media

import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Eshitish uchun oddiy pleyer.
 *
 * Ataylab `MediaPlayer` ishlatilgan: WAV fayllarni qurilmaning o'zi o'qiydi,
 * qo'shimcha kutubxona va uning versiyalari bilan bog'liq xatolar yo'q.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var stopAtMs: Long = -1

    /** Tanlangan qism tugaganda chaqiriladi (ovozli e'lon uchun qulay joy). */
    var onFinished: (() -> Unit)? = null

    val isPlaying: Boolean get() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    /** [startMs] dan [endMs] gacha eshitadi. [endMs] `null` bo'lsa — oxirigacha. */
    fun play(file: File, startMs: Long = 0, endMs: Long? = null) {
        stopPlayer()
        stopAtMs = endMs ?: -1
        val media = MediaPlayer()
        player = media
        try {
            media.setDataSource(file.absolutePath)
            media.setOnCompletionListener {
                stopPlayer()
                onFinished?.invoke()
            }
            media.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer xatolik: what=$what extra=$extra")
                stopPlayer()
                true
            }
            media.prepare()
            if (startMs > 0) media.seekTo(startMs.toInt())
            media.start()
        } catch (error: Exception) {
            Log.e(TAG, "Eshitishni boshlab bo'lmadi", error)
            stopPlayer()
        }
    }

    /** Joriy pozitsiya (millisoniya); pleyer yo'q bo'lsa 0. */
    fun positionMs(): Long {
        val media = player ?: return 0
        return runCatching { media.currentPosition.toLong() }.getOrDefault(0L)
    }

    /** Tanlangan oraliq tugagan bo'lsa — to'xtatadi. Har yangilanishda chaqiriladi. */
    fun stopIfPastEnd() {
        val limit = stopAtMs
        if (limit < 0) return
        if (positionMs() >= limit) {
            stopPlayer()
            onFinished?.invoke()
        }
    }

    fun stop() {
        stopPlayer()
    }

    fun release() {
        stopPlayer()
        onFinished = null
    }

    private fun stopPlayer() {
        val media = player ?: return
        player = null
        stopAtMs = -1
        runCatching { if (media.isPlaying) media.stop() }
        runCatching { media.release() }
    }

    private companion object {
        const val TAG = "AudioPlayer"
    }
}
