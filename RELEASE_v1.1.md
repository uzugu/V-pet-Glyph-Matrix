# Digimon Glyph Matrix v1.1

## Highlights

- Added a beginner-friendly in-app manual with spoiler-gated evolution guidance
- Added an interactive V1/V2/V3 evolution guide with translated monster data
- Added `Do Not Disturb` scheduling with freeze/silent behavior
- Added attention notifications and optional care-mistake visibility
- Added per-ROM autosave and per-ROM manual save slots
- Added richer live pet decoding in the launcher
- Added raw RAM/VRAM watch tools, watch profiles, baseline diffing, and raw snapshot export
- Added V3 training, win count, loss count, and win ratio decoding
- Improved widget input reliability and widget frame sync
- Added `Full Accuracy Mode` as the new max-precision timing option

## Added In This Release

### Live Stats and Debugging

- Live monitor now surfaces:
  - training
  - wins
  - losses
  - win ratio
  - care timer
  - attention state
- Advanced diagnostics now includes:
  - raw RAM watch panel
  - watch profiles (`CORE`, `CARE`, `TRAIN`, `BATTLE`, `PAGE0`)
  - baseline marking and diffing
  - raw snapshot export
  - raw snapshot inclusion in save-state bundle exports

### Guide and UX

- Reworked the in-app manual for new players
- Added spoiler confirmation for evolution content
- Added larger translated Digimon guide art with pixel-clean rendering
- Added a dedicated evolution guide activity for V1/V2/V3

### Save and Runtime Improvements

- Manual slots are now namespaced per ROM
- Autosaves are now namespaced per ROM
- `Do Not Disturb` can freeze emulation during configured quiet hours
- Widget input path was hardened to avoid dropped taps
- Widget previews and debug frame handling were stabilized

### Timing and Stability

- Default runtime timing now uses the old accurate behavior as baseline
- `Full Accuracy Mode` pushes wall-clock sync harder for users who want maximum fidelity
- Hardened audio shutdown and DND scheduling crash paths

## Notes

- `Tairyoku` is intentionally not shown yet. The byte mapping is still under investigation.
- V1/V2 hidden stat offsets remain more tentative than the V3 mappings.

## Release Assets

- `Digimon-Glyph-V1-release-signed.apk`

## Suggested GitHub Release Title

- `v1.1 - Guide, DND, RAM Tools, and Battle Stats`
