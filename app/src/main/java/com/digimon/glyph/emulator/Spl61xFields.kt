package com.digimon.glyph.emulator

import com.google.gson.JsonObject

internal fun Spl61xCore.fields(): JsonObject = JsonObject().also { o ->
    o.addProperty("_A", _A)
    o.addProperty("_AUDIO_CH0_CTRL", _AUDIO_CH0_CTRL)
    o.addProperty("_AUDIO_CH0_DATA", _AUDIO_CH0_DATA)
    o.addProperty("_AUDIO_CH1_CTRL", _AUDIO_CH1_CTRL)
    o.addProperty("_AUDIO_CH1_DATA", _AUDIO_CH1_DATA)
    o.addProperty("_BANK", _BANK)
    o.addProperty("_BF", _BF)
    o.addProperty("_CF", _CF)
    o.addProperty("_CLK32K_ENABLE", _CLK32K_ENABLE)
    o.addProperty("_CLK_CPU_CTRL", _CLK_CPU_CTRL)
    o.addProperty("_CPU_DIV", _CPU_DIV)
    o.addProperty("_CPU_ENBL", _CPU_ENBL)
    o.addProperty("_DF", _DF)
    o.addProperty("_ENTER_SLEEP", _ENTER_SLEEP)
    o.addProperty("_IF", _IF)
    o.addProperty("_INT_CLEAR", _INT_CLEAR)
    o.addProperty("_INT_CTRL", _INT_CTRL)
    o.addProperty("_IO_CTRL", _IO_CTRL)
    o.addProperty("_IREQ", _IREQ)
    o.addProperty("_LCD_CTRL", _LCD_CTRL)
    o.addProperty("_NF", _NF)
    o.addProperty("_NMI_CTRL", _NMI_CTRL)
    o.addProperty("_PC", _PC)
    o.addProperty("_SP", _SP)
    o.addProperty("_T128HZ_counter", _T128HZ_counter)
    o.addProperty("_T2K_counter", _T2K_counter)
    o.addProperty("_T64HZ", _T64HZ)
    o.addProperty("_T64HZ_counter", _T64HZ_counter)
    o.addProperty("_TIMER_CTRL", _TIMER_CTRL)
    o.addProperty("_TIME_BASE", _TIME_BASE)
    o.addProperty("_TM0", _TM0)
    o.addProperty("_TM0_LOAD", _TM0_LOAD)
    o.addProperty("_TM0_PRELOAD", _TM0_PRELOAD)
    o.addProperty("_TM0_counter", _TM0_counter)
    o.addProperty("_TM0_div", _TM0_div)
    o.addProperty("_TM1", _TM1)
    o.addProperty("_TM1_LOAD", _TM1_LOAD)
    o.addProperty("_TM1_PRELOAD", _TM1_PRELOAD)
    o.addProperty("_TM1_counter", _TM1_counter)
    o.addProperty("_TM1_div", _TM1_div)
    o.addProperty("_VF", _VF)
    o.addProperty("_WAKEUPREQ", _WAKEUPREQ)
    o.addProperty("_WAKEUP_CTRL", _WAKEUP_CTRL)
    o.addProperty("_X", _X)
    o.addProperty("_Y", _Y)
    o.addProperty("_ZF", _ZF)
    o.addProperty("_clock", _clock)
    o.addProperty("_instr_counter", _instr_counter)
    o.addProperty("_mem_size", _mem_size)
    o.addProperty("_sub_clock_div", _sub_clock_div)
    o.add("_pullup_ext", JsonObject().also { p -> p.addProperty("AB", _pullup_ext[0]); p.addProperty("CD", _pullup_ext[1]); p.addProperty("EF", _pullup_ext[2]) })
    o.add("_PDIR", JsonObject().also { p -> p.addProperty("AB", _PDIR[0]); p.addProperty("CD", _PDIR[1]); p.addProperty("EF", _PDIR[2]) })
    o.add("_PLATCH", JsonObject().also { p -> p.addProperty("AB", _PLATCH[0]); p.addProperty("CD", _PLATCH[1]); p.addProperty("EF", _PLATCH[2]) })
    o.add("_port_input", JsonObject().also { p ->
        p.add("AB", com.google.gson.JsonArray().also { a -> _port_input[0].forEach { a.add(it) } })
        p.add("CD", com.google.gson.JsonArray().also { a -> _port_input[1].forEach { a.add(it) } })
        p.add("EF", com.google.gson.JsonArray().also { a -> _port_input[2].forEach { a.add(it) } })
    })
}
internal fun Spl61xCore.loadFields(o: JsonObject) {
    _A = o["_A"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _AUDIO_CH0_CTRL = o["_AUDIO_CH0_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _AUDIO_CH0_DATA = o["_AUDIO_CH0_DATA"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _AUDIO_CH1_CTRL = o["_AUDIO_CH1_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _AUDIO_CH1_DATA = o["_AUDIO_CH1_DATA"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _BANK = o["_BANK"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _BF = o["_BF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _CF = o["_CF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _CLK32K_ENABLE = o["_CLK32K_ENABLE"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _CLK_CPU_CTRL = o["_CLK_CPU_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _CPU_DIV = o["_CPU_DIV"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _CPU_ENBL = o["_CPU_ENBL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _DF = o["_DF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _ENTER_SLEEP = o["_ENTER_SLEEP"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _IF = o["_IF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _INT_CLEAR = o["_INT_CLEAR"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _INT_CTRL = o["_INT_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _IO_CTRL = o["_IO_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _IREQ = o["_IREQ"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _LCD_CTRL = o["_LCD_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _NF = o["_NF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _NMI_CTRL = o["_NMI_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _PC = o["_PC"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _SP = o["_SP"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _T128HZ_counter = o["_T128HZ_counter"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _T2K_counter = o["_T2K_counter"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _T64HZ = o["_T64HZ"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _T64HZ_counter = o["_T64HZ_counter"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _TIMER_CTRL = o["_TIMER_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TIME_BASE = o["_TIME_BASE"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM0 = o["_TM0"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM0_LOAD = o["_TM0_LOAD"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM0_PRELOAD = o["_TM0_PRELOAD"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM0_counter = o["_TM0_counter"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _TM0_div = o["_TM0_div"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _TM1 = o["_TM1"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM1_LOAD = o["_TM1_LOAD"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM1_PRELOAD = o["_TM1_PRELOAD"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _TM1_counter = o["_TM1_counter"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _TM1_div = o["_TM1_div"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _VF = o["_VF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _WAKEUPREQ = o["_WAKEUPREQ"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _WAKEUP_CTRL = o["_WAKEUP_CTRL"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _X = o["_X"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _Y = o["_Y"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _ZF = o["_ZF"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _clock = o["_clock"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _instr_counter = o["_instr_counter"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _mem_size = o["_mem_size"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }
    _sub_clock_div = o["_sub_clock_div"].let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }
    _pullup_ext[0] = o["_pullup_ext"].asJsonObject["AB"].asInt
    _pullup_ext[1] = o["_pullup_ext"].asJsonObject["CD"].asInt
    _pullup_ext[2] = o["_pullup_ext"].asJsonObject["EF"].asInt
    _PDIR[0] = o["_PDIR"].asJsonObject["AB"].asInt
    _PDIR[1] = o["_PDIR"].asJsonObject["CD"].asInt
    _PDIR[2] = o["_PDIR"].asJsonObject["EF"].asInt
    _PLATCH[0] = o["_PLATCH"].asJsonObject["AB"].asInt
    _PLATCH[1] = o["_PLATCH"].asJsonObject["CD"].asInt
    _PLATCH[2] = o["_PLATCH"].asJsonObject["EF"].asInt
    for (j in 0..1) _port_input[0][j] = o["_port_input"].asJsonObject["AB"].asJsonArray[j].asInt
    for (j in 0..1) _port_input[1][j] = o["_port_input"].asJsonObject["CD"].asJsonArray[j].asInt
    for (j in 0..1) _port_input[2][j] = o["_port_input"].asJsonObject["EF"].asJsonArray[j].asInt
}
