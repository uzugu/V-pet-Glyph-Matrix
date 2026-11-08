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

## Preview21: stop the Digivice core during standby

This change belongs to **V-pet Glyph Matrix** (`com.digimon.glyph`), specifically
its D-Power EU 1.01 / SPL61X core. The separate reconstructed D-Arc app is not
required for this update.

As requested, this Digivice mode may deliberately stop firmware time while
asleep. Classic Digimon cores retain continuous real-time emulation. Digivice
standby starts when the phone screen is off and no interactive Glyph toy is
selected, or when the running firmware disables its LCD. LCD sleep uses the
hardware enable bits, not an all-zero picture; cold boot is allowed to run before
the LCD first turns on.

The worker parks without clocking, rendering or running firmware timers. Save
and status commands can still execute without advancing the core. Background
pause releases the CPU wake lock and stops walking/gesture sensors, PCM playback,
haptics and the legacy widget frame pusher. The service's maintenance polling is
reduced to once per second while paused and uses non-wakeup Handler callbacks.
An autosave is requested on entering standby. The optional Glyph timeout request
is released while paused.

Phone screen-on or interactive Glyph play removes background suspension. A
sleeping firmware LCD requires actual user input: widget/phone buttons or Glyph
interaction wake the worker, with a short firmware-time window for the wake input
to be serviced. Maintenance checks do not wake a sleeping LCD. On resume the
pacer rebases deliberately, preserving core/RAM state and avoiding hours of
catch-up work. Walking/automatic steps do not progress during standby.
DND Freeze remains independent.

The emulator status stays RUNNING because the core remains loaded and accepts
wake inputs; this standby is not STOP or a DND freeze. No save format or OWL
protocol change is involved. Widget settings, Japanese localization, Hermes
launcher compatibility and the preview20 Glyph option remain available.

Tests run the actual worker across twelve paused hours, check that maintenance
does not execute firmware, verify return without catch-up and a complete button
press/release after waking LCD sleep. A ROM-free Python test checks the LCD
hardware gate, including an enabled but blank screen. Existing classic clock,
stall and mode-change tests remain part of the gate.

No phone is connected for battery measurements, physical Glyph wake checks or
Nothing OS screen-event verification. CPU parking is verified in software;
battery-percentage savings are not measured.

Preview21 validation: 143 JVM tests (104 passed, 39 skipped), debug APK assembly
and lint passed (zero lint errors). Python tests: 11 passed, six private-ROM
integration tests skipped because `DPOWER_ROM` was unavailable.

## Preview22: queue walking steps during standby

Walking detection now remains registered while the Digivice core is parked,
superseding preview21's walking-sensor shutdown. Detected steps append to the
existing snapshot-backed pulse queue without waking firmware. Its count limit
is now 100,000, using constant memory. Actual wake input resumes playback at the
normal 80 ms press / 120 ms release cadence; long backlogs take time to drain.
A wake-up step detector is preferred if available, with the standard detector as
fallback. No continuous CPU wake lock is held for collection. Walking remains
opt-in and permission-gated; DND, Stop and classic ROM selection retain their
existing restrictions. Automatic steps remain stopped during standby.

Worker tests queue 300 steps across twelve paused hours without a firmware clock,
then check all contact edges after user wake. Pulse tests save/restore and drain
a 10,000-step backlog. Phone sensor delivery, firmware step crediting and battery
impact still need device testing.

Preview22 validation: 145 JVM tests (106 passed, 39 skipped), versioned debug APK
assembly and lint passed with zero lint errors. Package `com.digimon.glyph`,
version code 26, using the existing debug signing certificate. No phone connected.

## Preview23, phase 1: Kotlin D-Power core

The APK uses an Android-free Kotlin SPL61X core and PCM mixer. Chaquopy and all
Python/native-runtime packaging are removed. The pinned Python reference now
lives under `tools/dpower_reference/`, used only by host validation tools.
Opcode/SFR bodies are reproduced with `tools/port_spl61x.py`; primitive fields,
a single memory array and direct `when` dispatch keep the instruction loop free
of allocations. Timer fractions, audio event order and portable schema 1/2 saves
retain reference semantics. Malformed restores are validated on a fresh runtime
before replacing the running core.

`DPowerPortTest` compares all 256 opcode slots under four initial flag/register
states against Python, plus a synthetic program with timers, LCD, DAC and RAM.
The real-ROM test generates a private 30-second golden trace in `/tmp`, comparing
all snapshot fields/RAM, cumulative cycles, ordered audio events and PCM SHA-256.
It includes three front buttons and repeated step contacts. Set `DPOWER_ROM` to
run it; traces and ROMs must stay outside Git. `tools/dump_dpower_trace.py` can
also generate traces manually. Host Python remains necessary for these tests,
but is no longer part of Android execution.

Real-ROM benchmark variables were unset during initial implementation. Therefore
there are **no measured real-ROM before/after numbers or verified <3% result**
yet. The earlier desktop sample (Python: 5.873 CPU seconds per 10 emulated seconds)
is historical, not a matched benchmark against this port. `realRomCpuBenchmarks`
prints both the pure Python reference and Kotlin D-Power CPU throughput and all
six classic ROM throughputs when ROMs are supplied. These are software CPU
measurements; no phone battery improvement is claimed without hardware testing.

ROM-free synthetic benchmark on this host (original test firmware; not a Digimon
ROM): Python 23.332828 CPU seconds / 30.002745 emulated seconds (77.77% of one
core), Kotlin 0.271060 / 30.002747 (0.9035%, about 86x less CPU). Classic synthetic
baseline: 0.057615 CPU seconds / 600.000153 emulated seconds (0.00960%). Values
use per-thread CPU time for Kotlin and process CPU time for single-threaded
Python, include regular PCM drains, and exclude warmup. They do not establish
real-ROM throughput, Android scheduler overhead or phone battery savings.
