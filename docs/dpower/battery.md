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

Real-ROM benchmark variables were unset during initial implementation. At that
point there were **no measured real-ROM before/after numbers or verified <3% result**
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

### Phase 2: budgeted conservative idle skipping

Classic HALT batches only complete seven-cycle reference slots whose OSC1 ticks
cannot hit any timer, stopwatch, programmable-timer or buzzer edge. The edge's
slot uses the original instruction path, preserving interrupt sampling order,
latency, phase and elapsed cycles. Pending interrupts/keys, RESET and delayed IF
fall back to the reference. `idleSkippingEnabled=false` retains that reference
path for differential tests. No deprecated oscillator-only fast-forward is used.

D-Power's experimental, persisted debug option recognizes only an absolute jump
to itself in ROM, with no pending enabled IRQ, and stops before the earliest
peripheral event. Register-changing or port-reading polling loops are deliberately
not classified without a proven real-ROM trace. This narrower optimization is
off by default. The private 30-second trace comparison runs it both on and off.

The worker passes owed/step-gate/button-edge budgets to cores. Instructions remain
atomic (so they may exceed a fractional budget), but idle skips cannot cross it.
Non-link sessions do not call the virtual battle clock callback per instruction.
Tests compare three synthetic classic hours, tiny budgets/key inputs and D-Power
self-jump timer state, with six-ROM/midnight checks gated by `CLASSIC_ROM_DIR`.

### Phase 3: real-time standby and periodic catch-up

For both cores, a locked phone with no interactive Glyph, battle link, step gate
or current audible output parks the worker indefinitely and releases the CPU
wake lock. It retains elapsed-realtime debt; only explicit DND or the persisted
D-Power freeze option discards simulation time. Screen-on/Glyph activation,
actual input and save/export barriers repay debt through firmware execution.
Maintenance saves are suppressed in standby; an inexact
`AlarmManager.setAndAllowWhileIdle` service action requests catch-up, attention
checks, DND reconciliation and autosave, then schedules the next alarm. The
persisted interval defaults to 15 minutes (15–120 minute console control).
Android can delay inexact/Doze alarms; exact phone delivery is not established by
host tests. There is no process-death/offline restoration clock recovery added.

Catch-up stays on the emulator worker with command checks in 2 ms work slices.
Large debts show localized "Catching up" status without discarding time or
blocking input submission. Synchronous commands wait for their wall-time barrier;
if their existing timeout expires they fail safely rather than save stale state,
while catch-up continues. Very long D-Power debt can therefore require a retry of
a synchronous export/Stop after catch-up completes. Historical audio is mixed for
state fidelity but not replayed as a burst of old sounds. Active background audio
keeps execution running with 500 ms emulation coalescing (full-accuracy/link stays
fine); the PCM queue holds a complete burst. Physical playback needs a phone test.

Walking callbacks carry detector timestamps. During standby they enter an ordered
worker timeline without waking firmware; catch-up applies them when firmware
reaches those times. This timeline is memory-resident until catch-up; periodic
saves then contain the resulting step queue/core state. Intentional freeze keeps
preview22's queue-and-replay-on-wake behavior. Reset/restore session guards still
reject stale input.

Fake elapsed-clock tests compare eight suspended hours with a continuously
executed classic reference, including all timer/interrupt counters and core state;
they also exercise periodic catch-ups and out-of-order batched sensor timestamps.

Fake-clock worker waits over ten seconds: old normal pacing 1,232 (123.2/s);
standby one initial indefinite park and **zero periodic worker wakes**. The
15-minute alarm policy requests at most 4 catch-ups/hour by default (0.00111/s
if delivered on time), plus real input/system-triggered requests. These are
worker/policy counts, not measured whole-phone wakeups; sensor, service and OS
activity are separate.

### Phase 4: event-driven, bounded widget frames

Legacy widgets now render at at most 256 pixels on the long side, preserving the
widget's aspect ratio. A 1200x1200 ARGB bitmap's 5.76 MB pixel payload becomes at
most 256x256 / 262 KB (about 22x smaller, before transport encoding). Native LCD
sampling retains the skins' nearest-neighbor paint; the launcher controls final
ImageView upscaling, so nearest-neighbor host scaling is not guaranteed.

`FrameDebugState` notifies the widget worker on new frames; no 120 ms widget poll
remains. Pending notifications coalesce, and a content hash including geometry,
skin and freeze state suppresses unchanged RemoteViews pushes. Resize/config/DND
changes force a refresh. Widget and OWL reads no longer mark debug memory as
observed. RAM/VRAM previews are copied only for enabled telemetry or an observed
memory/debug UI. Each service frame renders the full LCD bitmap at most once,
sharing it between Even, OWL and preview consumers.

Interactive Glyph and actively observed OWL/Even displays retain responsive
pacing even with the front screen off. Authenticated Even frame requests expire
after three seconds without a reader; merely enabling its server cannot prevent
standby indefinitely. Actual button contacts hold a short active window after
catch-up so a locked-phone widget/remote tap can complete its press/release;
sensor and alarm events do not open that window.

### Final matched synthetic CPU run

After warmup, a matched full debug test run measured these original ROM-free programs
on this build host. Results vary with host/JIT state; neither workload is a
private Digimon ROM.

