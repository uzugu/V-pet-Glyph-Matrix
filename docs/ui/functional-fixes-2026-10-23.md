# Functional audit follow-up — 2026-10-23

Scope: original Glyph Android app on `feat/dpower-core`, following the [UI baseline](audit-2026-10-23.md). This is a tested preview, not a complete hardware certification.

## Implemented

- Observable device/import/runtime/Glyph state; static LCDs no longer imply the CPU stopped. UI identity is cached instead of repeatedly reading and hashing the ROM on the main thread.
- Cancellable archive scanning, duplicate-import prevention, request-scoped results and visible errors. Construct/validate the replacement before stopping the pet; require a successful save and roll back ROM files/runtime on switch failure.
- Device-aware controls/help/settings; D-Power hides classic battle/care/macros and does not decode its byte RAM using classic nibble profiles. Scrollable D-Power controls, plain action names, density-correct save buttons, reset/rewind confirmations.
- FIFO persisted commands replace the single overwritten command slot; ROM identity prevents stale commands acting on a new pet. Queued commands that miss their start deadline are cancelled instead of executing later after a reported timeout.
- Classic pin interrupt masks and pull-up release corrected. Snapshot schema 2 retains control/timer and buzzer state; malformed restores validate before mutation and legacy snapshots remain readable.
- Save failures abort explicit Stop, Restart and Freeze; reset attempts restore the old state if saving fails. Worker failures become visible runtime errors.
- Glyph output lifetime is separated from explicitly app-started simulation. Toy end/unbind detaches sensors/display immediately; old connection callbacks are ignored. Physical button repeats and stale release callbacks cannot generate phantom inputs for a replacement core.
- [Official SDK 2.0 integration](../glyph/sdk.md), correct brightness/device registration, Phone (3) capability gate and corrected short-press guidance.
- Optional [D-Power walking input](../dpower/pedometer.md) through the existing emulated Step contact.

## Verification

Run the checked-in JVM suite with `CLASSIC_ROM_DIR` pointing at the six private classic dumps. Each classic executes 600 firmware seconds and checks 600 one-Hz events, inputs, display and snapshot continuation. Other tests exercise queue ordering, deadline cancellation, stale status, malformed saves, interrupt masks and Step pulse continuation.

Run Python D-Power tests with `DPOWER_ROM` pointing at the private EU 1.01 dump. Four tests cover upstream equivalence, every LCD segment, all inputs, restore continuation and atomic invalid restore.

Build `testDebugUnitTest assembleDebug lintDebug` with JDK 17. No ROM bytes are tracked. Final checked-in suite: 48 JVM tests, zero failures/errors/skips; four D-Power Python tests passed.

## Still open

- No attached Nothing Phone or Android emulator: validate large fonts/TalkBack, physical gestures, walking recognition, firmware step credit, AOD, screen-off operation, CPython performance and battery use on device.
- Synchronous service commands wait for the actual result once execution begins. Their deadline bounds queue wait, not disk/core execution. Slow storage can still block the service main thread; asynchronous command completion needs a separate lifecycle design. The loop's bounded shutdown join also needs stress testing against blocked work.
- A failed autosave restore still starts a fresh core; recovery/quarantine before subsequent overwrite remains an unresolved data-recovery defect.
- No offline clock replay after process death; Stop and DND Freeze pause simulation intentionally. Ten-minute CPU tests do not establish long-running Android wall-clock performance.
- D-Power sound remains a tone approximation; link battles and decoded pet statistics are unavailable. Some advanced UI labels and section headings still need a visual/localisation pass.
- Lint warnings remain; passing lint means no fatal errors, not a warning-free or accessibility-certified app.
