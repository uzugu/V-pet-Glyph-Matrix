package com.digimon.glyph

/** Interactive 25x25 gameplay currently targets Phone (3), not stripe/AOD-only devices. */
object GlyphAvailability {
    val isAvailable: Boolean
        get() = try {
            android.os.Build.VERSION.SDK_INT >= 34 &&
                android.os.Build.MANUFACTURER.equals("Nothing", ignoreCase = true) &&
                com.nothing.ketchum.Common.is23112() &&
                com.nothing.ketchum.Common.getDeviceMatrixLength() == 25
        } catch (_: Exception) {
            false
        } catch (_: LinkageError) {
            false
        }
}
