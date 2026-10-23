package com.digimon.glyph.emulator

import org.junit.Assert.*
import org.junit.Test

class DPowerBrightnessTest {
    @Test fun presetsHaveRequestedRelativeLevels() {
        assertEquals(DPowerBrightness.BALANCED.pet, DPowerBrightness.BALANCED.clock)
        assertEquals(DPowerBrightness.DIM.pet, DPowerBrightness.DIM.clock)
        assertTrue(DPowerBrightness.DIM.pet < DPowerBrightness.BALANCED.pet)
        assertTrue(DPowerBrightness.PET_BRIGHTER.pet > DPowerBrightness.PET_BRIGHTER.clock)
        assertTrue(DPowerBrightness.CLOCK_BRIGHTER.pet < DPowerBrightness.CLOCK_BRIGHTER.clock)
    }
    @Test fun pixelsAreOpaqueGrayscaleForConsistentSdkConversion() {
        for (preset in DPowerBrightness.entries) for (pixel in listOf(preset.petColor, preset.clockColor)) {
            assertEquals(255, pixel ushr 24)
            assertEquals(pixel and 255, (pixel ushr 8) and 255)
            assertEquals(pixel and 255, (pixel ushr 16) and 255)
        }
    }
}
