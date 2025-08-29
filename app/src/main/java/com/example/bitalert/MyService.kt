package com.example.bitalert

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.*

class BitAlertService : Service() {

    // --- UUIDs for BLE Service and Characteristic ---
    private val serviceUuid: UUID = UUID.fromString("0000b81d-0000-1000-8000-00805f9b34fb") // Unique UUID for BitAlert Service
    private val alertCharacteristicUuid: UUID = UUID.fromString("0000b81e-0000-1000-8000-00805f9b34fb") // Unique UUID for Alert Characteristic

    // --- Coroutine Scope ---
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- Bluetooth Components (Lazy Initialization) ---
    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }
    private val bleScanner: BluetoothLeScanner by lazy { bluetoothAdapter.bluetoothLeScanner }
    private val bleAdvertiser: BluetoothLeAdvertiser by lazy { bluetoothAdapter.bluetoothLeAdvertiser }
    private var gattServer: BluetoothGattServer? = null

    // --- Lifecycle Methods ---
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "BitAlertService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        Log.d(TAG, "BitAlertService started.")

        serviceScope.launch {
            startBleScanning()
            startBleAdvertising()
            startGattServer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BitAlertService is being destroyed.")
        serviceScope.cancel()
        stopBleScanning()
        stopBleAdvertising()
        stopGattServer()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // == GATT Server Implementation ==========================================================

    private fun startGattServer() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "GATT Server: Bluetooth Connect permission not granted.")
            return
        }
        gattServer = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).openGattServer(this, gattServerCallback)

        val service = BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            alertCharacteristicUuid,
            // Properties: Allow other devices to write to this characteristic
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_READ,
            // Permissions: Allow read and write operations
            BluetoothGattCharacteristic.PERMISSION_WRITE or BluetoothGattCharacteristic.PERMISSION_READ
        )
        service.addCharacteristic(characteristic)

        gattServer?.addService(service)
        Log.d(TAG, "GATT Server started and service added.")
    }

    private fun stopGattServer() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "GATT Server: Bluetooth Connect permission not granted.")
            return
        }
        gattServer?.close()
        gattServer = null
        Log.d(TAG, "GATT Server stopped.")
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Device connected: ${device?.address}")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Device disconnected: ${device?.address}")
            }
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int, characteristic: BluetoothGattCharacteristic?, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
            if (characteristic?.uuid == alertCharacteristicUuid) {
                val message = value?.toString(Charsets.UTF_8)
                Log.i(TAG, "Received alert from ${device?.address}: '$message'")
                // TODO: Process the received alert (e.g., display it, re-broadcast it)

                if (responseNeeded) {
                    if (ActivityCompat.checkSelfPermission(this@BitAlertService, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.e(TAG, "GATT Server: Bluetooth Connect permission not granted.")
                        return
                    }
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }
            }
        }
    }


    // == BLE Scanning ========================================================================
    // ... (previous scanning code remains unchanged)
    private fun startBleScanning() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth Scan permission not granted.")
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled.")
            return
        }

        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        bleScanner.startScan(listOf(scanFilter), scanSettings, scanCallback)
        Log.d(TAG, "BLE scanning started.")
    }

    private fun stopBleScanning() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth Scan permission not granted.")
            return
        }
        bleScanner.stopScan(scanCallback)
        Log.d(TAG, "BLE scanning stopped.")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                Log.i(TAG, "Found BitAlert device: ${device.address}")
                // TODO: Connect to the device's GATT server to exchange alert data
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
        }
    }


    // == BLE Advertising =====================================================================
    // ... (previous advertising code remains unchanged)
    private fun startBleAdvertising() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth Advertise permission not granted.")
            return
        }

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(serviceUuid))
            .build()

        bleAdvertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        Log.d(TAG, "BLE advertising started.")
    }

    private fun stopBleAdvertising() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Bluetooth Advertise permission not granted.")
            return
        }
        bleAdvertiser.stopAdvertising(advertiseCallback)
        Log.d(TAG, "BLE advertising stopped.")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.i(TAG, "BLE advertising started successfully.")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            Log.e(TAG, "BLE advertising onStartFailure: $errorCode")
        }
    }


    // == Notification Management =============================================================
    // ... (previous notification code remains unchanged)
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BitAlert is Active")
            .setContentText("Relaying alerts to nearby devices.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "BitAlert Service Channel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Channel for the BitAlert background relay service."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    companion object {
        private const val TAG = "BitAlertService"
        const val CHANNEL_ID = "BitAlertChannel"
        const val NOTIFICATION_ID = 1
    }
}
