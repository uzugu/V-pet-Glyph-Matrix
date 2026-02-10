package com.digimon.glyph.emulator

/**
 * Seiko Epson E0C6200 CPU state.
 * 4-bit CMOS microcontroller used in Digimon Virtual Pet devices.
 * Ported from BrickEmuPy (azya52/BrickEmuPy).
 */
class Cpu {
    var a: Int = 0       // 4-bit accumulator A
    var b: Int = 0       // 4-bit accumulator B
    var ix: Int = 0      // 12-bit index X (XP:XH:XL)
    var iy: Int = 0      // 12-bit index Y (YP:YH:YL)
    var sp: Int = 0      // 8-bit stack pointer
    var pc: Int = 0x100  // 13-bit program counter (reset vector)
    var npc: Int = 0x100 // 13-bit next-page counter

    // Flags stored as Int 0/1
    var cf: Int = 0      // Carry
    var zf: Int = 0      // Zero
    var df: Int = 0      // Decimal (BCD mode)
    var ifl: Int = 0     // Interrupt enable

    var halt: Int = 0
    var ifDelay: Boolean = false
    var reset: Int = 0
    var instrCounter: Long = 0

    fun resetState() {
        a = 0; b = 0; ix = 0; iy = 0; sp = 0
        pc = 0x100; npc = 0x100
        cf = 0; zf = 0; df = 0; ifl = 0
        halt = 0; reset = 0; ifDelay = false
        instrCounter = 0
    }
}
