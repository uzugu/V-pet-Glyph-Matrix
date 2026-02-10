package com.digimon.glyph.emulator

/**
 * E0C6200 memory model.
 * RAM: 768 nibbles, VRAM: 160 nibbles, ROM: raw binary.
 */
class Memory {
    companion object {
        const val RAM_SIZE = 0x300
        const val VRAM_SIZE = 0x0A0
        const val VRAM_PART1_OFFSET = 0xE00
        const val VRAM_PART2_OFFSET = 0xE80
        const val VRAM_PART_SIZE = 0x050
        const val IORAM_OFFSET = 0xF00
        const val IORAM_SIZE = 0x07F
    }

    val ram = IntArray(RAM_SIZE)
    val vram = IntArray(VRAM_SIZE)

    private var rom = ByteArray(0)
    private var romSize = 0

    fun loadRom(data: ByteArray) {
        rom = data.copyOf()
        romSize = rom.size
    }

    fun getRomWord(address: Int): Int {
        if (romSize == 0) return 0
        val a0 = address % romSize
        val a1 = (address + 1) % romSize
        return ((rom[a0].toInt() and 0xFF) shl 8) or (rom[a1].toInt() and 0xFF)
    }

    fun resetRam() {
        ram.fill(0)
        vram.fill(0)
    }
}
