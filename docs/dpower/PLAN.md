# D-Power EU 1.01 integration

Use upstream GPL191X (SPL61X), pinned at 376cca92c6d000ffd6046e5491f2a1c3d1a5eab0, embedded with Chaquopy. ROMs remain user-supplied and are never committed. This is EU 1.01, not Japanese D-Ark.

The Nothing Phone Glyph Matrix is the primary play display. On-screen controls are secondary. Physical Glyph input must expose all six D-Power inputs.

1. Shared EmulatorCore interface, retaining classic CPU scheduling and display behavior.
2. Embedded upstream core with bounded execution batches and versioned, complete snapshots; validate against uninterrupted execution.
3. SHA-256 device selection, bounded import, explicit ZIP member choice and runtime ROM switch.
4. D-Power LCD mapping from upstream SVG; directional, step and card buttons. Classic battle and metadata unavailable on D-Power.
5. Unit tests, actual private-ROM integration tests, debug APK build. Phone speed/audio/lifecycle verification remains required before release.

Scope: D-Power integration. Prior clock lifecycle fixes and unrelated classic CPU audit defects are separate work. D-Power battle transport is not implemented.

## Current status

Steps 1–4 implemented. Local build, lint, unit tests and private-ROM integration tests are complete; results are recorded in README.md. Phone validation is pending hardware availability. No phone deployment or performance claim is made.
