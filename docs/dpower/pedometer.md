# D-Power walking input

Upstream's D-Power EU 1.01 configuration exposes `btnStep`: **CD, mask 2 (pin 1), active high**. Walking uses this existing emulated contact, never writes a guessed RAM step counter. Manual Step and Glyph hold/flick use the same pulse queue.

Enable **D-Power controls → Enable walking steps** and grant Physical activity permission on Android 10+. Start the pet to keep it running with the screen off. The option defaults off. Without a step detector or permission, manual Step remains available.

The implementation uses Android `TYPE_STEP_DETECTOR`, which reports individual walking events. See [official Android motion sensor documentation](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion#sensors-motion-stepdetector). There is deliberately no accelerometer shake heuristic or historical step-counter import. Permission revocation, Stop, DND Freeze and a switch to a classic ROM stop collection; an explicitly app-started pet can keep walking input while another Glyph toy is displayed.

Events are serialised into 80 ms high / 120 ms released pulses measured in **emulated cycles**, with up to 128 pending contacts. Batched events remain distinct. Queue/phase/time are saved with the D-Power snapshot; full reset clears them. Registration resets the timestamp boundary and ignores stale/duplicate sensor events. Sensor collection itself is not persisted across process death, so steps taken while stopped are not credited.

These pulse durations are an implementation choice pending physical/firmware calibration, not a measured original switch waveform. Tests verify contact sequencing, release gaps, queue bounds and snapshot continuation. Actual walking detection, pocket behaviour, in-game credited steps and screen-off sensor delivery still require a phone. A missed or unavailable Android detector cannot be corrected by the emulator.