| Core/workload | Before CPU seconds | After CPU seconds | Emulated seconds | CPU reduction |
| --- | ---: | ---: | ---: | ---: |
| D-Power synthetic CPU/timers/DAC | 23.472409 (Python) | 0.278173 (Kotlin) | 30.002745 | 84.4x |
| Classic synthetic 1 Hz ISR/HALT | 0.048162 (step path) | 0.012934 (idle skip) | 600.000000 | 3.72x |

D-Power used 0.927% of one desktop core in this synthetic run; classic used
0.00803% before / 0.00216% after. Per-CPU-second throughput was approximately
1.28 -> 107.86 emulated seconds for D-Power and 12,458 -> 46,388 for classic.
These synthetic measurements preceded the private-ROM run below. No phone
battery numbers are inferred from either set of host tests.

### Initial ROM-free validation

`./gradlew test lint assembleDebug` passed for both variants (163 tests each,
42 skipped, no failures); after the final battle callback guard the full debug
suite passed 164 tests (42 skipped) and the focused release clock/standby/idle/
pacer suite passed 23 tests (one private-ROM skip). Lint and APK packaging were
rerun with that guard. Lint has existing warnings; it reports no errors.
`python3 -m unittest discover -s tests -v` passed 11 tests with six private-ROM
skips. Localization passed 489 values and 42 verified species.

Executed differential coverage includes all 256 opcode slots with four seeds,
synthetic CPU/timer/RAM/LCD/audio-event traces, PCM hashes/fractional mixer state,
schema 1/2 round trips and atomic malformed restore, three classic HALT hours,
eight-hour suspended versus continuous interrupt/state execution, and active
battle fast-oscillator HALT callback granularity. `DPOWER_ROM` and
`CLASSIC_ROM_DIR` were unset: the required real-ROM 30-second D-Power input/step
traces, six-ROM multi-hour/midnight comparisons and real-ROM benchmarks skipped
cleanly. Those private-ROM checks were subsequently run as recorded below.
Physical audio, Doze alarm and battery measurements remain outstanding. Broader D-Power
flag/port polling-loop recognition still needs loop-specific equivalence proof;
the narrow debug optimization stays disabled by default. No private ROM or
generated trace is committed.

### Private-ROM verification, 2026-11-09

All seven firmware files were found outside this checkout. D-Power is the
96 KiB EU 1.01 image with SHA-256
`f1f0a92bdcbd87514b7c981ca126713ee7e050ec262fcbfe90e75d2e1bc852ca`;
the classic files identify as V1JA, V1JB, V1ES, V2J, V3J and V4J.

The full debug and release suites each passed 163 tests with one unrelated
Syncthing hardware round-trip skip (164 total per variant, zero failures). All 17 Python tests passed with no skips. The D-Power
30-second scripted trace matched Python exactly at every checkpoint, including
registers, flags, RAM, fractional timer fields, cumulative cycles, ordered audio
events and PCM SHA-256, with idle skipping disabled and enabled. All six classic
ROMs matched the per-step reference for three emulated hours each, including
button inputs and midnight rollover; timer/interrupt diagnostic counters also
matched. Existing six-ROM firmware clock and snapshot regressions passed.

In the full debug test run, after two emulated seconds of warmup, the D-Power
Kotlin runtime achieved
160.90 emulated seconds per CPU second (**0.6215% of one desktop core**).
The matched Python reference achieved 2.7094 emulated seconds per CPU second
(about 36.91% of one core), a **59.4x CPU improvement** on this workload. This
meets the desktop <3% target for the measured startup/idle firmware workload;
it does not measure every gameplay state. Both ran 30,000 advances with regular
PCM drains. The Kotlin benchmark uses the default disabled D-Power idle skip.

Classic real-ROM benchmarks ran 600 emulated seconds after 60 seconds of warmup,
with identical firmware and the HALT optimization disabled/enabled:

| Firmware | Before emu seconds / CPU second | After | CPU reduction | Before core usage | After |
| --- | ---: | ---: | ---: | ---: | ---: |
| DigimonV1JA | 7,407.4 | 13,421.1 | 1.81x | 0.01350% | 0.00745% |
| DigimonV1JB | 7,549.8 | 13,521.4 | 1.79x | 0.01325% | 0.00740% |
| DigimonV1ES | 7,282.3 | 13,085.5 | 1.80x | 0.01373% | 0.00764% |
| DigimonV2J | 7,464.0 | 13,617.7 | 1.82x | 0.01340% | 0.00734% |
| DigimonV3J | 7,499.2 | 13,521.6 | 1.80x | 0.01333% | 0.00740% |
| DigimonV4J | 7,575.0 | 13,445.9 | 1.78x | 0.01320% | 0.00744% |

CPU time uses JVM thread CPU time and Python process CPU time on the build host;
it excludes JIT/reference warmup. These software measurements do not establish
Android scheduler costs, physical audio behavior or phone battery life. The
conservative D-Power debug skip remains off by default; general polling-loop
recognition is still deferred.

Reproduce with local private paths (Gradle's rerun flag prevents cached tests
from hiding a change in environment variables):

```bash
DPOWER_ROM=/private/DPowerDigiviceEU101.bin \
CLASSIC_ROM_DIR=/private/classic \
./gradlew test --rerun-tasks

DPOWER_ROM=/private/DPowerDigiviceEU101.bin \
python3 -m unittest discover -s tests -v
```

No ROM or generated golden trace was added to Git.
