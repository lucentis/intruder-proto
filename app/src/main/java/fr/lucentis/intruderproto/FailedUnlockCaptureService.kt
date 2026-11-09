
package fr.lucentis.intruderproto

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

class FailedUnlockCaptureService : Service() {

    private var cameraCapture: CameraCapture? = null

    private val finished = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            finish("permission_missing")
            return START_NOT_STICKY
        }

        try {
            createNotificationChannel()

            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } catch (exception: Exception) {
            finish("foreground_service_rejected:${exception.javaClass.simpleName}")
            return START_NOT_STICKY
        }

        cameraCapture = CameraCapture(this) { result ->
            finish(result)
        }

        cameraCapture?.start()

        return START_NOT_STICKY
    }

    private fun finish(result: String) {
        if (!finished.compareAndSet(false, true)) return

        getSharedPreferences(
            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_LAST_CAPTURE_STATUS, result)
            .putLong(KEY_LAST_CAPTURE_TIME, System.currentTimeMillis())
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }

        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Capture de sécurité",
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
            .setContentText("Capture de sécurité en cours")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "intruder_camera"
        private const val NOTIFICATION_ID = 1001

        const val KEY_LAST_CAPTURE_STATUS = "last_capture_status"
        const val KEY_LAST_CAPTURE_TIME = "last_capture_time"
    }
}
