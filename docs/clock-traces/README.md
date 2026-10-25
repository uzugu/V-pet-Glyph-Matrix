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

This establishes the time fields and normal rollover, not a completed override.
Before exposing a control, the Android Kotlin core must independently verify
these observations, apply changes at a safe firmware boundary, reject unknown
ROMs, preserve every other RAM field/register/timer phase at the mutation,
and test continued execution and save/restore. Changing time of day may affect
normal firmware behavior such as sleep; that is distinct from directly editing
care, age or evolution counters. The existing nanofont phone-clock overlay is
separate from this firmware clock.
