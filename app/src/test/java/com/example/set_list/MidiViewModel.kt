package com.example.set_list

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class BleDevice(val address: String, val name: String, val device: BluetoothDevice)

class MidiViewModel(application: Application, private val repository: SetlistRepository) : AndroidViewModel(application) {

    private val TAG = "MidiViewModel"
    private val PREFS_NAME = "midi_prefs"
    private val PREF_KEY_LAST_DEVICE = "last_mac_address"

    private val midiManager = getApplication<Application>().getSystemService(MidiManager::class.java)
    private val bluetoothManager = getApplication<Application>().getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = getApplication<Application>().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
    private var outputPort: MidiOutputPort? = null
    private val sysExReceiver = SysExReceiver()
    private val responseReceived = AtomicBoolean(false)

    private val _discoveredDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BleDevice?>(null)
    val connectedDevice = _connectedDevice.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _connectionStatus = MutableStateFlow("Not connected")
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _currentKit = MutableStateFlow<Int?>(null)
    val currentKit = _currentKit.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress = _syncProgress.asStateFlow()

    private val _lastSyncedKitName = MutableStateFlow("")
    val lastSyncedKitName = _lastSyncedKitName.asStateFlow()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_ON) {
                    Log.i(TAG, "Bluetooth turned ON, attempting auto-connect.")
                    autoConnectToLastDevice()
                }
            }
        }
    }

    init {
        getApplication<Application>().registerReceiver(bluetoothStateReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        autoConnectToLastDevice()
    }

    @SuppressLint("MissingPermission")
    private fun autoConnectToLastDevice() {
        if (!hasBluetoothPermissions() || bluetoothAdapter?.isEnabled == false || _connectedDevice.value != null) {
            return
        }
        val lastMac = prefs.getString(PREF_KEY_LAST_DEVICE, null) ?: return

        val bondedDevice = bluetoothAdapter?.bondedDevices?.find { it.address == lastMac }
        if (bondedDevice != null) {
            Log.i(TAG, "Found last device in bonded devices. Connecting...")
            val bleDevice = BleDevice(bondedDevice.address, bondedDevice.name ?: "V71", bondedDevice)
            connectToDevice(bleDevice)
        } else {
            Log.w(TAG, "Last connected device $lastMac not found in bonded devices.")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = try { device.name ?: "Unknown" } catch (e: SecurityException) { "Unknown" }
            val ble = BleDevice(device.address, name, device)
            if (_discoveredDevices.value.none { it.address == ble.address }) {
                _discoveredDevices.value = (_discoveredDevices.value + ble).sortedBy { it.name }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startDeviceScan() {
        clearError()
        if (bleScanner == null || bluetoothAdapter?.isEnabled == false || !hasBluetoothPermissions()) {
            _errorMessage.value = "Bluetooth is not enabled or permissions are missing."
            return
        }
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        bleScanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        handler.postDelayed({ stopDeviceScan() }, 10000) // Scan for 10 seconds
    }

    @SuppressLint("MissingPermission")
    fun stopDeviceScan() {
        if (_isScanning.value) {
            bleScanner?.stopScan(scanCallback)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceToConnect: BleDevice) {
        stopDeviceScan()
        _connectionStatus.value = "Connecting..."
        midiManager?.openBluetoothDevice(deviceToConnect.device, { openedDevice ->
            if (openedDevice == null) {
                _errorMessage.value = "Connection failed: Could not open device."
                _connectionStatus.value = "Connection failed"
                return@openBluetoothDevice
            }
            disconnect() // Close any existing connection first
            midiDevice = openedDevice
            inputPort = openedDevice.openInputPort(0)
            outputPort = openedDevice.openOutputPort(0)

            if (inputPort != null && outputPort != null) {
                outputPort?.connect(sysExReceiver)
                _connectedDevice.value = deviceToConnect
                _connectionStatus.value = "Connected"
                // Save device address for auto-reconnect
                prefs.edit().putString(PREF_KEY_LAST_DEVICE, deviceToConnect.address).apply()
            } else {
                _errorMessage.value = "Could not open MIDI ports."
                disconnect()
            }
        }, handler)
    }

    fun syncKitNames() {
        if (inputPort == null || _isSyncing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isSyncing.value = true
                _syncProgress.value = 0f
                _lastSyncedKitName.value = "Starting sync..."
                sysExReceiver.clearData()

                for (kitNum in 1..RolandV71Config.TOTAL_KITS) {
                    responseReceived.set(false)
                    val address = RolandV71Config.getKitNameAddress(kitNum)
                    val request = createSysExRequest(address)
                    inputPort?.send(request, 0, request.size)

                    var attempts = 0
                    while (!responseReceived.get() && attempts < 30) {
                        delay(20)
                        attempts++
                    }
                    
                    if (responseReceived.get()) {
                         val lastPacket = synchronized(sysExReceiver.receivedData) { sysExReceiver.receivedData.lastOrNull() }
                         lastPacket?.let {
                            val kitName = extractKitName(it)
                             _lastSyncedKitName.value = "Kit $kitNum: $kitName"
                             if (kitName.isNotBlank()) {
                                 repository.updateKitName(kitNum, kitName)
                             }
                         }
                    } else {
                        _lastSyncedKitName.value = "Kit $kitNum: Timeout..."
                    }

                    _syncProgress.value = kitNum.toFloat() / RolandV71Config.TOTAL_KITS
                    delay(10) // Small delay to not overwhelm the device
                }

                _lastSyncedKitName.value = "Sync Complete!"
                delay(2000) // Show complete message

            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message}"
                Log.e(TAG, "Sync failed", e)
            } finally {
                _isSyncing.value = false
                _syncProgress.value = 0f
                _lastSyncedKitName.value = ""
            }
        }
    }
    
    private fun extractKitName(msg: ByteArray): String {
        return try {
            if (msg.size >= 18 && msg[5] == RolandV71Config.COMMAND_DT1) {
                val nameBytes = msg.sliceArray(10 until 10 + RolandV71Config.KIT_NAME_LENGTH)
                String(nameBytes).trim().replace("\u0000", "")
            } else {
                "Invalid Packet"
            }
        } catch (e: Exception) { "Parse Error" }
    }

    fun switchToKit(kitNumber: Int, channel: Int = RolandV71Config.MIDI_CHANNEL) {
        if (inputPort == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msg = byteArrayOf((0xC0 + channel).toByte(), (kitNumber - 1).toByte())
                inputPort?.send(msg, 0, msg.size)
                _currentKit.value = kitNumber
            } catch (e: IOException) {
                Log.e(TAG, "Failed to send switch command", e)
                _errorMessage.value = "Kit switch failed."
            }
        }
    }

    private fun createSysExRequest(address: ByteArray): ByteArray {
        val sizeBytes = RolandV71Config.longTo4ByteArray(RolandV71Config.KIT_NAME_LENGTH.toLong())
        val payload = byteArrayOf(
            RolandV71Config.ROLAND_ID, RolandV71Config.DEVICE_ID,
            *RolandV71Config.MODEL_ID, RolandV71Config.COMMAND_RQ1,
            *address, *sizeBytes
        )
        val checksum = calculateChecksum(payload)
        return byteArrayOf(SYSEX_START, *payload, checksum, SYSEX_END)
    }

    private fun calculateChecksum(payload: ByteArray): Byte {
        val sum = payload.sumOf { it.toInt() and 0xFF }
        return ((128 - (sum % 128)) and 0x7F).toByte()
    }

    fun disconnect() {
        try {
            outputPort?.disconnect(sysExReceiver)
            inputPort?.close()
            outputPort?.close()
            midiDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error during disconnect", e)
        }
        inputPort = null
        outputPort = null
        midiDevice = null
        _connectedDevice.value = null
        _connectionStatus.value = "Not connected"
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        try {
            getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bluetooth receiver was not registered.")
        }
    }

    fun clearError() { _errorMessage.value = null }

    fun hasBluetoothPermissions(): Boolean {
        val context = getApplication<Application>()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    inner class SysExReceiver : MidiReceiver() {
        val receivedData = mutableListOf<ByteArray>()
        fun clearData() = synchronized(receivedData) { receivedData.clear() }
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val data = msg.copyOfRange(offset, offset + count)
            if (count >= 18 && data.getOrNull(0) == SYSEX_START && data.getOrNull(5) == RolandV71Config.COMMAND_DT1) {
                synchronized(receivedData) { receivedData.add(data) }
                responseReceived.set(true)
            }
        }
    }
}
