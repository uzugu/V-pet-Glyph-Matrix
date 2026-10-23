# D-Arc-style Glyph clock

Reference inspected: sibling `d-arc-glyph/app-project/app/src/main/java/com/koast/dpowerglyph/runtime/DPowerRenderer.kt`, `drawGlyphIdleClock` and `GLYPH_CLOCK_DIGITS`. Matching copies also exist in `dpower-sol` and `dpower-acceptance`.

The optional **D-Power controls → Glyph clock** uses the exact [NanoFont 3x4 digit patterns](../glyph/nanofont.md), matching the sibling D-Arc renderer. Preview 6 corrects their rendering to one LED per pixel and places the three visible rows below the LCD at y=20..22, inside the circular display. Four digits and one-column gaps span x=5..19. The game LCD is unchanged. The preference defaults off and persists across restarts.

Time comes directly from the phone's local wall clock, in 24-hour format, including its current timezone. It does not depend on accumulated emulation time and does not adjust the firmware's clock. Display revision changes force a fresh frame even when ROM VRAM is static, including on minute changes, timezone changes and toggling the header. AOD uses the same renderer.

Difference from the reference: the reconstructed D-Arc game exposes `screen == IDLE`; this firmware emulator does not yet decode an equivalent screen state. The header therefore remains visible on every D-Power screen while enabled, rather than guessing that an unchanged LCD is idle. Classic ROMs retain their status-icon rows without a clock overlay.

Validation: digit-layout and rollover tests, plus a live loop regression showing an external display revision refreshes unchanged VRAM. Build/lint validation does not replace checking clock readability on the physical circular Matrix.
