# Always-On Glyph recovery — preview 8

The official [Matrix SDK AOD documentation](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit#toy-with-aod-capability) specifies periodic `EVENT_AOD` messages for the selected Always-On toy. Being an app-started emulator and being the selected Always-On toy are separate states.

## Confirmed app defect

Previously, `STATUS_END` cleared display ownership and released the SDK manager. A later `EVENT_AOD` only called `pushStaticFrame`, which returned when the renderer was missing. When the firmware used END → AOD without a new bind, this path could never recover the display.

Preview 8 tracks binding separately from interactive/AOD/end state. AOD while still bound reclaims the toy display, reconnects the manager, starts/resumes the selected core if needed, and pushes the current frame. If binding is asynchronous, the connection callback pushes it. Interactive START also pushes a frame even when the LCD has not changed. Actual unbind still revokes ownership; stale AOD or START events cannot take over another toy's LEDs. AOD does not register interactive flick sensors; live play restores them on START.

The manifest already declares AOD support. This fix does not override system timeout/pocket/screen policies, use an undocumented timeout API, or bypass intentional DND Freeze. It also does not establish the event sequence on the user's particular firmware. No phone was connected for this reproduction; the recovered transition is covered by local state tests, and SDK integration is compile/lint checked.

## Device check

Verify V-Pet is selected as the **Always-on Glyph Toy** in Nothing's Glyph settings, in addition to being an enabled interactive toy. Menu names can vary by firmware. Note the Nothing OS build and whether the failure is a black display or fallback to the system clock. Capture bind/start/end/AOD/reconnect messages in logcat if a phone is available.

## Optional interactive timeout request — preview20

**Emulator runtime → Keep Glyph display awake (experimental)**, also available
under **D-Power console → Glyph Display**, saves an opt-in preference. While the
emulator is running and V-pet owns an interactive toy session, SDK 2.0's exposed
`setGlyphMatrixTimeout(false)` requests disabling display timeout. Turning the
option off, END, AOD, actual unbind, emulator stop and DND Freeze restore timeout
with `true` before SDK release. Reconnection reapplies the preference only for a
running interactive session. The default-off path makes no timeout call.

This API exists in the pinned, unmodified SDK binary but is absent from Nothing's
published API reference. The SDK bytecode names its boolean parameter `needTimeout` (false disables the request); no attached
Phone (3) was available to verify firmware behavior, duration, pocket policy or
restoration. Treat it as a compatibility experiment, not a guarantee of always-on
LEDs. No app-owned output or simulated button presses are used. Other toys retain
display ownership after unbind.

The launcher widget's “Keep Digivice awake” option is separate: it keeps the
**phone screen** on only while its expanded view is open. Neither option changes
the ROM's own LCD blanking or the pet's sleep/care rules. Nothing's Always-on Glyph
Toy selection remains the supported path for system-controlled AOD.

Tests cover default/background isolation, one request per transition, restoration
on END/AOD/stop/disable, reconnection and failed-request retries. Hardware behavior
must still be checked on the phone.

Preview20 validation: 102 executed unit tests passed, 39 optional tests skipped. Debug assembly and lint passed with no errors. No Android device was attached.
