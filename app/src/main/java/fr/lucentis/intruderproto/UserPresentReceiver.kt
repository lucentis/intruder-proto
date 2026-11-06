
package fr.lucentis.intruderproto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class UserPresentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val preferences = context.getSharedPreferences(
            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

        preferences.edit()
            .putString(
                CameraCaptureService.KEY_LAST_CAPTURE_STATUS,
                "user_present_received"
            )
            .putLong(
                CameraCaptureService.KEY_LAST_CAPTURE_TIME,
                System.currentTimeMillis()
            )
            .apply()

        try {
            val serviceIntent = Intent(
                context,
                CameraCaptureService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

        } catch (exception: Exception) {
            preferences.edit()
                .putString(
                    CameraCaptureService.KEY_LAST_CAPTURE_STATUS,
                    "user_present_start_rejected:${exception.javaClass.simpleName}"
                )
                .putLong(
                    CameraCaptureService.KEY_LAST_CAPTURE_TIME,
                    System.currentTimeMillis()
                )
                .apply()
        }
    }
}
