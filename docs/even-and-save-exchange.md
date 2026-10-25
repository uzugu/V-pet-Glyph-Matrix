# Even G2 streaming and cross-device save exchange

Goal: Glyph remains the phone emulator and Nothing Matrix display. Even G2 is an optional remote LCD and gamepad, implemented as its own EHPK client. Steam Frame remains a separate app. Completed, portable snapshots move between phone and Frame using Syncthing.

## Current implementation

- Android preview11 / code15: **Even G2 & save sync** in the ROM loader.
- Phone-local authenticated HTTP host on `127.0.0.1:8766`, disabled until enabled in that menu. Does not expose a LAN listener. Token is generated privately on the phone.
- `even-client/` v0.1.1: SDK0.0.16, requires Even App2.2.10+. Integer-scaled LCD, classic care icons, native ring/glasses button gestures, browser preview and manual controls. No emulator or ROM on the glasses.
- Frame companion scene9: save browser with explicit select and resume, publish, refresh and pagination. Existing spatial UI and crash protection preserved.
- Explicit snapshot import with a backup. Neither device automatically loads the newest file. Divergent branch tips are visible and require choosing a state; emulator states cannot be merged.

## Use

1. Install `app/build/outputs/distributions/V-Pet-Glyph-1.4-dpower.preview16-debug.apk`.
2. Start the pet. Open **Even G2 & save sync**, enable streaming and copy the pairing key.
3. Install `even-client/vpet-stream.ehpk` in Even App. Paste the key, connect phone, then connect glasses. These apps run on the same phone; `127.0.0.1` is intentional.
4. Classic gestures: press A, swipe up B, swipe down C, double press B. D-Power: press Up, swipe up Left, swipe down Right, double press Down. For D-Power, tap then long press opens the native glasses menu with **Step** and **Card input**. The same actions are available on the phone controls; Card is an emulated contact, not an NFC scan.
5. Disconnect clears the remote LCD. Commands are short pulses, never persistent held keys. Lost acknowledgements are not retried. A connection loss invalidates queued inputs and starts a fresh input session on recovery; an unchanged LCD is redrawn. Native menus temporarily suppress game input. Locking the phone does not explicitly disconnect; Android can still suspend Even's WebView. Reopen and reconnect after process reclamation.

To switch devices: **publish**, pause/stop the source pet, wait for Syncthing, load the identical ROM revision on the destination, browse saves, choose the intended branch and **Back up and resume**. Publish again before returning. Time spent away is not silently fast-forwarded during import.

## Syncthing folders

Dedicated folder ID: `vpet-save-exchange-v1`.

- Host: `/srv/data/vpet-save-exchange`
- Frame: `/home/steamos/.local/share/vpet-frame/exchange`
- Android: accept the offered folder in Syncthing using a user-accessible shared-storage location, then choose that exact directory through **Choose exchange folder** in V-pet Glyph. Android cannot use another app's private directory.

Frame uses a separate user service `vpet-syncthing.service`, with official Syncthing2.1.7 arm64 installed as `~/.local/bin/vpet-syncthing`. GUI listens only on loopback. Config lives at `~/.local/state/vpet-syncthing`. Existing host devices/folders were preserved; the existing `np3` device is offered this additional folder. The phone must accept the Frame device if direct phone↔Frame sync is desired; the host can also forward snapshots while online.

Ignore `*.partial` in Syncthing. Do not synchronise working saves, preference databases, ROM libraries or backups. UUID filenames prevent ordinary overwrites; retain Syncthing conflict copies for inspection. A completed file is immutable. Backups live in the phone's private `handoff-backups/` or Frame's `saves/handoff-backups/`.

## Protocol v1

Files: `<romHash>--<uuid>.vpetsave`, max256KiB. Strict fields:

```
schema: "vpet.save-exchange", version: 1
id: UUID, parent: previous snapshot UUID or null
origin: device identifier, createdMs: Unix milliseconds
romHash: SHA-256 of ROM bytes, profile: canonical revision ID
core: "E0C6200" or "SPL61X"
payload: JSON state encoded as a string
payloadSha256: SHA-256 of the exact UTF-8 payload string
```

Classic payload uses Glyph STATE_VERSION2, including RAM, VRAM, CPU, timer phases, GPIO and buzzer fields. Diagnostic counters may reset on Frame. D-Power uses CORE, DPOWER_SNAPSHOT and STEP_PENDING/PHASE/REMAINING. The Python snapshot is schema2. Frame has no audio mixer: sound-generator state survives, but sample phase is reset when returning to phone. Device-local auto-step settings stay local.

Android uses SAF: write `.partial`, flush, verify content, then provider rename. Choose a local storage provider supporting rename; SAF does not promise filesystem fsync or atomic rename on every provider. Readers validate full identity/checksum/state before import. Frame validates on a fresh emulator, publishes with fsync and an exclusive final link, and backs up before replacement. Its exchange folder must support hardlinks (normal Linux home storage does).

Streaming API: authenticated `GET /v1/frame` returns version1,32×24 marker rows, ROM hash, readiness and runtime state. `POST /v1/press` accepts session UUID, monotonically consecutive seq, button and expected ROM hash. No retries after ambiguous input. CORS/preflight supported; server queues, request sizes and session history are bounded.

## Verification and remaining hardware checks

Locally verified:

