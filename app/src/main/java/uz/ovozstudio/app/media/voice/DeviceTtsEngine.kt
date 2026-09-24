package uz.ovozstudio.app.media.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.io.File
import java.util.Locale

/**
 * Qurilmaning o'z ovoz sintezatori (`android.speech.tts.TextToSpeech`).
 *
 * Bu — [VoiceEngine] ning amalga oshirilishi. Dvigatel ikki xil tanlanadi:
 *  - [enginePackage] — qaysi dastur sintez qiladi (`null` — tizimning
 *    standart dvigateli). Microsoft Sardor va Madina kabi ovozlar odatda
 *    alohida dvigatel dasturida keladi va faqat shu dvigatel tanlanganda
 *    ko'rinadi;
 *  - [setVoice] — dvigateldagi aniq ovoz.
 *
 * Ovoz tanlanmasa, til matnning yozuvidan aniqlanadi ([ScriptDetector]):
 * o'zbek ovozi **bo'lsa** o'zbek tilida o'qiydi, bo'lmasa zaxira tilga
 * o'tadi.
 *
 * Dvigatel matnni bo'laklarga **o'zi** bo'ladi ([TextChunker]): sintezator
 * bitta chaqiruvda cheklangan uzunlikni qabul qiladi, chegaradan uzun matnni
 * esa jimgina tashlab ketadi.
 */
