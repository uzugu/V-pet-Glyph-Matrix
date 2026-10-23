# Always-On Glyph recovery — preview 8

The official [Matrix SDK AOD documentation](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit#toy-with-aod-capability) specifies periodic `EVENT_AOD` messages for the selected Always-On toy. Being an app-started emulator and being the selected Always-On toy are separate states.

## Confirmed app defect

Previously, `STATUS_END` cleared display ownership and released the SDK manager. A later `EVENT_AOD` only called `pushStaticFrame`, which returned when the renderer was missing. When the firmware used END → AOD without a new bind, this path could never recover the display.

Preview 8 tracks binding separately from interactive/AOD/end state. AOD while still bound reclaims the toy display, reconnects the manager, starts/resumes the selected core if needed, and pushes the current frame. If binding is asynchronous, the connection callback pushes it. Interactive START also pushes a frame even when the LCD has not changed. Actual unbind still revokes ownership; stale AOD or START events cannot take over another toy's LEDs. AOD does not register interactive flick sensors; live play restores them on START.

The manifest already declares AOD support. This fix does not override system timeout/pocket/screen policies, use an undocumented timeout API, or bypass intentional DND Freeze. It also does not establish the event sequence on the user's particular firmware. No phone was connected for this reproduction; the recovered transition is covered by local state tests, and SDK integration is compile/lint checked.

## Device check

Verify V-Pet is selected as the **Always-on Glyph Toy** in Nothing's Glyph settings, in addition to being an enabled interactive toy. Menu names can vary by firmware. Note the Nothing OS build and whether the failure is a black display or fallback to the system clock. Capture bind/start/end/AOD/reconnect messages in logcat if a phone is available.
