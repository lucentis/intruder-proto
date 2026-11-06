package fr.lucentis.intruderproto

import android.Manifest
import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserHandle

class IntruderDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(
        context: Context,
        intent: Intent,
        user: UserHandle
    ) {
        saveEvent(context, EVENT_FAILED)
        startCameraCapture(context)
    }

    override fun onPasswordSucceeded(
        context: Context,
        intent: Intent,
        user: UserHandle
    ) {
        saveEvent(context, EVENT_SUCCEEDED)
    }

    private fun startCameraCapture(context: Context) {
        if (context.checkSelfPermission(Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            saveCaptureStatus(context, "permission_missing")
            return
        }

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

            saveCaptureStatus(context, "service_requested")

        } catch (exception: Exception) {
            saveCaptureStatus(
                context,
                "service_start_rejected:${exception.javaClass.simpleName}"
            )
        }
    }

    private fun saveEvent(context: Context, event: String) {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_LAST_EVENT, event)
            .apply()
    }

    private fun saveCaptureStatus(
        context: Context,
        status: String
    ) {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(
                CameraCaptureService.KEY_LAST_CAPTURE_STATUS,
                status
            )
            .putLong(
                CameraCaptureService.KEY_LAST_CAPTURE_TIME,
                System.currentTimeMillis()
            )
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "intruder_events"
        const val KEY_LAST_EVENT = "last_event"

        const val EVENT_FAILED = "failed"
        const val EVENT_SUCCEEDED = "succeeded"
    }
}