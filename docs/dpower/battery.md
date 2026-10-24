# Background battery investigation — preview12

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

This change is in **V-pet Glyph Matrix**, package`com.digimon.glyph`. The separate reconstructed D-Arc application in`d-arc-glyph/app-project` has its own render-driven runtime. It was inspected but not modified; these APKs do not share code at runtime.
