# D-Power PCM audio — preview 5

The old Android adapter retained only the strongest tone every ~10 ms. It discarded signed DAC samples and reused the classic square-wave buzzer player, which extends short sounds and imposes a minimum gain. D-Power speaker playback now bypasses that path entirely. Classic V-pet audio is unchanged.

## Source and timing

The pinned, unmodified `GPL191Xsound.py` emits two channels. Each event is applied at its emulated instruction boundary, not when the Android audio thread happens to wake. `Bus` accumulates elapsed CPU cycles and renders the previous channel state up to each event before changing it.

Tone mode uses the sine shape in upstream [audio_engine.py](https://github.com/azya52/BrickEmuPy/blob/376cca92c6d000ffd6046e5491f2a1c3d1a5eab0/audio_engine.py). Frequency-zero events are signed held DAC values. Each 48 kHz sample integrates both channels over its time interval; there is no minimum gain or artificial pulse extension. The two channels each have half-scale headroom before mixing into signed 16-bit mono PCM. Absolute gain and sample rate differ from upstream's desktop speaker settings; this is not a byte-identical copy of its 44.1 kHz output.

Kotlin drains PCM about every 10 ms. This is delivery batching only: events within that interval are already represented in the samples. A dedicated AudioTrack worker handles partial writes and owns the track lifecycle; it never blocks the CPU emulation thread. Muting, restoring and resetting discard queued playback. Playback queues are bounded; if the phone falls behind, old playback is dropped instead of changing firmware timing or replaying a long stale sound backlog.

Snapshot schema 2 retains oscillator phases and partial-sample integrals. The playback queue is intentionally not saved. Schema 1 loads with a fresh renderer phase; machine/channel state remains compatible. Invalid renderer state is checked before replacing the running machine. The strongest-tone callback remains solely for approximate haptic feedback.

## Validation and limits

Tests cover signed mixing, sine shape/frequency, stop timing, quiet gain, short and sub-sample pulses, bounded buffering, exact sample counts, phase continuation, legacy saves and atomic invalid restores. Private-ROM tests retain CPU equivalence with upstream and verify PCM continuation after restore.

This reproduces the upstream core's audio events, not a measured original device speaker. Event timestamps have CPU-instruction resolution; upstream's chip model, Android latency/underruns, phone performance and the original analogue speaker response remain validation limits. No physical Nothing Phone audio recording or listening comparison was available during this change.
