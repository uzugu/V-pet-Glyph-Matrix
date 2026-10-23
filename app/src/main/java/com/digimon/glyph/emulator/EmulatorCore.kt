package com.digimon.glyph.emulator

/** Hardware-independent runtime contract. Port pins are bit indices, not masks. */
interface EmulatorCore {
    val clockFrequency: Int
    val supportsClassicLink: Boolean get() = false
    var onBuzzerChange: ((Boolean, Int, Float) -> Unit)?
    var onSerialTx: ((Int) -> Unit)?
    var onPortDriveChange: ((String, Int?) -> Unit)?
    var onIoAccess: ((Boolean, Int, Int) -> Unit)?
    fun clock(): Double
    fun getVRAM(): IntArray
    fun getRAM(): IntArray
    fun getRawVRAM(): IntArray
    fun getDisplaySnapshot(lastGeneration: Long): E0C6200.DisplaySnapshot?
    fun getState(): Map<String, Any>
    fun restoreState(state: Map<String, Any>)
    fun getDiagnostics(): Map<String, Any>
    fun getCurrentDigimonState(romName: String?): DigimonState?
    fun resetCpu()
    fun pinSet(port: String, pin: Int, level: Int)
    fun pinRelease(port: String, pin: Int)
    fun receiveSerialByte(value: Int) {}
    fun applyLinkedPortDrive(port: String, value: Int?) {}
    fun syncLinkedPortDriveState() {}
}
