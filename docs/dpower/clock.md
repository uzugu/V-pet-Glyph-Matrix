# D-Arc-style Glyph clock

Reference inspected: sibling `d-arc-glyph/app-project/app/src/main/java/com/koast/dpowerglyph/runtime/DPowerRenderer.kt`, `drawGlyphIdleClock` and `GLYPH_CLOCK_DIGITS`. Matching copies also exist in `dpower-sol` and `dpower-acceptance`.

The optional **D-Power controls → Glyph clock** uses the exact [NanoFont 3x4 digit patterns](../glyph/nanofont.md), matching the sibling D-Arc renderer. Preview 7 keeps one LED per font pixel and splits the clock into hours above the LCD (rows 0..2) and minutes below (rows 21..23). Each two-digit pair spans seven columns, x=9..15, inside the circular display. The game LCD is unchanged. The preference defaults off and persists across restarts.

Time comes directly from the phone's local wall clock, in 24-hour format, including its current timezone. It does not depend on accumulated emulation time and does not adjust the firmware's clock. Display revision changes force a fresh frame even when ROM VRAM is static, including on minute changes, timezone changes and toggling the header. AOD uses the same renderer.

Difference from the reference: the reconstructed D-Arc game exposes `screen == IDLE`; this firmware emulator does not yet decode an equivalent screen state. The header therefore remains visible on every D-Power screen while enabled, rather than guessing that an unchanged LCD is idle. Classic ROMs retain their status-icon rows without a clock overlay.

Validation: digit-layout and rollover tests, plus a live loop regression showing an external display revision refreshes unchanged VRAM. Build/lint validation does not replace checking clock readability on the physical circular Matrix.
