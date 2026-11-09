package fr.lucentis.intruderproto

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

class UnlockMonitorService : Service() {

    // Manifest-declared receivers no longer get USER_PRESENT, so it is registered here at runtime
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_USER_PRESENT) return

            context.getSharedPreferences(
                IntruderDeviceAdminReceiver.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            ).edit()
                .putLong(KEY_LAST_UNLOCK_SUCCESS_TIME, System.currentTimeMillis())
                .apply()
        }
    }

    private var receiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running.value = true
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        try {
            createNotificationChannel()

            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } catch (exception: Exception) {
            lastError.value =
                "foreground_service_rejected:${exception.javaClass.simpleName}"
            stopSelf()
            return START_NOT_STICKY
        }

        lastError.value = null
        registerUnlockReceiver()

        return START_STICKY
    }

    private fun registerUnlockReceiver() {
        if (receiverRegistered) return

        ContextCompat.registerReceiver(
            this,
            unlockReceiver,
            IntentFilter(Intent.ACTION_USER_PRESENT),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        receiverRegistered = true
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Écoute du déverrouillage",
            NotificationManager.IMPORTANCE_LOW
        )

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Intruder Proto")
            .setContentText("Écoute du déverrouillage active")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(unlockReceiver)
            receiverRegistered = false
        }

        running.value = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "intruder_unlock_monitor"
        private const val NOTIFICATION_ID = 1002

        const val KEY_LAST_UNLOCK_SUCCESS_TIME = "last_unlock_success_time"

        // Observed directly by the UI; the service and the activity share one process
        val running = mutableStateOf(false)
        val lastError = mutableStateOf<String?>(null)
    }
}