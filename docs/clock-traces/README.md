# Classic Digimon firmware clock trace

2026-10-25: `tools/trace_classic_clock.py` verified each of the six ROM hashes
against the Frame catalog using the vendored upstream Python E0C6200 core.
No ROM files or full RAM dumps are included here.

Reproduce with private dumps:

```sh
python3 tools/trace_classic_clock.py \
  --frame-root /srv/data/projects/V-pet-frame \
  --rom-dir /home/uzugu/digimon-rom-research \
  --output /tmp/classic-clock-trace.json
```

The script uses only emulated button presses to set every hour and minute,
confirms 11:59 PM, then executes the firmware through 11:59:59 PM, midnight,
and midnight plus one second. Each expected field is checked, and all reads
and writes of the candidate fields record their instruction addresses.
`classic-clock-trace.json` contains those observations for all six revisions.

| RAM nibble | Observed meaning |
| --- | --- |
| `0x01A` | Seconds units, decimal 0–9 |
| `0x01B` | Seconds tens, decimal 0–5 |
| `0x01C` | Minutes units, decimal 0–9 |
| `0x01D` | Minutes tens, decimal 0–5 |
| `0x01E` | Hour 1–12, one binary nibble; 10=A, 11=B, 12=C |
| `0x01F` | AM=0, PM=1 |

The initial unset clock contains hour zero. After setting, both noon and
midnight use hour C with the appropriate AM/PM flag. These fields are shared
by V1JA, V1JB, V1ES, V2J, V3J and V4J in the verified dumps.

Recorded increment/carry writes occur at PCs `0x002B`, `0x002D`, `0x0030`,
`0x0039`, `0x003D` and `0x0040`; seconds also reset at `0x0241` when confirming
the clock. All three rollover observations reach HALT at PC `0x016D`.

## Android override — preview14

In the launcher's advanced controls, **Set Digimon clock to phone time** samples
local phone time when the command executes. **Choose Digimon clock time** uses a
24-hour picker and sets seconds to zero. These are one-time changes; they do not
continuously force the firmware to follow timezone or wall-clock edits. The
nanofont Glyph phone-clock overlay is independent.

Controls are enabled for a running, recognized classic ROM. Finish initial clock
setup first. The worker waits up to 1.2 seconds for HALT at PC `0x016D`, outside
initial setup, while normal budgeted emulation continues. A busy scene can reject
the request; return to idle and retry. Battle links reject clock changes. A
queued request which times out cannot apply later.

The core checks its own immutable ROM bytes against the six known hashes.
Neither a caller-supplied profile nor a forged saved PC can enable an unknown or
modified ROM. The mutation writes only `0x01A..0x01F`; every other RAM nibble,
CPU register, GPIO/buzzer state and peripheral fractional phase stays unchanged.
No instructions are skipped or executed outside the real-time pacing budget.
The normal firmware redraw displays the new hours/minutes and AM/PM label at its
next update. The operation does not patch VRAM or paint an Android clock over
the firmware. The service performs a durable autosave and reports a save failure
separately from a successful clock change.

A time-of-day change can affect normal subsequent game behavior such as sleep.
It does not directly edit age, care, evolution or other elapsed-life counters.
The existing fraction of a second is preserved: the next firmware tick may
arrive immediately. The clock's second phase is distinct from the peripheral
`DBG_IT1_SET` diagnostic edge. Tests must sample after the decimal carry has
finished rather than treating an intermediate instruction as a stable time.

Verification uses all six private ROMs in the Android Kotlin core, independently
of the Python trace:

- All 24 hour settings and 60 minute settings via emulated buttons; natural
  23:59:59 → midnight → midnight plus one second.
- Noon and midnight after an override; only the six time nibbles change at the
  operation, with every other saved machine field compared exactly.
- Actual StateManager JSON encoding/decoding, identical continuation and timer
  phases, and a further ten seconds of clock progression.
- Rendered hours/minutes and AM/PM pixels match the same time entered manually
  through the ROM's buttons; old digits change on the firmware's normal redraw.
- Commands on the actual emulator worker in all three display timing modes;
  cancellation, predicate failures and continued worker service.
- Busy/setup rejection and modified-ROM/forged-boundary rejection.
- Overridden 23:59:59 state exported through the real portable save protocol to
  the Frame Python runtime and back, then continued through midnight, for every
  revision. These local host checks do not wake or require the headset.

The full preview14 gate is 111 JVM tests: 110 passed, one opt-in hardware handoff
check skipped. Debug packaging and lint passed. No ROM bytes are checked in.
Phone UI/locked-screen checks and real phone-to-Frame SAF/Syncthing handoff remain
hardware checks; no Android device was connected through ADB.
