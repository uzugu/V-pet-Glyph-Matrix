package com.digimon.glyph.emulator

/** Original test firmware, not a Digimon ROM. Counts serviced 1 Hz interrupts and clears IT. */
object TimingRom {
    fun core(): E0C6200 {
        val rom = ByteArray(8192)
        fun instruction(address: Int, opcode: Int) {
            rom[address * 2] = (opcode shr 8).toByte()
            rom[address * 2 + 1] = opcode.toByte()
        }
        instruction(0x100, 0x020) // JP 0x120
        val irq = intArrayOf(0xF60, 0xE0F, 0xE80, 0xB00, 0xEC2, 0xF48, 0xFDF)
        irq.forEachIndexed { i, op -> instruction(0x102 + i, op) }
        instruction(0x120, 0xFF8) // HALT until interrupt
        instruction(0x121, 0x020) // JP 0x120
        return E0C6200(rom).apply {
            restoreState(getState() + mapOf("IF" to 1, "EIT" to E0C6200.IO_IT1, "SP" to 0xF0, "PC" to 0x120, "NPC" to 0x100))
        }
    }
}
