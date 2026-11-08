package com.digimon.glyph.widget

import org.junit.Assert.*
import org.junit.Test

class WidgetFramePolicyTest {
    @Test fun boundsAndAspectRatioSurviveLargeWidgetSizes() {
        assertEquals(256 to 128,WidgetFramePolicy.renderSize(1200,600))
        assertEquals(128 to 256,WidgetFramePolicy.renderSize(600,1200))
        assertEquals(256 to 256,WidgetFramePolicy.renderSize(0,0))
        assertEquals(32 to 16,WidgetFramePolicy.renderSize(32,16))
    }
    @Test fun contentChangesAndPresentationChangesInvalidateHash() {
        val pixels=IntArray(4)
        val first=WidgetFramePolicy.contentHash(pixels,2,2,0,false)
        assertEquals(first,WidgetFramePolicy.contentHash(pixels.copyOf(),2,2,0,false))
        pixels[3]=1
        assertNotEquals(first,WidgetFramePolicy.contentHash(pixels,2,2,0,false))
        pixels[3]=0
        assertNotEquals(first,WidgetFramePolicy.contentHash(pixels,2,2,1,false))
        assertNotEquals(first,WidgetFramePolicy.contentHash(pixels,2,2,0,true))
    }
}
