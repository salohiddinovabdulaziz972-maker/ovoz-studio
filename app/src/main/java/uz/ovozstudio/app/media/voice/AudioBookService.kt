package uz.ovozstudio.app.media.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import uz.ovozstudio.app.MainActivity
import uz.ovozstudio.app.R
import uz.ovozstudio.app.log.ErrorLog
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/** [AudioBookService] ga bitta ishni topshirish uchun kerakli hammasi. */
data class AudioBookRequest(
    val documentName: String,
    /** Bob-bob emas, tekis ro'yxat: xizmat bob tushunchasini bilmaydi. */
    val paragraphs: List<String>,
    val rate: Float,
    val enginePackage: String?,
    val voiceId: String?,
    /** Vaqtinchalik bo'lak fayllar shu yerda yaraladi va ish tugagach o'chadi. */
    val workDir: File,
    val destination: File,
)

/** Tugagan ishning natijasi. */
sealed interface AudioBookOutcome {
    data class Done(val file: File) : AudioBookOutcome
    data class Failed(val message: String) : AudioBookOutcome
    data object Cancelled : AudioBookOutcome
}

data class AudioBookState(
    val running: Boolean = false,
    val documentName: String = "",
    val stage: AudioBookExporter.Stage? = null,
    /** Joriy bosqichning foizi (0…1). */
    val progress: Float = 0f,
    val outcome: AudioBookOutcome? = null,
)

/**
 * Hujjatni MP3 audio-kitobga aylantirish ishining o'zi — ekran yopilsa ham,
 * ekran o'chsa ham davom etadi.
 *
 * Ish **shu yerda** ketadi, ViewModel ichida emas: agar ish ViewModel ichida
 * ketsa, foydalanuvchi ekrandan chiqqanda (masalan bosh ekranga qaytganda)
 * ViewModel tozalanib, ish yarim yo'lda to'xtab qolardi. Xizmat esa
 * ekrandan mustaqil yashaydi — foydalanuvchi ilovadan chiqib ketsa ham,
 * telefonni qulflab qo'ysa ham, ish davom etadi.
 *
 * Holat [state] orqali chiqadi: bitta jarayon ichida statik oqim, murakkab
 * `bindService` shart emas — ekran shunchaki shu oqimni kuzatadi, buyruqni
 * ([start], [cancel]) esa `Intent` orqali yuboradi. Ishga tushirish
 * paytidagi so'rov ([AudioBookRequest]) ham xuddi shu sababdan `Intent`
 * emas, oddiy maydon orqali uzatiladi: matn (butun kitob) o'nlab megabaytga
 * yetishi mumkin, `Intent` esa jarayonlararo xabar hisoblanib, hajmi
 * qattiq cheklangan (~1 MB) — bu yerda esa ikkalasi bir xil jarayonda.
 */
