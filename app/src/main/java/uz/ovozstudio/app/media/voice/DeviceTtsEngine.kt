package uz.ovozstudio.app.media.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Qurilmaning o'z ovoz sintezatori (`android.speech.tts.TextToSpeech`).
 *
 * Bu — [VoiceEngine] ning birinchi amalga oshirilishi. U o'zbek tilini
 * faqat qurilmada o'zbek ovozi **bo'lsa** to'g'ri o'qiydi; bo'lmasa zaxira
 * tilga o'tadi (qaysi tilga — [ScriptDetector] hal qiladi).
 *
 * Dvigatel matnni bo'laklarga **o'zi** bo'ladi ([TextChunker]): sintezator
 * bitta chaqiruvda cheklangan uzunlikni qabul qiladi, chegaradan uzun matnni
 * esa jimgina tashlab ketadi.
 */
class DeviceTtsEngine(context: Context) : VoiceEngine {

    private val appContext = context.applicationContext

    /**
     * Callback'lar asosiy oqimga uzatiladi.
     *
     * Sintezator o'z xizmatidan javob beradi — ya'ni boshqa oqimda. UI
     * holatini o'sha oqimdan o'zgartirish taqiqlanmagan bo'lsa ham,
     * Compose bunday o'zgarishni ko'rmasligi mumkin. Shu sababli hamma
     * xabar shu Handler orqali o'tadi.
     */
    private val main = Handler(Looper.getMainLooper())

    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    /** Navbatdagi bo'laklar va hozir o'qilayotganining indeksi. */
    private var chunks: List<SpeechChunk> = emptyList()
    private var index = 0

    private var listener: SpeechListener? = null

    /** Tayyorlash tugaganda chaqiriladigan callback (bir marta). */
    private var pendingInit: ((VoiceError?) -> Unit)? = null

