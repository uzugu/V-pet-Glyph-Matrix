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
- D-Arc idle batching, lower refresh and identical-save/display suppression are
  implemented; final build/lint and hardware validation are tracked separately.
- Initial clock traces across all six classic revisions agree on the candidate
  layout; rollover/read-write tracing and the override remain unfinished.
- G2/save exchange work is tracked in `even-and-save-exchange.md`; remaining
  hardware checks must not be represented as completed.

## Goal tracker

The user cleared the former goal and the unified objective is now active in
this session's goal tracker.
