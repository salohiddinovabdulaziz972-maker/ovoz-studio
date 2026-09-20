package uz.ovozstudio.app.media

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import uz.ovozstudio.app.MainActivity
import uz.ovozstudio.app.R
import uz.ovozstudio.app.settings.AppSettingsStore
import uz.ovozstudio.app.settings.LocaleContext

/**
 * Uzoq ishni (audio-kitob yig'ish) ekran o'chganda ham davom ettiradigan
 * fon xizmati.
 *
 * Nega kerak. Bir kitobni ovozga aylantirish o'nlab daqiqadan soatlargacha
 * davom etadi. Ekran o'chib, ilova fonda qolgach, Android ikki narsa qiladi:
 *
 *  1. protsessorni **uxlatadi** — wake lock bo'lmasa, oqimlar to'xtab qoladi
 *     va ish faqat ekran yoqilgan paytda oldinga siljiydi;
 *  2. jarayonni «fon» deb hisoblab, xotira kerak bo'lganda **o'ldirishi**
 *     mumkin — soatlik ish yo'qoladi.
 *
 * Shuning uchun xizmat ikkalasini ham hal qiladi: foreground xizmat jarayonni
 * o'ldirishdan himoya qiladi, qisman wake lock (`PARTIAL_WAKE_LOCK`) esa
 * protsessorni uyg'oq tutadi (ekran o'chiq bo'lishi mumkin, u yonmaydi).
 *
 * Xizmat ishni O'ZI bajarmaydi: yig'ish `BookBuilder` da, ViewModel ichida
 * qoladi. Shu sababli bu fayl kichik — u faqat «ish ketmoqda, uxlamang» deb
 * turadi ([RecordingService] bilan bir xil naqsh).
 *
 * `dataSync` turi (API 29+): Android hujjatida u «fayllarni mahalliy qayta
 * ishlash»ni ham o'z ichiga oladi. Android 15 da bu tur uchun sutkasiga
 * ~6 soat chegara bor: chegaraga yetganda tizim [onTimeout] ni chaqiradi va
 * xizmat to'xtamasa, ilova yiqiladi — shuning uchun u yerda xizmat o'zini
 * to'xtatadi (yig'ish esa oddiy holatda, ilova ochiq tursa, davom etaveradi).
 */
class WorkService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
        // Tizim xizmatni qayta boshlamasin: ishning o'zi ViewModel bilan
        // birga yashaydi, xizmatning yolg'iz qayta ishga tushishi keraksiz.
        return START_NOT_STICKY
    }

    /** Android 15+: `dataSync` vaqt chegarasi tugadi. To'xtamasak, tizim ilovani yiqitadi. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    /**
     * Protsessorni uyg'oq tutadi.
     *
     * Chegara bilan olinadi: ish tugab, [stop] chaqirilmay qolsa ham, wake
     * lock cheksiz ushlanib batareyani yemasligi kerak.
     * Ruxsat bo'lmasa (`SecurityException`) xizmat baribir ishlaydi — faqat
     * ekran o'chganda protsessor uxlashi mumkin.
     */
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val power = getSystemService(PowerManager::class.java) ?: return
            val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            lock.setReferenceCounted(false)
            lock.acquire(MAX_HOLD_MS)
            wakeLock = lock
        }
    }

    private fun releaseWakeLock() {
        val lock = wakeLock
        wakeLock = null
        if (lock != null && lock.isHeld) runCatching { lock.release() }
    }

    /**
     * Matnlari tanlangan tilda bo'lgan kontekst ([RecordingService] dagi
     * sabab bilan bir xil: xizmat konteksti tizim tilida, tanlangan til esa
     * faqat Activity kontekstiga qo'llanadi).
     */
    private fun localized(): Context = runCatching {
        LocaleContext.apply(this, AppSettingsStore.inFiles(filesDir).load().language)
    }.getOrDefault(this)

    private fun buildNotification(): Notification {
        createChannel()
        val strings = localized()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(strings.getString(R.string.work_notification_title))
            .setContentText(strings.getString(R.string.work_notification_text))
            .setSmallIcon(R.drawable.ic_work)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val strings = localized()
        val channel = NotificationChannel(
            CHANNEL_ID,
            strings.getString(R.string.work_notification_channel),
            // Eng past muhimlik: bildirishnoma ovoz chiqarmasligi kerak.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = strings.getString(R.string.work_notification_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "kitob"

        /** `RecordingService` ning identifikatoridan (1) farqli bo'lishi shart. */
        private const val NOTIFICATION_ID = 2

        private const val WAKE_LOCK_TAG = "OvozStudio:kitob"

        /** 5,5 soat (millisekundda): Android 15 dagi 6 soatlik `dataSync` chegarasidan oldin. */
        private const val MAX_HOLD_MS = (5 * 60 + 30) * 60 * 1000L

        /** Uzoq ish boshlanganda chaqiriladi. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, WorkService::class.java),
            )
        }

        /** Ish tugaganda (yoki xato bilan to'xtaganda) chaqiriladi. */
        fun stop(context: Context) {
            context.stopService(Intent(context, WorkService::class.java))
        }
    }
}
