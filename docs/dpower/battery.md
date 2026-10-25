# Background battery investigation — preview13

The reported overnight battery drain is not yet reproduced on the phone: ADB was unavailable during this change. This update removes measured waste; it does not establish an overnight battery-life improvement.

Changes:

- Normal emulator pacing coalesces work into roughly8ms windows, instead of waking the CPU after tiny instruction-sized time advances. Every instruction owed to elapsed time is still executed. Input/commands unpark the worker. Full-accuracy/link timing retains fine pacing, and the clock is never rebased for this optimization.
- Emulator worker uses normal priority rather than display/max priority.
- D-Power PCM mixer advances fully silent intervals in one operation, preserving fractional samples and the preceding audible edge. It still synthesizes active tones normally.
- Entirely silent PCM packets are discarded at playback delivery, not submitted to AudioTrack. After250ms without audible data, the playback thread releases AudioTrack. Muted/reset packets retain their existing epoch protection.

Microbenchmark on the build machine,30000 calls covering30 emulated seconds of silence: previous mixer0.5518 CPU seconds, optimized mixer0.0143 CPU seconds (about39× cheaper **for silent mixing only**, not the complete emulator or phone power draw).

A separate complete-runtime sample took6.173 CPU seconds before and5.873 after for10 emulated idle seconds on this loaded build host (about5% less CPU in that sample). The large mixing speedup does not translate to a39× emulator speedup. Android wakeup/audio-device savings are not measured by this desktop CPU sample.

The real EU1.01 ROM did not enter its CPU sleep state during the sampled startup/idle intervals. A static LCD does not mean firmware timers or instructions can be skipped. The app still holds its simulation wake lock to preserve the clock; removing that alone would risk suspending emulation or creating long catch-up debt. Further battery work needs a phone CPU/battery trace and longer locked-screen testing. Full-accuracy mode also intentionally retains fine pacing.

Checks: real-ROM Python state/audio/input continuation tests, silence/subsample waveform tests, classic firmware clock regression across display stalls and timing-mode changes, normal-mode worker wakeup bound and command delivery. Android build and lint are required before publishing the preview.

This change is in **V-pet Glyph Matrix**, package`com.digimon.glyph`. The separate reconstructed D-Arc application in`d-arc-glyph/app-project` has its own render-driven runtime. Its separate power-saving changes are documented in that repository; these APKs do not share code at runtime.


## Preview13: explicit power-saving mode

The launcher now has a persisted **Power saving** switch. It uses the existing
4 FPS display profile and 20 ms wakeup batching. It overrides the cosmetic
smoother-display preference; input temporarily boosts refresh and battle links
retain full timing. The mode changes neither CPU oscillator speed nor the owed
instruction budget. The simulation wake lock remains held in this mode so
Android sleep cannot silently stop the firmware clock. This is a reduction in
refresh/wakeup work, not a CPU-free standby mode for a running ROM.

New checks verify mode precedence, fewer than 90 worker waits over a 1.2-second
power-saving sample, one serviced firmware second in that sample, and the
existing render-stall/mode-transition continuity regression. With the six private
classic ROMs supplied through `CLASSIC_ROM_DIR`, the complete suite reports
75 tests, 74 passed, one opt-in hardware test skipped. Debug assembly and lint
passed. Phone battery and locked-screen Glyph retention remain unverified.


## Preview15: stop unused idle gesture sensors

In persisted power-saving mode, the input controller unregisters rotation and
acceleration sensors while controls are idle. They previously kept sampling at
normal rate even though gesture processing ignored idle samples. Pressing the
Glyph button immediately restores game-rate sensors; releasing it begins a
60-second activity window. A held button keeps controls armed. The timeout runs
on the main Handler using monotonic uptime, so becoming idle does not require
another sensor event. Stop cancels that timeout and releases held keys.

Registration resumes with fresh motion/filter state to prevent a stale sample
from generating a flick. Both linear acceleration and accelerometer fallback
follow this policy. Normal mode retains its original normal-rate idle sensors.
Hardware step inputs use the separate WalkingInput path and are unaffected.
Changing power mode applies the sensor policy immediately, including when the
emulator is temporarily running at an interaction-boost timing profile.

Host tests exercise the actual InputController against recorded SensorManager
calls and a virtual Handler: no idle registrations, immediate wake, held input,
release timeout, preference transitions, fallback sensor, stop cleanup, fresh
motion baseline and a deliberate flick. This establishes software behavior;
phone sensor power draw and gesture latency still require hardware measurement.

Preview15 gate: 117 JVM tests, 116 passed, one opt-in Frame hardware test
skipped. All six private classic ROMs were supplied; versioned debug packaging
and lint passed. No Android device was connected.
