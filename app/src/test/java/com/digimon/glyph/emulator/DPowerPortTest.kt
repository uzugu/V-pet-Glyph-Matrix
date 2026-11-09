package com.digimon.glyph.emulator

import com.google.gson.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DPowerPortTest {
    companion object {
        fun synthetic(): ByteArray = ByteArray(98304) { 0xea.toByte() }.apply {
            val code = intArrayOf(0xa9,1,0x85,0x1b,0xa9,0x80,0x85,0x0b,0xa9,2,0x85,0x20,0xa9,180,0x85,0x0f,
                0xa2,0,0xe8,0x86,0x40,0x8a,0x69,7,0x85,0x41,0x4c,0x12,0x04)
            code.forEachIndexed { i,v -> this[0x400+i]=v.toByte() }
            for (a in listOf(0x7ffa,0x7ffc,0x7ffe)) { this[a]=0; this[a+1]=4 }
        }
        fun root(): File = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }.first { File(it,"tools/dump_dpower_trace.py").exists() }
        fun golden(vararg args: String): JsonObject {
            val output = File.createTempFile("dpower-golden-", ".json")
            try {
                val process = ProcessBuilder(listOf("python3",File(root(),"tools/dump_dpower_trace.py").path,"--output",output.path)+args)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT).start()
                assertEquals("Python reference generation",0,process.waitFor())
                return JsonParser.parseString(output.readText()).asJsonObject
            } finally { output.delete() }
        }
        fun same(path: String, a: JsonElement, b: JsonElement) {
            when {
                a.isJsonObject -> { assertTrue(path,b.isJsonObject); assertEquals(path,a.asJsonObject.keySet(),b.asJsonObject.keySet()); a.asJsonObject.entrySet().forEach { (k,v) -> same("$path.$k",v,b.asJsonObject[k]) } }
                a.isJsonArray -> { assertEquals(path,a.asJsonArray.size(),b.asJsonArray.size()); a.asJsonArray.forEachIndexed { i,v -> same("$path[$i]",v,b.asJsonArray[i]) } }
                a.isJsonNull -> assertTrue(path,b.isJsonNull)
                a.asJsonPrimitive.isNumber || a.asJsonPrimitive.isBoolean -> {
                    fun n(x: JsonElement) = if(x.asJsonPrimitive.isBoolean) { if(x.asBoolean) 1.0 else 0.0 } else x.asDouble
                    assertEquals(path,n(a),n(b),0.0)
                }
                else -> assertEquals(path,a,b)
            }
        }
    }
    @Test fun allOpcodeSlotsMatchPythonIncludingDecimalAndPageFlags() {
        val trace=golden("--opcodes")
        for (entry in trace["opcodes"].asJsonArray) {
            val e=entry.asJsonObject; val op=e["opcode"].asInt;val seed=e["seed"].asInt
            val r=DPowerRuntime(synthetic());val c=r.core
            c._mem[0x400]=op;c._mem[0x401]=0x40;c._mem[0x402]=4
            c._A=seed*67;c._X=seed*17;c._Y=seed*31;c._SP=0xf0;c._set_ps(seed*85)
            assertEquals("opcode $op seed $seed",e["cycles"].asDouble,c.clock(),0.0)
            same("opcode $op seed $seed",e["state"],JsonParser.parseString(r.snapshot()))
        }
    }
    private fun compare(rom: ByteArray, trace: JsonObject, seconds: Int, idleSkip: Boolean = false) {
        val r=DPowerRuntime(rom);r.core.idleSkippingEnabled=idleSkip;var cycles=0.0
        val events=JsonArray()
        // Event timestamps exclude the current instruction, matching the Python bus.
        r.sound.onEvent={ channel,tone -> events.add(JsonArray().also { a -> a.add(r.sound.totalCycles); a.add(channel); a.add(tone?.json() ?: JsonNull.INSTANCE) }) }
        val digest=MessageDigest.getInstance("SHA-256")
        for(ms in 0 until seconds*1000) {
            when(ms) {1000->r.pin("EF",0,1);1100->r.pin("EF",0,0);3000->r.pin("EF",4,1);3100->r.pin("EF",4,0);5000->r.pin("EF",1,1);5100->r.pin("EF",1,0)}
            if(ms>=6000 && ms%500==0)r.pin("CD",1,1)
            if(ms>=6000 && ms%500==80)r.pin("CD",1,-1)
            cycles+=r.advance()
            if((ms+1)%10==0)digest.update(r.playback())
            if((ms+1)%1000==0) {
                val e=trace["checkpoints"].asJsonArray[(ms+1)/1000-1].asJsonObject
                assertEquals(e["cycles"].asDouble,cycles,0.0)
                same("ms ${ms+1}",e["state"],JsonParser.parseString(r.snapshot()))
            }
        }
        // Compare ordered audio values separately; cycle timestamps are checked by CPU checkpoints.
        val expected=trace["events"].asJsonArray
        assertEquals(expected.size(),events.size())
        for(i in 0 until events.size()) for(j in 0..2) same("audio $i/$j",expected[i].asJsonArray[j],events[i].asJsonArray[j])
        assertEquals(trace["pcmSha256"].asString,digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        if (idleSkip) {
            assertTrue("Real firmware polling optimization must be exercised", r.core.idleSkippedInstructions > 0)
            println("D-Power differential skipped ${r.core.idleSkippedInstructions} proven idle instructions")
        }
    }
    @Test fun syntheticTimersDacRamAndSnapshotsMatchPython() = compare(synthetic(),golden("--synthetic","--seconds","1"),1)
    @Test fun privateRomThirtySecondDifferential() {
        val path=System.getenv("DPOWER_ROM");assumeTrue("Set DPOWER_ROM",path!=null)
        val rom=File(path).readBytes()
        assertEquals(DPowerRuntime.ROM_SHA256,MessageDigest.getInstance("SHA-256").digest(rom).joinToString("") { "%02x".format(it.toInt() and 255) })
        val trace=golden("--seconds","30")
        compare(rom,trace,30)
        compare(rom,trace,30,idleSkip=true)
    }
    @Test fun toneMixerPcmAndFractionalStateMatchPython() {
        val trace=golden("--mixer")
        val m=DPowerMixer();val hash=MessageDigest.getInstance("SHA-256")
        for(i in 0 until 100) {
            if(i%7==0)m.change(0,DPowerMixer.Tone((440+i).toDouble(),.7))
            if(i%11==0)m.change(1,DPowerMixer.Tone((771+i).toDouble(),-.4))
            if(i%13==0)m.change(0,null)
            m.advance(2200.0+i*1.25);hash.update(m.drain())
        }
        assertEquals(trace["pcmSha256"].asString,hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
        same("pcm",trace["pcm"],m.state())
    }
    @Test fun portableSchemasAndMalformedSnapshotAreAtomic() {
        val r=DPowerRuntime(synthetic());repeat(20){r.advance()}
        val snapshot=r.snapshot(); val restored=DPowerRuntime(synthetic());restored.restore(snapshot)
        same("roundtrip",JsonParser.parseString(snapshot),JsonParser.parseString(restored.snapshot()))
        val schema1=JsonParser.parseString(snapshot).asJsonObject.apply { addProperty("schema",1);remove("pcm") }
        restored.restore(schema1.toString())
        for (change in listOf<(JsonObject)->Unit>(
            {it["fields"].asJsonObject.remove("_PC")},
            {it["fields"].asJsonObject.addProperty("_PC",65536)},
            {it["fields"].asJsonObject.addProperty("_CPU_DIV",3)},
            {it["fields"].asJsonObject.addProperty("_TM1_div",-1)},
            {it["fields"].asJsonObject.addProperty("_clock",123)},
            {it.addProperty("ram","AA==")},
            {it["pcm"].asJsonObject.addProperty("fraction",2)},
            {it.addProperty("extra",1)})) {
            val broken=JsonParser.parseString(snapshot).asJsonObject;change(broken)
            val before=restored.snapshot()
            try {restored.restore(broken.toString());fail("Accepted malformed snapshot")} catch(_:RuntimeException) {}
            assertEquals(before,restored.snapshot())
        }
    }
    private fun cpuNow(): Long {
        val bean=Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        return Class.forName("java.lang.management.ThreadMXBean").getMethod("getCurrentThreadCpuTime").invoke(bean) as Long
    }
    @Test fun syntheticCpuBenchmark() {
        val r=DPowerRuntime(synthetic());repeat(2000){r.advance();if(it%10==0)r.playback()}
        val start=cpuNow();var cycles=0.0
        repeat(30000){cycles+=r.advance();if(it%10==0)r.playback()}
        val cpu=(cpuNow()-start)/1e9
        println("SYNTHETIC D-Power Kotlin: $cpu CPU seconds / ${cycles/2200000} emulated seconds")
        val ref=golden("--synthetic","--benchmark","--seconds","30")
        println("SYNTHETIC D-Power Python: ${ref["cpuSeconds"]} CPU seconds / ${ref["cycles"].asDouble/2200000} emulated seconds")
        for (skip in listOf(false,true)) {
            val c=TimingRom.core().apply {idleSkippingEnabled=skip}
            c.runForCycles(60.0*c.clockFrequency)
            val t=cpuNow()
            val seconds=c.runForCycles(600.0*c.clockFrequency)/c.clockFrequency
            println("SYNTHETIC classic skip=$skip: ${(cpuNow()-t)/1e9} CPU seconds / $seconds emulated seconds")
        }
    }
    @Test fun realRomCpuBenchmarks() {
        val path=System.getenv("DPOWER_ROM");val dir=System.getenv("CLASSIC_ROM_DIR")
        assumeTrue("Set DPOWER_ROM and CLASSIC_ROM_DIR for both real-core benchmarks",path!=null && dir!=null)

        for (skip in listOf(false, true)) {
            val r=DPowerRuntime(File(path).readBytes());r.core.idleSkippingEnabled=skip
            repeat(2000){r.advance();if(it%10==0)r.playback()}
            val start=cpuNow();var cycles=0.0
            repeat(30000){cycles+=r.advance();if(it%10==0)r.playback()}
            val cpu=(cpuNow()-start)/1e9
            println("D-Power Kotlin skip=$skip: ${cycles/2200000/cpu} emulated seconds/CPU second; ${cpu/(cycles/2200000)*100}% desktop core; skipped ${r.core.idleSkippedInstructions} instructions")
        }
        val reference=golden("--benchmark","--seconds","30")
        println("D-Power Python reference: ${30/reference["cpuSeconds"].asDouble} emulated seconds/CPU second")
        for(device in listOf("DigimonV1JA","DigimonV1JB","DigimonV1ES","DigimonV2J","DigimonV3J","DigimonV4J")) for(skip in listOf(false,true)) {
            val c=E0C6200(File(dir,"$device.bin").readBytes()).apply {idleSkippingEnabled=skip}
            c.runForCycles(c.clockFrequency.toDouble()*60);val start=cpuNow()
            val elapsed=c.runForCycles(c.clockFrequency.toDouble()*600)/c.clockFrequency
            val used=(cpuNow()-start)/1e9
            println("$device skip=$skip: ${elapsed/used} emulated seconds/CPU second")
        }
    }
}