class AudioBookService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * Bekor qilish tugmasi bosilganda ko'tariladi. `job.cancel()` yetarli emas:
     * eksport bekor qilinishni har bo'lak orasida tekshiradi, oradagi bo'lak esa
     * sintez tugashini kutadi — shu oynada ish baribir muvaffaqiyatli tugashi
     * mumkin. U holda "bekor qilindi" bildirishnomasi ortidan "tayyor"
     * bildirishnomasi chiqib, fayl esa o'chirilgan bo'lardi. Bayroq tugmani
     * bosilgan-tugallangan ishdan ajratadi.
     */
    @Volatile
    private var cancelledFlag = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Foreground rejimi **hamma** shoxobda birinchi bo'lib e'lon qilinadi.
        // Sabab: xizmat `startForegroundService` bilan chaqirilgan, tizim esa
        // 5 soniya ichida `startForeground` ni talab qiladi. Bajarilmasa
        // `ForegroundServiceDidNotStartInTimeException` — ilova qulaydi.
        // `KeepAliveService` shu qoidani allaqachon yuritadi; bu xizmatda
        // ayniqsa bekor qilish shoxobi uni buzardi: ishi tugab bo'lgan
        // xizmatga kechikkan "bekor qil" tugmasi yangi nusxani uyg'otib,
        // hech qachon `startForeground` chaqirmasdan o'ldirilardi.
        createChannel()

        val idle = idleNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, idle, FOREGROUND_TYPE_MEDIA_PROCESSING)
        } else {
            startForeground(NOTIFICATION_ID, idle)
        }

        if (intent?.action == ACTION_CANCEL) {
            cancelledFlag = true
            if (job?.isActive == true) {
                job?.cancel() // `finally` bloki hammani tozalab, o'zi `stopSelf` qiladi
            } else {
                // Bekor qiladigan ish yo'q: xizmat shu yerda tamom. Pastdagi
                // umumiy shoxob kabi `stopSelf` chaqiriladi — bildirishnoma
                // osilib qolmaydi va `stopForeground` chaqirilgan bo'ladi.
                _state.update { it.copy(outcome = AudioBookOutcome.Cancelled) }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            return START_NOT_STICKY
        }

        if (job?.isActive == true) return START_NOT_STICKY // ish allaqachon ketyapti

        val request = pendingRequest
        pendingRequest = null
        if (request == null) {
            // So'rovsiz ishga tushirilgan — bunday holat bo'lmasligi kerak,
            // lekin xizmat jimgina osilib qolmasligi uchun darhol to'xtaydi.
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        runExport(request)
        return START_NOT_STICKY
    }

    private fun runExport(request: AudioBookRequest) {
        cancelledFlag = false // yangi ish: oldingi bekor qilish tugmasi unga tegishli emas
        _state.value = AudioBookState(running = true, documentName = request.documentName)

        // `job` **ataylab** shu yerda, ishga tushirishdan oldin yoziladi. Asosiy
        // oqim `startForeground` ning birinchi chaqiruvidan (yuqorida) keyin
        // qaytarilgan, ya'ni uning istisnosi allaqachon ortda qoldi. Endi bu
        // yerga qadar yetib kelgan har qanday shoxobda `job` biriktirilgan
        // bo'ladi, `finally` esa ishni har qanday xato yo'lida ham tozalaydi.
        job = scope.launch {
            notify(buildProgressNotification(request.documentName, null, 0f))

            val engine = DeviceTtsEngine(applicationContext, request.enginePackage)
            var lastNotified = -1
            try {
                if (!awaitPrepared(engine)) throw IOException("Ovoz dvigateli tayyor emas")
                engine.setVoice(request.voiceId)

                AudioBookExporter.export(
                    engine = engine,
                    paragraphs = request.paragraphs,
                    rate = request.rate,
                    work = request.workDir,
                    destination = request.destination,
                    isCancelled = { !isActive },
                    onProgress = { progress ->
                        _state.update { it.copy(stage = progress.stage, progress = progress.fraction) }
                        val percent = (progress.fraction * 100).toInt()
                        if (percent != lastNotified) {
                            lastNotified = percent
                            notify(buildProgressNotification(request.documentName, progress.stage, progress.fraction))
                        }
                    },
                )
                _state.update {
                    it.copy(running = false, outcome = AudioBookOutcome.Done(request.destination))
                }
                // "Bekor qil" tugmasi bosilgan-u, ish o'shandan keyin
                // muvaffaqiyatli tugagan bo'lsa — `.mmap` bilan qilingan
                // adashmasin: fayl haqiqatan tayyor.
                if (!cancelledFlag) notify(buildDoneNotification(request.documentName, success = true))
            } catch (cancelled: AudioBookExporter.Cancelled) {
                runCatching { request.destination.delete() }
                _state.update { it.copy(running = false, outcome = AudioBookOutcome.Cancelled) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                // `job.cancel()` chaqirilganda (masalan sintez kutilayotgan
                // paytda) coroutine mexanizmining o'zi shu istisnoni tashlaydi
                // — yuqoridagi `AudioBookExporter.Cancelled` bilan bir xil
                // natija: bekor qilindi, xato emas.
                runCatching { request.destination.delete() }
                _state.update { it.copy(running = false, outcome = AudioBookOutcome.Cancelled) }
            } catch (error: Throwable) {
                ErrorLog.error("audiobook.export", "Audio-kitob yasalmadi", error)
                runCatching { request.destination.delete() }
                _state.update {
                    it.copy(running = false, outcome = AudioBookOutcome.Failed(error.message ?: error.javaClass.simpleName))
                }
                notify(buildDoneNotification(request.documentName, success = false))
            } finally {
                // `job?.cancel()` shu blokni **ishga tushirmaydi**: coroutine
                // bekor qilinganda `finally` darhol bajariladi, keyin istisno
                // tashqariga otilib, keyingi kod umuman ishlamaydi. Shuning
                // uchun `Cancelled` istisnosi faqat `AudioBookExporter` ning
                // ichidan chiqqanda ishlaydi; `job.cancel()` bilan bekor
                // qilinganda esa bu blok kechikkan tugash xabaridan keyin
                // ishga tushadi. Ikkala holda ham tozalash — shu yerda.
                //
                // `withContext(NonCancellable)` — ataylab. Ish bekor qilinganda
                // coroutine allaqachon "bekor qilingan" holatda, shuning uchun
                // har qanday `suspend` chaqiruv (bu yerda `engine.release()`
                // ichidagi `delay`) darhol `CancellationException` tashlab,
                // dvigatelni **yopmasdan** qo'yib yuborardi: `TextToSpeech`
                // xizmatga ulangan holda qolib, keyingi eksport «band» xizmatga
                // urilib, ovozsiz tugardi. Tozalash tugagach blok baribir
                // istisno bilan chiqadi — bu `NonCancellable` ning maqsadi.
                withContext(NonCancellable) {
                    runCatching { engine.release() }
                }
                runCatching { request.workDir.deleteRecursively() }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    /** [VoiceEngine.prepare] ni suspend shaklga o'giradi. */
    private suspend fun awaitPrepared(engine: VoiceEngine): Boolean = suspendCancellableCoroutine { continuation ->
        engine.prepare { error ->
            if (continuation.isActive) continuation.resume(error == null)
        }
    }

    private fun notify(notification: Notification) {
        stopForegroundCompat()
        // Bildirishnoma ruxsati berilmagan bo'lishi mumkin (API 33+): bu holda
        // faqat bildirishnoma ko'rinmaydi, ish esa (foreground holat) baribir
        // davom etadi — `SecurityException` bilan yiqilmasligi kerak.
        try {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        } catch (ignored: SecurityException) {
            // Ruxsat yo'q — jimgina o'tiladi.
        }
    }

    /**
     * Foreground rejimidan chiqaradi, lekin bildirishnomani o'chirmaydi.
     *
     * Tugagan ish haqidagi xabar ham shu xizmatga tegishli, ya'ni u ham
     * foreground talab qiladi: `stopForeground` ni chaqirmasdan qolsa, tizim
     * 5 soniyadan keyin "xizmat foreground bo'lmadi" deb ilovani yiqitardi.
     * `STOP_FOREGROUND_DETACH` aynan shuni beradi — rejimdan chiqadi,
     * bildirishnoma esa joyida qoladi va foydalanuvchi uni bosib ilovani
     * ocha oladi.
     *
     * API 24–28 da bayroq yo'q: u yerda tizim foreground'ni boshqacha
     * kuzatadi va `stopForeground(true)` talab qilinmaydi.
     */
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
    }

    /** Ish yo'q paytda ko'rsatiladigan jimgina bildirishnoma — 5 soniya talabi uchun. */
    private fun idleNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_audiobook_title, ""))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun buildProgressNotification(documentName: String, stage: AudioBookExporter.Stage?, fraction: Float): Notification {
        val stageText = when (stage) {
            AudioBookExporter.Stage.SYNTHESIZING, null -> getString(R.string.notif_audiobook_stage_voice)
            AudioBookExporter.Stage.JOINING -> getString(R.string.notif_audiobook_stage_join)
            AudioBookExporter.Stage.ENCODING -> getString(R.string.notif_audiobook_stage_mp3)
        }
        val percent = (fraction * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_audiobook_title, documentName))
            .setContentText(getString(R.string.notif_audiobook_progress, stageText, percent))
            .setContentIntent(openAppIntent())
            .addAction(0, getString(R.string.notif_audiobook_cancel), cancelIntent())
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildDoneNotification(documentName: String, success: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_audiobook_title, documentName))
            .setContentText(
                getString(if (success) R.string.notif_audiobook_done else R.string.notif_audiobook_failed),
            )
            .setContentIntent(openAppIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, AudioBookService::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_audiobook),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "ovoz_studio_audiokitob"
        private const val NOTIFICATION_ID = 1002
        private const val ACTION_CANCEL = "uz.ovozstudio.app.action.CANCEL_AUDIOBOOK"

        /**
         * Manifestdagi `android:foregroundServiceType="mediaProcessing"` bilan
         * bir xil bo'lishi shart: nomlar mos kelmasa tizim xizmatni ishga
         * tushirmaydi.
         */
        private val FOREGROUND_TYPE_MEDIA_PROCESSING = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING

        /** [start] chaqirilishidan oldin shu yerga qo'yiladi — qarang: sinf hujjati. */
        @Volatile
        private var pendingRequest: AudioBookRequest? = null

        private val _state = MutableStateFlow(AudioBookState())

        /** Xizmatning joriy holati. Ish ketmayotganda ham oxirgi natija ([AudioBookState.outcome]) shu yerda qoladi. */
        val state: StateFlow<AudioBookState> = _state.asStateFlow()

        /** Yangi ish boshlaydi. Allaqachon ish ketayotgan bo'lsa e'tiborsiz qoldiriladi. */
        fun start(context: Context, request: AudioBookRequest) {
            if (_state.value.running) return
            pendingRequest = request
            ContextCompat.startForegroundService(context, Intent(context, AudioBookService::class.java))
        }

        fun cancel(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, AudioBookService::class.java).setAction(ACTION_CANCEL),
            )
        }

        /** Ko'rsatilgan natijani ([AudioBookState.outcome]) tozalaydi — foydalanuvchi xabarni yopgach. */
        fun clearOutcome() {
            _state.update { it.copy(outcome = null) }
        }
    }
}
