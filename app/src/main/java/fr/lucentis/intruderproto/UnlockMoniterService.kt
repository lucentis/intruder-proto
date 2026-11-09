
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
import android.os.Build
import android.os.IBinder

class UnlockMonitorService : Service() {

    private var userPresentReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        try {
            createNotificationChannel()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification()
                )
            }

            registerUserPresentReceiver()

            val preferences = getSharedPreferences(
                IntruderDeviceAdminReceiver.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

            lastUnlockSuccessTime.value = preferences.getLong(
                KEY_LAST_UNLOCK_SUCCESS_TIME,
                0L
            ).takeIf { it > 0L }

            running.value = true
            lastError.value = null

        } catch (exception: Exception) {
            running.value = false
            lastError.value =
                "monitor_start_failed:${exception.javaClass.simpleName}"

            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun registerUserPresentReceiver() {
        if (userPresentReceiver != null) return

        val receiver = object : BroadcastReceiver() {

            override fun onReceive(
                context: Context,
                intent: Intent
            ) {
                if (intent.action != Intent.ACTION_USER_PRESENT) return

                val timestamp = System.currentTimeMillis()

                getSharedPreferences(
                    IntruderDeviceAdminReceiver.PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                ).edit()
                    .putLong(KEY_LAST_UNLOCK_SUCCESS_TIME, timestamp)
                    .apply()

                lastUnlockSuccessTime.value = timestamp
            }
        }

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        userPresentReceiver = receiver
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Surveillance des déverrouillages",
                NotificationManager.IMPORTANCE_LOW
            )

            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("Intruder Proto")
            .setContentText("Écoute des déverrouillages active")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        userPresentReceiver?.let {
            unregisterReceiver(it)
        }

        userPresentReceiver = null
        running.value = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "unlock_monitor"
        private const val NOTIFICATION_ID = 1002

        const val KEY_LAST_UNLOCK_SUCCESS_TIME =
            "last_unlock_success_time"

        val running = androidx.compose.runtime.mutableStateOf(false)
        val lastError = androidx.compose.runtime.mutableStateOf<String?>(null)
        val lastUnlockSuccessTime =
            androidx.compose.runtime.mutableStateOf<Long?>(null)
    }
}
