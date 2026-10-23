# D-Power EU 1.01 on Nothing Glyph Matrix

This preview adds the user-supplied 96 KiB D-Power EU 1.01 firmware to the original Android Glyph app. It is not a Japanese D-Ark profile. No ROM is bundled.

The **Nothing Phone Glyph Matrix is the primary display**. The mapped 32×16 LCD runs through the existing 25×25 Glyph renderer, using full-width fit for D-Power so side columns are not cropped. The fit combines horizontal pixels, so it cannot retain the source LCD's full resolution. Phone previews and controls are optional setup/diagnostic tools.

## Play using the Glyph surface

- Horizontal flick: Left / Right.
- Positive / negative Z-axis flick (toward/away motion): Up / Down.
- Tap the physical Glyph button: Up (on release).
- Hold the Glyph button and make a negative flick: Step.
- Hold it and make a positive flick: Card.

A modified flick suppresses the release tap. All inputs use the upstream active-high EF/CD wiring. Step is a manual sensor pulse, not an automatic pedometer. Gesture sign/comfort and screen legibility require a Nothing Phone test. The phone's “D-Power controls and gestures” panel provides the same six inputs as optional buttons.

## ROMs and saves

Import a BIN or ZIP. Multiple supported BINs in a ZIP are offered for explicit selection. Reads/decompression are bounded and archive scanning runs off the UI thread. SHA-256 identifies the six known classic revisions and D-Power independently of filename; unknown 8/16 KiB classic images must have valid 12-bit encoding, and unknown 96 KiB images are rejected.

Import stages the ROM and asks the running service to save the previous pet before replacing it. ROM bytes are written with AtomicFile. Known-device names are derived from the hash when loading, so stale display-name files cannot select the wrong core. Snapshots remain isolated by ROM hash.

D-Power snapshots use schema 1, bind to the firmware hash, and preserve all scalar/dictionary machine state, mutable RAM/LCD RAM, fractional timer state, sound envelopes and audio output state. Restore validates into a fresh core before replacing the active instance. The JSON payload is stored as an opaque string by the existing save manager; fractional values therefore bypass its legacy scalar conversion. Classic snapshots retain their existing format.

## Implementation

- Upstream: https://github.com/azya52/BrickEmuPy
- Pin: `376cca92c6d000ffd6046e5491f2a1c3d1a5eab0`.
- Vendored GPL191X.py and GPL191Xsound.py are unchanged. SPL61X maps to GPL191X upstream. License: [UPSTREAM-LICENSE](UPSTREAM-LICENSE).
- Embedded CPython 3.11 via Chaquopy 17.0.0, arm64-v8a for the Nothing Phone and x86_64 for emulator testing.
- Each Kotlin clock call batches approximately 1 ms of firmware and returns actual cycles at 2.2 MHz. The existing elapsed-realtime pacer retains CPU debt. No oscillator-only skipping or guessed speed multiplier.
- `tools/generate_dpower_lcd.py` derives all 512 pixel addresses/masks from upstream SVG geometry. SVG identifiers contain bit indices, converted to masks during generation.
- Common `EmulatorCore` runtime contract; classic E0C6200 instruction implementation is unchanged.

## Validation

Private ROM tests (never copy firmware into Git):

```bash
DPOWER_ROM=/path/to/DPowerDigiviceEU101.bin python3 -m unittest discover -s tests -v
```

Tests compare batched execution with instruction-by-instruction upstream execution, exercise all six inputs, compare snapshot continuation, reject malformed snapshots without partial mutation, and walk all 512 individual LCD segments. Android unit tests cover bounded/validated import, multi-member archives and Glyph gesture wiring, alongside the existing timing/battle suites.

Build/test with JDK 17 and Android SDK configured:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug preview).

Local verification on 2026-10-23: debug APK and lint passed (lint retains warnings); 47 JVM tests passed, comprising 25 repository tests and 22 external audit tests, plus four private-ROM Python tests. External audit cases include deliberate reproductions of pre-existing classic defects, so their passing is not a claim those defects were fixed. The six classic ROMs retained their boot, snapshot and ten-minute timing checks.

## Remaining limits

This is a build-tested preview, not a phone-validated release. No Nothing Phone was connected during implementation, and no Android emulator was installed. Kotlin/CPython interoperability, sustained phone speed, battery usage, gestures and Glyph output still need on-device validation.

Audio currently forwards the strongest tone channel to the existing buzzer/haptic synthesizer at 10 ms intervals. Two-channel PCM mixing is not implemented; sound is not claimed hardware-accurate. D-Power link battles and decoded pet statistics/evolution metadata are unavailable. Existing DND freeze and service-stop clock semantics remain unchanged; this integration does not fix offline time recovery.
