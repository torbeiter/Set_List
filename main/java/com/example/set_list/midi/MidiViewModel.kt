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

data class BleDevice(val address: String, val name: String, val device: BluetoothDevice)

class MidiViewModel(application: Application, private val repository: SetlistRepository) : AndroidViewModel(application) {

    private val TAG = "MidiViewModel"
    private val midiManager = getApplication<Application>().getSystemService(MidiManager::class.java)
    private val bluetoothManager = getApplication<Application>().getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())

    private var midiDevice: MidiDevice? = null
    private var inputPort: MidiInputPort? = null
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
                Log.i(TAG, "✓ Connected to ${deviceToConnect.name}")
            }
        }, handler)
    }



    fun switchToKit(kitNumber: Int, channel: Int = RolandV71Config.MIDI_CHANNEL) {
        if (inputPort == null) {
            _errorMessage.value = "Not connected to any device."
            return
        }

        val programNumber = kitNumber - 1
        if (programNumber < 0 || programNumber > 127) {
            _errorMessage.value = "Invalid kit number: $kitNumber"
            return
        }

        val programChange = ByteArray(2).apply {
            this[0] = (0xC0 + channel).toByte()
            this[1] = programNumber.toByte()
        }

        try {
            inputPort?.send(programChange, 0, programChange.size)
            _currentKit.value = kitNumber
            Log.d(TAG, "TX: Kit #$kitNumber, Ch ${channel + 1}, Bytes: ${programChange.joinToString(" ") { "0x%02X".format(it) }}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to send Program Change", e)
            _errorMessage.value = "Failed to switch kit: ${e.message}"
        }
    }

    /**
     * Synchronisiert Kit-Namen vom V71.
     * WICHTIG: Nach dem Sync wird die Verbindung komplett neu aufgebaut!
     */
    fun syncKitNames() {
        if (midiDevice == null) {
            _errorMessage.value = "Not connected. Cannot sync."
            return
        }
        if (_isSyncing.value) {
            _errorMessage.value = "Sync is already in progress."
            return
        }

        viewModelScope.launch {
            var tempOutputPort: MidiOutputPort? = null
            var tempInputPort: MidiInputPort? = null

            try {
                _isSyncing.value = true
                _syncProgress.value = 0f
                sysExReceiver.clearData()

                Log.i(TAG, "Starting kit name sync...")

                // KRITISCH: Input-Port schließen vor Output-Port öffnen!
                Log.d(TAG, "Closing input port before sync...")
                inputPort?.close()
                inputPort = null

                kotlinx.coroutines.delay(200) // Kurz warten

                // Output-Port öffnen für Empfang
                tempOutputPort = midiDevice?.openOutputPort(0)
                if (tempOutputPort == null) {
                    throw IOException("Failed to open output port")
                }
                tempOutputPort.connect(sysExReceiver)

                // Input-Port neu öffnen für Senden
                tempInputPort = midiDevice?.openInputPort(0)
                if (tempInputPort == null) {
                    throw IOException("Failed to reopen input port")
                }

                Log.d(TAG, "Ports ready, sending requests...")

                // SysEx-Requests senden
                for (kitNum in 1..RolandV71Config.TOTAL_KITS) {
                    val address = RolandV71Config.getKitNameAddress(kitNum)
                    val request = createSysExRequest(address)
                    tempInputPort.send(request, 0, request.size)
                    _syncProgress.value = kitNum.toFloat() / RolandV71Config.TOTAL_KITS
                    kotlinx.coroutines.delay(50)
                }

                kotlinx.coroutines.delay(2000)

                val receivedCount = sysExReceiver.receivedData.size
                Log.i(TAG, "Received $receivedCount of 200 kit names")

                if (receivedCount == 0) {
                    _errorMessage.value = "No response. Check V71: SETUP→MIDI→Tx Channel"
                }

            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message}"
                Log.e(TAG, "Sync failed", e)
            } finally {
                // Alle Ports schließen
                try {
                    tempInputPort?.close()
                    tempOutputPort?.disconnect(sysExReceiver)
                    tempOutputPort?.close()
                    Log.d(TAG, "Closed sync ports")
                } catch (e: IOException) {
                    Log.e(TAG, "Error closing ports", e)
                }

                // Daten verarbeiten
                processAndSaveSysExData(sysExReceiver.receivedData)

                _isSyncing.value = false
                _syncProgress.value = 0f

                // Input-Port für Kit-Switching wiederherstellen
                Log.i(TAG, "Reopening input port for kit switching...")
                kotlinx.coroutines.delay(500)
                try {
                    inputPort = midiDevice?.openInputPort(0)
                    if (inputPort != null) {
                        Log.i(TAG, "✓ Input port ready for kit switching")
                    } else {
                        Log.e(TAG, "✗ Failed to reopen input port")
                        _errorMessage.value = "Failed to restore kit switching. Please reconnect."
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reopening input port", e)
                    _errorMessage.value = "Kit switching may not work. Please reconnect."
                }
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
            var savedCount = 0
            for (message in data) {
                if (message.size < 18 || message[0] != SYSEX_START || message.last() != SYSEX_END) continue
                if (message[5] != RolandV71Config.COMMAND_DT1) continue

                try {
                    val address = message.sliceArray(6..9)
                    val kitNumber = RolandV71Config.getKitNumberFromAddress(address)
                    val nameBytes = message.sliceArray(10 until 10 + RolandV71Config.KIT_NAME_LENGTH)
                    val kitName = String(nameBytes).trim().replace("\u0000", "")

                    if (kitName.isNotBlank() && kitNumber in 1..RolandV71Config.TOTAL_KITS) {
                        repository.updateKitName(kitNumber, kitName)
                        savedCount++
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing kit name", e)
                }
            }
            Log.i(TAG, "✓ Saved $savedCount kit names")
        }
    }

    fun disconnect() {
        try {
            inputPort?.close()
            inputPort = null

            midiDevice?.close()
            midiDevice = null

            _connectedDevice.value = null
            _connectionStatus.value = "Not connected"
            _currentKit.value = null

            Log.i(TAG, "Disconnected")
        } catch (e: IOException) {
            Log.e(TAG, "Error disconnecting", e)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDeviceScan()
        disconnect()
    }

    inner class SysExReceiver : MidiReceiver() {
        val receivedData = mutableListOf<ByteArray>()

        fun clearData() = receivedData.clear()

        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            if (count > 0 && msg.getOrNull(offset) == SYSEX_START &&
                msg.getOrNull(offset + count - 1) == SYSEX_END) {
                receivedData.add(msg.copyOfRange(offset, offset + count))
            }
        }
    }
}