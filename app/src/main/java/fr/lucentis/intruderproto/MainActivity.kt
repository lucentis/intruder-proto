package fr.lucentis.intruderproto

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val adminActive = mutableStateOf(false)
    private val cameraPermissionGranted = mutableStateOf(false)
    private val lastLoginEvent = mutableStateOf<String?>(null)
    private val lastCaptureStatus = mutableStateOf<String?>(null)

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

        lastCaptureStatus.value = preferences.getString(
            CameraCaptureService.KEY_LAST_CAPTURE_STATUS,
            null
        )
    }
}

@Composable
private fun IntruderProtoApp(
    isAdminActive: Boolean,
    cameraPermissionGranted: Boolean,
    lastLoginEvent: String?,
    lastCaptureStatus: String?,
    onActivateAdmin: () -> Unit,
    onRequestCameraPermission: () -> Unit
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