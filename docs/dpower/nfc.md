# NFC Card input (preview 10)

Start D-Power, open **D-Power console → Device Controls → Scan NFC card**, then hold an NFC tag near the phone’s antenna. Keep the app in front and the screen unlocked. One discovery sends one existing Card input and closes the scan. Tap Scan NFC card again for another input. Scanning times out after 30 seconds and stops on cancel, console dismissal or Activity pause. The Glyph hold-and-flick-right shortcut remains available without NFC.

The app declares Android’s normal `NFC` permission (no runtime permission prompt) and marks NFC hardware optional. Missing NFC hardware shows an explanation; disabled NFC offers Android NFC settings. Returning from settings requires explicitly starting a new scan.

## What the scan means

Pinned BrickEmuPy D-Power EU 1.01 configuration exposes `btnCard` as port **CD, mask 4 (pin 2), active high**. NFC detection triggers the existing Card press/release path. It does not write game state, decode a card’s identity, or choose an in-game power from tag data. Ordinary printed Digimon cards without NFC chips cannot be detected. The firmware decides what a Card contact does in its current screen.

The reader polls NFC A/B/F/V and barcode technologies, skipping NDEF checks. It does not read or store tag identifiers or payloads, write to tags, or open tag URLs. Scanning runs only during the explicit foreground scan dialog; this is not lock-screen NFC support.

A generation token accepts at most one callback per scan and rejects callbacks after cancellation or from an earlier scan. The activity validates running D-Power state and the runtime revision at delivery. The service validates them again and does not start the pet for an NFC command. Stop, ROM replacement/import and DND Freeze prevent acceptance.

## References and validation

- [Android NfcAdapter / enableReaderMode](https://developer.android.com/reference/android/nfc/NfcAdapter#enableReaderMode(android.app.Activity,%20android.nfc.NfcAdapter.ReaderCallback,%20int,%20android.os.Bundle)): reader mode is limited to the foreground Activity.
- [Android NFC basics and manifest requirements](https://developer.android.com/develop/connectivity/nfc/nfc).
- JVM tests cover single-delivery, cancellation, stale callbacks across rearming and runtime changes. Build/lint check the Android wiring.

Physical tag detection, NFC-disabled/settings flow, cancellation during detection, and the firmware’s Card response still require on-device verification. No device was connected for this implementation.
