# Clock synchronization

## Reproduced defect

The old worker tried to recover scheduler delays with `fastForwardOsc1()`,
then moved its wall-time anchor forward. This advances timer peripherals without
executing the ROM's interrupt handlers. The E0C6200 IT register has pending bits,
not a queue: several timer events collapse to one bit.

Original test firmware that counts the serviced 1 Hz interrupt reproduces this:
normal execution over five seconds services five interrupts; skipping five
seconds of oscillator ticks then executing the CPU services only one. Oscillator
and timer diagnostics still appear correct, concealing the lost firmware time.

## Fix

`RealtimePacer` converts Android `elapsedRealtimeNanos()` into a CPU-cycle budget
using the core's clock frequency. This phone clock includes suspend and is not
changed by timezone changes or edits to the displayed clock. Only executed
instructions repay the budget. Slow renders and scheduling gaps keep their debt;
they do not discard firmware work or collapse its timer events.

Rendering runs independently at 4, 15 or 30 FPS. All modes execute firmware at
1x speed. The former 1.20x correction and stored speed multipliers are retired.
Short execution batches keep commands and display callbacks responsive during
catch-up. Missed display frames are skipped; firmware instructions are preserved.

The oscillator, timer, stopwatch and programmable-timer fractional phases are
now included in save states, and the JSON codec preserves them as doubles.
Existing saves remain readable. Intentional DND pauses and debug battle stepping
retain their pause semantics. The pacing fix leaves the ROM's selected time of
day unchanged and does not invent offline progression after the process has stopped. Explicit one-time
clock controls are described in [the firmware clock trace](clock-traces/README.md).

## Verification

- Regression firmware proves five generated interrupts can collapse to one.
- A simulated hour with mixed 1 ms to 2.5 s gaps services all 3,600 seconds.
- 4/15/30 FPS schedules and both CPU oscillator modes preserve the 1 Hz timer.
- 600 repeated save/restores match uninterrupted fractional timer phases.
- The real JSON save codec preserves those phases and subsequent timer behavior.
- Real elapsed-time tests include a 500 ms stall.
- The actual worker is tested with a 2.3 s rendering stall and switches through
  all three display modes while preserving firmware seconds.

Simulated budget checks allow less than 0.7 ms of instruction-boundary overshoot.
A device that cannot execute the required instructions can temporarily lag while
catching up; this implementation does not hide lag by deleting firmware time.
These tests use original test firmware, not the user's Digimon ROM. No phone was
connected for device verification at the time of this change.
