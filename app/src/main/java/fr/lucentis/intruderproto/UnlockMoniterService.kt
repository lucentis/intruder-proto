
package fr.lucentis.intruderproto

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf

class UnlockMonitorService : Service() {

    private var userPresentReceiver: BroadcastReceiver? = null
    private var cameraCapture: CameraCapture? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        try {
            createNotificationChannel()

            val notification = createNotification()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val foregroundTypes =
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    if (
                        checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        0
                    }

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    foregroundTypes
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val foregroundTypes =
                    if (
                        checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    } else {
                        0
                    }

                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    foregroundTypes
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            saveMonitorStatus(STATUS_FOREGROUND_STARTED)

            registerUserPresentReceiver()

            saveMonitorStatus(STATUS_RECEIVER_REGISTERED)

            val preferences = getSharedPreferences(
                IntruderDeviceAdminReceiver.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )

            lastUnlockSuccessTime.value = preferences.getLong(
                KEY_LAST_UNLOCK_SUCCESS_TIME,
                0L
            ).takeIf { it > 0L }

            preferences.edit()
                .putBoolean(KEY_MONITOR_RUNNING, true)
                .apply()

            running.value = true
            lastError.value = null

        } catch (exception: Exception) {
            val status =
                "monitor_start_failed:${exception.javaClass.simpleName}"

            saveMonitorStatus(status)

            lastError.value = status
            running.value = false

            getSharedPreferences(
                IntruderDeviceAdminReceiver.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            ).edit()
                .putBoolean(KEY_MONITOR_RUNNING, false)
                .apply()

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

                saveMonitorStatus(STATUS_BROADCAST_RECEIVED)

                startCameraCapture()
            }
        }

        val filter = IntentFilter(Intent.ACTION_USER_PRESENT)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                receiver,
                filter,
                Context.RECEIVER_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }

        userPresentReceiver = receiver
    }

    private fun startCameraCapture() {
        if (checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            saveCaptureStatus("permission_missing")
            return
        }

        if (cameraCapture != null) {
            saveCaptureStatus("capture_already_running")
            return
        }

        try {
            cameraCapture = CameraCapture(this) { result ->
                saveCaptureStatus(result)
                cameraCapture = null
            }

            cameraCapture?.start()

        } catch (exception: Exception) {
            cameraCapture = null

            saveCaptureStatus(
                "capture_start_failed:${exception.javaClass.simpleName}"
            )
        }
    }

    private fun saveCaptureStatus(status: String) {
        getSharedPreferences(
            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(
                FailedUnlockCaptureService.KEY_LAST_CAPTURE_STATUS,
                status
            )
            .putLong(
                FailedUnlockCaptureService.KEY_LAST_CAPTURE_TIME,
                System.currentTimeMillis()
            )
            .apply()
    }

    private fun saveMonitorStatus(status: String) {
        lastStatus.value = status

        getSharedPreferences(
            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_LAST_MONITOR_STATUS, status)
            .apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Intruder Proto",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Service de surveillance des déverrouillages"
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
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
            .setContentText("Surveillance des déverrouillages en cours")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        userPresentReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
                // Le receiver peut déjà être désenregistré.
            }
        }

        userPresentReceiver = null

        cameraCapture?.cancel()
        cameraCapture = null

        running.value = false

        getSharedPreferences(
            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putBoolean(KEY_MONITOR_RUNNING, false)
            .apply()

        super.onDestroy()
    }

    companion object {
        private const val TAG = "UnlockMonitorService"

        const val CHANNEL_ID = "unlock_monitor"
        const val NOTIFICATION_ID = 1002

        const val KEY_LAST_UNLOCK_SUCCESS_TIME =
            "last_unlock_success_time"

        const val KEY_LAST_MONITOR_STATUS =
            "last_monitor_status"

        const val KEY_MONITOR_RUNNING =
            "monitor_running"

        const val STATUS_FOREGROUND_STARTED =
            "foreground_started"

        const val STATUS_RECEIVER_REGISTERED =
            "receiver_registered"

        const val STATUS_BROADCAST_RECEIVED =
            "broadcast_received"

        val running = mutableStateOf(false)
        val lastError = mutableStateOf<String?>(null)
        val lastStatus = mutableStateOf<String?>(null)
        val lastUnlockSuccessTime = mutableStateOf<Long?>(null)
    }
}