class DeviceTtsEngine(
    context: Context,
    private val enginePackage: String? = null,
) : VoiceEngine {

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

    /** Foydalanuvchi tanlagan ovoz; `null` — til bo'yicha avtomatik. */
    private var voiceId: String? = null

    /** Navbatdagi bo'laklar va hozir o'qilayotganining indeksi. */
    private var chunks: List<SpeechChunk> = emptyList()
    private var index = 0

    private var listener: SpeechListener? = null

    /**
     * Faylga sintez qilish holati. Bir vaqtda faqat bitta bo'lishi mumkin —
     * shuning uchun navbat emas, bitta callback yetarli.
     */
    private var fileCallback: ((SynthesisResult) -> Unit)? = null
    private var fileUtteranceId: String? = null
    private var fileDestination: File? = null
    private var fileCounter = 0

    /** Tayyorlash tugaganda chaqiriladigan callback (bir marta). */
    private var pendingInit: ((VoiceError?) -> Unit)? = null

    private val progress = object : UtteranceProgressListener() {

        override fun onStart(utteranceId: String?) {
            if (utteranceId != null && utteranceId.startsWith(FILE_PREFIX)) { // faylga yozishda boshlanish e'lon qilinmaydi
                // Faqat "aytiladigan" rejimda ahamiyatsiz: ayni damda kutilayotgan
                // so'z bo'lagi bo'lsa ham, fayl yozuvi uni boshlab yubormaydi.
                return
            }
            val i = chunkIndex(utteranceId) ?: return
            main.post {
                val current = chunks
                if (i >= current.size) return@post
                index = i
                listener?.onStarted(i, current.size, current[i])
            }
        }

        override fun onDone(utteranceId: String?) {
            if (utteranceId != null && utteranceId.startsWith(FILE_PREFIX)) {
                main.post { finishFile(utteranceId, SynthesisResult.Done) }
                return
            }
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
            if (utteranceId != null && utteranceId.startsWith(FILE_PREFIX)) {
                main.post { finishFile(utteranceId, SynthesisResult.Failed(VoiceError.SPEAK_FAILED)) }
                return
            }
            reportFailure()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            if (utteranceId != null && utteranceId.startsWith(FILE_PREFIX)) {
                main.post { finishFile(utteranceId, SynthesisResult.Failed(VoiceError.SPEAK_FAILED)) }
                return
            }
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
        val started = TextToSpeech(
            appContext,
            TextToSpeech.OnInitListener { status ->
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
            },
            enginePackage,
        )
        engine = started
        started.setOnUtteranceProgressListener(progress)
    }

    override fun isReady(): Boolean = ready && engine != null

    override fun voices(): List<VoiceInfo> {
        val voices = availableVoices() ?: return emptyList()
        return voices.map { voice ->
            VoiceInfo(
                id = voice.name.orEmpty(),
                localeTag = voice.locale?.toLanguageTag().orEmpty(),
                name = voice.name.orEmpty(),
                quality = voice.quality,
            )
        }.sortedBy { it.localeTag }
    }

    override fun engines(): List<TtsEngineInfo> {
        val list = try {
            engine?.engines
        } catch (error: Exception) {
            null
        } ?: return emptyList()
        return list.map { info ->
            val packageName = info.name.orEmpty()
            TtsEngineInfo(
                packageName = packageName,
                label = info.label.orEmpty().ifEmpty { packageName },
            )
        }
    }

    override fun setVoice(id: String?) {
        voiceId = id
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

        applyVoice(tts, request)

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

    override fun synthesizeToFile(request: SpeechRequest, destination: File, onResult: (SynthesisResult) -> Unit) {        val tts = engine
        if (tts == null || !ready) {
            onResult(SynthesisResult.Failed(VoiceError.NOT_AVAILABLE))
            return
        }
        if (request.text.isBlank()) {
            onResult(SynthesisResult.Failed(VoiceError.EMPTY_TEXT))
            return
        }

        applyVoice(tts, request)
        tts.setSpeechRate(request.rate.coerceIn(MIN_RATE, MAX_RATE))
        tts.setPitch(request.pitch.coerceIn(MIN_RATE, MAX_RATE))

        // Eski faylga yozish (bo'lsa) hali tugallanmagan deb hisoblanadi:
        // uning callback'i endi chaqirilmaydi, fayli esa chala qoladi —
        // buni chaqiruvchi (bitta vaqtda bittadan chaqirgani uchun) kutmaydi.
        fileCallback = onResult
        val utteranceId = "$FILE_PREFIX${fileCounter++}"
        fileUtteranceId = utteranceId
        fileDestination = destination

        val started = tts.synthesizeToFile(request.text, Bundle(), destination, utteranceId)
        if (started != TextToSpeech.SUCCESS) {
            finishFile(utteranceId, SynthesisResult.Failed(VoiceError.SPEAK_FAILED))
        }
    }

    override fun stop() {
        engine?.stop()
        listener = null
        chunks = emptyList()
        index = 0
        cancelFile()
    }

    /**
     * Jonli [speak] dan farqli — u yerda ataylab kichikroq chegara olinadi
     * (bo'lak ikki daqiqadan uzoq o'qilib, «to'xtat» tugmasini foydasiz
     * qilmasligi uchun). Faylga yozishda bunday cheklov yo'q: sintez
     * orqaga qaytarib bo'lmaydi va foydalanuvchi kutishni boshlashdan oldin
     * ko'radi, shuning uchun chegara [SYNTH_CHARS] gacha ko'tariladi.
     */
    override fun maxSynthChars(): Int = minOf(SYNTH_CHARS, TextToSpeech.getMaxSpeechInputLength())


    override fun release() {
        stop()
        engine?.shutdown()
        engine = null
        ready = false
    }

    /**
     * Ovozni qo'yadi: tanlangan ovoz bo'lsa o'sha, bo'lmasa til bo'yicha.
     *
     * Tartib muhim. `setLanguage` ovozni shu tilning **standart** ovoziga
     * qaytarib yuboradi, ya'ni aniq tanlangan ovozdan keyin chaqirilsa,
     * tanlovni bekor qilardi. Shuning uchun til faqat ovoz tanlanmagan
     * (yoki qo'yib bo'lmagan) holda beriladi.
     */
    private fun applyVoice(tts: TextToSpeech, request: SpeechRequest) {
        val wanted = voiceId
        if (wanted != null) {
            val voice = availableVoices()?.firstOrNull { it.name == wanted }
            if (voice != null && tts.setVoice(voice) == TextToSpeech.SUCCESS) return
        }

        // Til: aniq berilgan bo'lsa o'sha, aks holda matnning yozuvidan.
        val tag = request.languageTag ?: resolveLanguage(ScriptDetector.detect(request.text))
        if (tag != null) {
            tts.language = Locale.forLanguageTag(tag)
        }
    }

    /**
     * Dvigateldagi ovozlar. Ba'zi dvigatellar ovozlar ro'yxatini so'ralganda
     * istisno tashlaydi — bu holda ro'yxat bo'sh deb hisoblanadi, ilova
     * qulamaydi.
     */
    private fun availableVoices(): Set<Voice>? = try {
        engine?.voices
    } catch (error: Exception) {
        null
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

    /**
     * Faylga yozish tugadi (natijasidan qat'i nazar) — faqat hozirgi kutilayotgan
     * chaqiruv uchun; eskisidan kelgan kechikkan xabar shu tarzda e'tiborsiz
     * qoladi (identifikator allaqachon almashtirilgan bo'ladi).
     */
    private fun finishFile(utteranceId: String, result: SynthesisResult) {
        // Tasdiqlash **shu yerda** — chaqiruvchining oqimida. Ilgari tekshiruv
        // to'g'ridan-to'g'ri `UtteranceProgressListener` ichida, ya'ni TTS
        // binder oqimida bajarilardi: `fileUtteranceId` ga u yerdan o'qilardi,
        // yozilardi esa asosiy oqimdan. ARM'da bu xotira to'siqsiz ko'rinishi
        // mumkin — eskirgan `null` o'qilib, tugash xabari jimgina tashlab
        // yuborilardi, `synthesizeOne` esa abadiy osilib qolardi: eksport bir
        // foizda qotib qolar, na xato, na taraqqiyot.
        if (utteranceId != fileUtteranceId) return // eskirgan xabar — e'tiborsiz
        val callback = fileCallback ?: return
        fileCallback = null
        fileUtteranceId = null
        fileDestination = null
        callback(result)
    }

    /** Kutilayotgan faylga yozishni bekor qiladi ([stop] yoki [release] chaqirilganda). */
    private fun cancelFile() {
        val destination = fileDestination
        val callback = fileCallback
        fileCallback = null
        fileUtteranceId = null
        fileDestination = null
        // `finishFile` orqali emas: bu yerda tasdiq shart emas — chaqiruvchi
        // o'zi bekor qilmoqda, kechikkan tugash xabari esa yuqoridagi
        // tozalashdan keyin mos kelmaydi va jimgina tushib qoladi.
        callback?.invoke(SynthesisResult.Failed(VoiceError.SPEAK_FAILED))
        // Chala fayl qolib ketmasin: yarim yozilgan audio "tayyor" deb
        // ulanib qolishi mumkin edi.
        if (destination != null) runCatching { destination.delete() }
    }

    private fun utteranceId(index: Int): String = "$UTTERANCE_PREFIX$index"

    private fun chunkIndex(utteranceId: String?): Int? {
        if (utteranceId == null || !utteranceId.startsWith(UTTERANCE_PREFIX)) return null
        return utteranceId.removePrefix(UTTERANCE_PREFIX).toIntOrNull()
    }

    private companion object {
        const val UTTERANCE_PREFIX = "ovozstudio:chunk:"
        const val FILE_PREFIX = "ovozstudio:file:"

        /** Tezlik va balandlikning ruxsat etilgan chegarasi. */
        const val MIN_RATE = 0.5f
        const val MAX_RATE = 2.0f

        /**
         * Faylga sintez qilinadigan bitta bo'lakning eng katta hajmi.
         *
         * Nega tizim chegarasidan ([TextToSpeech.getMaxSpeechInputLength],
         * odatda 4000) **kattaroq**. Sintezator bo'lakni tayyorlash uchun har
         * chaqiruvda qo'shimcha ish qiladi, ya'ni umumiy vaqt chaqiruvlar
         * soniga bog'liq. 4000 dan 30000 ga o'tish chaqiruvlar sonini sakkiz
         * barobar kamaytiradi — kitob esa shu nisbatda tezroq yasaladi.
         *
         * Xavfsizlik ikki tomondan ta'minlangan. Birinchidan, bu chegara
         * **texnik** emas: u yerda uzun matnni bo'laklarga bo'lish sintezator
         * ichida o'zi bor, ya'ni matn to'liq o'qiladi. Ikkinchidan, natija
         * jimgina chala qolmaydi — `synthesizeToFile` holat qaytaradi, u esa
         * [synthesizeToFile] ichida xatoga aylanadi va ekranga `FAILED` bo'lib
         * yetib boradi. Ya'ni sinovsiz kattalashtirishning eng yomon oqibati —
         * «juda katta bo'ldi» degan xato, jim qolgan yarim kitob emas.
         *
         * Pastdagi `minOf` chegarani **hech qachon** tizim chegarasidan
         * oshirmaydi: qurilmada u 2000 bo'lsa, bo'lak 2000 bo'lib qoladi.
         */
        const val SYNTH_CHARS = 30_000
    }
}
