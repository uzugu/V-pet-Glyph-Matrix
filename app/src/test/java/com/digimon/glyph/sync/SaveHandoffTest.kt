package com.digimon.glyph.sync

import com.digimon.glyph.emulator.*
import java.io.File
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class SaveHandoffTest {
    @Test fun actualFrameRoundtripAllClassicRevisions() {
        val directory = System.getenv("CLASSIC_ROM_DIR")
        val bridge = File("../V-pet-frame/tools/save-roundtrip.py").let { if(it.exists()) it else File("../../V-pet-frame/tools/save-roundtrip.py") }
        assumeTrue("Requires private ROMs and adjacent Frame checkout", directory != null && bridge.exists())
        for (profile in listOf("DigimonV1JA","DigimonV1JB","DigimonV1ES","DigimonV2J","DigimonV3J","DigimonV4J")) {
            val rom = File(directory, "$profile.bin")
            val core = E0C6200(rom.readBytes())
            core.runForCycles(core.clockFrequency * 3.123)
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(rom.readBytes()).joinToString("") { "%02x".format(it) }
            val record = PortableSave.create(core, sha, profile, "test_phone", null)
            val input = File.createTempFile("vpet-in", ".json")
            val output = File.createTempFile("vpet-out", ".json")
            try {
                input.writeText(PortableSave.encode(record))
                val process = ProcessBuilder("python3",bridge.absolutePath,input.absolutePath,rom.absolutePath,output.absolutePath,"106000").redirectErrorStream(true).start()
                val log=process.inputStream.bufferedReader().readText()
                assertEquals(log,0,process.waitFor())
                core.runForCycles(106000.0)
                val received=PortableSave.decode(output.readText())
                assertEquals(record.id, received.parent)
                val restored=E0C6200(rom.readBytes()).apply {restoreState(PortableSave.state(received,this,sha,profile))}
                for ((key,expected) in core.getState()) {
                    if (key.startsWith("DBG_") || key in listOf("BZ_OUTPUT","BZ_GAIN")) continue
                    val actual=restored.getState()[key]
                    if(expected is IntArray) assertArrayEquals("$profile $key",expected,actual as IntArray)
                    else assertEquals("$profile $key",expected,actual)
                }
                val budget=core.clockFrequency*2.0
                assertEquals(core.runForCycles(budget),restored.runForCycles(budget),0.0)
                assertArrayEquals(core.getState()["RAM"] as IntArray,restored.getState()["RAM"] as IntArray)
                assertArrayEquals(core.getVRAM(),restored.getVRAM())
                for(key in listOf("PC","NPC","TM","PT","OSC1_COUNTER","TIMER_COUNTER")) assertEquals("$profile continued $key",core.getState()[key],restored.getState()[key])
            } finally {input.delete();output.delete()}
        }
    }
    /** Actual Syncthing + ARM device check, opt-in so normal JVM tests need no headset. */
    @Test fun syncthingFrameHardwareRoundtrip() {
        val directory = System.getenv("CLASSIC_ROM_DIR")
        val shared = System.getenv("FRAME_EXCHANGE_VALIDATION_DIR")
        assumeTrue("Requires running Frame validation responder and Syncthing folder", directory != null && shared != null)
        val folder = File(shared!!).apply { mkdirs() }
        for (profile in listOf("DigimonV1JA","DigimonV1JB","DigimonV1ES","DigimonV2J","DigimonV3J","DigimonV4J")) {
            val rom=File(directory,"$profile.bin").readBytes()
            val core=E0C6200(rom).apply {runForCycles(clockFrequency*3.123)}
            val sha=java.security.MessageDigest.getInstance("SHA-256").digest(rom).joinToString("") {"%02x".format(it)}
            val sent=PortableSave.create(core,sha,profile,"phone_test",null)
            val input=File(folder,"$sha--${sent.id}.vpetsave")
            val partial=File(folder,input.name+".partial")
            partial.writeText(PortableSave.encode(sent));check(partial.renameTo(input))
            val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90)
            var returned: Pair<File,PortableSave.Record>?=null
            while(returned==null && System.nanoTime()<deadline) {
                returned=folder.listFiles().orEmpty().filter {it.extension=="vpetsave"}.mapNotNull {file->
                    try {PortableSave.decode(file.readText()).takeIf {it.parent==sent.id && it.origin=="frame_test"}?.let {file to it}} catch(_:Exception){null}
                }.singleOrNull()
                if(returned==null)Thread.sleep(100)
            }
            val received=returned ?: error("No Frame reply for $profile; inspect responder and Syncthing status")
            try {
                core.runForCycles(106000.0)
                val restored=E0C6200(rom).apply {restoreState(PortableSave.state(received.second,this,sha,profile))}
                for ((key,expected) in core.getState()) {
                    if(key.startsWith("DBG_") || key in listOf("BZ_OUTPUT","BZ_GAIN"))continue
                    val actual=restored.getState()[key]
                    if(expected is IntArray)assertArrayEquals("ARM $profile $key",expected,actual as IntArray)
                    else assertEquals("ARM $profile $key",expected,actual)
                }
                val cycles=core.clockFrequency*2.0
                assertEquals(core.runForCycles(cycles),restored.runForCycles(cycles),0.0)
                assertArrayEquals(core.getState()["RAM"] as IntArray,restored.getState()["RAM"] as IntArray)
                assertArrayEquals(core.getVRAM(),restored.getVRAM())
            } finally {input.delete();received.first.delete()}
        }
    }
    @Test fun checksumAndIdentityFailClosed() {
        val core=TimingRom.core()
        val record=PortableSave.create(core,"a".repeat(64),"DigimonV1JA","test",null)
        assertEquals(record,PortableSave.decode(PortableSave.encode(record)))
        for(bad in listOf(record.copy(payload=record.payload+" "),record.copy(version=2),record.copy(profile="DPowerEU101"))) {
            assertThrows(Exception::class.java){PortableSave.decode(PortableSave.encode(bad))}
        }
        assertThrows(Exception::class.java){PortableSave.state(record,core,"b".repeat(64),record.profile)}
    }
}