    private val progress = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            val i = chunkIndex(utteranceId) ?: return
            main.post {
                val current = chunks
                if (i >= current.size) return@post
                index = i
                listener?.onStarted(i, current.size, current[i])
            }
        }

        override fun onDone(utteranceId: String?) {
            val i = chunkIndex(utteranceId) ?: return
            main.post {
                if (i + 1 < chunks.size) {
                    speakChunk(i + 1)
                } else {
                    val target = listener
                    listener = null
                    target?.onFinished()
                }
            }
        }

        @Deprecated("Eski imzo — yangisi pastda", ReplaceWith("onError(utteranceId, errorCode)"))
        override fun onError(utteranceId: String?) {
            reportFailure()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            reportFailure()
        }
    }

    override fun prepare(onResult: (VoiceError?) -> Unit) {
        if (engine != null) {
            onResult(if (ready) null else VoiceError.NOT_AVAILABLE)
            return
        }

        pendingInit = onResult

        // Diqqat: sintezator konstruktori callback'ni **sinxron** ham
        // chaqirishi mumkin (xizmat allaqachon ulangan bo'lsa). Shu sababli
        // callback `engine` o'zgaruvchisiga tayanmaydi — u hali `null`
        // bo'lishi mumkin.
        val started = TextToSpeech(appContext) { status ->
            val callback = pendingInit
            pendingInit = null
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                callback?.invoke(null)
            } else {
                // Xato bo'lsa ham obyekt qaytariladi, lekin u ishlamaydi:
                // uni darhol bo'shatamiz, aks holda keyingi urinish
                // «allaqachon yaratilgan» deb hisoblanib, abadiy qotib
                // qolardi.
                engine?.shutdown()
                engine = null
                callback?.invoke(VoiceError.NOT_AVAILABLE)
            }
        }
        engine = started
        started.setOnUtteranceProgressListener(progress)
    }

    override fun isReady(): Boolean = ready && engine != null

    override fun voices(): List<VoiceInfo> {
        val voices = engine?.voices ?: return emptyList()
        return voices.map { voice ->
            VoiceInfo(
                id = voice.name.orEmpty(),
                localeTag = voice.locale?.toLanguageTag().orEmpty(),
                name = voice.name.orEmpty(),
                quality = voice.quality,
            )
        }.sortedBy { it.localeTag }
    }

    override fun resolveLanguage(script: TextScript): String? {
        val tts = engine ?: return null
        for (tag in ScriptDetector.languageCandidates(script)) {
            val locale = Locale.forLanguageTag(tag)
            // Musbat qiymat — til mavjud; manfiy — yo'q yoki ma'lumot
            // yuklanmagan.
            if (tts.isLanguageAvailable(locale) >= TextToSpeech.LANG_AVAILABLE) return tag
        }
        return null
    }

    override fun speak(request: SpeechRequest, listener: SpeechListener) {
        val tts = engine
        if (tts == null || !ready) {
            listener.onError(VoiceError.NOT_AVAILABLE)
            return
        }
        if (request.text.isBlank()) {
            listener.onError(VoiceError.EMPTY_TEXT)
            return
        }

        // Til: aniq berilgan bo'lsa o'sha, aks holda matnning yozuvidan.
        val tag = request.languageTag ?: resolveLanguage(ScriptDetector.detect(request.text))
        if (tag != null) {
            tts.language = Locale.forLanguageTag(tag)
        }

        tts.setSpeechRate(request.rate.coerceIn(MIN_RATE, MAX_RATE))
        tts.setPitch(request.pitch.coerceIn(MIN_RATE, MAX_RATE))

        // Matnning o'zi emas, undan qirqib olingan bo'laklar o'qiladi:
        // ofsetlar manba matnga ishora qiladi.
        //
        // Chegara sintezatorning o'z chegarasidan **kichik** olinadi
        // (odatda 4000): chegara texnik limit emas, balki to'xtatish
        // qulayligi. Teng qilib qo'yilsa, bitta bo'lak ikki daqiqadan
        // uzoq o'qilardi va «to'xtat» tugmasi foydasiz bo'lardi.
        val limit = minOf(TextChunker.DEFAULT_MAX_CHARS, TextToSpeech.getMaxSpeechInputLength())
        val split = TextChunker.split(request.text, maxChars = limit)
        if (split.isEmpty()) {
            listener.onError(VoiceError.EMPTY_TEXT)
            return
        }

        this.listener = listener
        this.chunks = split
        this.index = 0

        // Avval eskisini to'xtatamiz: aks holda oldingi matnning qolgan
        // bo'laklari navbatda turib, yangisi ularning orqasida qolardi.
        tts.stop()
        speakChunk(0)
    }

    override fun stop() {
        engine?.stop()
        listener = null
        chunks = emptyList()
        index = 0
    }

    override fun release() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    /** [index] bo'lakni navbatga qo'yadi. Faqat asosiy oqimdan chaqiriladi. */
    private fun speakChunk(index: Int) {
        val tts = engine ?: return
        val chunk = chunks.getOrNull(index) ?: return
        this.index = index
        tts.speak(chunk.text, TextToSpeech.QUEUE_FLUSH, Bundle(), utteranceId(index))
    }

    private fun reportFailure() {
        main.post {
            val target = listener
            listener = null
            target?.onError(VoiceError.SPEAK_FAILED)
        }
    }

    private fun utteranceId(index: Int): String = "$UTTERANCE_PREFIX$index"

    private fun chunkIndex(utteranceId: String?): Int? {
        if (utteranceId == null || !utteranceId.startsWith(UTTERANCE_PREFIX)) return null
        return utteranceId.removePrefix(UTTERANCE_PREFIX).toIntOrNull()
    }

    private companion object {
        const val UTTERANCE_PREFIX = "ovozstudio:chunk:"

        /** Tezlik va balandlikning ruxsat etilgan chegarasi. */
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f
    }
}
