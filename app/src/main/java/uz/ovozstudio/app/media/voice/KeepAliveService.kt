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
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import uz.ovozstudio.app.MainActivity
import uz.ovozstudio.app.R

/**
 * Jonli o'qish paytida jarayonni tirik saqlaydi. Hech qanday ovoz mantig'i
 * bu yerda yo'q — matnni ham, ovoz dvigatelini ham bilmaydi.
 *
 * Nega kerak. Ovoz dvigateli (`TextToSpeech`) [uz.ovozstudio.app.ui.reader.ReaderViewModel]
 * ichida yashaydi va ekran o'chganda ham ishlashda davom etadi — FAQAT
 * jarayonning o'zi o'ldirilmasa. Lekin Android fondagi (foreground bo'lmagan)
 * jarayonni istalgan payt, ayniqsa batareya tejash rejimida, o'chirib
 * qo'yishi mumkin. Foreground xizmat + bildirishnoma tizimga «bu ish muhim,
 * hozir o'chirmang» deydi.
 *
 * [ReaderViewModel] o'qish boshlaganda [start], to'xtaganda [stop] ni
 * chaqiradi. Ikkalasi ham darhol qaytadi.
 */
class KeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Har doim, harakatidan qat'i nazar, DARHOL foreground bo'ladi: xizmat
        // `startForegroundService` bilan boshlangan bo'lsa, tizim shuni birinchi
        // bir necha soniyada talab qiladi — hatto keyingi qadam uni to'xtatish
        // bo'lsa ham. Aks holda ekran o'chgan paytda «to'xtatish» chaqiruvining
        // o'zi ilovani yiqitib qo'yishi mumkin edi.
        createChannel()
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: getString(R.string.notif_reading_title)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(label),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            },
        )

        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        // Tizim xizmatni o'zi o'chirib qo'ysa, qayta o'zi boshlanmaydi:
        // bu shunchaki hamrohlik xizmati, kerak bo'lsa ViewModel yana so'raydi.
        return START_NOT_STICKY
    }

    private fun buildNotification(label: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(label)
            .setContentText(getString(R.string.notif_reading_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_reading), NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ovoz_studio_oqish"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_LABEL = "label"
        private const val ACTION_STOP = "uz.ovozstudio.app.action.STOP_READING"

        /** O'qish boshlanganda chaqiriladi. [label] — bildirishnomada ko'rinadigan nom (masalan hujjat nomi). */
        fun start(context: Context, label: String) {
            val intent = Intent(context, KeepAliveService::class.java).putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * O'qish to'xtaganda (pauza yoki hujjat tugaganda) chaqiriladi.
         *
         * `startForegroundService` bilan chaqiriladi — oddiy `startService`
         * emas: ekran o'chgan/fon holatida oddiy `startService` "fondan
         * xizmat boshlab bo'lmaydi" xatosi bilan yiqilishi mumkin edi.
         * Xizmatning o'zi darhol foreground bo'lib, shu zahoti to'xtaydi.
         */
        fun stop(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
