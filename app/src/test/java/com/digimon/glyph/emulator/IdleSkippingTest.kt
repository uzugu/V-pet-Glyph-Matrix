package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class IdleSkippingTest {
    private fun compare(a: E0C6200,b: E0C6200) = ClassicFirmwareClockTest.assertUnchanged(a.getState(),b.getState(),skipDebug = true)
    @Test fun haltedClassicMatchesReferenceAcrossHoursAndPeripheralEdges() {
        val a=TimingRom.core().apply { idleSkippingEnabled=false }
        val b=TimingRom.core()
        var elapsed=0.0
        repeat(3*60) {
            val budget=60.0*a.clockFrequency
            assertEquals(a.runForCycles(budget),b.runForCycles(budget),0.0)
            compare(a,b);elapsed+=budget
        }
        assertEquals(a.getState()["DBG_IT1_SET"],b.getState()["DBG_IT1_SET"])
        assertEquals(a.getState()["DBG_INTERRUPTS"],b.getState()["DBG_INTERRUPTS"])
    }
    @Test fun tinyBudgetsAndPendingKeysNeverSkipInterrupts() {
        val a=TimingRom.core().apply { idleSkippingEnabled=false };val b=TimingRom.core()
        repeat(10000) { n ->
            if(n%700==0) {a.pinSet("K0",2,0);b.pinSet("K0",2,0)}
            if(n%700==20) {a.pinRelease("K0",2);b.pinRelease("K0",2)}
            val budget=(n%997+1).toDouble()
            assertEquals(a.runForCycles(budget),b.runForCycles(budget),0.0)
            compare(a,b)
        }
    }
    @Test fun allPrivateClassicRomsMatchAcrossMidnightAndThreeHours() {
        val dir=System.getenv("CLASSIC_ROM_DIR");assumeTrue("Set CLASSIC_ROM_DIR",dir!=null)
        for(device in listOf("DigimonV1JA","DigimonV1JB","DigimonV1ES","DigimonV2J","DigimonV3J","DigimonV4J")) {
            val rom=File(dir,"$device.bin").readBytes()
            val b=E0C6200(rom)
            b.runForCycles(2.0*b.clockFrequency)
            fun press(pin:Int) {b.pinSet("K0",pin,0);b.runForCycles(.2*b.clockFrequency);b.pinRelease("K0",pin);b.runForCycles(.3*b.clockFrequency)}
            press(1);press(2);press(0)
            repeat(1000) {if(!b.isFirmwareClockBoundary())b.runForCycles(.001*b.clockFrequency)}
            assertTrue("$device clock boundary",b.isFirmwareClockBoundary())
            b.overrideFirmwareClock(ClassicFirmwareClock.Time(23,59,59))
            val a=E0C6200(rom).apply {restoreState(b.getState());idleSkippingEnabled=false}
            repeat(180) {minute ->
                if(minute%30==0) {a.pinSet("K0",2,0);b.pinSet("K0",2,0)}
                assertEquals(a.runForCycles(.2*a.clockFrequency),b.runForCycles(.2*b.clockFrequency),0.0)
                a.pinRelease("K0",2);b.pinRelease("K0",2)
                assertEquals(a.runForCycles(59.8*a.clockFrequency),b.runForCycles(59.8*b.clockFrequency),0.0)
                compare(a,b)
                assertEquals(a.getState()["DBG_IT1_SET"],b.getState()["DBG_IT1_SET"])
            }
        }
    }
    @Test fun inertDpowerJumpMatchesReferenceTimersAndStopsBeforeEveryEvent() {
        val rom=DPowerPortTest.synthetic().apply {this[0x400]=0x4c;this[0x401]=0;this[0x402]=4}
        val a=DPowerRuntime(rom);val b=DPowerRuntime(rom)
        for(r in listOf(a,b)) {r.core._set_sfr_timer_ctrl(128);r.core._TM0_PRELOAD=65530;r.core._TM1_PRELOAD=65533}
        b.core.idleSkippingEnabled=true
        repeat(30000) {n ->
            assertEquals(a.advance(),b.advance(),0.0)
            if(n%1000==0) DPowerPortTest.same("idle $n",com.google.gson.JsonParser.parseString(a.snapshot()),com.google.gson.JsonParser.parseString(b.snapshot()))
            if(n%10==0)assertArrayEquals(a.playback(),b.playback())
        }
    }
}
