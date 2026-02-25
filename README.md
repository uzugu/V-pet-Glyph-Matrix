# Digimon V3 on Nothing Glyph Matrix

Android app that runs Digimon virtual pet ROMs on the **Nothing Phone (3) Glyph Matrix** (25x25 back LED grid).

- Emulates the original **Seiko Epson E0C6200** CPU
- Renders Digimon LCD output to the Glyph Matrix
- Maps phone motion + Glyph button to Digimon A/B/C inputs

## Features

- ROM loader for `.bin` and `.zip` (extracts first `.bin` found)
- Full E0C6200 emulation pipeline ported from BrickEmuPy architecture
- Emulator stability fixes for training flow soft-locks
- Real-time emulator loop with autosave and state restore
- Save system:
  - Rolling autosave with timestamp/ROM identity checks
  - 3 manual save slots
  - Manual save/load commands from launcher UI
- Session controls:
  - Restart emulator
  - Full reset (cold reset + autosave rewrite)
  - Explicit combo taps from launcher: `A+B`, `A+C`, `B+C`
- Audio:
  - Buzzer playback with envelope/one-shot behavior
  - In-app audio toggle
  - Optional haptic-from-audio toggle
- Display:
  - Center-priority render/crop tuning for circular Glyph area
  - Auto text zoom-out heuristic mode
  - Toggle for text zoom heuristic
  - 8-position menu ring overlay around the matrix
  - Pixel-art preview rendering without blur in launcher debug screens
- Input:
  - Glyph button hold -> **B** hold
  - Flick left/right -> **A/C**
  - Flick toward/away from user -> quick **B** tap
  - Haptic feedback on successful flick trigger
  - Combined holds supported (for example A+B and B+C)
  - Input polling stays active for 60 seconds after interaction, then returns to idle polling
- Battle link:
  - Nearby host/join (local phone-to-phone)
  - Experimental internet relay mode (`tcp://host:port/room`)
  - In-app transport selector + relay URL field in Battle section
- Debug and diagnostics:
  - Launcher reorganized with main gameplay controls + live screen previews first
  - Debug tools moved to collapsible **Show Debug / Hide Debug** section
  - Live input/frame debug panel inside collapsible section
  - Optional debug telemetry toggle (off by default)
  - Live screen previews remain available while launcher observes them, even with debug telemetry off
  - On-device command bus for runtime setting refresh and save/load commands
- Timing and power modes:
  - `Exact timing (higher battery)` toggle in launcher settings
  - `ON`: accuracy-first pacing, wake lock kept for stable timing
  - `OFF` (Power Save): reduced baseline speed and lower wake/scheduling pressure for reduced battery/heat
  - In `OFF`, user interaction temporarily boosts timing to exact mode for 60 seconds, then returns to power-save

## Requirements

- Android Studio (Hedgehog+ recommended)
- Android SDK 35
- JDK 17
- A Nothing phone with Glyph Toys support
- Digimon ROM file (`8192` or `16384` bytes)
- Optional for internet relay mode: Python 3 (to run `tools/battle_relay_server.py`)

## Project Setup

1. Clone:
```bash
git clone https://github.com/uzugu/V-pet-Glyph-Matrix.git
cd V-pet-Glyph-Matrix
```
2. Open the project in Android Studio.
3. Let Gradle sync and install missing SDK components if prompted.
4. Connect your phone with USB debugging enabled.

## Build and Install

Windows:
```powershell
.\gradlew.bat installDebug
```

Linux/macOS (including Steam Deck):
```bash
chmod +x gradlew
./gradlew installDebug
```

## How to Use on Phone

1. Launch the app (`Digimon V3`).
2. Tap **Select ROM File** and choose a Digimon `.bin` or `.zip`.
3. Open Glyph settings on the phone:
`Settings -> Glyph -> Glyph Toys`
4. Enable **Digimon V3** toy.
5. Start the toy and check the back Glyph Matrix.
6. Optional: use launcher combo buttons (`A+B`, `A+C`, `B+C`) for precise combo inputs.

## Controls

- Flick left/right -> **A/C**
- Flick toward/away from you -> quick **B**
- Glyph button hold -> **B** hold
- Hold Glyph button while triggering A or C for combo-style input timing
- Use launcher **Combo Buttons** for direct `A+B`, `A+C`, `B+C` taps

Notes:
- Flick uses impulse + rebound detection with cooldown/hysteresis.
- A short vibration confirms successful flick trigger.
- You can hold combinations by holding Glyph button with A/C actions (A+B or B+C).

## Timing Modes

- `Exact timing (higher battery)` switch is in the launcher app.
- `ON` is for most accurate speed.
- `OFF` is Power Save mode:
  - Lower render/clock pacing
  - Lower CPU and heat
  - Speed is intentionally not exact
  - Interaction with Glyph/flick/combo input boosts to exact timing for 60 seconds

## Internet Relay Battle (Experimental)

This mode allows battle linking over internet/LAN through a simple relay.

### Option A: Docker (recommended for quick local tests)

```bash
docker compose -f docker-compose.relay.yml up -d
```

Stop:

```bash
docker compose -f docker-compose.relay.yml down
```

### Option B: Python directly

1. Run relay server (for example on Raspberry Pi):
```bash
python3 tools/battle_relay_server.py --host 0.0.0.0 --port 19792
```
2. On both phones open **Battle Mode (Beta)**.
3. Enable **Use Internet relay (experimental)**.
4. Set the same relay URL on both phones, for example:
   - `tcp://192.168.0.50:19792/room1`
   - `tcp://your-public-ip:19792/room1`
5. Press **Host** on one phone and **Join** on the other.

Notes:
- Both devices must use the exact same room string.
- `Host` and `Join` are single-slot roles per room.
- Internet mode is currently relay-based only (no direct NAT punch-through yet).

## ROM Notes

- ROM files are not included in this repository.
- Accepted sizes: `8192` or `16384` bytes.
- The selected ROM is copied to app internal storage as:
  - `current_rom.bin`
  - `current_rom_name`

## Architecture (High Level)

`ROM -> E0C6200 emulator -> VRAM -> DisplayBridge -> 25x25 Bitmap -> GlyphRenderer -> Nothing Glyph SDK`

Input path:

`Sensors + Glyph Button -> InputController -> E0C6200 K0 pins`

## Useful Commands

Build APK:
```bash
./gradlew assembleDebug
```

Install debug build:
```bash
./gradlew installDebug
```

View logs:
```bash
adb logcat | grep -E "DigimonGlyphToy|DigimonInput|GlyphRenderer|AndroidRuntime"
```

Reset battery stats baseline before profiling:
```bash
adb shell dumpsys batterystats --reset
adb shell dumpsys batterystats com.digimon.glyph
```

## Troubleshooting

- App installs but toy shows nothing:
  - Confirm ROM was loaded in app first.
  - Re-enable the toy in Glyph Toys settings.
  - Check logs for `DigimonGlyphToy`.
- Controls feel wrong direction:
  - Tune axis/sign mapping in `app/src/main/java/com/digimon/glyph/input/InputController.kt`.
- Build fails with SDK/JDK errors:
  - Ensure SDK 35 and JDK 17 are installed and selected in Android Studio/Gradle.

## Tech Stack

- Kotlin
- Android SDK / Gradle
- Nothing Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-1.0.aar`)
