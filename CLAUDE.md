# DigimonGlyph - Agent Context (Hybrid Reference)

## 1) Project Purpose

DigimonGlyph is an Android app that runs Digimon virtual pet ROMs on the Nothing Glyph Matrix (25x25 rear LED grid).

Core idea:
- Emulate Seiko Epson E0C6200 MCU
- Convert emulated LCD/VRAM output to Glyph frame data
- Map phone input (motion + Glyph button) to Digimon A/B/C controls

Workspace:
`C:\Users\uzuik\AndroidStudioProjects\DigimonGlyph\`

## 2) Stable Architecture

Runtime flow:
`ROM -> E0C6200 -> VRAM -> DisplayBridge -> Bitmap -> GlyphRenderer -> GlyphMatrixManager`

Input flow:
`Sensors + Glyph button + command bus -> InputController -> E0C6200 K0 pins`

Main modules:
- `app/src/main/java/com/digimon/glyph/emulator/E0C6200.kt`
  - Main CPU/peripherals/state implementation.
- `app/src/main/java/com/digimon/glyph/emulator/DisplayBridge.kt`
  - VRAM to on-screen/debug/Glyph rendering bridge.
- `app/src/main/java/com/digimon/glyph/emulator/EmulatorLoop.kt`
  - Emulation clock loop and frame pacing.
- `app/src/main/java/com/digimon/glyph/DigimonGlyphToyService.kt`
  - Toy lifecycle, renderer/audio/input wiring, save/load, command processing.
- `app/src/main/java/com/digimon/glyph/input/InputController.kt`
  - Gesture/button mapping to A/B/C.
- `app/src/main/java/com/digimon/glyph/RomLoaderActivity.kt`
  - Launcher UI, ROM import, controls, debug view.

## 3) Current Input Behavior (Source of Truth)

K0 pin mapping:
- A = pin 2 (active-low)
- B = pin 1 (active-low)
- C = pin 0 (active-low)

Current control mapping:
- Flick left/right (dominant X axis) -> A/C
- Flick toward/away (dominant Z axis) -> short B tap
- Glyph physical button hold -> B hold
- Flick success -> vibration feedback

Explicit combo taps (launcher buttons):
- A+B
- A+C
- B+C

Combo path:
- UI posts `EmulatorCommandBus.CMD_PRESS_COMBO`
- Service handles command and calls `InputController.triggerComboTap()`

## 4) Current Launcher UI Layout

`RomLoaderActivity` is programmatic UI and scrollable.

Main section (always visible):
- ROM status + ROM picker
- Usage instructions
- Live screen previews:
  - Full Digivice
  - Glyph + Overlay
- Emulator options (zoom/audio)
- Autosave + manual slots
- Restart / Full reset
- Combo buttons
- Last command status

Debug section (collapsible):
- Show Debug / Hide Debug toggle
- Debug telemetry switch
- A/B/C indicator chips
- Detailed debug text

Important behavior:
- Live screen previews continue updating even when debug telemetry is OFF.

## 5) Save/State System

Implemented via `StateManager.kt` + service integration:
- Autosave with sequence/timestamp metadata
- 3 manual slots
- ROM safety checks (hash/name)
- Sync save on critical lifecycle events

Lifecycle hardening:
- Delayed shutdown on unbind (`UNBIND_STOP_GRACE_MS`) to avoid transient bind/unbind freezes from OS service churn.

## 6) Audio System

Files:
- `app/src/main/java/com/digimon/glyph/audio/BuzzerAudioEngine.kt`
- `app/src/main/java/com/digimon/glyph/emulator/EmulatorAudioSettings.kt`

Behavior:
- Service subscribes to `E0C6200.onBuzzerChange`
- Audio is toggleable from launcher
- Envelope/one-shot paths are wired from emulator state

## 7) Command/Settings Infrastructure

Prefs-backed command bus:
- `app/src/main/java/com/digimon/glyph/emulator/EmulatorCommandBus.kt`

Important commands:
- save/load autosave
- save/load slot
- restart
- full reset
- press combo
- refresh settings

Prefs-backed settings:
- `DisplayRenderSettings` (text zoom heuristic)
- `EmulatorAudioSettings` (audio toggle)
- `EmulatorDebugSettings` (debug telemetry toggle)

## 8) Critical Fixes Already Landed

### Training freeze fix (major)
Observed freeze:
- PC pinned at 54
- opcode `0xF38` executing as dummy
- interrupts/timers still active

Root cause:
- Opcode dispatch range misalignment in `E0C6200.kt`

Fix:
- ACPX range corrected to `0xF28..0xF2B`
- SCPX range corrected to `0xF38..0xF3B`

Result:
- Training freeze resolved.

### Diagnostics hardening
- Added core debug counters/state logging to isolate interrupt vs dispatch issues.
- Added debug telemetry toggle to avoid overhead in normal use.

## 9) Practical Build/Test Notes

Common commands:
- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat installDebug`

Device used in recent validation:
- Nothing device seen as `A024` in install output.

## 10) Known Risks / Next Iterations

- Display mapping/zoom heuristics can still be refined per ROM/menu edge cases.
- Gesture thresholds may need tuning for different users/devices.
- Audio authenticity can be compared further against BrickEmuPy in corner cases.
- UI remains programmatic by design; XML migration is optional cleanup.

## 11) Historical Note

The old version of this file is outdated (tilt-based control assumptions, missing new systems).
Use this document as current reference for agent sessions and future maintenance.

## 12) Timing / Power Mode (New)

New shared setting:
- `app/src/main/java/com/digimon/glyph/emulator/EmulatorTimingSettings.kt`

Launcher UI:
- `RomLoaderActivity` now has switch:
  - `Exact timing (higher battery)`

Runtime behavior:
- `EXACT` mode:
  - Accuracy-first pacing in `EmulatorLoop`
  - Service keeps partial CPU wakelock to stabilize timing
- `POWER_SAVE` mode:
  - Intentionally reduced emulation speed/pacing
  - Lower frame/update pressure and longer sleeps
  - Service releases CPU wakelock

Service wiring:
- `DigimonGlyphToyService.applyTimingSettingIfChanged()` applies mode live and reacts to `CMD_REFRESH_SETTINGS`.

## 13) Wrap-up Notes for Next Session

- Battery baseline command (used successfully):
  - `adb -s <serial> shell dumpsys batterystats --reset`
- If wireless ADB port changes, reconnect before install:
  - `adb connect <ip:port>`
- Known tradeoff:
  - Exact timing keeps higher CPU/battery.
  - Power Save minimizes power, but speed is intentionally not exact.
