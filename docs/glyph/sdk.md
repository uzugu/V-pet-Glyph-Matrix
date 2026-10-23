# Official Nothing Matrix integration review — 2026-10-23

Source: [official Matrix kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/tree/999b1143a6bff0dd79db11d21b92c15c56a346b1).
Current main contains SDK **2.0**; the latest tagged GitHub release remains **V1.1**, published 2025-08-07. These are different version channels.

Pinned source commit: `999b1143a6bff0dd79db11d21b92c15c56a346b1` (2026-03-10).
AAR SHA-256: `be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29`.

Run `python3 tools/fetch_glyph_sdk.py` before building. The new SDK binary is downloaded directly from Nothing, ignored by Git, and left unmodified. Its [upstream license](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/999b1143a6bff0dd79db11d21b92c15c56a346b1/LICENSE.md) applies; it is not part of this project's own open-source license.

## Integration decisions

- Support interactive 25×25 Phone (3) via `Common.is23112()`, `getDeviceMatrixLength()`, and `Glyph.DEVICE_23112`. Other phones retain the Android UI/widget. Phone (4a) Pro is 13×13, AOD-only and has no Glyph Touch; it is not silently advertised as supported gameplay hardware.
- SDK brightness is 0–255, corrected from 4095.
- Keep `build(context)`: it is the actual SDK 2.0 frame-builder signature. The README's no-argument example disagrees with its own API table.
- This app implements a Glyph **toy**, not unsolicited app-owned Matrix output. `setMatrixFrame` is gated by toy binding/start; unbind/end immediately stops sensors and detaches the display. Explicitly app-started simulation can continue without owning the LEDs. Future app-owned output must use `setAppMatrixFrame` / `closeAppMatrix`, requires firmware 20250801+, and yields to toys.
- Short button presses cycle toys. D-Power help now describes hold/release for Up, or hold/flick for Step/Card. No `change` event action is used, so longpress metadata remains zero. Touch event timing and firmware delivery still require physical testing.
- AOD metadata remains enabled; `EVENT_AOD` requests the current frame while bound. Verify firmware timeout, AOD cadence and screen-off power on hardware before release.

## Verification boundary

SDK compile, unit tests and lint can validate API compatibility, not LED ownership, touch behaviour, OEM service reconnects or battery cost. No Nothing Phone was attached during this review. Test toy carousel transitions, app Start/Stop, reconnect, DND, AOD and all physical controls on Phone (3).
