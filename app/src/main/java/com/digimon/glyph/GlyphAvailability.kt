package com.digimon.glyph

/**
 * Runtime check for Nothing Glyph Matrix SDK availability.
 * On non-Nothing phones the SDK classes won't exist even though the AAR is bundled.
 */
object GlyphAvailability {
    private var isNothingDevice: Boolean? = null

    val isAvailable: Boolean
        get() {
            if (isNothingDevice != null) return isNothingDevice!!
            isNothingDevice = try {
                Class.forName("com.nothing.ketchum.GlyphMatrixManager")
                // On non-Nothing devices, the class exists in the APK,
                // but we must check if the manufacturer is actually "Nothing"
                // or if we can actually use it without crashing.
                android.os.Build.MANUFACTURER.equals("Nothing", ignoreCase = true)
            } catch (_: Exception) {
                false
            } catch (_: Error) {
                false
            }
            return isNothingDevice!!
        }
}
