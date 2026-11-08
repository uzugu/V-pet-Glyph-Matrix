package com.digimon.glyph.emulator

/** SPL61X/GPL191X port of the pinned BrickEmuPy reference. See docs/dpower/UPSTREAM-LICENSE.
 * Instruction quirks and fractional timer arithmetic intentionally match that reference.
 * Regenerate instruction/SFR bodies with tools/port_spl61x.py.
 */
@Suppress("UNUSED_PARAMETER", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "NAME_SHADOWING")
class Spl61xCore(rom: ByteArray, val sound: DPowerSound) {
    val _mem = IntArray(rom.size) { rom[it].toInt() and 255 }
    var _pullup_ext = IntArray(3)
    var _port_input = Array(3) { IntArray(2) }
    var _PDIR = IntArray(3)
    var _PLATCH = IntArray(3)
    var _A: Int = 0
    var _AUDIO_CH0_CTRL: Int = 0
    var _AUDIO_CH0_DATA: Int = 0
    var _AUDIO_CH1_CTRL: Int = 0
    var _AUDIO_CH1_DATA: Int = 0
    var _BANK: Int = 0
    var _BF: Int = 0
    var _CF: Int = 0
    var _CLK32K_ENABLE: Int = 0
    var _CLK_CPU_CTRL: Int = 0
    var _CPU_DIV: Int = 0
    var _CPU_ENBL: Int = 0
    var _DF: Int = 0
    var _ENTER_SLEEP: Int = 0
    var _IF: Int = 0
    var _INT_CLEAR: Int = 0
    var _INT_CTRL: Int = 0
    var _IO_CTRL: Int = 0
    var _IREQ: Int = 0
    var _LCD_CTRL: Int = 0
    var _NF: Int = 0
    var _NMI_CTRL: Int = 0
    var _PC: Int = 0
    var _SP: Int = 0
    var _T128HZ_counter: Double = 0.0
    var _T2K_counter: Double = 0.0
    var _T64HZ: Int = 0
    var _T64HZ_counter: Double = 0.0
    var _TIMER_CTRL: Int = 0
    var _TIME_BASE: Int = 0
    var _TM0: Int = 0
    var _TM0_LOAD: Int = 0
    var _TM0_PRELOAD: Int = 0
    var _TM0_counter: Double = 0.0
    var _TM0_div: Double = 0.0
    var _TM1: Int = 0
    var _TM1_LOAD: Int = 0
    var _TM1_PRELOAD: Int = 0
    var _TM1_counter: Double = 0.0
    var _TM1_div: Double = 0.0
    var _VF: Int = 0
    var _WAKEUPREQ: Int = 0
    var _WAKEUP_CTRL: Int = 0
    var _X: Int = 0
    var _Y: Int = 0
    var _ZF: Int = 0
    var _clock: Int = 0
    var _instr_counter: Double = 0.0
    var _mem_size: Int = 0
    var _sub_clock_div: Double = 0.0
    init { require(rom.isNotEmpty()); _mem_size = rom.size; _clock = 2_200_000; _sub_clock_div = _clock / 32768.0; reset() }
    companion object {
        const val SUB_CLOCK = 32768
        const val SP = 256
        const val VADDR_NMI = 32762
        const val VADDR_RESET = 32764
        const val VADDR_IRQ = 32766
        const val SFR_TIMER_CTRL_ENABLE = 128
        const val SFR_TIMER_CTRL_TM0_COUNTER_MODE = 16
        const val SFR_TIMER_CTRL_TM0_MASK = 32
        const val SFR_TIMER_CTRL_TM0_TIMER1 = 32
        const val SFR_TIMER_CTRL_TM0_ROSC = 0
        const val SFR_TIMER_CTRL_TM1_MASK = 64
        const val SFR_TIMER_CTRL_TM1_32K = 64
        const val SFR_TIMER_CTRL_TM1_ROSC = 0
        const val SFR_TIMER_CTRL_CNT0_SRCB_MASK = 12
        const val SFR_TIMER_CTRL_CNT0_SRCB_CD1 = 0
        const val SFR_TIMER_CTRL_CNT0_SRCB_VDD = 4
        const val SFR_TIMER_CTRL_CNT0_SRCB_TIMEBASEH = 8
        const val SFR_TIMER_CTRL_CNT0_SRCB_CLK128 = 12
        const val SFR_TIMER_CTRL_CNT0_SRCA_MASK = 3
        const val SFR_TIMER_CTRL_CNT0_SRCA_CD0 = 0
        const val SFR_TIMER_CTRL_CNT0_SRCA_VDD = 1
        const val SFR_TIMER_CTRL_CNT0_SRCA_ROSC = 2
        const val SFR_TIMER_CTRL_CNT0_SRCA_CLK32 = 3
        const val SFR_WAKEUP_CTRL_EXT = 1
        const val SFR_WAKEUP_CTRL_TIME_BASEL = 2
        const val SFR_WAKEUP_CTRL_TIMER0 = 4
        const val SFR_WAKEUP_CTRL_TIME_BASEH = 8
        const val SFR_INT_CTRL_EXT = 1
        const val SFR_INT_CTRL_CLK2K = 2
        const val SFR_INT_CTRL_CLK128 = 4
        const val SFR_INT_CTRL_TIME_BASEL = 8
        const val SFR_INT_CTRL_TIME_BASEH = 16
        const val SFR_INT_CTRL_TIMER1 = 32
        const val SFR_INT_CTRL_TIMER0 = 64
        const val SFR_INT_CTRL_UART = 128
        const val SFR_NMI_CTRL_TIMER1 = 1
        const val SFR_TIMEBASE_L_MASK = 128
        const val SFR_TIMEBASE_L_SHIFT = 7
        const val SFR_TIMEBASE_L_2HZ = 0
        const val SFR_TIMEBASE_L_1HZ = 128
        const val SFR_TIMEBASE_H_MASK = 3
        const val SFR_TIMEBASE_H_4HZ = 0
        const val SFR_TIMEBASE_H_8HZ = 1
        const val SFR_TIMEBASE_H_16HZ = 2
        const val SFR_TIMEBASE_H_32HZ = 3
        val SFR_TIMEBASE_H_TBL = intArrayOf(15,7,3,1)
        val SFR_TIMEBASE_L_TBL = intArrayOf(31,63)
        const val SFR_AUDIO_CTRL_AUDIO_ENBL = 128
        const val SFR_AUDIO_CTRL_TONE_MODE = 64
        const val SFR_AUDIO_CTRL_DATA_SRC_MASK = 3
        const val SFR_AUDIO_CTRL_DIRECT = 0
        const val SFR_AUDIO_CTRL_TM0_OVFLW = 1
        const val SFR_AUDIO_CTRL_TM1_OVFLW = 2
        const val SFR_AUDIO_CTRL_TM01_OVFLW = 3
        const val SFR_CPU_DIV_DEFAULT = 2
        const val SFR_LCD_CTRL_ON = 2
        const val CPU_RAM_OFFSET = 64
        const val RAM_SIZE = 704
        const val DPRAM_OFFSET = 768
        const val DPRAM_SIZE = 128
    }
    fun _get_rom_addr(addr: Int): Int {
        return ((addr and 32767) or (_BANK * (addr and 32768)))
    }
    fun reset(): Unit {
        _TM0_counter = (0).toDouble()
        _TM0_div = (0).toDouble()
        _TM1_counter = (0).toDouble()
        _TM1_div = (0).toDouble()
        _T64HZ_counter = (0).toDouble()
        _T64HZ = 0
        _T128HZ_counter = (0).toDouble()
        _T2K_counter = (0).toDouble()
        _TM0 = 0
        _TM0_PRELOAD = 0
        _TM0_LOAD = 0
        _TM1 = 0
        _TM1_PRELOAD = 0
        _TM1_LOAD = 0
        _PC = 0
        _A = 0
        _X = 0
        _Y = 0
        _SP = 0
        _set_ps(4)
        _CPU_ENBL = 1
        _BANK = 0
        _WAKEUP_CTRL = 0
        _WAKEUPREQ = 0
        _ENTER_SLEEP = 0
        _TIME_BASE = 0
        _TIMER_CTRL = 0
        _CLK_CPU_CTRL = 0
        _CPU_DIV = SFR_CPU_DIV_DEFAULT
        _CLK32K_ENABLE = 128
        _PDIR = intArrayOf(0, 0, 0)
        _PLATCH = intArrayOf(0, 0, 0)
        _IO_CTRL = 0
        _INT_CTRL = 0
        _INT_CLEAR = 0
        _IREQ = 0
        _NMI_CTRL = 0
        _AUDIO_CH0_CTRL = 0
        _AUDIO_CH0_DATA = 0
        _AUDIO_CH1_CTRL = 0
        _AUDIO_CH1_DATA = 0
        _LCD_CTRL = 0
        _PC = ((_mem[((VADDR_RESET + 1) % _mem_size)] shl 8) or _mem[(VADDR_RESET % _mem_size)])
    }
    fun _port_read(port: Int): Int {
        return ((((_PDIR[port]).inv() and (_port_input[port][1] or (_pullup_ext[port] and (_port_input[port][0]).inv()))) or (_PDIR[port] and _PLATCH[port])) and 255)
    }
    fun port_handler(port: Int, mask: Int, level: Int): Unit {
        var prev_port: Int = 0
        if ((port == 3)) {
            if ((level == 0)) {
                reset()
            }
        }
        else {
            prev_port = _port_read(port)
            _port_input[port][0] = (_port_input[port][0] and (mask).inv())
            _port_input[port][1] = (_port_input[port][1] and (mask).inv())
            if ((level >= 0)) {
                _port_input[port][level] = (_port_input[port][level] or mask)
            }
            if (((port == 1) && ((mask and 2) != 0))) {
                if (((prev_port and 2) > (_port_read(port) and 2))) {
                    _IREQ = (_IREQ or SFR_INT_CTRL_EXT)
                }
                _update_timers_div()
            }
            if ((port == 2)) {
                if (((_port_read(port) and mask) != 0)) {
                    _WAKEUPREQ = (_WAKEUPREQ or (SFR_WAKEUP_CTRL_EXT and _WAKEUP_CTRL))
                }
            }
        }
    }
    fun _IRQ(): Unit {
        var addr: Int = 0
        _write_mem((_SP or SP), (_PC shr 8))
        _SP = ((_SP - 1) and 255)
        _write_mem((_SP or SP), (_PC and 255))
        _SP = ((_SP - 1) and 255)
        _write_mem((_SP or SP), _get_ps())
        _SP = ((_SP - 1) and 255)
        _IF = 1
        addr = (VADDR_IRQ or (_BANK * (_PC and 32768)))
        _PC = ((_mem[((addr + 1) % _mem_size)] shl 8) or _mem[(addr % _mem_size)])
    }
    fun _NMI(): Unit {
        var addr: Int = 0
        if ((_CPU_ENBL != 0)) {
            _write_mem((_SP or SP), (_PC shr 8))
            _SP = ((_SP - 1) and 255)
            _write_mem((_SP or SP), (_PC and 255))
            _SP = ((_SP - 1) and 255)
            _write_mem((_SP or SP), _get_ps())
            _SP = ((_SP - 1) and 255)
            addr = (VADDR_NMI or (_BANK * (_PC and 32768)))
            _PC = ((_mem[((addr + 1) % _mem_size)] shl 8) or _mem[(addr % _mem_size)])
        }
    }
    fun _update_audio_div(): Unit {
        var div: Double = 0.0
        if ((((_TIMER_CTRL and SFR_TIMER_CTRL_ENABLE) != 0) && ((_AUDIO_CH0_CTRL and SFR_AUDIO_CTRL_AUDIO_ENBL) != 0) && ((_AUDIO_CH0_CTRL and SFR_AUDIO_CTRL_TONE_MODE) != 0))) {
            div = ((_TM0_div * ((65536 - _TM0_PRELOAD)).toDouble()) * (2).toDouble())
            sound.tone(0, div)
        }
        else {
            sound.stop(0)
        }
        if ((((_TIMER_CTRL and SFR_TIMER_CTRL_ENABLE) != 0) && ((_AUDIO_CH1_CTRL and SFR_AUDIO_CTRL_AUDIO_ENBL) != 0) && ((_AUDIO_CH1_CTRL and SFR_AUDIO_CTRL_TONE_MODE) != 0))) {
            div = ((_TM1_div * ((65536 - _TM1_PRELOAD)).toDouble()) * (2).toDouble())
            sound.tone(1, div)
        }
        else {
            sound.stop(1)
        }
    }
    fun _update_timers_div(): Unit {
        var srcA: Int = 0
        var srcB: Int = 0
        var time_base_h: Int = 0
        if (((_TIMER_CTRL and SFR_TIMER_CTRL_TM1_32K) != 0)) {
            _TM1_div = _sub_clock_div
        }
        else {
            _TM1_div = (1).toDouble()
        }
        _TM0_div = (0).toDouble()
        if (((_TIMER_CTRL and SFR_TIMER_CTRL_TM0_COUNTER_MODE) != 0)) {
            srcB = (_TIMER_CTRL and SFR_TIMER_CTRL_CNT0_SRCB_MASK)
            srcA = (_TIMER_CTRL and SFR_TIMER_CTRL_CNT0_SRCA_MASK)
            if ((srcA == SFR_TIMER_CTRL_CNT0_SRCA_CLK32)) {
                if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_VDD)) {
                    _TM0_div = _sub_clock_div
                }
                else {
                    if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_TIMEBASEH)) {
                        time_base_h = (_TIME_BASE and SFR_TIMEBASE_H_MASK)
                        _TM0_div = ((_sub_clock_div * ((SUB_CLOCK / 64)).toDouble()) * (SFR_TIMEBASE_H_TBL[time_base_h]).toDouble())
                    }
                    else {
                        if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_CLK128)) {
                            _TM0_div = (_sub_clock_div * ((SUB_CLOCK / 128)).toDouble())
                        }
                        else {
                            if (((_port_read(1) and 2) != 0)) {
                                _TM0_div = _sub_clock_div
                            }
                        }
                    }
                }
            }
            else {
                if ((srcA == SFR_TIMER_CTRL_CNT0_SRCA_ROSC)) {
                    if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_VDD)) {
                        _TM0_div = (1).toDouble()
                    }
                    else {
                        if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_TIMEBASEH)) {
                            time_base_h = (_TIME_BASE and SFR_TIMEBASE_H_MASK)
                            _TM0_div = (((SUB_CLOCK / 64) * SFR_TIMEBASE_H_TBL[time_base_h])).toDouble()
                        }
                        else {
                            if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_CLK128)) {
                                _TM0_div = ((SUB_CLOCK / 128)).toDouble()
                            }
                            else {
                                if (((_port_read(1) and 2) != 0)) {
                                    _TM0_div = (1).toDouble()
                                }
                            }
                        }
                    }
                }
                else {
                    if ((srcA == SFR_TIMER_CTRL_CNT0_SRCA_VDD)) {
                        if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_TIMEBASEH)) {
                            time_base_h = (_TIME_BASE and SFR_TIMEBASE_H_MASK)
                            _TM0_div = (((SUB_CLOCK / 64) * SFR_TIMEBASE_H_TBL[time_base_h])).toDouble()
                        }
                        else {
                            if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_CLK128)) {
                                _TM0_div = ((SUB_CLOCK / 128)).toDouble()
                            }
                        }
                    }
                    else {
                        if (((_port_read(1) and 1) != 0)) {
                            if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_TIMEBASEH)) {
                                time_base_h = (_TIME_BASE and SFR_TIMEBASE_H_MASK)
                                _TM0_div = (((SUB_CLOCK / 64) * SFR_TIMEBASE_H_TBL[time_base_h])).toDouble()
                            }
                            else {
                                if ((srcB == SFR_TIMER_CTRL_CNT0_SRCB_CLK128)) {
                                    _TM0_div = ((SUB_CLOCK / 128)).toDouble()
                                }
                            }
                        }
                    }
                }
            }
        }
        else {
            if (((_TIMER_CTRL and SFR_TIMER_CTRL_TM0_TIMER1) != 0)) {
                _TM0_div = (_TM1_div * ((65536 - _TM1_PRELOAD)).toDouble())
            }
            else {
                _TM0_div = (1).toDouble()
            }
        }
        _update_audio_div()
    }
    fun _timers_clock(exec_cycles: Double): Unit {
        var time_base_h: Int = 0
        var time_base_l: Int = 0
        if (((_TIMER_CTRL and SFR_TIMER_CTRL_ENABLE) != 0)) {
            if ((_TM0_div != 0.0)) {
                _TM0_counter = (_TM0_counter - exec_cycles)
                while ((_TM0_counter <= (0).toDouble())) {
                    _TM0_counter = (_TM0_counter + _TM0_div)
                    _TM0 = (_TM0 + 1)
                    if ((_TM0 > 65535)) {
                        _TM0 = _TM0_PRELOAD
                        _IREQ = (_IREQ or SFR_INT_CTRL_TIMER0)
                        _WAKEUPREQ = (_WAKEUPREQ or (SFR_WAKEUP_CTRL_TIMER0 and _WAKEUP_CTRL))
                    }
                }
            }
            _TM1_counter = (_TM1_counter - exec_cycles)
            while ((_TM1_counter <= (0).toDouble())) {
                _TM1_counter = (_TM1_counter + _TM1_div)
                _TM1 = (_TM1 + 1)
                if ((_TM1 > 65535)) {
                    _TM1 = _TM1_PRELOAD
                    if (!(((_NMI_CTRL and SFR_NMI_CTRL_TIMER1) != 0))) {
                        _NMI()
                    }
                    else {
                        _IREQ = (_IREQ or SFR_INT_CTRL_TIMER1)
                    }
                }
            }
        }
        _T64HZ_counter = (_T64HZ_counter - exec_cycles)
        while ((_T64HZ_counter <= (0).toDouble())) {
            _T64HZ_counter = (_T64HZ_counter + (_sub_clock_div * ((SUB_CLOCK / 64)).toDouble()))
            _T64HZ = (_T64HZ + 1)
            time_base_h = (_TIME_BASE and SFR_TIMEBASE_H_MASK)
            if (!(((_T64HZ and SFR_TIMEBASE_H_TBL[time_base_h]) != 0))) {
                _IREQ = (_IREQ or SFR_INT_CTRL_TIME_BASEH)
                _WAKEUPREQ = (_WAKEUPREQ or (SFR_WAKEUP_CTRL_TIME_BASEH and _WAKEUP_CTRL))
            }
            time_base_l = ((_TIME_BASE and SFR_TIMEBASE_L_MASK) shr SFR_TIMEBASE_L_SHIFT)
            if (!(((_T64HZ and SFR_TIMEBASE_L_TBL[time_base_l]) != 0))) {
                _IREQ = (_IREQ or SFR_INT_CTRL_TIME_BASEL)
                _WAKEUPREQ = (_WAKEUPREQ or (SFR_WAKEUP_CTRL_TIME_BASEL and _WAKEUP_CTRL))
            }
        }
        _T128HZ_counter = (_T128HZ_counter - exec_cycles)
        while ((_T128HZ_counter <= (0).toDouble())) {
            _T128HZ_counter = (_T128HZ_counter + (_sub_clock_div * ((SUB_CLOCK / 128)).toDouble()))
            _IREQ = (_IREQ or SFR_INT_CTRL_CLK128)
        }
        _T2K_counter = (_T2K_counter - exec_cycles)
        while ((_T2K_counter <= (0).toDouble())) {
            _T2K_counter = (_T2K_counter + (_sub_clock_div * ((SUB_CLOCK / 2048)).toDouble()))
            _IREQ = (_IREQ or SFR_INT_CTRL_CLK2K)
        }
    }
    fun _set_sfr_io_portAB_dir(value: Int): Unit {
        _PDIR[0] = value
    }
    fun _get_sfr_io_portAB_data(): Int {
        return _port_read(0)
    }
    fun _set_sfr_io_portAB_data(value: Int): Unit {
        _PLATCH[0] = value
    }
    fun _set_sfr_io_portCD_dir(value: Int): Unit {
        _PDIR[1] = value
        _update_timers_div()
    }
    fun _get_sfr_io_portCD_data(): Int {
        return _port_read(1)
    }
    fun _set_sfr_io_portCD_data(value: Int): Unit {
        _PLATCH[1] = value
        _update_timers_div()
    }
    fun _set_sfr_io_portEF_dir(value: Int): Unit {
    }
    fun _get_sfr_io_portEF_data(): Int {
        return _port_read(2)
    }
    fun _set_sfr_io_portEF_data(value: Int): Unit {
        _PLATCH[2] = value
    }
    fun _get_sfr_audio_ch0_ctrl(): Int {
        return _AUDIO_CH0_CTRL
    }
    fun _set_sfr_audio_ch0_ctrl(value: Int): Unit {
        _AUDIO_CH0_CTRL = value
        _update_audio_div()
    }
    fun _get_sfr_audio_ch0_data(): Int {
        return 0
    }
    fun _set_sfr_audio_ch0_data(value: Int): Unit {
        _AUDIO_CH0_DATA = value
        sound.set_data(0, _AUDIO_CH0_CTRL, value)
        _update_audio_div()
    }
    fun _get_sfr_audio_ch1_ctrl(): Int {
        return _AUDIO_CH1_CTRL
    }
    fun _set_sfr_audio_ch1_ctrl(value: Int): Unit {
        _AUDIO_CH1_CTRL = value
        _update_audio_div()
    }
    fun _get_sfr_audio_ch1_data(): Int {
        return 0
    }
    fun _set_sfr_audio_ch1_data(value: Int): Unit {
        _AUDIO_CH1_DATA = value
        sound.set_data(1, _AUDIO_CH1_CTRL, value)
        _update_audio_div()
    }
    fun _get_sfr_bank_sel(): Int {
        return _BANK
    }
    fun _set_sfr_bank_sel(value: Int): Unit {
        _BANK = (value and 3)
    }
    fun _get_sfr_wakeup_ctrl(): Int {
        return _WAKEUPREQ
    }
    fun _set_sfr_wakeup_ctrl(value: Int): Unit {
        _WAKEUP_CTRL = value
        _WAKEUPREQ = (_WAKEUPREQ and value)
    }
    fun _get_sfr_timer_timebase_sel(): Int {
        return (_T64HZ and 255)
    }
    fun _set_sfr_timer_timebase_sel(value: Int): Unit {
        _TIME_BASE = value
        _update_timers_div()
    }
    fun _set_sfr_timer_ctrl(value: Int): Unit {
        _TIMER_CTRL = value
        _update_timers_div()
    }
    fun _set_sfr_clk_32768_en(value: Int): Unit {
        _CLK32K_ENABLE = value
    }
    fun _set_sfr_nmi_ctrl(value: Int): Unit {
        _NMI_CTRL = value
    }
    fun _get_sfr_int_ctrl(): Int {
        return _IREQ
    }
    fun _set_sfr_int_ctrl(value: Int): Unit {
        _INT_CTRL = value
        _IREQ = 0
    }
    fun _set_sfr_enter_sleep(value: Int): Unit {
        _ENTER_SLEEP = value
        _CPU_ENBL = 0
    }
    fun _set_sfr_clk_cpu_ctrl(value: Int): Unit {
        _CLK_CPU_CTRL = value
        _CPU_DIV = (2 shl (value and 3))
    }
    fun _set_sfr_clk_int_clear(value: Int): Unit {
        _INT_CLEAR = value
        _IREQ = (_IREQ and (value).inv())
    }
    fun _get_sfr_timer_TM1L(): Int {
        return (_TM1 and 255)
    }
    fun _set_sfr_timer_TM1L(value: Int): Unit {
        _TM1_PRELOAD = ((_TM1_PRELOAD and 65280) or value)
    }
    fun _get_sfr_timer_TM1H(): Int {
        return (_TM1 shr 8)
    }
    fun _set_sfr_timer_TM1H(value: Int): Unit {
        _TM1_PRELOAD = ((_TM1_PRELOAD and 255) or (value shl 8))
        _TM1 = _TM1_PRELOAD
    }
    fun _set_sfr_timer_TM1Load_Ctrl(value: Int): Unit {
        _TM1_LOAD = value
    }
    fun _get_sfr_timer_TM0L(): Int {
        return (_TM0 and 255)
    }
    fun _set_sfr_timer_TM0L(value: Int): Unit {
        _TM0_PRELOAD = ((_TM0_PRELOAD and 65280) or value)
    }
    fun _get_sfr_timer_TM0H(): Int {
        return (_TM0 shr 8)
    }
    fun _set_sfr_timer_TM0H(value: Int): Unit {
        _TM0_PRELOAD = ((_TM0_PRELOAD and 255) or (value shl 8))
        _TM0 = _TM0_PRELOAD
    }
    fun _set_sfr_timer_TM0Load_Ctrl(value: Int): Unit {
        _TM0_LOAD = value
    }
    fun _get_sfr_lcd_ctrl(): Int {
        return _LCD_CTRL
    }
    fun _set_sfr_lcd_ctrl(value: Int): Unit {
        _LCD_CTRL = value
    }
    fun _get_sfr_dummy(): Int {
        return 0
    }
    fun _set_sfr_dummy(value: Int): Unit {
    }
    fun _get_ps(): Int {
        return (((((((_NF shl 7) or (_VF shl 6)) or (_BF shl 4)) or (_DF shl 3)) or (_IF shl 2)) or (_ZF shl 1)) or _CF)
    }
    fun _set_ps(ps: Int): Unit {
        _NF = (ps shr 7)
        _VF = (if (((ps and 64) > 0)) 1 else 0)
        _BF = (if (((ps and 16) > 0)) 1 else 0)
        _DF = (if (((ps and 8) > 0)) 1 else 0)
        _IF = (if (((ps and 4) > 0)) 1 else 0)
        _ZF = (if (((ps and 2) > 0)) 1 else 0)
        _CF = (ps and 1)
    }
    fun _adc(operand: Int): Unit {
        var A: Int = 0
        var new_value: Int = 0
        A = _A
        new_value = ((A + operand) + _CF)
        if (((_DF != 0) && ((((A and 15) + (operand and 15)) + _CF) > 9))) {
            new_value = (new_value + 6)
        }
        _VF = (((((A xor operand)).inv() and (A xor new_value)) shr 7) and 1)
        _NF = ((new_value shr 7) and 1)
        if (((_DF != 0) && (new_value > 153))) {
            new_value = (new_value + 96)
        }
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        _A = (new_value and 255)
    }
    fun _sbc(operand: Int): Unit {
        var A: Int = 0
        var new_value: Int = 0
        A = _A
        new_value = ((A - operand) - (if (!((_CF != 0))) 1 else 0))
        if ((_DF != 0)) {
            if (((((A and 15) - (operand and 15)) - (if (!((_CF != 0))) 1 else 0)) < 0)) {
                new_value = (new_value - 6)
            }
            if ((new_value < 0)) {
                new_value = (new_value - 96)
            }
        }
        _VF = ((((A xor operand) and (A xor new_value)) shr 7) and 1)
        _NF = ((new_value shr 7) and 1)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value >= 0)) 1 else 0)
        _A = (new_value and 255)
    }
    fun _brk(): Int {
        _BF = 1
        _IRQ()
        return 7
    }
    fun _ora_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _A = (_A or _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 6
    }
    fun _ora_zp(): Int {
        _A = (_A or _read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 3
    }
    fun _asl_zp(): Int {
        var new_value: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        new_value = (_read_mem(zp) shl 1)
        _write_mem(zp, (new_value and 255))
        _PC = ((_PC + 1) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 5
    }
    fun _php(): Int {
        _write_mem((_SP or SP), _get_ps())
        _SP = ((_SP - 1) and 255)
        return 3
    }
    fun _ora_imm(): Int {
        _A = (_A or _read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 2
    }
    fun _asl_a(): Int {
        var new_value: Int = 0
        new_value = (_A shl 1)
        _A = (new_value and 255)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (new_value shr 8)
        return 2
    }
    fun _ora_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _A = (_A or _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _asl_abs(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        new_value = (_read_mem(addr) shl 1)
        _write_mem(addr, (new_value and 255))
        _PC = ((_PC + 2) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 6
    }
    fun _bpl(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if (!((_NF != 0))) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _ora_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = (_A or _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (5 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _ora_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _A = (_A or _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 3
    }
    fun _asl_zp_x(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        new_value = (_read_mem(addr) shl 1)
        _write_mem(addr, (new_value and 255))
        _PC = ((_PC + 1) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 6
    }
    fun _ora_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = (_A or _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _and_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _A = (_A and _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 6
    }
    fun _ora_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _A = (_A or _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _asl_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var new_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        new_value = (_read_mem(addr) shl 1)
        _write_mem(addr, (new_value and 255))
        _PC = ((_PC + 2) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return (6 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _clc(): Int {
        _CF = 0
        return 2
    }
    fun _jsr_abs(): Int {
        var pc: Int = 0
        pc = ((_PC + 1) and 65535)
        _write_mem((_SP or SP), (pc shr 8))
        _SP = ((_SP - 1) and 255)
        _write_mem((_SP or SP), (pc and 255))
        _SP = ((_SP - 1) and 255)
        _PC = (_read_mem(_PC) or (_read_mem(pc) shl 8))
        return 6
    }
    fun _and_abs_y(): Int {
        var addr: Int = 0
        addr = (((_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8)) + _Y) and 65535)
        _A = (_A and _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _and_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _A = (_A and _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _rol_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var new_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        new_value = ((_read_mem(addr) shl 1) or _CF)
        _write_mem(addr, (new_value and 255))
        _PC = ((_PC + 2) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return (6 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _bit_zp(): Int {
        var m: Int = 0
        m = _read_mem(_read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = (m shr 7)
        _VF = (if (((m and 64) > 0)) 1 else 0)
        _ZF = (if (!(((_A and m) != 0))) 1 else 0)
        return 3
    }
    fun _and_zp(): Int {
        _A = (_A and _read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 3
    }
    fun _rol_zp(): Int {
        var new_value: Int = 0
        var opcode: Int = 0
        opcode = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        new_value = ((_read_mem(opcode) shl 1) or _CF)
        _write_mem(opcode, (new_value and 255))
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 5
    }
    fun _plp(): Int {
        _SP = ((_SP + 1) and 255)
        _set_ps(_read_mem((_SP or SP)))
        return 4
    }
    fun _and_imm(): Int {
        _A = (_A and _read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 2
    }
    fun _rol_a(): Int {
        var new_value: Int = 0
        new_value = ((_A shl 1) or _CF)
        _A = (new_value and 255)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 2
    }
    fun _bit_abs(): Int {
        var addr: Int = 0
        var m: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        m = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (m shr 7)
        _VF = (if (((m and 64) > 0)) 1 else 0)
        _ZF = (if (!(((_A and m) != 0))) 1 else 0)
        return 4
    }
    fun _and_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _A = (_A and _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _rol_abs(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        new_value = ((_read_mem(addr) shl 1) or _CF)
        _write_mem(addr, (new_value and 255))
        _PC = ((_PC + 2) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 5
    }
    fun _bmi(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if ((_NF != 0)) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _and_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = (_A and _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (5 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _and_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _A = (_A and _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 6
    }
    fun _rol_zp_x(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        new_value = ((_read_mem(addr) shl 1) or _CF)
        _write_mem(addr, (new_value and 255))
        _PC = ((_PC + 1) and 65535)
        _NF = (if (((new_value and 128) > 0)) 1 else 0)
        _ZF = (if (!(((new_value and 255) != 0))) 1 else 0)
        _CF = (if ((new_value > 255)) 1 else 0)
        return 6
    }
    fun _sec(): Int {
        _CF = 1
        return 2
    }
    fun _rti(): Int {
        _SP = ((_SP + 1) and 255)
        _set_ps(_read_mem((_SP or SP)))
        _SP = ((_SP + 1) and 255)
        _PC = _read_mem((_SP or SP))
        _SP = ((_SP + 1) and 255)
        _PC = (_PC or (_read_mem((_SP or SP)) shl 8))
        return 6
    }
    fun _eor_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _A = (_A xor _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _eor_zp(): Int {
        _A = (_A xor _read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 3
    }
    fun _lsr_zp(): Int {
        var addr: Int = 0
        var prev_value: Int = 0
        addr = _read_mem(_PC)
        prev_value = _read_mem(addr)
        _write_mem(addr, (prev_value shr 1))
        _PC = ((_PC + 1) and 65535)
        _NF = 0
        _ZF = (if (!(((prev_value and 254) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 5
    }
    fun _pha(): Int {
        _write_mem((_SP or SP), _A)
        _SP = ((_SP - 1) and 255)
        return 3
    }
    fun _eor_imm(): Int {
        _A = (_A xor (_read_mem(_PC) and 255))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 2
    }
    fun _lsr_a(): Int {
        var prev_value: Int = 0
        prev_value = _A
        _A = (prev_value shr 1)
        _NF = 0
        _ZF = (if (!(((prev_value and 254) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 2
    }
    fun _jmp_abs(): Int {
        _PC = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        return 3
    }
    fun _eor_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _A = (_A xor _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _lsr_abs(): Int {
        var addr: Int = 0
        var prev_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        prev_value = _read_mem(addr)
        _write_mem(addr, (prev_value shr 1))
        _PC = ((_PC + 2) and 65535)
        _NF = 0
        _ZF = (if (!(((prev_value and 254) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 6
    }
    fun _bvc(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if (!((_VF != 0))) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _eor_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = (_A xor _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (5 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _eor_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _A = (_A xor _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _lsr_zp_x(): Int {
        var addr: Int = 0
        var prev_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        prev_value = _read_mem(addr)
        _write_mem(addr, (prev_value shr 1))
        _PC = ((_PC + 1) and 65535)
        _NF = 0
        _ZF = (if (!(((prev_value and 254) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 6
    }
    fun _cli(): Int {
        _IF = 0
        return 2
    }
    fun _eor_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = (_A xor _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _eor_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _A = (_A xor _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _lsr_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var prev_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        prev_value = _read_mem(addr)
        _write_mem(addr, (prev_value shr 1))
        _PC = ((_PC + 2) and 65535)
        _NF = 0
        _ZF = (if (!(((prev_value and 254) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return (6 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _rts(): Int {
        _SP = ((_SP + 1) and 255)
        _PC = _read_mem((_SP or SP))
        _SP = ((_SP + 1) and 255)
        _PC = (_PC or (_read_mem((_SP or SP)) shl 8))
        _PC = (_PC + 1)
        return 6
    }
    fun _adc_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _adc(_read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        return 6
    }
    fun _adc_zp(): Int {
        _adc(_read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        return 3
    }
    fun _ror_zp(): Int {
        var opcode: Int = 0
        var prev_value: Int = 0
        opcode = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        prev_value = _read_mem(opcode)
        _write_mem(opcode, ((prev_value shr 1) or (_CF shl 7)))
        _NF = _CF
        _ZF = (if (!((((prev_value and 254) or _CF) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 5
    }
    fun _pla(): Int {
        _SP = ((_SP + 1) and 255)
        _A = _read_mem((_SP or SP))
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _adc_imm(): Int {
        _adc(_read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _ror_a(): Int {
        var prev_value: Int = 0
        prev_value = _A
        _A = ((prev_value shr 1) or (_CF shl 7))
        _NF = _CF
        _ZF = (if (!((((prev_value and 254) or _CF) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 2
    }
    fun _jmp_ind(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _PC = _read_mem(addr)
        _PC = (_PC or (_read_mem(((addr + 1) and 65535)) shl 8))
        return 6
    }
    fun _adc_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _adc(_read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        return 4
    }
    fun _ror_abs(): Int {
        var addr: Int = 0
        var prev_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        prev_value = _read_mem(addr)
        _write_mem(addr, ((prev_value shr 1) or (_CF shl 7)))
        _PC = ((_PC + 2) and 65535)
        _NF = _CF
        _ZF = (if (!((((prev_value and 254) or _CF) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 6
    }
    fun _bvs(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if ((_VF != 0)) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _adc_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _adc(_read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        return (5 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _adc_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _adc(_read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        return 5
    }
    fun _ror_zp_x(): Int {
        var addr: Int = 0
        var prev_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        prev_value = _read_mem(addr)
        _write_mem(addr, ((prev_value shr 1) or (_CF shl 7)))
        _PC = ((_PC + 2) and 65535)
        _NF = _CF
        _ZF = (if (!((((prev_value and 254) or _CF) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return 6
    }
    fun _sei(): Int {
        _IF = 1
        return 2
    }
    fun _adc_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        _adc(_read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _adc_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _adc(_read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _ror_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var prev_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        prev_value = _read_mem(addr)
        _write_mem(addr, ((prev_value shr 1) or (_CF shl 7)))
        _PC = ((_PC + 2) and 65535)
        _NF = _CF
        _ZF = (if (!((((prev_value and 254) or _CF) != 0))) 1 else 0)
        _CF = (prev_value and 1)
        return (6 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _sta_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _write_mem(addr, _A)
        _PC = ((_PC + 1) and 65535)
        return 6
    }
    fun _sty_zp(): Int {
        _write_mem(_read_mem(_PC), _Y)
        _PC = ((_PC + 1) and 65535)
        return 3
    }
    fun _sta_zp(): Int {
        _write_mem(_read_mem(_PC), _A)
        _PC = ((_PC + 1) and 65535)
        return 3
    }
    fun _stx_zp(): Int {
        _write_mem(_read_mem(_PC), _X)
        _PC = ((_PC + 1) and 65535)
        return 3
    }
    fun _dey(): Int {
        _Y = ((_Y - 1) and 255)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 2
    }
    fun _txa(): Int {
        _A = _X
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 2
    }
    fun _sty_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _write_mem(addr, _Y)
        _PC = ((_PC + 2) and 65535)
        return 4
    }
    fun _sta_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _write_mem(addr, _A)
        _PC = ((_PC + 2) and 65535)
        return 4
    }
    fun _stx_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _write_mem(addr, _X)
        _PC = ((_PC + 2) and 65535)
        return 4
    }
    fun _bcc(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if (!((_CF != 0))) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _sta_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _write_mem(addr, _A)
        _PC = ((_PC + 1) and 65535)
        return 6
    }
    fun _sty_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _write_mem(addr, _Y)
        _PC = ((_PC + 1) and 65535)
        return 4
    }
    fun _sta_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _write_mem(addr, _A)
        _PC = ((_PC + 1) and 65535)
        return 4
    }
    fun _stx_zp_y(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _Y) and 255)
        _write_mem(addr, _X)
        _PC = ((_PC + 1) and 65535)
        return 4
    }
    fun _tya(): Int {
        _A = _Y
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 2
    }
    fun _sta_abs_y(): Int {
        var addr: Int = 0
        addr = (((_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8)) + _Y) and 65535)
        _write_mem(addr, _A)
        _PC = ((_PC + 2) and 65535)
        return 5
    }
    fun _txs(): Int {
        _SP = _X
        return 2
    }
    fun _sta_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _write_mem(addr, _A)
        _PC = ((_PC + 2) and 65535)
        return 5
    }
    fun _ldy_imm(): Int {
        _Y = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 2
    }
    fun _lda_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _A = _read_mem(addr)
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 6
    }
    fun _ldx_imm(): Int {
        _X = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 2
    }
    fun _ldy_zp(): Int {
        _Y = _read_mem(_read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 3
    }
    fun _lda_zp(): Int {
        _A = _read_mem(_read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 3
    }
    fun _ldx_zp(): Int {
        _X = _read_mem(_read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 3
    }
    fun _tay(): Int {
        _Y = _A
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 2
    }
    fun _lda_imm(): Int {
        _A = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 2
    }
    fun _tax(): Int {
        _X = _A
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 2
    }
    fun _ldy_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _Y = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 4
    }
    fun _lda_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _A = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _ldx_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _X = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 4
    }
    fun _bcs(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if ((_CF != 0)) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _lda_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = _read_mem(addr)
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (5 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _ldy_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _Y = _read_mem(addr)
        _PC = ((_PC + 1) and 65535)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 4
    }
    fun _lda_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _A = _read_mem(addr)
        _PC = ((_PC + 1) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return 4
    }
    fun _ldx_zp_y(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _Y) and 255)
        _X = _read_mem(addr)
        _PC = ((_PC + 1) and 65535)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 4
    }
    fun _clv(): Int {
        _VF = 0
        return 2
    }
    fun _lda_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        _A = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _tsx(): Int {
        _X = _SP
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 2
    }
    fun _ldy_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _Y = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _lda_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _A = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_A shr 7)
        _ZF = (if (!((_A != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _ldx_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        _X = _read_mem(addr)
        _PC = ((_PC + 2) and 65535)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _cpy_imm(): Int {
        var test_value: Int = 0
        test_value = (_Y - _read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 2
    }
    fun _cmp_ind_x(): Int {
        var addr: Int = 0
        var test_value: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        test_value = (_A - _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 6
    }
    fun _cpy_zp(): Int {
        var test_value: Int = 0
        test_value = (_Y - _read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 3
    }
    fun _cmp_zp(): Int {
        var test_value: Int = 0
        test_value = (_A - _read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 3
    }
    fun _dec_zp(): Int {
        var new_value: Int = 0
        var opcode: Int = 0
        opcode = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        new_value = ((_read_mem(opcode) - 1) and 255)
        _write_mem(opcode, new_value)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 5
    }
    fun _iny(): Int {
        _Y = ((_Y + 1) and 255)
        _NF = (_Y shr 7)
        _ZF = (if (!((_Y != 0))) 1 else 0)
        return 2
    }
    fun _cmp_imm(): Int {
        var test_value: Int = 0
        test_value = (_A - _read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 2
    }
    fun _cpy_abs(): Int {
        var addr: Int = 0
        var test_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        test_value = (_Y - _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 3
    }
    fun _cmp_abs(): Int {
        var addr: Int = 0
        var test_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        test_value = (_A - _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 4
    }
    fun _dec_abs(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        new_value = ((_read_mem(addr) - 1) and 255)
        _write_mem(addr, new_value)
        _PC = ((_PC + 2) and 65535)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 5
    }
    fun _dex(): Int {
        _X = ((_X - 1) and 255)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 2
    }
    fun _bne(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if (!((_ZF != 0))) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _cmp_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var test_value: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        test_value = (_A - _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return (5 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _cmp_zp_x(): Int {
        var addr: Int = 0
        var test_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        test_value = (_A - _read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 4
    }
    fun _dec_zp_x(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        new_value = ((_read_mem(addr) - 1) and 255)
        _write_mem(addr, new_value)
        _PC = ((_PC + 1) and 65535)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 6
    }
    fun _cld(): Int {
        _DF = 0
        return 2
    }
    fun _cmp_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var test_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        test_value = (_A - _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _cmp_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var test_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        test_value = (_A - _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _dec_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var new_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        new_value = ((_read_mem(addr) - 1) and 255)
        _write_mem(addr, new_value)
        _PC = ((_PC + 2) and 65535)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 7
    }
    fun _cpx_imm(): Int {
        var test_value: Int = 0
        test_value = (_X - _read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 2
    }
    fun _sbc_ind_x(): Int {
        var addr: Int = 0
        var zp: Int = 0
        zp = (_read_mem(_PC) + _X)
        addr = (_read_mem((zp and 255)) or (_read_mem(((zp + 1) and 255)) shl 8))
        _sbc(_read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        return 6
    }
    fun _cpx_zp(): Int {
        var test_value: Int = 0
        test_value = (_X - _read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 3
    }
    fun _sbc_zp(): Int {
        _sbc(_read_mem(_read_mem(_PC)))
        _PC = ((_PC + 1) and 65535)
        return 3
    }
    fun _inc_zp(): Int {
        var new_value: Int = 0
        var opcode: Int = 0
        opcode = _read_mem(_PC)
        _PC = ((_PC + 1) and 65535)
        new_value = ((_read_mem(opcode) + 1) and 255)
        _write_mem(opcode, new_value)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 5
    }
    fun _inx(): Int {
        _X = ((_X + 1) and 255)
        _NF = (_X shr 7)
        _ZF = (if (!((_X != 0))) 1 else 0)
        return 2
    }
    fun _sbc_imm(): Int {
        _sbc(_read_mem(_PC))
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _cpx_abs(): Int {
        var addr: Int = 0
        var test_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        test_value = (_X - _read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        _NF = ((test_value shr 7) and 1)
        _ZF = (if (!((test_value != 0))) 1 else 0)
        _CF = (if ((test_value >= 0)) 1 else 0)
        return 4
    }
    fun _sbc_abs(): Int {
        var addr: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        _sbc(_read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        return 4
    }
    fun _inc_abs(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        new_value = ((_read_mem(addr) + 1) and 255)
        _write_mem(addr, new_value)
        _PC = ((_PC + 2) and 65535)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 6
    }
    fun _nop(): Int {
        return 2
    }
    fun _beq(): Int {
        var prev_PC: Int = 0
        var rel: Int = 0
        if ((_ZF != 0)) {
            rel = _read_mem(_PC)
            prev_PC = ((_PC + 1) and 65535)
            _PC = (((prev_PC + rel) - ((rel and 128) shl 1)) and 65535)
            return (3 + (if (((_PC xor prev_PC) > 255)) 1 else 0))
        }
        _PC = ((_PC + 1) and 65535)
        return 2
    }
    fun _sbc_ind_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        var zp: Int = 0
        zp = _read_mem(_PC)
        base = (_read_mem(zp) or (_read_mem(((zp + 1) and 255)) shl 8))
        addr = ((base + _Y) and 65535)
        _sbc(_read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _sbc_zp_x(): Int {
        var addr: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        _sbc(_read_mem(addr))
        _PC = ((_PC + 1) and 65535)
        return 4
    }
    fun _inc_zp_x(): Int {
        var addr: Int = 0
        var new_value: Int = 0
        addr = ((_read_mem(_PC) + _X) and 255)
        new_value = ((_read_mem(addr) + 1) and 255)
        _write_mem(addr, new_value)
        _PC = ((_PC + 1) and 65535)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 6
    }
    fun _sed(): Int {
        _DF = 1
        return 2
    }
    fun _sbc_abs_y(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _Y) and 65535)
        _sbc(_read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _sbc_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        _sbc(_read_mem(addr))
        _PC = ((_PC + 2) and 65535)
        return (4 + (if (((base xor addr) > 255)) 1 else 0))
    }
    fun _inc_abs_x(): Int {
        var addr: Int = 0
        var base: Int = 0
        var new_value: Int = 0
        base = (_read_mem(_PC) or (_read_mem(((_PC + 1) and 65535)) shl 8))
        addr = ((base + _X) and 65535)
        new_value = ((_read_mem(addr) + 1) and 255)
        _write_mem(addr, new_value)
        _PC = ((_PC + 2) and 65535)
        _NF = (new_value shr 7)
        _ZF = (if (!((new_value != 0))) 1 else 0)
        return 7
    }
    fun _read_mem(addr: Int): Int {
        if (addr >= CPU_RAM_OFFSET) return _mem[((addr and 0x7FFF) or (_BANK * (addr and 0x8000))) % _mem_size]
        return when(addr) {
            0 -> _get_sfr_dummy()
            1 -> _get_sfr_dummy()
            2 -> _get_sfr_io_portAB_data()
            3 -> _get_sfr_io_portCD_data()
            4 -> _get_sfr_io_portEF_data()
            6 -> _get_sfr_dummy()
            7 -> _get_sfr_bank_sel()
            8 -> _get_sfr_wakeup_ctrl()
            9 -> _get_sfr_dummy()
            10 -> _get_sfr_timer_timebase_sel()
            11 -> _get_sfr_dummy()
            12 -> _get_sfr_dummy()
            13 -> _get_sfr_int_ctrl()
            16 -> _get_sfr_audio_ch0_data()
            17 -> _get_sfr_audio_ch1_data()
            18 -> _get_sfr_timer_TM1L()
            19 -> _get_sfr_timer_TM1H()
            20 -> _get_sfr_dummy()
            21 -> _get_sfr_timer_TM0L()
            22 -> _get_sfr_timer_TM0H()
            23 -> _get_sfr_dummy()
            24 -> _get_sfr_dummy()
            25 -> _get_sfr_dummy()
            26 -> _get_sfr_audio_ch0_ctrl()
            27 -> _get_sfr_audio_ch1_ctrl()
            31 -> _get_sfr_dummy()
            32 -> _get_sfr_dummy()
            else -> 0
        }
    }
    fun _write_mem(addr: Int, value: Int) {
        if (addr in CPU_RAM_OFFSET until DPRAM_OFFSET + DPRAM_SIZE) { _mem[addr % _mem_size] = value; return }
        when(addr) {
            0 -> _set_sfr_io_portCD_dir(value)
            1 -> _set_sfr_io_portAB_dir(value)
            2 -> _set_sfr_io_portAB_data(value)
            3 -> _set_sfr_io_portCD_data(value)
            4 -> _set_sfr_io_portEF_data(value)
            6 -> _set_sfr_io_portEF_dir(value)
            7 -> _set_sfr_bank_sel(value)
            8 -> _set_sfr_wakeup_ctrl(value)
            9 -> _set_sfr_enter_sleep(value)
            10 -> _set_sfr_timer_timebase_sel(value)
            11 -> _set_sfr_timer_ctrl(value)
            12 -> _set_sfr_clk_32768_en(value)
            13 -> _set_sfr_int_ctrl(value)
            16 -> _set_sfr_audio_ch0_data(value)
            17 -> _set_sfr_audio_ch1_data(value)
            18 -> _set_sfr_timer_TM1L(value)
            19 -> _set_sfr_timer_TM1H(value)
            20 -> _set_sfr_timer_TM1Load_Ctrl(value)
            21 -> _set_sfr_timer_TM0L(value)
            22 -> _set_sfr_timer_TM0H(value)
            23 -> _set_sfr_timer_TM0Load_Ctrl(value)
            24 -> _set_sfr_clk_int_clear(value)
            25 -> _set_sfr_nmi_ctrl(value)
            26 -> _set_sfr_audio_ch0_ctrl(value)
            27 -> _set_sfr_audio_ch1_ctrl(value)
            31 -> _set_sfr_clk_cpu_ctrl(value)
            32 -> _set_sfr_lcd_ctrl(value)
        }
    }
    fun clock(): Double {
        val cycles: Double
        if (_CPU_ENBL != 0) {
            val pc = _PC
            _PC = (pc + 1) and 0xFFFF
            val opcode = _mem[((pc and 0x7FFF) or (_BANK * (pc and 0x8000))) % _mem_size]
            val instructionCycles = when(opcode) {
                0 -> _brk()
                1 -> _ora_ind_x()
                2 -> _dummy()
                3 -> _dummy()
                4 -> _dummy()
                5 -> _ora_zp()
                6 -> _asl_zp()
                7 -> _dummy()
                8 -> _php()
                9 -> _ora_imm()
                10 -> _asl_a()
                11 -> _dummy()
                12 -> _dummy()
                13 -> _ora_abs()
                14 -> _asl_abs()
                15 -> _dummy()
                16 -> _bpl()
                17 -> _ora_ind_y()
                18 -> _dummy()
                19 -> _dummy()
                20 -> _dummy()
                21 -> _ora_zp_x()
                22 -> _asl_zp_x()
                23 -> _dummy()
                24 -> _clc()
                25 -> _ora_abs_y()
                26 -> _dummy()
                27 -> _dummy()
                28 -> _dummy()
                29 -> _ora_abs_x()
                30 -> _asl_abs_x()
                31 -> _dummy()
                32 -> _jsr_abs()
                33 -> _and_ind_x()
                34 -> _dummy()
                35 -> _dummy()
                36 -> _bit_zp()
                37 -> _and_zp()
                38 -> _rol_zp()
                39 -> _dummy()
                40 -> _plp()
                41 -> _and_imm()
                42 -> _rol_a()
                43 -> _dummy()
                44 -> _bit_abs()
                45 -> _and_abs()
                46 -> _rol_abs()
                47 -> _dummy()
                48 -> _bmi()
                49 -> _and_ind_y()
                50 -> _dummy()
                51 -> _dummy()
                52 -> _dummy()
                53 -> _and_zp_x()
                54 -> _rol_zp_x()
                55 -> _dummy()
                56 -> _sec()
                57 -> _and_abs_y()
                58 -> _dummy()
                59 -> _dummy()
                60 -> _dummy()
                61 -> _and_abs_x()
                62 -> _rol_abs_x()
                63 -> _dummy()
                64 -> _rti()
                65 -> _eor_ind_x()
                66 -> _dummy()
                67 -> _dummy()
                68 -> _dummy()
                69 -> _eor_zp()
                70 -> _lsr_zp()
                71 -> _dummy()
                72 -> _pha()
                73 -> _eor_imm()
                74 -> _lsr_a()
                75 -> _dummy()
                76 -> _jmp_abs()
                77 -> _eor_abs()
                78 -> _lsr_abs()
                79 -> _dummy()
                80 -> _bvc()
                81 -> _eor_ind_y()
                82 -> _dummy()
                83 -> _dummy()
                84 -> _dummy()
                85 -> _eor_zp_x()
                86 -> _lsr_zp_x()
                87 -> _dummy()
                88 -> _cli()
                89 -> _eor_abs_y()
                90 -> _dummy()
                91 -> _dummy()
                92 -> _dummy()
                93 -> _eor_abs_x()
                94 -> _lsr_abs_x()
                95 -> _dummy()
                96 -> _rts()
                97 -> _adc_ind_x()
                98 -> _dummy()
                99 -> _dummy()
                100 -> _dummy()
                101 -> _adc_zp()
                102 -> _ror_zp()
                103 -> _dummy()
                104 -> _pla()
                105 -> _adc_imm()
                106 -> _ror_a()
                107 -> _dummy()
                108 -> _jmp_ind()
                109 -> _adc_abs()
                110 -> _ror_abs()
                111 -> _dummy()
                112 -> _bvs()
                113 -> _adc_ind_y()
                114 -> _dummy()
                115 -> _dummy()
                116 -> _dummy()
                117 -> _adc_zp_x()
                118 -> _ror_zp_x()
                119 -> _dummy()
                120 -> _sei()
                121 -> _adc_abs_y()
                122 -> _dummy()
                123 -> _dummy()
                124 -> _dummy()
                125 -> _adc_abs_x()
                126 -> _ror_abs_x()
                127 -> _dummy()
                128 -> _dummy()
                129 -> _sta_ind_x()
                130 -> _dummy()
                131 -> _dummy()
                132 -> _sty_zp()
                133 -> _sta_zp()
                134 -> _stx_zp()
                135 -> _dummy()
                136 -> _dey()
                137 -> _dummy()
                138 -> _txa()
                139 -> _dummy()
                140 -> _sty_abs()
                141 -> _sta_abs()
                142 -> _stx_abs()
                143 -> _dummy()
                144 -> _bcc()
                145 -> _sta_ind_y()
                146 -> _dummy()
                147 -> _dummy()
                148 -> _sty_zp_x()
                149 -> _sta_zp_x()
                150 -> _stx_zp_y()
                151 -> _dummy()
                152 -> _tya()
                153 -> _sta_abs_y()
                154 -> _txs()
                155 -> _dummy()
                156 -> _dummy()
                157 -> _sta_abs_x()
                158 -> _dummy()
                159 -> _dummy()
                160 -> _ldy_imm()
                161 -> _lda_ind_x()
                162 -> _ldx_imm()
                163 -> _dummy()
                164 -> _ldy_zp()
                165 -> _lda_zp()
                166 -> _ldx_zp()
                167 -> _dummy()
                168 -> _tay()
                169 -> _lda_imm()
                170 -> _tax()
                171 -> _dummy()
                172 -> _ldy_abs()
                173 -> _lda_abs()
                174 -> _ldx_abs()
                175 -> _dummy()
                176 -> _bcs()
                177 -> _lda_ind_y()
                178 -> _dummy()
                179 -> _dummy()
                180 -> _ldy_zp_x()
                181 -> _lda_zp_x()
                182 -> _ldx_zp_y()
                183 -> _dummy()
                184 -> _clv()
                185 -> _lda_abs_y()
                186 -> _tsx()
                187 -> _dummy()
                188 -> _ldy_abs_x()
                189 -> _lda_abs_x()
                190 -> _ldx_abs_y()
                191 -> _dummy()
                192 -> _cpy_imm()
                193 -> _cmp_ind_x()
                194 -> _dummy()
                195 -> _dummy()
                196 -> _cpy_zp()
                197 -> _cmp_zp()
                198 -> _dec_zp()
                199 -> _dummy()
                200 -> _iny()
                201 -> _cmp_imm()
                202 -> _dex()
                203 -> _dummy()
                204 -> _cpy_abs()
                205 -> _cmp_abs()
                206 -> _dec_abs()
                207 -> _dummy()
                208 -> _bne()
                209 -> _cmp_ind_y()
                210 -> _dummy()
                211 -> _dummy()
                212 -> _dummy()
                213 -> _cmp_zp_x()
                214 -> _dec_zp_x()
                215 -> _dummy()
                216 -> _cld()
                217 -> _cmp_abs_y()
                218 -> _dummy()
                219 -> _dummy()
                220 -> _dummy()
                221 -> _cmp_abs_x()
                222 -> _dec_abs_x()
                223 -> _dummy()
                224 -> _cpx_imm()
                225 -> _sbc_ind_x()
                226 -> _dummy()
                227 -> _dummy()
                228 -> _cpx_zp()
                229 -> _sbc_zp()
                230 -> _inc_zp()
                231 -> _dummy()
                232 -> _inx()
                233 -> _sbc_imm()
                234 -> _nop()
                235 -> _dummy()
                236 -> _cpx_abs()
                237 -> _sbc_abs()
                238 -> _inc_abs()
                239 -> _dummy()
                240 -> _beq()
                241 -> _sbc_ind_y()
                242 -> _dummy()
                243 -> _dummy()
                244 -> _dummy()
                245 -> _sbc_zp_x()
                246 -> _inc_zp_x()
                247 -> _dummy()
                248 -> _sed()
                249 -> _sbc_abs_y()
                250 -> _dummy()
                251 -> _dummy()
                252 -> _dummy()
                253 -> _sbc_abs_x()
                254 -> _inc_abs_x()
                255 -> _dummy()
                else -> error("Invalid opcode")
            }
            cycles = (instructionCycles * _CPU_DIV).toDouble()
            _instr_counter++
            _timers_clock(cycles)
            if (_IF == 0 && (_IREQ and _INT_CTRL) != 0) _IRQ()
        } else {
            cycles = _sub_clock_div
            if ((_WAKEUP_CTRL and _WAKEUPREQ) != 0) { _CPU_ENBL = 1; _CPU_DIV = SFR_CPU_DIV_DEFAULT }
            if (_CLK32K_ENABLE != 0) _timers_clock(cycles)
        }
        sound.advanceAudio(cycles)
        return cycles
    }
    private fun _dummy() = 2
    fun runCycles(target: Double): Double {
        var elapsed = 0.0
        while (elapsed < target) elapsed += clock()
        return elapsed
    }
}
