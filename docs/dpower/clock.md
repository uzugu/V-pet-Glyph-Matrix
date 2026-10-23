# D-Arc-style Glyph clock

Reference inspected: sibling `d-arc-glyph/app-project/app/src/main/java/com/koast/dpowerglyph/runtime/DPowerRenderer.kt`, `drawGlyphIdleClock` and `GLYPH_CLOCK_DIGITS`. Matching copies also exist in `dpower-sol` and `dpower-acceptance`.

The optional **D-Power controls → Glyph clock header** uses the same digit patterns, HHMM ordering, three-column digits, one-column gaps, centred x=5 origin and 1.2-pixel circular marks. LED polarity is white on black; clipping confines the marks to the four spare rows above the 16-row LCD. Full LCD rendering, walking input and emulation continue unchanged. The preference defaults off and persists across restarts.

Time comes directly from the phone's local wall clock, in 24-hour format, including its current timezone. It does not depend on accumulated emulation time and does not adjust the firmware's clock. Display revision changes force a fresh frame even when ROM VRAM is static, including on minute changes, timezone changes and toggling the header. AOD uses the same renderer.

Difference from the reference: the reconstructed D-Arc game exposes `screen == IDLE`; this firmware emulator does not yet decode an equivalent screen state. The header therefore remains visible on every D-Power screen while enabled, rather than guessing that an unchanged LCD is idle. Classic ROMs retain their status-icon rows without a clock overlay.

Validation: digit-layout and rollover tests, plus a live loop regression showing an external display revision refreshes unchanged VRAM. Build/lint validation does not replace checking top-row readability on the physical circular Matrix.
