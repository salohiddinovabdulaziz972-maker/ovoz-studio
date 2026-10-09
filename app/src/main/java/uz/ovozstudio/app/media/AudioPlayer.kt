package uz.ovozstudio.app.media

import android.media.MediaPlayer
import android.media.PlaybackParams
import uz.ovozstudio.app.log.ErrorLog
import java.io.File

/**
 * Eshitish uchun oddiy pleyer.
 *
 * Ataylab `MediaPlayer` ishlatilgan: WAV fayllarni qurilmaning o'zi o'qiydi,
 * qo'shimcha kutubxona va uning versiyalari bilan bog'liq xatolar yo'q.
 *
 * Tezlik va balandlik `PlaybackParams` orqali beriladi. Ular pleyer
 * yaratilganda emas, **o'ynash boshlangandan keyin** qo'llanadi: fayl hali
 * tayyorlanmagan bo'lsa ba'zi qurilmalar so'rovni rad etadi. Shuning uchun
 * tanlangan qiymat maydonda saqlanadi va har yangi ijroda qayta qo'llanadi —
 * foydalanuvchi bir marta tanlagan tezlik keyingi eshitishda ham qoladi.
 */
class AudioPlayer {

    private var player: MediaPlayer? = null
    private var stopAtMs: Long = -1

    /** Joriy tezlik (1.0 — o'zgarmagan). */
    private var speed: Float = 1f

    /** Joriy balandlik (1.0 — o'zgarmagan). */
    private var pitch: Float = 1f

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
                ErrorLog.error("audio.player", "MediaPlayer xatolik: what=$what extra=$extra")
                stopPlayer()
                true
            }
            media.prepare()
            if (startMs > 0) media.seekTo(startMs.toInt())
            media.start()
            applyRate()
        } catch (error: Exception) {
            ErrorLog.error("audio.player", "Eshitishni boshlab bo'lmadi", error)
            stopPlayer()
        }
    }

    /**
     * Tezlik va balandlikni o'zgartiradi.
     *
     * Pleyer ishlamayotgan bo'lsa ham qiymat saqlanadi — keyingi ijroda
     * qo'llanadi. Bu ataylab: foydalanuvchi tezlikni oldindan tanlab,
     * keyin eshitishni boshlashi mumkin.
     */
    fun setRate(speed: Float, pitch: Float) {
        this.speed = speed.coerceIn(MIN_RATE, MAX_RATE)
        this.pitch = pitch.coerceIn(MIN_RATE, MAX_RATE)
        applyRate()
    }

    private fun applyRate() {
        val media = player ?: return
        if (!runCatching { media.isPlaying }.getOrDefault(false)) return
        runCatching {
            media.playbackParams = PlaybackParams()
                .setSpeed(this.speed)
                .setPitch(this.pitch)
        }.onFailure {
            ErrorLog.error("audio.player", "Tezlikni o'zgartirib bo'lmadi", it)
        }
    }

    /** Belgilangan vaqt oldinga yoki orqaga suradi; yangi pozitsiyani qaytaradi. */
    fun skipBy(deltaMs: Long, lowerMs: Long = 0L, upperMs: Long? = null): Long {
        val media = player ?: return 0L
        val current = positionMs()
        val upper = upperMs ?: runCatching { media.duration.toLong() }.getOrDefault(current)
        val target = (current + deltaMs).coerceIn(lowerMs, upper.coerceAtLeast(lowerMs))
        seekTo(target)
        return target
    }

    /**
     * Belgilangan joyga o'tadi (millisoniya).
     *
     * Kitobda bu belgi bo'ylab sakrash va «o'tkazib yuborish» uchun kerak:
     * fayl qaytadan ochilmaydi, ya'ni o'tish darhol bo'ladi.
     */
    fun seekTo(ms: Long) {
        val media = player ?: return
        val target = ms.coerceAtLeast(0).toInt()
        // Xato yiqitmaydi, lekin jurnalda qoladi: sakrash ishlamasa sabab shu.
        runCatching { media.seekTo(target) }
            .onFailure { ErrorLog.error("audio.player", "Belgilangan joyga o'tib bo'lmadi: $target ms", it) }
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
        /** Pastki chegara: 0.5×. Undan sekinroq o'qish amalda foydasiz. */
        const val MIN_RATE = 0.5f

        /** Yuqori chegara: 2.0×. Undan tez o'qishda so'zlar ajralmaydi. */
        const val MAX_RATE = 2.0f
    }

}
