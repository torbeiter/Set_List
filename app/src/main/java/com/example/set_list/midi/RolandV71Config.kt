package com.example.set_list.midi

object RolandV71Config {
    // MIDI IDs
    const val ROLAND_ID: Byte = 0x41
    const val DEVICE_ID: Byte = 0x10 // Standardwert 17 (0x10)

    // Korrekte Model ID für V71 laut Handbuch (01H 06H 01H)
    val MODEL_ID = byteArrayOf(0x01, 0x06, 0x01)

    // Roland Befehle
    const val COMMAND_RQ1: Byte = 0x11 // Request Data
    const val COMMAND_DT1: Byte = 0x12 // Data Set (Antwort)

    const val SYSEX_START: Byte = 0xF0.toByte()
    const val SYSEX_END: Byte = 0xF7.toByte()

    const val MIDI_CHANNEL = 9 // Kanal 10 (0-indexed)
    const val TOTAL_KITS = 200
    const val KIT_NAME_LENGTH = 16

    /**
     * Berechnet die Startadresse für einen Kit-Namen.
     * Basisadresse Kit 1: 04 00 00 00
     * Offset pro Kit: 00 01 00 00
     */
    fun getKitNameAddress(kitNumber: Int): ByteArray {
        val baseAddress = 0x04000000
        val offsetPerKit = 0x00010000
        val kitAddress = baseAddress + ((kitNumber - 1) * offsetPerKit)

        // Roland nutzt ein 4-Byte Adress-Schema (7-bit)
        return byteArrayOf(
            ((kitAddress shr 21) and 0x7F).toByte(),
            ((kitAddress shr 14) and 0x7F).toByte(),
            ((kitAddress shr 7) and 0x7F).toByte(),
            (kitAddress and 0x7F).toByte()
        )
    }

    /**
     * Extrahiert die Kit-Nummer aus der empfangenen Adresse.
     */
    fun getKitNumberFromAddress(address: ByteArray): Int {
        val addr = ((address[0].toInt() and 0x7F) shl 21) or
                ((address[1].toInt() and 0x7F) shl 14) or
                ((address[2].toInt() and 0x7F) shl 7) or
                (address[3].toInt() and 0x7F)

        val baseAddress = 0x04000000
        val offsetPerKit = 0x00010000
        return ((addr - baseAddress) / offsetPerKit) + 1
    }

    /**
     * Konvertiert eine Länge/Größe in das Roland 4-Byte Format.
     */
    fun longTo4ByteArray(value: Long): ByteArray {
        return byteArrayOf(
            ((value shr 21) and 0x7F).toByte(),
            ((value shr 14) and 0x7F).toByte(),
            ((value shr 7) and 0x7F).toByte(),
            (value and 0x7F).toByte()
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