- Six real classic ROMs: Kotlin→Frame with 106000 cycles of Python execution→Kotlin, preserving CPU/memory/timer phases and subsequent continuation. Diagnostic counters and instantaneous audio output excluded from identity comparison.
- Real D-Power ROM: phone Python→Frame Python→phone Python with execution on both sides; CPU fields, RAM, sound-generator and tone state match. Chaquopy Android UI integration remains a hardware check.
- Authenticated HTTP requests, replay/gap rejection, malformed/oversize inputs, server shutdown.
- EHPK frame validation, pixel scaling/cropping, BMP layout, gestures, serialized SDK sends, native menu input suppression and reconnect page lifecycle.
- Android suite:72 tests (71 passed,1 opt-in Frame hardware test skipped in the normal run), build/lint passed. The separate hardware handoff run passed all3 SaveHandoffTest methods with the responder active. Frame Python and19 Node tests passed;6 EHPK tests and build/package passed. Mobile pairing screen inspected in headless Chrome.
- Host↔Frame Syncthing transport tested, followed by full handoffs: all six classic Kotlin cores and D-Power phone Python runtime published snapshots through Syncthing to Frame ARM and resumed the returned states. Frame's production import/backup/execute/export path was used. This proves runtime/transport continuity; the Android app and SAF integration still need testing on the phone.
- Frame ARM backend:22 Python tests passed in an isolated directory;2 tests requiring phone runtime/private ROM were skipped. The VR app was not restarted.

Pending: install APK on the phone (no ADB device connected), accept Syncthing folder/device there, phone↔Frame hardware save handoff, actual G2 image/input/lock/reconnect testing. Frame companion/backend scene9 was deployed on2026-10-24, preserving the existing models. Runtime inspection confirms the save menu is mounted and connected. Code and working-save backups are on Frame under `~/.local/state/vpet-frame-upgrades/20261024T044207Z/`. Its existing compositor crash investigation remains separate.

**Network qualification:** Even's official networking docs require an origin whitelist and CORS; both are implemented. Docs describe plain HTTP for local development and HTTPS for production. Loopback HTTP/private-network access in the actual Android Even host is not yet proven. Treat this EHPK as a local development package until tested. A public-store release may need a different supported secure transport; do not claim store-ready connectivity from desktop tests.

Official references checked2026-10-24:

- https://hub.evenrealities.com/docs/build/networking
- https://hub.evenrealities.com/docs/build/background-lifecycle
- https://hub.evenrealities.com/docs/build/page-lifecycle
- SDK0.0.16 README (npm's current version).

Build EHPK: `cd even-client && npm ci && npm test && npm run build && npm run pack`.
For cross-runtime tests, put Frame beside this repository and set `CLASSIC_ROM_DIR` for the Android JVM tests; set `VPET_DPOWER_ROM` for Frame Python tests. No ROM data is included in either repository.

### Repeat the actual Syncthing/Frame handoff check

Use a unique validation subdirectory on both devices. Normal save browsing scans only the exchange root, so these tests do not enter the user-facing save list or touch working saves.

On Frame, run `tools/validate-syncthing-handoff.py --exchange-dir ~/.local/share/vpet-frame/exchange/validation/<run> --rom-dir ~/.local/share/vpet-frame/roms --count 7 --timeout 240`.

On the host, set `FRAME_EXCHANGE_VALIDATION_DIR=/srv/data/vpet-save-exchange/validation/<run>` and `CLASSIC_ROM_DIR` before running the Android JVM `SaveHandoffTest`; it publishes all six revisions and validates the Frame responses. Gradle treats these environment paths as test inputs so changing them cannot reuse a skipped/cached result. Then run `python3 tools/validate-dpower-syncthing.py --exchange-dir /srv/data/vpet-save-exchange/validation/<run> --rom /path/to/DPowerDigiviceEU101.bin`. The responder exits after seven requests. Both sides execute real firmware; snapshots are removed after verification. No ROM bytes are transferred by Syncthing.


## Preview16: publication and backup failure handling

Publishing a snapshot and recording local ancestry are separate commits. If the
ancestry preference cannot be saved, the completed snapshot remains intact and
the app reports success with a history warning. A resumed, durably saved pet
also remains resumed after a history failure. The next publication may form a
separate branch; existing files and explicit branch selection remain available.

SAF providers can preserve document IDs during rename and can lose a rename
acknowledgement after completing it. Cleanup deletes only partials whose rename
has not been attempted. Ambiguous rename failures leave the document alone;
check the exchange folder before retrying. Unfinished `.partial` files remain
ignored by readers and Syncthing. No cleanup path deletes a completed snapshot
through its former URI.

Before import changes a pet, its private backup is encoded, validated, written
to a unique temporary file, flushed with `FileDescriptor.sync`, checked by
reading it back and exclusively published with a hardlink. App-private Linux
storage must support hardlinks; failure prevents import. Existing backup IDs
cannot be overwritten. This syncs file contents before publication; directory
metadata persistence across sudden device power loss is not established by
host tests. The Android SAF provider's filesystem/rename guarantees still need
phone validation.

Save exchange rejects an active battle link. Host failure-injection tests cover
stable document IDs, failed preference commits, corrupted provider reads,
ambiguous rename outcomes, backup byte verification, identity reuse and an
unavailable backup directory. These exercise Android boundary mocks and actual
host filesystem writes; they do not constitute phone SAF/hardlink validation.

Preview16 software gate: 123 JVM tests, 122 passed and one opt-in Frame
hardware test skipped, with all six private classic ROMs supplied. Versioned
debug packaging and lint passed. ADB device discovery and wireless-debugging
mDNS discovery both returned no devices.
