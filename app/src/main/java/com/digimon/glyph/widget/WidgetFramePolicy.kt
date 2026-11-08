package com.digimon.glyph.widget

/** Pure rendering policy so size bounds and content suppression can be tested on the JVM. */
object WidgetFramePolicy {
    fun renderSize(width: Int, height: Int): Pair<Int, Int> {
        val w=if(width>0)width else 256
        val h=if(height>0)height else 256
        val scale=minOf(1.0,256.0/maxOf(w,h))
        return maxOf(1,(w*scale).toInt()) to maxOf(1,(h*scale).toInt())
    }
    fun contentHash(pixels: IntArray, width: Int, height: Int, skin: Int, frozen: Boolean): Long {
        var hash=1125899906842597L
        for(v in intArrayOf(width,height,skin,if(frozen)1 else 0))hash=31*hash+v
        for(pixel in pixels)hash=31*hash+pixel
        return hash
    }
}
