package com.example.set_list.midi

/**
 * Roland V-Drums SysEx Configuration for V71 Model
 *
 * Adressen und Werte basierend auf der V71 MIDI Implementation v1.12.
 */
object RolandV71Config {

    // SysEx Befehle
    const val SYSEX_START: Byte = 0xF0.toByte()
    const val SYSEX_END: Byte = 0xF7.toByte()
    const val ROLAND_ID: Byte = 0x41
    const val DEVICE_ID: Byte = 0x10 // Default device ID
    const val COMMAND_RQ1: Byte = 0x11 // Request Data 1
    const val COMMAND_DT1: Byte = 0x12 // Data Set 1

    /**
     * Model ID für das Roland V71
     */
    val MODEL_ID = byteArrayOf(
        0x01.toByte(), // Model ID#1 (V71)
        0x06.toByte(), // Model ID#2 (V71)
        0x01.toByte()  // Model ID#3 (V71)
    )

    /**
     * Gesamtzahl der Kits im V71-Modul.
     */
    const val TOTAL_KITS = 200

    /**
     * Start-Adresse für Kit 1.
     */
    private const val KIT_BASE_ADDRESS = 0x04000000L

    /**
     * Offset zwischen den Adressen aufeinanderfolgender Kits.
     */
    private const val KIT_ADDRESS_OFFSET = 0x040000L

    /**
     * Länge des Kit-Namens in Bytes.
     */
    const val KIT_NAME_LENGTH = 16

    /**
     * MIDI Channel für Drums (0-indexed).
     *
     * WICHTIG:
     * - 9 = MIDI Channel 10 (Standard für Drums)
     * - Das V71 MUSS folgende Einstellungen haben:
     *   SETUP → MIDI → Rx Channel: 10
     *   SETUP → MIDI → Program Change: ON  ← KRITISCH!
     */
    var MIDI_CHANNEL = 9  // Channel 10 (0-indexed)

    /**
     * Berechnet die 4-Byte SysEx-Adresse für einen gegebenen Kit-Namen.
     */
    fun getKitNameAddress(kitNumber: Int): ByteArray {
        require(kitNumber in 1..TOTAL_KITS) {
            "Kit number must be between 1 and $TOTAL_KITS"
        }
        val targetAddress = KIT_BASE_ADDRESS + (kitNumber - 1) * KIT_ADDRESS_OFFSET
        return longTo4ByteArray(targetAddress)
    }

    /**
     * Berechnet die Kit-Nummer aus einer gegebenen 4-Byte SysEx-Adresse.
     */
    fun getKitNumberFromAddress(address: ByteArray): Int {
        require(address.size == 4) { "Address must be 4 bytes" }
        val targetAddress = byteArrayToLong(address)
        return (((targetAddress - KIT_BASE_ADDRESS) / KIT_ADDRESS_OFFSET) + 1).toInt()
    }

    /**
     * Konvertiert einen Long-Wert in ein 4-Byte-Array (Big-Endian).
     */
    fun longTo4ByteArray(value: Long): ByteArray {
        return byteArrayOf(
            ((value shr 24) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    /**
     * Konvertiert ein 4-Byte-Array in einen Long-Wert (Big-Endian).
     */
    private fun byteArrayToLong(bytes: ByteArray): Long {
        require(bytes.size == 4) { "Byte-Array muss 4 Bytes lang sein" }
        return ((bytes[0].toLong() and 0xFF) shl 24) or
                ((bytes[1].toLong() and 0xFF) shl 16) or
                ((bytes[2].toLong() and 0xFF) shl 8) or
                (bytes[3].toLong() and 0xFF)
    }
}