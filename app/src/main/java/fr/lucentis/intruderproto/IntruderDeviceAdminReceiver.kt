package fr.lucentis.intruderproto

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle

class IntruderDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onPasswordFailed(
        context: Context,
        intent: Intent,
        user: UserHandle
    ) {
        saveEvent(context, EVENT_FAILED)
    }

    override fun onPasswordSucceeded(
        context: Context,
        intent: Intent,
        user: UserHandle
    ) {
        saveEvent(context, EVENT_SUCCEEDED)
    }

    private fun saveEvent(context: Context, event: String) {
        context.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_LAST_EVENT, event)
            .apply()
    }

    companion object {
        const val PREFERENCES_NAME = "intruder_events"
        const val KEY_LAST_EVENT = "last_event"

        const val EVENT_FAILED = "failed"
        const val EVENT_SUCCEEDED = "succeeded"
    }
}
