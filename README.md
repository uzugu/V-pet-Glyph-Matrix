# Digimon Glyph Matrix (for V-pets)

Play your favorite Digimon V-pet ROMs natively on Android! This app works seamlessly with the **Nothing Phone (3) Glyph Matrix**, but also features a **Home Screen Widget Mode** so you can raise your Digimon on **ANY Android phone**.

## Key Features

- **Play on Any Phone:** Raise your Digimon directly from your Android Home Screen with beautiful, customizable widgets.
- **Nothing Phone (3) Exclusive:** Renders authentic LCD pixel art directly to the 25x25 Glyph Matrix on the back of the device. Maps phone motion (flicks) to physical V-pet buttons.
- **Multiplayer Battles:**
  - **Online Play:** Connect with friends across the world using internet relays.
  - **Local Play:** Battle other devices nearby.
  - **AI Mode:** Practice and battle against bots.
- **True Emulation:** Accurately emulates the original Seiko Epson E0C6200 CPU for a 1:1 authentic V-pet experience. 
- **Auto-Save:** Rolling autosave with timestamp/ROM identity checks ensures your Digimon's progress is always safe.

## Installation

**[Download the latest APK from the Releases page!](https://github.com/uzugu/V-pet-Glyph-Matrix/releases)**

1. Download and install the APK.
2. Provide an authentic Digimon ROM file (classic **V1JA, V1JB, V1ES, V2J, V3J, V4J** and experimental **D-Power EU 1.01** are supported). Accepted formats are `.bin` or `.zip` (classic 8/16 KiB; D-Power 96 KiB).
3. If using a Nothing Phone (3), enable the app under `Settings -> Glyph -> Glyph Toys`.
4. Add the widget to your home screen!

## How to Play

### Home Screen Widget (Any Phone)
Tap the embedded buttons on the widget to interact with your Digimon, just like the real toy! Ensure your launcher is set to allow widgets to update in the background.

### Nothing Phone (3) Glyph Controls
- **Flick left/right** -> A/C Buttons
- **Flick toward/away from you** -> B Button
- **Glyph button hold** -> B hold
- Combos like `A+B` and `B+C` are supported via the launcher app.

## For Developers & Enthusiasts

This project ports the `BrickEmuPy` architecture directly into Android Kotlin for real-time emulation.

### Requirements to Build:
- Android Studio (Hedgehog+ recommended)
- Android SDK 35, JDK 17
- Optional for hosting your own internet relay mode: Python 3 (`tools/battle_relay_server.py`) or Docker (`docker-compose.relay.yml`).

### Build Instructions:
```bash
git clone https://github.com/uzugu/V-pet-Glyph-Matrix.git
cd V-pet-Glyph-Matrix
python3 tools/fetch_glyph_sdk.py
./gradlew assembleDebug
```

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

### Technical Deep Dive
- Uses `digimonStatsText` live rendering to scrape RAM offsets (e.g. `0x0B` for Species ID) providing the Android UI with exact stage and digimon info.
- Lobbies auto-generate Crest-themed names (e.g., `Courage-241`) and broadcast the user's Tamer name and Digimon states. 
- **Battle Protocol Deciphered:** The app intercepts hardware-level V-pet signal sequences, mapping the original physical battle protocol to native Android logic for seamless Local, Online, and AI battling.
- You can manually track states via emulator telemetry commands!

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
python3 tools/fetch_glyph_sdk.py
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
- Nothing Glyph Matrix SDK 2.0 (download with `python3 tools/fetch_glyph_sdk.py`)

## Native OWL widget (preview)

Install the matching Omalauncher Hermes OWL preview and this app's OWL preview,
then load your existing ROM in V-pet. The launcher's bundled `digivice` widget
is on home page 2. Tap its LCD to open the larger native OWL screen.

The LCD pixels and indicators use the current launcher theme; A/B/C buttons
control this app's existing emulator. Frames are pushed through an explicit
Android Messenger service at the emulator's selected frame rate, without HTTP,
Linux, a NAS, or a separate emulator. The phone app retains all ROMs and saves.
DND freezes are respected. The launcher stops listening when no active V-pet
widget needs frames, and slow receivers skip stale frames rather than queueing.

Bridge protocol 1: bind action `com.digimon.glyph.OWL_BIND` on
`com.digimon.glyph.DigimonGlyphToyService`. Only `dev.omalauncher.hermes` and
`dev.omalauncher` are accepted. Versioned `SUBSCRIBE`, `UNSUBSCRIBE`, `BUTTON`
and frame `ACK` messages use a reply Messenger. Frame payloads contain bounded
ASCII pixel rows (`p` LCD, `i` active indicator, `d` inactive indicator), display
size and ready/frozen state. ROM, RAM and saved-game data are not exposed.

## Clock synchronization

The emulator now follows the phone's elapsed time at 1x in every rendering mode.
Scheduler delays are recovered by executing the owed firmware instructions,
preserving each timer interrupt. The former speed correction is removed, and
fractional timer state survives save/restores. See
[the diagnosis and verification](docs/clock-synchronization.md).

SDK 2.0 setup, official documentation and hardware limits: [Glyph integration review](docs/glyph/sdk.md).

To produce a version-named preview APK, run `./gradlew :app:packageVersionedDebug`. The download is written to `app/build/outputs/distributions/V-Pet-Glyph-<version>-debug.apk`.

## D-Power console (1.4-dpower.preview9)

Open **D-Power console** after loading D-Power EU 1.01. The Digivice-themed menu groups device controls, step training, Glyph display, and sound/feedback. Automatic steps offer 0.5–4 steps/second through the emulated Step contact; walking and manual inputs take priority. Display options include the split NanoFont clock, proportional LCD fit, four pet/clock brightness presets, and smoother rendering. Settings persist automatically; ROM import, saves and DND remain on the main screen. See [step input](docs/dpower/pedometer.md) and [clock/brightness](docs/dpower/clock.md).

Preview 9 validation: 64 JVM tests, debug APK build and Android lint. Phone menu layout, physical LED brightness and firmware step crediting still need on-device verification.

Preview 10 adds **Scan NFC card** to the D-Power console. While the app is open and unlocked, one NFC tag detection sends the existing Card input. Tag contents do not select a power. See [NFC operation and limits](docs/dpower/nfc.md).
