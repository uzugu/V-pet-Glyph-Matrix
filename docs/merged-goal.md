# Unified project goal

Updated 2026-10-25 at the user's request to merge the previous goal.

Improve the Android V-pet Glyph and separate reconstructed D-Arc apps with
an explicit, battery-efficient power-saving mode, prioritizing D-Arc idle use.
Preserve game progression, clock timing, inputs, alarms, audio and save data.
Emulate and trace the six classic Digimon ROM revisions to establish how their
firmware clock is stored and displayed, then implement a verified clock override.
Keep the previously requested Even G2 streaming display/gamepad and Syncthing
save exchange with the separate Steam Frame app within this same project goal.

## Priority and acceptance criteria

1. **Power saving in both Android apps.** Inspect idle scheduling, CPU work,
   rendering, audio, persistence and wake locks. Provide a persisted mode with
   immediate response to input. Verify time progression across idle intervals
   and mode transitions. Measure software costs and distinguish them from
   overnight battery measurements on hardware.
2. **Classic firmware clock override.** Identify each supported ROM, reproduce
   clock setting and rollover in emulation, and trace candidate memory fields.
   Verify changes affect the intended displayed time without changing care or
   age counters. Test rollover, save/restore and unsupported-ROM rejection.
   Keep all ROM binaries outside Git.
3. **Finish existing G2 and save exchange validation.** Retain the existing
   versioned, atomic snapshots, ROM identity checks, backups and conflict
   handling. Verify real G2 display/input, reconnect and held-input release,
   plus phone/Frame save continuity when devices are available. Frame remains
   a separate application.

## Current evidence and remaining work

- V-pet Glyph commit `97fa3f5` reduces scheduler wakeups and silent D-Power audio
  processing. This does not establish acceptable overnight battery usage.
- Persisted V-pet power-saving controls are implemented in preview13; the full
  JVM suite, including six private-ROM cases, debug build and lint passed.
- D-Arc 1.1.1 adds idle batching, lower refresh, identical-save/display suppression
  and selective motion sensing. Commit `9b51440` in its separate repository
  integrates upstream fidelity fixes and passed 213 JVM tests, debug assembly and lint. Hardware checks remain pending.
- Preview15 additionally stops unused idle gesture sensors in power mode, with
  immediate restoration on Glyph input and a monotonic idle timeout. The full
  private-ROM suite reports 117 tests (116 passed, one hardware check skipped);
  debug packaging and lint passed.
- Preview14 implements one-time firmware clock controls for all six classic
  revisions. `clock-traces/` records Python and Kotlin button/rollover traces,
  safe-boundary and unchanged-state checks, JSON save continuation, rendered
  digits and portable phone/Kotlin–Frame/Python clock-state roundtrips.
  Phone UI validation remains a hardware check.
- Preview16 hardens save publication against stable SAF document IDs and
  ambiguous renames, separates ancestry failures from completed handoffs, and
  verifies/syncs private backups before replacing a pet. Battle-linked handoffs
  are rejected. Its full gate passed 122 tests with one hardware test skipped,
  versioned debug packaging and lint. Phone SAF validation remains pending.
- G2/save exchange work is tracked in `even-and-save-exchange.md`; remaining
  hardware checks must not be represented as completed.

## Goal tracker

The user cleared the former goal and the unified objective is now active in
this session's goal tracker.
