# Omalauncher widget

Install both the V-pet 1.4-dpower.preview19 APK and Omalauncher Hermes 0.18.0-hermes.4 (or a
later compatible build). This native integration is supplied by our launcher
fork; importing the OWL file alone into upstream Omalauncher is insufficient.
The launcher uses `dev.omalauncher.hermes`, so it can coexist with the public
`dev.omalauncher` app. Select Omalauncher Hermes as your home app to use the tile.

1. Open V-pet, load your own ROM and start the pet.
2. Open home page 2 in Omalauncher Hermes and find **digivice**.
3. The home tile is entirely the LCD, without headings, labels or controls.
4. Tap anywhere for the larger view and A/B/C for classic pets, or directions,
   Step and Card for D-Power. Native Back/close dismisses it. DND hides inputs.
5. Use the corner gear in the expanded view to choose one of eight LCD colors
   from the active launcher theme. Color and awake preferences persist.
6. “Keep Digivice awake” keeps the phone display on while the expanded view is
   open; closing it restores normal screen timeout. It defaults to off and does
   not override DND.
7. Three dots indicate a disconnected tile; tap it for recovery actions.

The default widget is `55-vpet.owl`. Launcher upgrades update untouched defaults;
user-edited or deleted defaults are preserved. If you customized or removed the
old widget, import the new supplied file separately or update your copy.
The widget follows the launcher's resource language, independently of V-pet's
in-app override; Japanese is translated, other languages use English.

## Connection

Protocol 2 binds the explicit `com.digimon.glyph.DigimonGlyphToyService` with
`com.digimon.glyph.OWL_BIND`. Allowed launcher package IDs remain
`dev.omalauncher.hermes` and `dev.omalauncher`. Both sides must use protocol 2.
Frames contain width/height, bounded theme-neutral rows, ready/frozen state,
`dpower`, `inputEpoch`, and an acknowledgement sequence. Inputs carry the epoch
of the frame the launcher accepted. The phone rejects mismatched sessions,
frozen/unavailable state and controls for another device type.

All widgets share one local connection. Frames are conflated, with one
unacknowledged delivery per client; invisible widgets stop subscribing.
The bridge needs no HTTP server, network, Linux or additional emulator.
Game buttons act on finger-down. The emulator serializes contacts using
120 ms holds and 80 ms released gaps measured in firmware cycles, so host
stalls cannot swallow presses. Save/session changes cancel pending contacts.
Battle-link controls retain their existing special timing.
ROMs and saves stay in V-pet. Existing timing and power-save settings apply.

## Verification

Unit checks cover device-specific inputs, stale sessions after save replacement
and service restart, frame bounds, localized recovery text, actual OWL tap actions, a display-only home tile,
and device-specific controls in the larger screen, and frozen/offline controls.
Builds and lint run for both projects. No phone was attached during development.

Still check on the phone: switching launchers, theme colors, Japanese at large
font sizes, all six D-Power controls, save reload, app/service restart,
lock/unlock, DND transitions and returning to the widget after backgrounding.
Overnight battery use and physical touch latency remain unmeasured.

## Settings/input update validation

The preview19 emulator build passed all 98 executed unit tests; 39 optional tests
were skipped. The button queue tests and actual-worker stall regression passed,
including separate contacts and firmware-clock hold/release timing.
Debug assembly and lint passed (0 errors; 160 warnings).
The matching launcher passed 232 unit tests with 2 optional integration tests
skipped, plus debug assembly and lint (0 errors).
No Android device was attached; screen timeout and physical touch accuracy
still need device verification.

Preview21 updates the V-pet backend only: the Digivice core now parks while its
firmware LCD sleeps, or while the phone screen is off with no interactive Glyph
toy. Press a real control to wake LCD sleep. Loaded state is kept in memory and
sleep time is not simulated on return. Classic pets keep their existing clock.
See [battery behavior](dpower/battery.md#preview21-stop-the-digivice-core-during-standby).

Preview23 replaces widget polling with new-frame notifications and bounds legacy
RemoteViews images to 256 px while preserving aspect ratio. Pixel-identical
images are not resent; configuration/resize changes force refresh. Debug RAM
copies require an actual observer. D-Power standby now defaults to real-time
catch-up, with the old freeze behavior available in its console. Existing OWL
protocol and launcher controls are unchanged.
