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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

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
            val deviceName = try { device.name ?: "Unnamed Device" } catch (e: SecurityException) { "Unnamed Device" }
            val bleDevice = BleDevice(device.address, deviceName, device)
            if (_discoveredDevices.value.none { it.address == bleDevice.address }) {
                _discoveredDevices.value = (_discoveredDevices.value + bleDevice).sortedBy { it.name }
            }
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

    fun getRequiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    fun startDeviceScan() {
        clearError()
        if (bleScanner == null || bluetoothAdapter?.isEnabled == false || !hasBluetoothPermissions()) {
            _errorMessage.value = "Bluetooth initialized failed or permissions missing"
            return
        }
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        bleScanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCallback)
        handler.postDelayed({ stopDeviceScan() }, 10000)
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
                _errorMessage.value = "Connection failed"
                return@openBluetoothDevice
            }
            disconnect()
            midiDevice = openedDevice
            inputPort = openedDevice.openInputPort(0)
            outputPort = openedDevice.openOutputPort(0)
            if (inputPort != null && outputPort != null) {
                outputPort?.connect(sysExReceiver)
                _connectedDevice.value = deviceToConnect
                _connectionStatus.value = "Connected"
            } else {
                _errorMessage.value = "MIDI Ports unavailable"
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
                sysExReceiver.clearData()

                for (kitNum in 1..RolandV71Config.TOTAL_KITS) {
                    responseReceived.set(false)
                    val address = RolandV71Config.getKitNameAddress(kitNum)
                    val request = createSysExRequest(address)

                    inputPort?.send(request, 0, request.size)

                    // Handshake mit Timeout
                    var attempts = 0
                    while (!responseReceived.get() && attempts < 30) {
                        delay(20)
                        attempts++
                    }

                    // Sicherheits-Pause nach jedem Paket, um BLE Stack zu entlasten
                    delay(10)
                    _syncProgress.value = kitNum.toFloat() / RolandV71Config.TOTAL_KITS
                }

                // Kurz warten, bis letztes Paket verarbeitet ist
                delay(500)
                processAndSaveSysExData(sysExReceiver.receivedData)

            } catch (e: Exception) {
                _errorMessage.value = "Sync failed: ${e.message}"
            } finally {
                _isSyncing.value = false
                _syncProgress.value = 0f
                // Nach dem Sync KEIN Reconnect, aber kurze Pause zur Hardware-Erholung
                Log.i(TAG, "Sync finished, hardware cooling down...")
            }
        }
    }

    fun switchToKit(kitNumber: Int, channel: Int = RolandV71Config.MIDI_CHANNEL) {
        if (inputPort == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val msg = byteArrayOf((0xC0 + channel).toByte(), (kitNumber - 1).toByte())
                inputPort?.send(msg, 0, msg.size)
                _currentKit.value = kitNumber
                Log.d(TAG, "Switched to Kit $kitNumber")
            } catch (e: IOException) {
                Log.e(TAG, "Switch failed", e)
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

    private suspend fun processAndSaveSysExData(data: List<ByteArray>) {
        withContext(Dispatchers.IO) {
            data.forEach { msg ->
                // Prüfung: Start(1) + Roland(1) + DevID(1) + Model(3) + DT1(1) = 7 Bytes Header vor der Adresse
                if (msg.size >= 16 && msg[6] == RolandV71Config.COMMAND_DT1) {
                    val address = msg.sliceArray(7..10) // Adresse ist 4 Bytes lang
                    val kitNumber = RolandV71Config.getKitNumberFromAddress(address)

                    // Name startet nach der Adresse (Index 11)
                    val nameBytes = msg.sliceArray(11 until 11 + RolandV71Config.KIT_NAME_LENGTH)
                    val kitName = String(nameBytes).trim().replace("\u0000", "")

                    if (kitName.isNotEmpty()) {
                        repository.updateKitName(kitNumber, kitName)
                    }
                }
            }
        }
    }

    fun disconnect() {
        try {
            outputPort?.disconnect(sysExReceiver)
            inputPort?.close()
            outputPort?.close()
            midiDevice?.close()
        } catch (e: Exception) { /* ignore */ }
        inputPort = null
        outputPort = null
        midiDevice = null
        _connectedDevice.value = null
        _connectionStatus.value = "Not connected"
    }

    fun clearError() { _errorMessage.value = null }

    inner class SysExReceiver : MidiReceiver() {
        val receivedData = mutableListOf<ByteArray>()
        fun clearData() = synchronized(receivedData) { receivedData.clear() }
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            val data = msg.copyOfRange(offset, offset + count)
            if (count >= 18 && data[0] == SYSEX_START && data[5] == RolandV71Config.COMMAND_DT1) {
                synchronized(receivedData) { receivedData.add(data) }
                responseReceived.set(true)
            }
        }
    }
}