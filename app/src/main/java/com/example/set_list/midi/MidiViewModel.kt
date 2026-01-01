package com.example.set_list.midi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.midi.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.set_list.data.SetlistRepository
import com.example.set_list.midi.RolandV71Config.SYSEX_END
import com.example.set_list.midi.RolandV71Config.SYSEX_START
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class MidiViewModel(application: Application, private val repository: SetlistRepository) : AndroidViewModel(application) {

    private val TAG = "MidiViewModel"
    private val midiManager = getApplication<Application>().getSystemService(MidiManager::class.java)
    private val bluetoothManager = getApplication<Application>().getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private val sharedPreferences = application.getSharedPreferences("midi_prefs", Context.MODE_PRIVATE)

    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var outputPort: MidiOutputPort? = null
    private val sysExReceiver = SysExReceiver()

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _connectionStatus = MutableStateFlow<String>("Not connected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _currentKit = MutableStateFlow<Int?>(null)
    val currentKit = _currentKit.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress = _syncProgress.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.d(TAG, "Bluetooth turned ON. Attempting to auto-connect.")
                        handler.postDelayed({ autoConnectToLastDevice() }, 1000)
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.d(TAG, "Bluetooth turned OFF. Disconnecting.")
                        disconnect()
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        application.registerReceiver(bluetoothStateReceiver, filter)
        autoConnectToLastDevice()
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val deviceName = try {
                device.name ?: "Unnamed Device"
            } catch (e: SecurityException) {
                "Unnamed Device"
            }
            val bleDevice = BleDevice(device.address, deviceName, device)

            if (_discoveredDevices.value.none { it.address == bleDevice.address }) {
                Log.d(TAG, "Device found: $deviceName (${device.address}) RSSI: ${result.rssi}")
                _discoveredDevices.value = (_discoveredDevices.value + bleDevice).sortedBy { it.name }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorText = when(errorCode) {
                ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started."
                ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Scan registration failed."
                ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal BLE error."
                ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE feature unsupported."
                else -> "BLE Scan Failed. Code: $errorCode"
            }
            Log.e(TAG, errorText)
            _errorMessage.value = errorText
            _isScanning.value = false
        }
    }

    fun hasBluetoothPermissions(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    fun startDeviceScan() {
        clearError()
        if (bleScanner == null) {
            _errorMessage.value = "Bluetooth LE not available on this device."
            return
        }
        if (bluetoothAdapter?.isEnabled == false) {
            _errorMessage.value = "Bluetooth is disabled. Please enable it."
            return
        }
        if (!hasBluetoothPermissions()) {
            _errorMessage.value = "Bluetooth permissions not granted."
            return
        }
        if (_isScanning.value) return

        _discoveredDevices.value = emptyList()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try {
            bleScanner.startScan(emptyList<ScanFilter>(), scanSettings, scanCallback)
            _isScanning.value = true
            Log.i(TAG, "BLE scan started")
            handler.postDelayed({ stopDeviceScan() }, 15000)
        } catch (e: SecurityException) {
            _errorMessage.value = "Bluetooth scan failed: Permission denied"
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDeviceScan() {
        if (!_isScanning.value) return
        try {
            bleScanner?.stopScan(scanCallback)
            Log.i(TAG, "BLE scan stopped")
        } catch (e: SecurityException) {
            _errorMessage.value = "Bluetooth scan failed: Permission denied"
        } finally {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceToConnect: BleDevice) {
        stopDeviceScan()
        _connectionStatus.value = "Connecting..."
        midiManager?.openBluetoothDevice(deviceToConnect.device, { openedDevice ->
            if (openedDevice == null) {
                _errorMessage.value = "Failed to open ${deviceToConnect.name}."
                _connectionStatus.value = "Connection failed"
                return@openBluetoothDevice
            }

            disconnect()

            midiDevice = openedDevice
            inputPort = openedDevice.openInputPort(0)

            if (inputPort == null) {
                _errorMessage.value = "Failed to open MIDI input port."
                _connectionStatus.value = "Connection failed"
                disconnect()
            } else {
                _connectedDevice.value = deviceToConnect
                _connectionStatus.value = "Connected to ${deviceToConnect.name}"
                saveLastConnectedDevice(deviceToConnect.address)
            }
        }, handler)
    }

    @SuppressLint("MissingPermission")
    fun autoConnectToLastDevice() {
        if (!hasBluetoothPermissions() || bluetoothAdapter?.isEnabled == false) {
            return
        }
        if (connectedDevice.value != null) return

        val lastDeviceAddress = sharedPreferences.getString("last_connected_device_address", null) ?: return

        try {
            val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
            val deviceToConnect = pairedDevices?.find { it.address == lastDeviceAddress }

            if (deviceToConnect != null) {
                Log.d(TAG, "Found last connected device: ${deviceToConnect.name}. Trying to connect...")
                val bleDevice = BleDevice(deviceToConnect.address, deviceToConnect.name ?: "Unknown Device", deviceToConnect)
                connectToDevice(bleDevice)
            }
        } catch (e: SecurityException) {
            _errorMessage.value = "Auto-connect failed: Permission denied."
        }
    }

    private fun saveLastConnectedDevice(address: String) {
        sharedPreferences.edit().putString("last_connected_device_address", address).apply()
    }

    fun switchToKit(kitNumber: Int, channel: Int = 9) {
        if (inputPort == null) {
            _errorMessage.value = "Not connected to any device."
            return
        }
        val programNumber = kitNumber - 1
        if (programNumber < 0 || programNumber > 127) {
            _errorMessage.value = "Invalid kit number."
            return
        }
        val buffer = ByteArray(2).apply {
            this[0] = (0xC0 + channel).toByte()
            this[1] = programNumber.toByte()
        }
        try {
            inputPort?.send(buffer, 0, buffer.size)
            _currentKit.value = kitNumber
        } catch (e: IOException) {
            _errorMessage.value = "Failed to send MIDI command: ${e.message}"
        }
    }

    fun syncKitNames() {
        if (midiDevice == null || inputPort == null) {
            _errorMessage.value = "Not connected. Cannot sync."
            return
        }
        if (_isSyncing.value) {
            _errorMessage.value = "Sync is already in progress."
            return
        }

        var tempOutputPort: MidiOutputPort? = null
        viewModelScope.launch {
            try {
                _isSyncing.value = true
                _syncProgress.value = 0f
                sysExReceiver.clearData()

                tempOutputPort = midiDevice?.openOutputPort(0)
                if (tempOutputPort == null) {
                    throw IOException("Failed to open output port for sync.")
                }
                tempOutputPort?.connect(sysExReceiver)

                for (kitNum in 1..RolandV71Config.TOTAL_KITS) {
                    val address = RolandV71Config.getKitNameAddress(kitNum)
                    val request = createSysExRequest(address)
                    inputPort?.send(request, 0, request.size)
                    _syncProgress.value = kitNum.toFloat() / RolandV71Config.TOTAL_KITS
                    kotlinx.coroutines.delay(200)
                }

                kotlinx.coroutines.delay(1000)

            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message}"
                Log.e(TAG, "Sync failed", e)
            } finally {
                _isSyncing.value = false
                _syncProgress.value = 0f
                try {
                    tempOutputPort?.disconnect(sysExReceiver)
                    tempOutputPort?.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing temporary output port after sync", e)
                }
                processAndSaveSysExData(sysExReceiver.receivedData)
            }
        }
    }

    private fun createSysExRequest(address: ByteArray): ByteArray {
        val size = RolandV71Config.KIT_NAME_LENGTH.toLong()
        val sizeBytes = RolandV71Config.longTo4ByteArray(size)
        val payload = byteArrayOf(
            RolandV71Config.ROLAND_ID,
            RolandV71Config.DEVICE_ID,
            *RolandV71Config.MODEL_ID,
            RolandV71Config.COMMAND_RQ1,
            *address,
            *sizeBytes
        )
        val checksum = calculateChecksum(payload)
        return byteArrayOf(SYSEX_START, *payload, checksum, SYSEX_END)
    }

    private fun calculateChecksum(payload: ByteArray): Byte {
        val sum = payload.sumOf { it.toInt() and 0xFF }
        return ((128 - (sum % 128)) and 0x7F).toByte()
    }

    private fun processAndSaveSysExData(data: List<ByteArray>) {
        viewModelScope.launch {
            for (message in data) {
                if (message.size < 18 || message[0] != SYSEX_START || message.last() != SYSEX_END) continue
                if (message[5] != RolandV71Config.COMMAND_DT1) continue

                val address = message.sliceArray(6..9)
                val kitNumber = RolandV71Config.getKitNumberFromAddress(address)
                val nameBytes = message.sliceArray(10 until 10 + RolandV71Config.KIT_NAME_LENGTH)
                val kitName = String(nameBytes).trim()

                if (kitName.isNotBlank()) {
                    repository.updateKitName(kitNumber, kitName)
                }
            }
        }
    }

    fun disconnect() {
        try {
            inputPort?.close()
            outputPort?.close()
            midiDevice?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing MIDI resources", e)
        } finally {
            inputPort = null
            outputPort = null
            midiDevice = null
            _connectedDevice.value = null
            _connectionStatus.value = "Not connected"
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDeviceScan()
        disconnect()
        getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
    }

    inner class SysExReceiver : MidiReceiver() {
        val receivedData = mutableListOf<ByteArray>()
        fun clearData() = receivedData.clear()
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            if (msg.getOrNull(offset) == SYSEX_START && msg.getOrNull(offset + count - 1) == SYSEX_END) {
                receivedData.add(msg.copyOfRange(offset, offset + count))
            }
        }
    }
}
