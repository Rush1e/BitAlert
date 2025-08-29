package com.example.bitalert

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bitalert.ui.theme.BitalertTheme

class MainActivity : ComponentActivity() {

    // An array of required permissions.
    // We add POST_NOTIFICATIONS for Android 13 (API 33) and above.
    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    // A launcher for requesting multiple permissions.
    private val multiplePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Check if all permissions were granted.
        val allPermissionsGranted = permissions.entries.all { it.value }
        if (allPermissionsGranted) {
            Log.d(TAG, "All permissions granted.")
            startBitAlertService()
        } else {
            // Handle the case where the user denies one or more permissions.
            // You might want to show a dialog explaining why the permissions are needed.
            Log.w(TAG, "Not all permissions were granted.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitalertTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BitAlertScreen(
                        onStartServiceClick = {
                            checkAndRequestPermissions()
                        }
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        // Check which permissions are already granted.
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            // All permissions are already granted, start the service.
            Log.d(TAG, "All permissions already granted.")
            startBitAlertService()
        } else {
            // Request the missing permissions.
            Log.d(TAG, "Requesting missing permissions: ${missingPermissions.joinToString()}")
            multiplePermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun startBitAlertService() {
        val intent = Intent(this, BitAlertService::class.java)
        ContextCompat.startForegroundService(this, intent)
        Log.i(TAG, "BitAlertService started.")
    }

    // Optional: Add a function to stop the service if you add a "Stop" button later.
    private fun stopBitAlertService() {
        val intent = Intent(this, BitAlertService::class.java)
        stopService(intent)
        Log.i(TAG, "BitAlertService stopped.")
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}

@Composable
fun BitAlertScreen(onStartServiceClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BitAlert Disaster Communication",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Press the button to start the relay service. This will allow your device to send and receive alerts from other nearby users without an internet connection.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onStartServiceClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Start Relay Service")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun BitAlertScreenPreview() {
    BitalertTheme {
        BitAlertScreen(onStartServiceClick = {})
    }
}
