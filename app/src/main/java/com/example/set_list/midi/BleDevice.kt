package com.example.set_list.midi

import android.bluetooth.BluetoothDevice

data class BleDevice(val address: String, val name: String, val device: BluetoothDevice)
