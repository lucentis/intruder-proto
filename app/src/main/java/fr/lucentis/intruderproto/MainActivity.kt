
package fr.lucentis.intruderproto

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
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

    private val adminRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val component = ComponentName(
            this,
            IntruderDeviceAdminReceiver::class.java
        )

        val manager = getSystemService(DevicePolicyManager::class.java)

        adminActive.value = manager.isAdminActive(component)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val component = ComponentName(
            this,
            IntruderDeviceAdminReceiver::class.java
        )

        val manager = getSystemService(DevicePolicyManager::class.java)

        adminActive.value = manager.isAdminActive(component)

        setContent {
            IntruderProtoApp(
                isAdminActive = adminActive.value,
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
                }
            )
        }
    }
}

@Composable
private fun IntruderProtoApp(
    isAdminActive: Boolean,
    onActivateAdmin: () -> Unit
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

                Button(
                    onClick = onActivateAdmin,
                    modifier = Modifier.padding(top = 24.dp)
                ) {
                    Text("Activer la surveillance")
                }
            }
        }
    }
}
