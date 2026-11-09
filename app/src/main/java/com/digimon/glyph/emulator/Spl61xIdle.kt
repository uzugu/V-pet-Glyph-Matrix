package com.digimon.glyph.emulator

import kotlin.math.abs
import kotlin.math.floor

/**
 * Closed-form execution of small, stable polling loops. Only pure loads, an
 * optional immediate AND/CMP, a taken backwards branch, and an optional write
 * to the unmapped $0f sink are admitted. RAM reads may be ISR-owned flags: no
 * CPU, input or interrupt handler can change them inside the bounded skip.
 */
internal fun Spl61xCore.skipIdleLoop(budget: Double): Double {
    if (_CPU_ENBL == 0 || _PC !in 0x400..0xfff0 ||
        (_IF == 0 && (_IREQ and _INT_CTRL) != 0)) return 0.0

    var pc = _PC
    var instructions = 1
    var ticks: Int
    var timerRead = -1
    if (_read_mem(pc) == 0x4c) {
        if ((_read_mem(pc + 1) or (_read_mem(pc + 2) shl 8)) != pc) return 0.0
        ticks = 3
    } else {
        ticks = 0
        if (_read_mem(pc) == 0x85 && _read_mem(pc + 1) == 0x0f) {
            pc += 2
            ticks += 3
            instructions++
        }
        val address: Int
        when (_read_mem(pc)) {
            0xa5 -> { address = _read_mem(pc + 1); pc += 2; ticks += 3 }
            0xad -> { address = _read_mem(pc + 1) or (_read_mem(pc + 2) shl 8); pc += 3; ticks += 4 }
            else -> return 0.0
        }
        when (address) {
            2, 3, 4, 8, 10, 13 -> Unit // Side-effect-free ports and interrupt/time-base flags.
            18, 19 -> timerRead = 1
            21, 22 -> timerRead = 0
            in 0x40..0x37f -> Unit
            else -> return 0.0
        }
        var a = _read_mem(address)
        var negative = a shr 7
        var zero = if (a == 0) 1 else 0
        var carry = _CF
        when (_read_mem(pc)) {
            0x29 -> {
                a = a and _read_mem(pc + 1)
                negative = a shr 7
                zero = if (a == 0) 1 else 0
                pc += 2; ticks += 2; instructions++
            }
            0xc9 -> {
                val difference = a - _read_mem(pc + 1)
                negative = (difference shr 7) and 1
                zero = if (difference == 0) 1 else 0
                carry = if (difference >= 0) 1 else 0
                pc += 2; ticks += 2; instructions++
            }
        }
        // Only a fixed point is safe: the first iteration still executes normally.
        if (_A != a || _NF != negative || _ZF != zero || _CF != carry) return 0.0
        val taken = when (_read_mem(pc)) {
            0x10 -> negative == 0
            0x30 -> negative != 0
            0x90 -> carry == 0
            0xb0 -> carry != 0
            0xd0 -> zero == 0
            0xf0 -> zero != 0
            else -> false
        }
        val relative = _read_mem(pc + 1)
        val next = pc + 2
        if (!taken || next + relative - ((relative and 128) shl 1) != _PC) return 0.0
        ticks += 3 + if ((_PC xor next) > 255) 1 else 0
        instructions++
        // Do not admit code that crosses the bank boundary during an iteration.
        if ((_PC xor next) and 0x8000 != 0) return 0.0
    }

    // Combining subtraction is exact on the hardware's binary clock grid. A
    // portable save may contain arbitrary fractional counters: fall back for
    // those rather than changing floating-point rounding through aggregation.
    if (!idleExact(_T64HZ_counter) || !idleExact(_T128HZ_counter) || !idleExact(_T2K_counter) ||
        (_CPU_DIV != 2 && _CPU_DIV != 4 && _CPU_DIV != 8 && _CPU_DIV != 16) ||
        _instr_counter < 0 || _instr_counter > 4_000_000_000_000.0 ||
        _instr_counter != floor(_instr_counter)) return 0.0
    var event = minOf(_T64HZ_counter, _T128HZ_counter, _T2K_counter)
    if (_TIMER_CTRL and 128 != 0) {
        if (!idleExact(_TM0_counter) || !idleExact(_TM1_counter) ||
            !idleExact(_TM0_div) || !idleExact(_TM1_div) || _TM0_div < 0 || _TM1_div <= 0 ||
            _TM0 !in 0..65535 || _TM1 !in 0..65535) return 0.0
        if (_TM0_div != 0.0) event = minOf(event,
            _TM0_counter + if (timerRead == 0) 0.0 else _TM0_div * (65535 - _TM0))
        event = minOf(event, _TM1_counter + if (timerRead == 1) 0.0 else _TM1_div * (65535 - _TM1))
        // A pending increment needs the original instruction/timer ordering.
        if ((_TM0_div != 0.0 && _TM0_counter <= 0) || _TM1_counter <= 0) return 0.0
    }
    val iteration = ticks.toDouble() * _CPU_DIV
    // The guard bounds even unusual callers; ordinary advances are <= 1 ms.
    val count = floor(minOf(budget, Math.nextDown(event), 1_000_000.0) / iteration).toInt()
    if (count < 2) return 0.0
    val cycles = count * iteration
    val skipped = count.toLong() * instructions
    _instr_counter += skipped.toDouble()
    idleSkippedInstructions += skipped
    _timers_clock(cycles)
    sound.advanceAudio(cycles)
    return cycles
}

private fun idleExact(value: Double): Boolean =
    value.isFinite() && abs(value) < 1_000_000_000 && value * 1024 == floor(value * 1024)
