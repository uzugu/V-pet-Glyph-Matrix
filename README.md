# Digimon V3 on Nothing Glyph Matrix

Android app that runs Digimon virtual pet ROMs on the **Nothing Phone (3) Glyph Matrix** (25x25 back LED grid).

- Emulates the original **Seiko Epson E0C6200** CPU
- Renders Digimon LCD output to the Glyph Matrix
- Maps phone motion + Glyph button to Digimon A/B/C inputs

## Features

- ROM loader for `.bin` and `.zip` (extracts first `.bin` found)
- Real-time emulator loop with state autosave/restore
- Menu indicator overlay (8 positions around the circular matrix)
- Tilt-hold controls with haptic confirmation
- Supports combined button holds (for example A+B, B+C)

## Requirements

- Android Studio (Hedgehog+ recommended)
- Android SDK 35
- JDK 17
- A Nothing Phone (3) with Glyph Toys support
- Digimon ROM file (`8192` or `16384` bytes)

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

## Controls

- Tilt left and hold -> **A**
- Glyph button hold -> **B**
- Tilt right and hold -> **C**

Notes:
- A/C use dwell + hysteresis to reduce accidental triggers.
- A short vibration confirms A/C activation.
- You can hold combinations by holding tilt + Glyph button (A+B or B+C).

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

## Troubleshooting

- App installs but toy shows nothing:
  - Confirm ROM was loaded in app first.
  - Re-enable the toy in Glyph Toys settings.
  - Check logs for `DigimonGlyphToy`.
- Controls feel wrong direction:
  - Update tilt axis flags in `app/src/main/java/com/digimon/glyph/input/InputController.kt`.
- Build fails with SDK/JDK errors:
  - Ensure SDK 35 and JDK 17 are installed and selected in Android Studio/Gradle.

## Tech Stack

- Kotlin
- Android SDK / Gradle
- Nothing Glyph Matrix SDK (`app/libs/glyph-matrix-sdk-1.0.aar`)

