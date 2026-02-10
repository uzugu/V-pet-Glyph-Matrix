# DigimonGlyph - Agent Context File

## What This Project Is

An Android app that runs Digimon Virtual Pet ROMs (V1/V2/V3) on the **Nothing Phone 3 Glyph Matrix** — a 25x25 RGB LED grid on the back of the phone. The app emulates the original **Seiko Epson E0C6200** 4-bit microcontroller, renders the emulated 32x16 LCD onto the 25x25 LED grid, and maps the phone's gyroscope + Glyph Button to the original 3-button input scheme.

## Architecture Overview

```
[ROM .bin 16KB] -> [E0C6200 CPU Emulator] -> [VRAM 160 nibbles]
                                                     |
                                               [DisplayBridge]
                                                     |
                                               [25x25 Bitmap]
                                                     |
                                              [GlyphRenderer]
                                                     |
                                          [Nothing Glyph Matrix LEDs]

[Gyroscope]    -> [InputController] -> [E0C6200 K0 port pins]
[Glyph Button] -> [InputController] -> [E0C6200 K0 port pins]
```

## Project Path

`C:\Users\uzuik\AndroidStudioProjects\DigimonGlyph\`

## File Map

### Build System
| File | Purpose |
|------|---------|
| `build.gradle.kts` | Root: AGP 8.7.3, Kotlin 2.1.0 |
| `app/build.gradle.kts` | App module: namespace `com.digimon.glyph`, minSdk 34, targetSdk 35, depends on glyph-matrix-sdk-1.0.aar + gson + appcompat |
| `settings.gradle.kts` | Single `:app` module |
| `gradle/wrapper/*` | Gradle 8.9 wrapper |
| `app/libs/glyph-matrix-sdk-1.0.aar` | Nothing Glyph Matrix SDK (downloaded from GitHub) |

### Emulator Core (`app/src/main/java/com/digimon/glyph/emulator/`)
| File | Lines | Purpose |
|------|-------|---------|
| **`E0C6200.kt`** | 1044 | **THE MAIN FILE.** Complete Seiko Epson E0C6200 4-bit CPU emulator. Ported from Python (BrickEmuPy by azya52). Contains CPU registers, 4096-entry instruction dispatch table, all ~70 opcodes, RAM (768 nibbles), VRAM (160 nibbles), I/O registers (~50), timer/stopwatch/programmable-timer peripherals, interrupt controller, button I/O via K0/K1 ports, state serialization. |
| `DisplayBridge.kt` | 208 | Converts VRAM (160 nibbles + 8 port values) into 25x25 Bitmap. Maps 32x16 LCD -> 25 columns (nearest-neighbor), 16 rows centered vertically (4-row top offset), icons in rows 0-2 and 22-24. **VRAM-to-pixel mapping is estimated and likely needs refinement.** |
| `EmulatorLoop.kt` | 77 | Dedicated thread: runs `E0C6200.clock()` at 1.06MHz rate, renders at ~15 FPS, delivers Bitmaps via callback. |
| `StateManager.kt` | 85 | Saves/restores full emulator state (CPU regs + RAM + VRAM + I/O) to SharedPreferences via JSON + Base64. |
| `Cpu.kt` | 35 | Placeholder — CPU state is in E0C6200.kt |
| `Memory.kt` | 40 | Placeholder — memory is in E0C6200.kt |
| `Alu.kt` | 6 | Placeholder — ALU is in E0C6200.kt |
| `InstructionDecoder.kt` | 6 | Placeholder — dispatch table is in E0C6200.kt |
| `IoController.kt` | 9 | Placeholder — I/O is in E0C6200.kt |

### Android / Glyph Integration (`app/src/main/java/com/digimon/glyph/`)
| File | Lines | Purpose |
|------|-------|---------|
| `DigimonGlyphToyService.kt` | 217 | Android Service implementing Glyph Toy pattern. Handles `MSG_GLYPH_TOY` messages (prepare/start/end/button/AOD). Manages full lifecycle: init SDK -> load ROM -> restore state -> start emulator+input -> autosave every 60s -> save on unbind. |
| `GlyphRenderer.kt` | 53 | Creates `GlyphMatrixObject` from Bitmap, builds `GlyphMatrixFrame`, calls `manager.setMatrixFrame(frame.render())` |
| `RomLoaderActivity.kt` | 165 | Launcher activity. File picker supporting `.zip` (extracts `.bin`) and raw `.bin` ROMs. Saves to internal storage as `current_rom.bin` + `current_rom_name`. |
| `input/InputController.kt` | 136 | Gyroscope tilt + Glyph Button -> Digimon A/B/C. Uses `TYPE_GAME_ROTATION_VECTOR` sensor. Hysteresis: activate 25deg, release 15deg, 150ms debounce. |

### Resources
| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | RomLoaderActivity (launcher) + DigimonGlyphToyService (Glyph Toy intent filter + metadata) |
| `res/values/strings.xml` | App name, toy name/summary, ROM status messages |
| `res/drawable/ic_digimon_preview.xml` | Vector drawable: Digimon egg silhouette for Glyph Toy preview |
| `app/proguard-rules.pro` | Keep rules for Nothing SDK |

## ROM Files

Located at project root (gitignored):
- `digimon.zip` -> contains `digimon.bin` (16384 bytes) + `digimon.svg`
- `digimonv2.zip` -> contains `digimonv2.bin` (16384 bytes) + `digimon.svg`
- `digimonv3.zip` -> contains `digimonv3.bin` (16384 bytes) + `digimon.svg`

The SVG file contains the LCD segment mapping (520 segments with IDs like `"{nibble}_{bit}"`).

## Reference Implementation

Cloned at `reference/BrickEmuPy/` (gitignored). Key files:
- `cores/E0C6200.py` (1988 lines) — The Python original that E0C6200.kt was ported from
- `assets/DigimonV3.brick` — Config: clock=1060000, K0 buttons (pin2=A, pin1=B, pin0=C), pullup K0=15 K1=15
- `assets/DigimonV3.svg` (921 lines) — Definitive LCD segment layout

## Nothing Glyph Matrix SDK API

Package: `com.nothing.ketchum`. Decompiled from the AAR:

```kotlin
// Manager: singleton, init/uninit lifecycle
GlyphMatrixManager.getInstance(context)
  .init(callback: GlyphMatrixManager.Callback)  // onServiceConnected/Disconnected
  .register(packageName: String)
  .setMatrixFrame(frame: GlyphMatrixFrame)       // push frame to LEDs
  .setMatrixFrame(rawArray: IntArray)             // alternative raw array
  .turnOff()
  .unInit()

// Frame: built from up to 3 layers (top/mid/low)
GlyphMatrixFrame.Builder()
  .addTop(obj: GlyphMatrixObject)  // top layer (we use this one)
  .addMid(obj) / .addLow(obj)
  .build(context): GlyphMatrixFrame
GlyphMatrixFrame.render(): IntArray

// Object: holds bitmap/text + position/scale/brightness
GlyphMatrixObject.Builder()
  .setImageSource(bitmap: Bitmap)  // 25x25 ARGB bitmap
  .setPosition(x, y)
  .setBrightness(value: Int)       // 0-4095
  .build(): GlyphMatrixObject

// Toy service communication
GlyphToy.MSG_GLYPH_TOY = 1                    // Message.what
GlyphToy.MSG_GLYPH_TOY_DATA = "data"          // Bundle key for event string
GlyphToy.STATUS_PREPARE = "prepare"
GlyphToy.STATUS_START = "start"
GlyphToy.STATUS_END = "end"
GlyphToy.EVENT_ACTION_DOWN = "action_down"     // Glyph Button press
GlyphToy.EVENT_ACTION_UP = "action_up"         // Glyph Button release
GlyphToy.EVENT_AOD = "aod"                     // Always-On Display
GlyphToy.EVENT_CHANGE = "change"
```

## E0C6200 CPU Quick Reference

- **4-bit CPU**: Registers A (4-bit), B (4-bit), IX (12-bit), IY (12-bit), SP (8-bit), PC (13-bit), NPC (13-bit)
- **Flags**: CF (carry), ZF (zero), DF (decimal/BCD), IF (interrupt enable)
- **Memory map**: RAM 0x000-0x2FF (768 nibbles), VRAM 0xE00-0xE4F + 0xE80-0xECF (160 nibbles), I/O 0xF00-0xF7E
- **ROM**: 16-bit words, upper 12 bits used as opcodes. `romWord(PC * 2)` reads big-endian from byte array
- **Clock**: 1,060,000 Hz CPU, 32,768 Hz OSC1 (timers)
- **Buttons**: K0 port, active-low (0=pressed). Pin 2=A(top), Pin 1=B(center), Pin 0=C(bottom). Pullup=15 (all high when released)
- **VRAM access**: `getVRAM()` returns IntArray(168) — 160 VRAM nibbles + 8 port values (P0,P1,P2,P3,R0,R1,R2,R4)
- **State**: `getState()` returns Map<String, Any>, `restoreState(map)` restores it

## Input Mapping

| Input | Digimon Button | K0 Port | Action |
|-------|---------------|---------|--------|
| Tilt left (roll < -25deg) | A (top) | Pin 2, level 0 | Cycle icons/menu |
| Glyph Button tap | B (center) | Pin 1, level 0 | Confirm/select |
| Tilt right (roll > +25deg) | C (bottom) | Pin 0, level 0 | Cancel/back |

## Known Issues & What Needs Work

### Critical: DisplayBridge VRAM Mapping
The `extractPixels()` method in `DisplayBridge.kt` has an **estimated** VRAM-to-pixel mapping based on SVG analysis. It is very likely wrong or partially wrong. To fix:
1. Run the ROM in BrickEmuPy on a PC and observe the correct display
2. Parse `DigimonV3.svg` segment IDs programmatically — each `<rect>` or `<path>` has an ID like `"24_0"` meaning VRAM nibble 24, bit 0, and coordinates giving the pixel position
3. Build an exact mapping table from the SVG and replace the current estimated one
4. The current mapping assumes: left half (cols 0-15) = VRAM[24-55], right half (cols 16-23) = VRAM[2-17], right half (cols 24-31) = VRAM[56-71], bottom 8 rows = VRAM[82-145]

### Build Environment
- No Android SDK on this dev machine — needs Android Studio with SDK 35
- Gradle 8.9 wrapper is present and functional
- Project builds to the point of needing `ANDROID_HOME` / `local.properties`

### Untested
- Nothing has been tested on device yet
- ROM byte ordering may need swapping (currently big-endian in `E0C6200.romWord()`)
- EmulatorLoop timing may need tuning
- GlyphRenderer brightness/scale values may need adjustment
- The `STATUS_START` event from the Glyph Toy system — we assume this is what triggers emulator start, but the exact message format may differ

### Nice to Have
- AOD mode currently just pushes one static frame — could show a simple animation
- No buzzer/haptic feedback (E0C6200 has buzzer output via R4 register)
- RomLoaderActivity UI is programmatic (no XML layout) — works but looks basic
- Support switching between V1/V2/V3 ROMs without re-picking
- The placeholder files (Cpu.kt, Memory.kt, Alu.kt, IoController.kt, InstructionDecoder.kt) could be deleted — they exist for plan compatibility but all logic is in E0C6200.kt

## How to Build & Test

1. Install Android Studio with SDK 35 (API level 35)
2. Open `C:\Users\uzuik\AndroidStudioProjects\DigimonGlyph\` in Android Studio
3. Let Gradle sync (it will download dependencies)
4. Connect Nothing Phone 3 via USB with developer mode + USB debugging
5. Build and run — the `RomLoaderActivity` launches
6. Pick one of your ROM zips (e.g., `digimonv3.zip`) from the file picker
7. Go to phone Settings -> Glyph -> Glyph Toys -> enable "Digimon V3"
8. The emulator should start on the Glyph Matrix

## Git

- Initialized, 1 commit on `master`
- `.gitignore` excludes: ROMs (*.zip, *.bin), reference/, _gradle_temp/, sdk_extracted/, .gradle, build dirs, IDE files
- Ready to push to GitHub (`gh repo create DigimonGlyph --public --source=. --push`)
