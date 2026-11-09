
package fr.lucentis.intruderproto

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : ComponentActivity() {

    private val adminActive = mutableStateOf(false)
    private val cameraPermissionGranted = mutableStateOf(false)
    private val lastLoginEvent = mutableStateOf<String?>(null)
    private val lastCaptureStatus = mutableStateOf<String?>(null)
    private val lastCaptureImage = mutableStateOf<Bitmap?>(null)

    private val monitorRunning = UnlockMonitorService.running
    private val monitorError = UnlockMonitorService.lastError
    private val monitorStatus = UnlockMonitorService.lastStatus

    private val adminRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshStatus()
    }

    private val cameraPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        refreshStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = ComponentName(
            this,
            IntruderDeviceAdminReceiver::class.java
        )

        setContent {
            IntruderProtoApp(
                isAdminActive = adminActive.value,
                cameraPermissionGranted = cameraPermissionGranted.value,
                lastLoginEvent = lastLoginEvent.value,
                lastCaptureStatus = lastCaptureStatus.value,
                lastCaptureImage = lastCaptureImage.value,
                monitorRunning = monitorRunning.value,
                monitorError = monitorError.value,
                monitorStatus = monitorStatus.value,
                onActivateAdmin = {
                    val intent = Intent(
                        DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                    ).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            component
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Autoriser la surveillance des tentatives de déverrouillage."
                        )
                    }

                    adminRequest.launch(intent)
                },
                onRequestCameraPermission = {
                    cameraPermissionRequest.launch(
                        Manifest.permission.CAMERA
                    )
                },
                onStartMonitor = {
                    val intent = Intent(
                        this,
                        UnlockMonitorService::class.java
                    )

                    try {
                        startForegroundService(intent)
                    } catch (exception: Exception) {
                        val status =
                            "monitor_start_failed:${exception.javaClass.simpleName}"

                        monitorError.value = status
                        monitorStatus.value = status

                        getSharedPreferences(
                            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
                            MODE_PRIVATE
                        ).edit()
                            .putString(
                                UnlockMonitorService.KEY_LAST_MONITOR_STATUS,
                                status
                            )
                            .putBoolean(
                                UnlockMonitorService.KEY_MONITOR_RUNNING,
                                false
                            )
                            .apply()
                    }
                },
                onStopMonitor = {
                    stopService(
                        Intent(this, UnlockMonitorService::class.java)
                    )
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val component = ComponentName(
            this,
            IntruderDeviceAdminReceiver::class.java
        )

        val manager = getSystemService(DevicePolicyManager::class.java)

        adminActive.value = manager.isAdminActive(component)

        cameraPermissionGranted.value =
            checkSelfPermission(Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        val preferences = getSharedPreferences(
            IntruderDeviceAdminReceiver.PREFERENCES_NAME,
            MODE_PRIVATE
        )

        lastLoginEvent.value = preferences.getString(
            IntruderDeviceAdminReceiver.KEY_LAST_EVENT,
            null
        )

        val captureStatus = preferences.getString(
            FailedUnlockCaptureService.KEY_LAST_CAPTURE_STATUS,
            null
        )

        lastCaptureStatus.value = captureStatus
        lastCaptureImage.value = loadLastCapture(captureStatus)

        val status = preferences.getString(
            UnlockMonitorService.KEY_LAST_MONITOR_STATUS,
            null
        )

        monitorStatus.value = status

        monitorError.value = status?.takeIf {
            it.startsWith("monitor_start_failed:")
        }
    }

    private fun loadLastCapture(status: String?): Bitmap? {
        if (status == null || !status.startsWith("success:")) {
            return null
        }

        val filename = status.substringAfter("success:")
        if (filename.isBlank()) return null

        val file = File(filesDir, "captures/$filename")
        if (!file.exists()) return null

        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            BitmapFactory.decodeFile(file.absolutePath, bounds)

            var sampleSize = 1
            while (
                bounds.outWidth / sampleSize > 1280 ||
                bounds.outHeight / sampleSize > 1280
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (exception: Exception) {
            null
        }
    }
}

@Composable
private fun IntruderProtoApp(
    isAdminActive: Boolean,
    cameraPermissionGranted: Boolean,
    lastLoginEvent: String?,
    lastCaptureStatus: String?,
    lastCaptureImage: Bitmap?,
    monitorRunning: Boolean,
    monitorError: String?,
    monitorStatus: String?,
    onActivateAdmin: () -> Unit,
    onRequestCameraPermission: () -> Unit,
    onStartMonitor: () -> Unit,
    onStopMonitor: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Intruder Proto",
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Le socle Android est prêt.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyLarge
                )

                Text(
                    text = if (isAdminActive) {
                        "Surveillance administrateur activée."
                    } else {
                        "Surveillance administrateur inactive."
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = if (cameraPermissionGranted) {
                        "Autorisation caméra accordée."
                    } else {
                        "Autorisation caméra non accordée."
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = when (lastLoginEvent) {
                        IntruderDeviceAdminReceiver.EVENT_FAILED ->
                            "Dernier événement : tentative échouée."

                        IntruderDeviceAdminReceiver.EVENT_SUCCEEDED ->
                            "Dernier événement : déverrouillage réussi après un échec."

                        else ->
                            "Aucun événement détecté pour le moment."
                    },
                    modifier = Modifier.padding(top = 16.dp)
                )

                Text(
                    text = "Résultat de la capture : ${
                        lastCaptureStatus ?: "Aucun essai pour le moment."
                    }",
                    modifier = Modifier.padding(top = 16.dp)
                )

                if (lastCaptureImage != null) {
                    Text(
                        text = "Dernière photo capturée",
                        modifier = Modifier.padding(top = 20.dp),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Image(
                        bitmap = lastCaptureImage.asImageBitmap(),
                        contentDescription = "Dernière photo capturée",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(top = 8.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = if (monitorRunning) {
                        "Surveillance des déverrouillages : active."
                    } else {
                        "Surveillance des déverrouillages : arrêtée."
                    },
                    modifier = Modifier.padding(top = 24.dp)
                )

                Text(
                    text = "État de débogage : ${
                        monitorStatus ?: "Aucun état enregistré."
                    }",
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (monitorError != null) {
                    Text(
                        text = "Erreur : $monitorError",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = onStartMonitor,
                    enabled = !monitorRunning,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Démarrer la surveillance")
                }

                Button(
                    onClick = onStopMonitor,
                    enabled = monitorRunning,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Arrêter la surveillance")
                }

                Button(
                    onClick = onActivateAdmin,
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Text("Activer la surveillance")
                }

                Button(
                    onClick = onRequestCameraPermission,
                    modifier = Modifier.padding(top = 12.dp)
                ) {
                    Text("Autoriser la caméra")
                }
            }
        }
    }
}
