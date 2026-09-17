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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import uz.ovozstudio.app.MainActivity
import uz.ovozstudio.app.R

/**
 * Yozib olish davomida ilovani tirik ushlab turadigan fon xizmati.
 *
 * Nima uchun kerak: ekran o'chsa yoki foydalanuvchi boshqa ilovaga o'tsa,
 * Android jarayonni «fon» deb hisoblaydi va uni istagan paytda o'ldirishi
 * mumkin. Bir soatlik yozuv shu paytda jimgina yo'qolardi. Foreground xizmat
 * jarayonni birinchi darajali qilib qo'yadi va tizim uni o'ldirmaydi.
 *
 * Xizmat yozuvni O'ZI boshqarmaydi: yozish yadrosi (`AudioRecorderEngine`)
 * ViewModel'da qoladi. Shu sababli bu fayl kichik va xavfsiz — u faqat
 * «yozish ketmoqda, bu ishni to'xtatib bo'lmaydi» deb turadi.
 *
 * `microphone` turi (API 29+) tizimga mikrofon bandligini to'g'ri e'lon
 * qiladi. Android 14'dan boshlab bu tur uchun alohida ruxsat kerak
 * (`FOREGROUND_SERVICE_MICROPHONE`) va xizmat faqat ilova ko'rinib turganda
 * boshlanishi mumkin — biz uni yozish tugmasi bosilganda, ekran ochiq
 * paytda boshlaymiz.
 */
class RecordingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Tizim xizmatni qayta boshlamasin: yozishni davom ettirish uchun
        // yadro kerak, u esa ViewModel bilan birga yashaydi.
        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.record_notification_title))
            .setContentText(getString(R.string.record_notification_text))
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(openApp)
            // Yozuv ketayotganda bildirishnomani supurib tashlab bo'lmaydi:
            // uni yo'qotish «yozish to'xtadi» degani emas, lekin shunday
            // ko'rinadi — foydalanuvchini chalg'itmaslik kerak.
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.record_notification_channel),
            // MUHIM: eng past muhimlik. O'rtacha muhimlikda bildirishnoma ovoz
            // chiqaradi va u ovoz to'g'ridan-to'g'ri yozuvga tushib qolardi.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.record_notification_channel_desc)
            setShowBadge(false)
        }
        // `getSystemService` null qaytarishi mumkin (nazariy holat) — kanal
        // yaratilmasa bildirishnoma sukut bo'yicha kanalga tushadi.
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "yozish"
        private const val NOTIFICATION_ID = 1

        /** Yozish boshlanganda chaqiriladi. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RecordingService::class.java),
            )
        }

        /** Yozish tugaganda (yoki xato bilan to'xtaganda) chaqiriladi. */
        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }
    }
}
